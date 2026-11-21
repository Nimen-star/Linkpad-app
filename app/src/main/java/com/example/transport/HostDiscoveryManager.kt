package com.example.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.protocol.BinaryProtocol
import com.example.protocol.DiscoveredHost
import com.example.protocol.PacketType
import com.example.protocol.TransportType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap

class HostDiscoveryManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hostMap = ConcurrentHashMap<String, DiscoveredHost>()

    private val _hosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val hosts: StateFlow<List<DiscoveredHost>> = _hosts.asStateFlow()

    private var beaconJob: Job? = null
    private var pruneJob: Job? = null
    private var nsdResolverJob: Job? = null
    private var nsdResolveQueue: Channel<NsdServiceInfo>? = null
    private var beaconSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        stopDiscovery()
        hostMap.clear()
        _hosts.value = emptyList()

        acquireMulticastLock()
        startBeaconListener()
        startNsdDiscovery()

        pruneJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                val now = System.currentTimeMillis()
                var changed = false
                val iterator = hostMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastSeenMs > 5_500L) {
                        iterator.remove()
                        changed = true
                    }
                }
                if (changed) publishHosts()
            }
        }
    }

    fun stopDiscovery() {
        beaconJob?.cancel()
        beaconJob = null
        pruneJob?.cancel()
        pruneJob = null
        nsdResolverJob?.cancel()
        nsdResolverJob = null
        nsdResolveQueue?.close()
        nsdResolveQueue = null
        runCatching { beaconSocket?.close() }
        beaconSocket = null
        stopNsdDiscovery()
        releaseMulticastLock()
    }

    private fun startBeaconListener() {
        beaconJob = scope.launch {
            runCatching {
                val sock: DatagramSocket = runCatching {
                    MulticastSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(LinkPadConstants.BEACON_UDP_PORT))
                        runCatching {
                            @Suppress("DEPRECATION")
                            joinGroup(InetAddress.getByName(LinkPadConstants.MULTICAST_GROUP_IP))
                        }
                    }
                }.getOrElse {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(LinkPadConstants.BEACON_UDP_PORT))
                    }
                }

                beaconSocket = sock
                val localIps = LinkPadConstants.findAllLocalIpv4s().toSet()
                val buf = ByteArray(BinaryProtocol.MAX_PACKET_SIZE + 16)
                val datagram = DatagramPacket(buf, buf.size)

                while (isActive) {
                    datagram.length = buf.size
                    sock.receive(datagram)
                    val packet = BinaryProtocol.decode(datagram.data, datagram.length) ?: continue
                    if (packet.type != PacketType.BEACON) continue
                    val ip = datagram.address?.hostAddress ?: continue
                    if (ip in localIps) continue
                    val port = if (packet.intPayload in 1024..65535) {
                        packet.intPayload
                    } else {
                        LinkPadConstants.HOST_UDP_PORT
                    }
                    val id = "udp:$ip:$port"
                    hostMap[id] = DiscoveredHost(
                        id = id,
                        name = packet.deviceName.ifBlank { ip },
                        hostAddress = ip,
                        port = port,
                        transport = TransportType.WIFI_UDP,
                        activePlayers = packet.extraByte.toInt() and 0xFF,
                        maxPlayers = LinkPadConstants.MAX_PLAYERS,
                        lastSeenMs = System.currentTimeMillis()
                    )
                    publishHosts()
                }
            }
        }
    }

    private fun startNsdDiscovery() {
        val queue = Channel<NsdServiceInfo>(capacity = 32)
        nsdResolveQueue = queue

        runCatching {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager

            nsdResolverJob = scope.launch {
                for (info in queue) {
                    val deferred = CompletableDeferred<Unit>()
                    runCatching {
                        @Suppress("DEPRECATION")
                        manager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                deferred.complete(Unit)
                            }

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                handleResolvedNsdService(resolved)
                                deferred.complete(Unit)
                            }
                        })
                    }.onFailure {
                        deferred.complete(Unit)
                    }
                    withTimeoutOrNull(2_500L) { deferred.await() }
                }
            }

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains("_linkpad")) return
                    queue.trySend(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            discoveryListener = listener
            manager.discoverServices(
                LinkPadConstants.NSD_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        }
    }

    private fun handleResolvedNsdService(resolved: NsdServiceInfo) {
        val ipv4 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolved.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                ?: resolved.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            resolved.host?.hostAddress
        } ?: return

        if (ipv4 in LinkPadConstants.findAllLocalIpv4s()) return
        val port = resolved.port.takeIf { it > 0 } ?: LinkPadConstants.HOST_UDP_PORT
        val cleanName = resolved.serviceName
            .removePrefix(LinkPadConstants.NSD_SERVICE_PREFIX)
            .ifBlank { ipv4 }
        val id = "udp:$ipv4:$port"
        val existing = hostMap[id]
        hostMap[id] = DiscoveredHost(
            id = id,
            name = existing?.name ?: cleanName,
            hostAddress = ipv4,
            port = port,
            transport = TransportType.WIFI_UDP,
            activePlayers = existing?.activePlayers ?: 0,
            maxPlayers = LinkPadConstants.MAX_PLAYERS,
            lastSeenMs = System.currentTimeMillis()
        )
        publishHosts()
    }

    private fun stopNsdDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        runCatching { manager.stopServiceDiscovery(listener) }
        discoveryListener = null
    }

    private fun publishHosts() {
        _hosts.value = hostMap.values.sortedWith(
            compareBy<DiscoveredHost> { it.transport != TransportType.WIFI_UDP }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("LinkPadDiscoverLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        multicastLock = null
    }
}
