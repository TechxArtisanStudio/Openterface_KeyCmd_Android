package com.openterface.keymod.gamepad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;
import com.openterface.keymod.R;

/**
 * Renders a small bitmap of a preset's dynamic layout for card thumbnails (off-screen {@link GamepadView}).
 *
 * <p>Renders at a larger virtual canvas first so {@link GamepadLayoutPresetConstants#contentMinEdgeScaleFactor}
 * matches typical landscape play (see {@link GamepadLayoutPresetConstants#DYNAMIC_LAYOUT_REFERENCE_MIN_EDGE_PX}),
 * then scales down for PNG size. Aspect is {@value #OFFSCREEN_RENDER_WIDTH_PX}:{@value #OFFSCREEN_RENDER_HEIGHT_PX}
 * (20:9), consistent with common full-bleed landscape phones; live {@link com.openterface.fragment.GamepadFragment}
 * uses {@code gamepad_view} {@code match_parent} on that window.
 */
public final class GamepadLayoutPreviewRenderer {

    /**
     * Bump when preview pixels / semantics change so {@link GamepadLayoutPreviewCache} filenames invalidate
     * stale PNGs without requiring a preset JSON edit.
     */
    public static final int CACHE_FORMAT_VERSION = 1;

    /**
     * Off-screen draw size: min-edge 720px → scale factor 0.9 vs 800px reference, avoiding the aggressive
     * {@link GamepadLayoutPresetConstants#DYNAMIC_LAYOUT_SCALE_MIN} floor that dominates small 480×270 windows.
     */
    private static final int OFFSCREEN_RENDER_WIDTH_PX = 1600;
    private static final int OFFSCREEN_RENDER_HEIGHT_PX = 720;

    /** Max width of the returned bitmap (height follows 20:9). */
    private static final int OUTPUT_MAX_WIDTH_PX = 480;

    private static final Gson GSON = new Gson();

    private GamepadLayoutPreviewRenderer() {}

    @Nullable
    public static Bitmap render(@NonNull Context themedContext, @NonNull GamepadLayoutPresetDocument doc) {
        GamepadLayoutPresetDocument drawDoc = cloneStrippingEmbeddedBackground(doc);
        if (drawDoc == null) {
            return null;
        }
        Bitmap hiRes = null;
        try {
            GamepadView v = new GamepadView(themedContext);
            v.refreshThemeAccentForHeadless();
            v.setLayout(GamepadLayout.SIMPLE);
            v.setGamepadContentInsets(0, 0, 0, 0);
            v.setEditMode(false);
            v.setLongPressEnabled(false);
            v.setKeyMappingHintsVisible(false);
            v.setLayoutDocument(drawDoc);
            float mouseScale = 1f;
            if (drawDoc.layout != null && drawDoc.layout.touchpadMouseButtonScale != null) {
                float t = drawDoc.layout.touchpadMouseButtonScale;
                if (t > 0f && !Float.isNaN(t)) {
                    mouseScale = t;
                }
            }
            v.setTouchpadMouseButtonLayoutScale(mouseScale);
            v.setBackgroundBitmap(null);
            applyLayoutBackground(v, drawDoc.layout);

            int wSpec = View.MeasureSpec.makeMeasureSpec(OFFSCREEN_RENDER_WIDTH_PX, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(OFFSCREEN_RENDER_HEIGHT_PX, View.MeasureSpec.EXACTLY);
            v.measure(wSpec, hSpec);
            v.layout(0, 0, OFFSCREEN_RENDER_WIDTH_PX, OFFSCREEN_RENDER_HEIGHT_PX);
            hiRes = Bitmap.createBitmap(OFFSCREEN_RENDER_WIDTH_PX, OFFSCREEN_RENDER_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(hiRes);
            v.draw(c);

            int outW = OUTPUT_MAX_WIDTH_PX;
            int outH = Math.max(
                    1,
                    Math.round((float) OUTPUT_MAX_WIDTH_PX * OFFSCREEN_RENDER_HEIGHT_PX / OFFSCREEN_RENDER_WIDTH_PX));
            if (outW == OFFSCREEN_RENDER_WIDTH_PX && outH == OFFSCREEN_RENDER_HEIGHT_PX) {
                return hiRes;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(hiRes, outW, outH, true);
            hiRes.recycle();
            hiRes = null;
            return scaled;
        } catch (RuntimeException e) {
            if (hiRes != null) {
                hiRes.recycle();
            }
            return null;
        }
    }

    private static void applyLayoutBackground(@NonNull GamepadView v, @Nullable GamepadLayoutPresetDocument.LayoutGlobals layout) {
        if (layout == null) {
            v.setBackgroundFillArgb(null);
            v.setBackgroundPatternId(null);
            return;
        }
        v.setBackgroundFillArgb(layout.backgroundFillArgb);
        v.setBackgroundPatternId(layout.backgroundPattern);
    }

    @Nullable
    private static GamepadLayoutPresetDocument cloneStrippingEmbeddedBackground(
            @NonNull GamepadLayoutPresetDocument src) {
        try {
            String json = GamepadLayoutPresetDocument.toJsonPretty(src);
            GamepadLayoutPresetDocument c = GSON.fromJson(json, GamepadLayoutPresetDocument.class);
            if (c != null) {
                GamepadLayoutDocEditor.normalizeModuleZOrder(c);
            }
            if (c != null && c.layout != null) {
                c.layout.backgroundImageData = null;
                c.layout.backgroundImageEncoding = null;
                c.layout.backgroundImageMediaType = null;
            }
            return c;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Themed context for {@link #render(Context, GamepadLayoutPresetDocument)}. */
    @NonNull
    public static Context wrapPreviewContext(@NonNull Context base) {
        return new ContextThemeWrapper(base, R.style.Theme_KeyCmd_GamepadPresetPickerDialog);
    }
}
