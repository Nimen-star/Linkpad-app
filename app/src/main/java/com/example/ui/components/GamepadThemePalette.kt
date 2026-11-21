package com.example.ui.components

import androidx.compose.ui.graphics.Color
import com.example.protocol.ControllerTheme
import com.example.protocol.PadButton
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillBase
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimBright
import com.example.ui.theme.GlassRimDim
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.OledWell
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber

data class GamepadThemePalette(
    val accent: Color,
    val wellColor: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val pressedTop: Color,
    val rimBright: Color,
    val rimMid: Color,
    val rimDim: Color,
    val dpadBody: Color,
    val legendIdle: Color,
    val legendPressed: Color,
    val buttonColorA: Color,
    val buttonColorB: Color,
    val buttonColorX: Color,
    val buttonColorY: Color,
    val usePlayStationSymbols: Boolean = false
)

fun resolveGamepadPalette(theme: ControllerTheme, slot: Int): GamepadThemePalette {
    val slotAccent = when (slot) {
        1 -> SignalEmerald
        2 -> CobaltBlue
        3 -> WarmAmber
        4 -> Color(0xFFC09BFF)
        else -> Color(0xFFD6DFED)
    }
    return when (theme) {
        ControllerTheme.LIQUID_GLASS -> GamepadThemePalette(
            accent = slotAccent,
            wellColor = OledWell,
            surfaceTop = Color.White.copy(alpha = 0.13f),
            surfaceBottom = GlassFillBase,
            pressedTop = slotAccent.copy(alpha = 0.36f),
            rimBright = GlassRimBright,
            rimMid = GlassRimMid,
            rimDim = GlassRimDim,
            dpadBody = Color(0xFF1E2634),
            legendIdle = PewterMuted,
            legendPressed = TitaniumText,
            buttonColorA = SignalEmerald,
            buttonColorB = Color(0xFFF27474),
            buttonColorX = CobaltBlue,
            buttonColorY = WarmAmber
        )
        ControllerTheme.XBOX_NEON -> GamepadThemePalette(
            accent = Color(0xFF3EF28E),
            wellColor = Color(0xFF070A0D),
            surfaceTop = Color(0xFF1F272E),
            surfaceBottom = Color(0xFF12171C),
            pressedTop = Color(0xFF287D4E),
            rimBright = Color(0x883EF28E),
            rimMid = Color(0x403EF28E),
            rimDim = Color(0x18FFFFFF),
            dpadBody = Color(0xFF1A2226),
            legendIdle = Color(0xFF9EB0A8),
            legendPressed = Color(0xFFFFFFFF),
            buttonColorA = Color(0xFF39E678),
            buttonColorB = Color(0xFFFF5252),
            buttonColorX = Color(0xFF42A5F5),
            buttonColorY = Color(0xFFFFCA28)
        )
        ControllerTheme.DUAL_SENSE -> GamepadThemePalette(
            accent = Color(0xFF56A8FF),
            wellColor = Color(0xFF11151E),
            surfaceTop = Color(0xFF2D3546),
            surfaceBottom = Color(0xFF1B212E),
            pressedTop = Color(0xFF2A589C),
            rimBright = Color(0x80B5D3FF),
            rimMid = Color(0x3882B4FF),
            rimDim = Color(0x18FFFFFF),
            dpadBody = Color(0xFF262D3B),
            legendIdle = Color(0xFFD5E1F5),
            legendPressed = Color(0xFFFFFFFF),
            buttonColorA = Color(0xFF72B5FF), // Cross
            buttonColorB = Color(0xFFFF7A90), // Circle
            buttonColorX = Color(0xFFD888FF), // Square
            buttonColorY = Color(0xFF5DE3C0), // Triangle
            usePlayStationSymbols = true
        )
        ControllerTheme.SWITCH_NEON -> GamepadThemePalette(
            accent = Color(0xFF00D2E6),
            wellColor = Color(0xFF12161E),
            surfaceTop = Color(0xFF242B37),
            surfaceBottom = Color(0xFF171C25),
            pressedTop = Color(0xFF008EA0),
            rimBright = Color(0x70FF4C5B),
            rimMid = Color(0x4500D2E6),
            rimDim = Color(0x1AFFFFFF),
            dpadBody = Color(0xFF202630),
            legendIdle = Color(0xFFE7ECF4),
            legendPressed = Color(0xFFFFFFFF),
            buttonColorA = Color(0xFFFF4C5B),
            buttonColorB = Color(0xFF00D2E6),
            buttonColorX = Color(0xFFFF8F4C),
            buttonColorY = Color(0xFF6AE69E)
        )
        ControllerTheme.RETRO_SNES -> GamepadThemePalette(
            accent = Color(0xFF9B7BFF),
            wellColor = Color(0xFF181920),
            surfaceTop = Color(0xFF343542),
            surfaceBottom = Color(0xFF22232D),
            pressedTop = Color(0xFF6B4ED4),
            rimBright = Color(0x70C6BEDD),
            rimMid = Color(0x389B7BFF),
            rimDim = Color(0x18FFFFFF),
            dpadBody = Color(0xFF2B2C36),
            legendIdle = Color(0xFFD7D2E6),
            legendPressed = Color(0xFFFFFFFF),
            buttonColorA = Color(0xFF8C62E8),
            buttonColorB = Color(0xFF8C62E8),
            buttonColorX = Color(0xFFB7A6F6),
            buttonColorY = Color(0xFFB7A6F6)
        )
    }
}

fun GamepadThemePalette.faceButtonAccent(button: PadButton): Color = when (button) {
    PadButton.A -> buttonColorA
    PadButton.B -> buttonColorB
    PadButton.X -> buttonColorX
    PadButton.Y -> buttonColorY
    else -> accent
}

fun GamepadThemePalette.faceButtonLabel(button: PadButton): String =
    if (!usePlayStationSymbols) {
        button.shortLabel
    } else {
        when (button) {
            PadButton.A -> "✕"
            PadButton.B -> "○"
            PadButton.X -> "□"
            PadButton.Y -> "△"
            else -> button.shortLabel
        }
    }

internal fun themeWellBackground(palette: GamepadThemePalette): Color = palette.wellColor.copy(alpha = 0.90f)
internal fun themePressedCapFill(palette: GamepadThemePalette, tint: Color): List<Color> =
    listOf(tint.copy(alpha = 0.44f), GlassFillElevated)
internal fun themeIdleCapFill(palette: GamepadThemePalette): List<Color> =
    listOf(palette.surfaceTop, palette.surfaceBottom)
internal fun themeSubtleWell(palette: GamepadThemePalette): Color =
    GlassFillSubtle.copy(alpha = 0.72f)
