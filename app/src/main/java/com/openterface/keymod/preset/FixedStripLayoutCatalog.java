package com.openterface.keymod.preset;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.util.TopShortcutProfileSlotPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only description of fixed strip rows 2–3 for pages 0–2, aligned with
 * {@link com.openterface.keymod.CustomKeyboardView#buildFixedTopRowsPage0()} and siblings.
 * Used by Shortcut Hub to show what each physical slot does (base + Fn overlay where applicable).
 */
public final class FixedStripLayoutCatalog {

    private FixedStripLayoutCatalog() {
    }

    public static final int VIEW_TYPE_SECTION = 0;
    public static final int VIEW_TYPE_SLOT = 1;

    public static final class Row {
        public final int viewType;
        @NonNull
        public final String primary;
        @Nullable
        public final String secondary;
        /** Strip page index 0–2 when {@link #viewType} is {@link #VIEW_TYPE_SLOT}. */
        public final int pageIndex;
        /** Physical strip row 2 or 3. */
        public final int stripRow;
        /** Column 0–6 within that row. */
        public final int col;
        /** 1 or 2 when this cell is a Shortcut Hub profile slot (page 2, Fn on); otherwise 0. */
        public final int profileSlot1Based;

        Row(int viewType, @NonNull String primary, @Nullable String secondary,
                int pageIndex, int stripRow, int col, int profileSlot1Based) {
            this.viewType = viewType;
            this.primary = primary;
            this.secondary = secondary;
            this.pageIndex = pageIndex;
            this.stripRow = stripRow;
            this.col = col;
            this.profileSlot1Based = profileSlot1Based;
        }

        static Row section(@NonNull String title, @Nullable String subtitle) {
            return new Row(VIEW_TYPE_SECTION, title, subtitle, -1, -1, -1, 0);
        }

        static Row slot(int page, int stripRow, int col, @NonNull String label, @Nullable String fnHint,
                int profileSlot1Based) {
            return new Row(VIEW_TYPE_SLOT, label, fnHint, page, stripRow, col, profileSlot1Based);
        }
    }

    /**
     * Fn overlay legend for fixed-row keys (matches {@code CustomKeyboardView.resolveFixedTopOverlayMapping}
     * labels; latch gating is described in section copy, not per-key).
     */
    @Nullable
    private static String overlayFnHint(int keyCode, boolean requiresShift) {
        switch (keyCode) {
            case 0x41:
                return "8";
            case 0x42:
                return "9";
            case 0x43:
                return "0";
            case 0x44:
                return "+";
            case 0x45:
                return "-";
            case 0x3A:
                return "1";
            case 0x3B:
                return "2";
            case 0x3C:
                return "3";
            case 0x3D:
                return "4";
            case 0x3E:
                return "5";
            case 0x3F:
                return "6";
            case 0x40:
                return "7";
            case 0x2E:
                return "*";
            case 0xE0:
                return "Scr Lk";
            case 0xE2:
                return "Prt Sc";
            case 0xE3:
                return "Caps";
            case 0x2B:
                return "Pause";
            case 0x52:
                return "Home";
            case 0x28:
                return "PgUp";
            case 0x29:
                return "Space";
            case 0xE1:
                return "Bksp";
            case 0x4C:
                return "Del";
            case 0x50:
                return "Ins";
            case 0x51:
                return "End";
            case 0x4F:
                return "PgDn";
            case 0x2F:
                return "~";
            case 0x30:
                return "'";
            case 0x33:
                return requiresShift ? "\"" : ":";
            case 0x20:
                return "%";
            case 0x1F:
                return "^";
            case 0x38:
                return requiresShift ? "&" : "<";
            case 0x31:
                return ">";
            case 0x64:
                return "*";
            case 0x2D:
                return requiresShift ? "." : ",";
            case 0x35:
                return "[";
            case 0x34:
                return requiresShift ? ":" : "]";
            case 0x22:
                return "#";
            case 0x23:
                return "@";
            case 0x36:
                return requiresShift ? "/" : "-";
            case 0x37:
                return requiresShift ? "\\" : "_";
            case 0x25:
                return "|";
            case 0x24:
                return "?";
            case 0xF00A:
            case 0xF00C:
                return null;
            default:
                return null;
        }
    }

    private static void addPage(
            List<Row> out,
            int pageIndex,
            @NonNull String pageTitle,
            @Nullable String pageSubtitle,
            @NonNull String[][] row2,
            @NonNull String[][] row3,
            @NonNull int[] row2Codes,
            @NonNull boolean[] row2Shift,
            @NonNull int[] row3Codes,
            @NonNull boolean[] row3Shift,
            @NonNull int[] row2ProfileSlot,
            @NonNull int[] row3ProfileSlot
    ) {
        out.add(Row.section(pageTitle, pageSubtitle));
        out.add(Row.section("Row 2", null));
        for (int c = 0; c < KeyboardStripPresetConstants.TOP_PANEL_COLUMNS; c++) {
            String hint = overlayFnHint(row2Codes[c], row2Shift[c]);
            String sub = hint != null ? "Fn: " + hint : null;
            out.add(Row.slot(pageIndex, 2, c, row2[c][0], sub, row2ProfileSlot[c]));
        }
        out.add(Row.section("Row 3", null));
        for (int c = 0; c < KeyboardStripPresetConstants.TOP_PANEL_COLUMNS; c++) {
            String hint = overlayFnHint(row3Codes[c], row3Shift[c]);
            String sub = hint != null ? "Fn: " + hint : null;
            out.add(Row.slot(pageIndex, 3, c, row3[c][0], sub, row3ProfileSlot[c]));
        }
    }

    /**
     * Builds catalog rows (sections + slots). Hub profile slot cells resolve names using {@code pm} when
     * {@link Row#profileSlot1Based} is 1 or 2.
     */
    @NonNull
    public static List<Row> build(@NonNull Context context, @NonNull ShortcutProfileManager pm) {
        List<Row> out = new ArrayList<>();
        int[] noSlot = new int[]{0, 0, 0, 0, 0, 0, 0};

        String p0sub = "F-keys show digit overlays when local Fn is off (strip page 0).";
        String[][] p0r2 = {{"F7"}, {"F8"}, {"F9"}, {"F10"}, {"F11"}, {"F12"}, {"="}};
        int[] p0r2c = {0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x2E};
        boolean[] p0r2s = {false, false, false, false, false, false, false};
        String[][] p0r3 = {{"F1"}, {"F2"}, {"F3"}, {"F4"}, {"F5"}, {"F6"}, {"FN"}};
        int[] p0r3c = {0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0xF00C};
        boolean[] p0r3s = {false, false, false, false, false, false, false};
        addPage(out, 0, "Page 0 — F-keys", p0sub, p0r2, p0r3, p0r2c, p0r2s, p0r3c, p0r3s, noSlot, noSlot);

        String p1sub = "Modifiers and navigation; second row shows overlays when local Fn is latched on.";
        String[][] p1r2 = {{"Ctrl"}, {"Alt"}, {"Win/Cmd"}, {"Tab"}, {"Up"}, {"Enter"}, {"PH1"}};
        int[] p1r2c = {0xE0, 0xE2, 0xE3, 0x2B, 0x52, 0x28, 0xF00A};
        boolean[] p1r2s = {false, false, false, false, false, false, false};
        String[][] p1r3 = {{"Esc"}, {"Shift"}, {"Del"}, {"Left"}, {"Down"}, {"Right"}, {"FN"}};
        int[] p1r3c = {0x29, 0xE1, 0x4C, 0x50, 0x51, 0x4F, 0xF00C};
        boolean[] p1r3s = {false, false, false, false, false, false, false};
        addPage(out, 1, "Page 1 — Modifiers & nav", p1sub, p1r2, p1r3, p1r2c, p1r2s, p1r3c, p1r3s, noSlot, noSlot);

        out.add(Row.section("Page 2 — Hub & symbols",
                "Row content swaps when local Fn is on (latch). Below: Fn off, then Fn on."));
        addPage2Variant(context, out, false, pm);
        addPage2Variant(context, out, true, pm);

        return out;
    }

    private static void addPage2Variant(
            @NonNull Context context,
            List<Row> out,
            boolean fnOn,
            @NonNull ShortcutProfileManager pm
    ) {
        out.add(Row.section(fnOn ? "Page 2 — local Fn on" : "Page 2 — local Fn off", null));
        out.add(Row.section("Row 2", null));
        if (fnOn) {
            String s1 = resolveHubSlotTitle(context, pm, 1);
            String s2 = resolveHubSlotTitle(context, pm, 2);
            addSlot(out, 2, 2, 0, s1, null, 1);
            addSlot(out, 2, 2, 1, s2, null, 2);
            addSlot(out, 2, 2, 2, "~", overlayFnHint(0x35, true), 0);
            addSlot(out, 2, 2, 3, "'", overlayFnHint(0x34, false), 0);
            addSlot(out, 2, 2, 4, "\"", overlayFnHint(0x34, true), 0);
            addSlot(out, 2, 2, 5, "%", overlayFnHint(0x22, true), 0);
            addSlot(out, 2, 2, 6, "^", overlayFnHint(0x23, true), 0);
            out.add(Row.section("Row 3", null));
            addSlot(out, 2, 3, 0, "<", overlayFnHint(0x36, true), 0);
            addSlot(out, 2, 3, 1, ">", overlayFnHint(0x37, true), 0);
            addSlot(out, 2, 3, 2, "*", overlayFnHint(0x25, true), 0);
            addSlot(out, 2, 3, 3, "&", overlayFnHint(0x24, true), 0);
            addSlot(out, 2, 3, 4, ",", overlayFnHint(0x36, false), 0);
            addSlot(out, 2, 3, 5, ".", overlayFnHint(0x37, false), 0);
            addSlot(out, 2, 3, 6, "FN", null, 0);
        } else {
            addSlot(out, 2, 2, 0, "(", null, 0);
            addSlot(out, 2, 2, 1, ")", null, 0);
            addSlot(out, 2, 2, 2, "[", overlayFnHint(0x2F, false), 0);
            addSlot(out, 2, 2, 3, "]", overlayFnHint(0x30, false), 0);
            addSlot(out, 2, 2, 4, ":", overlayFnHint(0x33, true), 0);
            addSlot(out, 2, 2, 5, "#", overlayFnHint(0x20, true), 0);
            addSlot(out, 2, 2, 6, "@", overlayFnHint(0x1F, true), 0);
            out.add(Row.section("Row 3", null));
            addSlot(out, 2, 3, 0, "/", overlayFnHint(0x38, false), 0);
            addSlot(out, 2, 3, 1, "\\", overlayFnHint(0x31, false), 0);
            addSlot(out, 2, 3, 2, "|", overlayFnHint(0x64, false), 0);
            addSlot(out, 2, 3, 3, "?", overlayFnHint(0x38, true), 0);
            addSlot(out, 2, 3, 4, "-", overlayFnHint(0x2D, false), 0);
            addSlot(out, 2, 3, 5, "_", overlayFnHint(0x2D, true), 0);
            addSlot(out, 2, 3, 6, "FN", null, 0);
        }
    }

    private static void addSlot(List<Row> out, int page, int stripRow, int col,
            @NonNull String label, @Nullable String fnHint, int profileSlot) {
        out.add(Row.slot(page, stripRow, col, label, fnHint, profileSlot));
    }

    @NonNull
    private static String resolveHubSlotTitle(@NonNull Context context,
            @NonNull ShortcutProfileManager pm,
            int slot1Based) {
        String id = TopShortcutProfileSlotPrefs.getResolvedProfileIdForSlot(context, slot1Based, pm);
        ShortcutProfileManager.ShortcutProfile p = pm.getProfileById(id);
        if (p != null && p.name != null && !p.name.trim().isEmpty()) {
            String n = p.name.trim();
            return n.length() > 12 ? n.substring(0, 12) + "\u2026" : n;
        }
        return "Hub " + slot1Based;
    }
}
