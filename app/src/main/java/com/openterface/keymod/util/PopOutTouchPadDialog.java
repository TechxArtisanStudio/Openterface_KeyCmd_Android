package com.openterface.keymod.util;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.TouchPadView;

/**
 * Pop-out touchpad using {@link R.layout#dialog_touchpad}: same wash, tips,
 * scroll vs move, and drag behavior as {@link com.openterface.fragment.PresentationFragment}.
 * Optional presentation pointer HID {@code 'C'} toggle on show/dismiss.
 *
 * <p>KM Pro mouse-key strip and hold-lock are implemented on the inline composite touchpad only
 * ({@link com.openterface.fragment.CompositeFragment}); this floating touchpad stays gesture-only
 * so it does not duplicate {@link com.openterface.keymod.prefs.KmProTouchpadPrefs} state or
 * {@link com.openterface.keymod.ConnectionManager} mouse-button hold paths.
 */
public class PopOutTouchPadDialog {

    private static final String TAG = "PopOutTouchPadDialog";

    private static final float TOUCHPAD_WASH_HEIGHT_RATIO = 0.32f;
    private static final float TOUCHPAD_WASH_CLICK_PEAK_INTENSITY = 0.20f;
    private static final float TOUCHPAD_WASH_DRAG_BASE_INTENSITY = 0.12f;
    private static final long TOUCHPAD_WASH_CLICK_FADE_IN_MS = 70L;
    private static final long TOUCHPAD_WASH_CLICK_FADE_OUT_MS = 460L;
    private static final long TOUCHPAD_WASH_DRAG_OFF_FADE_OUT_MS = 340L;
    private static final long POINTER_IDLE_AFTER_MS = 400L;

    private final Fragment host;
    private final boolean sendPresentationPointerKeys;

    private Dialog dialog;
    private View touchpadView;
    private boolean touchpadDragActive;
    private final Handler tipHandler = new Handler(Looper.getMainLooper());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TouchPadPointerPhase touchpadPointerPhase = TouchPadPointerPhase.IDLE;
    private View touchpadDialogWashOverlay;
    private int touchpadDialogWashColor = Color.TRANSPARENT;
    private float touchpadDialogWashIntensity;
    private AnimatorSet touchpadDialogWashAnimator;

    private final Runnable touchpadPointerIdleRunnable =
            () -> {
                touchpadPointerPhase = TouchPadPointerPhase.IDLE;
                touchpadUpdateTips();
            };

    public PopOutTouchPadDialog(@NonNull Fragment host, boolean sendPresentationPointerKeys) {
        this.host = host;
        this.sendPresentationPointerKeys = sendPresentationPointerKeys;
    }

    public void show() {
        if (!host.isAdded()) {
            return;
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            return;
        }

        touchpadDragActive = false;
        touchpadPointerPhase = TouchPadPointerPhase.IDLE;

        touchpadView = LayoutInflater.from(host.requireContext()).inflate(R.layout.dialog_touchpad, null);
        TouchPadView touchSurface = touchpadView.findViewById(R.id.touch_surface);
        TextView tips = touchpadView.findViewById(R.id.presentation_touchpad_tips);
        ImageButton infoBtn = touchpadView.findViewById(R.id.presentation_touchpad_info);

        touchpadInitWashStyle();
        touchpadSetupWash(touchSurface);
        touchpadUpdateTips();

        if (infoBtn != null) {
            infoBtn.setOnClickListener(
                    v -> TouchPadHelpDialog.show(host.requireContext(), true, false));
        }

        touchSurface.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float x, float y, float lastX, float lastY) {
                ConnectionManager cm = getConnectionManager();
                if (cm == null || !cm.isConnected()) {
                    return;
                }
                if (lastX == 0f && lastY == 0f) {
                    touchpadNotePhase(TouchPadPointerPhase.SCROLL);
                    cm.sendScroll((int) x, (int) y);
                    return;
                }
                touchpadNotePhase(TouchPadPointerPhase.MOVE);
                float dx = x - lastX;
                float dy = y - lastY;
                int clampedDx = (int) Math.max(-127, Math.min(127, dx));
                int clampedDy = (int) Math.max(-127, Math.min(127, dy));
                if (clampedDx != 0 || clampedDy != 0) {
                    int buttons = touchpadDragActive ? 1 : 0;
                    cm.sendMouseMovement(clampedDx, clampedDy, buttons);
                }
            }

            @Override
            public void onTouchClick() {
                if (touchpadDragActive) {
                    touchpadDragActive = false;
                    releaseTouchpadMouseButtons();
                    touchpadCancelWashAnimation();
                    touchpadAnimateWashTo(0f, TOUCHPAD_WASH_DRAG_OFF_FADE_OUT_MS, new DecelerateInterpolator());
                    touchpadUpdateTips();
                    return;
                }
                touchpadPulseClickVisual();
                TouchPadHaptics.onLeftClick(touchSurface.getContext());
                sendMouseClick(1);
            }

