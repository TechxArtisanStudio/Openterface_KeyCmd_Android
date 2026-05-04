package com.openterface.keymod.preset;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.R;
import com.openterface.keymod.util.KeyParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-key HID choices for Rows 2–3 strip slot editor (no Win/Ctrl/Alt/Cmd combos; Shift alone is
 * allowed for shifted symbols).
 */
public final class HidKeyCatalog {

    public static final class Choice {
        public final int keyCode;
        /** {@code 0} or {@code 0x02} (Shift) only. */
        public final int modifiers;
        @Nullable
        public final String labelOverride;

        public Choice(int keyCode, int modifiers, @Nullable String labelOverride) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.labelOverride = labelOverride;
        }
    }

    public static final class Section {
        public final int titleRes;
        @NonNull
        public final List<Choice> choices;

        public Section(int titleRes, @NonNull List<Choice> choices) {
            this.titleRes = titleRes;
            this.choices = choices;
        }
    }

    private HidKeyCatalog() {
    }

    @NonNull
    public static String formatChoiceLabel(
            @NonNull Context ctx,
            int keyCode,
            int modifiers,
            @NonNull String targetOs,
            @Nullable String labelOverride
    ) {
        if (labelOverride != null && !labelOverride.isEmpty()) {
            return labelOverride;
        }
        int m = (modifiers & 0x02) != 0 ? 0x02 : 0;
        String raw = KeyParser.toLabelForTargetOs(keyCode, m, targetOs);
        if (raw != null && !raw.startsWith("Key ")) {
            return KeyParser.displayLabel(raw, targetOs);
        }
        return fallbackLabel(ctx, keyCode);
    }

    @NonNull
    private static String fallbackLabel(@NonNull Context ctx, int keyCode) {
        switch (keyCode) {
            case 0xE0:
                return ctx.getString(R.string.rows23_hid_lctrl);
            case 0xE1:
                return ctx.getString(R.string.rows23_hid_lshift);
            case 0xE2:
                return ctx.getString(R.string.rows23_hid_lalt);
            case 0xE3:
                return ctx.getString(R.string.rows23_hid_lgui);
            case 0xE4:
                return ctx.getString(R.string.rows23_hid_rctrl);
            case 0xE5:
                return ctx.getString(R.string.rows23_hid_rshift);
            case 0xE6:
                return ctx.getString(R.string.rows23_hid_ralt);
            case 0xE7:
                return ctx.getString(R.string.rows23_hid_rgui);
            case 0xF00C:
                return ctx.getString(R.string.rows23_hid_local_fn);
            case 0xF00A:
                return ctx.getString(R.string.rows23_hid_ph1);
            case 102:
                return ctx.getString(R.string.rows23_hid_power);
            case 116:
                return ctx.getString(R.string.rows23_hid_execute);
            case 117:
                return ctx.getString(R.string.rows23_hid_help);
            case 118:
                return ctx.getString(R.string.rows23_hid_menu);
            case 119:
                return ctx.getString(R.string.rows23_hid_select);
            case 120:
                return ctx.getString(R.string.rows23_hid_stop);
            case 121:
                return ctx.getString(R.string.rows23_hid_again);
            case 122:
                return ctx.getString(R.string.rows23_hid_undo);
            case 123:
                return ctx.getString(R.string.rows23_hid_cut);
            case 124:
                return ctx.getString(R.string.rows23_hid_copy);
            case 125:
                return ctx.getString(R.string.rows23_hid_paste);
            case 126:
                return ctx.getString(R.string.rows23_hid_find);
            default:
                if (keyCode >= 104 && keyCode <= 115) {
                    return "F" + (keyCode - 104 + 13);
                }
                if (keyCode >= 89 && keyCode <= 97) {
                    return ctx.getString(R.string.rows23_hid_numpad_digit, keyCode - 89 + 1);
                }
                if (keyCode == 98) {
                    return ctx.getString(R.string.rows23_hid_numpad_0);
                }
                if (keyCode == 99) {
                    return ctx.getString(R.string.rows23_hid_numpad_dot);
                }
                if (keyCode == 103) {
                    return ctx.getString(R.string.rows23_hid_numpad_equals);
                }
                return ctx.getString(R.string.rows23_hid_unknown, keyCode);
        }
    }

    /** Allowed modifier mask for strip single-key save: none or Shift only. */
    public static int normalizeStripModifiers(int modifiers) {
        return (modifiers & 0x02) != 0 ? 0x02 : 0;
    }

    public static boolean isSameChoice(int k1, int m1, int k2, int m2) {
        return k1 == k2 && normalizeStripModifiers(m1) == normalizeStripModifiers(m2);
    }

    @NonNull
    public static List<Section> buildSections() {
        List<Section> out = new ArrayList<>();

        List<Choice> letters = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            char c = (char) ('A' + i);
            letters.add(new Choice(4 + i, 0, String.valueOf(c)));
        }
        out.add(new Section(R.string.rows23_hid_group_letters, letters));

        List<Choice> digits = new ArrayList<>(10);
        for (int i = 0; i < 9; i++) {
            digits.add(new Choice(30 + i, 0, String.valueOf((char) ('1' + i))));
        }
        digits.add(new Choice(39, 0, "0"));
        out.add(new Section(R.string.rows23_hid_group_digits, digits));

        List<Choice> punct = new ArrayList<>();
        addPunct(punct);
        out.add(new Section(R.string.rows23_hid_group_punctuation, punct));

        List<Choice> fn = new ArrayList<>(24);
        for (int f = 1; f <= 12; f++) {
            fn.add(new Choice(57 + f, 0, "F" + f));
        }
        for (int f = 13; f <= 24; f++) {
            fn.add(new Choice(104 + (f - 13), 0, "F" + f));
        }
        out.add(new Section(R.string.rows23_hid_group_function, fn));

        List<Choice> nav = new ArrayList<>();
        nav.add(new Choice(41, 0, null));
        nav.add(new Choice(43, 0, null));
        nav.add(new Choice(40, 0, null));
        nav.add(new Choice(42, 0, null));
        nav.add(new Choice(44, 0, null));
        nav.add(new Choice(73, 0, null));
        nav.add(new Choice(74, 0, null));
        nav.add(new Choice(75, 0, null));
        nav.add(new Choice(76, 0, null));
        nav.add(new Choice(77, 0, null));
        nav.add(new Choice(78, 0, null));
        nav.add(new Choice(79, 0, null));
        nav.add(new Choice(80, 0, null));
        nav.add(new Choice(81, 0, null));
        nav.add(new Choice(82, 0, null));
        nav.add(new Choice(70, 0, null));
        nav.add(new Choice(71, 0, null));
        nav.add(new Choice(72, 0, null));
        nav.add(new Choice(101, 0, null));
        out.add(new Section(R.string.rows23_hid_group_navigation, nav));

        List<Choice> np = new ArrayList<>();
        np.add(new Choice(83, 0, null));
        np.add(new Choice(84, 0, null));
        np.add(new Choice(85, 0, null));
        np.add(new Choice(86, 0, null));
        np.add(new Choice(87, 0, null));
        np.add(new Choice(88, 0, null));
        for (int k = 89; k <= 97; k++) {
            np.add(new Choice(k, 0, null));
        }
        np.add(new Choice(98, 0, null));
        np.add(new Choice(99, 0, null));
        np.add(new Choice(103, 0, null));
        out.add(new Section(R.string.rows23_hid_group_numpad, np));

        List<Choice> mods = new ArrayList<>();
        for (int k = 0xE0; k <= 0xE7; k++) {
            mods.add(new Choice(k, 0, null));
        }
        out.add(new Section(R.string.rows23_hid_group_modifiers_as_key, mods));

        List<Choice> locks = new ArrayList<>();
        locks.add(new Choice(57, 0, null));
        locks.add(new Choice(71, 0, null));
        out.add(new Section(R.string.rows23_hid_group_locks, locks));

        List<Choice> doc = new ArrayList<>();
        for (int k = 116; k <= 126; k++) {
            doc.add(new Choice(k, 0, null));
        }
        doc.add(new Choice(102, 0, null));
        out.add(new Section(R.string.rows23_hid_group_document, doc));

        List<Choice> strip = new ArrayList<>();
        strip.add(new Choice(0xF00C, 0, null));
        strip.add(new Choice(0xF00A, 0, null));
        out.add(new Section(R.string.rows23_hid_group_strip_special, strip));

        return out;
    }

    private static void addPunct(@NonNull List<Choice> punct) {
        punct.add(new Choice(45, 0, "-"));
        punct.add(new Choice(45, 0x02, "_"));
        punct.add(new Choice(46, 0, "="));
        punct.add(new Choice(46, 0x02, "+"));
        punct.add(new Choice(47, 0, "["));
        punct.add(new Choice(47, 0x02, "{"));
        punct.add(new Choice(48, 0, "]"));
        punct.add(new Choice(48, 0x02, "}"));
        punct.add(new Choice(49, 0, "\\"));
        punct.add(new Choice(49, 0x02, "|"));
        punct.add(new Choice(51, 0, ";"));
        punct.add(new Choice(51, 0x02, ":"));
        punct.add(new Choice(52, 0, "'"));
        punct.add(new Choice(52, 0x02, "\""));
        punct.add(new Choice(53, 0, "`"));
        punct.add(new Choice(53, 0x02, "~"));
        punct.add(new Choice(54, 0, ","));
        punct.add(new Choice(54, 0x02, "<"));
        punct.add(new Choice(55, 0, "."));
        punct.add(new Choice(55, 0x02, ">"));
        punct.add(new Choice(56, 0, "/"));
        punct.add(new Choice(56, 0x02, "?"));
        punct.add(new Choice(30, 0x02, "!"));
        punct.add(new Choice(31, 0x02, "@"));
        punct.add(new Choice(32, 0x02, "#"));
        punct.add(new Choice(33, 0x02, "$"));
        punct.add(new Choice(34, 0x02, "%"));
        punct.add(new Choice(35, 0x02, "^"));
        punct.add(new Choice(36, 0x02, "&"));
        punct.add(new Choice(37, 0x02, "*"));
        punct.add(new Choice(38, 0x02, "("));
        punct.add(new Choice(39, 0x02, ")"));
    }

    @NonNull
    public static List<Choice> allChoicesFlat() {
        List<Choice> flat = new ArrayList<>();
        for (Section s : buildSections()) {
            flat.addAll(s.choices);
        }
        return flat;
    }

    @Nullable
    public static Choice findMatchingChoice(int keyCode, int modifiers) {
        int m = normalizeStripModifiers(modifiers);
        for (Choice c : allChoicesFlat()) {
            if (c.keyCode == keyCode && normalizeStripModifiers(c.modifiers) == m) {
                return c;
            }
        }
        return null;
    }
}
