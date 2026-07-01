package com.openterface.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import com.openterface.keymod.basic.BasicPortraitScrollStripView;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.openterface.keymod.touchpad.TouchpadMouseStripBinder;
import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * KM Basic touchpad: reuses {@link TouchPadView} gestures with shared relative-mouse HID transport.
 */
public class BasicTouchpadFragment extends Fragment {

    public static BasicTouchpadFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicTouchpadFragment f = new BasicTouchpadFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;
    @Nullable private View touchpadRoot;
    @Nullable private KmBasicHoldLockController holdLockController;
    @Nullable private TouchpadMouseStripBinder mouseStripBinder;

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
        } else {
            holdLockController = null;
        }

        wireTouchPad(view.findViewById(R.id.basic_touch_pad));

        BasicPortraitScrollStripView scrollStrip = view.findViewById(R.id.basic_touchpad_scroll_strip);
        if (scrollStrip != null) {
            scrollStrip.setOnStripScrollListener(
                    (deltaX, deltaY) -> {
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

        if (holdLockController != null) {
            TouchpadMouseStripBinder.Host host =
                    new TouchpadMouseStripBinder.Host() {
                        @Override
                        public UsbSerialPort getUsbPort() {
                            return port;
                        }

                        @Override
                        public com.openterface.keymod.BluetoothService getBluetoothService() {
                            MainActivity ma = mainActivity();
                            return ma != null ? ma.getBluetoothService() : null;
                        }

                        @Override
                        public boolean isBluetoothServiceBound() {
                            MainActivity ma = mainActivity();
                            return ma != null && ma.isBluetoothServiceBound();
                        }
                    };
            mouseStripBinder = new TouchpadMouseStripBinder(this, host, holdLockController);
            mouseStripBinder.attach(
                    view.findViewById(R.id.basic_touchpad_btn_left),
                    view.findViewById(R.id.basic_touchpad_btn_middle),
                    view.findViewById(R.id.basic_touchpad_btn_right));
        }
    }

    @Override
    public void onDestroyView() {
        if (mouseStripBinder != null) {
            mouseStripBinder.detach();
            mouseStripBinder = null;
        }
        holdLockController = null;
        super.onDestroyView();
    }

    private int lockedMouseOr0() {
        return holdLockController != null ? holdLockController.getLockedMouseMask() : 0;
    }

    private int effectiveMouseMaskForHid() {
        if (mouseStripBinder != null) {
            return mouseStripBinder.effectiveMouseMaskForHid();
        }
        return lockedMouseOr0() & 0xFF;
    }

    /**
     * Horizontal padding uses the fixed {@code basic_keyboard_content_inset} dimens; landscape side
     * system insets are applied on {@link KeyboardMouseFragment}'s root. Bottom uses {@link
     * R.dimen#basic_touchpad_content_inset_bottom} and {@link
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
                            baseStart,
                            v.getPaddingTop(),
                            baseEnd,
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
        pad.setOnTouchPadListener(
                new TouchPadView.OnTouchPadListener() {
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
                        if ((effectiveMouseMaskForHid() & TouchpadMouseStripBinder.BTN_LEFT) != 0) {
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
                        if ((effectiveMouseMaskForHid() & TouchpadMouseStripBinder.BTN_LEFT) != 0) {
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
                        if ((effectiveMouseMaskForHid() & TouchpadMouseStripBinder.BTN_RIGHT) != 0) {
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
