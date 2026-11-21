package com.example.uhid

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Process
import com.example.transport.LinkPadConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

sealed class UhidBridgeState {
    data object Idle : UhidBridgeState()
    data object Checking : UhidBridgeState()
    data class SetupRequired(
        val tcp5555Open: Boolean,
        val wirelessConnectPort: Int? = null,
        val wirelessPairPort: Int? = null,
        val guidance: String
    ) : UhidBridgeState()
    data object WaitingTrustPrompt : UhidBridgeState()
    data object StartingAgent : UhidBridgeState()
    data class Active(
        val backend: UhidBackendType,
        val activeSlotsMask: Int = 0
    ) : UhidBridgeState()
    data class Error(val message: String) : UhidBridgeState()
}

/**
 * Manages the in-app loopback ADB connection and privileged UID 2000 [UhidHostAgent] pipeline
 * inside the unified LinkPad APK with zero external helper apps (no Shizuku) and zero root.
 */
class LinkPadHardwareBridge(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UhidBridgeState>(UhidBridgeState.Idle)
    val state: StateFlow<UhidBridgeState> = _state.asStateFlow()

    @Volatile
    private var adbManager: LinkPadAdbConnectionManager? = null
    @Volatile
    private var pipeSocket: DatagramSocket? = null
    @Volatile
    private var pipeAddr: InetAddress? = null
    private val pipeSeq = AtomicInteger(0)

    private var heartbeatJob: Job? = null
    private var mDnsPairJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var pairDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var discoveredPairPort: Int? = null
    @Volatile
    private var discoveredConnectPort: Int? = null

    fun refreshProbe() {
        scope.launch {
            _state.value = UhidBridgeState.Checking
            startMdnsAdbDiscovery()
            val alive = pingAgent()
            if (alive != null) {
                ensurePipeSocket()
                _state.value = UhidBridgeState.Active(alive.first, alive.second)
                startHeartbeatLoop()
                return@launch
            }
            val tcpOpen = isLoopbackPortOpen(5555)
            val wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
            if (tcpOpen) {
                // Attempt zero-click connect if trust was previously granted
                activateInternal(allowWaitPrompt = false)
            } else {
                _state.value = buildSetupRequiredState(tcpOpen = false, wirelessReady = wirelessReady)
            }
        }
    }

    fun activateHardwareGamepads() {
        scope.launch {
            activateInternal(allowWaitPrompt = true)
        }
    }

    fun pairAndActivateWirelessDebugging(pairPortText: String, pairingCode: String) {
        val cleanCode = pairingCode.filter { it.isDigit() }
        val explicitPort = pairPortText.trim().toIntOrNull() ?: discoveredPairPort
        if (cleanCode.length != 6 || explicitPort == null || explicitPort !in 1..65535) {
            _state.value = UhidBridgeState.Error("Enter the valid port and 6-digit Wireless Debugging code.")
            return
        }

        scope.launch {
            _state.value = UhidBridgeState.StartingAgent
            try {
                val mgr = getOrCreateAdbManager()
                val localHost = findPreferredLocalConnectHost()
                val ok = runCatching { mgr.pair(localHost, explicitPort, cleanCode) }
                    .recoverCatching { mgr.pair("127.0.0.1", explicitPort, cleanCode) }
                    .getOrDefault(false)
                if (!ok) {
                    _state.value = UhidBridgeState.Error("Wireless Debugging pairing failed. Double-check the port & 6-digit PIN.")
                    return@launch
                }
                delay(400L)
                activateInternal(allowWaitPrompt = true)
            } catch (t: Throwable) {
                _state.value = UhidBridgeState.Error(t.message ?: "Wireless Debugging pairing failed.")
            }
        }
    }

    fun sendSlotState(slot: Int, buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) {
        if (_state.value !is UhidBridgeState.Active) return
        val sock = ensurePipeSocket() ?: return
        val addr = pipeAddr ?: return
        val bytes = UhidProtocol.encodePipeState(
            cmd = UhidProtocol.CMD_INPUT,
            slot = slot,
            seq = pipeSeq.incrementAndGet(),
            buttonMask = buttonMask,
            lx = lx,
            ly = ly,
            rx = rx,
            ry = ry
        )
        runCatching {
            sock.send(DatagramPacket(bytes, bytes.size, addr, LinkPadConstants.UHID_AGENT_UDP_PORT))
        }
    }

    fun destroySlot(slot: Int) {
        if (_state.value !is UhidBridgeState.Active) return
        val sock = ensurePipeSocket() ?: return
        val addr = pipeAddr ?: return
        val bytes = UhidProtocol.encodePipeState(
            cmd = UhidProtocol.CMD_DESTROY_SLOT,
            slot = slot
        )
        runCatching {
            sock.send(DatagramPacket(bytes, bytes.size, addr, LinkPadConstants.UHID_AGENT_UDP_PORT))
        }
    }

    fun shutdownAgent() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopMdnsAdbDiscovery()
        scope.launch {
            runCatching {
                val sock = ensurePipeSocket()
                val addr = pipeAddr
                if (sock != null && addr != null) {
                    val stopBytes = UhidProtocol.encodePipeState(cmd = UhidProtocol.CMD_STOP_AGENT, slot = 0)
                    sock.send(DatagramPacket(stopBytes, stopBytes.size, addr, LinkPadConstants.UHID_AGENT_UDP_PORT))
                }
            }
            runCatching { pipeSocket?.close() }
            pipeSocket = null
            runCatching { adbManager?.disconnect() }
            _state.value = UhidBridgeState.Idle
        }
    }

    private suspend fun activateInternal(allowWaitPrompt: Boolean) {
        _state.value = UhidBridgeState.StartingAgent

        pingAgent()?.let { (backend, mask) ->
            ensurePipeSocket()
            _state.value = UhidBridgeState.Active(backend, mask)
            startHeartbeatLoop()
            return
        }

        val mgr = try {
            getOrCreateAdbManager()
        } catch (t: Throwable) {
            _state.value = UhidBridgeState.Error("ADB identity error: ${t.message}")
            return
        }

        val tcpOpen = isLoopbackPortOpen(5555)
        var connected = false
        var authWaiting = false

        if (tcpOpen) {
            if (allowWaitPrompt) {
                _state.value = UhidBridgeState.WaitingTrustPrompt
            }
            connected = runCatching { mgr.connect("127.0.0.1", 5555) }
                .recoverCatching {
                    val localIp = LinkPadConstants.findPrimaryLocalIpv4(appContext)
                    if (localIp != "127.0.0.1") mgr.connect(localIp, 5555) else false
                }
                .onFailure {
                    authWaiting = true
                }
                .getOrDefault(false)
        }

        if (!connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tlsPort = discoveredConnectPort
            if (tlsPort != null) {
                connected = runCatching { mgr.connect("127.0.0.1", tlsPort) }
                    .recoverCatching {
                        val localIp = LinkPadConstants.findPrimaryLocalIpv4(appContext)
                        mgr.connect(localIp, tlsPort)
                    }
                    .getOrDefault(false)
            }
            if (!connected) {
                connected = runCatching { mgr.connectTls(appContext, 3_500L) }.getOrDefault(false)
            }
        }

        if (!connected) {
            _state.value = if (authWaiting && allowWaitPrompt) {
                UhidBridgeState.Error("Approve 'Allow ADB debugging' on screen, then tap Retry Enable.")
            } else {
                buildSetupRequiredState(
                    tcpOpen = tcpOpen,
                    wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
                )
            }
            return
        }

        // Launch UhidHostAgent directly out of LinkPad's own installed APK under UID 2000
        val launched = launchUhidAgentOverAdb(mgr)
        if (!launched) {
            _state.value = UhidBridgeState.Error("Could not launch UHID agent over ADB shell.")
            return
        }

        var agentPong: Pair<UhidBackendType, Int>? = null
        for (attempt in 0 until 12) {
            delay(180L)
            agentPong = pingAgent()
            if (agentPong != null) break
        }

        if (agentPong != null) {
            ensurePipeSocket()
            _state.value = UhidBridgeState.Active(agentPong.first, agentPong.second)
            startHeartbeatLoop()
        } else {
            _state.value = UhidBridgeState.Error(
                "UHID agent did not respond. Enable USB/Wireless debugging security simulation if present in Developer Options, or use LinkPad IME below."
            )
        }
    }

    private fun launchUhidAgentOverAdb(mgr: LinkPadAdbConnectionManager): Boolean {
        return try {
            val pkg = appContext.packageName
            val pid = Process.myPid()
            val cmd = "APK=\$(pm path $pkg | head -n1 | cut -d: -f2); " +
                "nohup env CLASSPATH=\"\$APK\" /system/bin/app_process / com.example.uhid.UhidHostAgent --parent-pid=$pid >/dev/null 2>&1 &"
            val stream = mgr.openStream("shell:$cmd")
            runCatching {
                Thread.sleep(150L)
                stream.close()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(2_500L)
                val pong = pingAgent()
                if (pong != null) {
                    _state.value = UhidBridgeState.Active(pong.first, pong.second)
                } else {
                    val tcpOpen = isLoopbackPortOpen(5555)
                    _state.value = buildSetupRequiredState(
                        tcpOpen = tcpOpen,
                        wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
                    )
                    break
                }
            }
        }
    }

    private suspend fun pingAgent(): Pair<UhidBackendType, Int>? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { sock ->
                sock.soTimeout = 350
                val addr = InetAddress.getByName("127.0.0.1")
                val pingBytes = UhidProtocol.encodePipeState(cmd = UhidProtocol.CMD_PING, slot = 0)
                sock.send(DatagramPacket(pingBytes, pingBytes.size, addr, LinkPadConstants.UHID_AGENT_UDP_PORT))
                val respBuf = ByteArray(32)
                val resp = DatagramPacket(respBuf, respBuf.size)
                sock.receive(resp)
                UhidProtocol.decodePipePong(resp.data, resp.length)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isLoopbackPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildSetupRequiredState(tcpOpen: Boolean, wirelessReady: Boolean): UhidBridgeState.SetupRequired {
        val guidance = when {
            tcpOpen -> "Local ADB port 5555 detected. Tap Enable Hardware Gamepads and allow the prompt on screen."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessReady ->
                "Wireless Debugging detected on device. Tap Pair Wireless Debugging and enter the 6-digit code."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                "On Fire TV / Android TV: Settings -> Developer Options -> Turn ON ADB Debugging. On Android 11+ Phones: turn ON Wireless Debugging."
            else ->
                "On Fire TV / Android TV: Settings -> Developer Options -> Turn ON ADB Debugging, then tap Retry."
        }
        return UhidBridgeState.SetupRequired(
            tcp5555Open = tcpOpen,
            wirelessConnectPort = discoveredConnectPort,
            wirelessPairPort = discoveredPairPort,
            guidance = guidance
        )
    }

    private fun getOrCreateAdbManager(): LinkPadAdbConnectionManager {
        adbManager?.let { return it }
        return synchronized(this) {
            adbManager ?: LinkPadAdbConnectionManager(appContext).also { adbManager = it }
        }
    }

    private fun ensurePipeSocket(): DatagramSocket? {
        pipeSocket?.let { if (!it.isClosed) return it }
        return runCatching {
            pipeAddr = InetAddress.getByName("127.0.0.1")
            DatagramSocket().also { pipeSocket = it }
        }.getOrNull()
    }

    private fun findPreferredLocalConnectHost(): String {
        val localIp = LinkPadConstants.findPrimaryLocalIpv4(appContext)
        return if (localIp != "127.0.0.1") localIp else "127.0.0.1"
    }

    private fun startMdnsAdbDiscovery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (pairDiscoveryListener != null) return
        runCatching {
            val mgr = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val localIps = LinkPadConstants.findAllLocalIpv4s().toSet() + "127.0.0.1"

            fun createListener(isPairing: Boolean): NsdManager.DiscoveryListener {
                return object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) = Unit
                    override fun onServiceFound(service: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        mgr.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val hostIp = info.host?.hostAddress ?: return
                                if (hostIp !in localIps) return
                                if (isPairing) {
                                    discoveredPairPort = info.port
                                } else {
                                    discoveredConnectPort = info.port
                                }
                            }
                        })
                    }
                    override fun onServiceLost(service: NsdServiceInfo) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }
            }

            pairDiscoveryListener = createListener(isPairing = true).also {
                mgr.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, it)
            }
            connectDiscoveryListener = createListener(isPairing = false).also {
                mgr.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, it)
            }
        }
    }

    private fun stopMdnsAdbDiscovery() {
        mDnsPairJob?.cancel()
        mDnsPairJob = null
        val mgr = nsdManager ?: return
        pairDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        connectDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        pairDiscoveryListener = null
        connectDiscoveryListener = null
    }
}
