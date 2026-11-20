package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassFillBase
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassGlowBottom
import com.example.ui.theme.GlassGlowTop
import com.example.ui.theme.GlassRimBright
import com.example.ui.theme.GlassRimDim
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.GlassSheenTop
import com.example.ui.theme.ObsidianBg

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Soft diffused atmospheric light pools behind glass panels (subtle, never flashy)
        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(56.dp)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlassGlowTop.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.08f),
                            radius = size.maxDimension * 0.52f
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.08f),
                        radius = size.maxDimension * 0.52f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlassGlowBottom.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.88f),
                            radius = size.maxDimension * 0.50f
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.88f),
                        radius = size.maxDimension * 0.50f
                    )
                }
        )
        content()
    }
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    subtle: Boolean = false,
    accentBorderTint: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val baseFill = when {
        elevated -> GlassFillElevated
        subtle -> GlassFillSubtle
        else -> GlassFillBase
    }

    val topRimColor = accentBorderTint?.copy(alpha = 0.48f) ?: GlassRimBright
    val midRimColor = accentBorderTint?.copy(alpha = 0.18f) ?: GlassRimMid
    val rimBrush = Brush.linearGradient(
        colors = listOf(topRimColor, midRimColor, GlassRimDim),
        start = Offset(0f, 0f),
        end = Offset(600f, 600f)
    )

    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            GlassSheenTop,
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseFill)
            .background(sheenBrush)
            .border(BorderStroke(borderWidth, rimBrush), shape),
        content = content
    )
}
