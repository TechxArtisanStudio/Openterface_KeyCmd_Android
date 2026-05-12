package com.openterface.keymod.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.R;

/** Shows an ASCII send-preview dialog for compose-mode warnings. */
public final class ComposeSendPreviewDialog {

    private static final int PREVIEW_MAX_CHARS = 1200;

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

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.compose_send_preview_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
