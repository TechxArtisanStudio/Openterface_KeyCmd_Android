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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * KM Basic touchpad: reuses {@link TouchPadView} gestures with shared relative-mouse HID transport.
 */
public class BasicTouchpadFragment extends Fragment {

    /** HID relative button bit: left */
    private static final int BTN_LEFT = 0x01;
    /** HID relative button bit: right */
    private static final int BTN_RIGHT = 0x02;
    /** HID relative button bit: middle */
    private static final int BTN_MIDDLE = 0x04;

    /** Strip L/M/R currently held (OR of {@link #BTN_LEFT} / {@link #BTN_RIGHT} / {@link #BTN_MIDDLE}). */
    private final AtomicInteger stripHeldMouseButtons = new AtomicInteger(0);

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
                        deltaY,
                        stripHeldMouseButtons.get());
            });
        }

        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_left), BTN_LEFT);
        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_middle), BTN_MIDDLE);
        wireStripMouseButton(view.findViewById(R.id.basic_touchpad_btn_right), BTN_RIGHT);
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
                int mask = stripHeldMouseButtons.get();
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
                if ((stripHeldMouseButtons.get() & BTN_LEFT) != 0) {
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
                if ((stripHeldMouseButtons.get() & BTN_LEFT) != 0) {
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
                if ((stripHeldMouseButtons.get() & BTN_RIGHT) != 0) {
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
                if (stripHeldMouseButtons.get() != 0) {
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
        button.setOnTouchListener(
                BasicKeyFeedback.sustainedKeyTouchListener(
                        () -> {
                            MainActivity ma = mainActivity();
                            if (ma == null) {
                                return;
                            }
                            int mask = stripHeldMouseButtons.updateAndGet(v -> v | bit);
                            MouseRelHidTransport.sendRelButtonsNoMotion(
                                    port,
                                    ma.getBluetoothService(),
                                    ma.isBluetoothServiceBound(),
                                    mask);
                        },
                        () -> {
                            MainActivity ma = mainActivity();
                            if (ma == null) {
                                return;
                            }
                            int mask = stripHeldMouseButtons.updateAndGet(v -> v & ~bit);
                            if (mask == 0) {
                                MouseRelHidTransport.releaseAll(
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound());
                            } else {
                                MouseRelHidTransport.sendRelButtonsNoMotion(
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound(),
                                        mask);
                            }
                        }));
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
