package com.openterface.keymod.basic;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;

/**
 * Shared tap feedback for KM Basic key surfaces (haptic + pressed state for theme drawables).
 */
public final class BasicKeyFeedback {

    /** Delay after first key-down before auto-repeat starts (ms). */
    private static final long REPEAT_INITIAL_DELAY_MS = 400L;

    /** Interval between repeated key events while held (ms). */
    private static final long REPEAT_INTERVAL_MS = 50L;

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

    /**
     * Key-down fires {@code action} immediately; after {@link #REPEAT_INITIAL_DELAY_MS}, {@code action}
     * repeats every {@link #REPEAT_INTERVAL_MS} until release, cancel, or finger leaves the key.
     */
    public static View.OnTouchListener repeatableKeyTouchListener(Runnable action) {
        return new View.OnTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private boolean repeating;
            private final Runnable repeater =
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!repeating) {
                                return;
                            }
                            action.run();
                            handler.postDelayed(this, REPEAT_INTERVAL_MS);
                        }
                    };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        performKeyHaptic(v);
                        action.run();
                        repeating = true;
                        handler.postDelayed(repeater, REPEAT_INITIAL_DELAY_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boolean inside = isInsideView(v, e);
                        v.setPressed(inside);
                        if (!inside) {
                            repeating = false;
                            handler.removeCallbacks(repeater);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        repeating = false;
                        handler.removeCallbacks(repeater);
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private static boolean isInsideView(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x < view.getWidth() && y >= 0 && y < view.getHeight();
    }
}
