package com.openterface.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.basic.BasicKeyFeedback;
import com.openterface.keymod.hid.KeyboardHidTransport;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.Locale;
import java.util.Map;

/**
 * KM Basic numpad grid (HID keypad usages).
 *
 * <p>{@link com.openterface.keymod.MainActivity} handles {@code configChanges} for orientation, so
 * this fragment is not recreated on rotation. We inflate {@link R.layout#fragment_basic_numpad}
 * into a host {@link FrameLayout} on first show and again in {@link #onConfigurationChanged} so
 * {@code layout} vs {@code layout-land} variants apply correctly.
 */
public class BasicNumPadFragment extends Fragment {

    public static BasicNumPadFragment instantiateWithPort(@Nullable UsbSerialPort p) {
        BasicNumPadFragment f = new BasicNumPadFragment();
        f.port = p;
        return f;
    }

    public UsbSerialPort port;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        FrameLayout host = new FrameLayout(inflater.getContext());
        host.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        return host;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        inflateAndWireNumpad();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getView() != null) {
            inflateAndWireNumpad();
        }
    }

    /** Re-inflates numpad so {@code layout} / {@code layout-land} match the current orientation. */
    private void inflateAndWireNumpad() {
        ViewGroup host = (ViewGroup) requireView();
        host.removeAllViews();
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_basic_numpad, host, true);
        View numpadRoot = host.findViewById(R.id.basic_numpad_root);
        if (numpadRoot != null) {
            installNumpadContentInsets(numpadRoot);
        }
        GridLayout grid = host.findViewById(R.id.basic_numpad_grid);
        wireGrid(host, grid);
    }

    /**
     * Matches KM Basic keyboard horizontal insets ({@code base + navigationBars}). Bottom uses {@link
     * Math#max} of {@link R.dimen#basic_keyboard_content_inset_bottom} and {@code bars.bottom} so
     * portrait does not stack two full nav clearances; landscape still gets {@code baseEnd +
     * bars.right} for the side nav strip.
     */
    private void installNumpadContentInsets(@NonNull View root) {
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
                                    .getDimensionPixelSize(R.dimen.basic_keyboard_content_inset_bottom);
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

    private void wireGrid(View root, GridLayout grid) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof String)) {
                continue;
            }
            String raw = (String) tag;
            if ("NUMPAD_00".equals(raw)) {
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setClickable(true);
                    tv.setOnTouchListener(
                            (v, event) ->
                                    BasicKeyFeedback.handleStandardKeyTouch(
                                            v, event, () -> sendDoubleNumpadZero(root)));
                }
                continue;
            }
            Integer hid = resolveHid(raw);
            if (hid == null) {
                continue;
            }
            int code = hid;
            child.setClickable(true);
            child.setOnTouchListener(
                    (v, event) -> BasicKeyFeedback.handleStandardKeyTouch(v, event, () -> sendTap(root, code)));
        }
    }

    @Nullable
    private Integer resolveHid(String key) {
        try {
            Map<?, String> map = CH9329MSKBMap.getKeyCodeMap();
            String hex = map.get(key);
            if (hex == null) {
                hex = map.get(key.toUpperCase(Locale.US));
            }
            if (hex == null) {
                return null;
            }
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return null;
        }
    }

    /** Sends two numpad-zero HID taps (physical “00” key). */
    private void sendDoubleNumpadZero(View root) {
        Integer z = resolveHid("NUMPAD_0");
        if (z == null) {
            return;
        }
        int zero = z;
        sendTap(root, zero);
        root.postDelayed(() -> sendTap(root, zero), 50);
    }

    private void sendTap(View root, int hidCode) {
        MainActivity ma = mainActivity();
        if (ma == null) {
            return;
        }
        KeyboardHidTransport.sendKeyReport(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound(),
                0,
                hidCode);
        root.postDelayed(
                () -> KeyboardHidTransport.sendAllKeysReleased(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound()),
                30);
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
