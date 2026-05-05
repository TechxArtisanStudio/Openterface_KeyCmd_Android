package com.openterface.keymod.basic;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;

/**
 * Shared tap feedback for KM Basic key surfaces (haptic + pressed state for theme drawables).
 */
public final class BasicKeyFeedback {

    private BasicKeyFeedback() {}

    public static void performKeyHaptic(View view) {
        if (view == null || view.getContext() == null) {
            return;
        }
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(view.getContext())
                .getBoolean("haptic_feedback", true);
        if (!enabled) {
            return;
        }
        view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /**
     * Standard key touch: pressed highlight on down/move, haptic on down (matches {@code CustomKeyboardView}),
     * fire {@code onUpInside} only if release is inside the view.
     *
     * @return whether the event was consumed
     */
    public static boolean handleStandardKeyTouch(View view, MotionEvent event, Runnable onUpInside) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.setPressed(true);
                performKeyHaptic(view);
                return true;
            case MotionEvent.ACTION_MOVE:
                view.setPressed(isInsideView(view, event));
                return true;
            case MotionEvent.ACTION_UP:
                view.setPressed(false);
                if (isInsideView(view, event)) {
                    onUpInside.run();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false);
                return true;
            default:
                return false;
        }
    }

    private static boolean isInsideView(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x < view.getWidth() && y >= 0 && y < view.getHeight();
    }
}
