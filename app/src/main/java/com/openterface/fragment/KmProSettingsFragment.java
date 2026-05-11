package com.openterface.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ProfileUiStrings;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.prefs.KeyboardAlternatesHintsPrefs;
import com.openterface.keymod.prefs.TopShortcutDisplayModePrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard and Mouse Pro setup: shortcut strip display mode (aligned with row-1 DISPLAY key),
 * alternate hints on/off, and active Shortcut Hub profile picker.
 */
public class KmProSettingsFragment extends Fragment {

    private ImageButton closeButton;
    private MaterialButtonToggleGroup displayModeToggle;
    private MaterialButtonToggleGroup alternateHintsToggle;
    private TextInputLayout profileInputLayout;
    private MaterialAutoCompleteTextView profileDropdown;
    private OnBackPressedCallback rootBackCallback;

    private ShortcutProfileManager profileManager;
    private final List<ShortcutProfileManager.ShortcutProfile> pickerProfiles = new ArrayList<>();
    private ArrayAdapter<String> profileAdapter;

    private boolean suppressDisplayToggleCallback;
    private boolean suppressAlternateHintsToggleCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_km_pro_settings, container, false);
        profileManager = new ShortcutProfileManager(requireContext());
        closeButton = view.findViewById(R.id.km_pro_settings_close_button);
        displayModeToggle = view.findViewById(R.id.km_pro_display_mode_toggle);
        alternateHintsToggle = view.findViewById(R.id.km_pro_alternate_hints_toggle);
        profileInputLayout = view.findViewById(R.id.km_pro_profile_input_layout);
        profileDropdown = view.findViewById(R.id.km_pro_profile_dropdown);

        profileAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        profileDropdown.setAdapter(profileAdapter);

        closeButton.setOnClickListener(v -> dismissKmProSettings());

        displayModeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressDisplayToggleCallback) {
                return;
            }
            int mode = displayModeButtonIdToMode(checkedId);
            TopShortcutDisplayModePrefs.writeMode(requireContext(), mode);
            notifyKeyboardStripRefresh();
        });

        alternateHintsToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressAlternateHintsToggleCallback) {
                return;
            }
            boolean enabled = alternateHintsButtonIdToEnabled(checkedId);
            KeyboardAlternatesHintsPrefs.write(requireContext(), enabled);
            notifyKeyboardAlternatesHintsRefresh();
        });

        profileDropdown.setOnItemClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            if (position < 0 || position >= pickerProfiles.size()) {
                return;
            }
            ShortcutProfileManager.ShortcutProfile p = pickerProfiles.get(position);
            if (p != null && p.id != null) {
                profileManager.setActiveProfile(p.id);
                notifyKeyboardStripRefresh();
            }
        });

        profileDropdown.setOnClickListener(v -> profileDropdown.showDropDown());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                dismissKmProSettings();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), rootBackCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncDisplayModeToggleFromPrefs();
        syncAlternateHintsToggleFromPrefs();
        bindProfileDropdown();
    }

    private void dismissKmProSettings() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).hideKmProSettingsOverlay();
        }
    }

    private void syncDisplayModeToggleFromPrefs() {
        if (displayModeToggle == null) {
            return;
        }
        int mode = TopShortcutDisplayModePrefs.readMode(requireContext());
        int buttonId = displayModeModeToButtonId(mode);
        suppressDisplayToggleCallback = true;
        displayModeToggle.check(buttonId);
        suppressDisplayToggleCallback = false;
    }

    private void syncAlternateHintsToggleFromPrefs() {
        if (alternateHintsToggle == null) {
            return;
        }
        boolean enabled = KeyboardAlternatesHintsPrefs.read(requireContext());
        int buttonId = alternateHintsEnabledToButtonId(enabled);
        suppressAlternateHintsToggleCallback = true;
        alternateHintsToggle.check(buttonId);
        suppressAlternateHintsToggleCallback = false;
    }

    private static boolean alternateHintsButtonIdToEnabled(int checkedButtonId) {
        return checkedButtonId == R.id.km_pro_alternate_hints_on;
    }

    private static int alternateHintsEnabledToButtonId(boolean enabled) {
        return enabled ? R.id.km_pro_alternate_hints_on : R.id.km_pro_alternate_hints_off;
    }

    private static int displayModeButtonIdToMode(int checkedButtonId) {
        if (checkedButtonId == R.id.km_pro_display_mode_name) {
            return TopShortcutDisplayModePrefs.MODE_NAME;
        }
        if (checkedButtonId == R.id.km_pro_display_mode_chord) {
            return TopShortcutDisplayModePrefs.MODE_CHORD;
        }
        return TopShortcutDisplayModePrefs.MODE_ICON;
    }

    private static int displayModeModeToButtonId(int mode) {
        switch (TopShortcutDisplayModePrefs.clamp(mode)) {
            case TopShortcutDisplayModePrefs.MODE_NAME:
                return R.id.km_pro_display_mode_name;
            case TopShortcutDisplayModePrefs.MODE_CHORD:
                return R.id.km_pro_display_mode_chord;
            case TopShortcutDisplayModePrefs.MODE_ICON:
            default:
                return R.id.km_pro_display_mode_icon;
        }
    }

    private void bindProfileDropdown() {
        if (profileManager == null || profileAdapter == null || profileDropdown == null) {
            return;
        }
        profileManager.reloadProfilesFromPreferences();
        pickerProfiles.clear();
        pickerProfiles.addAll(profileManager.getProfilesForUiPicking());

        List<String> labels = new ArrayList<>();
        for (ShortcutProfileManager.ShortcutProfile p : pickerProfiles) {
            labels.add(ProfileUiStrings.displayName(requireContext(), p));
        }
        profileAdapter.clear();
        profileAdapter.addAll(labels);
        profileAdapter.notifyDataSetChanged();

        ShortcutProfileManager.ShortcutProfile active = profileManager.getActiveProfile();
        String activeLabel = active != null
                ? ProfileUiStrings.displayName(requireContext(), active)
                : "";
        profileDropdown.setText(activeLabel, false);
        if (profileInputLayout != null) {
            profileInputLayout.setError(null);
        }
    }

    private void notifyKeyboardStripRefresh() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshOpenKeyboardShortcutStripFromPrefs();
        }
    }

    private void notifyKeyboardAlternatesHintsRefresh() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshKeyboardAlternatesHintsFromPrefs();
        }
    }
}
