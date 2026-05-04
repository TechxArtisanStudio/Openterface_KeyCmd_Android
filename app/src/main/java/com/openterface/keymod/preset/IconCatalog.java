package com.openterface.keymod.preset;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated icons for Rows 2–3 strip slot editor (emoji or drawable resource name, matching
 * {@link com.openterface.keymod.util.ShortcutFavoriteRowViews#resolveShortcutIconRes}).
 */
public final class IconCatalog {

    public enum Kind {
        EMOJI,
        VECTOR_DRAWABLE
    }

    public static final class Entry {
        @NonNull
        public final Kind kind;
        /** Emoji character or drawable resource name (no extension). */
        @NonNull
        public final String value;

        public Entry(@NonNull Kind kind, @NonNull String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    private IconCatalog() {
    }

    @NonNull
    public static List<Entry> emojiEntries() {
        String[] glyphs = {
                "\u2318", "\u2325", "\u2303", "\u21E7",
                "\u25B6", "\u23F8", "\u23ED", "\u23EE",
                "\uD83D\uDD07", "\uD83D\uDD0A", "\uD83D\uDD09", "\uD83D\uDD08",
                "\u2702", "\uD83D\uDCCB", "\uD83D\uDCDD", "\u270F",
                "\u21A9", "\u232B", "\u238B", "\u21B9",
                "\u2302", "\uD83D\uDD0D", "\u2699", "\u2713",
                "\u274C", "\u2795", "\u2796", "\u2728"
        };
        List<Entry> out = new ArrayList<>(glyphs.length);
        for (String g : glyphs) {
            out.add(new Entry(Kind.EMOJI, g));
        }
        return out;
    }

    @NonNull
    public static List<Entry> vectorEntries() {
        String[] drawables = {
                "ic_top_copy", "ic_top_cut", "ic_top_paste", "ic_top_undo", "ic_top_save",
                "ic_top_select_all", "ic_media_play_arrow", "ic_media_skip_next", "ic_media_skip_previous",
                "ic_media_volume_up", "ic_media_volume_down", "ic_media_volume_off",
                "ic_mac_spotlight", "ic_mac_show_desktop", "ic_mac_show_apps", "ic_reset", "ic_edit",
                "ic_settings", "ic_list", "ic_history", "ic_voice", "ic_bookmark_star_24",
                "ic_bookmark_add_24", "ic_check", "close_24"
        };
        List<Entry> out = new ArrayList<>(drawables.length);
        for (String d : drawables) {
            out.add(new Entry(Kind.VECTOR_DRAWABLE, d));
        }
        return out;
    }
}
