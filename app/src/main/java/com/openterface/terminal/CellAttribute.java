package com.openterface.terminal;

import android.graphics.Color;

/**
 * Stores per-cell text attributes: colors and styles.
 */
public class CellAttribute {

    // Standard 8 ANSI colors
    public static final int COLOR_BLACK   = 0xFF000000;
    public static final int COLOR_RED     = 0xFFCC0000;
    public static final int COLOR_GREEN   = 0xFF00CC00;
    public static final int COLOR_YELLOW  = 0xFFCCCC00;
    public static final int COLOR_BLUE    = 0xFF0000CC;
    public static final int COLOR_MAGENTA = 0xFFCC00CC;
    public static final int COLOR_CYAN    = 0xFF00CCCC;
    public static final int COLOR_WHITE   = 0xFFCCCCCC;

    // Bright variants
    public static final int COLOR_BRIGHT_BLACK   = 0xFF555555;
    public static final int COLOR_BRIGHT_RED     = 0xFFFF5555;
    public static final int COLOR_BRIGHT_GREEN   = 0xFF55FF55;
    public static final int COLOR_BRIGHT_YELLOW  = 0xFFFFFF55;
    public static final int COLOR_BRIGHT_BLUE    = 0xFF5555FF;
    public static final int COLOR_BRIGHT_MAGENTA = 0xFFFF55FF;
    public static final int COLOR_BRIGHT_CYAN    = 0xFF55FFFF;
    public static final int COLOR_BRIGHT_WHITE   = 0xFFFFFFFF;

    public static final int DEFAULT_FG = COLOR_WHITE;
    public static final int DEFAULT_BG = COLOR_BLACK;

    public static final CellAttribute DEFAULT = new CellAttribute();

    public int fgColor;
    public int bgColor;
    public boolean bold;
    public boolean italic;
    public boolean underline;
    public boolean inverse;
    public boolean blink;

    public CellAttribute() {
        reset();
    }

    public CellAttribute(CellAttribute other) {
        copyFrom(other);
    }

    public void reset() {
        fgColor = DEFAULT_FG;
        bgColor = DEFAULT_BG;
        bold = false;
        italic = false;
        underline = false;
        inverse = false;
        blink = false;
    }

    public void copyFrom(CellAttribute other) {
        this.fgColor = other.fgColor;
        this.bgColor = other.bgColor;
        this.bold = other.bold;
        this.italic = other.italic;
        this.underline = other.underline;
        this.inverse = other.inverse;
        this.blink = other.blink;
    }

    public CellAttribute copy() {
        return new CellAttribute(this);
    }

    public boolean isDefault() {
        return fgColor == DEFAULT_FG
            && bgColor == DEFAULT_BG
            && !bold && !italic && !underline && !inverse && !blink;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CellAttribute)) return false;
        CellAttribute o = (CellAttribute) obj;
        return fgColor == o.fgColor && bgColor == o.bgColor
            && bold == o.bold && italic == o.italic
            && underline == o.underline && inverse == o.inverse
            && blink == o.blink;
    }

    @Override
    public int hashCode() {
        int h = fgColor;
        h = 31 * h + bgColor;
        h = 31 * h + (bold ? 1 : 0);
        h = 31 * h + (italic ? 1 : 0);
        h = 31 * h + (underline ? 1 : 0);
        h = 31 * h + (inverse ? 1 : 0);
        h = 31 * h + (blink ? 1 : 0);
        return h;
    }

    /**
     * Build a 256-color palette color from an index (0-255).
     * 0-7: standard, 8-15: bright, 16-231: 6x6x6 cube, 232-255: grayscale.
     */
    public static int color256(int index) {
        if (index < 0) index = 0;
        if (index > 255) index = 255;

        if (index < 16) {
            return STANDARD_16[index];
        } else if (index < 232) {
            // 6x6x6 color cube
            int n = index - 16;
            int b = n % 6; n /= 6;
            int g = n % 6; n /= 6;
            int r = n;
            int r8 = r == 0 ? 0 : 55 + r * 40;
            int g8 = g == 0 ? 0 : 55 + g * 40;
            int b8 = b == 0 ? 0 : 55 + b * 40;
            return 0xFF000000 | (r8 << 16) | (g8 << 8) | b8;
        } else {
            // Grayscale 232-255 → 8 to 238 step 10
            int v = 8 + (index - 232) * 10;
            return 0xFF000000 | (v << 16) | (v << 8) | v;
        }
    }

    private static final int[] STANDARD_16 = {
        COLOR_BLACK, COLOR_RED, COLOR_GREEN, COLOR_YELLOW,
        COLOR_BLUE, COLOR_MAGENTA, COLOR_CYAN, COLOR_WHITE,
        COLOR_BRIGHT_BLACK, COLOR_BRIGHT_RED, COLOR_BRIGHT_GREEN, COLOR_BRIGHT_YELLOW,
        COLOR_BRIGHT_BLUE, COLOR_BRIGHT_MAGENTA, COLOR_BRIGHT_CYAN, COLOR_BRIGHT_WHITE
    };
}
