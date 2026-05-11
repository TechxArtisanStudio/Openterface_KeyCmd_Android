package com.openterface.keymod.util;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openterface.keymod.TouchPadView;

/**
 * Gesture help on the touchpad: soft fade in; fade out when the pad is tapped or the info icon is toggled.
 */
public final class TouchPadHelpOverlay {

    private static final int FADE_IN_MS = 560;
    private static final int FADE_OUT_MS = 700;

    private static final PathInterpolator FADE_IN_INTERP = new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    private static final PathInterpolator FADE_OUT_INTERP = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private TouchPadHelpOverlay() {}

    public static void onInfoPressed(TextView overlay) {
        onInfoPressed(overlay, true);
    }

    /**
     * @param includeMainHelpTitle same as {@link #show(TextView, boolean)} when opening the overlay.
     */
    public static void onInfoPressed(TextView overlay, boolean includeMainHelpTitle) {
        onInfoPressed(overlay, includeMainHelpTitle, false);
    }

    /**
     * @param kmProCompositeTouchpad when true, help text follows KM Pro touchpad mode (Gestures only /
     *     Pad + keys / Hybrid); when false, standard full-gesture help (Mouse, pop-out, etc.).
     */
    public static void onInfoPressed(
            TextView overlay, boolean includeMainHelpTitle, boolean kmProCompositeTouchpad) {
        if (overlay == null) return;
        if (overlay.getVisibility() == View.VISIBLE && overlay.getAlpha() > 0.05f) {
            cancelAnimation(overlay);
            fadeOutAndHide(overlay);
        } else {
            show(overlay, includeMainHelpTitle, kmProCompositeTouchpad);
        }
    }

    /** Fade out if the hint is showing (e.g. user touched the touchpad or the hint text). */
    public static void dismissIfVisible(TextView overlay) {
        if (overlay == null) return;
        if (overlay.getVisibility() == View.VISIBLE && overlay.getAlpha() > 0.05f) {
            cancelAnimation(overlay);
            fadeOutAndHide(overlay);
        }
    }

    /**
     * Instantly removes the gesture help overlay (no fade). Use when switching UI modes where the
     * tutorial text must not linger (e.g. portrait numpad + touchpad).
     */
    public static void hideImmediately(TextView overlay) {
        if (overlay == null) return;
        cancelAnimation(overlay);
        overlay.setAlpha(0f);
        overlay.setVisibility(View.GONE);
    }

    public static void show(TextView overlay) {
        show(overlay, true, false);
    }

    /**
     * @param includeMainHelpTitle when false, omits the top “Touchpad gestures” line (compact dialog layout).
     */
    public static void show(TextView overlay, boolean includeMainHelpTitle) {
        show(overlay, includeMainHelpTitle, false);
    }

    /**
     * @param kmProCompositeTouchpad when true, uses {@link
     *     TouchPadTipsFormatter#buildKmProCompositeHelpOverlayText}.
     */
    public static void show(TextView overlay, boolean includeMainHelpTitle, boolean kmProCompositeTouchpad) {
        if (overlay == null) return;
        cancelAnimation(overlay);
        overlay.setText(
                kmProCompositeTouchpad
                        ? TouchPadTipsFormatter.buildKmProCompositeHelpOverlayText(
                                overlay.getContext(), includeMainHelpTitle)
                        : TouchPadTipsFormatter.buildGestureHelpOverlayText(
                                overlay.getContext(), includeMainHelpTitle));
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate()
                .alpha(1f)
                .setDuration(FADE_IN_MS)
                .setInterpolator(FADE_IN_INTERP)
                .withLayer()
                .start();
    }

    public static void clear(TextView overlay) {
        if (overlay != null) {
            cancelAnimation(overlay);
        }
    }

    /**
     * Touch on the pad or footer tips dismisses the hint. We intentionally do not attach a
     * dismiss listener to {@code help}; dismiss via pad tap or the info button toggle.
     */
    public static void wireDismissTouchTargets(
            @Nullable TouchPadView pad, @Nullable TextView tips, @Nullable TextView help) {
        if (help == null) return;
        if (tips != null) {
            tips.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dismissIfVisible(help);
                }
                return false;
            });
        }
        if (pad != null) {
            pad.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dismissIfVisible(help);
                }
                return false;
            });
        }
    }

    private static void cancelAnimation(TextView overlay) {
        overlay.animate().cancel();
    }

    private static void fadeOutAndHide(TextView overlay) {
        cancelAnimation(overlay);
        overlay.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_MS)
                .setInterpolator(FADE_OUT_INTERP)
                .withLayer()
                .withEndAction(() -> overlay.setVisibility(View.GONE))
                .start();
    }
}
