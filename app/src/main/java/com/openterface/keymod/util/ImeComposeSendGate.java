package com.openterface.keymod.util;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.R;

/**
 * Shared rules for IME / compose buffered HID send (same as Pro sub-compose in {@code CustomKeyboardView}).
 */
public final class ImeComposeSendGate {

    private ImeComposeSendGate() {}

    /**
     * Same predicate as legacy {@code CustomKeyboardView#imeCaptureTextContainsNonAscii}: any code point
     * {@code > 127} blocks send (compose path uses {@code HidTextKeystrokeSender} with {@code allowUnicode=false}).
     */
    public static boolean textContainsNonAscii(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp > 127) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * @return a string resource id to show when send is blocked, or {@code null} when send is allowed.
     */
    @Nullable
    @StringRes
    public static Integer resolveSendBlockedReasonResId(
            @Nullable ConnectionManager connectionManager, @Nullable String text) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return R.string.compose_no_connection;
        }
        if (text == null || text.isEmpty()) {
            return R.string.compose_empty;
        }
        if (textContainsNonAscii(text)) {
            return R.string.compose_ascii_warning;
        }
        return null;
    }
}
