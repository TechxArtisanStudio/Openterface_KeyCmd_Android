package com.openterface.keymod;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.android.material.color.MaterialColors;

import com.openterface.keymod.GamepadConfigManager.ComponentPosition;
import com.openterface.keymod.gamepad.GamepadCanvasBackgroundPreview;
import com.openterface.keymod.gamepad.GamepadCapLabels;
import com.openterface.keymod.gamepad.GamepadDpadVariantArt;
import com.openterface.keymod.gamepad.GamepadGestureLock;
import com.openterface.keymod.gamepad.GamepadGestureLockSensitivity;
import com.openterface.keymod.gamepad.GamepadLayoutDocEditor;
import com.openterface.keymod.gamepad.GamepadLayoutPresetConstants;
import com.openterface.keymod.gamepad.GamepadLayoutPresetDocument;
import com.openterface.keymod.gamepad.GamepadModuleAccent;
import com.openterface.keymod.gamepad.GamepadStickVisualArt;
import com.openterface.keymod.gamepad.render.GamepadDynamicLayoutRegistry;
import com.openterface.keymod.basic.BasicHoldLockPopup;

/**
 * Gamepad View - Custom view for rendering and interacting with gamepad components
 * Supports Xbox, PlayStation, and NES layouts
 * Draggable components in edit mode
 */
public class GamepadView extends View {

    private static final String TAG = "GamepadView";

    /** On-canvas latch badge: keyboard hold-lock vs turbo gesture latch. */
    public enum LatchBadgeKind {
        HOLD,
        TURBO
    }

    /** Centered hold / turbo badge on latched modules (drawn in {@link #drawKeyboardHoldLockBadgeIfNeeded}). */
    private static final float LATCH_BADGE_MIN_DP = 32f;
    private static final float LATCH_BADGE_MAX_DP = 56f;
    private static final float LATCH_BADGE_FRACTION_OF_MIN_SIDE = 0.52f;
    private static final int LATCH_BADGE_DRAWABLE_ALPHA = 210;

    // Paint objects
    private Paint bgPaint;
    private Paint buttonPaint;
    private Paint textPaint;
    private Paint stickPaint;
    private Paint dpadPaint;

    // Current layout
    private GamepadLayout currentLayout;
    private Map<String, GamepadConfigManager.ComponentPosition> componentPositions;
    private GamepadConfigManager configManager;

    // Interaction
    private boolean isEditMode = false;
    private String draggedComponentId = null;
    /** Pointer that started an in-progress edit drag (ACTION_MOVE must follow this finger). */
    private int dragPointerId = -1;
    private String pressedComponentId = null;
    private ButtonPressListener buttonPressListener;
    private AnalogStickListener analogStickListener;

    // Multi-touch tracking for D-pad hold behavior
    private Map<Integer, String> pointerComponents = new HashMap<>(); // pointerId -> componentId
    private Set<String> dpadPressedSet = new HashSet<>(); // currently held D-pad components
    private DpadStateListener dpadStateListener;
    private Set<String> buttonsPressedSet = new HashSet<>(); // currently held non-D-pad buttons
    private ButtonReleaseListener buttonReleaseListener;

    // Analog stick drag state
    private String  activeStickId      = null;
    private int     activeStickPointerId = -1;  // pointerId of the finger on the stick
    private float   activeStickCenterX = 0;
    private float   activeStickCenterY = 0;
    private float   activeStickRadius  = 0;
    private float   stickOffsetX       = 0;
    private float   stickOffsetY       = 0;

    // Long press detection (defaults; override via {@link #setEditLongPressConfig})
    private long longPressThresholdMs = 600L;
    private float longPressMoveThresholdPx = 15f;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private String longPressComponentId = null;
    private float longPressDownX = 0;
    private float longPressDownY = 0;
    private boolean longPressCancelled = false;
    private ComponentLongPressListener longPressListener;
    /** Dynamic edit mode: tap target at top-right of module outline; opens module config (replaces long-press there). */
    private ModuleConfigEditTapListener moduleConfigEditTapListener;

    /** Cancel hold-lock tracking if finger drifts this far before the popup is shown (popup is posted immediately). */
    private static final float KEYBOARD_HOLD_LOCK_CANCEL_MOVE_DP = 52f;
    private final Handler keyboardHoldLockHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private KeyboardHoldLockListener keyboardHoldLockListener;
    private final Map<Integer, HoldLockTracking> keyboardHoldLockByPointer = new HashMap<>();
    private final Map<String, LatchBadgeKind> gestureLatchBadgesByModuleId = new HashMap<>();
    /** From prefs / preset; diagonal latch commits only after this dwell (see {@link #setGestureLockCommitSensitivity}). */
    private int gestureLockMinPressMs;
    /** Scales {@link GamepadGestureLock} diagonal min and cancel radii. */
    private float gestureLockDiagonalRadiusScale = 1f;

    private static final class HoldLockTracking {
        @NonNull final String moduleId;
        final float downRawX;
        final float downRawY;
        /** {@link MotionEvent#getEventTime()} sample when this pointer went down on the module. */
        final long downEventTimeMs;
        float lastRawX;
        float lastRawY;
        final boolean diagonalMode;
        @NonNull final Runnable showPopupRunnable;
        @Nullable BasicHoldLockPopup popup;

        HoldLockTracking(
                @NonNull String moduleId,
                float downRawX,
                float downRawY,
                long downEventTimeMs,
                boolean diagonalMode,
                @NonNull Runnable showPopupRunnable) {
            this.moduleId = moduleId;
            this.downRawX = downRawX;
            this.downRawY = downRawY;
            this.downEventTimeMs = downEventTimeMs;
            this.lastRawX = downRawX;
            this.lastRawY = downRawY;
            this.diagonalMode = diagonalMode;
            this.showPopupRunnable = showPopupRunnable;
        }
    }

    // Component bounds (for touch detection)
    private Map<String, RectF> componentBounds;

    // Disabled components (visual only, no touch response)
    private Map<String, Boolean> disabledComponents = new HashMap<>();

    // Display labels for components (can differ from default hardcoded labels)
    private Map<String, String> componentDisplayLabels = new HashMap<>();

    // Configurable keycode for the main button (button_a)
    private int buttonAKeyCode = 40; // default: HID Enter
    private boolean showTwoButtons = false;
    /** Default false: fragment enables with Edit toggle; avoids config menus in play until sync. */
    private boolean longPressEnabled = false;

    /**
     * When false, hide automatic mapping hints (callouts, stick direction letters, D-pad letters,
     * touchpad title). Preset {@code displayLabel} on caps is unaffected.
     */
    private boolean keyMappingHintsVisible = true;

    // Button size scale (0.5 = 50%, 1.0 = 100%, 2.0 = 200%)
    private float buttonSizeScale = 1.0f;
    private float stickSizeScale = 1.0f;

    // Currently active stick directions (for highlighting labels)
    private Set<String> activeStickDirections = new java.util.HashSet<>();

    // Background image
    private android.graphics.Bitmap backgroundBitmap = null;
    /** Null = no custom fill (use default gradient when no bitmap). */
    @Nullable private Integer backgroundFillArgb = null;
    /** Canonical pattern id (e.g. {@link GamepadLayoutPresetConstants#BACKGROUND_PATTERN_DOTS}); null = none. */
    @Nullable private String backgroundPatternId = null;
    private Paint patternOverlayPaint;
    private Runnable onBackgroundChanged;

    /** Openterface wordmark on the background plane (drawn under sticks/buttons). */
    private Drawable brandWatermarkDrawable;
    /** When true, re-sample background luminance for adaptive wordmark tint/alpha. */
    private boolean brandWatermarkContrastDirty = true;
    private int cachedBrandWatermarkTint = Color.GRAY;
    private int cachedBrandWatermarkAlpha = 130;
    private static final double BRAND_WATERMARK_LUMA_THRESHOLD = 0.45;
    /** Dark glyph on light canvas (ARGB). */
    private static final int BRAND_WATERMARK_TINT_ON_LIGHT_BG = 0xDE1A1C22;
    /** Light glyph on dark canvas (ARGB). */
    private static final int BRAND_WATERMARK_TINT_ON_DARK_BG = 0xD9F5F6F8;

    // Background viewport (pan and zoom)
    private float bgScale = 1.0f;
    private float bgOffsetX = 0f;
    private float bgOffsetY = 0f;
    private float bgInitialScale = 1.0f;

    // Two-finger background manipulation
    private boolean isManipulatingBg = false;
    private float bgLastDistance = -1f;
    private float bgLastCenterX = 0f;
    private float bgLastCenterY = 0f;
    private float bgStartOffsetX = 0f;
    private float bgStartOffsetY = 0f;
    private float bgStartScale = 1.0f;

    /**
     * Insets reserved around the dynamic layout coordinate system so modules are not drawn under
     * system bars (e.g. gesture nav in landscape). Anchors stay 0–1 within the inner content rect.
     */
    private int contentInsetLeft;
    private int contentInsetTop;
    private int contentInsetRight;
    private int contentInsetBottom;

    /** Schema-driven SIMPLE layout (v2); when non-null, replaces fixed drawSimpleLayout. */
    private GamepadLayoutPresetDocument layoutDocument;
    private KeyCodeProvider keyCodeProvider;
    private TouchpadDeltaListener touchpadDeltaListener;
    private final List<String> dynamicHitTestOrder = new ArrayList<>();
    private final Map<Integer, String> dynamicPointerStick = new HashMap<>();
    private final Map<String, float[]> dynamicStickOffset = new HashMap<>();
    private int touchpadPointerId = -1;
    private float touchpadLastX;
    private float touchpadLastY;

    private ScrollStripWheelListener scrollStripWheelListener;
    private static final float SCROLL_STRIP_PIXELS_PER_WHEEL_UNIT = 5f;

    private static final class ScrollStripPointerState {
        String moduleId;
        float lastY;
        float accumY;
    }

    private final Map<Integer, ScrollStripPointerState> scrollStripPointerState = new HashMap<>();

    /**
     * Layout-level multiplier for {@code MOUSE_BUTTON} radius (L/M/R with touchpad). Set from preset
     * {@code layout.touchpadMouseButtonScale}; default 1.0. Clamped to {@code [0.5, 2.0]}.
     */
    private float touchpadMouseButtonLayoutScale = 1.0f;

    /** Last drawn {@code stick_left} D-pad variant (for clicky haptics). */
    private String dynamicLeftDpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;

    /** z-order low → high; hit-test from end to start. Cleared each frame in {@link #drawComponents}. */
    private final List<ModuleEditChip> moduleEditChips = new ArrayList<>();
    @Nullable private Drawable moduleConfigEditIconDrawable;

    private static final float MODULE_CONFIG_CHIP_RADIUS_DP = 12f;
    private static final float MODULE_CONFIG_CHIP_GAP_DP = 4f;
    private static final float MODULE_CONFIG_CHIP_HIT_SLOP_DP = 4f;

    private static final class ModuleEditChip {
        final String moduleId;
        final float cx;
        final float cy;
        final float drawRadius;
        final float hitRadius;

        ModuleEditChip(String moduleId, float cx, float cy, float drawRadius, float hitRadius) {
            this.moduleId = moduleId;
            this.cx = cx;
            this.cy = cy;
            this.drawRadius = drawRadius;
            this.hitRadius = hitRadius;
        }

        boolean containsPoint(float x, float y) {
            float dx = x - cx;
            float dy = y - cy;
            return dx * dx + dy * dy <= hitRadius * hitRadius;
        }
    }

    /** Base radius in dp before per-module {@code scale} and {@link #touchpadMouseButtonLayoutScale}. */
    private static final float MOUSE_BUTTON_BASE_RADIUS_DP = 52f;

    /** SNES/GBA pastel face + rim + label (Material theme accent used separately for presses). */
    private static final class FaceStyle {
        final int body;
        final int rim;
        final int label;
        FaceStyle(int body, int rim, int label) {
            this.body = body;
            this.rim = rim;
            this.label = label;
        }
    }

    private static final FaceStyle FACE_A = new FaceStyle(0xFFA7E3B5, 0xFF5FB57A, 0xFF1F4D2B);
    private static final FaceStyle FACE_B = new FaceStyle(0xFFF7B2B7, 0xFFD87078, 0xFF5A1D22);
    private static final FaceStyle FACE_X = new FaceStyle(0xFFA9CCEB, 0xFF4F86C6, 0xFF152F50);
    private static final FaceStyle FACE_Y = new FaceStyle(0xFFF7E89F, 0xFFC9A640, 0xFF4D3A04);
    private static final FaceStyle FACE_PS_SQUARE = new FaceStyle(0xFFE1D5F5, 0xFF9575CD, 0xFF311B92);
    private static final FaceStyle FACE_NEUTRAL = new FaceStyle(0xFFE0E0E0, 0xFF9E9E9E, 0xFF424242);
    /** Primary face action (e.g. button_a / Enter): muted sage, flat-friendly. */
    private static final FaceStyle FACE_PRIMARY_ACTION = new FaceStyle(0xFF4A6B5C, 0xFF2D4339, 0xFFE8F2ED);
    /** Secondary face action (e.g. button_b / Esc): warm stone, not loud yellow/red. */
    private static final FaceStyle FACE_SECONDARY_ACTION = new FaceStyle(0xFF6D625C, 0xFF423A36, 0xFFF2EBE7);
    /** Touchpad-adjacent mouse buttons L/M/R: cool graphite. */
    private static final FaceStyle FACE_MOUSE_ORBIT = new FaceStyle(0xFF5A5A62, 0xFF35353C, 0xFFECEEF2);
    /** Extra generic face buttons: muted cool tones (not candy primaries). */
    private static final FaceStyle[] FACE_EXTRA_CYCLE = {
            new FaceStyle(0xFF5B6B78, 0xFF3A4450, 0xFFE8EBF0),
            new FaceStyle(0xFF6A5B6E, 0xFF453D4A, 0xFFF3EAF4),
            new FaceStyle(0xFF5C6865, 0xFF3A4542, 0xFFE9F1EE),
            new FaceStyle(0xFF686075, 0xFF443E4C, 0xFFF1EDF6),
    };

    private int themeAccentPrimary = 0xFFF57C00;
    private final Paint retroShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint retroBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint retroRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint retroGlossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint retroTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint retroDpadFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path retroWorkPath = new Path();
    private Typeface retroLabelTypeface;
    /** Lazily loaded Material arrow drawables for cross D-pad arms ({@code mutate()} per element). */
    @Nullable private Drawable[] dpadDirectionIconPool;

    public GamepadView(Context context) {
        super(context);
        init();
    }

    public GamepadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamepadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        configManager = new GamepadConfigManager(getContext());
        componentBounds = new HashMap<>();

        // Initialize paints
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#F5F5F5"));

        patternOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        patternOverlayPaint.setStyle(Paint.Style.STROKE);
        patternOverlayPaint.setStrokeCap(Paint.Cap.ROUND);

        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(Color.parseColor("#2196F3"));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.parseColor("#333333"));

        dpadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dpadPaint.setColor(Color.parseColor("#444444"));

        retroLabelTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
        if (retroLabelTypeface == null) {
            retroLabelTypeface = Typeface.DEFAULT_BOLD;
        }

        // Default layout
        currentLayout = GamepadLayout.XBOX;
        componentPositions = configManager.loadLayoutPositions(currentLayout);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshThemeAccent();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshThemeAccent();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            markBrandWatermarkContrastDirty();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshThemeAccent();

        // Draw background image if set, otherwise gradient (custom fill and/or default) + optional pattern
        if (backgroundBitmap != null) {
            drawBackgroundWithPanZoom(canvas);
        } else {
            drawSolidOrDefaultBackground(canvas);
            drawProceduralBackgroundPattern(canvas);
        }

        drawBrandWatermark(canvas);

        // Draw components based on layout
        drawComponents(canvas);
    }

    /** Bottom-center brand mark; sits on the same layer as the gradient/bitmap background. */
    private void drawBrandWatermark(Canvas canvas) {
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        if (brandWatermarkDrawable == null) {
            Drawable d = AppCompatResources.getDrawable(getContext(), R.drawable.ic_openterface_wordmark);
            if (d == null) {
                return;
            }
            brandWatermarkDrawable = d.mutate();
        }
        float density = getResources().getDisplayMetrics().density;
        int hPx = Math.round(11f * density);
        int maxWPx = Math.round(138f * density);
        float aspect = 469.34f / 70f;
        int wPx = Math.min(maxWPx, Math.round(hPx * aspect));
        int left = (vw - wPx) / 2;
        int bottomPad = Math.round(14f * density);
        int top = vh - hPx - bottomPad;

        if (brandWatermarkContrastDirty) {
            recomputeBrandWatermarkContrast(vw, vh, left, top, wPx, hPx);
        }

        brandWatermarkDrawable.setTint(cachedBrandWatermarkTint);
        brandWatermarkDrawable.setAlpha(cachedBrandWatermarkAlpha);
        brandWatermarkDrawable.setBounds(left, top, left + wPx, top + hPx);
        brandWatermarkDrawable.draw(canvas);
        brandWatermarkDrawable.setAlpha(255);
    }

    private void markBrandWatermarkContrastDirty() {
        brandWatermarkContrastDirty = true;
    }

    /**
     * Samples the same background the user sees (solid/gradient or bitmap under the wordmark) and
     * caches tint + alpha for readable contrast.
     */
    private void recomputeBrandWatermarkContrast(int vw, int vh, int left, int top, int wPx, int hPx) {
        double lum;
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            lum = sampleBitmapAverageLuminanceUnderWordmark(vw, vh, left, top, wPx, hPx);
        } else {
            lum = solidBackgroundAverageLuminanceUnderWordmark(vh, left, top, wPx, hPx);
        }
        boolean lightBackdrop = lum > BRAND_WATERMARK_LUMA_THRESHOLD;
        if (lightBackdrop) {
            cachedBrandWatermarkTint = BRAND_WATERMARK_TINT_ON_LIGHT_BG;
            cachedBrandWatermarkAlpha = 168;
        } else {
            cachedBrandWatermarkTint = BRAND_WATERMARK_TINT_ON_DARK_BG;
            cachedBrandWatermarkAlpha = 148;
        }
        brandWatermarkContrastDirty = false;
    }

    /** Matches {@link #drawSolidOrDefaultBackground} top/bottom colors; samples a small grid in the wordmark rect. */
    private double solidBackgroundAverageLuminanceUnderWordmark(
            int vh, int left, int top, int wPx, int hPx) {
        int topColor;
        int bottomColor;
        if (backgroundFillArgb != null) {
            int c = backgroundFillArgb;
            topColor = c;
            bottomColor = isLightFace(c) ? darkenArgb(c, 0.9f) : ColorUtils.blendARGB(c, Color.WHITE, 0.1f);
        } else {
            topColor = Color.parseColor("#F5F5F5");
            bottomColor = Color.parseColor("#ECECEC");
        }
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            float sx = left + (wPx * (i / 2f));
            for (int j = 0; j < 3; j++) {
                float sy = top + (hPx * (j / 2f));
                float t = vh > 1 ? sy / (float) vh : 0f;
                if (t < 0f) {
                    t = 0f;
                } else if (t > 1f) {
                    t = 1f;
                }
                int c = ColorUtils.blendARGB(topColor, bottomColor, t);
                sum += ColorUtils.calculateLuminance(c);
                count++;
            }
        }
        return count > 0 ? sum / count : BRAND_WATERMARK_LUMA_THRESHOLD;
    }

    /** Inverse of {@link #drawBackgroundWithPanZoom} transform; averages luminance over a 3×3 grid. */
    private double sampleBitmapAverageLuminanceUnderWordmark(
            int vw, int vh, int left, int top, int wPx, int hPx) {
        android.graphics.Bitmap bm = backgroundBitmap;
        if (bm == null || bm.isRecycled()) {
            return BRAND_WATERMARK_LUMA_THRESHOLD;
        }
        int bw = bm.getWidth();
        int bh = bm.getHeight();
        float cx = vw / 2f + bgOffsetX;
        float cy = vh / 2f + bgOffsetY;
        float invScale = bgScale != 0f ? 1f / bgScale : 1f;
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            float sx = left + (wPx * (i / 2f));
            for (int j = 0; j < 3; j++) {
                float sy = top + (hPx * (j / 2f));
                float bx = (sx - cx) * invScale + bw / 2f;
                float by = (sy - cy) * invScale + bh / 2f;
                int x = Math.round(bx);
                int y = Math.round(by);
                if (x < 0 || y < 0 || x >= bw || y >= bh) {
                    continue;
                }
                int p = bm.getPixel(x, y);
                int a = Color.alpha(p);
                if (a < 16) {
                    continue;
                }
                sum += ColorUtils.calculateLuminance(p);
                count++;
            }
        }
        return count > 0 ? sum / count : BRAND_WATERMARK_LUMA_THRESHOLD;
    }

    private void drawBackgroundWithPanZoom(Canvas canvas) {
        canvas.save();
        int bw = backgroundBitmap.getWidth();
        int bh = backgroundBitmap.getHeight();
        int vw = getWidth();
        int vh = getHeight();

        // Compute initial fit scale (center-crop to fill screen while maintaining aspect ratio)
        float fitScale = Math.max((float) vw / bw, (float) vh / bh);
        bgInitialScale = fitScale;

        // Apply current transform: pan then scale, centered on viewport
        float cx = vw / 2f + bgOffsetX;
        float cy = vh / 2f + bgOffsetY;
        canvas.translate(cx, cy);
        canvas.scale(bgScale, bgScale);
        canvas.translate(-bw / 2f, -bh / 2f);
        canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        canvas.restore();
    }

    private void drawSolidOrDefaultBackground(Canvas canvas) {
        int vw = getWidth();
        int vh = getHeight();
        GamepadCanvasBackgroundPreview.drawSolidOrGradientFill(canvas, vw, vh, backgroundFillArgb, bgPaint);
    }

    private void drawProceduralBackgroundPattern(Canvas canvas) {
        if (backgroundPatternId == null) {
            return;
        }
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        GamepadCanvasBackgroundPreview.drawPatternOverlay(
                canvas,
                vw,
                vh,
                density,
                backgroundPatternId,
                backgroundFillArgb,
                patternOverlayPaint,
                false);
    }

    private void drawComponents(Canvas canvas) {
        moduleEditChips.clear();
        // Clear stale bounds from previous draw before re-registering all hit areas
        componentBounds.clear();
        switch (currentLayout) {
            case XBOX:
                drawXboxLayout(canvas);
                break;
            case PLAYSTATION:
                drawPlayStationLayout(canvas);
                break;
            case NES:
                drawNESLayout(canvas);
                break;
            case SIMPLE:
                if (useDynamicLayout()) {
                    drawDynamicSimpleLayout(canvas);
                } else {
                    drawSimpleLayout(canvas);
                }
                break;
        }
        if (isEditMode) {
            drawEditModeOutlines(canvas);
            if (useDynamicLayout()) {
                drawEditModeModuleConfigChips(canvas);
            }
        }
    }

    private boolean useDynamicLayout() {
        return layoutDocument != null && layoutDocument.modules != null;
    }

    private void refreshThemeAccent() {
        if (!isAttachedToWindow()) {
            return;
        }
        themeAccentPrimary = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary,
                Color.parseColor("#F57C00"));
    }

    private static int darkenArgb(int color, float valueMul) {
        int a = Color.alpha(color);
        int r = Math.min(255, Math.round(Color.red(color) * valueMul));
        int g = Math.min(255, Math.round(Color.green(color) * valueMul));
        int b = Math.min(255, Math.round(Color.blue(color) * valueMul));
        return Color.argb(a, r, g, b);
    }

    private static boolean isLightFace(int body) {
        return Color.red(body) * 0.299 + Color.green(body) * 0.587 + Color.blue(body) * 0.114 > 160;
    }

    private static FaceStyle faceStyleFromLegacy(int body) {
        int rim = darkenArgb(body, 0.78f);
        int label = isLightFace(body) ? 0xFF212121 : 0xFFFFFFFF;
        return new FaceStyle(body, rim, label);
    }

    private static int applyAlphaInt(int color, int alpha) {
        return Color.argb(Math.min(255, Math.max(0, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static FaceStyle faceStyleFromModuleAccent(int accentOpaque) {
        int opaque = GamepadModuleAccent.toOpaqueArgb(accentOpaque);
        int body = ColorUtils.blendARGB(0xFFE8EAEE, opaque, 0.55f);
        int rim = ColorUtils.blendARGB(0xFF2A2E36, opaque, 0.5f);
        int label = isLightFace(body) ? 0xFF1A1C22 : 0xFFF5F6F8;
        return new FaceStyle(body, rim, label);
    }

    private FaceStyle faceStyleForFaceLabel(String label, int legacyColor) {
        if (label == null) {
            return faceStyleFromLegacy(legacyColor);
        }
        String t = label.trim();
        if ("△".equals(t)) {
            return FACE_A;
        }
        if ("×".equals(t)) {
            return FACE_X;
        }
        if ("○".equals(t)) {
            return FACE_B;
        }
        if ("□".equals(t)) {
            return FACE_PS_SQUARE;
        }
        if (t.equalsIgnoreCase("a")) {
            return FACE_A;
        }
        if (t.equalsIgnoreCase("b")) {
            return FACE_B;
        }
        if (t.equalsIgnoreCase("x")) {
            return FACE_X;
        }
        if (t.equalsIgnoreCase("y")) {
            return FACE_Y;
        }
        String u = t.toUpperCase(Locale.US);
        if (u.contains("SELECT") || u.contains("START") || "≡".equals(t) || "⊞".equals(t)) {
            return FACE_NEUTRAL;
        }
        return faceStyleFromLegacy(legacyColor);
    }

    private FaceStyle faceStyleForModuleId(String moduleId) {
        if (moduleId == null) {
            return FACE_NEUTRAL;
        }
        if (GamepadLayoutPresetConstants.SHOULDER_L_ID.equals(moduleId)
                || GamepadLayoutPresetConstants.SHOULDER_R_ID.equals(moduleId)) {
            return new FaceStyle(0xFF5A5F68, 0xFF383C45, 0xFFECEEF2);
        }
        if (GamepadLayoutPresetConstants.TRIGGER_L_ID.equals(moduleId)
                || GamepadLayoutPresetConstants.TRIGGER_R_ID.equals(moduleId)) {
            return new FaceStyle(0xFF4A5058, 0xFF2A2E34, 0xFFE8EAEE);
        }
        if ("button_a".equals(moduleId)) {
            return FACE_PRIMARY_ACTION;
        }
        if ("button_b".equals(moduleId)) {
            return FACE_SECONDARY_ACTION;
        }
        if (moduleId.startsWith("mouse_btn_")) {
            return FACE_MOUSE_ORBIT;
        }
        int h = Math.abs(moduleId.hashCode());
        return FACE_EXTRA_CYCLE[h % FACE_EXTRA_CYCLE.length];
    }

    /**
     * @param cornerRadiusNorm {@code 0} = sharp square, {@code 1} = circle (same as legacy face buttons).
     * @param accentForRing pressed-state highlight ring (theme or per-module accent).
     */
    private void drawRetroFaceButton(Canvas canvas, float cx, float cy, float r, FaceStyle style,
                                     boolean pressed, String text, float cornerRadiusNorm, int accentForRing) {
        drawRetroFaceButtonOriented(canvas, cx, cy, r, r, style, pressed, text, cornerRadiusNorm, accentForRing, 0f,
                null);
    }

    /**
     * Rounded rect / stadium face in local axes (halfW × halfH), then rotated by {@code rotationDeg} clockwise.
     */
    private void drawRetroFaceButtonOriented(Canvas canvas, float cx, float cy, float halfW, float halfH,
                                           FaceStyle style, boolean pressed, @Nullable String text,
                                           float cornerRadiusNorm, int accentForRing, float rotationDeg,
                                           @Nullable Integer capLabelColorArgbOverride) {
        float cn = Math.max(0f, Math.min(1f, cornerRadiusNorm));
        float ref = Math.max(1f, Math.min(halfW, halfH));
        float cornerPx = cn * ref;
        float shadowDy = ref * 0.05f;
        float strokeRef = Math.max(1.25f, ref * 0.045f);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotationDeg);

        RectF bounds = new RectF(-halfW, -halfH, halfW, halfH);
        retroShadowPaint.setMaskFilter(new BlurMaskFilter(Math.max(2.5f, ref * 0.10f), BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x40000000);
        RectF shadow = new RectF(bounds);
        shadow.offset(0, shadowDy * 0.45f);
        canvas.drawRoundRect(shadow, cornerPx, cornerPx, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);

        int body = pressed ? darkenArgb(style.body, 0.88f) : style.body;
        int rimTone = darkenArgb(style.rim, pressed ? 0.92f : 1f);
        Shader lg = new LinearGradient(0, -halfH, 0, halfH, lightenArgb(body, 0.08f), rimTone, Shader.TileMode.CLAMP);
        retroBodyPaint.setShader(lg);
        canvas.drawRoundRect(bounds, cornerPx, cornerPx, retroBodyPaint);
        retroBodyPaint.setShader(null);

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(strokeRef);
        retroRingPaint.setColor(applyAlphaInt(0xFF000000, pressed ? 55 : 40));
        canvas.drawRoundRect(bounds, cornerPx, cornerPx, retroRingPaint);
        if (pressed) {
            float grow = ref * 0.015f;
            RectF ring = new RectF(bounds);
            ring.inset(-grow, -grow);
            float maxCorner = Math.min(ring.width(), ring.height()) * 0.5f;
            float c2 = Math.min(cornerPx + grow, maxCorner);
            retroRingPaint.setStrokeWidth(Math.max(1.5f, ref * 0.038f));
            retroRingPaint.setColor(applyAlphaInt(accentForRing, 210));
            canvas.drawRoundRect(ring, c2, c2, retroRingPaint);
        }

        float glossInset = ref * 0.10f;
        RectF glossBounds = new RectF(bounds);
        glossBounds.inset(glossInset, glossInset);
        float innerHalf = Math.max(1f, Math.min(halfW, halfH) - glossInset);
        float glossCorner = cn * innerHalf;
        float glossR = innerHalf * 0.98f;
        Shader rg = new RadialGradient(-halfW * 0.22f, -halfH * 0.26f, glossR,
                0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        retroGlossPaint.setShader(rg);
        canvas.drawRoundRect(glossBounds, glossCorner, glossCorner, retroGlossPaint);
        retroGlossPaint.setShader(null);

        if (text != null && !text.isEmpty()) {
            drawFittedFaceCapLabelLocal(canvas, halfW, halfH, style, text, capLabelColorArgbOverride);
        }
        canvas.restore();
    }

    /** Axis-aligned bounds for hit-testing a rotated rounded-rect button centered at ({@code cx}, {@code cy}). */
    private static RectF orientedButtonHitAabb(float cx, float cy, float halfW, float halfH, float rotationDeg) {
        double rad = Math.toRadians(rotationDeg);
        double c = Math.abs(Math.cos(rad));
        double s = Math.abs(Math.sin(rad));
        float ax = (float) (halfW * c + halfH * s);
        float ay = (float) (halfW * s + halfH * c);
        return new RectF(cx - ax, cy - ay, cx + ax, cy + ay);
    }

    private static String normalizeCapText(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim().replace('\n', ' ').replace('\r', ' ');
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    private static boolean mostlyEmojiOrSymbol(@NonNull String text) {
        int special = 0;
        int cpCount = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            cpCount++;
            int type = Character.getType(cp);
            if (cp >= 0x1F300
                    || Character.isIdeographic(cp)
                    || type == Character.OTHER_SYMBOL
                    || type == Character.MATH_SYMBOL
                    || type == Character.MODIFIER_SYMBOL) {
                special++;
            }
            i += Character.charCount(cp);
        }
        return cpCount > 0 && special * 2 >= cpCount;
    }

    /** Cap label centered at local origin (0,0), drawn inside caller's rotated/translated canvas. */
    private void drawFittedFaceCapLabelLocal(Canvas canvas, float halfW, float halfH, FaceStyle style, String raw,
            @Nullable Integer labelColorOverride) {
        String text = normalizeCapText(raw);
        if (text.isEmpty()) {
            return;
        }
        float labelR = Math.min(halfW, halfH);
        float maxW = Math.min(halfW * 2f * 0.76f, labelR * 2f * 0.76f);
        maxW = Math.max(32f, maxW);
        float maxH = Math.min(halfH * 1.25f, labelR * 1.38f);
        float maxSp = labelR * 0.55f;
        float minSp = labelR * 0.18f;
        boolean useBold = !mostlyEmojiOrSymbol(text);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        int labelArgb = labelColorOverride != null
                ? GamepadModuleAccent.toOpaqueArgb(labelColorOverride)
                : style.label;
        tp.setColor(labelArgb);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(useBold ? retroLabelTypeface : Typeface.DEFAULT);
        tp.setShadowLayer(1f, 0f, 0.5f, applyAlphaInt(0xFF000000, 35));
        for (int iter = 0; iter < 30; iter++) {
            float sp = maxSp - (maxSp - minSp) * iter / 29f;
            tp.setTextSize(sp);
            tp.setFakeBoldText(useBold);
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) maxW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build();
            if (sl.getHeight() <= maxH || iter == 29) {
                float dy = -sl.getHeight() / 2f + labelR * 0.05f;
                canvas.save();
                canvas.translate(-maxW / 2f, dy);
                sl.draw(canvas);
                canvas.restore();
                tp.clearShadowLayer();
                return;
            }
        }
    }

    /** Fitted 1–2 line text centered on the analog stick inner thumb cap. */
    private void drawStickInnerCapLabel(Canvas canvas, float innerCx, float innerCy, float capR, String raw, int argb) {
        String text = normalizeCapText(raw);
        if (text.isEmpty()) {
            return;
        }
        float maxW = capR * 1.65f;
        float maxH = capR * 1.25f;
        float maxSp = capR * 0.52f;
        float minSp = capR * 0.14f;
        boolean useBold = !mostlyEmojiOrSymbol(text);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(argb);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(useBold ? retroLabelTypeface : Typeface.DEFAULT);
        tp.setShadowLayer(1f, 0f, 0.5f, applyAlphaInt(0xFF000000, 35));
        for (int iter = 0; iter < 30; iter++) {
            float sp = maxSp - (maxSp - minSp) * iter / 29f;
            tp.setTextSize(sp);
            tp.setFakeBoldText(useBold);
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) maxW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build();
            if (sl.getHeight() <= maxH || iter == 29) {
                float dy = innerCy - sl.getHeight() / 2f + capR * 0.06f;
                canvas.save();
                canvas.translate(innerCx - maxW / 2f, dy);
                sl.draw(canvas);
                canvas.restore();
                tp.clearShadowLayer();
                return;
            }
        }
    }

    private void drawFittedCapsuleLabel(Canvas canvas, RectF bounds, String raw, int textColorArgb) {
        String text = normalizeCapText(raw);
        if (text.isEmpty()) {
            return;
        }
        float maxW = bounds.width() * 0.88f;
        float maxH = bounds.height() * 0.72f;
        float maxSp = Math.min(bounds.height(), bounds.width()) * 0.38f;
        float minSp = Math.min(bounds.height(), bounds.width()) * 0.14f;
        boolean useBold = !mostlyEmojiOrSymbol(text);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(textColorArgb);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(useBold ? retroLabelTypeface : Typeface.DEFAULT);
        tp.setShadowLayer(1f, 0f, 0.5f, applyAlphaInt(0xFF000000, 40));
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        for (int iter = 0; iter < 28; iter++) {
            float sp = maxSp - (maxSp - minSp) * iter / 27f;
            tp.setTextSize(sp);
            tp.setFakeBoldText(useBold);
            StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) maxW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build();
            if (sl.getHeight() <= maxH || iter == 27) {
                float dy = cy - sl.getHeight() / 2f;
                canvas.save();
                canvas.translate(cx - maxW / 2f, dy);
                sl.draw(canvas);
                canvas.restore();
                tp.clearShadowLayer();
                return;
            }
        }
    }

    private void drawMappingHintCallout(Canvas canvas, float cx, float top, String rawHint) {
        if (!keyMappingHintsVisible) {
            return;
        }
        String hint = normalizeCapText(rawHint);
        if (hint.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float maxPillWidth = Math.min(getWidth() * 0.42f, 220f * density);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(Math.max(10f * density, 11f * density));
        tp.setFakeBoldText(false);
        tp.setTypeface(Typeface.DEFAULT);
        tp.setTextAlign(Paint.Align.LEFT);
        CharSequence ell = TextUtils.ellipsize(hint, tp, maxPillWidth, TextUtils.TruncateAt.END);
        String line = ell.toString();
        float tw = tp.measureText(line);
        float padX = 8f * density;
        float padY = 3.5f * density;
        Paint.FontMetrics fm = tp.getFontMetrics();
        float textH = fm.descent - fm.ascent;
        RectF pill = new RectF(cx - tw / 2f - padX, top, cx + tw / 2f + padX, top + textH + padY * 2f);
        retroBodyPaint.setShader(null);
        retroBodyPaint.setColor(0xD8FFFFFF);
        float cr = 6f * density;
        canvas.drawRoundRect(pill, cr, cr, retroBodyPaint);
        tp.setColor(0xFF2C2C32);
        tp.clearShadowLayer();
        float baseline = pill.top + padY - fm.ascent;
        canvas.drawText(line, cx - tw / 2f, baseline, tp);
    }

    @Nullable
    private static String presetDisplayLabel(@Nullable GamepadLayoutPresetDocument.GamepadModule m) {
        if (m == null || m.displayLabel == null) {
            return null;
        }
        String t = m.displayLabel.trim();
        if (t.isEmpty()) {
            return null;
        }
        return GamepadCapLabels.clampToMaxCodePoints(t, GamepadCapLabels.MAX_CAP_LABEL_CODE_POINTS);
    }

    /** If cap text equals the mapping hint, skip drawing the redundant callout below. */
    private static boolean sameCapAndHint(@Nullable String cap, @Nullable String hint) {
        if (cap == null || hint == null) {
            return false;
        }
        return cap.trim().equals(hint.trim());
    }

    private String resolveMappingHintOnly(GamepadLayoutPresetDocument.GamepadModule m) {
        String fromSync = componentDisplayLabels.get(m.id);
        if (fromSync != null && !fromSync.isEmpty()) {
            return fromSync;
        }
        return shortButtonLabel(m);
    }

    public void setKeyMappingHintsVisible(boolean visible) {
        if (keyMappingHintsVisible == visible) {
            return;
        }
        keyMappingHintsVisible = visible;
        invalidate();
    }

    /** Slightly lift RGB toward white for a subtle top-lit body (0 = no change). */
    private static int lightenArgb(int color, float amount) {
        if (amount <= 0f) {
            return color;
        }
        int a = Color.alpha(color);
        int r = (int) Math.min(255, Color.red(color) + 255f * amount);
        int g = (int) Math.min(255, Color.green(color) + 255f * amount);
        int b = (int) Math.min(255, Color.blue(color) + 255f * amount);
        return Color.argb(a, r, g, b);
    }

    private void drawEditModeOutlines(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float stroke = 1.5f * density;
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(stroke);
        retroRingPaint.setColor(themeAccentPrimary);
        retroRingPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 5f * density}, 0f));
        for (RectF b : componentBounds.values()) {
            float rr = Math.min(b.width(), b.height()) * 0.12f;
            canvas.drawRoundRect(b, rr, rr, retroRingPaint);
        }
        retroRingPaint.setPathEffect(null);
    }

    /**
     * Small edit affordance at the top-right inside each module’s dashed outline (dynamic SIMPLE presets only).
     */
    private void drawEditModeModuleConfigChips(Canvas canvas) {
        if (layoutDocument == null || layoutDocument.modules == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float drawR = MODULE_CONFIG_CHIP_RADIUS_DP * density;
        float gap = MODULE_CONFIG_CHIP_GAP_DP * density;
        float hitSlop = MODULE_CONFIG_CHIP_HIT_SLOP_DP * density;
        float hitR = drawR + hitSlop;

        if (moduleConfigEditIconDrawable == null) {
            Drawable d = AppCompatResources.getDrawable(ctx, R.drawable.ic_edit);
            moduleConfigEditIconDrawable = d != null ? d.mutate() : null;
        }

        List<GamepadLayoutPresetDocument.GamepadModule> mods = new ArrayList<>(layoutDocument.modules);
        Collections.sort(mods, Comparator.comparingInt(m -> m.zIndex));

        int fillCol = ColorUtils.blendARGB(themeAccentPrimary, Color.WHITE, 0.72f);
        int strokeCol = ColorUtils.blendARGB(themeAccentPrimary, Color.BLACK, 0.35f);
        int iconTint = ColorUtils.blendARGB(themeAccentPrimary, Color.BLACK, 0.55f);

        for (GamepadLayoutPresetDocument.GamepadModule m : mods) {
            if (m == null || m.id == null) {
                continue;
            }
            if (isComponentDisabled(m.id)) {
                continue;
            }
            RectF b = componentBounds.get(m.id);
            if (b == null) {
                continue;
            }
            float cx = b.right - gap - drawR;
            float cy = b.top + gap + drawR;
            moduleEditChips.add(new ModuleEditChip(m.id, cx, cy, drawR, hitR));

            retroBodyPaint.setShader(null);
            retroBodyPaint.setColor(fillCol);
            canvas.drawCircle(cx, cy, drawR, retroBodyPaint);

            retroRingPaint.setStyle(Paint.Style.STROKE);
            retroRingPaint.setStrokeWidth(Math.max(1f, density));
            retroRingPaint.setColor(strokeCol);
            retroRingPaint.setPathEffect(null);
            canvas.drawCircle(cx, cy, drawR, retroRingPaint);

            if (moduleConfigEditIconDrawable != null) {
                float inset = drawR * 0.38f;
                int left = Math.round(cx - drawR + inset);
                int top = Math.round(cy - drawR + inset);
                int right = Math.round(cx + drawR - inset);
                int bottom = Math.round(cy + drawR - inset);
                moduleConfigEditIconDrawable.setTint(iconTint);
                moduleConfigEditIconDrawable.setBounds(left, top, right, bottom);
                moduleConfigEditIconDrawable.draw(canvas);
            }
        }
    }

    @Nullable
    private String findModuleIdForConfigChipHit(float x, float y) {
        if (!isEditMode || !useDynamicLayout() || moduleEditChips.isEmpty()) {
            return null;
        }
        for (int i = moduleEditChips.size() - 1; i >= 0; i--) {
            ModuleEditChip chip = moduleEditChips.get(i);
            if (chip.containsPoint(x, y)) {
                return chip.moduleId;
            }
        }
        return null;
    }

    private boolean isDpadDirPressed(String dir) {
        String id = "dpad_" + dir;
        return dpadPressedSet.contains(id) || id.equals(pressedComponentId);
    }

    /**
     * Keeps preset modules inside the safe area; set from window system bar insets (see GamepadFragment).
     */
    public void setGamepadContentInsets(int left, int top, int right, int bottom) {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.max(0, right);
        bottom = Math.max(0, bottom);
        if (contentInsetLeft == left && contentInsetTop == top
                && contentInsetRight == right && contentInsetBottom == bottom) {
            return;
        }
        contentInsetLeft = left;
        contentInsetTop = top;
        contentInsetRight = right;
        contentInsetBottom = bottom;
        invalidate();
    }

    private int contentWidthPx() {
        return Math.max(1, getWidth() - contentInsetLeft - contentInsetRight);
    }

    private int contentHeightPx() {
        return Math.max(1, getHeight() - contentInsetTop - contentInsetBottom);
    }

    /** Map view X/Y to normalized anchors for the dynamic layout content rect. */
    private void viewXYToDynamicNorm(float viewX, float viewY, float[] out) {
        out[0] = (viewX - contentInsetLeft) / contentWidthPx();
        out[1] = (viewY - contentInsetTop) / contentHeightPx();
    }

    private void applyDraggedAnchorsFromViewXY(float moveX, float moveY) {
        if (useDynamicLayout()) {
            float[] n = new float[2];
            viewXYToDynamicNorm(moveX, moveY, n);
            applyDraggedComponentAnchors(n[0], n[1]);
        } else {
            applyDraggedComponentAnchors(moveX / getWidth(), moveY / getHeight());
        }
    }

    /** Dynamic draw uses a translated canvas; bump stored hit rects into view coordinates. */
    private void offsetDynamicBoundsIntoViewCoordinates() {
        if (contentInsetLeft == 0 && contentInsetTop == 0) {
            return;
        }
        float dx = contentInsetLeft;
        float dy = contentInsetTop;
        for (RectF r : componentBounds.values()) {
            r.offset(dx, dy);
        }
    }

    public void setLayoutDocument(@androidx.annotation.Nullable GamepadLayoutPresetDocument doc) {
        this.layoutDocument = doc;
        dynamicHitTestOrder.clear();
        dynamicPointerStick.clear();
        dynamicStickOffset.clear();
        touchpadPointerId = -1;
        scrollStripPointerState.clear();
        invalidate();
    }

    public void setKeyCodeProvider(@androidx.annotation.Nullable KeyCodeProvider provider) {
        this.keyCodeProvider = provider;
    }

    public void setTouchpadDeltaListener(@androidx.annotation.Nullable TouchpadDeltaListener listener) {
        this.touchpadDeltaListener = listener;
    }

    public void setScrollStripWheelListener(@androidx.annotation.Nullable ScrollStripWheelListener listener) {
        this.scrollStripWheelListener = listener;
    }

    /**
     * Multiplier for drawing all {@code MOUSE_BUTTON} modules (typically touchpad L/M/R).
     * Values outside {@code [0.5, 2.0]} are clamped; NaN and non-positive fall back to 1.0.
     */
    public void setTouchpadMouseButtonLayoutScale(float scale) {
        if (!(scale > 0f) || Float.isNaN(scale)) {
            touchpadMouseButtonLayoutScale = 1.0f;
        } else {
            touchpadMouseButtonLayoutScale = Math.max(0.5f, Math.min(2.0f, scale));
        }
        invalidate();
    }

    private void drawDynamicSimpleLayout(Canvas canvas) {
        dynamicHitTestOrder.clear();
        if (layoutDocument == null || layoutDocument.modules == null) {
            return;
        }
        int cw = contentWidthPx();
        int ch = contentHeightPx();
        canvas.save();
        canvas.translate(contentInsetLeft, contentInsetTop);
        GamepadDynamicLayoutRegistry.drawSortedModules(this, canvas, layoutDocument, cw, ch);
        canvas.restore();
        offsetDynamicBoundsIntoViewCoordinates();
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicStickLikeModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        String upL = componentDisplayLabels.getOrDefault("stick_up", "W");
        String dnL = componentDisplayLabels.getOrDefault("stick_down", "S");
        String lfL = componentDisplayLabels.getOrDefault("stick_left", "A");
        String rtL = componentDisplayLabels.getOrDefault("stick_right", "D");
        if ("stick_right".equals(m.id)) {
            upL = componentDisplayLabels.getOrDefault("stick_r_up", "I");
            dnL = componentDisplayLabels.getOrDefault("stick_r_down", "K");
            lfL = componentDisplayLabels.getOrDefault("stick_r_left", "J");
            rtL = componentDisplayLabels.getOrDefault("stick_r_right", "L");
        } else if (GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
            String p = m.id + "_";
            upL = componentDisplayLabels.getOrDefault(p + "up", "W");
            dnL = componentDisplayLabels.getOrDefault(p + "down", "S");
            lfL = componentDisplayLabels.getOrDefault(p + "left", "A");
            rtL = componentDisplayLabels.getOrDefault(p + "right", "D");
        }
        String shortLabel;
        if ("stick_left".equals(m.id)) {
            shortLabel = "L";
        } else if ("stick_right".equals(m.id)) {
            shortLabel = "R";
        } else if (GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
            shortLabel = "L" + m.id.substring("stick_left_".length());
        } else {
            shortLabel = "?";
        }
        int accent = GamepadModuleAccent.resolve(m.moduleAccentArgb, themeAccentPrimary);
        float layoutScale = GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(w, h);
        drawAnalogStickForModule(canvas, x, y, 180f * layoutScale, m.scale, m.id, shortLabel,
                GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type) ? upL : null,
                GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type) ? dnL : null,
                GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type) ? lfL : null,
                GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type) ? rtL : null,
                m.stickVisualVariant,
                accent,
                m);
        dynamicHitTestOrder.add(m.id);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicDpadModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float layoutScale = GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(w, h);
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        String upL = componentDisplayLabels.getOrDefault("stick_up", "W");
        String dnL = componentDisplayLabels.getOrDefault("stick_down", "S");
        String lfL = componentDisplayLabels.getOrDefault("stick_left", "A");
        String rtL = componentDisplayLabels.getOrDefault("stick_right", "D");
        if (GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
            String p = m.id + "_";
            upL = componentDisplayLabels.getOrDefault(p + "up", "W");
            dnL = componentDisplayLabels.getOrDefault(p + "down", "S");
            lfL = componentDisplayLabels.getOrDefault(p + "left", "A");
            rtL = componentDisplayLabels.getOrDefault(p + "right", "D");
        }
        String variant = m.dpadVariant != null ? m.dpadVariant.trim()
                : GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        if ("stick_left".equals(m.id)) {
            dynamicLeftDpadVariant = GamepadDpadVariantArt.normalizeVariant(variant);
        }
        int accent = GamepadModuleAccent.resolve(m.moduleAccentArgb, themeAccentPrimary);
        String crossArmDec = GamepadLayoutPresetDocument.effectiveDpadCrossArmDecoration(m);
        String normDec = GamepadLayoutPresetConstants.normalizeDpadCrossArmDecoration(crossArmDec);
        Drawable[] iconPool = null;
        if (GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_ICONS.equals(normDec)) {
            ensureDpadDirectionIconDrawables();
            iconPool = dpadDirectionIconPool;
        }
        float labelRadialScale = keyMappingHintsVisible ? 1.12f : 1f;
        if (GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_LABELS.equals(normDec)
                || GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_ICONS.equals(normDec)) {
            labelRadialScale = Math.max(labelRadialScale, 1.12f);
        }
        drawDpadForModule(canvas, x, y, 180f * layoutScale, m.scale, m.id, variant, upL, dnL, lfL, rtL,
                m.dpadSplitGapRatio, m.dpadSplitOuterReachRatio, accent,
                keyMappingHintsVisible, labelRadialScale, crossArmDec, iconPool,
                presetDisplayLabel(m), m.displayLabelColorArgb);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicButtonModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float layoutScale = GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(w, h);
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        FaceStyle fs = faceStyleForModuleId(m.id);
        String cap = presetDisplayLabel(m);
        String hint = resolveMappingHintOnly(m);
        if (sameCapAndHint(cap, hint)) {
            hint = null;
        }
        if (Boolean.FALSE.equals(m.mappedKeyLabelVisible)) {
            hint = null;
        }
        float corner = GamepadLayoutPresetConstants.clampButtonCornerRadiusNorm(m.buttonCornerRadiusNorm);
        drawButtonForModule(canvas, x, y, 100f * m.scale * layoutScale, m.id, fs, cap, hint, corner, m.moduleAccentArgb, m);
        dynamicHitTestOrder.add(m.id);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicTouchpadModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        float ww = (m.widthNorm != null ? m.widthNorm : 0.35f) * w;
        float hh = (m.heightNorm != null ? m.heightNorm : 0.25f) * h;
        drawTouchpadModule(canvas, m, x, y, ww, hh);
        dynamicHitTestOrder.add(m.id);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicScrollStripModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        float ww = (m.widthNorm != null ? m.widthNorm : GamepadLayoutDocEditor.SCROLL_STRIP_DEFAULT_WIDTH_NORM) * w;
        float hh = (m.heightNorm != null ? m.heightNorm : GamepadLayoutDocEditor.SCROLL_STRIP_DEFAULT_HEIGHT_NORM) * h;
        drawScrollStripModule(canvas, m, x, y, ww, hh);
        dynamicHitTestOrder.add(m.id);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicMouseButtonModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float layoutScale = GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(w, h);
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        float density = getResources().getDisplayMetrics().density;
        float rpx = MOUSE_BUTTON_BASE_RADIUS_DP * density * m.scale * touchpadMouseButtonLayoutScale * layoutScale;
        String cap = presetDisplayLabel(m);
        String hint = resolveMappingHintOnly(m);
        if ("?".equals(hint)) {
            hint = null;
        }
        if (sameCapAndHint(cap, hint)) {
            hint = null;
        }
        drawButtonForModule(canvas, x, y, rpx, m.id, FACE_NEUTRAL, cap, hint, 1f, m.moduleAccentArgb, m);
        dynamicHitTestOrder.add(m.id);
    }

    /** @see GamepadDynamicLayoutRegistry */
    public void drawDynamicShoulderOrTriggerModule(Canvas canvas, GamepadLayoutPresetDocument.GamepadModule m, int w, int h) {
        float layoutScale = GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(w, h);
        float x = m.anchorX * w;
        float y = m.anchorY * h;
        float density = getResources().getDisplayMetrics().density;
        float ww = 108f * density * m.scale * layoutScale;
        float hh = 34f * density * m.scale * layoutScale;
        String cap = presetDisplayLabel(m);
        String hint = resolveMappingHintOnly(m);
        if (sameCapAndHint(cap, hint)) {
            hint = null;
        }
        int accent = GamepadModuleAccent.resolve(m.moduleAccentArgb, themeAccentPrimary);
        drawShoulderCapsuleForModule(canvas, x, y, ww, hh, m.id, cap, hint, accent, m.displayLabelColorArgb);
        dynamicHitTestOrder.add(m.id);
    }

    private String shortButtonLabel(GamepadLayoutPresetDocument.GamepadModule m) {
        if (m.hidKey == null) {
            return "?";
        }
        return String.valueOf(m.hidKey);
    }

    private void drawButtonForModule(Canvas canvas, float cx, float cy, float radiusPx, String id,
                                     FaceStyle style, @Nullable String capOnButton, @Nullable String mappingHint,
                                     float cornerRadiusNorm, @Nullable Integer moduleAccentArgb,
                                     @Nullable GamepadLayoutPresetDocument.GamepadModule buttonShape) {
        float rw = GamepadLayoutPresetConstants.clampButtonWidthRatio(
                buttonShape != null ? buttonShape.buttonWidthRatio : null);
        float rh = GamepadLayoutPresetConstants.clampButtonHeightRatio(
                buttonShape != null ? buttonShape.buttonHeightRatio : null);
        float rotDeg = GamepadLayoutPresetConstants.clampButtonRotationDeg(
                buttonShape != null ? buttonShape.buttonRotationDeg : null);
        float halfW = radiusPx * rw;
        float halfH = radiusPx * rh;
        componentBounds.put(id, orientedButtonHitAabb(cx, cy, halfW, halfH, rotDeg));
        int ringAccent = GamepadModuleAccent.resolve(moduleAccentArgb, themeAccentPrimary);
        FaceStyle fs = style;
        if (moduleAccentArgb != null) {
            fs = faceStyleFromModuleAccent(ringAccent);
        }
        Integer capLabelArgb = buttonShape != null ? buttonShape.displayLabelColorArgb : null;
        drawRetroFaceButtonOriented(canvas, cx, cy, halfW, halfH, fs, id.equals(pressedComponentId), capOnButton,
                cornerRadiusNorm, ringAccent, rotDeg, capLabelArgb);
        float density = getResources().getDisplayMetrics().density;
        if (mappingHint != null && !mappingHint.isEmpty()) {
            RectF hb = componentBounds.get(id);
            float hintY = hb != null ? hb.bottom + 6f * density : cy + halfH + 6f * density;
            drawMappingHintCallout(canvas, cx, hintY, mappingHint);
        }
        drawKeyboardHoldLockBadgeIfNeeded(canvas, id);
    }

    private void drawTouchpadModule(Canvas canvas, @NonNull GamepadLayoutPresetDocument.GamepadModule m,
                                    float cx, float cy, float ww, float hh) {
        String id = m.id;
        @Nullable Integer moduleAccentArgb = m.moduleAccentArgb;
        float density = getResources().getDisplayMetrics().density;
        float corner = 10f * density;
        float borderW = 1f * density;
        RectF bounds = new RectF(cx - ww / 2f, cy - hh / 2f, cx + ww / 2f, cy + hh / 2f);
        componentBounds.put(id, bounds);

        retroShadowPaint.setMaskFilter(new BlurMaskFilter(3.5f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x38000000);
        RectF shadowBounds = new RectF(bounds);
        shadowBounds.offset(0, 1.5f * density);
        canvas.drawRoundRect(shadowBounds, corner, corner, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);

        int surfTop = Color.parseColor("#EEF0F4");
        int surfBot = Color.parseColor("#D8DCE3");
        int borderCol = Color.parseColor("#A7ADB8");
        int labelCol = Color.parseColor("#5C6169");
        if (moduleAccentArgb != null) {
            int ac = GamepadModuleAccent.toOpaqueArgb(moduleAccentArgb);
            surfTop = ColorUtils.blendARGB(surfTop, ac, 0.38f);
            surfBot = ColorUtils.blendARGB(surfBot, ac, 0.42f);
            borderCol = ColorUtils.blendARGB(borderCol, ac, 0.45f);
            labelCol = ColorUtils.blendARGB(labelCol, ac, 0.35f);
        }
        if (m.displayLabelColorArgb != null) {
            labelCol = GamepadModuleAccent.toOpaqueArgb(m.displayLabelColorArgb);
        }
        Shader surface = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                surfTop, surfBot, Shader.TileMode.CLAMP);
        retroBodyPaint.setShader(surface);
        canvas.drawRoundRect(bounds, corner, corner, retroBodyPaint);
        retroBodyPaint.setShader(null);

        retroGlossPaint.setShader(null);
        retroGlossPaint.setColor(0x14000000);
        float step = 20f * density;
        for (float px = bounds.left + step; px < bounds.right - step * 0.25f; px += step) {
            for (float py = bounds.top + step; py < bounds.bottom - step * 0.25f; py += step) {
                canvas.drawCircle(px, py, 0.65f * density, retroGlossPaint);
            }
        }

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(borderW);
        retroRingPaint.setColor(borderCol);
        retroRingPaint.setShader(null);
        canvas.drawRoundRect(bounds, corner, corner, retroRingPaint);
        retroRingPaint.setColor(0x55FFFFFF);
        retroRingPaint.setStrokeWidth(Math.max(0.75f, 0.65f * density));
        RectF inset = new RectF(bounds);
        float insetPx = 0.8f * density;
        inset.inset(insetPx, insetPx);
        float ir = Math.max(corner - insetPx, 2f * density);
        canvas.drawRoundRect(inset, ir, ir, retroRingPaint);

        if (keyMappingHintsVisible) {
            String title = presetDisplayLabel(m);
            if (title == null || title.isEmpty()) {
                title = "TOUCHPAD";
            }
            retroTextPaint.setColor(labelCol);
            retroTextPaint.setTextAlign(Paint.Align.CENTER);
            retroTextPaint.setTextSize(Math.min(ww, hh) * 0.095f);
            retroTextPaint.setFakeBoldText(true);
            retroTextPaint.setLetterSpacing(0.12f);
            retroTextPaint.setTypeface(retroLabelTypeface);
            canvas.drawText(title, cx, cy + retroTextPaint.getTextSize() * 0.32f, retroTextPaint);
            retroTextPaint.setLetterSpacing(0f);
        }
    }

    private void drawScrollStripModule(Canvas canvas, @NonNull GamepadLayoutPresetDocument.GamepadModule m,
                                       float cx, float cy, float ww, float hh) {
        String id = m.id;
        @Nullable Integer moduleAccentArgb = m.moduleAccentArgb;
        float density = getResources().getDisplayMetrics().density;
        float corner = 8f * density;
        float borderW = 1f * density;
        RectF bounds = new RectF(cx - ww / 2f, cy - hh / 2f, cx + ww / 2f, cy + hh / 2f);
        componentBounds.put(id, bounds);

        retroShadowPaint.setMaskFilter(new BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x34000000);
        RectF shadowBounds = new RectF(bounds);
        shadowBounds.offset(0, 1.25f * density);
        canvas.drawRoundRect(shadowBounds, corner, corner, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);

        int surfTop = Color.parseColor("#E4E7EE");
        int surfBot = Color.parseColor("#CED3DC");
        int borderCol = Color.parseColor("#9AA0AC");
        int labelCol = Color.parseColor("#4B5058");
        if (moduleAccentArgb != null) {
            int ac = GamepadModuleAccent.toOpaqueArgb(moduleAccentArgb);
            surfTop = ColorUtils.blendARGB(surfTop, ac, 0.32f);
            surfBot = ColorUtils.blendARGB(surfBot, ac, 0.36f);
            borderCol = ColorUtils.blendARGB(borderCol, ac, 0.4f);
            labelCol = ColorUtils.blendARGB(labelCol, ac, 0.3f);
        }
        if (m.displayLabelColorArgb != null) {
            labelCol = GamepadModuleAccent.toOpaqueArgb(m.displayLabelColorArgb);
        }
        Shader surface = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                surfTop, surfBot, Shader.TileMode.CLAMP);
        retroBodyPaint.setShader(surface);
        canvas.drawRoundRect(bounds, corner, corner, retroBodyPaint);
        retroBodyPaint.setShader(null);

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(borderW);
        retroRingPaint.setColor(borderCol);
        retroRingPaint.setShader(null);
        canvas.drawRoundRect(bounds, corner, corner, retroRingPaint);

        Context ctx = getContext();
        if (ctx != null) {
            int chevronTint = ContextCompat.getColor(ctx, R.color.km_basic_scroll_strip_chevron);
            Drawable up = AppCompatResources.getDrawable(ctx, R.drawable.km_basic_scroll_strip_chevron_up);
            Drawable dn = AppCompatResources.getDrawable(ctx, R.drawable.km_basic_scroll_strip_chevron_down);
            if (up != null && dn != null) {
                up = DrawableCompat.wrap(up.mutate());
                dn = DrawableCompat.wrap(dn.mutate());
                DrawableCompat.setTint(up, chevronTint);
                DrawableCompat.setTint(dn, chevronTint);
                float pad = 6f * density;
                float maxChev = 22f * density;
                float chevSize = Math.min(maxChev, ww * 0.72f);
                chevSize = Math.max(6f * density, chevSize);
                int left = Math.round(cx - chevSize / 2f);
                int top = Math.round(bounds.top + pad);
                up.setBounds(left, top, Math.round(left + chevSize), Math.round(top + chevSize));
                up.draw(canvas);
                int bottomTop = Math.round(bounds.bottom - pad - chevSize);
                dn.setBounds(left, bottomTop, Math.round(left + chevSize), Math.round(bounds.bottom - pad));
                dn.draw(canvas);
            }
        }

        if (keyMappingHintsVisible) {
            String title = presetDisplayLabel(m);
            if (title == null || title.isEmpty()) {
                title = "SCROLL";
            }
            retroTextPaint.setColor(labelCol);
            retroTextPaint.setTextAlign(Paint.Align.CENTER);
            retroTextPaint.setTextSize(Math.min(ww, hh) * 0.11f);
            retroTextPaint.setFakeBoldText(true);
            retroTextPaint.setLetterSpacing(0.08f);
            retroTextPaint.setTypeface(retroLabelTypeface);
            canvas.drawText(title, cx, cy + retroTextPaint.getTextSize() * 0.28f, retroTextPaint);
            retroTextPaint.setLetterSpacing(0f);
        }
    }

    @Nullable
    private GamepadLayoutPresetDocument.GamepadModule findDynamicModuleById(@Nullable String moduleId) {
        if (layoutDocument == null || layoutDocument.modules == null || moduleId == null) {
            return null;
        }
        for (GamepadLayoutPresetDocument.GamepadModule mod : layoutDocument.modules) {
            if (mod != null && moduleId.equals(mod.id)) {
                return mod;
            }
        }
        return null;
    }

    private void applyScrollStripMoveForPointer(int pointerId, @NonNull MotionEvent event) {
        ScrollStripPointerState st = scrollStripPointerState.get(pointerId);
        if (st == null || scrollStripWheelListener == null) {
            return;
        }
        int idx = event.findPointerIndex(pointerId);
        if (idx < 0) {
            return;
        }
        float ny = event.getY(idx);
        float dy = ny - st.lastY;
        st.lastY = ny;
        GamepadLayoutPresetDocument.GamepadModule mod = findDynamicModuleById(st.moduleId);
        float sensitivity = 1f;
        if (mod != null && mod.scrollStripSensitivity != null) {
            sensitivity = mod.scrollStripSensitivity;
        }
        if (mod != null && Boolean.TRUE.equals(mod.scrollStripInvertY)) {
            dy = -dy;
        }
        st.accumY += (-dy / SCROLL_STRIP_PIXELS_PER_WHEEL_UNIT) * sensitivity;
        int sy = (int) st.accumY;
        if (sy != 0) {
            st.accumY -= sy;
            scrollStripWheelListener.onScrollStripWheel(st.moduleId, 0, sy);
        }
    }

    /**
     * Analog stick for SIMPLE v2: {@code boundsId} is stick_left / stick_right (matches positions JSON).
     * @param stickVisualVariant optional preset {@code stickVisualVariant} (draw overlay); null for legacy layouts.
     * @param accentPrimary theme or per-module accent for cap tint and overlays.
     */
    private void drawAnalogStickForModule(Canvas canvas, float cx, float cy, float baseRadius, float moduleScale,
                                          String boundsId, String shortLabel,
                                          String upLabel, String downLabel, String leftLabel, String rightLabel,
                                          @androidx.annotation.Nullable String stickVisualVariant,
                                          int accentPrimary,
                                          @Nullable GamepadLayoutPresetDocument.GamepadModule stickModule) {
        // Preset modules carry size in {@code moduleScale}; legacy SIMPLE uses global {@link #stickSizeScale}.
        float globalStickMul = useDynamicLayout() ? 1f : stickSizeScale;
        float scaledRadius = baseRadius * globalStickMul * moduleScale;
        float density = getResources().getDisplayMetrics().density;
        RectF bounds = new RectF(cx - scaledRadius, cy - scaledRadius, cx + scaledRadius, cy + scaledRadius);
        componentBounds.put(boundsId, bounds);

        final boolean rightStickFlatUi = "stick_right".equals(boundsId) || "stick_r".equals(boundsId);

        int outerHi = ColorUtils.blendARGB(Color.parseColor("#5E5E66"), accentPrimary, 0.28f);
        int outerLo = ColorUtils.blendARGB(Color.parseColor("#38383F"), accentPrimary, 0.34f);
        if (rightStickFlatUi) {
            int outerFlat = ColorUtils.blendARGB(outerHi, outerLo, 0.5f);
            retroBodyPaint.setShader(null);
            retroBodyPaint.setColor(outerFlat);
            canvas.drawCircle(cx, cy, scaledRadius, retroBodyPaint);
        } else {
            float hx = cx;
            float hy = cy - scaledRadius * 0.14f;
            Shader outer = new RadialGradient(hx, hy, scaledRadius * 1.05f,
                    outerHi, outerLo, Shader.TileMode.CLAMP);
            retroBodyPaint.setShader(outer);
            canvas.drawCircle(cx, cy, scaledRadius, retroBodyPaint);
            retroBodyPaint.setShader(null);
        }

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, 1.75f * density));
        retroRingPaint.setColor(ColorUtils.blendARGB(Color.parseColor("#6A6A74"), accentPrimary, 0.22f));
        canvas.drawCircle(cx, cy, scaledRadius * 0.995f, retroRingPaint);

        float innerCx = cx;
        float innerCy = cy;
        float ox = 0;
        float oy = 0;
        if (useDynamicLayout() && dynamicStickOffset.containsKey(boundsId)) {
            float[] d = dynamicStickOffset.get(boundsId);
            ox = d[0];
            oy = d[1];
        } else if (boundsId.equals(activeStickId)) {
            ox = stickOffsetX;
            oy = stickOffsetY;
        }
        innerCx += ox;
        innerCy += oy;

        boolean hubKeyCenter = activeStickDirections.contains(boundsId + "_center");
        boolean centerTap = hubKeyCenter || (boundsId.equals(activeStickId)
                && Math.abs(ox) < scaledRadius * 0.15f && Math.abs(oy) < scaledRadius * 0.15f);
        float capR = scaledRadius * 0.54f;
        int capHi = ColorUtils.blendARGB(Color.parseColor("#8E8C98"), accentPrimary, 0.24f);
        int capLo = ColorUtils.blendARGB(Color.parseColor("#4F4D56"), accentPrimary, 0.30f);
        if (rightStickFlatUi) {
            int capFlat = centerTap
                    ? ColorUtils.blendARGB(applyAlphaInt(accentPrimary, 190), darkenArgb(accentPrimary, 0.55f), 0.52f)
                    : ColorUtils.blendARGB(capHi, capLo, 0.52f);
            retroBodyPaint.setShader(null);
            retroBodyPaint.setColor(capFlat);
            canvas.drawCircle(innerCx, innerCy, capR, retroBodyPaint);
        } else {
            Shader capShader;
            if (centerTap) {
                capShader = new RadialGradient(innerCx - capR * 0.2f, innerCy - capR * 0.22f, capR * 1.05f,
                        applyAlphaInt(accentPrimary, 185),
                        darkenArgb(accentPrimary, 0.58f),
                        Shader.TileMode.CLAMP);
            } else {
                capShader = new RadialGradient(innerCx - capR * 0.22f, innerCy - capR * 0.24f, capR * 1.02f,
                        capHi, capLo, Shader.TileMode.CLAMP);
            }
            retroBodyPaint.setShader(capShader);
            canvas.drawCircle(innerCx, innerCy, capR, retroBodyPaint);
            retroBodyPaint.setShader(null);

            Shader spec = new RadialGradient(innerCx - capR * 0.28f, innerCy - capR * 0.28f, capR * 0.42f,
                    0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            retroGlossPaint.setShader(spec);
            canvas.drawCircle(innerCx, innerCy, capR * 0.93f, retroGlossPaint);
            retroGlossPaint.setShader(null);
        }

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1f, 1.1f * density));
        retroRingPaint.setColor(applyAlphaInt(0xFF000000, 45));
        canvas.drawCircle(innerCx, innerCy, capR * 0.99f, retroRingPaint);

        GamepadStickVisualArt.drawCapOverlay(canvas, rightStickFlatUi ? null : stickVisualVariant, innerCx, innerCy, capR,
                accentPrimary, retroBodyPaint, retroRingPaint, retroGlossPaint);

        String presetCap = stickModule != null ? presetDisplayLabel(stickModule) : null;
        boolean hasPresetCap = presetCap != null && !presetCap.isEmpty();
        int presetCapArgb = Color.parseColor("#F2F1F5");
        if (hasPresetCap && stickModule != null && stickModule.displayLabelColorArgb != null) {
            presetCapArgb = GamepadModuleAccent.toOpaqueArgb(stickModule.displayLabelColorArgb);
        }

        if (upLabel != null) {
            if (keyMappingHintsVisible) {
                float lr = scaledRadius * 1.08f;
                Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                dirPaint.setTextAlign(Paint.Align.CENTER);
                dirPaint.setFakeBoldText(true);
                dirPaint.setTextSize(lr * 0.20f);
                dirPaint.setTypeface(retroLabelTypeface);
                dirPaint.setShadowLayer(1f, 0f, 0.75f, 0x55000000);
                int activeDir = Color.parseColor("#FFFEFB");
                int dim = Color.parseColor("#9A96A3");
                dirPaint.setColor(activeStickDirections.contains(boundsId + "_up") ? activeDir : dim);
                canvas.drawText(upLabel, cx, cy - lr * 0.62f, dirPaint);
                dirPaint.setColor(activeStickDirections.contains(boundsId + "_down") ? activeDir : dim);
                canvas.drawText(downLabel, cx, cy + lr * 0.78f, dirPaint);
                dirPaint.setColor(activeStickDirections.contains(boundsId + "_left") ? activeDir : dim);
                canvas.drawText(leftLabel, cx - lr * 0.74f, cy + lr * 0.12f, dirPaint);
                dirPaint.setColor(activeStickDirections.contains(boundsId + "_right") ? activeDir : dim);
                canvas.drawText(rightLabel, cx + lr * 0.74f, cy + lr * 0.12f, dirPaint);
                dirPaint.clearShadowLayer();
            }
        }
        if (hasPresetCap) {
            drawStickInnerCapLabel(canvas, innerCx, innerCy, capR, presetCap, presetCapArgb);
        } else if (upLabel == null && !rightStickFlatUi) {
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.parseColor("#F2F1F5"));
            labelPaint.setTextSize(baseRadius * moduleScale * 0.48f);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setFakeBoldText(true);
            labelPaint.setTypeface(retroLabelTypeface);
            labelPaint.setShadowLayer(1.25f, 0f, 0.75f, 0x50000000);
            canvas.drawText(shortLabel, cx, cy + baseRadius * moduleScale * 0.28f, labelPaint);
            labelPaint.clearShadowLayer();
            if (keyMappingHintsVisible) {
                Paint l3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                l3Paint.setColor(Color.parseColor("#A8A4B0"));
                l3Paint.setTextSize(baseRadius * moduleScale * 0.22f);
                l3Paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("CLICK", cx, cy + baseRadius * moduleScale * 0.58f, l3Paint);
            }
        } else if (upLabel == null && rightStickFlatUi) {
            drawStickInnerCapLabel(canvas, innerCx, innerCy, capR, shortLabel, Color.parseColor("#F2F1F5"));
        }
    }

    /**
     * D-pad module draw ({@link GamepadLayoutPresetConstants#MODULE_TYPE_DPAD}).
     */
    private void drawDpadForModule(Canvas canvas, float cx, float cy, float baseRadius, float moduleScale,
                                   String boundsId, String dpadVariant, String upLabel, String downLabel,
                                   String leftLabel, String rightLabel, @Nullable Float dpadSplitGapRatio,
                                   @Nullable Float dpadSplitOuterReachRatio, int accentPrimary,
                                   boolean showMappingHints, float labelRadialScale,
                                   @NonNull String crossArmDecoration,
                                   @Nullable Drawable[] dpadDirectionIcons,
                                   @Nullable String centerHubLabel,
                                   @Nullable Integer centerHubLabelArgb) {
        float density = getResources().getDisplayMetrics().density;
        float globalStickMul = useDynamicLayout() ? 1f : stickSizeScale;
        GamepadDpadVariantArt.draw(canvas, cx, cy, baseRadius, density, globalStickMul, moduleScale,
                boundsId, dpadVariant, upLabel, downLabel, leftLabel, rightLabel,
                activeStickDirections, accentPrimary,
                retroDpadFillPaint, retroRingPaint, retroGlossPaint, retroShadowPaint, retroBodyPaint,
                retroLabelTypeface, componentBounds, dynamicHitTestOrder, dpadSplitGapRatio,
                dpadSplitOuterReachRatio, showMappingHints, labelRadialScale,
                crossArmDecoration, dpadDirectionIcons, centerHubLabel, centerHubLabelArgb);
    }

    private void ensureDpadDirectionIconDrawables() {
        if (dpadDirectionIconPool != null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        Drawable up = AppCompatResources.getDrawable(ctx, R.drawable.ic_dpad_arrow_up);
        Drawable dn = AppCompatResources.getDrawable(ctx, R.drawable.ic_dpad_arrow_down);
        Drawable lf = AppCompatResources.getDrawable(ctx, R.drawable.ic_dpad_arrow_left);
        Drawable rt = AppCompatResources.getDrawable(ctx, R.drawable.ic_dpad_arrow_right);
        dpadDirectionIconPool = new Drawable[] {
                up != null ? up.mutate() : null,
                dn != null ? dn.mutate() : null,
                lf != null ? lf.mutate() : null,
                rt != null ? rt.mutate() : null,
        };
    }

    private void drawShoulderCapsuleForModule(Canvas canvas, float cx, float cy, float width, float height,
                                              String moduleId, @Nullable String capOnCapsule,
                                              @Nullable String mappingHint, int accentPrimary,
                                              @Nullable Integer capLabelColorArgb) {
        float density = getResources().getDisplayMetrics().density;
        RectF bounds = new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        componentBounds.put(moduleId, bounds);
        float cr = Math.min(height * 0.45f, width * 0.22f);
        boolean pressed = moduleId.equals(pressedComponentId);

        retroShadowPaint.setMaskFilter(new BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x44000000);
        RectF sh = new RectF(bounds);
        sh.offset(0, 2f * density);
        canvas.drawRoundRect(sh, cr, cr, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);

        int top = pressed ? applyAlphaInt(accentPrimary, 220) : Color.parseColor("#6A6872");
        int bot = pressed ? darkenArgb(accentPrimary, 0.75f) : Color.parseColor("#45434C");
        Shader lg = new LinearGradient(cx, bounds.top, cx, bounds.bottom, top, bot, Shader.TileMode.CLAMP);
        retroBodyPaint.setShader(lg);
        canvas.drawRoundRect(bounds, cr, cr, retroBodyPaint);
        retroBodyPaint.setShader(null);

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, 1.2f * density));
        retroRingPaint.setColor(pressed ? accentPrimary : Color.parseColor("#B0B0B8"));
        canvas.drawRoundRect(bounds, cr, cr, retroRingPaint);

        if (capOnCapsule != null && !capOnCapsule.isEmpty()) {
            int capCol = capLabelColorArgb != null
                    ? GamepadModuleAccent.toOpaqueArgb(capLabelColorArgb)
                    : Color.WHITE;
            drawFittedCapsuleLabel(canvas, bounds, capOnCapsule, capCol);
        }
        if (keyMappingHintsVisible && mappingHint != null && !mappingHint.isEmpty()) {
            drawMappingHintCallout(canvas, cx, bounds.bottom + 4f * density, mappingHint);
        }
        drawKeyboardHoldLockBadgeIfNeeded(canvas, moduleId);
    }

    private void drawKeyboardHoldLockBadgeIfNeeded(Canvas canvas, String moduleId) {
        LatchBadgeKind kind = gestureLatchBadgesByModuleId.get(moduleId);
        if (kind == null) {
            return;
        }
        RectF hb = componentBounds.get(moduleId);
        if (hb == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float minSide = Math.min(hb.width(), hb.height());
        float sizePx = minSide * LATCH_BADGE_FRACTION_OF_MIN_SIDE;
        float minPx = LATCH_BADGE_MIN_DP * density;
        float maxPx = LATCH_BADGE_MAX_DP * density;
        sizePx = Math.max(minPx, Math.min(sizePx, maxPx));
        int size = Math.max(1, Math.round(sizePx));
        int resId = kind == LatchBadgeKind.TURBO ? R.drawable.ic_gamepad_turbo_latch_24 : R.drawable.ic_lock_24;
        Drawable d = AppCompatResources.getDrawable(getContext(), resId);
        if (d == null) {
            return;
        }
        d = d.mutate();
        int tint = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.WHITE);
        d.setTint(tint);
        d.setAlpha(LATCH_BADGE_DRAWABLE_ALPHA);
        float cx = hb.centerX();
        float cy = hb.centerY();
        int left = Math.round(cx - size * 0.5f);
        int top = Math.round(cy - size * 0.5f);
        d.setBounds(left, top, left + size, top + size);
        d.draw(canvas);
    }

    private void drawXboxLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.15f, h * 0.5f, 120);

        // Left Stick - 150% larger
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 90, "L");

        // Right Stick - 150% larger
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 90, "R");

        // ABXY Buttons - 150% larger
        drawButton(canvas, w * 0.85f, h * 0.55f, 52.5f, "A", Color.parseColor("#4CAF50"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 52.5f, "B", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 52.5f, "X", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 52.5f, "Y", Color.parseColor("#FFC107"));

        // Shoulders - 150% larger
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 75, 30, "LB");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 75, 30, "RB");

        // Center buttons - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.40f, 37.5f, "≡", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 37.5f, "⊞", Color.GRAY);
    }

    private void drawPlayStationLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.15f, h * 0.5f, 120);

        // Left Stick - 150% larger
        drawAnalogStick(canvas, w * 0.25f, h * 0.65f, 90, "L");

        // Right Stick - 150% larger
        drawAnalogStick(canvas, w * 0.75f, h * 0.65f, 90, "R");

        // △○×□ Buttons - 150% larger
        drawButton(canvas, w * 0.85f, h * 0.55f, 52.5f, "×", Color.parseColor("#2196F3"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 52.5f, "○", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.80f, h * 0.45f, 52.5f, "□", Color.parseColor("#9C27B0"));
        drawButton(canvas, w * 0.85f, h * 0.35f, 52.5f, "△", Color.parseColor("#4CAF50"));

        // L1/R1 - 150% larger
        drawShoulderButton(canvas, w * 0.30f, h * 0.10f, 75, 30, "L1");
        drawShoulderButton(canvas, w * 0.70f, h * 0.10f, 75, 30, "R1");

        // Center buttons - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.40f, 37.5f, "Select", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.40f, 37.5f, "Start", Color.GRAY);
    }

    private void drawNESLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // D-Pad - 150% larger
        drawDpad(canvas, w * 0.20f, h * 0.5f, 150);

        // A/B Buttons - 150% larger
        drawButton(canvas, w * 0.80f, h * 0.55f, 67.5f, "A", Color.parseColor("#F44336"));
        drawButton(canvas, w * 0.90f, h * 0.45f, 67.5f, "B", Color.parseColor("#F44336"));

        // Select/Start - 150% larger
        drawButton(canvas, w * 0.40f, h * 0.70f, 45, "SELECT", Color.GRAY);
        drawButton(canvas, w * 0.60f, h * 0.70f, 45, "START", Color.GRAY);
    }

    private void drawSimpleLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // Get configured display labels (default to "L" and "Enter")
        String stickLabel = componentDisplayLabels.getOrDefault("stick_l", "L");
        String stickUpLabel = componentDisplayLabels.getOrDefault("stick_up", "W");
        String stickDownLabel = componentDisplayLabels.getOrDefault("stick_down", "S");
        String stickLeftLabel = componentDisplayLabels.getOrDefault("stick_left", "A");
        String stickRightLabel = componentDisplayLabels.getOrDefault("stick_right", "D");
        String buttonLabel = componentDisplayLabels.getOrDefault("button_a", "Enter");
        String buttonBLabel = componentDisplayLabels.getOrDefault("button_b", "Esc");

        // Use saved positions with defaults
        float stickX = getPositionX("stick_left", 0.20f) * w;
        float stickY = getPositionY("stick_left", 0.50f) * h;
        float buttonAX = getPositionX("button_a", 0.85f) * w;
        float buttonAY = getPositionY("button_a", 0.50f) * h;
        float buttonBX = getPositionX("button_b", 0.93f) * w;
        float buttonBY = getPositionY("button_b", 0.40f) * h;

        // Analog stick on the left - 150% larger
        drawAnalogStick(canvas, stickX, stickY, 180, stickLabel,
                stickUpLabel, stickDownLabel, stickLeftLabel, stickRightLabel);

        if (showTwoButtons) {
            // Two buttons side by side on the right
            drawButton(canvas, buttonAX, buttonAY, 100, "A", Color.parseColor("#4CAF50"), buttonLabel);
            drawButton(canvas, buttonBX, buttonBY, 100, "B", Color.parseColor("#F44336"), buttonBLabel);
        } else {
            // Single big button on the far right - 150% larger, with configured label
            drawButton(canvas, buttonAX, buttonAY, 135, "A", Color.parseColor("#4CAF50"), buttonLabel);
        }
    }

    private void drawDpad(Canvas canvas, float cx, float cy, float size) {
        float density = getResources().getDisplayMetrics().density;
        float half = size / 2;
        float barHalf = half * 0.4f;

        componentBounds.put("dpad_up",
                new RectF(cx - barHalf, cy - half, cx + barHalf, cy));
        componentBounds.put("dpad_down",
                new RectF(cx - barHalf, cy, cx + barHalf, cy + half));
        componentBounds.put("dpad_left",
                new RectF(cx - half, cy - barHalf, cx, cy + barHalf));
        componentBounds.put("dpad_right",
                new RectF(cx, cy - barHalf, cx + half, cy + barHalf));

        float corner = Math.min(14f * density, barHalf * 0.45f);
        RectF vert = new RectF(cx - barHalf, cy - half, cx + barHalf, cy + half);
        RectF horiz = new RectF(cx - half, cy - barHalf, cx + half, cy + barHalf);

        Shader deep = new LinearGradient(cx, cy - half, cx, cy + half,
                Color.parseColor("#2A2730"), Color.parseColor("#393441"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setStyle(Paint.Style.FILL);
        retroDpadFillPaint.setShader(deep);
        retroWorkPath.reset();
        retroWorkPath.addRoundRect(vert, corner, corner, Path.Direction.CW);
        canvas.drawPath(retroWorkPath, retroDpadFillPaint);
        retroWorkPath.reset();
        retroWorkPath.addRoundRect(horiz, corner, corner, Path.Direction.CW);
        canvas.drawPath(retroWorkPath, retroDpadFillPaint);
        retroDpadFillPaint.setShader(null);

        int pressFill = applyAlphaInt(themeAccentPrimary, 200);
        retroGlossPaint.setShader(null);
        retroGlossPaint.setColor(pressFill);
        if (isDpadDirPressed("up")) {
            canvas.drawRect(cx - barHalf, cy - half, cx + barHalf, cy, retroGlossPaint);
        }
        if (isDpadDirPressed("down")) {
            canvas.drawRect(cx - barHalf, cy, cx + barHalf, cy + half, retroGlossPaint);
        }
        if (isDpadDirPressed("left")) {
            canvas.drawRect(cx - half, cy - barHalf, cx, cy + barHalf, retroGlossPaint);
        }
        if (isDpadDirPressed("right")) {
            canvas.drawRect(cx, cy - barHalf, cx + half, cy + barHalf, retroGlossPaint);
        }

        retroRingPaint.setShader(null);
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(2f * density);
        retroRingPaint.setColor(0xFF4A4550);
        retroWorkPath.reset();
        retroWorkPath.addRoundRect(vert, corner, corner, Path.Direction.CW);
        retroWorkPath.addRoundRect(horiz, corner, corner, Path.Direction.CW);
        canvas.drawPath(retroWorkPath, retroRingPaint);

        retroBodyPaint.setShader(null);
        retroBodyPaint.setColor(Color.parseColor("#4A4550"));
        canvas.drawCircle(cx, cy, half * 0.2f, retroBodyPaint);

        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(0xFFE8E8E8);
        arrowPaint.setTextSize(size * 0.15f);
        arrowPaint.setTextAlign(Paint.Align.CENTER);
        arrowPaint.setShadowLayer(2f, 0f, 1f, 0x66000000);
        canvas.drawText("▲", cx, cy - half * 0.7f, arrowPaint);
        canvas.drawText("▼", cx, cy + half * 0.8f, arrowPaint);
        canvas.drawText("◀", cx - half * 0.75f, cy + half * 0.05f, arrowPaint);
        canvas.drawText("▶", cx + half * 0.75f, cy + half * 0.05f, arrowPaint);
        arrowPaint.clearShadowLayer();
    }

    private void drawAnalogStick(Canvas canvas, float cx, float cy, float radius, String label) {
        drawAnalogStick(canvas, cx, cy, radius, label, null, null, null, null);
    }

    private void drawAnalogStick(Canvas canvas, float cx, float cy, float radius, String label,
                                 String upLabel, String downLabel, String leftLabel, String rightLabel) {
        String boundsId = "stick_" + label.toLowerCase();
        drawAnalogStickForModule(canvas, cx, cy, radius, 1f, boundsId, label,
                upLabel, downLabel, leftLabel, rightLabel, null, themeAccentPrimary, null);
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int color) {
        drawButton(canvas, cx, cy, radius, label, color, null);
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int color, String displayLabel) {
        float scaledRadius = radius * buttonSizeScale;
        String id = "button_" + label.toLowerCase().replace("(", "").replace(")", "");
        RectF bounds = new RectF(cx - scaledRadius, cy - scaledRadius, cx + scaledRadius, cy + scaledRadius);
        componentBounds.put(id, bounds);
        FaceStyle fs = faceStyleForFaceLabel(label, color);
        String capOnButton = displayLabel != null ? null : label;
        String mappingHint = displayLabel;
        drawRetroFaceButton(canvas, cx, cy, scaledRadius, fs, id.equals(pressedComponentId), capOnButton, 1f,
                themeAccentPrimary);
        float density = getResources().getDisplayMetrics().density;
        if (mappingHint != null && !mappingHint.isEmpty()) {
            drawMappingHintCallout(canvas, cx, cy + scaledRadius + 6f * density, mappingHint);
        }
    }

    private void drawShoulderButton(Canvas canvas, float cx, float cy, float width, float height, String label) {
        String id = label.toLowerCase();
        float density = getResources().getDisplayMetrics().density;
        RectF bounds = new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        componentBounds.put(id, bounds);
        float cr = 10f * density;
        boolean pressed = id.equals(pressedComponentId);

        retroShadowPaint.setMaskFilter(new BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x44000000);
        RectF sh = new RectF(bounds);
        sh.offset(0, 2f * density);
        canvas.drawRoundRect(sh, cr, cr, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);

        int top = pressed ? applyAlphaInt(themeAccentPrimary, 220) : Color.parseColor("#7A7880");
        int bot = pressed ? darkenArgb(themeAccentPrimary, 0.75f) : Color.parseColor("#4E4D55");
        Shader lg = new LinearGradient(cx, bounds.top, cx, bounds.bottom, top, bot, Shader.TileMode.CLAMP);
        retroBodyPaint.setShader(lg);
        canvas.drawRoundRect(bounds, cr, cr, retroBodyPaint);
        retroBodyPaint.setShader(null);

        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, 1.2f * density));
        retroRingPaint.setColor(pressed ? themeAccentPrimary : Color.parseColor("#B0B0B8"));
        canvas.drawRoundRect(bounds, cr, cr, retroRingPaint);

        retroTextPaint.setColor(Color.WHITE);
        retroTextPaint.setTextSize(Math.min(width, height) * 0.42f);
        retroTextPaint.setTextAlign(Paint.Align.CENTER);
        retroTextPaint.setFakeBoldText(true);
        retroTextPaint.setTypeface(retroLabelTypeface);
        retroTextPaint.setShadowLayer(1.5f, 0f, 1f, 0x44000000);
        canvas.drawText(label, cx, cy + height * 0.12f, retroTextPaint);
        retroTextPaint.clearShadowLayer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (useDynamicLayout()) {
            return onTouchDynamicLayout(event);
        }
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);

                    // Config long press only while editing (play mode: no menu on hold)
                    if (longPressEnabled && isEditMode) {
                        longPressComponentId = componentId;
                        longPressDownX = x;
                        longPressDownY = y;
                        longPressCancelled = false;
                        longPressRunnable = () -> {
                            if (longPressListener != null && longPressComponentId != null && !longPressCancelled) {
                                longPressListener.onComponentLongPress(longPressComponentId);
                            }
                        };
                        longPressHandler.postDelayed(longPressRunnable, longPressThresholdMs);
                    }

                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                    } else if (componentId.startsWith("stick_") && !isEditMode) {
                        activeStickId = componentId;
                        activeStickPointerId = pointerId;
                        RectF stickBounds = componentBounds.get(componentId);
                        activeStickCenterX = stickBounds.centerX();
                        activeStickCenterY = stickBounds.centerY();
                        activeStickRadius  = stickBounds.width() / 2f;
                        float rdx = x - activeStickCenterX;
                        float rdy = y - activeStickCenterY;
                        float rdist = (float) Math.sqrt(rdx * rdx + rdy * rdy);
                        if (rdist > activeStickRadius) {
                            rdx = rdx * activeStickRadius / rdist;
                            rdy = rdy * activeStickRadius / rdist;
                        }
                        stickOffsetX = rdx;
                        stickOffsetY = rdy;
                        invalidate();
                        if (analogStickListener != null) {
                            String label = componentId.replace("stick_", "");
                            analogStickListener.onAnalogStickMoved(
                                    label, rdx / activeStickRadius, rdy / activeStickRadius);
                        }
                    } else if (isEditMode) {
                        draggedComponentId = componentId;
                        dragPointerId = pointerId;
                    } else {
                        pressedComponentId = componentId;
                        buttonsPressedSet.add(componentId);
                        if (buttonPressListener != null) {
                            int keyCode = getKeyCodeForComponent(componentId);
                            buttonPressListener.onButtonPress(componentId, keyCode);
                        }
                        invalidate();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);

                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                    } else if (!isEditMode && buttonPressListener != null) {
                        buttonsPressedSet.add(componentId);
                        int keyCode = getKeyCodeForComponent(componentId);
                        buttonPressListener.onButtonPress(componentId, keyCode);
                    }
                } else if (event.getPointerCount() >= 2 && backgroundBitmap != null) {
                    // Start two-finger background manipulation if this pointer is on empty area
                    int otherIdx = pointerIndex == 0 ? 1 : 0;
                    float ox = event.getX(otherIdx);
                    float oy = event.getY(otherIdx);
                    String otherComponent = getComponentAt(ox, oy);
                    if (otherComponent == null) {
                        isManipulatingBg = true;
                        float dx = x - ox;
                        float dy = y - oy;
                        bgLastDistance = (float) Math.sqrt(dx * dx + dy * dy);
                        bgLastCenterX = (x + ox) / 2f;
                        bgLastCenterY = (y + oy) / 2f;
                        bgStartOffsetX = bgOffsetX;
                        bgStartOffsetY = bgOffsetY;
                        bgStartScale = bgScale;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                // Cancel long press only if user moved significantly
                if (!longPressCancelled && longPressRunnable != null) {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        float mx = event.getX(i);
                        float my = event.getY(i);
                        float dx = mx - longPressDownX;
                        float dy = my - longPressDownY;
                        if (Math.sqrt(dx * dx + dy * dy) > longPressMoveThresholdPx) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            longPressCancelled = true;
                        }
                    }
                }
                if (isManipulatingBg && event.getPointerCount() >= 2) {
                    // Two-finger pinch-to-zoom and pan on background
                    float x0 = event.getX(0), y0 = event.getY(0);
                    float x1 = event.getX(1), y1 = event.getY(1);
                    float dist = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                    float cx = (x0 + x1) / 2f;
                    float cy = (y0 + y1) / 2f;

                    // Zoom: scale relative to initial fit scale
                    if (bgLastDistance > 0) {
                        bgScale = Math.max(0.1f, bgStartScale * dist / bgLastDistance);
                    }

                    // Pan: offset change in display pixel coords
                    int vw = getWidth();
                    int vh = getHeight();
                    int bw = backgroundBitmap.getWidth();
                    int bh = backgroundBitmap.getHeight();
                    float s = bgInitialScale * bgScale;
                    float dx = (cx - bgLastCenterX) / (s != 0 ? s : 1f);
                    float dy = (cy - bgLastCenterY) / (s != 0 ? s : 1f);
                    bgOffsetX = bgStartOffsetX + dx;
                    bgOffsetY = bgStartOffsetY + dy;

                    bgLastCenterX = cx;
                    bgLastCenterY = cy;
                    bgLastDistance = dist;
                    bgStartOffsetX = bgOffsetX;
                    bgStartOffsetY = bgOffsetY;
                    bgStartScale = bgScale;
                    invalidate();
                    notifyBackgroundViewportChanged();
                } else if (activeStickId != null) {
                    // Find the pointer that's on the stick
                    float stickX = x, stickY = y; // fallback to event pointer
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (event.getPointerId(i) == activeStickPointerId) {
                            stickX = event.getX(i);
                            stickY = event.getY(i);
                            break;
                        }
                    }
                    float dx   = stickX - activeStickCenterX;
                    float dy   = stickY - activeStickCenterY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > activeStickRadius) {
                        dx = dx * activeStickRadius / dist;
                        dy = dy * activeStickRadius / dist;
                    }
                    stickOffsetX = dx;
                    stickOffsetY = dy;
                    invalidate();
                    if (analogStickListener != null) {
                        String label = activeStickId.replace("stick_", "");
                        analogStickListener.onAnalogStickMoved(
                                label, dx / activeStickRadius, dy / activeStickRadius);
                    }
                } else if (draggedComponentId != null && isEditMode) {
                    int dragIdx = dragPointerId >= 0 ? event.findPointerIndex(dragPointerId) : 0;
                    if (dragIdx < 0) {
                        dragIdx = 0;
                    }
                    float moveX = event.getX(dragIdx);
                    float moveY = event.getY(dragIdx);
                    applyDraggedAnchorsFromViewXY(moveX, moveY);
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                // Cancel long press
                if (longPressHandler != null && longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    longPressComponentId = null;
                }
                if (activeStickId != null) {
                    String label = activeStickId.replace("stick_", "");
                    activeStickId = null;
                    activeStickPointerId = -1;
                    stickOffsetX  = 0;
                    stickOffsetY  = 0;
                    invalidate();
                    if (analogStickListener != null) {
                        analogStickListener.onAnalogStickMoved(label, 0, 0);
                    }
                }
                if (draggedComponentId != null) {
                    draggedComponentId = null;
                    dragPointerId = -1;
                }

                // Release component for this pointer
                int legacyUpIdx = event.findPointerIndex(pointerId);
                float legacyUpRawX = legacyUpIdx >= 0 ? event.getRawX(legacyUpIdx) : event.getRawX();
                float legacyUpRawY = legacyUpIdx >= 0 ? event.getRawY(legacyUpIdx) : event.getRawY();
                releasePointerComponent(pointerId, legacyUpRawX, legacyUpRawY, event.getEventTime());

                // For ACTION_UP (last finger), clear all remaining state
                if (action == MotionEvent.ACTION_UP) {
                    pointerComponents.clear();
                    dpadPressedSet.clear();
                    buttonsPressedSet.clear();
                    cancelAllKeyboardHoldLockTracking();
                    if (pressedComponentId != null) {
                        pressedComponentId = null;
                    }
                    dragPointerId = -1;
                }

                if (pressedComponentId != null) {
                    pressedComponentId = null;
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                if (draggedComponentId != null && pointerId == dragPointerId) {
                    draggedComponentId = null;
                    dragPointerId = -1;
                }
                // Release component for the lifted pointer
                int legacyIdx = event.findPointerIndex(pointerId);
                float legacyRawX = legacyIdx >= 0 ? event.getRawX(legacyIdx) : event.getRawX();
                float legacyRawY = legacyIdx >= 0 ? event.getRawY(legacyIdx) : event.getRawY();
                releasePointerComponent(pointerId, legacyRawX, legacyRawY, event.getEventTime());
                return true;
            }
        }
        return true;
    }

    public void setKeyboardHoldLockListener(@Nullable KeyboardHoldLockListener listener) {
        this.keyboardHoldLockListener = listener;
    }

    /**
     * Minimum pointer-down duration before diagonal hold/turbo can commit on lift, and scale on diagonal
     * distance thresholds (see {@link GamepadGestureLockSensitivity}).
     */
    public void setGestureLockCommitSensitivity(int minPressMs, float diagonalRadiusScale) {
        gestureLockMinPressMs = GamepadGestureLockSensitivity.clampMinPressMs(minPressMs);
        gestureLockDiagonalRadiusScale =
                GamepadGestureLockSensitivity.clampRadiusScale(diagonalRadiusScale);
    }

    /**
     * Shows centered latch badges on modules (hold-lock vs turbo). Turbo wins if the same id were
     * present in both latch sets.
     */
    public void setGestureLatchBadges(@Nullable Map<String, LatchBadgeKind> badges) {
        gestureLatchBadgesByModuleId.clear();
        if (badges != null && !badges.isEmpty()) {
            gestureLatchBadgesByModuleId.putAll(badges);
        }
        invalidate();
    }

    /** Dismiss pending hold-lock UI without committing (preset change, detach, edit mode). */
    public void cancelAllKeyboardHoldLockTracking() {
        for (HoldLockTracking t : keyboardHoldLockByPointer.values()) {
            keyboardHoldLockHandler.removeCallbacks(t.showPopupRunnable);
            if (t.popup != null) {
                t.popup.dismiss();
                t.popup = null;
            }
        }
        keyboardHoldLockByPointer.clear();
    }

    @Nullable
    private GamepadLayoutPresetDocument.GamepadModule findGamepadModuleById(@Nullable String moduleId) {
        if (moduleId == null || layoutDocument == null || layoutDocument.modules == null) {
            return null;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : layoutDocument.modules) {
            if (m != null && moduleId.equals(m.id)) {
                return m;
            }
        }
        return null;
    }

    private boolean moduleHasGestureLockFeatures(@Nullable String moduleId) {
        GamepadLayoutPresetDocument.GamepadModule m = findGamepadModuleById(moduleId);
        return GamepadGestureLock.moduleGesturesEnabled(m);
    }

    private void maybeStartKeyboardHoldLockTracking(
            int pointerId, String moduleId, MotionEvent event, int pointerIndex) {
        if (!useDynamicLayout() || isEditMode) {
            return;
        }
        if (!moduleHasGestureLockFeatures(moduleId)) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule mod = findGamepadModuleById(moduleId);
        boolean diagonal = GamepadGestureLock.moduleUsesDiagonalGestureCommit(mod);
        if (keyboardHoldLockListener != null && keyboardHoldLockListener.isGestureLatchedAny(moduleId)) {
            return;
        }
        Runnable r = () -> showHoldLockPopupForPointer(pointerId);
        HoldLockTracking t =
                new HoldLockTracking(
                        moduleId,
                        event.getRawX(pointerIndex),
                        event.getRawY(pointerIndex),
                        event.getEventTime(),
                        diagonal,
                        r);
        keyboardHoldLockByPointer.put(pointerId, t);
        if (diagonal) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        keyboardHoldLockHandler.post(r);
    }

    private void showHoldLockPopupForPointer(int pointerId) {
        HoldLockTracking t = keyboardHoldLockByPointer.get(pointerId);
        if (t == null) {
            return;
        }
        String fingerComp = pointerComponents.get(pointerId);
        if (fingerComp == null || !fingerComp.equals(t.moduleId)) {
            keyboardHoldLockByPointer.remove(pointerId);
            return;
        }
        if (componentBounds.get(t.moduleId) == null) {
            return;
        }
        if (t.popup != null) {
            return;
        }
        if (t.diagonalMode) {
            return;
        }
        t.popup = new BasicHoldLockPopup(true);
        t.popup.showAboveScreenPoint(this, t.lastRawX, t.lastRawY);
    }

    private void updateKeyboardHoldLockOnDynamicMove(MotionEvent event) {
        if (keyboardHoldLockByPointer.isEmpty()) {
            return;
        }
        float cancelPx = KEYBOARD_HOLD_LOCK_CANCEL_MOVE_DP * getResources().getDisplayMetrics().density;
        for (Integer pid : new ArrayList<>(keyboardHoldLockByPointer.keySet())) {
            int idx = event.findPointerIndex(pid);
            if (idx < 0) {
                continue;
            }
            HoldLockTracking t = keyboardHoldLockByPointer.get(pid);
            if (t == null) {
                continue;
            }
            float rx = event.getRawX(idx);
            float ry = event.getRawY(idx);
            t.lastRawX = rx;
            t.lastRawY = ry;
            if (t.diagonalMode) {
                continue;
            }
            if (t.popup != null) {
                t.popup.updatePointer(rx, ry);
            } else {
                float ddx = rx - t.downRawX;
                float ddy = ry - t.downRawY;
                if (Math.hypot(ddx, ddy) > cancelPx) {
                    keyboardHoldLockHandler.removeCallbacks(t.showPopupRunnable);
                    keyboardHoldLockByPointer.remove(pid);
                }
            }
        }
    }

    private boolean endKeyboardHoldLockForPointerIfAny(
            int pointerId, float rawX, float rawY, long pointerUpEventTimeMs) {
        HoldLockTracking t = keyboardHoldLockByPointer.remove(pointerId);
        if (t == null) {
            return false;
        }
        keyboardHoldLockHandler.removeCallbacks(t.showPopupRunnable);
        boolean committed = false;
        if (t.diagonalMode) {
            GamepadLayoutPresetDocument.GamepadModule mod = findGamepadModuleById(t.moduleId);
            float density = getResources().getDisplayMetrics().density;
            float dx = rawX - t.downRawX;
            float dy = rawY - t.downRawY;
            float rMinDp = GamepadGestureLock.DIAGONAL_R_MIN_DP * gestureLockDiagonalRadiusScale;
            float rCancelDp = GamepadGestureLock.DIAGONAL_R_CANCEL_DP * gestureLockDiagonalRadiusScale;
            String slot =
                    GamepadGestureLock.classifyDiagonalSlot(dx, dy, density, rMinDp, rCancelDp);
            if (slot != null && !GamepadGestureLock.RESULT_CANCEL.equals(slot)) {
                String action = GamepadGestureLock.resolvedActionForSlot(mod, slot);
                if (mod != null
                        && !GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_NONE.equalsIgnoreCase(action)
                        && keyboardHoldLockListener != null) {
                    long dwell = pointerUpEventTimeMs - t.downEventTimeMs;
                    if (dwell < gestureLockMinPressMs) {
                        return false;
                    }
                    GamepadLayoutPresetDocument.GestureLockSlot slotObj =
                            GamepadGestureLock.slotForKey(mod.gestureLock, slot);
                    Integer hk = slotObj != null ? slotObj.hidKey : null;
                    Integer mm = slotObj != null ? slotObj.modifierMask : null;
                    keyboardHoldLockListener.onGestureLockActionCommitted(
                            t.moduleId, slot, action, hk, mm);
                    committed = true;
                }
            }
        } else if (t.popup != null) {
            if (pointerUpEventTimeMs - t.downEventTimeMs < gestureLockMinPressMs) {
                t.popup.dismiss();
                t.popup = null;
                return false;
            }
            t.popup.updatePointer(rawX, rawY);
            committed = t.popup.commitIfLockSelected();
            t.popup.dismiss();
            t.popup = null;
            if (committed && keyboardHoldLockListener != null) {
                keyboardHoldLockListener.onKeyboardHoldLockCommitted(t.moduleId);
            }
        }
        return committed;
    }

    private void releasePointerComponent(int pointerId, float rawX, float rawY, long pointerUpEventTimeMs) {
        boolean suppressButtonRelease =
                endKeyboardHoldLockForPointerIfAny(pointerId, rawX, rawY, pointerUpEventTimeMs);
        String releasedComponent = pointerComponents.remove(pointerId);
        if (releasedComponent != null) {
            if (isDpadComponent(releasedComponent)) {
                dpadPressedSet.remove(releasedComponent);
                if (dpadStateListener != null) {
                    dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                }
            } else if (buttonsPressedSet.remove(releasedComponent)) {
                int keyCode = getKeyCodeForComponent(releasedComponent);
                if (!suppressButtonRelease && buttonReleaseListener != null) {
                    buttonReleaseListener.onButtonRelease(releasedComponent, keyCode);
                }
            }
        }
    }

    private boolean isDpadComponent(String componentId) {
        return componentId != null && componentId.startsWith("dpad_");
    }

    /**
     * Get the current set of pressed D-pad key codes, in a deterministic order.
     * Returns an empty array if no D-pad directions are held.
     */
    private int[] getCurrentDpadKeys() {
        // Build in deterministic order so the HID report is stable
        String[] order = {"dpad_up", "dpad_left", "dpad_down", "dpad_right"};
        int count = 0;
        for (String id : order) {
            if (dpadPressedSet.contains(id)) count++;
        }
        int[] keys = new int[count];
        int idx = 0;
        for (String id : order) {
            if (dpadPressedSet.contains(id)) {
                keys[idx++] = getKeyCodeForComponent(id);
            }
        }
        return keys;
    }

    /**
     * Updates normalized position for the module being dragged. Dynamic layouts read anchors from
     * {@link #layoutDocument}; legacy SIMPLE reads {@link #componentPositions}. Keep both in sync
     * so the canvas repaints immediately and exit/save still merges positions.
     */
    private void applyDraggedComponentAnchors(float normX, float normY) {
        if (draggedComponentId == null) {
            return;
        }
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        float x = Math.max(0.02f, Math.min(0.98f, normX));
        float y = Math.max(0.02f, Math.min(0.98f, normY));

        if (layoutDocument != null && layoutDocument.modules != null) {
            for (GamepadLayoutPresetDocument.GamepadModule m : layoutDocument.modules) {
                if (m != null && draggedComponentId.equals(m.id)) {
                    m.anchorX = x;
                    m.anchorY = y;
                    break;
                }
            }
        }
        ComponentPosition pos = componentPositions.get(draggedComponentId);
        if (pos == null) {
            componentPositions.put(draggedComponentId, new ComponentPosition(x, y));
        } else {
            pos.x = x;
            pos.y = y;
        }
    }

    /** First sample for a stick finger (same math as MOVE) so the host sees hub press without a drag slop. */
    private void notifyDynamicStickAt(String stickModuleId, float touchX, float touchY) {
        if (analogStickListener == null) {
            return;
        }
        RectF b = componentBounds.get(stickModuleId);
        if (b == null) {
            return;
        }
        float scx = b.centerX();
        float scy = b.centerY();
        float rad = b.width() / 2f;
        float ddx = touchX - scx;
        float ddy = touchY - scy;
        float sdist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
        if (sdist > rad) {
            ddx = ddx * rad / sdist;
            ddy = ddy * rad / sdist;
        }
        dynamicStickOffset.put(stickModuleId, new float[]{ddx, ddy});
        String label = dynamicAnalogStickCallbackId(stickModuleId);
        analogStickListener.onAnalogStickMoved(label, ddx / rad, ddy / rad);
    }

    private boolean onTouchDynamicLayout(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (isEditMode && useDynamicLayout()) {
                    String chipModuleId = findModuleIdForConfigChipHit(x, y);
                    if (chipModuleId != null && moduleConfigEditTapListener != null) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        moduleConfigEditTapListener.onModuleConfigEditTap(chipModuleId);
                        return true;
                    }
                }
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);
                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                        if (GamepadLayoutPresetConstants.DPAD_VARIANT_CLICKY.equals(dynamicLeftDpadVariant)) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    } else if (componentId.startsWith("touchpad_") && !isEditMode) {
                        touchpadPointerId = pointerId;
                        touchpadLastX = x;
                        touchpadLastY = y;
                    } else if (GamepadLayoutPresetConstants.isScrollStripModuleId(componentId) && !isEditMode) {
                        ScrollStripPointerState sst = new ScrollStripPointerState();
                        sst.moduleId = componentId;
                        sst.lastY = y;
                        sst.accumY = 0f;
                        scrollStripPointerState.put(pointerId, sst);
                    } else if (componentId.startsWith("stick_") && !isEditMode
                            && !(isStickLeftDpadSplitLayout() && "stick_left".equals(componentId))) {
                        dynamicPointerStick.put(pointerId, componentId);
                        dynamicStickOffset.put(componentId, new float[]{0f, 0f});
                        notifyDynamicStickAt(componentId, x, y);
                        invalidate();
                    } else if (isEditMode) {
                        draggedComponentId = componentId;
                        dragPointerId = pointerId;
                    } else {
                        pressedComponentId = componentId;
                        buttonsPressedSet.add(componentId);
                        if (buttonPressListener != null) {
                            int keyCode = getKeyCodeForComponent(componentId);
                            buttonPressListener.onButtonPress(componentId, keyCode);
                        }
                        maybeStartKeyboardHoldLockTracking(pointerId, componentId, event, pointerIndex);
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                String componentId = getComponentAt(x, y);
                if (componentId != null && !isComponentDisabled(componentId)) {
                    pointerComponents.put(pointerId, componentId);
                    if (isDpadComponent(componentId)) {
                        if (dpadPressedSet.add(componentId) && dpadStateListener != null) {
                            dpadStateListener.onDpadStateChanged(getCurrentDpadKeys());
                        }
                        if (GamepadLayoutPresetConstants.DPAD_VARIANT_CLICKY.equals(dynamicLeftDpadVariant)) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    } else if (componentId.startsWith("touchpad_") && !isEditMode) {
                        if (touchpadPointerId < 0) {
                            touchpadPointerId = pointerId;
                            touchpadLastX = x;
                            touchpadLastY = y;
                        }
                    } else if (GamepadLayoutPresetConstants.isScrollStripModuleId(componentId) && !isEditMode) {
                        ScrollStripPointerState sst = new ScrollStripPointerState();
                        sst.moduleId = componentId;
                        sst.lastY = y;
                        sst.accumY = 0f;
                        scrollStripPointerState.put(pointerId, sst);
                    } else if (componentId.startsWith("stick_") && !isEditMode
                            && !(isStickLeftDpadSplitLayout() && "stick_left".equals(componentId))) {
                        dynamicPointerStick.put(pointerId, componentId);
                        dynamicStickOffset.put(componentId, new float[]{0f, 0f});
                        notifyDynamicStickAt(componentId, x, y);
                        invalidate();
                    } else if (!isEditMode && buttonPressListener != null) {
                        buttonsPressedSet.add(componentId);
                        int keyCode = getKeyCodeForComponent(componentId);
                        buttonPressListener.onButtonPress(componentId, keyCode);
                        maybeStartKeyboardHoldLockTracking(pointerId, componentId, event, pointerIndex);
                    }
                } else if (event.getPointerCount() >= 2 && backgroundBitmap != null) {
                    int otherIdx = pointerIndex == 0 ? 1 : 0;
                    float ox = event.getX(otherIdx);
                    float oy = event.getY(otherIdx);
                    String otherComponent = getComponentAt(ox, oy);
                    if (otherComponent == null) {
                        isManipulatingBg = true;
                        float dx = x - ox;
                        float dy = y - oy;
                        bgLastDistance = (float) Math.sqrt(dx * dx + dy * dy);
                        bgLastCenterX = (x + ox) / 2f;
                        bgLastCenterY = (y + oy) / 2f;
                        bgStartOffsetX = bgOffsetX;
                        bgStartOffsetY = bgOffsetY;
                        bgStartScale = bgScale;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!longPressCancelled && longPressRunnable != null) {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        float mx = event.getX(i);
                        float my = event.getY(i);
                        float dx = mx - longPressDownX;
                        float dy = my - longPressDownY;
                        if (Math.sqrt(dx * dx + dy * dy) > longPressMoveThresholdPx) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            longPressCancelled = true;
                        }
                    }
                }
                updateKeyboardHoldLockOnDynamicMove(event);
                if (isManipulatingBg && event.getPointerCount() >= 2) {
                    float x0 = event.getX(0), y0 = event.getY(0);
                    float x1 = event.getX(1), y1 = event.getY(1);
                    float dist = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                    float cx = (x0 + x1) / 2f;
                    float cy = (y0 + y1) / 2f;
                    if (bgLastDistance > 0) {
                        bgScale = Math.max(0.1f, bgStartScale * dist / bgLastDistance);
                    }
                    int vw = getWidth();
                    int vh = getHeight();
                    int bw = backgroundBitmap.getWidth();
                    int bh = backgroundBitmap.getHeight();
                    float s = bgInitialScale * bgScale;
                    float dx = (cx - bgLastCenterX) / (s != 0 ? s : 1f);
                    float dy = (cy - bgLastCenterY) / (s != 0 ? s : 1f);
                    bgOffsetX = bgStartOffsetX + dx;
                    bgOffsetY = bgStartOffsetY + dy;
                    bgLastCenterX = cx;
                    bgLastCenterY = cy;
                    bgLastDistance = dist;
                    bgStartOffsetX = bgOffsetX;
                    bgStartOffsetY = bgOffsetY;
                    bgStartScale = bgScale;
                    invalidate();
                    notifyBackgroundViewportChanged();
                } else {
                    // Touchpad and stick(s) may use different pointers — run both, not else-if.
                    if (touchpadPointerId >= 0 && touchpadDeltaListener != null) {
                        int idx = event.findPointerIndex(touchpadPointerId);
                        if (idx >= 0) {
                            float nx = event.getX(idx);
                            float ny = event.getY(idx);
                            touchpadDeltaListener.onTouchpadDelta(nx - touchpadLastX, ny - touchpadLastY);
                            touchpadLastX = nx;
                            touchpadLastY = ny;
                        }
                    }
                    if (!scrollStripPointerState.isEmpty()) {
                        for (int pid : new ArrayList<>(scrollStripPointerState.keySet())) {
                            applyScrollStripMoveForPointer(pid, event);
                        }
                    }
                    if (!dynamicPointerStick.isEmpty() && analogStickListener != null) {
                        for (Map.Entry<Integer, String> e : dynamicPointerStick.entrySet()) {
                            String sid = e.getValue();
                            int pid = e.getKey();
                            int idx = event.findPointerIndex(pid);
                            if (idx < 0) {
                                continue;
                            }
                            float sx = event.getX(idx);
                            float sy = event.getY(idx);
                            RectF b = componentBounds.get(sid);
                            if (b == null) {
                                continue;
                            }
                            float scx = b.centerX();
                            float scy = b.centerY();
                            float rad = b.width() / 2f;
                            float ddx = sx - scx;
                            float ddy = sy - scy;
                            float sdist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                            if (sdist > rad) {
                                ddx = ddx * rad / sdist;
                                ddy = ddy * rad / sdist;
                            }
                            dynamicStickOffset.put(sid, new float[]{ddx, ddy});
                            String label = dynamicAnalogStickCallbackId(sid);
                            analogStickListener.onAnalogStickMoved(label, ddx / rad, ddy / rad);
                        }
                        invalidate();
                    }
                    if (draggedComponentId != null && isEditMode) {
                        int dragIdx = dragPointerId >= 0 ? event.findPointerIndex(dragPointerId) : 0;
                        if (dragIdx < 0) {
                            dragIdx = 0;
                        }
                        float moveX = event.getX(dragIdx);
                        float moveY = event.getY(dragIdx);
                        applyDraggedAnchorsFromViewXY(moveX, moveY);
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                if (longPressHandler != null && longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    longPressComponentId = null;
                }
                for (Map.Entry<Integer, String> e : new HashMap<>(dynamicPointerStick).entrySet()) {
                    String sid = e.getValue();
                    String label = dynamicAnalogStickCallbackId(sid);
                    if (analogStickListener != null) {
                        analogStickListener.onAnalogStickMoved(label, 0, 0);
                    }
                }
                dynamicPointerStick.clear();
                dynamicStickOffset.clear();
                if (touchpadPointerId == pointerId) {
                    touchpadPointerId = -1;
                }
                scrollStripPointerState.remove(pointerId);
                if (draggedComponentId != null) {
                    draggedComponentId = null;
                    dragPointerId = -1;
                }
                int dynUpIdx = event.findPointerIndex(pointerId);
                float dynUpRawX = dynUpIdx >= 0 ? event.getRawX(dynUpIdx) : event.getRawX();
                float dynUpRawY = dynUpIdx >= 0 ? event.getRawY(dynUpIdx) : event.getRawY();
                releasePointerComponent(pointerId, dynUpRawX, dynUpRawY, event.getEventTime());
                if (action == MotionEvent.ACTION_UP) {
                    pointerComponents.clear();
                    dpadPressedSet.clear();
                    buttonsPressedSet.clear();
                    touchpadPointerId = -1;
                    scrollStripPointerState.clear();
                    cancelAllKeyboardHoldLockTracking();
                    if (pressedComponentId != null) {
                        pressedComponentId = null;
                    }
                    dragPointerId = -1;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (isManipulatingBg) {
                    isManipulatingBg = false;
                    bgLastDistance = -1f;
                }
                if (draggedComponentId != null && pointerId == dragPointerId) {
                    draggedComponentId = null;
                    dragPointerId = -1;
                }
                String releasedStick = dynamicPointerStick.remove(pointerId);
                if (releasedStick != null) {
                    dynamicStickOffset.remove(releasedStick);
                    String label = dynamicAnalogStickCallbackId(releasedStick);
                    if (analogStickListener != null) {
                        analogStickListener.onAnalogStickMoved(label, 0, 0);
                    }
                }
                if (touchpadPointerId == pointerId) {
                    touchpadPointerId = -1;
                }
                scrollStripPointerState.remove(pointerId);
                int dynPuIdx = event.findPointerIndex(pointerId);
                float dynPuRawX = dynPuIdx >= 0 ? event.getRawX(dynPuIdx) : event.getRawX();
                float dynPuRawY = dynPuIdx >= 0 ? event.getRawY(dynPuIdx) : event.getRawY();
                releasePointerComponent(pointerId, dynPuRawX, dynPuRawY, event.getEventTime());
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    /** Callback id for {@link AnalogStickListener}: {@code l}/{@code r} for primary slots, else the module id (e.g. custom {@code stick_*}). */
    private static String dynamicAnalogStickCallbackId(String sid) {
        if ("stick_left".equals(sid)) {
            return "l";
        }
        if ("stick_right".equals(sid)) {
            return "r";
        }
        return sid;
    }

    private boolean isStickLeftDpadSplitLayout() {
        if (layoutDocument == null || layoutDocument.modules == null) {
            return false;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : layoutDocument.modules) {
            if (m != null && "stick_left".equals(m.id)
                    && GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                return GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(
                        GamepadDpadVariantArt.normalizeVariant(m.dpadVariant));
            }
        }
        return false;
    }

    private boolean dpadSplitTouchArmsContain(float x, float y) {
        String[] keys = {"dpad_up", "dpad_down", "dpad_left", "dpad_right"};
        for (String k : keys) {
            RectF r = componentBounds.get(k);
            if (r != null && r.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private String getComponentAt(float x, float y) {
        if (useDynamicLayout()) {
            if (isEditMode && layoutDocument != null && layoutDocument.modules != null) {
                List<GamepadLayoutPresetDocument.GamepadModule> mods =
                        new ArrayList<>(layoutDocument.modules);
                Collections.sort(mods, Comparator.comparingInt(m -> m.zIndex));
                for (int i = mods.size() - 1; i >= 0; i--) {
                    GamepadLayoutPresetDocument.GamepadModule m = mods.get(i);
                    if (m == null || m.id == null) {
                        continue;
                    }
                    RectF b = componentBounds.get(m.id);
                    if (b != null && b.contains(x, y) && !isComponentDisabled(m.id)) {
                        return m.id;
                    }
                }
            }
            for (int i = dynamicHitTestOrder.size() - 1; i >= 0; i--) {
                String id = dynamicHitTestOrder.get(i);
                RectF b = componentBounds.get(id);
                if (b != null && b.contains(x, y) && !isComponentDisabled(id)) {
                    if ("stick_left".equals(id) && isStickLeftDpadSplitLayout()
                            && !dpadSplitTouchArmsContain(x, y)) {
                        continue;
                    }
                    return id;
                }
            }
        }
        // Check D-pad in deterministic order first (they share edges at center)
        String[] dpadOrder = {"dpad_up", "dpad_right", "dpad_down", "dpad_left"};
        for (String id : dpadOrder) {
            RectF bounds = componentBounds.get(id);
            if (bounds != null && bounds.contains(x, y)) {
                return id;
            }
        }
        // Check remaining components
        for (Map.Entry<String, RectF> entry : componentBounds.entrySet()) {
            if (entry.getKey().startsWith("dpad_")) continue;
            if (entry.getValue().contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int getKeyCodeForComponent(String componentId) {
        if (componentId != null && componentId.startsWith("mouse_btn_")) {
            return 0;
        }
        // Map component IDs to USB HID usage IDs (USB HID Keyboard/Keypad Usage Page)
        switch (componentId) {
            // D-Pad - Arrow keys (HID: Up=0x52, Down=0x51, Left=0x50, Right=0x4F)
            case "dpad_up":    return 82; // HID Up Arrow
            case "dpad_down":  return 81; // HID Down Arrow
            case "dpad_left":  return 80; // HID Left Arrow
            case "dpad_right": return 79; // HID Right Arrow

            // Xbox/PS/NES face buttons
            case "button_a": return buttonAKeyCode;  // configurable, default: HID Enter
            case "button_b": return 41;  // HID Escape
            case "button_x": return 42;  // HID Backspace/Delete
            case "button_y": return 101; // HID Application (Menu)

            // PlayStation buttons (same mapping as Xbox equivalents)
            // IDs from drawButton using UTF-8 symbol labels
            case "button_×":        // fall-through
            case "button_cross":    return 40;  // HID Return/Enter
            case "button_○":        // fall-through
            case "button_circle":   return 41;  // HID Escape
            case "button_□":        // fall-through
            case "button_square":   return 42;  // HID Backspace/Delete
            case "button_△":        // fall-through
            case "button_triangle": return 101; // HID Application (Menu)

            // NES buttons (B = Escape)
            case "nes_b": return 41;  // HID Escape

            // Shoulder buttons → F1-F4 (HID: F1=0x3A=58 … F4=0x3D=61)
            case "lb": case "l1": return 58; // HID F1
            case "rb": case "r1": return 59; // HID F2
            case "lt": case "l2": return 60; // HID F3
            case "rt": case "r2": return 61; // HID F4

            // Stick clicks (L3/R3 = mouse buttons, handled specially)
            case "stick_l_click": return 1001;
            case "stick_r_click": return 1002;

            // Center buttons → F5/F6 (HID: F5=0x3E=62, F6=0x3F=63)
            // Xbox "≡" drawButton generates id "button_≡"; "⊞" generates "button_⊞"
            case "button_≡": case "back":   case "button_back":
            case "button_select": case "select": return 62; // HID F5
            case "button_⊞": case "start":  case "button_start": return 63; // HID F6

            default:
                if (keyCodeProvider != null) {
                    int k = keyCodeProvider.getHidKey(componentId);
                    if (k != 0) {
                        return k;
                    }
                }
                return 0;
        }
    }

    public void setLayout(GamepadLayout layout) {
        this.currentLayout = layout;
        this.componentPositions = configManager.loadLayoutPositions(layout);
        invalidate();
    }

    /**
     * Get component X position, or return default if not found.
     */
    private float getPositionX(String componentId, float defaultX) {
        ComponentPosition pos = componentPositions.get(componentId);
        return pos != null ? pos.x : defaultX;
    }

    private float getPositionY(String componentId, float defaultY) {
        ComponentPosition pos = componentPositions.get(componentId);
        return pos != null ? pos.y : defaultY;
    }

    /**
     * Get current component positions (for saving).
     */
    public Map<String, ComponentPosition> getPositions() {
        return componentPositions;
    }

    /**
     * Listener notified when edit mode is exited (so positions can be saved).
     */
    public interface EditModeExitListener {
        void onEditModeExit(Map<String, ComponentPosition> positions);
    }

    public void setEditModeExitListener(EditModeExitListener listener) {
        this.editModeExitListener = listener;
    }

    public void savePositions() {
        configManager.saveLayoutPositions(currentLayout, componentPositions);
    }

    private EditModeExitListener editModeExitListener;

    public void setEditMode(boolean editMode) {
        if (!editMode && editModeExitListener != null) {
            editModeExitListener.onEditModeExit(componentPositions);
        }
        if (editMode) {
            cancelAllKeyboardHoldLockTracking();
        }
        isEditMode = editMode;
        invalidate();
    }

    public void setButtonPressListener(ButtonPressListener listener) {
        this.buttonPressListener = listener;
    }

    public void setAnalogStickListener(AnalogStickListener listener) {
        this.analogStickListener = listener;
    }

    public void setComponentLongPressListener(ComponentLongPressListener listener) {
        this.longPressListener = listener;
    }

    /**
     * Dynamic layout edit mode: tap the small edit chip at a module’s top-right to open its configuration.
     * Long-press on the module body is not used for that path (avoids accidental drags).
     */
    public void setModuleConfigEditTapListener(@Nullable ModuleConfigEditTapListener listener) {
        this.moduleConfigEditTapListener = listener;
    }

    public void setDpadStateListener(DpadStateListener listener) {
        this.dpadStateListener = listener;
    }

    public void setButtonReleaseListener(ButtonReleaseListener listener) {
        this.buttonReleaseListener = listener;
    }

    public void setComponentDisabled(String componentId, boolean disabled) {
        disabledComponents.put(componentId, disabled);
        invalidate();
    }

    public boolean isComponentDisabled(String componentId) {
        return Boolean.TRUE.equals(disabledComponents.get(componentId));
    }

    public void setComponentDisplayLabel(String componentId, String label) {
        componentDisplayLabels.put(componentId, label);
        invalidate();
    }

    public void setComponentDisplayLabels(Map<String, String> labels) {
        componentDisplayLabels.putAll(labels);
        invalidate();
    }

    public void setButtonAKeyCode(int keyCode) {
        this.buttonAKeyCode = keyCode;
    }

    public void setShowTwoButtons(boolean show) {
        this.showTwoButtons = show;
        invalidate();
    }

    /**
     * Tunes customize-mode long-press before module configuration menus on non-dynamic SIMPLE layouts.
     * Dynamic presets use {@link #setModuleConfigEditTapListener} instead. Values are clamped for safety.
     * Persisted keys: {@link com.openterface.keymod.gamepad.GamepadPreferenceKeys#EDIT_LONG_PRESS_MS},
     * {@link com.openterface.keymod.gamepad.GamepadPreferenceKeys#EDIT_LONG_PRESS_CANCEL_DP}.
     */
    public void setEditLongPressConfig(long thresholdMs, float moveCancelPx) {
        longPressThresholdMs = Math.max(250L, Math.min(1200L, thresholdMs));
        longPressMoveThresholdPx = Math.max(8f, Math.min(80f, moveCancelPx));
    }

    public void setLongPressEnabled(boolean enabled) {
        this.longPressEnabled = enabled;
        // Cancel any pending long press when disabling
        if (!enabled && longPressHandler != null && longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
        }
    }

    public void setButtonSizeScale(float scale) {
        this.buttonSizeScale = scale;
        invalidate();
    }

    public void setStickSizeScale(float scale) {
        this.stickSizeScale = scale;
        invalidate();
    }

    public void setBackgroundBitmap(android.graphics.Bitmap bitmap) {
        this.backgroundBitmap = bitmap;
        if (bitmap != null) {
            // Reset viewport to center-fit
            bgScale = 1.0f;
            bgOffsetX = 0f;
            bgOffsetY = 0f;
        }
        markBrandWatermarkContrastDirty();
        invalidate();
        if (onBackgroundChanged != null) onBackgroundChanged.run();
    }

    public void resetBackgroundViewport() {
        bgScale = 1.0f;
        bgOffsetX = 0f;
        bgOffsetY = 0f;
        markBrandWatermarkContrastDirty();
        invalidate();
    }

    public android.graphics.Bitmap getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public void setBackgroundChangedCallback(Runnable callback) {
        this.onBackgroundChanged = callback;
    }

    public void setBackgroundViewportCallback(Runnable callback) {
        this.onBackgroundViewportChanged = callback;
    }

    public float getBackgroundScale() { return bgScale; }
    public float getBackgroundOffsetX() { return bgOffsetX; }
    public float getBackgroundOffsetY() { return bgOffsetY; }

    public void setBackgroundViewport(float scale, float offsetX, float offsetY) {
        this.bgScale = scale;
        this.bgOffsetX = offsetX;
        this.bgOffsetY = offsetY;
        markBrandWatermarkContrastDirty();
        invalidate();
    }

    /** Null clears custom fill (default gradient when no bitmap). */
    public void setBackgroundFillArgb(@Nullable Integer argb) {
        this.backgroundFillArgb = argb;
        markBrandWatermarkContrastDirty();
        invalidate();
    }

    @Nullable
    public Integer getBackgroundFillArgb() {
        return backgroundFillArgb;
    }

    /** Pass null or {@link GamepadLayoutPresetConstants#BACKGROUND_PATTERN_NONE} to clear. */
    public void setBackgroundPatternId(@Nullable String patternId) {
        if (patternId == null || patternId.trim().isEmpty()
                || GamepadLayoutPresetConstants.BACKGROUND_PATTERN_NONE.equalsIgnoreCase(patternId.trim())) {
            this.backgroundPatternId = null;
        } else {
            this.backgroundPatternId = patternId.trim().toLowerCase(Locale.ROOT);
        }
        markBrandWatermarkContrastDirty();
        invalidate();
    }

    @Nullable
    public String getBackgroundPatternId() {
        return backgroundPatternId;
    }

    private Runnable onBackgroundViewportChanged;

    void notifyBackgroundViewportChanged() {
        markBrandWatermarkContrastDirty();
        if (onBackgroundViewportChanged != null) onBackgroundViewportChanged.run();
    }

    /**
     * Analog callbacks use {@code l}/{@code r}; legacy bounds use {@code stick_l}/{@code stick_r},
     * dynamic layout uses {@code stick_left}/{@code stick_right}. Highlight keys must match draw ids.
     */
    private static List<String> stickHighlightPrefixes(String stickLabel) {
        String s = stickLabel.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(2);
        if ("l".equals(s)) {
            out.add("stick_l");
            out.add("stick_left");
        } else if ("r".equals(s)) {
            out.add("stick_r");
            out.add("stick_right");
        } else if (s.startsWith("stick_")) {
            out.add(s);
        } else {
            out.add("stick_" + s);
        }
        return out;
    }

    public void setActiveStickDirections(String stickLabel, Set<String> directions) {
        for (String prefix : stickHighlightPrefixes(stickLabel)) {
            activeStickDirections.removeIf(d -> d.startsWith(prefix + "_"));
        }
        for (String prefix : stickHighlightPrefixes(stickLabel)) {
            for (String dir : directions) {
                activeStickDirections.add(prefix + "_" + dir);
            }
        }
        invalidate();
    }

    public void clearStickDirections(String stickLabel) {
        for (String prefix : stickHighlightPrefixes(stickLabel)) {
            activeStickDirections.removeIf(d -> d.startsWith(prefix + "_"));
        }
        invalidate();
    }

    private void drawDisabledButton(Canvas canvas, float cx, float cy, float radius, String label) {
        String id = "button_" + label.toLowerCase().replace("(", "").replace(")", "");
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        componentBounds.put(id, bounds);
        FaceStyle muted = new FaceStyle(
                applyAlphaInt(FACE_NEUTRAL.body, 110),
                applyAlphaInt(FACE_NEUTRAL.rim, 110),
                applyAlphaInt(FACE_NEUTRAL.label, 140));
        drawRetroFaceButton(canvas, cx, cy, radius, muted, false, label, 1f, themeAccentPrimary);
        float density = getResources().getDisplayMetrics().density;
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(2f * density);
        retroRingPaint.setPathEffect(new DashPathEffect(new float[]{6f * density, 4f * density}, 0f));
        retroRingPaint.setColor(0x88FFFFFF);
        retroRingPaint.setShader(null);
        RectF outline = new RectF(cx - radius * 1.02f, cy - radius * 1.02f, cx + radius * 1.02f, cy + radius * 1.02f);
        canvas.drawRoundRect(outline, radius * 1.02f, radius * 1.02f, retroRingPaint);
        retroRingPaint.setPathEffect(null);
    }

    public Map<String, GamepadConfigManager.ComponentPosition> getComponentPositions() {
        return componentPositions;
    }

    // Listener interfaces
    /**
     * Host (typically {@link com.openterface.fragment.GamepadFragment}) tracks latched keyboard keys from the
     * KM Basic-style hold-lock gesture on eligible modules.
     */
    public interface KeyboardHoldLockListener {
        void onKeyboardHoldLockCommitted(String moduleId);

        boolean isKeyboardHoldLockLatched(String moduleId);

        /**
         * Diagonal gesture commit (see {@link GamepadGestureLock}). Default no-op for hosts that only
         * support legacy vertical hold-lock.
         */
        default void onGestureLockActionCommitted(
                @NonNull String moduleId,
                @NonNull String slotKey,
                @NonNull String action,
                @Nullable Integer hidKeyOverride,
                @Nullable Integer modifierMaskOverride) {}

        /** True if this module is latched for hold-lock, turbo, or alternate gesture latch. */
        default boolean isGestureLatchedAny(@NonNull String moduleId) {
            return isKeyboardHoldLockLatched(moduleId);
        }
    }

    public interface ButtonPressListener {
        void onButtonPress(String buttonId, int keyCode);
    }

    public interface AnalogStickListener {
        void onAnalogStickMoved(String stickId, float x, float y);
    }

    public interface ComponentLongPressListener {
        void onComponentLongPress(String componentId);
    }

    public interface ModuleConfigEditTapListener {
        void onModuleConfigEditTap(String moduleId);
    }

    public interface DpadStateListener {
        /**
         * Called whenever D-pad touch state changes.
         * @param keyCodes HID key codes for currently held D-pad directions (empty = all released)
         */
        void onDpadStateChanged(int[] keyCodes);
    }

    public interface ButtonReleaseListener {
        void onButtonRelease(String buttonId, int keyCode);
    }

    public interface KeyCodeProvider {
        int getHidKey(String componentId);
    }

    public interface TouchpadDeltaListener {
        void onTouchpadDelta(float dxPixels, float dyPixels);
    }

    /** HID mouse wheel ticks from a {@link GamepadLayoutPresetConstants#MODULE_TYPE_SCROLL_STRIP} module. */
    public interface ScrollStripWheelListener {
        void onScrollStripWheel(String moduleId, int deltaX, int deltaY);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAllKeyboardHoldLockTracking();
        super.onDetachedFromWindow();
    }
}
