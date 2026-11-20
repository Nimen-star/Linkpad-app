package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.protocol.BinaryProtocol
import com.example.protocol.ControllerLayout
import com.example.protocol.DiscoveredHost
import com.example.protocol.InputPacket
import com.example.protocol.PacketType
import com.example.protocol.RejectReason
import com.example.protocol.TransportType
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

sealed class ClientConnectionState {
    data object Disconnected : ClientConnectionState()
    data class Connecting(val hostName: String) : ClientConnectionState()
    data class Connected(
        val hostName: String,
        val assignedSlot: Int,
        val transport: TransportType,
        val rttMs: Int
    ) : ClientConnectionState()
    data class NeedsPairingCode(val host: DiscoveredHost, val message: String) : ClientConnectionState()
    data class Error(val message: String) : ClientConnectionState()
}

class ControllerClientSession(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seq = AtomicInteger(0)

    private val _connectionState =
        MutableStateFlow<ClientConnectionState>(ClientConnectionState.Disconnected)
    val connectionState: StateFlow<ClientConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var activeSlot = 0
    @Volatile
    private var activeHostName = ""
    @Volatile
    private var activeTransport = TransportType.WIFI_UDP
    @Volatile
    private var lastRttMs = 0
    @Volatile
    private var currentMask = 0
    @Volatile
    private var currentLayout = ControllerLayout.RETRO_HANDHELD
    @Volatile
    private var currentLx: Byte = 0
    @Volatile
    private var currentLy: Byte = 0
    @Volatile
    private var currentRx: Byte = 0
    @Volatile
    private var currentRy: Byte = 0

    private var udpSocket: DatagramSocket? = null
    private var udpTargetAddress: InetAddress? = null
    private var udpTargetPort: Int = LinkPadConstants.HOST_UDP_PORT
    private var btSocket: BluetoothSocket? = null

    private var outgoingChannel = Channel<InputPacket>(capacity = 96)
    private var writerJob: Job? = null
    private var readerJob: Job? = null
    private var pingJob: Job? = null

    fun clearError() {
        val current = _connectionState.value
        if (current is ClientConnectionState.Error || current is ClientConnectionState.NeedsPairingCode) {
            _connectionState.value = ClientConnectionState.Disconnected
        }
    }

    suspend fun connect(
        host: DiscoveredHost,
        pairingCode: String,
        deviceName: String,
        clientId: Int = 0,
        trustedFavoriteMode: Boolean = false
    ): Boolean {
        disconnect()

        val codeInt = if (trustedFavoriteMode) {
            0
        } else {
            val parsed = pairingCode.trim().toIntOrNull()
            if (parsed == null || pairingCode.trim().length != 6) {
                _connectionState.value = ClientConnectionState.Error("Enter a valid 6-digit pairing code.")
                return false
            }
            parsed
        }

        _connectionState.value = ClientConnectionState.Connecting(host.name)
        activeHostName = host.name
        activeTransport = host.transport
        outgoingChannel = Channel(capacity = 96)

        return when (host.transport) {
            TransportType.WIFI_UDP -> connectUdp(host, codeInt, deviceName, clientId, trustedFavoriteMode)
            TransportType.BLUETOOTH_RFCOMM -> connectBluetooth(host, codeInt, deviceName, clientId, trustedFavoriteMode)
            TransportType.BLUETOOTH_HID -> false
        }
    }

    fun sendButtonMask(newMask: Int) {
        sendControllerState(
            buttonMask = newMask,
            layout = currentLayout,
            lx = currentLx,
            ly = currentLy,
            rx = currentRx,
            ry = currentRy
        )
    }

    fun sendControllerState(
        buttonMask: Int,
        layout: ControllerLayout,
        lx: Byte,
        ly: Byte,
        rx: Byte,
        ry: Byte
    ) {
        if (activeSlot !in 1..LinkPadConstants.MAX_PLAYERS) return
        if (buttonMask == currentMask &&
            layout == currentLayout &&
            lx == currentLx &&
            ly == currentLy &&
            rx == currentRx &&
            ry == currentRy
        ) {
            return
        }
        currentMask = buttonMask
        currentLayout = layout
        currentLx = lx
        currentLy = ly
        currentRx = rx
        currentRy = ry

        val packet = InputPacket(
            type = PacketType.INPUT,
            slot = activeSlot,
            sequence = seq.incrementAndGet() and 0xFF,
            buttonMask = buttonMask,
            layout = layout,
            intPayload = BinaryProtocol.currentTimestamp32(),
            lx = lx,
            ly = ly,
            rx = rx,
            ry = ry
        )
        outgoingChannel.trySend(packet)
    }

    fun disconnect() {
        val slot = activeSlot
        activeSlot = 0
        currentMask = 0
        currentLx = 0
        currentLy = 0
        currentRx = 0
        currentRy = 0

        if (slot in 1..LinkPadConstants.MAX_PLAYERS) {
            val bye = InputPacket(type = PacketType.BYE, slot = slot)
            runCatching {
                when (activeTransport) {
                    TransportType.WIFI_UDP -> {
                        val sock = udpSocket
                        val addr = udpTargetAddress
                        if (sock != null && addr != null && !sock.isClosed) {
                            val bytes = BinaryProtocol.encode(bye)
                            sock.send(DatagramPacket(bytes, bytes.size, addr, udpTargetPort))
                        }
                    }
                    TransportType.BLUETOOTH_RFCOMM -> {
                        btSocket?.outputStream?.let { out ->
                            synchronized(out) { BinaryProtocol.writeStreamFrame(out, bye) }
                        }
                    }
                    TransportType.BLUETOOTH_HID -> Unit
                }
            }
        }

        pingJob?.cancel()
        pingJob = null
        writerJob?.cancel()
        writerJob = null
        readerJob?.cancel()
        readerJob = null
        outgoingChannel.close()

        runCatching { udpSocket?.close() }
        udpSocket = null
        runCatching { btSocket?.close() }
        btSocket = null

        _connectionState.value = ClientConnectionState.Disconnected
    }

    private suspend fun connectUdp(
        host: DiscoveredHost,
        codeInt: Int,
        deviceName: String,
        clientId: Int,
        trustedFavoriteMode: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(host.hostAddress)
            val port = if (host.port > 0) host.port else LinkPadConstants.HOST_UDP_PORT
            val sock = DatagramSocket().apply {
                trafficClass = 0x10 // IPTOS_LOWDELAY
                soTimeout = 1_500
            }
            udpSocket = sock
            udpTargetAddress = addr
            udpTargetPort = port

            val hello = BinaryProtocol.helloWithClientId(
                codeInt = codeInt,
                deviceName = deviceName,
                clientId = clientId,
                trustedFavoriteMode = trustedFavoriteMode
            )
            val helloBytes = BinaryProtocol.encode(hello)
            val buf = ByteArray(BinaryProtocol.MAX_PACKET_SIZE + 16)
            val replyPacket = DatagramPacket(buf, buf.size)

            var response: InputPacket? = null
            for (attempt in 0 until 2) {
                sock.send(DatagramPacket(helloBytes, helloBytes.size, addr, port))
                response = runCatching {
                    sock.receive(replyPacket)
                    BinaryProtocol.decode(replyPacket.data, replyPacket.length)
                }.getOrNull()
                if (response != null) break
            }

            if (response == null) {
                sock.close()
                _connectionState.value =
                    ClientConnectionState.Error("Host did not respond. Check Wi-Fi and IP/Port.")
                return@withContext false
            }

            if (response.type == PacketType.REJECT) {
                sock.close()
                val reason = RejectReason.fromCode(response.extraByte)
                _connectionState.value = if (reason == RejectReason.TRUSTED_NOT_RECOGNIZED) {
                    ClientConnectionState.NeedsPairingCode(host, reason.message)
                } else {
                    ClientConnectionState.Error(reason.message)
                }
                return@withContext false
            }

            if (response.type != PacketType.WELCOME || response.slot !in 1..LinkPadConstants.MAX_PLAYERS) {
                sock.close()
                _connectionState.value = ClientConnectionState.Error("Unexpected handshake response.")
                return@withContext false
            }

            sock.soTimeout = 0
            activeSlot = response.slot
            lastRttMs = 1
            _connectionState.value = ClientConnectionState.Connected(
                hostName = host.name,
                assignedSlot = activeSlot,
                transport = TransportType.WIFI_UDP,
                rttMs = lastRttMs
            )

            startUdpWorkers(sock, addr, port)
            true
        } catch (e: Exception) {
            runCatching { udpSocket?.close() }
            udpSocket = null
            _connectionState.value =
                ClientConnectionState.Error(e.message ?: "Unable to connect via Wi-Fi UDP.")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBluetooth(
        host: DiscoveredHost,
        codeInt: Int,
        deviceName: String,
        clientId: Int,
        trustedFavoriteMode: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!LinkPadConstants.hasBluetoothConnectPermission(context)) {
            _connectionState.value =
                ClientConnectionState.Error("Bluetooth Connect permission is required.")
            return@withContext false
        }
        val btAddr = host.bluetoothAddress ?: host.hostAddress
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ClientConnectionState.Error("Enable Bluetooth to connect.")
            return@withContext false
        }

        try {
            runCatching { adapter.cancelDiscovery() }
            val device = adapter.getRemoteDevice(btAddr)
            val socket = device.createRfcommSocketToServiceRecord(LinkPadConstants.BT_SERVICE_UUID)
            btSocket = socket

            val connected = withTimeoutOrNull(4_500L) {
                socket.connect()
                true
            } ?: false

            if (!connected) {
                runCatching { socket.close() }
                _connectionState.value =
                    ClientConnectionState.Error("Bluetooth host unavailable. Ensure Bluetooth hosting is enabled.")
                return@withContext false
            }

            val out = socket.outputStream
            val input = socket.inputStream
            val hello = BinaryProtocol.helloWithClientId(
                codeInt = codeInt,
                deviceName = deviceName,
                clientId = clientId,
                trustedFavoriteMode = trustedFavoriteMode
            )
            BinaryProtocol.writeStreamFrame(out, hello)

            val response = withTimeoutOrNull(3_000L) {
                BinaryProtocol.readStreamFrame(input)
            }

            if (response == null) {
                runCatching { socket.close() }
                _connectionState.value = ClientConnectionState.Error("Bluetooth handshake timed out.")
                return@withContext false
            }

            if (response.type == PacketType.REJECT) {
                runCatching { socket.close() }
                val reason = RejectReason.fromCode(response.extraByte)
                _connectionState.value = if (reason == RejectReason.TRUSTED_NOT_RECOGNIZED) {
                    ClientConnectionState.NeedsPairingCode(host, reason.message)
                } else {
                    ClientConnectionState.Error(reason.message)
                }
                return@withContext false
            }

            if (response.type != PacketType.WELCOME || response.slot !in 1..LinkPadConstants.MAX_PLAYERS) {
                runCatching { socket.close() }
                _connectionState.value = ClientConnectionState.Error("Invalid Bluetooth host reply.")
                return@withContext false
            }

            activeSlot = response.slot
            lastRttMs = 4
            _connectionState.value = ClientConnectionState.Connected(
                hostName = host.name,
                assignedSlot = activeSlot,
                transport = TransportType.BLUETOOTH_RFCOMM,
                rttMs = lastRttMs
            )

            startBluetoothWorkers(socket)
            true
        } catch (_: SecurityException) {
            _connectionState.value = ClientConnectionState.Error("Missing Bluetooth permission.")
            false
        } catch (_: IOException) {
            runCatching { btSocket?.close() }
            btSocket = null
            _connectionState.value =
                ClientConnectionState.Error("Could not connect to Bluetooth host. Is Host Mode active?")
            false
        }
    }

    private fun startUdpWorkers(sock: DatagramSocket, addr: InetAddress, port: Int) {
        writerJob = scope.launch {
            for (packet in outgoingChannel) {
                val bytes = BinaryProtocol.encode(packet)
                runCatching {
                    sock.send(DatagramPacket(bytes, bytes.size, addr, port))
                }
            }
        }

        readerJob = scope.launch {
            val buf = ByteArray(BinaryProtocol.MAX_PACKET_SIZE + 16)
            val datagram = DatagramPacket(buf, buf.size)
            while (isActive && !sock.isClosed) {
                try {
                    datagram.length = buf.size
                    sock.receive(datagram)
                    val packet = BinaryProtocol.decode(datagram.data, datagram.length) ?: continue
                    handleHostReply(packet)
                } catch (_: IOException) {
                    break
                }
            }
        }

        startPingLoop()
    }

    private fun startBluetoothWorkers(socket: BluetoothSocket) {
        val out = socket.outputStream
        val input = socket.inputStream

        writerJob = scope.launch {
            for (packet in outgoingChannel) {
                synchronized(out) {
                    runCatching { BinaryProtocol.writeStreamFrame(out, packet) }
                }
            }
        }

        readerJob = scope.launch {
            while (isActive && socket.isConnected) {
                try {
                    val packet = BinaryProtocol.readStreamFrame(input) ?: continue
                    handleHostReply(packet)
                } catch (_: IOException) {
                    if (activeSlot != 0) {
                        activeSlot = 0
                        _connectionState.value =
                            ClientConnectionState.Error("Disconnected from Bluetooth host.")
                    }
                    break
                }
            }
        }

        startPingLoop()
    }

    private fun startPingLoop() {
        pingJob = scope.launch {
            while (isActive && activeSlot in 1..LinkPadConstants.MAX_PLAYERS) {
                val ping = InputPacket(
                    type = PacketType.PING,
                    slot = activeSlot,
                    extraByte = lastRttMs.coerceIn(0, 250).toByte(),
                    intPayload = BinaryProtocol.currentTimestamp32()
                )
                outgoingChannel.trySend(ping)
                delay(LinkPadConstants.PING_INTERVAL_MS)
            }
        }
    }

    private fun handleHostReply(packet: InputPacket) {
        when (packet.type) {
            PacketType.PONG -> {
                val now = BinaryProtocol.currentTimestamp32()
                val rtt = (now - packet.intPayload).coerceIn(1, 999)
                lastRttMs = rtt
                if (activeSlot in 1..LinkPadConstants.MAX_PLAYERS) {
                    _connectionState.value = ClientConnectionState.Connected(
                        hostName = activeHostName,
                        assignedSlot = activeSlot,
                        transport = activeTransport,
                        rttMs = rtt
                    )
                }
            }
            PacketType.BYE -> {
                activeSlot = 0
                _connectionState.value = ClientConnectionState.Error("Host ended the session.")
            }
            else -> Unit
        }
    }
}
