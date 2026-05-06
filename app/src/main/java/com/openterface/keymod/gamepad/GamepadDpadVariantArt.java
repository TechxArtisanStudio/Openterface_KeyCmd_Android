package com.openterface.keymod.gamepad;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * D-pad variant drawing and hit-rect registration for schema {@code DPAD} modules.
 */
public final class GamepadDpadVariantArt {

    private GamepadDpadVariantArt() {}

    @NonNull
    public static String normalizeVariant(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (GamepadLayoutPresetConstants.isAllowedDpadVariant(v)) {
            return v;
        }
        return GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
    }

    public static boolean usesDiscreteDpadHitTargets(@NonNull String variant) {
        return GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(normalizeVariant(variant));
    }

    /**
     * Places split D-pad hit rects. Segment <b>size</b> is fixed ({@link GamepadLayoutPresetConstants#DPAD_SPLIT_SEGMENT_DEPTH_NORM}
     * / {@link GamepadLayoutPresetConstants#DPAD_SPLIT_SEGMENT_BREADTH_NORM}); {@code outerReachRatio} only slides the
     * cluster in/out along each axis (outer edge distance from center = {@code half * outerReachRatio}).
     */
    /** @return outer distance in px from center to segment outer edge (after geometry clamp). */
    public static float registerSplitDpadHitRects(
            float cx, float cy, float half, float gapRatio, float outerReachRatio,
            @NonNull java.util.Map<String, RectF> componentBounds,
            @NonNull List<String> hitOrder) {
        float outerDist = placeSplitDpadRects(cx, cy, half, gapRatio, outerReachRatio, componentBounds);
        hitOrder.add("dpad_up");
        hitOrder.add("dpad_down");
        hitOrder.add("dpad_left");
        hitOrder.add("dpad_right");
        return outerDist;
    }

    /** Writes {@code dpad_up/down/left/right} into {@code componentBounds}; returns outer distance in px for labels. */
    private static float placeSplitDpadRects(
            float cx, float cy, float half, float gapRatio, float outerReachRatio,
            @NonNull java.util.Map<String, RectF> componentBounds) {
        float gap = half * gapRatio;
        float inner = gap * 0.5f;
        float padDepth = half * GamepadLayoutPresetConstants.DPAD_SPLIT_SEGMENT_DEPTH_NORM;
        float padBreadth = half * GamepadLayoutPresetConstants.DPAD_SPLIT_SEGMENT_BREADTH_NORM;
        float outerDist = half * outerReachRatio;
        float minRequired = inner + padDepth + 0.02f * half;
        if (outerDist < minRequired) {
            outerDist = minRequired;
        }
        componentBounds.put("dpad_up", new RectF(
                cx - padBreadth * 0.5f, cy - outerDist,
                cx + padBreadth * 0.5f, cy - outerDist + padDepth));
        componentBounds.put("dpad_down", new RectF(
                cx - padBreadth * 0.5f, cy + outerDist - padDepth,
                cx + padBreadth * 0.5f, cy + outerDist));
        componentBounds.put("dpad_left", new RectF(
                cx - outerDist, cy - padBreadth * 0.5f,
                cx - outerDist + padDepth, cy + padBreadth * 0.5f));
        componentBounds.put("dpad_right", new RectF(
                cx + outerDist - padDepth, cy - padBreadth * 0.5f,
                cx + outerDist, cy + padBreadth * 0.5f));
        return outerDist;
    }

