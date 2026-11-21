package com.example.ui.components

import android.hardware.SensorManager
import android.view.Surface
import com.example.protocol.AnalogAxes
import com.example.protocol.GyroMode
import com.example.protocol.GyroResponseMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Pure math utilities for drift-free 6-DOF Tilt Hold orientation fusion and
 * display-rotation-aware Rate Aim gyroscope velocity mapping.
 *
 * Axis convention across LinkPad (Dual Stick UI, Bluetooth HID, ViGEmBus XInput):
 * - X: -127 = Left, +127 = Right
 * - Y: -127 = Up (tilting top edge toward user), +127 = Down (tilting forward/away)
 */
object GyroMotionMath {

    fun remapRotationMatrixForDisplay(
        rawMatrix: FloatArray,
        displayRotation: Int
    ): FloatArray {
        val remapped = FloatArray(9)
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(rawMatrix, axisX, axisY, remapped)) {
            rawMatrix.copyInto(remapped, endIndex = 9)
        }
        return remapped
    }

    fun remapAngularRateForDisplay(
        gx: Float,
        gy: Float,
        gz: Float,
        displayRotation: Int
    ): Triple<Float, Float, Float> = when (displayRotation) {
        Surface.ROTATION_90 -> Triple(gy, -gx, gz)
        Surface.ROTATION_180 -> Triple(-gx, -gy, gz)
        Surface.ROTATION_270 -> Triple(-gy, gx, gz)
        else -> Triple(gx, gy, gz)
    }

    /**
     * Computes R_rel = R0^T * R_now for 3x3 row-major orthonormal rotation matrices.
     */
    fun relativeRotationMatrix(ref: FloatArray, current: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (r in 0..2) {
            for (c in 0..2) {
                out[r * 3 + c] =
                    ref[0 * 3 + r] * current[0 * 3 + c] +
                    ref[1 * 3 + r] * current[1 * 3 + c] +
                    ref[2 * 3 + r] * current[2 * 3 + c]
            }
        }
        return out
    }

    /**
     * Extracts joystick axes [-127..127] from relative orientation matrix [rel] (R0^T * R_now).
     * Never drifts back to center when held rotated.
     */
    fun computeTiltHoldAxes(
        rel: FloatArray,
        gyroMode: GyroMode,
        sensitivity: Float,
        invertY: Boolean
    ): AnalogAxes {
        if (gyroMode == GyroMode.OFF) return AnalogAxes()

        // Full scale reaches 127 around 52 deg at 1.0x sensitivity, narrower at higher sensitivity
        val sens = sensitivity.coerceIn(0.5f, 2.5f)
        val fullScaleRad = (Math.toRadians(52.0) / sens).toFloat().coerceIn(0.28f, 1.45f)

        // Wheel roll clockwise (turn right around +Z toward user) -> positive right
        val rollRightRad = atan2(-rel[3], rel[0])
        // Aim yaw panning beam right (around +Y up) -> -rel[2] positive right
        val yawRightRad = atan2(-rel[2], rel[8])
        // Pitch top edge toward user (+X right-hand curl) -> positive toward user -> UP (-Y)
        val pitchTowardsUserRad = atan2(rel[7], rel[8])

        val rawXRad = when (gyroMode) {
            GyroMode.STEER_LEFT_STICK -> rollRightRad
            GyroMode.AIM_RIGHT_STICK -> yawRightRad + rollRightRad * 0.35f
            GyroMode.OFF -> 0f
        }
        val signY = if (invertY) 1f else -1f
        val normX = softDeadzoneAndClamp(rawXRad / fullScaleRad, deadzone = 0.03f)
        val normY = softDeadzoneAndClamp((pitchTowardsUserRad / fullScaleRad) * signY, deadzone = 0.03f)

        val bx = (normX * 127f).roundToInt().coerceIn(-127, 127).toByte()
        val by = (normY * 127f).roundToInt().coerceIn(-127, 127).toByte()

        return when (gyroMode) {
            GyroMode.STEER_LEFT_STICK -> AnalogAxes(lx = bx, ly = by)
            GyroMode.AIM_RIGHT_STICK -> AnalogAxes(rx = bx, ry = by)
            GyroMode.OFF -> AnalogAxes()
        }
    }

    /**
     * Maps display-remapped angular rates (rad/s) for flick/aim play in Rate Aim mode.
     */
    fun computeRateAimAxes(
        scrGx: Float,
        scrGy: Float,
        scrGz: Float,
        gyroMode: GyroMode,
        sensitivity: Float,
        invertY: Boolean
    ): AnalogAxes {
        if (gyroMode == GyroMode.OFF) return AnalogAxes()
        val sens = sensitivity.coerceIn(0.5f, 2.5f)
        val gain = 36f * sens

        val rawRateX = when (gyroMode) {
            GyroMode.STEER_LEFT_STICK -> -scrGz
            GyroMode.AIM_RIGHT_STICK -> -scrGy - scrGz * 0.35f
            GyroMode.OFF -> 0f
        }
        val signY = if (invertY) 1f else -1f
        val rawRateY = scrGx * signY

        val outX = applyRateDeadzone(rawRateX, 0.05f) * gain
        val outY = applyRateDeadzone(rawRateY, 0.05f) * gain

        val bx = outX.roundToInt().coerceIn(-127, 127).toByte()
        val by = outY.roundToInt().coerceIn(-127, 127).toByte()

        return when (gyroMode) {
            GyroMode.STEER_LEFT_STICK -> AnalogAxes(lx = bx, ly = by)
            GyroMode.AIM_RIGHT_STICK -> AnalogAxes(rx = bx, ry = by)
            GyroMode.OFF -> AnalogAxes()
        }
    }

    fun isTiltHoldSupported(hasGameRotation: Boolean, hasRotationVector: Boolean): Boolean =
        hasGameRotation || hasRotationVector

    private fun softDeadzoneAndClamp(v: Float, deadzone: Float): Float {
        val mag = abs(v)
        if (mag <= deadzone) return 0f
        val rescaled = ((mag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        return if (v >= 0f) rescaled else -rescaled
    }

    private fun applyRateDeadzone(rate: Float, floor: Float): Float {
        val mag = abs(rate)
        if (mag <= floor) return 0f
        return if (rate >= 0f) mag - floor else -(mag - floor)
    }
}
