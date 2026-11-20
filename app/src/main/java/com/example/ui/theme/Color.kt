package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF090C11)
val GraphiteSurface = Color(0xFF131924)
val GraphiteElevated = Color(0xFF1B2332)
val OledWell = Color(0xFF0C1017)
val KeycapSurface = Color(0xFF1B2230)
val KeycapPressed = Color(0xFF2B364A)
val KeycapHighlight = Color(0xFF273142)

val HairlineBorder = Color(0x28FFFFFF)
val HairlineSubtle = Color(0x14FFFFFF)

val GlassRimBright = Color(0x45FFFFFF)
val GlassRimMid = Color(0x1CFFFFFF)
val GlassRimDim = Color(0x0AFFFFFF)
val GlassSheenTop = Color(0x14FFFFFF)
val GlassFillBase = Color(0xB8141B26)
val GlassFillElevated = Color(0xCC1C2534)
val GlassFillSubtle = Color(0x88101620)

val GlassGlowTop = Color(0xFF1F3047)
val GlassGlowBottom = Color(0xFF162130)

val TitaniumText = Color(0xFFF3F6FB)
val PewterMuted = Color(0xFF8C97AB)
val SteelDim = Color(0xFF535D70)

val SignalEmerald = Color(0xFF5CE0A8)
val EmeraldDim = Color(0xFF143126)
val CobaltBlue = Color(0xFF7CB2FF)
val CobaltDim = Color(0xFF182A44)
val WarmAmber = Color(0xFFE5B869)
val AmberDim = Color(0xFF312614)
val PrecisionCrimson = Color(0xFFF27474)
val CrimsonDim = Color(0xFF33191D)
val BrushedSilver = Color(0xFFD6DFED)

fun playerAccentColor(slot: Int): Color = when (slot) {
    1 -> SignalEmerald
    2 -> CobaltBlue
    3 -> WarmAmber
    4 -> Color(0xFFC09BFF)
    else -> BrushedSilver
}

fun playerDimColor(slot: Int): Color = when (slot) {
    1 -> EmeraldDim
    2 -> CobaltDim
    3 -> AmberDim
    4 -> Color(0xFF28203B)
    else -> KeycapSurface
}
