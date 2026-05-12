package com.openterface.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.openterface.keymod.prefs.KmProTouchpadPrefs;
import com.openterface.keymod.touchpad.TouchpadMouseStripBinder;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.basic.BasicPortraitScrollStripView;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.util.ImeTextForwarder;
import com.openterface.keymod.util.PopOutTouchPadDialog;
import com.openterface.keymod.util.TouchPadHaptics;
import com.openterface.keymod.util.TouchPadHelpOverlay;
import com.openterface.keymod.util.TouchPadPointerPhase;
import com.openterface.keymod.util.TouchPadTipsFormatter;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompositeFragment extends Fragment {

    private static final String TAG = "CompositeFragment";
    private static final float TOUCHPAD_WASH_HEIGHT_RATIO = 0.32f;
    private static final float TOUCHPAD_WASH_CLICK_PEAK_INTENSITY = 0.20f;
    private static final float TOUCHPAD_WASH_DRAG_BASE_INTENSITY = 0.12f;
    private static final long TOUCHPAD_WASH_CLICK_FADE_IN_MS = 70L;
    private static final long TOUCHPAD_WASH_CLICK_FADE_OUT_MS = 460L;
    private static final long TOUCHPAD_WASH_DRAG_OFF_FADE_OUT_MS = 340L;
    private static final long HYBRID_MOUSE_KEY_PULSE_MS = 85L;
    /** First pulse release, then gap, then second pulse (sequenced double-click flash). */
    private static final long HYBRID_DOUBLE_LEFT_SECOND_FLASH_DELAY_MS = HYBRID_MOUSE_KEY_PULSE_MS + 50L;
    private CustomKeyboardView keyboardView;
    private TouchPadView touchPad;
    private LinearLayout rootLayout;
    private LinearLayout touchpadSection;
    private LinearLayout toggleHandle;
    private View toggleHandlePill;
    private TextView touchPadTips;
    private TextView touchPadHelpOverlay;
    /** Normal layout only; null while split layout is shown. */
    private View touchPadInfoButton;
    /** Split mode views */
    private View splitRoot;
    private CustomKeyboardView keyboardViewLeft;
    private CustomKeyboardView keyboardViewRight;
    private ViewGroup splitTouchpadSection;
    private TouchPadView splitTouchPad;
    private TextView splitTouchPadTips;
    private TextView splitTouchPadHelpOverlay;
    /** Container to swap between normal and split layouts */
    private FrameLayout contentContainer;
    /** Landscape split: full-width system IME host (below {@link #splitRoot}). */
    private View splitImeHost;
    private EditText splitImeEdit;
    @Nullable
    private FrameLayout splitImeEditHost;
    @Nullable
    private LinearLayout splitImeEditorRow;
    @Nullable
    private LinearLayout splitImeActionRail;
    @Nullable
    private ImageButton splitImeRailToggle;
    @Nullable
    private ImageButton splitImeRailUndo;
    @Nullable
    private ImageButton splitImeRailClear;
    @Nullable
    private ImageButton splitImeRailSaved;
    @Nullable
    private ImageButton splitImeRailSend;
    @Nullable
    private ImageButton splitImeExpandToggle;
    private final View.OnLayoutChangeListener splitImeComposeRailWidthListener =
            (v, l, t, r, b, ol, ot, or, ob) -> applySplitImeComposeRailAdaptiveWidth();
    private LinearLayout splitLeftColumn;
    private LinearLayout splitRightColumn;
    private View splitToggleHandleView;
    private FrameLayout splitTopLeftFrame;
    private FrameLayout splitTopRightFrame;
    private LinearLayout splitImeShortcutsRow;
    private boolean splitShortcutsReparentedForIme;
    /** Inflated split layout root (landscape {@code fragment_composite_split}); IME padding target like Compose. */
    private View splitLayoutRoot;
    /** Outer vertical split: touchpad+toggles band vs IME stack (shortcuts + text; IME lift via root padding). */
    private static final float SPLIT_IME_OUTER_UPPER_WEIGHT = 0.30f;
    private static final float SPLIT_IME_OUTER_LOWER_WEIGHT = 0.70f;
    /** Weights inside the landscape IME host (shortcuts row : text); bottom reserve uses IME inset height. */
    private static final float SPLIT_IME_INNER_SHORTCUTS_WEIGHT = 2.0f;
    private static final float SPLIT_IME_INNER_TEXT_WEIGHT = 1.0f;
    private static final float SPLIT_TOUCHPAD_SECTION_WEIGHT_NORMAL = 1.2f;
    private static final float SPLIT_TOUCHPAD_SECTION_WEIGHT_IME_FULL_WIDTH = 1f;
    private static final float SPLIT_COMPOSE_SHORTCUTS_TOTAL_WEIGHT = 2f;
    private static final float SPLIT_COMPOSE_TOUCHPAD_WEIGHT = 2f;
    private static final float SPLIT_COMPOSE_MOUSE_KEYS_WEIGHT = 1f;
    private static final float SPLIT_COMPOSE_FOLDED_OUTER_UPPER_WEIGHT = 3f;
    private static final float SPLIT_COMPOSE_FOLDED_OUTER_LOWER_WEIGHT = 2f;
    private static final int SPLIT_COMPOSE_FOLDED_RAIL_MIN_DP = 108;
    private static final int SPLIT_COMPOSE_FOLDED_RAIL_MAX_DP = 176;
    private static final int SPLIT_COMPOSE_FOLDED_BUTTON_GAP_DP = 4;
    private View splitTouchPadInfoButton;
    /** Latest landscape split IME visibility from insets (true when software keyboard is visible). */
    private boolean splitLandscapeImeVisible;
    /** Split-landscape compose text area state: expanded (full-screen) or folded (bottom strip). */
    private boolean splitLandscapeComposeExpanded = true;
    @Nullable
    private PopOutTouchPadDialog imePopOutTouchPad;
    private boolean imeSubComposeChromeSnapshotValid;
    private float imeSubComposeSavedKeyboardWeight = 2.15f;
    private int imeSubComposeSavedTouchpadVisibility = View.VISIBLE;
    private int imeSubComposeSavedToggleVisibility = View.VISIBLE;
    /** Portrait IME sub-compose expanded: keyboard column consumes upper stack. */
    private static final float IME_SUB_COMPOSE_KEYBOARD_WEIGHT_EXPANDED = 24f;
    /**
     * Portrait BOTH + IME capture (sub-compose collapsed): outer stack ratio for
     * [touchpad+mouse] : [keyboard column] = 2 : 3, matching the target 2:1:2 three-way balance
     * with keyboard internals handled in {@link com.openterface.keymod.CustomKeyboardView}.
     */
    private static final float PORTRAIT_IME_SUB_COMPOSE_COLLAPSED_TOUCHPAD_WEIGHT = 1.0f;
    private static final float PORTRAIT_IME_SUB_COMPOSE_COLLAPSED_KEYBOARD_WEIGHT = 1.5f;
    /**
     * Portrait BOTH + IME Direct HID: more touchpad, less keyboard column than collapsed compose
     * so the shortcut strip + toolbar do not stretch when the editor row collapses.
     * Sum matches collapsed pair (2.6f) for similar balance with the toggle handle.
     */
    private static final float PORTRAIT_IME_DIRECT_HID_TOUCHPAD_WEIGHT = 1.35f;
    private static final float PORTRAIT_IME_DIRECT_HID_KEYBOARD_WEIGHT = 1.25f;
    /**
     * Portrait numpad strip + portrait IME Compose &amp; Send (collapsed): horizontal chrome width ratio
     * touchpad : mouse-key column. Mouse strip is placed on the layout start side (LTR: left).
     */
    private static final float PORTRAIT_STRIP_TOUCHPAD_WEIGHT = 5f;
    private static final float PORTRAIT_STRIP_MOUSE_KEYS_WEIGHT = 2f;
    private final ExecutorService imeSplitTextExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ImeSplitTextForward");
        t.setDaemon(true);
        return t;
    });
    @Nullable
    private CustomKeyboardView.OnImeSubComposeChromeListener imeSubComposeChromeListener;
    /** Track registered keyboard views for OS change listener cleanup */
    private final List<MainActivity.OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();
    public UsbSerialPort port;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    private boolean isDragMode = false;

    private static final long POINTER_IDLE_AFTER_MS = 400L;
    private final Handler tipHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable hybridDoubleLeftSecondFlashRunnable;
    private TouchPadPointerPhase pointerPhase = TouchPadPointerPhase.IDLE;
    private View touchPadBottomWashOverlay;
    private View splitTouchPadBottomWashOverlay;
    private int touchPadWashColor = Color.TRANSPARENT;
    private float currentTouchPadWashIntensity = 0f;
    private AnimatorSet touchPadButtonPulseAnimator;
    private final Runnable pointerIdleRunnable =
            () -> {
                pointerPhase = TouchPadPointerPhase.IDLE;
                updateTouchPadTips();
                updateSplitTouchPadTips();
            };

    private enum DisplayMode { BOTH, KEYBOARD, TOUCHPAD, SPLIT }
    private DisplayMode displayMode = DisplayMode.BOTH;

    /** Keyboard &amp; Mouse Pro: swipe-up host modifier locks (separate from KM Basic’s controller). */
    private final KmBasicHoldLockController proHoldLockController = new KmBasicHoldLockController();

    @Nullable private LinearLayout proTouchpadChromeRoot;
    @Nullable private ViewGroup proTouchpadMouseKeys;
    @Nullable private LinearLayout touchpadPadHost;
    @Nullable private BasicPortraitScrollStripView proTouchpadScrollStrip;
    @Nullable private TouchpadMouseStripBinder proMouseStripBinder;
    @Nullable private TextView proMouseBtnLeft;
    @Nullable private TextView proMouseBtnMiddle;
    @Nullable private TextView proMouseBtnRight;
    private boolean proTouchpadMouseLayoutCompact;
    private final TouchpadMouseStripBinder.Host proMouseStripHost =
            new TouchpadMouseStripBinder.Host() {
                @Override
                public UsbSerialPort getUsbPort() {
                    return port;
                }

                @Override
                public BluetoothService getBluetoothService() {
                    return bluetoothService;
                }

                @Override
                public boolean isBluetoothServiceBound() {
                    return isServiceBound;
                }
            };

    private final View.OnLayoutChangeListener proTouchpadSectionLayoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!isAdded()) {
                    return;
                }
                if (KmProTouchpadPrefs.showsMouseKeyStrip(requireContext())) {
                    applyProTouchpadMouseLayoutCompactOrComfortable();
                }
                applyProTouchpadScrollStripLayout();
            };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Bound to BluetoothService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    };

    /**
     * Reload Shortcut Hub prefs (profiles, My Shortcuts, strip display mode, hub slots) into the keyboard.
     */
    public void refreshKeyboardShortcutStripFromExternalHub() {
        if (keyboardView != null) {
            keyboardView.refreshAfterShortcutHubPrefsChange();
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.refreshAfterShortcutHubPrefsChange();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.refreshAfterShortcutHubPrefsChange();
        }
    }

    /** Reload alternate-hints preference and rebuild keyboards (Keyboard and Mouse Pro setup). */
    public void refreshKeyboardAlternatesHintsFromPrefs() {
        if (keyboardView != null) {
            keyboardView.reloadKeyboardAlternatesHintsFromPrefs();
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.reloadKeyboardAlternatesHintsFromPrefs();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.reloadKeyboardAlternatesHintsFromPrefs();
        }
    }

    /** Full keyboard rebuild after KM Pro setup (e.g. long-press repeat vs hold). */
    public void refreshCompositeKeyboardLayoutFromKmProSetup() {
        if (keyboardView != null) {
            keyboardView.rebuildKeyboardFromKmProSetup();
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.rebuildKeyboardFromKmProSetup();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.rebuildKeyboardFromKmProSetup();
        }
    }

    /** Apply KM Pro touchpad prefs (mouse strip visibility, binder, compact layout). */
    public void refreshProTouchpadChromeFromKmProSetup() {
        if (!isAdded()) {
            return;
        }
        bindProTouchpadChromeReferences();
        boolean show = KmProTouchpadPrefs.showsMouseKeyStrip(requireContext());
        if (proTouchpadMouseKeys != null) {
            proTouchpadMouseKeys.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        detachProMouseStripBinder();
        if (show) {
            attachProMouseStripBinderIfNeeded();
            applyProTouchpadMouseLayoutCompactOrComfortable();
            if (proTouchpadMouseKeys != null) {
                proTouchpadMouseKeys.post(CompositeFragment.this::applyProTouchpadMouseLayoutCompactOrComfortable);
            }
        } else {
            proTouchpadMouseLayoutCompact = false;
            resetProTouchpadChromeOrientationComfortable();
        }
        applyPadClickDragGesturesToTouchPads(requireContext());
        updateTouchPadTips();
        updateSplitTouchPadTips();
        updateHybridDragLeftVisual();
        applyProTouchpadScrollStripLayout();
        wireProTouchpadScrollStrip();
    }

    private void applyPadClickDragGesturesToTouchPads(Context context) {
        boolean padClickDrag =
                !KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(context);
        if (touchPad != null) {
            touchPad.setPadClickDragGesturesEnabled(padClickDrag);
        }
        if (splitTouchPad != null) {
            splitTouchPad.setPadClickDragGesturesEnabled(padClickDrag);
        }
    }

    private void cancelHybridDoubleLeftSecondFlash() {
        if (hybridDoubleLeftSecondFlashRunnable != null) {
            tipHandler.removeCallbacks(hybridDoubleLeftSecondFlashRunnable);
            hybridDoubleLeftSecondFlashRunnable = null;
        }
    }

    private void bindProTouchpadChromeReferences() {
        View root = contentContainer != null && contentContainer.getChildCount() > 0
                ? contentContainer.getChildAt(0)
                : null;
        if (root == null) {
            proTouchpadChromeRoot = null;
            proTouchpadMouseKeys = null;
            touchpadPadHost = null;
            proTouchpadScrollStrip = null;
            proMouseBtnLeft = null;
            proMouseBtnMiddle = null;
            proMouseBtnRight = null;
            return;
        }
        proTouchpadChromeRoot = root.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseKeys = root.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = root.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = root.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = root.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = root.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = root.findViewById(R.id.pro_touchpad_btn_right);
    }

    private void detachProMouseStripBinder() {
        if (proMouseStripBinder != null) {
            proMouseStripBinder.detach();
            proMouseStripBinder = null;
        }
    }

    private void registerProTouchpadSectionLayoutListener() {
        if (touchpadSection != null) {
            touchpadSection.removeOnLayoutChangeListener(proTouchpadSectionLayoutListener);
            touchpadSection.addOnLayoutChangeListener(proTouchpadSectionLayoutListener);
        }
    }

    private void attachProMouseStripBinderIfNeeded() {
        if (!KmProTouchpadPrefs.showsMouseKeyStrip(requireContext()) || touchpadSection == null) {
            return;
        }
        View left = touchpadSection.findViewById(R.id.pro_touchpad_btn_left);
        View mid = touchpadSection.findViewById(R.id.pro_touchpad_btn_middle);
        View right = touchpadSection.findViewById(R.id.pro_touchpad_btn_right);
        if (left == null && mid == null && right == null) {
            return;
        }
        proMouseStripBinder = new TouchpadMouseStripBinder(this, proMouseStripHost, proHoldLockController);
        proMouseStripBinder.attach(left, mid, right);
    }

    private int stripAndLockMaskWithoutGestureDrag() {
        if (proMouseStripBinder != null) {
            return proMouseStripBinder.effectiveMouseMaskForHid();
        }
        return proHoldLockController.getLockedMouseMask() & 0xFF;
    }

    private int snapshotProRelMoveButtonMask() {
        int drag = isDragMode ? TouchpadMouseStripBinder.BTN_LEFT : 0;
        if (!KmProTouchpadPrefs.showsMouseKeyStrip(requireContext())) {
            return drag & 0xFF;
        }
        return (drag | stripAndLockMaskWithoutGestureDrag()) & 0xFF;
    }

    /**
     * Reorders the two direct children of {@link #proTouchpadChromeRoot} so vertical comfortable layout
     * (pad above buttons) and default horizontal strip (pad start, mouse end) stay consistent.
     *
     * @param mouseKeysFirst if true, mouse-key strip is index 0 and pad host index 1 (portrait numpad /
     *     IME compose horizontal strip); if false, pad host first then mouse keys (XML default).
     */
    private void ensureProTouchpadChromeSiblingOrder(boolean mouseKeysFirst) {
        if (proTouchpadChromeRoot == null || touchpadPadHost == null || proTouchpadMouseKeys == null) {
            return;
        }
        int iPad = proTouchpadChromeRoot.indexOfChild(touchpadPadHost);
        int iMouse = proTouchpadChromeRoot.indexOfChild(proTouchpadMouseKeys);
        if (iPad < 0 || iMouse < 0) {
            return;
        }
        boolean already =
                mouseKeysFirst ? (iMouse == 0 && iPad == 1) : (iPad == 0 && iMouse == 1);
        if (already) {
            return;
        }
        proTouchpadChromeRoot.removeView(touchpadPadHost);
        proTouchpadChromeRoot.removeView(proTouchpadMouseKeys);
        if (mouseKeysFirst) {
            proTouchpadChromeRoot.addView(proTouchpadMouseKeys, 0);
            proTouchpadChromeRoot.addView(touchpadPadHost, 1);
        } else {
            proTouchpadChromeRoot.addView(touchpadPadHost, 0);
            proTouchpadChromeRoot.addView(proTouchpadMouseKeys, 1);
        }
    }

    private void resetProTouchpadChromeOrientationComfortable() {
        if (proTouchpadChromeRoot == null || touchpadPadHost == null || proTouchpadMouseKeys == null) {
            return;
        }
        ensureProTouchpadChromeSiblingOrder(false);
        proTouchpadChromeRoot.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.height = 0;
        padLp.weight = 1f;
        touchpadPadHost.setLayoutParams(padLp);
        LinearLayout.LayoutParams mLp = (LinearLayout.LayoutParams) proTouchpadMouseKeys.getLayoutParams();
        mLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        mLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mLp.weight = 0f;
        proTouchpadMouseKeys.setLayoutParams(mLp);
        configureProMouseKeysRow(false);
        applyProTouchpadMouseKeysDefaultPadding();
        applyProTouchpadScrollStripLayout();
    }

    private void applyProTouchpadMouseKeysDefaultPadding() {
        if (proTouchpadMouseKeys == null) {
            return;
        }
        int top =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_top);
        int bottom =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_bottom);
        proTouchpadMouseKeys.setPaddingRelative(0, top, 0, bottom);
    }

    private void wireProTouchpadScrollStrip() {
        if (proTouchpadScrollStrip == null || !isAdded()) {
            return;
        }
        proTouchpadScrollStrip.setSensitivityPercentSupplier(
                KmProTouchpadPrefs::getStripScrollSensitivityPercent);
        proTouchpadScrollStrip.setOnStripScrollListener(
                (deltaX, deltaY) -> sendScrollData(deltaX, deltaY));
    }

    /** Pad column: optional {@link BasicPortraitScrollStripView} beside {@link #touchpad_pad_content}. */
    private void applyProTouchpadScrollStripLayout() {
        if (touchpadPadHost == null || !isAdded()) {
            return;
        }
        if (!(touchpadPadHost instanceof LinearLayout)) {
            return;
        }
        LinearLayout host = touchpadPadHost;
        View padContent = host.findViewById(R.id.touchpad_pad_content);
        BasicPortraitScrollStripView strip = proTouchpadScrollStrip;
        if (padContent == null || strip == null) {
            return;
        }
        boolean show = KmProTouchpadPrefs.isScrollStripEnabled(requireContext());
        strip.setVisibility(show ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams cLp = (LinearLayout.LayoutParams) padContent.getLayoutParams();
        LinearLayout.LayoutParams sLp = (LinearLayout.LayoutParams) strip.getLayoutParams();
        if (show) {
            cLp.width = 0;
            cLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            cLp.weight = 5f;
            sLp.width = 0;
            sLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            sLp.weight = 1f;
        } else {
            cLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            cLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            cLp.weight = 0f;
            sLp.width = 0;
            sLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            sLp.weight = 0f;
        }
        padContent.setLayoutParams(cLp);
        strip.setLayoutParams(sLp);
    }

    private void applyProTouchpadMouseLayoutCompactOrComfortable() {
        if (proTouchpadChromeRoot == null
                || touchpadPadHost == null
                || proTouchpadMouseKeys == null
                || touchpadSection == null
                || !KmProTouchpadPrefs.showsMouseKeyStrip(requireContext())) {
            return;
        }
        if (isSplitLandscapeComposeSendMode()) {
            return;
        }
        int th = touchpadSection.getHeight();
        int threshold =
                getResources()
                        .getDimensionPixelSize(R.dimen.pro_touchpad_section_compact_height_threshold);
        boolean numpadStripHorizontal = isPortraitNumpadTouchpadMode();
        boolean imeComposeStripHorizontal = isPortraitImeComposeSendTouchpadMode();
        boolean compactByHeight = th > 0 && th <= threshold;
        boolean useHorizontalChrome = numpadStripHorizontal || imeComposeStripHorizontal || compactByHeight;
        if (!useHorizontalChrome && th <= 0) {
            return;
        }
        proTouchpadMouseLayoutCompact = useHorizontalChrome;
        if (!useHorizontalChrome) {
            resetProTouchpadChromeOrientationComfortable();
            return;
        }
        proTouchpadChromeRoot.setOrientation(LinearLayout.HORIZONTAL);
        boolean portraitTargetStrip = numpadStripHorizontal || imeComposeStripHorizontal;
        ensureProTouchpadChromeSiblingOrder(portraitTargetStrip);
        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.width = 0;
        LinearLayout.LayoutParams mLp = (LinearLayout.LayoutParams) proTouchpadMouseKeys.getLayoutParams();
        mLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        int topPad =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_top);
        int bottomPad =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_bottom);
        if (portraitTargetStrip) {
            padLp.weight = PORTRAIT_STRIP_TOUCHPAD_WEIGHT;
            mLp.width = 0;
            mLp.weight = PORTRAIT_STRIP_MOUSE_KEYS_WEIGHT;
            int hPad =
                    getResources()
                            .getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_strip_padding_horizontal);
            proTouchpadMouseKeys.setPaddingRelative(hPad, topPad, hPad, bottomPad);
        } else {
            padLp.weight = 1f;
            mLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            mLp.weight = 0f;
            proTouchpadMouseKeys.setPaddingRelative(0, topPad, 0, bottomPad);
        }
        touchpadPadHost.setLayoutParams(padLp);
        proTouchpadMouseKeys.setLayoutParams(mLp);
        configureProMouseKeysRow(true);
        applyProTouchpadScrollStripLayout();
    }

    private void configureProMouseKeysRow(boolean compactVerticalStrip) {
        if (!(proTouchpadMouseKeys instanceof LinearLayout)) {
            return;
        }
        LinearLayout row = (LinearLayout) proTouchpadMouseKeys;
        row.setOrientation(compactVerticalStrip ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int n = row.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = row.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
            if (compactVerticalStrip) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = 0;
                lp.weight = 1f;
                lp.setMarginStart(0);
                lp.setMarginEnd(0);
            } else {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.width = 0;
                lp.weight = i == 1 ? 1f : 2f;
                int gap = (int) (getResources().getDisplayMetrics().density * 8f);
                boolean splitStripHorizontalRow =
                        splitRoot != null
                                && KmProTouchpadPrefs.showsMouseKeyStrip(requireContext());
                int edgeInset =
                        splitStripHorizontalRow
                                ? getResources()
                                        .getDimensionPixelSize(
                                                R.dimen.pro_touchpad_mouse_keys_split_horizontal_edge_inset)
                                : 0;
                lp.setMarginStart(i == 0 ? edgeInset : gap);
                lp.setMarginEnd(i == n - 1 ? edgeInset : 0);
            }
            c.setLayoutParams(lp);
        }
    }

    private void updateHybridDragLeftVisual() {
        if (!isAdded()) {
            return;
        }
        bindProTouchpadChromeReferences();
        if (proMouseBtnLeft == null) {
            return;
        }
        if (!KmProTouchpadPrefs.isHybridMode(requireContext())) {
            proMouseBtnLeft.setPressed(false);
            proMouseBtnLeft.refreshDrawableState();
            proMouseBtnLeft.invalidate();
            return;
        }
        if (proHoldLockController.isMouseLocked(TouchpadMouseStripBinder.BTN_LEFT)) {
            return;
        }
        boolean stripLeft =
                proMouseStripBinder != null
                        && (proMouseStripBinder.getStripHeldMask() & TouchpadMouseStripBinder.BTN_LEFT)
                                != 0;
        boolean pressed = !stripLeft && isDragMode;
        proMouseBtnLeft.setPressed(pressed);
        proMouseBtnLeft.refreshDrawableState();
        if (pressed) {
            proMouseBtnLeft.jumpDrawablesToCurrentState();
        }
        proMouseBtnLeft.invalidate();
    }

    /**
     * {@link MouseRelHidTransport#sendLeftClick} (and similar) ends with {@link
     * MouseRelHidTransport#releaseAll}; if L/M/R strip or hold-lock still has buttons down, put the
     * mask back after a short delay.
     */
    private void scheduleReassertStripAndLockMouseButtons(long delayMs) {
        int held = stripAndLockMaskWithoutGestureDrag();
        if (held == 0) {
            return;
        }
        tipHandler.postDelayed(
                () -> {
                    if (!isAdded()) {
                        return;
                    }
                    int h = stripAndLockMaskWithoutGestureDrag();
                    if (h != 0) {
                        MouseRelHidTransport.sendRelButtonsNoMotion(
                                port, bluetoothService, isServiceBound, h);
                    }
                },
                delayMs);
    }

    private void pulseProMouseKeyHybrid(int buttonBit) {
        if (!KmProTouchpadPrefs.isHybridMode(requireContext()) || !isAdded()) {
            return;
        }
        bindProTouchpadChromeReferences();
        TextView key =
                buttonBit == TouchpadMouseStripBinder.BTN_LEFT
                        ? proMouseBtnLeft
                        : buttonBit == TouchpadMouseStripBinder.BTN_RIGHT
                                ? proMouseBtnRight
                                : proMouseBtnMiddle;
        if (key == null) {
            return;
        }
        if (key.isSelected()) {
            return;
        }
        if (proMouseStripBinder != null
                && (proMouseStripBinder.getStripHeldMask() & buttonBit) != 0) {
            return;
        }
        key.setPressed(true);
        key.refreshDrawableState();
        key.jumpDrawablesToCurrentState();
        key.invalidate();
        final int bit = buttonBit;
        tipHandler.postDelayed(
                () -> {
                    if (!isAdded()) {
                        return;
                    }
                    bindProTouchpadChromeReferences();
                    TextView k =
                            bit == TouchpadMouseStripBinder.BTN_LEFT
                                    ? proMouseBtnLeft
                                    : bit == TouchpadMouseStripBinder.BTN_RIGHT
                                            ? proMouseBtnRight
                                            : proMouseBtnMiddle;
                    if (k != null) {
                        k.setPressed(false);
                        k.refreshDrawableState();
                        k.invalidate();
                    }
                },
                HYBRID_MOUSE_KEY_PULSE_MS);
    }

    /** Updates {@link #port} on all keyboard halves and clears Pro hold-locks when the host disconnects. */
    public void applyHostPortToKeyboardViews(@Nullable UsbSerialPort newPort) {
        port = newPort;
        if (keyboardView != null) {
            keyboardView.setPort(newPort);
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.setPort(newPort);
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.setPort(newPort);
        }
        if (newPort == null) {
            proHoldLockController.clearAllAndReleaseHid(null, bluetoothService, isServiceBound);
        }
    }

    private void bindProHoldLockControllerToKeyboardViews() {
        if (keyboardView != null) {
            keyboardView.setHoldLockController(proHoldLockController);
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.setHoldLockController(proHoldLockController);
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.setHoldLockController(proHoldLockController);
        }
    }

    private void detachProHoldLockControllerFromKeyboardViews() {
        if (keyboardView != null) {
            keyboardView.setHoldLockController(null);
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.setHoldLockController(null);
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.setHoldLockController(null);
        }
    }

    public static CompositeFragment newInstance(UsbSerialPort port) {
        CompositeFragment fragment = new CompositeFragment();
        fragment.port = port;
        return fragment;
    }

    public static String makeChecksum(String data) {
        int total = 0;

        for (int i = 0; i < data.length(); i += 2) {
            String byteStr = data.substring(i, Math.min(i + 2, data.length()));
            total += Integer.parseInt(byteStr, 16);
        }

        int mod = total % 256;

        return String.format("%02X", mod);
    }

    public static void checkSendLogData(String sendKBData) {
        StringBuilder check_send_data = new StringBuilder();
        for (int i = 0; i < sendKBData.length(); i += 2) {
            if (i + 2 <= sendKBData.length()) {
                check_send_data.append(sendKBData.substring(i, i + 2)).append(" ");
            } else {
                check_send_data.append(sendKBData.substring(i)).append(" ");
            }
        }
        Log.d(TAG, "sendKBData: " + check_send_data.toString().trim());
    }

    public static byte[] hexStringToByteArray(String ByteData) {
        if (ByteData.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }

        int len = ByteData.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(ByteData.charAt(i), 16) << 4)
                    + Character.digit(ByteData.charAt(i + 1), 16));
        }
        return data;
    }

    private void releaseAllMSData() {
        String releaseSendMSData = "57AB00050501000000000D";
        if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                bluetoothService.sendData(releaseSendKBDataBytes);
                Log.d(TAG, "Sent Bluetooth release data");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error sending Bluetooth release data: " + e.getMessage());
            }
        } else if (port != null) {
            try {
                byte[] releaseSendKBDataBytes = hexStringToByteArray(releaseSendMSData);
                Thread.sleep(10);
                port.write(releaseSendKBDataBytes, 20);
                Log.d(TAG, "Sent USB release data");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error sending USB release data: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "No connection available for release data");
        }
    }

    private void setDragMode(boolean enabled) {
        cancelTouchPadButtonPulseAnimation();
        isDragMode = enabled;
        int base = stripAndLockMaskWithoutGestureDrag();
        int mask = enabled ? (base | TouchpadMouseStripBinder.BTN_LEFT) : base;
        MouseRelHidTransport.sendRelButtonsNoMotion(port, bluetoothService, isServiceBound, mask);
        if (enabled) {
            applyBottomWashIntensity(TOUCHPAD_WASH_DRAG_BASE_INTENSITY);
        } else {
            animateBottomWashIntensityTo(0f, TOUCHPAD_WASH_DRAG_OFF_FADE_OUT_MS, new DecelerateInterpolator());
        }
        updateTouchPadTips();
        updateSplitTouchPadTips();
        updateHybridDragLeftVisual();
        Log.d(TAG, "Drag mode " + (enabled ? "ON" : "OFF"));
    }

    private void initTouchPadWashStyle() {
        int accent = ThemeManager.getColorPrimary(requireContext());
        touchPadWashColor = ColorUtils.setAlphaComponent(accent, 0x88);
    }

    private View ensureBottomWashOverlayForPad(TouchPadView pad, View existingOverlay) {
        if (pad == null) return null;
        ViewParent parentRef = pad.getParent();
        if (!(parentRef instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) parentRef;
        if (!(parent instanceof FrameLayout)) return null;

        View overlay = existingOverlay;
        if (overlay == null) {
            overlay = new View(requireContext());
            overlay.setClickable(false);
            overlay.setFocusable(false);
            GradientDrawable washDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[] {touchPadWashColor, Color.TRANSPARENT});
            overlay.setBackground(washDrawable);
        } else {
            ViewParent existingParent = overlay.getParent();
            if (existingParent instanceof ViewGroup && existingParent != parent) {
                ((ViewGroup) existingParent).removeView(overlay);
            }
        }

        if (overlay.getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1,
                    Gravity.BOTTOM);
            parent.addView(overlay, lp);
        }

        View finalOverlay = overlay;
        pad.post(() -> {
            ViewGroup.LayoutParams params = finalOverlay.getLayoutParams();
            int washHeight = Math.max(1, Math.round(pad.getHeight() * TOUCHPAD_WASH_HEIGHT_RATIO));
            if (params != null && params.height != washHeight) {
                params.height = washHeight;
                finalOverlay.setLayoutParams(params);
            }
            applyBottomWashIntensity(isDragMode ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f);
        });
        return overlay;
    }

    private void setupBottomWashOverlays() {
        touchPadBottomWashOverlay = ensureBottomWashOverlayForPad(touchPad, touchPadBottomWashOverlay);
        splitTouchPadBottomWashOverlay = ensureBottomWashOverlayForPad(splitTouchPad, splitTouchPadBottomWashOverlay);
    }

    private void applyBottomWashIntensity(float intensity) {
        currentTouchPadWashIntensity = Math.max(0f, Math.min(1f, intensity));
        if (touchPadBottomWashOverlay != null) {
            touchPadBottomWashOverlay.setAlpha(currentTouchPadWashIntensity);
        }
        if (splitTouchPadBottomWashOverlay != null) {
            splitTouchPadBottomWashOverlay.setAlpha(currentTouchPadWashIntensity);
        }
    }

    private void animateBottomWashIntensityTo(float target, long durationMs, android.animation.TimeInterpolator interpolator) {
        float clampedTarget = Math.max(0f, Math.min(1f, target));
        ValueAnimator animator = ValueAnimator.ofFloat(currentTouchPadWashIntensity, clampedTarget);
        animator.setDuration(durationMs);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> applyBottomWashIntensity((float) a.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet();
        set.play(animator);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                touchPadButtonPulseAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                touchPadButtonPulseAnimator = null;
            }
        });
        touchPadButtonPulseAnimator = set;
        set.start();
    }

    private void cancelTouchPadButtonPulseAnimation() {
        if (touchPadButtonPulseAnimator != null) {
            touchPadButtonPulseAnimator.cancel();
            touchPadButtonPulseAnimator = null;
        }
    }

    private void pulseTouchPadButtonVisual() {
        if (touchPadBottomWashOverlay == null && splitTouchPadBottomWashOverlay == null) return;
        cancelTouchPadButtonPulseAnimation();
        final float rest = isDragMode ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f;
        final float peak = Math.max(rest, TOUCHPAD_WASH_CLICK_PEAK_INTENSITY);

        ValueAnimator fadeIn = ValueAnimator.ofFloat(rest, peak);
        fadeIn.setDuration(TOUCHPAD_WASH_CLICK_FADE_IN_MS);
        fadeIn.setInterpolator(new LinearInterpolator());
        fadeIn.addUpdateListener(a -> applyBottomWashIntensity((float) a.getAnimatedValue()));

        ValueAnimator fadeOut = ValueAnimator.ofFloat(peak, rest);
        fadeOut.setDuration(TOUCHPAD_WASH_CLICK_FADE_OUT_MS);
        fadeOut.setInterpolator(new DecelerateInterpolator());
        fadeOut.addUpdateListener(a -> applyBottomWashIntensity((float) a.getAnimatedValue()));

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(fadeIn, fadeOut);
        set.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        touchPadButtonPulseAnimator = null;
                        applyBottomWashIntensity(rest);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        touchPadButtonPulseAnimator = null;
                        applyBottomWashIntensity(isDragMode ? TOUCHPAD_WASH_DRAG_BASE_INTENSITY : 0f);
                    }
                });
        touchPadButtonPulseAnimator = set;
        set.start();
    }

    private void updateTouchPadTips() {
        if (touchPadTips == null || splitRoot != null) {
            return;
        }
        if (KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(requireContext())) {
            touchPadTips.setVisibility(View.GONE);
            return;
        }
        touchPadTips.setVisibility(View.VISIBLE);
        // Portrait numpad / IME capture: status line only; info button is hidden separately when IME is on.
        touchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    /**
     * Portrait BOTH, normal (non-split) layout, IME capture on, sub-compose strip not expanded.
     * Used to dismiss the full-screen help overlay in this mode (tips stay visible; no info button).
     */
    private boolean isPortraitImeCaptureSubComposeNormal() {
        if (splitRoot != null) {
            return false;
        }
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
            return false;
        }
        if (displayMode == DisplayMode.KEYBOARD) {
            return false;
        }
        if (keyboardView == null) {
            return false;
        }
        return keyboardView.isSystemImeCaptureMode() && !keyboardView.isImeSubComposeExpanded();
    }

    /** Portrait keyboard-only strip: touchpad + numpad; gesture help UI is suppressed. */
    private boolean isPortraitNumpadTouchpadMode() {
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
            return false;
        }
        return displayMode == DisplayMode.KEYBOARD;
    }

    /**
     * Portrait BOTH + IME capture collapsed in compose mode (not Direct HID), with L/M/R strip.
     * Mirrors portrait numpad strip geometry (touchpad : mouse strip = 2 : 1).
     */
    private boolean isPortraitImeComposeSendTouchpadMode() {
        if (splitRoot != null) {
            return false;
        }
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
            return false;
        }
        if (displayMode != DisplayMode.BOTH || keyboardView == null) {
            return false;
        }
        if (!keyboardView.isSystemImeCaptureMode()
                || keyboardView.isImeSubComposeExpanded()
                || keyboardView.isImeSubComposeDirectHidMode()) {
            return false;
        }
        return KmProTouchpadPrefs.showsMouseKeyStrip(requireContext());
    }

    private void applyPortraitNumpadTouchpadChrome() {
        applyTouchpadInfoVisibility();
        if (isPortraitNumpadTouchpadMode() && touchPadHelpOverlay != null) {
            TouchPadHelpOverlay.hideImmediately(touchPadHelpOverlay);
        }
    }

    private void applyTouchpadInfoVisibility() {
        boolean numpad = isPortraitNumpadTouchpadMode();
        boolean ime = isImeCaptureActive();
        boolean hide = numpad || ime;
        if (touchPadInfoButton != null) {
            touchPadInfoButton.setVisibility(hide ? View.GONE : View.VISIBLE);
        }
        if (splitTouchPadInfoButton != null) {
            splitTouchPadInfoButton.setVisibility(hide ? View.GONE : View.VISIBLE);
        }
        if (splitRoot == null) {
            if (isPortraitImeCaptureSubComposeNormal() && touchPadHelpOverlay != null) {
                TouchPadHelpOverlay.hideImmediately(touchPadHelpOverlay);
            }
            updateTouchPadTips();
        }
    }

    private boolean isImeCaptureActive() {
        if (keyboardView != null && keyboardView.isSystemImeCaptureMode()) {
            return true;
        }
        if (keyboardViewLeft != null && keyboardViewLeft.isSystemImeCaptureMode()) {
            return true;
        }
        if (keyboardViewRight != null && keyboardViewRight.isSystemImeCaptureMode()) {
            return true;
        }
        return false;
    }

    private void registerImeCaptureListener(@Nullable CustomKeyboardView kbd) {
        if (kbd == null) {
            return;
        }
        kbd.setOnImeCaptureModeChangedListener(this::onKeyboardImeCaptureModeChanged);
    }

    private void registerImeSubComposeChromeListener(@Nullable CustomKeyboardView kbd) {
        if (kbd == null) {
            return;
        }
        if (imeSubComposeChromeListener == null) {
            imeSubComposeChromeListener =
                    new CustomKeyboardView.OnImeSubComposeChromeListener() {
                        @Override
                        public void onImeSubComposeExpandedChanged(
                                CustomKeyboardView source, boolean expanded) {
                            if (!isAdded()) {
                                return;
                            }
                            applyImeSubComposeFragmentChrome(expanded);
                            applyOrientationLayout();
                        }

                        @Override
                        public void onImeSubComposeDirectHidModeChanged(
                                CustomKeyboardView source, boolean direct) {
                            if (!isAdded()) {
                                return;
                            }
                            if (splitRoot != null
                                    && splitImeHost != null
                                    && splitImeHost.getVisibility() == View.VISIBLE
                                    && source.isSystemImeCaptureMode()) {
                                applySplitImeLayout(true);
                            }
                            if (keyboardViewLeft != null) {
                                keyboardViewLeft.resyncImeSubComposeDirectHidFromPrefs();
                            }
                            if (keyboardViewRight != null) {
                                keyboardViewRight.resyncImeSubComposeDirectHidFromPrefs();
                            }
                            refreshSplitLandscapeImeComposeRailBinding();
                            applyOrientationLayout();
                        }

                        @Override
                        public void onImeToolbarPopOutTouchpadRequested(CustomKeyboardView source) {
                            if (!isAdded()) {
                                return;
                            }
                            if (imePopOutTouchPad == null) {
                                imePopOutTouchPad = new PopOutTouchPadDialog(CompositeFragment.this, false);
                            }
                            imePopOutTouchPad.show();
                        }
                    };
        }
        kbd.setOnImeSubComposeChromeListener(imeSubComposeChromeListener);
    }

    private void applyImeSubComposeFragmentChrome(boolean expanded) {
        if (splitRoot != null) {
            return;
        }
        if (!isAdded()) {
            return;
        }
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
            return;
        }
        if (touchpadSection == null || toggleHandle == null || keyboardView == null) {
            return;
        }
        if (expanded) {
            if (!imeSubComposeChromeSnapshotValid) {
                imeSubComposeSavedTouchpadVisibility = touchpadSection.getVisibility();
                imeSubComposeSavedToggleVisibility = toggleHandle.getVisibility();
                ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    imeSubComposeSavedKeyboardWeight = ((LinearLayout.LayoutParams) lp).weight;
                }
                imeSubComposeChromeSnapshotValid = true;
            }
            touchpadSection.setVisibility(View.GONE);
            toggleHandle.setVisibility(View.GONE);
            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, IME_SUB_COMPOSE_KEYBOARD_WEIGHT_EXPANDED));
        } else {
            if (imeSubComposeChromeSnapshotValid) {
                touchpadSection.setVisibility(imeSubComposeSavedTouchpadVisibility);
                toggleHandle.setVisibility(imeSubComposeSavedToggleVisibility);
                keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, imeSubComposeSavedKeyboardWeight));
                imeSubComposeChromeSnapshotValid = false;
            }
        }
        if (rootLayout != null) {
            rootLayout.requestLayout();
        }
        applyTouchpadInfoVisibility();
    }

    private void onKeyboardImeCaptureModeChanged(CustomKeyboardView source, boolean enabled) {
        applyTouchpadInfoVisibility();
        if (!enabled) {
            applyImeSubComposeFragmentChrome(false);
            clearSplitImeComposeRailBindingAll();
        }
        if (splitImeHost != null) {
            applySplitImeLayout(enabled);
        }
        if (enabled) {
            refreshSplitLandscapeImeComposeRailBinding();
        }
        applyOrientationLayout();
        requestCompositeImeInsetsAfterImeChange();
    }

    /**
     * Lift the whole column above the soft keyboard. {@code DrawerLayout} + weighted
     * {@code fragment_container} often prevent {@code adjustResize} alone from shrinking the
     * fragment, so IME bottom insets are applied as root padding.
     */
    private void setupCompositeImeRootInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean imeVisible = imeBottom > 0;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);
            if (rootLayout != null && v == rootLayout && keyboardView != null) {
                keyboardView.setPortraitSystemImeVisible(imeVisible);
            }
            if (splitLayoutRoot != null && v == splitLayoutRoot) {
                if (splitLandscapeImeVisible != imeVisible) {
                    splitLandscapeImeVisible = imeVisible;
                    if (isSplitLandscapeComposeSendMode()) {
                        v.post(this::applySplitImeComposeRailAdaptiveWidth);
                    }
                }
            }
            return windowInsets;
        });
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    @Nullable
    private View compositeImeInsetRoot() {
        if (splitLayoutRoot != null) {
            return splitLayoutRoot;
        }
        return rootLayout;
    }

    private void requestCompositeImeInsetsAfterImeChange() {
        View root = compositeImeInsetRoot();
        if (root == null) {
            return;
        }
        root.post(() -> ViewCompat.requestApplyInsets(root));
        root.postDelayed(() -> ViewCompat.requestApplyInsets(root), 120);
    }

    private void applySplitImeHostInnerWeights() {
        if (splitImeShortcutsRow == null || splitImeEdit == null) {
            return;
        }
        View weightedEditor = splitImeEditorRow != null ? splitImeEditorRow : splitImeEdit;
        LinearLayout.LayoutParams rowLp =
                (LinearLayout.LayoutParams) splitImeShortcutsRow.getLayoutParams();
        rowLp.height = 0;
        rowLp.weight = SPLIT_IME_INNER_SHORTCUTS_WEIGHT;
        splitImeShortcutsRow.setLayoutParams(rowLp);
        LinearLayout.LayoutParams editLp =
                (LinearLayout.LayoutParams) weightedEditor.getLayoutParams();
        editLp.height = 0;
        editLp.weight = SPLIT_IME_INNER_TEXT_WEIGHT;
        weightedEditor.setLayoutParams(editLp);
    }

    private void dockSplitShortcutsForIme() {
        if (splitShortcutsReparentedForIme
                || splitTopLeftFrame == null
                || splitTopRightFrame == null
                || splitImeShortcutsRow == null
                || splitLeftColumn == null
                || splitRightColumn == null) {
            return;
        }
        ViewGroup leftParent = (ViewGroup) splitTopLeftFrame.getParent();
        if (leftParent != null) {
            leftParent.removeView(splitTopLeftFrame);
        }
        ViewGroup rightParent = (ViewGroup) splitTopRightFrame.getParent();
        if (rightParent != null) {
            rightParent.removeView(splitTopRightFrame);
        }
        LinearLayout.LayoutParams half =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        splitImeShortcutsRow.addView(splitTopLeftFrame, half);
        splitImeShortcutsRow.addView(splitTopRightFrame, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        splitImeShortcutsRow.setVisibility(View.VISIBLE);
        splitLeftColumn.setVisibility(View.GONE);
        splitRightColumn.setVisibility(View.GONE);
        LinearLayout.LayoutParams tpLp =
                (LinearLayout.LayoutParams) splitTouchpadSection.getLayoutParams();
        tpLp.weight = SPLIT_TOUCHPAD_SECTION_WEIGHT_IME_FULL_WIDTH;
        splitTouchpadSection.setLayoutParams(tpLp);
        splitShortcutsReparentedForIme = true;
    }

    private void undockSplitShortcutsFromIme() {
        if (!splitShortcutsReparentedForIme
                || splitTopLeftFrame == null
                || splitTopRightFrame == null
                || splitImeShortcutsRow == null
                || splitLeftColumn == null
                || splitRightColumn == null) {
            return;
        }
        splitImeShortcutsRow.removeView(splitTopLeftFrame);
        splitImeShortcutsRow.removeView(splitTopRightFrame);
        LinearLayout.LayoutParams topLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 10f);
        splitLeftColumn.addView(splitTopLeftFrame, 0, topLp);
        splitRightColumn.addView(splitTopRightFrame, 0, topLp);
        splitImeShortcutsRow.setVisibility(View.GONE);
        splitLeftColumn.setVisibility(View.VISIBLE);
        splitRightColumn.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams tpLp =
                (LinearLayout.LayoutParams) splitTouchpadSection.getLayoutParams();
        tpLp.weight = SPLIT_TOUCHPAD_SECTION_WEIGHT_NORMAL;
        splitTouchpadSection.setLayoutParams(tpLp);
        splitShortcutsReparentedForIme = false;
    }

    private boolean isSplitLandscapeComposeSendMode() {
        if (splitRoot == null) {
            return false;
        }
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        CustomKeyboardView imeSource = keyboardViewRight != null ? keyboardViewRight : keyboardViewLeft;
        if (imeSource == null) {
            return false;
        }
        return imeSource.isSystemImeCaptureMode() && !imeSource.isImeSubComposeDirectHidMode();
    }

    private void applySplitComposeTouchpadStripRatios() {
        if (proTouchpadChromeRoot == null || touchpadPadHost == null || proTouchpadMouseKeys == null) {
            return;
        }
        if (splitToggleHandleView != null) {
            splitToggleHandleView.setVisibility(View.GONE);
        }
        ensureProTouchpadChromeSiblingOrder(false);
        proTouchpadChromeRoot.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.width = 0;
        padLp.weight = SPLIT_COMPOSE_TOUCHPAD_WEIGHT;
        touchpadPadHost.setLayoutParams(padLp);

        LinearLayout.LayoutParams mouseLp =
                (LinearLayout.LayoutParams) proTouchpadMouseKeys.getLayoutParams();
        mouseLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mouseLp.width = 0;
        mouseLp.weight = SPLIT_COMPOSE_MOUSE_KEYS_WEIGHT;
        proTouchpadMouseKeys.setLayoutParams(mouseLp);

        int topPad =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_top);
        int bottomPad =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_bottom);
        int sidePad =
                getResources()
                        .getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_strip_padding_horizontal);
        proTouchpadMouseKeys.setPaddingRelative(sidePad, topPad, sidePad, bottomPad);
        configureProMouseKeysRow(true);
        applyProTouchpadScrollStripLayout();
    }

    /**
     * Landscape split compose mode: hide shortcut panels, touchpad, and mouse keys; full width for
     * IME editor + vertical action rail.
     */
    private void applySplitLandscapeComposeRows() {
        if (!isSplitLandscapeComposeSendMode()
                || splitImeHost == null
                || splitImeEdit == null
                || splitRoot == null) {
            return;
        }
        ViewParent parent = splitRoot.getParent();
        if (!(parent instanceof LinearLayout)) {
            return;
        }
        LinearLayout outer = (LinearLayout) parent;
        LinearLayout.LayoutParams rootLp = (LinearLayout.LayoutParams) splitRoot.getLayoutParams();
        LinearLayout.LayoutParams imeLp = (LinearLayout.LayoutParams) splitImeHost.getLayoutParams();
        if (splitLandscapeComposeExpanded) {
            undockSplitShortcutsFromIme();
            splitRoot.setVisibility(View.GONE);
            rootLp.weight = 0f;
            imeLp.weight = 1f;
        } else {
            dockSplitShortcutsForIme();
            if (splitImeShortcutsRow != null) {
                // Folded compose keeps top area focused on touchpad; shortcut strips stay hidden.
                splitImeShortcutsRow.setVisibility(View.GONE);
            }
            applySplitComposeTouchpadStripRatios();
            splitRoot.setVisibility(View.VISIBLE);
            rootLp.weight = SPLIT_COMPOSE_FOLDED_OUTER_UPPER_WEIGHT;
            imeLp.weight = SPLIT_COMPOSE_FOLDED_OUTER_LOWER_WEIGHT;
        }
        splitImeHost.setVisibility(View.VISIBLE);
        if (splitLandscapeComposeExpanded && splitImeShortcutsRow != null) {
            splitImeShortcutsRow.setVisibility(View.GONE);
        }
        View textAreaHost = splitImeEditorRow != null ? splitImeEditorRow : splitImeEdit;
        LinearLayout.LayoutParams editLp = (LinearLayout.LayoutParams) textAreaHost.getLayoutParams();
        editLp.height = 0;
        editLp.weight = 1f;
        textAreaHost.setLayoutParams(editLp);
        splitRoot.setLayoutParams(rootLp);
        splitImeHost.setLayoutParams(imeLp);
        outer.requestLayout();
        applySplitImeComposeRailAdaptiveWidth();
        applySplitImeActionRailArrangement();
        updateSplitImeRailChromeVisibility();
        refreshSplitImeExpandToggleChrome();
    }

    @Nullable
    private View splitImeTextAreaHost() {
        if (splitImeEditHost != null) {
            return splitImeEditHost;
        }
        return splitImeEdit;
    }

    /**
     * With the software keyboard hidden (IME inset 0), move the vertical compose rail to the
     * physical end edge (right in LTR) so it clears the text area start. When the keyboard is
     * visible, keep the rail on the physical start edge (left in LTR). Order respects RTL.
     */
    private void applySplitImeComposeRailHorizontalOrder() {
        View textAreaHost = splitImeTextAreaHost();
        if (splitImeEditorRow == null || splitImeActionRail == null || textAreaHost == null) {
            return;
        }
        if (!isSplitLandscapeComposeSendMode()) {
            return;
        }
        boolean railOnPhysicalRight = !splitLandscapeImeVisible;
        boolean rtl = splitImeEditorRow.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        boolean wantRailFirst = railOnPhysicalRight == rtl;
        int railIndex = splitImeEditorRow.indexOfChild(splitImeActionRail);
        int editIndex = splitImeEditorRow.indexOfChild(textAreaHost);
        if (railIndex < 0 || editIndex < 0) {
            return;
        }
        boolean railIsFirst = railIndex < editIndex;
        if (railIsFirst == wantRailFirst) {
            return;
        }
        LinearLayout.LayoutParams railLp =
                (LinearLayout.LayoutParams) splitImeActionRail.getLayoutParams();
        LinearLayout.LayoutParams editLp =
                (LinearLayout.LayoutParams) textAreaHost.getLayoutParams();
        splitImeEditorRow.removeView(splitImeActionRail);
        splitImeEditorRow.removeView(textAreaHost);
        if (wantRailFirst) {
            splitImeEditorRow.addView(splitImeActionRail, railLp);
            splitImeEditorRow.addView(textAreaHost, editLp);
        } else {
            splitImeEditorRow.addView(textAreaHost, editLp);
            splitImeEditorRow.addView(splitImeActionRail, railLp);
        }
    }

    private void applySplitImeComposeRailAdaptiveWidth() {
        View textAreaHost = splitImeTextAreaHost();
        if (splitImeEditorRow == null || splitImeActionRail == null || textAreaHost == null) {
            return;
        }
        if (!isSplitLandscapeComposeSendMode()) {
            return;
        }
        applySplitImeComposeRailHorizontalOrder();

        int w = splitImeEditorRow.getWidth();
        if (w <= 0) {
            return;
        }
        int minRail;
        int maxRail;
        float target;
        if (splitLandscapeComposeExpanded) {
            minRail = getResources().getDimensionPixelSize(R.dimen.split_compose_ime_rail_min_width);
            maxRail = getResources().getDimensionPixelSize(R.dimen.split_compose_ime_rail_max_width);
            target = w / 10f;
        } else {
            minRail = dpToPx(SPLIT_COMPOSE_FOLDED_RAIL_MIN_DP);
            maxRail = dpToPx(SPLIT_COMPOSE_FOLDED_RAIL_MAX_DP);
            target = w / 4.5f;
        }
        int railPx = Math.max(minRail, Math.min(maxRail, Math.round(target)));

        LinearLayout.LayoutParams railLp =
                (LinearLayout.LayoutParams) splitImeActionRail.getLayoutParams();
        railLp.width = railPx;
        railLp.weight = 0f;
        splitImeActionRail.setLayoutParams(railLp);
        syncFoldedComposeMouseStripWidthWithRail(railPx);

        LinearLayout.LayoutParams editLp = (LinearLayout.LayoutParams) textAreaHost.getLayoutParams();
        editLp.width = 0;
        editLp.weight = 1f;
        textAreaHost.setLayoutParams(editLp);
    }

    /**
     * Folded landscape compose: keep touchpad mouse-key strip width aligned with the compose action rail
     * so the right-side chrome reads as one consistent column.
     */
    private void syncFoldedComposeMouseStripWidthWithRail(int railPx) {
        if (splitLandscapeComposeExpanded || proTouchpadMouseKeys == null || touchpadPadHost == null) {
            return;
        }
        LinearLayout.LayoutParams mouseLp =
                (LinearLayout.LayoutParams) proTouchpadMouseKeys.getLayoutParams();
        mouseLp.width = railPx;
        mouseLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mouseLp.weight = 0f;
        proTouchpadMouseKeys.setLayoutParams(mouseLp);

        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.width = 0;
        padLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.weight = 1f;
        touchpadPadHost.setLayoutParams(padLp);
    }

    private void applySplitImeActionRailArrangement() {
        if (splitImeActionRail == null) {
            return;
        }
        splitImeActionRail.removeAllViews();
        if (splitLandscapeComposeExpanded) {
            addRailButtonsSingleColumn();
        } else {
            addRailButtonsTwoColumn();
        }
    }

    private void addRailButtonsSingleColumn() {
        addRailButtonSingleColumn(splitImeRailToggle, false);
        addRailButtonSingleColumn(splitImeRailUndo, true);
        addRailButtonSingleColumn(splitImeRailClear, true);
        addRailButtonSingleColumn(splitImeRailSaved, true);
        addRailButtonSingleColumn(splitImeRailSend, true);
    }

    private void addRailButtonsTwoColumn() {
        addRailButtonsPairRow(splitImeRailToggle, splitImeRailUndo, false);
        addRailButtonsPairRow(splitImeRailClear, splitImeRailSaved, true);
        addRailButtonsSendRow(splitImeRailSend, true);
    }

    private void addRailButtonsPairRow(@Nullable View left, @Nullable View right, boolean addTopGap) {
        if (splitImeActionRail == null || (left == null && right == null)) {
            return;
        }
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        if (addTopGap) {
            rowLp.topMargin = dpToPx(SPLIT_COMPOSE_FOLDED_BUTTON_GAP_DP);
        }
        row.setLayoutParams(rowLp);
        if (left != null) {
            detachFromParent(left);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginEnd(dpToPx(SPLIT_COMPOSE_FOLDED_BUTTON_GAP_DP));
            row.addView(left, lp);
        }
        if (right != null) {
            detachFromParent(right);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginStart(dpToPx(SPLIT_COMPOSE_FOLDED_BUTTON_GAP_DP));
            row.addView(right, lp);
        }
        splitImeActionRail.addView(row);
    }

    private void addRailButtonsSendRow(@Nullable View send, boolean addTopGap) {
        if (splitImeActionRail == null || send == null) {
            return;
        }
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f);
        if (addTopGap) {
            rowLp.topMargin = dpToPx(SPLIT_COMPOSE_FOLDED_BUTTON_GAP_DP);
        }
        row.setLayoutParams(rowLp);
        detachFromParent(send);
        row.addView(send, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splitImeActionRail.addView(row);
    }

    private void addRailButtonSingleColumn(@Nullable View button, boolean addTopGap) {
        if (splitImeActionRail == null || button == null) {
            return;
        }
        detachFromParent(button);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        if (addTopGap) {
            lp.topMargin = dpToPx(4);
        }
        splitImeActionRail.addView(button, lp);
    }

    private void detachFromParent(@NonNull View child) {
        ViewParent parent = child.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(child);
        }
    }

    @Nullable
    private CustomKeyboardView splitImeKeyboardSource() {
        return keyboardViewRight != null ? keyboardViewRight : keyboardViewLeft;
    }

    private void clearSplitImeComposeRailBindingAll() {
        if (keyboardViewLeft != null) {
            keyboardViewLeft.clearSplitLandscapeImeComposeRail();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.clearSplitLandscapeImeComposeRail();
        }
    }

    private void refreshSplitLandscapeImeComposeRailBinding() {
        clearSplitImeComposeRailBindingAll();
        boolean canBind =
                isAdded()
                        && splitImeEdit != null
                        && splitImeRailToggle != null
                        && splitRoot != null
                        && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        CustomKeyboardView source = splitImeKeyboardSource();
        if (canBind
                && source != null
                && source.isSystemImeCaptureMode()
                && !source.isImeSubComposeDirectHidMode()) {
            source.bindSplitLandscapeImeComposeRail(
                    splitImeEdit,
                    splitImeRailToggle,
                    splitImeRailUndo,
                    splitImeRailClear,
                    splitImeRailSaved,
                    splitImeRailSend);
        }
        updateSplitImeRailChromeVisibility();
    }

    private void updateSplitImeRailChromeVisibility() {
        if (splitImeActionRail == null) {
            return;
        }
        boolean show =
                splitImeHost != null
                        && splitImeHost.getVisibility() == View.VISIBLE
                        && isSplitLandscapeComposeSendMode();
        splitImeActionRail.setVisibility(show ? View.VISIBLE : View.GONE);
        if (splitImeExpandToggle != null) {
            splitImeExpandToggle.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshSplitImeExpandToggleChrome() {
        if (splitImeExpandToggle == null || !isAdded()) {
            return;
        }
        if (splitLandscapeComposeExpanded) {
            splitImeExpandToggle.setImageResource(R.drawable.ic_ime_sub_compose_collapse_24);
            splitImeExpandToggle.setContentDescription(getString(R.string.ime_sub_compose_collapse));
        } else {
            splitImeExpandToggle.setImageResource(R.drawable.ic_ime_sub_compose_expand_24);
            splitImeExpandToggle.setContentDescription(getString(R.string.ime_sub_compose_expand));
        }
    }

    private void onSplitLandscapeComposeExpandToggleClicked() {
        if (!isSplitLandscapeComposeSendMode()) {
            return;
        }
        splitLandscapeComposeExpanded = !splitLandscapeComposeExpanded;
        applySplitLandscapeComposeRows();
        requestCompositeImeInsetsAfterImeChange();
    }

    private void removeSplitImeComposeRailLayoutListener() {
        if (splitImeEditorRow != null) {
            splitImeEditorRow.removeOnLayoutChangeListener(splitImeComposeRailWidthListener);
        }
    }

    private void applySplitImeLayout(boolean imeMode) {
        if (splitImeHost == null || splitImeEdit == null || splitTouchpadSection == null || splitRoot == null) {
            return;
        }
        ViewParent parent = splitRoot.getParent();
        if (!(parent instanceof LinearLayout)) {
            return;
        }
        LinearLayout outer = (LinearLayout) parent;
        LinearLayout.LayoutParams rootLp = (LinearLayout.LayoutParams) splitRoot.getLayoutParams();
        LinearLayout.LayoutParams imeLp = (LinearLayout.LayoutParams) splitImeHost.getLayoutParams();
        LinearLayout.LayoutParams touchLp = (LinearLayout.LayoutParams) splitTouchpadSection.getLayoutParams();
        boolean handledByComposeRows = false;
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imeMode) {
            if (isSplitLandscapeComposeSendMode()) {
                undockSplitShortcutsFromIme();
                applySplitLandscapeComposeRows();
                handledByComposeRows = true;
            } else {
                splitLandscapeComposeExpanded = true;
                if (splitToggleHandleView != null) {
                    splitToggleHandleView.setVisibility(View.VISIBLE);
                }
                dockSplitShortcutsForIme();
                rootLp.weight = SPLIT_IME_OUTER_UPPER_WEIGHT;
                imeLp.weight = SPLIT_IME_OUTER_LOWER_WEIGHT;
                splitRoot.setVisibility(View.VISIBLE);
                splitImeHost.setVisibility(View.VISIBLE);
                applySplitImeHostInnerWeights();
            }
            if (splitImeHost instanceof ViewGroup) {
                ((ViewGroup) splitImeHost).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            splitImeEdit.setFocusable(true);
            splitImeEdit.setFocusableInTouchMode(true);
            splitImeEdit.setClickable(true);
            ImeTextForwarder.detach(splitImeEdit);
            ImeTextForwarder.attach(
                    splitImeEdit,
                    this::getConnectionManagerForIme,
                    this::getTargetOsForIme,
                    imeSplitTextExecutor);
            splitImeEdit.post(() -> {
                splitImeEdit.requestFocus();
                if (imm != null) {
                    imm.showSoftInput(splitImeEdit, InputMethodManager.SHOW_IMPLICIT);
                }
                splitImeEdit.post(() -> {
                    if (imm != null && splitImeEdit != null) {
                        imm.showSoftInput(splitImeEdit, InputMethodManager.SHOW_IMPLICIT);
                    }
                    requestCompositeImeInsetsAfterImeChange();
                });
            });
            requestCompositeImeInsetsAfterImeChange();
            if (isSplitLandscapeComposeSendMode()) {
                refreshSplitLandscapeImeComposeRailBinding();
            }
        } else {
            splitLandscapeComposeExpanded = true;
            undockSplitShortcutsFromIme();
            if (splitToggleHandleView != null) {
                splitToggleHandleView.setVisibility(View.VISIBLE);
            }
            ImeTextForwarder.detach(splitImeEdit);
            splitImeEdit.setText("");
            imeLp.weight = 0f;
            splitImeHost.setVisibility(View.GONE);
            splitRoot.setVisibility(View.VISIBLE);
            touchLp.weight = SPLIT_TOUCHPAD_SECTION_WEIGHT_NORMAL;
            rootLp.weight = 1f;
            if (imm != null) {
                imm.hideSoftInputFromWindow(splitImeEdit.getWindowToken(), 0);
            }
            requestCompositeImeInsetsAfterImeChange();
        }
        if (!handledByComposeRows) {
            splitRoot.setLayoutParams(rootLp);
            splitImeHost.setLayoutParams(imeLp);
            splitTouchpadSection.setLayoutParams(touchLp);
            setSplitKeyboardColumnWeight(imeMode ? 0f : 22f);
            outer.requestLayout();
            refreshSplitImeExpandToggleChrome();
        }
        updateSplitImeRailChromeVisibility();
    }

    private void setSplitKeyboardColumnWeight(float keyboardWeight) {
        if (keyboardViewLeft != null) {
            LinearLayout.LayoutParams lpL = (LinearLayout.LayoutParams) keyboardViewLeft.getLayoutParams();
            lpL.weight = keyboardWeight;
            keyboardViewLeft.setLayoutParams(lpL);
        }
        if (keyboardViewRight != null) {
            LinearLayout.LayoutParams lpR = (LinearLayout.LayoutParams) keyboardViewRight.getLayoutParams();
            lpR.weight = keyboardWeight;
            keyboardViewRight.setLayoutParams(lpR);
        }
    }

    @Nullable
    private ConnectionManager getConnectionManagerForIme() {
        if (!(requireActivity() instanceof MainActivity)) {
            return null;
        }
        return ((MainActivity) requireActivity()).getConnectionManager();
    }

    private String getTargetOsForIme() {
        if (!(requireActivity() instanceof MainActivity)) {
            return "macos";
        }
        return ((MainActivity) requireActivity()).getTargetOs();
    }

    private void syncSplitImeChromeFromPrefs() {
        if (!isAdded() || keyboardViewRight == null) {
            return;
        }
        if (keyboardViewRight.isSystemImeCaptureMode()) {
            applySplitImeLayout(true);
        }
        applyTouchpadInfoVisibility();
        refreshSplitLandscapeImeComposeRailBinding();
    }

    private void syncNormalImeChromeFromPrefs() {
        if (!isAdded()) {
            return;
        }
        applyTouchpadInfoVisibility();
    }

    private void updateSplitTouchPadTips() {
        if (splitTouchPadTips == null) {
            return;
        }
        if (KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(requireContext())) {
            splitTouchPadTips.setVisibility(View.GONE);
            return;
        }
        splitTouchPadTips.setVisibility(View.VISIBLE);
        splitTouchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    private void notePointerPhase(TouchPadPointerPhase phase) {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = phase;
        updateTouchPadTips();
        updateSplitTouchPadTips();
        if (phase != TouchPadPointerPhase.IDLE) {
            tipHandler.postDelayed(pointerIdleRunnable, POINTER_IDLE_AFTER_MS);
        }
    }

    private void clearPointerPhaseForFingerUp() {
        tipHandler.removeCallbacks(pointerIdleRunnable);
        pointerPhase = TouchPadPointerPhase.IDLE;
        updateTouchPadTips();
        updateSplitTouchPadTips();
    }

    private void sendMouseButtonState(int buttonMask) {
        new Thread(() -> {
            try {
                String buttonByte = String.format("%02X", buttonMask & 0xFF);
                String sendMSData =
                        CH9329MSKBMap.getKeyCodeMap().get("prefix1") +
                        CH9329MSKBMap.getKeyCodeMap().get("prefix2") +
                        CH9329MSKBMap.getKeyCodeMap().get("address") +
                        CH9329MSKBMap.CmdData().get("CmdMS_REL") +
                        CH9329MSKBMap.DataLen().get("DataLenRelMS") +
                        CH9329MSKBMap.MSRelData().get("FirstData") +
                        buttonByte + "00" + "00" + "00";
                sendMSData += makeChecksum(sendMSData);
                byte[] bytes = hexStringToByteArray(sendMSData);
                if (isServiceBound && bluetoothService != null && bluetoothService.isConnected()) {
                    bluetoothService.sendData(bytes);
                } else if (port != null) {
                    port.write(bytes, 20);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending mouse button state: " + e.getMessage());
            }
        }).start();
    }

    public void sendHexRelData(float StartMoveMSX, float StartMoveMSY, float LastMoveMSX, float LastMoveMSY) {
        int mask = snapshotProRelMoveButtonMask();
        MouseRelHidTransport.sendRelMove(
                port,
                bluetoothService,
                isServiceBound,
                mask,
                StartMoveMSX,
                StartMoveMSY,
                LastMoveMSX,
                LastMoveMSY);
    }

    /** Send a scroll-wheel packet. deltaY>0 scrolls up, deltaY<0 scrolls down (natural). */
    public void sendScrollData(int deltaX, int deltaY) {
        int mask = snapshotProRelMoveButtonMask();
        MouseRelHidTransport.sendScroll(port, bluetoothService, isServiceBound, deltaX, deltaY, mask);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Bind to BluetoothService
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        initTouchPadWashStyle();

        // Create container to swap between normal and split layouts
        contentContainer = new FrameLayout(requireContext());
        contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Initially inflate normal layout
        View normalView = inflater.inflate(R.layout.fragment_composite, contentContainer, false);
        contentContainer.addView(normalView);

        setupNormalViews(normalView);
        applyOrientationLayout();
        applyDisplayMode();

        if (toggleHandle != null) {
            toggleHandle.setOnClickListener(v -> cycleDisplayMode());
        }

        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        setupTouchPad(touchPad, touchPadTips, touchPadInfoButton);

        // Register keyboard view for OS change updates
        registerKeyboardOsListener(keyboardView);
        registerTopModeShortcutListener(keyboardView);
        registerImeCaptureListener(keyboardView);
        registerImeSubComposeChromeListener(keyboardView);
        if (keyboardView != null) {
            keyboardView.post(this::syncNormalImeChromeFromPrefs);
        }
        bindProHoldLockControllerToKeyboardViews();

        if (savedInstanceState == null && touchPad != null) {
            touchPad.post(
                    () -> {
                        if (isPortraitNumpadTouchpadMode()) {
                            return;
                        }
                        if (KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(
                                requireContext())) {
                            return;
                        }
                        TouchPadHelpOverlay.show(helpOverlayForPad(touchPad), true, true);
                    });
        }

        return contentContainer;
    }

    @Override
    public void onDestroyView() {
        if (touchpadSection != null) {
            touchpadSection.removeOnLayoutChangeListener(proTouchpadSectionLayoutListener);
        }
        removeSplitImeComposeRailLayoutListener();
        clearSplitImeComposeRailBindingAll();
        detachProMouseStripBinder();
        detachProHoldLockControllerFromKeyboardViews();
        proHoldLockController.clearAllAndReleaseHid(port, bluetoothService, isServiceBound);
        if (imePopOutTouchPad != null) {
            imePopOutTouchPad.dismissIfShowing();
            imePopOutTouchPad = null;
        }
        imeSubComposeChromeSnapshotValid = false;
        undockSplitShortcutsFromIme();
        ImeTextForwarder.detach(splitImeEdit);
        tipHandler.removeCallbacks(pointerIdleRunnable);
        cancelHybridDoubleLeftSecondFlash();
        cancelTouchPadButtonPulseAnimation();
        if (touchPadBottomWashOverlay != null) {
            ViewParent parent = touchPadBottomWashOverlay.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(touchPadBottomWashOverlay);
            }
            touchPadBottomWashOverlay = null;
        }
        if (splitTouchPadBottomWashOverlay != null) {
            ViewParent parent = splitTouchPadBottomWashOverlay.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(splitTouchPadBottomWashOverlay);
            }
            splitTouchPadBottomWashOverlay = null;
        }
        super.onDestroyView();
        if (requireActivity() instanceof MainActivity) {
            for (MainActivity.OnTargetOsChangeListener listener : osChangeListeners) {
                ((MainActivity) requireActivity()).removeOsChangeListener(listener);
            }
            osChangeListeners.clear();
        }
        setDragMode(false);
        TouchPadHelpOverlay.clear(touchPadHelpOverlay);
        TouchPadHelpOverlay.clear(splitTouchPadHelpOverlay);
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }
    private void registerKeyboardOsListener(CustomKeyboardView kbdView) {
        if (kbdView == null || !(requireActivity() instanceof MainActivity)) return;
        MainActivity.OnTargetOsChangeListener listener = os -> {
            kbdView.reloadForTargetOs();
        };
        osChangeListeners.add(listener);
        ((MainActivity) requireActivity()).addOsChangeListener(listener);
    }

    private void registerTopModeShortcutListener(CustomKeyboardView kbdView) {
        if (kbdView == null || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        kbdView.setOnTopModeShortcutListener(mode -> ((MainActivity) requireActivity()).switchToLaunchMode(mode));
    }

    private void setupNormalViews(View view) {
        rootLayout = view.findViewById(R.id.composite_root);
        setupCompositeImeRootInsets(rootLayout);
        keyboardView = view.findViewById(R.id.keyboard_view);
        touchPad = view.findViewById(R.id.touchPad);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        proTouchpadChromeRoot = view.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseKeys = view.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = view.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = view.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = view.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = view.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = view.findViewById(R.id.pro_touchpad_btn_right);
        toggleHandle = view.findViewById(R.id.toggle_handle);
        toggleHandlePill = view.findViewById(R.id.toggle_handle_pill);
        touchPadTips = view.findViewById(R.id.touchPadTips);
        touchPadHelpOverlay = view.findViewById(R.id.touchPadHelpOverlay);
        touchPadInfoButton = view.findViewById(R.id.touchPadInfo);
        setupBottomWashOverlays();
        updateTouchPadTips();
        registerProTouchpadSectionLayoutListener();
        touchpadSection.post(CompositeFragment.this::refreshProTouchpadChromeFromKmProSetup);
    }

    private void setupSplitViews(View view) {
        splitLayoutRoot = view;
        setupCompositeImeRootInsets(view);
        splitRoot = view.findViewById(R.id.split_root);
        View topPanelContainer = view.findViewById(R.id.split_top_panel);
        splitTopLeftFrame = view.findViewById(R.id.split_top_left);
        splitTopRightFrame = view.findViewById(R.id.split_top_right);
        splitLeftColumn = view.findViewById(R.id.split_left_column);
        splitRightColumn = view.findViewById(R.id.split_right_column);
        splitImeShortcutsRow = view.findViewById(R.id.split_ime_shortcuts_row);
        splitShortcutsReparentedForIme = false;
        keyboardViewLeft = view.findViewById(R.id.keyboard_view_left);
        keyboardViewRight = view.findViewById(R.id.keyboard_view_right);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        splitTouchpadSection = touchpadSection;
        splitTouchPad = view.findViewById(R.id.touchPad);
        splitTouchPadTips = view.findViewById(R.id.touchPadTips);
        splitTouchPadHelpOverlay = view.findViewById(R.id.touchPadHelpOverlay);
        View splitToggleHandle = view.findViewById(R.id.toggle_handle);
        splitToggleHandleView = splitToggleHandle;
        setupBottomWashOverlays();

        if (splitTouchPadTips != null) {
            updateSplitTouchPadTips();
        }

        if (splitToggleHandle != null) {
            splitToggleHandle.setOnClickListener(v -> cycleDisplayMode());
        }

        // Suppress top panels inside each keyboard half by setting split part
        if (keyboardViewLeft != null) {
            keyboardViewLeft.setPort(port);
            keyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
            // Link to right keyboard for modifier state syncing
            if (keyboardViewRight != null) {
                keyboardViewLeft.setSplitPartner(keyboardViewRight);
            }
            // Register for OS change updates
            registerKeyboardOsListener(keyboardViewLeft);
            registerTopModeShortcutListener(keyboardViewLeft);
            // Create shared top panel from the left keyboard
            if (splitTopLeftFrame != null && splitTopRightFrame != null) {
                keyboardViewLeft.createSplitLandscapeTopPanel(
                        splitTopLeftFrame,
                        null,
                        splitTopRightFrame
                );
            } else if (topPanelContainer instanceof FrameLayout) {
                View topPanel = keyboardViewLeft.createTopPanel();
                if (topPanel != null) {
                    ((FrameLayout) topPanelContainer).addView(topPanel);
                }
            }
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.setPort(port);
            keyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
            // Link to left keyboard for modifier state syncing
            if (keyboardViewLeft != null) {
                keyboardViewRight.setSplitPartner(keyboardViewLeft);
            }
            // Register for OS change updates
            registerKeyboardOsListener(keyboardViewRight);
            registerTopModeShortcutListener(keyboardViewRight);
        }
        bindProHoldLockControllerToKeyboardViews();

        splitImeHost = view.findViewById(R.id.composite_split_ime_host);
        splitImeEditorRow = view.findViewById(R.id.composite_split_ime_editor_row);
        splitImeActionRail = view.findViewById(R.id.composite_split_ime_action_rail);
        splitImeEditHost = view.findViewById(R.id.composite_split_ime_edit_host);
        splitImeEdit = view.findViewById(R.id.composite_split_ime_edit);
        splitImeExpandToggle = view.findViewById(R.id.composite_split_ime_expand_toggle);
        splitImeRailToggle = view.findViewById(R.id.composite_split_ime_rail_toggle);
        splitImeRailUndo = view.findViewById(R.id.composite_split_ime_rail_undo);
        splitImeRailClear = view.findViewById(R.id.composite_split_ime_rail_clear);
        splitImeRailSaved = view.findViewById(R.id.composite_split_ime_rail_saved);
        splitImeRailSend = view.findViewById(R.id.composite_split_ime_rail_send);
        if (splitImeEditorRow != null) {
            splitImeEditorRow.addOnLayoutChangeListener(splitImeComposeRailWidthListener);
        }
        CustomKeyboardView splitImeChromeSource = splitImeKeyboardSource();
        if (splitImeChromeSource != null) {
            registerImeSubComposeChromeListener(splitImeChromeSource);
        }
        if (splitImeRailToggle != null && splitImeChromeSource != null) {
            splitImeRailToggle.setOnClickListener(v -> splitImeChromeSource.onSplitLandscapeImeRailModeToggleClicked());
        }
        if (splitImeRailUndo != null && splitImeChromeSource != null) {
            splitImeRailUndo.setOnClickListener(v -> splitImeChromeSource.onSplitLandscapeImeRailUndoClicked());
        }
        if (splitImeRailClear != null && splitImeChromeSource != null) {
            splitImeRailClear.setOnClickListener(v -> splitImeChromeSource.onSplitLandscapeImeRailClearClicked());
        }
        if (splitImeRailSaved != null && splitImeChromeSource != null) {
            splitImeRailSaved.setOnClickListener(v -> splitImeChromeSource.onSplitLandscapeImeRailSavedClicked());
        }
        if (splitImeRailSend != null && splitImeChromeSource != null) {
            splitImeRailSend.setOnClickListener(v -> splitImeChromeSource.onSplitLandscapeImeRailSendClicked());
        }
        if (splitImeExpandToggle != null) {
            splitImeExpandToggle.setOnClickListener(v -> onSplitLandscapeComposeExpandToggleClicked());
        }
        refreshSplitImeExpandToggleChrome();
        splitTouchPadInfoButton = view.findViewById(R.id.touchPadInfo);
        proTouchpadChromeRoot = view.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseKeys = view.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = view.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = view.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = view.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = view.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = view.findViewById(R.id.pro_touchpad_btn_right);
        setupTouchPad(splitTouchPad, splitTouchPadTips, splitTouchPadInfoButton);
        registerProTouchpadSectionLayoutListener();
        registerImeCaptureListener(keyboardViewLeft);
        registerImeCaptureListener(keyboardViewRight);
        if (keyboardViewRight != null) {
            keyboardViewRight.post(this::syncSplitImeChromeFromPrefs);
        }
        if (touchpadSection != null) {
            touchpadSection.post(CompositeFragment.this::refreshProTouchpadChromeFromKmProSetup);
        }
        refreshSplitLandscapeImeComposeRailBinding();
    }

    private TextView helpOverlayForPad(TouchPadView pad) {
        if (pad != null && pad == splitTouchPad) {
            return splitTouchPadHelpOverlay;
        }
        return touchPadHelpOverlay;
    }

    private void setupTouchPad(TouchPadView pad, TextView tips, View infoButton) {
        if (pad == null) return;
        pad.setPadClickDragGesturesEnabled(
                !KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(pad.getContext()));
        if (infoButton != null) {
            infoButton.setOnClickListener(
                    v -> TouchPadHelpOverlay.onInfoPressed(helpOverlayForPad(pad), true, true));
        }
        pad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float startX, float startY, float lastX, float lastY) {
                if (lastX == 0 && lastY == 0) {
                    notePointerPhase(TouchPadPointerPhase.SCROLL);
                    sendScrollData((int) startX, (int) startY);
                } else {
                    notePointerPhase(TouchPadPointerPhase.MOVE);
                    sendHexRelData(startX, startY, lastX, lastY);
                }
            }

            @Override
            public void onTouchClick() {
                if (isDragMode) {
                    setDragMode(false);
                    return;
                }
                if (KmProTouchpadPrefs.showsMouseKeyStrip(pad.getContext())
                        && (stripAndLockMaskWithoutGestureDrag() & TouchpadMouseStripBinder.BTN_LEFT) != 0) {
                    return;
                }
                pulseTouchPadButtonVisual();
                pulseProMouseKeyHybrid(TouchpadMouseStripBinder.BTN_LEFT);
                TouchPadHaptics.onLeftClick(pad.getContext());
                MouseRelHidTransport.sendLeftClick(port, bluetoothService, isServiceBound);
                scheduleReassertStripAndLockMouseButtons(50);
            }

            @Override
            public void onTouchDoubleClick() {
                if (isDragMode) {
                    setDragMode(false);
                    return;
                }
                if (KmProTouchpadPrefs.showsMouseKeyStrip(pad.getContext())
                        && (stripAndLockMaskWithoutGestureDrag() & TouchpadMouseStripBinder.BTN_LEFT) != 0) {
                    return;
                }
                pulseTouchPadButtonVisual();
                cancelHybridDoubleLeftSecondFlash();
                pulseProMouseKeyHybrid(TouchpadMouseStripBinder.BTN_LEFT);
                hybridDoubleLeftSecondFlashRunnable =
                        () -> {
                            hybridDoubleLeftSecondFlashRunnable = null;
                            pulseProMouseKeyHybrid(TouchpadMouseStripBinder.BTN_LEFT);
                        };
                tipHandler.postDelayed(
                        hybridDoubleLeftSecondFlashRunnable,
                        HYBRID_DOUBLE_LEFT_SECOND_FLASH_DELAY_MS);
                TouchPadHaptics.onDoubleClick(pad.getContext());
                MouseRelHidTransport.sendDoubleClick(port, bluetoothService, isServiceBound);
                scheduleReassertStripAndLockMouseButtons(120);
            }

            @Override
            public void onTouchRightClick() {
                if (KmProTouchpadPrefs.showsMouseKeyStrip(pad.getContext())
                        && (stripAndLockMaskWithoutGestureDrag() & TouchpadMouseStripBinder.BTN_RIGHT) != 0) {
                    return;
                }
                pulseTouchPadButtonVisual();
                pulseProMouseKeyHybrid(TouchpadMouseStripBinder.BTN_RIGHT);
                TouchPadHaptics.onRightClick(pad.getContext());
                MouseRelHidTransport.sendRightClick(port, bluetoothService, isServiceBound);
                scheduleReassertStripAndLockMouseButtons(50);
            }

            @Override
            public void onTouchLongPress() {
                if (isDragMode) {
                    return;
                }
                TouchPadHaptics.onDragToggle(pad.getContext());
                setDragMode(true);
            }

            @Override
            public void onTouchRelease() {
                clearPointerPhaseForFingerUp();
                if (isDragMode) {
                    return;
                }
                int held = stripAndLockMaskWithoutGestureDrag();
                if (held == 0) {
                    releaseAllMSData();
                } else if (KmProTouchpadPrefs.showsMouseKeyStrip(pad.getContext())) {
                    MouseRelHidTransport.sendRelButtonsNoMotion(
                            port, bluetoothService, isServiceBound, held);
                }
            }
        });
        TextView helpOverlay = helpOverlayForPad(pad);
        TouchPadHelpOverlay.wireDismissTouchTargets(pad, tips, helpOverlay, proTouchpadScrollStrip);
        View padParent = (View) pad.getParent();
        if (padParent != null) {
            View brand = padParent.findViewById(R.id.touchPadBrandLogo);
            if (brand != null) {
                brand.setOnTouchListener(
                        (v, e) -> {
                            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                TouchPadHelpOverlay.dismissIfVisible(helpOverlay);
                            }
                            return false;
                        });
            }
        }
    }

    private void cycleDisplayMode() {
        normalizeDisplayModeForOrientation();
        boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait) {
            switch (displayMode) {
                case BOTH:
                    displayMode = DisplayMode.KEYBOARD;
                    break;
                case KEYBOARD:
                case TOUCHPAD:
                case SPLIT:
                    displayMode = DisplayMode.BOTH;
                    break;
            }
        } else {
            switch (displayMode) {
                case KEYBOARD:
                    displayMode = DisplayMode.SPLIT;
                    break;
                case SPLIT:
                    displayMode = DisplayMode.KEYBOARD;
                    break;
                case BOTH:
                case TOUCHPAD:
                default:
                    displayMode = DisplayMode.SPLIT;
                    break;
            }
        }
        applyDisplayMode();
    }

    /**
     * Landscape Keyboard & Mouse: only keyboard-only and split layouts are offered; coerce legacy
     * BOTH/TOUCHPAD to SPLIT.
     */
    private void normalizeDisplayModeForOrientation() {
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        if (displayMode == DisplayMode.BOTH || displayMode == DisplayMode.TOUCHPAD) {
            displayMode = DisplayMode.SPLIT;
        }
    }

    private void applyDisplayMode() {
        normalizeDisplayModeForOrientation();
        boolean isInSplit = displayMode == DisplayMode.SPLIT;

        if (isInSplit) {
            ensureSplitLayout();
            return;
        }

        // Ensure we have the normal layout
        ensureNormalLayout();

        boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (touchpadSection != null) {
            // Portrait keyboard-only (numpad): show touchpad above grid; landscape KEYBOARD stays touchpad-off.
            boolean showTouchpad =
                    (isPortrait || displayMode != DisplayMode.KEYBOARD)
                            && displayMode != DisplayMode.TOUCHPAD;
            touchpadSection.setVisibility(showTouchpad ? View.VISIBLE : View.GONE);
        }
        if (keyboardView != null) {
            keyboardView.setVisibility(
                displayMode != DisplayMode.TOUCHPAD ? View.VISIBLE : View.GONE);

            keyboardView.reloadForCurrentOrientation();
            keyboardView.setShowExtraPortraitKeys(displayMode == DisplayMode.KEYBOARD);
        }

        applyPortraitNumpadTouchpadChrome();
    }

    private void ensureSplitLayout() {
        if (splitRoot == null) {
            TouchPadHelpOverlay.clear(touchPadHelpOverlay);
            touchPadHelpOverlay = null;
            View normal = contentContainer.getChildAt(0);
            if (normal != null) {
                contentContainer.removeView(normal);
            }
            View splitView = LayoutInflater.from(requireContext()).inflate(
                    R.layout.fragment_composite_split, contentContainer, false);
            contentContainer.addView(splitView);
            setupSplitViews(splitView);
        }

        // Update split keyboard views
        if (keyboardViewLeft != null) {
            keyboardViewLeft.reloadForCurrentOrientation();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.reloadForCurrentOrientation();
        }
    }

    private void ensureNormalLayout() {
        if (splitRoot != null) {
            imeSubComposeChromeSnapshotValid = false;
            undockSplitShortcutsFromIme();
            removeSplitImeComposeRailLayoutListener();
            clearSplitImeComposeRailBindingAll();
            if (splitImeEdit != null) {
                ImeTextForwarder.detach(splitImeEdit);
            }
            detachProMouseStripBinder();
            if (touchpadSection != null) {
                touchpadSection.removeOnLayoutChangeListener(proTouchpadSectionLayoutListener);
            }
            TouchPadHelpOverlay.clear(splitTouchPadHelpOverlay);
            splitTouchPadHelpOverlay = null;
            View split = contentContainer.getChildAt(0);
            if (split != null) {
                contentContainer.removeView(split);
            }
            splitRoot = null;
            keyboardViewLeft = null;
            keyboardViewRight = null;
            splitImeHost = null;
            splitImeEdit = null;
            splitImeEditHost = null;
            splitImeEditorRow = null;
            splitImeActionRail = null;
            splitImeRailToggle = null;
            splitImeRailUndo = null;
            splitImeRailClear = null;
            splitImeRailSaved = null;
            splitImeRailSend = null;
            splitImeExpandToggle = null;
            splitLeftColumn = null;
            splitRightColumn = null;
            splitTopLeftFrame = null;
            splitTopRightFrame = null;
            splitImeShortcutsRow = null;
            splitShortcutsReparentedForIme = false;
            splitLayoutRoot = null;
            splitTouchPadInfoButton = null;
            splitTouchpadSection = null;
            splitToggleHandleView = null;

            View normalView = LayoutInflater.from(requireContext()).inflate(
                    R.layout.fragment_composite, contentContainer, false);
            contentContainer.addView(normalView);
            setupNormalViews(normalView);
            if (toggleHandle != null) {
                toggleHandle.setOnClickListener(v -> cycleDisplayMode());
            }
            if (keyboardView != null && port != null) {
                keyboardView.setPort(port);
            }
            registerTopModeShortcutListener(keyboardView);
            registerImeCaptureListener(keyboardView);
            registerImeSubComposeChromeListener(keyboardView);
            setupTouchPad(touchPad, touchPadTips, touchPadInfoButton);
            if (keyboardView != null) {
                keyboardView.post(this::syncNormalImeChromeFromPrefs);
            }
            bindProHoldLockControllerToKeyboardViews();
        }
        applyOrientationLayout();
    }

    private void applyOrientationLayout() {
        if (splitRoot != null) {
            return;
        }
        if (rootLayout == null || touchpadSection == null || toggleHandle == null || keyboardView == null) {
            return;
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            rootLayout.setOrientation(LinearLayout.HORIZONTAL);

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    getResources().getDimensionPixelSize(R.dimen.toggle_handle_width_landscape),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 2.0f));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), dpToPx(40)));
            }
        } else {
            rootLayout.setOrientation(LinearLayout.VERTICAL);

            // Portrait numpad + touchpad: touchpad : numpad (keyboard strip) = 1 : 4.
            // Portrait BOTH (full keyboard): default 1.5 : 1.0; IME capture collapsed sub-compose uses
            // PORTRAIT_IME_SUB_COMPOSE_COLLAPSED_* so the strip + editor row get more vertical space.
            float touchpadWeight = displayMode == DisplayMode.KEYBOARD ? 1f : 1.5f;
            float keyboardWeight = displayMode == DisplayMode.KEYBOARD ? 4f : 1.0f;
            if (displayMode != DisplayMode.KEYBOARD
                    && keyboardView.isSystemImeCaptureMode()
                    && !keyboardView.isImeSubComposeExpanded()) {
                if (keyboardView.isImeSubComposeDirectHidMode()) {
                    touchpadWeight = PORTRAIT_IME_DIRECT_HID_TOUCHPAD_WEIGHT;
                    keyboardWeight = PORTRAIT_IME_DIRECT_HID_KEYBOARD_WEIGHT;
                } else {
                    touchpadWeight = PORTRAIT_IME_SUB_COMPOSE_COLLAPSED_TOUCHPAD_WEIGHT;
                    keyboardWeight = PORTRAIT_IME_SUB_COMPOSE_COLLAPSED_KEYBOARD_WEIGHT;
                }
            }

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, touchpadWeight));

            toggleHandle.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getDimensionPixelSize(R.dimen.toggle_handle_height)));
            toggleHandle.setGravity(android.view.Gravity.CENTER);

            keyboardView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, keyboardWeight));

            if (toggleHandlePill != null) {
                toggleHandlePill.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(4)));
            }
        }
        if (touchpadSection != null) {
            touchpadSection.post(this::applyProTouchpadMouseLayoutCompactOrComfortable);
        }
        if (!isLandscape
                && keyboardView != null
                && keyboardView.isSystemImeCaptureMode()
                && keyboardView.isImeSubComposeExpanded()) {
            applyImeSubComposeFragmentChrome(true);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;

        if (displayMode == DisplayMode.SPLIT && isPortrait) {
            displayMode = DisplayMode.BOTH;
            ensureNormalLayout();
            applyDisplayMode();
            return;
        }

        normalizeDisplayModeForOrientation();
        applyDisplayMode();
        if (displayMode == DisplayMode.SPLIT) {
            if (keyboardViewLeft != null) {
                keyboardViewLeft.reloadForCurrentOrientation();
                keyboardViewLeft.setSplitPart(CustomKeyboardView.SPLIT_LEFT);
            }
            if (keyboardViewRight != null) {
                keyboardViewRight.reloadForCurrentOrientation();
                keyboardViewRight.setSplitPart(CustomKeyboardView.SPLIT_RIGHT);
            }
        }
    }
}