    public static void draw(
            @NonNull Canvas canvas,
            float cx,
            float cy,
            float half,
            float density,
            float stickSizeScale,
            float moduleScale,
            @NonNull String boundsId,
            @NonNull String variant,
            @Nullable String upLabel,
            @Nullable String downLabel,
            @Nullable String leftLabel,
            @Nullable String rightLabel,
            @NonNull Set<String> activeStickDirections,
            int themeAccentPrimary,
            @NonNull Paint retroDpadFillPaint,
            @NonNull Paint retroRingPaint,
            @NonNull Paint retroGlossPaint,
            @NonNull Paint retroShadowPaint,
            @NonNull Paint retroBodyPaint,
            @NonNull Typeface labelTypeface,
            @NonNull java.util.Map<String, RectF> componentBounds,
            @NonNull List<String> hitOrder,
            @Nullable Float dpadSplitGapRatio,
            @Nullable Float dpadSplitOuterReachRatio) {
        String v = normalizeVariant(variant);
        float gapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(dpadSplitGapRatio);
        float outerReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(dpadSplitOuterReachRatio);
        float minOuter = GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(gapRatio);
        if (outerReachRatio < minOuter) {
            outerReachRatio = minOuter;
        }
        float scaledHalf = half * stickSizeScale * moduleScale;
        RectF outer = new RectF(cx - scaledHalf, cy - scaledHalf, cx + scaledHalf, cy + scaledHalf);
        componentBounds.put(boundsId, outer);
        hitOrder.add(boundsId);

        switch (v) {
            case GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT:
                drawSplit(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, labelTypeface, componentBounds, hitOrder, gapRatio, outerReachRatio);
                break;
            case GamepadLayoutPresetConstants.DPAD_VARIANT_DISC:
                drawDisc(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, labelTypeface);
                break;
            case GamepadLayoutPresetConstants.DPAD_VARIANT_PIVOT:
                drawPivot(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, labelTypeface);
                break;
            case GamepadLayoutPresetConstants.DPAD_VARIANT_FLOATING:
                canvas.save();
                canvas.translate(0, -2.2f * density);
                drawCross(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, retroBodyPaint, labelTypeface, true);
                canvas.restore();
                break;
            case GamepadLayoutPresetConstants.DPAD_VARIANT_CLICKY:
                drawCross(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, retroBodyPaint, labelTypeface, true);
                break;
            case GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS:
            default:
                drawCross(canvas, cx, cy, scaledHalf, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                        activeStickDirections, themeAccentPrimary, retroDpadFillPaint, retroRingPaint, retroGlossPaint,
                        retroShadowPaint, retroBodyPaint, labelTypeface, false);
                break;
        }
    }

    private static void drawSplit(
            Canvas canvas, float cx, float cy, float half, float density, String boundsId,
            @Nullable String upLabel, @Nullable String downLabel, @Nullable String leftLabel, @Nullable String rightLabel,
            Set<String> activeStickDirections, int themeAccentPrimary,
            Paint retroDpadFillPaint, Paint retroRingPaint, Paint retroGlossPaint, Paint retroShadowPaint,
            Typeface labelTypeface, java.util.Map<String, RectF> componentBounds, List<String> hitOrder,
            float gapRatio, float outerReachRatio) {
        float outerDist = registerSplitDpadHitRects(cx, cy, half, gapRatio, outerReachRatio, componentBounds, hitOrder);
        RectF up = componentBounds.get("dpad_up");
        RectF dn = componentBounds.get("dpad_down");
        RectF lf = componentBounds.get("dpad_left");
        RectF rt = componentBounds.get("dpad_right");
        if (up == null || dn == null || lf == null || rt == null) {
            return;
        }
        int pressTint = applyAlphaInt(themeAccentPrimary, 88);
        drawSplitPad(canvas, up, density, boundsId + "_up", activeStickDirections, pressTint,
                retroDpadFillPaint, retroRingPaint, retroGlossPaint, retroShadowPaint);
        drawSplitPad(canvas, dn, density, boundsId + "_down", activeStickDirections, pressTint,
                retroDpadFillPaint, retroRingPaint, retroGlossPaint, retroShadowPaint);
        drawSplitPad(canvas, lf, density, boundsId + "_left", activeStickDirections, pressTint,
                retroDpadFillPaint, retroRingPaint, retroGlossPaint, retroShadowPaint);
        drawSplitPad(canvas, rt, density, boundsId + "_right", activeStickDirections, pressTint,
                retroDpadFillPaint, retroRingPaint, retroGlossPaint, retroShadowPaint);
        float labelR = Math.max(outerDist, half * 0.35f);
        drawDirectionLabels(canvas, cx, cy, labelR, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                activeStickDirections, labelTypeface);
    }

