package com.example.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.protocol.AnalogAxes
import com.example.protocol.OsInputBridgeMode
import com.example.protocol.PadButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure Kotlin normalized touch-spot layout for Zero-ADB Touchscreen Game Mapping.
 * Easily unit-tested without an Android device.
 */
object TouchMapperLayout {
    data class NormalizedPoint(val xFrac: Float, val yFrac: Float)

    fun buttonSpot(button: PadButton): NormalizedPoint? = when (button) {
        PadButton.A -> NormalizedPoint(0.85f, 0.78f)
        PadButton.B -> NormalizedPoint(0.92f, 0.67f)
        PadButton.X -> NormalizedPoint(0.78f, 0.67f)
        PadButton.Y -> NormalizedPoint(0.85f, 0.56f)
        PadButton.L -> NormalizedPoint(0.14f, 0.14f)
        PadButton.R -> NormalizedPoint(0.86f, 0.14f)
        PadButton.L2 -> NormalizedPoint(0.14f, 0.28f)
        PadButton.R2 -> NormalizedPoint(0.86f, 0.28f)
        PadButton.L3 -> NormalizedPoint(0.28f, 0.84f)
        PadButton.R3 -> NormalizedPoint(0.68f, 0.84f)
        PadButton.START -> NormalizedPoint(0.56f, 0.12f)
        PadButton.SELECT -> NormalizedPoint(0.44f, 0.12f)
        else -> null
    }

    fun leftStickPoint(
        lx: Byte,
        ly: Byte,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        val nx = (lx / 127f).coerceIn(-1f, 1f)
        val ny = (ly / 127f).coerceIn(-1f, 1f)
        if (abs(nx) < 0.15f && abs(ny) < 0.15f) return null
        val cx = screenWidth * 0.18f
        val cy = screenHeight * 0.72f
        val radius = min(screenWidth, screenHeight) * 0.12f
        return (cx + nx * radius) to (cy + ny * radius)
    }

    fun rightStickPoint(
        rx: Byte,
        ry: Byte,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        val nx = (rx / 127f).coerceIn(-1f, 1f)
        val ny = (ry / 127f).coerceIn(-1f, 1f)
        if (abs(nx) < 0.15f && abs(ny) < 0.15f) return null
        val cx = screenWidth * 0.68f
        val cy = screenHeight * 0.48f
        val radius = min(screenWidth, screenHeight) * 0.14f
        return (cx + nx * radius) to (cy + ny * radius)
    }
}

/**
 * Zero-ADB, Zero-Root Accessibility input bridge inside the unified LinkPad APK.
 * Converts incoming Wi-Fi UDP controller packets into:
 * 1) TV & App D-Pad / Back / Home / Click navigation (API 24+ focus traversal & API 33+ GLOBAL_ACTION_DPAD_*), or
 * 2) Multi-touch game gestures (dispatchGesture + API 26+ continuous StrokeDescription drags).
 */
class LinkPadAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transitionJob: Job? = null
    private var continuousLoopJob: Job? = null

    private var activeLeftStroke: GestureDescription.StrokeDescription? = null
    private var lastLeftX = 0f
    private var lastLeftY = 0f
    private var activeRightStroke: GestureDescription.StrokeDescription? = null
    private var lastRightX = 0f
    private var lastRightY = 0f
    private var pendingTapPoint: Pair<Float, Float>? = null

    private var activeDirection: PadButton? = null
    private var dirHoldStartMs = 0L
    private var dirLastRepeatMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        InputBridgeBus.setAccessibilityBridgeActive(true)

        transitionJob = scope.launch {
            InputBridgeBus.inputTransitions.collect { transition ->
                if (!InputBridgeBus.hostActive.value) return@collect
                when (InputBridgeBus.osInputBridgeMode.value) {
                    OsInputBridgeMode.TV_DPAD_NAV -> {
                        if (transition.pressed) {
                            handleTvButtonPress(transition.button)
                        } else if (transition.button == activeDirection) {
                            activeDirection = null
                        }
                    }
                    OsInputBridgeMode.TOUCH_GAMEPAD -> {
                        if (transition.pressed) {
                            TouchMapperLayout.buttonSpot(transition.button)?.let { spot ->
                                val dm = resources.displayMetrics
                                pendingTapPoint = (spot.xFrac * dm.widthPixels) to (spot.yFrac * dm.heightPixels)
                                dispatchTouchFrame(InputBridgeBus.p1AnalogAxes.value)
                            }
                            if (transition.button == PadButton.B && InputBridgeBus.p1ButtonMask.value == 0) {
                                // Quick back swipe isn't forced in touch game mode since B is usually jump/cancel
                            }
                        }
                    }
                    OsInputBridgeMode.KEYBOARD_IME -> {
                        // Keyboard IME handles key injection directly when active
                    }
                }
            }
        }

        continuousLoopJob = scope.launch {
            while (isActive) {
                delay(32L)
                if (!InputBridgeBus.hostActive.value) {
                    releaseActiveStrokes()
                    activeDirection = null
                    continue
                }
                val axes = InputBridgeBus.p1AnalogAxes.value
                when (InputBridgeBus.osInputBridgeMode.value) {
                    OsInputBridgeMode.TV_DPAD_NAV -> {
                        releaseActiveStrokes()
                        tickTvNavigationLoop(axes)
                    }
                    OsInputBridgeMode.TOUCH_GAMEPAD -> {
                        dispatchTouchFrame(axes)
                    }
                    OsInputBridgeMode.KEYBOARD_IME -> {
                        releaseActiveStrokes()
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        releaseActiveStrokes()
        activeDirection = null
    }

    override fun onDestroy() {
        InputBridgeBus.setAccessibilityBridgeActive(false)
        transitionJob?.cancel()
        continuousLoopJob?.cancel()
        releaseActiveStrokes()
        scope.cancel()
        super.onDestroy()
    }

    private fun handleTvButtonPress(button: PadButton) {
        when (button) {
            PadButton.UP, PadButton.DOWN, PadButton.LEFT, PadButton.RIGHT -> {
                activeDirection = button
                dirHoldStartMs = System.currentTimeMillis()
                dirLastRepeatMs = dirHoldStartMs
                dispatchTvDpadDirection(button)
            }
            PadButton.A -> dispatchTvCenterClick()
            PadButton.B -> performGlobalAction(GLOBAL_ACTION_BACK)
            PadButton.SELECT -> performGlobalAction(GLOBAL_ACTION_HOME)
            PadButton.START -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            else -> Unit
        }
    }

    private fun tickTvNavigationLoop(axes: AnalogAxes) {
        val now = System.currentTimeMillis()
        val stickDir = when {
            axes.ly <= -55 -> PadButton.UP
            axes.ly >= 55 -> PadButton.DOWN
            axes.lx <= -55 -> PadButton.LEFT
            axes.lx >= 55 -> PadButton.RIGHT
            else -> null
        }

        val currentMask = InputBridgeBus.p1ButtonMask.value
        val dpadHeld = listOf(PadButton.UP, PadButton.DOWN, PadButton.LEFT, PadButton.RIGHT)
            .firstOrNull { (currentMask and it.bit) != 0 }

        val targetDir = dpadHeld ?: stickDir
        if (targetDir == null) {
            activeDirection = null
            return
        }

        if (activeDirection != targetDir) {
            activeDirection = targetDir
            dirHoldStartMs = now
            dirLastRepeatMs = now
            dispatchTvDpadDirection(targetDir)
        } else if (now - dirHoldStartMs > 280L && now - dirLastRepeatMs >= 135L) {
            dirLastRepeatMs = now
            dispatchTvDpadDirection(targetDir)
        }
    }

    private fun dispatchTvDpadDirection(dir: PadButton) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val action = when (dir) {
                PadButton.UP -> GLOBAL_ACTION_DPAD_UP
                PadButton.DOWN -> GLOBAL_ACTION_DPAD_DOWN
                PadButton.LEFT -> GLOBAL_ACTION_DPAD_LEFT
                PadButton.RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
                else -> return
            }
            if (performGlobalAction(action)) return
        }

        // Fallback for API 24-32 (FireOS 7 / Android 9-12): focusSearch traversal
        val focusDir = when (dir) {
            PadButton.UP -> View.FOCUS_UP
            PadButton.DOWN -> View.FOCUS_DOWN
            PadButton.LEFT -> View.FOCUS_LEFT
            PadButton.RIGHT -> View.FOCUS_RIGHT
            else -> return
        }

        val root = rootInActiveWindow
        val current = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val next = current?.focusSearch(focusDir)
        if (next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            return
        }

        // Last fallback: directional scroll/swipe gesture for lists
        val dm = resources.displayMetrics
        val cx = dm.widthPixels * 0.5f
        val cy = dm.heightPixels * 0.5f
        val stepX = dm.widthPixels * 0.18f
        val stepY = dm.heightPixels * 0.18f
        val path = Path().apply {
            when (dir) {
                PadButton.UP -> {
                    moveTo(cx, cy - stepY)
                    lineTo(cx, cy + stepY)
                }
                PadButton.DOWN -> {
                    moveTo(cx, cy + stepY)
                    lineTo(cx, cy - stepY)
                }
                PadButton.LEFT -> {
                    moveTo(cx - stepX, cy)
                    lineTo(cx + stepX, cy)
                }
                PadButton.RIGHT -> {
                    moveTo(cx + stepX, cy)
                    lineTo(cx - stepX, cy)
                }
                else -> return
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 90L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun dispatchTvCenterClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return
        }
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (focused != null) {
            if (focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            val rect = Rect()
            focused.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                val tap = Path().apply {
                    moveTo(rect.exactCenterX(), rect.exactCenterY())
                    lineTo(rect.exactCenterX() + 1f, rect.exactCenterY() + 1f)
                }
                dispatchGesture(
                    GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(tap, 0L, 35L))
                        .build(),
                    null,
                    null
                )
            }
        }
    }

    private fun dispatchTouchFrame(axes: AnalogAxes) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        if (w <= 0 || h <= 0) return

        val leftTarget = TouchMapperLayout.leftStickPoint(axes.lx, axes.ly, w, h)
        val rightTarget = TouchMapperLayout.rightStickPoint(axes.rx, axes.ry, w, h)
        val tapTarget = pendingTapPoint.also { pendingTapPoint = null }

        val builder = GestureDescription.Builder()
        var hasStroke = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (leftTarget != null) {
                val (tx, ty) = leftTarget
                val prev = activeLeftStroke
                val stroke = if (prev == null) {
                    val path = Path().apply {
                        moveTo(tx, ty)
                        lineTo(tx + 0.5f, ty + 0.5f)
                    }
                    GestureDescription.StrokeDescription(path, 0L, 32L, true)
                } else {
                    val path = Path().apply {
                        moveTo(lastLeftX, lastLeftY)
                        lineTo(tx, ty)
                    }
                    prev.continueStroke(path, 0L, 32L, true)
                }
                lastLeftX = tx
                lastLeftY = ty
                activeLeftStroke = stroke
                builder.addStroke(stroke)
                hasStroke = true
            } else if (activeLeftStroke != null) {
                val prev = activeLeftStroke!!
                val endPath = Path().apply {
                    moveTo(lastLeftX, lastLeftY)
                    lineTo(lastLeftX, lastLeftY)
                }
                builder.addStroke(prev.continueStroke(endPath, 0L, 20L, false))
                activeLeftStroke = null
                hasStroke = true
            }

            if (rightTarget != null) {
                val (tx, ty) = rightTarget
                val prev = activeRightStroke
                val stroke = if (prev == null) {
                    val path = Path().apply {
                        moveTo(tx, ty)
                        lineTo(tx + 0.5f, ty + 0.5f)
                    }
                    GestureDescription.StrokeDescription(path, 0L, 32L, true)
                } else {
                    val path = Path().apply {
                        moveTo(lastRightX, lastRightY)
                        lineTo(tx, ty)
                    }
                    prev.continueStroke(path, 0L, 32L, true)
                }
                lastRightX = tx
                lastRightY = ty
                activeRightStroke = stroke
                builder.addStroke(stroke)
                hasStroke = true
            } else if (activeRightStroke != null) {
                val prev = activeRightStroke!!
                val endPath = Path().apply {
                    moveTo(lastRightX, lastRightY)
                    lineTo(lastRightX, lastRightY)
                }
                builder.addStroke(prev.continueStroke(endPath, 0L, 20L, false))
                activeRightStroke = null
                hasStroke = true
            }
        } else {
            val singleTarget = leftTarget ?: rightTarget
            if (singleTarget != null) {
                val path = Path().apply {
                    moveTo(singleTarget.first, singleTarget.second)
                    lineTo(singleTarget.first + 1f, singleTarget.second + 1f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0L, 32L))
                hasStroke = true
            }
        }

        if (tapTarget != null) {
            val tapPath = Path().apply {
                moveTo(tapTarget.first, tapTarget.second)
                lineTo(tapTarget.first + 1f, tapTarget.second + 1f)
            }
            builder.addStroke(GestureDescription.StrokeDescription(tapPath, 0L, 35L))
            hasStroke = true
        }

        if (hasStroke) {
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    activeLeftStroke = null
                    activeRightStroke = null
                }
            }, null)
        }
    }

    private fun releaseActiveStrokes() {
        val leftPrev = activeLeftStroke
        val rightPrev = activeRightStroke
        activeLeftStroke = null
        activeRightStroke = null
        if ((leftPrev != null || rightPrev != null) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = GestureDescription.Builder()
            var hasStroke = false
            if (leftPrev != null) {
                val endPath = Path().apply {
                    moveTo(lastLeftX, lastLeftY)
                    lineTo(lastLeftX, lastLeftY)
                }
                runCatching {
                    builder.addStroke(leftPrev.continueStroke(endPath, 0L, 20L, false))
                    hasStroke = true
                }
            }
            if (rightPrev != null) {
                val endPath = Path().apply {
                    moveTo(lastRightX, lastRightY)
                    lineTo(lastRightX, lastRightY)
                }
                runCatching {
                    builder.addStroke(rightPrev.continueStroke(endPath, 0L, 20L, false))
                    hasStroke = true
                }
            }
            if (hasStroke) {
                runCatching { dispatchGesture(builder.build(), null, null) }
            }
        }
    }
}
