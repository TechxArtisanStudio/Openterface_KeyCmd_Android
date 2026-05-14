package com.openterface.keymod;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

/**
 * Beginner tutorial overlay that highlights views step-by-step.
 */
public class TutorialOverlay extends FrameLayout {

    public static final String PREFS_NAME = "TutorialPrefs";
    public static final String KEY_TUTORIAL_SHOWN = "tutorial_shown_v2";

    private final View dimView;
    private final HighlightView highlightView;
    private final CardView tooltipCard;
    private final TextView tooltipText;
    private final Button nextButton;
    private final Button skipButton;
    private final Rect highlightRect = new Rect();

    private Step[] steps;
    private int currentStep = 0;
    /** When false, {@link #dismiss()} does not set {@link #KEY_TUTORIAL_SHOWN} (used for non-Basic mode tours). */
    private boolean markBasicQuickStartPrefOnDismiss = true;
    @Nullable
    private Runnable onDismissExtra;

    public static boolean isShown(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_TUTORIAL_SHOWN, false);
    }

    public static void markShown(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TUTORIAL_SHOWN, true).apply();
    }

    public TutorialOverlay(Context context) {
        super(context);
        int surfaceColor = resolveThemeColor(context, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
        int onSurfaceColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int primaryColor = resolveThemeColor(context, android.R.attr.colorPrimary, 0xFF1976D2);
        int secondaryTextColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF757575);

        // Dark dim layer
        dimView = new View(context);
        dimView.setBackgroundColor(0xB3000000);
        LayoutParams dimParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(dimView, dimParams);

        // Custom highlight view that draws a glowing border around the target
        highlightView = new HighlightView(context, primaryColor);
        addView(highlightView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Tooltip card
        tooltipCard = new CardView(context);
        tooltipCard.setRadius(12f);
        tooltipCard.setCardElevation(16f);
        tooltipCard.setUseCompatPadding(false);
        tooltipCard.setContentPadding(0, 0, 0, 0);

        int padding = dpToPx(20);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(surfaceColor);

        tooltipText = new TextView(context);
        tooltipText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tooltipText.setTextColor(onSurfaceColor);
        tooltipText.setGravity(Gravity.CENTER);
        tooltipText.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        content.addView(tooltipText, textParams);

        // Button row
        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        int topMargin = dpToPx(12);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = topMargin;
        buttonRow.setLayoutParams(rowParams);

        skipButton = new Button(context);
        skipButton.setText(context.getString(R.string.tutorial_skip));
        skipButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        skipButton.setTextColor(secondaryTextColor);
        skipButton.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        skipParams.setMargins(0, 0, dpToPx(16), 0);
        skipButton.setLayoutParams(skipParams);
        skipButton.setOnClickListener(v -> dismiss());

        nextButton = new Button(context);
        nextButton.setText(context.getString(R.string.tutorial_next));
        nextButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        nextButton.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dpToPx(8));
        bg.setColor(primaryColor);
        nextButton.setBackground(bg);
        nextButton.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));
        nextButton.setOnClickListener(v -> advance());

        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nextButton.setLayoutParams(nextParams);

        buttonRow.addView(skipButton);
        buttonRow.addView(nextButton);
        content.addView(buttonRow);

        tooltipCard.addView(content);
        LayoutParams cardParams = new LayoutParams(
                dpToPx(300), LayoutParams.WRAP_CONTENT);
        tooltipCard.setLayoutParams(cardParams);
        addView(tooltipCard);
    }

    public void setSteps(Step[] steps) {
        this.steps = steps;
        this.currentStep = 0;
        showCurrentStep();
    }

    /**
     * Default true (KM Basic quick start). Set false for per-mode guides so completing them does not
     * mark the Basic tutorial pref.
     */
    public void setMarkBasicQuickStartPrefOnDismiss(boolean mark) {
        this.markBasicQuickStartPrefOnDismiss = mark;
    }

    /** Runs once when the overlay is removed (Done, Skip, or last step finished). */
    public void setOnDismissExtra(@Nullable Runnable onDismissExtra) {
        this.onDismissExtra = onDismissExtra;
    }

    private void showCurrentStep() {
        if (steps == null || currentStep >= steps.length) {
            dismiss();
            return;
        }

        Step step = steps[currentStep];
        step.onShow(getContext());
        String stepIndicator =
                getContext().getString(R.string.tutorial_step_indicator, currentStep + 1, steps.length);
        tooltipText.setText(stepIndicator + step.description());

        if (currentStep == steps.length - 1) {
            nextButton.setText(getContext().getString(R.string.tutorial_done));
        } else {
            nextButton.setText(getContext().getString(R.string.tutorial_next));
        }

        // Find the target view
        View targetView = null;
        for (int viewId : step.targetViewIds()) {
            targetView = ((Activity) getContext()).findViewById(viewId);
            if (targetView != null && targetView.isShown()) break;
        }

        final View finalTarget = targetView;
        final int delay = step.delayMs();
        final int insetTop = dpToPx(step.insetTopDp());
        final int insetBottom = dpToPx(step.insetBottomDp());
        postDelayed(
                () -> {
                    if (finalTarget != null) {
                        int[] overlayPos = new int[2];
                        getLocationOnScreen(overlayPos);
                        int[] targetPos = new int[2];
                        finalTarget.getLocationOnScreen(targetPos);

                        highlightRect.set(
                                targetPos[0] - overlayPos[0] - insetTop,
                                targetPos[1] - overlayPos[1] - insetTop,
                                targetPos[0] - overlayPos[0] + finalTarget.getWidth() + insetTop,
                                targetPos[1] - overlayPos[1] + finalTarget.getHeight() + insetBottom);
                        highlightView.setHighlightRect(highlightRect);
                        positionTooltip(finalTarget);
                    } else {
                        highlightRect.setEmpty();
                        highlightView.setHighlightRect(highlightRect);
                        positionTooltipFallback();
                    }
                },
                delay);
    }

    private void positionTooltip(View targetView) {
        int[] location = new int[2];
        targetView.getLocationOnScreen(location);
        int viewTop = location[1];

        int cardHeight = dpToPx(140);
        int tooltipBottom = viewTop - dpToPx(40);

        LayoutParams params = (LayoutParams) tooltipCard.getLayoutParams();
        if (tooltipBottom > cardHeight) {
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = Math.max(0, tooltipBottom - cardHeight);
        } else {
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.bottomMargin = dpToPx(16);
        }
        tooltipCard.setLayoutParams(params);
    }

    /** When no highlight target is visible (e.g. wrong submode), keep the card readable at the bottom. */
    private void positionTooltipFallback() {
        LayoutParams params = (LayoutParams) tooltipCard.getLayoutParams();
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.topMargin = 0;
        params.bottomMargin = dpToPx(24);
        tooltipCard.setLayoutParams(params);
    }

    private void advance() {
        if (currentStep < steps.length - 1) {
            currentStep++;
            showCurrentStep();
        } else {
            dismiss();
        }
    }

    private void dismiss() {
        if (markBasicQuickStartPrefOnDismiss) {
            markShown(getContext());
        }
        if (onDismissExtra != null) {
            Runnable r = onDismissExtra;
            onDismissExtra = null;
            r.run();
        }
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (steps != null && currentStep < steps.length && event.getAction() == android.view.MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            boolean inHighlight = highlightRect.contains((int) x, (int) y);
            if (!inHighlight) {
                advance();
            }
        }
        return true;
    }

    /**
     * Custom view that draws a glowing border around the highlight area.
     */
    private static class HighlightView extends View {
        private final RectF drawRect = new RectF();
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int radius = 0;

        public HighlightView(Context context, int borderColor) {
            super(context);
            borderPaint.setColor(borderColor);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(6f);
        }

        public void setHighlightRect(Rect rect) {
            float density = getContext().getResources().getDisplayMetrics().density;
            this.radius = (int) (12 * density);
            drawRect.set(rect);
            borderPaint.setStrokeWidth(3 * density);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (drawRect.width() > 0 && drawRect.height() > 0) {
                canvas.drawRoundRect(drawRect, radius, radius, borderPaint);
            }
        }
    }

    public interface Step {
        int[] targetViewIds();
        String description();
        String buttonText();
        default void onShow(Context context) {}
        default int delayMs() { return 0; }
        default int insetTopDp() { return 8; }
        default int insetBottomDp() { return 8; }
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static int resolveThemeColor(Context context, int attrId, int fallback) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrId, typedValue, true)) {
            return typedValue.data;
        }
        return fallback;
    }
}
