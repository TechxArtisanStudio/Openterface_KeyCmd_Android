package com.openterface.keymod.util;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sensitive page shielding utility class.
 * Provides two layers of protection:
 * 1. FLAG_SECURE to prevent screenshots/screen recording (platform level).
 * 2. View alpha masking for sensitive content (UI level - content stays hidden even if captured).
 */
public class SensitivePageShield {

    private final Activity activity;
    private final List<View> sensitiveViews = new ArrayList<>();
    private boolean isSecure = false;

    // Static reference count: multiple sensitive fragments share the same Window.
    // Only clear FLAG_SECURE when the last active shield is disabled.
    private static int sActiveSecureCount = 0;

    public SensitivePageShield(Activity activity) {
        this.activity = activity;
    }

    /**
     * Register a sensitive View to be masked when shielding is active.
     */
    public SensitivePageShield registerSensitiveView(View view) {
        if (view != null && !sensitiveViews.contains(view)) {
            sensitiveViews.add(view);
        }
        return this;
    }

    /**
     * Enable shielding: set FLAG_SECURE and apply view masking.
     */
    public void enable() {
        if (isSecure) return;

        // 1. Set FLAG_SECURE to block screenshots
        enableSecureFlag();

        // 2. Apply masking to sensitive Views (content stays hidden even in screenshots)
        overlaySensitiveViews(true);

        isSecure = true;
    }

    /**
     * Disable shielding: clear FLAG_SECURE and remove view masking.
     */
    public void disable() {
        if (!isSecure) return;

        // 1. Clear FLAG_SECURE
        disableSecureFlag();

        // 2. Remove masking
        overlaySensitiveViews(false);

        isSecure = false;
    }

    /**
     * Check if shielding is currently active.
     */
    public boolean isSecure() {
        return isSecure;
    }

    private void enableSecureFlag() {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window != null) {
            // 第一个 shield enable 时才设置 FLAG_SECURE
            if (sActiveSecureCount == 0) {
                window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                );
            }
            sActiveSecureCount++;
        }
    }

    private void disableSecureFlag() {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window != null && sActiveSecureCount > 0) {
            sActiveSecureCount--;
            // 最后一个 shield disable 时才清除 FLAG_SECURE
            if (sActiveSecureCount == 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    /**
     * Apply/remove masking to registered sensitive Views.
     * Masking uses alpha so the View stays laid out but its content becomes invisible,
     * keeping the layout stable while hiding sensitive data from screenshots.
     */
    private void overlaySensitiveViews(boolean overlay) {
        for (View view : sensitiveViews) {
            if (view != null) {
                if (overlay) {
                    // alpha=0 makes the view fully transparent
                    view.setAlpha(0f);
                } else {
                    // alpha=1 restores full opacity
                    view.setAlpha(1f);
                }
            }
        }
    }

    /**
     * Release resources and clear the view list.
     */
    public void release() {
        disable();
        sensitiveViews.clear();
    }
}
