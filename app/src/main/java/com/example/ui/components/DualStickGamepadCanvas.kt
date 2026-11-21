package com.example.ui.components

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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.AnalogAxes
import com.example.protocol.ControlCustomization
import com.example.protocol.ControllerTheme
import com.example.protocol.HapticStrength
import com.example.protocol.PadButton
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.GlassFillBase
import com.example.ui.theme.GlassFillElevated
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimBright
import com.example.ui.theme.GlassRimDim
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OledWell
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TitaniumText
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DualStickControllerPad(
    slot: Int,
    hapticStrength: HapticStrength,
    stickDeadzonePct: Int,
    customization: ControlCustomization,
    gyroAxes: AnalogAxes,
    editLayoutMode: Boolean,
    onCustomizationChange: (ControlCustomization) -> Unit,
    onStateChanged: (buttonMask: Int, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit,
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = resolveGamepadPalette(controllerTheme, slot)
    val accent = palette.accent
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)
    val latestHaptic by rememberUpdatedState(hapticStrength)

    var dpadBits by remember { mutableIntStateOf(0) }
    var abxyBits by remember { mutableIntStateOf(0) }
    var l1Bit by remember { mutableIntStateOf(0) }
    var l2Bit by remember { mutableIntStateOf(0) }
    var l3Bit by remember { mutableIntStateOf(0) }
    var r1Bit by remember { mutableIntStateOf(0) }
    var r2Bit by remember { mutableIntStateOf(0) }
    var r3Bit by remember { mutableIntStateOf(0) }
    var selectBit by remember { mutableIntStateOf(0) }
    var startBit by remember { mutableIntStateOf(0) }

    var stickLx by remember { mutableIntStateOf(0) }
    var stickLy by remember { mutableIntStateOf(0) }
    var stickRx by remember { mutableIntStateOf(0) }
    var stickRy by remember { mutableIntStateOf(0) }

    fun pushState(
        newDpad: Int = dpadBits,
        newAbxy: Int = abxyBits,
        newL1: Int = l1Bit,
        newL2: Int = l2Bit,
        newL3: Int = l3Bit,
        newR1: Int = r1Bit,
        newR2: Int = r2Bit,
        newR3: Int = r3Bit,
        newSelect: Int = selectBit,
        newStart: Int = startBit,
        newLx: Int = stickLx,
        newLy: Int = stickLy,
        newRx: Int = stickRx,
        newRy: Int = stickRy,
        isFace: Boolean = false
    ) {
        val oldMask = dpadBits or abxyBits or l1Bit or l2Bit or l3Bit or r1Bit or r2Bit or r3Bit or selectBit or startBit
        dpadBits = newDpad
        abxyBits = newAbxy
        l1Bit = newL1
        l2Bit = newL2
        l3Bit = newL3
        r1Bit = newR1
        r2Bit = newR2
        r3Bit = newR3
        selectBit = newSelect
        startBit = newStart
        stickLx = newLx
        stickLy = newLy
        stickRx = newRx
        stickRy = newRy

        val newMask = newDpad or newAbxy or newL1 or newL2 or newL3 or newR1 or newR2 or newR3 or selectBit or startBit
        val newlyPressed = newMask and oldMask.inv()
        if (newlyPressed != 0) {
            performGamepadHaptic(context, view, latestHaptic, isFaceButton = isFace)
        }

        val combinedLx = (stickLx + gyroAxes.lx.toInt()).coerceIn(-127, 127).toByte()
        val combinedLy = (stickLy + gyroAxes.ly.toInt()).coerceIn(-127, 127).toByte()
        val combinedRx = (stickRx + gyroAxes.rx.toInt()).coerceIn(-127, 127).toByte()
        val combinedRy = (stickRy + gyroAxes.ry.toInt()).coerceIn(-127, 127).toByte()
        latestOnStateChanged(newMask, combinedLx, combinedLy, combinedRx, combinedRy)
    }

    LaunchedEffect(gyroAxes) {
        val totalMask = dpadBits or abxyBits or l1Bit or l2Bit or l3Bit or r1Bit or r2Bit or r3Bit or selectBit or startBit
        val combinedLx = (stickLx + gyroAxes.lx.toInt()).coerceIn(-127, 127).toByte()
        val combinedLy = (stickLy + gyroAxes.ly.toInt()).coerceIn(-127, 127).toByte()
        val combinedRx = (stickRx + gyroAxes.rx.toInt()).coerceIn(-127, 127).toByte()
        val combinedRy = (stickRy + gyroAxes.ry.toInt()).coerceIn(-127, 127).toByte()
        latestOnStateChanged(totalMask, combinedLx, combinedLy, combinedRx, combinedRy)
    }

    val totalMask = dpadBits or abxyBits or l1Bit or l2Bit or l3Bit or r1Bit or r2Bit or r3Bit or selectBit or startBit
    val controlScale = customization.scale.coerceIn(0.72f, 1.35f)
    val controlAlpha = customization.opacity.coerceIn(0.35f, 1.0f)
    val deadzoneFrac = (stickDeadzonePct.coerceIn(0, 25)) / 100f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(controlAlpha)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val isWide = maxWidth >= maxHeight * 1.15f

        if (isWide) {
            val primarySize = (min(maxHeight.value * 0.44f, maxWidth.value * 0.19f) * controlScale)
                .coerceIn(106f, 178f).dp
            val secondarySize = (primarySize.value * 0.88f).coerceIn(94f, 158f).dp
            val triggerW = (86f * controlScale).coerceIn(70f, 114f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Shoulder Row: L2 + L1  |  (Top-middle settings lives above)  |  R1 + R2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.leftShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(leftShoulderOffset = it))
                        }
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
                                    .width(triggerW)
                                    .height(48.dp)
                                    .testTag("pad_button_l2"),
                                onPressedChange = { down ->
                                    pushState(newL2 = if (down) PadButton.L2.bit else 0)
                                }
                            )
                            ShoulderBumper(
                                label = "L1",
                                isLeft = false,
                                pressed = l1Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(triggerW)
                                    .height(48.dp)
                                    .testTag("pad_button_l"),
                                onPressedChange = { down ->
                                    pushState(newL1 = if (down) PadButton.L.bit else 0)
                                }
                            )
                        }
                    }

                    DraggableControlSlot(
                        offset = customization.rightShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(rightShoulderOffset = it))
                        }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShoulderBumper(
                                label = "R1",
                                isLeft = true,
                                pressed = r1Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(triggerW)
                                    .height(48.dp)
                                    .testTag("pad_button_r"),
                                onPressedChange = { down ->
                                    pushState(newR1 = if (down) PadButton.R.bit else 0)
                                }
                            )
                            ShoulderBumper(
                                label = "R2",
                                isLeft = false,
                                pressed = r2Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(triggerW)
                                    .height(48.dp)
                                    .testTag("pad_button_r2"),
                                onPressedChange = { down ->
                                    pushState(newR2 = if (down) PadButton.R2.bit else 0)
                                }
                            )
                        }
                    }
                }

                // Dual Wings + Center Menu Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Wing: Left Analog Stick + D-Pad
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DraggableControlSlot(
                            offset = customization.leftStickOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(leftStickOffset = it))
                            }
                        ) {
                            InteractiveAnalogStick(
                                label = "L",
                                accent = accent,
                                size = primarySize,
                                deadzoneFrac = deadzoneFrac,
                                palette = palette,
                                externalNormX = gyroAxes.lx / 127f,
                                externalNormY = gyroAxes.ly / 127f,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_left_stick"),
                                onAxisChanged = { nx, ny ->
                                    pushState(newLx = nx, newLy = ny)
                                }
                            )
                        }
                        DraggableControlSlot(
                            offset = customization.dpadOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(dpadOffset = it))
                            },
                            modifier = Modifier.padding(start = 52.dp)
                        ) {
                            InteractiveCrossDpad(
                                activeMask = totalMask,
                                accent = accent,
                                size = secondarySize,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_dpad"),
                                onDpadMaskChange = { mask -> pushState(newDpad = mask) }
                            )
                        }
                    }

                    // Center Cluster: L3 / SELECT / START / R3
                    DraggableControlSlot(
                        offset = customization.menuOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(menuOffset = it))
                        },
                        modifier = Modifier.align(Alignment.Bottom)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactThumbClickButton(
                                    label = "L3",
                                    pressed = l3Bit != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_l3"),
                                    onPressedChange = { down ->
                                        pushState(newL3 = if (down) PadButton.L3.bit else 0)
                                    }
                                )
                                CompactThumbClickButton(
                                    label = "R3",
                                    pressed = r3Bit != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_r3"),
                                    onPressedChange = { down ->
                                        pushState(newR3 = if (down) PadButton.R3.bit else 0)
                                    }
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MenuPillButton(
                                    label = "SELECT",
                                    pressed = selectBit != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_select"),
                                    onPressedChange = { down ->
                                        pushState(newSelect = if (down) PadButton.SELECT.bit else 0)
                                    }
                                )
                                MenuPillButton(
                                    label = "START",
                                    pressed = startBit != 0,
                                    accent = accent,
                                    palette = palette,
                                    enabled = !editLayoutMode,
                                    modifier = Modifier.testTag("pad_button_start"),
                                    onPressedChange = { down ->
                                        pushState(newStart = if (down) PadButton.START.bit else 0)
                                    }
                                )
                            }
                        }
                    }

                    // Right Wing: ABXY + Right Analog Stick
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                size = primarySize,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_abxy"),
                                onAbxyMaskChange = { mask -> pushState(newAbxy = mask, isFace = true) }
                            )
                        }
                        DraggableControlSlot(
                            offset = customization.rightStickOffset,
                            editMode = editLayoutMode,
                            onOffsetChange = {
                                onCustomizationChange(customization.copy(rightStickOffset = it))
                            },
                            modifier = Modifier.padding(end = 52.dp)
                        ) {
                            InteractiveAnalogStick(
                                label = "R",
                                accent = accent,
                                size = secondarySize,
                                deadzoneFrac = deadzoneFrac,
                                palette = palette,
                                externalNormX = gyroAxes.rx / 127f,
                                externalNormY = gyroAxes.ry / 127f,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_right_stick"),
                                onAxisChanged = { nx, ny ->
                                    pushState(newRx = nx, newRy = ny)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Dual Stick Layout
            val primarySize = ((maxWidth.value * 0.37f) * controlScale).coerceIn(112f, 168f).dp
            val secondarySize = (primarySize.value * 0.88f).coerceIn(96f, 148f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 42.dp, bottom = 10.dp),
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
                        }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShoulderBumper(
                                label = "L2",
                                isLeft = true,
                                pressed = l2Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_l2"),
                                onPressedChange = { down ->
                                    pushState(newL2 = if (down) PadButton.L2.bit else 0)
                                }
                            )
                            ShoulderBumper(
                                label = "L1",
                                isLeft = false,
                                pressed = l1Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_l"),
                                onPressedChange = { down ->
                                    pushState(newL1 = if (down) PadButton.L.bit else 0)
                                }
                            )
                        }
                    }

                    DraggableControlSlot(
                        offset = customization.rightShoulderOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(rightShoulderOffset = it))
                        }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShoulderBumper(
                                label = "R1",
                                isLeft = true,
                                pressed = r1Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_r"),
                                onPressedChange = { down ->
                                    pushState(newR1 = if (down) PadButton.R.bit else 0)
                                }
                            )
                            ShoulderBumper(
                                label = "R2",
                                isLeft = false,
                                pressed = r2Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(48.dp)
                                    .testTag("pad_button_r2"),
                                onPressedChange = { down ->
                                    pushState(newR2 = if (down) PadButton.R2.bit else 0)
                                }
                            )
                        }
                    }
                }

                // Upper Sticks / Face Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlSlot(
                        offset = customization.leftStickOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(leftStickOffset = it))
                        }
                    ) {
                        InteractiveAnalogStick(
                            label = "L",
                            accent = accent,
                            size = primarySize,
                            deadzoneFrac = deadzoneFrac,
                            palette = palette,
                            externalNormX = gyroAxes.lx / 127f,
                            externalNormY = gyroAxes.ly / 127f,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_left_stick"),
                            onAxisChanged = { nx, ny -> pushState(newLx = nx, newLy = ny) }
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
                            size = primarySize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_abxy"),
                            onAbxyMaskChange = { mask -> pushState(newAbxy = mask, isFace = true) }
                        )
                    }
                }

                // Lower D-Pad / Right Stick Row
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
                            size = secondarySize,
                            palette = palette,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_dpad"),
                            onDpadMaskChange = { mask -> pushState(newDpad = mask) }
                        )
                    }

                    DraggableControlSlot(
                        offset = customization.rightStickOffset,
                        editMode = editLayoutMode,
                        onOffsetChange = {
                            onCustomizationChange(customization.copy(rightStickOffset = it))
                        }
                    ) {
                        InteractiveAnalogStick(
                            label = "R",
                            accent = accent,
                            size = secondarySize,
                            deadzoneFrac = deadzoneFrac,
                            palette = palette,
                            externalNormX = gyroAxes.rx / 127f,
                            externalNormY = gyroAxes.ry / 127f,
                            enabled = !editLayoutMode,
                            modifier = Modifier.testTag("pad_right_stick"),
                            onAxisChanged = { nx, ny -> pushState(newRx = nx, newRy = ny) }
                        )
                    }
                }

                // Bottom Menu & L3/R3 Strip
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactThumbClickButton(
                                label = "L3",
                                pressed = l3Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_l3"),
                                onPressedChange = { down ->
                                    pushState(newL3 = if (down) PadButton.L3.bit else 0)
                                }
                            )
                            MenuPillButton(
                                label = "SELECT",
                                pressed = selectBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_select"),
                                onPressedChange = { down ->
                                    pushState(newSelect = if (down) PadButton.SELECT.bit else 0)
                                }
                            )
                            MenuPillButton(
                                label = "START",
                                pressed = startBit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_start"),
                                onPressedChange = { down ->
                                    pushState(newStart = if (down) PadButton.START.bit else 0)
                                }
                            )
                            CompactThumbClickButton(
                                label = "R3",
                                pressed = r3Bit != 0,
                                accent = accent,
                                palette = palette,
                                enabled = !editLayoutMode,
                                modifier = Modifier.testTag("pad_button_r3"),
                                onPressedChange = { down ->
                                    pushState(newR3 = if (down) PadButton.R3.bit else 0)
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
internal fun CompactThumbClickButton(
    label: String,
    pressed: Boolean,
    accent: Color,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    val latestCallback by rememberUpdatedState(onPressedChange)
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .clip(CircleShape)
            .background(if (pressed) accent.copy(alpha = 0.28f) else themeSubtleWell(palette))
            .border(
                BorderStroke(1.dp, if (pressed) accent else palette.rimMid),
                CircleShape
            )
            .semantics { contentDescription = "$label stick button" }
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
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
            color = if (pressed) palette.legendPressed else palette.legendIdle
        )
    }
}

