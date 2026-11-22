package com.example.uhid

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Process
import com.example.transport.LinkPadConstants
import io.github.muntashirakon.adb.AdbStream
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
import java.io.BufferedReader
import java.io.InputStreamReader
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
 * Manages the in-app ADB connection and privileged UID 2000 [UhidHostAgent] pipeline.
 * Supports:
 * 1) Local wlan0 IP or 127.0.0.1 ADB port 5555 (Fire TV / Android TV)
 * 2) Android 11+ Wireless Debugging TLS pairing & connect
 * 3) Remote activation from a Controller Phone -> TV LAN IP:5555 (bypasses FireOS localhost blocks)
 */
class LinkPadHardwareBridge(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UhidBridgeState>(UhidBridgeState.Idle)
    val state: StateFlow<UhidBridgeState> = _state.asStateFlow()

    @Volatile
    private var adbManager: LinkPadAdbConnectionManager? = null
    @Volatile
    private var agentShellStream: AdbStream? = null
    private var agentOutputJob: Job? = null

    @Volatile
    private var pipeSocket: DatagramSocket? = null
    @Volatile
    private var pipeAddr: InetAddress? = null
    private val pipeSeq = AtomicInteger(0)

    private var heartbeatJob: Job? = null
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
            val tcpOpen = findReachableAdb5555Host() != null
            if (tcpOpen) {
                activateInternal(allowWaitPrompt = false)
            } else {
                _state.value = buildSetupRequiredState(
                    tcpOpen = false,
                    wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
                )
            }
        }
    }

    suspend fun quickPingAttach() {
        val current = _state.value
        if (current is UhidBridgeState.Active ||
            current is UhidBridgeState.StartingAgent ||
            current is UhidBridgeState.Checking ||
            current is UhidBridgeState.WaitingTrustPrompt
        ) {
            return
        }
        val alive = pingAgent() ?: return
        ensurePipeSocket()
        _state.value = UhidBridgeState.Active(alive.first, alive.second)
        startHeartbeatLoop()
    }

    fun activateHardwareGamepads() {
        scope.launch {
            activateInternal(allowWaitPrompt = true)
        }
    }

    fun currentDiscoveredPairPort(): Int? = discoveredPairPort

    /**
     * Allows a Controller Phone on the same Wi-Fi network to launch [UhidHostAgent] on a Host TV
     * when the TV's firmware (e.g. Fire OS 7/8) blocks self-loopback 127.0.0.1 ADB connections.
     */
    suspend fun activateRemoteHostOverWifi(targetIp: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanIp = targetIp.trim()
            require(cleanIp.isNotEmpty()) { "Missing TV IP address" }
            val mgr = getOrCreateAdbManager()
            val ok = runCatching { mgr.connect(cleanIp, 5555) }.getOrDefault(false)
            if (!ok) {
                error("Could not connect to $cleanIp:5555. Enable ADB Debugging on your TV and approve the prompt.")
            }
            val pkg = appContext.packageName
            val cmd = "APK=\$(pm path $pkg | tr -d '\\r' | head -n1 | cut -d: -f2); " +
                "if command -v setsid >/dev/null 2>&1; then " +
                "setsid sh -c \"CLASSPATH=\\\"\$APK\\\" /system/bin/app_process / com.example.uhid.UhidHostAgent --persistent\" </dev/null >/dev/null 2>&1 & " +
                "else " +
                "nohup env CLASSPATH=\"\$APK\" /system/bin/app_process / com.example.uhid.UhidHostAgent --persistent </dev/null >/dev/null 2>&1 & " +
                "fi; echo REMOTE_LAUNCHED"
            val stream = mgr.openStream("shell:$cmd")
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val line = reader.readLine().orEmpty()
            delay(500L)
            runCatching { stream.close() }
            runCatching { mgr.disconnect() }
            if (line.contains("REMOTE_LAUNCHED")) {
                "Launched persistent UHID engine on $cleanIp"
            } else {
                "Sent remote hardware trigger to $cleanIp"
            }
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
                delay(450L)
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
        agentOutputJob?.cancel()
        agentOutputJob = null
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
            runCatching { agentShellStream?.close() }
            agentShellStream = null
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
            _state.value = UhidBridgeState.Error("ADB key error: ${t.message}")
            return
        }

        val adbHost = findReachableAdb5555Host()
        var connected = false
        var authRejected = false

        if (adbHost != null) {
            if (allowWaitPrompt) {
                _state.value = UhidBridgeState.WaitingTrustPrompt
            }
            connected = runCatching { mgr.connect(adbHost, 5555) }
                .onFailure { authRejected = true }
                .getOrDefault(false)
        }

        if (!connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tlsPort = discoveredConnectPort
            val localIp = findPreferredLocalConnectHost()
            if (tlsPort != null) {
                connected = runCatching { mgr.connect(localIp, tlsPort) }
                    .recoverCatching { mgr.connect("127.0.0.1", tlsPort) }
                    .getOrDefault(false)
            }
            if (!connected) {
                connected = runCatching { mgr.connectTls(appContext, 3_500L) }.getOrDefault(false)
            }
        }

        if (!connected) {
            _state.value = if (authRejected && allowWaitPrompt) {
                UhidBridgeState.Error(
                    "ADB authorization did not complete. Tap 'Always allow' on the system popup or use Zero-ADB Accessibility/Keyboard mode above."
                )
            } else {
                buildSetupRequiredState(
                    tcpOpen = adbHost != null,
                    wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
                )
            }
            return
        }

        val launchError = launchAndAwaitUhidAgent(mgr)
        if (launchError != null) {
            _state.value = UhidBridgeState.Error(launchError)
            return
        }

        var agentPong: Pair<UhidBackendType, Int>? = null
        for (attempt in 0 until 15) {
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
                "UHID kernel driver blocked or unreachable on this firmware. Use the Zero-ADB Touch/TV Accessibility or Virtual Keyboard modes above."
            )
        }
    }

    /**
     * Launches UhidHostAgent inside the kept-open ADB shell stream (no premature stream.close() SIGHUP!)
     * and inspects stdout for immediate diagnostic confirmation or errors.
     */
    private suspend fun launchAndAwaitUhidAgent(mgr: LinkPadAdbConnectionManager): String? {
        return try {
            agentOutputJob?.cancel()
            runCatching { agentShellStream?.close() }
            agentShellStream = null

            val pkg = appContext.packageName
            val pid = Process.myPid()
            val cmd = "APK=\$(pm path $pkg | tr -d '\\r' | head -n1 | cut -d: -f2); " +
                "if [ -z \"\$APK\" ]; then echo LINKPAD_AGENT_ERROR:APK_NOT_FOUND; exit 1; fi; " +
                "CLASSPATH=\"\$APK\" exec /system/bin/app_process / com.example.uhid.UhidHostAgent --parent-pid=$pid"

            val stream = mgr.openStream("shell:$cmd")
            agentShellStream = stream
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))

            var firstError: String? = null
            var sawReady = false

            // Wait briefly for initial status line before moving reader to background
            val startMs = System.currentTimeMillis()
            while (System.currentTimeMillis() - startMs < 1_800L) {
                if (reader.ready()) {
                    val line = reader.readLine()?.trim() ?: break
                    if (line.startsWith("LINKPAD_AGENT_READY:")) {
                        sawReady = true
                        break
                    }
                    if (line.startsWith("LINKPAD_AGENT_ERROR:")) {
                        firstError = "Hardware ID blocked by kernel/SELinux (${line.substringAfter(":")}). Use Zero-ADB Accessibility or Keyboard mode above."
                        break
                    }
                }
                if (pingAgent() != null) {
                    sawReady = true
                    break
                }
                delay(80L)
            }

            if (firstError != null) {
                runCatching { stream.close() }
                agentShellStream = null
                return firstError
            }

            // Keep ADB stream drained asynchronously so shell stays alive without blocking buffer
            agentOutputJob = scope.launch {
                runCatching {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("LINKPAD_AGENT_ERROR:") && _state.value !is UhidBridgeState.Active) {
                            _state.value = UhidBridgeState.Error(
                                "Hardware ID blocked (${line.substringAfter(":")}). Use Zero-ADB Accessibility or Keyboard mode."
                            )
                        }
                    }
                }
            }
            if (!sawReady) null else null
        } catch (t: Throwable) {
            "ADB shell launch failed: ${t.message ?: "unknown error"}"
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
                    val tcpOpen = findReachableAdb5555Host() != null
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

    private fun findReachableAdb5555Host(): String? {
        val candidates = linkedSetOf("127.0.0.1")
        val primary = LinkPadConstants.findPrimaryLocalIpv4(appContext)
        if (primary != "127.0.0.1") candidates.add(primary)
        candidates.addAll(LinkPadConstants.findAllLocalIpv4s())

        for (host in candidates) {
            val open = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, 5555), 220)
                    true
                }
            } catch (_: Throwable) {
                false
            }
            if (open) return host
        }
        return null
    }

    private fun buildSetupRequiredState(tcpOpen: Boolean, wirelessReady: Boolean): UhidBridgeState.SetupRequired {
        val guidance = when {
            tcpOpen -> "Local ADB port 5555 detected. Tap Enable Hardware ID and accept the prompt."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessReady ->
                "Wireless Debugging detected on device. Tap Pair Wireless Debugging and enter the 6-digit PIN."
            else ->
                "If your Fire TV or Phone blocks local ADB (common on Fire OS 7/8 & unrooted phones), use the Zero-ADB Accessibility or Keyboard modes above — or tap 'Remote Activate TV' from your connected phone."
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
                                val current = _state.value
                                if (current is UhidBridgeState.SetupRequired) {
                                    _state.value = buildSetupRequiredState(
                                        tcpOpen = current.tcp5555Open,
                                        wirelessReady = discoveredConnectPort != null || discoveredPairPort != null
                                    )
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
        val mgr = nsdManager ?: return
        pairDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        connectDiscoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        pairDiscoveryListener = null
        connectDiscoveryListener = null
    }
}
