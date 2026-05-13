package com.openterface.keymod.util;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.google.android.material.card.MaterialCardView;
import com.openterface.keymod.R;

/**
 * Full-screen reading help above the reorder sheet: API 31+ requests window background blur (via
 * reflection so stubs stay compatible); all API levels use a strong dim veil + light card so text
 * stays readable when blur is off (common on emulators). Tap anywhere to dismiss.
 */
public final class MyShortcutsReorderHelpReadModeDialog {

    private MyShortcutsReorderHelpReadModeDialog() {
    }

    /**
     * Builds formatted help: bold title line, then HTML paragraphs with bold section labels and actions.
     */
    @NonNull
    public static CharSequence formatHelpText(
            @NonNull Context context,
            @NonNull String profileTitle,
            int favoriteCount
    ) {
        final int htmlMode = HtmlCompat.FROM_HTML_MODE_COMPACT;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String title = context.getString(
                R.string.my_shortcuts_reorder_help_read_mode_title, profileTitle, favoriteCount);
        sb.append(title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.14f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                context.getString(R.string.my_shortcuts_reorder_help_read_mode_block1), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                context.getString(R.string.my_shortcuts_reorder_help_read_mode_block2), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                context.getString(R.string.my_shortcuts_reorder_help_read_mode_block3), htmlMode));
        return sb;
    }

    public static void show(@NonNull AppCompatActivity activity, @NonNull CharSequence body) {
        final Dialog dialog = new Dialog(activity, R.style.Theme_KeyCmd_FullScreenReadingHelp);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_my_shortcuts_reorder_help_read_mode);
        dialog.setCancelable(true);

        TextView text = dialog.findViewById(R.id.reorder_help_read_text);
        if (text != null) {
            text.setText(body);
        }

        View.OnClickListener dismiss = v -> dialog.dismiss();
        View root = dialog.findViewById(R.id.reorder_help_read_root);
        View dim = dialog.findViewById(R.id.reorder_help_read_dim);
        MaterialCardView card = dialog.findViewById(R.id.reorder_help_read_card);
        TextView footer = dialog.findViewById(R.id.reorder_help_read_footer);
        if (root != null) {
            root.setOnClickListener(dismiss);
        }
        if (dim != null) {
            dim.setOnClickListener(dismiss);
        }
        if (card != null) {
            card.setOnClickListener(dismiss);
        }
        if (text != null) {
            text.setOnClickListener(dismiss);
        }
        if (footer != null) {
            footer.setOnClickListener(dismiss);
        }

        final Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                android.view.WindowManager.LayoutParams lp =
                        (android.view.WindowManager.LayoutParams) window.getAttributes();
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                lp.dimAmount = 0.58f;
                window.setAttributes(lp);
            }
        }

        dialog.show();

        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.getDecorView().post(() -> invokeSetBackgroundBlurRadius(window, activity));
        }
    }

    private static void invokeSetBackgroundBlurRadius(@NonNull Window window, @NonNull AppCompatActivity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        int radiusPx = (int) (72f * density);
        try {
            java.lang.reflect.Method m = Window.class.getMethod("setBackgroundBlurRadius", int.class);
            m.invoke(window, radiusPx);
        } catch (ReflectiveOperationException ignored) {
        } catch (Throwable ignored) {
        }
    }
}
