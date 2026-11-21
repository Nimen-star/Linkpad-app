package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.example.protocol.AnalogAxes
import com.example.protocol.ControlCustomization
import com.example.protocol.ControllerTheme
import com.example.protocol.HapticStrength
import com.example.protocol.PadButton
import com.example.protocol.ProPaddleConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TitaniumText
import com.example.ui.theme.WarmAmber
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RacingWheelControllerPad(
    slot: Int,
    hapticStrength: HapticStrength,
    customization: ControlCustomization,
    gyroAxes: AnalogAxes,
    editLayoutMode: Boolean,
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    onCustomizationChange: (ControlCustomization) -> Unit,
    onStateChanged: (buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = resolveGamepadPalette(controllerTheme, slot)
    val accent = palette.accent
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)
    val latestHaptic by rememberUpdatedState(hapticStrength)

    var abxyBits by remember { mutableIntStateOf(0) }
    var l1Bit by remember { mutableIntStateOf(0) }
    var r1Bit by remember { mutableIntStateOf(0) }
    var brakeBit by remember { mutableIntStateOf(0) }
    var throttleBit by remember { mutableIntStateOf(0) }
    var selectBit by remember { mutableIntStateOf(0) }
    var startBit by remember { mutableIntStateOf(0) }
    var touchSteerX by remember { mutableIntStateOf(0) }

    fun pushState(
        newAbxy: Int = abxyBits,
        newL1: Int = l1Bit,
        newR1: Int = r1Bit,
        newBrake: Int = brakeBit,
        newThrottle: Int = throttleBit,
        newSelect: Int = selectBit,
        newStart: Int = startBit,
        newTouchSteerX: Int = touchSteerX,
        isFace: Boolean = false
    ) {
        val oldMask = abxyBits or l1Bit or r1Bit or brakeBit or throttleBit or selectBit or startBit
        abxyBits = newAbxy
        l1Bit = newL1
        r1Bit = newR1
        brakeBit = newBrake
        throttleBit = newThrottle
        selectBit = newSelect
        startBit = newStart
        touchSteerX = newTouchSteerX

        val newMask = abxyBits or l1Bit or r1Bit or brakeBit or throttleBit or selectBit or startBit
        if ((newMask and oldMask.inv()) != 0) {
            performGamepadHaptic(context, view, latestHaptic, isFaceButton = isFace)
        }
        val combinedLx = (touchSteerX + gyroAxes.lx.toInt()).coerceIn(-127, 127).toByte()
        latestOnStateChanged(newMask, combinedLx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
    }

    LaunchedEffect(gyroAxes) {
        val totalMask = abxyBits or l1Bit or r1Bit or brakeBit or throttleBit or selectBit or startBit
        val combinedLx = (touchSteerX + gyroAxes.lx.toInt()).coerceIn(-127, 127).toByte()
        latestOnStateChanged(totalMask, combinedLx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
    }

    val totalMask = abxyBits or l1Bit or r1Bit or brakeBit or throttleBit or selectBit or startBit
    val wheelVisualNorm = ((touchSteerX + gyroAxes.lx.toInt()).coerceIn(-127, 127)) / 127f
    val controlScale = customization.scale.coerceIn(0.72f, 1.35f)
    val controlAlpha = customization.opacity.coerceIn(0.35f, 1.0f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(controlAlpha)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val isWide = maxWidth >= maxHeight * 1.15f

        if (isWide) {
            val wheelSize = (min(maxHeight.value * 0.72f, maxWidth.value * 0.36f) * controlScale)
                .coerceIn(168f, 286f).dp
            val abxySize = (min(maxHeight.value * 0.40f, maxWidth.value * 0.16f) * controlScale)
                .coerceIn(92f, 148f).dp
            val pedalW = (84f * controlScale).coerceIn(68f, 114f).dp
            val pedalH = (150f * controlScale).coerceIn(112f, 210f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Paddle shifters + Menu pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShoulderBumper(
                        label = "L1 · DOWN",
                        isLeft = true,
                        pressed = l1Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width((124f * controlScale).coerceIn(96f, 152f).dp)
                            .height(48.dp)
                            .testTag("pad_button_l"),
                        onPressedChange = { down -> pushState(newL1 = if (down) PadButton.L.bit else 0) }
                    )

                    DraggableControlSlot(
                        offset = customization.menuOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(menuOffset = it)) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuPillButton(
                                label = "SELECT",
                                pressed = selectBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_select"),
                                onPressedChange = { down -> pushState(newSelect = if (down) PadButton.SELECT.bit else 0) }
                            )
                            MenuPillButton(
                                label = "START",
                                pressed = startBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_start"),
                                onPressedChange = { down -> pushState(newStart = if (down) PadButton.START.bit else 0) }
                            )
                        }
                    }

                    ShoulderBumper(
                        label = "R1 · UP",
                        isLeft = false,
                        pressed = r1Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width((124f * controlScale).coerceIn(96f, 152f).dp)
                            .height(48.dp)
                            .testTag("pad_button_r"),
                        onPressedChange = { down -> pushState(newR1 = if (down) PadButton.R.bit else 0) }
                    )
                }

                // Main Cockpit: Brake Pedal | Steering Wheel | ABXY + Gas Pedal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.leftShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(leftShoulderOffset = it)) }
                    ) {
                        RacingPedalButton(
                            label = "BRAKE",
                            subLabel = "L2",
                            pressed = brakeBit != 0,
                            pedalColor = PrecisionCrimson,
                            palette = palette,
                            width = pedalW,
                            height = pedalH,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_l2"),
                            onPressedChange = { down -> pushState(newBrake = if (down) PadButton.L2.bit else 0) }
                        )
                    }

                    DraggableControlSlot(
                        offset = customization.leftStickOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(leftStickOffset = it)) }
                    ) {
                        InteractiveSteeringWheel(
                            size = wheelSize,
                            visualNorm = wheelVisualNorm,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_steering_wheel"),
                            onTouchSteerChanged = { sx -> pushState(newTouchSteerX = sx) }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DraggableControlSlot(
                            offset = customization.abxyOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = { onCustomizationChange(customization.copy(abxyOffset = it)) }
                        ) {
                            InteractiveAbxyCluster(
                                activeMask = totalMask,
                                accent = accent,
                                size = abxySize,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_abxy"),
                                onAbxyMaskChange = { mask -> pushState(newAbxy = mask, isFace = true) }
                            )
                        }

                        DraggableControlSlot(
                            offset = customization.rightShoulderOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = { onCustomizationChange(customization.copy(rightShoulderOffset = it)) }
                        ) {
                            RacingPedalButton(
                                label = "GAS",
                                subLabel = "R2",
                                pressed = throttleBit != 0,
                                pedalColor = SignalEmerald,
                                palette = palette,
                                width = pedalW,
                                height = pedalH,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_r2"),
                                onPressedChange = { down -> pushState(newThrottle = if (down) PadButton.R2.bit else 0) }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Racing Wheel
            val wheelSize = ((maxWidth.value * 0.66f) * controlScale).coerceIn(176f, 260f).dp
            val pedalW = (94f * controlScale).coerceIn(76f, 124f).dp
            val pedalH = (118f * controlScale).coerceIn(92f, 152f).dp
            val abxySize = (114f * controlScale).coerceIn(92f, 138f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 42.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShoulderBumper(
                        label = "L1",
                        isLeft = true,
                        pressed = l1Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width(92.dp)
                            .height(48.dp)
                            .testTag("pad_button_l"),
                        onPressedChange = { down -> pushState(newL1 = if (down) PadButton.L.bit else 0) }
                    )
                    ShoulderBumper(
                        label = "R1",
                        isLeft = false,
                        pressed = r1Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width(92.dp)
                            .height(48.dp)
                            .testTag("pad_button_r"),
                        onPressedChange = { down -> pushState(newR1 = if (down) PadButton.R.bit else 0) }
                    )
                }

                DraggableControlSlot(
                    offset = customization.leftStickOffset,
                    editMode = editLayoutMode,
                    onOffsetChange = { onCustomizationChange(customization.copy(leftStickOffset = it)) }
                ) {
                    InteractiveSteeringWheel(
                        size = wheelSize,
                        visualNorm = wheelVisualNorm,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier.testTag("pad_steering_wheel"),
                        onTouchSteerChanged = { sx -> pushState(newTouchSteerX = sx) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.leftShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(leftShoulderOffset = it)) }
                    ) {
                        RacingPedalButton(
                            label = "BRAKE",
                            subLabel = "L2",
                            pressed = brakeBit != 0,
                            pedalColor = PrecisionCrimson,
                            palette = palette,
                            width = pedalW,
                            height = pedalH,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_l2"),
                            onPressedChange = { down -> pushState(newBrake = if (down) PadButton.L2.bit else 0) }
                        )
                    }

                    DraggableControlSlot(
                        offset = customization.abxyOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(abxyOffset = it)) }
                    ) {
                        InteractiveAbxyCluster(
                            activeMask = totalMask,
                            accent = accent,
                            size = abxySize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_abxy"),
                            onAbxyMaskChange = { mask -> pushState(newAbxy = mask, isFace = true) }
                        )
                    }

                    DraggableControlSlot(
                        offset = customization.rightShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(rightShoulderOffset = it)) }
                    ) {
                        RacingPedalButton(
                            label = "GAS",
                            subLabel = "R2",
                            pressed = throttleBit != 0,
                            pedalColor = SignalEmerald,
                            palette = palette,
                            width = pedalW,
                            height = pedalH,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_r2"),
                            onPressedChange = { down -> pushState(newThrottle = if (down) PadButton.R2.bit else 0) }
                        )
                    }
                }

                DraggableControlSlot(
                    offset = customization.menuOffset,
                    editMode = editLayoutMode,
                    onOffsetChange = { onCustomizationChange(customization.copy(menuOffset = it)) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        MenuPillButton(
                            label = "SELECT",
                            pressed = selectBit != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_select"),
                            onPressedChange = { down -> pushState(newSelect = if (down) PadButton.SELECT.bit else 0) }
                        )
                        MenuPillButton(
                            label = "START",
                            pressed = startBit != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_start"),
                            onPressedChange = { down -> pushState(newStart = if (down) PadButton.START.bit else 0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveSteeringWheel(
    size: Dp,
    visualNorm: Float,
    palette: GamepadThemePalette,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTouchSteerChanged: (Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onTouchSteerChanged)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    var activeTouch by remember { mutableStateOf(false) }

    val clampedNorm = visualNorm.coerceIn(-1f, 1f)
    val wheelAngleDeg = clampedNorm * 115f

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Interactive racing steering wheel" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            var capturedId: PointerId? = null
                            var startAngle = 0f
                            var lastOutX = 0
                            val cx = sizePx / 2f
                            val cy = sizePx / 2f

                            while (true) {
                                val event = awaitPointerEvent()
                                if (capturedId == null) {
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        capturedId = down.id
                                        startAngle = atan2(down.position.y - cy, down.position.x - cx)
                                        activeTouch = true
                                    }
                                }
                                val current = capturedId?.let { id -> event.changes.firstOrNull { it.id == id } }
                                if (current == null || !current.pressed) {
                                    capturedId = null
                                    activeTouch = false
                                    if (lastOutX != 0) {
                                        lastOutX = 0
                                        latestCallback(0)
                                    }
                                } else {
                                    val curAngle = atan2(current.position.y - cy, current.position.x - cx)
                                    var delta = curAngle - startAngle
                                    while (delta > PI) delta -= (2.0 * PI).toFloat()
                                    while (delta < -PI) delta += (2.0 * PI).toFloat()
                                    val maxRad = Math.toRadians(105.0).toFloat()
                                    val outX = ((delta / maxRad).coerceIn(-1f, 1f) * 127f)
                                        .roundToInt()
                                        .coerceIn(-127, 127)
                                    if (outX != lastOutX) {
                                        lastOutX = outX
                                        latestCallback(outX)
                                    }
                                    current.consume()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val c = Offset(w / 2f, h / 2f)
            val rimR = w * 0.42f
            val hubR = w * 0.16f

            rotate(degrees = wheelAngleDeg, pivot = c) {
                // Wheel rim
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(palette.surfaceTop, palette.surfaceBottom, palette.surfaceTop),
                        center = c
                    ),
                    radius = rimR,
                    center = c,
                    style = Stroke(width = w * 0.088f)
                )
                drawCircle(
                    color = if (activeTouch || kotlin.math.abs(clampedNorm) > 0.05f) palette.accent else palette.rimBright,
                    radius = rimR + w * 0.044f,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = palette.rimDim,
                    radius = rimR - w * 0.044f,
                    center = c,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Top 12 o'clock centering stripe
                drawLine(
                    color = palette.accent,
                    start = Offset(c.x, c.y - rimR - w * 0.042f),
                    end = Offset(c.x, c.y - rimR + w * 0.042f),
                    strokeWidth = w * 0.042f,
                    cap = StrokeCap.Round
                )

                // Left, Right & Bottom spokes
                val spokeWidth = w * 0.062f
                drawLine(
                    color = palette.dpadBody,
                    start = Offset(c.x - hubR * 0.8f, c.y),
                    end = Offset(c.x - rimR + w * 0.03f, c.y),
                    strokeWidth = spokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = palette.dpadBody,
                    start = Offset(c.x + hubR * 0.8f, c.y),
                    end = Offset(c.x + rimR - w * 0.03f, c.y),
                    strokeWidth = spokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = palette.dpadBody,
                    start = Offset(c.x, c.y + hubR * 0.8f),
                    end = Offset(c.x, c.y + rimR - w * 0.03f),
                    strokeWidth = spokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Center telemetry hub (remains readable upright)
            drawCircle(
                color = themeWellBackground(palette),
                radius = hubR,
                center = c
            )
            drawCircle(
                color = palette.rimBright,
                radius = hubR,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }

        Text(
            text = "${(clampedNorm * 90f).roundToInt()}°",
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (kotlin.math.abs(clampedNorm) > 0.08f) palette.accent else palette.legendPressed
        )
    }
}

@Composable
private fun RacingPedalButton(
    label: String,
    subLabel: String,
    pressed: Boolean,
    pedalColor: Color,
    palette: GamepadThemePalette,
    width: Dp,
    height: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    val latestCallback by rememberUpdatedState(onPressedChange)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(45),
        label = "pedalScale"
    )
    val shape = RoundedCornerShape(20.dp)
    val fillBrush = Brush.verticalGradient(
        colors = if (pressed) {
            listOf(pedalColor.copy(alpha = 0.45f), pedalColor.copy(alpha = 0.16f))
        } else {
            themeIdleCapFill(palette)
        }
    )

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fillBrush)
            .border(
                BorderStroke(1.3.dp, if (pressed) pedalColor else palette.rimMid),
                shape
            )
            .semantics { contentDescription = "$label $subLabel pedal" }
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (stripe in 0..3) {
                Box(
                    modifier = Modifier
                        .width(width * 0.56f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (pressed) pedalColor.copy(alpha = 0.85f) else palette.rimMid
                        )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (pressed) palette.legendPressed else palette.legendIdle
            )
            Text(
                text = subLabel,
                fontFamily = SpaceGroteskFamily,
                fontSize = 10.sp,
                color = if (pressed) pedalColor else palette.legendIdle.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
fun ProDualStickControllerPad(
    slot: Int,
    hapticStrength: HapticStrength,
    stickDeadzonePct: Int,
    customization: ControlCustomization,
    paddleConfig: ProPaddleConfig,
    gyroAxes: AnalogAxes,
    editLayoutMode: Boolean,
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    onCustomizationChange: (ControlCustomization) -> Unit,
    onStateChanged: (buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = resolveGamepadPalette(controllerTheme, slot)
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)
    val latestHaptic by rememberUpdatedState(hapticStrength)
    val latestPaddleConfig by rememberUpdatedState(paddleConfig)

    var baseMask by remember { mutableIntStateOf(0) }
    var baseLx by remember { mutableIntStateOf(0) }
    var baseLy by remember { mutableIntStateOf(0) }
    var baseRx by remember { mutableIntStateOf(0) }
    var baseRy by remember { mutableIntStateOf(0) }
    var paddlePressedFlags by remember { mutableIntStateOf(0) }

    fun paddleMaskForFlags(flags: Int, cfg: ProPaddleConfig): Int {
        var bits = 0
        if ((flags and 1) != 0) bits = bits or cfg.m1.bit
        if ((flags and 2) != 0) bits = bits or cfg.m2.bit
        if ((flags and 4) != 0) bits = bits or cfg.m3.bit
        if ((flags and 8) != 0) bits = bits or cfg.m4.bit
        return bits
    }

    fun emitMerged(
        newBaseMask: Int = baseMask,
        lx: Byte = baseLx.toByte(),
        ly: Byte = baseLy.toByte(),
        rx: Byte = baseRx.toByte(),
        ry: Byte = baseRy.toByte(),
        newPaddleFlags: Int = paddlePressedFlags
    ) {
        val prevCombined = baseMask or paddleMaskForFlags(paddlePressedFlags, latestPaddleConfig)
        baseMask = newBaseMask
        baseLx = lx.toInt()
        baseLy = ly.toInt()
        baseRx = rx.toInt()
        baseRy = ry.toInt()
        paddlePressedFlags = newPaddleFlags
        val nextCombined = newBaseMask or paddleMaskForFlags(newPaddleFlags, latestPaddleConfig)
        if ((nextCombined and prevCombined.inv()) != 0 && newPaddleFlags != paddlePressedFlags) {
            performGamepadHaptic(context, view, latestHaptic, isFaceButton = true)
        }
        latestOnStateChanged(nextCombined, lx, ly, rx, ry)
    }

    Box(modifier = modifier.fillMaxSize()) {
        DualStickControllerPad(
            slot = slot,
            hapticStrength = hapticStrength,
            stickDeadzonePct = stickDeadzonePct,
            customization = customization,
            gyroAxes = gyroAxes,
            editLayoutMode = editLayoutMode,
            controllerTheme = controllerTheme,
            onCustomizationChange = onCustomizationChange,
            onStateChanged = { mask, lx, ly, rx, ry ->
                emitMerged(newBaseMask = mask, lx = lx, ly = ly, rx = rx, ry = ry)
            },
            modifier = Modifier.fillMaxSize()
        )

        DraggableControlSlot(
            offset = customization.paddlesOffset,
            editMode = editLayoutMode,
            onOffsetChange = { onCustomizationChange(customization.copy(paddlesOffset = it)) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .alpha(customization.opacity.coerceIn(0.35f, 1.0f))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val paddles = listOf(
                    Triple(1, "M1", paddleConfig.m1),
                    Triple(2, "M2", paddleConfig.m2),
                    Triple(4, "M3", paddleConfig.m3),
                    Triple(8, "M4", paddleConfig.m4)
                )
                paddles.forEach { (flag, tag, mappedButton) ->
                    val pressed = (paddlePressedFlags and flag) != 0
                    ShoulderBumper(
                        label = "$tag·${mappedButton.shortLabel}",
                        isLeft = flag == 1 || flag == 2,
                        pressed = pressed,
                        accent = palette.accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width(74.dp)
                            .height(48.dp)
                            .testTag("pad_paddle_${tag.lowercase()}"),
                        onPressedChange = { down ->
                            val nextFlags = if (down) paddlePressedFlags or flag else paddlePressedFlags and flag.inv()
                            if (down && (paddlePressedFlags and flag) == 0) {
                                performGamepadHaptic(context, view, latestHaptic, isFaceButton = true)
                            }
                            emitMerged(newPaddleFlags = nextFlags)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ArcadeFightControllerPad(
    slot: Int,
    hapticStrength: HapticStrength,
    customization: ControlCustomization,
    gyroAxes: AnalogAxes,
    editLayoutMode: Boolean,
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    onCustomizationChange: (ControlCustomization) -> Unit,
    onStateChanged: (buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = resolveGamepadPalette(controllerTheme, slot)
    val accent = palette.accent
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)
    val latestHaptic by rememberUpdatedState(hapticStrength)

    var gateBits by remember { mutableIntStateOf(0) }
    var sixGridBits by remember { mutableIntStateOf(0) }
    var l2Bit by remember { mutableIntStateOf(0) }
    var r2Bit by remember { mutableIntStateOf(0) }
    var selectBit by remember { mutableIntStateOf(0) }
    var startBit by remember { mutableIntStateOf(0) }

    fun pushState(
        newGate: Int = gateBits,
        newGrid: Int = sixGridBits,
        newL2: Int = l2Bit,
        newR2: Int = r2Bit,
        newSelect: Int = selectBit,
        newStart: Int = startBit,
        isFace: Boolean = false
    ) {
        val oldMask = gateBits or sixGridBits or l2Bit or r2Bit or selectBit or startBit
        gateBits = newGate
        sixGridBits = newGrid
        l2Bit = newL2
        r2Bit = newR2
        selectBit = newSelect
        startBit = newStart
        val newMask = gateBits or sixGridBits or l2Bit or r2Bit or selectBit or startBit
        if ((newMask and oldMask.inv()) != 0) {
            performGamepadHaptic(context, view, latestHaptic, isFaceButton = isFace)
        }
        latestOnStateChanged(newMask, gyroAxes.lx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
    }

    LaunchedEffect(gyroAxes) {
        val totalMask = gateBits or sixGridBits or l2Bit or r2Bit or selectBit or startBit
        latestOnStateChanged(totalMask, gyroAxes.lx, gyroAxes.ly, gyroAxes.rx, gyroAxes.ry)
    }

    val controlScale = customization.scale.coerceIn(0.72f, 1.35f)
    val controlAlpha = customization.opacity.coerceIn(0.35f, 1.0f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(controlAlpha)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val isWide = maxWidth >= maxHeight * 1.15f

        if (isWide) {
            val stickSize = (min(maxHeight.value * 0.66f, maxWidth.value * 0.28f) * controlScale)
                .coerceIn(148f, 246f).dp
            val btnSize = (min(maxHeight.value * 0.23f, maxWidth.value * 0.11f) * controlScale)
                .coerceIn(58f, 88f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Menu / Macro Triggers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.menuOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(menuOffset = it)) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuPillButton(
                                label = "COIN",
                                pressed = selectBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_select"),
                                onPressedChange = { down -> pushState(newSelect = if (down) PadButton.SELECT.bit else 0) }
                            )
                            MenuPillButton(
                                label = "1P START",
                                pressed = startBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_start"),
                                onPressedChange = { down -> pushState(newStart = if (down) PadButton.START.bit else 0) }
                            )
                        }
                    }

                    DraggableControlSlot(
                        offset = customization.rightShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(rightShoulderOffset = it)) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShoulderBumper(
                                label = "L2",
                                isLeft = true,
                                pressed = l2Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_l2"),
                                onPressedChange = { down -> pushState(newL2 = if (down) PadButton.L2.bit else 0) }
                            )
                            ShoulderBumper(
                                label = "R2",
                                isLeft = false,
                                pressed = r2Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_r2"),
                                onPressedChange = { down -> pushState(newR2 = if (down) PadButton.R2.bit else 0) }
                            )
                        }
                    }
                }

                // Main Arcade Deck: Octagonal Ball-Top Joystick | 6-Button Fight Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.dpadOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(dpadOffset = it)) }
                    ) {
                        ArcadeOctagonalStick(
                            activeMask = gateBits,
                            size = stickSize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_arcade_stick"),
                            onMaskChanged = { mask -> pushState(newGate = mask) }
                        )
                    }

                    DraggableControlSlot(
                        offset = customization.abxyOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = { onCustomizationChange(customization.copy(abxyOffset = it)) }
                    ) {
                        ArcadeSixButtonGrid(
                            activeMask = sixGridBits,
                            buttonSize = btnSize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_arcade_buttons"),
                            onGridMaskChanged = { mask -> pushState(newGrid = mask, isFace = true) }
                        )
                    }
                }
            }
        } else {
            // Portrait Arcade Fight Pad
            val stickSize = ((maxWidth.value * 0.44f) * controlScale).coerceIn(138f, 200f).dp
            val btnSize = ((maxWidth.value * 0.145f) * controlScale).coerceIn(50f, 70f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 42.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShoulderBumper(
                        label = "L2",
                        isLeft = true,
                        pressed = l2Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width(86.dp)
                            .height(48.dp)
                            .testTag("pad_button_l2"),
                        onPressedChange = { down -> pushState(newL2 = if (down) PadButton.L2.bit else 0) }
                    )
                    ShoulderBumper(
                        label = "R2",
                        isLeft = false,
                        pressed = r2Bit != 0,
                        accent = accent,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier
                            .width(86.dp)
                            .height(48.dp)
                            .testTag("pad_button_r2"),
                        onPressedChange = { down -> pushState(newR2 = if (down) PadButton.R2.bit else 0) }
                    )
                }

                DraggableControlSlot(
                    offset = customization.abxyOffset,
                    editMode = editLayoutMode,
                    onOffsetChange = { onCustomizationChange(customization.copy(abxyOffset = it)) }
                ) {
                    ArcadeSixButtonGrid(
                        activeMask = sixGridBits,
                        buttonSize = btnSize,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier.testTag("pad_arcade_buttons"),
                        onGridMaskChanged = { mask -> pushState(newGrid = mask, isFace = true) }
                    )
                }

                DraggableControlSlot(
                    offset = customization.dpadOffset,
                    editMode = editLayoutMode,
                    onOffsetChange = { onCustomizationChange(customization.copy(dpadOffset = it)) }
                ) {
                    ArcadeOctagonalStick(
                        activeMask = gateBits,
                        size = stickSize,
                        palette = palette,
                        enabled = !editLayoutMode,
                        modifier = Modifier.testTag("pad_arcade_stick"),
                        onMaskChanged = { mask -> pushState(newGate = mask) }
                    )
                }

                DraggableControlSlot(
                    offset = customization.menuOffset,
                    editMode = editLayoutMode,
                    onOffsetChange = { onCustomizationChange(customization.copy(menuOffset = it)) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        MenuPillButton(
                            label = "COIN",
                            pressed = selectBit != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_select"),
                            onPressedChange = { down -> pushState(newSelect = if (down) PadButton.SELECT.bit else 0) }
                        )
                        MenuPillButton(
                            label = "1P START",
                            pressed = startBit != 0,
                            accent = accent,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_button_start"),
                            onPressedChange = { down -> pushState(newStart = if (down) PadButton.START.bit else 0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArcadeOctagonalStick(
    activeMask: Int,
    size: Dp,
    palette: GamepadThemePalette,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onMaskChanged: (Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onMaskChanged)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    var knobOffsetX by remember { mutableFloatStateOf(0f) }
    var knobOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Arcade 8-way octagonal gate stick" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            var capturedId: PointerId? = null
                            var lastMask = 0
                            val c = sizePx / 2f
                            val deadzone = sizePx * 0.11f
                            val maxTravel = sizePx * 0.28f

                            while (true) {
                                val event = awaitPointerEvent()
                                if (capturedId == null) {
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) capturedId = down.id
                                }
                                val current = capturedId?.let { id -> event.changes.firstOrNull { it.id == id } }
                                val newMask: Int
                                if (current == null || !current.pressed) {
                                    capturedId = null
                                    knobOffsetX = 0f
                                    knobOffsetY = 0f
                                    newMask = 0
                                } else {
                                    val dx = current.position.x - c
                                    val dy = current.position.y - c
                                    val dist = hypot(dx, dy)
                                    val clamped = min(dist, maxTravel)
                                    val sc = if (dist > 0.001f) clamped / dist else 0f
                                    knobOffsetX = (dx * sc) / maxTravel
                                    knobOffsetY = (dy * sc) / maxTravel

                                    var m = 0
                                    if (dist >= deadzone) {
                                        val nx = dx / dist
                                        val ny = dy / dist
                                        if (nx >= 0.3827f) m = m or PadButton.RIGHT.bit
                                        if (nx <= -0.3827f) m = m or PadButton.LEFT.bit
                                        if (ny >= 0.3827f) m = m or PadButton.DOWN.bit
                                        if (ny <= -0.3827f) m = m or PadButton.UP.bit
                                    }
                                    newMask = m
                                    current.consume()
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val c = Offset(w / 2f, h / 2f)
            val wellR = w * 0.46f
            val gateR = w * 0.35f
            val ballR = w * 0.21f
            val active = activeMask != 0

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeWellBackground(palette), palette.surfaceBottom),
                    center = c,
                    radius = wellR
                ),
                radius = wellR,
                center = c
            )
            drawCircle(
                color = if (active) palette.accent.copy(alpha = 0.75f) else palette.rimBright,
                radius = wellR,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )

            // Octagonal 8-way gate lines
            val octPath = androidx.compose.ui.graphics.Path()
            for (k in 0..7) {
                val angle = (k * 45.0 + 22.5) * PI / 180.0
                val px = c.x + (gateR * cos(angle)).toFloat()
                val py = c.y + (gateR * sin(angle)).toFloat()
                if (k == 0) octPath.moveTo(px, py) else octPath.lineTo(px, py)
            }
            octPath.close()
            drawPath(
                path = octPath,
                color = palette.rimMid,
                style = Stroke(width = 1.1.dp.toPx())
            )

            val shift = w * 0.26f
            val ballCenter = Offset(c.x + knobOffsetX * shift, c.y + knobOffsetY * shift)

            // Metallic shaft from dust washer to ball top
            drawLine(
                color = palette.rimBright,
                start = c,
                end = ballCenter,
                strokeWidth = w * 0.065f,
                cap = StrokeCap.Round
            )

            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.accent.copy(alpha = 0.38f), Color.Transparent),
                        center = ballCenter,
                        radius = ballR * 1.45f
                    ),
                    radius = ballR * 1.45f,
                    center = ballCenter
                )
            }

            // Glossy ball top
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (active) {
                        listOf(Color.White.copy(alpha = 0.8f), palette.accent, palette.accent.copy(alpha = 0.7f))
                    } else {
                        listOf(palette.rimBright, palette.surfaceTop, palette.surfaceBottom)
                    },
                    center = Offset(ballCenter.x - ballR * 0.28f, ballCenter.y - ballR * 0.28f),
                    radius = ballR * 1.2f
                ),
                radius = ballR,
                center = ballCenter
            )
            drawCircle(
                color = if (active) palette.accent else palette.rimBright,
                radius = ballR,
                center = ballCenter,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}

@Composable
private fun ArcadeSixButtonGrid(
    activeMask: Int,
    buttonSize: Dp,
    palette: GamepadThemePalette,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onGridMaskChanged: (Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onGridMaskChanged)
    val density = LocalDensity.current
    val btnPx = with(density) { buttonSize.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }
    val staggerPx = with(density) { 14.dp.toPx() }

    val totalW = buttonSize * 3 + 20.dp + 12.dp
    val totalH = buttonSize * 2 + 12.dp + 16.dp

    val buttons = listOf(
        Triple(PadButton.X, "X", Offset(btnPx * 0.5f, btnPx * 0.5f + staggerPx)),
        Triple(PadButton.Y, "Y", Offset(btnPx * 1.5f + gapPx, btnPx * 0.5f)),
        Triple(PadButton.L, "L1", Offset(btnPx * 2.5f + gapPx * 2f, btnPx * 0.5f + staggerPx * 0.5f)),
        Triple(PadButton.A, "A", Offset(btnPx * 0.5f, btnPx * 1.5f + gapPx + staggerPx)),
        Triple(PadButton.B, "B", Offset(btnPx * 1.5f + gapPx, btnPx * 1.5f + gapPx)),
        Triple(PadButton.R, "R1", Offset(btnPx * 2.5f + gapPx * 2f, btnPx * 1.5f + gapPx + staggerPx * 0.5f))
    )

    Box(
        modifier = modifier
            .size(width = totalW, height = totalH)
            .semantics { contentDescription = "6-button arcade fight pad grid" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(btnPx) {
                        awaitPointerEventScope {
                            val activePointers = mutableMapOf<PointerId, Offset>()
                            var lastMask = 0
                            val hitR = btnPx * 0.62f

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
                                    for ((btn, _, center) in buttons) {
                                        if (hypot(pos.x - center.x, pos.y - center.y) <= hitR) {
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
        val radiusPx = btnPx / 2f
        buttons.forEach { (btn, _, center) ->
            val pressed = (activeMask and btn.bit) != 0
            val btnAccent = palette.faceButtonAccent(btn)
            val labelText = palette.faceButtonLabel(btn)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - radiusPx).roundToInt(),
                            (center.y - radiusPx).roundToInt()
                        )
                    }
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            if (pressed) themePressedCapFill(palette, btnAccent) else themeIdleCapFill(palette)
                        )
                    )
                    .border(
                        BorderStroke(
                            if (pressed) 1.8.dp else 1.2.dp,
                            if (pressed) btnAccent else btnAccent.copy(alpha = 0.50f)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelText,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (pressed) palette.legendPressed else palette.legendIdle
                )
            }
        }
    }
}
