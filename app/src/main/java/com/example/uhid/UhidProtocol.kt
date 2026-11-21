package com.example.uhid

import com.example.protocol.PadButton
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class UhidBackendType(val label: String) {
    NONE("Inactive"),
    KERNEL_UHID("Kernel UHID"),
    UINPUT_CLI("AOSP uinput"),
    INPUT_MANAGER("InputManager")
}

data class PipeSlotPacket(
    val cmd: Byte,
    val slot: Int,
    val seq: Int,
    val buttonMask: Int,
    val lx: Byte,
    val ly: Byte,
    val rx: Byte,
    val ry: Byte
)

object UhidProtocol {
    const val MAGIC_0: Byte = 'L'.code.toByte()
    const val MAGIC_1: Byte = 'U'.code.toByte()

    const val CMD_INPUT: Byte = 1
    const val CMD_DESTROY_SLOT: Byte = 2
    const val CMD_PING: Byte = 3
    const val CMD_STOP_AGENT: Byte = 4
    const val CMD_PONG: Byte = 5

    const val PIPE_PACKET_SIZE = 12

    // Linux include/uapi/linux/uhid.h constants
    const val UHID_DESTROY = 1
    const val UHID_START = 2
    const val UHID_OPEN = 4
    const val UHID_CREATE2 = 11
    const val UHID_INPUT2 = 12
    const val BUS_USB: Short = 0x03
    const val UHID_EVENT_SIZE = 4380

    const val REPORT_ID: Byte = 0x01
    val NEUTRAL_PAYLOAD = byteArrayOf(0, 0, 0, 128.toByte(), 128.toByte(), 128.toByte(), 128.toByte())

