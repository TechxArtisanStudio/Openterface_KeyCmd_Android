package com.openterface.keymod;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.openterface.keymod.help.HelpImageConfig;
import com.openterface.keymod.help.HelpImageConfigManager;
import com.openterface.keymod.help.HelpImageDownloader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Beginner tutorial overlay that highlights views step-by-step.
 */
public class TutorialOverlay extends FrameLayout {

    public static final String PREFS_NAME = "TutorialPrefs";
    public static final String KEY_TUTORIAL_SHOWN = "tutorial_shown_v2";

    private final View dimView;
    private final HighlightView highlightView;
    private final CardView tooltipCard;
    private final Rect highlightRect = new Rect();

    // Layout-related fields are non-final so the tooltip card content can be rebuilt
    // when the device rotates (see {@link #onConfigurationChanged}).
    private ImageView helpImageView;
    private PlayerView helpVideoView;
    private ProgressBar loadingIndicator;
    private TextView tooltipText;
    private Button nextButton;
    private Button skipButton;
    private LinearLayout buttonRow;
    private boolean isLandscape;

    /**
     * In landscape, media views are wrapped in a left-side column. This is {@code null} in portrait,
     * where media views are added directly into the vertical content column. Hidden when no media
     * is available for a step so the text column can expand to full width.
     */
    @Nullable
    private LinearLayout mediaColumn;

    private HelpImageConfig config;
    private String currentModeKey;

    @Nullable
    private ExoPlayer videoPlayer;

    private Step[] steps;
    private int currentStep = 0;
    /** When false, {@link #dismiss()} does not set {@link #KEY_TUTORIAL_SHOWN} (used for non-Basic mode tours). */
    private boolean markBasicQuickStartPrefOnDismiss = true;
    @Nullable
    private Runnable onDismissExtra;

    /** Current video error listener, tracked to prevent accumulation on the player. */
    @Nullable
    private Player.Listener currentVideoListener;

    /** URL of the in-progress background video download (for cancellation on step change). */
    @Nullable
    private String backgroundVideoDownloadUrl;

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

        // Build the tooltip card content based on the current orientation.
        buildTooltipContent();

