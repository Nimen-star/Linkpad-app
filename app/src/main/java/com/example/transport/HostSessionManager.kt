package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.bridge.InputBridgeBus
import com.example.protocol.AnalogAxes
import com.example.protocol.BinaryProtocol
import com.example.protocol.InputPacket
import com.example.protocol.KeymapProfile
import com.example.protocol.PacketType
import com.example.protocol.PlayerSlotState
import com.example.protocol.RejectReason
import com.example.protocol.TransportType
import com.example.uhid.LinkPadHardwareBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class HostSessionManager(private val context: Context) {

    private data class ClientEndpoint(
        val slot: Int,
        val key: String,
        val deviceName: String,
        val transport: TransportType,
        val udpAddress: InetAddress? = null,
        val udpPort: Int = 0,
        val btSocket: BluetoothSocket? = null,
        val lastSeenMs: Long = System.currentTimeMillis(),
        val buttonMask: Int = 0,
        val layout: com.example.protocol.ControllerLayout = com.example.protocol.ControllerLayout.RETRO_HANDHELD,
        val lx: Byte = 0,
        val ly: Byte = 0,
        val rx: Byte = 0,
        val ry: Byte = 0,
        val rttMs: Int = 0,
        val clientId: Int = 0
    )

    val hardwareBridge = LinkPadHardwareBridge(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slotLock = Any()
    private val slots = arrayOfNulls<ClientEndpoint>(LinkPadConstants.MAX_PLAYERS)
    private val slotKeymaps = ConcurrentHashMap<Int, KeymapProfile>().apply {
        for (i in 1..LinkPadConstants.MAX_PLAYERS) {
            this[i] = KeymapProfile.GAMEPAD_STANDARD
        }
    }
    private val packetCounter = AtomicInteger(0)

    @Volatile
    private var running = false
    @Volatile
    private var pairingCodeInt = 0
    @Volatile
    private var pairingCodeStr = ""
    @Volatile
    private var hostDeviceName = "LinkPad Host"
    @Volatile
    private var allowAnyFavoriteNoCode = false
    private val trustedClientIds = ConcurrentHashMap.newKeySet<Int>()
    @Volatile
    var onTrustClientId: ((Int) -> Unit)? = null

    private var udpSocket: DatagramSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    private var udpJob: Job? = null
    private var beaconJob: Job? = null
    private var watchdogJob: Job? = null
    private var btAcceptJob: Job? = null

    fun startHosting(
        deviceName: String,
        enableBluetooth: Boolean = false,
        initialKeymaps: Map<Int, KeymapProfile> = emptyMap()
    ): String {
        if (running) {
            if (enableBluetooth && btAcceptJob == null) {
                startBluetoothServer()
            }
            return pairingCodeStr
        }

        hostDeviceName = deviceName.ifBlank { "LinkPad Host" }
        initialKeymaps.forEach { (slot, profile) ->
            if (slot in 1..LinkPadConstants.MAX_PLAYERS) {
                slotKeymaps[slot] = profile
            }
        }

        val code = generatePairingCode()
        pairingCodeStr = code
        pairingCodeInt = code.toInt()
        running = true

        InputBridgeBus.setLocalIpAddress(LinkPadConstants.findPrimaryLocalIpv4(context))
        InputBridgeBus.setHostActive(true, code)
        publishSlots()

        acquireMulticastLock()
        startUdpServer()
        startBeaconBroadcaster()
        registerNsdService()
        startWatchdog()
        hardwareBridge.refreshProbe()

        if (enableBluetooth) {
            startBluetoothServer()
        }
        return code
    }

    fun regeneratePairingCode(): String {
        val code = generatePairingCode()
        pairingCodeStr = code
        pairingCodeInt = code.toInt()
        InputBridgeBus.setHostActive(running, code)
        return code
    }

    fun updateSlotKeymap(slot: Int, profile: KeymapProfile) {
        if (slot !in 1..LinkPadConstants.MAX_PLAYERS) return
        slotKeymaps[slot] = profile
        publishSlots()
    }

    fun kickPlayer(slot: Int) {
        val removed = synchronized(slotLock) {
            val idx = slot - 1
            if (idx !in slots.indices) return
            val existing = slots[idx]
            slots[idx] = null
            existing
        }
        removed?.let { endpoint ->
            hardwareBridge.destroySlot(endpoint.slot)
            val bye = InputPacket(type = PacketType.BYE, slot = endpoint.slot)
            sendToEndpoint(endpoint, bye)
            runCatching { endpoint.btSocket?.close() }
            publishSlots()
        }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        if (!running) return
        if (enabled) {
            if (btAcceptJob == null) startBluetoothServer()
        } else {
            stopBluetoothServer()
        }
    }

    fun setTrustedConfiguration(trustedIds: Set<Int>, allowAnyFavorite: Boolean) {
        allowAnyFavoriteNoCode = allowAnyFavorite
        trustedClientIds.clear()
        trustedClientIds.addAll(trustedIds)
    }

    fun stopHosting() {
        if (!running) return
        running = false

        val bye = InputPacket(type = PacketType.BYE)
        val activeEndpoints = synchronized(slotLock) {
            slots.filterNotNull().also {
                for (i in slots.indices) slots[i] = null
            }
        }
        activeEndpoints.forEach { endpoint ->
            sendToEndpoint(endpoint, bye)
            runCatching { endpoint.btSocket?.close() }
        }

        udpJob?.cancel()
        udpJob = null
        beaconJob?.cancel()
        beaconJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        stopBluetoothServer()

        runCatching { udpSocket?.close() }
        udpSocket = null
        unregisterNsdService()
        releaseMulticastLock()
        hardwareBridge.shutdownAgent()

        InputBridgeBus.setHostActive(false)
    }

    private fun startUdpServer() {
        udpJob = scope.launch {
            try {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    trafficClass = 0x10 // IPTOS_LOWDELAY
                    bind(InetSocketAddress(LinkPadConstants.HOST_UDP_PORT))
                }
                udpSocket = sock
                val buffer = ByteArray(BinaryProtocol.MAX_PACKET_SIZE + 16)
                val datagram = DatagramPacket(buffer, buffer.size)

                while (isActive && running) {
                    datagram.length = buffer.size
                    sock.receive(datagram)
                    val packet = BinaryProtocol.decode(datagram.data, datagram.length) ?: continue
                    val senderAddr = datagram.address ?: continue
                    val senderPort = datagram.port
                    val clientKey = "udp:${senderAddr.hostAddress}:$senderPort"

                    handleIncomingPacket(
                        packet = packet,
                        clientKey = clientKey,
                        transport = TransportType.WIFI_UDP,
                        udpAddress = senderAddr,
                        udpPort = senderPort,
                        btSocket = null
                    ) { reply ->
                        val bytes = BinaryProtocol.encode(reply)
                        runCatching {
                            sock.send(DatagramPacket(bytes, bytes.size, senderAddr, senderPort))
                        }
                    }
                }
            } catch (_: SocketException) {
                // Socket closed normally on stopHosting()
            } catch (_: IOException) {
                // Handled gracefully
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothServer() {
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) {
            InputBridgeBus.setBluetoothHostingActive(false)
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            InputBridgeBus.setBluetoothHostingActive(false)
            return
        }

        btAcceptJob = scope.launch {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(
                    LinkPadConstants.BT_SERVICE_NAME,
                    LinkPadConstants.BT_SERVICE_UUID
                )
                btServerSocket = server
                InputBridgeBus.setBluetoothHostingActive(true)

                while (isActive && running) {
                    val clientSocket = try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    } ?: continue

                    launchBluetoothClientReader(clientSocket)
                }
            } catch (_: SecurityException) {
                InputBridgeBus.setBluetoothHostingActive(false)
            } catch (_: IOException) {
                InputBridgeBus.setBluetoothHostingActive(false)
            }
        }
    }

    private fun launchBluetoothClientReader(socket: BluetoothSocket) {
        scope.launch {
            val remoteAddress = runCatching { socket.remoteDevice?.address }.getOrNull() ?: "bt"
            val clientKey = "bt:$remoteAddress:${System.identityHashCode(socket)}"
            try {
                val input = socket.inputStream
                val output = socket.outputStream
                while (isActive && running && socket.isConnected) {
                    val packet = BinaryProtocol.readStreamFrame(input) ?: continue
                    handleIncomingPacket(
                        packet = packet,
                        clientKey = clientKey,
                        transport = TransportType.BLUETOOTH_RFCOMM,
                        udpAddress = null,
                        udpPort = 0,
                        btSocket = socket
                    ) { reply ->
                        synchronized(output) {
                            runCatching {
                                BinaryProtocol.writeStreamFrame(output, reply)
                            }
                        }
                    }
                    if (packet.type == PacketType.BYE) break
                }
            } catch (_: IOException) {
                // Client disconnected
            } finally {
                removeClientByKey(clientKey)
                runCatching { socket.close() }
            }
        }
    }

    private fun stopBluetoothServer() {
        btAcceptJob?.cancel()
        btAcceptJob = null
        runCatching { btServerSocket?.close() }
        btServerSocket = null
        InputBridgeBus.setBluetoothHostingActive(false)
    }

    private fun handleIncomingPacket(
        packet: InputPacket,
        clientKey: String,
        transport: TransportType,
        udpAddress: InetAddress?,
        udpPort: Int,
        btSocket: BluetoothSocket?,
        replySender: (InputPacket) -> Unit
    ) {
        packetCounter.incrementAndGet()
        val now = System.currentTimeMillis()

        when (packet.type) {
            PacketType.HELLO -> {
                val clientId = packet.extractClientId32()
                val isFavoriteMode = packet.extraByte == BinaryProtocol.HELLO_MODE_TRUSTED_FAVORITE
                if (isFavoriteMode) {
                    val trusted = allowAnyFavoriteNoCode ||
                        (clientId != 0 && trustedClientIds.contains(clientId))
                    if (!trusted) {
                        replySender(
                            InputPacket(
                                type = PacketType.REJECT,
                                extraByte = RejectReason.TRUSTED_NOT_RECOGNIZED.code
                            )
                        )
                        return
                    }
                } else {
                    if (packet.intPayload != pairingCodeInt) {
                        replySender(
                            InputPacket(
                                type = PacketType.REJECT,
                                extraByte = RejectReason.INVALID_CODE.code
                            )
                        )
                        return
                    }
                    if (clientId != 0 && trustedClientIds.add(clientId)) {
                        onTrustClientId?.invoke(clientId)
                    }
                }
                val assignedSlot = synchronized(slotLock) {
                    val existingIdx = slots.indexOfFirst {
                        it != null && (it.key == clientKey || (clientId != 0 && it.clientId == clientId))
                    }
                    if (existingIdx >= 0) {
                        val existing = slots[existingIdx]!!
                        slots[existingIdx] = existing.copy(
                            key = clientKey,
                            udpAddress = udpAddress ?: existing.udpAddress,
                            udpPort = if (udpPort != 0) udpPort else existing.udpPort,
                            deviceName = packet.deviceName.ifBlank { existing.deviceName },
                            lastSeenMs = now,
                            btSocket = btSocket ?: existing.btSocket,
                            clientId = if (clientId != 0) clientId else existing.clientId
                        )
                        existing.slot
                    } else {
                        val freeIdx = slots.indexOfFirst { it == null }
                        if (freeIdx < 0) {
                            -1
                        } else {
                            val newSlot = freeIdx + 1
                            slots[freeIdx] = ClientEndpoint(
                                slot = newSlot,
                                key = clientKey,
                                deviceName = packet.deviceName.ifBlank { "Player $newSlot" },
                                transport = transport,
                                udpAddress = udpAddress,
                                udpPort = udpPort,
                                btSocket = btSocket,
                                lastSeenMs = now,
                                clientId = clientId
                            )
                            newSlot
                        }
                    }
                }
                if (assignedSlot < 0) {
                    replySender(
                        InputPacket(
                            type = PacketType.REJECT,
                            extraByte = RejectReason.HOST_FULL.code
                        )
                    )
                } else {
                    replySender(
                        InputPacket(
                            type = PacketType.WELCOME,
                            slot = assignedSlot,
                            intPayload = pairingCodeInt
                        )
                    )
                    publishSlots()
                }
            }

            PacketType.INPUT -> {
                var prevMask = 0
                var matchedSlot = -1
                var stateChanged = false
                synchronized(slotLock) {
                    val idx = if (packet.slot in 1..LinkPadConstants.MAX_PLAYERS &&
                        slots[packet.slot - 1]?.key == clientKey
                    ) {
                        packet.slot - 1
                    } else {
                        slots.indexOfFirst { it?.key == clientKey }
                    }
                    if (idx >= 0) {
                        val current = slots[idx]!!
                        prevMask = current.buttonMask
                        matchedSlot = current.slot
                        stateChanged = current.buttonMask != packet.buttonMask ||
                            current.layout != packet.layout ||
                            current.lx != packet.lx ||
                            current.ly != packet.ly ||
                            current.rx != packet.rx ||
                            current.ry != packet.ry
                        slots[idx] = current.copy(
                            buttonMask = packet.buttonMask,
                            layout = packet.layout,
                            lx = packet.lx,
                            ly = packet.ly,
                            rx = packet.rx,
                            ry = packet.ry,
                            lastSeenMs = now
                        )
                    }
                }
                if (matchedSlot > 0) {
                    if (matchedSlot == 1) {
                        InputBridgeBus.updateP1AnalogAxes(
                            AnalogAxes(
                                lx = packet.lx,
                                ly = packet.ly,
                                rx = packet.rx,
                                ry = packet.ry
                            )
                        )
                    }
                    val keymap = slotKeymaps[matchedSlot] ?: KeymapProfile.GAMEPAD_STANDARD
                    InputBridgeBus.emitMaskChange(
                        slot = matchedSlot,
                        previousMask = prevMask,
                        newMask = packet.buttonMask,
                        keymapProfile = keymap
                    )
                    hardwareBridge.sendSlotState(
                        slot = matchedSlot,
                        buttonMask = packet.buttonMask,
                        lx = packet.lx,
                        ly = packet.ly,
                        rx = packet.rx,
                        ry = packet.ry
                    )
                    if (stateChanged) {
                        publishSlots()
                    }
                }
            }

            PacketType.PING -> {
                var rttUpdated = false
                synchronized(slotLock) {
                    val idx = if (packet.slot in 1..LinkPadConstants.MAX_PLAYERS &&
                        slots[packet.slot - 1]?.key == clientKey
                    ) {
                        packet.slot - 1
                    } else {
                        slots.indexOfFirst { it?.key == clientKey }
                    }
                    if (idx >= 0) {
                        val current = slots[idx]!!
                        val rtt = packet.extraByte.toInt() and 0xFF
                        slots[idx] = current.copy(
                            lastSeenMs = now,
                            rttMs = if (rtt > 0) rtt else current.rttMs
                        )
                        rttUpdated = true
                    }
                }
                replySender(
                    InputPacket(
                        type = PacketType.PONG,
                        slot = packet.slot,
                        intPayload = packet.intPayload
                    )
                )
                if (rttUpdated) {
                    publishSlots()
                }
            }

            PacketType.BYE -> {
                removeClientByKey(clientKey)
            }

            else -> Unit
        }
    }

    private fun removeClientByKey(clientKey: String) {
        val removedSlot = synchronized(slotLock) {
            val idx = slots.indexOfFirst { it?.key == clientKey }
            if (idx >= 0) {
                val s = slots[idx]?.slot ?: (idx + 1)
                slots[idx] = null
                s
            } else {
                null
            }
        }
        if (removedSlot != null) {
            hardwareBridge.destroySlot(removedSlot)
            publishSlots()
        }
    }

    private fun sendToEndpoint(endpoint: ClientEndpoint, packet: InputPacket) {
        when (endpoint.transport) {
            TransportType.WIFI_UDP -> {
                val addr = endpoint.udpAddress ?: return
                val sock = udpSocket ?: return
                val bytes = BinaryProtocol.encode(packet)
                runCatching {
                    sock.send(DatagramPacket(bytes, bytes.size, addr, endpoint.udpPort))
                }
            }
            TransportType.BLUETOOTH_RFCOMM -> {
                val out = endpoint.btSocket?.outputStream ?: return
                synchronized(out) {
                    runCatching { BinaryProtocol.writeStreamFrame(out, packet) }
                }
            }
            TransportType.BLUETOOTH_HID -> Unit
        }
    }

    private fun startBeaconBroadcaster() {
        beaconJob = scope.launch {
            val beaconSock = runCatching {
                DatagramSocket().apply { broadcast = true }
            }.getOrNull() ?: return@launch

            try {
                while (isActive && running) {
                    val activeCount = synchronized(slotLock) { slots.count { it != null } }
                    val beacon = InputPacket(
                        type = PacketType.BEACON,
                        extraByte = activeCount.toByte(),
                        intPayload = LinkPadConstants.HOST_UDP_PORT,
                        deviceName = hostDeviceName
                    )
                    val bytes = BinaryProtocol.encode(beacon)
                    for (addr in LinkPadConstants.findBroadcastAddresses()) {
                        runCatching {
                            beaconSock.send(
                                DatagramPacket(
                                    bytes,
                                    bytes.size,
                                    addr,
                                    LinkPadConstants.BEACON_UDP_PORT
                                )
                            )
                        }
                    }
                    delay(LinkPadConstants.BEACON_INTERVAL_MS)
                }
            } finally {
                runCatching { beaconSock.close() }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob = scope.launch {
            while (isActive && running) {
                delay(1_000L)
                val pps = packetCounter.getAndSet(0)
                InputBridgeBus.setPacketsPerSecond(pps)
                InputBridgeBus.setLocalIpAddress(LinkPadConstants.findPrimaryLocalIpv4(context))

                val now = System.currentTimeMillis()
                val evictedSlots = mutableListOf<Int>()
                synchronized(slotLock) {
                    for (i in slots.indices) {
                        val endpoint = slots[i] ?: continue
                        if (now - endpoint.lastSeenMs > LinkPadConstants.CLIENT_TIMEOUT_MS) {
                            runCatching { endpoint.btSocket?.close() }
                            evictedSlots.add(endpoint.slot)
                            slots[i] = null
                        }
                    }
                }
                if (evictedSlots.isNotEmpty()) {
                    evictedSlots.forEach { hardwareBridge.destroySlot(it) }
                    publishSlots()
                }
            }
        }
    }

    private fun publishSlots() {
        val snapshot = synchronized(slotLock) {
            (0 until LinkPadConstants.MAX_PLAYERS).map { idx ->
                val slotNum = idx + 1
                val endpoint = slots[idx]
                val keymap = slotKeymaps[slotNum] ?: KeymapProfile.GAMEPAD_STANDARD
                if (endpoint == null) {
                    PlayerSlotState(
                        slot = slotNum,
                        connected = false,
                        keymapProfile = keymap
                    )
                } else {
                    PlayerSlotState(
                        slot = slotNum,
                        connected = true,
                        deviceName = endpoint.deviceName,
                        transport = endpoint.transport,
                        rttMs = endpoint.rttMs,
                        buttonMask = endpoint.buttonMask,
                        layout = endpoint.layout,
                        lx = endpoint.lx,
                        ly = endpoint.ly,
                        rx = endpoint.rx,
                        ry = endpoint.ry,
                        keymapProfile = keymap,
                        lastSeenMs = endpoint.lastSeenMs
                    )
                }
            }
        }
        InputBridgeBus.updateSlots(snapshot)
    }

    private fun registerNsdService() {
        runCatching {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "${LinkPadConstants.NSD_SERVICE_PREFIX}$hostDeviceName"
                serviceType = LinkPadConstants.NSD_SERVICE_TYPE
                port = LinkPadConstants.HOST_UDP_PORT
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            nsdListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        val manager = nsdManager ?: return
        val listener = nsdListener ?: return
        runCatching { manager.unregisterService(listener) }
        nsdListener = null
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("LinkPadHostLock")?.apply {
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

    private fun generatePairingCode(): String =
        Random.nextInt(100_000, 1_000_000).toString()
}
