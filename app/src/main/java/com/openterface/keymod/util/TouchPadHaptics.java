package com.openterface.keymod.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Short vibration patterns for touchpad-emulated mouse actions (click, double-click, right-click, drag toggle).
 */
public final class TouchPadHaptics {

    private static final String TAG = "TouchPadHaptics";

    private TouchPadHaptics() {}

    public static void onLeftClick(@NonNull Context context) {
        vibrateOneShot(context, 22, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    public static void onDoubleClick(@NonNull Context context) {
        long[] timingsMs = {0, 18, 52, 18};
        int[] amplitudes = {
                0,
                VibrationEffect.DEFAULT_AMPLITUDE,
                0,
                VibrationEffect.DEFAULT_AMPLITUDE
        };
        vibrateWaveform(context, timingsMs, amplitudes);
    }

    public static void onRightClick(@NonNull Context context) {
        vibrateOneShot(context, 38, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /** Drag lock toggled (long-press). */
    public static void onDragToggle(@NonNull Context context) {
        vibrateOneShot(context, 58, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    private static Vibrator getVibrator(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = context.getSystemService(VibratorManager.class);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static void vibrateOneShot(@NonNull Context context, long durationMs, int amplitude) {
        Vibrator v = getVibrator(context);
        if (v == null || !v.hasVibrator()) {
            return;
        }
        try {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
        } catch (Exception e) {
            Log.v(TAG, "Vibration skipped: " + e.getMessage());
        }
    }

    private static void vibrateWaveform(@NonNull Context context, long[] timingsMs, int[] amplitudes) {
        Vibrator v = getVibrator(context);
        if (v == null || !v.hasVibrator()) {
            return;
        }
        try {
            v.vibrate(VibrationEffect.createWaveform(timingsMs, amplitudes, -1));
        } catch (Exception e) {
            Log.v(TAG, "Vibration skipped: " + e.getMessage());
        }
    }
}
