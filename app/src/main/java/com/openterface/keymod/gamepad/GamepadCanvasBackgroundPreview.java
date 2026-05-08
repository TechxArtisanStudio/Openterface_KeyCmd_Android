package com.openterface.keymod.gamepad;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import androidx.core.graphics.ColorUtils;

/**
 * Shared canvas drawing for procedural gamepad background fill + pattern overlay.
 * Used by {@link com.openterface.keymod.GamepadView} and thumbnail chips in the background picker.
 */
public final class GamepadCanvasBackgroundPreview {

    private GamepadCanvasBackgroundPreview() {}

    /**
     * Vertical gradient fill matching {@code GamepadView} solid/default background (no bitmap).
     */
    public static void drawSolidOrGradientFill(
            Canvas canvas,
            int vw,
            int vh,
            @Nullable Integer backgroundFillArgb,
            Paint bgPaint) {
        if (vw <= 0 || vh <= 0) {
            return;
        }
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
        Shader sh = new LinearGradient(0, 0, 0, vh, topColor, bottomColor, Shader.TileMode.CLAMP);
        bgPaint.setShader(sh);
        canvas.drawRect(0, 0, vw, vh, bgPaint);
        bgPaint.setShader(null);
    }

    /**
     * Pattern overlay; {@code patternId} null or {@link GamepadLayoutPresetConstants#BACKGROUND_PATTERN_NONE} draws nothing.
     */
    public static void drawPatternOverlay(
            Canvas canvas,
            int vw,
            int vh,
            float density,
            @Nullable String patternId,
            @Nullable Integer fillArgbForContrast,
            Paint patternPaint) {
        if (vw <= 0 || vh <= 0 || patternId == null || patternId.trim().isEmpty()) {
            return;
        }
        String id = patternId.trim().toLowerCase(java.util.Locale.ROOT);
        if (GamepadLayoutPresetConstants.BACKGROUND_PATTERN_NONE.equalsIgnoreCase(id)) {
            return;
        }
        int ref = fillArgbForContrast != null ? fillArgbForContrast : Color.parseColor("#F0F0F0");
        double lum = ColorUtils.calculateLuminance(ref);
        int strokeRgb = lum > 0.52 ? 0xFF000000 : 0xFFFFFFFF;
        int strokeA = lum > 0.52 ? 14 : 18;
        patternPaint.setColor(Color.argb(strokeA, Color.red(strokeRgb), Color.green(strokeRgb), Color.blue(strokeRgb)));

        switch (id) {
            case GamepadLayoutPresetConstants.BACKGROUND_PATTERN_DOTS: {
                patternPaint.setStyle(Paint.Style.FILL);
                float step = 24f * density;
                float r = Math.max(0.55f, 0.35f * density);
                for (float x = step * 0.5f; x < vw; x += step) {
                    for (float y = step * 0.5f; y < vh; y += step) {
                        canvas.drawCircle(x, y, r, patternPaint);
                    }
                }
                patternPaint.setStyle(Paint.Style.STROKE);
                break;
            }
            case GamepadLayoutPresetConstants.BACKGROUND_PATTERN_MICRO_GRID: {
                patternPaint.setStrokeWidth(Math.max(0.5f, 0.35f * density));
                float g = 14f * density;
                for (float x = 0; x <= vw; x += g) {
                    canvas.drawLine(x, 0, x, vh, patternPaint);
                }
                for (float y = 0; y <= vh; y += g) {
                    canvas.drawLine(0, y, vw, y, patternPaint);
                }
                break;
            }
            case GamepadLayoutPresetConstants.BACKGROUND_PATTERN_DIAGONAL_HATCH: {
                patternPaint.setStrokeWidth(Math.max(0.5f, 0.4f * density));
                float spacing = 18f * density;
                for (float k = -vh; k < vw + vh; k += spacing) {
                    canvas.drawLine(k, 0, k + vh, vh, patternPaint);
                }
                break;
            }
            case GamepadLayoutPresetConstants.BACKGROUND_PATTERN_NOISE: {
                patternPaint.setStyle(Paint.Style.FILL);
                float cell = 5f * density;
                float dotR = Math.max(0.45f, 0.28f * density);
                for (int ix = 0; ix * cell < vw; ix++) {
                    for (int iy = 0; iy * cell < vh; iy++) {
                        int h = (ix * 92837111 ^ iy * 689287499) & 0x7fffffff;
                        if ((h % 11) < 3) {
                            float cx = ix * cell + cell * 0.5f;
                            float cy = iy * cell + cell * 0.5f;
                            canvas.drawCircle(cx, cy, dotR, patternPaint);
                        }
                    }
                }
                patternPaint.setStyle(Paint.Style.STROKE);
                break;
            }
            default:
                break;
        }
    }

    /** Fill + optional pattern for small preview surfaces. */
    public static void drawThumbnail(
            Canvas canvas,
            int vw,
            int vh,
            float density,
            @Nullable Integer fillArgb,
            @Nullable String patternId) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawSolidOrGradientFill(canvas, vw, vh, fillArgb, fill);
        Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawPatternOverlay(canvas, vw, vh, density, patternId, fillArgb, overlay);
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
}
