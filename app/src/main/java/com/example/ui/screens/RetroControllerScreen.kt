package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.protocol.AnalogAxes
import com.example.protocol.ControlCustomization
import com.example.protocol.ControllerLayout
import com.example.protocol.GyroMode
import com.example.protocol.HapticStrength
import com.example.transport.BluetoothHidState
import com.example.transport.ClientConnectionState
import com.example.ui.components.DualStickControllerPad
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.RetroControllerPad
import com.example.ui.theme.BrushedSilver
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.GlassFillSubtle
import com.example.ui.theme.GlassRimMid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PewterMuted
import com.example.ui.theme.PrecisionCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TitaniumText
import kotlin.math.roundToInt

private enum class OrientationChoice(val label: String, val orientationFlag: Int) {
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    AUTO("Auto", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
}

@Composable
fun RetroControllerScreen(
    connectionState: ClientConnectionState,
    hidState: BluetoothHidState,
    controllerLayout: ControllerLayout,
    hapticStrength: HapticStrength,
    gyroMode: GyroMode,
    stickDeadzonePct: Int,
    customization: ControlCustomization,
    onSelectLayout: (ControllerLayout) -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onSelectGyroMode: (GyroMode) -> Unit,
    onChangeDeadzonePct: (Int) -> Unit,
    onChangeCustomization: (ControlCustomization) -> Unit,
    onResetCustomization: () -> Unit,
    onSendControllerState: (buttonMask: Int, layout: ControllerLayout, lx: Byte, ly: Byte, rx: Byte, ry: Byte) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    var orientationChoice by remember { mutableStateOf(OrientationChoice.LANDSCAPE) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var editLayoutMode by remember { mutableStateOf(false) }
    var gyroAxes by remember { mutableStateOf(AnalogAxes()) }
    var latestMask by remember { mutableIntStateOf(0) }

    fun applyImmersiveFullscreen() {
        val window = activity?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            if (lp.layoutInDisplayCutoutMode !=
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            ) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        applyImmersiveFullscreen()
    }

    DisposableEffect(orientationChoice) {
        activity?.requestedOrientation = orientationChoice.orientationFlag
        applyImmersiveFullscreen()
        onDispose { }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val prevCutoutMode = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            0
        }
        applyImmersiveFullscreen()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = prevCutoutMode
                    window.attributes = lp
                }
            }
        }
    }

    DisposableEffect(gyroMode) {
        if (gyroMode == GyroMode.OFF) {
            gyroAxes = AnalogAxes()
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val sensitivity = 42f
                    val yaw = (-event.values[2] * sensitivity).roundToInt().coerceIn(-127, 127).toByte()
                    val pitch = (-event.values[0] * sensitivity).roundToInt().coerceIn(-127, 127).toByte()
                    gyroAxes = when (gyroMode) {
                        GyroMode.STEER_LEFT_STICK -> AnalogAxes(lx = yaw, ly = pitch)
                        GyroMode.AIM_RIGHT_STICK -> AnalogAxes(rx = yaw, ry = pitch)
                        GyroMode.OFF -> AnalogAxes()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
                gyroAxes = AnalogAxes()
            }
        }
    }

    BackHandler {
        when {
            showSettingsModal -> showSettingsModal = false
            editLayoutMode -> editLayoutMode = false
            else -> onDisconnect()
        }
    }

    val connected = connectionState as? ClientConnectionState.Connected
    val isHidActive = hidState is BluetoothHidState.Connected || hidState is BluetoothHidState.Connecting
    val slot = connected?.assignedSlot ?: 1

    LiquidGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            when (controllerLayout) {
                ControllerLayout.RETRO_HANDHELD -> {
                    RetroControllerPad(
                        slot = slot,
                        hapticStrength = hapticStrength,
                        customization = customization,
                        editLayoutMode = editLayoutMode,
                        onCustomizationChange = onChangeCustomization,
                        onMaskChanged = { mask ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.RETRO_HANDHELD, 0, 0, 0, 0)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ControllerLayout.DUAL_STICK -> {
                    DualStickControllerPad(
                        slot = slot,
                        hapticStrength = hapticStrength,
                        stickDeadzonePct = stickDeadzonePct,
                        customization = customization,
                        gyroAxes = gyroAxes,
                        editLayoutMode = editLayoutMode,
                        onCustomizationChange = onChangeCustomization,
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.DUAL_STICK, lx, ly, rx, ry)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Minimal Top-Middle Settings Hub (no P1 / IP / latency HUD on controller canvas)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editLayoutMode) {
                    LiquidGlassPanel(
                        shape = CircleShape,
                        elevated = true,
                        accentBorderTint = SignalEmerald,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { editLayoutMode = false }
                            .testTag("finish_layout_edit_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done editing positions",
                                tint = SignalEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Done",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = TitaniumText
                            )
                        }
                    }

                    LiquidGlassPanel(
                        shape = CircleShape,
                        subtle = true,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { onResetCustomization() }
                            .testTag("reset_layout_edit_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset positions",
                                tint = BrushedSilver,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Reset",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = BrushedSilver
                            )
                        }
                    }
                } else {
                    LiquidGlassPanel(
                        shape = CircleShape,
                        elevated = true,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { showSettingsModal = true }
                            .testTag("controller_settings_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Controller Settings",
                                tint = BrushedSilver,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Settings",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = BrushedSilver
                            )
                        }
                    }
                }
            }

            // Disconnected Overlay (skipped when native Bluetooth HID session is connected/connecting)
            if (!isHidActive &&
                (connectionState is ClientConnectionState.Error ||
                    connectionState is ClientConnectionState.Disconnected)
            ) {
                val msg = (connectionState as? ClientConnectionState.Error)?.message
                    ?: "Disconnected"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(22.dp),
                        elevated = true,
                        accentBorderTint = PrecisionCrimson
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Disconnected",
                                style = MaterialTheme.typography.titleLarge,
                                color = TitaniumText
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PewterMuted
                            )
                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrushedSilver,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("return_to_discovery_button")
                            ) {
                                Text("Reconnect")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsModal) {
        Dialog(
            onDismissRequest = { showSettingsModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg.copy(alpha = 0.55f))
                    .clickable(onClick = { showSettingsModal = false })
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassPanel(
                    shape = RoundedCornerShape(26.dp),
                    elevated = true,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Modal Header with Disconnect X & Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Controller Settings",
                                style = MaterialTheme.typography.titleLarge,
                                color = TitaniumText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showSettingsModal = false
                                        onDisconnect()
                                    },
                                    border = BorderStroke(1.dp, PrecisionCrimson.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = PrecisionCrimson.copy(alpha = 0.14f),
                                        contentColor = PrecisionCrimson
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("disconnect_controller_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Disconnect",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Layout Mode Switcher: Dual Stick vs Retro
                        SettingChipSection(
                            label = "CONTROLLER LAYOUT",
                            options = ControllerLayout.entries.map { it.label },
                            selectedIndex = ControllerLayout.entries.indexOf(controllerLayout),
                            testTagPrefix = "layout_option",
                            onSelect = { idx ->
                                onSelectLayout(ControllerLayout.entries[idx])
                                onSendControllerState(
                                    latestMask,
                                    ControllerLayout.entries[idx],
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            }
                        )

                        // Orientation Lock
                        SettingChipSection(
                            label = "ORIENTATION",
                            options = OrientationChoice.entries.map { it.label },
                            selectedIndex = OrientationChoice.entries.indexOf(orientationChoice),
                            testTagPrefix = "orientation_option",
                            onSelect = { idx -> orientationChoice = OrientationChoice.entries[idx] }
                        )

                        // ABXY & Button Haptic Strength
                        SettingChipSection(
                            label = "ABXY & BUTTON HAPTICS",
                            options = HapticStrength.entries.map { it.label },
                            selectedIndex = HapticStrength.entries.indexOf(hapticStrength),
                            testTagPrefix = "haptic_option",
                            onSelect = { idx -> onSelectHapticStrength(HapticStrength.entries[idx]) }
                        )

                        // Gyro Motion
                        SettingChipSection(
                            label = "GYRO MOTION",
                            options = GyroMode.entries.map { it.label },
                            selectedIndex = GyroMode.entries.indexOf(gyroMode),
                            testTagPrefix = "gyro_option",
                            onSelect = { idx -> onSelectGyroMode(GyroMode.entries[idx]) }
                        )

                        // Deadzone Slider
                        SliderSettingRow(
                            label = "STICK DEADZONE",
                            valueText = "$stickDeadzonePct%",
                            value = stickDeadzonePct.toFloat(),
                            range = 0f..25f,
                            onValueChange = { onChangeDeadzonePct(it.roundToInt()) }
                        )

                        // Scale Slider
                        SliderSettingRow(
                            label = "BUTTON SCALE",
                            valueText = "${(customization.scale * 100f).roundToInt()}%",
                            value = customization.scale,
                            range = 0.72f..1.35f,
                            onValueChange = { v ->
                                onChangeCustomization(customization.copy(scale = v))
                            }
                        )

                        // Opacity Slider
                        SliderSettingRow(
                            label = "OPACITY",
                            valueText = "${(customization.opacity * 100f).roundToInt()}%",
                            value = customization.opacity,
                            range = 0.35f..1.0f,
                            onValueChange = { v ->
                                onChangeCustomization(customization.copy(opacity = v))
                            }
                        )

                        // Customize Positions / Reset / Resume Play
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editLayoutMode = true
                                    showSettingsModal = false
                                },
                                border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_drag_customize_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenWith,
                                    contentDescription = null,
                                    tint = CobaltBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Drag Controls", color = CobaltBlue, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { onResetCustomization() },
                                border = BorderStroke(1.dp, GlassRimMid),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reset_controls_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Reset customization",
                                    tint = BrushedSilver,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Reset", color = BrushedSilver, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { showSettingsModal = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrushedSilver,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("close_controller_settings_button")
                        ) {
                            Text("Resume Playing", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingChipSection(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    testTagPrefix: String,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = PewterMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { idx, text ->
                val selected = idx == selectedIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) CobaltBlue.copy(alpha = 0.22f) else GlassFillSubtle,
                    border = BorderStroke(1.dp, if (selected) CobaltBlue else GlassRimMid),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(idx) }
                        .testTag("${testTagPrefix}_$idx")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (selected) TitaniumText else PewterMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = PewterMuted
            )
            Text(
                text = valueText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = TitaniumText
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = BrushedSilver,
                activeTrackColor = CobaltBlue,
                inactiveTrackColor = GlassFillSubtle
            )
        )
    }
}
