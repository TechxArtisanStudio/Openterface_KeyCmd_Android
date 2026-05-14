package com.openterface.keymod;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.prefs.KmProSubmodePrefs;

/**
 * Builds {@link TutorialOverlay.Step} arrays for KM Basic and per-mode guides.
 */
public final class ModeTutorialSteps {

    private ModeTutorialSteps() {}

    public static TutorialOverlay.Step[] kmBasicQuickStart(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(
                    activity,
                    new int[] {R.id.basic_km_connection, R.id.connection_container},
                    R.string.tutorial_desc_connection,
                    false),
            step(
                    activity,
                    new int[] {R.id.basic_km_target_os, R.id.target_os_header_button},
                    R.string.tutorial_desc_target_os,
                    false),
            step(
                    activity,
                    new int[] {
                        R.id.basic_km_submode_tabs_row,
                        R.id.basic_km_tab_keyboard,
                        R.id.basic_km_tab_touchpad,
                        R.id.basic_km_tab_numpad
                    },
                    R.string.tutorial_desc_submode_tabs,
                    false),
            step(
                    activity,
                    new int[] {
                        R.id.keyboard_view, R.id.keyboard_view_left, R.id.basic_km_tab_keyboard
                    },
                    R.string.tutorial_desc_keyboard_modes,
                    false),
            step(
                    activity,
                    new int[] {R.id.basic_km_menu_button, R.id.menu_button},
                    R.string.tutorial_desc_menu,
                    true)
        };
    }

    public static TutorialOverlay.Step[] kmProGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(
                    activity,
                    new int[] {R.id.connection_container},
                    R.string.mode_tutorial_pro_step_connection,
                    false),
            step(
                    activity,
                    new int[] {R.id.target_os_header_button},
                    R.string.mode_tutorial_pro_step_target_os,
                    false),
            step(
                    activity,
                    new int[] {R.id.km_pro_header_tabs_scroll, R.id.km_pro_header_tab_keyboard},
                    R.string.mode_tutorial_pro_step_submodes,
                    false),
            step(
                    activity,
                    new int[] {R.id.km_pro_setup_header_button},
                    R.string.mode_tutorial_pro_step_setup,
                    false),
            step(
                    activity,
                    new int[] {
                        R.id.keyboard_view, R.id.keyboard_view_left, R.id.keyboard_view_right
                    },
                    R.string.mode_tutorial_pro_step_keyboard_strip,
                    false),
            step(
                    activity,
                    new int[] {
                        R.id.basic_compose_editor,
                        R.id.basic_compose_root,
                        R.id.km_pro_compose_fragment_host
                    },
                    R.string.mode_tutorial_pro_step_compose_editor,
                    false,
                    650,
                    () ->
                            activity.ensureKmProFragmentSubmode(
                                    KmProSubmodePrefs.SUBMODE_COMPOSE)),
            step(
                    activity,
                    new int[] {R.id.basic_compose_actions, R.id.basic_compose_send},
                    R.string.mode_tutorial_pro_step_compose_actions,
                    true)
        };
    }

    public static TutorialOverlay.Step[] presentationGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(
                    activity,
                    new int[] {R.id.tool_carousel},
                    R.string.mode_tutorial_presentation_step_tools,
                    false),
            step(
                    activity,
                    new int[] {R.id.btn_next, R.id.btn_previous},
                    R.string.mode_tutorial_presentation_step_nav,
                    false),
            step(
                    activity,
                    new int[] {R.id.btn_play, R.id.btn_black_screen},
                    R.string.mode_tutorial_presentation_step_actions,
                    false),
            step(
                    activity,
                    new int[] {R.id.timer_card_container},
                    R.string.mode_tutorial_presentation_step_timer,
                    true)
        };
    }

    public static TutorialOverlay.Step[] gamepadGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(
                    activity,
                    new int[] {R.id.gamepad_chrome_connection_wrap},
                    R.string.mode_tutorial_gamepad_step_connection,
                    false),
            step(
                    activity,
                    new int[] {R.id.edit_mode_toggle},
                    R.string.mode_tutorial_gamepad_step_customize,
                    false),
            step(
                    activity,
                    new int[] {R.id.gamepad_presets_btn, R.id.gamepad_active_preset_chip},
                    R.string.mode_tutorial_gamepad_step_presets,
                    false),
            step(
                    activity,
                    new int[] {R.id.gamepad_view},
                    R.string.mode_tutorial_gamepad_step_sticks,
                    true)
        };
    }

    public static TutorialOverlay.Step[] shortcutHubGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(
                    activity,
                    new int[] {R.id.create_profile_button, R.id.import_button},
                    R.string.mode_tutorial_hub_step_profiles,
                    false),
            step(
                    activity,
                    new int[] {R.id.profiles_recycler},
                    R.string.mode_tutorial_hub_step_open_profile,
                    false),
            step(
                    activity,
                    new int[] {R.id.connection_container},
                    R.string.mode_tutorial_hub_step_connection,
                    false),
            step(
                    activity,
                    new int[] {R.id.mode_guide_header_button},
                    R.string.mode_tutorial_hub_step_replay_hint,
                    true)
        };
    }

    private static TutorialOverlay.Step step(
            @NonNull final MainActivity activity,
            @NonNull final int[] viewIds,
            final int descriptionRes,
            final boolean last) {
        return step(activity, viewIds, descriptionRes, last, 400, null);
    }

    private static TutorialOverlay.Step step(
            @NonNull final MainActivity activity,
            @NonNull final int[] viewIds,
            final int descriptionRes,
            final boolean last,
            final int delayMs,
            @Nullable final Runnable extraOnShow) {
        return new TutorialOverlay.Step() {
            @Override
            public int[] targetViewIds() {
                return viewIds;
            }

            @Override
            public String description() {
                return activity.getString(descriptionRes);
            }

            @Override
            public String buttonText() {
                return activity.getString(
                        last ? R.string.tutorial_done : R.string.tutorial_next);
            }

            @Override
            public void onShow(Context context) {
                MainActivity.closeDrawerIfOpen(context);
                if (extraOnShow != null) {
                    extraOnShow.run();
                }
            }

            @Override
            public int delayMs() {
                return delayMs;
            }
        };
    }
}
