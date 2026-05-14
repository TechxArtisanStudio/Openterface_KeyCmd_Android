package com.openterface.keymod.preset;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.util.KeyParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Page 0–1 and generic sections; shown in every Fn tab filter. */
    public static final int FN_LATCH_NONE = 0;
    /** Page 2 catalog rows when local Fn latch is off. */
    public static final int FN_LATCH_OFF = 1;
    /** Page 2 catalog rows when local Fn latch is on. */
    public static final int FN_LATCH_ON = 2;

    /**
     * Page 2 fixed strip row 2: base caps (local Fn off) / latched caps (local Fn on).
     * Keep in sync with {@link com.openterface.keymod.CustomKeyboardView#buildFixedTopRowsPage2()}.
     */
    public static final String[] PAGE2_ROW2_BASE = {"(", ")", "[", "]", ":", "#", "@"};
    /** Row 2 latched punctuation caps (local Fn on). */
    public static final String[] PAGE2_ROW2_FN = {"`", "~", "'", "\"", "%", "^", "|"};
    /** Row 3 base caps (local Fn off); index 6 is the Fn toggle label in the grid only. */
    public static final String[] PAGE2_ROW3_BASE = {"/", "\\", "|", "?", "-", "_", "FN"};
    /** Row 3 latched caps (local Fn on). */
    public static final String[] PAGE2_ROW3_FN = {"<", ">", "*", "&", ",", ".", "FN"};

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
        /**
         * Distinguishes page 2 Fn-off vs Fn-on blocks for Hub filtering; {@link #FN_LATCH_NONE} elsewhere.
         */
        public final int fnLatchGroup;

        Row(int viewType, @NonNull String primary, @Nullable String secondary,
                int pageIndex, int stripRow, int col, int profileSlot1Based, int fnLatchGroup) {
            this.viewType = viewType;
            this.primary = primary;
            this.secondary = secondary;
            this.pageIndex = pageIndex;
            this.stripRow = stripRow;
            this.col = col;
            this.profileSlot1Based = profileSlot1Based;
            this.fnLatchGroup = fnLatchGroup;
        }

        static Row section(@NonNull String title, @Nullable String subtitle) {
            return section(title, subtitle, FN_LATCH_NONE);
        }

        static Row section(@NonNull String title, @Nullable String subtitle, int fnLatchGroup) {
            return new Row(VIEW_TYPE_SECTION, title, subtitle, -1, -1, -1, 0, fnLatchGroup);
        }

        static Row slot(int page, int stripRow, int col, @NonNull String label, @Nullable String fnHint,
                int profileSlot1Based) {
            return slot(page, stripRow, col, label, fnHint, profileSlot1Based, FN_LATCH_NONE);
        }

        static Row slot(int page, int stripRow, int col, @NonNull String label, @Nullable String fnHint,
                int profileSlot1Based, int fnLatchGroup) {
            return new Row(VIEW_TYPE_SLOT, label, fnHint, page, stripRow, col, profileSlot1Based, fnLatchGroup);
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
                // US HID: 0x33 unshifted is ';', shifted is ':'.
                return requiresShift ? "\"" : ";";
            case 0x20:
                return "%";
            case 0x1F:
                return "^";
            case 0x38:
                return requiresShift ? "&" : "<";
            case 0x31:
                return requiresShift ? "*" : ">";
            case 0x2D:
                return requiresShift ? "." : ",";
            case 0x26:
                return requiresShift ? "`" : null;
            case 0x27:
                return requiresShift ? "~" : null;
            case 0x35:
                // Page 2 grave key: Fn-off pair is ( / ); Fn-on row shows ` / ~ with ( / ) hints.
                return requiresShift ? ")" : "(";
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

    /**
     * Factory top-right local-Fn corner hint for fixed strip page index 2 (punctuation grid), independent
     * of Rows 2–3 strip profile decorative glyphs. Uses the same-slot other-latch cap from
     * {@link #PAGE2_ROW2_BASE} / {@link #PAGE2_ROW2_FN} / {@link #PAGE2_ROW3_BASE} / {@link #PAGE2_ROW3_FN}
     * (not HID {@link #overlayFnHint} pairings).
     *
     * @param stripRow    physical strip row {@code 2} or {@code 3}
     * @param stripCol    column {@code 0…6} (row 3 col {@code 6} is the Fn toggle — returns {@code null})
     * @param localFnLocked same meaning as {@code CustomKeyboardView#fixedTopLocalFnLocked}
     * @return opposite-layer legend for the key cap, or {@code null} if none
     */
    @Nullable
    public static String page2LocalFnOppositeCornerHint(int stripRow, int stripCol, boolean localFnLocked) {
        if (stripRow != 2 && stripRow != 3) {
            return null;
        }
        if (stripCol < 0 || stripCol > 6) {
            return null;
        }
        if (stripRow == 3 && stripCol == 6) {
            return null;
        }
        if (stripRow == 3 && stripCol > 5) {
            return null;
        }
        if (stripRow == 2) {
            if (stripCol >= PAGE2_ROW2_BASE.length) {
                return null;
            }
            return localFnLocked ? PAGE2_ROW2_BASE[stripCol] : PAGE2_ROW2_FN[stripCol];
        }
        return localFnLocked ? PAGE2_ROW3_BASE[stripCol] : PAGE2_ROW3_FN[stripCol];
    }

    /**
     * Human key/chord label for strip catalog list cells. Uses Fn overlay hints when present; otherwise
     * {@link KeyParser#toLabelForTargetOs} with catalog cap fallback when the parser yields {@code Key <id>}.
     */
    @NonNull
    private static String stripCatalogKeyEventLabel(
            int hidCode,
            boolean hidShift,
            boolean fnLayer,
            @Nullable String fnOverlayHint,
            @NonNull String fallbackPhysicalLabel,
            @NonNull String targetOs
    ) {
        String os = targetOs != null && !targetOs.trim().isEmpty() ? targetOs.trim() : "macos";
        if (fnLayer && fnOverlayHint != null && !fnOverlayHint.isEmpty()) {
            return fnOverlayHint;
        }
        int mod = hidShift ? 0x02 : 0;
        String parsed = KeyParser.toLabelForTargetOs(hidCode, mod, os);
        if (parsed.startsWith("Key ")) {
            return fallbackPhysicalLabel;
        }
        return parsed;
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
            String sub = hint;
            out.add(Row.slot(pageIndex, 2, c, row2[c][0], sub, row2ProfileSlot[c]));
        }
        out.add(Row.section("Row 3", null));
        for (int c = 0; c < KeyboardStripPresetConstants.TOP_PANEL_COLUMNS; c++) {
            String hint = overlayFnHint(row3Codes[c], row3Shift[c]);
            String sub = hint;
            out.add(Row.slot(pageIndex, 3, c, row3[c][0], sub, row3ProfileSlot[c]));
        }
    }

    /**
     * Builds catalog rows (sections + slots) for fixed strip pages 0–2. {@code pm} is used for page 2 display.
     */
    @NonNull
    public static List<Row> build(@NonNull Context context, @NonNull ShortcutProfileManager pm) {
        List<Row> out = new ArrayList<>();
        int[] noSlot = new int[]{0, 0, 0, 0, 0, 0, 0};

        String p0sub = context.getString(R.string.shortcut_hub_strip_catalog_page0_subtitle);
        String[][] p0r2 = {{"F7"}, {"F8"}, {"F9"}, {"F10"}, {"F11"}, {"F12"}, {"="}};
        int[] p0r2c = {0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x2E};
        boolean[] p0r2s = {false, false, false, false, false, false, false};
        String[][] p0r3 = {{"F1"}, {"F2"}, {"F3"}, {"F4"}, {"F5"}, {"F6"}, {"FN"}};
        int[] p0r3c = {0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0xF00C};
        boolean[] p0r3s = {false, false, false, false, false, false, false};
        addPage(out, 0, context.getString(R.string.shortcut_hub_strip_catalog_page0_title), p0sub, p0r2, p0r3,
                p0r2c, p0r2s, p0r3c, p0r3s, noSlot, noSlot);

        String p1sub = "Modifiers and navigation; second row shows overlays when local Fn is latched on.";
        String[][] p1r2 = {{"Ctrl"}, {"Alt"}, {"Win/Cmd"}, {"Tab"}, {"Up"}, {"Enter"}, {"PH1"}};
        int[] p1r2c = {0xE0, 0xE2, 0xE3, 0x2B, 0x52, 0x28, 0xF00A};
        boolean[] p1r2s = {false, false, false, false, false, false, false};
        String[][] p1r3 = {{"Esc"}, {"Shift"}, {"Del"}, {"Left"}, {"Down"}, {"Right"}, {"FN"}};
        int[] p1r3c = {0x29, 0xE1, 0x4C, 0x50, 0x51, 0x4F, 0xF00C};
        boolean[] p1r3s = {false, false, false, false, false, false, false};
        addPage(out, 1, "Page 1 — Modifiers & nav", p1sub, p1r2, p1r3, p1r2c, p1r2s, p1r3c, p1r3s, noSlot, noSlot);

        out.add(Row.section("Page 2 — Hub & symbols",
                "Row content swaps when local Fn is on (latch). Active app profile and Rows 2–3 strip profile are set in Keyboard & Mouse Pro setup and Shortcut Hub."));
        addPage2Variant(context, out, false, pm);
        addPage2Variant(context, out, true, pm);

        return out;
    }

    /**
     * Flat list for strip profile grid UI: page and row band headers (full width) plus slot cells in
     * row-major order (col0 base, col0 fn, col1 base, …) for a 2-column {@link androidx.recyclerview.widget.GridLayoutManager}.
     */
    @NonNull
    public static List<StripCatalogGridItem> buildGridItems(
            @NonNull Context context,
            @NonNull ShortcutProfileManager pm,
            @NonNull String targetOs
    ) {
        List<StripCatalogGridItem> out = new ArrayList<>();

        appendGridPage0(context, out, targetOs);

        appendGridPage(
                context, out, 1,
                "Page 1 — Modifiers & nav",
                "Modifiers and navigation; second row shows overlays when local Fn is latched on.",
                new String[]{"Ctrl", "Alt", "Win/Cmd", "Tab", "Up", "Enter", "PH1"},
                new int[]{0xE0, 0xE2, 0xE3, 0x2B, 0x52, 0x28, 0xF00A},
                new boolean[]{false, false, false, false, false, false, false},
                new String[]{"Esc", "Shift", "Del", "Left", "Down", "Right", "FN"},
                new int[]{0x29, 0xE1, 0x4C, 0x50, 0x51, 0x4F, 0xF00C},
                new boolean[]{false, false, false, false, false, false, false},
                targetOs);

        appendGridPage2(context, out, pm, targetOs);
        return out;
    }

    private static void appendGridPage0(
            @NonNull Context context,
            @NonNull List<StripCatalogGridItem> out,
            @NonNull String targetOs
    ) {
        out.add(StripCatalogGridItem.pageHeader(
                context.getString(R.string.shortcut_hub_strip_catalog_page0_title),
                context.getString(R.string.shortcut_hub_strip_catalog_page0_subtitle)));
        appendGridStripRowPage0(context, out, 2,
                new String[]{"F7", "F8", "F9", "F10", "F11", "F12", "="},
                new int[]{0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x2E},
                new boolean[]{false, false, false, false, false, false, false},
                targetOs);
        appendGridStripRowPage0(context, out, 3,
                new String[]{"F1", "F2", "F3", "F4", "F5", "F6", "FN"},
                new int[]{0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0xF00C},
                new boolean[]{false, false, false, false, false, false, false},
                targetOs);
    }

    /**
     * Page 0 grid: Base column shows unlatched strip caps (digits/symbols); Fn column shows F-key labels,
     * matching {@link com.openterface.keymod.CustomKeyboardView#resolveFixedTopLocalFnMapping} (latch off → overlay).
     * Layer chips use the same strings as {@link #appendGridStripRow}: Base and Fn.
     */
    private static void appendGridStripRowPage0(
            @NonNull Context context,
            @NonNull List<StripCatalogGridItem> out,
            int stripRow,
            @NonNull String[] fLabels,
            @NonNull int[] codes,
            @NonNull boolean[] shifts,
            @NonNull String targetOs
    ) {
        out.add(StripCatalogGridItem.rowBandHeader(stripRow == 2 ? "Row 2" : "Row 3"));
        String baseLayer = context.getString(R.string.shortcut_hub_strip_grid_layer_base);
        String fnLayerLbl = context.getString(R.string.shortcut_hub_strip_grid_layer_fn);
        for (int c = 0; c < KeyboardStripPresetConstants.TOP_PANEL_COLUMNS; c++) {
            String baseKey = StripSlotMapStore.slotKey(0, stripRow, c, false);
            String fnKey = StripSlotMapStore.slotKey(0, stripRow, c, true);
            String fCap = fLabels[c];
            String digit = overlayFnHint(codes[c], shifts[c]);
            String basePhysical = digit != null ? digit : fCap;
            String baseEv = stripCatalogKeyEventLabel(codes[c], shifts[c], false, null, basePhysical, targetOs);
            String fnEv = stripCatalogKeyEventLabel(codes[c], shifts[c], true, null, fCap, targetOs);
            out.add(StripCatalogGridItem.slotCell(
                    baseKey, 0, stripRow, c, false, baseLayer, basePhysical, null, baseEv));
            out.add(StripCatalogGridItem.slotCell(
                    fnKey, 0, stripRow, c, true, fnLayerLbl, fCap, null, fnEv));
        }
    }

    private static void appendGridPage(
            @NonNull Context context,
            List<StripCatalogGridItem> out,
            int pageIndex,
            @NonNull String pageTitle,
            @Nullable String pageSubtitle,
            @NonNull String[] row2Labels,
            @NonNull int[] row2Codes,
            @NonNull boolean[] row2Shift,
            @NonNull String[] row3Labels,
            @NonNull int[] row3Codes,
            @NonNull boolean[] row3Shift,
            @NonNull String targetOs
    ) {
        out.add(StripCatalogGridItem.pageHeader(pageTitle, pageSubtitle));
        appendGridStripRow(context, out, pageIndex, 2, row2Labels, row2Codes, row2Shift, null, targetOs);
        appendGridStripRow(context, out, pageIndex, 3, row3Labels, row3Codes, row3Shift, null, targetOs);
    }

    private static void appendGridStripRow(
            @NonNull Context context,
            List<StripCatalogGridItem> out,
            int pageIndex,
            int stripRow,
            @NonNull String[] labels,
            @NonNull int[] codes,
            @NonNull boolean[] shifts,
            @Nullable String[] latchOnPrimaryPerCol,
            @NonNull String targetOs
    ) {
        out.add(StripCatalogGridItem.rowBandHeader(stripRow == 2 ? "Row 2" : "Row 3"));
        for (int c = 0; c < KeyboardStripPresetConstants.TOP_PANEL_COLUMNS; c++) {
            String baseKey = StripSlotMapStore.slotKey(pageIndex, stripRow, c, false);
            String fnKey = StripSlotMapStore.slotKey(pageIndex, stripRow, c, true);
            String phy = labels[c];
            String hint = overlayFnHint(codes[c], shifts[c]);
            String fnPrimary = hint != null ? hint
                    : context.getString(R.string.shortcut_hub_strip_grid_fn_layer);
            String latchLine = null;
            if (latchOnPrimaryPerCol != null && c < latchOnPrimaryPerCol.length) {
                String onCap = latchOnPrimaryPerCol[c];
                if (onCap != null && !onCap.equals(phy)) {
                    latchLine = context.getString(R.string.shortcut_hub_strip_grid_fn_on_hint, onCap);
                }
            }
            String baseLayer = context.getString(R.string.shortcut_hub_strip_grid_layer_base);
            String fnLayerLbl = context.getString(R.string.shortcut_hub_strip_grid_layer_fn);
            String baseEv = stripCatalogKeyEventLabel(codes[c], shifts[c], false, null, phy, targetOs);
            String fnEv = stripCatalogKeyEventLabel(codes[c], shifts[c], true, hint, fnPrimary, targetOs);
            out.add(StripCatalogGridItem.slotCell(
                    baseKey, pageIndex, stripRow, c, false, baseLayer, phy, latchLine, baseEv));
            out.add(StripCatalogGridItem.slotCell(
                    fnKey, pageIndex, stripRow, c, true, fnLayerLbl, fnPrimary, null, fnEv));
        }
    }

    private static void appendGridPage2(
            @NonNull Context context,
            List<StripCatalogGridItem> out,
            @NonNull ShortcutProfileManager pm,
            @NonNull String targetOs
    ) {
        out.add(StripCatalogGridItem.pageHeader(
                "Page 2 — Hub & symbols",
                "Caps swap when local Fn is latched; slot keys are the same in both states."));

        int[] r2OffCodes = {0x26, 0x27, 0x2F, 0x30, 0x33, 0x20, 0x1F};
        boolean[] r2OffShift = {true, true, false, false, true, true, true};
        int[] r3OffCodes = {0x38, 0x31, 0x31, 0x38, 0x2D, 0x2D, 0xF00C};
        boolean[] r3OffShift = {false, false, true, true, false, true, false};

        appendGridStripRow(context, out, 2, 2, PAGE2_ROW2_BASE, r2OffCodes, r2OffShift, PAGE2_ROW2_FN, targetOs);
        appendGridStripRow(context, out, 2, 3, PAGE2_ROW3_BASE, r3OffCodes, r3OffShift, PAGE2_ROW3_FN, targetOs);
    }

    private static void addPage2Variant(
            @NonNull Context context,
            List<Row> out,
            boolean fnOn,
            @NonNull ShortcutProfileManager pm
    ) {
        final int latch = fnOn ? FN_LATCH_ON : FN_LATCH_OFF;
        out.add(Row.section(fnOn ? "Page 2 — local Fn on" : "Page 2 — local Fn off", null, latch));
        out.add(Row.section("Row 2", null, latch));
        if (fnOn) {
            addSlot(out, 2, 2, 0, "`", overlayFnHint(0x35, false), 0, latch);
            addSlot(out, 2, 2, 1, "~", overlayFnHint(0x35, true), 0, latch);
            addSlot(out, 2, 2, 2, "'", overlayFnHint(0x34, false), 0, latch);
            addSlot(out, 2, 2, 3, "\"", overlayFnHint(0x34, true), 0, latch);
            addSlot(out, 2, 2, 4, "%", overlayFnHint(0x22, true), 0, latch);
            addSlot(out, 2, 2, 5, "^", overlayFnHint(0x23, true), 0, latch);
            addSlot(out, 2, 2, 6, "|", overlayFnHint(0x31, true), 0, latch);
            out.add(Row.section("Row 3", null, latch));
            addSlot(out, 2, 3, 0, "<", overlayFnHint(0x36, true), 0, latch);
            addSlot(out, 2, 3, 1, ">", overlayFnHint(0x37, true), 0, latch);
            addSlot(out, 2, 3, 2, "*", overlayFnHint(0x25, true), 0, latch);
            addSlot(out, 2, 3, 3, "&", overlayFnHint(0x24, true), 0, latch);
            addSlot(out, 2, 3, 4, ",", overlayFnHint(0x36, false), 0, latch);
            addSlot(out, 2, 3, 5, ".", overlayFnHint(0x37, false), 0, latch);
            addSlot(out, 2, 3, 6, "FN", null, 0, latch);
        } else {
            addSlot(out, 2, 2, 0, "(", overlayFnHint(0x26, true), 0, latch);
            addSlot(out, 2, 2, 1, ")", overlayFnHint(0x27, true), 0, latch);
            addSlot(out, 2, 2, 2, "[", overlayFnHint(0x2F, false), 0, latch);
            addSlot(out, 2, 2, 3, "]", overlayFnHint(0x30, false), 0, latch);
            addSlot(out, 2, 2, 4, ":", overlayFnHint(0x33, true), 0, latch);
            addSlot(out, 2, 2, 5, "#", overlayFnHint(0x20, true), 0, latch);
            addSlot(out, 2, 2, 6, "@", overlayFnHint(0x1F, true), 0, latch);
            out.add(Row.section("Row 3", null, latch));
            addSlot(out, 2, 3, 0, "/", overlayFnHint(0x38, false), 0, latch);
            addSlot(out, 2, 3, 1, "\\", overlayFnHint(0x31, false), 0, latch);
            addSlot(out, 2, 3, 2, "|", overlayFnHint(0x31, true), 0, latch);
            addSlot(out, 2, 3, 3, "?", overlayFnHint(0x38, true), 0, latch);
            addSlot(out, 2, 3, 4, "-", overlayFnHint(0x2D, false), 0, latch);
            addSlot(out, 2, 3, 5, "_", overlayFnHint(0x2D, true), 0, latch);
            addSlot(out, 2, 3, 6, "FN", null, 0, latch);
        }
    }

    private static void addSlot(List<Row> out, int page, int stripRow, int col,
            @NonNull String label, @Nullable String fnHint, int profileSlot, int fnLatch) {
        out.add(Row.slot(page, stripRow, col, label, fnHint, profileSlot, fnLatch));
    }

    /** Parsed canonical strip slot key {@code b-p0r2c1} / legacy {@code p0_r2_c0_base} (rows 2–3 only). */
    public static final class ParsedSlotKey {
        public final int pageIndex;
        public final int stripRow;
        /** 0-based column index (0…{@link KeyboardStripPresetConstants#TOP_PANEL_COLUMNS}-1). */
        public final int col;
        /** {@code true} = Fn layer ({@code f-p…} or legacy {@code _fn}). */
        public final boolean fnLayer;

        public ParsedSlotKey(int pageIndex, int stripRow, int col, boolean fnLayer) {
            this.pageIndex = pageIndex;
            this.stripRow = stripRow;
            this.col = col;
            this.fnLayer = fnLayer;
        }
    }

    /** Factory HID for a physical strip slot (matches {@link #buildGridItems} page 0–2, Fn-off caps). */
    public static final class FactoryHid {
        public final int keyCode;
        /** {@code 0} or {@code 0x02} (Shift) only. */
        public final int modifiers;

        public FactoryHid(int keyCode, int modifiers) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }
    }

    /** Canonical: {@code b-p0r2c1} … {@code f-p0r2c7} (1-based column in id). */
    private static final Pattern SLOT_KEY_PATTERN_CANONICAL =
            Pattern.compile("^(b|f)-p(\\d+)r(2|3)c([1-7])$", Pattern.CASE_INSENSITIVE);

    /** Legacy persisted form: {@code p0_r2_c0_base}. */
    private static final Pattern SLOT_KEY_PATTERN_LEGACY =
            Pattern.compile("^p(\\d+)_r(2|3)_c(\\d+)_(base|fn)$", Pattern.CASE_INSENSITIVE);

    private static final int[] P0_R2_CODES = {0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x2E};
    private static final boolean[] P0_R2_SHIFT = {false, false, false, false, false, false, false};
    private static final int[] P0_R3_CODES = {0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0xF00C};
    private static final boolean[] P0_R3_SHIFT = {false, false, false, false, false, false, false};

    private static final int[] P1_R2_CODES = {0xE0, 0xE2, 0xE3, 0x2B, 0x52, 0x28, 0xF00A};
    private static final boolean[] P1_R2_SHIFT = {false, false, false, false, false, false, false};
    private static final int[] P1_R3_CODES = {0x29, 0xE1, 0x4C, 0x50, 0x51, 0x4F, 0xF00C};
    private static final boolean[] P1_R3_SHIFT = {false, false, false, false, false, false, false};

    /** Cols 0–1: Shift+9 / Shift+0 → "(" / ")"; remainder matches page 2 punctuation row. */
    private static final int[] P2_R2_CODES = {0x26, 0x27, 0x2F, 0x30, 0x33, 0x20, 0x1F};
    private static final boolean[] P2_R2_SHIFT = {true, true, false, false, true, true, true};
    private static final int[] P2_R3_CODES = {0x38, 0x31, 0x31, 0x38, 0x2D, 0x2D, 0xF00C};
    private static final boolean[] P2_R3_SHIFT = {false, false, true, true, false, true, false};

    @Nullable
    public static ParsedSlotKey parseSlotKey(@NonNull String slotKey) {
        String t = slotKey.trim();
        Matcher m = SLOT_KEY_PATTERN_CANONICAL.matcher(t);
        if (m.matches()) {
            boolean fn = "f".equalsIgnoreCase(m.group(1));
            int page = Integer.parseInt(m.group(2));
            int row = Integer.parseInt(m.group(3));
            int col1 = Integer.parseInt(m.group(4));
            int col0 = col1 - 1;
            if (col0 < 0 || col0 >= KeyboardStripPresetConstants.TOP_PANEL_COLUMNS) {
                return null;
            }
            return new ParsedSlotKey(page, row, col0, fn);
        }
        Matcher legacy = SLOT_KEY_PATTERN_LEGACY.matcher(t);
        if (!legacy.matches()) {
            return null;
        }
        int page = Integer.parseInt(legacy.group(1));
        int row = Integer.parseInt(legacy.group(2));
        int col = Integer.parseInt(legacy.group(3));
        boolean fn = "fn".equalsIgnoreCase(legacy.group(4));
        if (col < 0 || col >= KeyboardStripPresetConstants.TOP_PANEL_COLUMNS) {
            return null;
        }
        return new ParsedSlotKey(page, row, col, fn);
    }

    /**
     * HID + optional Shift for the factory mapping of a strip slot (same physical key for Base/Fn grid
     * columns; Fn-off Page 2 codes for page index 2).
     */
    @Nullable
    public static FactoryHid resolveFactoryHidForSlot(@NonNull ParsedSlotKey slot) {
        int c = slot.col;
        if (c < 0 || c >= KeyboardStripPresetConstants.TOP_PANEL_COLUMNS) {
            return null;
        }
        switch (slot.pageIndex) {
            case 0:
                if (slot.stripRow == 2) {
                    return new FactoryHid(P0_R2_CODES[c], P0_R2_SHIFT[c] ? 0x02 : 0);
                }
                if (slot.stripRow == 3) {
                    return new FactoryHid(P0_R3_CODES[c], P0_R3_SHIFT[c] ? 0x02 : 0);
                }
                return null;
            case 1:
                if (slot.stripRow == 2) {
                    return new FactoryHid(P1_R2_CODES[c], P1_R2_SHIFT[c] ? 0x02 : 0);
                }
                if (slot.stripRow == 3) {
                    return new FactoryHid(P1_R3_CODES[c], P1_R3_SHIFT[c] ? 0x02 : 0);
                }
                return null;
            case 2:
                if (slot.stripRow == 2) {
                    return new FactoryHid(P2_R2_CODES[c], P2_R2_SHIFT[c] ? 0x02 : 0);
                }
                if (slot.stripRow == 3) {
                    return new FactoryHid(P2_R3_CODES[c], P2_R3_SHIFT[c] ? 0x02 : 0);
                }
                return null;
            default:
                return null;
        }
    }
}
