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
import com.openterface.keymod.basic.BasicKeyFeedback;
import com.openterface.keymod.basic.BasicPortraitScrollStripView;
import com.openterface.keymod.hid.MouseRelHidTransport;
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
                        deltaY);
            });
        }

        wireMouseButton(view.findViewById(R.id.basic_touchpad_btn_left), () -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                MouseRelHidTransport.sendLeftClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
        wireMouseButton(view.findViewById(R.id.basic_touchpad_btn_middle), () -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                MouseRelHidTransport.sendMiddleClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
        wireMouseButton(view.findViewById(R.id.basic_touchpad_btn_right), () -> {
            MainActivity ma = mainActivity();
            if (ma != null) {
                MouseRelHidTransport.sendRightClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
    }

    /**
     * Horizontal insets match {@link BasicKeyboardFragment#installKeyboardContentInsets}; bottom uses
     * {@link R.dimen#basic_touchpad_content_inset_bottom} so the L/M/R row clears portrait gesture /
     * 3-button nav when {@code navigationBars().bottom} is zero. Plus {@link
     * WindowInsetsCompat.Type#navigationBars()}. Top keeps the layout {@code 8dp} breathing room.
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
                    ViewCompat.setPaddingRelative(
                            v,
                            baseStart + bars.left,
                            v.getPaddingTop(),
                            baseEnd + bars.right,
                            baseBottom + bars.bottom);
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
                if (lastX == 0 && lastY == 0) {
                    MouseRelHidTransport.sendScroll(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            (int) startX,
                            (int) startY);
                } else {
                    MouseRelHidTransport.sendRelMove(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            false,
                            startX,
                            startY,
                            lastX,
                            lastY);
                }
            }

            @Override
            public void onTouchClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendLeftClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchDoubleClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendDoubleClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRightClick() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.sendRightClick(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }

            @Override
            public void onTouchRelease() {
                MainActivity ma = mainActivity();
                if (ma == null) {
                    return;
                }
                MouseRelHidTransport.releaseAll(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
            }
        });
    }

    private static void wireMouseButton(@Nullable View button, Runnable onUpInside) {
        if (button == null) {
            return;
        }
        button.setOnTouchListener(
                (v, event) -> BasicKeyFeedback.handleStandardKeyTouch(v, event, onUpInside));
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
