package com.example.protocol

import android.view.KeyEvent

enum class PadButton(val shortLabel: String) {
    UP("UP"),
    DOWN("DOWN"),
    LEFT("LEFT"),
    RIGHT("RIGHT"),
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    L("L"),
    R("R"),
    START("START"),
    SELECT("SELECT"),
    L2("L2"),
    R2("R2"),
    L3("L3"),
    R3("R3");

    val bit: Int get() = 1 shl ordinal
}

enum class TransportType(val badge: String) {
    WIFI_UDP("UDP"),
    BLUETOOTH_RFCOMM("BT"),
    BLUETOOTH_HID("HID")
}

enum class ControllerLayout(val wireId: Byte, val label: String) {
    RETRO_HANDHELD(0, "Retro"),
    DUAL_STICK(1, "Dual Stick"),
    RACING_WHEEL(2, "Wheel"),
    PRO_DUAL_STICK(3, "Pro Paddles"),
    ARCADE_FIGHT_PAD(4, "Fight Pad");

    companion object {
        fun fromWireId(id: Byte): ControllerLayout =
            entries.firstOrNull { it.wireId == id } ?: RETRO_HANDHELD
    }
}

enum class ControllerTheme(val label: String) {
    LIQUID_GLASS("Liquid Glass"),
    XBOX_NEON("Xbox Dark"),
    DUAL_SENSE("DualSense"),
    SWITCH_NEON("Switch"),
    RETRO_SNES("Retro SNES")
}

enum class HapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Light"),
    CRISP("Crisp"),
    STRONG("Strong")
}

enum class GyroMode(val label: String) {
    OFF("Off"),
    STEER_LEFT_STICK("Steer (L-Stick)"),
    AIM_RIGHT_STICK("Aim (R-Stick)")
}

enum class GyroResponseMode(val label: String) {
    TILT_HOLD("Tilt Hold"),
    RATE_AIM("Rate Aim")
}

data class ProPaddleConfig(
    val m1: PadButton = PadButton.A,
    val m2: PadButton = PadButton.B,
    val m3: PadButton = PadButton.L3,
    val m4: PadButton = PadButton.R3
) {
    fun buttonFor(index: Int): PadButton = when (index) {
        1 -> m1
        2 -> m2
        3 -> m3
        else -> m4
    }

    fun withPaddle(index: Int, button: PadButton): ProPaddleConfig = when (index) {
        1 -> copy(m1 = button)
        2 -> copy(m2 = button)
        3 -> copy(m3 = button)
        else -> copy(m4 = button)
    }

    companion object {
        val REMAPPABLE_CHOICES = listOf(
            PadButton.A,
            PadButton.B,
            PadButton.X,
            PadButton.Y,
            PadButton.L,
            PadButton.R,
            PadButton.L2,
            PadButton.R2,
            PadButton.L3,
            PadButton.R3
        )
    }
}

data class ControlOffset(
    val x: Float = 0f,
    val y: Float = 0f
)

data class ControlCustomization(
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val dpadOffset: ControlOffset = ControlOffset(),
    val leftStickOffset: ControlOffset = ControlOffset(),
    val rightStickOffset: ControlOffset = ControlOffset(),
    val abxyOffset: ControlOffset = ControlOffset(),
    val leftShoulderOffset: ControlOffset = ControlOffset(),
    val rightShoulderOffset: ControlOffset = ControlOffset(),
    val menuOffset: ControlOffset = ControlOffset(),
    val paddlesOffset: ControlOffset = ControlOffset()
) {
    fun resetPositions(): ControlCustomization = copy(
        dpadOffset = ControlOffset(),
        leftStickOffset = ControlOffset(),
        rightStickOffset = ControlOffset(),
        abxyOffset = ControlOffset(),
        leftShoulderOffset = ControlOffset(),
        rightShoulderOffset = ControlOffset(),
        menuOffset = ControlOffset(),
        paddlesOffset = ControlOffset()
    )
}

