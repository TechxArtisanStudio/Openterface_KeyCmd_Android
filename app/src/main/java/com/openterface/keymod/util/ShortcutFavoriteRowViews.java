package com.openterface.keymod.util;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutUiStrings;
import com.openterface.keymod.prefs.ShortcutHubDetailUiPrefs;

/**
 * Shared row binding for "My shortcuts" list rows (strip picker, reorder sheet, Hub list).
 */
public final class ShortcutFavoriteRowViews {

    private ShortcutFavoriteRowViews() {
    }

    public static int resolveShortcutIconRes(@NonNull Context ctx, String iconName) {
        if (iconName == null) {
            return 0;
        }
        String raw = iconName.trim();
        if (raw.isEmpty() || isEmojiIcon(raw)) {
            return 0;
        }
        return ctx.getResources().getIdentifier(raw, "drawable", ctx.getPackageName());
    }

    public static boolean isEmojiIcon(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        return !raw.matches("^[A-Za-z0-9_]+$");
    }

    private static int listRowIconTint(@NonNull Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFF212121);
    }

    /**
     * Binds {@link R.layout#dialog_row_top_strip_favorite} (or any layout with the same ids).
     */
    public static void bindFavoriteStripRow(
            @NonNull Context ctx,
            @NonNull View row,
            @NonNull ShortcutProfileManager.Shortcut shortcut,
            @NonNull String targetOs
    ) {
        ImageView iconDrawable = row.findViewById(R.id.favorite_row_icon_drawable);
        TextView iconEmoji = row.findViewById(R.id.favorite_row_icon_emoji);
        TextView nameTv = row.findViewById(R.id.favorite_row_name);
        TextView chordTv = row.findViewById(R.id.favorite_row_chord);
        nameTv.setVisibility(View.VISIBLE);

        String chord = "";
        if (shortcut.label != null && !shortcut.label.trim().isEmpty()) {
            chord = KeyParser.displayLabel(shortcut.label.trim(), targetOs);
        }
        chordTv.setText(chord);

        nameTv.setText(ShortcutUiStrings.shortcutDisplayName(ctx, shortcut));

        int iconRes = resolveShortcutIconRes(ctx, shortcut.icon);
        if (iconRes != 0) {
            iconDrawable.setImageResource(iconRes);
            iconDrawable.setColorFilter(listRowIconTint(ctx), PorterDuff.Mode.SRC_IN);
            iconDrawable.setVisibility(View.VISIBLE);
            iconEmoji.setVisibility(View.GONE);
            iconEmoji.setText("");
        } else {
            iconDrawable.setImageDrawable(null);
            iconDrawable.clearColorFilter();
            iconDrawable.setVisibility(View.GONE);
            String raw = shortcut.icon != null ? shortcut.icon.trim() : "";
            if (isEmojiIcon(raw)) {
                iconEmoji.setText(raw);
                iconEmoji.setVisibility(View.VISIBLE);
            } else {
                iconEmoji.setVisibility(View.GONE);
            }
        }
    }

    private static boolean hasUsableIcon(@NonNull Context ctx, @NonNull ShortcutProfileManager.Shortcut shortcut) {
        int iconRes = resolveShortcutIconRes(ctx, shortcut.icon);
        if (iconRes != 0) {
            return true;
        }
        String raw = shortcut.icon != null ? shortcut.icon.trim() : "";
        return isEmojiIcon(raw);
    }

    private static void applyIconViews(
            @NonNull Context ctx,
            @NonNull ShortcutProfileManager.Shortcut shortcut,
            ImageView iconDrawable,
            TextView iconEmoji
    ) {
        int iconRes = resolveShortcutIconRes(ctx, shortcut.icon);
        if (iconRes != 0) {
            iconDrawable.setImageResource(iconRes);
            iconDrawable.setColorFilter(listRowIconTint(ctx), PorterDuff.Mode.SRC_IN);
            iconDrawable.setVisibility(View.VISIBLE);
            iconEmoji.setVisibility(View.GONE);
            iconEmoji.setText("");
        } else {
            iconDrawable.setImageDrawable(null);
            iconDrawable.clearColorFilter();
            iconDrawable.setVisibility(View.GONE);
            String raw = shortcut.icon != null ? shortcut.icon.trim() : "";
            if (isEmojiIcon(raw)) {
                iconEmoji.setText(raw);
                iconEmoji.setVisibility(View.VISIBLE);
            } else {
                iconEmoji.setVisibility(View.GONE);
            }
        }
    }

    private static void setSp(TextView tv, float sp) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /**
     * Shortcut Hub profile detail only: row presentation by layout style and display mode.
     * Reuses {@code favorite_row_*} ids from compact strip row or {@link R.layout#dialog_row_shortcut_hub_card_cell}.
     */
    public static void bindShortcutHubDetailRow(
            @NonNull Context ctx,
            @NonNull View row,
            @NonNull ShortcutProfileManager.Shortcut shortcut,
            @NonNull String targetOs,
            int displayMode,
            boolean isCardLayout
    ) {
        ImageView iconDrawable = row.findViewById(R.id.favorite_row_icon_drawable);
        TextView iconEmoji = row.findViewById(R.id.favorite_row_icon_emoji);
        TextView nameTv = row.findViewById(R.id.favorite_row_name);
        TextView chordTv = row.findViewById(R.id.favorite_row_chord);
        if (iconDrawable == null || iconEmoji == null || nameTv == null || chordTv == null) {
            return;
        }

        String chord = "";
        if (shortcut.label != null && !shortcut.label.trim().isEmpty()) {
            chord = KeyParser.displayLabel(shortcut.label.trim(), targetOs);
        }
        String name = ShortcutUiStrings.shortcutDisplayName(ctx, shortcut);
        applyIconViews(ctx, shortcut, iconDrawable, iconEmoji);
        boolean hasIcon = hasUsableIcon(ctx, shortcut);

        int mode = ShortcutHubDetailUiPrefs.clampDisplay(displayMode);
        if (mode == ShortcutHubDetailUiPrefs.DISPLAY_HYBRID) {
            mode = hasIcon ? ShortcutHubDetailUiPrefs.DISPLAY_ICON : ShortcutHubDetailUiPrefs.DISPLAY_CHORD;
        } else if (mode == ShortcutHubDetailUiPrefs.DISPLAY_ICON && !hasIcon) {
            mode = ShortcutHubDetailUiPrefs.DISPLAY_CHORD;
        }

        int primary = ContextCompat.getColor(ctx, R.color.text_primary);
        int secondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        nameTv.setVisibility(View.VISIBLE);
        chordTv.setVisibility(View.VISIBLE);

        if (isCardLayout) {
            if (mode == ShortcutHubDetailUiPrefs.DISPLAY_NAME) {
                View iconWrap = (View) iconDrawable.getParent();
                iconWrap.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
                chordTv.setVisibility(View.VISIBLE);
                setSp(nameTv, 18f);
                nameTv.setTextColor(primary);
                nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.BOLD);
                nameTv.setText(name);
                setSp(chordTv, 13f);
                chordTv.setTextColor(secondary);
                chordTv.setTypeface(chordTv.getTypeface(), android.graphics.Typeface.NORMAL);
                chordTv.setText(chord);
            } else if (mode == ShortcutHubDetailUiPrefs.DISPLAY_ICON) {
                View iconWrap = (View) iconDrawable.getParent();
                iconWrap.setVisibility(View.VISIBLE);
                setSp(iconEmoji, 32f);
                setSp(nameTv, 12f);
                nameTv.setTextColor(secondary);
                nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.NORMAL);
                nameTv.setText(name);
                chordTv.setVisibility(View.GONE);
                chordTv.setText("");
            } else {
                View iconWrap = (View) iconDrawable.getParent();
                iconWrap.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
                chordTv.setVisibility(View.VISIBLE);
                setSp(chordTv, 17f);
                chordTv.setTextColor(primary);
                chordTv.setTypeface(chordTv.getTypeface(), android.graphics.Typeface.BOLD);
                chordTv.setText(chord);
                setSp(nameTv, 12f);
                nameTv.setTextColor(secondary);
                nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.NORMAL);
                nameTv.setText(name);
            }
            return;
        }

        View iconWrap = (View) iconDrawable.getParent();
        iconWrap.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
        setSp(iconEmoji, 18f);

        if (mode == ShortcutHubDetailUiPrefs.DISPLAY_NAME) {
            chordTv.setVisibility(View.VISIBLE);
            nameTv.setMaxLines(2);
            setSp(nameTv, 15f);
            nameTv.setTextColor(primary);
            nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.BOLD);
            nameTv.setText(name);
            setSp(chordTv, 12f);
            chordTv.setTextColor(secondary);
            chordTv.setTypeface(chordTv.getTypeface(), android.graphics.Typeface.NORMAL);
            chordTv.setText(chord);
        } else if (mode == ShortcutHubDetailUiPrefs.DISPLAY_ICON) {
            setSp(nameTv, 12f);
            nameTv.setTextColor(secondary);
            nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.NORMAL);
            nameTv.setMaxLines(2);
            nameTv.setText(name);
            chordTv.setVisibility(View.GONE);
            chordTv.setText("");
        } else {
            chordTv.setVisibility(View.VISIBLE);
            nameTv.setMaxLines(2);
            setSp(nameTv, 11f);
            nameTv.setTextColor(secondary);
            nameTv.setTypeface(nameTv.getTypeface(), android.graphics.Typeface.NORMAL);
            nameTv.setText(name);
            setSp(chordTv, 14f);
            chordTv.setTextColor(primary);
            chordTv.setTypeface(chordTv.getTypeface(), android.graphics.Typeface.BOLD);
            chordTv.setText(chord);
        }
    }
}
