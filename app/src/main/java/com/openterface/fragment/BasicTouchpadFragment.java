package com.openterface.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.basic.BasicHoldLockPopup;
import com.openterface.keymod.basic.BasicKeyFeedback;
import com.openterface.keymod.basic.BasicPortraitScrollStripView;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * KM Basic touchpad: reuses {@link TouchPadView} gestures with shared relative-mouse HID transport.
 */
public class BasicTouchpadFragment extends Fragment {

    private static final long KM_BASIC_HOLD_LOCK_MS = 1000L;

    /** HID relative button bit: left */
    private static final int BTN_LEFT = 0x01;
    /** HID relative button bit: right */
    private static final int BTN_RIGHT = 0x02;
    /** HID relative button bit: middle */
    private static final int BTN_MIDDLE = 0x04;

    /** Strip L/M/R currently held (OR of {@link #BTN_LEFT} / {@link #BTN_RIGHT} / {@link #BTN_MIDDLE}). */
    private final AtomicInteger stripHeldMouseButtons = new AtomicInteger(0);

    private final Handler stripHandler = new Handler(Looper.getMainLooper());
    @Nullable private KmBasicHoldLockController holdLockController;
    private final KmBasicHoldLockController.Listener holdLockListener =
            controller -> refreshMouseStripLockUi();

