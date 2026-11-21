package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.example.protocol.AnalogAxes
import com.example.protocol.ControlCustomization
import com.example.protocol.ControlOffset
import com.example.protocol.ControllerTheme
import com.example.protocol.HapticStrength
import com.example.protocol.PadButton
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillBase
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimBright
import com.example.ui.theme.GlassRimDim
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OledWell
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.playerAccentColor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

internal fun performGamepadHaptic(
    context: Context,
    view: View,
    strength: HapticStrength,
    isFaceButton: Boolean = false
) {
    if (strength == HapticStrength.OFF) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            val durationMs = when (strength) {
                HapticStrength.OFF -> 0L
                HapticStrength.LIGHT -> if (isFaceButton) 10L else 8L
                HapticStrength.CRISP -> if (isFaceButton) 16L else 12L
                HapticStrength.STRONG -> if (isFaceButton) 26L else 20L
            }
            val amplitude = when (strength) {
                HapticStrength.OFF -> 0
                HapticStrength.LIGHT -> 90
                HapticStrength.CRISP -> if (isFaceButton) 185 else 155
                HapticStrength.STRONG -> 255
            }
            if (durationMs > 0L) {
                runCatching {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }.onSuccess { return }
            }
        }
    }
    val constant = when {
        strength == HapticStrength.STRONG -> HapticFeedbackConstants.LONG_PRESS
        isFaceButton -> HapticFeedbackConstants.VIRTUAL_KEY
        else -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    view.performHapticFeedback(constant)
}

