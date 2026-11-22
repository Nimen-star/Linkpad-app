package com.example.uhid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicInteger

data class UsbAdbDeviceInfo(
    val deviceId: Int,
    val productName: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean
)

/**
 * Pure Kotlin ADB USB wire frame codec (24-byte header + optional payload).
 * Unit-tested independently on the JVM without USB hardware.
 */
object AdbWireCodec {
    const val A_SYNC = 0x434E5953
    const val A_CNXN = 0x4E584E43
    const val A_OPEN = 0x4E45504F
    const val A_OKAY = 0x59414B4F
    const val A_CLSE = 0x45534C43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    const val VERSION = 0x01000000
    const val MAX_USB_PAYLOAD = 4096
    const val HEADER_SIZE = 24

    data class Frame(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray = ByteArray(0)
    )

    fun checksum(payload: ByteArray, length: Int = payload.size): Int {
        var sum = 0
        for (i in 0 until length) {
            sum += payload[i].toInt() and 0xFF
        }
        return sum
    }

    fun encodeHeader(
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray = ByteArray(0),
        length: Int = payload.size
    ): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(length)
        buf.putInt(checksum(payload, length))
        buf.putInt(command.inv())
        return buf.array()
    }

    data class ParsedHeader(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCheck: Int,
        val magic: Int
    ) {
        val isValidMagic: Boolean get() = magic == command.inv()
    }

    fun decodeHeader(bytes: ByteArray): ParsedHeader? {
        if (bytes.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.getInt()
        val a0 = buf.getInt()
        val a1 = buf.getInt()
        val len = buf.getInt()
        val chk = buf.getInt()
        val magic = buf.getInt()
        val parsed = ParsedHeader(cmd, a0, a1, len, chk, magic)
        return if (parsed.isValidMagic && len in 0..(1024 * 1024)) parsed else null
    }

    fun encodeSyncCommand(tag4: String, arg: Int): ByteArray {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(tag4.toByteArray(Charsets.US_ASCII), 0, 4)
        buf.putInt(arg)
        return buf.array()
    }
}

/**
 * Tracks attached USB OTG ADB devices using Android [UsbManager] (class 255, subclass 66, protocol 1).
 */
class UsbAdbTracker(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _targets = MutableStateFlow<List<UsbAdbDeviceInfo>>(emptyList())
    val targets: StateFlow<List<UsbAdbDeviceInfo>> = _targets.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        }
        refresh()
    }

    fun stop() {
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
    }

    fun refresh() {
        val mgr = usbManager ?: run {
            _targets.value = emptyList()
            return
        }
        val list = mgr.deviceList.values
            .filter { findAdbInterface(it) != null }
            .map { dev ->
                val rawName = listOfNotNull(dev.manufacturerName, dev.productName)
                    .joinToString(" ")
                    .trim()
                val label = rawName.ifBlank { "Android USB Device (${dev.deviceName})" }
                UsbAdbDeviceInfo(
                    deviceId = dev.deviceId,
                    productName = label,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    hasPermission = mgr.hasPermission(dev)
                )
            }
        _targets.value = list
    }

    fun requestPermission(deviceId: Int): Boolean {
        val mgr = usbManager ?: return false
        val device = mgr.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: return false
        if (mgr.hasPermission(device)) {
            refresh()
            return true
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(appContext, deviceId, intent, flags)
        mgr.requestPermission(device, pi)
        return true
    }

    fun openSession(deviceId: Int): UsbAdbSession? {
        val mgr = usbManager ?: return null
        val device = mgr.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: return null
        if (!mgr.hasPermission(device)) return null
        val adbInterface = findAdbInterface(device) ?: return null
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until adbInterface.endpointCount) {
            val ep = adbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
            }
        }
        val bulkIn = inEp ?: return null
        val bulkOut = outEp ?: return null
        val conn = mgr.openDevice(device) ?: return null
        if (!conn.claimInterface(adbInterface, true)) {
            runCatching { conn.close() }
            return null
        }
        return UsbAdbSession(appContext, conn, adbInterface, bulkIn, bulkOut)
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.uhid.USB_ADB_PERMISSION"

        fun findAdbInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == 255 && intf.interfaceSubclass == 66 && intf.interfaceProtocol == 1) {
                    return intf
                }
            }
            return null
        }
    }
}

/**
 * Self-contained ADB client over Android USB Host OTG endpoints.
 * Shares [AdbIdentityStore] RSA keys with Wi-Fi ADB so one USB trust approval carries over to Wi-Fi.
 */
