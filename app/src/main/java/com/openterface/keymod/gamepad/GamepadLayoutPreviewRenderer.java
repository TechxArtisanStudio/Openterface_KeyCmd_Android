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
 */
public final class GamepadLayoutPreviewRenderer {

    private static final int RENDER_WIDTH_PX = 480;
    private static final int RENDER_HEIGHT_PX = 270;
    private static final Gson GSON = new Gson();

    private GamepadLayoutPreviewRenderer() {}

    @Nullable
    public static Bitmap render(@NonNull Context themedContext, @NonNull GamepadLayoutPresetDocument doc) {
        GamepadLayoutPresetDocument drawDoc = cloneStrippingEmbeddedBackground(doc);
        if (drawDoc == null) {
            return null;
        }
        try {
            GamepadView v = new GamepadView(themedContext);
            v.refreshThemeAccentForHeadless();
            v.setLayout(GamepadLayout.SIMPLE);
            v.setGamepadContentInsets(0, 0, 0, 0);
            v.setEditMode(false);
            v.setLongPressEnabled(false);
            v.setKeyMappingHintsVisible(true);
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
            v.setBackgroundFillArgb(null);
            v.setBackgroundPatternId(null);
            int wSpec = View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH_PX, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(RENDER_HEIGHT_PX, View.MeasureSpec.EXACTLY);
            v.measure(wSpec, hSpec);
            v.layout(0, 0, RENDER_WIDTH_PX, RENDER_HEIGHT_PX);
            Bitmap bmp = Bitmap.createBitmap(RENDER_WIDTH_PX, RENDER_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            v.draw(c);
            return bmp;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static GamepadLayoutPresetDocument cloneStrippingEmbeddedBackground(
            @NonNull GamepadLayoutPresetDocument src) {
        try {
            String json = GamepadLayoutPresetDocument.toJsonPretty(src);
            GamepadLayoutPresetDocument c = GSON.fromJson(json, GamepadLayoutPresetDocument.class);
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
        return new ContextThemeWrapper(base, R.style.Theme_KeyMod_GamepadPresetPickerDialog);
    }
}
