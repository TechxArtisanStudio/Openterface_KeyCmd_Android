package com.openterface.keymod.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
            @Nullable Runnable onSendWithUnicodeHostEntry,
            @Nullable Runnable onPreviewUnicodeHostEntry) {
        show(
                context,
                warningInfo,
                bufferText,
                onSendAnyway,
                onCheck,
                onPreview,
                targetOs,
                onSendWithUnicodeHostEntry,
                onPreviewUnicodeHostEntry,
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
            @Nullable Runnable onPreviewUnicodeHostEntry,
            @Nullable Runnable onMacUnicodeHexAudit) {
        final boolean showUnicodeHostEntry =
                warningInfo.hasNonAscii && onSendWithUnicodeHostEntry != null;
        final boolean useTabbed = showUnicodeHostEntry && bufferText != null;
        final int layoutRes =
                useTabbed ? R.layout.dialog_compose_send_warning_tabbed : R.layout.dialog_compose_send_warning;

        View content = LayoutInflater.from(context).inflate(layoutRes, null, false);
        applyComposeSendWarningInsets(context, content);
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
                    v -> {
                        MaterialAlertDialogBuilder info =
                                new MaterialAlertDialogBuilder(context)
                                        .setTitle(R.string.compose_send_warning_unicode_info_title)
                                        .setMessage(unicodeHostSetupHint(context, targetOsForHint))
                                        .setPositiveButton(android.R.string.ok, null);
                        if ("macos".equals(targetOsForHint) && onMacUnicodeHexAudit != null) {
                            info.setNeutralButton(
                                    R.string.unicode_hex_audit_entry,
                                    (dialog, which) -> {
                                        dialog.dismiss();
                                        onMacUnicodeHexAudit.run();
                                    });
                        }
                        info.show();
                    });
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
        Window window = dialog.getWindow();
        if (window != null) {
            // Let our card / root define the panel color (Material default surface reads brown in dark).
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(
                d -> {
                    clearMaterialCustomPanelHorizontalPull((AlertDialog) d);
                    applyComposeSendWarningInsets(context, content);
                    content.post(() -> applyComposeSendWarningInsets(context, content));
                });
        dialog.show();
    }

    /**
     * Material alert can reset custom-view margins; re-apply our gutters and card padding so
     * horizontal spacing matches {@code dialog_compose_send_warning_tabbed.xml} / {@code dialog_compose_send_warning.xml}.
     */
    private static void applyComposeSendWarningInsets(@NonNull Context context, @NonNull View content) {
        Resources res = context.getResources();
        View tabbedRoot = content.findViewById(R.id.compose_send_warning_tabbed_root);
        if (tabbedRoot != null) {
            int gutterH = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_h);
            int gutterTop = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_top);
            int gutterBottom = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_bottom);
            tabbedRoot.setPaddingRelative(gutterH, gutterTop, gutterH, gutterBottom);

            MaterialCardView card = content.findViewById(R.id.compose_send_warning_tabbed_card);
            if (card != null) {
                int ch = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_card_padding_h);
                int ctop = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_card_padding_top);
                int cbottom = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_card_padding_bottom);
                card.setContentPadding(ch, ctop, ch, cbottom);
                if (card.getChildCount() > 0) {
                    View column = card.getChildAt(0);
                    int inner = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_inner_padding_h);
                    column.setPaddingRelative(
                            inner, column.getPaddingTop(), inner, column.getPaddingBottom());
                }
            }
            return;
        }
        View simpleRoot = content.findViewById(R.id.compose_send_warning_simple_root);
        if (simpleRoot != null) {
            int gutterH = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_h);
            int gutterTop = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_top);
            int gutterBottom = res.getDimensionPixelSize(R.dimen.compose_send_warning_tabbed_dialog_gutter_bottom);
            simpleRoot.setPaddingRelative(gutterH, gutterTop, gutterH, gutterBottom);
            View body = content.findViewById(R.id.compose_send_warning_simple_body);
            if (body != null) {
                int bh = res.getDimensionPixelSize(R.dimen.compose_send_warning_dialog_body_padding_h);
                int bt = res.getDimensionPixelSize(R.dimen.compose_send_warning_dialog_body_padding_top);
                int bb = res.getDimensionPixelSize(R.dimen.compose_send_warning_dialog_body_padding_bottom);
                body.setPaddingRelative(bh, bt, bh, bb);
            }
        }
    }

    /** Material M3 alert may apply horizontal pull on the custom panel; zero it so our root gutters hold. */
    private static void clearMaterialCustomPanelHorizontalPull(@NonNull AlertDialog dialog) {
        View custom = dialog.findViewById(com.google.android.material.R.id.custom);
        if (custom == null) {
            custom = dialog.findViewById(android.R.id.custom);
        }
        if (custom != null) {
            ViewGroup.LayoutParams lp = custom.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                m.leftMargin = 0;
                m.rightMargin = 0;
                custom.setLayoutParams(m);
            }
        }
        View panel = dialog.findViewById(com.google.android.material.R.id.customPanel);
        if (panel != null) {
            ViewGroup.LayoutParams lp2 = panel.getLayoutParams();
            if (lp2 instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m2 = (ViewGroup.MarginLayoutParams) lp2;
                m2.leftMargin = 0;
                m2.rightMargin = 0;
                panel.setLayoutParams(m2);
            }
        }
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
            int strokePx = Math.max(2, (int) (2f * density + 0.5f));
            card.setStrokeWidth(strokePx);
            int primary =
                    MaterialColors.getColor(
                            card, com.google.android.material.R.attr.colorPrimary, Color.TRANSPARENT);
            card.setStrokeColor(ColorStateList.valueOf(primary));
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
