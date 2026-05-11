package com.openterface.keymod.touchpad;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.basic.BasicHoldLockPopup;
import com.openterface.keymod.basic.BasicKeyFeedback;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * L/M/R mouse strip for touchpads: press/hold HID, long-press {@link BasicHoldLockPopup}, and
 * hold-swipe-to-lock via {@link KmBasicHoldLockController}. Shared by KM Basic touchpad and KM Pro
 * composite touchpad.
 */
public final class TouchpadMouseStripBinder {

    /** Same delay as legacy {@code BasicTouchpadFragment#KM_BASIC_HOLD_LOCK_MS}. */
    public static final long HOLD_LOCK_SHOW_DELAY_MS = 1000L;

    /** HID relative button bit: left */
    public static final int BTN_LEFT = 0x01;
    /** HID relative button bit: right */
    public static final int BTN_RIGHT = 0x02;
    /** HID relative button bit: middle */
    public static final int BTN_MIDDLE = 0x04;

    public interface Host {
        @Nullable
        UsbSerialPort getUsbPort();

        @Nullable
        BluetoothService getBluetoothService();

        boolean isBluetoothServiceBound();
    }

    private final Fragment fragment;
    private final Host host;
    private final KmBasicHoldLockController holdLockController;
    private final AtomicInteger stripHeldMouseButtons = new AtomicInteger(0);
    private final Handler stripHandler = new Handler(Looper.getMainLooper());
    private final KmBasicHoldLockController.Listener holdLockListener =
            controller -> refreshMouseStripLockUi();

    @Nullable private View leftView;
    @Nullable private View middleView;
    @Nullable private View rightView;

    public TouchpadMouseStripBinder(
            @NonNull Fragment fragment,
            @NonNull Host host,
            @NonNull KmBasicHoldLockController holdLockController) {
        this.fragment = fragment;
        this.host = host;
        this.holdLockController = holdLockController;
    }

    public void attach(@Nullable View left, @Nullable View middle, @Nullable View right) {
        detachViewsOnly();
        this.leftView = left;
        this.middleView = middle;
        this.rightView = right;
        holdLockController.addListener(holdLockListener);
        wireStripMouseButton(left, BTN_LEFT);
        wireStripMouseButton(middle, BTN_MIDDLE);
        wireStripMouseButton(right, BTN_RIGHT);
        refreshMouseStripLockUi();
    }

    /**
     * Clears listeners and pending lock UI; call from fragment {@code onDestroyView}. Does not
     * clear HID (host fragment handles disconnect).
     */
    public void detach() {
        stripHandler.removeCallbacksAndMessages(null);
        holdLockController.removeListener(holdLockListener);
        detachViewsOnly();
    }

    private void detachViewsOnly() {
        setOnTouchNull(leftView);
        setOnTouchNull(middleView);
        setOnTouchNull(rightView);
        leftView = null;
        middleView = null;
        rightView = null;
    }

    private static void setOnTouchNull(@Nullable View v) {
        if (v != null) {
            v.setOnTouchListener(null);
        }
    }

    public int getStripHeldMask() {
        return stripHeldMouseButtons.get() & 0xFF;
    }

    public int lockedMouseOr0() {
        return holdLockController.getLockedMouseMask();
    }

    public int effectiveMouseMaskForHid() {
        return (stripHeldMouseButtons.get() | holdLockController.getLockedMouseMask()) & 0xFF;
    }

    private void refreshMouseStripLockUi() {
        int locked = holdLockController.getLockedMouseMask();
        if (leftView != null) {
            leftView.setSelected((locked & BTN_LEFT) != 0);
        }
        if (middleView != null) {
            middleView.setSelected((locked & BTN_MIDDLE) != 0);
        }
        if (rightView != null) {
            rightView.setSelected((locked & BTN_RIGHT) != 0);
        }
    }

