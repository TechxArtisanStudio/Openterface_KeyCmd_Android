package com.openterface.keymod.preset;

import androidx.annotation.NonNull;

import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Factory content for the two non-deletable Rows 2–3 strip built-ins ("Symbols ★" and "Math ∑").
 *
 * <p>Each builder fills every customizable strip slot across pages 0/1/2 (base + Fn layers) so the
 * Shortcut Hub picker presents a complete, distinct strip skin. Slot HIDs match the factory action
 * for "Symbols ★" (visual swap only); "Math ∑" rebinds {@code page 0 row 2 base} to shifted digits
 * so the displayed glyph reflects what is typed and keeps factory HIDs elsewhere.
 *
 * <p>Modifiers stay within {@link HidKeyCatalog#normalizeStripModifiers} (none or Shift only).
 * Glyphs render via the existing {@code customIconGlyph} path in {@code CustomKeyboardView}
 * (any non-alphanumeric icon is treated as an emoji glyph by {@code isEmojiIcon}).
 */
public final class Rows23StripProfileBuiltins {

    private static final int MOD_NONE = 0;
    private static final int MOD_SHIFT = 0x02;

    private static final String SYMBOLS_ID_PREFIX = "builtin_symbols_";
    private static final String MATH_ID_PREFIX = "builtin_math_";

    private Rows23StripProfileBuiltins() {
    }

    @NonNull
    public static Rows23StripProfile buildSymbolsProfile() {
        Rows23StripProfile p = newProfileShell(
                Rows23StripProfileConstants.SYMBOLS_PROFILE_ID, "Symbols ★");
        int order = 0;
        for (SlotSpec spec : SYMBOLS_SPECS) {
            order = appendSlot(p, SYMBOLS_ID_PREFIX, spec, order);
        }
        return p;
    }

    @NonNull
    public static Rows23StripProfile buildMathProfile() {
        Rows23StripProfile p = newProfileShell(
                Rows23StripProfileConstants.MATH_PROFILE_ID, "Math ∑");
        int order = 0;
        for (SlotSpec spec : MATH_SPECS) {
            order = appendSlot(p, MATH_ID_PREFIX, spec, order);
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

        SlotSpec(int page, int row, int col, boolean fnLayer,
                 @NonNull String glyph, int keyCode, int modifiers) {
            this.page = page;
            this.row = row;
            this.col = col;
            this.fnLayer = fnLayer;
            this.glyph = glyph;
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }
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
            // Page 2 row 2 base — still Shift+9 / Shift+0 (same HID as "(" / ")"); use non-ASCII display
            // glyphs so strip rendering uses the same centered-glyph autosize path as other slots.
            new SlotSpec(2, 2, 0, false, "\u2985", 0x26, MOD_SHIFT), // BLACK TORTOISE SHELL BRACKET
            new SlotSpec(2, 2, 1, false, "\u2986", 0x27, MOD_SHIFT),
            new SlotSpec(2, 2, 2, false, "◈", 0x2F, MOD_NONE),
            new SlotSpec(2, 2, 3, false, "◉", 0x30, MOD_NONE),
            new SlotSpec(2, 2, 4, false, "◎", 0x33, MOD_SHIFT),
            new SlotSpec(2, 2, 5, false, "●", 0x20, MOD_SHIFT),
            new SlotSpec(2, 2, 6, false, "○", 0x1F, MOD_SHIFT),
            // Page 2 row 2 fn — same grave/tilde HID as base (strip overlay reads f-p… for these cells)
            new SlotSpec(2, 2, 0, true, "`", 0x35, MOD_NONE),
            new SlotSpec(2, 2, 1, true, "~", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 2, true, "'", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 3, true, "▽", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 4, true, "◀", 0x34, MOD_SHIFT),
            new SlotSpec(2, 2, 5, true, "◁", 0x22, MOD_SHIFT),
            new SlotSpec(2, 2, 6, true, "▶", 0x23, MOD_SHIFT),
            // Page 2 row 3 base — / \ | ? - _
            new SlotSpec(2, 3, 0, false, "♬", 0x38, MOD_NONE),
            new SlotSpec(2, 3, 1, false, "♪", 0x31, MOD_NONE),
            new SlotSpec(2, 3, 2, false, "♫", 0x64, MOD_NONE),
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
            // Page 2 row 2 base — same HID as "(" / ")"; mathematical angle brackets for display only.
            new SlotSpec(2, 2, 0, false, "\u27E8", 0x26, MOD_SHIFT),
            new SlotSpec(2, 2, 1, false, "\u27E9", 0x27, MOD_SHIFT),
            new SlotSpec(2, 2, 2, false, "≡", 0x2F, MOD_NONE),
            new SlotSpec(2, 2, 3, false, "⋮", 0x30, MOD_NONE),
            new SlotSpec(2, 2, 4, false, "⋯", 0x33, MOD_SHIFT),
            new SlotSpec(2, 2, 5, false, "⋰", 0x20, MOD_SHIFT),
            new SlotSpec(2, 2, 6, false, "⋱", 0x1F, MOD_SHIFT),
            // Page 2 row 2 fn — grave/tilde (match factory page 2 row 2 cols 0–1)
            new SlotSpec(2, 2, 0, true, "`", 0x35, MOD_NONE),
            new SlotSpec(2, 2, 1, true, "~", 0x35, MOD_SHIFT),
            new SlotSpec(2, 2, 2, true, "'", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 3, true, "◇", 0x34, MOD_NONE),
            new SlotSpec(2, 2, 4, true, "◈", 0x34, MOD_SHIFT),
            new SlotSpec(2, 2, 5, true, "⊞", 0x22, MOD_SHIFT),
            new SlotSpec(2, 2, 6, true, "⊟", 0x23, MOD_SHIFT),
            // Page 2 row 3 base — / \ | ? - _ (relations)
            new SlotSpec(2, 3, 0, false, "∝", 0x38, MOD_NONE),
            new SlotSpec(2, 3, 1, false, "∞", 0x31, MOD_NONE),
            new SlotSpec(2, 3, 2, false, "⊥", 0x64, MOD_NONE),
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
}
