package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.fragment.CompositeFragment;
import com.openterface.fragment.GamepadFragment;
import com.openterface.fragment.KeyboardMouseFragment;
import com.openterface.fragment.PresentationFragment;
import com.openterface.fragment.ShortcutHubFragment;

import androidx.fragment.app.Fragment;

/**
 * One-shot per-mode onboarding overlays (separate from {@link TutorialOverlay#KEY_TUTORIAL_SHOWN} KM
 * Basic quick start). Keys are versioned so future copy changes can re-show if needed.
 */
public final class ModeGuidePrefs {

    public static final String PREFS_NAME = "ModeGuidePrefs";

    private static final String KEY_KM_PRO_V1 = "mode_guide_km_pro_v1";
    private static final String KEY_PRESENTATION_V1 = "mode_guide_presentation_v1";
    private static final String KEY_GAMEPAD_V1 = "mode_guide_gamepad_v1";
    private static final String KEY_SHORTCUT_HUB_V1 = "mode_guide_shortcut_hub_v1";

    private ModeGuidePrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isModeGuideCompleted(@NonNull Context context, @NonNull GuideHostMode mode) {
        if (mode == GuideHostMode.KM_BASIC) {
            return TutorialOverlay.isShown(context);
        }
        return prefs(context).getBoolean(keyFor(mode), false);
    }

    public static void markModeGuideCompleted(@NonNull Context context, @NonNull GuideHostMode mode) {
        if (mode == GuideHostMode.KM_BASIC) {
            return;
        }
        prefs(context).edit().putBoolean(keyFor(mode), true).apply();
    }

    /** Clears completion so the next visit can auto-show again (debug / optional). */
    public static void clearModeGuideCompleted(@NonNull Context context, @NonNull GuideHostMode mode) {
        if (mode == GuideHostMode.KM_BASIC) {
            context.getApplicationContext()
                    .getSharedPreferences(TutorialOverlay.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(TutorialOverlay.KEY_TUTORIAL_SHOWN, false)
                    .apply();
            return;
        }
        prefs(context).edit().putBoolean(keyFor(mode), false).apply();
    }

    private static String keyFor(GuideHostMode mode) {
        switch (mode) {
            case KM_PRO:
                return KEY_KM_PRO_V1;
            case PRESENTATION:
                return KEY_PRESENTATION_V1;
            case GAMEPAD:
                return KEY_GAMEPAD_V1;
            case SHORTCUT_HUB:
                return KEY_SHORTCUT_HUB_V1;
            case KM_BASIC:
            default:
                throw new IllegalArgumentException("No prefs key for mode: " + mode);
        }
    }

    /** Modes that have a dedicated overlay tour (Basic uses {@link TutorialOverlay#KEY_TUTORIAL_SHOWN}). */
    public enum GuideHostMode {
        KM_BASIC,
        KM_PRO,
        PRESENTATION,
        GAMEPAD,
        SHORTCUT_HUB
    }

    @Nullable
    public static GuideHostMode guideModeForTopFragment(@Nullable Fragment f) {
        if (f instanceof KeyboardMouseFragment) {
            return GuideHostMode.KM_BASIC;
        }
        if (f instanceof CompositeFragment) {
            return GuideHostMode.KM_PRO;
        }
        if (f instanceof PresentationFragment) {
            return GuideHostMode.PRESENTATION;
        }
        if (f instanceof GamepadFragment) {
            return GuideHostMode.GAMEPAD;
        }
        if (f instanceof ShortcutHubFragment) {
            return GuideHostMode.SHORTCUT_HUB;
        }
        return null;
    }

    public static boolean supportsHeaderModeGuideButton(@Nullable Fragment f) {
        return guideModeForTopFragment(f) != null && !(f instanceof KeyboardMouseFragment) && !(f instanceof GamepadFragment);
    }
}
