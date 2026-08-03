package com.openterface.keymod;

import com.openterface.keymod.ConnectionManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.gridlayout.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.hid.Ch9329PacketUtil;
import com.openterface.keymod.hid.HidKeyboardTransport;
import com.openterface.keymod.hid.KeyboardHidTransport;
import com.openterface.keymod.hid.KeyboardTransport;
import com.openterface.keymod.util.HidTextKeystrokeSender;
import com.openterface.keymod.util.KeyParser;
import com.openterface.keymod.util.TopModeShortcutPrefs;
import com.google.android.material.color.MaterialColors;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.openterface.keymod.basic.BasicHoldLockPopup;
import com.openterface.keymod.basic.BasicKeyFeedback;
import com.openterface.keymod.basic.BasicKeyPreview;
import com.openterface.keymod.basic.KmBasicHoldLockController;
import com.openterface.keymod.basic.KmBasicHoldLockTiming;
import com.openterface.keymod.basic.KmBasicKeyboardPrefs;
import com.openterface.keymod.preset.FixedStripLayoutCatalog;
import com.openterface.keymod.preset.Rows23StripProfile;
import com.openterface.keymod.prefs.KeyboardAlternatesHintsPrefs;
import com.openterface.keymod.prefs.KmProKeyTapPreviewPrefs;
import com.openterface.keymod.prefs.KmProSubmodePrefs;
import com.openterface.keymod.prefs.TopShortcutDisplayModePrefs;
import com.openterface.keymod.preset.Rows23StripProfileManager;
import com.openterface.keymod.preset.StripSlotMapStore;
import com.openterface.target.CH9329MSKBMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;

public class CustomKeyboardView extends LinearLayout {

    /** KM Pro keyboard input: built-in HID QWERTY only (legacy IME modes removed). */
    public enum KmProKeyboardInputMode {
        BUILT_IN_QWERTY
    }

    private static final String TAG = "CustomKeyboardView";

    /**
     * Touch / HID correlation for KM Pro built-in keys. Debug builds log by default; on release APKs
     * run: {@code adb shell setprop log.tag.KmProTouch DEBUG} then
     * {@code adb logcat -s KmProTouch:D KeyboardHidTransport:D}.
     */
    private static final String TAG_KMPRO_TOUCH = "KmProTouch";
    private static final int TOP_PANEL_COLUMNS = 7;
    private static final int TOP_PANEL_ROWS = 3;
    private static final int FIXED_TOP_ROWS_DEFAULT_PAGE_INDEX = 1;
    private static final int TOP_PANEL_PAGE_SIZE = TOP_PANEL_COLUMNS * TOP_PANEL_ROWS;
    private static final int TOP_ROW_PAGE_SIZE = TOP_PANEL_COLUMNS;
    /** Landscape split top strip: left cols 1–3 (0-based [0,3)), right cols 4–7 ([3,7)). */
    private static final int TOP_PANEL_LEFT_START_COL = 0;
    private static final int TOP_PANEL_LEFT_END_COL = 3;
    private static final int TOP_PANEL_RIGHT_START_COL = 3;
    private static final int TOP_PANEL_RIGHT_END_COL = TOP_PANEL_COLUMNS;
    private static final float TOP_PANEL_ROW_WEIGHT = 0.8f;
    private static final float TOP_PANEL_SCROLLABLE_ROW_WEIGHT = TOP_PANEL_ROW_WEIGHT;
    private static final float TOP_PANEL_FIXED_ROWS_WEIGHT = TOP_PANEL_ROW_WEIGHT * 2f;
    private static final float TOP_PANEL_TOTAL_WEIGHT = TOP_PANEL_ROWS * TOP_PANEL_ROW_WEIGHT;
    /**
     * KM Pro landscape: layout weight for shortcut strip vs letter keyboard — full mode inside
     * {@link CustomKeyboardView}, and each side column in {@code layout-land/fragment_composite_split}
     * (see {@link CompositeFragment#applyKmProLandscapeSplitStripVsKeyboardWeights()}).
     */
    public static final float KM_PRO_LANDSCAPE_SHORTCUT_STRIP_HEIGHT_WEIGHT = 1f;
    public static final float KM_PRO_LANDSCAPE_LETTER_KEYBOARD_HEIGHT_WEIGHT = 2.2f;
    /**
     * {@link #expandSpaceBarForSplitRows} halves the wide Space (e.g. 36%p → 18%p). Openterface on
     * Space applies only to the unsplit bar; halves stay under this floor even after split rescale.
     */
    private static final float KM_PRO_LANDSCAPE_WIDE_SPACE_MIN_WIDTH_PERCENT = 30f;
    /** Top shortcut strip (row 1 profile + rows 2–3 fixed): label text (sp). */
    private static final float TOP_SHORTCUT_PANEL_TEXT_SP = 12f;
    private static final float TOP_SHORTCUT_PANEL_ACTION_LABEL_SP = 12f;
    private static final float TOP_SHORTCUT_PANEL_CUSTOM_GLYPH_SP = 20f;
    /** Fixed top rows only (page 0–2 strip): same size as Combo Text mode; bold preserved in Text mode to limit wrap. */
    private static final float TOP_FIXED_ROWS_TEXT_SP = 12f;
    /** BI / IME strip toggle: slightly larger than {@link #TOP_FIXED_ROWS_TEXT_SP}, always bold. */
    private static final float TOP_FIXED_ROWS_IME_TOGGLE_TEXT_SP = 13.5f;
    private static final float TOP_FIXED_ROWS_ACTION_LABEL_SP = 12f;
    /**
     * Emoji / single-glyph icons on fixed rows 2–3: autosize so single-char dingbats / math glyphs
     * (e.g. ∑ ∂ ❄ ✓ ⊿) grow toward the cell's height, while wider compounds shrink to the floor.
     * Range is set to roughly match {@link #TOP_SHORTCUT_PANEL_CUSTOM_GLYPH_SP} (favorites strip)
     * at the high end so glyph-only profiles look comparable to the Default profile's drawable icons.
     */
    private static final int TOP_FIXED_ROWS_CUSTOM_GLYPH_MIN_SP = 14;
    private static final int TOP_FIXED_ROWS_CUSTOM_GLYPH_MAX_SP = 22;
    private static final int TOP_FIXED_ROWS_CUSTOM_GLYPH_STEP_SP = 1;
    /** Fixed rows 2–3: Fn-layer alternate (same placement as main-keyboard {@code keyCornerHint}; stronger alpha for strip). */
    private static final float TOP_FIXED_ROWS_FN_HINT_SP = 7f;
    private static final float TOP_FIXED_ROWS_FN_HINT_ALPHA = 0.36f;
    private static final int TOP_FIXED_ROWS_FN_HINT_MAX_CHARS = 8;
    /** Extra numpad Fn-arrow overlay (dp); larger than top strip 24dp icons for the taller grid cells. */
    private static final int EXTRA_NUMPAD_FN_ARROW_ICON_DP = 36;
    /** Fn-layer Save / Undo / Tab icons — match top shortcut row visual weight (24dp assets, modest cell size). */
    private static final int EXTRA_NUMPAD_FN_ACTION_ICON_DP = 28;
    /**
     * Shared text size (sp) for extra-grid digits 0–9, operators / * - + =, comma/dot,
     * and Fn-layer substitutes ($ ¥ € £ % 000) so one visual block.
     */
    private static final float EXTRA_NUMPAD_GRID_KEY_TEXT_SP = 20f;
    private static final int KEY_MODE_FN = 0xF005;
    /** Local Fn latch for keyboard-only extra numpad grid (does not affect global QWERTY Fn layer). */
    private static final int KEY_EXTRA_NUMPAD_FN = 0xF006;
    /** Top strip PH1–PH3: tap switches app mode; long-press picks mode (saved per slot). */
    private static final int KEY_TOP_MODE_SLOT_1 = 0xF007;
    private static final int KEY_TOP_MODE_SLOT_2 = 0xF008;
    private static final int KEY_TOP_MODE_SLOT_3 = 0xF009;
    /** PH1: toggle system IME capture vs KeyCmd HID keyboard. */
    private static final int KEY_IME_TOGGLE = 0xF00A;
    private static final int KEY_TOP_SHORTCUT_DISPLAY_TOGGLE = 0xF00B;
    /** Local Fn latch for fixed top rows 2-3 only. */
    private static final int KEY_FIXED_TOP_LOCAL_FN = 0xF00C;
    /** Row-1 strip: opens quick-create shortcut sheet (after last favorite on last page). */
    private static final int KEY_TOP_STRIP_CREATE_SHORTCUT = 0xF014;
    private static final int KEY_NOOP_PLACEHOLDER = -1;
    private static final String APP_PREFS_NAME = "AppPrefs";
    private static final String KEY_SYSTEM_IME_CAPTURE = "system_ime_capture_mode";
    private static final String KEY_IME_SUB_COMPOSE_DIRECT_HID = "ime_sub_compose_direct_hid";
    /** 0 = shortcut name, 1 = icon-first, 2 = chord (e.g. Alt+X). */
    private static final int DISPLAY_MODE_NAME = TopShortcutDisplayModePrefs.MODE_NAME;
    private static final int DISPLAY_MODE_ICON = TopShortcutDisplayModePrefs.MODE_ICON;
    private static final int DISPLAY_MODE_CHORD = TopShortcutDisplayModePrefs.MODE_CHORD;
    private static final int MOD_CTRL = 1;
    private static final int MOD_SHIFT = 2;
    private static final int MOD_ALT = 4;
    private static final int MOD_WIN = 8;
    private boolean isShiftLeftLocked = false;
    private boolean isCtrlLeftLocked = false;
    private boolean isAltLeftLocked = false;
    private boolean isWinLeftLocked = false;
    private boolean isCtrlRightLocked = false;
    private boolean isAltRightLocked = false;
    private boolean isWinRightLocked = false;
    @Nullable private KmBasicHoldLockController holdLockController;
    private final KmBasicHoldLockController.Listener proHoldLockListener =
            controller -> {
                if (isAttachedToWindow()) {
                    refreshVisibleTopPanelButtonStates();
                    post(this::refreshBuiltInModifierKeyCapsFromTree);
                }
            };
    @Nullable private BasicHoldLockPopup activeHoldLockPopup;
    @Nullable private View holdLockPopupAnchorView;
    private boolean holdLockFingerDown;
    private float holdLockGestureRawX;
    private float holdLockGestureRawY;
    private int holdLockPendingModMask;
    private final Runnable proHoldLockPopupRunnable = this::onProHoldLockPopupTimeout;
    /** Per modifier key view: boot bits for each finger in chord sustain (multi-touch). */
    private final ArrayMap<View, Integer> proChordSustainBootByView = new ArrayMap<>();
    private boolean proChordHostHoldSent;
    private int proChordActiveExtKey;
    private int proChordLongPressPendingMask;
    @Nullable private View proChordLongPressAnchorView;
    private final Runnable proChordLongPressRunnable = this::onProChordLongPressThreshold;
    private final SharedPreferences.OnSharedPreferenceChangeListener kmProModifierPrefListener =
            this::onKmProModifierPreferenceChanged;
    private boolean isRunning = true;
    private boolean isSymbolMode = false;
    private boolean isFnLocked = false;
    private boolean extraNumpadFnLocked = false;
    private boolean fixedTopLocalFnLocked = false;
    /** Prevents duplicate strip edition dialogs when split keyboard has two Fn keys. */
    private boolean stripLayoutEditionDialogShowing = false;
    /** Extra numpad NumLock visual state: false = State A(default text), true = State B(theme accent). */
    private boolean extraNumpadNumStateB = false;
    private GridLayout extraNumpadGrid;
    private ImageButton extraNumpadFnButton;
    private boolean showExtraPortraitKeys = false;
    /** When false, main keyboard and fixed strip page 1 omit the left GUI (Win/Cmd/Super) key. */
    private boolean showGuiHidKey = true;
    /** When true, only the top shortcut strip(s) are shown (Compose mode). */
    private boolean shortcutsStripOnly = false;
    /** Top strip display mode for favorites and eligible fixed-row keys; see {@link #DISPLAY_MODE_NAME}. */
    private int topShortcutDisplayMode = DISPLAY_MODE_ICON;
    /**
     * When false, lower-keyboard keys that normally support long-press alternates use hold-to-repeat
     * (gaming) and keycap alternate hints are hidden. Toggled via the main keyboard left Shift key
     * (built-in Pro layout: row below letters, left of Z) when main keyboard Fn is latched.
     */
    private boolean keyboardAlternatesHintsEnabled = true;
    /**
     * Fixed-strip momentary press sync: when a direction/Enter/modifier key on the fixed strip is
     * held down, its HID code is stored here so the matching main-keyboard key can mirror the
     * pressed highlight during {@link #refreshBuiltInModifierKeyCapsFromTree()}. Cleared on release.
     */
    private int stripMomentarySyncKeyCode = -1;
    /** KM Pro setup: floating label above key while pressed (see {@link KmProKeyTapPreviewPrefs}). */
    private boolean kmProKeyTapPreviewEnabled;
    private final BasicKeyPreview kmProKeyPreview = new BasicKeyPreview();
    private Runnable gamingRepeatRunnable;
    private Runnable gamingRepeatStarterRunnable;
    private Key gamingRepeatKey;
    private boolean gamingRepeatActive;
    /** Pairs each gaming repeat tap with {@link #sendReleaseData} (same as KM Basic {@code tapKey}). */
    private final Runnable gamingTapReleaseRunnable = this::sendReleaseData;

    /**
     * Coalesced all-keys-released after ordinary taps (KM Basic {@code physicalKeyReleaseRunnable}
     * parity): one pending post per burst so rapid keys do not stack competing release threads.
     */
    private final Runnable keyboardTapReleaseRunnable = this::sendReleaseData;

    /** Split keyboard mode: which half to render (for landscape split mode with touchpad in middle) */
    public static final int SPLIT_NONE = 0;
    public static final int SPLIT_LEFT = 1;
    public static final int SPLIT_RIGHT = 2;
    private int splitPart = SPLIT_NONE;
    /** The paired keyboard view in split mode, for syncing modifier states */
    private CustomKeyboardView splitPartner;

    private final ExecutorService imeTextExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UnicodeStripSend");
        t.setDaemon(true);
        return t;
    });

    public interface OnImeCaptureModeChangedListener {
        void onImeCaptureModeChanged(CustomKeyboardView source, boolean enabled);
    }

    /** @deprecated KM Pro IME sub-compose removed; interface retained for compatibility. */
    public interface OnImeSubComposeChromeListener {
        void onImeSubComposeExpandedChanged(CustomKeyboardView source, boolean expanded);

        void onImeToolbarPopOutTouchpadRequested(CustomKeyboardView source);

        default void onImeSubComposeDirectHidModeChanged(CustomKeyboardView source, boolean direct) {
        }
    }

    public interface OnTopModeShortcutListener {
        void onRequestSwitchToMode(String launchPanelMode);
    }

    public interface OnKmProSecondaryLayoutToggleListener {
        void onRequestToggle(CustomKeyboardView source);
    }

    private OnTopModeShortcutListener onTopModeShortcutListener;
    @Nullable private OnKmProSecondaryLayoutToggleListener onKmProSecondaryLayoutToggleListener;
    /** Wraps QWERTY rows only (KM Pro single-pane); strip stays visible when this is {@link View#INVISIBLE}. */
    @Nullable private LinearLayout kmProLetterKeyboardBody;
    /** Portrait KM Pro full QWERTY: bottom-centered wordmark (sibling after letter body). */
    @Nullable private ImageView kmProPortraitBrandFooter;

    private List<List<Key>> lowerKeys;
    private UsbSerialPort port;
    private volatile KeyboardTransport transport;
    private volatile boolean useCustomTransport = false;
    private Handler repeatHandler = new Handler();
    /** Hold-to-repeat for Space / Bksp / DEL / arrows: tap-like HID pairs after {@link ViewConfiguration} delays. */
    private Runnable holdKeyRepeatRunnable;
    private Runnable holdKeyRepeatStarterRunnable;
    private Key holdKeyRepeatKey;
    private boolean holdKeyRepeatActive;
    private boolean holdRepeatSuppressUpTap;
    private static final int ALT_LONG_PRESS_TIMEOUT_MS = ViewConfiguration.getLongPressTimeout();
    /**
     * macOS: brief Caps (0x39) tap toggles input source; longer hold toggles Caps Lock LED. Use a hold
     * safely above the host threshold (see BasicPhysicalKeyboardView.MAC_CAPS_LONG_PRESS_MS).
     */
    private static final long MAC_CAPS_LONG_PRESS_MS = 500L;
    private static final long MAC_CAPS_MIN_TAP_HOLD_MS = 30L;
    /** Long-press local Fn (fixed strip row 3 col 7) to open strip layout edition entry (not per-cell). */
    private static final int STRIP_EDIT_VIA_FN_MS = 900;
    private static final long MODIFIER_RAPID_TAP_WINDOW_MS = 3000L;
    private static final long MODIFIER_LOCK_HINT_COOLDOWN_MS = 10_000L;
    private static final int MODIFIER_RAPID_TAP_HINT_THRESHOLD = 2;
    /** Inside this radius from touch-down, release commits the default alternate (center). */
    private static final int ALT_GESTURE_R_MIN_DP = 12;
    /** Beyond this radius from touch-down, release cancels (no character). */
    private static final int ALT_GESTURE_R_CANCEL_DP = 228;
    /** Half-width of neutral band on X/Y for 3×3 classification (cardinal vs diagonal vs default cross). */
    private static final int ALT_GESTURE_AXIS_DEADZONE_DP = 18;
    private static final int ALT_POPUP_VERTICAL_OFFSET_DP = 96;
    /** Additional upward shift when bottom-row alternates are present (finger otherwise covers row-3). */
    private static final int ALT_POPUP_BOTTOM_ROW_EXTRA_OFFSET_DP = 18;
    private static final int ALT_POPUP_CONTAINER_PADDING_DP_PORTRAIT = 6;
    private static final int ALT_POPUP_CONTAINER_PADDING_DP_LANDSCAPE = 8;
    private static final int ALT_POPUP_OPTION_MARGIN_DP_PORTRAIT = 2;
    private static final int ALT_POPUP_OPTION_MARGIN_DP_LANDSCAPE = 3;
    private static final int ALT_POPUP_OPTION_TEXT_SP_PORTRAIT = 15;
    private static final int ALT_POPUP_OPTION_TEXT_SP_LANDSCAPE = 20;
    private static final int ALT_POPUP_OPTION_PADDING_HORIZONTAL_DP_PORTRAIT = 8;
    private static final int ALT_POPUP_OPTION_PADDING_HORIZONTAL_DP_LANDSCAPE = 10;
    private static final int ALT_POPUP_OPTION_PADDING_VERTICAL_DP_PORTRAIT = 5;
    private static final int ALT_POPUP_OPTION_PADDING_VERTICAL_DP_LANDSCAPE = 7;
    /** Minimum width/height so cardinal wash pills read clearly (single glyphs otherwise shrink too small). */
    private static final int ALT_POPUP_CELL_MIN_SIZE_DP = 40;
    private static final float BASE_KEYCAP_TEXT_SP_PORTRAIT = 17f;
    private static final float BASE_KEYCAP_TEXT_SP_LANDSCAPE = 19f;
    /**
     * Bottom row on Keyboard &amp; Mouse Pro (Fn / Ctrl / Alt / Space / GUI): slightly smaller than
     * {@link #BASE_KEYCAP_TEXT_SP_LANDSCAPE} / {@link #BASE_KEYCAP_TEXT_SP_PORTRAIT} to avoid wrap.
     */
    private static final float PRO_BOTTOM_ROW_TEXT_SP_LANDSCAPE = 14.5f;
    private static final float PRO_BOTTOM_ROW_TEXT_SP_PORTRAIT = 13f;
    private static final int KEY_OUTER_MARGIN_DP = 2;
    private final Handler longPressHandler = new Handler();
    private PopupWindow alternatePopupWindow;
    private ViewGroup alternatePopupContainer;
    private View alternateAnchorView;
    /** TextView per slot index {@link AlternatePopupGeometry#SLOT_CENTER}…{@link AlternatePopupGeometry#SLOT_DOWN_RIGHT}. */
    private final TextView[] alternateSlotViews = new TextView[AlternatePopupGeometry.SLOT_COUNT];
    private AlternatePopupModel currentAlternatePopupModel;
    /** Latest pick from {@link AlternatePopupGeometry#pickSlot}. */
    private int currentAlternatePick = AlternatePopupGeometry.RESULT_DEFAULT;
    private int lastAlternatePickForHaptic = Integer.MIN_VALUE;
    private float alternatesGestureStartRawX;
    private float alternatesGestureStartRawY;
    private int topPanelPageIndex = 0;
    private int fixedTopRowsPageIndex = FIXED_TOP_ROWS_DEFAULT_PAGE_INDEX;
    private int rapidTapModifierCode = -1;
    private int rapidTapModifierCount = 0;
    private long rapidTapModifierLastTapMs = 0L;
    private long lastModifierLockHintToastMs = 0L;
    private ShortcutProfileManager shortcutProfileManager;
    private final List<TopShortcutPanel> topShortcutPanels = new ArrayList<>();
    private final List<List<Key>> fixedTopRowsPanels = new ArrayList<>();
    private FrameLayout topPanelViewport;
    private LinearLayout topPanelRootContainer;
    private FrameLayout fixedTopRowsViewport;
    private LinearLayout previousFixedTopRowsContainer;
    private LinearLayout activeFixedTopRowsContainer;
    private LinearLayout nextFixedTopRowsContainer;
    private LinearLayout previousTopPanelContainer;
    private LinearLayout activeTopPanelContainer;
    private LinearLayout nextTopPanelContainer;
    private FrameLayout splitTopLeftViewport;
    private FrameLayout splitTopRightViewport;
    private View splitTopCenterSpacer;
    private LinearLayout splitPreviousTopPanelContainerLeft;
    private LinearLayout splitActiveTopPanelContainerLeft;
    private LinearLayout splitNextTopPanelContainerLeft;
    private FrameLayout splitFixedTopRowsViewportLeft;
    private LinearLayout splitPreviousFixedTopRowsContainerLeft;
    private LinearLayout splitActiveFixedTopRowsContainerLeft;
    private LinearLayout splitNextFixedTopRowsContainerLeft;
    private LinearLayout splitPreviousTopPanelContainerRight;
    private LinearLayout splitActiveTopPanelContainerRight;
    private LinearLayout splitNextTopPanelContainerRight;
    private FrameLayout splitFixedTopRowsViewportRight;
    private LinearLayout splitPreviousFixedTopRowsContainerRight;
    private LinearLayout splitActiveFixedTopRowsContainerRight;
    private LinearLayout splitNextFixedTopRowsContainerRight;

    private BluetoothService bluetoothService;
    private boolean isServiceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.v(TAG, "Bound to BluetoothService");
            updateTransport();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.v(TAG, "Unbound from BluetoothService");
            updateTransport();
        }
    };

    private static class Key {
        String label;
        String symbolLabel;
        String alternates;
        String cornerHint;
        int code;
        String codeStr;
        float widthPercent;
        int iconResId;
        float horizontalGap;
        boolean isRepeatable;
        boolean requiresShift;
        int shortcutModifiers;
        boolean isTopPanelKey;
        String customIconGlyph;
        boolean allowTopPanelPagingGesture;
        /** If >= 0, scrolling row-1 cell maps to this index in ordered My Shortcuts (long-press to reassign). */
        int topStripFavoriteSlotIndex = -1;
        /** Rows 2–3 strip catalog cell for active strip profile overrides; -1 if not a slot. */
        int stripSlotPage = -1;
        int stripSlotRow = -1;
        int stripSlotCol = -1;
        /** Factory HID scan before Rows 2–3 overrides rewrite {@link #code}; used for page-0 digit overlay lookup. */
        int stripSlotFactoryCode;
        /**
         * When non-zero, this strip cap was bound to a Rows 2–3 shortcut whose
         * {@link com.openterface.keymod.ShortcutProfileManager.Shortcut#unicodeCodePoint} is set.
         * The strip-press handler dispatches via {@code HidTextKeystrokeSender} (per-OS Unicode
         * Hex Input alt-code) instead of {@link #sendShortcutWithModifiers}.
         */
        int unicodeCodePoint;

        Key(String label, String symbolLabel, String alternates, String cornerHint, int code, String codeStr, float widthPercent, int iconResId,
            float horizontalGap, boolean isRepeatable, boolean requiresShift, int shortcutModifiers, boolean isTopPanelKey) {
            this.label = label;
            this.symbolLabel = symbolLabel;
            this.alternates = alternates;
            this.cornerHint = cornerHint;
            this.code = code;
            this.codeStr = codeStr;
            this.widthPercent = widthPercent;
            this.iconResId = iconResId;
            this.horizontalGap = horizontalGap;
            this.isRepeatable = isRepeatable;
            this.requiresShift = requiresShift;
            this.shortcutModifiers = shortcutModifiers;
            this.isTopPanelKey = isTopPanelKey;
            this.customIconGlyph = "";
            this.allowTopPanelPagingGesture = true;
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId,
            float horizontalGap, boolean isRepeatable, boolean requiresShift, int shortcutModifiers, boolean isTopPanelKey) {
            this(label, symbolLabel, "", "", code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, shortcutModifiers, isTopPanelKey);
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable, boolean requiresShift) {
            this(label, symbolLabel, "", "", code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, -1, false);
        }

        Key(String label, String symbolLabel, int code, String codeStr, float widthPercent, int iconResId, float horizontalGap, boolean isRepeatable) {
            this(label, symbolLabel, code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, false);
        }
    }

    private static class AlternateOption {
        final String display;
        final int keyCode;
        final boolean requiresShift;
        final int modifierMask;
        /**
         * When non-zero, {@link #sendAlternateOption} sends this code point with
         * {@link HidTextKeystrokeSender} (Unicode entry) instead of {@link #keyCode}.
         */
        final int unicodeCodePoint;

        AlternateOption(String display, int keyCode, boolean requiresShift) {
            this(display, keyCode, requiresShift, 0, 0);
        }

        AlternateOption(String display, int keyCode, boolean requiresShift, int modifierMask) {
            this(display, keyCode, requiresShift, modifierMask, 0);
        }

        AlternateOption(String display, int keyCode, boolean requiresShift, int modifierMask, int unicodeCodePoint) {
            this.display = display;
            this.keyCode = keyCode;
            this.requiresShift = requiresShift;
            this.modifierMask = modifierMask;
            this.unicodeCodePoint = unicodeCodePoint;
        }
    }

    /** Long-press popup: 3×3 grid (see {@link AlternatePopupGeometry}). */
    private static class AlternatePopupModel {
        final AlternateOption[] slotOptions = new AlternateOption[AlternatePopupGeometry.SLOT_COUNT];
        /** Center cell: lift-in-inner-radius default (capital for a–z, primary label otherwise). */
        final AlternateOption defaultOption;

        AlternatePopupModel(AlternateOption[] srcSlots) {
            System.arraycopy(srcSlots, 0, this.slotOptions, 0, AlternatePopupGeometry.SLOT_COUNT);
            this.defaultOption = slotOptions[AlternatePopupGeometry.SLOT_CENTER];
        }

        int countSelectable() {
            int c = 0;
            for (AlternateOption o : slotOptions) {
                if (o != null) {
                    c++;
                }
            }
            return c;
        }

        boolean[] slotOccupiedForGestureFlags() {
            boolean[] b = new boolean[AlternatePopupGeometry.SLOT_COUNT];
            for (int i = 0; i < AlternatePopupGeometry.SLOT_COUNT; i++) {
                b[i] = slotOptions[i] != null;
            }
            // Empty diagonals inherit vertical cardinal for gesture tolerance only.
            if (!b[AlternatePopupGeometry.SLOT_UP_LEFT] && b[AlternatePopupGeometry.SLOT_UP]) {
                b[AlternatePopupGeometry.SLOT_UP_LEFT] = true;
            }
            if (!b[AlternatePopupGeometry.SLOT_UP_RIGHT] && b[AlternatePopupGeometry.SLOT_UP]) {
                b[AlternatePopupGeometry.SLOT_UP_RIGHT] = true;
            }
            if (!b[AlternatePopupGeometry.SLOT_DOWN_LEFT] && b[AlternatePopupGeometry.SLOT_DOWN]) {
                b[AlternatePopupGeometry.SLOT_DOWN_LEFT] = true;
            }
            if (!b[AlternatePopupGeometry.SLOT_DOWN_RIGHT] && b[AlternatePopupGeometry.SLOT_DOWN]) {
                b[AlternatePopupGeometry.SLOT_DOWN_RIGHT] = true;
            }
            return b;
        }

        int resolveGesturePick(int pick) {
            if (pick < 0 || pick >= AlternatePopupGeometry.SLOT_COUNT) {
                return pick;
            }
            if (slotOptions[pick] != null) {
                return pick;
            }
            if ((pick == AlternatePopupGeometry.SLOT_UP_LEFT || pick == AlternatePopupGeometry.SLOT_UP_RIGHT)
                    && slotOptions[AlternatePopupGeometry.SLOT_UP] != null) {
                return AlternatePopupGeometry.SLOT_UP;
            }
            if ((pick == AlternatePopupGeometry.SLOT_DOWN_LEFT || pick == AlternatePopupGeometry.SLOT_DOWN_RIGHT)
                    && slotOptions[AlternatePopupGeometry.SLOT_DOWN] != null) {
                return AlternatePopupGeometry.SLOT_DOWN;
            }
            return pick;
        }

        AlternateOption resolveCommittedOption(int pick) {
            if (pick == AlternatePopupGeometry.RESULT_DEFAULT) {
                return defaultOption;
            }
            int resolvedPick = resolveGesturePick(pick);
            if (resolvedPick < 0 || resolvedPick >= AlternatePopupGeometry.SLOT_COUNT) {
                return null;
            }
            return slotOptions[resolvedPick];
        }

        int defaultHighlightSlot() {
            return AlternatePopupGeometry.SLOT_CENTER;
        }
    }

    private static class TopShortcutPanel {
        String title;
        List<Key> keys;

        TopShortcutPanel(String title, List<Key> keys) {
            this.title = title;
            this.keys = keys;
        }
    }

    private static class ExtraGridKey {
        final Key key;
        final int row;
        final int col;
        final int rowSpan;
        final int colSpan;

        ExtraGridKey(Key key, int row, int col) {
            this(key, row, col, 1, 1);
        }

        ExtraGridKey(Key key, int row, int col, int rowSpan, int colSpan) {
            this.key = key;
            this.row = row;
            this.col = col;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
        }
    }

    public CustomKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomKeyboardView);
            try {
                showGuiHidKey = a.getBoolean(R.styleable.CustomKeyboardView_showGuiHidKey, true);
            } finally {
                a.recycle();
            }
        } else {
            showGuiHidKey = true;
        }
        init(context);
    }
    
    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Reload keyboard layout when orientation changes
        boolean isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        Log.v(TAG, "Orientation changed: landscape=" + isLandscape + ", reloading keyboard");
        kmProKeyPreview.dismiss();
        loadKeyboardForCurrentState(getContext());
        removeAllViews();
        updateKeyboard();
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        shortcutProfileManager = new ShortcutProfileManager(context.getApplicationContext());
        loadTopShortcutDisplayModeFromPrefs(context);
        SharedPreferences appPrefs = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);
        if (appPrefs.getBoolean(KEY_SYSTEM_IME_CAPTURE, false)
                || appPrefs.getBoolean(KEY_IME_SUB_COMPOSE_DIRECT_HID, false)) {
            appPrefs.edit()
                    .putBoolean(KEY_SYSTEM_IME_CAPTURE, false)
                    .putBoolean(KEY_IME_SUB_COMPOSE_DIRECT_HID, false)
                    .apply();
        }
        keyboardAlternatesHintsEnabled = KeyboardAlternatesHintsPrefs.read(context);
        kmProKeyTapPreviewEnabled = KmProKeyTapPreviewPrefs.read(context);

        // Load keyboard layout based on orientation (matching iOS behavior)
        reloadForCurrentOrientation();
        
        Log.v(TAG, "Parsed keyboard (landscape=" + isLandscape(context) + "): lowerKeys=" + lowerKeys.size());
        bindService(context);
        updateKeyboard();
    }

    public void reloadForCurrentOrientation() {
        Context context = getContext();
        loadKeyboardForCurrentState(context);
        kmProKeyPreview.dismiss();
        removeAllViews();
        updateKeyboard();
    }

    private void loadKeyboardForCurrentState(Context context) {
        int keyboardResId;
        if (showGuiHidKey) {
            keyboardResId = isLandscape(context)
                    ? R.xml.keyboard_lower_landscape
                    : R.xml.keyboard_lower_portrait;
        } else {
            if (isLandscape(context)) {
                String targetOs =
                        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                                .getString("target_os", "macos");
                if ("macos".equals(targetOs)) {
                    keyboardResId = R.xml.keyboard_lower_landscape_no_gui_mac;
                } else if ("windows".equals(targetOs) || "linux".equals(targetOs)) {
                    keyboardResId = R.xml.keyboard_lower_landscape_no_gui_pc;
                } else {
                    keyboardResId = R.xml.keyboard_lower_landscape_no_gui;
                }
            } else {
                keyboardResId = R.xml.keyboard_lower_portrait_no_gui;
            }
        }
        lowerKeys = parseKeyboard(context, keyboardResId);
        applyTargetOsLabels(context);
        if (!showGuiHidKey) {
            applyProQwertyDisplayModePresentation(context);
        }
    }

    /** Update key labels based on target OS (Win → Cmd for macOS) */
    public void reloadForTargetOs() {
        Context context = getContext();
        if (context == null) return;
        loadKeyboardForCurrentState(context);
        kmProKeyPreview.dismiss();
        removeAllViews();
        updateKeyboard();
    }

    private void applyTargetOsLabels(Context context) {
        String targetOs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");

        String guiLabel;
        int guiIconResId;
        if ("macos".equals(targetOs)) {
            guiLabel = context.getString(R.string.modifier_command);
            guiIconResId = R.drawable.keyboard_command_key_24px;
        } else if ("linux".equals(targetOs)) {
            guiLabel = context.getString(R.string.Super);
            guiIconResId = R.drawable.ic_os_linux;
        } else {
            guiLabel = context.getString(R.string.Win);
            guiIconResId = R.drawable.windows;
        }

        for (List<Key> row : lowerKeys) {
            for (Key key : row) {
                if (key == null) {
                    continue;
                }
                if (key.code == 0xE0) {
                    key.label = context.getString(R.string.modifier_control);
                    key.iconResId = 0;
                } else if (key.code == 0xE2) {
                    key.label = "macos".equals(targetOs)
                            ? context.getString(R.string.modifier_option)
                            : context.getString(R.string.Alt);
                    key.iconResId = 0;
                } else if (key.code == 0xE3) {
                    key.label = guiLabel;
                    key.iconResId = guiIconResId;
                } else if (key.code == 0xE4) {
                    key.label = context.getString(R.string.modifier_control);
                    key.iconResId = 0;
                } else if (key.code == 0xE6) {
                    key.label = "macos".equals(targetOs)
                            ? context.getString(R.string.modifier_option)
                            : context.getString(R.string.Alt);
                    key.iconResId = 0;
                } else if (key.code == 0xE7) {
                    key.label = guiLabel;
                    key.iconResId = guiIconResId;
                } else if ("Win".equals(key.label)) {
                    // Legacy layouts: @string/Win resolved to the literal "Win" (English).
                    key.label = guiLabel;
                    key.iconResId = guiIconResId;
                }
            }
        }
    }

    /**
     * Keyboard &amp; Mouse Pro (no-GUI): letter-grid keys follow Keys display (names / icons / chord)
     * like the shortcut strip. Call after {@link #applyTargetOsLabels(Context)}.
     */
    private void applyProQwertyDisplayModePresentation(Context context) {
        if (showGuiHidKey || lowerKeys == null) {
            return;
        }
        String targetOs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
        final int mode = topShortcutDisplayMode;

        String guiNameLabel;
        int guiIconResId;
        if ("macos".equals(targetOs)) {
            guiNameLabel = context.getString(R.string.modifier_command);
            guiIconResId = R.drawable.keyboard_command_key_24px;
        } else if ("linux".equals(targetOs)) {
            guiNameLabel = context.getString(R.string.Super);
            guiIconResId = R.drawable.ic_os_linux;
        } else {
            guiNameLabel = context.getString(R.string.Win);
            guiIconResId = R.drawable.windows;
        }

        for (List<Key> row : lowerKeys) {
            for (Key key : row) {
                if (key == null) {
                    continue;
                }
                if (mode == DISPLAY_MODE_ICON) {
                    if (key.code == 0x2B) {
                        key.label = "";
                        key.iconResId = R.drawable.keyboard_tab_24;
                    } else if (key.code == 0xE0) {
                        key.label = context.getString(R.string.modifier_control);
                        key.iconResId = 0;
                    } else if (key.code == 0xE2) {
                        key.label = "macos".equals(targetOs)
                                ? context.getString(R.string.modifier_option)
                                : context.getString(R.string.Alt);
                        key.iconResId = 0;
                    } else if (key.code == 0xE3) {
                        key.label = guiNameLabel;
                        key.iconResId = guiIconResId;
                    } else if (key.code == 0x2C) {
                        key.label = "";
                        key.iconResId = R.drawable.space_bar_24px;
                    } else if (key.code == 0xE1) {
                        key.label = context.getString(R.string.Shift);
                        key.iconResId = R.drawable.shift_24px;
                    } else if (key.code == 0x28) {
                        key.label = context.getString(R.string.Enter_Button);
                        key.iconResId = R.drawable.keyboard_return_24px;
                    } else if (key.code == 0x2A) {
                        key.label = context.getString(R.string.BackSpace);
                        key.iconResId = R.drawable.backspace;
                    } else if (key.code == 0x4C) {
                        key.label = "";
                        key.iconResId = R.drawable.backspace;
                    } else if (key.code == 0xE4) {
                        key.label = context.getString(R.string.modifier_control);
                        key.iconResId = 0;
                    } else if (key.code == 0xE6) {
                        key.label = "macos".equals(targetOs)
                                ? context.getString(R.string.modifier_option)
                                : context.getString(R.string.Alt);
                        key.iconResId = 0;
                    } else if (key.code == 0xE7) {
                        key.label = guiNameLabel;
                        key.iconResId = guiIconResId;
                    } else if (key.code == 0x65) {
                        key.label = "";
                        key.iconResId = R.drawable.ic_list_alt_24;
                    }
                } else if (mode == DISPLAY_MODE_NAME || mode == DISPLAY_MODE_CHORD) {
                    if (key.code == 0x2B) {
                        key.label = context.getString(R.string.key_display_name_tab);
                        key.iconResId = 0;
                    } else if (key.code == 0xE0) {
                        key.label = context.getString(R.string.modifier_control);
                        key.iconResId = 0;
                    } else if (key.code == 0xE2) {
                        key.label = "macos".equals(targetOs)
                                ? context.getString(R.string.modifier_option)
                                : context.getString(R.string.Alt);
                        key.iconResId = 0;
                    } else if (key.code == 0xE3) {
                        key.label = guiNameLabel;
                        key.iconResId = 0;
                    } else if (key.code == 0x2C) {
                        key.label = context.getString(R.string.Space_Button);
                        key.iconResId = 0;
                    } else if (key.code == 0xE1) {
                        key.label = context.getString(R.string.Shift);
                        key.iconResId = 0;
                    } else if (key.code == 0x28) {
                        key.label = context.getString(R.string.Enter_Button);
                        key.iconResId = 0;
                    } else if (key.code == 0x2A) {
                        key.label = context.getString(R.string.key_display_name_bksp);
                        key.iconResId = 0;
                    } else if (key.code == 0x4C) {
                        key.label = context.getString(R.string.Delete);
                        key.iconResId = 0;
                    } else if (key.code == 0xE4) {
                        key.label = context.getString(R.string.modifier_control);
                        key.iconResId = 0;
                    } else if (key.code == 0xE6) {
                        key.label = "macos".equals(targetOs)
                                ? context.getString(R.string.modifier_option)
                                : context.getString(R.string.Alt);
                        key.iconResId = 0;
                    } else if (key.code == 0xE7) {
                        key.label = guiNameLabel;
                        key.iconResId = 0;
                    } else if (key.code == 0x65) {
                        key.label = "App";
                        key.iconResId = 0;
                    }
                }
            }
        }
    }

    /** Split partner: sync display mode, strip panels, and QWERTY row after primary updates prefs. */
    private void syncTopShortcutDisplayModeFromPrimary(int normalized) {
        topShortcutDisplayMode = normalized;
        rebuildFixedTopRowsPanels();
        rebuildTopShortcutPanels();
        syncTopPanelViewportContent();
        Context ctx = getContext();
        if (ctx != null && !showGuiHidKey) {
            applyProQwertyDisplayModePresentation(ctx);
        }
        removeAllViews();
        updateKeyboard();
    }

    /** After prefs sync (Shortcut Hub / KM Pro setup): refresh bottom-row presentation and full layout. */
    private void refreshProQwertyLetterGridOnlySelf() {
        Context ctx = getContext();
        if (ctx != null && !showGuiHidKey) {
            applyProQwertyDisplayModePresentation(ctx);
        }
        removeAllViews();
        updateKeyboard();
    }

    public void setShowExtraPortraitKeys(boolean enabled) {
        if (showExtraPortraitKeys == enabled) return;
        if (!enabled) {
            extraNumpadFnLocked = false;
        }
        showExtraPortraitKeys = enabled;
        removeAllViews();
        updateKeyboard();
    }

    /**
     * Compose mode: render only the same top shortcut rows as Keyboard &amp; Mouse, no letter grid.
     */
    public void setShortcutsStripOnly(boolean enabled) {
        if (shortcutsStripOnly == enabled) return;
        shortcutsStripOnly = enabled;
        removeAllViews();
        updateKeyboard();
    }

    /** Set which half of the keyboard to render (for landscape split mode). */
    public void setSplitPart(int part) {
        if (splitPart == part) return;
        splitPart = part;
        // Reload keys to restore original widths before re-filtering
        loadKeyboardForCurrentState(getContext());
        removeAllViews();
        updateKeyboard();
    }

    /** Set the paired keyboard view in split mode for syncing modifier states. */
    public void setSplitPartner(CustomKeyboardView partner) {
        splitPartner = partner;
    }

    /** KM Pro: host swipe-up modifier locks (injected from {@link com.openterface.fragment.CompositeFragment}). */
    public void setHoldLockController(@Nullable KmBasicHoldLockController controller) {
        if (holdLockController != null) {
            holdLockController.removeListener(proHoldLockListener);
        }
        holdLockController = controller;
        if (holdLockController != null) {
            holdLockController.addListener(proHoldLockListener);
        }
    }

    private void dismissProHoldLockPopup() {
        if (activeHoldLockPopup != null) {
            activeHoldLockPopup.dismiss();
            activeHoldLockPopup = null;
        }
        holdLockPopupAnchorView = null;
        holdLockFingerDown = false;
    }

    private void onProHoldLockPopupTimeout() {
        if (holdLockPopupAnchorView == null || !holdLockFingerDown) {
            return;
        }
        activeHoldLockPopup = new BasicHoldLockPopup();
        activeHoldLockPopup.show(holdLockPopupAnchorView, holdLockGestureRawX, holdLockGestureRawY);
    }

    private void clearProChordUiPreserveHostForLock() {
        longPressHandler.removeCallbacks(proChordLongPressRunnable);
        proChordSustainBootByView.clear();
        proChordHostHoldSent = false;
        proChordActiveExtKey = 0;
        proChordLongPressAnchorView = null;
    }

    private void clearProChordAndHoldLockPopupUiState() {
        longPressHandler.removeCallbacks(proChordLongPressRunnable);
        longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
        dismissProHoldLockPopup();
        if (proChordHostHoldSent || !proChordSustainBootByView.isEmpty()) {
            sendKeyboardAllKeysReleasedSync();
        }
        proChordSustainBootByView.clear();
        proChordHostHoldSent = false;
        proChordActiveExtKey = 0;
        proChordLongPressAnchorView = null;
        if (holdLockController != null && transport instanceof HidKeyboardTransport) {
            holdLockController.reassertKeyboardModifiersIfNeeded(
                    port, bluetoothService, isServiceBound);
        }
    }

    /** Split primary: clear partner ephemeral chord UI (does not clear this side’s host hold-locks). */
    private void clearPartnerChordStateFromPrimary() {
        if (splitPartner == null) {
            return;
        }
        splitPartner.clearPartnerChordStateFromRemote();
    }

    private void clearPartnerChordStateFromRemote() {
        longPressHandler.removeCallbacks(proChordLongPressRunnable);
        if (proChordHostHoldSent || !proChordSustainBootByView.isEmpty()) {
            sendKeyboardAllKeysReleasedSync();
        }
        proChordSustainBootByView.clear();
        proChordHostHoldSent = false;
        proChordActiveExtKey = 0;
        proChordLongPressAnchorView = null;
        if (holdLockController != null && transport instanceof HidKeyboardTransport) {
            holdLockController.reassertKeyboardModifiersIfNeeded(
                    port, bluetoothService, isServiceBound);
        }
        refreshVisibleTopPanelButtonStates();
        post(this::refreshBuiltInModifierKeyCapsFromTree);
    }

    private void onKmProModifierPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!isAttachedToWindow()) {
            return;
        }
        if (KmBasicKeyboardPrefs.PREF_CHORD_SUSTAIN_HID.equals(key)) {
            clearProChordAndHoldLockPopupUiState();
            refreshVisibleTopPanelButtonStates();
            post(this::refreshBuiltInModifierKeyCapsFromTree);
            return;
        }
        if (KmBasicKeyboardPrefs.PREF_KEY.equals(key)) {
            clearProChordAndHoldLockPopupUiState();
            if (holdLockController != null && transport instanceof HidKeyboardTransport) {
                holdLockController.clearAllAndReleaseHid(port, bluetoothService, isServiceBound);
            }
            isShiftLeftLocked = false;
            isCtrlLeftLocked = false;
            isAltLeftLocked = false;
            isWinLeftLocked = false;
            isCtrlRightLocked = false;
            isAltRightLocked = false;
            isWinRightLocked = false;
            syncModifierStates();
            updateKeyboard();
        }
    }

    private void onProChordLongPressThreshold() {
        if (proChordLongPressAnchorView == null) {
            return;
        }
        View anchor = proChordLongPressAnchorView;
        int boot = proChordLongPressPendingMask;
        if (boot == 0) {
            boot = bootModifierMaskForBuiltInExtendedKey(proChordActiveExtKey);
        }
        if (boot == 0) {
            return;
        }
        proChordSustainBootByView.put(anchor, boot);
        Context ctx = getContext();
        if (ctx != null && KmBasicKeyboardPrefs.isChordSustainHidEnabled(ctx)) {
            int agg = proChordSustainAggregateBootMaskOr0();
            if (agg != 0) {
                if (transport != null && transport.isConnected()) {
                    transport.sendKey(mergeHoldLockedBootMask(agg), 0);
                }
            } else if (proChordActiveExtKey != 0) {
                if (transport != null && transport.isConnected()) {
                    transport.sendKey(mergeHoldLockedBootMask(0), proChordActiveExtKey);
                }
            }
            proChordHostHoldSent = true;
        } else {
            proChordHostHoldSent = false;
        }
        refreshVisibleTopPanelButtonStates();
        post(this::refreshBuiltInModifierKeyCapsFromTree);
    }

    private void reassertKeyboardAfterHidRelease() {
        if (holdLockController != null && transport instanceof HidKeyboardTransport) {
            holdLockController.reassertKeyboardModifiersIfNeeded(port, bluetoothService, isServiceBound);
        }
        Context ctx = getContext();
        if (ctx != null
                && KmBasicKeyboardPrefs.isMomentaryChordMode(ctx)
                && KmBasicKeyboardPrefs.isChordSustainHidEnabled(ctx)
                && !proChordSustainBootByView.isEmpty()) {
            int agg = proChordSustainAggregateBootMaskOr0();
            if (agg != 0) {
                if (transport != null && transport.isConnected()) {
                    transport.sendKey(mergeHoldLockedBootMask(agg), 0);
                }
            } else if (proChordActiveExtKey != 0) {
                if (transport != null && transport.isConnected()) {
                    transport.sendKey(mergeHoldLockedBootMask(0), proChordActiveExtKey);
                }
            }
            proChordHostHoldSent = true;
        }
    }

    private int bootModifierMaskForBuiltInExtendedKey(int extKey) {
        switch (extKey) {
            case 0xE0:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
            case 0xE1:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
            case 0xE2:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
            case 0xE3:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
            case 0xE4:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("CtrlR"));
            case 0xE5:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("ShiftR"));
            case 0xE6:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("AltR"));
            case 0xE7:
                return parseHex(CH9329MSKBMap.KBShortCutKey().get("WinR"));
            default:
                return 0;
        }
    }

    private int proChordSustainAggregateBootMaskOr0() {
        int agg = 0;
        for (int i = 0; i < proChordSustainBootByView.size(); i++) {
            agg |= proChordSustainBootByView.valueAt(i);
        }
        return agg;
    }

    private void releaseProChordSustainFingerForView(@Nullable View v) {
        if (v == null || !proChordSustainBootByView.containsKey(v)) {
            return;
        }
        proChordSustainBootByView.remove(v);
        if (!proChordHostHoldSent) {
            refreshVisibleTopPanelButtonStates();
            post(this::refreshBuiltInModifierKeyCapsFromTree);
            return;
        }
        sendKeyboardAllKeysReleasedSync();
        if (!proChordSustainBootByView.isEmpty()) {
            int agg = proChordSustainAggregateBootMaskOr0();
            if (agg != 0) {
                if (transport != null && transport.isConnected()) {
                    transport.sendKey(mergeHoldLockedBootMask(agg), 0);
                }
            } else if (holdLockController != null && transport instanceof HidKeyboardTransport) {
                holdLockController.reassertKeyboardModifiersIfNeeded(
                        port, bluetoothService, isServiceBound);
            }
            proChordHostHoldSent = true;
        } else {
            if (holdLockController != null && transport instanceof HidKeyboardTransport) {
                holdLockController.reassertKeyboardModifiersIfNeeded(
                        port, bluetoothService, isServiceBound);
            }
            proChordHostHoldSent = false;
        }
        refreshVisibleTopPanelButtonStates();
        post(this::refreshBuiltInModifierKeyCapsFromTree);
    }

    private int getHoldLockedBootModMaskOr0() {
        if (holdLockController == null) {
            return 0;
        }
        return holdLockController.getLockedModMask();
    }

    private int mergeHoldLockedBootMask(int modifiers) {
        return modifiers | getHoldLockedBootModMaskOr0();
    }

    /** ORs chord-held boot modifier bits into HID sends while chord fingers are down (KM Basic parity). */
    private int mergeChordHeldBootMask(int modifiers) {
        Context ctx = getContext();
        if (ctx == null || !KmBasicKeyboardPrefs.isMomentaryChordMode(ctx)) {
            return modifiers;
        }
        return modifiers | proChordSustainAggregateBootMaskOr0();
    }

    private boolean isProBuiltInModifierTouchKey(Key key) {
        if (key == null || key.isTopPanelKey) {
            return false;
        }
        if (isFnAlternateHintsToggleKey(key)) {
            return false;
        }
        int c = key.code;
        if (c == 0xE0 || c == 0xE1 || c == 0xE2 || c == 0xE3) {
            return true;
        }
        // KM Pro no-GUI landscape: right extended modifiers (CtrlR / AltR / WinR).
        return !showGuiHidKey && (c == 0xE4 || c == 0xE6 || c == 0xE7);
    }

    private void tapProModifierMomentary(Key key) {
        if (key == null) {
            return;
        }
        sendKeyData(0, key.code);
        maybeShowModifierLockHint(key);
    }

    private void reassertLockedKeyboardAfterAllKeysReleased() {
        reassertKeyboardAfterHidRelease();
    }

    public void setOnTopModeShortcutListener(OnTopModeShortcutListener listener) {
        onTopModeShortcutListener = listener;
    }

    public void setOnKmProSecondaryLayoutToggleListener(
            @Nullable OnKmProSecondaryLayoutToggleListener listener) {
        onKmProSecondaryLayoutToggleListener = listener;
    }

    /**
     * KM Pro portrait: show or hide only the main letter-key rows while keeping the fixed shortcut strip
     * (Shortcut Hub row + fixed rows with P1R2C7) visible. {@link View#INVISIBLE} preserves layout weight
     * for alignment with the IME slot.
     */
    public void setKmProPortraitLetterBodyVisible(boolean visible) {
        if (kmProLetterKeyboardBody != null) {
            kmProLetterKeyboardBody.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    /**
     * Portrait KM Pro full built-in QWERTY: bottom-centered Openterface wordmark below letter rows.
     * Matches KM Basic portrait touchpad wordmark size/tint/alpha ({@code fragment_basic_touchpad}); margins
     * from {@link R.dimen#km_pro_keyboard_brand_footer_margin_top} and
     * {@link R.dimen#km_pro_keyboard_brand_footer_margin_bottom} keep the band compact so
     * {@link #kmProLetterKeyboardBody} (weight 1) receives more vertical space.
     */
    private void maybeAddKmProPortraitKeyboardBrandFooter(@Nullable LinearLayout letterBodyContainer) {
        if (letterBodyContainer == null
                || splitPart != SPLIT_NONE
                || shortcutsStripOnly
                || showExtraPortraitKeys
                || isLandscape(getContext())) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        ImageView brand = new ImageView(ctx);
        brand.setImageResource(R.drawable.ic_openterface_wordmark);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.setAdjustViewBounds(true);
        Resources res = ctx.getResources();
        int h = res.getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_height);
        int maxW = res.getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_max_width);
        int topMargin = res.getDimensionPixelSize(R.dimen.km_pro_keyboard_brand_footer_margin_top);
        int bottomMargin =
                res.getDimensionPixelSize(R.dimen.km_pro_keyboard_brand_footer_margin_bottom);
        brand.setMaxWidth(maxW);
        brand.setPadding(0, 0, 0, 0);
        brand.setAlpha(0.82f);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        brand.setLayoutParams(lp);
        brand.setColorFilter(
                ContextCompat.getColor(ctx, R.color.text_secondary), PorterDuff.Mode.SRC_IN);
        brand.setContentDescription(ctx.getString(R.string.touch_pad_brand_content_description));
        addView(brand);
        kmProPortraitBrandFooter = brand;
    }

    public void setOnImeCaptureModeChangedListener(@Nullable OnImeCaptureModeChangedListener listener) {
    }

    public void setOnImeSubComposeChromeListener(@Nullable OnImeSubComposeChromeListener listener) {
    }

    public boolean isImeSubComposeExpanded() {
        return false;
    }

    public boolean isImeSubComposeDirectHidMode() {
        return false;
    }

    public boolean isSystemImeCaptureMode() {
        return false;
    }

    public KmProKeyboardInputMode getKmProKeyboardInputMode() {
        return KmProKeyboardInputMode.BUILT_IN_QWERTY;
    }

    public void setPortraitSystemImeVisible(boolean visible) {
    }

    public void setKmProKeyboardInputModeFromHost(@NonNull KmProKeyboardInputMode target) {
    }

    public void applyImeCaptureFromPartner(boolean enabled) {
    }

    public void forceKmProBuiltInKeyboardModeForLandscapeGuard() {
    }

    public void resyncImeSubComposeDirectHidFromPrefs() {
    }

    public void cancelDeferredKmProLayoutFinish() {
    }

    public void bindSplitLandscapeImeComposeRail(
            @NonNull EditText externalEdit,
            @Nullable ImageButton railToggle,
            @Nullable ImageButton railUndo,
            @Nullable ImageButton railClear,
            @Nullable ImageButton railSaved,
            @Nullable ImageButton railSend) {
    }

    public void clearSplitLandscapeImeComposeRail() {
    }

    public void onSplitLandscapeImeRailSavedClicked() {
    }

    public void onSplitLandscapeImeRailModeToggleClicked() {
    }

    public void onSplitLandscapeImeRailUndoClicked() {
    }

    public void onSplitLandscapeImeRailClearClicked() {
    }

    public void onSplitLandscapeImeRailSendClicked() {
    }

    /** Rebuild top shortcut panels after PH slot mode prefs change (no full keyboard reload). */
    public void refreshTopShortcutModeSlots() {
        rebuildTopShortcutPanels();
        if (splitPartner != null) {
            splitPartner.rebuildTopShortcutPanels();
        }
        syncTopPanelViewportContent();
    }

    /** Reload Shortcut Hub profiles from storage into this view's manager (call split partner too if needed). */
    public void reloadShortcutProfileManagerFromPrefs() {
        shortcutProfileManager.reloadProfilesFromPreferences();
    }

    /**
     * Reload profiles, strip display mode, and rebuild fixed + scrolling strip after Shortcut Hub
     * imports a profile or strip preset JSON.
     */
    public void refreshAfterShortcutHubPrefsChange() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        reloadShortcutProfileManagerFromPrefs();
        loadTopShortcutDisplayModeFromPrefs(ctx);
        refreshProfileSlotStrip();
        if (splitPartner != null) {
            splitPartner.reloadShortcutProfileManagerFromPrefs();
            splitPartner.loadTopShortcutDisplayModeFromPrefs(splitPartner.getContext());
            splitPartner.refreshProfileSlotStrip();
            splitPartner.refreshProQwertyLetterGridOnlySelf();
        }
        refreshProQwertyLetterGridOnlySelf();
    }

    /** Rebuild fixed strip + scrolling row 1 after profile-slot prefs or active profile change. */
    private void refreshProfileSlotStrip() {
        rebuildFixedTopRowsPanels();
        rebuildTopShortcutPanels();
        if (splitPartner != null) {
            splitPartner.rebuildFixedTopRowsPanels();
            splitPartner.rebuildTopShortcutPanels();
        }
        syncTopPanelViewportContent();
        refreshVisibleTopPanelButtonStates();
        if (splitPartner != null) {
            splitPartner.refreshVisibleTopPanelButtonStates();
        }
    }

    private void refreshTopShortcutFavoriteStrip() {
        rebuildTopShortcutPanels();
        if (splitPartner != null) {
            splitPartner.rebuildTopShortcutPanels();
        }
        syncTopPanelViewportContent();
        refreshVisibleTopPanelButtonStates();
        if (splitPartner != null) {
            splitPartner.refreshVisibleTopPanelButtonStates();
        }
    }

    private static int topModeSlotIndexFromKeyCode(int code) {
        if (code == KEY_TOP_MODE_SLOT_1) {
            return 1;
        }
        if (code == KEY_TOP_MODE_SLOT_2) {
            return 2;
        }
        if (code == KEY_TOP_MODE_SLOT_3) {
            return 3;
        }
        return 0;
    }

    private static boolean isTopModeSlotKey(Key key) {
        return key != null && topModeSlotIndexFromKeyCode(key.code) > 0;
    }

    private static boolean isTopShortcutToggleKey(Key key) {
        return key != null
                && key.isTopPanelKey
                && key.code == KEY_TOP_SHORTCUT_DISPLAY_TOGGLE;
    }

    private static boolean isFixedTopLocalFnKey(Key key) {
        return key != null
                && key.isTopPanelKey
                && key.code == KEY_FIXED_TOP_LOCAL_FN;
    }

    /**
     * Top-strip modifier keys (Ctrl / Shift / Alt / Win): long-press toggles lock. They appear on
     * the swipeable favorites row and on fixed strip page 1 (row 2); page 0 is F‑keys only.
     */
    private static boolean isTopModifierLockCandidate(Key key) {
        return key != null
                && key.isTopPanelKey
                && (key.code == 0xE0 || key.code == 0xE1 || key.code == 0xE2 || key.code == 0xE3);
    }

    private static boolean isTopImeToggleKey(Key key) {
        return key != null && key.isTopPanelKey && key.code == KEY_IME_TOGGLE;
    }

    private static boolean isTopShortcutActionLabelEligible(Key key) {
        if (key == null || !key.isTopPanelKey || key.code == KEY_NOOP_PLACEHOLDER
                || key.code == KEY_TOP_SHORTCUT_DISPLAY_TOGGLE
                || key.code == KEY_FIXED_TOP_LOCAL_FN) {
            return false;
        }
        // Fixed strip icon keys use shortcutModifiers == -1; still honor strip display mode (name/icon/chord).
        if (key.code == 0xE1 // Shift
                || key.code == 0x4C // Del / backspace
                || key.code == KEY_IME_TOGGLE
                || key.code == 0x52 || key.code == 0x28 // Up, Enter
                || key.code == 0x50 || key.code == 0x51 || key.code == 0x4F) { // Arrows
            return true;
        }
        return key.shortcutModifiers >= 0 || key.code == 0x2B;
    }

    private boolean isTopPanelModifierKey(Key key) {
        if (key == null || !key.isTopPanelKey) {
            return false;
        }
        return key.code == 0xE0 || key.code == 0xE2 || key.code == 0xE3;
    }

    private void loadTopShortcutDisplayModeFromPrefs(Context context) {
        topShortcutDisplayMode = TopShortcutDisplayModePrefs.readMode(context);
    }

    private void setTopShortcutDisplayMode(int mode) {
        int normalized = TopShortcutDisplayModePrefs.clamp(mode);
        if (topShortcutDisplayMode == normalized) {
            return;
        }
        topShortcutDisplayMode = normalized;
        Context context = getContext();
        if (context != null) {
            TopShortcutDisplayModePrefs.writeMode(context, normalized);
        }
        rebuildFixedTopRowsPanels();
        rebuildTopShortcutPanels();
        syncTopPanelViewportContent();
        if (splitPartner != null) {
            splitPartner.syncTopShortcutDisplayModeFromPrimary(normalized);
        }
        if (context != null && !showGuiHidKey) {
            applyProQwertyDisplayModePresentation(context);
        }
        removeAllViews();
        updateKeyboard();
    }

    private void cycleTopShortcutDisplayMode() {
        int next = topShortcutDisplayMode + 1;
        if (next > DISPLAY_MODE_CHORD) {
            next = DISPLAY_MODE_NAME;
        }
        setTopShortcutDisplayMode(next);
    }

    /** Row-1 DISPLAY key icon reflects current strip display mode. */
    private int resolveDisplayToggleKeyIconRes() {
        switch (topShortcutDisplayMode) {
            case DISPLAY_MODE_NAME:
                return R.drawable.text_on_24;
            case DISPLAY_MODE_ICON:
                return R.drawable.icon_on_24;
            case DISPLAY_MODE_CHORD:
            default:
                return R.drawable.display_mode_chord_text_k_24;
        }
    }

    private boolean stripNameMode(Key k) {
        return isTopShortcutActionLabelEligible(k) && topShortcutDisplayMode == DISPLAY_MODE_NAME;
    }

    private boolean stripIconMode(Key k) {
        return isTopShortcutActionLabelEligible(k) && topShortcutDisplayMode == DISPLAY_MODE_ICON;
    }

    private boolean stripChordMode(Key k) {
        return isTopShortcutActionLabelEligible(k) && topShortcutDisplayMode == DISPLAY_MODE_CHORD;
    }

    private String formatTopShortcutActionLabel(Key key) {
        if (key == null) {
            return "";
        }
        if (key.code == KEY_IME_TOGGLE) {
            return key.label != null ? key.label : "";
        }
        if (key.symbolLabel != null) {
            String chord = key.symbolLabel.trim();
            if (!chord.isEmpty()) {
                return chord;
            }
        }
        String keyPart = topShortcutKeyCodeLabel(key.code, key.label);
        if (keyPart.isEmpty()) {
            return key.label != null ? key.label : "";
        }
        if (key.shortcutModifiers <= 0) {
            return keyPart;
        }
        String targetOs = getTargetOs();
        if ("macos".equals(targetOs)) {
            String modifierGlyphs = shortcutModifierGlyphs(key.shortcutModifiers);
            if (!modifierGlyphs.isEmpty()) {
                return modifierGlyphs + keyPart;
            }
        }
        String modifierText = shortcutModifierText(key.shortcutModifiers, targetOs);
        if (modifierText.isEmpty()) {
            return keyPart;
        }
        return modifierText + "+" + keyPart;
    }

    private String shortcutModifierGlyphs(int modifiers) {
        StringBuilder out = new StringBuilder();
        if ((modifiers & MOD_CTRL) != 0) out.append("⌃");
        if ((modifiers & MOD_ALT) != 0) out.append("⌥");
        if ((modifiers & MOD_SHIFT) != 0) out.append("⇧");
        if ((modifiers & MOD_WIN) != 0) out.append("⌘");
        return out.toString();
    }

    private String shortcutModifierText(int modifiers, String targetOs) {
        List<String> parts = new ArrayList<>(4);
        if ((modifiers & MOD_CTRL) != 0) parts.add("Ctrl");
        if ((modifiers & MOD_ALT) != 0) parts.add("Alt");
        if ((modifiers & MOD_SHIFT) != 0) parts.add("Shift");
        if ((modifiers & MOD_WIN) != 0) {
            if ("macos".equals(targetOs)) {
                parts.add("Cmd");
            } else if ("linux".equals(targetOs)) {
                parts.add("Super");
            } else {
                parts.add("Win");
            }
        }
        return TextUtils.join("+", parts);
    }

    private String topShortcutKeyCodeLabel(int keyCode, String fallbackLabel) {
        switch (keyCode) {
            case 0x04: return "A";
            case 0x06: return "C";
            case 0x16: return "S";
            case 0x19: return "V";
            case 0x1B: return "X";
            case 0x1D: return "Z";
            case 0x2B: return "Tab";
            default:
                if (fallbackLabel == null) {
                    return "";
                }
                return fallbackLabel;
        }
    }

    private Key modeSlotKey(int slotIndex1Based) {
        int keyCode = KEY_TOP_MODE_SLOT_1 + (slotIndex1Based - 1);
        Context ctx = getContext();
        int icon = R.drawable.keyboard_mouse;
        if (ctx != null) {
            String mode = TopModeShortcutPrefs.getModeForSlot(ctx, slotIndex1Based);
            icon = TopModeShortcutPrefs.iconResForMode(mode);
        }
        String codeStr = Integer.toHexString(keyCode).toUpperCase();
        return new Key("", "", keyCode, codeStr, 1f, icon, 0f, false, false, -1, true);
    }

    /**
     * View context is often a {@link android.view.ContextThemeWrapper}, not the activity itself.
     */
    @Nullable
    private static AppCompatActivity unwrapAppCompatActivity(@Nullable Context context) {
        Context c = context;
        while (c != null) {
            if (c instanceof AppCompatActivity) {
                return (AppCompatActivity) c;
            }
            if (c instanceof ContextWrapper) {
                Context base = ((ContextWrapper) c).getBaseContext();
                if (base == c) {
                    break;
                }
                c = base;
            } else {
                break;
            }
        }
        return null;
    }

    private void showTopModeSlotPicker(int slotIndex1Based) {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        if (act == null) {
            return;
        }
        Context ctx = act;
        String[] modes = TopModeShortcutPrefs.getSelectableModes();
        CharSequence[] labels = TopModeShortcutPrefs.getModeLabels(ctx);
        String current = TopModeShortcutPrefs.getModeForSlot(ctx, slotIndex1Based);
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(act)
                .setTitle(R.string.top_mode_slot_picker_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    TopModeShortcutPrefs.setModeForSlot(act.getApplicationContext(), slotIndex1Based, modes[which]);
                    refreshTopShortcutModeSlots();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Long-press entry for editing rows 2–3 strip layout (opens Keyboard and Mouse Pro setup).
     */
    private void showStripLayoutEditionEntry() {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        if (act == null) {
            return;
        }
        if (stripLayoutEditionDialogShowing) {
            return;
        }
        stripLayoutEditionDialogShowing = true;
        if (splitPartner != null) {
            splitPartner.stripLayoutEditionDialogShowing = true;
        }
        Context ctx = getContext();
        if (ctx == null) {
            stripLayoutEditionDialogShowing = false;
            if (splitPartner != null) {
                splitPartner.stripLayoutEditionDialogShowing = false;
            }
            return;
        }
        int overrideCount = new Rows23StripProfileManager(ctx, shortcutProfileManager).getActiveOverrideSlotCount();
        AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle(R.string.strip_edition_dialog_title)
                .setMessage(ctx.getString(R.string.strip_edition_dialog_message, overrideCount))
                .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                .create();
        dialog.setOnDismissListener(di -> {
            stripLayoutEditionDialogShowing = false;
            if (splitPartner != null) {
                splitPartner.stripLayoutEditionDialogShowing = false;
            }
        });
        dialog.show();
    }

    private void showTopStripFavoriteReorderSheet(int scrollToStripIndex) {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        if (act == null) {
            return;
        }
        reloadShortcutProfileManagerFromPrefs();
        if (splitPartner != null) {
            splitPartner.reloadShortcutProfileManagerFromPrefs();
        }
        ShortcutProfileManager.ShortcutProfile ap = shortcutProfileManager.getActiveProfile();
        if (ap == null) {
            return;
        }
        MyShortcutsReorderBottomSheet.show(
                act,
                shortcutProfileManager,
                ap.id,
                getTargetOs(),
                scrollToStripIndex,
                () -> {
                    reloadShortcutProfileManagerFromPrefs();
                    refreshTopShortcutFavoriteStrip();
                    if (splitPartner != null) {
                        splitPartner.reloadShortcutProfileManagerFromPrefs();
                        splitPartner.refreshTopShortcutFavoriteStrip();
                    }
                });
    }

    private void showCreateShortcutFromStrip() {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        if (act == null) {
            return;
        }
        reloadShortcutProfileManagerFromPrefs();
        if (splitPartner != null) {
            splitPartner.reloadShortcutProfileManagerFromPrefs();
        }
        ShortcutProfileManager.ShortcutProfile ap = shortcutProfileManager.getActiveProfile();
        if (ap == null) {
            Toast.makeText(getContext(), R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }
        String targetOs = getTargetOs();
        CreateShortcutBottomSheet.show(act, shortcutProfileManager, ap.id, targetOs, () -> {
            reloadShortcutProfileManagerFromPrefs();
            refreshTopShortcutFavoriteStrip();
            if (splitPartner != null) {
                splitPartner.reloadShortcutProfileManagerFromPrefs();
                splitPartner.refreshTopShortcutFavoriteStrip();
            }
        });
    }

    /** Sync modifier lock states to the paired keyboard. */
    private void syncModifierStates() {
        if (splitPartner == null) return;
        splitPartner.isShiftLeftLocked = isShiftLeftLocked;
        splitPartner.isCtrlLeftLocked = isCtrlLeftLocked;
        splitPartner.isAltLeftLocked = isAltLeftLocked;
        splitPartner.isWinLeftLocked = isWinLeftLocked;
        splitPartner.isCtrlRightLocked = isCtrlRightLocked;
        splitPartner.isAltRightLocked = isAltRightLocked;
        splitPartner.isWinRightLocked = isWinRightLocked;
        splitPartner.post(() -> splitPartner.updateKeyboard());
    }

    /** Sync Fn lock so both halves show F-layer labels and resolve mappings (e.g. yuiop → F6–F10). */
    private void syncFnLockedToPartner() {
        if (splitPartner == null) return;
        splitPartner.isFnLocked = isFnLocked;
        splitPartner.post(() -> splitPartner.updateKeyboard());
    }

    private void syncKeyboardAlternatesHintsToPartner() {
        if (splitPartner == null) {
            return;
        }
        splitPartner.keyboardAlternatesHintsEnabled = keyboardAlternatesHintsEnabled;
        splitPartner.post(() -> splitPartner.updateKeyboard());
    }

    private void toggleKeyboardAlternatesHintsFromUser() {
        keyboardAlternatesHintsEnabled = !keyboardAlternatesHintsEnabled;
        Context ctx = getContext();
        if (ctx != null) {
            KeyboardAlternatesHintsPrefs.write(ctx, keyboardAlternatesHintsEnabled);
        }
        syncKeyboardAlternatesHintsToPartner();
        updateKeyboard();
    }

    /** Reload alternate-hints pref from disk and rebuild (e.g. after Keyboard and Mouse Pro setup). */
    public void reloadKeyboardAlternatesHintsFromPrefs() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        keyboardAlternatesHintsEnabled = KeyboardAlternatesHintsPrefs.read(ctx);
        syncKeyboardAlternatesHintsToPartner();
        updateKeyboard();
    }

    /** Reload KM Pro key tap preview pref (Keyboard and Mouse Pro setup); no full layout rebuild. */
    public void reloadKmProKeyTapPreviewFromPrefs() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        kmProKeyTapPreviewEnabled = KmProKeyTapPreviewPrefs.read(ctx);
        syncKmProKeyTapPreviewToPartner();
    }

    private void syncKmProKeyTapPreviewToPartner() {
        if (splitPartner == null) {
            return;
        }
        splitPartner.kmProKeyTapPreviewEnabled = kmProKeyTapPreviewEnabled;
    }

    /** Full layout rebuild after KM Pro setup (e.g. long-press repeat vs hold). */
    public void rebuildKeyboardFromKmProSetup() {
        updateKeyboard();
    }

    /** Create a shared top scrolling panel view for split mode. */
    public FrameLayout createTopPanel() {
        rebuildTopShortcutPanels();
        rebuildFixedTopRowsPanels();
        if (topShortcutPanels.isEmpty()) {
            return null;
        }
        clearSplitTopPanelReferences();

        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }

        FrameLayout root = new FrameLayout(getContext());
        root.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout stack = new LinearLayout(getContext());
        stack.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        stack.setOrientation(VERTICAL);

        FrameLayout viewport = new FrameLayout(getContext());
        viewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_SCROLLABLE_ROW_WEIGHT));
        fixedTopRowsViewport = new FrameLayout(getContext());
        fixedTopRowsViewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_FIXED_ROWS_WEIGHT));
        previousFixedTopRowsContainer = createTopShortcutPanelView();
        activeFixedTopRowsContainer = createTopShortcutPanelView();
        nextFixedTopRowsContainer = createTopShortcutPanelView();
        fixedTopRowsViewport.addView(previousFixedTopRowsContainer);
        fixedTopRowsViewport.addView(activeFixedTopRowsContainer);
        fixedTopRowsViewport.addView(nextFixedTopRowsContainer);

        previousTopPanelContainer = createTopShortcutPanelView();
        activeTopPanelContainer = createTopShortcutPanelView();
        nextTopPanelContainer = createTopShortcutPanelView();
        viewport.addView(previousTopPanelContainer);
        viewport.addView(activeTopPanelContainer);
        viewport.addView(nextTopPanelContainer);
        stack.addView(viewport);
        stack.addView(fixedTopRowsViewport);
        root.addView(stack);

        syncTopPanelViewportContent();

        // Post to wait for layout, then set initial positions
        root.post(() -> {
            float width = viewport.getWidth();
            if (width > 0) {
                activeTopPanelContainer.setTranslationX(0);
                if (previousTopPanelContainer != null) {
                    previousTopPanelContainer.setTranslationX(-width);
                }
                if (nextTopPanelContainer != null) {
                    nextTopPanelContainer.setTranslationX(width);
                }
                resetFixedTopRowsPositions(0f);
            }
        });

        return root;
    }

    public void createSplitLandscapeTopPanel(
            FrameLayout leftHost,
            View centerSpacer,
            FrameLayout rightHost
    ) {
        if (leftHost == null || rightHost == null) {
            return;
        }
        rebuildTopShortcutPanels();
        rebuildFixedTopRowsPanels();
        if (topShortcutPanels.isEmpty()) {
            return;
        }
        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }

        topPanelViewport = null;
        topPanelRootContainer = null;
        fixedTopRowsViewport = null;
        previousFixedTopRowsContainer = null;
        activeFixedTopRowsContainer = null;
        nextFixedTopRowsContainer = null;
        previousTopPanelContainer = null;
        activeTopPanelContainer = null;
        nextTopPanelContainer = null;

        splitTopCenterSpacer = centerSpacer;
        LinearLayout leftStack = new LinearLayout(getContext());
        leftStack.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        leftStack.setOrientation(VERTICAL);
        LinearLayout rightStack = new LinearLayout(getContext());
        rightStack.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        rightStack.setOrientation(VERTICAL);

        splitTopLeftViewport = new FrameLayout(getContext());
        splitTopRightViewport = new FrameLayout(getContext());
        splitTopLeftViewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_SCROLLABLE_ROW_WEIGHT));
        splitTopRightViewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_SCROLLABLE_ROW_WEIGHT));
        splitFixedTopRowsViewportLeft = new FrameLayout(getContext());
        splitFixedTopRowsViewportLeft.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_FIXED_ROWS_WEIGHT));
        splitPreviousFixedTopRowsContainerLeft = createTopShortcutPanelView();
        splitActiveFixedTopRowsContainerLeft = createTopShortcutPanelView();
        splitNextFixedTopRowsContainerLeft = createTopShortcutPanelView();
        splitFixedTopRowsViewportLeft.addView(splitPreviousFixedTopRowsContainerLeft);
        splitFixedTopRowsViewportLeft.addView(splitActiveFixedTopRowsContainerLeft);
        splitFixedTopRowsViewportLeft.addView(splitNextFixedTopRowsContainerLeft);

        splitFixedTopRowsViewportRight = new FrameLayout(getContext());
        splitFixedTopRowsViewportRight.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_FIXED_ROWS_WEIGHT));
        splitPreviousFixedTopRowsContainerRight = createTopShortcutPanelView();
        splitActiveFixedTopRowsContainerRight = createTopShortcutPanelView();
        splitNextFixedTopRowsContainerRight = createTopShortcutPanelView();
        splitFixedTopRowsViewportRight.addView(splitPreviousFixedTopRowsContainerRight);
        splitFixedTopRowsViewportRight.addView(splitActiveFixedTopRowsContainerRight);
        splitFixedTopRowsViewportRight.addView(splitNextFixedTopRowsContainerRight);

        splitPreviousTopPanelContainerLeft = createTopShortcutPanelView();
        splitActiveTopPanelContainerLeft = createTopShortcutPanelView();
        splitNextTopPanelContainerLeft = createTopShortcutPanelView();
        splitTopLeftViewport.addView(splitPreviousTopPanelContainerLeft);
        splitTopLeftViewport.addView(splitActiveTopPanelContainerLeft);
        splitTopLeftViewport.addView(splitNextTopPanelContainerLeft);

        splitPreviousTopPanelContainerRight = createTopShortcutPanelView();
        splitActiveTopPanelContainerRight = createTopShortcutPanelView();
        splitNextTopPanelContainerRight = createTopShortcutPanelView();
        splitTopRightViewport.addView(splitPreviousTopPanelContainerRight);
        splitTopRightViewport.addView(splitActiveTopPanelContainerRight);
        splitTopRightViewport.addView(splitNextTopPanelContainerRight);
        leftStack.addView(splitTopLeftViewport);
        leftStack.addView(splitFixedTopRowsViewportLeft);
        rightStack.addView(splitTopRightViewport);
        rightStack.addView(splitFixedTopRowsViewportRight);

        leftHost.removeAllViews();
        rightHost.removeAllViews();
        leftHost.addView(leftStack);
        rightHost.addView(rightStack);

        OnTouchListener sharedTouch = createTopPanelTouchListener(null);
        splitTopLeftViewport.setOnTouchListener(sharedTouch);
        splitTopRightViewport.setOnTouchListener(sharedTouch);
        if (splitTopCenterSpacer != null) {
            splitTopCenterSpacer.setOnTouchListener(sharedTouch);
        }

        syncTopPanelViewportContent();
        leftHost.post(() -> {
            resetTopPanelPositions(0f);
            resetFixedTopRowsPositions(0f);
        });
    }
    
    /**
     * Check if device is in landscape orientation
     * Matches iOS OrientationManager.isLandscape behavior
     */
    private boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation 
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * KM Pro landscape built-in keyboard (letter grid + shortcut strip): split vertical space between
     * shortcut strip and letter grid using layout weights
     * ({@link #KM_PRO_LANDSCAPE_SHORTCUT_STRIP_HEIGHT_WEIGHT} : {@link #KM_PRO_LANDSCAPE_LETTER_KEYBOARD_HEIGHT_WEIGHT})
     * instead of a fixed strip height.
     * <p>
     * Note: {@link CompositeFragment} sets {@code setShowExtraPortraitKeys(false)} for normal Keyboard
     * submode, including landscape full keyboard; do not gate this on {@code showExtraPortraitKeys}.
     */
    private boolean useKmProLandscapeFullShortcutStripHeightWeightRatio() {
        return isLandscape(getContext()) && splitPart == SPLIT_NONE && !shortcutsStripOnly;
    }

    /**
     * KM Pro landscape full keyboard (not split, not shortcut-strip-only): show Openterface wordmark
     * on the wide Space key instead of the generic space-bar icon. Split halves are excluded by
     * {@link #useKmProLandscapeFullShortcutStripHeightWeightRatio} and by {@link #KM_PRO_LANDSCAPE_WIDE_SPACE_MIN_WIDTH_PERCENT}.
     */
    private boolean shouldUseOpenterfaceWordmarkOnSpaceKey(@NonNull Key key) {
        return key.code == 0x2C
                && key.iconResId != 0
                && useKmProLandscapeFullShortcutStripHeightWeightRatio()
                && key.widthPercent >= KM_PRO_LANDSCAPE_WIDE_SPACE_MIN_WIDTH_PERCENT;
    }

    private void bindService(Context context) {
        Intent intent = new Intent(context, BluetoothService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * {@link XmlPullParser#getAttributeValue} does not expand {@code &#...;} / named entities in
     * custom namespace attrs. Decode the subset used in keyboard XML so corner hints, symbols,
     * and alternates match real characters (e.g. {@code &#92;} → {@code \} for {@code /}).
     */
    private static String decodeKeyboardXmlEntities(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        if (input.indexOf('&') < 0) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == '&') {
                int semi = input.indexOf(';', i);
                if (semi > i && semi - i <= 14) {
                    String ent = input.substring(i, semi + 1);
                    String repl = decodeSingleKeyboardEntity(ent);
                    if (repl != null) {
                        sb.append(repl);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(input.charAt(i));
            i++;
        }
        return sb.toString();
    }

    private static String decodeSingleKeyboardEntity(String ent) {
        if ("&amp;".equals(ent)) {
            return "&";
        }
        if ("&lt;".equals(ent)) {
            return "<";
        }
        if ("&gt;".equals(ent)) {
            return ">";
        }
        if ("&quot;".equals(ent)) {
            return "\"";
        }
        if ("&apos;".equals(ent)) {
            return "'";
        }
        if (ent.startsWith("&#") && ent.endsWith(";") && ent.length() >= 4) {
            String inner = ent.substring(2, ent.length() - 1);
            try {
                int cp;
                if (inner.startsWith("x") || inner.startsWith("X")) {
                    cp = Integer.parseInt(inner.substring(1), 16);
                } else {
                    cp = Integer.parseInt(inner, 10);
                }
                if (cp >= 0 && cp <= 0xFFFF) {
                    return String.valueOf((char) cp);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }

    /**
     * Splits {@code keyAlternates} on commas without regex quirks.
     * Empty segments are kept so token indices align with picker slots (Up…Right, then corners).
     */
    private static List<String> splitAlternatesTokens(String alternates) {
        if (TextUtils.isEmpty(alternates)) {
            return Collections.emptyList();
        }
        int len = alternates.length();
        // A lone comma is one Up-slot token; naive comma-split would yield two empty segments.
        if (len == 1) {
            return Collections.singletonList(alternates);
        }
        // Shorthand for comma or period on Up when the next character is not a delimiter comma.
        if (len == 2) {
            char c0 = alternates.charAt(0);
            if (c0 == ',' && alternates.charAt(1) != ',') {
                return Arrays.asList(",", alternates.substring(1));
            }
            if (c0 == '.') {
                return Arrays.asList(".", alternates.substring(1));
            }
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < alternates.length(); i++) {
            if (alternates.charAt(i) == ',') {
                out.add(alternates.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(alternates.substring(start).trim());
        // Legacy ",9,," style: leading comma + digit meant comma on Up and digit on Down (comma is the separator).
        if (out.size() >= 2 && out.get(0).isEmpty()) {
            String second = out.get(1);
            if (second.length() == 1 && Character.isDigit(second.charAt(0))) {
                String d = second;
                if (alternates.startsWith("," + d)) {
                    out.set(0, ",");
                } else if (alternates.startsWith("." + d)) {
                    out.set(0, ".");
                }
            }
        }
        return out;
    }

    /**
     * {@code @package:string/name} as emitted for some compiled {@code res/xml} attrs (not only
     * {@code @string/name}). Optional package defaults to the app package.
     */
    private static final Pattern KEYBOARD_LAYOUT_STRING_RES =
            Pattern.compile("^@(?:([^:]+):)?string/(.+)$");

    /**
     * Resolves string resource references in keyboard layout XML: {@code @string/name},
     * {@code @package:string/name}, or numeric {@code @12345}. Raw literals are returned unchanged.
     */
    private static String resolveKeyboardLayoutStringAttr(Context context, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("@")) {
            return value;
        }
        try {
            if (value.length() > 1) {
                String idStr = value.substring(1);
                try {
                    int resId = Integer.parseInt(idStr);
                    return context.getResources().getString(resId);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            Matcher m = KEYBOARD_LAYOUT_STRING_RES.matcher(value);
            if (m.matches()) {
                String pkg = m.group(1);
                String name = m.group(2);
                if (pkg == null || pkg.isEmpty()) {
                    pkg = context.getPackageName();
                }
                int resId = context.getResources().getIdentifier(name, "string", pkg);
                if (resId != 0) {
                    return context.getResources().getString(resId);
                }
            }
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "resolveKeyboardLayoutStringAttr: not found: " + value, e);
        }
        return value;
    }

    private List<List<Key>> parseKeyboard(Context context, int resourceId) {
        List<List<Key>> rows = new ArrayList<>();
        List<Key> currentRow = null;
        final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
        final String CUSTOM_NS = "http://schemas.android.com/apk/res-auto";

        try {
            XmlPullParser parser = context.getResources().getXml(resourceId);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("Row".equals(tag)) {
                        currentRow = new ArrayList<>();
                        System.out.println("Parsing new Row for resource ID: " + resourceId);
                    } else if ("Key".equals(tag) && currentRow != null) {
                        String label = parser.getAttributeValue(ANDROID_NS, "keyLabel");
                        if (label == null) {
                            label = "";
                            System.out.println("Warning: android:keyLabel is missing");
                        } else {
                            try {
                                if (label.startsWith("@") && label.length() > 1) {
                                    String idStr = label.substring(1);
                                    try {
                                        int resId = Integer.parseInt(idStr);
                                        label = context.getResources().getString(resId);
                                        System.out.println("Resolved resource ID " + resId + " to: " + label);
                                    } catch (NumberFormatException e) {
                                        System.out.println("Warning: Invalid resource ID format: " + label);
                                    } catch (Resources.NotFoundException e) {
                                        System.out.println("Warning: Resource ID not found: " + label);
                                        e.printStackTrace();
                                    }
                                } else {
                                    String resourceName = label.startsWith("@string/") ? label.substring("@string/".length()) : label;
                                    int resId = context.getResources().getIdentifier(resourceName, "string", context.getPackageName());
                                    if (resId != 0) {
                                        label = context.getResources().getString(resId);
                                        System.out.println("Resolved string resource " + resourceName + " to: " + label);
                                    } else {
                                        System.out.println("Warning: String resource not found for keyLabel: " + label);
                                    }
                                }
                            } catch (Resources.NotFoundException e) {
                                System.out.println("Warning: Failed to resolve string resource: " + label);
                                e.printStackTrace();
                            }
                        }

                        String symbolLabel = parser.getAttributeValue(CUSTOM_NS, "keySymbolLabel");
                        if (symbolLabel == null) {
                            symbolLabel = "";
                            System.out.println("Warning: custom:keySymbolLabel is missing");
                        }
                        String alternates = parser.getAttributeValue(CUSTOM_NS, "keyAlternates");
                        if (alternates == null) {
                            alternates = "";
                        }
                        String cornerHint = parser.getAttributeValue(CUSTOM_NS, "keyCornerHint");
                        if (cornerHint == null) {
                            cornerHint = "";
                        }

                        symbolLabel = resolveKeyboardLayoutStringAttr(context, symbolLabel);
                        alternates = resolveKeyboardLayoutStringAttr(context, alternates);
                        cornerHint = resolveKeyboardLayoutStringAttr(context, cornerHint);

                        symbolLabel = decodeKeyboardXmlEntities(symbolLabel);
                        alternates = decodeKeyboardXmlEntities(alternates);
                        cornerHint = decodeKeyboardXmlEntities(cornerHint);

                        String codeStr = parser.getAttributeValue(ANDROID_NS, "codes");
                        int code = 0;
                        try {
                            code = codeStr != null ? Integer.parseInt(codeStr, 16) : 0;
                        } catch (NumberFormatException e) {
                            System.out.println("Warning: Invalid android:codes value: " + codeStr);
                            e.printStackTrace();
                        }

                        String widthStr = parser.getAttributeValue(ANDROID_NS, "keyWidth");
                        float widthPercent = 10.0f;
                        if (widthStr != null) {
                            try {
                                if (widthStr.endsWith("%p")) {
                                    widthPercent = Float.parseFloat(widthStr.replace("%p", ""));
                                } else {
                                    widthPercent = Float.parseFloat(widthStr);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Warning: Invalid android:keyWidth value: " + widthStr);
                            }
                        }

                        String gapStr = parser.getAttributeValue(ANDROID_NS, "horizontalGap");
                        float horizontalGap = 0.0f;
                        if (gapStr != null) {
                            try {
                                if (gapStr.endsWith("%p")) {
                                    horizontalGap = Float.parseFloat(gapStr.replace("%p", ""));
                                } else {
                                    horizontalGap = Float.parseFloat(gapStr);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Warning: Invalid android:horizontalGap value: " + gapStr);
                            }
                        }

                        String isRepeatableStr = parser.getAttributeValue(ANDROID_NS, "isRepeatable");
                        boolean isRepeatable = false;
                        if (isRepeatableStr != null) {
                            isRepeatable = Boolean.parseBoolean(isRepeatableStr);
                        }

                        String requiresShiftStr = parser.getAttributeValue(CUSTOM_NS, "keyRequiresShift");
                        boolean requiresShift = false;
                        if (requiresShiftStr != null) {
                            requiresShift = Boolean.parseBoolean(requiresShiftStr);
                        }

                        int iconResId = 0;
                        if (label.equals("Win")) {
                            iconResId = R.drawable.windows;
                            System.out.println("Hardcoded icon for Win: " + iconResId);
                        } else if (label.equals("Space")) {
                            iconResId = R.drawable.space_bar_24px;
                            System.out.println("Hardcoded icon for Space: " + iconResId);
                        } else if (label.equals("BackSpace")) {
                            iconResId = R.drawable.backspace;
                            System.out.println("Hardcoded icon for BackSpace: " + iconResId);
                        } else if (code == 0x4C) {
                            // Forward delete (Del): same glyph as backspace, mirrored in ImageButton setup.
                            iconResId = R.drawable.backspace;
                        } else if (label.equals("Up_arrow")) {
                            iconResId = R.drawable.caret_up_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Down_arrow")) {
                            iconResId = R.drawable.caret_down_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Left_arrow")) {
                            iconResId = R.drawable.caret_left_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Right_arrow")) {
                            iconResId = R.drawable.caret_right_fill;
                            System.out.println("Hardcoded icon for arrow_up: " + iconResId);
                        } else if (label.equals("Shift")) {
                            iconResId = R.drawable.shift_24px;
                            System.out.println("Hardcoded icon for Shift: " + iconResId);
                        } else if (label.equals("Enter")) {
                            iconResId = R.drawable.keyboard_return_24px;
                            System.out.println("Hardcoded icon for Enter: " + iconResId);
                        }

//                        Log.v(TAG,"Parsed Key: label=" + label + ", symbolLabel=" + symbolLabel + ", code=0x" + Integer.toHexString(code).toUpperCase() + ", codeStr=" + codeStr + ", width=" + widthPercent + ", icon=" + iconResId + ", gap=" + horizontalGap + ", repeatable=" + isRepeatable);
                        currentRow.add(new Key(label, symbolLabel, alternates, cornerHint, code, codeStr, widthPercent, iconResId, horizontalGap, isRepeatable, requiresShift, -1, false));
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("Row".equals(parser.getName()) && currentRow != null) {
                        if (!currentRow.isEmpty()) {
                            rows.add(currentRow);
                            System.out.println("Row added with " + currentRow.size() + " keys");
                        }
                        currentRow = null;
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            System.out.println("Error parsing keyboard XML: " + e.getMessage());
            e.printStackTrace();
        }

        if (rows.isEmpty()) {
            System.out.println("Warning: No rows parsed for keyboard resource ID: " + resourceId);
        }
        return rows;
    }

    /**
     * Split mode slices each row at the middle; a single wide Space bar would sit entirely on
     * one half. Replace that key with two half-width Space keys (same HID 0x2C) so left and
     * right keyboards each get a space bar.
     */
    private List<List<Key>> expandSpaceBarForSplitRows(List<List<Key>> rows) {
        List<List<Key>> result = new ArrayList<>();
        for (List<Key> row : rows) {
            List<Key> newRow = new ArrayList<>();
            for (Key k : row) {
                if (k != null && k.code == 0x2C) {
                    float half = k.widthPercent / 2f;
                    newRow.add(cloneKeyWithWidth(k, half));
                    newRow.add(cloneKeyWithWidth(k, half));
                } else {
                    newRow.add(k);
                }
            }
            result.add(newRow);
        }
        return result;
    }

    /**
     * Pro no-GUI QWERTY: compact cap size for bottom-row modifiers, Space, Shift, Enter, and Bksp when
     * shown as text. Tab lives on the letter row and uses {@link #BASE_KEYCAP_TEXT_SP_PORTRAIT} /
     * {@link #BASE_KEYCAP_TEXT_SP_LANDSCAPE} like other letter keys.
     */
    private static boolean isProBottomRowCompactTextKey(Key key, boolean noGuiKeyboard) {
        return noGuiKeyboard
                && key != null
                && (key.code == 0xE0 || key.code == 0xE2 || key.code == 0xE3
                        || key.code == 0xE4 || key.code == 0xE6 || key.code == 0xE7
                        || key.code == 0x2C || key.code == 0xE1 || key.code == 0x28 || key.code == 0x2A
                        || key.code == 0x65);
    }

    private float baseKeycapTextSpForKey(Key key) {
        boolean land = isLandscape(getContext());
        if (isProBottomRowCompactTextKey(key, !showGuiHidKey)) {
            return land ? PRO_BOTTOM_ROW_TEXT_SP_LANDSCAPE : PRO_BOTTOM_ROW_TEXT_SP_PORTRAIT;
        }
        return land ? BASE_KEYCAP_TEXT_SP_LANDSCAPE : BASE_KEYCAP_TEXT_SP_PORTRAIT;
    }

    private static Key cloneKeyWithWidth(Key src, float widthPercent) {
        return new Key(
                src.label,
                src.symbolLabel,
                src.alternates,
                src.cornerHint,
                src.code,
                src.codeStr,
                widthPercent,
                src.iconResId,
                src.horizontalGap,
                src.isRepeatable,
                src.requiresShift,
                src.shortcutModifiers,
                src.isTopPanelKey);
    }

    /** Letter-row keycap background (shared by {@link Button} and centered Fn-layer icon cells). */
    private void applyLetterRowKeyFaceBackground(View v, Key key, boolean isFunctionalKey) {
        v.setBackgroundResource(R.drawable.key_background);
        if (isFunctionalKey) {
            v.setBackgroundResource(R.drawable.function_button_background);
        } else if (key.code == 0xE1 && isFnAlternateHintsToggleKey(key)) {
            if (isShiftLeftLocked) {
                v.setBackgroundResource(R.drawable.press_button_background);
            }
            if (v instanceof TextView) {
                ((TextView) v).setSelected(isShiftLeftLocked);
            } else {
                v.setSelected(isShiftLeftLocked);
            }
        } else if (isProBuiltInModifierTouchKey(key)) {
            boolean on = isProModifierCapVisualOn(key);
            if (on) {
                v.setBackgroundResource(R.drawable.press_button_background);
            }
            v.setSelected(on);
        } else if (key.code == KEY_MODE_FN && isFnLocked) {
            v.setBackgroundResource(R.drawable.press_button_background);
        } else if (key.code == 16) {
            v.setBackgroundResource(R.drawable.key_background);
        }
    }

    private boolean isProModifierCapVisualOn(Key key) {
        if (key == null) {
            return false;
        }
        int ext = key.code;
        int boot = bootModifierMaskForBuiltInExtendedKey(ext);
        boolean latched = isModifierLockedStateForKey(key);
        boolean held =
                holdLockController != null && boot != 0 && holdLockController.isModifierLocked(boot);
        int aggregate = proChordSustainAggregateBootMaskOr0();
        boolean chordHighlight =
                (boot != 0 && (aggregate & boot) != 0)
                        || (boot == 0 && proChordActiveExtKey == ext);
        return latched || held || chordHighlight;
    }

    private void refreshBuiltInModifierKeyCapsFromTree() {
        refreshBuiltInModifierKeyCapsRecursive(this);
    }

    private void refreshBuiltInModifierKeyCapsRecursive(View v) {
        if (v == null) {
            return;
        }
        Object tag = v.getTag();
        if (tag instanceof Key) {
            Key k = (Key) tag;
            if (k.isTopPanelKey && isTopModifierLockCandidate(k)) {
                boolean on = isProModifierCapVisualOn(k);
                applyTopPanelKeyCapBackground(v, k, on);
                v.setSelected(on);
            } else if (stripMomentarySyncKeyCode != -1
                    && k.code == stripMomentarySyncKeyCode) {
                // Momentary press sync: strip key held → mirror on main-keyboard counterpart
                // (covers both modifier keys and direction/Enter keys).
                v.setBackgroundResource(R.drawable.press_button_background);
                v.setSelected(true);
            } else if (isProBuiltInModifierTouchKey(k)) {
                applyLetterRowKeyFaceBackground(v, k, false);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                refreshBuiltInModifierKeyCapsRecursive(g.getChildAt(i));
            }
        }
    }

    private void updateKeyboard() {
        stopGamingKeyRepeat();
        detachLocalImeFieldQuiet();
        kmProKeyPreview.dismiss();
        removeAllViews();
        kmProLetterKeyboardBody = null;
        kmProPortraitBrandFooter = null;

        if (shortcutsStripOnly && splitPart == SPLIT_NONE) {
            addTopFunctionRows();
            return;
        }

        List<List<Key>> currentKeys = lowerKeys;

        // In split keyboard mode, divide each row into left or right half
        if (splitPart != SPLIT_NONE) {
            currentKeys = expandSpaceBarForSplitRows(currentKeys);
            List<List<Key>> splitKeys = new ArrayList<>();
            for (List<Key> row : currentKeys) {
                // Even-sized rows split evenly (e.g. KM Pro landscape no-GUI bottom row has 8 keys →
                // mid 4: left half Fn…LGui, right half Space…RCtrl only; Space is not shared across halves).
                int mid = (row.size() + 1) / 2; // round up for odd rows
                List<Key> sideKeys = new ArrayList<>();
                if (splitPart == SPLIT_LEFT) {
                    for (int i = 0; i < mid; i++) sideKeys.add(row.get(i));
                } else {
                    for (int i = mid; i < row.size(); i++) sideKeys.add(row.get(i));
                }
                // Rescale widths to fill half of the row
                float sideWeightSum = 0;
                for (Key k : sideKeys) sideWeightSum += k.widthPercent;
                if (sideWeightSum > 0) {
                    for (Key k : sideKeys) {
                        k.widthPercent = (k.widthPercent / sideWeightSum) * 50f;
                    }
                }
                if (!sideKeys.isEmpty()) {
                    splitKeys.add(sideKeys);
                }
            }
            currentKeys = splitKeys;
        }

        // Portrait numpad-only: fragment shows touchpad; render extra grid without QWERTY rows.
        if (showExtraPortraitKeys && !isLandscape(getContext()) && splitPart == SPLIT_NONE) {
            addExtraPortraitKeys();
            return;
        }

        // In fullscreen keyboard mode, hide arrow keys from the main keyboard area.
        // Direction keys are provided by the extra middle panel in this mode.
        if (showExtraPortraitKeys) {
            List<List<Key>> filtered = new ArrayList<>();
            for (List<Key> row : currentKeys) {
                List<Key> out = new ArrayList<>();
                for (Key k : row) {
                    if ("Up_arrow".equals(k.label)
                            || "Down_arrow".equals(k.label)
                            || "Left_arrow".equals(k.label)
                            || "Right_arrow".equals(k.label)) {
                        continue;
                    }
                    out.add(k);
                }
                if (!out.isEmpty()) {
                    filtered.add(out);
                }
            }
            currentKeys = filtered;
        }

        // Show two shortcut rows above letter keyboard in portrait normal mode
        if (!showExtraPortraitKeys && splitPart == SPLIT_NONE) {
            addTopFunctionRows();
        }

        // In landscape fullscreen mode, always show the top scrolling panel
        if (showExtraPortraitKeys && isLandscape(getContext()) && splitPart == SPLIT_NONE) {
            addTopFunctionRows();
        }

        LinearLayout letterBodyContainer = null;
        final boolean wrapLetterRows = splitPart == SPLIT_NONE;
        if (wrapLetterRows) {
            letterBodyContainer = new LinearLayout(getContext());
            letterBodyContainer.setOrientation(VERTICAL);
            float letterBodyWeight =
                    useKmProLandscapeFullShortcutStripHeightWeightRatio()
                            ? KM_PRO_LANDSCAPE_LETTER_KEYBOARD_HEIGHT_WEIGHT
                            : 1f;
            letterBodyContainer.setLayoutParams(
                    new LayoutParams(LayoutParams.MATCH_PARENT, 0, letterBodyWeight));
            kmProLetterKeyboardBody = letterBodyContainer;
            addView(letterBodyContainer);
        }

        String[] functionalKeyCodes = {"46", "47", "48", "49", "4A", "4B", "4C", "4D", "4E", "3B", "3C", "3D", "3E", "3F", "29", "3A", "40", "41", "42", "43", "44", "45", "3D", "3F"};

        for (List<Key> row : currentKeys) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            // Use weight=1 so each row shares the available height equally,
            // regardless of the keyboard container's actual pixel height.
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
            rowLayout.setOrientation(HORIZONTAL);

            float totalWeight = 0;
            for (Key key : row) {
                totalWeight += key.widthPercent / 10.0f;
            }

            for (Key key : row) {
                View button;
                View listenerTarget;
                float weight = key.widthPercent / 10.0f;
                LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
                int keyMargin = dpToPx(KEY_OUTER_MARGIN_DP);
                params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin);

                if (key.horizontalGap > 0) {
                    params.setMargins((int)(key.horizontalGap * getContext().getResources().getDisplayMetrics().widthPixels / 100), keyMargin, keyMargin, keyMargin);
                }

                boolean isFunctionalKey = false;
                for (String functionCode : functionalKeyCodes) {
                    if (key.codeStr.equals(functionCode)) {
                        isFunctionalKey = true;
                        Log.v(TAG, "Matched functional key: label=" + key.label + ", code=0x" + Integer.toHexString(key.code));
                        break;
                    }
                }

                // Win/Cmd/Super: use ImageButton only when an icon is present (Names mode clears
                // icon on bottom-row 0xE3 so Cmd/Win/Super render as text).
                boolean shouldUseIconButton = (key.label.equals("Win") && key.iconResId != 0)
                        || (key.label.equals("Cmd") && key.iconResId != 0)
                        || (key.code == 0x2C && key.iconResId != 0)
                        || (key.code == 0x2A && key.iconResId != 0)
                        || (key.code == 0x4C && key.iconResId != 0)
                        || (key.code == 0xE1 && key.iconResId != 0)
                        || (key.code == 0x28 && key.iconResId != 0)
                        || key.label.equals("Up_arrow")
                        || key.label.equals("Down_arrow")
                        || key.label.equals("Left_arrow")
                        || key.label.equals("Right_arrow")
                        || (key.label.equals("Super") && key.iconResId != 0)
                        || (key.code == 0xE3 && key.iconResId != 0)
                        || (key.code == 0xE7 && key.iconResId != 0)
                        || (key.code == 0x65 && key.iconResId != 0)
                        || (key.code == 0x2B && key.iconResId != 0);
                if (shouldUseIconButton) {
                    if (shouldUseOpenterfaceWordmarkOnSpaceKey(key)) {
                        // Match portrait KM Pro footer + touchpad footer (maybeAddKmProPortraitKeyboardBrandFooter,
                        // fragment_composite touchPadBrandLogo): fixed height, max width, secondary tint, 0.82 alpha.
                        // Use explicit logoW from drawable aspect so the ImageView cannot measure oversized
                        // before maxWidth is applied (WRAP_CONTENT + wide vector was still filling the key).
                        Context ctx = getContext();
                        FrameLayout brandCell = new FrameLayout(ctx);
                        applyFlatKeyStyle(brandCell);
                        brandCell.setLayoutParams(params);
                        brandCell.setBackgroundResource(R.drawable.key_background);
                        // Touch + pressed state must live on this cell: key_background is here, not on the
                        // centered wordmark ImageView (otherwise attachKeyListeners setPressed has no effect).
                        brandCell.setClickable(true);
                        brandCell.setFocusable(true);
                        brandCell.setContentDescription(ctx.getString(R.string.Space_Button));

                        ImageView brand = new ImageView(ctx);
                        Resources res = ctx.getResources();
                        int logoH =
                                res.getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_height);
                        int maxLogoW =
                                res.getDimensionPixelSize(R.dimen.km_basic_touchpad_brand_logo_max_width);
                        int logoW = maxLogoW;
                        Drawable wordmark = ContextCompat.getDrawable(ctx, R.drawable.ic_openterface_wordmark);
                        if (wordmark != null) {
                            int iw = wordmark.getIntrinsicWidth();
                            int ih = wordmark.getIntrinsicHeight();
                            if (iw > 0 && ih > 0) {
                                logoW = Math.min(maxLogoW, Math.round(logoH * (iw / (float) ih)));
                            }
                        }
                        FrameLayout.LayoutParams logoLp =
                                new FrameLayout.LayoutParams(logoW, logoH, Gravity.CENTER);
                        brand.setLayoutParams(logoLp);
                        brand.setImageResource(R.drawable.ic_openterface_wordmark);
                        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        brand.setAdjustViewBounds(false);
                        brand.setAlpha(0.82f);
                        brand.setColorFilter(
                                ContextCompat.getColor(ctx, R.color.text_secondary),
                                PorterDuff.Mode.SRC_IN);
                        brand.setClickable(false);
                        brand.setFocusable(false);
                        brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                        brandCell.addView(brand);
                        button = brandCell;
                        listenerTarget = brandCell;
                    } else {
                    ImageButton imageButton = new ImageButton(getContext());
                    applyFlatKeyStyle(imageButton);
                    imageButton.setLayoutParams(params);
                    if (key.code == 0x2B && key.iconResId != 0) {
                        imageButton.setContentDescription(
                                getContext().getString(R.string.key_display_name_tab));
                    }
                    if (key.code == 0x4C && key.iconResId != 0) {
                        imageButton.setContentDescription(getContext().getString(R.string.Delete));
                    }
                    if (key.code == 0x2C && key.iconResId != 0) {
                        imageButton.setContentDescription(
                                getContext().getString(R.string.Space_Button));
                    }
                    if (key.code == 0x65 && key.iconResId != 0) {
                        imageButton.setContentDescription(
                                getContext().getString(R.string.km_basic_cd_application_key));
                    }
                    if ((key.code == 0xE3 && isWinLeftLocked)
                            || (key.code == 0xE7 && isWinRightLocked)
                            || (key.code == 0xE1 && isShiftLeftLocked)) {
                        imageButton.setBackgroundResource(R.drawable.press_button_background);
                    } else {
                        imageButton.setBackgroundResource(R.drawable.key_background);
                    }
                    boolean fnHintsOnMainShift = isFnAlternateHintsToggleKey(key);
                    if (key.iconResId != 0 || fnHintsOnMainShift) {
                        int capIconRes =
                                fnHintsOnMainShift
                                        ? (keyboardAlternatesHintsEnabled
                                                ? R.drawable.ic_keyboard_alternate_on
                                                : R.drawable.ic_keyboard_alternate_off)
                                        : key.iconResId;
                        imageButton.setImageResource(capIconRes);
                        imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                        if (isBackspaceKey(key)) {
                            imageButton.setScaleX(isFnLocked ? -1f : 1f);
                        } else if (key.code == 0x4C) {
                            imageButton.setScaleX(-1f);
                        }
                        if ("Win".equals(key.label) || "Cmd".equals(key.label) || "Super".equals(key.label)
                                || key.code == 0xE3
                                || key.code == 0xE7
                                || key.code == 0x65
                                || (key.code == 0x2B && key.iconResId != 0)
                                || key.code == 0x2A
                                || key.code == 0x4C
                                || key.code == 0xE1
                                || key.code == 0x28
                                || (key.code == 0x2C && key.iconResId != 0)
                                || fnHintsOnMainShift) {
                            imageButton.setColorFilter(resolveThemeTextColor());
                        }
                    }
                    int iconPaddingDp =
                            ((isBackspaceKey(key) || key.code == 0x4C) && isLandscape(getContext()))
                                    ? 2
                                    : 4;
                    imageButton.setPadding(
                            dpToPx(iconPaddingDp),
                            dpToPx(iconPaddingDp),
                            dpToPx(iconPaddingDp),
                            dpToPx(iconPaddingDp));
                    button = imageButton;
                    listenerTarget = imageButton;
                    }
                } else {
                    String fnDisplayLabel = getFnDisplayLabel(key);
                    int fnDisplayIconResId = getFnDisplayIconResId(key);
                    if (fnDisplayIconResId != 0) {
                        FrameLayout fnIconCell = new FrameLayout(getContext());
                        applyFlatKeyStyle(fnIconCell);
                        fnIconCell.setLayoutParams(params);
                        applyLetterRowKeyFaceBackground(fnIconCell, key, isFunctionalKey);

                        ImageView fnIconView = new ImageView(getContext());
                        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                        fnIconView.setLayoutParams(iconLp);
                        fnIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        fnIconView.setImageResource(fnDisplayIconResId);
                        Drawable iconDw = fnIconView.getDrawable();
                        if (iconDw != null) {
                            iconDw = iconDw.mutate();
                            iconDw.setTint(resolveThemeTextColor());
                            fnIconView.setImageDrawable(iconDw);
                        }
                        fnIconView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                        fnIconView.setClickable(true);
                        fnIconView.setFocusable(true);
                        fnIconCell.setClickable(false);
                        fnIconCell.addView(fnIconView);
                        button = fnIconCell;
                        listenerTarget = fnIconView;
                    } else {
                    Button textButton = new Button(getContext());
                    applyFlatKeyStyle(textButton);
                    textButton.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    textButton.setGravity(Gravity.CENTER);
                    textButton.setTextSize(baseKeycapTextSpForKey(key));
                    textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                    applyLetterRowKeyFaceBackground(textButton, key, isFunctionalKey);

                    if (!TextUtils.isEmpty(fnDisplayLabel)) {
                        textButton.setText(fnDisplayLabel);
                        textButton.setSingleLine(true);
                        textButton.setMaxLines(1);
                        textButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        float fnSp = getFnLabelTextSizeSp(fnDisplayLabel);
                        textButton.setTextSize(fnSp);
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        textButton.setTextColor(resolveThemeTextColor());
                    } else if (key.iconResId == 0 && !key.label.isEmpty()) {
                        String displayLabel = key.label;
                        String symbolLabel = key.symbolLabel;
                        boolean showAlternateLabel = isShiftLeftLocked || isSymbolMode;

                        if (!showAlternateLabel && displayLabel.contains("\n")) {
                            String[] parts = displayLabel.split("\n");
                            if (parts.length == 2) {
                                symbolLabel = parts[0];
                                displayLabel = parts[1];
                            }
                            String combinedText = symbolLabel + "\n" + displayLabel;
                            SpannableString spannable = new SpannableString(combinedText);
                            textButton.setText(spannable);
                            textButton.setTextColor(resolveThemeTextColor());
                            Log.v(TAG, "Applied Spannable: combinedText=" + combinedText + ", symbolLabel=" + symbolLabel + ", displayLabel=" + displayLabel);
                        } else {
                            textButton.setText(showAlternateLabel && !symbolLabel.isEmpty() ? symbolLabel : displayLabel);
                            textButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                            textButton.setTextColor(resolveThemeTextColor());
                        }
                    }

                    if (isProBottomRowCompactTextKey(key, !showGuiHidKey)) {
                        textButton.setSingleLine(true);
                        textButton.setMaxLines(1);
                        textButton.setEllipsize(TextUtils.TruncateAt.END);
                    }

                    if (key.iconResId != 0) {
                        textButton.setCompoundDrawablesWithIntrinsicBounds(0, key.iconResId, 0, 0);
                        textButton.setCompoundDrawablePadding(dpToPx(4));
                    }
                    boolean compactHintRow = !isLandscape(getContext());
                    String capHints = buildAlternateHintsLineForKeycap(key, compactHintRow);
                    if (keyboardAlternatesHintsEnabled
                            && !TextUtils.isEmpty(capHints)
                            && TextUtils.isEmpty(fnDisplayLabel)
                            && fnDisplayIconResId == 0) {
                        FrameLayout keyContainer = new FrameLayout(getContext());
                        keyContainer.setLayoutParams(params);
                        keyContainer.addView(textButton);
                        TextView hintRow = new TextView(getContext());
                        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                        hintParams.setMargins(0, dpToPx(2), 0, 0);
                        hintRow.setLayoutParams(hintParams);
                        hintRow.setText(capHints);
                        hintRow.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactHintRow ? 9f : 11f);
                        hintRow.setTypeface(Typeface.MONOSPACE);
                        hintRow.setSingleLine(true);
                        hintRow.setMaxLines(1);
                        hintRow.setHorizontallyScrolling(true);
                        // Keep Alt1-Alt4 on one row in portrait by tightening glyph spacing.
                        hintRow.setLetterSpacing(compactHintRow ? -0.08f : 0f);
                        hintRow.setTextColor(resolveThemeTextColor());
                        hintRow.setAlpha(0.5f);
                        hintRow.setIncludeFontPadding(false);
                        hintRow.setClickable(false);
                        hintRow.setFocusable(false);
                        keyContainer.addView(hintRow);
                        hintRow.bringToFront();
                        button = keyContainer;
                    } else if (keyboardAlternatesHintsEnabled
                            && !TextUtils.isEmpty(key.cornerHint)
                            && TextUtils.isEmpty(fnDisplayLabel)
                            && fnDisplayIconResId == 0) {
                        FrameLayout keyContainer = new FrameLayout(getContext());
                        keyContainer.setLayoutParams(params);
                        keyContainer.addView(textButton);

                        TextView cornerHintView = new TextView(getContext());
                        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
                        hintParams.setMargins(0, dpToPx(3), dpToPx(6), 0);
                        cornerHintView.setLayoutParams(hintParams);
                        cornerHintView.setText(key.cornerHint);
                        cornerHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                        cornerHintView.setTextColor(resolveThemeTextColor());
                        cornerHintView.setAlpha(0.2f);
                        cornerHintView.setTypeface(cornerHintView.getTypeface(), android.graphics.Typeface.BOLD);
                        cornerHintView.setIncludeFontPadding(false);
                        cornerHintView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        cornerHintView.setPadding(0, 0, 0, 0);
                        cornerHintView.setClickable(false);
                        cornerHintView.setFocusable(false);
                        keyContainer.addView(cornerHintView);
                        cornerHintView.bringToFront();

                        button = keyContainer;
                    } else {
                        textButton.setLayoutParams(params);
                        button = textButton;
                    }
                    // Touch must stay on the inner key face (Button or ImageView): a clickable child
                    // consumes events before the parent FrameLayout, so wrapping FrameLayout must NOT
                    // be the listener target or long-press alternates never run.
                    listenerTarget = textButton;
                    }
                }

                attachKeyListeners(listenerTarget, key);
                rowLayout.addView(button);
            }
            if (letterBodyContainer != null) {
                letterBodyContainer.addView(rowLayout);
            } else {
                addView(rowLayout);
            }
        }

        maybeAddKmProPortraitKeyboardBrandFooter(letterBodyContainer);

        if (showExtraPortraitKeys && !isLandscape(getContext())) {
            addExtraPortraitKeys();
        }
    }

    @NonNull
    private String buildKmProKeyTapPreviewLabel(@Nullable Key key) {
        if (key == null) {
            return "";
        }
        if (isFnAlternateHintsToggleKey(key)) {
            return "";
        }
        int code = key.code;
        if (code == KEY_NOOP_PLACEHOLDER
                || code == KEY_IME_TOGGLE
                || code == KEY_TOP_SHORTCUT_DISPLAY_TOGGLE
                || code == KEY_TOP_STRIP_CREATE_SHORTCUT
                || code == KEY_EXTRA_NUMPAD_FN) {
            return "";
        }
        if (code == KEY_MODE_FN || code == KEY_FIXED_TOP_LOCAL_FN) {
            return "";
        }
        if (code >= KEY_TOP_MODE_SLOT_1 && code <= KEY_TOP_MODE_SLOT_3) {
            return "";
        }

        String fnText = getFnDisplayLabel(key);
        if (isFnLocked && !TextUtils.isEmpty(fnText)) {
            return fnText.trim();
        }

        boolean showAlt = isShiftLeftLocked || isSymbolMode;
        String displayLabel = key.label != null ? key.label : "";
        String symbolLabel = key.symbolLabel != null ? key.symbolLabel : "";
        if (displayLabel.contains("\n")) {
            String[] parts = displayLabel.split("\n");
            if (parts.length == 2) {
                symbolLabel = parts[0];
                displayLabel = parts[1];
            }
        }

        if (showAlt && !TextUtils.isEmpty(symbolLabel)) {
            String s = symbolLabel.trim();
            if (!s.isEmpty()) {
                int nl = s.indexOf('\n');
                return nl >= 0 ? s.substring(0, nl) : s;
            }
        }

        displayLabel = displayLabel.trim();
        if (displayLabel.isEmpty()) {
            return "";
        }
        if (displayLabel.length() == 1) {
            char c = displayLabel.charAt(0);
            if (Character.isLetter(c)) {
                char lower = Character.toLowerCase(c);
                return showAlt ? String.valueOf(Character.toUpperCase(lower)) : String.valueOf(lower);
            }
        }
        if (displayLabel.length() > 14 || displayLabel.indexOf('\n') >= 0) {
            return "";
        }
        return displayLabel;
    }

    private void updateKmProKeyTapPreviewForPointer(View v, Key key, MotionEvent event) {
        if (!kmProKeyTapPreviewEnabled) {
            return;
        }
        if (isAlternatePopupVisible()) {
            kmProKeyPreview.dismiss();
            return;
        }
        if (!isTouchInsideViewWithSlop(v, event)) {
            kmProKeyPreview.dismiss();
            return;
        }
        String t = buildKmProKeyTapPreviewLabel(key);
        if (TextUtils.isEmpty(t)) {
            kmProKeyPreview.dismiss();
            return;
        }
        kmProKeyPreview.show(v, t);
    }

    /** Correlated touch/HID timeline for KM Pro; see {@link #TAG_KMPRO_TOUCH}. */
    private static void logKmProTouch(String message) {
        if (!BuildConfig.DEBUG && !Log.isLoggable(TAG_KMPRO_TOUCH, Log.DEBUG)) {
            return;
        }
        Log.v(TAG_KMPRO_TOUCH, SystemClock.uptimeMillis() + " " + message);
    }

    /** Attaches click + touch + long-click listeners to a key view. */
    private void attachKeyListeners(View btn, Key key) {
        if (isProBuiltInModifierTouchKey(key)) {
            btn.setTag(key);
            btn.setOnTouchListener(createProMainKeyboardModifierTouchListener(key));
            return;
        }
        final boolean[] gamingHoldActive = new boolean[]{false};
        btn.setOnTouchListener((v, event) -> {
            final boolean gamingTouch = !keyboardAlternatesHintsEnabled && keySupportsAlternatesWhenEnabled(key);
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    holdRepeatSuppressUpTap = false;
                    gamingHoldActive[0] = false;
                    performKeyHapticFeedback(v);
                    if (gamingTouch) {
                        v.setPressed(true);
                        gamingHoldActive[0] = true;
                        setParentDisallowInterceptTouchEvent(v, true);
                        if (KmBasicKeyboardPrefs.isLongPressSustainedHoldMode(getContext())) {
                            logKmProTouch(
                                    "DOWN gaming sustainedHold key="
                                            + key.label
                                            + " code=0x"
                                            + Integer.toHexString(key.code)
                                            + " raw="
                                            + event.getRawX()
                                            + ","
                                            + event.getRawY());
                            sendHidKeyDataForKey(key);
                        } else {
                            logKmProTouch(
                                    "DOWN gaming repeat key="
                                            + key.label
                                            + " code=0x"
                                            + Integer.toHexString(key.code)
                                            + " raw="
                                            + event.getRawX()
                                            + ","
                                            + event.getRawY());
                            sendHidKeyTapForGamingRepeat(key);
                            startGamingKeyRepeat(key);
                        }
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    if (shouldRepeatOnLongPress(key) && getContext() != null) {
                        startHoldKeyRepeatFromDown(key);
                    }
                    if (shouldEnableAlternates(key)) {
                        alternatesGestureStartRawX = event.getRawX();
                        alternatesGestureStartRawY = event.getRawY();
                        final Runnable openAlternates = () -> showAlternatesPopup(v, key);
                        v.setTag(R.id.tag_custom_keyboard_pending_alternates, openAlternates);
                        longPressHandler.postDelayed(openAlternates, ALT_LONG_PRESS_TIMEOUT_MS);
                    }
                    if (isFnAlternateHintsToggleKey(key)) {
                        v.setPressed(true);
                        setParentDisallowInterceptTouchEvent(v, true);
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    // Listener consumes DOWN/MOVE so the framework never applies pressed; drive
                    // key_background state_pressed explicitly (alternate-hints path).
                    v.setPressed(true);
                    v.setTag(R.id.tag_custom_keyboard_tap_consume_move, Boolean.TRUE);
                    setParentDisallowInterceptTouchEvent(v, true);
                    updateKmProKeyTapPreviewForPointer(v, key, event);
                    logKmProTouch(
                            "DOWN altHintsPath key="
                                    + key.label
                                    + " code=0x"
                                    + Integer.toHexString(key.code)
                                    + " keyboardAltHints="
                                    + keyboardAlternatesHintsEnabled
                                    + " enableAlt="
                                    + shouldEnableAlternates(key)
                                    + " holdRepeat="
                                    + shouldRepeatOnLongPress(key)
                                    + " raw="
                                    + event.getRawX()
                                    + ","
                                    + event.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (isAlternatePopupVisible()) {
                        kmProKeyPreview.dismiss();
                        updateAlternateSelection(event.getRawX(), event.getRawY());
                        return true;
                    }
                    if (v.getTag(R.id.tag_custom_keyboard_pending_alternates) instanceof Runnable) {
                        // Keep the touch on this key until long-press fires; otherwise a parent may
                        // cancel the stream before the alternates popup opens.
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    if (Boolean.TRUE.equals(v.getTag(R.id.tag_custom_keyboard_tap_consume_move))) {
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    if (gamingTouch && gamingHoldActive[0]) {
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    if (holdKeyRepeatActive && holdKeyRepeatKey == key && !isTouchInsideView(v, event)) {
                        stopRepeatingDelete();
                    }
                    if (isFnAlternateHintsToggleKey(key)) {
                        updateKmProKeyTapPreviewForPointer(v, key, event);
                        return true;
                    }
                    updateKmProKeyTapPreviewForPointer(v, key, event);
                    return false;
                }
                case MotionEvent.ACTION_UP: {
                    kmProKeyPreview.dismiss();
                    v.setTag(R.id.tag_custom_keyboard_tap_consume_move, null);
                    setParentDisallowInterceptTouchEvent(v, false);
                    v.setPressed(false);
                    Object pendingObj = v.getTag(R.id.tag_custom_keyboard_pending_alternates);
                    final boolean hadPendingAlternatesRunnable = pendingObj instanceof Runnable;
                    if (hadPendingAlternatesRunnable) {
                        Runnable pending = (Runnable) pendingObj;
                        longPressHandler.removeCallbacks(pending);
                        v.setTag(R.id.tag_custom_keyboard_pending_alternates, null);
                    }
                    if (gamingTouch && gamingHoldActive[0]) {
                        logKmProTouch("UP gamingEnd key=" + key.label);
                        stopGamingKeyRepeat();
                        scheduleKeyboardTapRelease();
                        gamingHoldActive[0] = false;
                        return true;
                    }
                    if (isAlternatePopupVisible()) {
                        logKmProTouch("UP alternatesPopupCommit key=" + key.label);
                        commitCurrentAlternateSelection();
                        dismissAlternatesPopup();
                        scheduleKeyboardTapRelease();
                        return true;
                    }
                    boolean suppressTapUp = holdRepeatSuppressUpTap;
                    if (shouldRepeatOnLongPress(key)) {
                        stopRepeatingDelete();
                    }
                    // Short tap on alternates-capable keys: commit even if UP is outside the key
                    // (e.g. glide K→J — same stream still delivers UP to K with x/y past the cap).
                    boolean commitShortTapAlternatesKey =
                            hadPendingAlternatesRunnable && shouldEnableAlternates(key);
                    boolean insideSlop = isTouchInsideViewSlopForTapUp(v, event);
                    boolean willHandleKeyPress =
                            !suppressTapUp && (insideSlop || commitShortTapAlternatesKey);
                    logKmProTouch(
                            "UP key="
                                    + key.label
                                    + " code=0x"
                                    + Integer.toHexString(key.code)
                                    + " suppress="
                                    + suppressTapUp
                                    + " hadPendingAlt="
                                    + hadPendingAlternatesRunnable
                                    + " insideSlop="
                                    + insideSlop
                                    + " commitShortTapAlt="
                                    + commitShortTapAlternatesKey
                                    + " willHandleKeyPress="
                                    + willHandleKeyPress
                                    + " xy="
                                    + event.getX()
                                    + ","
                                    + event.getY()
                                    + " wh="
                                    + v.getWidth()
                                    + "x"
                                    + v.getHeight()
                                    + " raw="
                                    + event.getRawX()
                                    + ","
                                    + event.getRawY());
                    if (willHandleKeyPress) {
                        handleKeyPress(key);
                    }
                    holdRepeatSuppressUpTap = false;
                    scheduleKeyboardTapRelease();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: {
                    kmProKeyPreview.dismiss();
                    v.setTag(R.id.tag_custom_keyboard_tap_consume_move, null);
                    setParentDisallowInterceptTouchEvent(v, false);
                    v.setPressed(false);
                    Object pendingCancel = v.getTag(R.id.tag_custom_keyboard_pending_alternates);
                    final boolean hadPendingAlternatesOnCancel = pendingCancel instanceof Runnable;
                    if (hadPendingAlternatesOnCancel) {
                        Runnable pending = (Runnable) pendingCancel;
                        longPressHandler.removeCallbacks(pending);
                        v.setTag(R.id.tag_custom_keyboard_pending_alternates, null);
                    }
                    if (gamingTouch && gamingHoldActive[0]) {
                        stopGamingKeyRepeat();
                        scheduleKeyboardTapRelease();
                        gamingHoldActive[0] = false;
                        return true;
                    }
                    if (isAlternatePopupVisible()) {
                        dismissAlternatesPopup();
                        return true;
                    }
                    if (shouldRepeatOnLongPress(key)) {
                        stopRepeatingDelete();
                    }
                    boolean suppressTapCancel = holdRepeatSuppressUpTap;
                    boolean cancelWillHandle =
                            !suppressTapCancel
                                    && hadPendingAlternatesOnCancel
                                    && shouldEnableAlternates(key);
                    logKmProTouch(
                            "CANCEL key="
                                    + key.label
                                    + " code=0x"
                                    + Integer.toHexString(key.code)
                                    + " suppress="
                                    + suppressTapCancel
                                    + " hadPendingAlt="
                                    + hadPendingAlternatesOnCancel
                                    + " willHandleKeyPress="
                                    + cancelWillHandle);
                    if (cancelWillHandle) {
                        handleKeyPress(key);
                    }
                    holdRepeatSuppressUpTap = false;
                    scheduleKeyboardTapRelease();
                    return false;
                }
                default:
                    return false;
            }
        });
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        return x >= 0 && x <= view.getWidth() && y >= 0 && y <= view.getHeight();
    }

    private static void setParentDisallowInterceptTouchEvent(@Nullable View v, boolean disallow) {
        if (v == null) {
            return;
        }
        ViewParent parent = v.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /**
     * For ACTION_UP tap commit only: expand hit rect by touch slop so quick lifts slightly outside
     * the key still send HID (KM Pro built-in vs KM Basic responsiveness).
     */
    private static boolean isTouchInsideViewSlopForTapUp(@Nullable View view, @Nullable MotionEvent event) {
        return isTouchInsideViewWithSlop(view, event);
    }

    /** Same expanded hit rect as tap-up commit; used for KM Pro tap preview during MOVE to avoid edge flicker. */
    private static boolean isTouchInsideViewWithSlop(@Nullable View view, @Nullable MotionEvent event) {
        if (view == null || event == null) {
            return false;
        }
        Context ctx = view.getContext();
        int slop = ctx != null ? ViewConfiguration.get(ctx).getScaledTouchSlop() : 0;
        float x = event.getX();
        float y = event.getY();
        return x >= -slop && x <= view.getWidth() + slop && y >= -slop && y <= view.getHeight() + slop;
    }

    /**
     * Main lower-keyboard left Shift (HID 0xE1): same key as XML row above Fn — not the top shortcut
     * strip. When {@link #isFnLocked} is true, toggles long-press alternates/hints and must own the
     * touch stream (no shift-lock tap path on that cap).
     */
    private static boolean isMainKeyboardBuiltInShiftLeft(Key key) {
        return key != null
                && key.code == 0xE1
                && !key.isTopPanelKey
                && key.stripSlotPage < 0;
    }

    private boolean isFnAlternateHintsToggleKey(Key key) {
        return isFnLocked && !showGuiHidKey && isMainKeyboardBuiltInShiftLeft(key);
    }

    /**
     * Keys that would show long-press alternates when {@link #keyboardAlternatesHintsEnabled} is true
     * (used for gaming touch path when hints are off).
     */
    private boolean keySupportsAlternatesWhenEnabled(Key key) {
        if (key == null || key.isRepeatable || key.isTopPanelKey) {
            return false;
        }
        if (isFnLocked && resolveFnMapping(key) != null) {
            return false;
        }
        if (extraNumpadFnLocked && resolveExtraNumpadFnMapping(key) != null) {
            return false;
        }
        if (key.code >= 0xE0 && key.code <= 0xE7) {
            return false;
        }
        if (key.code >= 0xF001 && key.code <= KEY_TOP_STRIP_CREATE_SHORTCUT) {
            return false;
        }
        if ("Space".equalsIgnoreCase(key.label) || "Enter".equalsIgnoreCase(key.label)) {
            return false;
        }
        return key.label.length() == 1 || key.symbolLabel.length() == 1 || !TextUtils.isEmpty(key.alternates);
    }

    private boolean shouldEnableAlternates(Key key) {
        return keyboardAlternatesHintsEnabled && keySupportsAlternatesWhenEnabled(key);
    }

    private static class FnMapping {
        final String label;
        final int keyCode;
        final int modifierMask;
        final int iconResId;

        FnMapping(String label, int keyCode, int modifierMask) {
            this(label, keyCode, modifierMask, 0);
        }

        FnMapping(String label, int keyCode, int modifierMask, int iconResId) {
            this.label = label;
            this.keyCode = keyCode;
            this.modifierMask = modifierMask;
            this.iconResId = iconResId;
        }
    }

    private String getFnDisplayLabel(Key key) {
        if (isFnAlternateHintsToggleKey(key)) {
            return null;
        }
        FnMapping mapping = resolveFnMapping(key);
        return mapping != null ? mapping.label : null;
    }

    private int getFnDisplayIconResId(Key key) {
        if (isFnAlternateHintsToggleKey(key)) {
            return keyboardAlternatesHintsEnabled
                    ? R.drawable.ic_keyboard_alternate_on
                    : R.drawable.ic_keyboard_alternate_off;
        }
        FnMapping mapping = resolveFnMapping(key);
        return mapping != null ? mapping.iconResId : 0;
    }

    private FnMapping resolveFnMapping(Key key) {
        if (!isFnLocked || key == null) {
            return null;
        }
        if (key.code >= 0xE0 && key.code <= 0xE7) {
            return null;
        }
        if (key.code == KEY_MODE_FN || key.code == 0x2C || key.code == 0x28) {
            return null;
        }

        // KM Pro lower keyboard (km_pro_keys_layout): Fn+Q–P → digits 1–0; Fn+A–L → F1–F9; Fn+Z,X,C → F10–F12;
        // Fn+Backspace → Del (see also sendHidKeyDataForKey forward-delete path).
        switch (key.code) {
            case 0x2B: return new FnMapping("Esc", 0x29, 0); // Tab → Esc when Fn latched
            case 0x14: return new FnMapping("1", 0x1E, 0);  // q
            case 0x1A: return new FnMapping("2", 0x1F, 0);  // w
            case 0x08: return new FnMapping("3", 0x20, 0);  // e
            case 0x15: return new FnMapping("4", 0x21, 0);  // r
            case 0x17: return new FnMapping("5", 0x22, 0);  // t
            case 0x1C: return new FnMapping("6", 0x23, 0);  // y
            case 0x18: return new FnMapping("7", 0x24, 0);  // u
            case 0x0C: return new FnMapping("8", 0x25, 0);  // i
            case 0x12: return new FnMapping("9", 0x26, 0);  // o
            case 0x13: return new FnMapping("0", 0x27, 0);   // p
            case 0x04: return new FnMapping("F1", 0x3A, 0);  // a
            case 0x16: return new FnMapping("F2", 0x3B, 0);  // s
            case 0x07: return new FnMapping("F3", 0x3C, 0);  // d
            case 0x09: return new FnMapping("F4", 0x3D, 0);  // f
            case 0x0A: return new FnMapping("F5", 0x3E, 0);  // g
            case 0x0B: return new FnMapping("F6", 0x3F, 0);  // h
            case 0x0D: return new FnMapping("F7", 0x40, 0);  // j
            case 0x0E: return new FnMapping("F8", 0x41, 0);  // k
            case 0x0F: return new FnMapping("F9", 0x42, 0);  // l
            case 0x1D: return new FnMapping("F10", 0x43, 0); // z
            case 0x1B: return new FnMapping("F11", 0x44, 0); // x
            case 0x06: return new FnMapping("F12", 0x45, 0); // c
            case 0x2A: return new FnMapping("Del", 0x4C, 0, R.drawable.backspace_24);
            default: return null;
        }
    }

    private float getFnLabelTextSizeSp(String fnLabel) {
        if (fnLabel == null) {
            return 14f;
        }
        switch (fnLabel) {
            case "INS":
            case "PGUP":
            case "PGDN":
            case "HOME":
            case "END":
                return 11f;
            default:
                return 14f;
        }
    }

    private boolean shouldRepeatOnLongPress(Key key) {
        if (key == null) {
            return false;
        }
        if (key.isRepeatable || isArrowKey(key) || isBackspaceKey(key)) {
            return true;
        }
        // Space (0x2C) and forward-delete (0x4C, e.g. KM Pro fixed-strip DEL) — XML may omit isRepeatable.
        return key.code == 0x2C || key.code == 0x4C;
    }

    /** Fixed-strip keys that may use hold-repeat (excludes modifier lock, Fn strip editor, Caps timing). */
    private boolean fixedStripCellSupportsHoldRepeat(Key key) {
        return key != null
                && shouldRepeatOnLongPress(key)
                && !isMacCapsMomentaryFromTopStripModifier(key)
                && !isTopModifierLockCandidate(key)
                && !isFixedTopLocalFnKey(key);
    }

    private boolean isArrowKey(Key key) {
        if (key == null) {
            return false;
        }
        if (key.code == 0x52 || key.code == 0x51 || key.code == 0x50 || key.code == 0x4F) {
            return true;
        }
        FnMapping extra = resolveExtraNumpadFnMapping(key);
        return extra != null
                && (extra.keyCode == 0x52 || extra.keyCode == 0x51 || extra.keyCode == 0x50 || extra.keyCode == 0x4F);
    }

    private boolean isAlternatePopupVisible() {
        return alternatePopupWindow != null && alternatePopupWindow.isShowing();
    }

    /**
     * Fills {@code slots[0..8]} for the 3×3 alternate grid. Comma tokens map in order to
     * Up, Down, Left, Right; optional tokens 5–8 map to Up-Left, Up-Right, Down-Left, Down-Right.
     * Center is capital for {@code a–z} or the mappable base label.
     */
    private void fillAlternateSlotOptions(Key key, AlternateOption[] slots) {
        Arrays.fill(slots, null);
        if (key == null) {
            return;
        }
        if (!TextUtils.isEmpty(key.alternates)) {
            List<String> tokens = splitAlternatesTokens(key.alternates);
            int[] cardinalSlots = {
                    AlternatePopupGeometry.SLOT_UP,
                    AlternatePopupGeometry.SLOT_DOWN,
                    AlternatePopupGeometry.SLOT_LEFT,
                    AlternatePopupGeometry.SLOT_RIGHT
            };
            for (int t = 0; t < tokens.size() && t < 4; t++) {
                String trimmed = tokens.get(t).trim();
                if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) != 1) {
                    continue;
                }
                String symbol = new String(Character.toChars(trimmed.codePointAt(0)));
                AlternateOption mapped = mapAsciiAlternate(symbol);
                if (mapped != null) {
                    slots[cardinalSlots[t]] = mapped;
                }
            }
            int[] cornerSlots = {
                    AlternatePopupGeometry.SLOT_UP_LEFT,
                    AlternatePopupGeometry.SLOT_UP_RIGHT,
                    AlternatePopupGeometry.SLOT_DOWN_LEFT,
                    AlternatePopupGeometry.SLOT_DOWN_RIGHT
            };
            for (int i = 0; i < cornerSlots.length && i + 4 < tokens.size(); i++) {
                String trimmed = tokens.get(i + 4).trim();
                if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) != 1) {
                    continue;
                }
                String symbol = new String(Character.toChars(trimmed.codePointAt(0)));
                AlternateOption mapped = mapAsciiAlternate(symbol);
                if (mapped != null) {
                    slots[cornerSlots[i]] = mapped;
                }
            }
        }
        if (!TextUtils.isEmpty(key.label) && key.label.length() == 1) {
            char c = key.label.charAt(0);
            if (c >= 'a' && c <= 'z') {
                AlternateOption cap = mapAsciiAlternate(String.valueOf(Character.toUpperCase(c)));
                if (cap != null) {
                    slots[AlternatePopupGeometry.SLOT_CENTER] = cap;
                }
            } else {
                AlternateOption baseOpt = mapAsciiAlternate(key.label);
                if (baseOpt != null) {
                    slots[AlternatePopupGeometry.SLOT_CENTER] = baseOpt;
                }
            }
        }
        normalizeSlashKeyAlternates(key, slots);
    }

    /**
     * Ensures {@code /} offers backslash on Up when missing or wrongly set to pipe; adds pipe on
     * Down only for the classic Up=\ pairing (not when Up is already another glyph such as €).
     */
    private void normalizeSlashKeyAlternates(Key key, AlternateOption[] slots) {
        if (key == null || TextUtils.isEmpty(key.label) || key.label.length() != 1 || key.label.charAt(0) != '/') {
            return;
        }
        AlternateOption bs = mapAsciiAlternate("\\");
        AlternateOption pipe = mapAsciiAlternate("|");
        if (bs != null) {
            AlternateOption up = slots[AlternatePopupGeometry.SLOT_UP];
            // Keep explicit "?" on Up (shift-/); do not replace with "\" (KM Pro portrait / key).
            if (up != null && "?".equals(up.display)) {
                // leave Up as-is
            } else if (up == null || "|".equals(up.display)) {
                slots[AlternatePopupGeometry.SLOT_UP] = bs;
            }
        }
        AlternateOption upAfter = slots[AlternatePopupGeometry.SLOT_UP];
        if (slots[AlternatePopupGeometry.SLOT_DOWN] == null
                && pipe != null
                && slots[AlternatePopupGeometry.SLOT_UP_LEFT] == null
                && upAfter != null
                && "\\".equals(upAfter.display)) {
            slots[AlternatePopupGeometry.SLOT_DOWN] = pipe;
        }
    }

    private AlternatePopupModel buildAlternatePopupModel(Key key) {
        AlternateOption[] slots = new AlternateOption[AlternatePopupGeometry.SLOT_COUNT];
        fillAlternateSlotOptions(key, slots);
        return new AlternatePopupModel(slots);
    }

    private String buildAlternateHintsLineForKeycap(Key key, boolean compactHintRow) {
        AlternateOption[] slots = new AlternateOption[AlternatePopupGeometry.SLOT_COUNT];
        fillAlternateSlotOptions(key, slots);
        StringBuilder sb = new StringBuilder();
        String sep = compactHintRow ? "" : " ";
        int[] cardinalOrder = {
                AlternatePopupGeometry.SLOT_UP,
                AlternatePopupGeometry.SLOT_DOWN,
                AlternatePopupGeometry.SLOT_LEFT,
                AlternatePopupGeometry.SLOT_RIGHT
        };
        for (int s : cardinalOrder) {
            if (slots[s] != null) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(slots[s].display);
            }
        }
        return sb.toString();
    }

    private void performAlternatesPopupHaptic(View anchor, int feedbackType) {
        if (anchor == null || getContext() == null) {
            return;
        }
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("haptic_feedback", true);
        if (!enabled) {
            return;
        }
        anchor.performHapticFeedback(feedbackType, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /**
     * Long-press picker slots {@link AlternatePopupGeometry#SLOT_UP}…{@link AlternatePopupGeometry#SLOT_RIGHT}
     * (first four {@code keyAlternates} tokens), as opposed to diagonals 5–8.
     */
    private static boolean isAlternateCardinalSlot(int slot) {
        return slot == AlternatePopupGeometry.SLOT_UP
                || slot == AlternatePopupGeometry.SLOT_DOWN
                || slot == AlternatePopupGeometry.SLOT_LEFT
                || slot == AlternatePopupGeometry.SLOT_RIGHT;
    }

    /**
     * Idle fill for cardinal long-press cells (Up/Down/Left/Right): opaque blend of
     * {@code colorPrimaryContainer} toward the popup card color so it always paints reliably
     * (translucent {@link GradientDrawable} can appear invisible on some devices with
     * {@link PopupWindow}). Corners/center stay on the plain popup until selected.
     */
    private int resolveAlternateCardinalWashArgb() {
        Context ctx = getContext();
        if (ctx == null) {
            return 0;
        }
        int container = MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorPrimaryContainer,
                ThemeManager.getColorPrimaryContainer(ctx));
        int popupBg = ContextCompat.getColor(ctx, R.color.background_light);
        // Heavily toward popup base with a faint primary-container tint (opaque blend; translucent
        // fills can fail on some PopupWindow surfaces).
        return ColorUtils.blendARGB(container, popupBg, 0.72f);
    }

    private static void setAlternateCardinalIdleBackground(TextView tv, int washArgb, float cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(washArgb);
        gd.setCornerRadius(cornerRadiusPx);
        tv.setBackground(gd);
    }

    private void onAlternatePickChanged(View anchor, int newPick) {
        int old = lastAlternatePickForHaptic;
        if (newPick == old) {
            return;
        }
        lastAlternatePickForHaptic = newPick;
        if (newPick == AlternatePopupGeometry.RESULT_CANCEL && old != AlternatePopupGeometry.RESULT_CANCEL) {
            performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.REJECT);
        } else if (newPick != AlternatePopupGeometry.RESULT_CANCEL && old == AlternatePopupGeometry.RESULT_CANCEL) {
            performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.VIRTUAL_KEY);
        } else if (newPick >= 0 && newPick < AlternatePopupGeometry.SLOT_COUNT && old >= 0 && old < AlternatePopupGeometry.SLOT_COUNT && newPick != old) {
            performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.KEYBOARD_TAP);
        } else if (newPick >= 0 && newPick < AlternatePopupGeometry.SLOT_COUNT && old == AlternatePopupGeometry.RESULT_DEFAULT) {
            performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.VIRTUAL_KEY);
        } else if (newPick == AlternatePopupGeometry.RESULT_DEFAULT && old >= 0 && old < AlternatePopupGeometry.SLOT_COUNT) {
            performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void applyAlternatePickHighlight() {
        if (currentAlternatePopupModel == null) {
            return;
        }
        int highlightSlot;
        if (currentAlternatePick == AlternatePopupGeometry.RESULT_CANCEL) {
            highlightSlot = -1;
            alternatePopupContainer.setAlpha(0.42f);
        } else {
            alternatePopupContainer.setAlpha(1f);
            if (currentAlternatePick == AlternatePopupGeometry.RESULT_DEFAULT) {
                highlightSlot = currentAlternatePopupModel.defaultHighlightSlot();
            } else {
                highlightSlot = currentAlternatePick;
            }
        }
        int cardinalWashArgb = resolveAlternateCardinalWashArgb();
        float cardinalCornerPx = dpToPx(10);
        for (int s = 0; s < AlternatePopupGeometry.SLOT_COUNT; s++) {
            TextView tv = alternateSlotViews[s];
            if (tv == null) {
                continue;
            }
            AlternateOption opt = currentAlternatePopupModel.slotOptions[s];
            if (opt == null) {
                tv.setBackgroundResource(android.R.color.transparent);
                continue;
            }
            if (highlightSlot >= 0 && s == highlightSlot) {
                tv.setBackgroundResource(R.drawable.alternate_popup_option_selected_background);
            } else if (isAlternateCardinalSlot(s) && cardinalWashArgb != 0) {
                setAlternateCardinalIdleBackground(tv, cardinalWashArgb, cardinalCornerPx);
            } else {
                tv.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private void showAlternatesPopup(View anchor, Key key) {
        AlternatePopupModel model = buildAlternatePopupModel(key);
        if (model.countSelectable() < 2) {
            return;
        }
        boolean landscape = isLandscape(getContext());
        int containerPaddingDp = landscape
                ? ALT_POPUP_CONTAINER_PADDING_DP_LANDSCAPE
                : ALT_POPUP_CONTAINER_PADDING_DP_PORTRAIT;
        int optionMarginDp = landscape
                ? ALT_POPUP_OPTION_MARGIN_DP_LANDSCAPE
                : ALT_POPUP_OPTION_MARGIN_DP_PORTRAIT;
        int optionTextSp = landscape
                ? ALT_POPUP_OPTION_TEXT_SP_LANDSCAPE
                : ALT_POPUP_OPTION_TEXT_SP_PORTRAIT;
        int optionPadHorizontalDp = landscape
                ? ALT_POPUP_OPTION_PADDING_HORIZONTAL_DP_LANDSCAPE
                : ALT_POPUP_OPTION_PADDING_HORIZONTAL_DP_PORTRAIT;
        int optionPadVerticalDp = landscape
                ? ALT_POPUP_OPTION_PADDING_VERTICAL_DP_LANDSCAPE
                : ALT_POPUP_OPTION_PADDING_VERTICAL_DP_PORTRAIT;

        dismissAlternatesPopup();
        alternateAnchorView = anchor;
        currentAlternatePopupModel = model;
        currentAlternatePick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAlternatePickForHaptic = AlternatePopupGeometry.RESULT_DEFAULT;
        Arrays.fill(alternateSlotViews, null);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setBackgroundResource(R.drawable.alternate_popup_background);
        int containerPaddingPx = dpToPx(containerPaddingDp);
        grid.setPadding(containerPaddingPx, containerPaddingPx, containerPaddingPx, containerPaddingPx);

        int[][] slotPlacement = {
                {
                        AlternatePopupGeometry.SLOT_UP_LEFT,
                        AlternatePopupGeometry.SLOT_UP,
                        AlternatePopupGeometry.SLOT_UP_RIGHT,
                },
                {
                        AlternatePopupGeometry.SLOT_LEFT,
                        AlternatePopupGeometry.SLOT_CENTER,
                        AlternatePopupGeometry.SLOT_RIGHT,
                },
                {
                        AlternatePopupGeometry.SLOT_DOWN_LEFT,
                        AlternatePopupGeometry.SLOT_DOWN,
                        AlternatePopupGeometry.SLOT_DOWN_RIGHT,
                }
        };
        int minRow = 2;
        int maxRow = 0;
        int minCol = 2;
        int maxCol = 0;
        boolean hasAnyDisplayedCell = false;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = slotPlacement[row][col];
                if (model.slotOptions[slot] != null) {
                    hasAnyDisplayedCell = true;
                    if (row < minRow) minRow = row;
                    if (row > maxRow) maxRow = row;
                    if (col < minCol) minCol = col;
                    if (col > maxCol) maxCol = col;
                }
            }
        }
        if (!hasAnyDisplayedCell) {
            minRow = 1;
            maxRow = 1;
            minCol = 1;
            maxCol = 1;
        }
        boolean hasBottomRowAlternates =
                model.slotOptions[AlternatePopupGeometry.SLOT_DOWN_LEFT] != null
                        || model.slotOptions[AlternatePopupGeometry.SLOT_DOWN] != null
                        || model.slotOptions[AlternatePopupGeometry.SLOT_DOWN_RIGHT] != null;
        grid.setRowCount(maxRow - minRow + 1);
        grid.setColumnCount(maxCol - minCol + 1);
        int marginPx = dpToPx(optionMarginDp);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                int slot = slotPlacement[row][col];
                TextView cell = new TextView(getContext());
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                        GridLayout.spec(row - minRow),
                        GridLayout.spec(col - minCol));
                glp.width = LayoutParams.WRAP_CONTENT;
                glp.height = LayoutParams.WRAP_CONTENT;
                glp.setMargins(marginPx, marginPx, marginPx, marginPx);
                cell.setLayoutParams(glp);
                AlternateOption shown = model.slotOptions[slot];
                cell.setText(shown != null ? shown.display : "");
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, optionTextSp);
                cell.setTypeface(Typeface.SANS_SERIF);
                int ph = dpToPx(optionPadHorizontalDp);
                int pv = dpToPx(optionPadVerticalDp);
                cell.setPadding(ph, pv, ph, pv);
                cell.setGravity(Gravity.CENTER);
                cell.setTextColor(resolveThemeTextColor());
                int minCell = dpToPx(ALT_POPUP_CELL_MIN_SIZE_DP);
                cell.setMinimumWidth(minCell);
                cell.setMinimumHeight(minCell);
                if (shown == null) {
                    cell.setAlpha(0.35f);
                } else {
                    cell.setAlpha(1f);
                }
                grid.addView(cell);
                alternateSlotViews[slot] = cell;
            }
        }
        alternatePopupContainer = grid;
        applyAlternatePickHighlight();
        performAlternatesPopupHaptic(anchor, HapticFeedbackConstants.LONG_PRESS);

        alternatePopupWindow = new PopupWindow(grid,
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false);
        alternatePopupWindow.setOutsideTouchable(false);
        alternatePopupWindow.setClippingEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alternatePopupWindow.setElevation(dpToPx(12));
        }
        grid.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        anchor.post(() -> {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            int popupX = (int) (loc[0] + (anchor.getWidth() / 2f) - (grid.getMeasuredWidth() / 2f));
            int verticalOffsetDp = ALT_POPUP_VERTICAL_OFFSET_DP
                    + (hasBottomRowAlternates ? ALT_POPUP_BOTTOM_ROW_EXTRA_OFFSET_DP : 0);
            int popupY = loc[1] - dpToPx(verticalOffsetDp);
            alternatePopupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY);
            applyAlternatePickHighlight();
        });
    }

    private void updateAlternateSelection(float rawX, float rawY) {
        if (alternatePopupContainer == null || currentAlternatePopupModel == null || alternateAnchorView == null) {
            return;
        }
        float dx = rawX - alternatesGestureStartRawX;
        float dy = rawY - alternatesGestureStartRawY;
        boolean[] occ = currentAlternatePopupModel.slotOccupiedForGestureFlags();
        int pick = AlternatePopupGeometry.pickSlot(dx, dy,
                dpToPx(ALT_GESTURE_R_MIN_DP),
                dpToPx(ALT_GESTURE_R_CANCEL_DP),
                dpToPx(ALT_GESTURE_AXIS_DEADZONE_DP),
                occ);
        pick = currentAlternatePopupModel.resolveGesturePick(pick);
        if (pick != currentAlternatePick) {
            onAlternatePickChanged(alternateAnchorView, pick);
            currentAlternatePick = pick;
            applyAlternatePickHighlight();
        }
    }

    private void commitCurrentAlternateSelection() {
        if (currentAlternatePopupModel == null) {
            return;
        }
        if (currentAlternatePick == AlternatePopupGeometry.RESULT_CANCEL) {
            return;
        }
        AlternateOption opt = currentAlternatePopupModel.resolveCommittedOption(currentAlternatePick);
        if (opt != null) {
            sendAlternateOption(opt);
        }
    }

    private void sendAlternateOption(AlternateOption option) {
        if (option.unicodeCodePoint != 0) {
            ConnectionManager cm = peekConnectionManager();
            if (cm == null) {
                Log.w(TAG, "Unicode alternate needs ConnectionManager; skipping U+"
                        + Integer.toHexString(option.unicodeCodePoint));
                return;
            }
            String targetOs = getTargetOs();
            String ch = new String(Character.toChars(option.unicodeCodePoint));
            imeTextExecutor.execute(() -> {
                try {
                    HidTextKeystrokeSender.send(ch, cm, targetOs, true, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return;
        }
        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += isShiftLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;
        combinedValue += isCtrlRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("CtrlR")) : 0;
        combinedValue += isAltRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("AltR")) : 0;
        combinedValue += isWinRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("WinR")) : 0;
        if ((option.modifierMask & MOD_CTRL) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((option.modifierMask & MOD_SHIFT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((option.modifierMask & MOD_ALT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((option.modifierMask & MOD_WIN) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }
        if (option.requiresShift) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        sendKeyData(combinedValue, option.keyCode);
    }

    private void dismissAlternatesPopup() {
        if (alternatePopupWindow != null) {
            alternatePopupWindow.dismiss();
            alternatePopupWindow = null;
        }
        if (alternateAnchorView != null) {
            alternateAnchorView.setPressed(false);
            alternateAnchorView = null;
        }
        alternatePopupContainer = null;
        Arrays.fill(alternateSlotViews, null);
        currentAlternatePopupModel = null;
        currentAlternatePick = AlternatePopupGeometry.RESULT_DEFAULT;
        lastAlternatePickForHaptic = Integer.MIN_VALUE;
        kmProKeyPreview.dismiss();
    }

    private void performKeyHapticFeedback(View view) {
        if (view == null || getContext() == null) {
            return;
        }
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("haptic_feedback", true);
        if (!enabled) {
            return;
        }
        view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        );
    }

    private AlternateOption mapAsciiAlternate(String token) {
        if (TextUtils.isEmpty(token) || token.length() != 1) {
            return null;
        }
        char c = token.charAt(0);
        if (c >= 'a' && c <= 'z') {
            return new AlternateOption(token, 0x04 + (c - 'a'), false);
        }
        if (c >= 'A' && c <= 'Z') {
            return new AlternateOption(token, 0x04 + (c - 'A'), true);
        }
        switch (c) {
            case '1': return new AlternateOption(token, 0x1E, false);
            case '!': return new AlternateOption(token, 0x1E, true);
            case '2': return new AlternateOption(token, 0x1F, false);
            case '@': return new AlternateOption(token, 0x1F, true);
            case '3': return new AlternateOption(token, 0x20, false);
            case '#': return new AlternateOption(token, 0x20, true);
            case '4': return new AlternateOption(token, 0x21, false);
            case '$': return new AlternateOption(token, 0x21, true);
            case '5': return new AlternateOption(token, 0x22, false);
            case '%': return new AlternateOption(token, 0x22, true);
            case '6': return new AlternateOption(token, 0x23, false);
            case '^': return new AlternateOption(token, 0x23, true);
            case '7': return new AlternateOption(token, 0x24, false);
            case '&': return new AlternateOption(token, 0x24, true);
            case '8': return new AlternateOption(token, 0x25, false);
            case '*': return new AlternateOption(token, 0x25, true);
            case '9': return new AlternateOption(token, 0x26, false);
            case '(': return new AlternateOption(token, 0x26, true);
            case '0': return new AlternateOption(token, 0x27, false);
            case ')': return new AlternateOption(token, 0x27, true);
            case '-': return new AlternateOption(token, 0x2D, false);
            case '_': return new AlternateOption(token, 0x2D, true);
            case '=': return new AlternateOption(token, 0x2E, false);
            case '+': return new AlternateOption(token, 0x2E, true);
            case '[': return new AlternateOption(token, 0x2F, false);
            case '{': return new AlternateOption(token, 0x2F, true);
            case ']': return new AlternateOption(token, 0x30, false);
            case '}': return new AlternateOption(token, 0x30, true);
            case ',': return new AlternateOption(token, 0x36, false);
            case '<': return new AlternateOption(token, 0x36, true);
            case '.': return new AlternateOption(token, 0x37, false);
            case '>': return new AlternateOption(token, 0x37, true);
            case '/': return new AlternateOption(token, 0x38, false);
            case '?': return new AlternateOption(token, 0x38, true);
            case ';': return new AlternateOption(token, 0x33, false);
            case ':': return new AlternateOption(token, 0x33, true);
            case '\'': return new AlternateOption(token, 0x34, false);
            case '"': return new AlternateOption(token, 0x34, true);
            case '`': return new AlternateOption(token, 0x35, false);
            case '~': return new AlternateOption(token, 0x35, true);
            case '\\': return new AlternateOption(token, 0x31, false);
            case '|': return new AlternateOption(token, 0x31, true);
            // Currency alternates follow existing extra-numpad Fn mappings.
            // These are host-layout dependent but provide practical shortcuts.
            case '€': return new AlternateOption(token, 0x1F, false, MOD_ALT | MOD_SHIFT);
            case '¥': return new AlternateOption(token, 0x1C, false, MOD_ALT);
            case '£': return new AlternateOption(token, 0x20, false, MOD_ALT);
            // No single standard HID usage; host entry via Unicode compose (see HidTextKeystrokeSender).
            case '₹': return new AlternateOption(token, 0, false, 0, 0x20B9);
            case '₩': return new AlternateOption(token, 0, false, 0, 0x20A9);
            case '₽': return new AlternateOption(token, 0, false, 0, 0x20BD);
            case '₺': return new AlternateOption(token, 0, false, 0, 0x20BA);
            default: return null;
        }
    }

    /**
     * Builds the number-pad subview as a custom nested layout:
     *   Top section (weight 3): left operator panel (+,-,*,/) + number grid (1-9 + right column)
     *   Bottom row (weight 1): ABC , !?# 0 = . Enter
     */
    private void buildNumberPadLayout() {
        int m = dpToPx(KEY_OUTER_MARGIN_DP);

        // Single horizontal section: op panel (left, weight 1) + num grid (right, weight 9)
        LinearLayout mainSection = new LinearLayout(getContext());
        mainSection.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
        mainSection.setOrientation(LinearLayout.HORIZONTAL);

        // ── Left panel: +, -, *, ABC (4 equal rows) ──────────────────
        LinearLayout opPanel = new LinearLayout(getContext());
        opPanel.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));
        opPanel.setOrientation(LinearLayout.VERTICAL);

        // Row 4 of op panel is ABC (function); rows 1-3 are +, -, *
        String[] opLabels   = {"+", "-", "*", "ABC"};
        int[]    opCodes    = {0x2E, 0x2D, 0x25, 0xF002};
        String[] opCodeStrs = {"2E",  "2D",  "25",  "F002"};
        boolean[] opShift   = {true,  false, true,  false};
        boolean[] opIsFn    = {false, false, false, true};
        for (int i = 0; i < opLabels.length; i++) {
            Key k = new Key(opLabels[i], "", opCodes[i], opCodeStrs[i], 10.0f, 0, 0f, false, opShift[i]);
            Button btn = new Button(getContext());
            applyFlatKeyStyle(btn);
            LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
            p.setMargins(m, m, m, m);
            btn.setLayoutParams(p);
            btn.setBackgroundResource(opIsFn[i]
                    ? R.drawable.function_button_background
                    : R.drawable.key_background);
            btn.setGravity(Gravity.CENTER);
            btn.setTextSize(12);
            btn.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            btn.setText(k.label);
            btn.setTextColor(resolveThemeTextColor());
            attachKeyListeners(btn, k);
            opPanel.addView(btn);
        }
        mainSection.addView(opPanel);

        // ── Number grid: 4 equal rows ─────────────────────────────────
        // Right column (%, Space, ⌫, Enter) all have weight 1 → same column width.
        // Number keys have weight 2. Row 4: [/(w2), 0(w2), !?#(w2), Enter(w1)]
        // so 0 sits directly under 8.
        LinearLayout numGrid = new LinearLayout(getContext());
        numGrid.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 9.0f));
        numGrid.setOrientation(LinearLayout.VERTICAL);

        Key[][] numRows = {
            {
                new Key("1", "", 0x1E, "1E", 20.0f, 0, 0f, false),
                new Key("2", "", 0x1F, "1F", 20.0f, 0, 0f, false),
                new Key("3", "", 0x20, "20", 20.0f, 0, 0f, false),
                new Key("%", "", 0x22, "22", 10.0f, 0, 0f, false, true)
            },
            {
                new Key("4", "", 0x21, "21", 20.0f, 0, 0f, false),
                new Key("5", "", 0x22, "22", 20.0f, 0, 0f, false),
                new Key("6", "", 0x23, "23", 20.0f, 0, 0f, false),
                new Key("Space", "", 0x2C, "2C", 10.0f, 0, 0f, false)
            },
            {
                new Key("7", "", 0x24, "24", 20.0f, 0, 0f, false),
                new Key("8", "", 0x25, "25", 20.0f, 0, 0f, false),
                new Key("9", "", 0x26, "26", 20.0f, 0, 0f, false),
                new Key("BackSpace", "", 0x2A, "2A", 10.0f, R.drawable.backspace, 0f, true)
            },
            {
                // / under 7, 0 under 8 (same weight=2), !?# under 9, Enter in right col
                new Key("/",    "", 0x38,   "38",   20.0f, 0, 0f, false),
                new Key("0",    "", 0x27,   "27",   20.0f, 0, 0f, false),
                new Key("!?#",  "", 0xF004, "F004", 20.0f, 0, 0f, false),
                new Key("Enter","", 0x28,   "28",   10.0f, R.drawable.keyboard_return_24px, 0f, false)
            }
        };

        for (Key[] rowKeys : numRows) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (Key k : rowKeys) {
                float weight = k.widthPercent / 10.0f;
                LayoutParams p = new LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
                p.setMargins(m, m, m, m);
                View btn;
                if ("BackSpace".equals(k.label)) {
                    ImageButton ib = new ImageButton(getContext());
                    applyFlatKeyStyle(ib);
                    ib.setLayoutParams(p);
                    ib.setBackgroundResource(R.drawable.key_background);
                    ib.setImageResource(k.iconResId);
                    ib.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                    ib.setScaleX(isFnLocked ? -1f : 1f);
                    ib.setColorFilter(resolveThemeTextColor());
                    ib.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    attachKeyListeners(ib, k);
                    btn = ib;
                } else {
                    Button b = new Button(getContext());
                    applyFlatKeyStyle(b);
                    b.setLayoutParams(p);
                    b.setGravity(Gravity.CENTER);
                    b.setTextSize(12);
                    b.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                    b.setText(k.label);
                    b.setTextColor(resolveThemeTextColor());
                    if ("Space".equals(k.label) || k.code == 0xF004 || k.code == 0x28) {
                        b.setBackgroundResource(R.drawable.function_button_background);
                    } else {
                        b.setBackgroundResource(R.drawable.key_background);
                    }
                    attachKeyListeners(b, k);
                    btn = b;
                }
                rowLayout.addView(btn);
            }
            numGrid.addView(rowLayout);
        }
        mainSection.addView(numGrid);
        addView(mainSection);
    }

    /**
     * Adds compact shortcut rows above the letter keyboard.
     */
    private void addTopFunctionRows() {
        rebuildTopShortcutPanels();
        rebuildFixedTopRowsPanels();
        if (topShortcutPanels.isEmpty()) {
            return;
        }

        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }

        float topStripWeight = TOP_PANEL_TOTAL_WEIGHT;
        LinearLayout topStripContainer = new LinearLayout(getContext());
        int portraitShortcutStripHeight =
                getResources().getDimensionPixelSize(R.dimen.compose_shortcut_strip_height);
        // KM Pro landscape full keyboard: strip : letter grid matches split column weights (see constants).
        if (useKmProLandscapeFullShortcutStripHeightWeightRatio()) {
            topStripContainer.setLayoutParams(
                    new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            0,
                            KM_PRO_LANDSCAPE_SHORTCUT_STRIP_HEIGHT_WEIGHT));
        } else if (!shortcutsStripOnly && splitPart == SPLIT_NONE) {
            // Portrait + landscape non-full: fixed shortcut-strip height so the letter grid gets the
            // remainder; fixed height avoids starving QWERTY vs macro rows on small landscape widths.
            topStripContainer.setLayoutParams(
                    new LayoutParams(LayoutParams.MATCH_PARENT, portraitShortcutStripHeight, 0f));
            topStripContainer.setMinimumHeight(portraitShortcutStripHeight);
        } else {
            topStripContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, topStripWeight));
        }
        topStripContainer.setOrientation(VERTICAL);
        FrameLayout viewport = new FrameLayout(getContext());
        viewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_SCROLLABLE_ROW_WEIGHT));
        fixedTopRowsViewport = new FrameLayout(getContext());
        fixedTopRowsViewport.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, TOP_PANEL_FIXED_ROWS_WEIGHT));
        previousFixedTopRowsContainer = createTopShortcutPanelView();
        activeFixedTopRowsContainer = createTopShortcutPanelView();
        nextFixedTopRowsContainer = createTopShortcutPanelView();
        fixedTopRowsViewport.addView(previousFixedTopRowsContainer);
        fixedTopRowsViewport.addView(activeFixedTopRowsContainer);
        fixedTopRowsViewport.addView(nextFixedTopRowsContainer);
        topPanelViewport = viewport;
        topPanelRootContainer = topStripContainer;
        topStripContainer.addView(viewport);
        topStripContainer.addView(fixedTopRowsViewport);
        initializeTopPanelViewport();
        addView(topStripContainer);
    }

    private void initializeTopPanelViewport() {
        if (topPanelViewport == null) {
            return;
        }

        topPanelViewport.removeAllViews();
        previousTopPanelContainer = createTopShortcutPanelView();
        activeTopPanelContainer = createTopShortcutPanelView();
        nextTopPanelContainer = createTopShortcutPanelView();
        topPanelViewport.addView(previousTopPanelContainer);
        topPanelViewport.addView(activeTopPanelContainer);
        topPanelViewport.addView(nextTopPanelContainer);
        syncTopPanelViewportContent();
        topPanelViewport.post(() -> resetTopPanelPositions(0f));
    }

    private void syncTopPanelViewportContent() {
        if (fixedTopRowsPanels.isEmpty()) {
            rebuildFixedTopRowsPanels();
        }
        if (fixedTopRowsPageIndex < 0) {
            fixedTopRowsPageIndex = 0;
        }
        if (fixedTopRowsPageIndex >= fixedTopRowsPanels.size()) {
            fixedTopRowsPageIndex = fixedTopRowsPanels.size() - 1;
        }
        if (topShortcutPanels.isEmpty()) {
            rebuildTopShortcutPanels();
        }
        if (topShortcutPanels.isEmpty()) {
            return;
        }
        if (topPanelPageIndex < 0) {
            topPanelPageIndex = 0;
        }
        if (topPanelPageIndex >= topShortcutPanels.size()) {
            topPanelPageIndex = topShortcutPanels.size() - 1;
        }
        TopShortcutPanel previous = topPanelPageIndex > 0
                ? topShortcutPanels.get(topPanelPageIndex - 1)
                : null;
        TopShortcutPanel active = topShortcutPanels.get(topPanelPageIndex);
        TopShortcutPanel next = topPanelPageIndex < topShortcutPanels.size() - 1
                ? topShortcutPanels.get(topPanelPageIndex + 1)
                : null;

        if (isSplitTopPanelMode()) {
            bindSplitTopShortcutPanelView(
                    splitPreviousTopPanelContainerLeft,
                    splitPreviousTopPanelContainerRight,
                    previous);
            bindSplitTopShortcutPanelView(
                    splitActiveTopPanelContainerLeft,
                    splitActiveTopPanelContainerRight,
                    active);
            bindSplitTopShortcutPanelView(
                    splitNextTopPanelContainerLeft,
                    splitNextTopPanelContainerRight,
                    next);
            if (splitActiveTopPanelContainerLeft != null) {
                splitActiveTopPanelContainerLeft.bringToFront();
            }
            if (splitActiveTopPanelContainerRight != null) {
                splitActiveTopPanelContainerRight.bringToFront();
            }
            List<Key> fixedPrevious = fixedTopRowsPageIndex > 0
                    ? fixedTopRowsPanels.get(fixedTopRowsPageIndex - 1) : null;
            List<Key> fixedActive = fixedTopRowsPanels.get(fixedTopRowsPageIndex);
            List<Key> fixedNext = fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                    ? fixedTopRowsPanels.get(fixedTopRowsPageIndex + 1) : null;
            bindSplitFixedTopRowsPanelView(
                    splitPreviousFixedTopRowsContainerLeft,
                    splitPreviousFixedTopRowsContainerRight,
                    fixedPrevious,
                    fixedTopRowsPageIndex > 0 ? fixedTopRowsPageIndex - 1 : -1);
            bindSplitFixedTopRowsPanelView(
                    splitActiveFixedTopRowsContainerLeft,
                    splitActiveFixedTopRowsContainerRight,
                    fixedActive,
                    fixedTopRowsPageIndex);
            bindSplitFixedTopRowsPanelView(
                    splitNextFixedTopRowsContainerLeft,
                    splitNextFixedTopRowsContainerRight,
                    fixedNext,
                    fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                            ? fixedTopRowsPageIndex + 1
                            : -1);
            if (splitActiveFixedTopRowsContainerLeft != null) {
                splitActiveFixedTopRowsContainerLeft.bringToFront();
            }
            if (splitActiveFixedTopRowsContainerRight != null) {
                splitActiveFixedTopRowsContainerRight.bringToFront();
            }
            return;
        }

        bindTopShortcutPanelView(previousTopPanelContainer, previous);
        bindTopShortcutPanelView(activeTopPanelContainer, active);
        bindTopShortcutPanelView(nextTopPanelContainer, next);
        if (activeTopPanelContainer != null) {
            activeTopPanelContainer.bringToFront();
        }
        List<Key> fixedPrevious = fixedTopRowsPageIndex > 0
                ? fixedTopRowsPanels.get(fixedTopRowsPageIndex - 1) : null;
        List<Key> fixedActive = fixedTopRowsPanels.get(fixedTopRowsPageIndex);
        List<Key> fixedNext = fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                ? fixedTopRowsPanels.get(fixedTopRowsPageIndex + 1) : null;
        bindFixedTopRowsPanelView(
                previousFixedTopRowsContainer,
                fixedPrevious,
                0,
                TOP_PANEL_COLUMNS,
                fixedTopRowsPageIndex > 0 ? fixedTopRowsPageIndex - 1 : -1);
        bindFixedTopRowsPanelView(
                activeFixedTopRowsContainer,
                fixedActive,
                0,
                TOP_PANEL_COLUMNS,
                fixedTopRowsPageIndex);
        bindFixedTopRowsPanelView(
                nextFixedTopRowsContainer,
                fixedNext,
                0,
                TOP_PANEL_COLUMNS,
                fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                        ? fixedTopRowsPageIndex + 1
                        : -1);
        if (activeFixedTopRowsContainer != null) {
            activeFixedTopRowsContainer.bringToFront();
        }
    }

    private LinearLayout createTopShortcutPanelView() {
        LinearLayout topPanelContainer = new LinearLayout(getContext());
        topPanelContainer.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
        topPanelContainer.setOrientation(VERTICAL);
        return topPanelContainer;
    }

    private void bindTopShortcutPanelView(LinearLayout topPanelContainer, TopShortcutPanel panel) {
        if (topPanelContainer == null) {
            return;
        }
        topPanelContainer.removeAllViews();
        if (panel == null) {
            topPanelContainer.setVisibility(View.INVISIBLE);
            return;
        }
        topPanelContainer.setVisibility(View.VISIBLE);
        addShortcutPanelRows(topPanelContainer, panel.keys, 0, TOP_PANEL_COLUMNS, 0, 1);
    }

    private void bindSplitTopShortcutPanelView(
            LinearLayout leftContainer,
            LinearLayout rightContainer,
            TopShortcutPanel panel
    ) {
        bindTopShortcutPanelViewWithColumns(
                leftContainer,
                panel,
                TOP_PANEL_LEFT_START_COL,
                TOP_PANEL_LEFT_END_COL
        );
        bindTopShortcutPanelViewWithColumns(
                rightContainer,
                panel,
                TOP_PANEL_RIGHT_START_COL,
                TOP_PANEL_RIGHT_END_COL
        );
    }

    private void bindTopShortcutPanelViewWithColumns(
            LinearLayout topPanelContainer,
            TopShortcutPanel panel,
            int startColInclusive,
            int endColExclusive
    ) {
        if (topPanelContainer == null) {
            return;
        }
        topPanelContainer.removeAllViews();
        if (panel == null) {
            topPanelContainer.setVisibility(View.INVISIBLE);
            return;
        }
        topPanelContainer.setVisibility(View.VISIBLE);
        addShortcutPanelRows(topPanelContainer, panel.keys, startColInclusive, endColExclusive, 0, 1);
    }

    private void bindFixedTopRowsPanelView(
            LinearLayout container,
            List<Key> panelKeys,
            int startColInclusive,
            int endColExclusive,
            int fixedTopBindPageIndex
    ) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        if (panelKeys == null) {
            container.setVisibility(View.INVISIBLE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        addShortcutPanelRows(
                container, panelKeys, startColInclusive, endColExclusive, 0, 2, fixedTopBindPageIndex);
    }

    private void bindSplitFixedTopRowsPanelView(
            LinearLayout leftContainer,
            LinearLayout rightContainer,
            List<Key> panelKeys,
            int fixedTopBindPageIndex
    ) {
        bindFixedTopRowsPanelView(
                leftContainer,
                panelKeys,
                TOP_PANEL_LEFT_START_COL,
                TOP_PANEL_LEFT_END_COL,
                fixedTopBindPageIndex
        );
        bindFixedTopRowsPanelView(
                rightContainer,
                panelKeys,
                TOP_PANEL_RIGHT_START_COL,
                TOP_PANEL_RIGHT_END_COL,
                fixedTopBindPageIndex
        );
    }

    private void rebuildTopShortcutPanels() {
        topShortcutPanels.clear();
        ShortcutProfileManager.ShortcutProfile activeProfile = shortcutProfileManager.getActiveProfile();
        List<ShortcutProfileManager.Shortcut> ordered = activeProfile != null
                ? shortcutProfileManager.getOrderedShortcutsForTopStrip(activeProfile.id)
                : new ArrayList<>();
        if (ordered == null || ordered.isEmpty()) {
            ordered = new ArrayList<>();
        }

        int favoriteCount = ordered.size();
        int totalSlots = favoriteCount + 2;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalSlots / TOP_ROW_PAGE_SIZE));
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int pageStart = pageIndex * TOP_ROW_PAGE_SIZE;
            List<Key> pageKeys = buildTopPanelPageKeys(ordered, pageStart);
            String title = activeProfile != null ? activeProfile.name : "Default";
            if (totalPages > 1) {
                title = title + " " + (pageIndex + 1) + "/" + totalPages;
            }
            topShortcutPanels.add(new TopShortcutPanel(title, pageKeys));
        }
        if (topShortcutPanels.isEmpty()) {
            topPanelPageIndex = 0;
        } else {
            if (topPanelPageIndex < 0) {
                topPanelPageIndex = 0;
            }
            if (topPanelPageIndex >= topShortcutPanels.size()) {
                topPanelPageIndex = topShortcutPanels.size() - 1;
            }
        }
    }

    private void rebuildFixedTopRowsPanels() {
        fixedTopRowsPanels.clear();
        fixedTopRowsPanels.add(buildFixedTopRowsPage0());
        fixedTopRowsPanels.add(buildFixedTopRowsPage1());
        fixedTopRowsPanels.add(buildFixedTopRowsPage2());
        if (fixedTopRowsPageIndex < 0 || fixedTopRowsPageIndex >= fixedTopRowsPanels.size()) {
            fixedTopRowsPageIndex = FIXED_TOP_ROWS_DEFAULT_PAGE_INDEX;
        }
    }

    private List<Key> buildTopPanelPageKeys(
            List<ShortcutProfileManager.Shortcut> orderedFavorites,
            int pageStartGlobalIndex
    ) {
        List<Key> keys = new ArrayList<>(TOP_PANEL_PAGE_SIZE);
        keys.addAll(buildProfileTopRow(orderedFavorites, pageStartGlobalIndex));
        keys.addAll(buildFixedTopPanelRows());
        return keys;
    }

    /**
     * Row 1: favorites plus trailing {@link #KEY_TOP_STRIP_CREATE_SHORTCUT} then
     * {@link #KEY_TOP_SHORTCUT_DISPLAY_TOGGLE} on the same page (typically last page).
     */
    private List<Key> buildProfileTopRow(
            List<ShortcutProfileManager.Shortcut> orderedFavorites,
            int pageStartGlobalIndex
    ) {
        int n = orderedFavorites != null ? orderedFavorites.size() : 0;
        int createIndex = n;
        int displayIndex = n + 1;
        List<Key> row = new ArrayList<>(TOP_ROW_PAGE_SIZE);
        for (int i = 0; i < TOP_ROW_PAGE_SIZE; i++) {
            int globalIndex = pageStartGlobalIndex + i;
            if (globalIndex < n) {
                row.add(buildTopPanelShortcutKey(orderedFavorites.get(globalIndex), globalIndex));
            } else if (globalIndex == createIndex) {
                row.add(buildTopStripCreateShortcutKey());
            } else if (globalIndex == displayIndex) {
                row.add(buildTopStripDisplayToggleKey());
            } else {
                Key k = new Key("", "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true);
                k.topStripFavoriteSlotIndex = -1;
                row.add(k);
            }
        }
        return row;
    }

    private Key buildTopStripCreateShortcutKey() {
        Key key = new Key("", "", KEY_TOP_STRIP_CREATE_SHORTCUT, "", 1f,
                R.drawable.ic_top_strip_create_shortcut_24, 0f, false, false, -1, true);
        key.topStripFavoriteSlotIndex = -1;
        return key;
    }

    private Key buildTopStripDisplayToggleKey() {
        Key key = new Key("DISPLAY", "", KEY_TOP_SHORTCUT_DISPLAY_TOGGLE, "", 1f,
                resolveDisplayToggleKeyIconRes(), 0f, false, false, -1, true);
        key.topStripFavoriteSlotIndex = -1;
        return key;
    }

    private Key buildTopPanelShortcutKey(
            ShortcutProfileManager.Shortcut shortcut,
            int topStripFavoriteSlotIndex
    ) {
        if (shortcut == null) {
            Key k = new Key("", "", KEY_NOOP_PLACEHOLDER, "", 1f, 0, 0f, false, false, -1, true);
            k.topStripFavoriteSlotIndex = topStripFavoriteSlotIndex;
            return k;
        }
        String label = compactShortcutName(shortcut);
        String symbol = compactShortcutSymbol(shortcut);
        int iconResId = resolveShortcutIconRes(shortcut.icon);
        int normalizedModifiers = normalizeShortcutModifiersForTargetOs(shortcut.modifiers);
        Key key = new Key(
                label,
                symbol,
                shortcut.keyCode,
                String.format("%02X", shortcut.keyCode),
                1f,
                iconResId,
                0f,
                false,
                false,
                normalizedModifiers,
                true
        );
        key.topStripFavoriteSlotIndex = topStripFavoriteSlotIndex;
        if (iconResId == 0 && isEmojiIcon(shortcut.icon)) {
            key.customIconGlyph = shortcut.icon.trim();
        } else {
            key.customIconGlyph = "";
        }
        return key;
    }

    private int normalizeShortcutModifiersForTargetOs(int modifiers) {
        String targetOs = getTargetOs();
        if ("macos".equals(targetOs)) {
            boolean hasCtrl = (modifiers & MOD_CTRL) != 0;
            boolean hasCmd = (modifiers & MOD_WIN) != 0;
            if (hasCtrl && !hasCmd) {
                return (modifiers & ~MOD_CTRL) | MOD_WIN;
            }
        }
        return modifiers;
    }

    /** FN strip icon resource: {@code ic_swap_horiz_24} normally; hidden (0) when Fn is latched so the key renders as text "Sw". */
    private int fnStripIconRes() {
        return fixedTopLocalFnLocked ? 0 : R.drawable.ic_swap_horiz_24;
    }

    /** FN strip label: "FN" normally; "Sw" when Fn is latched. */
    private String fnStripLabel() {
        return fixedTopLocalFnLocked ? "Sw" : "FN";
    }

    /**
     * Non-modifier keys whose fixed-strip press should mirror a momentary highlight on the matching
     * main-keyboard key. Main keyboard has no arrow cluster in any orientation; only {@code Enter}
     * (0x28) is shared between fixed strip and main keyboard.
     */
    private static boolean isStripMomentarySyncKey(int keyCode) {
        return keyCode == 0x28; // Enter
    }

    private List<Key> buildFixedTopRowsPage0() {
        List<Key> keys = new ArrayList<>(TOP_PANEL_COLUMNS * 2);
        // Row 2: HID F7–F12, = (scan 0x40–0x45, 0x2E). Strip shows digit/symbol caps when local Fn latch is off; F caps when latch on (see resolveFixedTopLocalFnMapping).
        keys.add(fixedStripSlotKey(new Key("F7", "", 0x40, "40", 1f, 0, 0f, false, false, -1, true), 0, 2, 0));
        keys.add(fixedStripSlotKey(new Key("F8", "", 0x41, "41", 1f, 0, 0f, false, false, -1, true), 0, 2, 1));
        keys.add(fixedStripSlotKey(new Key("F9", "", 0x42, "42", 1f, 0, 0f, false, false, -1, true), 0, 2, 2));
        keys.add(fixedStripSlotKey(new Key("F10", "", 0x43, "43", 1f, 0, 0f, false, false, -1, true), 0, 2, 3));
        keys.add(fixedStripSlotKey(new Key("F11", "", 0x44, "44", 1f, 0, 0f, false, false, -1, true), 0, 2, 4));
        keys.add(fixedStripSlotKey(new Key("F12", "", 0x45, "45", 1f, 0, 0f, false, false, -1, true), 0, 2, 5));
        keys.add(fixedStripSlotKey(new Key("=", "", 0x2E, "2E", 1f, 0, 0f, false, false, -1, true), 0, 2, 6));
        // Row 3: HID F1–F6 + local Fn toggle. Same latch rule as row 2: digit caps when latch off, F1–F6 when latch on.
        keys.add(fixedStripSlotKey(new Key("F1", "", 0x3A, "3A", 1f, 0, 0f, false, false, -1, true), 0, 3, 0));
        keys.add(fixedStripSlotKey(new Key("F2", "", 0x3B, "3B", 1f, 0, 0f, false, false, -1, true), 0, 3, 1));
        keys.add(fixedStripSlotKey(new Key("F3", "", 0x3C, "3C", 1f, 0, 0f, false, false, -1, true), 0, 3, 2));
        keys.add(fixedStripSlotKey(new Key("F4", "", 0x3D, "3D", 1f, 0, 0f, false, false, -1, true), 0, 3, 3));
        keys.add(fixedStripSlotKey(new Key("F5", "", 0x3E, "3E", 1f, 0, 0f, false, false, -1, true), 0, 3, 4));
        keys.add(fixedStripSlotKey(new Key("F6", "", 0x3F, "3F", 1f, 0, 0f, false, false, -1, true), 0, 3, 5));
        keys.add(markFixedRowKey(new Key(fnStripLabel(), "", KEY_FIXED_TOP_LOCAL_FN, "F00C", 1f, fnStripIconRes(), 0f, false, false, -1, true)));
        applyStripSlotOverrides(keys);
        return keys;
    }

    private List<Key> buildFixedTopRowsPage1() {
        List<Key> keys = new ArrayList<>(TOP_PANEL_COLUMNS * 2);
        // Row 2: CTRL, ALT, WIN/Command (target-OS aware), TAB(icon), Up, Enter(icon), keyboard (IME) toggle
        keys.add(fixedStripSlotKey(buildTopPanelModifierKey(0xE0), 1, 2, 0));
        keys.add(fixedStripSlotKey(buildTopPanelModifierKey(0xE2), 1, 2, 1));
        // GUI (Win/Cmd/Super) always on strip page 1; {@link #showGuiHidKey} only omits it from the main keyboard XML.
        keys.add(fixedStripSlotKey(buildTopPanelModifierKey(0xE3), 1, 2, 2));
        keys.add(fixedStripSlotKey(new Key("TAB", "", 0x2B, "2B", 1f, R.drawable.keyboard_tab_24, 0f, false, false, -1, true), 1, 2, 3));
        keys.add(fixedStripSlotKey(new Key("UP", "", 0x52, "52", 1f, R.drawable.keyboard_arrow_up_24, 0f, false, false, -1, true), 1, 2, 4));
        keys.add(fixedStripSlotKey(new Key("ENTER", "", 0x28, "28", 1f, R.drawable.keyboard_return_24px, 0f, false, false, -1, true), 1, 2, 5));
        // Row 2 col 7: secondary layout toggle (portrait BI/IME, landscape Full/Split).
        keys.add(fixedStripSlotKey(buildKmProSecondaryLayoutToggleKey(), 1, 2, 6));
        // Row 3: ESC, SHIFT, DEL, Left(icon), Down(icon), Right(icon), local Fn toggle
        keys.add(fixedStripSlotKey(new Key("ESC", "", 0x29, "29", 1f, 0, 0f, false, false, -1, true), 1, 3, 0));
        keys.add(fixedStripSlotKey(new Key("SHIFT", "", 0xE1, "E1", 1f, R.drawable.shift_24px, 0f, false, false, -1, true), 1, 3, 1));
        keys.add(fixedStripSlotKey(new Key("DEL", "", 0x4C, "4C", 1f, R.drawable.backspace_24, 0f, false, false, -1, true), 1, 3, 2));
        keys.add(fixedStripSlotKey(new Key("LEFT", "", 0x50, "50", 1f, R.drawable.keyboard_arrow_left_24, 0f, false, false, -1, true), 1, 3, 3));
        keys.add(fixedStripSlotKey(new Key("DOWN", "", 0x51, "51", 1f, R.drawable.keyboard_arrow_down_24, 0f, false, false, -1, true), 1, 3, 4));
        keys.add(fixedStripSlotKey(new Key("RIGHT", "", 0x4F, "4F", 1f, R.drawable.keyboard_arrow_right_24, 0f, false, false, -1, true), 1, 3, 5));
        keys.add(markFixedRowKey(new Key(fnStripLabel(), "", KEY_FIXED_TOP_LOCAL_FN, "F00C", 1f, fnStripIconRes(), 0f, false, false, -1, true)));
        applyStripSlotOverrides(keys);
        return keys;
    }

    /**
     * Optional override for the secondary layout toggle key label.
     * When non-null, this label is used instead of the preference-derived label.
     * This allows the terminal to set its own label independently of KM Pro preferences.
     */
    @Nullable private String secondaryToggleLabelOverride;

    /**
     * Set an explicit label for the IME/BI (or Split/Full) toggle key.
     * Pass null to revert to preference-derived labels.
     */
    public void setSecondaryToggleLabelOverride(@Nullable String label) {
        this.secondaryToggleLabelOverride = label;
    }

    private Key buildKmProSecondaryLayoutToggleKey() {
        Context ctx = getContext();
        if (ctx == null) {
            return new Key("BI", "", KEY_IME_TOGGLE, "F00A", 1f, 0, 0f, false, false, -1, true);
        }
        // Use override label if set (allows terminal to control its own label)
        if (secondaryToggleLabelOverride != null) {
            return new Key(secondaryToggleLabelOverride, "", KEY_IME_TOGGLE, "F00A",
                    1f, 0, 0f, false, false, -1, true);
        }
        if (isLandscape(ctx)) {
            boolean split = KmProSubmodePrefs.isLandscapeSplit(ctx);
            String label =
                    split
                            ? ctx.getString(R.string.km_pro_secondary_toggle_split_short)
                            : ctx.getString(R.string.km_pro_secondary_toggle_full_short);
            return new Key(label, "", KEY_IME_TOGGLE, "F00A", 1f, 0, 0f, false, false, -1, true);
        }
        boolean ime = KmProSubmodePrefs.isPortraitImeSurface(ctx);
        // Action-oriented labels: show "IME" while built-in is visible; show "BI" while system IME is up.
        String label =
                ime
                        ? ctx.getString(R.string.km_pro_secondary_toggle_built_in_short)
                        : ctx.getString(R.string.km_pro_secondary_toggle_ime_short);
        return new Key(label, "", KEY_IME_TOGGLE, "F00A", 1f, 0, 0f, false, false, -1, true);
    }

    private List<Key> buildFixedTopRowsPage2() {
        List<Key> keys = new ArrayList<>(TOP_PANEL_COLUMNS * 2);
        // Page 2 (Shortcut Hub): local Fn swaps row 2/3 punctuation.
        if (fixedTopLocalFnLocked) {
            // Fn latched: row 2 cols 1–2 are grave/tilde (strip f-p2r2c1 / f-p2r2c2).
            keys.add(fixedStripSlotKey(buildPage2PunctKey("`", 0x35, false), 2, 2, 0));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("~", 0x35, true), 2, 2, 1));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("'", 0x34, false), 2, 2, 2));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("\"", 0x34, true), 2, 2, 3));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("%", 0x22, true), 2, 2, 4));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("^", 0x23, true), 2, 2, 5));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("|", 0x31, true), 2, 2, 6));
        } else {
            // Fn off: base caps ( / ) on 9/0 keys + Shift (strip b-p2r2c1 / b-p2r2c2).
            keys.add(fixedStripSlotKey(buildPage2PunctKey("(", 0x26, true), 2, 2, 0));
            keys.add(fixedStripSlotKey(buildPage2PunctKey(")", 0x27, true), 2, 2, 1));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("[", 0x2F, false), 2, 2, 2));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("]", 0x30, false), 2, 2, 3));
            keys.add(fixedStripSlotKey(buildPage2PunctKey(":", 0x33, true), 2, 2, 4));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("#", 0x20, true), 2, 2, 5));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("@", 0x1F, true), 2, 2, 6));
        }
        if (fixedTopLocalFnLocked) {
            keys.add(fixedStripSlotKey(buildPage2PunctKey("<", 0x36, true), 2, 3, 0));
            keys.add(fixedStripSlotKey(buildPage2PunctKey(">", 0x37, true), 2, 3, 1));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("*", 0x25, true), 2, 3, 2));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("&", 0x24, true), 2, 3, 3));
            keys.add(fixedStripSlotKey(buildPage2PunctKey(",", 0x36, false), 2, 3, 4));
            keys.add(fixedStripSlotKey(buildPage2PunctKey(".", 0x37, false), 2, 3, 5));
        } else {
            keys.add(fixedStripSlotKey(buildPage2PunctKey("/", 0x38, false), 2, 3, 0));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("\\", 0x31, false), 2, 3, 1));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("|", 0x31, true), 2, 3, 2));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("?", 0x38, true), 2, 3, 3));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("-", 0x2D, false), 2, 3, 4));
            keys.add(fixedStripSlotKey(buildPage2PunctKey("_", 0x2D, true), 2, 3, 5));
        }
        keys.add(markFixedRowKey(new Key(fnStripLabel(), "", KEY_FIXED_TOP_LOCAL_FN, "F00C", 1f, fnStripIconRes(), 0f, false, false, -1, true)));
        applyStripSlotOverrides(keys);
        return keys;
    }

    private static Key buildPage2PunctKey(String label, int hidScanCode, boolean requiresShift) {
        String hex = Integer.toHexString(hidScanCode).toUpperCase();
        return new Key(label, "", hidScanCode, hex, 1f, 0, 0f, false, requiresShift, -1, true);
    }

    private List<Key> buildFixedTopPanelRows() {
        return buildFixedTopRowsPage1();
    }

    private int resolveFixedTopLocalFnIconRes(Key key, FnMapping mapping) {
        if (mapping == null) {
            return key != null ? key.iconResId : 0;
        }
        // Fn overlay: prefer text label over the base key's icon (e.g. Shift cap → END).
        return mapping.iconResId;
    }

    private Key markFixedRowKey(Key key) {
        if (key != null) {
            key.allowTopPanelPagingGesture = false;
        }
        return key;
    }

    private Key fixedStripSlotKey(Key key, int pageIndex, int stripRow, int col) {
        key = markFixedRowKey(key);
        if (key != null && key.code != KEY_FIXED_TOP_LOCAL_FN && key.code != KEY_IME_TOGGLE) {
            key.stripSlotPage = pageIndex;
            key.stripSlotRow = stripRow;
            key.stripSlotCol = col;
            key.stripSlotFactoryCode = key.code;
        }
        return key;
    }

    /**
     * Physical factory HID scan for a strip slot (before Rows 2–3 overrides rewrite {@link Key#code}).
     * Used for page-0 digit/F overlay typing and latch rules.
     */
    private static int resolvedStripSlotFactoryScanCode(@Nullable Key key) {
        if (key == null) {
            return 0;
        }
        if (key.stripSlotPage < 0) {
            return key.code;
        }
        if (key.stripSlotFactoryCode != 0) {
            return key.stripSlotFactoryCode;
        }
        FixedStripLayoutCatalog.FactoryHid fh = FixedStripLayoutCatalog.resolveFactoryHidForSlot(
                new FixedStripLayoutCatalog.ParsedSlotKey(
                        key.stripSlotPage, key.stripSlotRow, key.stripSlotCol, false));
        return fh != null ? fh.keyCode : key.code;
    }

    /**
     * Page 2 strip row 2 columns 1–2: {@link #buildFixedTopRowsPage2()} swaps physical caps when local Fn
     * latches; strip profile uses {@code b-p2r2c1}/{@code b-p2r2c2} for "(" / ")" and {@code f-p2r2c1}/
     * {@code f-p2r2c2} for "`" / "~". Primary slot suffix must follow {@link #fixedTopLocalFnLocked} so
     * {@link #applyStripSlotOverrides} does not overwrite the latched row back to the base shortcuts.
     */
    private static boolean isPage2Row2ParenGraveDualLayerStripSlot(@Nullable Key k) {
        return k != null && k.stripSlotPage == 2 && k.stripSlotRow == 2 && k.stripSlotCol >= 0 && k.stripSlotCol <= 1;
    }

    /**
     * Any page-2 row-2/3 strip slot follows {@link #fixedTopLocalFnLocked} so {@link #buildFixedTopRowsPage2()}
     * (which swaps physical caps per latch) and {@link #applyStripSlotOverrides} read the same layer
     * (b-p2… when off, f-p2… when on). Without this, Rows 2–3 strip profile overrides only
     * see the base layer slot map and Fn-layer glyphs are never rendered.
     */
    private static boolean isPage2Rows23DualLayerStripSlot(@Nullable Key k) {
        return k != null && k.stripSlotPage == 2 && (k.stripSlotRow == 2 || k.stripSlotRow == 3);
    }

    /** Page 2 punctuation grid slots that use factory corner hints (excludes row 3 Fn toggle). */
    private static boolean isPage2FixedPunctStripCornerSlot(@Nullable Key k) {
        if (k == null || k.stripSlotPage != 2) {
            return false;
        }
        if (k.stripSlotRow == 2) {
            return k.stripSlotCol >= 0 && k.stripSlotCol <= 6;
        }
        return k.stripSlotRow == 3 && k.stripSlotCol >= 0 && k.stripSlotCol <= 5;
    }

    /**
     * Page 0 row 2/3 F-keys: {@link Key#label}/{@link Key#code} are the latch-on (Fn) layer; digit overlay is
     * latch-off (base). Other pages: label/code are latch-off; overlay is latch-on. So we read {@code f-p…}
     * slots for page-0 F-row keys and {@code b-p…} elsewhere when applying overrides to the key itself.
     * <p>Page 2 row 2 columns 1–2 are an exception: the rebuilt row swaps paren base vs grave/tilde when local
     * Fn is latched, so we read {@code f-p…} for the primary shortcut when {@link #fixedTopLocalFnLocked} is
     * true (see {@link #isPage2Row2ParenGraveDualLayerStripSlot(Key)}).
     */
    private void applyStripSlotOverrides(@NonNull java.util.List<Key> keys) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        Rows23StripProfileManager mgr = new Rows23StripProfileManager(ctx, shortcutProfileManager);
        for (Key k : keys) {
            if (k == null || k.stripSlotPage < 0) {
                continue;
            }
            boolean latchOnLayer = isFixedTopRowsFnDigitStripKey(k);
            if (isPage2Rows23DualLayerStripSlot(k)) {
                latchOnLayer = fixedTopLocalFnLocked;
            }
            String slotKey = StripSlotMapStore.slotKey(
                    k.stripSlotPage, k.stripSlotRow, k.stripSlotCol, latchOnLayer);
            ShortcutProfileManager.Shortcut sc = mgr.resolveActiveSlotShortcut(slotKey);
            if (sc != null) {
                applyRows23ShortcutToFixedKey(k, sc);
            }
        }
    }

    private void applyRows23ShortcutToFixedKey(@NonNull Key key, @NonNull ShortcutProfileManager.Shortcut shortcut) {
        String label = compactStripCapLabel(shortcut);
        String symbol = compactShortcutSymbol(shortcut);
        int iconResId = resolveShortcutIconRes(shortcut.icon);
        int normalizedModifiers = normalizeShortcutModifiersForTargetOs(shortcut.modifiers);
        key.label = label;
        key.symbolLabel = symbol;
        key.code = shortcut.keyCode;
        key.codeStr = String.format("%02X", shortcut.keyCode);
        key.iconResId = iconResId;
        key.shortcutModifiers = normalizedModifiers;
        key.unicodeCodePoint = shortcut.unicodeCodePoint;
        if (iconResId == 0 && isEmojiIcon(shortcut.icon)) {
            key.customIconGlyph = shortcut.icon.trim();
        } else {
            key.customIconGlyph = "";
        }
        // Page 2: overrides may replace a slot whose physical factory scan differed; keep factory scan
        // aligned with the active shortcut so {@link #resolvedStripSlotFactoryScanCode} and overlay logic match.
        if (key.stripSlotPage == 2) {
            key.stripSlotFactoryCode = shortcut.keyCode;
            key.requiresShift = (normalizedModifiers & MOD_SHIFT) != 0;
        }
    }

    /**
     * Rows 2–3 profile override for the fixed strip's <em>overlay</em> (latch-off digit layer on page 0 F-row;
     * latch-on overlay on page 1). Uses the opposite persistence suffix from {@link #applyStripSlotOverrides}.
     */
    @Nullable
    private FnMapping rows23StripOverlayOverrideMapping(@NonNull Key key) {
        if (key.stripSlotPage < 0) {
            return null;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return null;
        }
        Rows23StripProfileManager mgr = new Rows23StripProfileManager(ctx, shortcutProfileManager);
        boolean latchOnLayer = isFixedTopRowsFnDigitStripKey(key);
        if (isPage2Rows23DualLayerStripSlot(key)) {
            latchOnLayer = fixedTopLocalFnLocked;
        }
        String overlayKey = StripSlotMapStore.slotKey(
                key.stripSlotPage, key.stripSlotRow, key.stripSlotCol, !latchOnLayer);
        ShortcutProfileManager.Shortcut s = mgr.resolveActiveSlotShortcut(overlayKey);
        if (s == null) {
            return null;
        }
        String label = compactStripCapLabel(s);
        int norm = normalizeShortcutModifiersForTargetOs(s.modifiers);
        int iconResId = resolveShortcutIconRes(s.icon);
        return new FnMapping(label, s.keyCode, norm, iconResId);
    }

    /** Strip cap text for a Rows 2–3 shortcut: prefer chord from key/label over a stale display name. */
    @NonNull
    private String compactStripCapLabel(@NonNull ShortcutProfileManager.Shortcut shortcut) {
        if (getContext() == null) {
            return "";
        }
        String os = getTargetOs();
        String s = "";
        if (shortcut.label != null && !shortcut.label.trim().isEmpty()) {
            String fromLabel = KeyParser.displayLabel(shortcut.label.trim(), os).trim();
            if (!KeyParser.isUnparsedKeyTokenLabel(fromLabel)) {
                s = fromLabel;
            }
        }
        if (s.isEmpty() && shortcut.keyCode >= 0) {
            int mods = normalizeShortcutModifiersForTargetOs(shortcut.modifiers);
            s = KeyParser.toLabelForTargetOs(shortcut.keyCode, mods, os).trim();
        }
        if (s.isEmpty()) {
            s = ShortcutUiStrings.shortcutDisplayName(getContext(), shortcut).trim();
        }
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private int resolveShortcutIconRes(String iconName) {
        if (iconName == null) {
            return 0;
        }
        String raw = iconName.trim();
        if (raw.isEmpty() || isEmojiIcon(raw)) {
            return 0;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return 0;
        }
        return ctx.getResources().getIdentifier(raw, "drawable", ctx.getPackageName());
    }

    /**
     * True when {@code raw} should be rendered through the {@link Key#customIconGlyph} centered-glyph
     * path instead of as a drawable resource name. Excludes pure printable-ASCII punctuation so plain
     * caps such as "(", ")", "*", "?" stay on the standard chord-text path (with strip cap typography),
     * even when a strip shortcut populates {@code shortcut.icon} with that punctuation.
     */
    private boolean isEmojiIcon(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.matches("^[A-Za-z0-9_]+$")) {
            return false;
        }
        boolean allPrintableAscii = true;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            if (cp < 0x21 || cp > 0x7E) {
                allPrintableAscii = false;
                break;
            }
            i += Character.charCount(cp);
        }
        if (allPrintableAscii) {
            // Grave / tilde: still use centered custom-glyph autosize (Fn layer on page-2 row-2)
            // so they are not drawn as small chord text beside a redundant Fn corner hint.
            if ("\u0060".equals(t) || "~".equals(t)) {
                return true;
            }
            return false;
        }
        return true;
    }

    private Key buildTopPanelModifierKey(int code) {
        String targetOs = getTargetOs();
        Context ctx = getContext();
        String label;
        int iconResId = 0;
        if ("macos".equals(targetOs)) {
            if (code == 0xE0) {
                label = ctx != null ? ctx.getString(R.string.modifier_control) : "Control";
                iconResId = R.drawable.keyboard_control_key_24px;
            } else if (code == 0xE2) {
                label = ctx != null ? ctx.getString(R.string.modifier_option) : "Option";
                iconResId = R.drawable.keyboard_option_key_24px;
            } else {
                label = ctx != null ? ctx.getString(R.string.modifier_command) : "Command";
                iconResId = R.drawable.keyboard_command_key_24px;
            }
        } else if ("linux".equals(targetOs)) {
            if (code == 0xE0) {
                label = ctx != null ? ctx.getString(R.string.modifier_control) : "Ctrl";
            } else if (code == 0xE2) {
                label = ctx != null ? ctx.getString(R.string.Alt) : "Alt";
            } else {
                label = ctx != null ? ctx.getString(R.string.Super) : "Super";
                iconResId = R.drawable.ic_os_linux;
            }
        } else {
            if (code == 0xE0) {
                label = ctx != null ? ctx.getString(R.string.modifier_control) : "Ctrl";
            } else if (code == 0xE2) {
                label = ctx != null ? ctx.getString(R.string.Alt) : "Alt";
            } else {
                label = ctx != null ? ctx.getString(R.string.Win) : "Win";
                iconResId = R.drawable.windows;
            }
        }
        // Make these keys action-label eligible so row-2-col-7 toggle can switch icon/text.
        return new Key(label, "", code, String.format("%02X", code), 1f, iconResId, 0f, false, false, 0, true);
    }

    private String compactShortcutName(ShortcutProfileManager.Shortcut shortcut) {
        if (shortcut == null || getContext() == null) {
            return "";
        }
        String label = ShortcutUiStrings.shortcutDisplayName(getContext(), shortcut).trim();
        if (label.isEmpty()) {
            return "";
        }
        return label.length() > 8 ? label.substring(0, 8) : label;
    }

    private String compactShortcutSymbol(ShortcutProfileManager.Shortcut shortcut) {
        if (shortcut == null) {
            return "";
        }
        String lbl = shortcut.label != null ? shortcut.label.trim() : "";
        String icn = shortcut.icon != null ? shortcut.icon.trim() : "";
        // Built-ins store the same string in label and icon for strip glyphs. Prefer that for
        // symbol/chord display so e.g. ⟨ / ⦅ stay visible even when HID resolves to "(" from 0x26+Shift.
        if (!lbl.isEmpty() && lbl.equals(icn)) {
            String fromLbl = KeyParser.displayLabel(lbl, getTargetOs()).trim();
            if (!fromLbl.isEmpty() && !KeyParser.isUnparsedKeyTokenLabel(fromLbl)) {
                return fromLbl.length() > 10 ? fromLbl.substring(0, 10) : fromLbl;
            }
        }
        if (shortcut.keyCode >= 0) {
            int mods = normalizeShortcutModifiersForTargetOs(shortcut.modifiers);
            String chord = KeyParser.toLabelForTargetOs(shortcut.keyCode, mods, getTargetOs());
            if (!chord.isEmpty()) {
                return chord.length() > 10 ? chord.substring(0, 10) : chord;
            }
        }
        String symbol = shortcut.label != null ? shortcut.label.trim() : "";
        if (symbol.isEmpty()) {
            return "";
        }
        symbol = KeyParser.displayLabel(symbol, getTargetOs());
        return symbol.length() > 10 ? symbol.substring(0, 10) : symbol;
    }

    private String getTargetOs() {
        Context ctx = getContext();
        if (ctx == null) return "macos";
        return ctx.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .getString("target_os", "macos");
    }

    /**
     * Top-strip Ctrl/Shift/Alt/Win cell under local Fn overlay that sends HID Caps Lock (0x39), e.g. CAPS on Cmd.
     */
    private boolean isMacCapsMomentaryFromTopStripModifier(Key key) {
        if (!"macos".equals(getTargetOs()) || key == null || !isTopModifierLockCandidate(key)) {
            return false;
        }
        FnMapping m = resolveFixedTopLocalFnMapping(key);
        return m != null && m.keyCode == 0x39;
    }

    /**
     * Idle top-strip keycap: row 1 (Shortcut Hub profile favorites, swipe strip) vs fixed rows 2–3.
     * Row-1 keys keep {@link Key#allowTopPanelPagingGesture}; fixed rows use {@code markFixedRowKey}.
     */
    private int resolveTopPanelIdleBackgroundRes(Key key) {
        if (key != null && key.isTopPanelKey && key.allowTopPanelPagingGesture) {
            return R.drawable.function_button_background_profile_strip;
        }
        return R.drawable.function_button_background;
    }

    private void applyTopPanelKeyCapBackground(View view, Key key, boolean keyLockedVisualState) {
        view.setClipToOutline(false);
        view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        int bg = keyLockedVisualState
                ? R.drawable.press_button_background
                : resolveTopPanelIdleBackgroundRes(key);
        view.setBackgroundResource(bg);
    }

    /**
     * BI/IME strip toggle: ring uses {@link ThemeManager#getColorPrimary} so the accent matches the
     * user's color family even when {@code ?attr/colorPrimary} does not resolve strongly on this view's
     * context (same rationale as {@link #applyTopImeToggleIconTint}).
     */
    private void applyTopPanelImeToggleKeyCapBackground(View view) {
        view.setClipToOutline(false);
        view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        Context ctx = view.getContext();
        if (ctx == null) {
            view.setBackgroundResource(R.drawable.top_strip_ime_toggle_background);
            return;
        }
        int cornerPx = dpToPx(9);
        int strokePx = dpToPx(2);
        int primary = ThemeManager.getColorPrimary(ctx);
        int primaryContainer = ThemeManager.getColorPrimaryContainer(ctx);
        int fillIdle = ContextCompat.getColor(ctx, R.color.key_bg_function);

        GradientDrawable pressedActivated = new GradientDrawable();
        pressedActivated.setShape(GradientDrawable.RECTANGLE);
        pressedActivated.setCornerRadius(cornerPx);
        pressedActivated.setColor(primary);

        GradientDrawable pressedKeymod = new GradientDrawable();
        pressedKeymod.setShape(GradientDrawable.RECTANGLE);
        pressedKeymod.setCornerRadius(cornerPx);
        pressedKeymod.setColor(primaryContainer);

        GradientDrawable activatedIdle = new GradientDrawable();
        activatedIdle.setShape(GradientDrawable.RECTANGLE);
        activatedIdle.setCornerRadius(cornerPx);
        activatedIdle.setColor(primaryContainer);
        activatedIdle.setStroke(strokePx, primary);

        GradientDrawable defaultIdle = new GradientDrawable();
        defaultIdle.setShape(GradientDrawable.RECTANGLE);
        defaultIdle.setCornerRadius(cornerPx);
        defaultIdle.setColor(fillIdle);
        defaultIdle.setStroke(strokePx, primary);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(
                new int[] {android.R.attr.state_pressed, android.R.attr.state_activated}, pressedActivated);
        sld.addState(new int[] {android.R.attr.state_pressed}, pressedKeymod);
        sld.addState(new int[] {android.R.attr.state_activated}, activatedIdle);
        sld.addState(StateSet.WILD_CARD, defaultIdle);
        view.setBackground(sld);
    }

    private void applyTopImeToggleLabelTypography(TextView tv) {
        if (tv == null) {
            return;
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TOP_FIXED_ROWS_IME_TOGGLE_TEXT_SP);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private void applyTopImeToggleIconTint(ImageButton ib) {
        if (ib == null) {
            return;
        }
        Context ctx = ib.getContext();
        if (ctx == null) {
            return;
        }
        // Use configured accent directly. MaterialColors harmonization against the light keycap fill
        // can wash out the tint in light mode (same issue as the text label).
        ib.setColorFilter(ThemeManager.getColorPrimary(ctx));
    }

    /** Name/chord strip modes can render PH1 as a {@link Button}; keep accent foreground in sync with icon mode. */
    private void applyTopImeToggleLabelTextColor(TextView tv) {
        if (tv == null) {
            return;
        }
        Context ctx = tv.getContext();
        if (ctx == null) {
            return;
        }
        tv.setTextColor(ThemeManager.getColorPrimary(ctx));
    }

    private void addShortcutPanelRows(
            LinearLayout parent,
            List<Key> panelKeys,
            int startColInclusive,
            int endColExclusive,
            int startRowInclusive,
            int endRowExclusive
    ) {
        addShortcutPanelRows(
                parent, panelKeys, startColInclusive, endColExclusive,
                startRowInclusive, endRowExclusive, -1);
    }

    /**
     * @param fixedTopBindPageIndex strip page index (0–3) for the keys in {@code panelKeys} when
     *        rendering the two fixed rows; {@code -1} when not a fixed-rows slice (ignored).
     */
    private void addShortcutPanelRows(
            LinearLayout parent,
            List<Key> panelKeys,
            int startColInclusive,
            int endColExclusive,
            int startRowInclusive,
            int endRowExclusive,
            int fixedTopBindPageIndex
    ) {
        int m = dpToPx(KEY_OUTER_MARGIN_DP);
        int clampedStart = Math.max(0, startColInclusive);
        int clampedEnd = Math.min(TOP_PANEL_COLUMNS, endColExclusive);
        int visibleColumns = Math.max(1, clampedEnd - clampedStart);
        int clampedStartRow = Math.max(0, startRowInclusive);
        int clampedEndRow = Math.min(TOP_PANEL_ROWS, endRowExclusive);
        boolean fixedRowsSlice = panelKeys != null
                && panelKeys.size() == TOP_PANEL_COLUMNS * 2
                && (clampedEndRow - clampedStartRow) == 2;
        int fixedTopPageForResolvers = fixedRowsSlice && fixedTopBindPageIndex >= 0
                ? fixedTopBindPageIndex
                : fixedTopRowsPageIndex;
        for (int rowIndex = clampedStartRow; rowIndex < clampedEndRow; rowIndex++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, TOP_PANEL_ROW_WEIGHT));
            rowLayout.setOrientation(HORIZONTAL);
            OnTouchListener rowTouchListener = fixedRowsSlice
                    ? createFixedTopRowsTouchListener(null)
                    : createTopPanelTouchListener(null);
            rowLayout.setOnTouchListener(rowTouchListener);

            for (int localCol = 0; localCol < visibleColumns; localCol++) {
                int col = clampedStart + localCol;
                int index = rowIndex * TOP_PANEL_COLUMNS + col;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
                p.setMargins(m, m, m, m);

                if (index >= panelKeys.size()) {
                    View spacer = new View(getContext());
                    spacer.setLayoutParams(p);
                    spacer.setOnTouchListener(rowTouchListener);
                    rowLayout.addView(spacer);
                    continue;
                }

                Key k = panelKeys.get(index);
                boolean modifierLocked = (k.code == 0xE0 && isCtrlLeftLocked)
                    || (k.code == 0xE1 && isShiftLeftLocked)
                    || (k.code == 0xE2 && isAltLeftLocked)
                    || (k.code == 0xE3 && isWinLeftLocked);
                // Fn-layer lock only tints the local Fn key — never other keys (modifier locks stay
                // independent when Fn toggles).
                boolean keyLockedVisualState =
                        isFixedTopLocalFnKey(k)
                                ? fixedTopLocalFnLocked
                                : (isTopModifierLockCandidate(k)
                                        ? isProModifierCapVisualOn(k)
                                        : modifierLocked);
                FnMapping fixedTopLocalFn = fixedRowsSlice
                        ? resolveFixedTopLocalFnMapping(k, fixedTopPageForResolvers)
                        : resolveFixedTopLocalFnMapping(k);
                int effectiveTopIconResId = resolveFixedTopLocalFnIconRes(k, fixedTopLocalFn);
                String effectiveTopLabel = fixedTopLocalFn != null ? fixedTopLocalFn.label : k.label;
                boolean stripEligible = isTopShortcutActionLabelEligible(k);
                boolean nameMode = stripNameMode(k);
                boolean iconMode = stripIconMode(k);
                boolean chordMode = stripChordMode(k);
                boolean renderAsActionLabel = nameMode;
                boolean renderAsCustomIconText = iconMode
                        && fixedTopLocalFn == null
                        && k.customIconGlyph != null
                        && !k.customIconGlyph.trim().isEmpty();
                String fnCornerHint = fixedRowsSlice
                        ? resolveFixedTopLocalFnCornerHint(
                                k, fixedTopLocalFn, renderAsActionLabel, fixedTopPageForResolvers)
                        : null;
                final boolean iconPrimaryCap;
                if (!stripEligible) {
                    iconPrimaryCap = effectiveTopIconResId != 0;
                } else if (nameMode) {
                    iconPrimaryCap = false;
                } else if (iconMode) {
                    iconPrimaryCap = effectiveTopIconResId != 0;
                } else {
                    iconPrimaryCap = false;
                }
                if (iconPrimaryCap) {
                    // Icon-primary caps: skip Fn corner hint (avoids clutter with centered icon + overlay text).
                    fnCornerHint = null;
                }
                if (iconPrimaryCap) {
                    ImageButton ib = new ImageButton(getContext());
                    applyFlatKeyStyle(ib);
                    ib.setLayoutParams(p);
                    if (isTopImeToggleKey(k)) {
                        applyTopPanelImeToggleKeyCapBackground(ib);
                        ib.setActivated(false);
                        ib.setSelected(false);
                    } else {
                        applyTopPanelKeyCapBackground(ib, k, keyLockedVisualState);
                        ib.setSelected(keyLockedVisualState);
                    }
                    ib.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                    int iconPad = fixedRowsSlice ? dpToPx(5) : 0;
                    ib.setPadding(iconPad, iconPad, iconPad, iconPad);
                    ib.setImageResource(effectiveTopIconResId);
                    boolean forwardDelIcon = effectiveTopIconResId == R.drawable.backspace_24
                            && (k.code == 0x4C
                            || (fixedTopLocalFn != null && fixedTopLocalFn.keyCode == 0x4C));
                    ib.setScaleX(forwardDelIcon ? -1f : 1f);
                    if (isTopImeToggleKey(k)) {
                        applyTopImeToggleIconTint(ib);
                    } else {
                        ib.setColorFilter(resolveThemeTextColor());
                    }
                    if (isTopModeSlotKey(k)) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            int slot = topModeSlotIndexFromKeyCode(k.code);
                            String mode = TopModeShortcutPrefs.getModeForSlot(ctx, slot);
                            ib.setContentDescription(ctx.getString(TopModeShortcutPrefs.labelResForMode(mode)));
                        }
                    } else if (isTopImeToggleKey(k)) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            ib.setContentDescription(ctx.getString(R.string.top_shortcut_km_pro_input_built_in_cd));
                        }
                    } else if (isTopShortcutToggleKey(k)) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            int cdRes;
                            switch (topShortcutDisplayMode) {
                                case DISPLAY_MODE_NAME:
                                    cdRes = R.string.top_shortcut_display_mode_cd_name;
                                    break;
                                case DISPLAY_MODE_ICON:
                                    cdRes = R.string.top_shortcut_display_mode_cd_icon;
                                    break;
                                case DISPLAY_MODE_CHORD:
                                default:
                                    cdRes = R.string.top_shortcut_display_mode_cd_chord;
                                    break;
                            }
                            ib.setContentDescription(ctx.getString(cdRes));
                        }
                    } else if (k.code == KEY_TOP_STRIP_CREATE_SHORTCUT) {
                        Context ctx = getContext();
                        if (ctx != null) {
                            ib.setContentDescription(ctx.getString(R.string.top_strip_create_shortcut_cd));
                        }
                    }
                    ib.setTag(k);
                    ib.setOnTouchListener(fixedRowsSlice
                            ? createFixedTopRowsTouchListener(k)
                            : createTopPanelTouchListener(k));
                    rowLayout.addView(ib);
                } else if (renderAsCustomIconText) {
                    Button iconTextButton = new Button(getContext());
                    applyFlatKeyStyle(iconTextButton);
                    iconTextButton.setLayoutParams(p);
                    applyTopPanelKeyCapBackground(iconTextButton, k, keyLockedVisualState);
                    iconTextButton.setSelected(keyLockedVisualState);
                    iconTextButton.setGravity(Gravity.CENTER);
                    if (fixedRowsSlice) {
                        // Tight padding so autosize can use most of the cell; dingbat / math glyphs
                        // in custom strip shortcuts then approach drawable-icon visual weight.
                        int glyphPad = dpToPx(1);
                        iconTextButton.setPadding(glyphPad, glyphPad, glyphPad, glyphPad);
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                                iconTextButton,
                                TOP_FIXED_ROWS_CUSTOM_GLYPH_MIN_SP,
                                TOP_FIXED_ROWS_CUSTOM_GLYPH_MAX_SP,
                                TOP_FIXED_ROWS_CUSTOM_GLYPH_STEP_SP,
                                TypedValue.COMPLEX_UNIT_SP);
                        // Regular weight: Unicode dingbats / math glyphs render cleanest in normal weight;
                        // bold can compress or distort outline shapes (e.g. ✓ ⊿ ❄).
                        iconTextButton.setTypeface(Typeface.DEFAULT);
                    } else {
                        iconTextButton.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                TOP_SHORTCUT_PANEL_CUSTOM_GLYPH_SP);
                        iconTextButton.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                    iconTextButton.setText(k.customIconGlyph);
                    iconTextButton.setTextColor(resolveThemeTextColor());
                    iconTextButton.setAllCaps(false);
                    iconTextButton.setTag(k);
                    iconTextButton.setOnTouchListener(fixedRowsSlice
                            ? createFixedTopRowsTouchListener(k)
                            : createTopPanelTouchListener(k));
                    rowLayout.addView(iconTextButton);
                } else {
                    Button b = new Button(getContext());
                    applyFlatKeyStyle(b);
                    b.setLayoutParams(fixedRowsSlice && !TextUtils.isEmpty(fnCornerHint)
                            ? new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT)
                            : p);
                    if (isTopImeToggleKey(k)) {
                        applyTopPanelImeToggleKeyCapBackground(b);
                        b.setActivated(false);
                        b.setSelected(false);
                    } else {
                        applyTopPanelKeyCapBackground(b, k, keyLockedVisualState);
                        b.setSelected(keyLockedVisualState);
                    }
                    b.setGravity(Gravity.CENTER);
                    int textPad = fixedRowsSlice ? dpToPx(2) : dpToPx(1);
                    b.setPadding(textPad, textPad, textPad, textPad);
                    b.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                            fixedRowsSlice ? TOP_FIXED_ROWS_TEXT_SP : TOP_SHORTCUT_PANEL_TEXT_SP);
                    final String topButtonText;
                    if (fixedTopLocalFn != null) {
                        topButtonText = effectiveTopLabel;
                    } else if (k.allowTopPanelPagingGesture && k.topStripFavoriteSlotIndex >= 0) {
                        // Row-1 favorites: name mode = title; icon/chord (or icon fallback) = chord line.
                        if (nameMode) {
                            String name = k.label != null ? k.label.trim() : "";
                            topButtonText = !TextUtils.isEmpty(name)
                                    ? name
                                    : formatTopShortcutActionLabel(k);
                        } else {
                            topButtonText = formatTopShortcutActionLabel(k);
                        }
                    } else if (nameMode || chordMode || (iconMode && effectiveTopIconResId == 0)) {
                        topButtonText = formatTopShortcutActionLabel(k);
                    } else if (k.symbolLabel != null && !k.symbolLabel.isEmpty()) {
                        if (k.label != null && !k.label.trim().isEmpty()) {
                            topButtonText = k.symbolLabel + "\n" + k.label;
                        } else {
                            topButtonText = k.symbolLabel;
                        }
                    } else {
                        topButtonText = k.label != null && !k.label.trim().isEmpty()
                                ? k.label
                                : (k.symbolLabel != null ? k.symbolLabel : "");
                    }
                    b.setText(topButtonText);
                    if (renderAsActionLabel) {
                        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                fixedRowsSlice ? TOP_FIXED_ROWS_ACTION_LABEL_SP
                                        : TOP_SHORTCUT_PANEL_ACTION_LABEL_SP);
                        b.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                    b.setAllCaps(false);
                    if ("ESC".equals(k.label)
                            || "CTRL".equals(k.label)
                            || "ALT".equals(k.label)
                            || "HOME".equals(k.label)
                            || "END".equals(k.label)
                            || "PGUP".equals(k.label)
                            || "PGDN".equals(k.label)
                            || isTopPanelModifierKey(k)) {
                        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
                    } else if (fixedRowsSlice && !nameMode) {
                        b.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                    if (isTopImeToggleKey(k)) {
                        applyTopImeToggleLabelTypography(b);
                        applyTopImeToggleLabelTextColor(b);
                    } else {
                        b.setTextColor(resolveThemeTextColor());
                    }
                    b.setTag(k);
                    b.setOnTouchListener(fixedRowsSlice
                            ? createFixedTopRowsTouchListener(k)
                            : createTopPanelTouchListener(k));
                    if (fixedRowsSlice && !TextUtils.isEmpty(fnCornerHint)) {
                        addFixedRowCellWithOptionalFnHint(rowLayout, p, b, fnCornerHint);
                    } else {
                        rowLayout.addView(b);
                    }
                }
            }
            parent.addView(rowLayout);
        }
    }

    private boolean isSplitTopPanelMode() {
        return splitTopLeftViewport != null && splitTopRightViewport != null
                && splitActiveTopPanelContainerLeft != null && splitActiveTopPanelContainerRight != null;
    }

    private void clearSplitTopPanelReferences() {
        splitTopLeftViewport = null;
        splitTopRightViewport = null;
        splitTopCenterSpacer = null;
        splitFixedTopRowsViewportLeft = null;
        splitFixedTopRowsViewportRight = null;
        splitPreviousFixedTopRowsContainerLeft = null;
        splitActiveFixedTopRowsContainerLeft = null;
        splitNextFixedTopRowsContainerLeft = null;
        splitPreviousFixedTopRowsContainerRight = null;
        splitActiveFixedTopRowsContainerRight = null;
        splitNextFixedTopRowsContainerRight = null;
        splitPreviousTopPanelContainerLeft = null;
        splitActiveTopPanelContainerLeft = null;
        splitNextTopPanelContainerLeft = null;
        splitPreviousTopPanelContainerRight = null;
        splitActiveTopPanelContainerRight = null;
        splitNextTopPanelContainerRight = null;
        fixedTopRowsViewport = null;
        previousFixedTopRowsContainer = null;
        activeFixedTopRowsContainer = null;
        nextFixedTopRowsContainer = null;
    }

    private boolean hasAnyTopPanelMode() {
        return activeTopPanelContainer != null || isSplitTopPanelMode();
    }

    private boolean hasAnyFixedRowsPagerMode() {
        return activeFixedTopRowsContainer != null || splitActiveFixedTopRowsContainerLeft != null;
    }

    private boolean isModifierLockedStateForKey(Key key) {
        if (key == null) return false;
        switch (key.code) {
            case 0xE0: return isCtrlLeftLocked;
            case 0xE1: return isShiftLeftLocked;
            case 0xE2: return isAltLeftLocked;
            case 0xE3: return isWinLeftLocked;
            case 0xE4: return isCtrlRightLocked;
            case 0xE6: return isAltRightLocked;
            case 0xE7: return isWinRightLocked;
            default: return false;
        }
    }

    private void setModifierLockedStateForKey(Key key, boolean locked) {
        if (key == null) return;
        switch (key.code) {
            case 0xE0:
                isCtrlLeftLocked = locked;
                break;
            case 0xE1:
                isShiftLeftLocked = locked;
                break;
            case 0xE2:
                isAltLeftLocked = locked;
                break;
            case 0xE3:
                isWinLeftLocked = locked;
                break;
            case 0xE4:
                isCtrlRightLocked = locked;
                break;
            case 0xE6:
                isAltRightLocked = locked;
                break;
            case 0xE7:
                isWinRightLocked = locked;
                break;
            default:
                return;
        }
        syncModifierStates();
        refreshVisibleTopPanelButtonStates();
        post(this::refreshBuiltInModifierKeyCapsFromTree);
        if (splitPartner != null) {
            splitPartner.refreshVisibleTopPanelButtonStates();
        }
    }

    private void sendMomentaryModifierClick(Key key) {
        if (key == null) {
            return;
        }
        FnMapping fixedTopLocalFn = resolveFixedTopLocalFnMapping(key);
        int effectiveKeyCode = fixedTopLocalFn != null ? fixedTopLocalFn.keyCode : key.code;
        int fnModifierMask = fixedTopLocalFn != null ? fixedTopLocalFn.modifierMask : 0;
        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += isShiftLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;
        combinedValue += isCtrlRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("CtrlR")) : 0;
        combinedValue += isAltRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("AltR")) : 0;
        combinedValue += isWinRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("WinR")) : 0;
        if ((fnModifierMask & MOD_CTRL) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((fnModifierMask & MOD_SHIFT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((fnModifierMask & MOD_ALT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((fnModifierMask & MOD_WIN) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }
        sendKeyData(combinedValue, effectiveKeyCode);
    }

    private boolean isProMomentaryChordMode() {
        Context ctx = getContext();
        return ctx != null && KmBasicKeyboardPrefs.isMomentaryChordMode(ctx);
    }

    /**
     * Top strip / fixed rows: Ctrl, Shift, Alt, Win (and macOS Fn→Caps on those cells). Sticky +
     * hold-lock popup matches KM Basic; chord mode matches KM Basic long-press + sustain.
     *
     * @param fixedRows {@code true} for fixed rows 2–3 pager; {@code false} for scrolling row-1 strip.
     */
    private OnTouchListener createProTopStripModifierTouchListener(Key key, boolean fixedRows) {
        final boolean[] longPressConsumed = new boolean[1];
        final Runnable[] pendingMacCapsLongPress = new Runnable[1];
        final boolean[] macCapsLongFired = new boolean[1];
        final boolean[] macCapsDownActive = new boolean[1];
        final long[] macCapsDownTime = new long[1];

        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (pendingMacCapsLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingMacCapsLongPress[0]);
                        pendingMacCapsLongPress[0] = null;
                    }
                    longPressHandler.removeCallbacks(proChordLongPressRunnable);
                    longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                    macCapsDownActive[0] = false;
                    longPressConsumed[0] = false;
                    if (fixedRows) {
                        cancelFixedTopRowsAnimations();
                    } else {
                        cancelTopPanelAnimations();
                    }
                    if (isMacCapsMomentaryFromTopStripModifier(key)) {
                        macCapsLongFired[0] = false;
                        macCapsDownActive[0] = true;
                        macCapsDownTime[0] = SystemClock.uptimeMillis();
                        pendingMacCapsLongPress[0] =
                                () -> {
                                    macCapsLongFired[0] = true;
                                    performKeyHapticFeedback(v);
                                    pendingMacCapsLongPress[0] = null;
                                };
                        longPressHandler.postDelayed(
                                pendingMacCapsLongPress[0], MAC_CAPS_LONG_PRESS_MS);
                        sendMomentaryModifierClick(key);
                    } else if (isProMomentaryChordMode()) {
                        clearPartnerChordStateFromPrimary();
                        dismissProHoldLockPopup();
                        performKeyHapticFeedback(v);
                        proChordActiveExtKey = key.code;
                        proChordLongPressAnchorView = v;
                        proChordLongPressPendingMask = bootModifierMaskForBuiltInExtendedKey(key.code);
                        holdLockPopupAnchorView = v;
                        holdLockFingerDown = true;
                        holdLockPendingModMask = proChordLongPressPendingMask;
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        longPressHandler.postDelayed(
                                proChordLongPressRunnable,
                                ALT_LONG_PRESS_TIMEOUT_MS);
                        longPressHandler.postDelayed(
                                proHoldLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    } else {
                        dismissProHoldLockPopup();
                        performKeyHapticFeedback(v);
                        holdLockPopupAnchorView = v;
                        holdLockFingerDown = true;
                        holdLockPendingModMask = bootModifierMaskForBuiltInExtendedKey(key.code);
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        longPressHandler.postDelayed(
                                proHoldLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    }
                    v.setPressed(true);
                    if (key != null) {
                        stripMomentarySyncKeyCode = key.code;
                        refreshBuiltInModifierKeyCapsFromTree();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isMacCapsMomentaryFromTopStripModifier(key)) {
                        return true;
                    } else if (isProMomentaryChordMode()) {
                        if (activeHoldLockPopup != null) {
                            v.setPressed(true);
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                            activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                            return true;
                        }
                        boolean insideChord = BasicKeyFeedback.isPointerInsideView(v, event);
                        if (insideChord) {
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                        }
                        v.setPressed(insideChord);
                        if (!insideChord) {
                            longPressHandler.removeCallbacks(proChordLongPressRunnable);
                            longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                            dismissProHoldLockPopup();
                        }
                    } else {
                        boolean insideSticky = BasicKeyFeedback.isPointerInsideView(v, event);
                        if (activeHoldLockPopup != null) {
                            v.setPressed(true);
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                            activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                            return true;
                        }
                        if (insideSticky) {
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                        }
                        v.setPressed(insideSticky);
                        if (!insideSticky) {
                            longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                            dismissProHoldLockPopup();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (stripMomentarySyncKeyCode != -1) {
                        stripMomentarySyncKeyCode = -1;
                        post(this::refreshBuiltInModifierKeyCapsFromTree);
                    }
                    longPressHandler.removeCallbacks(proChordLongPressRunnable);
                    longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                    if (pendingMacCapsLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingMacCapsLongPress[0]);
                        pendingMacCapsLongPress[0] = null;
                    }
                    if (macCapsDownActive[0] && isMacCapsMomentaryFromTopStripModifier(key)) {
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                            sendReleaseData();
                            macCapsDownActive[0] = false;
                            dismissProHoldLockPopup();
                            return true;
                        }
                        if (longPressConsumed[0]) {
                            sendReleaseData();
                            macCapsDownActive[0] = false;
                            dismissProHoldLockPopup();
                            return true;
                        }
                        performKeyHapticFeedback(v);
                        v.performClick();
                        long elapsed = SystemClock.uptimeMillis() - macCapsDownTime[0];
                        if (!macCapsLongFired[0]) {
                            long delay =
                                    elapsed < MAC_CAPS_MIN_TAP_HOLD_MS
                                            ? MAC_CAPS_MIN_TAP_HOLD_MS - elapsed
                                            : 0;
                            scheduleKeyboardTapRelease(delay);
                        } else {
                            sendReleaseData();
                        }
                        macCapsDownActive[0] = false;
                        dismissProHoldLockPopup();
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !longPressConsumed[0]) {
                        performKeyHapticFeedback(v);
                        v.performClick();
                        holdLockFingerDown = false;
                        MainActivity ma = unwrapMainActivityForHid();
                        boolean committedLock = false;
                        if (activeHoldLockPopup != null) {
                            activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                            committedLock = activeHoldLockPopup.commitIfLockSelected();
                        }
                        dismissProHoldLockPopup();
                        if (committedLock && holdLockController != null && ma != null
                                && transport instanceof HidKeyboardTransport) {
                            setModifierLockedStateForKey(key, false);
                            holdLockController.lockModifier(
                                    holdLockPendingModMask,
                                    port,
                                    ma.getBluetoothService(),
                                    ma.isBluetoothServiceBound());
                            clearProChordUiPreserveHostForLock();
                            refreshVisibleTopPanelButtonStates();
                            post(this::refreshBuiltInModifierKeyCapsFromTree);
                            holdLockPopupAnchorView = null;
                            scheduleKeyboardTapRelease();
                            return true;
                        }
                        if (isProMomentaryChordMode()) {
                            if (proChordSustainBootByView.containsKey(v)) {
                                releaseProChordSustainFingerForView(v);
                            } else if (BasicKeyFeedback.isPointerInsideView(v, event)) {
                                int boot = bootModifierMaskForBuiltInExtendedKey(key.code);
                                if (holdLockController != null
                                        && ma != null
                                        && transport instanceof HidKeyboardTransport
                                        && holdLockController.isModifierLocked(boot)) {
                                    holdLockController.unlockModifier(
                                            boot,
                                            port,
                                            ma.getBluetoothService(),
                                            ma.isBluetoothServiceBound());
                                } else {
                                    tapProModifierMomentary(key);
                                }
                            }
                            holdLockPopupAnchorView = null;
                            refreshVisibleTopPanelButtonStates();
                            post(this::refreshBuiltInModifierKeyCapsFromTree);
                        } else {
                            if (BasicKeyFeedback.isPointerInsideView(v, event) && ma != null) {
                                int boot = bootModifierMaskForBuiltInExtendedKey(key.code);
                                if (holdLockController != null
                                        && transport instanceof HidKeyboardTransport
                                        && holdLockController.isModifierLocked(boot)) {
                                    holdLockController.unlockModifier(
                                            boot,
                                            port,
                                            ma.getBluetoothService(),
                                            ma.isBluetoothServiceBound());
                                } else {
                                    setModifierLockedStateForKey(key, !isModifierLockedStateForKey(key));
                                }
                                refreshVisibleTopPanelButtonStates();
                                post(this::refreshBuiltInModifierKeyCapsFromTree);
                            }
                            holdLockPopupAnchorView = null;
                        }
                        scheduleKeyboardTapRelease();
                    } else {
                        dismissProHoldLockPopup();
                        if (isProMomentaryChordMode() && proChordSustainBootByView.containsKey(v)) {
                            releaseProChordSustainFingerForView(v);
                        }
                        proChordLongPressAnchorView = null;
                        holdLockPopupAnchorView = null;
                        holdLockFingerDown = false;
                    }
                    return true;
                default:
                    return false;
            }
        };
    }

    @Nullable
    private MainActivity unwrapMainActivityForHid() {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        return act instanceof MainActivity ? (MainActivity) act : null;
    }

    private OnTouchListener createProMainKeyboardModifierTouchListener(Key key) {
        final int ext = key.code;
        final int boot = bootModifierMaskForBuiltInExtendedKey(ext);
        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    clearPartnerChordStateFromPrimary();
                    longPressHandler.removeCallbacks(proChordLongPressRunnable);
                    longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                    dismissProHoldLockPopup();
                    v.setPressed(true);
                    performKeyHapticFeedback(v);
                    if (isProMomentaryChordMode()) {
                        proChordActiveExtKey = ext;
                        proChordLongPressAnchorView = v;
                        proChordLongPressPendingMask = boot;
                        holdLockPopupAnchorView = v;
                        holdLockFingerDown = true;
                        holdLockPendingModMask = boot;
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        longPressHandler.postDelayed(
                                proChordLongPressRunnable, ALT_LONG_PRESS_TIMEOUT_MS);
                        longPressHandler.postDelayed(
                                proHoldLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    } else {
                        holdLockPopupAnchorView = v;
                        holdLockFingerDown = true;
                        holdLockPendingModMask = boot;
                        holdLockGestureRawX = event.getRawX();
                        holdLockGestureRawY = event.getRawY();
                        longPressHandler.postDelayed(
                                proHoldLockPopupRunnable, KmBasicHoldLockTiming.HOLD_LOCK_POPUP_MS);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isProMomentaryChordMode()) {
                        if (activeHoldLockPopup != null) {
                            v.setPressed(true);
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                            activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                            return true;
                        }
                        boolean inside = BasicKeyFeedback.isPointerInsideView(v, event);
                        if (inside) {
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                        }
                        v.setPressed(inside);
                        if (!inside) {
                            longPressHandler.removeCallbacks(proChordLongPressRunnable);
                            longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                            dismissProHoldLockPopup();
                        }
                    } else {
                        boolean insideSticky = BasicKeyFeedback.isPointerInsideView(v, event);
                        if (activeHoldLockPopup != null) {
                            v.setPressed(true);
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                            activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                            return true;
                        }
                        if (insideSticky) {
                            holdLockGestureRawX = event.getRawX();
                            holdLockGestureRawY = event.getRawY();
                        }
                        v.setPressed(insideSticky);
                        if (!insideSticky) {
                            longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                            dismissProHoldLockPopup();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    longPressHandler.removeCallbacks(proChordLongPressRunnable);
                    longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                    holdLockFingerDown = false;
                    MainActivity ma = unwrapMainActivityForHid();
                    if (activeHoldLockPopup != null) {
                        activeHoldLockPopup.updatePointer(event.getRawX(), event.getRawY());
                        boolean committedLock = activeHoldLockPopup.commitIfLockSelected();
                        dismissProHoldLockPopup();
                        if (committedLock && holdLockController != null && ma != null
                                && transport instanceof HidKeyboardTransport) {
                            setModifierLockedStateForKey(key, false);
                            holdLockController.lockModifier(
                                    holdLockPendingModMask,
                                    port,
                                    ma.getBluetoothService(),
                                    ma.isBluetoothServiceBound());
                            clearProChordUiPreserveHostForLock();
                            refreshVisibleTopPanelButtonStates();
                            post(this::refreshBuiltInModifierKeyCapsFromTree);
                            holdLockPopupAnchorView = null;
                            scheduleKeyboardTapRelease();
                            return true;
                        }
                    }
                    dismissProHoldLockPopup();
                    if (isProMomentaryChordMode()) {
                        if (proChordSustainBootByView.containsKey(v)) {
                            releaseProChordSustainFingerForView(v);
                        } else if (BasicKeyFeedback.isPointerInsideView(v, event)) {
                            if (holdLockController != null
                                    && ma != null
                                    && transport instanceof HidKeyboardTransport
                                    && holdLockController.isModifierLocked(boot)) {
                                holdLockController.unlockModifier(
                                        boot,
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound());
                            } else {
                                tapProModifierMomentary(key);
                            }
                        }
                    } else {
                        if (BasicKeyFeedback.isPointerInsideView(v, event) && ma != null) {
                            if (holdLockController != null
                                    && transport instanceof HidKeyboardTransport
                                    && holdLockController.isModifierLocked(boot)) {
                                holdLockController.unlockModifier(
                                        boot,
                                        port,
                                        ma.getBluetoothService(),
                                        ma.isBluetoothServiceBound());
                            } else {
                                setModifierLockedStateForKey(key, !isModifierLockedStateForKey(key));
                            }
                        }
                    }
                    holdLockPopupAnchorView = null;
                    proChordLongPressAnchorView = null;
                    performKeyHapticFeedback(v);
                    v.performClick();
                    refreshVisibleTopPanelButtonStates();
                    post(this::refreshBuiltInModifierKeyCapsFromTree);
                    scheduleKeyboardTapRelease();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    longPressHandler.removeCallbacks(proChordLongPressRunnable);
                    longPressHandler.removeCallbacks(proHoldLockPopupRunnable);
                    dismissProHoldLockPopup();
                    if (isProMomentaryChordMode() && proChordSustainBootByView.containsKey(v)) {
                        releaseProChordSustainFingerForView(v);
                    }
                    proChordLongPressAnchorView = null;
                    holdLockPopupAnchorView = null;
                    holdLockFingerDown = false;
                    refreshVisibleTopPanelButtonStates();
                    post(this::refreshBuiltInModifierKeyCapsFromTree);
                    return true;
                default:
                    return false;
            }
        };
    }

    private void maybeShowModifierLockHint(Key key) {
        if (key == null) {
            return;
        }
        if (!isTopModifierLockCandidate(key) && !isProBuiltInModifierTouchKey(key)) {
            return;
        }
        if (isTopModifierLockCandidate(key)
                && fixedTopRowsPageIndex != FIXED_TOP_ROWS_DEFAULT_PAGE_INDEX) {
            rapidTapModifierCode = -1;
            rapidTapModifierCount = 0;
            rapidTapModifierLastTapMs = 0L;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (rapidTapModifierCode == key.code && (now - rapidTapModifierLastTapMs) <= MODIFIER_RAPID_TAP_WINDOW_MS) {
            rapidTapModifierCount++;
        } else {
            rapidTapModifierCode = key.code;
            rapidTapModifierCount = 1;
        }
        rapidTapModifierLastTapMs = now;
        if (rapidTapModifierCount >= MODIFIER_RAPID_TAP_HINT_THRESHOLD
                && (now - lastModifierLockHintToastMs) >= MODIFIER_LOCK_HINT_COOLDOWN_MS) {
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, R.string.top_strip_modifier_long_press_lock_hint, Toast.LENGTH_LONG).show();
            }
            lastModifierLockHintToastMs = now;
            rapidTapModifierCode = -1;
            rapidTapModifierCount = 0;
            rapidTapModifierLastTapMs = 0L;
        }
    }

    private OnTouchListener createTopPanelTouchListener(Key key) {
        if (key != null && isTopModifierLockCandidate(key)) {
            return createProTopStripModifierTouchListener(key, false);
        }
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final boolean[] isDragging = new boolean[1];
        final boolean[] longPressConsumed = new boolean[1];
        final Runnable[] pendingModeLongPress = new Runnable[1];
        final Runnable[] pendingMyFavoritesLongPress = new Runnable[1];
        final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final int swipeThreshold = dpToPx(56);
        final int modeSlotIndex = key != null ? topModeSlotIndexFromKeyCode(key.code) : 0;
        final int myFavoritesSlotIndex = key != null ? key.topStripFavoriteSlotIndex : -1;
        final boolean canSwipePanel = key == null || key.allowTopPanelPagingGesture;

        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (pendingModeLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingModeLongPress[0]);
                        pendingModeLongPress[0] = null;
                    }
                    if (pendingMyFavoritesLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingMyFavoritesLongPress[0]);
                        pendingMyFavoritesLongPress[0] = null;
                    }
                    longPressConsumed[0] = false;
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
                    cancelTopPanelAnimations();
                    if (modeSlotIndex > 0) {
                        pendingModeLongPress[0] = () -> {
                            longPressConsumed[0] = true;
                            showTopModeSlotPicker(modeSlotIndex);
                            pendingModeLongPress[0] = null;
                        };
                        longPressHandler.postDelayed(pendingModeLongPress[0], ALT_LONG_PRESS_TIMEOUT_MS);
                    } else if (myFavoritesSlotIndex >= 0) {
                        pendingMyFavoritesLongPress[0] = () -> {
                            longPressConsumed[0] = true;
                            showTopStripFavoriteReorderSheet(myFavoritesSlotIndex);
                            pendingMyFavoritesLongPress[0] = null;
                        };
                        longPressHandler.postDelayed(pendingMyFavoritesLongPress[0], ALT_LONG_PRESS_TIMEOUT_MS);
                    }
                    if (key != null) {
                        v.setPressed(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    // Do not cancel mode long-press on small movement (finger jitter); only cancel
                    // when the user is clearly horizontal-swipe paging the strip.
                    if (canSwipePanel && !isDragging[0] && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        isDragging[0] = true;
                        if (key != null) {
                            v.setPressed(false);
                        }
                        if (pendingModeLongPress[0] != null) {
                            longPressHandler.removeCallbacks(pendingModeLongPress[0]);
                            pendingModeLongPress[0] = null;
                        }
                        if (pendingMyFavoritesLongPress[0] != null) {
                            longPressHandler.removeCallbacks(pendingMyFavoritesLongPress[0]);
                            pendingMyFavoritesLongPress[0] = null;
                        }
                    }
                    if (isDragging[0] && hasAnyTopPanelMode()) {
                        updateTopPanelDrag(applyTopPanelEdgeResistance(dx));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (key != null) {
                        v.setPressed(false);
                    }
                    if (pendingModeLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingModeLongPress[0]);
                        pendingModeLongPress[0] = null;
                    }
                    if (pendingMyFavoritesLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingMyFavoritesLongPress[0]);
                        pendingMyFavoritesLongPress[0] = null;
                    }
                    float totalDx = event.getRawX() - startX[0];
                    if (isDragging[0]) {
                        finishTopPanelSwipe(totalDx, swipeThreshold);
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && key != null && !longPressConsumed[0]) {
                        performKeyHapticFeedback(v);
                        v.performClick();
                        handleKeyPress(key);
                        scheduleKeyboardTapRelease();
                    }
                    return true;
                default:
                    return false;
            }
        };
    }

    private OnTouchListener createFixedTopRowsTouchListener(Key key) {
        // Page 1 + local Fn: Ctrl/Alt/Win/Shift cells show SCR LK / PRT SC / etc. and must use the
        // normal HID path (handleKeyPress → sendHidKeyDataForKey). Keep the modifier listener only
        // when there is no active Fn overlay, or for macOS Cmd→Caps momentary behavior.
        if (key != null && isTopModifierLockCandidate(key)
                && (resolveFixedTopLocalFnMapping(key) == null
                        || isMacCapsMomentaryFromTopStripModifier(key))) {
            return createProTopStripModifierTouchListener(key, true);
        }
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final boolean[] isDragging = new boolean[1];
        final boolean[] longPressConsumed = new boolean[1];
        final Runnable[] pendingFnStripEditLongPress = new Runnable[1];
        final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final int swipeThreshold = dpToPx(56);
        final boolean canSwipePanel = true;
        final boolean isLocalFnStripKey = isFixedTopLocalFnKey(key);

        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (pendingFnStripEditLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingFnStripEditLongPress[0]);
                        pendingFnStripEditLongPress[0] = null;
                    }
                    longPressConsumed[0] = false;
                    holdRepeatSuppressUpTap = false;
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
                    cancelFixedTopRowsAnimations();
                    if (isLocalFnStripKey) {
                        pendingFnStripEditLongPress[0] = () -> {
                            longPressConsumed[0] = true;
                            performKeyHapticFeedback(v);
                            showStripLayoutEditionEntry();
                            pendingFnStripEditLongPress[0] = null;
                        };
                        longPressHandler.postDelayed(pendingFnStripEditLongPress[0], STRIP_EDIT_VIA_FN_MS);
                    }
                    if (key != null && fixedStripCellSupportsHoldRepeat(key)) {
                        startHoldKeyRepeatFromDown(key);
                    }
                    if (key != null) {
                        v.setPressed(true);
                        if (isStripMomentarySyncKey(key.code)) {
                            stripMomentarySyncKeyCode = key.code;
                            refreshBuiltInModifierKeyCapsFromTree();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    if (canSwipePanel && !isDragging[0] && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        isDragging[0] = true;
                        if (holdKeyRepeatActive && holdKeyRepeatKey == key) {
                            stopRepeatingDelete();
                        }
                        if (key != null) {
                            v.setPressed(false);
                        }
                        if (stripMomentarySyncKeyCode != -1) {
                            stripMomentarySyncKeyCode = -1;
                            post(this::refreshBuiltInModifierKeyCapsFromTree);
                        }
                        if (pendingFnStripEditLongPress[0] != null) {
                            longPressHandler.removeCallbacks(pendingFnStripEditLongPress[0]);
                            pendingFnStripEditLongPress[0] = null;
                        }
                    }
                    if (isDragging[0] && hasAnyFixedRowsPagerMode()) {
                        updateFixedTopRowsDrag(applyFixedTopRowsEdgeResistance(dx));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (key != null) {
                        v.setPressed(false);
                    }
                    if (stripMomentarySyncKeyCode != -1) {
                        stripMomentarySyncKeyCode = -1;
                        post(this::refreshBuiltInModifierKeyCapsFromTree);
                    }
                    boolean stripSuppressTapUp = holdRepeatSuppressUpTap;
                    if (key != null && fixedStripCellSupportsHoldRepeat(key)) {
                        stopRepeatingDelete();
                    }
                    if (pendingFnStripEditLongPress[0] != null) {
                        longPressHandler.removeCallbacks(pendingFnStripEditLongPress[0]);
                        pendingFnStripEditLongPress[0] = null;
                    }
                    float totalDx = event.getRawX() - startX[0];
                    if (isDragging[0]) {
                        finishFixedTopRowsSwipe(totalDx, swipeThreshold);
                        holdRepeatSuppressUpTap = false;
                        return true;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && key != null && !longPressConsumed[0]) {
                        performKeyHapticFeedback(v);
                        v.performClick();
                        if (!stripSuppressTapUp) {
                            handleKeyPress(key);
                        }
                        scheduleKeyboardTapRelease();
                        holdRepeatSuppressUpTap = false;
                    } else {
                        holdRepeatSuppressUpTap = false;
                    }
                    return true;
                default:
                    return false;
            }
        };
    }

    private float applyTopPanelEdgeResistance(float translationX) {
        boolean draggingPastFirst = topPanelPageIndex == 0 && translationX > 0;
        boolean draggingPastLast = topPanelPageIndex == topShortcutPanels.size() - 1 && translationX < 0;
        if (draggingPastFirst || draggingPastLast) {
            return translationX * 0.35f;
        }
        return translationX;
    }

    private void finishTopPanelSwipe(float translationX, int swipeThreshold) {
        if (!hasAnyTopPanelMode()) {
            return;
        }

        boolean moveToNext = translationX < -swipeThreshold && topPanelPageIndex < topShortcutPanels.size() - 1;
        boolean moveToPrevious = translationX > swipeThreshold && topPanelPageIndex > 0;
        if (!moveToNext && !moveToPrevious) {
            animateTopPanelToRest();
            return;
        }

        int targetPage = moveToNext ? topPanelPageIndex + 1 : topPanelPageIndex - 1;
        float width = getTopPanelWidth();
        if (isSplitTopPanelMode()) {
            if (moveToNext && splitNextTopPanelContainerLeft != null && splitNextTopPanelContainerRight != null) {
                LinearLayout recycledLeft = splitPreviousTopPanelContainerLeft;
                LinearLayout recycledRight = splitPreviousTopPanelContainerRight;
                splitActiveTopPanelContainerLeft.animate().translationX(-width).setDuration(140).start();
                splitNextTopPanelContainerLeft.animate()
                        .translationX(0f)
                        .setDuration(140)
                        .withEndAction(() -> {
                            topPanelPageIndex = targetPage;
                            splitPreviousTopPanelContainerLeft = splitActiveTopPanelContainerLeft;
                            splitActiveTopPanelContainerLeft = splitNextTopPanelContainerLeft;
                            splitNextTopPanelContainerLeft = recycledLeft;
                            splitPreviousTopPanelContainerRight = splitActiveTopPanelContainerRight;
                            splitActiveTopPanelContainerRight = splitNextTopPanelContainerRight;
                            splitNextTopPanelContainerRight = recycledRight;
                            bindSplitTopShortcutPanelView(
                                    splitNextTopPanelContainerLeft,
                                    splitNextTopPanelContainerRight,
                                    topPanelPageIndex < topShortcutPanels.size() - 1
                                            ? topShortcutPanels.get(topPanelPageIndex + 1)
                                            : null);
                            resetTopPanelPositions(0f);
                            splitActiveTopPanelContainerLeft.bringToFront();
                            splitActiveTopPanelContainerRight.bringToFront();
                        })
                        .start();
                splitActiveTopPanelContainerRight.animate().translationX(-width).setDuration(140).start();
                splitNextTopPanelContainerRight.animate().translationX(0f).setDuration(140).start();
                if (splitPreviousTopPanelContainerLeft != null) {
                    splitPreviousTopPanelContainerLeft.animate().translationX(-2f * width).setDuration(140).start();
                }
                if (splitPreviousTopPanelContainerRight != null) {
                    splitPreviousTopPanelContainerRight.animate().translationX(-2f * width).setDuration(140).start();
                }
                return;
            }
            if (moveToPrevious && splitPreviousTopPanelContainerLeft != null && splitPreviousTopPanelContainerRight != null) {
                LinearLayout recycledLeft = splitNextTopPanelContainerLeft;
                LinearLayout recycledRight = splitNextTopPanelContainerRight;
                splitActiveTopPanelContainerLeft.animate().translationX(width).setDuration(140).start();
                splitPreviousTopPanelContainerLeft.animate()
                        .translationX(0f)
                        .setDuration(140)
                        .withEndAction(() -> {
                            topPanelPageIndex = targetPage;
                            splitNextTopPanelContainerLeft = splitActiveTopPanelContainerLeft;
                            splitActiveTopPanelContainerLeft = splitPreviousTopPanelContainerLeft;
                            splitPreviousTopPanelContainerLeft = recycledLeft;
                            splitNextTopPanelContainerRight = splitActiveTopPanelContainerRight;
                            splitActiveTopPanelContainerRight = splitPreviousTopPanelContainerRight;
                            splitPreviousTopPanelContainerRight = recycledRight;
                            bindSplitTopShortcutPanelView(
                                    splitPreviousTopPanelContainerLeft,
                                    splitPreviousTopPanelContainerRight,
                                    topPanelPageIndex > 0
                                            ? topShortcutPanels.get(topPanelPageIndex - 1)
                                            : null);
                            resetTopPanelPositions(0f);
                            splitActiveTopPanelContainerLeft.bringToFront();
                            splitActiveTopPanelContainerRight.bringToFront();
                        })
                        .start();
                splitActiveTopPanelContainerRight.animate().translationX(width).setDuration(140).start();
                splitPreviousTopPanelContainerRight.animate().translationX(0f).setDuration(140).start();
                if (splitNextTopPanelContainerLeft != null) {
                    splitNextTopPanelContainerLeft.animate().translationX(2f * width).setDuration(140).start();
                }
                if (splitNextTopPanelContainerRight != null) {
                    splitNextTopPanelContainerRight.animate().translationX(2f * width).setDuration(140).start();
                }
                return;
            }
            animateTopPanelToRest();
            return;
        }

        if (moveToNext && nextTopPanelContainer != null) {
            LinearLayout recycledContainer = previousTopPanelContainer;
            activeTopPanelContainer.animate().translationX(-width).setDuration(140).start();
            nextTopPanelContainer.animate()
                    .translationX(0f)
                    .setDuration(140)
                    .withEndAction(() -> {
                        topPanelPageIndex = targetPage;
                        previousTopPanelContainer = activeTopPanelContainer;
                        activeTopPanelContainer = nextTopPanelContainer;
                        nextTopPanelContainer = recycledContainer;
                        bindTopShortcutPanelView(nextTopPanelContainer,
                                topPanelPageIndex < topShortcutPanels.size() - 1
                                        ? topShortcutPanels.get(topPanelPageIndex + 1)
                                        : null);
                        resetTopPanelPositions(0f);
                        activeTopPanelContainer.bringToFront();
                    })
                    .start();
            if (previousTopPanelContainer != null) {
                previousTopPanelContainer.animate().translationX(-2f * width).setDuration(140).start();
            }
            return;
        }
        if (moveToPrevious && previousTopPanelContainer != null) {
            LinearLayout recycledContainer = nextTopPanelContainer;
            activeTopPanelContainer.animate().translationX(width).setDuration(140).start();
            previousTopPanelContainer.animate()
                    .translationX(0f)
                    .setDuration(140)
                    .withEndAction(() -> {
                        topPanelPageIndex = targetPage;
                        nextTopPanelContainer = activeTopPanelContainer;
                        activeTopPanelContainer = previousTopPanelContainer;
                        previousTopPanelContainer = recycledContainer;
                        bindTopShortcutPanelView(previousTopPanelContainer,
                                topPanelPageIndex > 0
                                        ? topShortcutPanels.get(topPanelPageIndex - 1)
                                        : null);
                        resetTopPanelPositions(0f);
                        activeTopPanelContainer.bringToFront();
                    })
                    .start();
            if (nextTopPanelContainer != null) {
                nextTopPanelContainer.animate().translationX(2f * width).setDuration(140).start();
            }
            return;
        }

        animateTopPanelToRest();
    }

    private void cancelTopPanelAnimations() {
        if (isSplitTopPanelMode()) {
            if (splitPreviousTopPanelContainerLeft != null) splitPreviousTopPanelContainerLeft.animate().cancel();
            if (splitActiveTopPanelContainerLeft != null) splitActiveTopPanelContainerLeft.animate().cancel();
            if (splitNextTopPanelContainerLeft != null) splitNextTopPanelContainerLeft.animate().cancel();
            if (splitPreviousTopPanelContainerRight != null) splitPreviousTopPanelContainerRight.animate().cancel();
            if (splitActiveTopPanelContainerRight != null) splitActiveTopPanelContainerRight.animate().cancel();
            if (splitNextTopPanelContainerRight != null) splitNextTopPanelContainerRight.animate().cancel();
            return;
        }
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.animate().cancel();
        }
        if (activeTopPanelContainer != null) {
            activeTopPanelContainer.animate().cancel();
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.animate().cancel();
        }
    }

    private void cancelFixedTopRowsAnimations() {
        if (isSplitTopPanelMode()) {
            if (splitPreviousFixedTopRowsContainerLeft != null) splitPreviousFixedTopRowsContainerLeft.animate().cancel();
            if (splitActiveFixedTopRowsContainerLeft != null) splitActiveFixedTopRowsContainerLeft.animate().cancel();
            if (splitNextFixedTopRowsContainerLeft != null) splitNextFixedTopRowsContainerLeft.animate().cancel();
            if (splitPreviousFixedTopRowsContainerRight != null) splitPreviousFixedTopRowsContainerRight.animate().cancel();
            if (splitActiveFixedTopRowsContainerRight != null) splitActiveFixedTopRowsContainerRight.animate().cancel();
            if (splitNextFixedTopRowsContainerRight != null) splitNextFixedTopRowsContainerRight.animate().cancel();
            return;
        }
        if (previousFixedTopRowsContainer != null) previousFixedTopRowsContainer.animate().cancel();
        if (activeFixedTopRowsContainer != null) activeFixedTopRowsContainer.animate().cancel();
        if (nextFixedTopRowsContainer != null) nextFixedTopRowsContainer.animate().cancel();
    }

    private void updateTopPanelDrag(float translationX) {
        if (!hasAnyTopPanelMode()) {
            return;
        }
        float width = getTopPanelWidth();
        if (isSplitTopPanelMode()) {
            splitActiveTopPanelContainerLeft.setTranslationX(translationX);
            splitActiveTopPanelContainerRight.setTranslationX(translationX);
            if (splitPreviousTopPanelContainerLeft != null) {
                splitPreviousTopPanelContainerLeft.setTranslationX(translationX - width);
            }
            if (splitPreviousTopPanelContainerRight != null) {
                splitPreviousTopPanelContainerRight.setTranslationX(translationX - width);
            }
            if (splitNextTopPanelContainerLeft != null) {
                splitNextTopPanelContainerLeft.setTranslationX(translationX + width);
            }
            if (splitNextTopPanelContainerRight != null) {
                splitNextTopPanelContainerRight.setTranslationX(translationX + width);
            }
            return;
        }
        activeTopPanelContainer.setTranslationX(translationX);
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.setTranslationX(translationX - width);
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.setTranslationX(translationX + width);
        }
    }

    private void updateFixedTopRowsDrag(float translationX) {
        if (!hasAnyFixedRowsPagerMode()) {
            return;
        }
        float width = getFixedTopRowsWidth();
        if (isSplitTopPanelMode()) {
            splitActiveFixedTopRowsContainerLeft.setTranslationX(translationX);
            splitActiveFixedTopRowsContainerRight.setTranslationX(translationX);
            if (splitPreviousFixedTopRowsContainerLeft != null) {
                splitPreviousFixedTopRowsContainerLeft.setTranslationX(translationX - width);
            }
            if (splitPreviousFixedTopRowsContainerRight != null) {
                splitPreviousFixedTopRowsContainerRight.setTranslationX(translationX - width);
            }
            if (splitNextFixedTopRowsContainerLeft != null) {
                splitNextFixedTopRowsContainerLeft.setTranslationX(translationX + width);
            }
            if (splitNextFixedTopRowsContainerRight != null) {
                splitNextFixedTopRowsContainerRight.setTranslationX(translationX + width);
            }
            return;
        }
        activeFixedTopRowsContainer.setTranslationX(translationX);
        if (previousFixedTopRowsContainer != null) {
            previousFixedTopRowsContainer.setTranslationX(translationX - width);
        }
        if (nextFixedTopRowsContainer != null) {
            nextFixedTopRowsContainer.setTranslationX(translationX + width);
        }
    }

    private void resetTopPanelPositions(float activeTranslationX) {
        if (!hasAnyTopPanelMode()) {
            return;
        }
        float width = getTopPanelWidth();
        if (isSplitTopPanelMode()) {
            splitActiveTopPanelContainerLeft.setTranslationX(activeTranslationX);
            splitActiveTopPanelContainerRight.setTranslationX(activeTranslationX);
            if (splitPreviousTopPanelContainerLeft != null) {
                splitPreviousTopPanelContainerLeft.setTranslationX(activeTranslationX - width);
            }
            if (splitPreviousTopPanelContainerRight != null) {
                splitPreviousTopPanelContainerRight.setTranslationX(activeTranslationX - width);
            }
            if (splitNextTopPanelContainerLeft != null) {
                splitNextTopPanelContainerLeft.setTranslationX(activeTranslationX + width);
            }
            if (splitNextTopPanelContainerRight != null) {
                splitNextTopPanelContainerRight.setTranslationX(activeTranslationX + width);
            }
            return;
        }
        activeTopPanelContainer.setTranslationX(activeTranslationX);
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.setTranslationX(activeTranslationX - width);
        }
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.setTranslationX(activeTranslationX + width);
        }
    }

    private void resetFixedTopRowsPositions(float activeTranslationX) {
        if (!hasAnyFixedRowsPagerMode()) {
            return;
        }
        float width = getFixedTopRowsWidth();
        if (isSplitTopPanelMode()) {
            splitActiveFixedTopRowsContainerLeft.setTranslationX(activeTranslationX);
            splitActiveFixedTopRowsContainerRight.setTranslationX(activeTranslationX);
            if (splitPreviousFixedTopRowsContainerLeft != null) {
                splitPreviousFixedTopRowsContainerLeft.setTranslationX(activeTranslationX - width);
            }
            if (splitPreviousFixedTopRowsContainerRight != null) {
                splitPreviousFixedTopRowsContainerRight.setTranslationX(activeTranslationX - width);
            }
            if (splitNextFixedTopRowsContainerLeft != null) {
                splitNextFixedTopRowsContainerLeft.setTranslationX(activeTranslationX + width);
            }
            if (splitNextFixedTopRowsContainerRight != null) {
                splitNextFixedTopRowsContainerRight.setTranslationX(activeTranslationX + width);
            }
            return;
        }
        activeFixedTopRowsContainer.setTranslationX(activeTranslationX);
        if (previousFixedTopRowsContainer != null) {
            previousFixedTopRowsContainer.setTranslationX(activeTranslationX - width);
        }
        if (nextFixedTopRowsContainer != null) {
            nextFixedTopRowsContainer.setTranslationX(activeTranslationX + width);
        }
    }

    private void animateTopPanelToRest() {
        if (!hasAnyTopPanelMode()) {
            return;
        }
        float width = getTopPanelWidth();
        if (isSplitTopPanelMode()) {
            if (splitPreviousTopPanelContainerLeft != null) {
                splitPreviousTopPanelContainerLeft.animate().translationX(-width).setDuration(140).start();
            }
            if (splitPreviousTopPanelContainerRight != null) {
                splitPreviousTopPanelContainerRight.animate().translationX(-width).setDuration(140).start();
            }
            splitActiveTopPanelContainerLeft.animate().translationX(0f).setDuration(140).start();
            splitActiveTopPanelContainerRight.animate().translationX(0f).setDuration(140).start();
            if (splitNextTopPanelContainerLeft != null) {
                splitNextTopPanelContainerLeft.animate().translationX(width).setDuration(140).start();
            }
            if (splitNextTopPanelContainerRight != null) {
                splitNextTopPanelContainerRight.animate().translationX(width).setDuration(140).start();
            }
            return;
        }
        if (previousTopPanelContainer != null) {
            previousTopPanelContainer.animate().translationX(-width).setDuration(140).start();
        }
        activeTopPanelContainer.animate().translationX(0f).setDuration(140).start();
        if (nextTopPanelContainer != null) {
            nextTopPanelContainer.animate().translationX(width).setDuration(140).start();
        }
    }

    private void animateFixedTopRowsToRest() {
        if (!hasAnyFixedRowsPagerMode()) {
            return;
        }
        float width = getFixedTopRowsWidth();
        if (isSplitTopPanelMode()) {
            if (splitPreviousFixedTopRowsContainerLeft != null) {
                splitPreviousFixedTopRowsContainerLeft.animate().translationX(-width).setDuration(140).start();
            }
            if (splitPreviousFixedTopRowsContainerRight != null) {
                splitPreviousFixedTopRowsContainerRight.animate().translationX(-width).setDuration(140).start();
            }
            splitActiveFixedTopRowsContainerLeft.animate().translationX(0f).setDuration(140).start();
            splitActiveFixedTopRowsContainerRight.animate().translationX(0f).setDuration(140).start();
            if (splitNextFixedTopRowsContainerLeft != null) {
                splitNextFixedTopRowsContainerLeft.animate().translationX(width).setDuration(140).start();
            }
            if (splitNextFixedTopRowsContainerRight != null) {
                splitNextFixedTopRowsContainerRight.animate().translationX(width).setDuration(140).start();
            }
            return;
        }
        if (previousFixedTopRowsContainer != null) {
            previousFixedTopRowsContainer.animate().translationX(-width).setDuration(140).start();
        }
        activeFixedTopRowsContainer.animate().translationX(0f).setDuration(140).start();
        if (nextFixedTopRowsContainer != null) {
            nextFixedTopRowsContainer.animate().translationX(width).setDuration(140).start();
        }
    }

    private float applyFixedTopRowsEdgeResistance(float translationX) {
        boolean draggingPastFirst = fixedTopRowsPageIndex == 0 && translationX > 0;
        boolean draggingPastLast = fixedTopRowsPageIndex == fixedTopRowsPanels.size() - 1 && translationX < 0;
        if (draggingPastFirst || draggingPastLast) {
            return translationX * 0.35f;
        }
        return translationX;
    }

    private void finishFixedTopRowsSwipe(float translationX, int swipeThreshold) {
        if (!hasAnyFixedRowsPagerMode()) {
            return;
        }
        boolean moveToNext = translationX < -swipeThreshold && fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1;
        boolean moveToPrevious = translationX > swipeThreshold && fixedTopRowsPageIndex > 0;
        if (!moveToNext && !moveToPrevious) {
            animateFixedTopRowsToRest();
            return;
        }
        int targetPage = moveToNext ? fixedTopRowsPageIndex + 1 : fixedTopRowsPageIndex - 1;
        float width = getFixedTopRowsWidth();
        if (isSplitTopPanelMode()) {
            if (moveToNext && splitNextFixedTopRowsContainerLeft != null && splitNextFixedTopRowsContainerRight != null) {
                LinearLayout recycledLeft = splitPreviousFixedTopRowsContainerLeft;
                LinearLayout recycledRight = splitPreviousFixedTopRowsContainerRight;
                splitActiveFixedTopRowsContainerLeft.animate().translationX(-width).setDuration(140).start();
                splitNextFixedTopRowsContainerLeft.animate().translationX(0f).setDuration(140).withEndAction(() -> {
                    fixedTopRowsPageIndex = targetPage;
                    splitPreviousFixedTopRowsContainerLeft = splitActiveFixedTopRowsContainerLeft;
                    splitActiveFixedTopRowsContainerLeft = splitNextFixedTopRowsContainerLeft;
                    splitNextFixedTopRowsContainerLeft = recycledLeft;
                    splitPreviousFixedTopRowsContainerRight = splitActiveFixedTopRowsContainerRight;
                    splitActiveFixedTopRowsContainerRight = splitNextFixedTopRowsContainerRight;
                    splitNextFixedTopRowsContainerRight = recycledRight;
                    bindSplitFixedTopRowsPanelView(
                            splitNextFixedTopRowsContainerLeft,
                            splitNextFixedTopRowsContainerRight,
                            fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                                    ? fixedTopRowsPanels.get(fixedTopRowsPageIndex + 1)
                                    : null,
                            fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                                    ? fixedTopRowsPageIndex + 1
                                    : -1);
                    resetFixedTopRowsPositions(0f);
                    splitActiveFixedTopRowsContainerLeft.bringToFront();
                    splitActiveFixedTopRowsContainerRight.bringToFront();
                }).start();
                splitActiveFixedTopRowsContainerRight.animate().translationX(-width).setDuration(140).start();
                splitNextFixedTopRowsContainerRight.animate().translationX(0f).setDuration(140).start();
                if (splitPreviousFixedTopRowsContainerLeft != null) {
                    splitPreviousFixedTopRowsContainerLeft.animate().translationX(-2f * width).setDuration(140).start();
                }
                if (splitPreviousFixedTopRowsContainerRight != null) {
                    splitPreviousFixedTopRowsContainerRight.animate().translationX(-2f * width).setDuration(140).start();
                }
                return;
            }
            if (moveToPrevious && splitPreviousFixedTopRowsContainerLeft != null && splitPreviousFixedTopRowsContainerRight != null) {
                LinearLayout recycledLeft = splitNextFixedTopRowsContainerLeft;
                LinearLayout recycledRight = splitNextFixedTopRowsContainerRight;
                splitActiveFixedTopRowsContainerLeft.animate().translationX(width).setDuration(140).start();
                splitPreviousFixedTopRowsContainerLeft.animate().translationX(0f).setDuration(140).withEndAction(() -> {
                    fixedTopRowsPageIndex = targetPage;
                    splitNextFixedTopRowsContainerLeft = splitActiveFixedTopRowsContainerLeft;
                    splitActiveFixedTopRowsContainerLeft = splitPreviousFixedTopRowsContainerLeft;
                    splitPreviousFixedTopRowsContainerLeft = recycledLeft;
                    splitNextFixedTopRowsContainerRight = splitActiveFixedTopRowsContainerRight;
                    splitActiveFixedTopRowsContainerRight = splitPreviousFixedTopRowsContainerRight;
                    splitPreviousFixedTopRowsContainerRight = recycledRight;
                    bindSplitFixedTopRowsPanelView(
                            splitPreviousFixedTopRowsContainerLeft,
                            splitPreviousFixedTopRowsContainerRight,
                            fixedTopRowsPageIndex > 0
                                    ? fixedTopRowsPanels.get(fixedTopRowsPageIndex - 1)
                                    : null,
                            fixedTopRowsPageIndex > 0 ? fixedTopRowsPageIndex - 1 : -1);
                    resetFixedTopRowsPositions(0f);
                    splitActiveFixedTopRowsContainerLeft.bringToFront();
                    splitActiveFixedTopRowsContainerRight.bringToFront();
                }).start();
                splitActiveFixedTopRowsContainerRight.animate().translationX(width).setDuration(140).start();
                splitPreviousFixedTopRowsContainerRight.animate().translationX(0f).setDuration(140).start();
                if (splitNextFixedTopRowsContainerLeft != null) {
                    splitNextFixedTopRowsContainerLeft.animate().translationX(2f * width).setDuration(140).start();
                }
                if (splitNextFixedTopRowsContainerRight != null) {
                    splitNextFixedTopRowsContainerRight.animate().translationX(2f * width).setDuration(140).start();
                }
                return;
            }
            animateFixedTopRowsToRest();
            return;
        }
        if (moveToNext && nextFixedTopRowsContainer != null) {
            LinearLayout recycledContainer = previousFixedTopRowsContainer;
            activeFixedTopRowsContainer.animate().translationX(-width).setDuration(140).start();
            nextFixedTopRowsContainer.animate().translationX(0f).setDuration(140).withEndAction(() -> {
                fixedTopRowsPageIndex = targetPage;
                previousFixedTopRowsContainer = activeFixedTopRowsContainer;
                activeFixedTopRowsContainer = nextFixedTopRowsContainer;
                nextFixedTopRowsContainer = recycledContainer;
                bindFixedTopRowsPanelView(
                        nextFixedTopRowsContainer,
                        fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                                ? fixedTopRowsPanels.get(fixedTopRowsPageIndex + 1)
                                : null,
                        0,
                        TOP_PANEL_COLUMNS,
                        fixedTopRowsPageIndex < fixedTopRowsPanels.size() - 1
                                ? fixedTopRowsPageIndex + 1
                                : -1);
                resetFixedTopRowsPositions(0f);
                activeFixedTopRowsContainer.bringToFront();
            }).start();
            if (previousFixedTopRowsContainer != null) {
                previousFixedTopRowsContainer.animate().translationX(-2f * width).setDuration(140).start();
            }
            return;
        }
        if (moveToPrevious && previousFixedTopRowsContainer != null) {
            LinearLayout recycledContainer = nextFixedTopRowsContainer;
            activeFixedTopRowsContainer.animate().translationX(width).setDuration(140).start();
            previousFixedTopRowsContainer.animate().translationX(0f).setDuration(140).withEndAction(() -> {
                fixedTopRowsPageIndex = targetPage;
                nextFixedTopRowsContainer = activeFixedTopRowsContainer;
                activeFixedTopRowsContainer = previousFixedTopRowsContainer;
                previousFixedTopRowsContainer = recycledContainer;
                bindFixedTopRowsPanelView(
                        previousFixedTopRowsContainer,
                        fixedTopRowsPageIndex > 0
                                ? fixedTopRowsPanels.get(fixedTopRowsPageIndex - 1)
                                : null,
                        0,
                        TOP_PANEL_COLUMNS,
                        fixedTopRowsPageIndex > 0 ? fixedTopRowsPageIndex - 1 : -1);
                resetFixedTopRowsPositions(0f);
                activeFixedTopRowsContainer.bringToFront();
            }).start();
            if (nextFixedTopRowsContainer != null) {
                nextFixedTopRowsContainer.animate().translationX(2f * width).setDuration(140).start();
            }
            return;
        }
        animateFixedTopRowsToRest();
    }

    private void refreshVisibleTopPanelButtonStates() {
        if (isSplitTopPanelMode()) {
            refreshTopPanelButtonStates(splitPreviousTopPanelContainerLeft);
            refreshTopPanelButtonStates(splitActiveTopPanelContainerLeft);
            refreshTopPanelButtonStates(splitNextTopPanelContainerLeft);
            refreshTopPanelButtonStates(splitPreviousTopPanelContainerRight);
            refreshTopPanelButtonStates(splitActiveTopPanelContainerRight);
            refreshTopPanelButtonStates(splitNextTopPanelContainerRight);
            refreshTopPanelButtonStates(splitPreviousFixedTopRowsContainerLeft);
            refreshTopPanelButtonStates(splitActiveFixedTopRowsContainerLeft);
            refreshTopPanelButtonStates(splitNextFixedTopRowsContainerLeft);
            refreshTopPanelButtonStates(splitPreviousFixedTopRowsContainerRight);
            refreshTopPanelButtonStates(splitActiveFixedTopRowsContainerRight);
            refreshTopPanelButtonStates(splitNextFixedTopRowsContainerRight);
            return;
        }
        refreshTopPanelButtonStates(previousTopPanelContainer);
        refreshTopPanelButtonStates(activeTopPanelContainer);
        refreshTopPanelButtonStates(nextTopPanelContainer);
        refreshTopPanelButtonStates(previousFixedTopRowsContainer);
        refreshTopPanelButtonStates(activeFixedTopRowsContainer);
        refreshTopPanelButtonStates(nextFixedTopRowsContainer);
    }

    private void refreshTopPanelButtonStates(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof Button || view instanceof ImageButton) {
            Object tag = view.getTag();
            if (tag instanceof Key) {
                Key key = (Key) tag;
                boolean modifierLocked = (key.code == 0xE0 && isCtrlLeftLocked)
                        || (key.code == 0xE1 && isShiftLeftLocked)
                        || (key.code == 0xE2 && isAltLeftLocked)
                        || (key.code == 0xE3 && isWinLeftLocked);
                boolean keyLockedVisualState =
                        isFixedTopLocalFnKey(key)
                                ? fixedTopLocalFnLocked
                                : (isTopModifierLockCandidate(key)
                                        ? isProModifierCapVisualOn(key)
                                        : modifierLocked);
                if (isTopImeToggleKey(key)) {
                    applyTopPanelImeToggleKeyCapBackground(view);
                    view.setActivated(false);
                    view.setSelected(false);
                    if (view instanceof ImageButton) {
                        applyTopImeToggleIconTint((ImageButton) view);
                    } else if (view instanceof TextView) {
                        TextView tv = (TextView) view;
                        applyTopImeToggleLabelTypography(tv);
                        applyTopImeToggleLabelTextColor(tv);
                    }
                } else {
                    applyTopPanelKeyCapBackground(view, key, keyLockedVisualState);
                    view.setSelected(keyLockedVisualState);
                }
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshTopPanelButtonStates(group.getChildAt(i));
            }
        }
    }

    private float getTopPanelWidth() {
        if (isSplitTopPanelMode()) {
            if (splitTopLeftViewport != null && splitTopLeftViewport.getWidth() > 0) {
                return splitTopLeftViewport.getWidth();
            }
            if (splitActiveTopPanelContainerLeft != null && splitActiveTopPanelContainerLeft.getWidth() > 0) {
                return splitActiveTopPanelContainerLeft.getWidth();
            }
        }
        if (topPanelViewport != null && topPanelViewport.getWidth() > 0) {
            return topPanelViewport.getWidth();
        }
        if (activeTopPanelContainer != null && activeTopPanelContainer.getWidth() > 0) {
            return activeTopPanelContainer.getWidth();
        }
        return getWidth();
    }

    private float getFixedTopRowsWidth() {
        if (isSplitTopPanelMode()) {
            if (splitFixedTopRowsViewportLeft != null && splitFixedTopRowsViewportLeft.getWidth() > 0) {
                return splitFixedTopRowsViewportLeft.getWidth();
            }
            if (splitActiveFixedTopRowsContainerLeft != null && splitActiveFixedTopRowsContainerLeft.getWidth() > 0) {
                return splitActiveFixedTopRowsContainerLeft.getWidth();
            }
        }
        if (fixedTopRowsViewport != null && fixedTopRowsViewport.getWidth() > 0) {
            return fixedTopRowsViewport.getWidth();
        }
        if (activeFixedTopRowsContainer != null && activeFixedTopRowsContainer.getWidth() > 0) {
            return activeFixedTopRowsContainer.getWidth();
        }
        return getWidth();
    }

    private void addExtraPortraitKeys() {
        int primaryModifier = "macos".equals(getTargetOs()) ? MOD_WIN : MOD_CTRL;
        List<ExtraGridKey> gridKeys = new ArrayList<>();

        // Row 1: CUT, PASTE, COPY, ALL (left-to-right)
        gridKeys.add(new ExtraGridKey(new Key("CUT", "", 0x1B, "1B", 25f, R.drawable.content_cut_24, 0f, false, false, primaryModifier, false), 0, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("PASTE", "", 0x19, "19", 25f, R.drawable.content_paste_24, 0f, false, false, primaryModifier, false), 0, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("COPY", "", 0x06, "06", 25f, R.drawable.content_copy_24, 0f, false, false, primaryModifier, false), 0, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("ALL", "", 0x04, "04", 25f, R.drawable.select_all_24, 0f, false, false, primaryModifier, false), 0, 6, 1, 2));

        // Row 2 (was row 4): ESC (Fn → NumLock), # (Fn → |), Undo (Fn → Redo), Backspace
        gridKeys.add(new ExtraGridKey(new Key("ESC", "", 0x29, "29", 25f, 0, 0f, false), 1, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("#", "", 0x20, "20", 25f, 0, 0f, false, false, MOD_SHIFT, false), 1, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("UNDO", "", 0x1D, "1D", 25f, R.drawable.undo_24, 0f, false, false, primaryModifier, false), 1, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("BKSP", "", 0x2A, "2A", 25f, R.drawable.backspace_24, 0f, false), 1, 6, 1, 2));

        // Row 3 (was row 5)
        gridKeys.add(new ExtraGridKey(new Key("=", "", 0x67, "67", 25f, 0, 0f, false), 2, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("Space", "", 0x2C, "2C", 25f, 0, 0f, false), 2, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("/", "", 0x54, "54", 25f, 0, 0f, false), 2, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("*", "", 0x55, "55", 25f, 0, 0f, false), 2, 6, 1, 2));

        // Row 4 (was row 6)
        gridKeys.add(new ExtraGridKey(new Key("7", "", 0x5F, "5F", 25f, 0, 0f, false), 3, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("8", "", 0x60, "60", 25f, 0, 0f, false), 3, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("9", "", 0x61, "61", 25f, 0, 0f, false), 3, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("+", "", 0x57, "57", 25f, 0, 0f, false), 3, 6, 1, 2));

        // Row 5 (was row 7)
        gridKeys.add(new ExtraGridKey(new Key("4", "", 0x5C, "5C", 25f, 0, 0f, false), 4, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("5", "", 0x5D, "5D", 25f, 0, 0f, false), 4, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("6", "", 0x5E, "5E", 25f, 0, 0f, false), 4, 4, 1, 2));
        // Tab (icon) primary; local Fn → Save (icon)
        gridKeys.add(new ExtraGridKey(new Key("TAB", "", 0x2B, "2B", 25f, 0, 0f, false), 4, 6, 1, 2));

        // Row 6 (was row 8)
        gridKeys.add(new ExtraGridKey(new Key("1", "", 0x59, "59", 25f, 0, 0f, false), 5, 0, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("2", "", 0x5A, "5A", 25f, 0, 0f, false), 5, 2, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("3", "", 0x5B, "5B", 25f, 0, 0f, false), 5, 4, 1, 2));
        gridKeys.add(new ExtraGridKey(new Key("ENTER", "", 0x28, "28", 25f, 0, 0f, false), 5, 6, 2, 2));

        // Row 7 (was row 9)
        gridKeys.add(new ExtraGridKey(new Key("FN", "", KEY_EXTRA_NUMPAD_FN, "F006", 25f, R.drawable.ic_swap_horiz_24, 0f, false), 6, 0, 1, 1));
        gridKeys.add(new ExtraGridKey(new Key(".", "", 0x63, "63", 25f, 0, 0f, false), 6, 1, 1, 1));
        gridKeys.add(new ExtraGridKey(new Key("0", "", 0x62, "62", 25f, 0, 0f, false), 6, 2, 1, 4));

        addExtraGrid(7, 8, gridKeys);
    }

    private void addExtraGrid(int rows, int columns, List<ExtraGridKey> gridKeys) {
        extraNumpadGrid = new GridLayout(getContext());
        GridLayout gridLayout = extraNumpadGrid;
        extraNumpadFnButton = null;
        gridLayout.setColumnCount(columns);
        gridLayout.setRowCount(rows);
        gridLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 9.6f));

        for (ExtraGridKey entry : gridKeys) {
            GridLayout.Spec rowSpec = GridLayout.spec(entry.row, entry.rowSpan, getExtraGridRowSpanWeight(entry.row, entry.rowSpan));
            GridLayout.Spec colSpec = GridLayout.spec(entry.col, entry.colSpan, (float) entry.colSpan);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
            params.width = 0;
            params.height = 0;
            params.setGravity(Gravity.FILL);
            int keyMargin = dpToPx(KEY_OUTER_MARGIN_DP);
            params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin);
            if (shouldRenderExtraNumpadSplitPlusCell(entry)) {
                gridLayout.addView(createExtraNumpadSplitPlusCell(params));
                continue;
            }
            if (shouldRenderExtraNumpadZeroWordmarkCell(entry)) {
                gridLayout.addView(createExtraNumpadZeroWordmarkCell(entry, params));
                continue;
            }
            if (entry.key.iconResId != 0) {
                ImageButton iconButton = new ImageButton(getContext());
                applyFlatKeyStyle(iconButton);
                iconButton.setBackgroundResource(R.drawable.function_button_background);
                iconButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                int iconPadDp = (entry.key.code == 0x2A && "BKSP".equals(entry.key.label)) ? 1 : 6;
                iconButton.setPadding(dpToPx(iconPadDp), dpToPx(iconPadDp), dpToPx(iconPadDp), dpToPx(iconPadDp));
                iconButton.setImageResource(entry.key.iconResId);
                iconButton.setColorFilter(resolveThemeTextColor());
                iconButton.setLayoutParams(params);
                iconButton.setTag(R.id.tag_custom_keyboard_extra_cell_key, entry.key);
                if (entry.key.code == KEY_EXTRA_NUMPAD_FN) {
                    iconButton.setContentDescription("Fn");
                    extraNumpadFnButton = iconButton;
                    iconButton.setSelected(extraNumpadFnLocked);
                }
                if (shouldRepeatOnLongPress(entry.key)) {
                    attachKeyListeners(iconButton, entry.key);
                } else {
                    iconButton.setOnClickListener(v -> handleKeyPress(entry.key));
                    iconButton.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            performKeyHapticFeedback(v);
                        }
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            scheduleKeyboardTapRelease();
                        }
                        return false;
                    });
                }
                gridLayout.addView(iconButton);
            } else {
                Button textButton = new Button(getContext());
                applyFlatKeyStyle(textButton);
                textButton.setBackgroundResource(R.drawable.function_button_background);
                textButton.setGravity(Gravity.CENTER);
                textButton.setTextSize(14);
                textButton.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                textButton.setText(entry.key.label);
                textButton.setTextColor(resolveThemeTextColor());
                textButton.setTag(R.id.tag_custom_keyboard_extra_cell_key, entry.key);
                styleExtraNumpadGridKeyButton(textButton, entry.key.label);
                textButton.setLayoutParams(params);
                if (shouldRepeatOnLongPress(entry.key)) {
                    attachKeyListeners(textButton, entry.key);
                } else {
                    textButton.setOnClickListener(v -> handleKeyPress(entry.key));
                    textButton.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            performKeyHapticFeedback(v);
                        }
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            scheduleKeyboardTapRelease();
                        }
                        return false;
                    });
                }
                gridLayout.addView(textButton);
            }
        }

        addView(gridLayout);
        refreshExtraNumpadGridUi();
    }

    private boolean shouldRenderExtraNumpadSplitPlusCell(ExtraGridKey entry) {
        return entry != null
                && entry.key != null
                && "+".equals(entry.key.label)
                && entry.key.code == 0x57
                && entry.row == 3
                && entry.col == 6
                && entry.rowSpan == 1
                && entry.colSpan == 2;
    }

    /** Wide numpad 0: match KM Basic {@code fragment_basic_numpad} wordmark on {@code NUMPAD_0}. */
    private boolean shouldRenderExtraNumpadZeroWordmarkCell(@Nullable ExtraGridKey entry) {
        return entry != null
                && entry.key != null
                && entry.key.code == 0x62
                && "0".equals(entry.key.label)
                && entry.colSpan == 4
                && entry.rowSpan == 1;
    }

    private View createExtraNumpadZeroWordmarkCell(ExtraGridKey entry, GridLayout.LayoutParams params) {
        Context ctx = getContext();
        FrameLayout cell = new FrameLayout(ctx);
        applyFlatKeyStyle(cell);
        cell.setLayoutParams(params);
        cell.setBackgroundResource(R.drawable.function_button_background);
        cell.setClickable(false);

        ImageView brand = new ImageView(ctx);
        Resources res = ctx.getResources();
        int padV = dpToPx(4);
        // Match fragment_basic_numpad NUMPAD_0 inner image padding (6dp horizontal, 4dp vertical).
        int padH = dpToPx(6);
        brand.setPaddingRelative(padH, padV, padH, padV);
        int maxH = res.getDimensionPixelSize(R.dimen.km_basic_numpad_zero_wordmark_max_height);
        int maxW = res.getDimensionPixelSize(R.dimen.km_basic_numpad_zero_wordmark_max_width);
        brand.setMaxWidth(maxW);
        brand.setMaxHeight(maxH);
        brand.setAdjustViewBounds(true);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.setImageResource(R.drawable.ic_openterface_wordmark);
        brand.setColorFilter(
                ContextCompat.getColor(ctx, R.color.basic_key_label_color), PorterDuff.Mode.SRC_IN);
        brand.setContentDescription(ctx.getString(R.string.kb_basic_numpad_zero_desc));
        brand.setDuplicateParentStateEnabled(true);
        FrameLayout.LayoutParams logoLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);
        brand.setLayoutParams(logoLp);
        brand.setTag(R.id.tag_custom_keyboard_extra_cell_key, entry.key);
        if (shouldRepeatOnLongPress(entry.key)) {
            attachKeyListeners(brand, entry.key);
        } else {
            brand.setOnClickListener(v -> handleKeyPress(entry.key));
            brand.setOnTouchListener(
                    (v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            performKeyHapticFeedback(v);
                        }
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            scheduleKeyboardTapRelease();
                        }
                        return false;
                    });
        }
        cell.addView(brand);
        return cell;
    }

    private View createExtraNumpadSplitPlusCell(GridLayout.LayoutParams params) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(params);

        int innerGap = dpToPx(2);
        int sidePadding = dpToPx(2);

        Key plusKey = new Key("-", "", 0x56, "56", 25f, 0, 0f, false);
        Button plusButton = new Button(getContext());
        applyFlatKeyStyle(plusButton);
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f);
        plusParams.setMargins(0, 0, 0, innerGap);
        plusButton.setLayoutParams(plusParams);
        plusButton.setBackgroundResource(R.drawable.function_button_background);
        plusButton.setGravity(Gravity.CENTER);
        plusButton.setTextSize(14);
        plusButton.setPadding(sidePadding, sidePadding, sidePadding, innerGap);
        plusButton.setText(plusKey.label);
        plusButton.setTextColor(resolveThemeTextColor());
        plusButton.setTag(R.id.tag_custom_keyboard_extra_cell_key, plusKey);
        styleExtraNumpadGridKeyButton(plusButton, plusKey.label);
        if (shouldRepeatOnLongPress(plusKey)) {
            attachKeyListeners(plusButton, plusKey);
        } else {
            plusButton.setOnClickListener(v -> handleKeyPress(plusKey));
            plusButton.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performKeyHapticFeedback(v);
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    scheduleKeyboardTapRelease();
                }
                return false;
            });
        }

        Key spaceKey = new Key("+", "", 0x57, "57", 25f, 0, 0f, false);
        Button spaceButton = new Button(getContext());
        applyFlatKeyStyle(spaceButton);
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f);
        spaceParams.setMargins(0, innerGap, 0, 0);
        spaceButton.setLayoutParams(spaceParams);
        spaceButton.setBackgroundResource(R.drawable.function_button_background);
        spaceButton.setGravity(Gravity.CENTER);
        spaceButton.setTextSize(14);
        spaceButton.setPadding(sidePadding, innerGap, sidePadding, sidePadding);
        spaceButton.setText(spaceKey.label);
        spaceButton.setTextColor(resolveThemeTextColor());
        spaceButton.setTag(R.id.tag_custom_keyboard_extra_cell_key, spaceKey);
        styleExtraNumpadGridKeyButton(spaceButton, spaceKey.label);
        if (shouldRepeatOnLongPress(spaceKey)) {
            attachKeyListeners(spaceButton, spaceKey);
        } else {
            spaceButton.setOnClickListener(v -> handleKeyPress(spaceKey));
            spaceButton.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performKeyHapticFeedback(v);
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    scheduleKeyboardTapRelease();
                }
                return false;
            });
        }

        container.addView(plusButton);
        container.addView(spaceButton);
        return container;
    }

    /**
     * Extra numpad row heights: rows 0–2 (shortcuts, ESC row, operators / * = -) share the same compact
     * weight so the operator row matches the two rows above; rows 3–6 (digits, Tab, 123+Enter, Fn row)
     * use a larger weight so that block gets more of the vertical space.
     */
    private float getExtraGridRowSpanWeight(int row, int rowSpan) {
        float total = 0f;
        for (int i = 0; i < rowSpan; i++) {
            int r = row + i;
            total += r <= 2 ? 0.5f : 1.0f;
        }
        return total;
    }

    private static boolean isExtraNumpadArrowDirectionLabel(String label) {
        return "UP".equals(label) || "LEFT".equals(label) || "DOWN".equals(label) || "RIGHT".equals(label);
    }

    /**
     * Centers an icon in the key face (arrows use larger dp; Save/Undo/Tab match top shortcut row assets).
     */
    private void applyExtraNumpadGridIconOverlay(Button b, int iconResId, String contentDescription, int iconSizeDp) {
        b.setContentDescription(contentDescription);
        b.setText("");
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setCompoundDrawables(null, null, null, null);
        b.setCompoundDrawablePadding(0);
        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        if (icon == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                b.setForeground(null);
            }
            return;
        }
        icon = icon.mutate();
        icon.setTint(resolveThemeTextColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+: use foreground overlay with precise centering
            int sizePx = dpToPx(iconSizeDp);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{
                    new ColorDrawable(Color.TRANSPARENT),
                    icon
            });
            layers.setLayerGravity(1, Gravity.CENTER);
            layers.setLayerWidth(1, sizePx);
            layers.setLayerHeight(1, sizePx);
            b.setForeground(layers);
        } else {
            // API 21-22: use compound drawable (centered via gravity)
            b.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            b.setCompoundDrawablePadding(0);
            // Use a wrapper drawable to control size
            int sizePx = dpToPx(iconSizeDp);
            icon.setBounds(0, 0, sizePx, sizePx);
            b.setCompoundDrawables(icon, null, null, null);
        }
        b.setTextColor(resolveThemeTextColor());
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.NORMAL);
    }

    private void clearExtraNumpadArrowIconOverlay(Button b) {
        if (b != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            b.setForeground(null);
        }
    }

    private boolean isExtraNumpadDenseGlyphLabel(String label) {
        if (label == null) {
            return false;
        }
        if (label.length() == 1) {
            char c = label.charAt(0);
            if (c >= '0' && c <= '9') {
                return true;
            }
            switch (c) {
                case '/':
                case '*':
                case '-':
                case '+':
                case '=':
                case ',':
                case '.':
                case '$':
                case '¥':
                case '€':
                case '£':
                case '%':
                case '#':
                case '|':
                    return true;
                default:
                    return false;
            }
        }
        return "000".equals(label);
    }

    /** Typography for one extra numpad text key (digits, ops, punctuation, shortcuts). */
    private void styleExtraNumpadGridKeyButton(Button b, String displayLabel) {
        if (isExtraNumpadDenseGlyphLabel(displayLabel)) {
            b.setTextSize(EXTRA_NUMPAD_GRID_KEY_TEXT_SP);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        } else if ("HOME".equals(displayLabel)
                || "END".equals(displayLabel)
                || "PGUP".equals(displayLabel)
                || "PGDN".equals(displayLabel)
                || "INS".equals(displayLabel)
                || "NUM".equals(displayLabel)
                || "REDO".equals(displayLabel)
                || "ESC".equals(displayLabel)
                || "ENTER".equals(displayLabel)) {
            b.setTextSize(14);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            b.setTextSize(14);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private FnMapping resolveExtraNumpadFnMapping(Key key) {
        if (!extraNumpadFnLocked || key == null) {
            return null;
        }
        // Row 2 (ESC / # / Undo / Bksp): gate on label — UNDO shares 0x1D with Mac redo chord, use label to disambiguate.
        if ("ESC".equals(key.label) && key.code == 0x29) {
            return new FnMapping("NUM", 0x53, 0);
        }
        if ("#".equals(key.label) && key.code == 0x20) {
            return new FnMapping("|", 0x31, MOD_SHIFT, 0);
        }
        if ("BKSP".equals(key.label) && key.code == 0x2A) {
            return null;
        }
        if ("UNDO".equals(key.label) && key.code == 0x1D) {
            if ("macos".equals(getTargetOs())) {
                return new FnMapping("REDO", 0x1D, MOD_WIN | MOD_SHIFT, R.drawable.redo_24);
            }
            return new FnMapping("REDO", 0x1C, MOD_CTRL, R.drawable.redo_24);
        }
        if ("TAB".equals(key.label) && key.code == 0x2B) {
            int primaryMod = "macos".equals(getTargetOs()) ? MOD_WIN : MOD_CTRL;
            return new FnMapping("Save", 0x16, primaryMod, R.drawable.save_24);
        }
        // Operator row (grid row 2): Fn shows £ € ¥ $ left-to-right (=, Space, /, *).
        if ("Space".equals(key.label) && key.code == 0x2C) {
            return new FnMapping("€", 0x1F, MOD_ALT | MOD_SHIFT);
        }
        // Split column under *: Fn shows % (top) and forward delete (bottom).
        if ("-".equals(key.label) && key.code == 0x56) {
            return new FnMapping("%", 0x22, MOD_SHIFT);
        }
        if ("+".equals(key.label) && key.code == 0x57) {
            return new FnMapping("DEL", 0x4C, 0, R.drawable.backspace_24);
        }
        switch (key.code) {
            // Numpad / * when Fn: ¥ and $ (order matches £ € ¥ $ on operator row).
            case 0x54: return new FnMapping("¥", 0x1C, MOD_ALT);
            case 0x55: return new FnMapping("$", 0x21, MOD_SHIFT);
            case 0x67: return new FnMapping("£", 0x20, MOD_ALT);
            case 0x62: return new FnMapping("000", 0x62, 0);
            case 0x60: return new FnMapping("UP", 0x52, 0, R.drawable.keyboard_arrow_up_24);
            case 0x5C: return new FnMapping("LEFT", 0x50, 0, R.drawable.keyboard_arrow_left_24);
            case 0x5A: return new FnMapping("DOWN", 0x51, 0, R.drawable.keyboard_arrow_down_24);
            case 0x5E: return new FnMapping("RIGHT", 0x4F, 0, R.drawable.keyboard_arrow_right_24);
            case 0x5F: return new FnMapping("HOME", 0x4A, 0);
            case 0x59: return new FnMapping("END", 0x4D, 0);
            case 0x61: return new FnMapping("PGUP", 0x4B, 0);
            case 0x5B: return new FnMapping("PGDN", 0x4E, 0);
            case 0x5D: return new FnMapping("INS", 0x49, 0);
            case 0x63: return new FnMapping(",", 0x36, 0);
            default:
                return null;
        }
    }

    private boolean isFixedTopRowKey(Key key) {
        return key != null && key.isTopPanelKey && !key.allowTopPanelPagingGesture;
    }

    /**
     * F1–F12 and equals on fixed strip page 0 (digit overlay when local Fn latch is off).
     * Uses {@link Key#stripSlotFactoryCode} when set so Rows 2–3 overrides that rewrite {@link Key#code}
     * still participate in page-0 latch / overlay logic.
     */
    private boolean isFixedTopRowsFnDigitStripKey(Key key) {
        if (key == null) {
            return false;
        }
        int c = resolvedStripSlotFactoryScanCode(key);
        return (c >= 0x3A && c <= 0x45) || c == 0x2E;
    }

    /** Page 2 (Shortcut Hub) fixed-row keys: punctuation grid (and trailing local Fn). */
    private boolean isFixedTopRowsPage2StripKey(Key key) {
        if (key == null) {
            return false;
        }
        int c = key.code;
        switch (c) {
            case 0x1F: // @
            case 0x20: // #
            case 0x22: // %
            case 0x23: // ^
            case 0x24: // &
            case 0x25: // *
            case 0x2D: // -, _
            case 0x2F: // [
            case 0x30: // ]
            case 0x31: // \, | (same HID usage; Shift distinguishes)
            case 0x33: // :
            case 0x34: // ', "
            case 0x35: // `, ~ (Fn-on); also legacy grave slot
            case 0x26: // ( — 9 + Shift
            case 0x27: // ) — 0 + Shift
            case 0x36: // <, ,
            case 0x37: // >, .
            case 0x38: // /, ?
                return true;
            default:
                return false;
        }
    }

    /** Keys on fixed strip page 1 that use {@link #resolveFixedTopOverlayMapping} when local Fn is on. */
    private static boolean isFixedTopRowsPage1FnOverlayKey(Key key) {
        if (key == null) {
            return false;
        }
        switch (key.code) {
            case 0xE0:
            case 0xE2:
            case 0xE3:
            case 0x2B:
            case 0x52:
            case 0x28:
            case 0x29:
            case 0xE1:
            case 0x4C:
            case 0x50:
            case 0x51:
            case 0x4F:
                return true;
            default:
                return false;
        }
    }

    /**
     * Local Fn overlay mapping for fixed rows when the latch matches the current strip page.
     * Page 0 (F7–F12 / F1–F6 / =): digit overlay when latch is off. Page 1 (ESC / nav): overlay when
     * latch is on. Page 2 uses rebuilt rows instead. Keys with no overlay return null.
     * Use {@link #resolveFixedTopLocalFnMapping(Key)} for HID / behavior (visible strip page).
     */
    private FnMapping resolveFixedTopLocalFnMapping(Key key) {
        int page = fixedTopRowsPageIndex;
        if (key != null && key.stripSlotPage >= 0) {
            page = key.stripSlotPage;
        }
        return resolveFixedTopLocalFnMapping(key, page);
    }

    private FnMapping resolveFixedTopLocalFnMapping(Key key, int panelPageIndex) {
        int effectivePage = (key != null && key.stripSlotPage >= 0) ? key.stripSlotPage : panelPageIndex;
        FnMapping overlay = resolveFixedTopOverlayMapping(key);
        if (overlay == null) {
            return null;
        }
        // Page 2 already rebuilds row-2/3 key lists by latch state; avoid double remapping.
        if (isFixedTopRowsPage2StripKey(key)) {
            return null;
        }
        if (isFixedTopRowsFnDigitStripKey(key)) {
            if (effectivePage == 0) {
                return !fixedTopLocalFnLocked ? overlay : null;
            }
            return null;
        }
        if (effectivePage == 1 && isFixedTopRowsPage1FnOverlayKey(key)) {
            return fixedTopLocalFnLocked ? overlay : null;
        }
        return null;
    }

    /** Overlay target for fixed-row keys (independent of latch); null if key has no Fn-layer pair. */
    @Nullable
    private FnMapping resolveFixedTopOverlayMapping(Key key) {
        if (key == null || !isFixedTopRowKey(key)
                || isFixedTopLocalFnKey(key)
                || isTopModeSlotKey(key)) {
            return null;
        }
        FnMapping stripFn = rows23StripOverlayOverrideMapping(key);
        if (stripFn != null) {
            return stripFn;
        }
        int factoryScan = resolvedStripSlotFactoryScanCode(key);
        switch (factoryScan) {
            // Page 0: F1–F12 digit/symbol overlay (latch gating in {@link #resolveFixedTopLocalFnMapping}).
            case 0x41: return new FnMapping("8", 0x25, 0);
            case 0x42: return new FnMapping("9", 0x26, 0);
            case 0x43: return new FnMapping("0", 0x27, 0);
            case 0x44: return new FnMapping("+", 0x2E, MOD_SHIFT);
            case 0x45: return new FnMapping("-", 0x2D, 0);
            case 0x3A: return new FnMapping("1", 0x1E, 0);
            case 0x3B: return new FnMapping("2", 0x1F, 0);
            case 0x3C: return new FnMapping("3", 0x20, 0);
            case 0x3D: return new FnMapping("4", 0x21, 0);
            case 0x3E: return new FnMapping("5", 0x22, 0);
            case 0x3F: return new FnMapping("6", 0x23, 0);
            case 0x40: return new FnMapping("7", 0x24, 0);
            case 0x2E: return new FnMapping("*", 0x25, MOD_SHIFT);
            case KEY_IME_TOGGLE: return null;
            // Page 1: local Fn overlay when latch on (ESC / navigation row).
            case 0xE0: return new FnMapping("SCR LK", 0x47, 0);
            case 0xE2: return new FnMapping("PRT SC", 0x46, 0);
            case 0xE3: return new FnMapping("CAPS", 0x39, 0);
            case 0x2B: return new FnMapping("PAUSE", 0x48, 0);
            case 0x52: return new FnMapping("HOME", 0x4A, 0);
            case 0x28: return new FnMapping("PGUP", 0x4B, 0);
            case 0x29: return new FnMapping("SPACE", 0x2C, 0, R.drawable.space_bar_24px);
            case 0xE1: return new FnMapping("BKSP", 0x2A, 0, R.drawable.backspace_24);
            case 0x4C: return new FnMapping("DEL", 0x4C, 0, R.drawable.backspace_24);
            case 0x50: return new FnMapping("INS", 0x49, 0);
            case 0x51: return new FnMapping("END", 0x4D, 0);
            case 0x4F: return new FnMapping("PGDN", 0x4E, 0);
            // Fixed-top page 2: punctuation pairs by fixed slot (profile slots remain hint-suppressed).
            case 0x2F: return new FnMapping("~", 0x35, MOD_SHIFT); // [ -> ~
            case 0x30: return new FnMapping("'", 0x34, 0);         // ] -> '
            case 0x33: return new FnMapping("\"", 0x34, MOD_SHIFT); // : -> "
            case 0x20: return new FnMapping("%", 0x22, MOD_SHIFT); // # -> %
            case 0x1F: return new FnMapping("^", 0x23, MOD_SHIFT); // @ -> ^
            case 0x38:
                return key.requiresShift
                        ? new FnMapping("&", 0x24, MOD_SHIFT)      // ? -> &
                        : new FnMapping("<", 0x36, MOD_SHIFT);     // / -> <
            case 0x31:
                if (key.stripSlotPage == 2 && key.stripSlotRow == 3 && key.requiresShift) {
                    return new FnMapping("*", 0x25, MOD_SHIFT); // | -> *
                }
                return new FnMapping(">", 0x37, MOD_SHIFT); // \ -> >
            case 0x2D:
                return key.requiresShift
                        ? new FnMapping(".", 0x37, 0)              // _ -> .
                        : new FnMapping(",", 0x36, 0);             // - -> ,
            case 0x26:
                if (key.stripSlotPage == 2 && key.stripSlotRow == 2 && key.stripSlotCol == 0 && key.requiresShift) {
                    return new FnMapping("`", 0x35, 0);
                }
                return null;
            case 0x27:
                if (key.stripSlotPage == 2 && key.stripSlotRow == 2 && key.stripSlotCol == 1 && key.requiresShift) {
                    return new FnMapping("~", 0x35, MOD_SHIFT);
                }
                return null;
            case 0x35:
                // Page 2 row 2 Fn-on: physical ` / ~ — pair with ( / ) when latch off.
                if (key.stripSlotPage == 2 && key.stripSlotRow == 2 && key.stripSlotCol == 0 && !key.requiresShift) {
                    return new FnMapping("(", 0x26, MOD_SHIFT);
                }
                if (key.stripSlotPage == 2 && key.stripSlotRow == 2 && key.stripSlotCol == 1 && key.requiresShift) {
                    return new FnMapping(")", 0x27, MOD_SHIFT);
                }
                return new FnMapping("[", 0x2F, 0);         // [ key / legacy ~ slot -> [
            case 0x34:
                return key.requiresShift
                        ? new FnMapping(":", 0x33, MOD_SHIFT)      // " -> :
                        : new FnMapping("]", 0x30, 0);             // ' -> ]
            case 0x22: return new FnMapping("#", 0x20, MOD_SHIFT); // % -> #
            case 0x23: return new FnMapping("@", 0x1F, MOD_SHIFT); // ^ -> @
            case 0x36:
                return key.requiresShift
                        ? new FnMapping("/", 0x38, 0)              // < -> /
                        : new FnMapping("-", 0x2D, 0);             // , -> -
            case 0x37:
                return key.requiresShift
                        ? new FnMapping("\\", 0x31, 0)             // > -> \
                        : new FnMapping("_", 0x2D, MOD_SHIFT);     // . -> _
            case 0x25: return new FnMapping("|", 0x31, MOD_SHIFT); // * -> |
            case 0x24: return new FnMapping("?", 0x38, MOD_SHIFT); // & -> ?
            default:
                return null;
        }
    }

    private static boolean suppressFixedTopFnBaseCornerHintWhenLocalFnOn(int keyCode) {
        switch (keyCode) {
            case 0xE0:
            case 0xE2:
            case 0xE3:
            case 0x2B:
            case 0x52:
            case 0x28:
            case 0x50:
            case 0x51:
            case 0x4F:
                return true;
            default:
                return false;
        }
    }

    @Nullable
    private String resolveFixedTopLocalFnCornerHint(Key k,
            @Nullable FnMapping activeOverlay,
            boolean renderAsActionLabel,
            int panelPageIndex) {
        if (k == null || isFixedTopLocalFnKey(k)) {
            return null;
        }
        int effectivePage = (k.stripSlotPage >= 0) ? k.stripSlotPage : panelPageIndex;
        // Fixed strip page 1 (ESC/nav): no top-right local-Fn corner hints on its two rows (strip row 2
        // and row 3) — avoids redundant overlay labels (e.g. SCR LK / PRT SC / CAPS on modifiers).
        if (effectivePage == 1) {
            return null;
        }
        if (effectivePage == 2 && isPage2FixedPunctStripCornerSlot(k)) {
            String factory = FixedStripLayoutCatalog.page2LocalFnOppositeCornerHint(
                    k.stripSlotRow, k.stripSlotCol, fixedTopLocalFnLocked);
            if (!TextUtils.isEmpty(factory)) {
                return truncateFixedTopFnCornerHint(factory);
            }
            return null;
        }
        FnMapping overlay = resolveFixedTopOverlayMapping(k);
        if (overlay == null) {
            return null;
        }
        if (k.code == 0x29 && activeOverlay == null) {
            return null;
        }
        if (activeOverlay != null && suppressFixedTopFnBaseCornerHintWhenLocalFnOn(
                resolvedStripSlotFactoryScanCode(k))) {
            return null;
        }
        String raw = activeOverlay != null
                ? fixedTopFnBaseLegendForHint(k, renderAsActionLabel)
                : fixedTopFnOverlayLegendForHint(overlay, k);
        String hint = truncateFixedTopFnCornerHint(raw);
        return TextUtils.isEmpty(hint) ? null : hint;
    }

    private String fixedTopFnOverlayLegendForHint(FnMapping overlay, Key key) {
        if (!TextUtils.isEmpty(overlay.label)) {
            return overlay.label;
        }
        return "";
    }

    private String fixedTopFnBaseLegendForHint(Key k, boolean renderAsActionLabel) {
        if (renderAsActionLabel && isTopShortcutActionLabelEligible(k)) {
            String s = formatTopShortcutActionLabel(k);
            if (!TextUtils.isEmpty(s)) {
                return s;
            }
        }
        if (!TextUtils.isEmpty(k.label)) {
            String lbl = k.label.trim();
            int nl = lbl.indexOf('\n');
            if (nl >= 0) {
                return lbl.substring(0, nl).trim();
            }
            return lbl;
        }
        return k.symbolLabel != null ? k.symbolLabel.trim() : "";
    }

    private static String truncateFixedTopFnCornerHint(@Nullable String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= TOP_FIXED_ROWS_FN_HINT_MAX_CHARS) {
            return t;
        }
        return t.substring(0, TOP_FIXED_ROWS_FN_HINT_MAX_CHARS - 1) + "\u2026";
    }

    /** Wraps {@code inner} when hint non-empty; {@code inner} must use {@link FrameLayout.LayoutParams} filling the wrap. */
    private void addFixedRowCellWithOptionalFnHint(
            LinearLayout rowLayout,
            LinearLayout.LayoutParams outerCellParams,
            View inner,
            @Nullable String fnCornerHint) {
        if (TextUtils.isEmpty(fnCornerHint)) {
            inner.setLayoutParams(outerCellParams);
            rowLayout.addView(inner);
            return;
        }
        FrameLayout wrap = new FrameLayout(getContext());
        wrap.setLayoutParams(outerCellParams);
        wrap.addView(inner);
        TextView hintTv = new TextView(getContext());
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        hp.setMargins(0, dpToPx(2), dpToPx(4), 0);
        hintTv.setLayoutParams(hp);
        hintTv.setText(fnCornerHint);
        hintTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TOP_FIXED_ROWS_FN_HINT_SP);
        hintTv.setTextColor(resolveThemeTextColor());
        hintTv.setAlpha(TOP_FIXED_ROWS_FN_HINT_ALPHA);
        hintTv.setTypeface(hintTv.getTypeface(), Typeface.BOLD);
        hintTv.setIncludeFontPadding(false);
        hintTv.setBackgroundColor(Color.TRANSPARENT);
        hintTv.setPadding(0, 0, 0, 0);
        hintTv.setClickable(false);
        hintTv.setFocusable(false);
        hintTv.setSingleLine(true);
        hintTv.setMaxLines(1);
        hintTv.setEllipsize(TextUtils.TruncateAt.END);
        wrap.addView(hintTv);
        hintTv.bringToFront();
        rowLayout.addView(wrap);
    }

    private void refreshExtraNumpadGridUi() {
        if (extraNumpadGrid == null) {
            return;
        }
        if (extraNumpadFnButton != null) {
            extraNumpadFnButton.setSelected(extraNumpadFnLocked);
            extraNumpadFnButton.setBackgroundResource(extraNumpadFnLocked
                    ? R.drawable.press_button_background
                    : R.drawable.function_button_background);
        }
        for (int i = 0; i < extraNumpadGrid.getChildCount(); i++) {
            refreshExtraNumpadGridUiForView(extraNumpadGrid.getChildAt(i));
        }
    }

    private void refreshExtraNumpadGridUiForView(View child) {
        if (child == null) {
            return;
        }
        Object tag = child.getTag(R.id.tag_custom_keyboard_extra_cell_key);
        if (tag instanceof Key) {
            Key baseKey = (Key) tag;
            if (baseKey.code == KEY_EXTRA_NUMPAD_FN) {
                return;
            }
            FnMapping mapping = resolveExtraNumpadFnMapping(baseKey);
            if (child instanceof ImageButton) {
                ImageButton ib = (ImageButton) child;
                if (mapping != null && mapping.iconResId != 0) {
                    ib.setImageResource(mapping.iconResId);
                    ib.setColorFilter(resolveThemeTextColor());
                } else if (baseKey.iconResId != 0) {
                    ib.setImageResource(baseKey.iconResId);
                    ib.setColorFilter(resolveThemeTextColor());
                }
                if (baseKey.code == 0x2A) {
                    boolean forwardDel = mapping != null && mapping.keyCode == 0x4C;
                    ib.setScaleX(forwardDel ? -1f : 1f);
                }
            } else if (child instanceof Button) {
                Button b = (Button) child;
                if (mapping != null) {
                    if (mapping.iconResId != 0) {
                        boolean isDelBackspaceIcon = "DEL".equals(mapping.label) && mapping.iconResId == R.drawable.backspace_24;
                        int iconDp = isDelBackspaceIcon
                                ? 20
                                : isExtraNumpadArrowDirectionLabel(mapping.label)
                                ? EXTRA_NUMPAD_FN_ARROW_ICON_DP
                                : EXTRA_NUMPAD_FN_ACTION_ICON_DP;
                        applyExtraNumpadGridIconOverlay(b, mapping.iconResId, mapping.label, iconDp);
                        if (isDelBackspaceIcon) {
                            int iconPad = dpToPx(6);
                            b.setPadding(iconPad, iconPad, iconPad, iconPad);
                        }
                        b.setScaleX(mapping.keyCode == 0x4C ? -1f : 1f);
                    } else {
                        clearExtraNumpadArrowIconOverlay(b);
                        b.setContentDescription(null);
                        b.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        b.setCompoundDrawablePadding(0);
                        int pad = dpToPx(2);
                        b.setPadding(pad, pad, pad, pad);
                        b.setText(mapping.label);
                        b.setTextColor(resolveExtraNumpadLabelColor(mapping.label));
                        styleExtraNumpadGridKeyButton(b, mapping.label);
                        b.setScaleX(1f);
                    }
                } else {
                    clearExtraNumpadArrowIconOverlay(b);
                    b.setContentDescription(null);
                    b.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    b.setCompoundDrawablePadding(0);
                    int pad = dpToPx(2);
                    b.setPadding(pad, pad, pad, pad);
                    b.setScaleX(1f);
                    if ("TAB".equals(baseKey.label) && baseKey.code == 0x2B) {
                        applyExtraNumpadGridIconOverlay(b, R.drawable.keyboard_tab_24, "Tab",
                                EXTRA_NUMPAD_FN_ACTION_ICON_DP);
                    } else if ("Space".equals(baseKey.label) && baseKey.code == 0x2C) {
                        applyExtraNumpadGridIconOverlay(b, R.drawable.space_bar_24px, "Space",
                                EXTRA_NUMPAD_FN_ACTION_ICON_DP);
                    } else {
                        b.setText(baseKey.label);
                        b.setTextColor(resolveExtraNumpadLabelColor(baseKey.label));
                        styleExtraNumpadGridKeyButton(b, baseKey.label);
                    }
                }
            }
            return;
        }
        if (child instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) child;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshExtraNumpadGridUiForView(group.getChildAt(i));
            }
        }
    }

    public static byte[] hexStringToByteArray(String ByteData) {
        return Ch9329PacketUtil.hexStringToByteArray(ByteData);
    }

    public void setPort(UsbSerialPort port) {
        this.port = port;
        if (port == null) {
            clearProChordAndHoldLockPopupUiState();
        }
        updateTransport();
        Log.v(TAG, "Port set in CustomKeyboardView: " + (port != null ? "Valid" : "Null"));
    }

    /** Rebuild transport from current port / BT state. Called when hardware state changes. */
    private void updateTransport() {
        // If a custom transport was set (e.g., TerminalKeyboardTransport for terminal mode),
        // don't override it with the default HID transport.
        if (useCustomTransport) {
            return;
        }
        this.transport = new HidKeyboardTransport(
                getContext(), port, bluetoothService, isServiceBound);
    }

    /**
     * Set a custom transport (e.g. TerminalKeyboardTransport for terminal mode).
     * Overrides the auto-created HidKeyboardTransport.
     */
    public void setTransport(KeyboardTransport t) {
        this.transport = t;
        this.useCustomTransport = true;
    }

    /**
     * Clear the custom transport and revert to the default HID transport.
     * Called when exiting terminal mode to restore normal keyboard behavior.
     * After this call, updateTransport() will rebuild HidKeyboardTransport
     * from current port / BT state on next hardware state change.
     */
    public void clearCustomTransport() {
        this.useCustomTransport = false;
        this.transport = null;
        // Rebuild the default HID transport from current hardware state
        updateTransport();
    }

    /**
     * Toggle Ctrl lock state programmatically.
     * Updates internal state, refreshes key visuals, and syncs to split partner.
     * Used by TerminalFragment's bottom bar Ctrl button.
     */
    public void toggleCtrlLock() {
        isCtrlLeftLocked = !isCtrlLeftLocked;
        syncModifierStates();
        updateKeyboard();
        refreshVisibleTopPanelButtonStates();
    }

    /**
     * Query current Ctrl lock state.
     * Used by TerminalFragment to sync the bottom bar Ctrl button visual.
     */
    public boolean isCtrlLocked() {
        return isCtrlLeftLocked;
    }

    public static String makeChecksum(String data) {
        return Ch9329PacketUtil.makeChecksum(data);
    }

    public void sendReleaseData() {
        repeatHandler.removeCallbacks(keyboardTapReleaseRunnable);
        repeatHandler.removeCallbacks(gamingTapReleaseRunnable);
        logKmProTouch(
                "sendReleaseData usbPort="
                        + (port != null)
                        + " btBound="
                        + isServiceBound
                        + " btConn="
                        + (bluetoothService != null && bluetoothService.isConnected()));
        if (transport != null && transport.isConnected()) {
            transport.sendAllKeysReleased();
        }
        Log.v(TAG, "Sent keyboard release (all keys)");
        post(this::reassertKeyboardAfterHidRelease);
    }

    /** Same as {@link #scheduleKeyboardTapRelease(long)} with the default post-tap delay. */
    private void scheduleKeyboardTapRelease() {
        scheduleKeyboardTapRelease(30L);
    }

    /**
     * Cancels any pending coalesced or gaming delayed release, then schedules one all-keys-released
     * (mirrors KM Basic {@code scheduleReleaseAfterPhysicalKey}).
     */
    private void scheduleKeyboardTapRelease(long delayMs) {
        repeatHandler.removeCallbacks(gamingTapReleaseRunnable);
        repeatHandler.removeCallbacks(keyboardTapReleaseRunnable);
        logKmProTouch("scheduleKeyboardTapRelease delayMs=" + delayMs);
        repeatHandler.postDelayed(keyboardTapReleaseRunnable, delayMs);
    }

    private int parseHex(String hex) {
        return hex != null ? Integer.parseInt(hex.replace("0x", ""), 16) : 0;
    }

    private int resolveExtraNumpadLabelColor(String label) {
        // NUM has two visual states on local Fn layer:
        // State A -> normal text color (black light / white dark)
        // State B -> current theme accent color.
        if ("NUM".equals(label) && extraNumpadNumStateB) {
            return ThemeManager.getColorPrimary(getContext());
        }
        return resolveThemeTextColor();
    }

    private void handleKeyPress(Key key) {
        Log.v(TAG, "Key pressed: label=" + key.label + ", code=" + key.code);
        logKmProTouch(
                "handleKeyPress enter label="
                        + (key != null ? key.label : "null")
                        + " code=0x"
                        + (key != null ? Integer.toHexString(key.code) : "0"));

        int topSlot = topModeSlotIndexFromKeyCode(key.code);
        if (topSlot > 0) {
            Context ctx = getContext();
            if (ctx != null && onTopModeShortcutListener != null) {
                String mode = TopModeShortcutPrefs.getModeForSlot(ctx, topSlot);
                onTopModeShortcutListener.onRequestSwitchToMode(mode);
            }
            return;
        }

        if (isTopShortcutToggleKey(key)) {
            cycleTopShortcutDisplayMode();
            return;
        }

        if (key.code == KEY_IME_TOGGLE) {
            if (onKmProSecondaryLayoutToggleListener != null) {
                onKmProSecondaryLayoutToggleListener.onRequestToggle(this);
            }
            return;
        }

        if (key.code == KEY_NOOP_PLACEHOLDER) {
            return;
        }

        if (key.code == KEY_TOP_STRIP_CREATE_SHORTCUT) {
            showCreateShortcutFromStrip();
            return;
        }

        if (key.code == KEY_EXTRA_NUMPAD_FN) {
            extraNumpadFnLocked = !extraNumpadFnLocked;
            refreshExtraNumpadGridUi();
            if (splitPartner != null) {
                splitPartner.extraNumpadFnLocked = extraNumpadFnLocked;
                splitPartner.refreshExtraNumpadGridUi();
            }
            return;
        }

        if (key.code == KEY_FIXED_TOP_LOCAL_FN) {
            fixedTopLocalFnLocked = !fixedTopLocalFnLocked;
            rebuildFixedTopRowsPanels();
            rebuildTopShortcutPanels();
            syncTopPanelViewportContent();
            // Modifier lock flags (Ctrl/Shift/Alt/Win) are unchanged here — only Fn-layer and keycap
            // labels refresh; re-apply top-strip pressed visuals for icon keys.
            refreshVisibleTopPanelButtonStates();
            if (splitPartner != null) {
                splitPartner.fixedTopLocalFnLocked = fixedTopLocalFnLocked;
                splitPartner.rebuildFixedTopRowsPanels();
                splitPartner.rebuildTopShortcutPanels();
                splitPartner.syncTopPanelViewportContent();
                splitPartner.refreshVisibleTopPanelButtonStates();
            }
            return;
        }

        // Local extra numpad State A/B toggle for NUM key (displayed as Fn+ESC).
        if (extraNumpadFnLocked && "ESC".equals(key.label) && key.code == 0x29) {
            extraNumpadNumStateB = !extraNumpadNumStateB;
            refreshExtraNumpadGridUi();
            if (splitPartner != null) {
                splitPartner.extraNumpadNumStateB = extraNumpadNumStateB;
                splitPartner.refreshExtraNumpadGridUi();
            }
        }

        // Fn latched: main keyboard left Shift toggles long-press alternates/hints (before shortcut path).
        if (isFnAlternateHintsToggleKey(key)) {
            toggleKeyboardAlternatesHintsFromUser();
            return;
        }

        // QWERTY-row Ctrl/Shift/Alt/Win: dedicated touch listener (sticky / chord + hold-lock).
        if (isProBuiltInModifierTouchKey(key)) {
            return;
        }

        // Top-strip Ctrl/Shift/Alt/Win use {@code shortcutModifiers == 0} in {@link #buildTopPanelModifierKey};
        // they must not take the profile-shortcut path — handled by touch listeners + switch below.
        if (key.shortcutModifiers >= 0 && !isTopModifierLockCandidate(key)) {
            FnMapping fnOverride = extraNumpadFnLocked ? resolveExtraNumpadFnMapping(key) : null;
            if (fnOverride == null) {
                if (key.unicodeCodePoint != 0) {
                    sendStripUnicodeShortcut(key.unicodeCodePoint);
                } else {
                    sendShortcutWithModifiers(key.shortcutModifiers, key.code);
                }
                return;
            }
            // Local Fn overrides row-2 # (→|) and Tab cell (→Save), etc. — continue to merged HID send below.
        }

        boolean updateRequired = false;
        final boolean topStripModifierTap =
                key.isTopPanelKey && isTopModifierLockCandidate(key);
        switch (key.code) {
            case KEY_MODE_FN:
                isFnLocked = !isFnLocked;
                syncFnLockedToPartner();
                updateRequired = true;
                break;
            case 0xE1: // Shift Left
                if (!topStripModifierTap) {
                    isShiftLeftLocked = !isShiftLeftLocked;
                    syncModifierStates();
                    updateRequired = true;
                }
                break;
            case 0xE0: // Ctrl Left
                if (!topStripModifierTap) {
                    isCtrlLeftLocked = !isCtrlLeftLocked;
                    syncModifierStates();
                    updateRequired = true;
                }
                break;
            case 0xE2: // Alt Left
                if (!topStripModifierTap) {
                    isAltLeftLocked = !isAltLeftLocked;
                    syncModifierStates();
                    updateRequired = true;
                }
                break;
            case 0xE3: // Win Left
                if (!topStripModifierTap) {
                    isWinLeftLocked = !isWinLeftLocked;
                    syncModifierStates();
                    updateRequired = true;
                }
                break;
        }

        if (updateRequired) {
            if (key.isTopPanelKey && (key.code == 0xE0 || key.code == 0xE1 || key.code == 0xE2 || key.code == 0xE3)) {
                refreshVisibleTopPanelButtonStates();
            } else {
                updateKeyboard();
            }
        }

        if (key.code == KEY_MODE_FN) {
            return;
        }
        if (key.code == 0xE0 || key.code == 0xE1 || key.code == 0xE2 || key.code == 0xE3) {
            FnMapping stripFnOverlay = resolveFixedTopLocalFnMapping(key);
            if (stripFnOverlay == null || isMacCapsMomentaryFromTopStripModifier(key)) {
                return;
            }
        }

        sendHidKeyDataForKey(key);
    }

    /**
     * Sends one HID keyboard report for {@code key} (Fn layers, locked modifiers, extra numpad),
     * with no profile/mode/UI side effects.
     */
    /**
     * One logical keystroke for gaming repeat: HID key report then all-keys-released after a short delay.
     * Without the release, repeated {@link #sendHidKeyDataForKey} calls keep the same key down and the
     * host does not see separate presses (unlike KM Basic {@code tapKey}).
     */
    private void sendHidKeyTapForGamingRepeat(Key key) {
        repeatHandler.removeCallbacks(keyboardTapReleaseRunnable);
        repeatHandler.removeCallbacks(gamingTapReleaseRunnable);
        logKmProTouch(
                "sendHidKeyTapForGamingRepeat key="
                        + (key != null ? key.label : "null")
                        + " code=0x"
                        + (key != null ? Integer.toHexString(key.code) : "0"));
        sendHidKeyDataForKey(key);
        repeatHandler.postDelayed(gamingTapReleaseRunnable, 30);
    }

    private void sendHidKeyDataForKey(Key key) {
        if (key == null) {
            return;
        }
        FnMapping fixedTopLocalFn = resolveFixedTopLocalFnMapping(key);
        FnMapping extraNumpadFn = resolveExtraNumpadFnMapping(key);
        FnMapping fnMapping = fixedTopLocalFn != null
                ? fixedTopLocalFn
                : (extraNumpadFn != null ? extraNumpadFn : resolveFnMapping(key));
        int effectiveKeyCode = fnMapping != null ? fnMapping.keyCode : key.code;
        int fnModifierMask = fnMapping != null ? fnMapping.modifierMask : 0;
        // Forward delete: Fn+Backspace (layout overlay) or Shift+Backspace (shift cap shows Del).
        boolean backspaceForwardDelete = isBackspaceKey(key) && (isFnLocked || isShiftLeftLocked);
        if (backspaceForwardDelete) {
            effectiveKeyCode = 0x4C;
        }
        boolean effectiveShiftLocked = isShiftLeftLocked && !backspaceForwardDelete;

        int combinedValue = 0;
        combinedValue += isCtrlLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl")) : 0;
        combinedValue += effectiveShiftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift")) : 0;
        combinedValue += isAltLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt")) : 0;
        combinedValue += isWinLeftLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("Win")) : 0;
        combinedValue += isCtrlRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("CtrlR")) : 0;
        combinedValue += isAltRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("AltR")) : 0;
        combinedValue += isWinRightLocked ? parseHex(CH9329MSKBMap.KBShortCutKey().get("WinR")) : 0;
        // Extra numpad Fn mapping replaces the keycap meaning (e.g. Tab→Save); do not add base requiresShift
        // or we would send Shift+Tab instead of Tab, Shift+# alongside Fn modifiers, etc.
        if (key.requiresShift && extraNumpadFn == null) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((fnModifierMask & 0x01) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((fnModifierMask & 0x02) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((fnModifierMask & 0x04) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((fnModifierMask & 0x08) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }

        if (extraNumpadFn != null && "000".equals(extraNumpadFn.label)) {
            sendExtraNumpadTripleNumpadZero(combinedValue);
            return;
        }

        sendKeyData(combinedValue, effectiveKeyCode);
    }

    private void startGamingKeyRepeat(final Key key) {
        stopGamingKeyRepeat();
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        gamingRepeatKey = key;
        gamingRepeatActive = true;
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        final int initialDelay = vc.getKeyRepeatTimeout();
        final int repeatDelay = vc.getKeyRepeatDelay();
        gamingRepeatStarterRunnable = () -> {
            if (!gamingRepeatActive || gamingRepeatKey != key) {
                return;
            }
            gamingRepeatRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!gamingRepeatActive || gamingRepeatKey != key) {
                        return;
                    }
                    sendHidKeyTapForGamingRepeat(key);
                    repeatHandler.postDelayed(this, repeatDelay);
                }
            };
            repeatHandler.post(gamingRepeatRunnable);
        };
        repeatHandler.postDelayed(gamingRepeatStarterRunnable, initialDelay);
    }

    private void stopGamingKeyRepeat() {
        gamingRepeatActive = false;
        gamingRepeatKey = null;
        if (gamingRepeatStarterRunnable != null) {
            repeatHandler.removeCallbacks(gamingRepeatStarterRunnable);
            gamingRepeatStarterRunnable = null;
        }
        if (gamingRepeatRunnable != null) {
            repeatHandler.removeCallbacks(gamingRepeatRunnable);
            gamingRepeatRunnable = null;
        }
        repeatHandler.removeCallbacks(gamingTapReleaseRunnable);
    }

    /** Fn-layer numpad 0 → three keypad-zero presses (with release between each). */
    private void sendExtraNumpadTripleNumpadZero(int modifiers) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    sendKeyData(modifiers, 0x62);
                    Thread.sleep(40);
                    sendKeyboardAllKeysReleasedSync();
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "extraNumpadTriple0").start();
    }

    private void sendKeyboardAllKeysReleasedSync() {
        if (transport != null && transport.isConnected()) {
            transport.sendAllKeysReleased();
        }
    }

    private boolean isBackspaceKey(Key key) {
        return key != null && (key.code == 0x2A || "BackSpace".equals(key.label));
    }

    private void sendShortcutWithModifiers(int shortcutModifiers, int keyCode) {
        int combinedValue = 0;
        if ((shortcutModifiers & MOD_CTRL) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Ctrl"));
        }
        if ((shortcutModifiers & MOD_SHIFT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Shift"));
        }
        if ((shortcutModifiers & MOD_ALT) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Alt"));
        }
        if ((shortcutModifiers & MOD_WIN) != 0) {
            combinedValue |= parseHex(CH9329MSKBMap.KBShortCutKey().get("Win"));
        }
        sendKeyData(combinedValue, keyCode);
    }

    /**
     * Strip-cap dispatch for Rows 2–3 shortcuts whose
     * {@link com.openterface.keymod.ShortcutProfileManager.Shortcut#unicodeCodePoint} is set:
     * forwards the BMP code point to the host through {@link HidTextKeystrokeSender} on the
     * shared {@link #imeTextExecutor} thread (mirrors {@link #sendAlternateOption}'s Unicode
     * branch). Requires Unicode Hex Input enabled on the host (Mac layout / Windows
     * EnableHexNumpad / Linux IBus); silently no-ops if there is no active connection.
     */
    private void sendStripUnicodeShortcut(int codePoint) {
        if (codePoint == 0) {
            return;
        }
        ConnectionManager cm = peekConnectionManager();
        if (cm == null) {
            Log.w(TAG, "Unicode strip shortcut needs ConnectionManager; skipping U+"
                    + Integer.toHexString(codePoint));
            return;
        }
        String targetOs = getTargetOs();
        String ch = new String(Character.toChars(codePoint));
        imeTextExecutor.execute(() -> {
            try {
                HidTextKeystrokeSender.send(ch, cm, targetOs, true, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void sendKeyData(int modifiers, int keyCode) {
        int m = mergeHoldLockedBootMask(modifiers);
        m = mergeChordHeldBootMask(m);
        logKmProTouch(
                "sendKeyData mod=0x"
                        + Integer.toHexString(m)
                        + " key=0x"
                        + Integer.toHexString(keyCode)
                        + " usb="
                        + (port != null)
                        + " btBound="
                        + isServiceBound
                        + " btConn="
                        + (bluetoothService != null && bluetoothService.isConnected()));
        if (transport != null && transport.isConnected()) {
            transport.sendKey(m, keyCode);
        }
    }

    /**
     * Begins hold-to-repeat using system key-repeat timing; each tick sends a tap-like HID pair
     * so the host sees distinct presses (same idea as {@link #sendHidKeyTapForGamingRepeat}).
     */
    private void startHoldKeyRepeatFromDown(Key key) {
        Context ctx = getContext();
        if (key == null || ctx == null) {
            return;
        }
        stopGamingKeyRepeat();
        stopHoldKeyRepeatOnly();
        holdKeyRepeatKey = key;
        holdKeyRepeatActive = true;
        holdRepeatSuppressUpTap = false;
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        final int initialDelay = vc.getKeyRepeatTimeout();
        final int repeatDelay = Math.max(vc.getKeyRepeatDelay(), 50);
        holdKeyRepeatStarterRunnable = () -> {
            if (!holdKeyRepeatActive || holdKeyRepeatKey != key) {
                return;
            }
            holdKeyRepeatStarterRunnable = null;
            holdKeyRepeatRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!holdKeyRepeatActive || holdKeyRepeatKey != key) {
                        return;
                    }
                    sendHidKeyTapForGamingRepeat(key);
                    holdRepeatSuppressUpTap = true;
                    repeatHandler.postDelayed(this, repeatDelay);
                }
            };
            repeatHandler.post(holdKeyRepeatRunnable);
        };
        repeatHandler.postDelayed(holdKeyRepeatStarterRunnable, initialDelay);
    }

    /** Stops hold-repeat runnables without clearing {@link #holdRepeatSuppressUpTap} (caller reads it first on UP). */
    private void stopHoldKeyRepeatOnly() {
        holdKeyRepeatActive = false;
        holdKeyRepeatKey = null;
        if (holdKeyRepeatStarterRunnable != null) {
            repeatHandler.removeCallbacks(holdKeyRepeatStarterRunnable);
            holdKeyRepeatStarterRunnable = null;
        }
        if (holdKeyRepeatRunnable != null) {
            repeatHandler.removeCallbacks(holdKeyRepeatRunnable);
            holdKeyRepeatRunnable = null;
        }
        repeatHandler.removeCallbacks(gamingTapReleaseRunnable);
    }

    private void stopRepeatingDelete() {
        stopHoldKeyRepeatOnly();
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void applyFlatKeyStyle(View view) {
        if (view == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setStateListAnimator(null);
            view.setElevation(0f);
            view.setTranslationZ(0f);
        }
    }

    private int resolveThemeTextColor() {
        Context ctx = getContext();
        if (ctx == null) {
            return 0xFF212121;
        }
        return ContextCompat.getColor(ctx, R.color.text_primary);
    }



    @Nullable
    private ConnectionManager peekConnectionManager() {
        AppCompatActivity act = unwrapAppCompatActivity(getContext());
        if (act instanceof MainActivity) {
            return ((MainActivity) act).getConnectionManager();
        }
        return null;
    }

    private void detachLocalImeFieldQuiet() {
        // KM Pro IME capture removed; no-op for layout lifecycle.
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context ctx = getContext();
        if (ctx != null) {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                    .registerOnSharedPreferenceChangeListener(kmProModifierPrefListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDeferredKmProLayoutFinish();
        Context ctx = getContext();
        if (ctx != null) {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                    .unregisterOnSharedPreferenceChangeListener(kmProModifierPrefListener);
        }
        super.onDetachedFromWindow();
        clearSplitLandscapeImeComposeRail();
        detachLocalImeFieldQuiet();
        longPressHandler.removeCallbacksAndMessages(null);
        stopRepeatingDelete();
        dismissAlternatesPopup();
        kmProKeyPreview.dismiss();
        if (isServiceBound) {
            getContext().unbindService(serviceConnection);
            isServiceBound = false;
            Log.v(TAG, "Unbound from BluetoothService");
        }
    }
}