@Composable
fun InteractiveAnalogStick(
    label: String,
    accent: Color,
    size: Dp,
    deadzoneFrac: Float = 0.08f,
    enabled: Boolean = true,
    palette: GamepadThemePalette = resolveGamepadPalette(ControllerTheme.LIQUID_GLASS, 1),
    externalNormX: Float = 0f,
    externalNormY: Float = 0f,
    modifier: Modifier = Modifier,
    onAxisChanged: (x: Int, y: Int) -> Unit
) {
    val latestCallback by rememberUpdatedState(onAxisChanged)
    val latestDeadzone by rememberUpdatedState(deadzoneFrac)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val maxTravel = sizePx * 0.29f

    var knobNormX by remember { mutableFloatStateOf(0f) }
    var knobNormY by remember { mutableFloatStateOf(0f) }
    var activeTouch by remember { mutableStateOf(false) }

    val displayNormX = (knobNormX + externalNormX).coerceIn(-1f, 1f)
    val displayNormY = (knobNormY + externalNormY).coerceIn(-1f, 1f)
    val gyroDeflected = !activeTouch && (kotlin.math.abs(externalNormX) > 0.06f || kotlin.math.abs(externalNormY) > 0.06f)

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "$label analog stick" }
            .then(
                if (enabled) {
                    Modifier.pointerInput(sizePx) {
                        awaitPointerEventScope {
                            var capturedId: PointerId? = null
                            var origin = Offset(sizePx / 2f, sizePx / 2f)
                            var lastOutX = 0
                            var lastOutY = 0

                            while (true) {
                                val event = awaitPointerEvent()
                                if (capturedId == null) {
                                    val downChange = event.changes.firstOrNull { it.pressed }
                                    if (downChange != null) {
                                        capturedId = downChange.id
                                        origin = Offset(sizePx / 2f, sizePx / 2f)
                                        activeTouch = true
                                    }
                                }

                                val currentChange = capturedId?.let { id ->
                                    event.changes.firstOrNull { it.id == id }
                                }

                                if (currentChange == null || !currentChange.pressed) {
                                    capturedId = null
                                    activeTouch = false
                                    knobNormX = 0f
                                    knobNormY = 0f
                                    if (lastOutX != 0 || lastOutY != 0) {
                                        lastOutX = 0
                                        lastOutY = 0
                                        latestCallback(0, 0)
                                    }
                                } else {
                                    val dx = currentChange.position.x - origin.x
                                    val dy = currentChange.position.y - origin.y
                                    val dist = hypot(dx, dy)
                                    val clampedDist = min(dist, maxTravel)
                                    val scale = if (dist > 0.001f) clampedDist / dist else 0f
                                    val cx = dx * scale
                                    val cy = dy * scale
                                    knobNormX = (cx / maxTravel).coerceIn(-1f, 1f)
                                    knobNormY = (cy / maxTravel).coerceIn(-1f, 1f)

                                    val mag = hypot(knobNormX, knobNormY)
                                    val dz = latestDeadzone.coerceIn(0f, 0.30f)
                                    val (outX, outY) = if (mag < dz) {
                                        0 to 0
                                    } else {
                                        val rescaled = ((mag - dz) / (1f - dz)).coerceIn(0f, 1f)
                                        val dirX = knobNormX / mag
                                        val dirY = knobNormY / mag
                                        (dirX * rescaled * 127f).roundToInt().coerceIn(-127, 127) to
                                            (dirY * rescaled * 127f).roundToInt().coerceIn(-127, 127)
                                    }
                                    if (outX != lastOutX || outY != lastOutY) {
                                        lastOutX = outX
                                        lastOutY = outY
                                        latestCallback(outX, outY)
                                    }
                                    currentChange.consume()
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
            val outerR = w * 0.48f
            val gateR = w * 0.36f
            val capR = w * 0.27f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(themeWellBackground(palette), palette.surfaceBottom),
                    center = c,
                    radius = outerR
                ),
                radius = outerR,
                center = c
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(
                        if (activeTouch || gyroDeflected) accent.copy(alpha = 0.72f) else palette.rimBright,
                        palette.rimDim
                    )
                ),
                radius = outerR,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawCircle(
                color = palette.rimMid,
                radius = gateR,
                center = c,
                style = Stroke(width = 0.8.dp.toPx())
            )

            val shift = maxTravel * 0.85f
            val capCenter = Offset(
                x = c.x + displayNormX * shift,
                y = c.y + displayNormY * shift
            )

            if (activeTouch || gyroDeflected) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.32f), Color.Transparent),
                        center = capCenter,
                        radius = capR * 1.45f
                    ),
                    radius = capR * 1.45f,
                    center = capCenter
                )
            }

            drawCircle(
                brush = Brush.verticalGradient(
                    colors = if (activeTouch || gyroDeflected) {
                        themePressedCapFill(palette, accent)
                    } else {
                        themeIdleCapFill(palette)
                    }
                ),
                radius = capR,
                center = capCenter
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(
                        if (activeTouch || gyroDeflected) accent else palette.rimBright,
                        if (activeTouch || gyroDeflected) accent.copy(alpha = 0.4f) else palette.rimDim
                    )
                ),
                radius = capR,
                center = capCenter,
                style = Stroke(width = if (activeTouch || gyroDeflected) 1.8.dp.toPx() else 1.2.dp.toPx())
            )
            drawCircle(
                color = palette.wellColor.copy(alpha = 0.55f),
                radius = capR * 0.56f,
                center = capCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        val shiftDp = with(density) { (maxTravel * 0.85f).toDp() }
        Text(
            text = label,
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (activeTouch || gyroDeflected) palette.legendPressed else palette.legendIdle.copy(alpha = 0.78f),
            modifier = Modifier.graphicsLayer {
                translationX = (displayNormX * shiftDp.toPx())
                translationY = (displayNormY * shiftDp.toPx())
            }
        )
    }
}
