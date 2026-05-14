package com.openterface.keymod.util;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.R;

/**
 * Shared rules for IME / compose buffered HID send (same as Pro sub-compose in {@code CustomKeyboardView}).
 */
public final class ImeComposeSendGate {

    public static final int LONG_TEXT_WARNING_THRESHOLD = 300;

    private ImeComposeSendGate() {}

    public static final class WarningInfo {
        public final boolean hasNonAscii;
        public final boolean hasLengthRisk;
        public final int charCount;

        public WarningInfo(boolean hasNonAscii, boolean hasLengthRisk, int charCount) {
            this.hasNonAscii = hasNonAscii;
            this.hasLengthRisk = hasLengthRisk;
            this.charCount = charCount;
        }
    }

    public static final class SendAssessment {
        @Nullable public final Integer hardBlockReasonResId;
        @Nullable public final WarningInfo warningInfo;

        public SendAssessment(
                @Nullable Integer hardBlockReasonResId, @Nullable WarningInfo warningInfo) {
            this.hardBlockReasonResId = hardBlockReasonResId;
            this.warningInfo = warningInfo;
        }

        public boolean canSend() {
            return hardBlockReasonResId == null;
        }
    }

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

    public static boolean textLengthHasRisk(@Nullable String s) {
        return s != null && s.length() >= LONG_TEXT_WARNING_THRESHOLD;
    }

    @Nullable
    public static WarningInfo buildWarningInfo(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        boolean hasNonAscii = textContainsNonAscii(text);
        boolean hasLengthRisk = textLengthHasRisk(text);
        if (!hasNonAscii && !hasLengthRisk) {
            return null;
        }
        return new WarningInfo(hasNonAscii, hasLengthRisk, text.length());
    }

    /**
     * @return a string resource id to show when send is blocked, or {@code null} when send is allowed.
     */
    @Nullable
    @StringRes
    public static Integer resolveSendBlockedReasonResId(
            @Nullable ConnectionManager connectionManager, @Nullable String text) {
        return assess(connectionManager, text).hardBlockReasonResId;
    }

    @NonNull
    public static SendAssessment assess(
            @Nullable ConnectionManager connectionManager, @Nullable String text) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return new SendAssessment(R.string.compose_no_connection, null);
        }
        if (text == null || text.isEmpty()) {
            return new SendAssessment(R.string.compose_empty, null);
        }
        return new SendAssessment(null, buildWarningInfo(text));
    }
}
