package com.openterface.keymod.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.R;

/** Shared compose-send warning dialog used by KM Basic and KM Pro IME compose flows. */
public final class ComposeSendWarningDialog {

    private ComposeSendWarningDialog() {}

    public static void show(
            @NonNull Context context,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @NonNull Runnable onSendAnyway,
            @NonNull Runnable onCheck,
            @NonNull Runnable onPreview) {
        View content =
                LayoutInflater.from(context).inflate(R.layout.dialog_compose_send_warning, null, false);
        TextView message = content.findViewById(R.id.compose_send_warning_message);
        View inlineActions = content.findViewById(R.id.compose_send_warning_inline_actions);
        View checkButton = content.findViewById(R.id.compose_send_warning_check_button);
        View previewButton = content.findViewById(R.id.compose_send_warning_preview_button);
        View sendAnywayButton = content.findViewById(R.id.compose_send_warning_send_anyway_button);
        message.setText(buildMessage(context, warningInfo));
        if (warningInfo.hasNonAscii) {
            inlineActions.setVisibility(View.VISIBLE);
            checkButton.setVisibility(View.VISIBLE);
            previewButton.setVisibility(View.VISIBLE);
        } else {
            inlineActions.setVisibility(View.GONE);
            checkButton.setVisibility(View.GONE);
            previewButton.setVisibility(View.GONE);
        }

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.compose_send_warning_title)
                        .setView(content)
                        .create();

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        checkButton.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    onCheck.run();
                });
        previewButton.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    onPreview.run();
                });
        sendAnywayButton.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    onSendAnyway.run();
                });
        dialog.show();
    }

    @NonNull
    private static CharSequence buildMessage(
            @NonNull Context context,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendLabeledLine(
                sb,
                context.getString(R.string.compose_send_warning_count_label),
                context.getString(R.string.compose_send_warning_count, warningInfo.charCount));
        if (warningInfo.hasNonAscii || warningInfo.hasLengthRisk) {
            sb.append("\n\n");
            int start = sb.length();
            sb.append(context.getString(R.string.compose_send_warning_issues_header));
            sb.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    start,
                    sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (warningInfo.hasNonAscii) {
            appendIssueLine(
                    sb,
                    context.getString(R.string.compose_send_warning_non_ascii_label),
                    context.getString(R.string.compose_send_warning_non_ascii));
        }
        if (warningInfo.hasLengthRisk) {
            appendIssueLine(
                    sb,
                    context.getString(R.string.compose_send_warning_length_label),
                    context.getString(R.string.compose_send_warning_length));
        }
        return sb;
    }

    private static void appendLabeledLine(
            @NonNull SpannableStringBuilder sb,
            @NonNull String label,
            @NonNull String body) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        int start = sb.length();
        sb.append(label);
        sb.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(": ");
        sb.append(body);
    }

    private static void appendIssueLine(
            @NonNull SpannableStringBuilder sb,
            @NonNull String label,
            @NonNull String body) {
        sb.append('\n').append("- ");
        int start = sb.length();
        sb.append(label);
        sb.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(": ");
        sb.append(body);
    }
}
