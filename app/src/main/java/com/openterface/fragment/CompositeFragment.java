package com.openterface.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.CustomKeyboardView;
import com.openterface.keymod.hid.MouseRelHidTransport;
import com.openterface.keymod.prefs.KmProSubmodePrefs;
import com.openterface.keymod.prefs.KmProTouchpadPrefs;
import com.openterface.keymod.touchpad.TouchpadMouseStripBinder;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.basic.BasicPortraitScrollStripView;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.TouchPadView;
import com.openterface.keymod.util.KmProImeDirectSendController;
import com.openterface.keymod.util.TouchPadHaptics;
import com.openterface.keymod.util.TouchPadHelpDialog;
import com.openterface.keymod.util.TouchPadPointerPhase;
import com.openterface.keymod.util.TouchPadTipsFormatter;
import com.openterface.target.CH9329MSKBMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    @Nullable private LinearLayout kmProKeyboardSlot;
    @Nullable private EditText kmProImeHost;
    @Nullable private KmProImeDirectSendController kmProImeDirectSend;

    private int kmProKeyboardBaselinePaddingLeft;
    private int kmProKeyboardBaselinePaddingTop;
    private int kmProKeyboardBaselinePaddingRight;
    private int kmProKeyboardBaselinePaddingBottom;
    private boolean kmProKeyboardBaselinePaddingCaptured;
    private TextView touchPadTips;
    /** Normal layout only; null while split layout is shown. */
    private View touchPadInfoButton;
    /** Split mode views */
    private View splitRoot;
    private CustomKeyboardView keyboardViewLeft;
    private CustomKeyboardView keyboardViewRight;
    private ViewGroup splitTouchpadSection;
    private TouchPadView splitTouchPad;
    private TextView splitTouchPadTips;
    /** Container to swap between normal and split layouts */
    private FrameLayout contentContainer;
    private LinearLayout splitLeftColumn;
    private LinearLayout splitRightColumn;
    private FrameLayout splitTopLeftFrame;
    private FrameLayout splitTopRightFrame;
    /** Inflated split layout root (landscape {@code fragment_composite_split}). */
    private View splitLayoutRoot;
    private View splitTouchPadInfoButton;
    @Nullable private View kmProComposeFragmentHost;

    /**
     * Portrait Keyboard submode + BOTH display: slightly taller keyboard band vs touchpad so IME
     * does not crush the third shortcut row.
     */
    private static final float PORTRAIT_BOTH_BUILT_IN_TOUCHPAD_WEIGHT = 1.10f;
    private static final float PORTRAIT_BOTH_BUILT_IN_KEYBOARD_WEIGHT = 1.52f;

    /** Portrait Keyboard submode + KEYBOARD-only display (no touchpad). */
    private static final float PORTRAIT_KEYBOARD_ONLY_TOUCHPAD_WEIGHT = 0.92f;
    private static final float PORTRAIT_KEYBOARD_ONLY_KEYBOARD_WEIGHT = 4.38f;
    /**
     * Portrait numpad strip: horizontal chrome width ratio touchpad : mouse-key column.
     */
    private static final float PORTRAIT_STRIP_TOUCHPAD_WEIGHT = 5f;
    private static final float PORTRAIT_STRIP_MOUSE_KEYS_WEIGHT = 2f;
    private static final float PORTRAIT_COMFORTABLE_TOUCHPAD_WEIGHT = 5f;
    private static final float PORTRAIT_COMFORTABLE_MOUSE_KEYS_WEIGHT = 2f;

    private final List<MainActivity.OnTargetOsChangeListener> osChangeListeners = new ArrayList<>();
    public UsbSerialPort port;
    private BluetoothService bluetoothService;
    private boolean isServiceBound;
    /** True after {@link Context#bindService} for {@link BluetoothService} returns true; drives unbind in {@link #onDestroyView()}. */
    private boolean proBluetoothServiceBindRequested;
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

    private static final String TAG_KM_PRO_COMPOSE = "km_pro_compose";

    private enum DisplayMode { BOTH, KEYBOARD, TOUCHPAD, SPLIT }
    private DisplayMode displayMode = DisplayMode.BOTH;
    private enum ProSubmode { KEYBOARD, NUMPAD, COMPOSE }
    private ProSubmode currentSubmode = ProSubmode.KEYBOARD;

    /** Keyboard &amp; Mouse Pro: swipe-up host modifier locks (separate from KM Basic’s controller). */
    private final KmBasicHoldLockController proHoldLockController = new KmBasicHoldLockController();

    @Nullable private LinearLayout proTouchpadChromeRoot;
    /** Landscape split: wraps mouse keys + optional split brand logo; null in other layouts. */
    @Nullable private LinearLayout proTouchpadMouseColumn;
    @Nullable private ImageView proTouchpadSplitBrandLogo;
    @Nullable private ViewGroup proTouchpadMouseKeys;
    @Nullable private ViewGroup touchpadPadHost;
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
                refreshProTouchpadSplitBrandLogo();
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
        CustomKeyboardView composeStrip = kmProComposeEmbeddedShortcutStripView();
        if (composeStrip != null) {
            composeStrip.refreshAfterShortcutHubPrefsChange();
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
        CustomKeyboardView composeStrip = kmProComposeEmbeddedShortcutStripView();
        if (composeStrip != null) {
            composeStrip.reloadKeyboardAlternatesHintsFromPrefs();
        }
    }

    /** Reload key tap preview pref (Keyboard and Mouse Pro setup). */
    public void refreshKmProKeyTapPreviewFromPrefs() {
        if (keyboardView != null) {
            keyboardView.reloadKmProKeyTapPreviewFromPrefs();
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.reloadKmProKeyTapPreviewFromPrefs();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.reloadKmProKeyTapPreviewFromPrefs();
        }
        CustomKeyboardView composeStrip = kmProComposeEmbeddedShortcutStripView();
        if (composeStrip != null) {
            composeStrip.reloadKmProKeyTapPreviewFromPrefs();
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
        CustomKeyboardView composeStrip = kmProComposeEmbeddedShortcutStripView();
        if (composeStrip != null) {
            composeStrip.rebuildKeyboardFromKmProSetup();
        }
    }

    /** Apply KM Pro touchpad prefs (mouse strip visibility, binder, compact layout). */
    public void refreshProTouchpadChromeFromKmProSetup() {
        if (!isAdded()) {
            return;
        }
        bindProTouchpadChromeReferences();
        boolean show = KmProTouchpadPrefs.showsMouseKeyStrip(requireContext());
        if (proTouchpadMouseColumn != null) {
            proTouchpadMouseColumn.setVisibility(show ? View.VISIBLE : View.GONE);
            if (proTouchpadMouseKeys != null) {
                proTouchpadMouseKeys.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } else if (proTouchpadMouseKeys != null) {
            proTouchpadMouseKeys.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        detachProMouseStripBinder();
        if (show) {
            attachProMouseStripBinderIfNeeded();
            applyProTouchpadMouseLayoutCompactOrComfortable();
            View postTarget = proTouchpadChromeMouseSibling();
            if (postTarget != null) {
                postTarget.post(CompositeFragment.this::applyProTouchpadMouseLayoutCompactOrComfortable);
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
        refreshProTouchpadSplitBrandLogo();
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
            proTouchpadMouseColumn = null;
            proTouchpadSplitBrandLogo = null;
            proTouchpadMouseKeys = null;
            touchpadPadHost = null;
            proTouchpadScrollStrip = null;
            proMouseBtnLeft = null;
            proMouseBtnMiddle = null;
            proMouseBtnRight = null;
            return;
        }
        proTouchpadChromeRoot = root.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseColumn = root.findViewById(R.id.pro_touchpad_mouse_column);
        proTouchpadSplitBrandLogo = root.findViewById(R.id.pro_touchpad_split_brand_logo);
        proTouchpadMouseKeys = root.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = root.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = root.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = root.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = root.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = root.findViewById(R.id.pro_touchpad_btn_right);
    }

    /**
     * Direct child of {@link #proTouchpadChromeRoot} that sits beside {@link #touchpadPadHost}
     * (mouse-key row, or landscape-split column wrapping keys + brand logo).
     */
    @Nullable
    private ViewGroup proTouchpadChromeMouseSibling() {
        if (proTouchpadMouseColumn != null) {
            return proTouchpadMouseColumn;
        }
        return proTouchpadMouseKeys;
    }

    /**
     * KM Pro landscape split: Openterface wordmark under L/M/R — same pixel sizing as {@link
     * CustomKeyboardView} wide Space key ({@code km_basic_touchpad_brand_logo_*}).
     */
    private void refreshProTouchpadSplitBrandLogo() {
        if (!isAdded() || proTouchpadSplitBrandLogo == null) {
            return;
        }
        boolean showLogo =
                splitRoot != null
                        && getResources().getConfiguration().orientation
                                == Configuration.ORIENTATION_LANDSCAPE
                        && KmProTouchpadPrefs.showsMouseKeyStrip(requireContext());
        if (!showLogo) {
            proTouchpadSplitBrandLogo.setVisibility(View.GONE);
            applyProTouchpadMouseColumnInnerLayout(false);
            return;
        }
        proTouchpadSplitBrandLogo.setVisibility(View.VISIBLE);
        Context ctx = requireContext();
        int logoH = ctx.getResources().getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_height);
        int maxLogoW =
                ctx.getResources().getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_max_width);
        int logoW = maxLogoW;
        Drawable wordmark = ContextCompat.getDrawable(ctx, R.drawable.ic_openterface_wordmark);
        if (wordmark != null) {
            int iw = wordmark.getIntrinsicWidth();
            int ih = wordmark.getIntrinsicHeight();
            if (iw > 0 && ih > 0) {
                logoW = Math.min(maxLogoW, Math.round(logoH * (iw / (float) ih)));
            }
        }
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(logoW, logoH);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin =
                ctx.getResources()
                        .getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_margin_bottom);
        proTouchpadSplitBrandLogo.setLayoutParams(lp);
        proTouchpadSplitBrandLogo.setImageResource(R.drawable.ic_openterface_wordmark);
        proTouchpadSplitBrandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        proTouchpadSplitBrandLogo.setAdjustViewBounds(false);
        proTouchpadSplitBrandLogo.setAlpha(0.82f);
        proTouchpadSplitBrandLogo.setColorFilter(
                ContextCompat.getColor(ctx, R.color.text_secondary), PorterDuff.Mode.SRC_IN);
        proTouchpadSplitBrandLogo.setContentDescription(
                ctx.getString(R.string.touch_pad_brand_content_description));
        if (proTouchpadMouseLayoutCompact && proTouchpadMouseColumn != null) {
            applyProTouchpadMouseColumnInnerLayout(true);
        } else {
            applyProTouchpadMouseColumnInnerLayout(false);
        }
    }

    /**
     * When {@link #proTouchpadMouseColumn} is used, distribute height between the key strip and the
     * logo in landscape compact chrome; otherwise use natural wrap heights.
     */
    private void applyProTouchpadMouseColumnInnerLayout(boolean compactLandscapeChrome) {
        if (proTouchpadMouseColumn == null || proTouchpadMouseKeys == null) {
            return;
        }
        LinearLayout.LayoutParams kLp =
                (LinearLayout.LayoutParams) proTouchpadMouseKeys.getLayoutParams();
        if (compactLandscapeChrome
                && proTouchpadSplitBrandLogo != null
                && proTouchpadSplitBrandLogo.getVisibility() == View.VISIBLE) {
            kLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            kLp.height = 0;
            kLp.weight = 1f;
        } else {
            kLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            kLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            kLp.weight = 0f;
        }
        proTouchpadMouseKeys.setLayoutParams(kLp);
        if (proTouchpadSplitBrandLogo != null) {
            LinearLayout.LayoutParams gLp =
                    (LinearLayout.LayoutParams) proTouchpadSplitBrandLogo.getLayoutParams();
            gLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            gLp.gravity = Gravity.CENTER_HORIZONTAL;
            gLp.weight = 0f;
            if (proTouchpadSplitBrandLogo.getVisibility() != View.VISIBLE) {
                gLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            proTouchpadSplitBrandLogo.setLayoutParams(gLp);
        }
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
     * @param mouseKeysFirst if true, mouse-key strip is index 0 and pad host index 1 (portrait numpad
     *     horizontal strip); if false, pad host first then mouse keys (XML default).
     */
    private void ensureProTouchpadChromeSiblingOrder(boolean mouseKeysFirst) {
        ViewGroup mouseSibling = proTouchpadChromeMouseSibling();
        if (proTouchpadChromeRoot == null || touchpadPadHost == null || mouseSibling == null) {
            return;
        }
        int iPad = proTouchpadChromeRoot.indexOfChild(touchpadPadHost);
        int iMouse = proTouchpadChromeRoot.indexOfChild(mouseSibling);
        if (iPad < 0 || iMouse < 0) {
            return;
        }
        boolean already =
                mouseKeysFirst ? (iMouse == 0 && iPad == 1) : (iPad == 0 && iMouse == 1);
        if (already) {
            return;
        }
        proTouchpadChromeRoot.removeView(touchpadPadHost);
        proTouchpadChromeRoot.removeView(mouseSibling);
        if (mouseKeysFirst) {
            proTouchpadChromeRoot.addView(mouseSibling, 0);
            proTouchpadChromeRoot.addView(touchpadPadHost, 1);
        } else {
            proTouchpadChromeRoot.addView(touchpadPadHost, 0);
            proTouchpadChromeRoot.addView(mouseSibling, 1);
        }
    }

    private void resetProTouchpadChromeOrientationComfortable() {
        ViewGroup mouseSibling = proTouchpadChromeMouseSibling();
        if (proTouchpadChromeRoot == null || touchpadPadHost == null || proTouchpadMouseKeys == null
                || mouseSibling == null) {
            return;
        }
        ensureProTouchpadChromeSiblingOrder(false);
        proTouchpadChromeRoot.setOrientation(LinearLayout.VERTICAL);
        boolean isPortrait =
                getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.height = 0;
        padLp.weight = isPortrait ? PORTRAIT_COMFORTABLE_TOUCHPAD_WEIGHT : 1f;
        touchpadPadHost.setLayoutParams(padLp);
        LinearLayout.LayoutParams mLp = (LinearLayout.LayoutParams) mouseSibling.getLayoutParams();
        mLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (isPortrait) {
            mLp.height = 0;
            mLp.weight = PORTRAIT_COMFORTABLE_MOUSE_KEYS_WEIGHT;
        } else {
            mLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mLp.weight = 0f;
        }
        mouseSibling.setLayoutParams(mLp);
        configureProMouseKeysRow(false);
        applyProTouchpadMouseKeysDefaultPadding();
        applyProTouchpadMouseColumnInnerLayout(false);
        applyProTouchpadScrollStripLayout();
        refreshProTouchpadSplitBrandLogo();
    }

    private void applyProTouchpadMouseKeysDefaultPadding() {
        if (proTouchpadMouseKeys == null) {
            return;
        }
        int top =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_top);
        int bottom =
                getResources().getDimensionPixelSize(R.dimen.pro_touchpad_mouse_keys_padding_bottom);
        int hPad =
                getResources()
                        .getDimensionPixelSize(R.dimen.basic_touchpad_mouse_column_horizontal_padding);
        proTouchpadMouseKeys.setPaddingRelative(hPad, top, hPad, bottom);
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
        LinearLayout row = touchpadPadHost.findViewById(R.id.touchpad_pad_and_strip);
        if (row == null) {
            return;
        }
        View padContent = row.findViewById(R.id.touchpad_pad_content);
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

    /**
     * Gesture-hint {@link TextView} is overlaid top-center on the pad + scroll strip; forward touches
     * so drags that start on the hint band still hit the pad or strip underneath.
     */
    private void wireKmProTouchPadTipsPassthrough(@Nullable TextView tips) {
        if (tips == null || touchpadPadHost == null) {
            return;
        }
        View row = touchpadPadHost.findViewById(R.id.touchpad_pad_and_strip);
        if (!(row instanceof ViewGroup)) {
            return;
        }
        ViewGroup padAndStrip = (ViewGroup) row;
        tips.setOnTouchListener(
                (v, event) -> {
                    MotionEvent copy = MotionEvent.obtain(event);
                    copy.offsetLocation(
                            v.getLeft() - padAndStrip.getLeft(),
                            v.getTop() - padAndStrip.getTop());
                    boolean handled = padAndStrip.dispatchTouchEvent(copy);
                    copy.recycle();
                    return handled;
                });
    }

    private void applyProTouchpadMouseLayoutCompactOrComfortable() {
        // Posted from layout passes and refresh paths; can run after the user leaves KM Pro (e.g. side
        // nav to KM Basic) and this fragment is already detached — avoid requireContext() there.
        if (!isAdded()) {
            return;
        }
        if (proTouchpadChromeRoot == null
                || touchpadPadHost == null
                || proTouchpadMouseKeys == null
                || touchpadSection == null
                || !KmProTouchpadPrefs.showsMouseKeyStrip(requireContext())) {
            return;
        }
        ViewGroup mouseSibling = proTouchpadChromeMouseSibling();
        if (mouseSibling == null) {
            return;
        }
        int th = touchpadSection.getHeight();
        int threshold =
                getResources()
                        .getDimensionPixelSize(R.dimen.pro_touchpad_section_compact_height_threshold);
        boolean numpadStripHorizontal = isPortraitNumpadTouchpadMode();
        boolean compactByHeight = th > 0 && th <= threshold;
        boolean useHorizontalChrome = numpadStripHorizontal || compactByHeight;
        if (!useHorizontalChrome && th <= 0) {
            return;
        }
        proTouchpadMouseLayoutCompact = useHorizontalChrome;
        if (!useHorizontalChrome) {
            resetProTouchpadChromeOrientationComfortable();
            return;
        }
        proTouchpadChromeRoot.setOrientation(LinearLayout.HORIZONTAL);
        boolean portraitTargetStrip = numpadStripHorizontal;
        ensureProTouchpadChromeSiblingOrder(portraitTargetStrip);
        LinearLayout.LayoutParams padLp = (LinearLayout.LayoutParams) touchpadPadHost.getLayoutParams();
        padLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        padLp.width = 0;
        LinearLayout.LayoutParams mLp = (LinearLayout.LayoutParams) mouseSibling.getLayoutParams();
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
                            .getDimensionPixelSize(R.dimen.basic_touchpad_mouse_column_horizontal_padding);
            proTouchpadMouseKeys.setPaddingRelative(hPad, topPad, hPad, bottomPad);
        } else {
            padLp.weight = 1f;
            mLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            mLp.weight = 0f;
            int hPad =
                    getResources()
                            .getDimensionPixelSize(R.dimen.basic_touchpad_mouse_column_horizontal_padding);
            proTouchpadMouseKeys.setPaddingRelative(hPad, topPad, hPad, bottomPad);
        }
        touchpadPadHost.setLayoutParams(padLp);
        mouseSibling.setLayoutParams(mLp);
        configureProMouseKeysRow(true);
        applyProTouchpadScrollStripLayout();
        refreshProTouchpadSplitBrandLogo();
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
        if (isAdded()) {
            Fragment composeChild = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
            if (composeChild instanceof BasicComposeFragment) {
                ((BasicComposeFragment) composeChild).onHostPortChanged(newPort);
            }
        }
        if (newPort == null) {
            proHoldLockController.clearAllAndReleaseHid(null, bluetoothService, isServiceBound);
        }
    }

    @Nullable
    private CustomKeyboardView kmProComposeEmbeddedShortcutStripView() {
        if (!isAdded()) {
            return null;
        }
        Fragment f = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
        if (!(f instanceof BasicComposeFragment)) {
            return null;
        }
        return ((BasicComposeFragment) f).getKmProEmbeddedShortcutStripOrNull();
    }

    /**
     * Keeps the KM Pro Compose embedded shortcut strip in sync with {@link #port} and
     * {@link #proHoldLockController} (same as the main keyboard slot).
     */
    public void syncKmProComposeShortcutStripFromKeyboardHost() {
        if (!isAdded()) {
            return;
        }
        Fragment f = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
        if (!(f instanceof BasicComposeFragment)) {
            return;
        }
        ((BasicComposeFragment) f)
                .syncKmProEmbeddedShortcutStripFromCompositeHost(port, proHoldLockController);
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
        syncKmProComposeShortcutStripFromKeyboardHost();
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
        Fragment compose = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
        if (compose instanceof BasicComposeFragment) {
            ((BasicComposeFragment) compose).syncKmProEmbeddedShortcutStripFromCompositeHost(port, null);
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
        if (!KmProTouchpadPrefs.isGestureStatusLineVisible(requireContext())) {
            touchPadTips.setVisibility(View.GONE);
            return;
        }
        touchPadTips.setVisibility(View.VISIBLE);
        // Portrait numpad: status line only; info button is hidden in numpad strip mode.
        touchPadTips.setText(
                TouchPadTipsFormatter.buildCompact(requireContext(), isDragMode, pointerPhase));
    }

    /** Numpad strip: touchpad + numpad grid; gesture help UI is suppressed (portrait or landscape). */
    private boolean isPortraitNumpadTouchpadMode() {
        return currentSubmode == ProSubmode.NUMPAD && displayMode == DisplayMode.KEYBOARD;
    }

    private void applyPortraitNumpadTouchpadChrome() {
        applyTouchpadInfoVisibility();
    }

    private void applyTouchpadInfoVisibility() {
        boolean numpad = isPortraitNumpadTouchpadMode();
        if (touchPadInfoButton != null) {
            touchPadInfoButton.setVisibility(numpad ? View.GONE : View.VISIBLE);
        }
        if (splitTouchPadInfoButton != null) {
            splitTouchPadInfoButton.setVisibility(numpad ? View.GONE : View.VISIBLE);
        }
        if (splitRoot == null) {
            updateTouchPadTips();
        }
    }

    /**
     * Applies IME-related bottom padding on {@code root}.
     *
     * <p>For {@code km_pro_keyboard_slot}, bottom padding stays {@code 0}: MainActivity uses {@code
     * adjustResize}, so the window already shrinks for the IME and padding the slot again would crush
     * {@link com.openterface.keymod.CustomKeyboardView}. Other roots (e.g. split layout) keep full {@code Type.ime()} bottom.
     */
    private void setupCompositeImeRootInsets(@NonNull View root) {
        final boolean slotZeroImePadding = root.getId() == R.id.km_pro_keyboard_slot;
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, windowInsets) -> {
                    int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    int bottom = slotZeroImePadding ? 0 : imeBottom;
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                    return windowInsets;
                });
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    private void captureKmProKeyboardPaddingBaseline() {
        if (keyboardView == null || kmProKeyboardBaselinePaddingCaptured) {
            return;
        }
        kmProKeyboardBaselinePaddingLeft = keyboardView.getPaddingLeft();
        kmProKeyboardBaselinePaddingTop = keyboardView.getPaddingTop();
        kmProKeyboardBaselinePaddingRight = keyboardView.getPaddingRight();
        kmProKeyboardBaselinePaddingBottom = keyboardView.getPaddingBottom();
        kmProKeyboardBaselinePaddingCaptured = true;
    }

    private void restoreKmProKeyboardViewPaddingBaseline() {
        if (keyboardView == null || !kmProKeyboardBaselinePaddingCaptured) {
            return;
        }
        keyboardView.setPadding(
                kmProKeyboardBaselinePaddingLeft,
                kmProKeyboardBaselinePaddingTop,
                kmProKeyboardBaselinePaddingRight,
                kmProKeyboardBaselinePaddingBottom);
    }

    /** BI + IME: same bottom reserve so the shortcut strip stays vertically aligned when toggling. */
    private void applyKmProPortraitKeyboardStripBottomReserve() {
        if (keyboardView == null || !kmProKeyboardBaselinePaddingCaptured || !isAdded()) {
            return;
        }
        int extra =
                getResources()
                        .getDimensionPixelSize(R.dimen.km_pro_portrait_keyboard_bottom_reserve);
        keyboardView.setPadding(
                kmProKeyboardBaselinePaddingLeft,
                kmProKeyboardBaselinePaddingTop,
                kmProKeyboardBaselinePaddingRight,
                kmProKeyboardBaselinePaddingBottom + extra);
    }

    /**
     * Portrait KM Pro Keyboard submode: apply strip bottom reserve; otherwise restore XML baseline.
     */
    private void updateKmProPortraitKeyboardStripBottomPadding() {
        if (keyboardView == null) {
            return;
        }
        if (splitRoot != null) {
            if (kmProKeyboardBaselinePaddingCaptured) {
                restoreKmProKeyboardViewPaddingBaseline();
            }
            return;
        }
        if (!isAdded() || !kmProKeyboardBaselinePaddingCaptured) {
            return;
        }
        if (!usePortraitStyleKmProRootLayout() || currentSubmode != ProSubmode.KEYBOARD) {
            restoreKmProKeyboardViewPaddingBaseline();
            return;
        }
        if (displayMode == DisplayMode.TOUCHPAD) {
            restoreKmProKeyboardViewPaddingBaseline();
            return;
        }
        applyKmProPortraitKeyboardStripBottomReserve();
    }

    private void updateSplitTouchPadTips() {
        if (splitTouchPadTips == null) {
            return;
        }
        if (KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(requireContext())) {
            splitTouchPadTips.setVisibility(View.GONE);
            return;
        }
        if (!KmProTouchpadPrefs.isGestureStatusLineVisible(requireContext())) {
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
        proBluetoothServiceBindRequested =
                requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        initTouchPadWashStyle();

        // Create container to swap between normal and split layouts
        contentContainer = new FrameLayout(requireContext());
        contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Initially inflate normal layout
        View normalView = inflater.inflate(R.layout.fragment_composite, contentContainer, false);
        contentContainer.addView(normalView);

        currentSubmode = loadPersistedSubmode();
        displayMode = loadPersistedLandscapeLayout();
        setupNormalViews(normalView);
        applyOrientationLayout();
        applyDisplayMode();
        syncMainActivityKmProTabs();
        applyKmProSubmodeRequestedOrientation();

        if (keyboardView != null && port != null) {
            keyboardView.setPort(port);
        }

        setupTouchPad(touchPad, touchPadTips, touchPadInfoButton);

        // Register keyboard view for OS change updates
        registerKeyboardOsListener(keyboardView);
        registerTopModeShortcutListener(keyboardView);
        bindProHoldLockControllerToKeyboardViews();

        return contentContainer;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncMainActivityKmProTabs();
        applyKmProSubmodeRequestedOrientation();
    }

    @Override
    public void onDestroyView() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
        if (touchpadSection != null) {
            touchpadSection.removeOnLayoutChangeListener(proTouchpadSectionLayoutListener);
        }
        detachProMouseStripBinder();
        detachProHoldLockControllerFromKeyboardViews();
        proHoldLockController.clearAllAndReleaseHid(port, bluetoothService, isServiceBound);
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
        hideKmProImeSurface();
        if (kmProImeDirectSend != null) {
            kmProImeDirectSend.detach();
            kmProImeDirectSend = null;
        }
        hideKmProComposeSubUi();
        // Must run before super.onDestroyView(): setDragMode updates tips / hybrid visuals on live views.
        clearKeyboardOsListeners();
        setDragMode(false);
        super.onDestroyView();
        if (proBluetoothServiceBindRequested) {
            proBluetoothServiceBindRequested = false;
            Context c = getContext();
            if (c != null) {
                try {
                    c.unbindService(serviceConnection);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "BluetoothService unbind skipped", e);
                }
            }
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "Unbound from BluetoothService");
        }
    }

    /** Remove all Target OS listeners registered for this fragment's keyboard views. */
    private void clearKeyboardOsListeners() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            for (MainActivity.OnTargetOsChangeListener listener : osChangeListeners) {
                main.removeOsChangeListener(listener);
            }
        }
        osChangeListeners.clear();
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

    private void registerSecondaryLayoutToggleListener(@Nullable CustomKeyboardView kbdView) {
        if (kbdView == null) {
            return;
        }
        kbdView.setOnKmProSecondaryLayoutToggleListener(source -> onSecondaryLayoutToggleRequested());
    }

    private void setupNormalViews(View view) {
        rootLayout = view.findViewById(R.id.composite_root);
        kmProComposeFragmentHost = view.findViewById(R.id.km_pro_compose_fragment_host);
        kmProKeyboardSlot = view.findViewById(R.id.km_pro_keyboard_slot);
        kmProImeHost = view.findViewById(R.id.km_pro_ime_host);
        kmProKeyboardBaselinePaddingCaptured = false;
        keyboardView = view.findViewById(R.id.keyboard_view);
        captureKmProKeyboardPaddingBaseline();
        View imeInsetTarget = kmProKeyboardSlot != null ? kmProKeyboardSlot : rootLayout;
        setupCompositeImeRootInsets(imeInsetTarget);
        touchPad = view.findViewById(R.id.touchPad);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        proTouchpadChromeRoot = view.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseKeys = view.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = view.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = view.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = view.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = view.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = view.findViewById(R.id.pro_touchpad_btn_right);
        touchPadTips = view.findViewById(R.id.touchPadTips);
        touchPadInfoButton = view.findViewById(R.id.touchPadInfo);
        wireKmProTouchPadTipsPassthrough(touchPadTips);
        setupBottomWashOverlays();
        updateTouchPadTips();
        registerProTouchpadSectionLayoutListener();
        registerSecondaryLayoutToggleListener(keyboardView);
        touchpadSection.post(CompositeFragment.this::refreshProTouchpadChromeFromKmProSetup);
        if (kmProImeHost != null) {
            if (kmProImeDirectSend != null) {
                kmProImeDirectSend.detach();
            }
            kmProImeDirectSend = new KmProImeDirectSendController(this);
            kmProImeDirectSend.attach(kmProImeHost);
        }
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
        keyboardViewLeft = view.findViewById(R.id.keyboard_view_left);
        keyboardViewRight = view.findViewById(R.id.keyboard_view_right);
        touchpadSection = view.findViewById(R.id.touchpad_section);
        splitTouchpadSection = touchpadSection;
        splitTouchPad = view.findViewById(R.id.touchPad);
        splitTouchPadTips = view.findViewById(R.id.touchPadTips);
        setupBottomWashOverlays();

        if (splitTouchPadTips != null) {
            updateSplitTouchPadTips();
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
            registerSecondaryLayoutToggleListener(keyboardViewLeft);
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
            registerSecondaryLayoutToggleListener(keyboardViewRight);
        }
        bindProHoldLockControllerToKeyboardViews();

        splitTouchPadInfoButton = view.findViewById(R.id.touchPadInfo);
        proTouchpadChromeRoot = view.findViewById(R.id.pro_touchpad_chrome_root);
        proTouchpadMouseKeys = view.findViewById(R.id.pro_touchpad_mouse_keys);
        touchpadPadHost = view.findViewById(R.id.touchpad_pad_host);
        proTouchpadScrollStrip = view.findViewById(R.id.pro_touchpad_scroll_strip);
        proMouseBtnLeft = view.findViewById(R.id.pro_touchpad_btn_left);
        proMouseBtnMiddle = view.findViewById(R.id.pro_touchpad_btn_middle);
        proMouseBtnRight = view.findViewById(R.id.pro_touchpad_btn_right);
        wireKmProTouchPadTipsPassthrough(splitTouchPadTips);
        setupTouchPad(splitTouchPad, splitTouchPadTips, splitTouchPadInfoButton);
        registerProTouchpadSectionLayoutListener();
        if (touchpadSection != null) {
            touchpadSection.post(CompositeFragment.this::refreshProTouchpadChromeFromKmProSetup);
        }
        applyKmProLandscapeSplitStripVsKeyboardWeights();
    }

    /**
     * Landscape split: each outer column splits height between the shortcut strip frames and the
     * half-keyboard using the same weights as full-keyboard mode inside {@link CustomKeyboardView}.
     */
    private void applyKmProLandscapeSplitStripVsKeyboardWeights() {
        if (!isLandscapeOrientation()
                || splitTopLeftFrame == null
                || splitTopRightFrame == null
                || keyboardViewLeft == null
                || keyboardViewRight == null) {
            return;
        }
        float strip = CustomKeyboardView.KM_PRO_LANDSCAPE_SHORTCUT_STRIP_HEIGHT_WEIGHT;
        float letters = CustomKeyboardView.KM_PRO_LANDSCAPE_LETTER_KEYBOARD_HEIGHT_WEIGHT;
        applySplitColumnStripVsKeyboardWeight(splitTopLeftFrame, keyboardViewLeft, strip, letters);
        applySplitColumnStripVsKeyboardWeight(splitTopRightFrame, keyboardViewRight, strip, letters);
    }

    private static void applySplitColumnStripVsKeyboardWeight(
            View stripHost, View keyboardHost, float stripWeight, float letterWeight) {
        ViewGroup.LayoutParams slp = stripHost.getLayoutParams();
        ViewGroup.LayoutParams klp = keyboardHost.getLayoutParams();
        if (!(slp instanceof LinearLayout.LayoutParams)
                || !(klp instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams stripParams = (LinearLayout.LayoutParams) slp;
        LinearLayout.LayoutParams kbParams = (LinearLayout.LayoutParams) klp;
        stripParams.height = 0;
        stripParams.weight = stripWeight;
        kbParams.height = 0;
        kbParams.weight = letterWeight;
        stripHost.setLayoutParams(stripParams);
        keyboardHost.setLayoutParams(kbParams);
    }

    private void setupTouchPad(TouchPadView pad, TextView tips, View infoButton) {
        if (pad == null) return;
        pad.setPadClickDragGesturesEnabled(
                !KmProTouchpadPrefs.isPadPlusMouseKeysNoTouchClickGestures(pad.getContext()));
        if (infoButton != null) {
            infoButton.setOnClickListener(
                    v -> TouchPadHelpDialog.show(requireContext(), true, true));
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
    }

    private boolean isLandscapeOrientation() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * NumPad in landscape uses the same vertical composition as portrait. NumPad and Compose are kept
     * portrait-only via {@link #applyKmProSubmodeRequestedOrientation()}; this branch remains for
     * any transient frame during rotation.
     */
    private boolean usePortraitStyleKmProRootLayout() {
        if (!isLandscapeOrientation()) {
            return true;
        }
        return currentSubmode == ProSubmode.NUMPAD;
    }

    /**
     * KM Pro NumPad is designed for portrait (touchpad strip + numpad grid). Lock the activity to
     * portrait while that tab is active so landscape does not show the full keyboard + shortcuts
     * layout.
     */
    private void applyKmProSubmodeRequestedOrientation() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (currentSubmode == ProSubmode.NUMPAD || currentSubmode == ProSubmode.COMPOSE) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    @NonNull
    private static ProSubmode proSubmodeFromPrefKey(@NonNull String key) {
        if (KmProSubmodePrefs.SUBMODE_NUMPAD.equals(key)) {
            return ProSubmode.NUMPAD;
        }
        if (KmProSubmodePrefs.SUBMODE_COMPOSE.equals(key)) {
            return ProSubmode.COMPOSE;
        }
        return ProSubmode.KEYBOARD;
    }

    @NonNull
    private static String prefKeyFromProSubmode(@NonNull ProSubmode submode) {
        switch (submode) {
            case NUMPAD:
                return KmProSubmodePrefs.SUBMODE_NUMPAD;
            case COMPOSE:
                return KmProSubmodePrefs.SUBMODE_COMPOSE;
            case KEYBOARD:
            default:
                return KmProSubmodePrefs.SUBMODE_KEYBOARD;
        }
    }

    /** Called from {@link MainActivity} header chips. */
    public void applyKmProSubmodeFromHost(@NonNull String submodeKey) {
        if (!isAdded()) {
            return;
        }
        ProSubmode next = proSubmodeFromPrefKey(submodeKey);
        if (next == currentSubmode) {
            syncMainActivityKmProTabs();
            return;
        }
        currentSubmode = next;
        KmProSubmodePrefs.setSubmode(requireContext(), prefKeyFromProSubmode(currentSubmode));
        applyDisplayMode();
        syncMainActivityKmProTabs();
        applyKmProSubmodeRequestedOrientation();
    }

    private void syncMainActivityKmProTabs() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).syncKmProHeaderTabSelectionUi();
        }
    }

    private void hideKmProImeSurface() {
        if (!isAdded()) {
            return;
        }
        if (kmProImeDirectSend != null) {
            kmProImeDirectSend.clearEditorAndState();
        }
        if (kmProImeHost != null) {
            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(kmProImeHost.getWindowToken(), 0);
            }
            kmProImeHost.clearFocus();
            kmProImeHost.setVisibility(View.GONE);
        }
        if (keyboardView != null && splitRoot == null) {
            keyboardView.setKmProPortraitLetterBodyVisible(true);
            if (currentSubmode == ProSubmode.KEYBOARD) {
                keyboardView.setVisibility(View.VISIBLE);
            }
        }
        updateKmProPortraitKeyboardStripBottomPadding();
    }

    private void showKmProImeSurface() {
        if (!isAdded() || splitRoot != null || keyboardView == null || kmProImeHost == null) {
            return;
        }
        if (kmProImeDirectSend != null) {
            kmProImeDirectSend.clearEditorAndState();
        }
        keyboardView.setVisibility(View.VISIBLE);
        keyboardView.setKmProPortraitLetterBodyVisible(false);
        kmProImeHost.setVisibility(View.VISIBLE);
        kmProImeHost.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            kmProImeHost.post(
                    () -> imm.showSoftInput(kmProImeHost, InputMethodManager.SHOW_IMPLICIT));
        }
        updateKmProPortraitKeyboardStripBottomPadding();
    }

    /**
     * Portrait keyboard submode: swap built-in HID vs system IME in the weighted keyboard slot.
     */
    private void applyPortraitKeyboardSurface() {
        if (!isAdded() || splitRoot != null) {
            return;
        }
        if (currentSubmode != ProSubmode.KEYBOARD) {
            hideKmProImeSurface();
            applyOrientationLayout();
            return;
        }
        if (isLandscapeOrientation()) {
            hideKmProImeSurface();
            applyOrientationLayout();
            return;
        }
        if (KmProSubmodePrefs.isPortraitImeSurface(requireContext())) {
            showKmProImeSurface();
        } else {
            hideKmProImeSurface();
        }
        applyOrientationLayout();
    }

    private ProSubmode loadPersistedSubmode() {
        return proSubmodeFromPrefKey(KmProSubmodePrefs.getSubmode(requireContext()));
    }

    private DisplayMode loadPersistedLandscapeLayout() {
        return KmProSubmodePrefs.isLandscapeSplit(requireContext())
                ? DisplayMode.SPLIT
                : DisplayMode.KEYBOARD;
    }

    private void persistLandscapeLayout(@NonNull DisplayMode mode) {
        KmProSubmodePrefs.setLandscapeLayoutKey(
                requireContext(),
                mode == DisplayMode.SPLIT
                        ? KmProSubmodePrefs.LAYOUT_SPLIT
                        : KmProSubmodePrefs.LAYOUT_FULL);
    }

    private void requestSubmode(@NonNull ProSubmode requestedSubmode) {
        applyKmProSubmodeFromHost(prefKeyFromProSubmode(requestedSubmode));
    }

    private void onSecondaryLayoutToggleRequested() {
        boolean isLandscape = isLandscapeOrientation();
        if (currentSubmode != ProSubmode.KEYBOARD) {
            requestSubmode(ProSubmode.KEYBOARD);
            return;
        }
        if (isLandscape) {
            displayMode = displayMode == DisplayMode.SPLIT ? DisplayMode.KEYBOARD : DisplayMode.SPLIT;
            persistLandscapeLayout(displayMode);
            applyDisplayMode();
            return;
        }
        boolean ime = !KmProSubmodePrefs.isPortraitImeSurface(requireContext());
        KmProSubmodePrefs.setPortraitInputSurface(requireContext(), ime);
        applyPortraitKeyboardSurface();
        refreshSecondaryToggleLabels();
    }

    private void refreshSecondaryToggleLabels() {
        if (keyboardView != null) {
            keyboardView.reloadForCurrentOrientation();
        }
        if (keyboardViewLeft != null) {
            keyboardViewLeft.reloadForCurrentOrientation();
        }
        if (keyboardViewRight != null) {
            keyboardViewRight.reloadForCurrentOrientation();
        }
        CustomKeyboardView composeStrip = kmProComposeEmbeddedShortcutStripView();
        if (composeStrip != null) {
            composeStrip.reloadForCurrentOrientation();
        }
    }

    private void hideKmProComposeSubUi() {
        if (kmProComposeFragmentHost != null) {
            kmProComposeFragmentHost.setVisibility(View.GONE);
        }
        if (!isAdded()) {
            return;
        }
        Fragment existing = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
        if (existing != null) {
            // Always remove: compose host can be destroyed on split/normal layout swaps; draft is
            // retained in-memory by BasicComposeFragment when enabled (see KmProComposeDraftRetentionPrefs).
            getChildFragmentManager().beginTransaction().remove(existing).commitAllowingStateLoss();
        }
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).hideImeSavedTextOverlay();
        }
    }

    private void showKmProComposeSubUi() {
        if (kmProComposeFragmentHost == null || !isAdded()) {
            return;
        }
        kmProComposeFragmentHost.setVisibility(View.VISIBLE);
        Fragment current = getChildFragmentManager().findFragmentByTag(TAG_KM_PRO_COMPOSE);
        if (!(current instanceof BasicComposeFragment)) {
            BasicComposeFragment frag = BasicComposeFragment.instantiateForKmProEmbedded(port);
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.km_pro_compose_fragment_host, frag, TAG_KM_PRO_COMPOSE)
                    .runOnCommit(
                            () -> {
                                Fragment f =
                                        getChildFragmentManager()
                                                .findFragmentByTag(TAG_KM_PRO_COMPOSE);
                                if (f instanceof BasicComposeFragment) {
                                    ((BasicComposeFragment) f).requestEditorImeForKmProEmbedded();
                                }
                                syncKmProComposeShortcutStripFromKeyboardHost();
                            })
                    .commitAllowingStateLoss();
        } else {
            ((BasicComposeFragment) current).onHostPortChanged(port);
            ((BasicComposeFragment) current).requestEditorImeForKmProEmbedded();
            syncKmProComposeShortcutStripFromKeyboardHost();
        }
    }

    private void applyDisplayMode() {
        if (currentSubmode != ProSubmode.KEYBOARD) {
            hideKmProImeSurface();
        }
        boolean isLandscape = isLandscapeOrientation();

        if (currentSubmode == ProSubmode.NUMPAD) {
            hideKmProComposeSubUi();
            displayMode = DisplayMode.KEYBOARD;
            ensureNormalLayout();
            if (kmProKeyboardSlot != null) {
                kmProKeyboardSlot.setVisibility(View.VISIBLE);
            }
            if (touchpadSection != null) {
                touchpadSection.setVisibility(View.VISIBLE);
            }
            if (keyboardView != null) {
                keyboardView.setVisibility(View.VISIBLE);
                keyboardView.setShortcutsStripOnly(false);
                keyboardView.setShowExtraPortraitKeys(true);
                keyboardView.reloadForCurrentOrientation();
            }
            applyOrientationLayout();
            applyPortraitNumpadTouchpadChrome();
            syncMainActivityKmProTabs();
            refreshSecondaryToggleLabels();
            return;
        }

        if (currentSubmode == ProSubmode.COMPOSE) {
            displayMode = DisplayMode.BOTH;
            ensureNormalLayout();
            if (touchpadSection != null) {
                touchpadSection.setVisibility(View.GONE);
            }
            if (kmProKeyboardSlot != null) {
                kmProKeyboardSlot.setVisibility(View.GONE);
            }
            showKmProComposeSubUi();
            if (keyboardView != null) {
                keyboardView.setShowExtraPortraitKeys(false);
                keyboardView.setShortcutsStripOnly(false);
                keyboardView.reloadForCurrentOrientation();
            }
            applyOrientationLayout();
            applyPortraitNumpadTouchpadChrome();
            syncMainActivityKmProTabs();
            refreshSecondaryToggleLabels();
            return;
        }

        // Keyboard submode
        if (isLandscape) {
            if (displayMode != DisplayMode.SPLIT && displayMode != DisplayMode.KEYBOARD) {
                displayMode = loadPersistedLandscapeLayout();
            }
            if (displayMode != DisplayMode.SPLIT) {
                displayMode = DisplayMode.KEYBOARD;
            }
            persistLandscapeLayout(displayMode);
        } else {
            displayMode = DisplayMode.BOTH;
        }

        boolean isInSplit = displayMode == DisplayMode.SPLIT;

        if (isInSplit) {
            hideKmProComposeSubUi();
            hideKmProImeSurface();
            ensureSplitLayout();
            if (keyboardViewLeft != null) {
                keyboardViewLeft.setShortcutsStripOnly(false);
                keyboardViewLeft.setShowExtraPortraitKeys(false);
            }
            if (keyboardViewRight != null) {
                keyboardViewRight.setShortcutsStripOnly(false);
                keyboardViewRight.setShowExtraPortraitKeys(false);
            }
            syncMainActivityKmProTabs();
            refreshSecondaryToggleLabels();
            return;
        }

        // Ensure we have the normal layout
        ensureNormalLayout();
        hideKmProComposeSubUi();
        if (kmProKeyboardSlot != null) {
            kmProKeyboardSlot.setVisibility(View.VISIBLE);
        }

        boolean portraitLike =
                !isLandscapeOrientation() || usePortraitStyleKmProRootLayout();
        if (touchpadSection != null) {
            boolean showTouchpad =
                    (portraitLike || displayMode != DisplayMode.KEYBOARD)
                            && displayMode != DisplayMode.TOUCHPAD;
            touchpadSection.setVisibility(showTouchpad ? View.VISIBLE : View.GONE);
        }
        if (keyboardView != null) {
            keyboardView.setVisibility(
                    displayMode != DisplayMode.TOUCHPAD ? View.VISIBLE : View.GONE);
            keyboardView.setShortcutsStripOnly(false);
            keyboardView.reloadForCurrentOrientation();
            keyboardView.setShowExtraPortraitKeys(false);
        }

        syncMainActivityKmProTabs();
        refreshSecondaryToggleLabels();
        applyPortraitNumpadTouchpadChrome();
        applyPortraitKeyboardSurface();
    }

    private void ensureSplitLayout() {
        if (splitRoot == null) {
            clearKeyboardOsListeners();
            View normal = contentContainer.getChildAt(0);
            if (normal != null) {
                contentContainer.removeView(normal);
            }
            View splitView =
                    LayoutInflater.from(requireContext())
                            .inflate(R.layout.fragment_composite_split, contentContainer, false);
            contentContainer.addView(splitView);
            try {
                setupSplitViews(splitView);
            } catch (RuntimeException e) {
                Log.e(TAG, "KM Pro split layout failed; falling back to full keyboard", e);
                contentContainer.removeView(splitView);
                displayMode = DisplayMode.KEYBOARD;
                persistLandscapeLayout(displayMode);
                View normalView =
                        LayoutInflater.from(requireContext())
                                .inflate(R.layout.fragment_composite, contentContainer, false);
                contentContainer.addView(normalView);
                setupNormalViews(normalView);
                if (keyboardView != null && port != null) {
                    keyboardView.setPort(port);
                }
                registerTopModeShortcutListener(keyboardView);
                registerKeyboardOsListener(keyboardView);
                setupTouchPad(touchPad, touchPadTips, touchPadInfoButton);
                bindProHoldLockControllerToKeyboardViews();
                applyOrientationLayout();
            }
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
            clearKeyboardOsListeners();
            detachProMouseStripBinder();
            if (touchpadSection != null) {
                touchpadSection.removeOnLayoutChangeListener(proTouchpadSectionLayoutListener);
            }
            View split = contentContainer.getChildAt(0);
            if (split != null) {
                contentContainer.removeView(split);
            }
            splitRoot = null;
            keyboardViewLeft = null;
            keyboardViewRight = null;
            splitLeftColumn = null;
            splitRightColumn = null;
            splitTopLeftFrame = null;
            splitTopRightFrame = null;
            splitLayoutRoot = null;
            splitTouchPadInfoButton = null;
            splitTouchpadSection = null;

            View normalView = LayoutInflater.from(requireContext()).inflate(
                    R.layout.fragment_composite, contentContainer, false);
            contentContainer.addView(normalView);
            setupNormalViews(normalView);
            if (keyboardView != null && port != null) {
                keyboardView.setPort(port);
            }
            registerTopModeShortcutListener(keyboardView);
            registerKeyboardOsListener(keyboardView);
            setupTouchPad(touchPad, touchPadTips, touchPadInfoButton);
            bindProHoldLockControllerToKeyboardViews();
        }
        applyOrientationLayout();
    }

    private void applyOrientationLayout() {
        if (splitRoot != null) {
            applyKmProLandscapeSplitStripVsKeyboardWeights();
            return;
        }
        if (rootLayout == null || touchpadSection == null || keyboardView == null) {
            return;
        }

        View keyboardColumn = kmProKeyboardSlot != null ? kmProKeyboardSlot : keyboardView;

        if (usePortraitStyleKmProRootLayout()) {
            rootLayout.setOrientation(LinearLayout.VERTICAL);

            float touchpadWeight;
            float keyboardWeight;

            if (currentSubmode == ProSubmode.NUMPAD) {
                touchpadWeight = 1f;
                keyboardWeight = 4f;
            } else {
                touchpadWeight =
                        displayMode == DisplayMode.KEYBOARD
                                ? PORTRAIT_KEYBOARD_ONLY_TOUCHPAD_WEIGHT
                                : 1.5f;
                keyboardWeight =
                        displayMode == DisplayMode.KEYBOARD
                                ? PORTRAIT_KEYBOARD_ONLY_KEYBOARD_WEIGHT
                                : 1.0f;
                if (displayMode == DisplayMode.BOTH) {
                    touchpadWeight = PORTRAIT_BOTH_BUILT_IN_TOUCHPAD_WEIGHT;
                    keyboardWeight = PORTRAIT_BOTH_BUILT_IN_KEYBOARD_WEIGHT;
                }
            }

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, touchpadWeight));

            keyboardColumn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, keyboardWeight));

            applyKmProComposeFragmentHostLayoutParamsPortrait();
            updateKmProPortraitKeyboardStripBottomPadding();
        } else {
            rootLayout.setOrientation(LinearLayout.HORIZONTAL);

            restoreKmProKeyboardViewPaddingBaseline();

            touchpadSection.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            keyboardColumn.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 2.0f));

            applyKmProComposeFragmentHostLayoutParamsLandscape();
        }
        if (touchpadSection != null && touchpadSection.getVisibility() == View.VISIBLE) {
            touchpadSection.post(this::applyProTouchpadMouseLayoutCompactOrComfortable);
        }
    }

    private void applyKmProComposeFragmentHostLayoutParamsPortrait() {
        if (kmProComposeFragmentHost == null
                || kmProComposeFragmentHost.getVisibility() != View.VISIBLE
                || splitRoot != null) {
            return;
        }
        kmProComposeFragmentHost.setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void applyKmProComposeFragmentHostLayoutParamsLandscape() {
        if (kmProComposeFragmentHost == null
                || kmProComposeFragmentHost.getVisibility() != View.VISIBLE
                || splitRoot != null) {
            return;
        }
        kmProComposeFragmentHost.setLayoutParams(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    /** Entering landscape: ensure KM Pro keyboard stays on built-in HID layout. */
    private void applyKmProLandscapeBuiltInKeyboardGuard() {
        if (displayMode == DisplayMode.SPLIT) {
            if (keyboardViewLeft != null) {
                keyboardViewLeft.forceKmProBuiltInKeyboardModeForLandscapeGuard();
            }
        } else if (keyboardView != null) {
            keyboardView.forceKmProBuiltInKeyboardModeForLandscapeGuard();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyKmProLandscapeBuiltInKeyboardGuard();
        }
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
