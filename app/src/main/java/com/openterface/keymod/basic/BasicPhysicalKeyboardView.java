package com.openterface.keymod.basic;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.hid.KeyboardHidTransport;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * KM Basic physical-style keyboard (F-row, QWERTY, modifiers). No shortcut strip.
 */
public class BasicPhysicalKeyboardView extends LinearLayout {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BasicKeyPreview keyPreview = new BasicKeyPreview();
    @Nullable
    private MainActivity mainActivity;
    @Nullable
    private UsbSerialPort port;

    private boolean stickyShift;
    private boolean stickyCtrl;
    private boolean stickyAlt;
    private boolean stickyWin;
    private boolean capsLock;

    @Nullable
    private View shiftKeyLeft;
    @Nullable
    private View shiftKeyRight;
    @Nullable
    private View capsKeyView;

    private final List<View> ctrlModifierKeys = new ArrayList<>(2);
    private final List<View> altModifierKeys = new ArrayList<>(2);
    private final List<View> winModifierKeys = new ArrayList<>(2);

    /** CH9329 extended keyboard codes (same as Pro momentary modifiers). */
    private static final int HID_EXT_LCTRL = 0xE0;
    private static final int HID_EXT_LSHIFT = 0xE1;
    private static final int HID_EXT_LALT = 0xE2;
    private static final int HID_EXT_LGUI = 0xE3;
    private static final int HID_EXT_RCTRL = 0xE4;
    private static final int HID_EXT_RSHIFT = 0xE5;
    private static final int HID_EXT_RALT = 0xE6;
    private static final int HID_EXT_RGUI = 0xE7;

    private int chordHeldModMask;
    @Nullable
    private View chordHeldView;
    private View chordLongPressAnchor;
    private int chordLongPressPendingMask;
    private boolean chordLongPressActivated;
    private final Runnable chordLongPressRunnable =
            () -> {
                if (chordLongPressAnchor == null) {
                    return;
                }
                chordLongPressActivated = true;
                chordHeldModMask = chordLongPressPendingMask;
                chordHeldView = chordLongPressAnchor;
                chordHeldView.setSelected(true);
                keyPreview.dismiss();
            };

