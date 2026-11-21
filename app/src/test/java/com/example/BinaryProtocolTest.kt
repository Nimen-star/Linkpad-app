package com.example

import android.view.KeyEvent
import com.example.protocol.BinaryProtocol
import com.example.protocol.ControllerLayout
import com.example.protocol.InputPacket
import com.example.protocol.KeymapProfile
import com.example.protocol.PacketType
import com.example.protocol.PadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BinaryProtocolTest {

    @Test
    fun encodeDecodeInputPacket_preservesAllFieldsAndExactSize() {
        val mask = PadButton.UP.bit or PadButton.RIGHT.bit or PadButton.A.bit or PadButton.L.bit
        val original = InputPacket(
            type = PacketType.INPUT,
            slot = 3,
            sequence = 219,
            buttonMask = mask,
            layout = ControllerLayout.RETRO_HANDHELD,
            extraByte = 0,
            intPayload = 987654
        )
        val bytes = BinaryProtocol.encode(original)
        assertEquals(BinaryProtocol.BASE_SIZE, bytes.size)

        val decoded = BinaryProtocol.decode(bytes)
        assertNotNull(decoded)
        assertEquals(PacketType.INPUT, decoded?.type)
        assertEquals(3, decoded?.slot)
        assertEquals(219, decoded?.sequence)
        assertEquals(mask, decoded?.buttonMask)
        assertEquals(ControllerLayout.RETRO_HANDHELD, decoded?.layout)
        assertEquals(987654, decoded?.intPayload)
    }

    @Test
    fun streamFraming_roundTripsHelloWithDeviceName() {
        val hello = InputPacket(
            type = PacketType.HELLO,
            intPayload = 482915,
            deviceName = "Pixel 9 Pro"
        )
        val baos = ByteArrayOutputStream()
        BinaryProtocol.writeStreamFrame(baos, hello)

        val decoded = BinaryProtocol.readStreamFrame(ByteArrayInputStream(baos.toByteArray()))
        assertNotNull(decoded)
        assertEquals(PacketType.HELLO, decoded?.type)
        assertEquals(482915, decoded?.intPayload)
        assertEquals("Pixel 9 Pro", decoded?.deviceName)
    }

    @Test
    fun decodeInvalidMagic_returnsNull() {
        val bad = ByteArray(BinaryProtocol.BASE_SIZE) { 0x11 }
        assertNull(BinaryProtocol.decode(bad))
    }

    @Test
    fun keymapProfiles_mapStandardAndPlayerKeyboardKeys() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            KeymapProfile.GAMEPAD_STANDARD.resolveKeyCode(PadButton.A)
        )
        assertEquals(
            KeyEvent.KEYCODE_W,
            KeymapProfile.KEYBOARD_P1_WASD.resolveKeyCode(PadButton.UP)
        )
        assertEquals(
            KeyEvent.KEYCODE_NUMPAD_8,
            KeymapProfile.KEYBOARD_P4_NUMPAD.resolveKeyCode(PadButton.UP)
        )
    }

    @Test
    fun helloWithClientId_packsAndExtractsSignedAndUnsigned32BitIds() {
        val sampleId = -0x34A912EF
        val pkt = BinaryProtocol.helloWithClientId(
            codeInt = 123456,
            deviceName = "Pixel 9",
            clientId = sampleId,
            trustedFavoriteMode = true
        )
        val encoded = BinaryProtocol.encode(pkt)
        val decoded = BinaryProtocol.decode(encoded)
        assertNotNull(decoded)
        assertEquals(BinaryProtocol.HELLO_MODE_TRUSTED_FAVORITE, decoded?.extraByte)
        assertEquals(0, decoded?.intPayload)
        assertEquals(sampleId, decoded?.extractClientId32())

        val codePkt = BinaryProtocol.helloWithClientId(
            codeInt = 654321,
            deviceName = "Pixel 9",
            clientId = 0x1A2B3C4D,
            trustedFavoriteMode = false
        )
        val decodedCode = BinaryProtocol.decode(BinaryProtocol.encode(codePkt))
        assertNotNull(decodedCode)
        assertEquals(BinaryProtocol.HELLO_MODE_CODE, decodedCode?.extraByte)
        assertEquals(654321, decodedCode?.intPayload)
        assertEquals(0x1A2B3C4D, decodedCode?.extractClientId32())
    }

    @Test
    fun dualStickAndExtendedButtons_roundTripAccurately() {
        val mask = PadButton.L2.bit or PadButton.R2.bit or PadButton.L3.bit or PadButton.R3.bit or PadButton.A.bit
        val original = InputPacket(
            type = PacketType.INPUT,
            slot = 1,
            sequence = 77,
            buttonMask = mask,
            layout = ControllerLayout.DUAL_STICK,
            lx = (-120).toByte(),
            ly = 95.toByte(),
            rx = 127.toByte(),
            ry = (-127).toByte()
        )
        val encoded = BinaryProtocol.encode(original)
        val decoded = BinaryProtocol.decode(encoded)
        assertNotNull(decoded)
        assertEquals(ControllerLayout.DUAL_STICK, decoded?.layout)
        assertEquals(mask, decoded?.buttonMask)
        assertEquals((-120).toByte(), decoded?.lx)
        assertEquals(95.toByte(), decoded?.ly)
        assertEquals(127.toByte(), decoded?.rx)
        assertEquals((-127).toByte(), decoded?.ry)
    }

    @Test
    fun newControllerLayouts_mapWireIdsAndRoundTripAccurately() {
        val layouts = listOf(
            ControllerLayout.RETRO_HANDHELD to 0.toByte(),
            ControllerLayout.DUAL_STICK to 1.toByte(),
            ControllerLayout.RACING_WHEEL to 2.toByte(),
            ControllerLayout.PRO_DUAL_STICK to 3.toByte(),
            ControllerLayout.ARCADE_FIGHT_PAD to 4.toByte()
        )
        for ((layout, wireId) in layouts) {
            assertEquals(wireId, layout.wireId)
            assertEquals(layout, ControllerLayout.fromWireId(wireId))

            val pkt = InputPacket(
                type = PacketType.INPUT,
                slot = 2,
                sequence = 42,
                buttonMask = PadButton.A.bit or PadButton.L2.bit or PadButton.R2.bit,
                layout = layout,
                lx = (-64).toByte(),
                ly = 0,
                rx = 0,
                ry = 32
            )
            val decoded = BinaryProtocol.decode(BinaryProtocol.encode(pkt))
            assertNotNull(decoded)
            assertEquals(layout, decoded?.layout)
            assertEquals((-64).toByte(), decoded?.lx)
            assertEquals(32.toByte(), decoded?.ry)
        }
        assertEquals(ControllerLayout.RETRO_HANDHELD, ControllerLayout.fromWireId(99.toByte()))
    }
}