    fun encodePipeState(
        cmd: Byte,
        slot: Int,
        seq: Int = 0,
        buttonMask: Int = 0,
        lx: Byte = 0,
        ly: Byte = 0,
        rx: Byte = 0,
        ry: Byte = 0
    ): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        cmd,
        slot.coerceIn(0, 255).toByte(),
        (seq and 0xFF).toByte(),
        ((buttonMask ushr 8) and 0xFF).toByte(),
        (buttonMask and 0xFF).toByte(),
        lx,
        ly,
        rx,
        ry,
        0
    )

    fun decodePipeState(data: ByteArray, length: Int = data.size): PipeSlotPacket? {
        if (length < PIPE_PACKET_SIZE) return null
        if (data[0] != MAGIC_0 || data[1] != MAGIC_1) return null
        val mask = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
        return PipeSlotPacket(
            cmd = data[2],
            slot = data[3].toInt() and 0xFF,
            seq = data[4].toInt() and 0xFF,
            buttonMask = mask,
            lx = data[7],
            ly = data[8],
            rx = data[9],
            ry = data[10]
        )
    }

    fun encodePipePong(backend: UhidBackendType, activeMask: Int): ByteArray = byteArrayOf(
        MAGIC_0,
        MAGIC_1,
        CMD_PONG,
        backend.ordinal.toByte(),
        (activeMask and 0xFF).toByte(),
        0, 0, 0, 0, 0, 0, 0
    )

    fun decodePipePong(data: ByteArray, length: Int = data.size): Pair<UhidBackendType, Int>? {
        if (length < PIPE_PACKET_SIZE) return null
        if (data[0] != MAGIC_0 || data[1] != MAGIC_1 || data[2] != CMD_PONG) return null
        val backendOrdinal = data[3].toInt() and 0xFF
        val backend = UhidBackendType.entries.getOrNull(backendOrdinal) ?: UhidBackendType.NONE
        val activeMask = data[4].toInt() and 0xFF
        return backend to activeMask
    }

    /**
     * Maps LinkPad buttons & analog axes into the 7-byte HID Gamepad report body (after Report ID).
     * Standard Android/Linux BTN_GAMEPAD usage bits: A=0, B=1, X=3, Y=4, L1=6, R1=7, L2=8, R2=9, SELECT=10, START=11, L3=13, R3=14.
     */
    fun encodeHidReportPayload(
        buttonMask: Int,
        lx: Byte,
        ly: Byte,
        rx: Byte,
        ry: Byte
    ): ByteArray {
        var hidButtons = 0
        if ((buttonMask and PadButton.A.bit) != 0) hidButtons = hidButtons or (1 shl 0)
        if ((buttonMask and PadButton.B.bit) != 0) hidButtons = hidButtons or (1 shl 1)
        if ((buttonMask and PadButton.X.bit) != 0) hidButtons = hidButtons or (1 shl 3)
        if ((buttonMask and PadButton.Y.bit) != 0) hidButtons = hidButtons or (1 shl 4)
        if ((buttonMask and PadButton.L.bit) != 0) hidButtons = hidButtons or (1 shl 6)
        if ((buttonMask and PadButton.R.bit) != 0) hidButtons = hidButtons or (1 shl 7)
        if ((buttonMask and PadButton.L2.bit) != 0) hidButtons = hidButtons or (1 shl 8)
        if ((buttonMask and PadButton.R2.bit) != 0) hidButtons = hidButtons or (1 shl 9)
        if ((buttonMask and PadButton.SELECT.bit) != 0) hidButtons = hidButtons or (1 shl 10)
        if ((buttonMask and PadButton.START.bit) != 0) hidButtons = hidButtons or (1 shl 11)
        if ((buttonMask and PadButton.L3.bit) != 0) hidButtons = hidButtons or (1 shl 13)
        if ((buttonMask and PadButton.R3.bit) != 0) hidButtons = hidButtons or (1 shl 14)

        val hat = dpadMaskToHat(buttonMask)
        return byteArrayOf(
            (hidButtons and 0xFF).toByte(),
            ((hidButtons ushr 8) and 0xFF).toByte(),
            hat.toByte(),
            (lx.toInt() + 128).coerceIn(0, 255).toByte(),
            (ly.toInt() + 128).coerceIn(0, 255).toByte(),
            (rx.toInt() + 128).coerceIn(0, 255).toByte(),
            (ry.toInt() + 128).coerceIn(0, 255).toByte()
        )
    }

    private fun dpadMaskToHat(mask: Int): Int {
        val up = (mask and PadButton.UP.bit) != 0
        val down = (mask and PadButton.DOWN.bit) != 0
        val left = (mask and PadButton.LEFT.bit) != 0
        val right = (mask and PadButton.RIGHT.bit) != 0
        return when {
            up && right -> 2
            down && right -> 4
            down && left -> 6
            up && left -> 8
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }
    }

    fun buildUhidCreate2Event(
        name: String,
        phys: String,
        uniq: String,
        vendorId: Int,
        productId: Int,
        descriptor: ByteArray = GAMEPAD_HID_DESCRIPTOR,
        byteOrder: ByteOrder = ByteOrder.nativeOrder()
    ): ByteArray {
        val out = ByteArray(UHID_EVENT_SIZE)
        val buf = ByteBuffer.wrap(out).order(byteOrder)
        buf.putInt(0, UHID_CREATE2)

        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, out, 4, minOf(nameBytes.size, 127))

        val physBytes = phys.toByteArray(Charsets.UTF_8)
        System.arraycopy(physBytes, 0, out, 132, minOf(physBytes.size, 63))

        val uniqBytes = uniq.toByteArray(Charsets.UTF_8)
        System.arraycopy(uniqBytes, 0, out, 196, minOf(uniqBytes.size, 63))

        val rdSize = minOf(descriptor.size, 4096)
        buf.putShort(260, rdSize.toShort())
        buf.putShort(262, BUS_USB)
        buf.putInt(264, vendorId)
        buf.putInt(268, productId)
        buf.putInt(272, 1) // version
        buf.putInt(276, 0) // country
        System.arraycopy(descriptor, 0, out, 280, rdSize)
        return out
    }

    /**
     * Prepends Report ID (1) required by /dev/uhid for descriptors declaring Report ID (1).
     */
    fun buildUhidInput2Event(
        payload7Bytes: ByteArray,
        byteOrder: ByteOrder = ByteOrder.nativeOrder()
    ): ByteArray {
        val reportLen = 1 + payload7Bytes.size
        val out = ByteArray(4 + 2 + reportLen)
        val buf = ByteBuffer.wrap(out).order(byteOrder)
        buf.putInt(0, UHID_INPUT2)
        buf.putShort(4, reportLen.toShort())
        out[6] = REPORT_ID
        System.arraycopy(payload7Bytes, 0, out, 7, payload7Bytes.size)
        return out
    }

    fun buildUhidDestroyEvent(byteOrder: ByteOrder = ByteOrder.nativeOrder()): ByteArray {
        val out = ByteArray(4)
        ByteBuffer.wrap(out).order(byteOrder).putInt(0, UHID_DESTROY)
        return out
    }

    // Standards-compliant 7-byte HID Gamepad Report Descriptor with explicit Report ID (1).
    val GAMEPAD_HID_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(), // Usage (Gamepad)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x01.toByte(), //   Report ID (1)

        // 16 Buttons (2 bytes)
        0x05.toByte(), 0x09.toByte(), //   Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), //   Usage Minimum (Button 1)
        0x29.toByte(), 0x10.toByte(), //   Usage Maximum (Button 16)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x95.toByte(), 0x10.toByte(), //   Report Count (16)
        0x81.toByte(), 0x02.toByte(), //   Input (Data, Var, Abs)

        // Hat Switch (4 bits) + 4-bit padding (1 byte)
        0x05.toByte(), 0x01.toByte(), //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x39.toByte(), //   Usage (Hat switch)
        0x15.toByte(), 0x01.toByte(), //   Logical Minimum (1)
        0x25.toByte(), 0x08.toByte(), //   Logical Maximum (8)
        0x35.toByte(), 0x00.toByte(), //   Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
        0x65.toByte(), 0x14.toByte(), //   Unit (Eng Rot: Angular Pos)
        0x75.toByte(), 0x04.toByte(), //   Report Size (4)
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x81.toByte(), 0x42.toByte(), //   Input (Data, Var, Abs, Null)
        0x65.toByte(), 0x00.toByte(), //   Unit (None)
        0x35.toByte(), 0x00.toByte(), //   Physical Minimum (0 -> reset)
        0x45.toByte(), 0x00.toByte(), //   Physical Maximum (0 -> reset)
        0x75.toByte(), 0x04.toByte(), //   Report Size (4)
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x81.toByte(), 0x03.toByte(), //   Input (Const, Var, Abs)

        // 4 Analog Axes inside Physical Collection: LX (X), LY (Y), RX (Z), RY (Rz) (4 bytes)
        0x05.toByte(), 0x01.toByte(), //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
        0x09.toByte(), 0x30.toByte(), //     Usage (X)
        0x09.toByte(), 0x31.toByte(), //     Usage (Y)
        0x09.toByte(), 0x32.toByte(), //     Usage (Z)
        0x09.toByte(), 0x35.toByte(), //     Usage (Rz)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8)
        0x95.toByte(), 0x04.toByte(), //     Report Count (4)
        0x81.toByte(), 0x02.toByte(), //     Input (Data, Var, Abs)
        0xC0.toByte(),                //   End Collection (Physical)

        0xC0.toByte()                 // End Collection (Application)
    )
}
