package com.example.uhid

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.transport.LinkPadConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class WirelessAdbTarget(
    val ip: String,
    val connectPort: Int = 5555,
    val pairPort: Int? = null,
    val label: String,
    val isTcp5555Open: Boolean = false,
    val hasTlsConnect: Boolean = false,
    val hasTlsPairing: Boolean = false
) {
    val canOneClickSetup: Boolean
        get() = isTcp5555Open || hasTlsConnect
}

object WirelessTargetHelpers {
    fun extractSubnet24Hosts(localIpv4: String): List<String> {
        val parts = localIpv4.trim().split(".")
        if (parts.size != 4) return emptyList()
        val octets = parts.map { it.toIntOrNull() ?: return emptyList() }
        if (octets.any { it !in 0..255 }) return emptyList()
        if (octets[0] == 127) return emptyList()
        val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
        return (1..254)
            .map { last -> "$prefix.$last" }
            .filter { it != localIpv4 }
    }
}

/**
 * Automatically discovers wireless Fire TVs, Android TVs (:5555), and Android 11+ Wireless Debugging
 * devices (_adb-tls-connect._tcp / _adb-tls-pairing._tcp) on the active Wi-Fi LAN without
 * triggering premature debugging approval popups.
 */
class WirelessAdbScanner(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val targetMap = ConcurrentHashMap<String, WirelessAdbTarget>()
    private val _targets = MutableStateFlow<List<WirelessAdbTarget>>(emptyList())
    val targets: StateFlow<List<WirelessAdbTarget>> = _targets.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null
    private var nsdResolveJob: Job? = null
    private var nsdResolveQueue: Channel<Pair<Boolean, NsdServiceInfo>>? = null
    private var nsdManager: NsdManager? = null
    private var tlsConnectListener: NsdManager.DiscoveryListener? = null
    private var tlsPairListener: NsdManager.DiscoveryListener? = null

    fun startScan(priorityIps: List<String> = emptyList()) {
        scanJob?.cancel()
        startMdnsDiscoveryIfNeeded()

        scanJob = scope.launch {
            _scanning.value = true
            try {
                val localIps = LinkPadConstants.findAllLocalIpv4s().toSet() + "127.0.0.1"
                val primaryIp = LinkPadConstants.findPrimaryLocalIpv4(appContext)
                val candidates = LinkedHashSet<String>()
                priorityIps
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it !in localIps }
                    .forEach { candidates.add(it) }

                WirelessTargetHelpers.extractSubnet24Hosts(primaryIp)
                    .filter { it !in localIps }
                    .forEach { candidates.add(it) }

                LinkPadConstants.findAllLocalIpv4s().forEach { extraLocal ->
                    WirelessTargetHelpers.extractSubnet24Hosts(extraLocal)
                        .filter { it !in localIps }
                        .forEach { candidates.add(it) }
                }

                // Clear stale tcp5555 flags for IPs not responding on re-scan
                val responsive = ConcurrentHashMap.newKeySet<String>()
                val gate = Semaphore(48)
                candidates.map { ip ->
                    async {
                        if (!isActive) return@async
                        gate.withPermit {
                            if (isPortOpen(ip, 5555, timeoutMs = 240)) {
                                responsive.add(ip)
                                mergeTcp5555Target(ip)
                            }
                        }
                    }
                }.awaitAll()

                targetMap.entries.toList().forEach { (ip, existing) ->
                    if (existing.isTcp5555Open && ip !in responsive) {
                        if (existing.hasTlsConnect || existing.hasTlsPairing) {
                            targetMap[ip] = existing.copy(
                                isTcp5555Open = false,
                                connectPort = if (existing.hasTlsConnect) existing.connectPort else 5555
                            )
                        } else {
                            targetMap.remove(ip)
                        }
                    }
                }
                publish()
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
        stopMdnsDiscovery()
    }

    private fun mergeTcp5555Target(ip: String) {
        val existing = targetMap[ip]
        targetMap[ip] = if (existing == null) {
            WirelessAdbTarget(
                ip = ip,
                connectPort = 5555,
                label = "TV / Android Device",
                isTcp5555Open = true
            )
        } else {
            existing.copy(
                connectPort = 5555,
                isTcp5555Open = true
            )
        }
        publish()
    }

    private fun startMdnsDiscoveryIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (tlsConnectListener != null) return

        runCatching {
            val mgr = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr

            val queue = Channel<Pair<Boolean, NsdServiceInfo>>(capacity = 32)
            nsdResolveQueue = queue

            nsdResolveJob = scope.launch {
                for ((isPair, info) in queue) {
                    val done = CompletableDeferred<Unit>()
                    runCatching {
                        @Suppress("DEPRECATION")
                        mgr.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                done.complete(Unit)
                            }

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                handleResolvedMdns(isPair, resolved)
                                done.complete(Unit)
                            }
                        })
                    }.onFailure {
                        done.complete(Unit)
                    }
                    withTimeoutOrNull(2_000L) { done.await() }
                }
            }

            fun buildListener(isPair: Boolean) = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit
                override fun onServiceFound(service: NsdServiceInfo) {
                    queue.trySend(isPair to service)
                }
                override fun onServiceLost(service: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }

            tlsPairListener = buildListener(isPair = true).also {
                mgr.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, it)
            }
            tlsConnectListener = buildListener(isPair = false).also {
                mgr.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, it)
            }
        }
    }

    private fun handleResolvedMdns(isPair: Boolean, resolved: NsdServiceInfo) {
        val hostIp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolved.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                ?: resolved.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            resolved.host?.hostAddress
        } ?: return

        val localIps = LinkPadConstants.findAllLocalIpv4s().toSet() + "127.0.0.1"
        if (hostIp in localIps) return
        val port = resolved.port
        if (port !in 1..65535) return

        val current = targetMap[hostIp]
        val friendlyName = resolved.serviceName
            .substringAfter("adb-", "")
            .substringBefore("-")
            .ifBlank { current?.label ?: "Wireless Android Device" }

        targetMap[hostIp] = if (current == null) {
            WirelessAdbTarget(
                ip = hostIp,
                connectPort = if (!isPair) port else 5555,
                pairPort = if (isPair) port else null,
                label = friendlyName,
                isTcp5555Open = false,
                hasTlsConnect = !isPair,
                hasTlsPairing = isPair
            )
        } else {
            current.copy(
                connectPort = if (!isPair && !current.isTcp5555Open) port else current.connectPort,
                pairPort = if (isPair) port else current.pairPort,
                label = if (current.label == "TV / Android Device" && friendlyName.isNotBlank()) friendlyName else current.label,
                hasTlsConnect = current.hasTlsConnect || !isPair,
                hasTlsPairing = current.hasTlsPairing || isPair
            )
        }
        publish()
    }

    private fun stopMdnsDiscovery() {
        val mgr = nsdManager
        if (mgr != null) {
            tlsPairListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
            tlsConnectListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        }
        tlsPairListener = null
        tlsConnectListener = null
        nsdResolveJob?.cancel()
        nsdResolveJob = null
        nsdResolveQueue?.close()
        nsdResolveQueue = null
    }

    private fun publish() {
        _targets.value = targetMap.values.sortedWith(
            compareByDescending<WirelessAdbTarget> { it.isTcp5555Open }
                .thenByDescending { it.hasTlsConnect }
                .thenBy { it.ip }
        )
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }
}