            @Override
            public void onTouchDoubleClick() {
                if (touchpadDragActive) {
                    touchpadDragActive = false;
                    releaseTouchpadMouseButtons();
                    touchpadCancelWashAnimation();
                    touchpadAnimateWashTo(0f, TOUCHPAD_WASH_DRAG_OFF_FADE_OUT_MS, new DecelerateInterpolator());
                    touchpadUpdateTips();
                    return;
                }
                touchpadPulseClickVisual();
                TouchPadHaptics.onDoubleClick(touchSurface.getContext());
                sendMouseClick(1);
                mainHandler.postDelayed(() -> {
                    if (!host.isAdded()) {
                        return;
                    }
                    touchpadPulseClickVisual();
                    sendMouseClick(1);
                }, 150);
            }

            @Override
            public void onTouchRightClick() {
                touchpadPulseClickVisual();
                TouchPadHaptics.onRightClick(touchSurface.getContext());
                sendMouseClick(2);
            }

            @Override
            public void onTouchLongPress() {
                if (touchpadDragActive) {
                    return;
                }
                ConnectionManager cm = getConnectionManager();
                if (cm == null || !cm.isConnected()) {
                    return;
                }
                touchpadCancelWashAnimation();
                TouchPadHaptics.onDragToggle(touchSurface.getContext());
                touchpadDragActive = true;
                cm.sendMouseMovement(0, 0, 1);
                touchpadApplyWashIntensity(TOUCHPAD_WASH_DRAG_BASE_INTENSITY);
                touchpadUpdateTips();
            }