@Composable
internal fun DraggableControlSlot(
    offset: ControlOffset,
    editMode: Boolean,
    onOffsetChange: (ControlOffset) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val latestOffset by rememberUpdatedState(offset)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val highlightShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .offset { IntOffset(latestOffset.x.roundToInt(), latestOffset.y.roundToInt()) }
            .then(
                if (editMode) {
                    Modifier
                        .border(1.5.dp, SignalEmerald.copy(alpha = 0.72f), highlightShape)
                        .background(SignalEmerald.copy(alpha = 0.08f), highlightShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = latestOffset
                                latestOnOffsetChange(
                                    ControlOffset(
                                        x = (current.x + dragAmount.x).coerceIn(-320f, 320f),
                                        y = (current.y + dragAmount.y).coerceIn(-260f, 260f)
                                    )
                                )
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

@Composable
fun RetroControllerPad(
    slot: Int,
    hapticStrength: HapticStrength,
    customization: ControlCustomization,
    editLayoutMode: Boolean,
    onCustomizationChange: (ControlCustomization) -> Unit,
    onMaskChanged: (Int) -> Unit = {},
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    gyroAxes: AnalogAxes = AnalogAxes(),
    onStateChanged: ((mask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = resolveGamepadPalette(controllerTheme, slot)
    val accent = palette.accent
    val latestOnMaskChanged by rememberUpdatedState(onMaskChanged)
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)
    val latestHaptic by rememberUpdatedState(hapticStrength)

    var dpadBits by remember { mutableIntStateOf(0) }
    var abxyBits by remember { mutableIntStateOf(0) }
    var lPressed by remember { mutableIntStateOf(0) }
    var rPressed by remember { mutableIntStateOf(0) }
    var selectPressed by remember { mutableIntStateOf(0) }
    var startPressed by remember { mutableIntStateOf(0) }

    fun emitCombined(
        newDpad: Int = dpadBits,
        newAbxy: Int = abxyBits,
        newL: Int = lPressed,
        newR: Int = rPressed,
        newSelect: Int = selectPressed,
        newStart: Int = startPressed,
        isFace: Boolean = false
    ) {
        val oldTotal = dpadBits or abxyBits or lPressed or rPressed or selectPressed or startPressed
        dpadBits = newDpad
        abxyBits = newAbxy
        lPressed = newL
        rPressed = newR
        selectPressed = newSelect
        startPressed = newStart
        val newTotal = newDpad or newAbxy or newL or newR or newSelect or newStart
        if (newTotal != oldTotal) {
            val newlyPressed = newTotal and oldTotal.inv()
            if (newlyPressed != 0) {
                performGamepadHaptic(context, view, latestHaptic, isFaceButton = isFace)
            }
            latestOnMaskChanged(newTotal)
            latestOnStateChanged?.invoke(newTotal, gyroAxes.lx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
        }
    }

    LaunchedEffect(gyroAxes) {
        val total = dpadBits or abxyBits or lPressed or rPressed or selectPressed or startPressed
        latestOnStateChanged?.invoke(total, gyroAxes.lx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
    }

    val totalMask = dpadBits or abxyBits or lPressed or rPressed or selectPressed or startPressed
    val controlScale = customization.scale.coerceIn(0.72f, 1.35f)
    val controlAlpha = customization.opacity.coerceIn(0.35f, 1.0f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(controlAlpha)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val isWide = maxWidth >= maxHeight * 1.15f

        if (isWide) {
            val controlSize = (min(maxHeight.value * 0.66f, maxWidth.value * 0.28f) * controlScale)
                .coerceIn(132f, 250f).dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DraggableControlSlot(
                            offset = customization.leftShoulderOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(leftShoulderOffset = it))
                            }
                        ) {
                            ShoulderBumper(
                                label = "L",
                                isLeft = true,
                                pressed = lPressed != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width((142f * controlScale).coerceIn(108f, 178f).dp)
                                    .height(48.dp)
                                    .testTag("pad_button_l"),
                                onPressedChange = { down ->
                                    emitCombined(newL = if (down) PadButton.L.bit else 0)
                                }
                            )
                        }
                        DraggableControlSlot(
                            offset = customization.rightShoulderOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(rightShoulderOffset = it))
                            }
                        ) {
                            ShoulderBumper(
                                label = "R",
                                isLeft = false,
                                pressed = rPressed != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width((142f * controlScale).coerceIn(108f, 178f).dp)
                                    .height(48.dp)
                                    .testTag("pad_button_r"),
                                onPressedChange = { down ->
                                    emitCombined(newR = if (down) PadButton.R.bit else 0)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DraggableControlSlot(
                            offset = customization.dpadOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(dpadOffset = it))
                            }
                        ) {
                            InteractiveCrossDpad(
                                activeMask = totalMask,
                                accent = accent,
                                size = controlSize,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_dpad"),
                                onDpadMaskChange = { mask -> emitCombined(newDpad = mask) }
                            )
                        }

                        DraggableControlSlot(
                            offset = customization.menuOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(menuOffset = it))
                            },
                            modifier = Modifier.padding(top = 82.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                MenuPillButton(
                                    label = "SELECT",
                                    pressed = selectPressed != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_select"),
                                    onPressedChange = { down ->
                                        emitCombined(newSelect = if (down) PadButton.SELECT.bit else 0)
                                    }
                                )
                                MenuPillButton(
                                    label = "START",
                                    pressed = startPressed != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_start"),
                                    onPressedChange = { down ->
                                        emitCombined(newStart = if (down) PadButton.START.bit else 0)
                                    }
                                )
                            }
                        }

                        DraggableControlSlot(
                            offset = customization.abxyOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(abxyOffset = it))
                            }
                        ) {
                            InteractiveAbxyCluster(
                                activeMask = totalMask,
                                accent = accent,
                                size = controlSize,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_abxy"),
                                onAbxyMaskChange = { mask ->
                                    emitCombined(newAbxy = mask, isFace = true)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.45f))
                }
            }
        } else {
            val controlSize = ((maxWidth.value * 0.42f) * controlScale).coerceIn(132f, 212f).dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DraggableControlSlot(
                        offset = customization.leftShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(leftShoulderOffset = it))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        ShoulderBumper(
                            label = "L",
                            isLeft = true,
                            pressed = lPressed != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pad_button_l"),
                            onPressedChange = { down ->
                                emitCombined(newL = if (down) PadButton.L.bit else 0)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    DraggableControlSlot(
                        offset = customization.rightShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(rightShoulderOffset = it))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        ShoulderBumper(
                            label = "R",
                            isLeft = false,
                            pressed = rPressed != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pad_button_r"),
                            onPressedChange = { down ->
                                emitCombined(newR = if (down) PadButton.R.bit else 0)
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.dpadOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(dpadOffset = it))
                        }
                    ) {
                        InteractiveCrossDpad(
                            activeMask = totalMask,
                            accent = accent,
                            size = controlSize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_dpad"),
                            onDpadMaskChange = { mask -> emitCombined(newDpad = mask) }
                        )
                    }
                    DraggableControlSlot(
                        offset = customization.abxyOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(abxyOffset = it))
                        }
                    ) {
                        InteractiveAbxyCluster(
                            activeMask = totalMask,
                            accent = accent,
                            size = controlSize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_abxy"),
                            onAbxyMaskChange = { mask ->
                                emitCombined(newAbxy = mask, isFace = true)
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    DraggableControlSlot(
                        offset = customization.menuOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(menuOffset = it))
                        }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MenuPillButton(
                                label = "SELECT",
                                pressed = selectPressed != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_select"),
                                onPressedChange = { down ->
                                    emitCombined(newSelect = if (down) PadButton.SELECT.bit else 0)
                                }
                            )
                            MenuPillButton(
                                label = "START",
                                pressed = startPressed != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_start"),
                                onPressedChange = { down ->
                                    emitCombined(newStart = if (down) PadButton.START.bit else 0)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShoulderBumper(
    label: String,
    isLeft: Boolean,
    pressed: Boolean,
    accent: Color,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    val latestCallback by rememberUpdatedState(onPressedChange)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(45),
        label = "bumperScale"
    )
    val shape = if (isLeft) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 22.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    }
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            if (pressed) accent.copy(alpha = 0.85f) else palette.rimBright,
            if (pressed) accent.copy(alpha = 0.32f) else palette.rimDim
        )
    )
    val fillBrush = Brush.verticalGradient(
        colors = if (pressed) {
            themePressedCapFill(palette, accent)
        } else {
            themeIdleCapFill(palette)
        }
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fillBrush)
            .border(BorderStroke(1.dp, rimBrush), shape)
            .semantics { contentDescription = "Shoulder button $label" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(label) {
                        awaitPointerEventScope {
                            var wasDown = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyDown = event.changes.any { it.pressed }
                                if (anyDown != wasDown) {
                                    wasDown = anyDown
                                    latestCallback(anyDown)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (pressed) palette.legendPressed else palette.legendIdle
        )
    }
}

@Composable
internal fun MenuPillButton(
    label: String,
    pressed: Boolean,
    accent: Color,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    val latestCallback by rememberUpdatedState(onPressedChange)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(45),
        label = "pillScale"
    )

    Box(
        modifier = modifier
            .size(width = 78.dp, height = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = "$label button" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(label) {
                        awaitPointerEventScope {
                            var wasDown = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyDown = event.changes.any { it.pressed }
                                if (anyDown != wasDown) {
                                    wasDown = anyDown
                                    latestCallback(anyDown)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { rotationZ = -18f }
                    .size(width = 44.dp, height = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (pressed) accent.copy(alpha = 0.34f) else palette.surfaceBottom)
                    .border(
                        width = 1.dp,
                        color = if (pressed) accent.copy(alpha = 0.85f) else palette.rimMid,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                color = if (pressed) palette.legendPressed else palette.legendIdle
            )
        }
    }
}

@Composable
internal fun InteractiveCrossDpad(
    activeMask: Int,
    accent: Color,
    size: Dp,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    modifier: Modifier = Modifier,
    onDpadMaskChange: (Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onDpadMaskChange)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Directional D-pad" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            val activePointers = mutableMapOf<PointerId, Offset>()
                            var lastMask = 0
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        activePointers[change.id] = change.position
                                    } else {
                                        activePointers.remove(change.id)
                                    }
                                    change.consume()
                                }

                                var newMask = 0
                                val cx = sizePx / 2f
                                val cy = sizePx / 2f
                                val deadzone = sizePx * 0.11f

                                for (pos in activePointers.values) {
                                    val dx = pos.x - cx
                                    val dy = pos.y - cy
                                    val dist = hypot(dx, dy)
                                    if (dist >= deadzone) {
                                        val nx = dx / dist
                                        val ny = dy / dist
                                        if (nx >= 0.3827f) newMask = newMask or PadButton.RIGHT.bit
                                        if (nx <= -0.3827f) newMask = newMask or PadButton.LEFT.bit
                                        if (ny >= 0.3827f) newMask = newMask or PadButton.DOWN.bit
                                        if (ny <= -0.3827f) newMask = newMask or PadButton.UP.bit
                                    }
                                }

                                if (newMask != lastMask) {
                                    lastMask = newMask
                                    latestCallback(newMask)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        CrossDpadGraphic(
            activeMask = activeMask,
            accent = accent,
            size = size,
            palette = palette
        )
    }
}

@Composable
fun CrossDpadGraphic(
    activeMask: Int,
    accent: Color,
    size: Dp,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1)
) {
    val up = (activeMask and PadButton.UP.bit) != 0
    val down = (activeMask and PadButton.DOWN.bit) != 0
    val left = (activeMask and PadButton.LEFT.bit) != 0
    val right = (activeMask and PadButton.RIGHT.bit) != 0

    val density = LocalDensity.current
    val cornerRadiusPx = remember(density) { with(density) { 9.dp.toPx() } }
    val rimStrokePx = remember(density) { with(density) { 1.2.dp.toPx() } }
    val hairlineStrokePx = remember(density) { with(density) { 1.dp.toPx() } }

    val crossPath = remember { Path() }
    val chevronPath = remember { Path() }
    val fillPaint = remember(cornerRadiusPx, palette.dpadBody) {
        Paint().apply {
            style = PaintingStyle.Fill
            color = palette.dpadBody
            asFrameworkPaint().pathEffect = android.graphics.CornerPathEffect(cornerRadiusPx)
        }
    }
    val rimPaint = remember(cornerRadiusPx, rimStrokePx, palette.rimBright) {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = rimStrokePx
            color = palette.rimBright
            asFrameworkPaint().pathEffect = android.graphics.CornerPathEffect(cornerRadiusPx)
        }
    }

    val cachedSize = remember { floatArrayOf(-1f, -1f) }

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val center = Offset(cx, cy)
        val halfArm = w * 0.16f
        val outer = w * 0.42f

        if (cachedSize[0] != w || cachedSize[1] != h) {
            cachedSize[0] = w
            cachedSize[1] = h
            crossPath.reset()
            crossPath.moveTo(cx - halfArm, cy - outer)
            crossPath.lineTo(cx + halfArm, cy - outer)
            crossPath.lineTo(cx + halfArm, cy - halfArm)
            crossPath.lineTo(cx + outer, cy - halfArm)
            crossPath.lineTo(cx + outer, cy + halfArm)
            crossPath.lineTo(cx + halfArm, cy + halfArm)
            crossPath.lineTo(cx + halfArm, cy + outer)
            crossPath.lineTo(cx - halfArm, cy + outer)
            crossPath.lineTo(cx - halfArm, cy + halfArm)
            crossPath.lineTo(cx - outer, cy + halfArm)
            crossPath.lineTo(cx - outer, cy - halfArm)
            crossPath.lineTo(cx - halfArm, cy - halfArm)
            crossPath.close()
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(themeWellBackground(palette), themeSubtleWell(palette)),
                center = center,
                radius = w * 0.50f
            ),
            radius = w * 0.49f,
            center = center
        )
        drawCircle(
            brush = Brush.linearGradient(listOf(palette.rimBright, palette.rimDim)),
            radius = w * 0.49f,
            center = center,
            style = Stroke(width = hairlineStrokePx)
        )

        drawIntoCanvas { canvas ->
            canvas.drawPath(crossPath, fillPaint)
            canvas.drawPath(crossPath, rimPaint)
        }

        val glowR = halfArm * 1.15f
        if (up) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx, cy - outer * 0.68f),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(cx, cy - outer * 0.68f)
            )
        }
        if (down) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx, cy + outer * 0.68f),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(cx, cy + outer * 0.68f)
            )
        }
        if (left) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx - outer * 0.68f, cy),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(cx - outer * 0.68f, cy)
            )
        }
        if (right) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx + outer * 0.68f, cy),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(cx + outer * 0.68f, cy)
            )
        }

        fun drawChevron(px: Float, py: Float, dx: Float, dy: Float, active: Boolean) {
            val s = halfArm * 0.34f
            chevronPath.reset()
            chevronPath.moveTo(px + dx * s, py + dy * s)
            chevronPath.lineTo(px - dx * s * 0.45f - dy * s * 0.85f, py - dy * s * 0.45f + dx * s * 0.85f)
            chevronPath.lineTo(px - dx * s * 0.45f + dy * s * 0.85f, py - dy * s * 0.45f - dx * s * 0.85f)
            chevronPath.close()
            drawPath(chevronPath, color = if (active) TitaniumText else PewterMuted.copy(alpha = 0.55f))
        }

        val chevronDist = outer * 0.72f
        drawChevron(cx, cy - chevronDist, 0f, -1f, up)
        drawChevron(cx, cy + chevronDist, 0f, 1f, down)
        drawChevron(cx - chevronDist, cy, -1f, 0f, left)
        drawChevron(cx + chevronDist, cy, 1f, 0f, right)

        drawCircle(
            brush = Brush.radialGradient(
                listOf(OledWell, Color(0xFF1A212E)),
                center = center,
                radius = halfArm * 0.62f
            ),
            radius = halfArm * 0.60f,
            center = center
        )
        drawCircle(
            color = GlassRimMid,
            radius = halfArm * 0.60f,
            center = center,
            style = Stroke(width = hairlineStrokePx)
        )
    }
}

