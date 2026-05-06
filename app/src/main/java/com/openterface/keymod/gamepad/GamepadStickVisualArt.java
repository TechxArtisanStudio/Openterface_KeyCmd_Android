package com.openterface.keymod.gamepad;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Optional thumbstick cap rendering for {@link GamepadLayoutPresetDocument.GamepadModule#stickVisualVariant}.
 */
public final class GamepadStickVisualArt {

    private GamepadStickVisualArt() {}

    @Nullable
    public static String normalizeStickVisual(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (GamepadLayoutPresetConstants.STICK_VISUAL_DEFAULT.equals(v)) {
            return null;
        }
        if (GamepadLayoutPresetConstants.isAllowedStickVisualVariant(v)) {
            return v;
        }
        return null;
    }

    /**
     * Draw cap decoration on top of the filled thumb circle at ({@code innerCx}, {@code innerCy}) with radius {@code capR}.
     */
    public static void drawCapOverlay(
            @NonNull Canvas canvas,
            @Nullable String stickVisualVariant,
            float innerCx,
            float innerCy,
            float capR,
            int themeAccentPrimary,
            @NonNull Paint retroBodyPaint,
            @NonNull Paint retroRingPaint,
            @NonNull Paint retroGlossPaint) {
        String v = normalizeStickVisual(stickVisualVariant);
        if (v == null || GamepadLayoutPresetConstants.STICK_VISUAL_HALL_EFFECT.equals(v)) {
            return;
        }
        if (GamepadLayoutPresetConstants.STICK_VISUAL_LOW_PROFILE.equals(v)) {
            float r = capR * 0.88f;
            retroRingPaint.setShader(null);
            retroRingPaint.setStyle(Paint.Style.STROKE);
            retroRingPaint.setStrokeWidth(Math.max(1f, capR * 0.06f));
            retroRingPaint.setColor(applyAlphaInt(0xFF000000, 55));
            canvas.drawCircle(innerCx, innerCy, r, retroRingPaint);
            return;
        }
        if (GamepadLayoutPresetConstants.STICK_VISUAL_C_STICK.equals(v)) {
            float r = capR * 0.92f;
            Shader capShader = new RadialGradient(innerCx - r * 0.15f, innerCy - r * 0.18f, r * 1.05f,
                    Color.parseColor("#C4B8D8"), Color.parseColor("#5E5372"), Shader.TileMode.CLAMP);
            retroBodyPaint.setShader(capShader);
            canvas.drawCircle(innerCx, innerCy, r, retroBodyPaint);
            retroBodyPaint.setShader(null);
            retroRingPaint.setStyle(Paint.Style.STROKE);
            retroRingPaint.setStrokeWidth(Math.max(1f, r * 0.08f));
            retroRingPaint.setColor(applyAlphaInt(themeAccentPrimary, 120));
            canvas.drawCircle(innerCx, innerCy, r * 0.98f, retroRingPaint);
            return;
        }
        if (GamepadLayoutPresetConstants.STICK_VISUAL_CONVEX.equals(v)) {
            Shader capShader = new RadialGradient(innerCx, innerCy - capR * 0.35f, capR * 1.1f,
                    Color.parseColor("#B8B6C2"), Color.parseColor("#4A4852"), Shader.TileMode.CLAMP);
            retroBodyPaint.setShader(capShader);
            canvas.drawCircle(innerCx, innerCy, capR * 0.98f, retroBodyPaint);
            retroBodyPaint.setShader(null);
            Shader spec = new RadialGradient(innerCx - capR * 0.25f, innerCy - capR * 0.35f, capR * 0.45f,
                    0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            retroGlossPaint.setShader(spec);
            canvas.drawCircle(innerCx, innerCy, capR * 0.92f, retroGlossPaint);
            retroGlossPaint.setShader(null);
            return;
        }
        if (GamepadLayoutPresetConstants.STICK_VISUAL_CONCAVE.equals(v)) {
            retroRingPaint.setShader(null);
            retroRingPaint.setStyle(Paint.Style.STROKE);
            retroRingPaint.setStrokeWidth(Math.max(1.2f, capR * 0.09f));
            retroRingPaint.setColor(Color.parseColor("#2A2830"));
            canvas.drawCircle(innerCx, innerCy, capR * 0.88f, retroRingPaint);
            Shader bowl = new RadialGradient(innerCx, innerCy + capR * 0.12f, capR * 0.75f,
                    0x22000000, 0x00000000, Shader.TileMode.CLAMP);
            retroGlossPaint.setShader(bowl);
            canvas.drawCircle(innerCx, innerCy, capR * 0.9f, retroGlossPaint);
            retroGlossPaint.setShader(null);
        }
    }

    private static int applyAlphaInt(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