            @Override
            public void onTouchRelease() {
                touchpadClearPhaseOnFingerUp();
                if (!touchpadDragActive) {
                    releaseTouchpadMouseButtons();
                }
            }
        });
        Dialog d = new Dialog(host.requireContext());
        d.setContentView(touchpadView);
        d.setCanceledOnTouchOutside(true);
        d.setCancelable(true);
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        touchpadView.setOnClickListener(v -> d.dismiss());

        d.setOnDismissListener(
                dlg -> {
                    if (touchpadDragActive) {
                        touchpadDragActive = false;
                        releaseTouchpadMouseButtons();
                    }
                    touchpadTeardownUi();
                    if (sendPresentationPointerKeys) {
                        sendKeyHID(6);
                    }
                    dialog = null;
                    touchpadView = null;
                });
        dialog = d;
        d.show();

        if (sendPresentationPointerKeys) {
            sendKeyHID(6);
        }
        Log.v(TAG, "Touchpad dialog shown, presentationPointerKeys=" + sendPresentationPointerKeys);
    }

    public void dismissIfShowing() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
        touchpadView = null;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private ConnectionManager getConnectionManager() {
        if (host.getActivity() instanceof MainActivity) {
            return ((MainActivity) host.getActivity()).getConnectionManager();
        }
        return null;
    }

    private void sendKeyHID(int keyCode) {
        ConnectionManager cm = getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            return;
        }
        cm.sendKeyEvent(0, keyCode);
        mainHandler.postDelayed(
                () -> {
                    if (!host.isAdded()) {
                        return;
                    }
                    ConnectionManager c = getConnectionManager();
                    if (c != null && c.isConnected()) {
                        c.sendKeyRelease();
                    }
                },
                100);
    }

    private void touchpadInitWashStyle() {
        int accent = ThemeManager.getColorPrimary(host.requireContext());
        touchpadDialogWashColor = ColorUtils.setAlphaComponent(accent, 0x88);
    }

    private void touchpadSetupWash(TouchPadView touchPad) {
        if (touchPad == null) {
            return;
        }
        ViewParent parentRef = touchPad.getParent();
        if (!(parentRef instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) parentRef;
        if (!(parent instanceof FrameLayout)) {
            return;
        }

        if (touchpadDialogWashOverlay == null) {
            touchpadDialogWashOverlay = new View(host.requireContext());
            touchpadDialogWashOverlay.setClickable(false);
            touchpadDialogWashOverlay.setFocusable(false);
            GradientDrawable washDrawable =
                    new GradientDrawable(
                            GradientDrawable.Orientation.BOTTOM_TOP,
                            new int[] {touchpadDialogWashColor, Color.TRANSPARENT});
            touchpadDialogWashOverlay.setBackground(washDrawable);
        } else {
            ViewParent existingParent = touchpadDialogWashOverlay.getParent();
            if (existingParent instanceof ViewGroup && existingParent != parent) {
                ((ViewGroup) existingParent).removeView(touchpadDialogWashOverlay);
            }
        }

        if (touchpadDialogWashOverlay.getParent() == null) {
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM);
            int padIndex = parent.indexOfChild(touchPad);
            int insertAt = padIndex >= 0 ? padIndex + 1 : 0;
            parent.addView(touchpadDialogWashOverlay, insertAt, lp);
        }

        touchPad.post(
                () -> {
                    if (touchpadDialogWashOverlay == null) {
                        return;
                    }
                    ViewGroup.LayoutParams params = touchpadDialogWashOverlay.getLayoutParams();
                    int washHeight = Math.max(1, Math.round(touchPad.getHeight() * TOUCHPAD_WASH_HEIGHT_RATIO));
                    if (params != null && params.height != washHeight) {
                        params.height = washHeight;
                        touchpadDialogWashOverlay.setLayoutParams(params);
                    }
                    touchpadApplyWashIntensity(touchpadDragActive ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f);
                });
    }

    private void touchpadApplyWashIntensity(float intensity) {
        touchpadDialogWashIntensity = Math.max(0f, Math.min(1f, intensity));
        if (touchpadDialogWashOverlay != null) {
            touchpadDialogWashOverlay.setAlpha(touchpadDialogWashIntensity);
        }
    }

    private void touchpadAnimateWashTo(float target, long durationMs, TimeInterpolator interpolator) {
        float clampedTarget = Math.max(0f, Math.min(1f, target));
        ValueAnimator animator = ValueAnimator.ofFloat(touchpadDialogWashIntensity, clampedTarget);
        animator.setDuration(durationMs);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> touchpadApplyWashIntensity((float) a.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet();
        set.play(animator);
        set.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        touchpadDialogWashAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        touchpadDialogWashAnimator = null;
                    }
                });
        touchpadDialogWashAnimator = set;
        set.start();
    }

    private void touchpadCancelWashAnimation() {
        if (touchpadDialogWashAnimator != null) {
            touchpadDialogWashAnimator.cancel();
            touchpadDialogWashAnimator = null;
        }
    }

    private void touchpadPulseClickVisual() {
        if (touchpadDialogWashOverlay == null) {
            return;
        }
        touchpadCancelWashAnimation();
        final float rest = touchpadDragActive ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f;
        final float peak = Math.max(rest, TOUCHPAD_WASH_CLICK_PEAK_INTENSITY);

        ValueAnimator fadeIn = ValueAnimator.ofFloat(rest, peak);
        fadeIn.setDuration(TOUCHPAD_WASH_CLICK_FADE_IN_MS);
        fadeIn.setInterpolator(new LinearInterpolator());
        fadeIn.addUpdateListener(a -> touchpadApplyWashIntensity((float) a.getAnimatedValue()));

        ValueAnimator fadeOut = ValueAnimator.ofFloat(peak, rest);
        fadeOut.setDuration(TOUCHPAD_WASH_CLICK_FADE_OUT_MS);
        fadeOut.setInterpolator(new DecelerateInterpolator());
        fadeOut.addUpdateListener(a -> touchpadApplyWashIntensity((float) a.getAnimatedValue()));

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(fadeIn, fadeOut);
        set.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        touchpadDialogWashAnimator = null;
                        touchpadApplyWashIntensity(rest);
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        touchpadDialogWashAnimator = null;
                        touchpadApplyWashIntensity(
                                touchpadDragActive ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f);
                    }
                });
        touchpadDialogWashAnimator = set;
        set.start();
    }

    private void touchpadUpdateTips() {
        if (touchpadView == null) {
            return;
        }
        TextView tips = touchpadView.findViewById(R.id.presentation_touchpad_tips);
        if (tips == null) {
            return;
        }
        tips.setText(
                TouchPadTipsFormatter.buildCompact(
                        host.requireContext(), touchpadDragActive, touchpadPointerPhase, true));
    }

    private void touchpadNotePhase(TouchPadPointerPhase phase) {
        tipHandler.removeCallbacks(touchpadPointerIdleRunnable);
        touchpadPointerPhase = phase;
        touchpadUpdateTips();
        if (phase != TouchPadPointerPhase.IDLE) {
            tipHandler.postDelayed(touchpadPointerIdleRunnable, POINTER_IDLE_AFTER_MS);
        }
    }

    private void touchpadClearPhaseOnFingerUp() {
        tipHandler.removeCallbacks(touchpadPointerIdleRunnable);
        touchpadPointerPhase = TouchPadPointerPhase.IDLE;
        touchpadUpdateTips();
    }

    private void touchpadTeardownUi() {
        tipHandler.removeCallbacks(touchpadPointerIdleRunnable);
        touchpadCancelWashAnimation();
        if (touchpadDialogWashOverlay != null) {
            ViewParent p = touchpadDialogWashOverlay.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(touchpadDialogWashOverlay);
            }
            touchpadDialogWashOverlay = null;
        }
        touchpadPointerPhase = TouchPadPointerPhase.IDLE;
    }

    private void sendMouseClick(int buttons) {
        ConnectionManager cm = getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            return;
        }
        cm.sendMouseMovement(0, 0, buttons);
        mainHandler.postDelayed(
                () -> {
                    if (!host.isAdded()) {
                        return;
                    }
                    ConnectionManager c = getConnectionManager();
                    if (c != null && c.isConnected()) {
                        c.sendMouseMovement(0, 0, 0);
                    }
                },
                100);
    }

    private void releaseTouchpadMouseButtons() {
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            cm.sendMouseMovement(0, 0, 0);
        }
    }
}
