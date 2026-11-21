package com.example.bridge

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.protocol.PadButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Zero-ADB / Zero-Root fallback Input Method Service inside the unified LinkPad APK.
 * When active as the device keyboard on Fire TV / Android TV / Phones, incoming Wi-Fi UDP
 * transitions from connected LinkPad controllers dispatch native KeyEvents to the focused app.
 */
class LinkPadInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        collectJob = scope.launch {
            InputBridgeBus.inputTransitions.collect { transition ->
                val ic = currentInputConnection
                val keyCode = transition.keymapProfile.resolveKeyCode(transition.button)
                val now = SystemClock.uptimeMillis()
                val action = if (transition.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP

                val dispatched = if (ic != null) {
                    ic.sendKeyEvent(KeyEvent(now, now, action, keyCode, 0))
                } else {
                    false
                }

                // For TV / non-text-field navigation (Fire TV home/menus/games), fall back to
                // sendDownUpKeyEvents on key-down if no InputConnection was bound or rejected it.
                if (!dispatched && transition.pressed) {
                    val tvKey = when (transition.button) {
                        PadButton.A -> KeyEvent.KEYCODE_DPAD_CENTER
                        PadButton.B -> KeyEvent.KEYCODE_BACK
                        else -> keyCode
                    }
                    runCatching { sendDownUpKeyEvents(tvKey) }
                }
            }
        }
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
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
