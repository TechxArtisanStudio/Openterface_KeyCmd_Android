package com.openterface.keymod.preset;

import androidx.annotation.NonNull;

import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Factory content for non-deletable Rows 2–3 strip built-ins.
 *
 * <p>Two visual-only built-ins ("Symbols ★" and "Math ∑") render decorative glyphs while sending
 * factory ASCII HID. Four newer built-ins ("Box &amp; Lines", "Latin Extended", "Arrows &amp; Shapes",
 * "Currency &amp; Punctuation") populate most slots with a {@code unicodeCodePoint} and dispatch
 * via the per-OS Unicode Hex Input alt-code path in
 * {@code CustomKeyboardView.sendStripUnicodeShortcut} / {@code ShortcutHubFragment.executeUnicodeShortcut}.
 *
 * <p>All builders fill every customizable strip slot across pages 0/1/2 (base + Fn layers) so the
 * Shortcut Hub picker presents a complete, distinct strip skin. Page 2 row 2 cols 0–1 (base + Fn)
 * always remain ASCII paren / grave-tilde to satisfy the invariant enforced by
 * {@link Rows23StripProfileManager#replaceBuiltInProfileIfPage2Row2LayoutStale} and
 * {@code Rows23StripBuiltinsTest.symbolsAndMathProfiles_page2Row2*}.
 *
 * <p>Modifiers stay within {@link HidKeyCatalog#normalizeStripModifiers} (none or Shift only).
 */
public final class Rows23StripProfileBuiltins {

    private static final int MOD_NONE = 0;
    private static final int MOD_SHIFT = 0x02;

    private static final String SYMBOLS_ID_PREFIX = "builtin_symbols_";
    private static final String MATH_ID_PREFIX = "builtin_math_";
    private static final String BOX_LINES_ID_PREFIX = "builtin_boxlines_";
    private static final String LATIN_ID_PREFIX = "builtin_latin_";
    private static final String ARROWS_ID_PREFIX = "builtin_arrows_";
    private static final String CURRENCY_ID_PREFIX = "builtin_currency_";

    private Rows23StripProfileBuiltins() {
    }

    @NonNull
    public static Rows23StripProfile buildSymbolsProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.SYMBOLS_PROFILE_ID, "Symbols ★",
                SYMBOLS_ID_PREFIX, SYMBOLS_SPECS);
    }

    @NonNull
    public static Rows23StripProfile buildMathProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.MATH_PROFILE_ID, "Math ∑",
                MATH_ID_PREFIX, MATH_SPECS);
    }

    @NonNull
    public static Rows23StripProfile buildBoxLinesProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.BOX_LINES_PROFILE_ID, "Box & Lines",
                BOX_LINES_ID_PREFIX, BOX_LINES_SPECS);
    }

    @NonNull
    public static Rows23StripProfile buildLatinProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.LATIN_PROFILE_ID, "Latin Extended",
                LATIN_ID_PREFIX, LATIN_SPECS);
    }

    @NonNull
    public static Rows23StripProfile buildArrowsProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.ARROWS_PROFILE_ID, "Arrows & Shapes",
                ARROWS_ID_PREFIX, ARROWS_SPECS);
    }

    @NonNull
    public static Rows23StripProfile buildCurrencyProfile() {
        return buildFromSpecs(
                Rows23StripProfileConstants.CURRENCY_PROFILE_ID, "Currency & Punctuation",
                CURRENCY_ID_PREFIX, CURRENCY_SPECS);
    }

    @NonNull
    private static Rows23StripProfile buildFromSpecs(
            @NonNull String id, @NonNull String name,
            @NonNull String idPrefix, @NonNull SlotSpec[] specs) {
        Rows23StripProfile p = newProfileShell(id, name);
        int order = 0;
        for (SlotSpec spec : specs) {
            order = appendSlot(p, idPrefix, spec, order);
        }
        return p;
    }

    @NonNull
    private static Rows23StripProfile newProfileShell(@NonNull String id, @NonNull String name) {
        Rows23StripProfile p = new Rows23StripProfile();
        p.id = id;
        p.name = name;
        p.createdAt = System.currentTimeMillis();
        p.shortcuts = new ArrayList<>();
        p.slotMap = new HashMap<>();
        return p;
    }

    private static int appendSlot(
            @NonNull Rows23StripProfile p,
            @NonNull String idPrefix,
            @NonNull SlotSpec spec,
            int order) {
        String slotKey = StripSlotMapStore.slotKey(spec.page, spec.row, spec.col, spec.fnLayer);
        String shortcutId = idPrefix + slotKey;
        Shortcut s = new Shortcut();
        s.id = shortcutId;
        s.name = spec.glyph;
        s.label = spec.glyph;
        s.keyCode = spec.keyCode;
        s.modifiers = HidKeyCatalog.normalizeStripModifiers(spec.modifiers);
        s.icon = spec.glyph;
        s.displayOrder = order;
        s.unicodeCodePoint = spec.unicodeCodePoint;
        p.shortcuts.add(s);
        p.slotMap.put(slotKey, shortcutId);
        return order + 1;
    }

    private static final class SlotSpec {
        final int page;
        final int row;
        final int col;
        final boolean fnLayer;
        final String glyph;
        final int keyCode;
        final int modifiers;
        /** Non-zero = dispatch as Unicode (host-side OS hex-input); {@link #keyCode} ignored. */
        final int unicodeCodePoint;

        SlotSpec(int page, int row, int col, boolean fnLayer,
                 @NonNull String glyph, int keyCode, int modifiers) {
            this(page, row, col, fnLayer, glyph, keyCode, modifiers, 0);
        }

        SlotSpec(int page, int row, int col, boolean fnLayer,
                 @NonNull String glyph, int keyCode, int modifiers, int unicodeCodePoint) {
            this.page = page;
            this.row = row;
            this.col = col;
            this.fnLayer = fnLayer;
            this.glyph = glyph;
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.unicodeCodePoint = unicodeCodePoint;
        }
    }

    /** Convenience: Unicode-typed slot (keyCode 0, modifiers 0, glyph = string of code point). */
    @NonNull
    private static SlotSpec u(int page, int row, int col, boolean fnLayer, int codePoint) {
        return new SlotSpec(page, row, col, fnLayer,
                new String(Character.toChars(codePoint)), 0, MOD_NONE, codePoint);
    }

    /** ASCII paren-base for page 2 row 2 cols 0–1 (preserves built-in invariant). */
    @NonNull
    private static SlotSpec parenBase(int col, @NonNull String glyph, int hid) {
        return new SlotSpec(2, 2, col, false, glyph, hid, MOD_SHIFT);
    }

    /** ASCII grave-or-tilde Fn for page 2 row 2 cols 0–1 (preserves built-in invariant). */
    @NonNull
    private static SlotSpec graveTildeFn(int col, @NonNull String glyph, int modifiers) {
        return new SlotSpec(2, 2, col, true, glyph, 0x35, modifiers);
    }

    /**
     * "Symbols ★" — keeps factory HID for every slot; only the rendered glyph changes.
     * Each region uses a coherent decorative palette so the strip reads like a themed skin.
     */
    private static final SlotSpec[] SYMBOLS_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — digit overlay 7 8 9 0 + - *
            new SlotSpec(0, 2, 0, false, "✶", 0x24, MOD_NONE),
            new SlotSpec(0, 2, 1, false, "✷", 0x25, MOD_NONE),
            new SlotSpec(0, 2, 2, false, "✸", 0x26, MOD_NONE),
            new SlotSpec(0, 2, 3, false, "✹", 0x27, MOD_NONE),
            new SlotSpec(0, 2, 4, false, "✺", 0x2E, MOD_SHIFT),
            new SlotSpec(0, 2, 5, false, "✻", 0x2D, MOD_NONE),
            new SlotSpec(0, 2, 6, false, "✼", 0x25, MOD_SHIFT),
            // Page 0 row 2 fn — F7..F12, =
            new SlotSpec(0, 2, 0, true, "★", 0x40, MOD_NONE),
            new SlotSpec(0, 2, 1, true, "☆", 0x41, MOD_NONE),
            new SlotSpec(0, 2, 2, true, "✦", 0x42, MOD_NONE),
            new SlotSpec(0, 2, 3, true, "✧", 0x43, MOD_NONE),
            new SlotSpec(0, 2, 4, true, "✪", 0x44, MOD_NONE),
            new SlotSpec(0, 2, 5, true, "✫", 0x45, MOD_NONE),
            new SlotSpec(0, 2, 6, true, "✬", 0x2E, MOD_NONE),
            // Page 0 row 3 base — digits 1..6
            new SlotSpec(0, 3, 0, false, "❅", 0x1E, MOD_NONE),
            new SlotSpec(0, 3, 1, false, "❆", 0x1F, MOD_NONE),
            new SlotSpec(0, 3, 2, false, "❇", 0x20, MOD_NONE),
            new SlotSpec(0, 3, 3, false, "❈", 0x21, MOD_NONE),
            new SlotSpec(0, 3, 4, false, "❉", 0x22, MOD_NONE),
            new SlotSpec(0, 3, 5, false, "❊", 0x23, MOD_NONE),
            // Page 0 row 3 fn — F1..F6
            new SlotSpec(0, 3, 0, true, "☀", 0x3A, MOD_NONE),
            new SlotSpec(0, 3, 1, true, "☁", 0x3B, MOD_NONE),
            new SlotSpec(0, 3, 2, true, "☂", 0x3C, MOD_NONE),
            new SlotSpec(0, 3, 3, true, "☃", 0x3D, MOD_NONE),
            new SlotSpec(0, 3, 4, true, "☄", 0x3E, MOD_NONE),
            new SlotSpec(0, 3, 5, true, "☼", 0x3F, MOD_NONE),
            // Page 1 row 2 base — Ctrl, Alt, Win, Tab, Up, Enter
            new SlotSpec(1, 2, 0, false, "♥", 0xE0, MOD_NONE),
            new SlotSpec(1, 2, 1, false, "♡", 0xE2, MOD_NONE),
            new SlotSpec(1, 2, 2, false, "♦", 0xE3, MOD_NONE),
            new SlotSpec(1, 2, 3, false, "♢", 0x2B, MOD_NONE),
            new SlotSpec(1, 2, 4, false, "♠", 0x52, MOD_NONE),
            new SlotSpec(1, 2, 5, false, "♣", 0x28, MOD_NONE),
            // Page 1 row 2 fn — overlay: SCRLK PRTSC CAPS PAUSE HOME PGUP
            new SlotSpec(1, 2, 0, true, "⚀", 0x47, MOD_NONE),
            new SlotSpec(1, 2, 1, true, "⚁", 0x46, MOD_NONE),
            new SlotSpec(1, 2, 2, true, "⚂", 0x39, MOD_NONE),
            new SlotSpec(1, 2, 3, true, "⚃", 0x48, MOD_NONE),
            new SlotSpec(1, 2, 4, true, "⚄", 0x4A, MOD_NONE),
            new SlotSpec(1, 2, 5, true, "⚅", 0x4B, MOD_NONE),
            // Page 1 row 3 base — Esc, Shift, Del, Left, Down, Right
            new SlotSpec(1, 3, 0, false, "✓", 0x29, MOD_NONE),
            new SlotSpec(1, 3, 1, false, "✗", 0xE1, MOD_NONE),
            new SlotSpec(1, 3, 2, false, "✘", 0x4C, MOD_NONE),
            new SlotSpec(1, 3, 3, false, "✚", 0x50, MOD_NONE),
            new SlotSpec(1, 3, 4, false, "✜", 0x51, MOD_NONE),
            new SlotSpec(1, 3, 5, false, "✤", 0x4F, MOD_NONE),
            // Page 1 row 3 fn — overlay: SPACE BKSP DEL INS END PGDN
            new SlotSpec(1, 3, 0, true, "⚐", 0x2C, MOD_NONE),
            new SlotSpec(1, 3, 1, true, "⚑", 0x2A, MOD_NONE),
            new SlotSpec(1, 3, 2, true, "⚒", 0x4C, MOD_NONE),
            new SlotSpec(1, 3, 3, true, "⚓", 0x49, MOD_NONE),
            new SlotSpec(1, 3, 4, true, "⚔", 0x4D, MOD_NONE),
            new SlotSpec(1, 3, 5, true, "⚕", 0x4E, MOD_NONE),
            // Page 2 row 2 base — ( ) then [ ] …; Fn layer is ` ~ (matches factory row when latch off)
            new SlotSpec(2, 2, 0, false, "(", 0x26, MOD_SHIFT),
            new SlotSpec(2, 2, 1, false, ")", 0x27, MOD_SHIFT),
            new SlotSpec(2, 2, 2, false, "◈", 0x2F, MOD_NONE),
            new SlotSpec(2, 2, 3, false, "◉", 0x30, MOD_NONE),
            new SlotSpec(2, 2, 4, false, "◎", 0x33, MOD_SHIFT),
            new SlotSpec(2, 2, 5, false, "●", 0x20, MOD_SHIFT),
            new SlotSpec(2, 2, 6, false, "○", 0x1F, MOD_SHIFT),
            // Page 2 row 2 fn — same grave/tilde HID as base (strip overlay reads f-p… for these cells)
            new SlotSpec(2, 2, 0, true, "`", 0x35, MOD_NONE),
            new SlotSpec(2, 2, 1, true, "~", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 2, true, "▼", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 3, true, "▽", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 4, true, "◀", 0x34, MOD_SHIFT),
            new SlotSpec(2, 2, 5, true, "◁", 0x22, MOD_SHIFT),
            new SlotSpec(2, 2, 6, true, "▶", 0x23, MOD_SHIFT),
            // Page 2 row 3 base — / \ | ? - _
            new SlotSpec(2, 3, 0, false, "♬", 0x38, MOD_NONE),
            new SlotSpec(2, 3, 1, false, "♪", 0x31, MOD_NONE),
            new SlotSpec(2, 3, 2, false, "♫", 0x31, MOD_SHIFT),
            new SlotSpec(2, 3, 3, false, "♩", 0x38, MOD_SHIFT),
            new SlotSpec(2, 3, 4, false, "♭", 0x2D, MOD_NONE),
            new SlotSpec(2, 3, 5, false, "♯", 0x2D, MOD_SHIFT),
            // Page 2 row 3 fn — overlay: < > * & , .
            new SlotSpec(2, 3, 0, true, "☯", 0x36, MOD_SHIFT),
            new SlotSpec(2, 3, 1, true, "☮", 0x37, MOD_SHIFT),
            new SlotSpec(2, 3, 2, true, "☢", 0x25, MOD_SHIFT),
            new SlotSpec(2, 3, 3, true, "☣", 0x24, MOD_SHIFT),
            new SlotSpec(2, 3, 4, true, "☥", 0x36, MOD_NONE),
            new SlotSpec(2, 3, 5, true, "☦", 0x37, MOD_NONE),
    };

    /**
     * "Math ∑" — page 0 row 2 base sends Shift+digit so the rendered glyph (! @ # $ % ^ &) matches
     * what is typed; every other region keeps factory HID with math/science-leaning glyphs.
     */
    private static final SlotSpec[] MATH_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — Shift+digits → rendered = typed (!@#$%^&)
            new SlotSpec(0, 2, 0, false, "!", 0x1E, MOD_SHIFT),
            new SlotSpec(0, 2, 1, false, "@", 0x1F, MOD_SHIFT),
            new SlotSpec(0, 2, 2, false, "#", 0x20, MOD_SHIFT),
            new SlotSpec(0, 2, 3, false, "$", 0x21, MOD_SHIFT),
            new SlotSpec(0, 2, 4, false, "%", 0x22, MOD_SHIFT),
            new SlotSpec(0, 2, 5, false, "^", 0x23, MOD_SHIFT),
            new SlotSpec(0, 2, 6, false, "&", 0x24, MOD_SHIFT),
            // Page 0 row 2 fn — F7..F12, = with calculus / physics glyphs
            new SlotSpec(0, 2, 0, true, "∑", 0x40, MOD_NONE),
            new SlotSpec(0, 2, 1, true, "∫", 0x41, MOD_NONE),
            new SlotSpec(0, 2, 2, true, "∂", 0x42, MOD_NONE),
            new SlotSpec(0, 2, 3, true, "√", 0x43, MOD_NONE),
            new SlotSpec(0, 2, 4, true, "π", 0x44, MOD_NONE),
            new SlotSpec(0, 2, 5, true, "Ω", 0x45, MOD_NONE),
            new SlotSpec(0, 2, 6, true, "≈", 0x2E, MOD_NONE),
            // Page 0 row 3 base — digits 1..6 with arithmetic / relations
            new SlotSpec(0, 3, 0, false, "÷", 0x1E, MOD_NONE),
            new SlotSpec(0, 3, 1, false, "×", 0x1F, MOD_NONE),
            new SlotSpec(0, 3, 2, false, "±", 0x20, MOD_NONE),
            new SlotSpec(0, 3, 3, false, "∞", 0x21, MOD_NONE),
            new SlotSpec(0, 3, 4, false, "≠", 0x22, MOD_NONE),
            new SlotSpec(0, 3, 5, false, "≤", 0x23, MOD_NONE),
            // Page 0 row 3 fn — F1..F6 with units / operators
            new SlotSpec(0, 3, 0, true, "°", 0x3A, MOD_NONE),
            new SlotSpec(0, 3, 1, true, "µ", 0x3B, MOD_NONE),
            new SlotSpec(0, 3, 2, true, "∇", 0x3C, MOD_NONE),
            new SlotSpec(0, 3, 3, true, "∆", 0x3D, MOD_NONE),
            new SlotSpec(0, 3, 4, true, "∝", 0x3E, MOD_NONE),
            new SlotSpec(0, 3, 5, true, "∴", 0x3F, MOD_NONE),
            // Page 1 row 2 base — Ctrl/Alt/Win/Tab/Up/Enter (factory HID, vector glyphs)
            new SlotSpec(1, 2, 0, false, "∮", 0xE0, MOD_NONE),
            new SlotSpec(1, 2, 1, false, "∇", 0xE2, MOD_NONE),
            new SlotSpec(1, 2, 2, false, "⊕", 0xE3, MOD_NONE),
            new SlotSpec(1, 2, 3, false, "⊗", 0x2B, MOD_NONE),
            new SlotSpec(1, 2, 4, false, "⊥", 0x52, MOD_NONE),
            new SlotSpec(1, 2, 5, false, "∥", 0x28, MOD_NONE),
            // Page 1 row 2 fn — overlay codes with set / membership glyphs
            new SlotSpec(1, 2, 0, true, "∈", 0x47, MOD_NONE),
            new SlotSpec(1, 2, 1, true, "∉", 0x46, MOD_NONE),
            new SlotSpec(1, 2, 2, true, "⊂", 0x39, MOD_NONE),
            new SlotSpec(1, 2, 3, true, "⊃", 0x48, MOD_NONE),
            new SlotSpec(1, 2, 4, true, "⊆", 0x4A, MOD_NONE),
            new SlotSpec(1, 2, 5, true, "⊇", 0x4B, MOD_NONE),
            // Page 1 row 3 base — Esc/Shift/Del/Left/Down/Right (logic glyphs)
            new SlotSpec(1, 3, 0, false, "¬", 0x29, MOD_NONE),
            new SlotSpec(1, 3, 1, false, "∀", 0xE1, MOD_NONE),
            new SlotSpec(1, 3, 2, false, "∃", 0x4C, MOD_NONE),
            new SlotSpec(1, 3, 3, false, "↔", 0x50, MOD_NONE),
            new SlotSpec(1, 3, 4, false, "⇒", 0x51, MOD_NONE),
            new SlotSpec(1, 3, 5, false, "⇐", 0x4F, MOD_NONE),
            // Page 1 row 3 fn — overlay: SPACE BKSP DEL INS END PGDN
            new SlotSpec(1, 3, 0, true, "∧", 0x2C, MOD_NONE),
            new SlotSpec(1, 3, 1, true, "∨", 0x2A, MOD_NONE),
            new SlotSpec(1, 3, 2, true, "⊕", 0x4C, MOD_NONE),
            new SlotSpec(1, 3, 3, true, "⊗", 0x49, MOD_NONE),
            new SlotSpec(1, 3, 4, true, "⊖", 0x4D, MOD_NONE),
            new SlotSpec(1, 3, 5, true, "⊙", 0x4E, MOD_NONE),
            // Page 2 row 2 base — ( ) then [ ] : # @
            new SlotSpec(2, 2, 0, false, "(", 0x26, MOD_SHIFT),
            new SlotSpec(2, 2, 1, false, ")", 0x27, MOD_SHIFT),
            new SlotSpec(2, 2, 2, false, "≡", 0x2F, MOD_NONE),
            new SlotSpec(2, 2, 3, false, "⋮", 0x30, MOD_NONE),
            new SlotSpec(2, 2, 4, false, "⋯", 0x33, MOD_SHIFT),
            new SlotSpec(2, 2, 5, false, "⋰", 0x20, MOD_SHIFT),
            new SlotSpec(2, 2, 6, false, "⋱", 0x1F, MOD_SHIFT),
            // Page 2 row 2 fn — grave/tilde (match factory page 2 row 2 cols 0–1)
            new SlotSpec(2, 2, 0, true, "`", 0x35, MOD_NONE),
            new SlotSpec(2, 2, 1, true, "~", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 2, true, "▽", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 3, true, "◇", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 4, true, "◈", 0x34, MOD_SHIFT),
            new SlotSpec(2, 2, 5, true, "⊞", 0x22, MOD_SHIFT),
            new SlotSpec(2, 2, 6, true, "⊟", 0x23, MOD_SHIFT),
            // Page 2 row 3 base — / \ | ? - _ (relations)
            new SlotSpec(2, 3, 0, false, "∝", 0x38, MOD_NONE),
            new SlotSpec(2, 3, 1, false, "∞", 0x31, MOD_NONE),
            new SlotSpec(2, 3, 2, false, "⊥", 0x31, MOD_SHIFT),
            new SlotSpec(2, 3, 3, false, "∠", 0x38, MOD_SHIFT),
            new SlotSpec(2, 3, 4, false, "∡", 0x2D, MOD_NONE),
            new SlotSpec(2, 3, 5, false, "∢", 0x2D, MOD_SHIFT),
            // Page 2 row 3 fn — overlay: < > * & , . (integrals)
            new SlotSpec(2, 3, 0, true, "∰", 0x36, MOD_SHIFT),
            new SlotSpec(2, 3, 1, true, "∱", 0x37, MOD_SHIFT),
            new SlotSpec(2, 3, 2, true, "∲", 0x25, MOD_SHIFT),
            new SlotSpec(2, 3, 3, true, "∳", 0x24, MOD_SHIFT),
            new SlotSpec(2, 3, 4, true, "∴", 0x36, MOD_NONE),
            new SlotSpec(2, 3, 5, true, "∵", 0x37, MOD_NONE),
    };

    /**
     * "Box & Lines" — terminal table-drawing glyphs typed via Unicode HID. Page 2 row 2 cols 0–1
     * stay ASCII paren / grave-tilde to keep the page-2 invariant.
     */
    private static final SlotSpec[] BOX_LINES_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — light horizontals, heavy + double horizontals, top corners
            u(0, 2, 0, false, 0x2500), // ─
            u(0, 2, 1, false, 0x2501), // ━
            u(0, 2, 2, false, 0x2550), // ═
            u(0, 2, 3, false, 0x250C), // ┌
            u(0, 2, 4, false, 0x2510), // ┐
            u(0, 2, 5, false, 0x2554), // ╔
            u(0, 2, 6, false, 0x2557), // ╗
            // Page 0 row 2 fn — bottom corners + verticals
            u(0, 2, 0, true, 0x2514), // └
            u(0, 2, 1, true, 0x2518), // ┘
            u(0, 2, 2, true, 0x255A), // ╚
            u(0, 2, 3, true, 0x255D), // ╝
            u(0, 2, 4, true, 0x2502), // │
            u(0, 2, 5, true, 0x2503), // ┃
            u(0, 2, 6, true, 0x2551), // ║
            // Page 0 row 3 base — light T-junctions + cross
            u(0, 3, 0, false, 0x251C), // ├
            u(0, 3, 1, false, 0x2524), // ┤
            u(0, 3, 2, false, 0x252C), // ┬
            u(0, 3, 3, false, 0x2534), // ┴
            u(0, 3, 4, false, 0x253C), // ┼
            u(0, 3, 5, false, 0x250A), // ┊
            // Page 0 row 3 fn — heavy T-junctions + cross
            u(0, 3, 0, true, 0x2523), // ┣
            u(0, 3, 1, true, 0x252B), // ┫
            u(0, 3, 2, true, 0x2533), // ┳
            u(0, 3, 3, true, 0x253B), // ┻
            u(0, 3, 4, true, 0x254B), // ╋
            u(0, 3, 5, true, 0x2507), // ┇
            // Page 1 row 2 base — double T-junctions + cross
            u(1, 2, 0, false, 0x2560), // ╠
            u(1, 2, 1, false, 0x2563), // ╣
            u(1, 2, 2, false, 0x2566), // ╦
            u(1, 2, 3, false, 0x2569), // ╩
            u(1, 2, 4, false, 0x256C), // ╬
            u(1, 2, 5, false, 0x256A), // ╪
            // Page 1 row 2 fn — mixed light/double horizontal
            u(1, 2, 0, true, 0x255E), // ╞
            u(1, 2, 1, true, 0x2561), // ╡
            u(1, 2, 2, true, 0x2564), // ╤
            u(1, 2, 3, true, 0x2567), // ╧
            u(1, 2, 4, true, 0x2565), // ╥
            u(1, 2, 5, true, 0x2568), // ╨
            // Page 1 row 3 base — mixed double/light vertical + rounded arcs
            u(1, 3, 0, false, 0x255F), // ╟
            u(1, 3, 1, false, 0x2562), // ╢
            u(1, 3, 2, false, 0x256B), // ╫
            u(1, 3, 3, false, 0x256D), // ╭
            u(1, 3, 4, false, 0x256E), // ╮
            u(1, 3, 5, false, 0x256F), // ╯
            // Page 1 row 3 fn — last arc, diagonals, dashes
            u(1, 3, 0, true, 0x2570), // ╰
            u(1, 3, 1, true, 0x2571), // ╱
            u(1, 3, 2, true, 0x2572), // ╲
            u(1, 3, 3, true, 0x2573), // ╳
            u(1, 3, 4, true, 0x2504), // ┄
            u(1, 3, 5, true, 0x2505), // ┅
            // Page 2 row 2 base — cols 0–1 reserved ASCII; cols 2–6 dashes + first block
            parenBase(0, "(", 0x26),
            parenBase(1, ")", 0x27),
            u(2, 2, 2, false, 0x2506), // ┆
            u(2, 2, 3, false, 0x2508), // ┈
            u(2, 2, 4, false, 0x2509), // ┉
            u(2, 2, 5, false, 0x250B), // ┋
            u(2, 2, 6, false, 0x2580), // ▀
            // Page 2 row 2 fn — cols 0–1 reserved ASCII; cols 2–6 blocks
            graveTildeFn(0, "`", MOD_NONE),
            graveTildeFn(1, "~", MOD_SHIFT),
            u(2, 2, 2, true, 0x2584), // ▄
            u(2, 2, 3, true, 0x2588), // █
            u(2, 2, 4, true, 0x258C), // ▌
            u(2, 2, 5, true, 0x2590), // ▐
            u(2, 2, 6, true, 0x2591), // ░
            // Page 2 row 3 base — shades + squares + rectangles
            u(2, 3, 0, false, 0x2592), // ▒
            u(2, 3, 1, false, 0x2593), // ▓
            u(2, 3, 2, false, 0x25A0), // ■
            u(2, 3, 3, false, 0x25A1), // □
            u(2, 3, 4, false, 0x25AC), // ▬
            u(2, 3, 5, false, 0x25AD), // ▭
            // Page 2 row 3 fn — corner triangles + circles
            u(2, 3, 0, true, 0x25E2), // ◢
            u(2, 3, 1, true, 0x25E3), // ◣
            u(2, 3, 2, true, 0x25E4), // ◤
            u(2, 3, 3, true, 0x25E5), // ◥
            u(2, 3, 4, true, 0x25CF), // ●
            u(2, 3, 5, true, 0x25CB), // ○
    };

    /**
     * "Latin Extended" — Latin-1 Supplement letters (accented), fractions, units. Typed via
     * Unicode HID; ASCII paren / grave-tilde reserved on page 2 row 2 cols 0–1.
     */
    private static final SlotSpec[] LATIN_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — uppercase A variants + Æ
            u(0, 2, 0, false, 0x00C0), // À
            u(0, 2, 1, false, 0x00C1), // Á
            u(0, 2, 2, false, 0x00C2), // Â
            u(0, 2, 3, false, 0x00C3), // Ã
            u(0, 2, 4, false, 0x00C4), // Ä
            u(0, 2, 5, false, 0x00C5), // Å
            u(0, 2, 6, false, 0x00C6), // Æ
            // Page 0 row 2 fn — lowercase a variants + æ
            u(0, 2, 0, true, 0x00E0), // à
            u(0, 2, 1, true, 0x00E1), // á
            u(0, 2, 2, true, 0x00E2), // â
            u(0, 2, 3, true, 0x00E3), // ã
            u(0, 2, 4, true, 0x00E4), // ä
            u(0, 2, 5, true, 0x00E5), // å
            u(0, 2, 6, true, 0x00E6), // æ
            // Page 0 row 3 base — uppercase Ç..Ì
            u(0, 3, 0, false, 0x00C7), // Ç
            u(0, 3, 1, false, 0x00C8), // È
            u(0, 3, 2, false, 0x00C9), // É
            u(0, 3, 3, false, 0x00CA), // Ê
            u(0, 3, 4, false, 0x00CB), // Ë
            u(0, 3, 5, false, 0x00CC), // Ì
            // Page 0 row 3 fn — lowercase ç..ì
            u(0, 3, 0, true, 0x00E7), // ç
            u(0, 3, 1, true, 0x00E8), // è
            u(0, 3, 2, true, 0x00E9), // é
            u(0, 3, 3, true, 0x00EA), // ê
            u(0, 3, 4, true, 0x00EB), // ë
            u(0, 3, 5, true, 0x00EC), // ì
            // Page 1 row 2 base — uppercase Í..Ò
            u(1, 2, 0, false, 0x00CD), // Í
            u(1, 2, 1, false, 0x00CE), // Î
            u(1, 2, 2, false, 0x00CF), // Ï
            u(1, 2, 3, false, 0x00D0), // Ð
            u(1, 2, 4, false, 0x00D1), // Ñ
            u(1, 2, 5, false, 0x00D2), // Ò
            // Page 1 row 2 fn — lowercase í..ò
            u(1, 2, 0, true, 0x00ED), // í
            u(1, 2, 1, true, 0x00EE), // î
            u(1, 2, 2, true, 0x00EF), // ï
            u(1, 2, 3, true, 0x00F0), // ð
            u(1, 2, 4, true, 0x00F1), // ñ
            u(1, 2, 5, true, 0x00F2), // ò
            // Page 1 row 3 base — uppercase Ó..Ù (skip × at 00D7)
            u(1, 3, 0, false, 0x00D3), // Ó
            u(1, 3, 1, false, 0x00D4), // Ô
            u(1, 3, 2, false, 0x00D5), // Õ
            u(1, 3, 3, false, 0x00D6), // Ö
            u(1, 3, 4, false, 0x00D8), // Ø
            u(1, 3, 5, false, 0x00D9), // Ù
            // Page 1 row 3 fn — lowercase ó..ù (skip ÷ at 00F7)
            u(1, 3, 0, true, 0x00F3), // ó
            u(1, 3, 1, true, 0x00F4), // ô
            u(1, 3, 2, true, 0x00F5), // õ
            u(1, 3, 3, true, 0x00F6), // ö
            u(1, 3, 4, true, 0x00F8), // ø
            u(1, 3, 5, true, 0x00F9), // ù
            // Page 2 row 2 base — cols 0–1 reserved ASCII; cols 2–6 uppercase Ú..Þ
            parenBase(0, "(", 0x26),
            parenBase(1, ")", 0x27),
            u(2, 2, 2, false, 0x00DA), // Ú
            u(2, 2, 3, false, 0x00DB), // Û
            u(2, 2, 4, false, 0x00DC), // Ü
            u(2, 2, 5, false, 0x00DD), // Ý
            u(2, 2, 6, false, 0x00DE), // Þ
            // Page 2 row 2 fn — cols 0–1 reserved ASCII; cols 2–6 lowercase ú..þ
            graveTildeFn(0, "`", MOD_NONE),
            graveTildeFn(1, "~", MOD_SHIFT),
            u(2, 2, 2, true, 0x00FA), // ú
            u(2, 2, 3, true, 0x00FB), // û
            u(2, 2, 4, true, 0x00FC), // ü
            u(2, 2, 5, true, 0x00FD), // ý
            u(2, 2, 6, true, 0x00FE), // þ
            // Page 2 row 3 base — eszett, fractions, sign units
            u(2, 3, 0, false, 0x00DF), // ß
            u(2, 3, 1, false, 0x00BC), // ¼
            u(2, 3, 2, false, 0x00BD), // ½
            u(2, 3, 3, false, 0x00BE), // ¾
            u(2, 3, 4, false, 0x00B0), // °
            u(2, 3, 5, false, 0x00B1), // ±
            // Page 2 row 3 fn — math + super + section
            u(2, 3, 0, true, 0x00D7), // ×
            u(2, 3, 1, true, 0x00F7), // ÷
            u(2, 3, 2, true, 0x00B5), // µ
            u(2, 3, 3, true, 0x00B2), // ²
            u(2, 3, 4, true, 0x00B3), // ³
            u(2, 3, 5, true, 0x00A7), // §
    };

    /**
     * "Arrows & Shapes" — directional arrows + geometric shapes typed via Unicode HID.
     */
    private static final SlotSpec[] ARROWS_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — cardinals + diagonals
            u(0, 2, 0, false, 0x2190), // ←
            u(0, 2, 1, false, 0x2191), // ↑
            u(0, 2, 2, false, 0x2192), // →
            u(0, 2, 3, false, 0x2193), // ↓
            u(0, 2, 4, false, 0x2196), // ↖
            u(0, 2, 5, false, 0x2197), // ↗
            u(0, 2, 6, false, 0x2198), // ↘
            // Page 0 row 2 fn — last diagonal + bidirectional + harpoons
            u(0, 2, 0, true, 0x2199), // ↙
            u(0, 2, 1, true, 0x2194), // ↔
            u(0, 2, 2, true, 0x2195), // ↕
            u(0, 2, 3, true, 0x21C4), // ⇄
            u(0, 2, 4, true, 0x21C5), // ⇅
            u(0, 2, 5, true, 0x21C6), // ⇆
            u(0, 2, 6, true, 0x21CB), // ⇋
            // Page 0 row 3 base — hook arrows + return start
            u(0, 3, 0, false, 0x21A9), // ↩
            u(0, 3, 1, false, 0x21AA), // ↪
            u(0, 3, 2, false, 0x21B0), // ↰
            u(0, 3, 3, false, 0x21B1), // ↱
            u(0, 3, 4, false, 0x21B2), // ↲
            u(0, 3, 5, false, 0x21B3), // ↳
            // Page 0 row 3 fn — return + rotation
            u(0, 3, 0, true, 0x21B4), // ↴
            u(0, 3, 1, true, 0x21B5), // ↵
            u(0, 3, 2, true, 0x21B6), // ↶
            u(0, 3, 3, true, 0x21B7), // ↷
            u(0, 3, 4, true, 0x21BA), // ↺
            u(0, 3, 5, true, 0x21BB), // ↻
            // Page 1 row 2 base — double arrows
            u(1, 2, 0, false, 0x21D0), // ⇐
            u(1, 2, 1, false, 0x21D1), // ⇑
            u(1, 2, 2, false, 0x21D2), // ⇒
            u(1, 2, 3, false, 0x21D3), // ⇓
            u(1, 2, 4, false, 0x21D4), // ⇔
            u(1, 2, 5, false, 0x21D5), // ⇕
            // Page 1 row 2 fn — white-block + bold arrows
            u(1, 2, 0, true, 0x21E6), // ⇦
            u(1, 2, 1, true, 0x21E7), // ⇧
            u(1, 2, 2, true, 0x21E8), // ⇨
            u(1, 2, 3, true, 0x21E9), // ⇩
            u(1, 2, 4, true, 0x2B05), // ⬅
            u(1, 2, 5, true, 0x2B06), // ⬆
            // Page 1 row 3 base — bold arrows + filled triangles
            u(1, 3, 0, false, 0x2B07), // ⬇
            u(1, 3, 1, false, 0x27A1), // ➡
            u(1, 3, 2, false, 0x27A4), // ➤
            u(1, 3, 3, false, 0x25C0), // ◀
            u(1, 3, 4, false, 0x25B6), // ▶
            u(1, 3, 5, false, 0x25B2), // ▲
            // Page 1 row 3 fn — triangles + corner triangles
            u(1, 3, 0, true, 0x25BC), // ▼
            u(1, 3, 1, true, 0x25E2), // ◢
            u(1, 3, 2, true, 0x25E3), // ◣
            u(1, 3, 3, true, 0x25E4), // ◤
            u(1, 3, 4, true, 0x25E5), // ◥
            u(1, 3, 5, true, 0x25B3), // △
            // Page 2 row 2 base — cols 0–1 reserved ASCII; cols 2–6 white triangles + diamond
            parenBase(0, "(", 0x26),
            parenBase(1, ")", 0x27),
            u(2, 2, 2, false, 0x25BD), // ▽
            u(2, 2, 3, false, 0x25B7), // ▷
            u(2, 2, 4, false, 0x25C1), // ◁
            u(2, 2, 5, false, 0x25C7), // ◇
            u(2, 2, 6, false, 0x25C6), // ◆
            // Page 2 row 2 fn — cols 0–1 reserved ASCII; cols 2–6 circles
            graveTildeFn(0, "`", MOD_NONE),
            graveTildeFn(1, "~", MOD_SHIFT),
            u(2, 2, 2, true, 0x25CB), // ○
            u(2, 2, 3, true, 0x25CF), // ●
            u(2, 2, 4, true, 0x25D0), // ◐
            u(2, 2, 5, true, 0x25D1), // ◑
            u(2, 2, 6, true, 0x25EF), // ◯
            // Page 2 row 3 base — stars + suits
            u(2, 3, 0, false, 0x2605), // ★
            u(2, 3, 1, false, 0x2606), // ☆
            u(2, 3, 2, false, 0x2660), // ♠
            u(2, 3, 3, false, 0x2663), // ♣
            u(2, 3, 4, false, 0x2665), // ♥
            u(2, 3, 5, false, 0x2666), // ♦
            // Page 2 row 3 fn — notes, marks, hearts
            u(2, 3, 0, true, 0x266A), // ♪
            u(2, 3, 1, true, 0x266B), // ♫
            u(2, 3, 2, true, 0x2713), // ✓
            u(2, 3, 3, true, 0x2717), // ✗
            u(2, 3, 4, true, 0x2718), // ✘
            u(2, 3, 5, true, 0x2764), // ❤
    };

    /**
     * "Currency & Punctuation" — currencies + decorative punctuation typed via Unicode HID.
     */
    private static final SlotSpec[] CURRENCY_SPECS = new SlotSpec[] {
            // Page 0 row 2 base — major currencies row 1
            u(0, 2, 0, false, 0x20AC), // €
            u(0, 2, 1, false, 0x00A3), // £
            u(0, 2, 2, false, 0x00A5), // ¥
            u(0, 2, 3, false, 0x00A2), // ¢
            u(0, 2, 4, false, 0x00A4), // ¤
            u(0, 2, 5, false, 0x20A9), // ₩
            u(0, 2, 6, false, 0x20B9), // ₹
            // Page 0 row 2 fn — currencies row 2
            u(0, 2, 0, true, 0x20BD), // ₽
            u(0, 2, 1, true, 0x20BF), // ₿
            u(0, 2, 2, true, 0x20A8), // ₨
            u(0, 2, 3, true, 0x20AA), // ₪
            u(0, 2, 4, true, 0x20AB), // ₫
            u(0, 2, 5, true, 0x20A1), // ₡
            u(0, 2, 6, true, 0x20A6), // ₦
            // Page 0 row 3 base — more currencies
            u(0, 3, 0, false, 0x20B1), // ₱
            u(0, 3, 1, false, 0x20BA), // ₺
            u(0, 3, 2, false, 0x20A0), // ₠
            u(0, 3, 3, false, 0x20A3), // ₣
            u(0, 3, 4, false, 0x20A4), // ₤
            u(0, 3, 5, false, 0x20AD), // ₭
            // Page 0 row 3 fn — special punctuation row 1
            u(0, 3, 0, true, 0x00A1), // ¡
            u(0, 3, 1, true, 0x00BF), // ¿
            u(0, 3, 2, true, 0x00A7), // §
            u(0, 3, 3, true, 0x00B6), // ¶
            u(0, 3, 4, true, 0x00A9), // ©
            u(0, 3, 5, true, 0x00AE), // ®
            // Page 1 row 2 base — marks
            u(1, 2, 0, false, 0x2122), // ™
            u(1, 2, 1, false, 0x2117), // ℗
            u(1, 2, 2, false, 0x00B0), // °
            u(1, 2, 3, false, 0x00AC), // ¬
            u(1, 2, 4, false, 0x00A6), // ¦
            u(1, 2, 5, false, 0x00A8), // ¨
            // Page 1 row 2 fn — dashes & quotes (singles)
            u(1, 2, 0, true, 0x2013), // –
            u(1, 2, 1, true, 0x2014), // —
            u(1, 2, 2, true, 0x2010), // ‐
            u(1, 2, 3, true, 0x2018), // '
            u(1, 2, 4, true, 0x2019), // '
            u(1, 2, 5, true, 0x201C), // "
            // Page 1 row 3 base — quote variants
            u(1, 3, 0, false, 0x201D), // "
            u(1, 3, 1, false, 0x201E), // „
            u(1, 3, 2, false, 0x2039), // ‹
            u(1, 3, 3, false, 0x203A), // ›
            u(1, 3, 4, false, 0x00AB), // «
            u(1, 3, 5, false, 0x00BB), // »
            // Page 1 row 3 fn — bullets, daggers, primes
            u(1, 3, 0, true, 0x2022), // •
            u(1, 3, 1, true, 0x2026), // …
            u(1, 3, 2, true, 0x2020), // †
            u(1, 3, 3, true, 0x2021), // ‡
            u(1, 3, 4, true, 0x2032), // ′
            u(1, 3, 5, true, 0x2033), // ″
            // Page 2 row 2 base — cols 0–1 reserved ASCII; cols 2–6 ordinals + macron + perthousand + ※
            parenBase(0, "(", 0x26),
            parenBase(1, ")", 0x27),
            u(2, 2, 2, false, 0x00AA), // ª
            u(2, 2, 3, false, 0x00BA), // º
            u(2, 2, 4, false, 0x00AF), // ¯
            u(2, 2, 5, false, 0x2030), // ‰
            u(2, 2, 6, false, 0x203B), // ※
            // Page 2 row 2 fn — cols 0–1 reserved ASCII; cols 2–6 rare punctuation
            graveTildeFn(0, "`", MOD_NONE),
            graveTildeFn(1, "~", MOD_SHIFT),
            u(2, 2, 2, true, 0x203C), // ‼
            u(2, 2, 3, true, 0x2042), // ⁂
            u(2, 2, 4, true, 0x204B), // ⁋
            u(2, 2, 5, true, 0x212B), // Å
            u(2, 2, 6, true, 0x2031), // ‱
            // Page 2 row 3 base — checkboxes
            u(2, 3, 0, false, 0x2713), // ✓
            u(2, 3, 1, false, 0x2717), // ✗
            u(2, 3, 2, false, 0x2613), // ☓
            u(2, 3, 3, false, 0x2611), // ☑
            u(2, 3, 4, false, 0x2612), // ☒
            u(2, 3, 5, false, 0x2610), // ☐
            // Page 2 row 3 fn — decorative
            u(2, 3, 0, true, 0x2726), // ✦
            u(2, 3, 1, true, 0x2727), // ✧
            u(2, 3, 2, true, 0x2756), // ❖
            u(2, 3, 3, true, 0x2740), // ❀
            u(2, 3, 4, true, 0x263C), // ☼
            u(2, 3, 5, true, 0x2605), // ★
    };
}
