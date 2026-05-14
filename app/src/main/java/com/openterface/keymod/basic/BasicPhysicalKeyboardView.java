package com.openterface.keymod.basic;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.hid.HostKeyboardLockLeds;
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

    /**
     * Sticky mode: latched modifier bits (USB HID boot keyboard modifier byte). Left: Ctrl 0x01,
     * Shift 0x02, Alt 0x04, Win 0x08; right: CtrlR 0x10, ShiftR 0x20, AltR 0x40, WinR 0x80. Momentary
     * taps use usages 0xE0–0xE7 in the key slot via {@link #tapModifierMomentary(int)}.
     */
    private int stickyModMask;
    private boolean capsLock;

    @Nullable
    private View shiftKeyLeft;
    @Nullable
    private View shiftKeyRight;
    @Nullable
    private View capsKeyView;

    private final HostKeyboardLockLeds.Listener hostKeyboardLockListener =
            (numLock, hostCaps, scrollLock) -> {
                refreshModifierVisuals();
            };

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

    /**
     * Per–modifier-key view: boot modifier byte bits for each finger that completed chord long-press
     * and has not released (multi-touch chord). OR of all values is the effective chord mask.
     */
    private final ArrayMap<View, Integer> chordSustainBootByView = new ArrayMap<>();
    private View chordLongPressAnchor;
    private int chordLongPressPendingMask;
    /** Extended HID code for the modifier key being chord-held (for fallback send). */
    private int chordActiveExtKey;
    /** True after we sent a sustained modifier-down to the host (needs release on UP / clear). */
    private boolean chordHostHoldSent;
    private final Runnable chordLongPressRunnable = this::onChordLongPressThreshold;
    private final Runnable physicalKeyReleaseRunnable = this::runPhysicalKeyRelease;
    private final Runnable macCapsDelayedReleaseRunnable =
            () -> {
                runPhysicalKeyRelease();
                refreshModifierVisuals();
            };

    /**
     * macOS distinguishes Caps tap (input-source / 中英 toggle) vs long-press (Caps Lock) by hold time.
     * Hold slightly above the host threshold so long-press reliably engages Caps Lock.
     */
    private static final long MAC_CAPS_LONG_PRESS_MS = 500L;
    private static final long MAC_CAPS_MIN_TAP_HOLD_MS = 30L;
    @Nullable private KmBasicHoldLockController kmBasicHoldLockController;
    @Nullable private BasicHoldLockPopup activeHoldLockPopup;
    private final Runnable holdLockPopupRunnable = this::onHoldLockPopupTimeout;
    @Nullable private View holdLockPopupAnchorView;
    /**
     * True from modifier ACTION_DOWN until UP/CANCEL/dismiss for hold-lock. Do not rely on {@link
     * View#isPressed()} at the 1s timeout — many key surfaces clear pressed during chord sustain.
     */
    private boolean holdLockFingerDown;
    /**
     * Latest raw finger position while waiting for / using the hold-lock popup. The popup uses this
     * as gesture origin (not ACTION_DOWN) so a ~1s press does not consume the vertical budget and
     * “swipe up toward the lock” is measured from where the finger was when the lock appeared.
     */
    private float holdLockGestureRawX;
    private float holdLockGestureRawY;
    private int holdLockPendingModMask;
    private final KmBasicHoldLockController.Listener kmBasicHoldLockListener =
            controller -> {
                if (isAttachedToWindow()) {
                    refreshModifierVisuals();
                }
            };

    private final SharedPreferences.OnSharedPreferenceChangeListener kmBasicPrefListener =
            (sharedPreferences, key) -> {
                if (KmBasicKeyboardPrefs.PREF_LONG_PRESS_BEHAVIOR.equals(key)) {
                    MainActivity ma = mainActivity;
                    if (ma != null && isAttachedToWindow()) {
                        bind(ma, port);
                    }
                    return;
                }
                if (!KmBasicKeyboardPrefs.PREF_KEY.equals(key)) {
                    return;
                }
                stickyModMask = 0;
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
            HostKeyboardLockLeds.get().addListener(hostKeyboardLockListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            HostKeyboardLockLeds.get().removeListener(hostKeyboardLockListener);
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .unregisterOnSharedPreferenceChangeListener(kmBasicPrefListener);
        }
        super.onDetachedFromWindow();
    }

    public void bind(@Nullable MainActivity activity, @Nullable UsbSerialPort usbPort) {
        keyPreview.dismiss();
        handler.removeCallbacks(chordLongPressRunnable);
        handler.removeCallbacks(holdLockPopupRunnable);
        dismissHoldLockPopup();
        handler.removeCallbacks(physicalKeyReleaseRunnable);
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
        handler.removeCallbacks(holdLockPopupRunnable);
        dismissHoldLockPopup();
        if (chordHostHoldSent || !chordSustainBootByView.isEmpty()) {
            MainActivity ma = mainActivity;
            if (ma != null) {
                KeyboardHidTransport.sendAllKeysReleased(
                        port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
                reassertLockedKeyboardAfterAllKeysReleased();
            }
        }
        chordHostHoldSent = false;
        chordSustainBootByView.clear();
        chordLongPressAnchor = null;
    }

    private void onChordLongPressThreshold() {
        if (chordLongPressAnchor == null) {
            return;
        }
        View anchor = chordLongPressAnchor;
        int contrib = bootMaskForChordFinger(chordLongPressPendingMask, chordActiveExtKey);
        if (contrib == 0) {
            return;
        }
        chordSustainBootByView.put(anchor, contrib);
        if (KmBasicKeyboardPrefs.isChordSustainHidEnabled(getContext())) {
            MainActivity ma = mainActivity;
            if (ma != null) {
                int agg = chordSustainAggregateBootMaskOr0();
                if (agg != 0) {
                    KeyboardHidTransport.sendKeyReport(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            mergedChordSustainModifierBootMask(),
                            0);
                } else {
                    KeyboardHidTransport.sendKeyReport(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound(),
                            lockedModsOr0(),
                            chordActiveExtKey);
                }
                chordHostHoldSent = true;
            } else {
                chordHostHoldSent = false;
            }
        } else {
            chordHostHoldSent = false;
        }
        keyPreview.dismiss();
        refreshModifierVisuals();
    }

    private int shiftMaskBoth() {
        return parseMod("Shift") | parseMod("ShiftR");
    }

    /** True when either Shift side is active (sticky, locked, or chord-held). */
    private boolean stickyShiftLayer() {
        return ((stickyModMask | lockedModsOr0()) & shiftMaskBoth()) != 0;
    }

    /** Sticky latched shift, lock, or chord mode with Shift long-held. */
    private boolean shiftLayerActive() {
        if (isMomentaryChordMode()) {
            return ((chordSustainAggregateBootMaskOr0() | lockedModsOr0()) & shiftMaskBoth()) != 0;
        }
        return stickyShiftLayer();
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
                lockedModsOr0() | chordSustainAggregateBootMaskOr0(),
                extendedKeyCode);
        handler.postDelayed(
                () -> {
                    KeyboardHidTransport.sendAllKeysReleased(
                            port,
                            ma.getBluetoothService(),
                            ma.isBluetoothServiceBound());
                    reassertLockedKeyboardAfterAllKeysReleased();
                },
                30);
    }

    private void reassertLockedKeyboardAfterAllKeysReleased() {
        MainActivity ma = mainActivity;
        if (ma == null || kmBasicHoldLockController == null) {
            return;
        }
        kmBasicHoldLockController.reassertKeyboardModifiersIfNeeded(
                port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
    }

    private void dismissHoldLockPopup() {
        if (activeHoldLockPopup != null) {
            activeHoldLockPopup.dismiss();
            activeHoldLockPopup = null;
        }
        holdLockPopupAnchorView = null;
        holdLockFingerDown = false;
    }

    private void onHoldLockPopupTimeout() {
        if (holdLockPopupAnchorView == null || !holdLockFingerDown) {
            return;
        }
        keyPreview.dismiss();
        activeHoldLockPopup = new BasicHoldLockPopup();
        activeHoldLockPopup.show(holdLockPopupAnchorView, holdLockGestureRawX, holdLockGestureRawY);
    }

    private void clearChordUiPreserveHostForLock() {
        handler.removeCallbacks(chordLongPressRunnable);
        handler.removeCallbacks(holdLockPopupRunnable);
        dismissHoldLockPopup();
        chordSustainBootByView.clear();
        chordHostHoldSent = false;
        chordLongPressAnchor = null;
    }

    public void setHoldLockController(@Nullable KmBasicHoldLockController controller) {
        if (kmBasicHoldLockController != null) {
            kmBasicHoldLockController.removeListener(kmBasicHoldLockListener);
        }
        kmBasicHoldLockController = controller;
        if (kmBasicHoldLockController != null) {
            kmBasicHoldLockController.addListener(kmBasicHoldLockListener);
        }
    }

    private View.OnTouchListener createChordModifierTouchListener(
            final int extendedKeyCode, final int holdModMask, final Supplier<String> previewText) {
        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handler.removeCallbacks(chordLongPressRunnable);
                    handler.removeCallbacks(holdLockPopupRunnable);
                    dismissHoldLockPopup();
                    v.setPressed(true);
                    BasicKeyFeedback.performKeyHaptic(v);
                    chordActiveExtKey = extendedKeyCode;
                    chordLongPressAnchor = v;
                    chordLongPressPendingMask = holdModMask;
                    holdLockPopupAnchorView = v;
                    holdLockFingerDown = true;
                    holdLockPendingModMask = holdModMask;
                    holdLockGestureRawX = event.getRawX();
                    holdLockGestureRawY = event.getRawY();
                    handler.postDelayed(
                            chordLongPressRunnable,
                            ViewConfiguration.get(v.getContext()).getLongPressTimeout());
                    handler.postDelayed(holdLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    if (previewText != null) {
                        keyPreview.show(v, previewText.get());
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    boolean inside = BasicKeyFeedback.isPointerInsideView(v, event);
                    if (activeHoldLockPopup != null) {
                        v.setPressed(true);
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        activeHoldLockPopup.updatePointer(
                                event.getRawX(), event.getRawY());
                        return true;
                    }
                    if (inside) {
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                    }
                    v.setPressed(inside);
                    if (!inside) {
                        handler.removeCallbacks(chordLongPressRunnable);
                        handler.removeCallbacks(holdLockPopupRunnable);
                        dismissHoldLockPopup();
                        keyPreview.dismiss();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(chordLongPressRunnable);
                    handler.removeCallbacks(holdLockPopupRunnable);
                    holdLockFingerDown = false;
                    v.setPressed(false);
                    keyPreview.dismiss();
                    boolean committedLock = false;
                    if (activeHoldLockPopup != null) {
                        activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                        committedLock = activeHoldLockPopup.commitIfLockSelected();
                        dismissHoldLockPopup();
                    }
                    if (committedLock && kmBasicHoldLockController != null) {
                        MainActivity maLock = mainActivity;
                        if (maLock != null) {
                            kmBasicHoldLockController.lockModifier(
                                    holdLockPendingModMask,
                                    port,
                                    maLock.getBluetoothService(),
                                    maLock.isBluetoothServiceBound());
                        }
                        clearChordUiPreserveHostForLock();
                        refreshModifierVisuals();
                        chordLongPressAnchor = null;
                        holdLockPopupAnchorView = null;
                        return true;
                    }
                    if (chordSustainBootByView.containsKey(v)) {
                        releaseChordSustainFingerForView(v);
                    } else if (BasicKeyFeedback.isPointerInsideView(v, event)) {
                        MainActivity maTap = mainActivity;
                        if (kmBasicHoldLockController != null
                                && maTap != null
                                && kmBasicHoldLockController.isModifierLocked(
                                        holdLockPendingModMask)) {
                            kmBasicHoldLockController.unlockModifier(
                                    holdLockPendingModMask,
                                    port,
                                    maTap.getBluetoothService(),
                                    maTap.isBluetoothServiceBound());
                        } else {
                            tapModifierMomentary(extendedKeyCode);
                        }
                    }
                    chordLongPressAnchor = null;
                    holdLockPopupAnchorView = null;
                    refreshModifierVisuals();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(chordLongPressRunnable);
                    handler.removeCallbacks(holdLockPopupRunnable);
                    dismissHoldLockPopup();
                    v.setPressed(false);
                    keyPreview.dismiss();
                    if (chordSustainBootByView.containsKey(v)) {
                        releaseChordSustainFingerForView(v);
                    }
                    chordLongPressAnchor = null;
                    holdLockPopupAnchorView = null;
                    refreshModifierVisuals();
                    return true;
                default:
                    return false;
            }
        };
    }

    private void wireChordModifierTouch(View v, int extendedKeyCode, int holdModMask, Supplier<String> preview) {
        applyHoldLockModifierKeySurface(v);
        v.setOnTouchListener(createChordModifierTouchListener(extendedKeyCode, holdModMask, preview));
    }

    private void wireBottomModifier(
            View v, String which, int extKey, int holdMask, int stickyMaskBit, Supplier<String> preview) {
        if (isMomentaryChordMode()) {
            // Lists are also used in chord mode for locked / chord-held visuals (see
            // applyModifierVisualsChordMode); sticky registration alone would leave them empty here.
            registerModifierKeyInSideLists(which, v);
            wireChordModifierTouch(v, extKey, holdMask, preview);
        } else {
            wireStickyModifierTap(v, which, stickyMaskBit, preview);
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
            int hold = isLeft ? parseMod("Shift") : parseMod("ShiftR");
            wireChordModifierTouch(v, ext, hold, preview);
        } else {
            styleStickyModifierKeySurface(v);
            int bit = isLeft ? parseMod("Shift") : parseMod("ShiftR");
            v.setOnTouchListener(createStickyModifierWithLockListener(bit, preview));
        }
    }

    private void refreshModifierVisuals() {
        if (isMomentaryChordMode()) {
            applyModifierVisualsChordMode();
        } else {
            refreshStickyModifierVisuals();
        }
    }

    /** Chord-held key, locked modifiers, and caps highlight. */
    private void applyModifierVisualsChordMode() {
        int locked = lockedModsOr0();
        if (shiftKeyLeft != null) {
            shiftKeyLeft.setSelected(
                    chordSustainBootByView.containsKey(shiftKeyLeft)
                            || (locked & parseMod("Shift")) != 0);
        }
        if (shiftKeyRight != null) {
            shiftKeyRight.setSelected(
                    chordSustainBootByView.containsKey(shiftKeyRight)
                            || (locked & parseMod("ShiftR")) != 0);
        }
        applyChordOrLockVisualSide(ctrlModifierKeys, parseMod("Ctrl"), parseMod("CtrlR"), locked);
        applyChordOrLockVisualSide(altModifierKeys, parseMod("Alt"), parseMod("AltR"), locked);
        applyChordOrLockVisualSide(winModifierKeys, parseMod("Win"), parseMod("WinR"), locked);
        if (capsKeyView != null) {
            capsKeyView.setSelected(effectiveCapsLockForUi());
        }
    }

    private void applyChordOrLockVisualSide(
            List<View> keys, int leftBit, int rightBit, int lockedMask) {
        if (keys.size() > 0) {
            View v = keys.get(0);
            v.setSelected(chordSustainBootByView.containsKey(v) || (lockedMask & leftBit) != 0);
        }
        if (keys.size() > 1) {
            View v = keys.get(1);
            v.setSelected(chordSustainBootByView.containsKey(v) || (lockedMask & rightBit) != 0);
        }
    }

    private int parseMod(String name) {
        return Integer.parseInt(CH9329MSKBMap.KBShortCutKey().get(name), 16);
    }

    private int stickyModifiersMask() {
        return stickyModMask;
    }

    private int lockedModsOr0() {
        return kmBasicHoldLockController != null
                ? kmBasicHoldLockController.getLockedModMask()
                : 0;
    }

    private int chordSustainAggregateBootMaskOr0() {
        int agg = 0;
        for (int i = 0; i < chordSustainBootByView.size(); i++) {
            agg |= chordSustainBootByView.valueAt(i);
        }
        return agg;
    }

    private int bootModifierMaskFromExtendedKey(int extKey) {
        switch (extKey) {
            case HID_EXT_LCTRL:
                return parseMod("Ctrl");
            case HID_EXT_LSHIFT:
                return parseMod("Shift");
            case HID_EXT_LALT:
                return parseMod("Alt");
            case HID_EXT_LGUI:
                return parseMod("Win");
            case HID_EXT_RCTRL:
                return parseMod("CtrlR");
            case HID_EXT_RSHIFT:
                return parseMod("ShiftR");
            case HID_EXT_RALT:
                return parseMod("AltR");
            case HID_EXT_RGUI:
                return parseMod("WinR");
            default:
                return 0;
        }
    }

    private int bootMaskForChordFinger(int holdModMask, int extKey) {
        return holdModMask != 0 ? holdModMask : bootModifierMaskFromExtendedKey(extKey);
    }

    /**
     * One finger released from chord sustain: refresh host modifiers for any remaining chord fingers
     * or locks only.
     */
    private void releaseChordSustainFingerForView(View v) {
        if (!chordSustainBootByView.containsKey(v)) {
            return;
        }
        chordSustainBootByView.remove(v);
        if (!chordHostHoldSent) {
            refreshModifierVisuals();
            return;
        }
        MainActivity ma = mainActivity;
        if (ma == null) {
            chordHostHoldSent = false;
            refreshModifierVisuals();
            return;
        }
        KeyboardHidTransport.sendAllKeysReleased(
                port, ma.getBluetoothService(), ma.isBluetoothServiceBound());
        if (!chordSustainBootByView.isEmpty()) {
            int agg = chordSustainAggregateBootMaskOr0();
            if (agg != 0) {
                KeyboardHidTransport.sendKeyReport(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound(),
                        mergedChordSustainModifierBootMask(),
                        0);
            } else {
                KeyboardHidTransport.sendKeyReport(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound(),
                        lockedModsOr0(),
                        chordActiveExtKey);
            }
            chordHostHoldSent = true;
        } else {
            reassertLockedKeyboardAfterAllKeysReleased();
            chordHostHoldSent = false;
        }
        refreshModifierVisuals();
    }

    /**
     * Boot keyboard modifier byte for chord sustain / reassert HID sends: combine swipe-up locks with
     * the OR of all chord-held fingers so a second send does not drop locked bits or other chord
     * fingers.
     */
    private int mergedChordSustainModifierBootMask() {
        return lockedModsOr0() | chordSustainAggregateBootMaskOr0();
    }

    private int effectiveModifiersMask() {
        int base = isMomentaryChordMode() ? chordSustainAggregateBootMaskOr0() : stickyModifiersMask();
        return base | lockedModsOr0();
    }

    private int effectiveModifiersForPhysicalKey(boolean isLetter, boolean needsShiftForSymbol) {
        int mods = effectiveModifiersMask();
        boolean shiftForCase =
                !isMomentaryChordMode() && isLetter && effectiveCapsLockForUi() != stickyShiftLayer();
        if (needsShiftForSymbol) {
            mods |= parseMod("Shift");
        } else if (shiftForCase) {
            mods |= parseMod("Shift");
        }
        return mods;
    }

    private void tapKey(int hidCode, boolean isLetter, boolean needsShiftForSymbol) {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        int mods = effectiveModifiersForPhysicalKey(isLetter, needsShiftForSymbol);
        KeyboardHidTransport.sendKeyReport(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound(),
                mods,
                hidCode);
        scheduleReleaseAfterPhysicalKey();
    }

    /** HID key-down only (sustained-hold mode); pair with {@link #scheduleReleaseAfterPhysicalKey}. */
    private void sendPhysicalKeyDown(int hidCode, boolean isLetter, boolean needsShiftForSymbol) {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        int mods = effectiveModifiersForPhysicalKey(isLetter, needsShiftForSymbol);
        KeyboardHidTransport.sendKeyReport(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound(),
                mods,
                hidCode);
    }

    private void scheduleReleaseAfterPhysicalKey() {
        handler.removeCallbacks(physicalKeyReleaseRunnable);
        handler.postDelayed(physicalKeyReleaseRunnable, 30);
    }

    private void runPhysicalKeyRelease() {
        MainActivity ma = mainActivity;
        if (ma == null) {
            return;
        }
        KeyboardHidTransport.sendAllKeysReleased(
                port,
                ma.getBluetoothService(),
                ma.isBluetoothServiceBound());
        reassertLockedKeyboardAfterAllKeysReleased();
        if (KmBasicKeyboardPrefs.isChordSustainHidEnabled(getContext())
                && !chordSustainBootByView.isEmpty()) {
            int agg = chordSustainAggregateBootMaskOr0();
            if (agg != 0) {
                KeyboardHidTransport.sendKeyReport(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound(),
                        mergedChordSustainModifierBootMask(),
                        0);
            } else if (chordActiveExtKey != 0) {
                KeyboardHidTransport.sendKeyReport(
                        port,
                        ma.getBluetoothService(),
                        ma.isBluetoothServiceBound(),
                        lockedModsOr0(),
                        chordActiveExtKey);
            }
            chordHostHoldSent = true;
        }
    }

    private String previewLetter(String letter) {
        if (letter.length() != 1) {
            return letter;
        }
        char c = letter.charAt(0);
        if (!Character.isLetter(c)) {
            return letter;
        }
        boolean upper = effectiveCapsLockForUi() != shiftLayerActive();
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

    private void toggleStickyModBit(int bit) {
        stickyModMask ^= bit;
    }

    private void tapModifierToggle(String which) {
        if ("caps".equals(which)) {
            if (!HostKeyboardLockLeds.get().hasReceivedLedFromHost()) {
                capsLock = !capsLock;
            }
        }
    }

    private boolean effectiveCapsLockForUi() {
        if (HostKeyboardLockLeds.get().hasReceivedLedFromHost()) {
            return HostKeyboardLockLeds.get().isCapsLock();
        }
        return capsLock;
    }

    private void refreshStickyModifierVisuals() {
        int locked = lockedModsOr0();
        if (shiftKeyLeft != null) {
            shiftKeyLeft.setSelected((stickyModMask & parseMod("Shift")) != 0
                    || (locked & parseMod("Shift")) != 0);
        }
        if (shiftKeyRight != null) {
            shiftKeyRight.setSelected((stickyModMask & parseMod("ShiftR")) != 0
                    || (locked & parseMod("ShiftR")) != 0);
        }
        refreshStickySideKeys(ctrlModifierKeys, parseMod("Ctrl"), parseMod("CtrlR"));
        refreshStickySideKeys(altModifierKeys, parseMod("Alt"), parseMod("AltR"));
        refreshStickySideKeys(winModifierKeys, parseMod("Win"), parseMod("WinR"));
        if (capsKeyView != null) {
            capsKeyView.setSelected(effectiveCapsLockForUi());
        }
    }

    private void refreshStickySideKeys(List<View> keys, int leftBit, int rightBit) {
        int locked = lockedModsOr0();
        if (keys.size() > 0) {
            keys.get(0).setSelected((stickyModMask & leftBit) != 0 || (locked & leftBit) != 0);
        }
        if (keys.size() > 1) {
            keys.get(1).setSelected((stickyModMask & rightBit) != 0 || (locked & rightBit) != 0);
        }
    }

    private static void setSelectedOnModifierKeys(List<View> keys, boolean selected) {
        for (int i = 0, n = keys.size(); i < n; i++) {
            keys.get(i).setSelected(selected);
        }
    }

    /**
     * Background for every key that participates in KM Basic swipe-up lock (Shift/Ctrl/Alt/Win
     * and Caps in sticky mode): {@code state_selected} shows primary stroke + container fill.
     */
    private static void applyHoldLockModifierKeySurface(View v) {
        v.setBackgroundResource(R.drawable.basic_shift_key_background);
    }

    /** Same drawable/states as chord modifiers: latched / locked uses {@code state_selected}. */
    private void styleStickyModifierKeySurface(View v) {
        applyHoldLockModifierKeySurface(v);
    }

    /** Adds a bottom-row modifier view to the side lists used by {@link #refreshModifierVisuals()}. */
    private void registerModifierKeyInSideLists(String which, View v) {
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

    private void registerStickyModifierKey(String which, View v) {
        styleStickyModifierKeySurface(v);
        registerModifierKeyInSideLists(which, v);
    }

    private void wireStickyModifierTap(View v, String which, int stickyMaskBit, Supplier<String> previewText) {
        registerStickyModifierKey(which, v);
        v.setOnTouchListener(createStickyModifierWithLockListener(stickyMaskBit, previewText));
    }

    private View.OnTouchListener createStickyModifierWithLockListener(
            final int stickyMaskBit, final Supplier<String> previewText) {
        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handler.removeCallbacks(holdLockPopupRunnable);
                    dismissHoldLockPopup();
                    v.setPressed(true);
                    BasicKeyFeedback.performKeyHaptic(v);
                    holdLockPopupAnchorView = v;
                    holdLockFingerDown = true;
                    holdLockPendingModMask = stickyMaskBit;
                    holdLockGestureRawX = event.getRawX();
                    holdLockGestureRawY = event.getRawY();
                    handler.postDelayed(holdLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    if (previewText != null) {
                        keyPreview.show(v, previewText.get());
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    boolean insideSticky = BasicKeyFeedback.isPointerInsideView(v, event);
                    if (activeHoldLockPopup != null) {
                        v.setPressed(true);
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        activeHoldLockPopup.updatePointer(
                                event.getRawX(), event.getRawY());
                        return true;
                    }
                    if (insideSticky) {
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                    }
                    v.setPressed(insideSticky);
                    if (!insideSticky) {
                        handler.removeCallbacks(holdLockPopupRunnable);
                        dismissHoldLockPopup();
                        keyPreview.dismiss();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(holdLockPopupRunnable);
                    holdLockFingerDown = false;
                    v.setPressed(false);
                    keyPreview.dismiss();
                    boolean committedStickyLock = false;
                    if (activeHoldLockPopup != null) {
                        activeHoldLockPopup.updatePointer(
                                event.getRawX(), event.getRawY());
                        committedStickyLock = activeHoldLockPopup.commitIfLockSelected();
                        dismissHoldLockPopup();
                    }
                    MainActivity maSticky = mainActivity;
                    if (committedStickyLock
                            && kmBasicHoldLockController != null
                            && maSticky != null) {
                        stickyModMask &= ~stickyMaskBit;
                        kmBasicHoldLockController.lockModifier(
                                holdLockPendingModMask,
                                port,
                                maSticky.getBluetoothService(),
                                maSticky.isBluetoothServiceBound());
                        refreshModifierVisuals();
                        holdLockPopupAnchorView = null;
                        return true;
                    }
                    if (BasicKeyFeedback.isPointerInsideView(v, event) && maSticky != null) {
                        if (kmBasicHoldLockController != null
                                && kmBasicHoldLockController.isModifierLocked(stickyMaskBit)) {
                            kmBasicHoldLockController.unlockModifier(
                                    stickyMaskBit,
                                    port,
                                    maSticky.getBluetoothService(),
                                    maSticky.isBluetoothServiceBound());
                        } else {
                            toggleStickyModBit(stickyMaskBit);
                        }
                        refreshModifierVisuals();
                    }
                    holdLockPopupAnchorView = null;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdLockPopupRunnable);
                    dismissHoldLockPopup();
                    v.setPressed(false);
                    keyPreview.dismiss();
                    holdLockPopupAnchorView = null;
                    return true;
                default:
                    return false;
            }
        };
    }

    private static void applyKeyCellMargins(LinearLayout.LayoutParams lp, Context context) {
        int mh = context.getResources().getDimensionPixelSize(R.dimen.basic_keyboard_key_margin_h);
        int mv = context.getResources().getDimensionPixelSize(R.dimen.basic_keyboard_key_margin_v);
        lp.setMargins(mh, mv, mh, mv);
    }

    private View inflateKey(LinearLayout row, String label, @Nullable String hint, float weight) {
        return inflateKey(row, label, hint, weight, 0, 0);
    }

    /**
     * @param iconContentDescRes used when {@code iconRes != 0} for accessibility; ignored when no
     *     icon.
     */
    private View inflateKey(
            LinearLayout row,
            String label,
            @Nullable String hint,
            float weight,
            @DrawableRes int iconRes,
            @StringRes int iconContentDescRes) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.basic_key_button, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        applyKeyCellMargins(lp, getContext());
        v.setLayoutParams(lp);
        TextView lab = v.findViewById(R.id.basic_key_label);
        TextView h = v.findViewById(R.id.basic_key_hint);
        ImageView icon = v.findViewById(R.id.basic_key_icon);
        if (iconRes != 0) {
            lab.setVisibility(GONE);
            h.setVisibility(GONE);
            icon.setVisibility(VISIBLE);
            icon.setImageResource(iconRes);
            ColorStateList iconTint =
                    AppCompatResources.getColorStateList(getContext(), R.color.basic_key_icon_tint);
            icon.setImageTintList(
                    iconTint != null
                            ? iconTint
                            : ColorStateList.valueOf(
                                    ContextCompat.getColor(getContext(), R.color.text_primary)));
            String cd = getContext().getString(iconContentDescRes);
            icon.setContentDescription(cd);
            v.setContentDescription(cd);
        } else {
            icon.setVisibility(GONE);
            lab.setVisibility(VISIBLE);
            lab.setText(label);
            if (hint != null && !hint.isEmpty()) {
                h.setText(hint);
                h.setVisibility(VISIBLE);
            } else {
                h.setVisibility(GONE);
            }
            v.setContentDescription(null);
        }
        row.addView(v);
        return v;
    }

    /**
     * Space bar uses the wide Openterface wordmark; {@link #inflateKey} defaults to arrow-icon size, so
     * expand the icon to fit the key cell with horizontal insets and height from
     * {@code km_openterface_wordmark_keyboard_*}.
     */
    private void applySpaceBarBrandIconLayout(View keyRoot) {
        ImageView icon = keyRoot.findViewById(R.id.basic_key_icon);
        if (icon == null || icon.getVisibility() != VISIBLE) {
            return;
        }
        int horizontalPad =
                getResources()
                        .getDimensionPixelSize(
                                R.dimen.km_openterface_wordmark_keyboard_padding_horizontal);
        int logoHeight =
                getResources().getDimensionPixelSize(R.dimen.km_openterface_wordmark_keyboard_height);
        icon.setPadding(horizontalPad, 0, horizontalPad, 0);
        icon.setImageAlpha(210);
        FrameLayout.LayoutParams flp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, logoHeight);
        flp.gravity = Gravity.CENTER;
        icon.setLayoutParams(flp);
        icon.setAdjustViewBounds(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
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

    private boolean isMacTarget() {
        return mainActivity != null && "macos".equals(mainActivity.getTargetOs());
    }

    /**
     * KM Basic Caps on macOS: short hold (~30 ms) for input-source / 中英 toggle; long-press for Caps Lock.
     * Other target OS: same as a normal tap (toggle Caps in one shot).
     */
    private void wireMacAwareCapsKey(View v, Supplier<String> previewText) {
        final boolean[] longFired = {false};
        final Runnable[] pendingLong = {null};
        final long[] downAt = {0L};
        final boolean[] keyDownActive = {false};

        v.setOnTouchListener(
                (view, event) -> {
                    if (!isMacTarget()) {
                        return BasicKeyFeedback.handleStandardKeyTouch(
                                view,
                                event,
                                () -> {
                                    tapKey(0x39, false, false);
                                    if (!HostKeyboardLockLeds.get().hasReceivedLedFromHost()) {
                                        capsLock = !capsLock;
                                    }
                                    refreshModifierVisuals();
                                },
                                keyPreview,
                                previewText);
                    }
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            longFired[0] = false;
                            downAt[0] = SystemClock.uptimeMillis();
                            keyDownActive[0] = true;
                            handler.removeCallbacks(macCapsDelayedReleaseRunnable);
                            handler.removeCallbacks(physicalKeyReleaseRunnable);
                            if (pendingLong[0] != null) {
                                handler.removeCallbacks(pendingLong[0]);
                                pendingLong[0] = null;
                            }
                            pendingLong[0] =
                                    () -> {
                                        longFired[0] = true;
                                        BasicKeyFeedback.performKeyHaptic(view);
                                        pendingLong[0] = null;
                                    };
                            handler.postDelayed(pendingLong[0], MAC_CAPS_LONG_PRESS_MS);
                            view.setPressed(true);
                            BasicKeyFeedback.performKeyHaptic(view);
                            if (previewText != null) {
                                String t = previewText.get();
                                if (t != null && !t.isEmpty()) {
                                    keyPreview.show(view, t);
                                }
                            }
                            sendPhysicalKeyDown(0x39, false, false);
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            boolean inside = BasicKeyFeedback.isPointerInsideView(view, event);
                            view.setPressed(inside);
                            if (inside) {
                                if (previewText != null) {
                                    String t = previewText.get();
                                    if (t != null && !t.isEmpty()) {
                                        keyPreview.show(view, t);
                                    }
                                }
                            } else {
                                keyPreview.dismiss();
                                if (pendingLong[0] != null) {
                                    handler.removeCallbacks(pendingLong[0]);
                                    pendingLong[0] = null;
                                }
                                if (keyDownActive[0]) {
                                    scheduleMacCapsKeyRelease(longFired[0], downAt[0], false);
                                    keyDownActive[0] = false;
                                }
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                            view.setPressed(false);
                            keyPreview.dismiss();
                            if (pendingLong[0] != null) {
                                handler.removeCallbacks(pendingLong[0]);
                                pendingLong[0] = null;
                            }
                            if (keyDownActive[0]) {
                                boolean inside = BasicKeyFeedback.isPointerInsideView(view, event);
                                scheduleMacCapsKeyRelease(longFired[0], downAt[0], inside);
                                keyDownActive[0] = false;
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            view.setPressed(false);
                            keyPreview.dismiss();
                            if (pendingLong[0] != null) {
                                handler.removeCallbacks(pendingLong[0]);
                                pendingLong[0] = null;
                            }
                            if (keyDownActive[0]) {
                                scheduleMacCapsKeyRelease(longFired[0], downAt[0], false);
                                keyDownActive[0] = false;
                            }
                            return true;
                        default:
                            return false;
                    }
                });
    }

    private void scheduleMacCapsKeyRelease(boolean longFired, long downAtMs, boolean commitInside) {
        long elapsed = SystemClock.uptimeMillis() - downAtMs;
        if (!longFired) {
            long delay =
                    elapsed < MAC_CAPS_MIN_TAP_HOLD_MS
                            ? MAC_CAPS_MIN_TAP_HOLD_MS - elapsed
                            : 0;
            handler.postDelayed(macCapsDelayedReleaseRunnable, delay);
        } else {
            runPhysicalKeyRelease();
            if (commitInside && !HostKeyboardLockLeds.get().hasReceivedLedFromHost()) {
                capsLock = !capsLock;
            }
            refreshModifierVisuals();
        }
    }

    /** Character / function keys: first tap on key-down, then auto-repeat while held. */
    private void wireRepeatableTap(View v, Runnable onAction, Supplier<String> previewText) {
        v.setOnTouchListener(
                BasicKeyFeedback.repeatableKeyTouchListener(onAction, keyPreview, previewText));
    }

    /**
     * Repeat vs sustained hold per {@link KmBasicKeyboardPrefs#PREF_LONG_PRESS_BEHAVIOR}; includes auto-repeat
     * or HID hold for the same logical key.
     */
    private void wireKeyedRepeatOrHold(
            View v,
            int hidCode,
            boolean isLetter,
            boolean needsShiftForSymbol,
            Supplier<String> previewText) {
        if (KmBasicKeyboardPrefs.isLongPressSustainedHoldMode(getContext())) {
            v.setOnTouchListener(
                    BasicKeyFeedback.sustainedKeyTouchListener(
                            () -> sendPhysicalKeyDown(hidCode, isLetter, needsShiftForSymbol),
                            this::scheduleReleaseAfterPhysicalKey,
                            keyPreview,
                            previewText));
        } else {
            wireRepeatableTap(v, () -> tapKey(hidCode, isLetter, needsShiftForSymbol), previewText);
        }
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
        for (int i = 0; i < labels.length; i++) {
            View k = inflateKey(row, labels[i], null, 1f);
            int code = codes[i];
            final String lab = labels[i];
            // F1..F12: store 0..1 bias; BasicKeyPreview scales shift with key width + base dimen.
            if (i >= 1) {
                float t = (i - 1) / 11f;
                k.setTag(R.id.basic_key_preview_offset_x, t);
            }
            wireKeyedRepeatOrHold(k, code, false, false, () -> lab);
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
            wireKeyedRepeatOrHold(
                    k,
                    codes[idx],
                    false,
                    hints[idx] != null && shiftLayerActive(),
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
            wireKeyedRepeatOrHold(
                    k,
                    codes[idx],
                    letter,
                    hints[idx] != null && shiftLayerActive(),
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
                    wireMacAwareCapsKey(k, () -> "Caps");
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
                wireKeyedRepeatOrHold(k, codes[idx], false, false, () -> "Enter");
            } else {
                wireKeyedRepeatOrHold(
                        k,
                        codes[idx],
                        true,
                        hints[idx] != null && shiftLayerActive(),
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
                wireKeyedRepeatOrHold(
                        k,
                        codes[idx],
                        true,
                        hints[idx] != null && shiftLayerActive(),
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
        wireKeyedRepeatOrHold(up, 0x52, false, false, () -> "\u2191");
        addArrowRowSpacer(top, 1f);

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(HORIZONTAL);
        bottom.setBaselineAligned(false);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        View left = inflateArrowKey(bottom, R.drawable.keyboard_arrow_left_24, R.string.Left_arrow, 1f);
        wireKeyedRepeatOrHold(left, 0x50, false, false, () -> "\u2190");
        View down = inflateArrowKey(bottom, R.drawable.keyboard_arrow_down_24, R.string.Down_arrow, 1f);
        wireKeyedRepeatOrHold(down, 0x51, false, false, () -> "\u2193");
        View right = inflateArrowKey(bottom, R.drawable.keyboard_arrow_right_24, R.string.Right_arrow, 1f);
        wireKeyedRepeatOrHold(right, 0x4F, false, false, () -> "\u2192");

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
        wireBottomModifier(
                k0, "ctrl", HID_EXT_LCTRL, parseMod("Ctrl"), parseMod("Ctrl"), () -> labels[0]);
        View k1 = inflateKey(row, labels[1], null, w[1]);
        wireBottomModifier(k1, "alt", HID_EXT_LALT, parseMod("Alt"), parseMod("Alt"), () -> labels[1]);
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireBottomModifier(k2, "win", HID_EXT_LGUI, parseMod("Win"), parseMod("Win"), () -> labels[2]);
        View k3 =
                inflateKey(
                        row,
                        "",
                        null,
                        w[3],
                        R.drawable.ic_openterface_wordmark,
                        R.string.Space_Button);
        applySpaceBarBrandIconLayout(k3);
        wireKeyedRepeatOrHold(k3, 0x2C, false, false, () -> labels[3]);
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireBottomModifier(k4, "win", HID_EXT_RGUI, parseMod("WinR"), parseMod("WinR"), () -> labels[4]);
        View k5 = inflateKey(row, labels[5], null, w[5]);
        wireBottomModifier(k5, "alt", HID_EXT_RALT, parseMod("AltR"), parseMod("AltR"), () -> labels[5]);
        row.addView(createArrowCluster(4f));
    }

    private void addWinBottomRow(LinearLayout row) {
        MainActivity ma = mainActivity;
        String os = ma != null ? ma.getTargetOs().toLowerCase(Locale.US) : "windows";
        boolean isWindows = "windows".equals(os);
        boolean isLinux = "linux".equals(os);
        String guiLabel =
                isLinux
                        ? getContext().getString(R.string.km_basic_key_sup)
                        : getContext().getString(R.string.Win);

        String[] labels = {"Ctrl", guiLabel, "Alt", "Space", "Alt", "App", "Ctrl"};
        float[] w = {1f, 1f, 1f, 3f, 1f, 1f, 1f};
        View k0 = inflateKey(row, labels[0], null, w[0]);
        wireBottomModifier(
                k0, "ctrl", HID_EXT_LCTRL, parseMod("Ctrl"), parseMod("Ctrl"), () -> labels[0]);
        View k1 =
                isWindows
                        ? inflateKey(
                                row,
                                "",
                                null,
                                w[1],
                                R.drawable.ic_os_windows,
                                R.string.km_basic_cd_windows_modifier)
                        : inflateKey(row, labels[1], null, w[1]);
        wireBottomModifier(
                k1,
                "win",
                HID_EXT_LGUI,
                parseMod("Win"),
                parseMod("Win"),
                () -> (isLinux ? getContext().getString(R.string.km_basic_key_sup) : getContext().getString(R.string.Win)));
        View k2 = inflateKey(row, labels[2], null, w[2]);
        wireBottomModifier(k2, "alt", HID_EXT_LALT, parseMod("Alt"), parseMod("Alt"), () -> labels[2]);
        View k3 =
                inflateKey(
                        row,
                        "",
                        null,
                        w[3],
                        R.drawable.ic_openterface_wordmark,
                        R.string.Space_Button);
        applySpaceBarBrandIconLayout(k3);
        wireKeyedRepeatOrHold(k3, 0x2C, false, false, () -> labels[3]);
        View k4 = inflateKey(row, labels[4], null, w[4]);
        wireBottomModifier(k4, "alt", HID_EXT_RALT, parseMod("AltR"), parseMod("AltR"), () -> labels[4]);
        View k5 =
                isWindows
                        ? inflateKey(
                                row,
                                "",
                                null,
                                w[5],
                                R.drawable.ic_list_alt_24,
                                R.string.km_basic_cd_application_key)
                        : inflateKey(row, labels[5], null, w[5]);
        wireKeyedRepeatOrHold(k5, 0x65, false, false, () -> labels[5]);
        View k6 = inflateKey(row, labels[6], null, w[6]);
        wireBottomModifier(
                k6, "ctrl", HID_EXT_RCTRL, parseMod("CtrlR"), parseMod("CtrlR"), () -> labels[6]);
        row.addView(createArrowCluster(4f));
    }

    public void onHostPortChanged(@Nullable UsbSerialPort newPort) {
        port = newPort;
    }
}
