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
                    false,
                    "connection"),
            step(
                    activity,
                    new int[] {R.id.basic_km_target_os, R.id.target_os_header_button},
                    R.string.tutorial_desc_target_os,
                    false,
                    "target_os"),
            step(
                    activity,
                    new int[] {
                        R.id.basic_km_submode_tabs_row,
                        R.id.basic_km_tab_keyboard,
                        R.id.basic_km_tab_touchpad,
                        R.id.basic_km_tab_numpad
                    },
                    R.string.tutorial_desc_submode_tabs,
                    false,
                    "submode_tabs"),
            step(
                    activity,
                    new int[] {
                        R.id.keyboard_view, R.id.keyboard_view_left, R.id.basic_km_tab_keyboard
                    },
                    R.string.tutorial_desc_keyboard_modes,
                    false,
                    "keyboard_modes"),
            step(
                    activity,
                    new int[] {R.id.basic_physical_keyboard, R.id.basic_keyboard_surface},
                    R.string.tutorial_desc_modifier_hold_swipe,
                    false,
                    450,
                    () -> activity.ensureKmBasicKeyboardSubmodeForGuide(),
                    "modifier_hold_swipe"),
            step(
                    activity,
                    new int[] {R.id.basic_km_menu_button, R.id.menu_button},
                    R.string.tutorial_desc_menu,
                    true,
                    "menu")
        };
    }

    public static TutorialOverlay.Step[] kmProGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(activity,
                    new int[] {R.id.connection_container},
                    R.string.mode_tutorial_pro_step_connection,
                    false, "connection"),
            step(activity,
                    new int[] {R.id.target_os_header_button},
                    R.string.mode_tutorial_pro_step_target_os,
                    false, "target_os"),
            step(activity,
                    new int[] {R.id.km_pro_header_tabs_scroll, R.id.km_pro_header_tab_keyboard},
                    R.string.mode_tutorial_pro_step_submodes,
                    false, "submodes"),
            step(activity,
                    new int[] {R.id.km_pro_setup_header_button},
                    R.string.mode_tutorial_pro_step_setup,
                    false, "setup"),
            step(activity,
                    new int[] {
                        R.id.keyboard_view, R.id.keyboard_view_left, R.id.keyboard_view_right
                    },
                    R.string.mode_tutorial_pro_step_keyboard_strip,
                    false, "keyboard_strip"),
            step(activity,
                    new int[] {
                        R.id.keyboard_view, R.id.keyboard_view_left, R.id.keyboard_view_right
                    },
                    R.string.mode_tutorial_pro_step_modifier_hold_swipe,
                    false,
                    550,
                    () ->
                            activity.ensureKmProFragmentSubmode(
                                    KmProSubmodePrefs.SUBMODE_KEYBOARD),
                    "modifier_hold_swipe"),
            step(activity,
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
                                    KmProSubmodePrefs.SUBMODE_COMPOSE),
                    "compose_editor"),
            step(activity,
                    new int[] {R.id.basic_compose_actions, R.id.basic_compose_send},
                    R.string.mode_tutorial_pro_step_compose_actions,
                    true, "compose_actions")
        };
    }

    public static TutorialOverlay.Step[] presentationGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(activity,
                    new int[] {R.id.tool_carousel},
                    R.string.mode_tutorial_presentation_step_tools,
                    false, "tools"),
            step(activity,
                    new int[] {R.id.btn_next, R.id.btn_previous},
                    R.string.mode_tutorial_presentation_step_nav,
                    false, "nav"),
            step(activity,
                    new int[] {R.id.btn_play, R.id.btn_black_screen},
                    R.string.mode_tutorial_presentation_step_actions,
                    false, "actions"),
            step(activity,
                    new int[] {R.id.timer_card_container},
                    R.string.mode_tutorial_presentation_step_timer,
                    true, "timer")
        };
    }

    public static TutorialOverlay.Step[] gamepadGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(activity,
                    new int[] {R.id.gamepad_chrome_connection_wrap},
                    R.string.mode_tutorial_gamepad_step_connection,
                    false, "connection"),
            step(activity,
                    new int[] {R.id.gamepad_presets_btn, R.id.gamepad_active_preset_chip},
                    R.string.mode_tutorial_gamepad_step_presets,
                    false, "presets"),
            step(activity,
                    new int[] {R.id.edit_mode_toggle},
                    R.string.mode_tutorial_gamepad_step_customize,
                    false, "customize"),
            step(activity,
                    new int[] {R.id.gamepad_view},
                    R.string.mode_tutorial_gamepad_step_rearrange,
                    false,
                    500,
                    () -> activity.ensureGamepadEditModeForGuide(),
                    "rearrange"),
            step(activity,
                    new int[] {R.id.gamepad_view},
                    R.string.mode_tutorial_gamepad_step_sticks,
                    true,
                    280,
                    () -> activity.ensureGamepadEditModeExitForGuide(),
                    "sticks")
        };
    }

    public static TutorialOverlay.Step[] shortcutHubGuide(@NonNull final MainActivity activity) {
        return new TutorialOverlay.Step[] {
            step(activity,
                    new int[] {R.id.create_profile_button, R.id.import_button},
                    R.string.mode_tutorial_hub_step_profiles,
                    false, "profiles"),
            step(activity,
                    new int[] {R.id.profiles_recycler},
                    R.string.mode_tutorial_hub_step_open_profile,
                    false, "open_profile"),
            step(activity,
                    new int[] {
                        R.id.detail_profile_name,
                        R.id.hub_detail_tabs,
                        R.id.back_button,
                        R.id.reset_default_profile_button
                    },
                    R.string.mode_tutorial_hub_step_detail_intro,
                    false,
                    550,
                    () -> activity.ensureShortcutHubDefaultProfileForGuide(),
                    "detail_intro"),
            step(activity,
                    new int[] {R.id.my_shortcuts_recycler},
                    R.string.mode_tutorial_hub_step_favorites_list,
                    false, "favorites_list"),
            step(activity,
                    new int[] {R.id.browse_shortcuts_recycler},
                    R.string.mode_tutorial_hub_step_browse_category,
                    false,
                    500,
                    () -> activity.ensureShortcutHubBrowseFirstCategoryForGuide(),
                    "browse_category"),
            step(activity,
                    new int[] {R.id.add_shortcut_button},
                    R.string.mode_tutorial_hub_step_add_shortcut_toolbar,
                    false, "add_shortcut_toolbar"),
            step(activity,
                    new int[] {R.id.km_pro_setup_header_button},
                    R.string.mode_tutorial_hub_step_detail_setup_gear,
                    false, "detail_setup_gear"),
            step(activity,
                    new int[] {R.id.connection_container},
                    R.string.mode_tutorial_hub_step_connection,
                    false, "connection"),
            step(activity,
                    new int[] {R.id.mode_guide_header_button},
                    R.string.mode_tutorial_hub_step_replay_hint,
                    true, "replay_hint")
        };
    }

    private static TutorialOverlay.Step step(
            @NonNull final MainActivity activity,
            @NonNull final int[] viewIds,
            final int descriptionRes,
            final boolean last,
            @Nullable final String imageKey) {
        return step(activity, viewIds, descriptionRes, last, 400, null, imageKey);
    }

    private static TutorialOverlay.Step step(
            @NonNull final MainActivity activity,
            @NonNull final int[] viewIds,
            final int descriptionRes,
            final boolean last,
            final int delayMs,
            @Nullable final Runnable extraOnShow,
            @Nullable final String imageKey) {
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

            @Override
            @Nullable
            public String imageKey() {
                return imageKey;
            }
        };
    }
}
