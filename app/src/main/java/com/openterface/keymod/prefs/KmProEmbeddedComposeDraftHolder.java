package com.openterface.keymod.prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * In-process draft for {@code BasicComposeFragment} when embedded in KM Pro. Not persisted to disk.
 */
public final class KmProEmbeddedComposeDraftHolder {

    private static final Object LOCK = new Object();

    private static boolean hasDraft;
    private static String editorText = "";
    @Nullable private static String undoSnapshot;
    private static boolean highlightNonAscii;

    private KmProEmbeddedComposeDraftHolder() {}

    /** Clears any stored draft (e.g. when the user turns retention off in settings). */
    public static void clear() {
        synchronized (LOCK) {
            hasDraft = false;
            editorText = "";
            undoSnapshot = null;
            highlightNonAscii = false;
        }
    }

    /**
     * Saves the current embedded compose buffer. Call from {@code BasicComposeFragment} when draft
     * retention is enabled (typically {@code onPause}).
     */
    public static void saveFromEmbeddedEditor(
            @NonNull String text, @Nullable String undoSnapshotParam, boolean highlightNonAsciiParam) {
        synchronized (LOCK) {
            hasDraft = true;
            editorText = text;
            undoSnapshot = undoSnapshotParam;
            highlightNonAscii = highlightNonAsciiParam;
        }
    }

    /**
     * Returns and clears the stored draft, or {@code null} if none was saved since last consume/clear.
     */
    @Nullable
    public static Snapshot consumeDraft() {
        synchronized (LOCK) {
            if (!hasDraft) {
                return null;
            }
            hasDraft = false;
            return new Snapshot(editorText, undoSnapshot, highlightNonAscii);
        }
    }

    public static final class Snapshot {
        @NonNull public final String editorText;
        @Nullable public final String undoSnapshot;
        public final boolean highlightNonAscii;

        Snapshot(@Nullable String editorText, @Nullable String undoSnapshot, boolean highlightNonAscii) {
            this.editorText = editorText != null ? editorText : "";
            this.undoSnapshot = undoSnapshot;
            this.highlightNonAscii = highlightNonAscii;
        }
    }
}
