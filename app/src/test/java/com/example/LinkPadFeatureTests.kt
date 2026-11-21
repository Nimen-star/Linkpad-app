package com.example

import com.example.data.UserSettings
import com.example.protocol.ControlOffset
import com.example.protocol.ControllerLayout
import com.example.protocol.GyroMode
import com.example.protocol.PadButton
import com.example.uhid.AdbIdentityStore
import com.example.uhid.UhidProtocol
import com.example.ui.components.GyroMotionMath
import com.example.ui.components.formatTapToRevealIpText
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.math.cos
import kotlin.math.sin

class LinkPadFeatureTests {

    @Test
    fun uhidDescriptor_declaresReportIdAndResetsPhysicalBounds() {
        val desc = UhidProtocol.GAMEPAD_HID_DESCRIPTOR
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
        val neutral = UhidProtocol.encodeHidReportPayload(0, 0, 0, 0, 0)
        assertArrayEquals(UhidProtocol.NEUTRAL_PAYLOAD, neutral)

        // Verify Android/Linux BTN_GAMEPAD mapping (A=bit0, B=bit1, X=bit3, Y=bit4) + UP+RIGHT Hat=2
        val mask = PadButton.A.bit or PadButton.X.bit or PadButton.UP.bit or PadButton.RIGHT.bit
        val active = UhidProtocol.encodeHidReportPayload(
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
    fun uhidBinaryEvents_packCreate2Input2AndPipeFramesAccurately() {
        val createEvent = UhidProtocol.buildUhidCreate2Event(
            name = "LinkPad Wireless Pad 1",
            phys = "linkpad/slot1",
            uniq = "lp-slot-1",
            vendorId = 0x1209,
            productId = 0x4C51,
            byteOrder = ByteOrder.LITTLE_ENDIAN
        )
        assertEquals(4380, createEvent.size)
        val buf = ByteBuffer.wrap(createEvent).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(UhidProtocol.UHID_CREATE2, buf.getInt(0))
        assertEquals(UhidProtocol.GAMEPAD_HID_DESCRIPTOR.size, buf.getShort(260).toInt() and 0xFFFF)
        assertEquals(0x1209, buf.getInt(264))
        assertEquals(0x4C51, buf.getInt(268))

        val payload = UhidProtocol.encodeHidReportPayload(PadButton.A.bit, 12, -34, 56, -78)
        val inputEvent = UhidProtocol.buildUhidInput2Event(payload, ByteOrder.LITTLE_ENDIAN)
        assertEquals(14, inputEvent.size)
        val inBuf = ByteBuffer.wrap(inputEvent).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(UhidProtocol.UHID_INPUT2, inBuf.getInt(0))
        assertEquals(8, inBuf.getShort(4).toInt())
        assertEquals(UhidProtocol.REPORT_ID, inputEvent[6])
        assertEquals(payload[0], inputEvent[7])

        val wire = UhidProtocol.encodePipeState(
            cmd = UhidProtocol.CMD_INPUT,
            slot = 3,
            seq = 42,
            buttonMask = PadButton.A.bit or PadButton.START.bit,
            lx = (-100).toByte(),
            ly = 100,
            rx = 50,
            ry = (-50).toByte()
        )
        val decoded = UhidProtocol.decodePipeState(wire)
        assertNotNull(decoded)
        assertEquals(3, decoded!!.slot)
        assertEquals(42, decoded.seq)
        assertEquals(PadButton.A.bit or PadButton.START.bit, decoded.buttonMask)
        assertEquals((-100).toByte(), decoded.lx)
        assertEquals(100.toByte(), decoded.ly)
        assertEquals(50.toByte(), decoded.rx)
        assertEquals((-50).toByte(), decoded.ry)
    }

    @Test
    fun adbIdentityStore_buildsValidSelfSignedX509Certificate() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val der = AdbIdentityStore.buildSelfSignedX509Der(kp.public, kp.private)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        cert.verify(kp.public)
        assertTrue(cert.subjectX500Principal.name.contains("LinkPad"))
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

    @Test
    fun gyroTiltHold_holdsFullSteerWithoutCenterDriftAndMatchesPitchSign() {
        // Steering clockwise right by 45 deg: Z rotation by -45 deg
        val theta = Math.toRadians(45.0).toFloat()
        val c = cos(theta)
        val s = sin(theta)
        val steerRightMatrix = floatArrayOf(
            c, s, 0f,
            -s, c, 0f,
            0f, 0f, 1f
        )
        val steerAxes = GyroMotionMath.computeTiltHoldAxes(
            rel = steerRightMatrix,
            gyroMode = GyroMode.STEER_LEFT_STICK,
            sensitivity = 1.0f,
            invertY = false
        )
        assertTrue("Expected sustained positive X steer, got ${steerAxes.lx}", steerAxes.lx > 90)
        assertEquals(0.toByte(), steerAxes.ly)

        // Tilting top edge toward user (+X rotation by +30 deg) -> UP (negative Y)
        val pitchRad = Math.toRadians(30.0).toFloat()
        val pc = cos(pitchRad)
        val ps = sin(pitchRad)
        val tiltTowardUser = floatArrayOf(
            1f, 0f, 0f,
            0f, pc, -ps,
            0f, ps, pc
        )
        val aimToward = GyroMotionMath.computeTiltHoldAxes(
            rel = tiltTowardUser,
            gyroMode = GyroMode.AIM_RIGHT_STICK,
            sensitivity = 1.0f,
            invertY = false
        )
        assertTrue("Tilting toward user should aim UP (negative Y), got ${aimToward.ry}", aimToward.ry < -50)

        // With invertY = true, sign must flip positive
        val aimInverted = GyroMotionMath.computeTiltHoldAxes(
            rel = tiltTowardUser,
            gyroMode = GyroMode.AIM_RIGHT_STICK,
            sensitivity = 1.0f,
            invertY = true
        )
        assertTrue("Inverted pitch should be positive Y, got ${aimInverted.ry}", aimInverted.ry > 50)

        // Relative orientation composition: ref rotated + current = ref * pitch must still yield same tilt
        val ref = floatArrayOf(
            0f, -1f, 0f,
            1f, 0f, 0f,
            0f, 0f, 1f
        )
        val current = FloatArray(9)
        for (r in 0..2) {
            for (col in 0..2) {
                current[r * 3 + col] =
                    ref[r * 3 + 0] * tiltTowardUser[0 * 3 + col] +
                    ref[r * 3 + 1] * tiltTowardUser[1 * 3 + col] +
                    ref[r * 3 + 2] * tiltTowardUser[2 * 3 + col]
            }
        }
        val rel = GyroMotionMath.relativeRotationMatrix(ref, current)
        val composedAxes = GyroMotionMath.computeTiltHoldAxes(
            rel = rel,
            gyroMode = GyroMode.AIM_RIGHT_STICK,
            sensitivity = 1.0f,
            invertY = false
        )
        assertEquals(aimToward.ry, composedAxes.ry)
    }

    @Test
    fun gyroRateAim_respectsDeadzoneAndPitchDirection() {
        val dead = GyroMotionMath.computeRateAimAxes(
            scrGx = 0.02f,
            scrGy = 0.01f,
            scrGz = -0.02f,
            gyroMode = GyroMode.AIM_RIGHT_STICK,
            sensitivity = 1.25f,
            invertY = false
        )
        assertEquals(0.toByte(), dead.rx)
        assertEquals(0.toByte(), dead.ry)

        val upwardTurn = GyroMotionMath.computeRateAimAxes(
            scrGx = 1.2f,
            scrGy = -1.0f,
            scrGz = 0f,
            gyroMode = GyroMode.AIM_RIGHT_STICK,
            sensitivity = 1.0f,
            invertY = false
        )
        assertTrue("Positive X angular velocity (toward user) aims UP (<0)", upwardTurn.ry < -25)
        assertTrue("Negative Y angular velocity (pan right) aims RIGHT (>0)", upwardTurn.rx > 25)
    }

    @Test
    fun userSettings_keepsCustomizationsIsolatedPerLayout() {
        val base = UserSettings()
        val updated = base.copy(
            customizationsByLayout = base.customizationsByLayout + (
                ControllerLayout.RETRO_HANDHELD to base.customizationFor(ControllerLayout.RETRO_HANDHELD).copy(
                    scale = 1.35f,
                    dpadOffset = ControlOffset(x = 24f, y = -12f)
                )
            )
        )
        assertEquals(1.35f, updated.customizationFor(ControllerLayout.RETRO_HANDHELD).scale, 0.001f)
        assertEquals(24f, updated.customizationFor(ControllerLayout.RETRO_HANDHELD).dpadOffset.x, 0.001f)
        assertEquals(1.0f, updated.customizationFor(ControllerLayout.DUAL_STICK).scale, 0.001f)
        assertEquals(0f, updated.customizationFor(ControllerLayout.DUAL_STICK).dpadOffset.x, 0.001f)
    }
}
