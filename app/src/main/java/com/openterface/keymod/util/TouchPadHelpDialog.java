package com.openterface.keymod.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shows touchpad gesture / KM Pro mode help in a {@link MaterialAlertDialogBuilder} so content is
 * readable regardless of touchpad size.
 */
public final class TouchPadHelpDialog {

    private TouchPadHelpDialog() {}

    /**
     * @param includeMainTitle when true, sets a dialog title line in addition to the message body.
     * @param kmProCompositeTouchpad when true, message reflects KM Pro composite touchpad mode.
     */
    public static void show(
            @NonNull Context context, boolean includeMainTitle, boolean kmProCompositeTouchpad) {
        TouchPadTipsFormatter.HelpDialogParts parts =
                kmProCompositeTouchpad
                        ? TouchPadTipsFormatter.buildKmProCompositeHelpDialogParts(
                                context, includeMainTitle)
                        : TouchPadTipsFormatter.buildGestureHelpDialogParts(context, includeMainTitle);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        if (parts.title != null && parts.title.length() > 0) {
            builder.setTitle(parts.title);
        }
        builder.setMessage(parts.message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