    public static BasicTouchpadFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicTouchpadFragment f = new BasicTouchpadFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;
    @Nullable private View touchpadRoot;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_basic_touchpad, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(lp);
        }
        touchpadRoot = view.findViewById(R.id.basic_touchpad_root);
        if (touchpadRoot != null) {
            installTouchpadContentInsets(touchpadRoot);
        }
        Fragment p = getParentFragment();
        if (p instanceof KeyboardMouseFragment) {
            holdLockController = ((KeyboardMouseFragment) p).getHoldLockController();
            holdLockController.addListener(holdLockListener);
        } else {
            holdLockController = null;
        }

        wireTouchPad(view.findViewById(R.id.basic_touch_pad));

        BasicPortraitScrollStripView scrollStrip = view.findViewById(R.id.basic_touchpad_scroll_strip);
        if (scrollStrip != null) {
            scrollStrip.setOnStripScrollListener((deltaX, deltaY) -> {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendScroll(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound(),
                        deltaX,
                        deltaY,
                        effectiveMouseMaskForHid());
            });
        }

        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_left), BTN_LEFT);
        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_middle), BTN_MIDDLE);
        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_right), BTN_RIGHT);
        refreshMouseStripLockUi();
    }

    @Override
    public void onDestroyView() {
        if (holdLockController != null) {
            holdLockController.removeListener(holdLockListener);
            holdLockController = null;
        }
        super.onDestroyView();
    }

    private int lockedMouseOr0() {
        return holdLockController != null ? holdLockController.getLockedMouseMask() : 0;
    }

    private int effectiveMouseMaskForHid() {
        return (stripHeldMouseButtons.get() | lockedMouseOr0()) & 0xFF;
    }

    private void refreshMouseStripLockUi() {
        View v = getView();
        if (v == null) {
            return;
        }
        int locked = lockedMouseOr0();
        View left = v.findViewById(R.id.basic_touchpad_btn_left);
        View mid = v.findViewById(R.id.basic_touchpad_btn_middle);
        View right = v.findViewById(R.id.basic_touchpad_btn_right);
        if (left != null) {
            left.setSelected((locked & BTN_LEFT) != 0);
        }
        if (mid != null) {
            mid.setSelected((locked & BTN_MIDDLE) != 0);
        }
        if (right != null) {
            right.setSelected((locked & BTN_RIGHT) != 0);
        }
    }

    /**
     * Horizontal insets match {@link BasicKeyboardFragment#installKeyboardContentInsets}. Bottom
     * uses {@link R.dimen#basic_touchpad_content_inset_bottom} and {@link
     * WindowInsetsCompat.Type#navigationBars()} bottom, combined as {@code max(base, bars.bottom)}
     * so we do not stack two full nav clearances when the window already fits above the bar but
     * insets are still reported. Top keeps the layout {@code 8dp} breathing room.
     */
    private void installTouchpadContentInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    int baseStart =
                            getResources().getDimensionPixelSize(R.dimen.basic_keyboard_content_inset);
                    int baseEnd =
                            getResources()
                                    .getDimensionPixelSize(R.dimen.basic_keyboard_content_inset_end);
                    int baseBottom =
                            getResources()
                                    .getDimensionPixelSize(R.dimen.basic_touchpad_content_inset_bottom);
                    int bottomPad = Math.max(baseBottom, bars.bottom);
                    ViewCompat.setPaddingRelative(
                            v,
                            baseStart + bars.left,
                            v.getPaddingTop(),
                            baseEnd + bars.right,
                            bottomPad);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (touchpadRoot != null) {
            installTouchpadContentInsets(touchpadRoot);
        }
    }

    private void wireTouchPad(@NonNull TouchPadView pad) {
        pad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                int mask = effectiveMouseMaskForHid();
                if (lastX == 0 && lastY == 0) {
                    MouseRelHidTransport.sendScroll(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            (int) startX,
                            (int) startY,
                            mask);
                } else {
                    MouseRelHidTransport.sendRelMove(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            mask,
                            startX,
                            startY,
                            lastX,
                            lastY);
                }
            }

            @Override
            public void onTouchClick() {
                if ((effectiveMouseMaskForHid() & BTN_LEFT) != 0) {
                    return;
                }
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendLeftClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchDoubleClick() {
                if ((effectiveMouseMaskForHid() & BTN_LEFT) != 0) {
                    return;
                }
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendDoubleClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRightClick() {
                if ((effectiveMouseMaskForHid() & BTN_RIGHT) != 0) {
                    return;
                }
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendRightClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRelease() {
                if (effectiveMouseMaskForHid() != 0) {
                    return;
                }
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.releaseAll(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
    }

    private void wireStripMouseButton(@Nullable View button, int bit) {
        if (button == null) {
            return;
        }
        final BasicHoldLockPopup[] popupHolder = new BasicHoldLockPopup[1];
        /** Latest raw coords; used as hold-lock gesture origin when the popup fires (not initial DOWN). */
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
                    MainActivity ma = mainActivity();
                    if (ma == null) {
                        return false;
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            stripFingerDown[0] = true;
                            gestureLockedTapOnly[0] =
                                    holdLockController != null
                                            && holdLockController.isMouseLocked(bit)
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
                                stripHandler.postDelayed(lockPopupRunnable, KM_BASIC_HOLD_LOCK_MS);
                                int downMask =
                                        stripHeldMouseButtons.updateAndGet(x -> x | bit)
                                                | lockedMouseOr0();
                                MouseRelHidTransport.sendRelButtonsNoMotion(
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound(),
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
                                        && BasicKeyFeedback.isPointerInsideView(v, event)
                                        && holdLockController != null) {
                                    holdLockController.unlockMouseButtons(
                                            bit,
                                            port,
                                            ma.getBluetoothService(),
                                            ma.isBluetoothServiceBound());
                                }
                                refreshMouseStripLockUi();
                                return true;
                            }
                            boolean committed = false;
                            if (popupHolder[0] != null) {
                                popupHolder[0].updatePointer(
                                        event.getRawX(), event.getRawY());
                                committed = popupHolder[0].commitIfLockSelected();
                                popupHolder[0].dismiss();
                                popupHolder[0] = null;
                            }
                            if (committed && holdLockController != null) {
                                holdLockController.lockMouseButtons(
                                        bit,
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound());
                                stripHeldMouseButtons.updateAndGet(x -> x & ~bit);
                                finishStripFingerUp(ma);
                                refreshMouseStripLockUi();
                                return true;
                            }
                            stripHeldMouseButtons.updateAndGet(x -> x & ~bit);
                            finishStripFingerUp(ma);
                            refreshMouseStripLockUi();
                            return true;
                        default:
                            return false;
                    }
                });
    }

    private void finishStripFingerUp(MainActivity ma) {
        int combined = effectiveMouseMaskForHid();
        if (combined == 0) {
            MouseRelHidTransport.releaseAll(
                    port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
        } else {
            MouseRelHidTransport.sendRelButtonsNoMotion(
                    port, ma.getBluetoothService(), ma.isBluetoothServiceBound(), combined);
        }
    }

    @Nullable
    private MainActivity mainActivity() {
        if (requireActivity() instanceof MainActivity) {
            return (MainActivity) requireActivity();
        }
        return null;
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
    }
}
