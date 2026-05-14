package com.openterface.keymod.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openterface.keymod.R;

/** Shared compose-send warning dialog used by KM Basic and KM Pro IME compose flows. */
public final class ComposeSendWarningDialog {

    // TODO: When compose_send_warning_unicode_tutorial_url is non-empty, add a "Watch tutorial"
    // action or link that opens the URL (e.g. YouTube).

    private ComposeSendWarningDialog() {}

    public static void show(
            @NonNull Context context,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @NonNull Runnable onSendAnyway,
            @NonNull Runnable onCheck,
            @NonNull Runnable onPreview) {
        show(
                context,
                warningInfo,
                null,
                onSendAnyway,
                onCheck,
                onPreview,
                null,
                null,
                null);
    }

    public static void show(
            @NonNull Context context,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @Nullable String bufferText,
            @NonNull Runnable onSendAnyway,
            @NonNull Runnable onCheck,
            @NonNull Runnable onPreview,
            @Nullable String targetOs,
            @Nullable Runnable onSendWithUnicodeHostEntry) {
        show(
                context,
                warningInfo,
                bufferText,
                onSendAnyway,
                onCheck,
                onPreview,
                targetOs,
                onSendWithUnicodeHostEntry,
                null);
    }

    public static void show(
            @NonNull Context context,
            @NonNull ImeComposeSendGate.WarningInfo warningInfo,
            @Nullable String bufferText,
            @NonNull Runnable onSendAnyway,
            @NonNull Runnable onCheck,
            @NonNull Runnable onPreview,
            @Nullable String targetOs,
            @Nullable Runnable onSendWithUnicodeHostEntry,
            @Nullable Runnable onPreviewUnicodeHostEntry) {
        final boolean showUnicodeHostEntry =
                warningInfo.hasNonAscii && onSendWithUnicodeHostEntry != null;
        final boolean useTabbed = showUnicodeHostEntry && bufferText != null;
        final int layoutRes =
                useTabbed ? R.layout.dialog_compose_send_warning_tabbed : R.layout.dialog_compose_send_warning;

        View content = LayoutInflater.from(context).inflate(layoutRes, null, false);
        TextView message = content.findViewById(R.id.compose_send_warning_message);
        View inlineActions = content.findViewById(R.id.compose_send_warning_inline_actions);
        View checkButton = content.findViewById(R.id.compose_send_warning_check_button);
        View previewButton = content.findViewById(R.id.compose_send_warning_preview_button);
        View unicodeRow = content.findViewById(R.id.compose_send_warning_unicode_row);
        MaterialButton unicodeHostEntryButton =
                content.findViewById(R.id.compose_send_warning_unicode_host_entry_button);
        View unicodeInfoButton = content.findViewById(R.id.compose_send_warning_unicode_info_button);
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

        final String targetOsForHint = targetOs;
        if (showUnicodeHostEntry && unicodeHostEntryButton != null) {
            unicodeHostEntryButton.setContentDescription(
                    context.getString(R.string.compose_send_warning_send_unicode_host_entry));
        } else {
            if (unicodeRow != null) {
                unicodeRow.setVisibility(View.GONE);
            }
        }

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context).setView(content);
        if (!useTabbed) {
            builder.setTitle(R.string.compose_send_warning_title);
        }
        AlertDialog dialog = builder.create();

