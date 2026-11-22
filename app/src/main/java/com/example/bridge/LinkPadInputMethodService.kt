package com.example.bridge

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.protocol.KeymapProfile
import com.example.protocol.PadButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Zero-ADB / Zero-Root Input Method Service inside the unified LinkPad APK.
 * Dispatches held KeyEvents (buttons + Left Stick / Gyro converted to D-Pad/WASD/Arrows)
 * cleanly to emulators, Fire TV apps, and games without ADB Debugging.
 */
class LinkPadInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var analogStickJob: Job? = null
    private var syntheticDirMask = 0

    override fun onCreate() {
        super.onCreate()
        InputBridgeBus.setImeBridgeActive(true)

        collectJob = scope.launch {
            InputBridgeBus.inputTransitions.collect { transition ->
                dispatchButtonKey(
                    button = transition.button,
                    pressed = transition.pressed,
                    profile = transition.keymapProfile
                )
            }
        }

        // Convert P1 analog stick / gyro tilt into directional key holds so Dual Stick / Wheel works in IME mode
        analogStickJob = scope.launch {
            while (isActive) {
                delay(24L)
                val axes = InputBridgeBus.p1AnalogAxes.value
                val nextDirMask = computeStickDirectionMask(axes.lx, axes.ly, syntheticDirMask)
                if (nextDirMask != syntheticDirMask) {
                    val profile = InputBridgeBus.p1KeymapProfile.value
                    val changed = syntheticDirMask xor nextDirMask
                    syntheticDirMask = nextDirMask
                    for (dir in listOf(PadButton.UP, PadButton.DOWN, PadButton.LEFT, PadButton.RIGHT)) {
                        if ((changed and dir.bit) != 0) {
                            dispatchButtonKey(dir, (nextDirMask and dir.bit) != 0, profile)
                        }
                    }
                }
            }
        }
    }

    private fun dispatchButtonKey(button: PadButton, pressed: Boolean, profile: KeymapProfile) {
        val ic = currentInputConnection
        val keyCode = profile.resolveKeyCode(button)
        val now = SystemClock.uptimeMillis()
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP

        val dispatched = if (ic != null) {
            val ev = KeyEvent(
                now,
                now,
                action,
                keyCode,
                0,
                0,
                -1,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                if (profile == KeymapProfile.GAMEPAD_STANDARD) InputDevice.SOURCE_GAMEPAD else InputDevice.SOURCE_KEYBOARD
            )
            ic.sendKeyEvent(ev)
        } else {
            false
        }

        if (!dispatched && pressed) {
            val tvKey = when (button) {
                PadButton.A -> KeyEvent.KEYCODE_DPAD_CENTER
                PadButton.B -> KeyEvent.KEYCODE_BACK
                else -> keyCode
            }
            runCatching { sendDownUpKeyEvents(tvKey) }
        }
    }

    private fun computeStickDirectionMask(lx: Byte, ly: Byte, prevMask: Int): Int {
        var mask = 0
        val leftThreshold = if ((prevMask and PadButton.LEFT.bit) != 0) -28 else -48
        val rightThreshold = if ((prevMask and PadButton.RIGHT.bit) != 0) 28 else 48
        val upThreshold = if ((prevMask and PadButton.UP.bit) != 0) -28 else -48
        val downThreshold = if ((prevMask and PadButton.DOWN.bit) != 0) 28 else 48

        if (lx <= leftThreshold) mask = mask or PadButton.LEFT.bit
        if (lx >= rightThreshold) mask = mask or PadButton.RIGHT.bit
        if (ly <= upThreshold) mask = mask or PadButton.UP.bit
        if (ly >= downThreshold) mask = mask or PadButton.DOWN.bit
        return mask
    }

    override fun onCreateInputView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E60B0D12"))
            setPadding(24, 10, 24, 10)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(context).apply {
                text = "LinkPad Wi-Fi Controller Keyboard Active"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
            })
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        InputBridgeBus.setImeBridgeActive(false)
        collectJob?.cancel()
        analogStickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
