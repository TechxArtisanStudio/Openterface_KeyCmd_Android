package com.openterface.keymod.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.R;

/** Shows an ASCII send-preview dialog for compose-mode warnings. */
public final class ComposeSendPreviewDialog {

    private static final int PREVIEW_MAX_CHARS = 1200;
    /** Max fraction of screen height for the scrollable preview body so the dialog stays scrollable. */
    private static final float PREVIEW_BODY_MAX_HEIGHT_FRACTION = 0.56f;
    /** Approximate horizontal margin (dialog + padding) when width not yet laid out. */
    private static final int PREVIEW_WIDTH_MARGIN_DP = 56;

    private ComposeSendPreviewDialog() {}

    public static void show(@NonNull Context context, @NonNull String originalText) {
        PreviewResult result = buildAsciiPreview(originalText);
        String previewText = result.sendableText;
        boolean truncated = previewText.length() > PREVIEW_MAX_CHARS;
        if (truncated) {
            previewText = previewText.substring(0, PREVIEW_MAX_CHARS) + "\n…";
        }
        String previewBody =
                previewText.isEmpty()
                        ? context.getString(R.string.compose_send_preview_empty_payload)
                        : previewText;
        String summaryText =
                context.getString(
                        R.string.compose_send_preview_summary,
                        result.originalLength,
                        result.sendableLength,
                        result.droppedNonAsciiCount);

        View content =
                LayoutInflater.from(context).inflate(R.layout.dialog_compose_send_preview, null, false);
        TextView summaryView = content.findViewById(R.id.compose_send_preview_summary_text);
        TextView payloadView = content.findViewById(R.id.compose_send_preview_payload_text);
        summaryView.setText(summaryText);
        payloadView.setText(previewBody);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.compose_send_preview_title)
                        .setView(content)
                        .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> capPreviewScrollHeight(context, content));
        dialog.show();
    }

    /** Preview for the Unicode host-entry HID plan (transcript + step estimate). */
    public static void showUnicodeHostPlan(
            @NonNull Context context, @NonNull String bufferText, @Nullable String targetOs) {
        String os = targetOs != null ? targetOs : "macos";
        int steps = HidTextKeystrokeSender.countSendUnits(bufferText, true, os);
        String transcript =
                ComposeSendPlanDescription.buildUnicodeTranscript(
                        bufferText, os, ComposeSendPlanDescription.UNICODE_TRANSCRIPT_MAX_CHARS);
        String stepsLine =
                context.getString(R.string.compose_send_warning_tab_hid_steps_unicode, steps);
        String summaryBody =
                context.getString(
                        R.string.compose_send_preview_unicode_summary_body,
                        bufferText.length(),
                        stepsLine);

        View content =
                LayoutInflater.from(context).inflate(R.layout.dialog_compose_send_preview, null, false);
        TextView summaryTitle = content.findViewById(R.id.compose_send_preview_summary_title);
        TextView summaryView = content.findViewById(R.id.compose_send_preview_summary_text);
        TextView payloadTitle = content.findViewById(R.id.compose_send_preview_payload_title);
        TextView payloadView = content.findViewById(R.id.compose_send_preview_payload_text);
        summaryTitle.setText(R.string.compose_send_preview_unicode_summary_title);
        summaryView.setText(summaryBody);
        payloadTitle.setText(R.string.compose_send_warning_tab_unicode_plan_title);
        payloadView.setText(transcript.isEmpty() ? context.getString(R.string.compose_send_preview_empty_payload) : transcript);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.compose_send_preview_unicode_title)
                        .setView(content)
                        .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> capPreviewScrollHeight(context, content));
        dialog.show();
    }

    /**
     * Caps {@link R.id#compose_send_preview_scroll} height so long bodies scroll instead of clipping
     * under the dialog action bar.
     */
    private static void capPreviewScrollHeight(@NonNull Context context, @NonNull View content) {
        NestedScrollView scroll = content.findViewById(R.id.compose_send_preview_scroll);
        if (scroll == null) {
            return;
        }
        scroll.post(
                () -> {
                    View inner = scroll.getChildAt(0);
                    if (inner == null) {
                        return;
                    }
                    int maxPx =
                            (int)
                                    (context.getResources().getDisplayMetrics().heightPixels
                                            * PREVIEW_BODY_MAX_HEIGHT_FRACTION);
                    int width = scroll.getWidth();
                    if (width <= 0) {
                        float density = context.getResources().getDisplayMetrics().density;
                        width =
                                context.getResources().getDisplayMetrics().widthPixels
                                        - (int) (PREVIEW_WIDTH_MARGIN_DP * density + 0.5f);
                    }
                    int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
                    int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    inner.measure(widthSpec, heightSpec);
                    int innerHeight = inner.getMeasuredHeight();
                    int verticalPad = scroll.getPaddingTop() + scroll.getPaddingBottom();
                    int want = innerHeight + verticalPad;
                    ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                    lp.height = Math.min(want, maxPx);
                    scroll.setLayoutParams(lp);
                });
    }

    @NonNull
    private static PreviewResult buildAsciiPreview(@NonNull String original) {
        StringBuilder ascii = new StringBuilder(original.length());
        int dropped = 0;
        for (int i = 0; i < original.length(); ) {
            int cp = original.codePointAt(i);
            if (cp > 0x7E) {
                dropped++;
            } else {
                ascii.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return new PreviewResult(original.length(), ascii.length(), dropped, ascii.toString());
    }

    private static final class PreviewResult {
        final int originalLength;
        final int sendableLength;
        final int droppedNonAsciiCount;
        @NonNull final String sendableText;

        PreviewResult(
                int originalLength,
                int sendableLength,
                int droppedNonAsciiCount,
                @NonNull String sendableText) {
            this.originalLength = originalLength;
            this.sendableLength = sendableLength;
            this.droppedNonAsciiCount = droppedNonAsciiCount;
            this.sendableText = sendableText;
        }
    }
}