        if (useTabbed) {
            View unicodePreviewButton = content.findViewById(R.id.compose_send_warning_unicode_preview_button);
            if (unicodePreviewButton != null) {
                unicodePreviewButton.setVisibility(
                        onPreviewUnicodeHostEntry != null ? View.VISIBLE : View.GONE);
            }
            wireDualModeUi(content);
            populateUnicodePlan(context, content, bufferText, targetOs);
        }

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
        if (unicodeInfoButton != null) {
            unicodeInfoButton.setOnClickListener(
                    v ->
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.compose_send_warning_unicode_info_title)
                                    .setMessage(unicodeHostSetupHint(context, targetOsForHint))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show());
        }
        if (unicodeHostEntryButton != null) {
            unicodeHostEntryButton.setOnClickListener(
                    v -> {
                        dialog.dismiss();
                        if (onSendWithUnicodeHostEntry != null) {
                            onSendWithUnicodeHostEntry.run();
                        }
                    });
        }
        View unicodePreviewTabbed = content.findViewById(R.id.compose_send_warning_unicode_preview_button);
        if (unicodePreviewTabbed != null && onPreviewUnicodeHostEntry != null) {
            unicodePreviewTabbed.setOnClickListener(
                    v -> {
                        dialog.dismiss();
                        onPreviewUnicodeHostEntry.run();
                    });
        }
        sendAnywayButton.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    onSendAnyway.run();
                });
        dialog.show();
    }

    private static void wireDualModeUi(@NonNull View content) {
        View modeAscii = content.findViewById(R.id.compose_send_warning_mode_ascii);
        View modeUnicode = content.findViewById(R.id.compose_send_warning_mode_unicode);
        View switchToAsciiIcon = content.findViewById(R.id.compose_send_warning_switch_to_ascii_icon);
        View switchUnicodeIcon = content.findViewById(R.id.compose_send_warning_switch_unicode_icon);
        if (modeAscii == null || modeUnicode == null || switchUnicodeIcon == null) {
            return;
        }

        final boolean[] unicodeMode = {false};

        Runnable applyMode =
                () -> {
                    modeAscii.setVisibility(unicodeMode[0] ? View.GONE : View.VISIBLE);
                    modeUnicode.setVisibility(unicodeMode[0] ? View.VISIBLE : View.GONE);
                    if (switchToAsciiIcon != null) {
                        switchToAsciiIcon.setVisibility(unicodeMode[0] ? View.VISIBLE : View.GONE);
                    }
                    switchUnicodeIcon.setVisibility(unicodeMode[0] ? View.GONE : View.VISIBLE);
                    applyUnicodeSubpageChrome(content, unicodeMode[0]);
                };

        switchUnicodeIcon.setOnClickListener(
                v -> {
                    unicodeMode[0] = true;
                    applyMode.run();
                });
        if (switchToAsciiIcon != null) {
            switchToAsciiIcon.setOnClickListener(
                    v -> {
                        unicodeMode[0] = false;
                        applyMode.run();
                    });
        }

        applyMode.run();
    }

    private static void applyUnicodeSubpageChrome(@NonNull View content, boolean unicodeMode) {
        MaterialCardView card = content.findViewById(R.id.compose_send_warning_tabbed_card);
        if (card == null) {
            return;
        }
        float density = card.getResources().getDisplayMetrics().density;
        if (unicodeMode) {
            card.setStrokeWidth((int) (density + 0.5f));
            int primary =
                    MaterialColors.getColor(
                            card, com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT);
            int strokeArgb = (0xCC << 24) | (primary & 0x00FFFFFF);
            card.setStrokeColor(ColorStateList.valueOf(strokeArgb));
        } else {
            card.setStrokeWidth(0);
        }
    }

    private static void populateUnicodePlan(
            @NonNull Context context,
            @NonNull View content,
            @NonNull String bufferText,
            @Nullable String targetOs) {
        String os = targetOs != null ? targetOs : "macos";
        TextView unicodeMessage = content.findViewById(R.id.compose_send_warning_unicode_message);
        if (unicodeMessage == null) {
            return;
        }
        int steps = HidTextKeystrokeSender.countSendUnits(bufferText, true, os);
        String hidLine = context.getString(R.string.compose_send_warning_tab_hid_steps_unicode, steps);
        String body =
                context.getString(R.string.compose_send_warning_unicode_mode_intro)
                        + "\n\n"
                        + context.getString(R.string.compose_send_warning_unicode_preview_hint)
                        + "\n\n"
                        + hidLine;
        unicodeMessage.setText(body);
    }

    @NonNull
    private static CharSequence unicodeHostSetupHint(
            @NonNull Context context, @Nullable String targetOs) {
        if ("macos".equals(targetOs)) {
            return context.getString(R.string.compose_send_warning_unicode_host_hint_macos);
        }
        if ("windows".equals(targetOs)) {
            return context.getString(R.string.compose_send_warning_unicode_host_hint_windows);
        }
        if ("linux".equals(targetOs)) {
            return context.getString(R.string.compose_send_warning_unicode_host_hint_linux);
        }
        return context.getString(R.string.compose_send_warning_unicode_host_hint_other);
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
