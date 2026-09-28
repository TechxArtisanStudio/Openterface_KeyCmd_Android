package com.openterface.keymod.util;

import android.app.Activity;
import android.view.Display;

import androidx.annotation.NonNull;

/**
 * Display-related API-level compat helpers.
 */
public final class DisplayCompat {

    private DisplayCompat() {}

    /**
     * Returns the rotation of the display, handling the API 30+ deprecation
     * of {@code getDefaultDisplay()} in favor of {@code Activity.getDisplay()}.
     */
    public static int getRotation(@NonNull Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Display display = activity.getDisplay();
            if (display != null) {
                return display.getRotation();
            }
        }
        return activity.getWindowManager().getDefaultDisplay().getRotation();
    }
}
