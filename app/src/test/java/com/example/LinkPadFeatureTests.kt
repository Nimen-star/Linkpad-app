package com.example

import com.example.protocol.PadButton
import com.example.transport.BluetoothHidController
import com.example.ui.components.formatTapToRevealIpText
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPadFeatureTests {

    @Test
    fun hidDescriptor_declaresReportIdAndResetsPhysicalBounds() {
        val desc = BluetoothHidController.GAMEPAD_HID_DESCRIPTOR
        // Must declare Report ID (1): 0x85, 0x01
        val hasReportId = desc.indices.any { i ->
            i + 1 < desc.size && desc[i] == 0x85.toByte() && desc[i + 1] == 0x01.toByte()
        }
        assertTrue("HID descriptor must declare Report ID 1", hasReportId)

        // Must reset Physical Maximum (0x45, 0x00) after Hat Switch 315deg range
        val resetsPhysicalMax = desc.indices.any { i ->
            i + 1 < desc.size && desc[i] == 0x45.toByte() && desc[i + 1] == 0x00.toByte()
        }
        assertTrue("HID descriptor must reset Physical Maximum before axes", resetsPhysicalMax)
    }

    @Test
    fun encodeHidReportPayload_mapsNeutralButtonsHatAndSticksCorrectly() {
        val neutral = BluetoothHidController.encodeHidReportPayload(0, 0, 0, 0, 0)
        assertArrayEquals(BluetoothHidController.DEFAULT_REPORT_BYTES, neutral)

        // Verify Android/Linux BTN_GAMEPAD mapping (A=bit0, B=bit1, X=bit3, Y=bit4) + UP+RIGHT Hat=2
        val mask = PadButton.A.bit or PadButton.X.bit or PadButton.UP.bit or PadButton.RIGHT.bit
        val active = BluetoothHidController.encodeHidReportPayload(
            buttonMask = mask,
            lx = (-127).toByte(),
            ly = 127.toByte(),
            rx = 0,
            ry = 1
        )
        assertEquals(7, active.size)
        // A (1<<0) | X (1<<3) = 0b00001001 = 9
        assertEquals(9.toByte(), active[0])
        assertEquals(0.toByte(), active[1])
        assertEquals(2.toByte(), active[2])
        assertEquals(1.toByte(), active[3])
        assertEquals(255.toByte(), active[4])
        assertEquals(128.toByte(), active[5])
        assertEquals(129.toByte(), active[6])
    }

    @Test
    fun formatTapToRevealIpText_masksUnlessRevealed() {
        assertEquals(
            "•••.•••.•••.•••:47850",
            formatTapToRevealIpText("192.168.1.30", 47850, revealed = false)
        )
        assertEquals(
            "192.168.1.30:47850",
            formatTapToRevealIpText("192.168.1.30", 47850, revealed = true)
        )
        assertEquals(
            "•••.•••.•••.•••",
            formatTapToRevealIpText("192.168.1.30", null, revealed = false)
        )
        assertEquals(
            "192.168.1.30",
            formatTapToRevealIpText("192.168.1.30", null, revealed = true)
        )
    }
}