    private final SharedPreferences.OnSharedPreferenceChangeListener kmBasicPrefListener =
            (sharedPreferences, key) -> {
                if (!KmBasicKeyboardPrefs.PREF_KEY.equals(key)) {
                    return;
                }
                stickyShift = false;
                stickyCtrl = false;
                stickyAlt = false;
                stickyWin = false;
                capsLock = false;
                MainActivity ma = mainActivity;
                if (ma != null && isAttachedToWindow()) {
                    bind(ma, port);
                }
            };

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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .registerOnSharedPreferenceChangeListener(kmBasicPrefListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .unregisterOnSharedPreferenceChangeListener(kmBasicPrefListener);
        }
        super.onDetachedFromWindow();
    }

    public void bind(@Nullable MainActivity activity, @Nullable UsbSerialPort usbPort) {
        keyPreview.dismiss();
        handler.removeCallbacks(chordLongPressRunnable);
        clearChordHoldState();
        shiftKeyLeft = null;
        shiftKeyRight = null;
        capsKeyView = null;
        ctrlModifierKeys.clear();
        altModifierKeys.clear();
        winModifierKeys.clear();
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
        refreshModifierVisuals();
    }

    private boolean isMomentaryChordMode() {
        return KmBasicKeyboardPrefs.isMomentaryChordMode(getContext());
    }

    private void clearChordHoldState() {
        handler.removeCallbacks(chordLongPressRunnable);
        chordLongPressAnchor = null;
        chordLongPressActivated = false;
        chordHeldModMask = 0;
        if (chordHeldView != null) {
            chordHeldView.setSelected(false);
            chordHeldView = null;
        }
    }

    /** Sticky latched shift, or chord mode with Shift long-held. */
    private boolean shiftLayerActive() {
        if (isMomentaryChordMode()) {
            return (chordHeldModMask & parseMod("Shift")) != 0;
        }
        return stickyShift;
    }

    private void tapModifierMomentary(int extendedKeyCode) {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        KeyboardHidTransport.sendKeyReport(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound(),
                0,
                extendedKeyCode);
        handler.postDelayed(
                () ->
                        KeyboardHidTransport.sendAllKeysReleased(
                                port,
                                ma.getBluetoothService(),
                                ma.isBluetoothServiceBound()),
                30);
    }

    private View.OnTouchListener createChordModifierTouchListener(
            final int extendedKeyCode, final int holdModMask, final Supplier<String> previewText) {
        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    BasicKeyFeedback.performKeyHaptic(v);
                    chordLongPressActivated = false;
                    chordLongPressAnchor = v;
                    chordLongPressPendingMask = holdModMask;
                    handler.removeCallbacks(chordLongPressRunnable);
                    handler.postDelayed(
                            chordLongPressRunnable,
                            ViewConfiguration.get(v.getContext()).getLongPressTimeout());
                    if (previewText != null) {
                        keyPreview.show(v, previewText.get());
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    boolean inside = BasicKeyFeedback.isPointerInsideView(v, event);
                    v.setPressed(inside);
                    if (!inside) {
                        handler.removeCallbacks(chordLongPressRunnable);
                        keyPreview.dismiss();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(chordLongPressRunnable);
                    v.setPressed(false);
                    keyPreview.dismiss();
                    if (chordLongPressActivated) {
                        chordHeldModMask = 0;
                        if (chordHeldView != null) {
                            chordHeldView.setSelected(false);
                            chordHeldView = null;
                        }
                        chordLongPressActivated = false;
                        chordLongPressAnchor = null;
                    } else if (BasicKeyFeedback.isPointerInsideView(v, event)) {
                        tapModifierMomentary(extendedKeyCode);
                    }
                    chordLongPressAnchor = null;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(chordLongPressRunnable);
                    v.setPressed(false);
                    keyPreview.dismiss();
                    if (chordLongPressActivated) {
                        chordHeldModMask = 0;
                        if (chordHeldView != null) {
                            chordHeldView.setSelected(false);
                            chordHeldView = null;
                        }
                        chordLongPressActivated = false;
                    }
                    chordLongPressAnchor = null;
                    return true;
                default:
                    return false;
            }
        };
    }

    private void wireChordModifierTouch(View v, int extendedKeyCode, int holdModMask, Supplier<String> preview) {
        v.setOnTouchListener(createChordModifierTouchListener(extendedKeyCode, holdModMask, preview));
    }

    private void wireBottomModifier(View v, String which, int extKey, int holdMask, Supplier<String> preview) {
        if (isMomentaryChordMode()) {
            wireChordModifierTouch(v, extKey, holdMask, preview);
        } else {
            wireStickyModifierTap(v, which, preview);
        }
    }

    private void wireShiftKey(View v, boolean isLeft, Supplier<String> preview) {
        if (isLeft) {
            shiftKeyLeft = v;
        } else {
            shiftKeyRight = v;
        }
        if (isMomentaryChordMode()) {
            int ext = isLeft ? HID_EXT_LSHIFT : HID_EXT_RSHIFT;
            wireChordModifierTouch(v, ext, parseMod("Shift"), preview);
        } else {
            styleStickyModifierKeySurface(v);
            wireTap(
                    v,
                    () -> {
                        tapModifierToggle("shift");
                        refreshModifierVisuals();
                    },
                    preview);
        }
    }

    private void refreshModifierVisuals() {
        if (isMomentaryChordMode()) {
            clearStickyLatchVisuals();
        } else {
            refreshStickyModifierVisuals();
        }
    }

    /** In chord mode, latched modifier highlights are off; Shift/Caps use default key background. */
    private void clearStickyLatchVisuals() {
        if (shiftKeyLeft != null) {
            shiftKeyLeft.setSelected(false);
        }
        if (shiftKeyRight != null) {
            shiftKeyRight.setSelected(false);
        }
        setSelectedOnModifierKeys(ctrlModifierKeys, false);
        setSelectedOnModifierKeys(altModifierKeys, false);
        setSelectedOnModifierKeys(winModifierKeys, false);
        if (capsKeyView != null) {
            capsKeyView.setSelected(false);
        }
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

    private int effectiveModifiersMask() {
        if (isMomentaryChordMode()) {
            return chordHeldModMask;
        }
        return stickyModifiersMask();
    }

    private void tapKey(int hidCode, boolean isLetter, boolean needsShiftForSymbol) {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        int mods = effectiveModifiersMask();
        boolean shiftForCase =
                !isMomentaryChordMode() && isLetter && capsLock != stickyShift;
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

    private String previewLetter(String letter) {
        if (letter.length() != 1) {
            return letter;
        }
        char c = letter.charAt(0);
        if (!Character.isLetter(c)) {
            return letter;
        }
        boolean upper =
                isMomentaryChordMode() ? capsLock : (capsLock != stickyShift);
        return upper ? letter.toUpperCase(Locale.ROOT) : letter.toLowerCase(Locale.ROOT);
    }

    /** Primary label, or shift hint when sticky Shift is on (number/symbol row and punctuation). */
    private String previewShiftLayer(String primary, @Nullable String hint) {
        if (hint != null && shiftLayerActive()) {
            return hint;
        }
        return primary;
    }

    /** Letters use caps/shift case; other keys use shift layer when applicable. */
    private String previewQwertyRowKey(String label, @Nullable String hint) {
        if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
            return previewLetter(label);
        }
        return previewShiftLayer(label, hint);
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

    private void refreshStickyModifierVisuals() {
        if (shiftKeyLeft != null) {
            shiftKeyLeft.setSelected(stickyShift);
        }
        if (shiftKeyRight != null) {
            shiftKeyRight.setSelected(stickyShift);
        }
        setSelectedOnModifierKeys(ctrlModifierKeys, stickyCtrl);
        setSelectedOnModifierKeys(altModifierKeys, stickyAlt);
        setSelectedOnModifierKeys(winModifierKeys, stickyWin);
        if (capsKeyView != null) {
            capsKeyView.setSelected(capsLock);
        }
    }

    private static void setSelectedOnModifierKeys(List<View> keys, boolean selected) {
        for (int i = 0, n = keys.size(); i < n; i++) {
            keys.get(i).setSelected(selected);
        }
    }

    /** Same drawable/states as Shift: latched modifier uses {@code state_selected}. */
    private void styleStickyModifierKeySurface(View v) {
        v.setBackgroundResource(R.drawable.basic_shift_key_background);
    }

    private void registerStickyModifierKey(String which, View v) {
        styleStickyModifierKeySurface(v);
        switch (which) {
            case "ctrl":
                ctrlModifierKeys.add(v);
                break;
            case "alt":
                altModifierKeys.add(v);
                break;
            case "win":
                winModifierKeys.add(v);
                break;
            default:
                break;
        }
    }

    private void wireStickyModifierTap(View v, String which, Supplier<String> previewText) {
        registerStickyModifierKey(which, v);
        wireTap(
                v,
                () -> {
                    tapModifierToggle(which);
                    refreshModifierVisuals();
                },
                previewText);
    }

    private static void applyKeyCellMargins(LinearLayout.LayoutParams lp, Context context) {
        int mh = context.getResources().getDimensionPixelSize(R.dimen.basic_keyboard_key_margin_h);
        int mv = context.getResources().getDimensionPixelSize(R.dimen.basic_keyboard_key_margin_v);
        lp.setMargins(mh, mv, mh, mv);
    }

    private View inflateKey(LinearLayout row, String label, @Nullable String hint, float weight) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.basic_key_button, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        applyKeyCellMargins(lp, getContext());
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

    /**
     * Arrow cluster keys use Material Symbols–style {@code keyboard_arrow_*_24} vectors (centered icon).
     */
    private View inflateArrowKey(LinearLayout row, @DrawableRes int iconRes, @StringRes int cdRes, float weight) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.basic_key_arrow_button, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        applyKeyCellMargins(lp, getContext());
        v.setLayoutParams(lp);
        ImageView icon = v.findViewById(R.id.basic_key_arrow_icon);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.text_primary)));
        icon.setContentDescription(getContext().getString(cdRes));
        row.addView(v);
        return v;
    }

    private void wireTap(View v, Runnable onTap, Supplier<String> previewText) {
        v.setOnTouchListener(
                (view, event) ->
                        BasicKeyFeedback.handleStandardKeyTouch(
                                view, event, onTap, keyPreview, previewText));
    }

    /** Character / function keys: first tap on key-down, then auto-repeat while held. */
    private void wireRepeatableTap(View v, Runnable onAction, Supplier<String> previewText) {
        v.setOnTouchListener(
                BasicKeyFeedback.repeatableKeyTouchListener(onAction, keyPreview, previewText));
    }

    /** Row container; {@code heightWeight} is the vertical share (Basic full keyboard uses {@code 1:1:2:2:2:2}). */
    private LinearLayout newRow(float heightWeight) {
        LinearLayout row = new LinearLayout(getContext());
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, heightWeight));
        row.setOrientation(HORIZONTAL);
        addView(row);
        return row;
    }

    private void addRowEscF() {
        LinearLayout row = newRow(1f);
        int[] codes = new int[] {
                0x29, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45
        };
        String[] labels = new String[] {
                "Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
        };
        int maxShiftPx =
                getResources().getDimensionPixelSize(R.dimen.basic_key_preview_f_row_max_shift);
        for (int i = 0; i < labels.length; i++) {
            View k = inflateKey(row, labels[i], null, 1f);
            int code = codes[i];
            final String lab = labels[i];
            // F1..F12: shift preview away from finger (right at F1 → left at F12); Esc stays centered.
            if (i >= 1) {
                float t = (i - 1) / 11f;
                int offsetXp = (int) (maxShiftPx * (1f - 2f * t));
                k.setTag(R.id.basic_key_preview_offset_x, offsetXp);
            }
            wireRepeatableTap(k, () -> tapKey(code, false, false), () -> lab);
        }
    }

    private void addRowNumbers() {
        LinearLayout row = newRow(1f);
        String[] labels = {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "\u232B"};
        String[] hints = {"~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", null};
        int[] codes = {0x35, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2A};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            float w = i == labels.length - 1 ? 1.4f : 1f;
            View k = inflateKey(row, labels[idx], hints[idx], w);
            wireRepeatableTap(
                    k,
                    () -> tapKey(codes[idx], false, hints[idx] != null && shiftLayerActive()),
                    () -> previewShiftLayer(labels[idx], hints[idx]));
        }
    }

    private void addRowQwerty1() {
        LinearLayout row = newRow(2f);
        String[] labels = {"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"};
        String[] hints = {null, null, null, null, null, null, null, null, null, null, null, "{", "}", "|"};
        int[] codes = {0x2B, 0x14, 0x1A, 0x08, 0x15, 0x17, 0x1C, 0x18, 0x0C, 0x12, 0x13, 0x2F, 0x30, 0x31};
        float[] w = {1.3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            boolean letter = labels[idx].length() == 1 && Character.isLetter(labels[idx].charAt(0));
            wireRepeatableTap(
                    k,
                    () -> tapKey(codes[idx], letter, hints[idx] != null && shiftLayerActive()),
                    () -> previewQwertyRowKey(labels[idx], hints[idx]));
        }
    }

    private void addRowQwerty2() {
        LinearLayout row = newRow(2f);
        String[] labels = {"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"};
        String[] hints = {null, null, null, null, null, null, null, null, null, null, ":", "\"", null};
        int[] codes = {0x39, 0x04, 0x16, 0x07, 0x09, 0x0A, 0x0B, 0x0D, 0x0E, 0x0F, 0x33, 0x34, 0x28};
        float[] w = {1.4f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.6f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            if (i == 0) {
                capsKeyView = k;
                if (isMomentaryChordMode()) {
                    wireTap(
                            k,
                            () -> {
                                tapKey(0x39, false, false);
                                capsLock = !capsLock;
                            },
                            () -> "Caps");
                } else {
                    styleStickyModifierKeySurface(k);
                    wireTap(
                            k,
                            () -> {
                                tapModifierToggle("caps");
                                refreshModifierVisuals();
                            },
                            () -> "Caps");
                }
            } else if (i == labels.length - 1) {
                wireRepeatableTap(k, () -> tapKey(codes[idx], false, false), () -> "Enter");
            } else {
                wireRepeatableTap(
                        k,
                        () -> tapKey(codes[idx], true, hints[idx] != null && shiftLayerActive()),
                        () -> previewQwertyRowKey(labels[idx], hints[idx]));
            }
        }
    }

    private void addRowQwerty3() {
        LinearLayout row = newRow(2f);
        String[] labels = {"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "Shift"};
        String[] hints = {null, null, null, null, null, null, null, null, "<", ">", "?", null};
        int[] codes = {0xE1, 0x1D, 0x1B, 0x06, 0x19, 0x05, 0x11, 0x10, 0x36, 0x37, 0x38, 0xE5};
        float[] w = {1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.5f};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            View k = inflateKey(row, labels[idx], hints[idx], w[idx]);
            if (i == 0 || i == labels.length - 1) {
                wireShiftKey(k, i == 0, () -> "Shift");
            } else {
                wireRepeatableTap(
                        k,
                        () -> tapKey(codes[idx], true, hints[idx] != null && shiftLayerActive()),
                        () -> previewQwertyRowKey(labels[idx], hints[idx]));
            }
        }
    }

    private void addRowBottom() {
        LinearLayout row = newRow(2f);
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
     * spacer / up / spacer on the top row; left / down / right on the bottom row. Icons use
     * Material Symbols–style {@code keyboard_arrow_*_24} drawables.
     */
    private View createArrowCluster(float horizontalWeight) {
        Context c = getContext();
        LinearLayout cluster = new LinearLayout(c);
        cluster.setOrientation(VERTICAL);
        cluster.setBaselineAligned(false);
        LinearLayout.LayoutParams clusterLp =
                new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, horizontalWeight);
        applyKeyCellMargins(clusterLp, c);
        cluster.setLayoutParams(clusterLp);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setBaselineAligned(false);
        top.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addArrowRowSpacer(top, 1f);
        View up = inflateArrowKey(top, R.drawable.keyboard_arrow_up_24, R.string.Up_arrow, 1f);
        wireRepeatableTap(up, () -> tapKey(0x52, false, false), () -> "\u2191");
        addArrowRowSpacer(top, 1f);

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(HORIZONTAL);
        bottom.setBaselineAligned(false);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        View left = inflateArrowKey(bottom, R.drawable.keyboard_arrow_left_24, R.string.Left_arrow, 1f);
        wireRepeatableTap(left, () -> tapKey(0x50, false, false), () -> "\u2190");
        View down = inflateArrowKey(bottom, R.drawable.keyboard_arrow_down_24, R.string.Down_arrow, 1f);
        wireRepeatableTap(down, () -> tapKey(0x51, false, false), () -> "\u2193");
        View right = inflateArrowKey(bottom, R.drawable.keyboard_arrow_right_24, R.string.Right_arrow, 1f);
        wireRepeatableTap(right, () -> tapKey(0x4F, false, false), () -> "\u2192");

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
        wireBottomModifier(k0, "ctrl", HID_EXT_LCTRL, parseMod("Ctrl"), () -> labels[0]);
        View k1 = inflateKey(row, labels[1], null, w[1]);
        wireBottomModifier(k1, "alt", HID_EXT_LALT, parseMod("Alt"), () -> labels[1]);
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireBottomModifier(k2, "win", HID_EXT_LGUI, parseMod("Win"), () -> labels[2]);
        View k3 = inflateKey(row, labels[3], null, w[3]);
        wireRepeatableTap(k3, () -> tapKey(0x2C, false, false), () -> labels[3]);
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireBottomModifier(k4, "win", HID_EXT_RGUI, parseMod("Win"), () -> labels[4]);
        View k5 = inflateKey(row, labels[5], null, w[5]);
        wireBottomModifier(k5, "alt", HID_EXT_RALT, parseMod("Alt"), () -> labels[5]);
        row.addView(createArrowCluster(4f));
    }

    private void addWinBottomRow(LinearLayout row) {
        String[] labels = {"Ctrl", "Win", "Alt", "Space", "Alt", "App", "Ctrl"};
        float[] w = {1f, 1f, 1f, 3f, 1f, 1f, 1f};
        View k0 = inflateKey(row, labels[0], null, w[0]);
        wireBottomModifier(k0, "ctrl", HID_EXT_LCTRL, parseMod("Ctrl"), () -> labels[0]);
        View k1 = inflateKey(row, labels[1], null, w[1]);
        wireBottomModifier(k1, "win", HID_EXT_LGUI, parseMod("Win"), () -> labels[1]);
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireBottomModifier(k2, "alt", HID_EXT_LALT, parseMod("Alt"), () -> labels[2]);
        View k3 = inflateKey(row, labels[3], null, w[3]);
        wireRepeatableTap(k3, () -> tapKey(0x2C, false, false), () -> labels[3]);
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireBottomModifier(k4, "alt", HID_EXT_RALT, parseMod("Alt"), () -> labels[4]);
        View k5 = inflateKey(row, labels[5], null, w[5]);
        wireRepeatableTap(k5, () -> tapKey(0x65, false, false), () -> labels[5]);
        View k6 = inflateKey(row, labels[6], null, w[6]);
        wireBottomModifier(k6, "ctrl", HID_EXT_RCTRL, parseMod("Ctrl"), () -> labels[6]);
        row.addView(createArrowCluster(4f));
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
    }
}