    private static void drawSplitPad(
            Canvas canvas, RectF r, float density, String activeKey, Set<String> activeStickDirections, int pressTint,
            Paint retroDpadFillPaint, Paint retroRingPaint, Paint retroGlossPaint, Paint retroShadowPaint) {
        float corner = Math.min(11f * density, Math.min(r.width(), r.height()) * 0.35f);
        retroShadowPaint.setMaskFilter(new BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x40000000);
        canvas.save();
        canvas.translate(0, 1.5f * density);
        canvas.drawRoundRect(r, corner, corner, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);
        canvas.restore();
        Shader bodyGrad = new LinearGradient(r.left, r.top, r.right, r.bottom,
                Color.parseColor("#4E4E56"), Color.parseColor("#303036"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setShader(bodyGrad);
        canvas.drawRoundRect(r, corner, corner, retroDpadFillPaint);
        retroDpadFillPaint.setShader(null);
        if (activeStickDirections.contains(activeKey)) {
            retroGlossPaint.setShader(null);
            retroGlossPaint.setColor(pressTint);
            canvas.drawRoundRect(r, corner, corner, retroGlossPaint);
        }
        retroRingPaint.setShader(null);
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.2f, 1.4f * density));
        retroRingPaint.setColor(0xFF1C1A1F);
        canvas.drawRoundRect(r, corner, corner, retroRingPaint);
    }

