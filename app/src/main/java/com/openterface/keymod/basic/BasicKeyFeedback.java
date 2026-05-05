package com.openterface.keymod.basic;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.function.Supplier;

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
        return handleStandardKeyTouch(view, event, onUpInside, null, null);
    }

    /**
     * Same as {@link #handleStandardKeyTouch(View, MotionEvent, Runnable)} with optional tap preview: shown on
     * press while the finger stays on the key, dismissed on release/cancel/leave.
     */
    public static boolean handleStandardKeyTouch(
            View view,
            MotionEvent event,
            Runnable onUpInside,
            @Nullable BasicKeyPreview preview,
            @Nullable Supplier<String> previewText) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.setPressed(true);
                performKeyHaptic(view);
                maybeShowPreview(preview, previewText, view);
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean inside = isInsideView(view, event);
                view.setPressed(inside);
                if (preview != null) {
                    if (inside) {
                        maybeShowPreview(preview, previewText, view);
                    } else {
                        preview.dismiss();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                view.setPressed(false);
                if (preview != null) {
                    preview.dismiss();
                }
                if (isInsideView(view, event)) {
                    onUpInside.run();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false);
                if (preview != null) {
                    preview.dismiss();
                }
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
        return repeatableKeyTouchListener(action, null, null);
    }

    /**
     * Same as {@link #repeatableKeyTouchListener(Runnable)} with optional tap preview (shown on down while the
     * finger stays on the key; not reshown on each auto-repeat tick).
     */
    public static View.OnTouchListener repeatableKeyTouchListener(
            Runnable action, @Nullable BasicKeyPreview preview, @Nullable Supplier<String> previewText) {
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
                        maybeShowPreview(preview, previewText, v);
                        action.run();
                        repeating = true;
                        handler.postDelayed(repeater, REPEAT_INITIAL_DELAY_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boolean inside = isInsideView(v, e);
                        v.setPressed(inside);
                        if (inside) {
                            maybeShowPreview(preview, previewText, v);
                        } else if (preview != null) {
                            preview.dismiss();
                        }
                        if (!inside) {
                            repeating = false;
                            handler.removeCallbacks(repeater);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (preview != null) {
                            preview.dismiss();
                        }
                        repeating = false;
                        handler.removeCallbacks(repeater);
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    /**
     * One key-down on {@link MotionEvent#ACTION_DOWN}; {@link Runnable#run release} on {@link
     * MotionEvent#ACTION_UP}, {@link MotionEvent#ACTION_CANCEL}, or finger leaving the key (match
     * {@link #repeatableKeyTouchListener} slide-off semantics).
     */
    public static View.OnTouchListener sustainedKeyTouchListener(Runnable onDown, Runnable onRelease) {
        return sustainedKeyTouchListener(onDown, onRelease, null, null);
    }

    /**
     * Same as {@link #sustainedKeyTouchListener(Runnable, Runnable)} with optional tap preview while the finger
     * stays on the key.
     */
    public static View.OnTouchListener sustainedKeyTouchListener(
            Runnable onDown,
            Runnable onRelease,
            @Nullable BasicKeyPreview preview,
            @Nullable Supplier<String> previewText) {
        return new View.OnTouchListener() {
            private boolean hostKeyDown;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        performKeyHaptic(v);
                        maybeShowPreview(preview, previewText, v);
                        onDown.run();
                        hostKeyDown = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boolean inside = isInsideView(v, e);
                        v.setPressed(inside);
                        if (inside) {
                            maybeShowPreview(preview, previewText, v);
                        } else if (preview != null) {
                            preview.dismiss();
                        }
                        if (!inside && hostKeyDown) {
                            onRelease.run();
                            hostKeyDown = false;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (preview != null) {
                            preview.dismiss();
                        }
                        if (hostKeyDown) {
                            onRelease.run();
                            hostKeyDown = false;
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private static void maybeShowPreview(
            @Nullable BasicKeyPreview preview, @Nullable Supplier<String> previewText, View anchor) {
        if (preview == null || previewText == null) {
            return;
        }
        String t = previewText.get();
        if (t == null || t.isEmpty()) {
            return;
        }
        preview.show(anchor, t);
    }

    private static boolean isInsideView(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x < view.getWidth() && y >= 0 && y < view.getHeight();
    }

    /** Whether the event is inside {@code view}'s bounds (local coordinates). */
    public static boolean isPointerInsideView(View view, MotionEvent event) {
        return isInsideView(view, event);
    }
}
