package com.ello.litertchat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** App-owned tactile feedback. Unlike View.performHapticFeedback, this intentionally
 * does not get muted by Android's separate "Touch feedback" switch. */
internal fun View.performElloHaptic(feedback: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    val (duration, amplitude) = when (feedback) {
        HapticFeedbackConstants.CLOCK_TICK -> 18L to 165
        HapticFeedbackConstants.CONFIRM -> 48L to 220
        HapticFeedbackConstants.REJECT -> 55L to 210
        HapticFeedbackConstants.CONTEXT_CLICK -> 42L to 200
        else -> 34L to 185
    }
    val effect = if (feedback == HapticFeedbackConstants.REJECT) {
        VibrationEffect.createWaveform(longArrayOf(0, 30, 35, 30), intArrayOf(0, amplitude, 0, amplitude), -1)
    } else {
        VibrationEffect.createOneShot(duration, amplitude)
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                .build())
        } else {
            vibrator.vibrate(effect)
        }
    } catch (_: RuntimeException) {
        // A device with a restricted vibrator service can still use platform feedback.
        performHapticFeedback(feedback)
    }
}

/** One short tick per single-key insertion or deletion. Bulk pastes and autocorrects stay silent. */
internal fun View.performElloTypingHaptic(previous: String, next: String) {
    if (previous == next) return
    val before = previous.codePointCount(0, previous.length)
    val after = next.codePointCount(0, next.length)
    if (kotlin.math.abs(after - before) == 1) {
        performElloHaptic(HapticFeedbackConstants.CLOCK_TICK)
    }
}