class UsbAdbSession(
    context: Context,
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint
) : AutoCloseable {

    private val identity = AdbIdentityStore.loadOrCreate(context.applicationContext)
    private val nextLocalId = AtomicInteger(1)
    private var maxPayload = AdbWireCodec.MAX_USB_PAYLOAD

    fun connect(onWaitingTrustPrompt: () -> Unit): Boolean {
        val banner = "host::features=shell_v2,cmd,stat_v2\u0000".toByteArray(Charsets.UTF_8)
        if (!writeFrame(AdbWireCodec.A_CNXN, AdbWireCodec.VERSION, maxPayload, banner)) return false

        var sentSignature = false
        var sentPubKey = false
        val deadlineMs = System.currentTimeMillis() + 30_000L

        while (System.currentTimeMillis() < deadlineMs) {
            val frame = readFrame(timeoutMs = 5_000) ?: continue
            when (frame.command) {
                AdbWireCodec.A_CNXN -> {
                    if (frame.arg1 in 1024..65536) {
                        maxPayload = minOf(frame.arg1, 16384)
                    }
                    return true
                }
                AdbWireCodec.A_AUTH -> {
                    if (frame.arg0 == AdbWireCodec.AUTH_TOKEN) {
                        if (!sentSignature) {
                            sentSignature = true
                            val sig = AdbIdentityStore.signAdbToken(identity.privateKey, frame.payload)
                            if (!writeFrame(AdbWireCodec.A_AUTH, AdbWireCodec.AUTH_SIGNATURE, 0, sig)) {
                                return false
                            }
                        } else if (!sentPubKey) {
                            sentPubKey = true
                            onWaitingTrustPrompt()
                            val pub = AdbIdentityStore.encodeAdbPublicKey(
                                identity.certificate.publicKey as RSAPublicKey,
                                "linkpad@android"
                            )
                            if (!writeFrame(AdbWireCodec.A_AUTH, AdbWireCodec.AUTH_RSAPUBLICKEY, 0, pub)) {
                                return false
                            }
                        } else {
                            return false
                        }
                    }
                }
            }
        }
        return false
    }

    fun runShellCommand(command: String, timeoutMs: Int = 15_000): String {
        val localId = nextLocalId.getAndIncrement()
        val dest = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
        if (!writeFrame(AdbWireCodec.A_OPEN, localId, 0, dest)) {
            error("USB ADB OPEN failed")
        }

        val out = ByteArrayOutputStream()
        var remoteId = 0
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val frame = readFrame(timeoutMs = 3_000) ?: continue
            if (frame.arg1 != localId) {
                continue
            }
            when (frame.command) {
                AdbWireCodec.A_OKAY -> {
                    if (remoteId == 0) remoteId = frame.arg0
                }
                AdbWireCodec.A_WRTE -> {
                    if (remoteId == 0) remoteId = frame.arg0
                    out.write(frame.payload)
                    writeFrame(AdbWireCodec.A_OKAY, localId, remoteId)
                }
                AdbWireCodec.A_CLSE -> {
                    if (remoteId != 0) writeFrame(AdbWireCodec.A_CLSE, localId, remoteId)
                    break
                }
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Enables TCP/IP wireless ADB port 5555 via USB control command `tcpip:5555`.
     */
    fun enableTcpIp(port: Int = 5555): Boolean {
        val localId = nextLocalId.getAndIncrement()
        val dest = "tcpip:$port\u0000".toByteArray(Charsets.UTF_8)
        if (!writeFrame(AdbWireCodec.A_OPEN, localId, 0, dest)) return false

        val response = StringBuilder()
        var remoteId = 0
        val deadline = System.currentTimeMillis() + 6_000L
        while (System.currentTimeMillis() < deadline) {
            val frame = readFrame(timeoutMs = 2_000) ?: break
            if (frame.arg1 != localId) continue
            when (frame.command) {
                AdbWireCodec.A_OKAY -> {
                    if (remoteId == 0) remoteId = frame.arg0
                }
                AdbWireCodec.A_WRTE -> {
                    if (remoteId == 0) remoteId = frame.arg0
                    response.append(String(frame.payload, Charsets.UTF_8))
                    writeFrame(AdbWireCodec.A_OKAY, localId, remoteId)
                }
                AdbWireCodec.A_CLSE -> {
                    break
                }
            }
        }
        return response.toString().contains("restarting", ignoreCase = true) ||
            response.toString().contains(port.toString()) ||
            remoteId != 0
    }

    /**
     * Streams a local APK file cleanly over ADB `sync:` protocol to `/data/local/tmp/...`.
     */
    fun pushFileViaSync(
        localFile: File,
        remotePath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        val localId = nextLocalId.getAndIncrement()
        if (!writeFrame(AdbWireCodec.A_OPEN, localId, 0, "sync:\u0000".toByteArray(Charsets.UTF_8))) {
            return false
        }

        val remoteId = awaitStreamReady(localId) ?: return false

        val specBytes = "$remotePath,33188".toByteArray(Charsets.UTF_8)
        val sendHdr = AdbWireCodec.encodeSyncCommand("SEND", specBytes.size)
        if (!writeChunkWithOkay(localId, remoteId, sendHdr + specBytes)) return false

        val totalSize = localFile.length().coerceAtLeast(1L)
        val chunkLimit = minOf(maxPayload - 8, 3800)
        val readBuf = ByteArray(chunkLimit)
        var sentBytes = 0L

        FileInputStream(localFile).use { fis ->
            while (true) {
                val n = fis.read(readBuf)
                if (n <= 0) break
                val dataHdr = AdbWireCodec.encodeSyncCommand("DATA", n)
                val packet = ByteArray(8 + n)
                System.arraycopy(dataHdr, 0, packet, 0, 8)
                System.arraycopy(readBuf, 0, packet, 8, n)
                if (!writeChunkWithOkay(localId, remoteId, packet)) return false
                sentBytes += n
                onProgress((sentBytes.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f))
            }
        }

        val mtimeSec = (localFile.lastModified() / 1000L).toInt()
        val doneHdr = AdbWireCodec.encodeSyncCommand("DONE", mtimeSec)
        if (!writeChunkWithOkay(localId, remoteId, doneHdr)) return false

        // Wait for sync OKAY / FAIL response packet
        var syncOk = false
        val respFrame = readStreamDataOrClose(localId, remoteId)
        if (respFrame != null && respFrame.size >= 4) {
            val tag = String(respFrame, 0, 4, Charsets.US_ASCII)
            syncOk = tag == "OKAY"
        }

        val quitHdr = AdbWireCodec.encodeSyncCommand("QUIT", 0)
        runCatching { writeFrame(AdbWireCodec.A_WRTE, localId, remoteId, quitHdr) }
        runCatching { writeFrame(AdbWireCodec.A_CLSE, localId, remoteId) }
        return syncOk
    }

    private fun awaitStreamReady(localId: Int): Int? {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            val frame = readFrame(timeoutMs = 2_000) ?: continue
            if (frame.arg1 == localId && frame.command == AdbWireCodec.A_OKAY) {
                return frame.arg0
            }
            if (frame.arg1 == localId && frame.command == AdbWireCodec.A_CLSE) {
                return null
            }
        }
        return null
    }

    private fun writeChunkWithOkay(localId: Int, remoteId: Int, data: ByteArray): Boolean {
        if (!writeFrame(AdbWireCodec.A_WRTE, localId, remoteId, data)) return false
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            val frame = readFrame(timeoutMs = 3_000) ?: continue
            if (frame.arg1 == localId) {
                if (frame.command == AdbWireCodec.A_OKAY) return true
                if (frame.command == AdbWireCodec.A_CLSE) return false
                if (frame.command == AdbWireCodec.A_WRTE) {
                    writeFrame(AdbWireCodec.A_OKAY, localId, remoteId)
                }
            }
        }
        return false
    }

    private fun readStreamDataOrClose(localId: Int, remoteId: Int): ByteArray? {
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            val frame = readFrame(timeoutMs = 3_000) ?: continue
            if (frame.arg1 != localId) continue
            when (frame.command) {
                AdbWireCodec.A_WRTE -> {
                    writeFrame(AdbWireCodec.A_OKAY, localId, remoteId)
                    return frame.payload
                }
                AdbWireCodec.A_CLSE -> return null
            }
        }
        return null
    }

    private fun writeFrame(
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray = ByteArray(0)
    ): Boolean {
        val header = AdbWireCodec.encodeHeader(command, arg0, arg1, payload)
        if (!bulkWriteExact(header)) return false
        if (payload.isNotEmpty() && !bulkWriteExact(payload)) return false
        return true
    }

    private fun bulkWriteExact(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset < bytes.size) {
            val len = bytes.size - offset
            val slice = if (offset == 0) bytes else bytes.copyOfRange(offset, bytes.size)
            val written = connection.bulkTransfer(bulkOut, slice, len, 5_000)
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    private fun readFrame(timeoutMs: Int): AdbWireCodec.Frame? {
        val hdrBytes = bulkReadExact(AdbWireCodec.HEADER_SIZE, timeoutMs) ?: return null
        val hdr = AdbWireCodec.decodeHeader(hdrBytes) ?: return null
        val payload = if (hdr.dataLength > 0) {
            bulkReadExact(hdr.dataLength, timeoutMs) ?: return null
        } else {
            ByteArray(0)
        }
        return AdbWireCodec.Frame(hdr.command, hdr.arg0, hdr.arg1, payload)
    }

    private fun bulkReadExact(size: Int, timeoutMs: Int): ByteArray? {
        val out = ByteArray(size)
        val tmp = ByteArray(size)
        var received = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (received < size && System.currentTimeMillis() < deadline) {
            val remaining = size - received
            val n = connection.bulkTransfer(bulkIn, tmp, remaining, timeoutMs.coerceAtLeast(300))
            if (n > 0) {
                System.arraycopy(tmp, 0, out, received, n)
                received += n
            } else if (n < 0) {
                return null
            }
        }
        return if (received == size) out else null
    }

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }
}
