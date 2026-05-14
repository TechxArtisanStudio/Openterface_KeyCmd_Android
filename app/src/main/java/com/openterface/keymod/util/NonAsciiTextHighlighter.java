package com.openterface.keymod.util;

import android.text.Editable;
import android.text.style.BackgroundColorSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Applies removable highlight spans over non-ASCII code points in an {@link Editable}.
 */
public final class NonAsciiTextHighlighter {

    private NonAsciiTextHighlighter() {}

    private static final class NonAsciiHighlightSpan extends BackgroundColorSpan {
        NonAsciiHighlightSpan(@ColorInt int color) {
            super(color);
        }
    }

    public static int apply(@NonNull Editable editable, @ColorInt int highlightColor) {
        clear(editable);
        int highlighted = 0;
        for (int i = 0; i < editable.length(); ) {
            int cp = Character.codePointAt(editable, i);
            int next = i + Character.charCount(cp);
            if (cp > 127) {
                editable.setSpan(
                        new NonAsciiHighlightSpan(highlightColor),
                        i,
                        next,
                        Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
                highlighted++;
            }
            i = next;
        }
        return highlighted;
    }

    public static void clear(@NonNull Editable editable) {
        NonAsciiHighlightSpan[] spans =
                editable.getSpans(0, editable.length(), NonAsciiHighlightSpan.class);
        for (NonAsciiHighlightSpan span : spans) {
            editable.removeSpan(span);
        }
    }
}