@Composable
internal fun InteractiveAbxyCluster(
    activeMask: Int,
    accent: Color,
    size: Dp,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    modifier: Modifier = Modifier,
    onAbxyMaskChange: (Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onAbxyMaskChange)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "A B X Y face buttons" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            val activePointers = mutableMapOf<PointerId, Offset>()
                            var lastMask = 0
                            val r = sizePx * 0.21f
                            val hitRadius = r * 1.42f
                            val c = sizePx / 2f
                            val spread = sizePx * 0.28f

                            val targets = listOf(
                                PadButton.Y to Offset(c, c - spread),
                                PadButton.A to Offset(c, c + spread),
                                PadButton.X to Offset(c - spread, c),
                                PadButton.B to Offset(c + spread, c)
                            )

                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        activePointers[change.id] = change.position
                                    } else {
                                        activePointers.remove(change.id)
                                    }
                                    change.consume()
                                }

                                var newMask = 0
                                for (pos in activePointers.values) {
                                    for ((btn, btnCenter) in targets) {
                                        if (hypot(pos.x - btnCenter.x, pos.y - btnCenter.y) <= hitRadius) {
                                            newMask = newMask or btn.bit
                                        }
                                    }
                                }

                                if (newMask != lastMask) {
                                    lastMask = newMask
                                    latestCallback(newMask)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        AbxyClusterGraphic(
            activeMask = activeMask,
            accent = accent,
            size = size,
            palette = palette
        )
    }
}

