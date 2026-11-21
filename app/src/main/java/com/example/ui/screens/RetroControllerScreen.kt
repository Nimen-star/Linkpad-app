package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CenterFocusStrong
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
import com.example.protocol.ControllerTheme
import com.example.protocol.GyroMode
import com.example.protocol.GyroResponseMode
import com.example.protocol.HapticStrength
import com.example.protocol.ProPaddleConfig
import com.example.transport.ClientConnectionState
import com.example.ui.components.ArcadeFightControllerPad
import com.example.ui.components.DualStickControllerPad
import com.example.ui.components.GyroMotionMath
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCheckRow
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.ProDualStickControllerPad
import com.example.ui.components.RacingWheelControllerPad
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
    previewMode: Boolean = false,
    connectionState: ClientConnectionState,
    controllerLayout: ControllerLayout,
    controllerTheme: ControllerTheme = ControllerTheme.LIQUID_GLASS,
    hapticStrength: HapticStrength,
    gyroMode: GyroMode,
    gyroResponseMode: GyroResponseMode = GyroResponseMode.TILT_HOLD,
    gyroSensitivity: Float = 1.0f,
    invertGyroY: Boolean = false,
    stickDeadzonePct: Int,
    proPaddleConfig: ProPaddleConfig = ProPaddleConfig(),
    customization: ControlCustomization,
    onSelectLayout: (ControllerLayout) -> Unit,
    onSelectTheme: (ControllerTheme) -> Unit = {},
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onSelectGyroMode: (GyroMode) -> Unit,
    onSelectGyroResponseMode: (GyroResponseMode) -> Unit = {},
    onChangeGyroSensitivity: (Float) -> Unit = {},
    onChangeInvertGyroY: (Boolean) -> Unit = {},
    onChangeDeadzonePct: (Int) -> Unit,
    onChangeProPaddleConfig: (ProPaddleConfig) -> Unit = {},
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
    var recenterNonce by remember { mutableIntStateOf(0) }

    fun currentDisplayRotation(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val r = activity?.display?.rotation
            if (r != null) return r
        }
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

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

    DisposableEffect(gyroMode, gyroResponseMode, gyroSensitivity, invertGyroY, recenterNonce) {
        if (gyroMode == GyroMode.OFF) {
            gyroAxes = AnalogAxes()
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val gameRotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            val rotVecSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

            val useTiltHold = gyroResponseMode == GyroResponseMode.TILT_HOLD &&
                (gameRotSensor != null || rotVecSensor != null)
            val activeSensor = when {
                useTiltHold -> gameRotSensor ?: rotVecSensor
                gyroSensor != null -> gyroSensor
                else -> null
            }

            var baselineMatrix: FloatArray? = null
            val rawMatrix = FloatArray(9)
            var smoothGx = 0f
            var smoothGy = 0f
            var smoothGz = 0f

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val displayRot = currentDisplayRotation()
                    if (useTiltHold) {
                        SensorManager.getRotationMatrixFromVector(rawMatrix, event.values)
                        val remapped = GyroMotionMath.remapRotationMatrixForDisplay(rawMatrix, displayRot)
                        val base = baselineMatrix
                        if (base == null) {
                            baselineMatrix = remapped.copyOf()
                            gyroAxes = AnalogAxes()
                        } else {
                            val rel = GyroMotionMath.relativeRotationMatrix(base, remapped)
                            gyroAxes = GyroMotionMath.computeTiltHoldAxes(
                                rel = rel,
                                gyroMode = gyroMode,
                                sensitivity = gyroSensitivity,
                                invertY = invertGyroY
                            )
                        }
                    } else {
                        val (rx, ry, rz) = GyroMotionMath.remapAngularRateForDisplay(
                            gx = event.values[0],
                            gy = event.values[1],
                            gz = event.values[2],
                            displayRotation = displayRot
                        )
                        val alpha = 0.42f
                        smoothGx += (rx - smoothGx) * alpha
                        smoothGy += (ry - smoothGy) * alpha
                        smoothGz += (rz - smoothGz) * alpha
                        gyroAxes = GyroMotionMath.computeRateAimAxes(
                            scrGx = smoothGx,
                            scrGy = smoothGy,
                            scrGz = smoothGz,
                            gyroMode = gyroMode,
                            sensitivity = gyroSensitivity,
                            invertY = invertGyroY
                        )
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            if (activeSensor != null && sensorManager != null) {
                sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_GAME)
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
                        controllerTheme = controllerTheme,
                        gyroAxes = gyroAxes,
                        onCustomizationChange = onChangeCustomization,
                        onMaskChanged = { mask -> latestMask = mask },
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.RETRO_HANDHELD, lx, ly, rx, ry)
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
                        controllerTheme = controllerTheme,
                        onCustomizationChange = onChangeCustomization,
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.DUAL_STICK, lx, ly, rx, ry)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ControllerLayout.RACING_WHEEL -> {
                    RacingWheelControllerPad(
                        slot = slot,
                        hapticStrength = hapticStrength,
                        customization = customization,
                        gyroAxes = gyroAxes,
                        editLayoutMode = editLayoutMode,
                        controllerTheme = controllerTheme,
                        onCustomizationChange = onChangeCustomization,
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.RACING_WHEEL, lx, ly, rx, ry)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ControllerLayout.PRO_DUAL_STICK -> {
                    ProDualStickControllerPad(
                        slot = slot,
                        hapticStrength = hapticStrength,
                        stickDeadzonePct = stickDeadzonePct,
                        customization = customization,
                        paddleConfig = proPaddleConfig,
                        gyroAxes = gyroAxes,
                        editLayoutMode = editLayoutMode,
                        controllerTheme = controllerTheme,
                        onCustomizationChange = onChangeCustomization,
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.PRO_DUAL_STICK, lx, ly, rx, ry)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ControllerLayout.ARCADE_FIGHT_PAD -> {
                    ArcadeFightControllerPad(
                        slot = slot,
                        hapticStrength = hapticStrength,
                        customization = customization,
                        gyroAxes = gyroAxes,
                        editLayoutMode = editLayoutMode,
                        controllerTheme = controllerTheme,
                        onCustomizationChange = onChangeCustomization,
                        onStateChanged = { mask, lx, ly, rx, ry ->
                            latestMask = mask
                            onSendControllerState(mask, ControllerLayout.ARCADE_FIGHT_PAD, lx, ly, rx, ry)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Minimal Top-Middle Hub
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
                    if (previewMode) {
                        LiquidGlassPanel(
                            shape = CircleShape,
                            subtle = true,
                            accentBorderTint = CobaltBlue.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "PREVIEW · OFFLINE",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = CobaltBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

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

                    if (gyroMode != GyroMode.OFF) {
                        LiquidGlassPanel(
                            shape = CircleShape,
                            subtle = true,
                            accentBorderTint = SignalEmerald.copy(alpha = 0.55f),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable { recenterNonce++ }
                                .testTag("gyro_recenter_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Recenter Gyro",
                                    tint = SignalEmerald,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Recenter",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = TitaniumText
                                )
                            }
                        }
                    }
                }
            }

            // Disconnected Overlay (suppressed during Offline Preview)
            if (!previewMode &&
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
                    .background(ObsidianBg.copy(alpha = 0.58f))
                    .clickable(onClick = { showSettingsModal = false })
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassPanel(
                    shape = RoundedCornerShape(26.dp),
                    elevated = true,
                    modifier = Modifier
                        .widthIn(max = 490.dp)
                        .fillMaxWidth()
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (previewMode) "Customize Controller" else "Controller Settings",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TitaniumText
                                )
                                Text(
                                    text = "Saved per layout (${controllerLayout.label})",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = PewterMuted
                                )
                            }
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
                                    contentDescription = if (previewMode) "Close Preview" else "Disconnect",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (previewMode) "Exit" else "Disconnect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Layout Mode Switcher
                        WrappingChipSection(
                            label = "CONTROLLER LAYOUT",
                            options = ControllerLayout.entries.map { it.label },
                            selectedIndex = ControllerLayout.entries.indexOf(controllerLayout),
                            testTagPrefix = "layout_option",
                            onSelect = { idx ->
                                val picked = ControllerLayout.entries[idx]
                                onSelectLayout(picked)
                                onSendControllerState(latestMask, picked, 0, 0, 0, 0)
                            }
                        )

                        // Visual Controller Theme
                        WrappingChipSection(
                            label = "CONTROLLER THEME",
                            options = ControllerTheme.entries.map { it.label },
                            selectedIndex = ControllerTheme.entries.indexOf(controllerTheme),
                            testTagPrefix = "theme_option",
                            onSelect = { idx ->
                                onSelectTheme(ControllerTheme.entries[idx])
                            }
                        )

                        // Orientation Lock
                        WrappingChipSection(
                            label = "ORIENTATION",
                            options = OrientationChoice.entries.map { it.label },
                            selectedIndex = OrientationChoice.entries.indexOf(orientationChoice),
                            testTagPrefix = "orientation_option",
                            onSelect = { idx -> orientationChoice = OrientationChoice.entries[idx] }
                        )

                        // ABXY & Button Haptic Strength
                        WrappingChipSection(
                            label = "ABXY & BUTTON HAPTICS",
                            options = HapticStrength.entries.map { it.label },
                            selectedIndex = HapticStrength.entries.indexOf(hapticStrength),
                            testTagPrefix = "haptic_option",
                            onSelect = { idx -> onSelectHapticStrength(HapticStrength.entries[idx]) }
                        )

                        // Gyro Motion
                        WrappingChipSection(
                            label = "GYRO MOTION",
                            options = GyroMode.entries.map { it.label },
                            selectedIndex = GyroMode.entries.indexOf(gyroMode),
                            testTagPrefix = "gyro_option",
                            onSelect = { idx -> onSelectGyroMode(GyroMode.entries[idx]) }
                        )

                        if (gyroMode != GyroMode.OFF) {
                            WrappingChipSection(
                                label = "GYRO FEEL",
                                options = GyroResponseMode.entries.map { it.label },
                                selectedIndex = GyroResponseMode.entries.indexOf(gyroResponseMode),
                                testTagPrefix = "gyro_feel_option",
                                onSelect = { idx ->
                                    recenterNonce++
                                    onSelectGyroResponseMode(GyroResponseMode.entries[idx])
                                }
                            )

                            SliderSettingRow(
                                label = "GYRO SENSITIVITY",
                                valueText = "${(gyroSensitivity * 100f).roundToInt()}%",
                                value = gyroSensitivity,
                                range = 0.5f..2.5f,
                                onValueChange = onChangeGyroSensitivity
                            )

                            LiquidGlassCheckRow(
                                checked = invertGyroY,
                                label = "Invert Gyro Y (Pitch)",
                                onCheckedChange = onChangeInvertGyroY,
                                modifier = Modifier.testTag("invert_gyro_y_checkbox")
                            )
                        }

                        if (controllerLayout == ControllerLayout.PRO_DUAL_STICK) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PRO PADDLES (TAP TO REMAP M1–M4)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PewterMuted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    (1..4).forEach { paddleIdx ->
                                        val mapped = proPaddleConfig.buttonFor(paddleIdx)
                                        val choices = ProPaddleConfig.REMAPPABLE_CHOICES
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = GlassFillSubtle,
                                            border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.55f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    val nextIdx = (choices.indexOf(mapped) + 1) % choices.size
                                                    onChangeProPaddleConfig(
                                                        proPaddleConfig.withPaddle(paddleIdx, choices[nextIdx])
                                                    )
                                                }
                                                .testTag("paddle_map_m$paddleIdx")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "M$paddleIdx:${mapped.shortLabel}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TitaniumText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                            label = "BUTTON SCALE (${controllerLayout.label.uppercase()})",
                            valueText = "${(customization.scale * 100f).roundToInt()}%",
                            value = customization.scale,
                            range = 0.72f..1.35f,
                            onValueChange = { v ->
                                onChangeCustomization(customization.copy(scale = v))
                            }
                        )

                        // Opacity Slider
                        SliderSettingRow(
                            label = "OPACITY (${controllerLayout.label.uppercase()})",
                            valueText = "${(customization.opacity * 100f).roundToInt()}%",
                            value = customization.opacity,
                            range = 0.35f..1.0f,
                            onValueChange = { v ->
                                onChangeCustomization(customization.copy(opacity = v))
                            }
                        )

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
                            Text(
                                text = if (previewMode) "Done Customizing" else "Resume Playing",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrappingChipSection(
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { idx, text ->
                val selected = idx == selectedIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) CobaltBlue.copy(alpha = 0.22f) else GlassFillSubtle,
                    border = BorderStroke(1.dp, if (selected) CobaltBlue else GlassRimMid),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(idx) }
                        .testTag("${testTagPrefix}_$idx")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
