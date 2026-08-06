package com.openterface.keymod.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * Helper class to apply/remove RenderEffect blur on the host Activity's content view
 * when a BottomSheetDialog is shown. Requires API 31+ (Android 12+).
 *
 * <p>This provides a blur effect behind BottomSheetDialogs, which don't support
 * {@code Window.setBackgroundBlurRadius()} directly.</p>
 *
 * <p>Usage in BottomSheetDialogFragment:</p>
 * <pre>
 * // In onShowListener or onStart():
 * BottomSheetBlurHelper.applyBlur(this);
 *
 * // In onStop() or onDismiss():
 * BottomSheetBlurHelper.removeBlur(this);
 * </pre>
 */
public final class BottomSheetBlurHelper {

    /** Blur radius in dp. Subtle effect for background dimming. Android caps at 150px. */
    private static final float BLUR_RADIUS_DP = 5f;

    private BottomSheetBlurHelper() {
        // Utility class, no instantiation
    }

    /**
     * Apply blur effect to the activity's content view.
     * Call from BottomSheetDialogFragment's onShowListener or onStart().
     *
     * @param fragment The BottomSheetDialogFragment showing the dialog
     */
    public static void applyBlur(@NonNull Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (!fragment.isAdded()) return;

        Activity activity = fragment.getActivity();
        if (activity == null) return;

        applyBlur(activity);
    }

    /**
     * Remove blur effect from the activity's content view.
     * Call from BottomSheetDialogFragment's onStop() or onDismiss().
     *
     * @param fragment The BottomSheetDialogFragment hiding the dialog
     */
    public static void removeBlur(@NonNull Fragment fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        // Don't check isAdded() here - we need to remove blur even when fragment is detaching
        Activity activity = fragment.getActivity();
        if (activity == null) return;

        removeBlur(activity);
    }

    /**
     * Apply blur effect to the activity's content view.
     * Can be used for both Fragment-based and non-fragment BottomSheetDialogs.
     *
     * @param activity The host Activity
     */
    public static void applyBlur(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        View activityRoot = activity.findViewById(android.R.id.content);
        if (activityRoot != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            float radius = BLUR_RADIUS_DP * density;
            activityRoot.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                            radius, radius,
                            android.graphics.Shader.TileMode.CLAMP));
        }
    }

    /**
     * Remove blur effect from the activity's content view.
     * Can be used for both Fragment-based and non-fragment BottomSheetDialogs.
     *
     * @param activity The host Activity
     */
    public static void removeBlur(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        View activityRoot = activity.findViewById(android.R.id.content);
        if (activityRoot != null) {
            activityRoot.setRenderEffect(null);
        }
    }
}