enum class KeymapProfile(val displayName: String) {
    GAMEPAD_STANDARD("Gamepad"),
    KEYBOARD_P1_WASD("Keys WASD"),
    KEYBOARD_P2_ARROWS("Keys Arrows"),
    KEYBOARD_P3_IJKL("Keys IJKL"),
    KEYBOARD_P4_NUMPAD("Keys Numpad");

    fun resolveKeyCode(button: PadButton): Int = when (this) {
        GAMEPAD_STANDARD -> when (button) {
            PadButton.UP -> KeyEvent.KEYCODE_DPAD_UP
            PadButton.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            PadButton.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            PadButton.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            PadButton.A -> KeyEvent.KEYCODE_BUTTON_A
            PadButton.B -> KeyEvent.KEYCODE_BUTTON_B
            PadButton.X -> KeyEvent.KEYCODE_BUTTON_X
            PadButton.Y -> KeyEvent.KEYCODE_BUTTON_Y
            PadButton.L -> KeyEvent.KEYCODE_BUTTON_L1
            PadButton.R -> KeyEvent.KEYCODE_BUTTON_R1
            PadButton.START -> KeyEvent.KEYCODE_BUTTON_START
            PadButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
            PadButton.L2 -> KeyEvent.KEYCODE_BUTTON_L2
            PadButton.R2 -> KeyEvent.KEYCODE_BUTTON_R2
            PadButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
            PadButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
        }
        KEYBOARD_P1_WASD -> when (button) {
            PadButton.UP -> KeyEvent.KEYCODE_W
            PadButton.DOWN -> KeyEvent.KEYCODE_S
            PadButton.LEFT -> KeyEvent.KEYCODE_A
            PadButton.RIGHT -> KeyEvent.KEYCODE_D
            PadButton.A -> KeyEvent.KEYCODE_K
            PadButton.B -> KeyEvent.KEYCODE_J
            PadButton.X -> KeyEvent.KEYCODE_I
            PadButton.Y -> KeyEvent.KEYCODE_U
            PadButton.L -> KeyEvent.KEYCODE_Q
            PadButton.R -> KeyEvent.KEYCODE_E
            PadButton.START -> KeyEvent.KEYCODE_ENTER
            PadButton.SELECT -> KeyEvent.KEYCODE_SPACE
            PadButton.L2 -> KeyEvent.KEYCODE_Z
            PadButton.R2 -> KeyEvent.KEYCODE_C
            PadButton.L3 -> KeyEvent.KEYCODE_SHIFT_LEFT
            PadButton.R3 -> KeyEvent.KEYCODE_F
        }
        KEYBOARD_P2_ARROWS -> when (button) {
            PadButton.UP -> KeyEvent.KEYCODE_DPAD_UP
            PadButton.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            PadButton.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            PadButton.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            PadButton.A -> KeyEvent.KEYCODE_PERIOD
            PadButton.B -> KeyEvent.KEYCODE_COMMA
            PadButton.X -> KeyEvent.KEYCODE_SLASH
            PadButton.Y -> KeyEvent.KEYCODE_SEMICOLON
            PadButton.L -> KeyEvent.KEYCODE_LEFT_BRACKET
            PadButton.R -> KeyEvent.KEYCODE_RIGHT_BRACKET
            PadButton.START -> KeyEvent.KEYCODE_SHIFT_RIGHT
            PadButton.SELECT -> KeyEvent.KEYCODE_BACKSLASH
            PadButton.L2 -> KeyEvent.KEYCODE_MINUS
            PadButton.R2 -> KeyEvent.KEYCODE_EQUALS
            PadButton.L3 -> KeyEvent.KEYCODE_APOSTROPHE
            PadButton.R3 -> KeyEvent.KEYCODE_P
        }
        KEYBOARD_P3_IJKL -> when (button) {
            PadButton.UP -> KeyEvent.KEYCODE_I
            PadButton.DOWN -> KeyEvent.KEYCODE_K
            PadButton.LEFT -> KeyEvent.KEYCODE_J
            PadButton.RIGHT -> KeyEvent.KEYCODE_L
            PadButton.A -> KeyEvent.KEYCODE_N
            PadButton.B -> KeyEvent.KEYCODE_B
            PadButton.X -> KeyEvent.KEYCODE_M
            PadButton.Y -> KeyEvent.KEYCODE_H
            PadButton.L -> KeyEvent.KEYCODE_Y
            PadButton.R -> KeyEvent.KEYCODE_O
            PadButton.START -> KeyEvent.KEYCODE_9
            PadButton.SELECT -> KeyEvent.KEYCODE_0
            PadButton.L2 -> KeyEvent.KEYCODE_7
            PadButton.R2 -> KeyEvent.KEYCODE_8
            PadButton.L3 -> KeyEvent.KEYCODE_T
            PadButton.R3 -> KeyEvent.KEYCODE_G
        }
        KEYBOARD_P4_NUMPAD -> when (button) {
            PadButton.UP -> KeyEvent.KEYCODE_NUMPAD_8
            PadButton.DOWN -> KeyEvent.KEYCODE_NUMPAD_2
            PadButton.LEFT -> KeyEvent.KEYCODE_NUMPAD_4
            PadButton.RIGHT -> KeyEvent.KEYCODE_NUMPAD_6
            PadButton.A -> KeyEvent.KEYCODE_NUMPAD_1
            PadButton.B -> KeyEvent.KEYCODE_NUMPAD_0
            PadButton.X -> KeyEvent.KEYCODE_NUMPAD_3
            PadButton.Y -> KeyEvent.KEYCODE_NUMPAD_5
            PadButton.L -> KeyEvent.KEYCODE_NUMPAD_7
            PadButton.R -> KeyEvent.KEYCODE_NUMPAD_9
            PadButton.START -> KeyEvent.KEYCODE_NUMPAD_ENTER
            PadButton.SELECT -> KeyEvent.KEYCODE_NUMPAD_DOT
            PadButton.L2 -> KeyEvent.KEYCODE_NUMPAD_DIVIDE
            PadButton.R2 -> KeyEvent.KEYCODE_NUMPAD_MULTIPLY
            PadButton.L3 -> KeyEvent.KEYCODE_NUMPAD_SUBTRACT
            PadButton.R3 -> KeyEvent.KEYCODE_NUMPAD_ADD
        }
    }
}

