package com.kimrosil.flowstep;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Tactile Dopamine & Haptic Feedback Manager.
 * Triggers satisfying physical micro-pulses upon step completion.
 */
public class HapticFeedbackUtil {

    public static void performClickFeedback(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void performStepComplete(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Satisfying, crisp double micro-pulse: 35ms on, 50ms off, 45ms on
            long[] timings = new long[]{0, 35, 50, 45};
            int[] amplitudes = new int[]{0, 200, 0, 255};
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            v.vibrate(new long[]{0, 35, 50, 45}, -1);
        }
    }

    public static void performGoalComplete(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Triumphant triple pulse: 40ms, 40ms, 70ms
            long[] timings = new long[]{0, 40, 40, 40, 40, 70};
            int[] amplitudes = new int[]{0, 180, 0, 220, 0, 255};
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            v.vibrate(new long[]{0, 40, 40, 40, 40, 70}, -1);
        }
    }

    public static void performParkThought(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(25, 150));
        } else {
            v.vibrate(25);
        }
    }
}