    private static void drawDisc(
            Canvas canvas, float cx, float cy, float half, float density, String boundsId,
            @Nullable String upLabel, @Nullable String downLabel, @Nullable String leftLabel, @Nullable String rightLabel,
            Set<String> activeStickDirections, int themeAccentPrimary,
            Paint retroDpadFillPaint, Paint retroRingPaint, Paint retroGlossPaint, Paint retroShadowPaint,
            Typeface labelTypeface) {
        retroShadowPaint.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x48000000);
        canvas.save();
        canvas.translate(0, 2.5f * density);
        canvas.drawCircle(cx, cy, half * 0.98f, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);
        canvas.restore();
        Shader bodyGrad = new RadialGradient(cx, cy - half * 0.12f, half * 1.02f,
                Color.parseColor("#5A5A62"), Color.parseColor("#2E2E34"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setShader(bodyGrad);
        canvas.drawCircle(cx, cy, half * 0.98f, retroDpadFillPaint);
        retroDpadFillPaint.setShader(null);
        int pressTint = applyAlphaInt(themeAccentPrimary, 90);
        retroGlossPaint.setShader(null);
        retroGlossPaint.setColor(pressTint);
        drawDiscWedge(canvas, cx, cy, half, -Math.PI * 0.75, -Math.PI * 0.25, boundsId + "_up", activeStickDirections, retroGlossPaint);
        drawDiscWedge(canvas, cx, cy, half, Math.PI * 0.25, Math.PI * 0.75, boundsId + "_right", activeStickDirections, retroGlossPaint);
        drawDiscWedge(canvas, cx, cy, half, Math.PI * 0.75, Math.PI * 1.25, boundsId + "_down", activeStickDirections, retroGlossPaint);
        drawDiscWedge(canvas, cx, cy, half, -Math.PI * 0.25, Math.PI * 0.25, boundsId + "_left", activeStickDirections, retroGlossPaint);
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, 1.8f * density));
        retroRingPaint.setColor(0xFF1C1A1F);
        canvas.drawCircle(cx, cy, half * 0.98f, retroRingPaint);
        drawDirectionLabels(canvas, cx, cy, half, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                activeStickDirections, labelTypeface);
    }

    private static void drawDiscWedge(
            Canvas canvas, float cx, float cy, float half, double a0, double a1,
            String activeKey, Set<String> activeStickDirections, Paint retroGlossPaint) {
        if (!activeStickDirections.contains(activeKey)) {
            return;
        }
        Path p = new Path();
        p.moveTo(cx, cy);
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            double t = a0 + (a1 - a0) * (i / (double) steps);
            float x = cx + (float) (Math.cos(t) * half * 0.95f);
            float y = cy + (float) (Math.sin(t) * half * 0.95f);
            p.lineTo(x, y);
        }
        p.close();
        canvas.drawPath(p, retroGlossPaint);
    }

    private static void drawPivot(
            Canvas canvas, float cx, float cy, float half, float density, String boundsId,
            @Nullable String upLabel, @Nullable String downLabel, @Nullable String leftLabel, @Nullable String rightLabel,
            Set<String> activeStickDirections, int themeAccentPrimary,
            Paint retroDpadFillPaint, Paint retroRingPaint, Paint retroGlossPaint, Paint retroShadowPaint,
            Typeface labelTypeface) {
        float rh = half * 0.42f;
        float rw = half * 0.88f;
        RectF hbar = new RectF(cx - rw, cy - rh, cx + rw, cy + rh);
        RectF vbar = new RectF(cx - rh, cy - rw, cx + rh, cy + rw);
        float corner = Math.min(12f * density, rh * 0.6f);
        retroShadowPaint.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(0x48000000);
        canvas.save();
        canvas.translate(0, 2.5f * density);
        canvas.drawRoundRect(hbar, corner, corner, retroShadowPaint);
        canvas.drawRoundRect(vbar, corner, corner, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);
        canvas.restore();
        Shader g1 = new LinearGradient(cx - rw, cy, cx + rw, cy,
                Color.parseColor("#4E4E56"), Color.parseColor("#303036"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setShader(g1);
        canvas.drawRoundRect(hbar, corner, corner, retroDpadFillPaint);
        Shader g2 = new LinearGradient(cx, cy - rw, cx, cy + rw,
                Color.parseColor("#4E4E56"), Color.parseColor("#303036"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setShader(g2);
        canvas.drawRoundRect(vbar, corner, corner, retroDpadFillPaint);
        retroDpadFillPaint.setShader(null);
        int pressTint = applyAlphaInt(themeAccentPrimary, 88);
        retroGlossPaint.setShader(null);
        retroGlossPaint.setColor(pressTint);
        if (activeStickDirections.contains(boundsId + "_up")) {
            canvas.drawRect(cx - rw * 0.35f, cy - rw, cx + rw * 0.35f, cy - rh * 0.2f, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_down")) {
            canvas.drawRect(cx - rw * 0.35f, cy + rh * 0.2f, cx + rw * 0.35f, cy + rw, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_left")) {
            canvas.drawRect(cx - rw, cy - rh * 0.35f, cx - rh * 0.2f, cy + rh * 0.35f, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_right")) {
            canvas.drawRect(cx + rh * 0.2f, cy - rh * 0.35f, cx + rw, cy + rh * 0.35f, retroGlossPaint);
        }
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, 1.8f * density));
        retroRingPaint.setColor(0xFF1C1A1F);
        canvas.drawRoundRect(hbar, corner, corner, retroRingPaint);
        canvas.drawRoundRect(vbar, corner, corner, retroRingPaint);
        float hub = rh * 1.1f;
        retroDpadFillPaint.setShader(null);
        retroDpadFillPaint.setColor(0xFF232128);
        canvas.drawCircle(cx, cy, hub, retroDpadFillPaint);
        drawDirectionLabels(canvas, cx, cy, half, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                activeStickDirections, labelTypeface);
    }

    private static void drawCross(
            Canvas canvas, float cx, float cy, float half, float density, String boundsId,
            @Nullable String upLabel, @Nullable String downLabel, @Nullable String leftLabel, @Nullable String rightLabel,
            Set<String> activeStickDirections, int themeAccentPrimary,
            Paint retroDpadFillPaint, Paint retroRingPaint, Paint retroGlossPaint, Paint retroShadowPaint,
            Paint retroBodyPaint, Typeface labelTypeface, boolean thickRim) {
        float barHalf = half * 0.36f;
        float corner = Math.min(11f * density, barHalf * 0.55f);
        RectF vert = new RectF(cx - barHalf, cy - half, cx + barHalf, cy + half);
        RectF horiz = new RectF(cx - half, cy - barHalf, cx + half, cy + barHalf);
        Path armV = new Path();
        armV.addRoundRect(vert, corner, corner, Path.Direction.CW);
        Path armH = new Path();
        armH.addRoundRect(horiz, corner, corner, Path.Direction.CW);
        Path cross = new Path();
        cross.op(armV, armH, Path.Op.UNION);
        retroShadowPaint.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        retroShadowPaint.setColor(thickRim ? 0x52000000 : 0x48000000);
        canvas.save();
        canvas.translate(0, thickRim ? 3.2f * density : 2.5f * density);
        canvas.drawPath(cross, retroShadowPaint);
        retroShadowPaint.setMaskFilter(null);
        canvas.restore();
        Shader bodyGrad = new LinearGradient(cx - half, cy - half, cx + half, cy + half,
                Color.parseColor("#4E4E56"), Color.parseColor("#303036"), Shader.TileMode.CLAMP);
        retroDpadFillPaint.setStyle(Paint.Style.FILL);
        retroDpadFillPaint.setShader(bodyGrad);
        canvas.drawPath(cross, retroDpadFillPaint);
        retroDpadFillPaint.setShader(null);
        int pressTint = applyAlphaInt(themeAccentPrimary, 88);
        retroGlossPaint.setShader(null);
        retroGlossPaint.setColor(pressTint);
        if (activeStickDirections.contains(boundsId + "_up")) {
            canvas.drawRect(cx - barHalf, cy - half, cx + barHalf, cy, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_down")) {
            canvas.drawRect(cx - barHalf, cy, cx + barHalf, cy + half, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_left")) {
            canvas.drawRect(cx - half, cy - barHalf, cx, cy + barHalf, retroGlossPaint);
        }
        if (activeStickDirections.contains(boundsId + "_right")) {
            canvas.drawRect(cx, cy - barHalf, cx + half, cy + barHalf, retroGlossPaint);
        }
        retroRingPaint.setShader(null);
        retroRingPaint.setStyle(Paint.Style.STROKE);
        retroRingPaint.setStrokeWidth(Math.max(1.5f, (thickRim ? 2.1f : 1.8f) * density));
        retroRingPaint.setColor(0xFF1C1A1F);
        canvas.drawPath(cross, retroRingPaint);
        retroRingPaint.setStrokeWidth(Math.max(1f, 1.1f * density));
        retroRingPaint.setColor(0x66FFFFFF);
        canvas.save();
        canvas.translate(0, -0.6f * density);
        canvas.drawPath(cross, retroRingPaint);
        canvas.restore();
        float hub = Math.min(barHalf * 0.95f, half * 0.22f);
        retroBodyPaint.setShader(null);
        retroBodyPaint.setColor(0xFF232128);
        canvas.drawRoundRect(cx - hub, cy - hub, cx + hub, cy + hub, corner * 0.35f, corner * 0.35f, retroBodyPaint);
        drawDirectionLabels(canvas, cx, cy, half, density, boundsId, upLabel, downLabel, leftLabel, rightLabel,
                activeStickDirections, labelTypeface);
    }

    private static void drawDirectionLabels(
            Canvas canvas, float cx, float cy, float half, float density, String boundsId,
            @Nullable String upLabel, @Nullable String downLabel, @Nullable String leftLabel, @Nullable String rightLabel,
            Set<String> activeStickDirections, Typeface labelTypeface) {
        Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dirPaint.setTextAlign(Paint.Align.CENTER);
        dirPaint.setFakeBoldText(true);
        dirPaint.setTextSize(half * 0.30f);
        dirPaint.setTypeface(labelTypeface);
        int activeLabel = Color.parseColor("#F5F2EA");
        int dim = Color.parseColor("#8C8894");
        float labelLift = half * 0.02f;
        dirPaint.setShadowLayer(1.5f, 0f, 1f, 0x66000000);
        dirPaint.setColor(activeStickDirections.contains(boundsId + "_up") ? activeLabel : dim);
        canvas.drawText(upLabel != null ? upLabel : "W", cx, cy - half * 0.58f + labelLift, dirPaint);
        dirPaint.setColor(activeStickDirections.contains(boundsId + "_down") ? activeLabel : dim);
        canvas.drawText(downLabel != null ? downLabel : "S", cx, cy + half * 0.72f + labelLift, dirPaint);
        dirPaint.setColor(activeStickDirections.contains(boundsId + "_left") ? activeLabel : dim);
        canvas.drawText(leftLabel != null ? leftLabel : "A", cx - half * 0.64f, cy + half * 0.14f + labelLift, dirPaint);
        dirPaint.setColor(activeStickDirections.contains(boundsId + "_right") ? activeLabel : dim);
        canvas.drawText(rightLabel != null ? rightLabel : "D", cx + half * 0.64f, cy + half * 0.14f + labelLift, dirPaint);
        dirPaint.clearShadowLayer();
    }

    private static int applyAlphaInt(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
