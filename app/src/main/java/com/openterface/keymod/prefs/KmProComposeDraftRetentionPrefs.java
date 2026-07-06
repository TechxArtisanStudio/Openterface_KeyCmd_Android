package com.openterface.keymod.prefs;

/**
 * When on (default), KM Pro embedded Compose keeps the long-text buffer in memory while you switch
 * to Keyboard or NumPad (or other paths that remove the compose fragment).
 *
 * <p>This preference has been removed from the UI; the buffer is always retained.
 */
public final class KmProComposeDraftRetentionPrefs {

    private KmProComposeDraftRetentionPrefs() {}

    /** Compose draft retention is always enabled. */
    public static boolean read(android.content.Context context) {
        return true;
    }
}
