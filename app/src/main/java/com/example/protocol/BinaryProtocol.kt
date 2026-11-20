package com.example.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

enum class PacketType(val code: Byte) {
    HELLO(1),
    WELCOME(2),
    REJECT(3),
    INPUT(4),
    PING(5),
    PONG(6),
    BYE(7),
    BEACON(8);

    companion object {
        fun fromCode(code: Byte): PacketType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Compact 16-byte fixed binary protocol with optional trailing UTF-8 device name (max 24 bytes)
 * on HELLO and BEACON frames. Analog bytes [12..15] remain 0 in v1 Retro Handheld mode and are
 * reserved for future Dual-Joystick axes without changing the wire protocol.
 */
data class InputPacket(
    val type: PacketType,
    val slot: Int = 0,
    val sequence: Int = 0,
    val buttonMask: Int = 0,
    val layout: ControllerLayout = ControllerLayout.RETRO_HANDHELD,
    val extraByte: Byte = 0,
    val intPayload: Int = 0,
    val lx: Byte = 0,
    val ly: Byte = 0,
    val rx: Byte = 0,
    val ry: Byte = 0,
    val deviceName: String = ""
) {
    fun extractClientId32(): Int =
        ((lx.toInt() and 0xFF) shl 24) or
            ((ly.toInt() and 0xFF) shl 16) or
            ((rx.toInt() and 0xFF) shl 8) or
            (ry.toInt() and 0xFF)
}

object BinaryProtocol {
    const val MAGIC: Byte = 0x4C
    const val BASE_SIZE = 16
    const val MAX_NAME_BYTES = 24
    const val MAX_PACKET_SIZE = BASE_SIZE + MAX_NAME_BYTES
    const val HELLO_MODE_CODE: Byte = 0
    const val HELLO_MODE_TRUSTED_FAVORITE: Byte = 2

    fun helloWithClientId(
        codeInt: Int,
        deviceName: String,
        clientId: Int,
        trustedFavoriteMode: Boolean
    ): InputPacket = InputPacket(
        type = PacketType.HELLO,
        extraByte = if (trustedFavoriteMode) HELLO_MODE_TRUSTED_FAVORITE else HELLO_MODE_CODE,
        intPayload = if (trustedFavoriteMode) 0 else codeInt,
        lx = ((clientId ushr 24) and 0xFF).toByte(),
        ly = ((clientId ushr 16) and 0xFF).toByte(),
        rx = ((clientId ushr 8) and 0xFF).toByte(),
        ry = (clientId and 0xFF).toByte(),
        deviceName = deviceName
    )

    fun encode(packet: InputPacket): ByteArray {
        val nameBytes = if (packet.deviceName.isNotEmpty() &&
            (packet.type == PacketType.HELLO || packet.type == PacketType.BEACON)
        ) {
            val raw = packet.deviceName.toByteArray(StandardCharsets.UTF_8)
            if (raw.size > MAX_NAME_BYTES) raw.copyOf(MAX_NAME_BYTES) else raw
        } else {
            ByteArray(0)
        }

        val out = ByteArray(BASE_SIZE + nameBytes.size)
        out[0] = MAGIC
        out[1] = packet.type.code
        out[2] = (packet.slot and 0xFF).toByte()
        out[3] = (packet.sequence and 0xFF).toByte()
        out[4] = ((packet.buttonMask ushr 8) and 0xFF).toByte()
        out[5] = (packet.buttonMask and 0xFF).toByte()
        out[6] = packet.layout.wireId
        out[7] = packet.extraByte
        out[8] = ((packet.intPayload ushr 24) and 0xFF).toByte()
        out[9] = ((packet.intPayload ushr 16) and 0xFF).toByte()
        out[10] = ((packet.intPayload ushr 8) and 0xFF).toByte()
        out[11] = (packet.intPayload and 0xFF).toByte()
        out[12] = packet.lx
        out[13] = packet.ly
        out[14] = packet.rx
        out[15] = packet.ry
        if (nameBytes.isNotEmpty()) {
            System.arraycopy(nameBytes, 0, out, BASE_SIZE, nameBytes.size)
        }
        return out
    }

    fun decode(bytes: ByteArray, length: Int = bytes.size): InputPacket? {
        if (length < BASE_SIZE || bytes.size < length) return null
        if (bytes[0] != MAGIC) return null
        val type = PacketType.fromCode(bytes[1]) ?: return null
        val slot = bytes[2].toInt() and 0xFF
        val sequence = bytes[3].toInt() and 0xFF
        val buttonMask = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val layout = ControllerLayout.fromWireId(bytes[6])
        val extraByte = bytes[7]
        val intPayload = ((bytes[8].toInt() and 0xFF) shl 24) or
            ((bytes[9].toInt() and 0xFF) shl 16) or
            ((bytes[10].toInt() and 0xFF) shl 8) or
            (bytes[11].toInt() and 0xFF)
        val name = if (length > BASE_SIZE) {
            String(bytes, BASE_SIZE, length - BASE_SIZE, StandardCharsets.UTF_8)
                .trim { it <= ' ' || it == '\u0000' }
        } else {
            ""
        }

        return InputPacket(
            type = type,
            slot = slot,
            sequence = sequence,
            buttonMask = buttonMask,
            layout = layout,
            extraByte = extraByte,
            intPayload = intPayload,
            lx = bytes[12],
            ly = bytes[13],
            rx = bytes[14],
            ry = bytes[15],
            deviceName = name
        )
    }

    fun writeStreamFrame(output: OutputStream, packet: InputPacket) {
        val payload = encode(packet)
        val framed = ByteArray(payload.size + 1)
        framed[0] = payload.size.toByte()
        System.arraycopy(payload, 0, framed, 1, payload.size)
        output.write(framed)
        output.flush()
    }

    fun readStreamFrame(input: InputStream): InputPacket? {
        val dis = input as? DataInputStream ?: DataInputStream(input)
        val lenByte = input.read()
        if (lenByte < 0) throw EOFException("Stream closed")
        val len = lenByte and 0xFF
        if (len < BASE_SIZE || len > MAX_PACKET_SIZE) return null
        val buf = ByteArray(len)
        dis.readFully(buf)
        return decode(buf, len)
    }

    fun currentTimestamp32(): Int = (System.nanoTime() / 1_000_000L).toInt()
}