enum class RejectReason(val code: Byte, val message: String) {
    INVALID_CODE(1, "Invalid pairing code. Check the 6-digit code on the Host."),
    HOST_FULL(2, "Host already has 4 players connected."),
    UNKNOWN(3, "Connection rejected by Host."),
    TRUSTED_NOT_RECOGNIZED(4, "Enter the 6-digit code once to trust this device permanently.");

    companion object {
        fun fromCode(code: Byte): RejectReason =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class DiscoveredHost(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val bluetoothAddress: String? = null,
    val transport: TransportType,
    val activePlayers: Int = 0,
    val maxPlayers: Int = 4,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class PlayerSlotState(
    val slot: Int,
    val connected: Boolean = false,
    val deviceName: String = "",
    val transport: TransportType = TransportType.WIFI_UDP,
    val rttMs: Int = 0,
    val buttonMask: Int = 0,
    val layout: ControllerLayout = ControllerLayout.RETRO_HANDHELD,
    val lx: Byte = 0,
    val ly: Byte = 0,
    val rx: Byte = 0,
    val ry: Byte = 0,
    val keymapProfile: KeymapProfile = KeymapProfile.GAMEPAD_STANDARD,
    val lastSeenMs: Long = 0L
)

data class RecentHostEntry(
    val name: String,
    val address: String,
    val port: Int,
    val transport: TransportType,
    val bluetoothAddress: String? = null
)

data class FavoriteHostEntry(
    val name: String,
    val address: String,
    val port: Int,
    val transport: TransportType,
    val bluetoothAddress: String? = null
)

data class PlayerInputTransition(
    val slot: Int,
    val button: PadButton,
    val pressed: Boolean,
    val keymapProfile: KeymapProfile
)

data class AnalogAxes(
    val lx: Byte = 0,
    val ly: Byte = 0,
    val rx: Byte = 0,
    val ry: Byte = 0
)