@Composable
fun AbxyClusterGraphic(
    activeMask: Int,
    accent: Color,
    size: Dp,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1)
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val btnSizeDp = size * 0.35f
    val btnRadiusPx = with(density) { (btnSizeDp / 2).toPx() }
    val c = sizePx / 2f
    val spread = sizePx * 0.28f

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(themeWellBackground(palette), themeSubtleWell(palette))
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(palette.rimBright, palette.rimDim))
                    ),
                    CircleShape
                )
        )

        val buttons = listOf(
            PadButton.Y to Offset(c, c - spread),
            PadButton.A to Offset(c, c + spread),
            PadButton.X to Offset(c - spread, c),
            PadButton.B to Offset(c + spread, c)
        )

        buttons.forEach { (btn, center) ->
            val pressed = (activeMask and btn.bit) != 0
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.88f else 1f,
                animationSpec = tween(40),
                label = "btnScale_${btn.shortLabel}"
            )
            val btnAccent = palette.faceButtonAccent(btn)
            val capBrush = Brush.verticalGradient(
                colors = if (pressed) {
                    themePressedCapFill(palette, btnAccent)
                } else {
                    themeIdleCapFill(palette)
                }
            )
            val rimBrush = Brush.linearGradient(
                colors = listOf(
                    if (pressed) btnAccent else btnAccent.copy(alpha = 0.48f),
                    if (pressed) btnAccent.copy(alpha = 0.45f) else palette.rimDim
                )
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - btnRadiusPx).roundToInt(),
                            (center.y - btnRadiusPx).roundToInt()
                        )
                    }
                    .size(btnSizeDp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(capBrush)
                    .border(BorderStroke(if (pressed) 1.7.dp else 1.1.dp, rimBrush), CircleShape)
                    .drawBehind {
                        drawArc(
                            color = Color.White.copy(alpha = if (pressed) 0.12f else 0.24f),
                            startAngle = 215f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                            size = Size(this.size.width - 6.dp.toPx(), this.size.height - 6.dp.toPx()),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = palette.faceButtonLabel(btn),
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size >= 140.dp) 17.sp else 12.sp,
                    color = if (pressed) palette.legendPressed else btnAccent.copy(alpha = 0.92f)
                )
            }
        }
    }
}
