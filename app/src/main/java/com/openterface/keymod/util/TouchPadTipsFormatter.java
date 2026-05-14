package com.openterface.keymod.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.prefs.KmProTouchpadPrefs;

/**
 * Rich text for the on-pad status line and touchpad help dialog content.
 */
public final class TouchPadTipsFormatter {

    /** Title + message for {@link TouchPadHelpDialog}; title may be null when compact. */
    public static final class HelpDialogParts {
        @Nullable public final CharSequence title;
        public final CharSequence message;

        public HelpDialogParts(@Nullable CharSequence title, CharSequence message) {
            this.title = title;
            this.message = message;
        }
    }

    private TouchPadTipsFormatter() {}

    /**
     * Three-line status: title, logical mouse buttons / drag lock, and live touch surface activity.
     */
    public static CharSequence buildCompact(Context context, boolean dragModeOn, TouchPadPointerPhase pointerPhase) {
        return buildCompact(context, dragModeOn, pointerPhase, true);
    }

    /**
     * @param includeTouchpadTitle when false, omits the leading title line ({@code touch_pad_title}).
     */
    public static CharSequence buildCompact(
            Context context,
            boolean dragModeOn,
            TouchPadPointerPhase pointerPhase,
            boolean includeTouchpadTitle) {
        String title = context.getString(R.string.touch_pad_title);
        String mouse = context.getString(
                dragModeOn ? R.string.touch_pad_status_buttons_drag : R.string.touch_pad_status_buttons_up);
        String touch;
        switch (pointerPhase) {
            case MOVE:
                touch = context.getString(R.string.touch_pad_status_touch_move);
                break;
            case SCROLL:
                touch = context.getString(R.string.touch_pad_status_touch_scroll);
                break;
            default:
                touch = context.getString(R.string.touch_pad_status_touch_idle);
                break;
        }

        String full =
                includeTouchpadTitle
                        ? title + "\n" + mouse + "\n" + touch
                        : mouse + "\n" + touch;
        SpannableStringBuilder ssb = new SpannableStringBuilder(full);
        int primary = ContextCompat.getColor(context, R.color.text_primary);
        int secondary = ContextCompat.getColor(context, R.color.text_secondary);
        int accent = ThemeManager.getColorPrimary(context);

        int mouseStart;
        int mouseEnd;
        if (includeTouchpadTitle) {
            int titleEnd = title.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new RelativeSizeSpan(1.08f), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            mouseStart = titleEnd + 1;
            mouseEnd = mouseStart + mouse.length();
        } else {
            mouseStart = 0;
            mouseEnd = mouse.length();
        }

        ssb.setSpan(new StyleSpan(Typeface.BOLD), mouseStart, mouseEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(
                new ForegroundColorSpan(dragModeOn ? accent : secondary),
                mouseStart,
                mouseEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int touchStart = mouseEnd + 1;
        int touchEnd = touchStart + touch.length();
        ssb.setSpan(
                new ForegroundColorSpan(secondary),
                touchStart,
                touchEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }

    /** Plain full message (no spans); useful for tests or sharing. */
    public static String buildGestureHelpMessage(Context context) {
        String title = context.getString(R.string.touch_pad_help_overlay_title);
        String one = context.getString(R.string.touch_pad_help_overlay_one_finger);
        String oneD = context.getString(R.string.touch_pad_help_overlay_one_finger_detail);
        String two = context.getString(R.string.touch_pad_help_overlay_two_fingers);
        String twoD = context.getString(R.string.touch_pad_help_overlay_two_fingers_detail);
        return title + "\n\n" + one + "\n" + oneD + "\n\n" + two + "\n" + twoD;
    }

    /** Title + body: bold title, bold “One finger” / “Two fingers” lines; body uses theme primary color. */
    public static CharSequence buildGestureHelpOverlayText(Context context) {
        return buildGestureHelpOverlayText(context, true);
    }

    /**
     * @param includeMainTitle when false, omits {@code touch_pad_help_overlay_title} so the block fits a small
     *     touchpad dialog.
     */
    public static CharSequence buildGestureHelpOverlayText(Context context, boolean includeMainTitle) {
        String title = context.getString(R.string.touch_pad_help_overlay_title);
        String one = context.getString(R.string.touch_pad_help_overlay_one_finger);
        String oneD = context.getString(R.string.touch_pad_help_overlay_one_finger_detail);
        String two = context.getString(R.string.touch_pad_help_overlay_two_fingers);
        String twoD = context.getString(R.string.touch_pad_help_overlay_two_fingers_detail);

        String full =
                includeMainTitle
                        ? title + "\n\n" + one + "\n" + oneD + "\n\n" + two + "\n" + twoD
                        : one + "\n" + oneD + "\n\n" + two + "\n" + twoD;
        SpannableStringBuilder ssb = new SpannableStringBuilder(full);
        int primary = ContextCompat.getColor(context, R.color.text_primary);

        if (includeMainTitle) {
            int titleEnd = title.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int oneStart = titleEnd + 2;
            int oneEnd = oneStart + one.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int twoStart = oneEnd + 1 + oneD.length() + 2;
            int twoEnd = twoStart + two.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            int oneStart = 0;
            int oneEnd = one.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int twoStart = oneEnd + 1 + oneD.length() + 2;
            int twoEnd = twoStart + two.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return ssb;
    }

    /**
     * Parts for a Material help dialog: optional dialog title line plus styled gesture body (no
     * duplicate title in the message when {@code includeMainTitle} is true).
     */
    public static HelpDialogParts buildGestureHelpDialogParts(Context context, boolean includeMainTitle) {
        CharSequence message = buildGestureHelpOverlayText(context, false);
        if (includeMainTitle) {
            return new HelpDialogParts(context.getString(R.string.touch_pad_help_overlay_title), message);
        }
        return new HelpDialogParts(null, message);
    }

    /**
     * Help text for the KM Pro composite touchpad info icon: copy depends on {@link
     * KmProTouchpadPrefs#readMode}.
     */
    public static CharSequence buildKmProCompositeHelpOverlayText(Context context, boolean includeMainTitle) {
        int mode = KmProTouchpadPrefs.readMode(context);
        final String title;
        final String body;
        if (mode == KmProTouchpadPrefs.MODE_MOUSE_KEYS_BASIC) {
            title = context.getString(R.string.touch_pad_help_km_pad_keys_title);
            body = context.getString(R.string.touch_pad_help_km_pad_keys_body);
        } else if (mode == KmProTouchpadPrefs.MODE_HYBRID) {
            title = context.getString(R.string.touch_pad_help_km_hybrid_title);
            body = context.getString(R.string.touch_pad_help_km_hybrid_body);
        } else {
            title = context.getString(R.string.touch_pad_help_km_gestures_only_title);
            body = context.getString(R.string.touch_pad_help_km_gestures_only_body);
        }

        String full = includeMainTitle ? title + "\n\n" + body : body;
        SpannableStringBuilder ssb = new SpannableStringBuilder(full);
        int primary = ContextCompat.getColor(context, R.color.text_primary);
        if (includeMainTitle) {
            int titleEnd = title.length();
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(primary), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    /** KM Pro mode title + body for a Material dialog (no duplicate title in message). */
    public static HelpDialogParts buildKmProCompositeHelpDialogParts(Context context, boolean includeMainTitle) {
        int mode = KmProTouchpadPrefs.readMode(context);
        final String title;
        final String body;
        if (mode == KmProTouchpadPrefs.MODE_MOUSE_KEYS_BASIC) {
            title = context.getString(R.string.touch_pad_help_km_pad_keys_title);
            body = context.getString(R.string.touch_pad_help_km_pad_keys_body);
        } else if (mode == KmProTouchpadPrefs.MODE_HYBRID) {
            title = context.getString(R.string.touch_pad_help_km_hybrid_title);
            body = context.getString(R.string.touch_pad_help_km_hybrid_body);
        } else {
            title = context.getString(R.string.touch_pad_help_km_gestures_only_title);
            body = context.getString(R.string.touch_pad_help_km_gestures_only_body);
        }
        if (includeMainTitle) {
            return new HelpDialogParts(title, body);
        }
        return new HelpDialogParts(null, body);
    }
}