        addView(tooltipCard);
    }

    /**
     * (Re)builds the tooltip card's inner content layout based on the current orientation.
     * Called once from the constructor and again from {@link #onConfigurationChanged(Configuration)}
     * when the device rotates.
     */
    private void buildTooltipContent() {
        Context context = getContext();
        int surfaceColor = resolveThemeColor(context, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
        int onSurfaceColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int primaryColor = resolveThemeColor(context, android.R.attr.colorPrimary, 0xFF1976D2);
        int secondaryTextColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF757575);

        // Remove any previous content (e.g. after a rotation rebuild).
        tooltipCard.removeAllViews();

        // Detect orientation for layout direction
        isLandscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        int padding = dpToPx(isLandscape ? 16 : 20);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(isLandscape
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(surfaceColor);

        // --- Media column / area ---
        // In landscape, media lives in a left-side vertical container.
        // In portrait, media is added directly into the vertical content column.
        // The mediaColumn is hidden dynamically when no media is available for a step,
        // allowing the text column to expand to full width (landscape only).
        if (isLandscape) {
            mediaColumn = new LinearLayout(context);
            mediaColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mediaColParams = new LinearLayout.LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f);
            mediaColParams.setMarginEnd(dpToPx(12));
            content.addView(mediaColumn, mediaColParams);
            // Start hidden; shown when a step has media to display.
            mediaColumn.setVisibility(View.GONE);
        } else {
            mediaColumn = null;
        }

        // Local reference used only during construction. In portrait mode media views
        // go directly into the vertical content column; in landscape they go into
        // the left-side mediaColumn (which starts GONE until a step has media).
        final LinearLayout mediaTarget = isLandscape ? mediaColumn : content;

        // Help image area (GIF/PNG overlay)
        helpImageView = new ImageView(context);
        helpImageView.setAdjustViewBounds(true);
        helpImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        helpImageView.setMaxHeight(dpToPx(isLandscape ? 140 : 180));
        helpImageView.setVisibility(View.GONE);
        helpImageView.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        imageParams.bottomMargin = dpToPx(8);
        mediaTarget.addView(helpImageView, imageParams);

        // Help video area (MP4 overlay)
        helpVideoView = new PlayerView(context);
        helpVideoView.setResizeMode(
                com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        helpVideoView.setUseController(true);
        helpVideoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        helpVideoView.setVisibility(View.GONE);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        videoParams.bottomMargin = dpToPx(8);
        mediaTarget.addView(helpVideoView, videoParams);

        // Loading indicator
        loadingIndicator = new ProgressBar(context);
        loadingIndicator.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                dpToPx(32), dpToPx(32));
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.bottomMargin = dpToPx(4);
        mediaTarget.addView(loadingIndicator, loadingParams);

        // --- Text + button column / area ---
        // In landscape, text and buttons live in a right-side vertical container.
        // In portrait, they are added directly into the vertical content column.
        final LinearLayout textColumn;
        if (isLandscape) {
            textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f);
            textColParams.setMarginStart(dpToPx(4));
            content.addView(textColumn, textColParams);
        } else {
            textColumn = content;
        }

        tooltipText = new TextView(context);
        tooltipText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, isLandscape ? 14 : 15);
        tooltipText.setTextColor(onSurfaceColor);
        tooltipText.setGravity(isLandscape ? Gravity.START : Gravity.CENTER);
        tooltipText.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textColumn.addView(tooltipText, textParams);

        // Button row
        buttonRow = new LinearLayout(context);
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
        textColumn.addView(buttonRow);

        tooltipCard.addView(content);
        int cardWidthDp;
        if (isLandscape) {
            // In landscape, use ~85% of screen width but cap at 560dp so the card
            // stays readable and does not overflow on narrow landscape devices.
            int screenWidthDp = context.getResources().getConfiguration().screenWidthDp;
            cardWidthDp = Math.min(560, (int) (screenWidthDp * 0.85f));
            // Ensure the card is at least 360dp wide so the two columns are usable.
            cardWidthDp = Math.max(cardWidthDp, 360);
        } else {
            cardWidthDp = 300;
        }
        LayoutParams cardParams = new LayoutParams(
                dpToPx(cardWidthDp), LayoutParams.WRAP_CONTENT);
        tooltipCard.setLayoutParams(cardParams);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Release the old player so it can be re-created against the new PlayerView
        // that buildTooltipContent() creates below.
        removeCurrentVideoListener();
        backgroundVideoDownloadUrl = null;
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }

        // Rebuild the tooltip card content for the new orientation.
        buildTooltipContent();

        // Re-show the current step so the tooltip is re-positioned and media re-loaded
        // for the new layout. If no steps are set yet this is a no-op.
        if (steps != null && currentStep < steps.length) {
            showCurrentStep();
        } else {
            positionTooltipFallback();
        }
    }

    public void setSteps(Step[] steps) {
        this.steps = steps;
        this.currentStep = 0;
        hideImeForOverlay();
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

    /**
     * Set the help-image config and mode key so the overlay can show images
     * alongside each step's text. Call before {@link #setSteps(Step[])}.
     */
    public void setConfig(@NonNull HelpImageConfig config, @NonNull String modeKey) {
        this.config = config;
        this.currentModeKey = modeKey;
    }

    /**
     * Look up the StepConfig for a given imageKey from the current mode.
     * Used to read poster/autoPlay/loop fields.
     */
    @Nullable
    private HelpImageConfig.StepConfig getStepConfig(String imageKey) {
        if (config == null || currentModeKey == null || imageKey == null) return null;
        HelpImageConfig.ModeConfig mode = config.modes.get(currentModeKey);
        if (mode == null || mode.steps == null) return null;
        for (HelpImageConfig.StepConfig sc : mode.steps) {
            if (imageKey.equals(sc.id)) return sc;
        }
        return null;
    }

    private void showCurrentStep() {
        if (steps == null || currentStep >= steps.length) {
            dismiss();
            return;
        }

        hideImeForOverlay();

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

        // Load help media for this step (video preferred, fallback to image)
        stopVideo();
        loadHelpMedia(step);

        // Preload media for upcoming steps in the background
        preloadUpcomingSteps();

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
                    hideImeForOverlay();
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

        boolean isLandscape = getContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // In landscape the card is wider but shorter (media + text are side-by-side).
        int cardHeight = dpToPx(isLandscape ? 220 : 140);
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        hideImeForOverlay();
    }

    /**
     * Dismisses the soft keyboard so it does not cover the tooltip or highlights in Keyboard &amp; Mouse
     * modes (Compose, IME surfaces, etc.).
     */
    private void hideImeForOverlay() {
        Context c = getContext();
        if (!(c instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) c;
        android.view.Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }
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
        /** Returns the help-image key for this step, or null if no image is associated. */
        @Nullable
        default String imageKey() { return null; }
    }

    /**
     * Load help media for the given step (video only for now).
     * If a video is configured, attempts to play it.
     * If no video, hides media area and shows text-only guidance.
     */
    private void loadHelpMedia(Step step) {
        String imageKey = step.imageKey();
        if (config == null || currentModeKey == null || imageKey == null) {
            hideMediaArea();
            return;
        }

        // Try video only
        String videoUrl = config.getVideoUrl(currentModeKey, imageKey);
        if (videoUrl != null) {
            showVideoWithUrl(videoUrl, imageKey);
            return;
        }

        // No video configured — hide media area, show text-only guidance
        hideMediaArea();
    }

    /**
     * Load and play a video. Uses local cache if available (instant start),
     * otherwise streams from remote URL while downloading to cache in background.
     * On failure, hides media area and shows text-only guidance.
     */
    private void showVideoWithUrl(String videoUrl, String imageKey) {
        showMediaColumn();
        helpImageView.setVisibility(View.GONE);
        helpVideoView.setVisibility(View.VISIBLE);

        HelpImageDownloader downloader = HelpImageDownloader.getInstance(getContext());
        File cachedVideo = downloader.getCachedVideoFile(videoUrl, config.version);

        if (cachedVideo != null) {
            // Cached → play from local file (instant start)
            Log.d("TutorialOverlay", "Using cached video: " + cachedVideo.getAbsolutePath());
            playVideoFromUri(Uri.fromFile(cachedVideo), imageKey);
            return;
        }

        // Not cached → show poster, stream from remote URL, cache in background
        showPosterForStep(imageKey);
        playVideoFromUri(Uri.parse(videoUrl), imageKey);

        // Background download for next-time instant start
        backgroundVideoDownloadUrl = videoUrl;
        downloader.downloadVideo(videoUrl, config.version, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(@NonNull File localFile) {
                backgroundVideoDownloadUrl = null;
                Log.d("TutorialOverlay", "Video cached for next time: " + localFile);
            }

            @Override
            public void onError(@NonNull Exception error) {
                backgroundVideoDownloadUrl = null;
                Log.w("TutorialOverlay", "Background video cache failed", error);
            }
        });
    }

    /**
     * Unified video playback: initializes player if needed, removes old listener,
     * configures autoPlay/loop from StepConfig, and attaches a tracked error listener.
     */
    private void playVideoFromUri(Uri uri, String imageKey) {
        if (videoPlayer == null) {
            videoPlayer = new ExoPlayer.Builder(getContext()).build();
        }
        // Always (re)attach the player to the current helpVideoView — this handles
        // the case where the view was rebuilt after a configuration change.
        helpVideoView.setPlayer(videoPlayer);

        // Remove old listener to prevent accumulation
        removeCurrentVideoListener();

        // Read loop/autoPlay from StepConfig
        HelpImageConfig.StepConfig stepConfig = getStepConfig(imageKey);
        int repeatMode = (stepConfig != null && stepConfig.loop)
                ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF;
        boolean autoPlay = stepConfig == null || stepConfig.autoPlay;

        videoPlayer.setMediaItem(MediaItem.fromUri(uri));
        videoPlayer.setRepeatMode(repeatMode);

        // Attach tracked error listener for fallback
        currentVideoListener = new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                post(() -> fallbackToImage(imageKey));
            }
        };
        videoPlayer.addListener(currentVideoListener);

        videoPlayer.prepare();
        videoPlayer.setPlayWhenReady(autoPlay);
    }

    /**
     * Remove the current video error listener to prevent accumulation.
     */
    private void removeCurrentVideoListener() {
        if (videoPlayer != null && currentVideoListener != null) {
            videoPlayer.removeListener(currentVideoListener);
        }
        currentVideoListener = null;
    }

    /**
     * When video playback fails, hide media area and show text-only guidance.
     * Image fallback is disabled because remote image assets are not yet available.
     */
    private void fallbackToImage(String imageKey) {
        stopVideo();
        hideMediaArea();
    }

    /**
     * Stop video playback, remove error listener, and cancel background download marker.
     */
    private void stopVideo() {
        removeCurrentVideoListener();
        backgroundVideoDownloadUrl = null;
        if (videoPlayer != null) {
            videoPlayer.stop();
            videoPlayer.clearMediaItems();
        }
        helpVideoView.setVisibility(View.GONE);
    }

    private void showImage(File localFile) {
        showMediaColumn();
        helpImageView.setVisibility(View.VISIBLE);
        helpVideoView.setVisibility(View.GONE);
        Glide.with(getContext())
                .load(localFile)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // already cached by downloader
                .into(helpImageView);
    }

    /**
     * Show a poster image while the video is streaming/loading.
     */
    private void showPosterForStep(String imageKey) {
        HelpImageConfig.StepConfig stepConfig = getStepConfig(imageKey);
        if (stepConfig == null || stepConfig.poster == null || stepConfig.poster.isEmpty()) return;

        String posterUrl;
        if (stepConfig.poster.startsWith("http://") || stepConfig.poster.startsWith("https://")) {
            posterUrl = stepConfig.poster;
        } else {
            String base = config.baseUrl;
            if (!base.endsWith("/")) base += "/";
            posterUrl = base + stepConfig.poster;
        }

        helpImageView.setVisibility(View.VISIBLE);
        Glide.with(getContext())
                .load(posterUrl)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(helpImageView);
    }

    /**
     * Preload media for the next 2 steps in the background (fire and forget).
     */
    private void preloadUpcomingSteps() {
        if (config == null || currentModeKey == null || steps == null) return;
        String version = config.version;
        HelpImageDownloader downloader = HelpImageDownloader.getInstance(getContext());

        List<String> imageUrls = new ArrayList<>();
        List<String> videoUrls = new ArrayList<>();

        for (int i = currentStep + 1; i <= Math.min(currentStep + 2, steps.length - 1); i++) {
            String imageKey = steps[i].imageKey();
            if (imageKey == null) continue;

            String videoUrl = config.getVideoUrl(currentModeKey, imageKey);
            if (videoUrl != null && !downloader.isVideoCached(videoUrl, version)) {
                videoUrls.add(videoUrl);
            }

            String imageUrl = config.getImageUrl(currentModeKey, imageKey);
            if (imageUrl != null && !downloader.isCached(imageUrl, version)) {
                imageUrls.add(imageUrl);
            }
        }

        if (!imageUrls.isEmpty()) downloader.preload(imageUrls, version);
        if (!videoUrls.isEmpty()) downloader.preloadVideo(videoUrls, version);
    }

    private void hideMediaArea() {
        helpImageView.setVisibility(View.GONE);
        helpVideoView.setVisibility(View.GONE);
        loadingIndicator.setVisibility(View.GONE);
        // In landscape, collapse the empty media column so the text column expands
        // to full width instead of being squeezed to the right of an empty area.
        if (mediaColumn != null) {
            mediaColumn.setVisibility(View.GONE);
        }
        // With no media, the text fills the full card width — center the buttons
        // so they don't look off to one side.
        buttonRow.setGravity(Gravity.CENTER);
    }

    /**
     * In landscape, make the left-side media column visible. No-op in portrait
     * (where media views are added directly to the content column).
     * Also restores right-aligned buttons since they share the narrow text column.
     */
    private void showMediaColumn() {
        if (mediaColumn != null) {
            mediaColumn.setVisibility(View.VISIBLE);
        }
        // With media present, buttons share the narrow right column — keep them right-aligned.
        buttonRow.setGravity(Gravity.END);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        backgroundVideoDownloadUrl = null;
        // Release ExoPlayer to prevent memory leaks
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }
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
