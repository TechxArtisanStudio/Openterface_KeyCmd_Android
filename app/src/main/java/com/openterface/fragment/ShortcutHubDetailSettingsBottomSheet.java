package com.openterface.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.openterface.keymod.R;
import com.openterface.keymod.prefs.ShortcutHubDetailUiPrefs;

/**
 * Per-profile Shortcut Hub layout (list vs card) and shortcut row display mode.
 */
public class ShortcutHubDetailSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ShortcutHubDetailSettings";
    private static final String ARG_PROFILE_ID = "profile_id";

    private String profileId;
    private MaterialButtonToggleGroup layoutToggle;
    private MaterialButtonToggleGroup displayToggle;
    private boolean suppressLayoutCallback;
    private boolean suppressDisplayCallback;

    @NonNull
    public static ShortcutHubDetailSettingsBottomSheet newInstance(@NonNull String profileId) {
        ShortcutHubDetailSettingsBottomSheet f = new ShortcutHubDetailSettingsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROFILE_ID, profileId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        profileId = args != null ? args.getString(ARG_PROFILE_ID, "") : "";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_shortcut_hub_detail_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView title = view.findViewById(R.id.hub_detail_settings_title);
        if (title != null) {
            if (profileId != null && !profileId.isEmpty()) {
                com.openterface.keymod.ShortcutProfileManager pm =
                        new com.openterface.keymod.ShortcutProfileManager(requireContext());
                com.openterface.keymod.ShortcutProfileManager.ShortcutProfile p = pm.getProfileById(profileId);
                if (p != null) {
                    title.setText(getString(R.string.shortcut_hub_detail_settings_title_with_profile,
                            com.openterface.keymod.ProfileUiStrings.displayName(requireContext(), p)));
                } else {
                    title.setText(R.string.shortcut_hub_detail_settings_title);
                }
            } else {
                title.setText(R.string.shortcut_hub_detail_settings_title);
            }
        }

        layoutToggle = view.findViewById(R.id.hub_detail_layout_toggle);
        displayToggle = view.findViewById(R.id.hub_detail_display_toggle);

        if (layoutToggle != null) {
            layoutToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || suppressLayoutCallback || profileId == null || profileId.isEmpty()) {
                    return;
                }
                int layout = checkedId == R.id.hub_detail_layout_card
                        ? ShortcutHubDetailUiPrefs.LAYOUT_CARD
                        : ShortcutHubDetailUiPrefs.LAYOUT_LIST;
                ShortcutHubDetailUiPrefs.writeLayout(requireContext(), profileId, layout);
                notifyHostHubUiChanged();
            });
        }

        if (displayToggle != null) {
            displayToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || suppressDisplayCallback || profileId == null || profileId.isEmpty()) {
                    return;
                }
                int mode = displayButtonIdToMode(checkedId);
                ShortcutHubDetailUiPrefs.writeDisplay(requireContext(), profileId, mode);
                notifyHostHubUiChanged();
            });
        }

        syncTogglesFromPrefs();
    }

    private void notifyHostHubUiChanged() {
        if (getParentFragment() instanceof ShortcutHubFragment) {
            ((ShortcutHubFragment) getParentFragment()).onShortcutHubDetailUiPrefsChanged();
        }
    }

    private void syncTogglesFromPrefs() {
        if (profileId == null || profileId.isEmpty()) {
            return;
        }
        int layout = ShortcutHubDetailUiPrefs.readLayout(requireContext(), profileId);
        int display = ShortcutHubDetailUiPrefs.readDisplay(requireContext(), profileId);

        if (layoutToggle != null) {
            int layoutButtonId = layout == ShortcutHubDetailUiPrefs.LAYOUT_CARD
                    ? R.id.hub_detail_layout_card
                    : R.id.hub_detail_layout_list;
            suppressLayoutCallback = true;
            layoutToggle.check(layoutButtonId);
            suppressLayoutCallback = false;
        }

        if (displayToggle != null) {
            suppressDisplayCallback = true;
            displayToggle.check(displayModeToButtonId(display));
            suppressDisplayCallback = false;
        }
    }

    private static int displayButtonIdToMode(int checkedButtonId) {
        if (checkedButtonId == R.id.hub_detail_display_icon) {
            return ShortcutHubDetailUiPrefs.DISPLAY_ICON;
        }
        if (checkedButtonId == R.id.hub_detail_display_chord) {
            return ShortcutHubDetailUiPrefs.DISPLAY_CHORD;
        }
        if (checkedButtonId == R.id.hub_detail_display_hybrid) {
            return ShortcutHubDetailUiPrefs.DISPLAY_HYBRID;
        }
        return ShortcutHubDetailUiPrefs.DISPLAY_NAME;
    }

    private static int displayModeToButtonId(int mode) {
        switch (ShortcutHubDetailUiPrefs.clampDisplay(mode)) {
            case ShortcutHubDetailUiPrefs.DISPLAY_ICON:
                return R.id.hub_detail_display_icon;
            case ShortcutHubDetailUiPrefs.DISPLAY_CHORD:
                return R.id.hub_detail_display_chord;
            case ShortcutHubDetailUiPrefs.DISPLAY_HYBRID:
                return R.id.hub_detail_display_hybrid;
            case ShortcutHubDetailUiPrefs.DISPLAY_NAME:
            default:
                return R.id.hub_detail_display_name;
        }
    }
}
