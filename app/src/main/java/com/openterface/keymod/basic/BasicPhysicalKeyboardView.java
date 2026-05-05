package com.openterface.keymod.basic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.hid.KeyboardHidTransport;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.Locale;

/**
 * KM Basic physical-style keyboard (F-row, QWERTY, modifiers). No shortcut strip.
 */
public class BasicPhysicalKeyboardView extends LinearLayout {

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private MainActivity mainActivity;
    @Nullable
    private UsbSerialPort port;

    private boolean stickyShift;
    private boolean stickyCtrl;
    private boolean stickyAlt;
    private boolean stickyWin;
    private boolean capsLock;

    public BasicPhysicalKeyboardView(Context context) {
        super(context);
        init();
    }

    public BasicPhysicalKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BasicPhysicalKeyboardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setBaselineAligned(false);
        if (!isInEditMode()) {
            setSaveEnabled(false);
        }
    }

    public void bind(@Nullable MainActivity activity, @Nullable UsbSerialPort usbPort) {
        mainActivity = activity;
        port = usbPort;
        removeAllViews();
        if (activity == null) {
            return;
        }
        addRowEscF();
        addRowNumbers();
        addRowQwerty1();
        addRowQwerty2();
        addRowQwerty3();
        addRowBottom();
    }

    private int parseMod(String name) {
        return Integer.parseInt(CH9329MSKBMap.KBShortCutKey().get(name), 16);
    }

    private int stickyModifiersMask() {
        int m = 0;
        if (stickyCtrl) {
            m |= parseMod("Ctrl");
        }
        if (stickyShift) {
            m |= parseMod("Shift");
        }
        if (stickyAlt) {
            m |= parseMod("Alt");
        }
        if (stickyWin) {
            m |= parseMod("Win");
        }
        return m;
    }

    private void tapKey(int hidCode, boolean isLetter, boolean needsShiftForSymbol) {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        int mods = stickyModifiersMask();
        boolean shiftForCase = isLetter && capsLock != stickyShift;
        if (needsShiftForSymbol) {
            mods |= parseMod("Shift");
        } else if (shiftForCase) {
            mods |= parseMod("Shift");
        }
        KeyboardHidTransport.sendKeyReport(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound(),
                mods,
                hidCode);
        handler.postDelayed(
                () -> KeyboardHidTransport.sendAllKeysReleased(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound()),
                30);
    }

    private void tapModifierToggle(String which) {
        switch (which) {
            case "shift":
                stickyShift = !stickyShift;
                break;
            case "ctrl":
                stickyCtrl = !stickyCtrl;
                break;
            case "alt":
                stickyAlt = !stickyAlt;
                break;
            case "win":
                stickyWin = !stickyWin;
                break;
            case "caps":
                capsLock = !capsLock;
                break;
            default:
                break;
        }
    }

    private View inflateKey(LinearLayout row, String label, @Nullable String hint, float weight) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.basic_key_button, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        v.setLayoutParams(lp);
        TextView lab = v.findViewById(R.id.basic_key_label);
        TextView h = v.findViewById(R.id.basic_key_hint);
        lab.setText(label);
        if (hint != null && !hint.isEmpty()) {
            h.setText(hint);
            h.setVisibility(VISIBLE);
        } else {
            h.setVisibility(GONE);
        }
        row.addView(v);
        return v;
    }

    private void wireTap(View v, Runnable onTap) {
        v.setOnTouchListener(
                (view, event) -> BasicKeyFeedback.handleStandardKeyTouch(view, event, onTap));
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        row.setOrientation(HORIZONTAL);
        addView(row);
        return row;
    }

    private void addRowEscF() {
        LinearLayout row = newRow();
        int[] codes = new int[] {
                0x29, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45
        };
        String[] labels = new String[] {
                "Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
        };
        for (int i = 0; i < labels.length; i++) {
            View k = inflateKey(row, labels[i], null, 1f);
            int code = codes[i];
            wireTap(k, () -> tapKey(code, false, false));
        }
    }

    private void addRowNumbers() {
        LinearLayout row = newRow();
        String[] labels = {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "\u232B"};
        String[] hints = {"~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", null};
        int[] codes = {0x35, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2A};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            float w = i == labels.length - 1 ? 1.4f : 1f;
            View k = inflateKey(row, labels[idx], hints[idx], w);
            wireTap(k, () -> tapKey(codes[idx], false, hints[idx] != null && stickyShift));
        }
    }

    private void addRowQwerty1() {
        LinearLayout row = newRow();
        String[] labels = {"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"};
        String[] hints = {null, null, null, null, null, null, null, null, null, null, null, "{", "}", "|"};
        int[] codes = {0x2B, 0x14, 0x1A, 0x08, 0x15, 0x17, 0x1C, 0x18, 0x0C, 0x12, 0x13, 0x2F, 0x30, 0x64};
        float[] w = {1.3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            boolean letter = labels[idx].length() == 1 && Character.isLetter(labels[idx].charAt(0));
            wireTap(k, () -> tapKey(codes[idx], letter, hints[idx] != null && stickyShift));
        }
    }

    private void addRowQwerty2() {
        LinearLayout row = newRow();
        String[] labels = {"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"};
        String[] hints = {null, null, null, null, null, null, null, null, null, null, ":", "\"", null};
        int[] codes = {0x39, 0x04, 0x16, 0x07, 0x09, 0x0A, 0x0B, 0x0D, 0x0E, 0x0F, 0x33, 0x34, 0x28};
        float[] w = {1.4f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.6f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            if (i == 0) {
                wireTap(k, () -> tapModifierToggle("caps"));
            } else if (i == labels.length - 1) {
                wireTap(k, () -> tapKey(codes[idx], false, false));
            } else {
                wireTap(k, () -> tapKey(codes[idx], true, hints[idx] != null && stickyShift));
            }
        }
    }

    private void addRowQwerty3() {
        LinearLayout row = newRow();
        String[] labels = {"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "Shift"};
        String[] hints = {null, null, null, null, null, null, null, null, "<", ">", "?", null};
        int[] codes = {0xE1, 0x1D, 0x1B, 0x06, 0x19, 0x05, 0x11, 0x10, 0x36, 0x37, 0x38, 0xE5};
        float[] w = {1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.5f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            if (i == 0 || i == labels.length - 1) {
                wireTap(k, () -> tapModifierToggle("shift"));
            } else {
                wireTap(k, () -> tapKey(codes[idx], true, hints[idx] != null && stickyShift));
            }
        }
    }

    private void addRowBottom() {
        LinearLayout row = newRow();
        MainActivity ma = mainActivity;
        String os = ma != null ? ma.getTargetOs().toLowerCase(Locale.US) : "macos";
        if ("macos".equals(os)) {
            addMacBottomRow(row);
        } else {
            addWinBottomRow(row);
        }
    }

    /**
     * Arrow keys in a 2×3 cluster (same total horizontal weight as four single keys on Mac):
     * {@code [ ][↑][ ]} / {@code [←][↓][→]}.
     */
    private View createArrowCluster(float horizontalWeight) {
        Context c = getContext();
        LinearLayout cluster = new LinearLayout(c);
        cluster.setOrientation(VERTICAL);
        cluster.setBaselineAligned(false);
        LinearLayout.LayoutParams clusterLp =
                new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, horizontalWeight);
        cluster.setLayoutParams(clusterLp);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setBaselineAligned(false);
        top.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addArrowRowSpacer(top, 1f);
        View up = inflateKey(top, "\u2191", null, 1f);
        wireTap(up, () -> tapKey(0x52, false, false));
        addArrowRowSpacer(top, 1f);

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(HORIZONTAL);
        bottom.setBaselineAligned(false);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        View left = inflateKey(bottom, "\u2190", null, 1f);
        wireTap(left, () -> tapKey(0x50, false, false));
        View down = inflateKey(bottom, "\u2193", null, 1f);
        wireTap(down, () -> tapKey(0x51, false, false));
        View right = inflateKey(bottom, "\u2192", null, 1f);
        wireTap(right, () -> tapKey(0x4F, false, false));

        cluster.addView(top);
        cluster.addView(bottom);
        return cluster;
    }

    private void addArrowRowSpacer(LinearLayout row, float weight) {
        Space s = new Space(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        s.setLayoutParams(lp);
        row.addView(s);
    }

    private void addMacBottomRow(LinearLayout row) {
        String[] labels = {"Ctrl", "\u2325", "\u2318", "Space", "\u2318", "\u2325"};
        float[] w = {1f, 1f, 1f, 3f, 1f, 1f};
        View k0 = inflateKey(row, labels[0], null, w[0]);
        wireTap(k0, () -> tapModifierToggle("ctrl"));
        View k1 = inflateKey(row, labels[1], null, w[1]);
        wireTap(k1, () -> tapModifierToggle("alt"));
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireTap(k2, () -> tapModifierToggle("win"));
        View k3 = inflateKey(row, labels[3], null, w[3]);
        wireTap(k3, () -> tapKey(0x2C, false, false));
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireTap(k4, () -> tapModifierToggle("win"));
        View k5 = inflateKey(row, labels[5], null, w[5]);
        wireTap(k5, () -> tapModifierToggle("alt"));
        row.addView(createArrowCluster(4f));
    }

    private void addWinBottomRow(LinearLayout row) {
        String[] labels = {"Ctrl", "Win", "Alt", "Space", "Alt", "App", "Ctrl"};
        float[] w = {1f, 1f, 1f, 3f, 1f, 1f, 1f};
        View k0 = inflateKey(row, labels[0], null, w[0]);
        wireTap(k0, () -> tapModifierToggle("ctrl"));
        View k1 = inflateKey(row, labels[1], null, w[1]);
        wireTap(k1, () -> tapModifierToggle("win"));
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireTap(k2, () -> tapModifierToggle("alt"));
        View k3 = inflateKey(row, labels[3], null, w[3]);
        wireTap(k3, () -> tapKey(0x2C, false, false));
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireTap(k4, () -> tapModifierToggle("alt"));
        View k5 = inflateKey(row, labels[5], null, w[5]);
        wireTap(k5, () -> tapKey(0x65, false, false));
        View k6 = inflateKey(row, labels[6], null, w[6]);
        wireTap(k6, () -> tapModifierToggle("ctrl"));
        row.addView(createArrowCluster(4f));
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
    }
}