    private void wireStripMouseButton(@Nullable View button, int bit) {
        if (button == null) {
            return;
        }
        final BasicHoldLockPopup[] popupHolder = new BasicHoldLockPopup[1];
        final float[] lastRaw = new float[2];
        final boolean[] gestureLockedTapOnly = new boolean[1];
        final boolean[] stripFingerDown = new boolean[1];
        final Runnable lockPopupRunnable =
                () -> {
                    if (!stripFingerDown[0]) {
                        return;
                    }
                    if (gestureLockedTapOnly[0]) {
                        return;
                    }
                    popupHolder[0] = new BasicHoldLockPopup();
                    popupHolder[0].show(button, lastRaw[0], lastRaw[1]);
                };
        button.setOnTouchListener(
                (v, event) -> {
                    if (!fragment.isAdded()) {
                        return false;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            stripFingerDown[0] = true;
                            gestureLockedTapOnly[0] =
                                    holdLockController.isMouseLocked(bit)
                                            && (stripHeldMouseButtons.get() & bit) == 0;
                            BasicKeyFeedback.performKeyHaptic(v);
                            v.setPressed(true);
                            lastRaw[0] = event.getRawX();
                            lastRaw[1] = event.getRawY();
                            stripHandler.removeCallbacks(lockPopupRunnable);
                            if (popupHolder[0] != null) {
                                popupHolder[0].dismiss();
                                popupHolder[0] = null;
                            }
                            if (!gestureLockedTapOnly[0]) {
                                stripHandler.postDelayed(lockPopupRunnable, HOLD_LOCK_SHOW_DELAY_MS);
                                int downMask =
                                        stripHeldMouseButtons.updateAndGet(x -> x | bit)
                                                | holdLockController.getLockedMouseMask();
                                MouseRelHidTransport.sendRelButtonsNoMotion(
                                        host.getUsbPort(),
                                        host.getBluetoothService(),
                                        host.isBluetoothServiceBound(),
                                        downMask);
                            }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            lastRaw[0] = event.getRawX();
                            lastRaw[1] = event.getRawY();
                            if (popupHolder[0] != null) {
                                v.setPressed(true);
                                popupHolder[0].updatePointer(event.getRawX(), event.getRawY());
                                return true;
                            }
                            boolean inside = BasicKeyFeedback.isPointerInsideView(v, event);
                            v.setPressed(inside);
                            if (!inside) {
                                stripHandler.removeCallbacks(lockPopupRunnable);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            stripFingerDown[0] = false;
                            stripHandler.removeCallbacks(lockPopupRunnable);
                            v.setPressed(false);
                            if (gestureLockedTapOnly[0]) {
                                gestureLockedTapOnly[0] = false;
                                if (event.getActionMasked() == MotionEvent.ACTION_UP
                                        && BasicKeyFeedback.isPointerInsideView(v, event)) {
                                    holdLockController.unlockMouseButtons(
                                            bit,
                                            host.getUsbPort(),
                                            host.getBluetoothService(),
                                            host.isBluetoothServiceBound());
                                }
                                refreshMouseStripLockUi();
                                return true;
                            }
                            boolean committed = false;
                            if (popupHolder[0] != null) {
                                popupHolder[0].updatePointer(event.getRawX(), event.getRawY());
                                committed = popupHolder[0].commitIfLockSelected();
                                popupHolder[0].dismiss();
                                popupHolder[0] = null;
                            }
                            if (committed) {
                                holdLockController.lockMouseButtons(
                                        bit,
                                        host.getUsbPort(),
                                        host.getBluetoothService(),
                                        host.isBluetoothServiceBound());
                                stripHeldMouseButtons.updateAndGet(x -> x & ~bit);
                                finishStripFingerUp();
                                refreshMouseStripLockUi();
                                return true;
                            }
                            stripHeldMouseButtons.updateAndGet(x -> x & ~bit);
                            finishStripFingerUp();
                            refreshMouseStripLockUi();
                            return true;
                        default:
                            return false;
                    }
                });
    }

    private void finishStripFingerUp() {
        int combined = effectiveMouseMaskForHid();
        if (combined == 0) {
            MouseRelHidTransport.releaseAll(
                    host.getUsbPort(),
                    host.getBluetoothService(),
                    host.isBluetoothServiceBound());
        } else {
            MouseRelHidTransport.sendRelButtonsNoMotion(
                    host.getUsbPort(),
                    host.getBluetoothService(),
                    host.isBluetoothServiceBound(),
                    combined);
        }
    }
}
