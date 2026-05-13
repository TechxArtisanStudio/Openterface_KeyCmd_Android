package com.openterface.fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ProfileUiStrings;
import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.basic.KmBasicKeyboardPrefs;
import com.openterface.keymod.prefs.KeyboardAlternatesHintsPrefs;
import com.openterface.keymod.prefs.KmProTouchpadPrefs;
import com.openterface.keymod.prefs.TopShortcutDisplayModePrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard and Mouse Pro setup: keys display mode for the shortcut strip and main keyboard
 * (aligned with row-1 DISPLAY key), alternate hints on/off, and active Shortcut Hub profile picker.
 */
public class KmProSettingsFragment extends Fragment {

    private ImageButton closeButton;
    private MaterialButtonToggleGroup displayModeToggle;
    private MaterialButtonToggleGroup alternateHintsToggle;
    private LinearLayout gamingKeyBehaviorSection;
    private MaterialButtonToggleGroup longPressBehaviorToggle;
    private MaterialButtonToggleGroup touchpadModeToggle;
    @Nullable
    private SwitchCompat scrollStripSwitch;
    @Nullable
    private SwitchCompat gestureStatusSwitch;
    @Nullable
    private SeekBar scrollStripSensitivitySeekBar;
    @Nullable
    private TextView scrollStripSensitivityValueText;
    private RadioGroup modifierBehaviorGroup;
    private View chordSustainCard;
    private SwitchCompat chordSustainSwitch;
    private TextInputLayout profileInputLayout;
    private MaterialAutoCompleteTextView profileDropdown;
    private OnBackPressedCallback rootBackCallback;

    private ShortcutProfileManager profileManager;
    private final List<ShortcutProfileManager.ShortcutProfile> pickerProfiles = new ArrayList<>();
    private ArrayAdapter<String> profileAdapter;

    private boolean suppressDisplayToggleCallback;
    private boolean suppressAlternateHintsToggleCallback;
    private boolean suppressGamingBehaviorToggleCallback;
    private boolean suppressModifierBehaviorCallback;
    private boolean suppressChordSustainCallback;
    private boolean suppressTouchpadModeToggleCallback;
    private boolean suppressScrollStripPrefsCallback;
    private boolean suppressGestureStatusPrefsCallback;
    private boolean loadingModifierPrefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_km_pro_settings, container, false);
        profileManager = new ShortcutProfileManager(requireContext());
        closeButton = view.findViewById(R.id.km_pro_settings_close_button);
        displayModeToggle = view.findViewById(R.id.km_pro_display_mode_toggle);
        alternateHintsToggle = view.findViewById(R.id.km_pro_alternate_hints_toggle);
        gamingKeyBehaviorSection = view.findViewById(R.id.km_pro_gaming_key_behavior_section);
        longPressBehaviorToggle = view.findViewById(R.id.km_pro_long_press_behavior_toggle);
        touchpadModeToggle = view.findViewById(R.id.km_pro_touchpad_mode_toggle);
        scrollStripSwitch = view.findViewById(R.id.km_pro_touchpad_scroll_strip_switch);
        gestureStatusSwitch = view.findViewById(R.id.km_pro_touchpad_gesture_status_switch);
        scrollStripSensitivitySeekBar = view.findViewById(R.id.km_pro_touchpad_strip_scroll_sensitivity_seekbar);
        scrollStripSensitivityValueText = view.findViewById(R.id.km_pro_touchpad_strip_scroll_sensitivity_value_text);
        modifierBehaviorGroup = view.findViewById(R.id.km_pro_modifier_behavior_group);
        chordSustainCard = view.findViewById(R.id.km_pro_chord_sustain_card);
        chordSustainSwitch = view.findViewById(R.id.km_pro_chord_sustain_switch);
        profileInputLayout = view.findViewById(R.id.km_pro_profile_input_layout);
        profileDropdown = view.findViewById(R.id.km_pro_profile_dropdown);

        profileAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        if (profileDropdown != null) {
            profileDropdown.setAdapter(profileAdapter);
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissKmProSettings());
        }

        if (displayModeToggle != null) {
            displayModeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || suppressDisplayToggleCallback) {
                    return;
                }
                int mode = displayModeButtonIdToMode(checkedId);
                TopShortcutDisplayModePrefs.writeMode(requireContext(), mode);
                notifyKeyboardStripRefresh();
            });
        }

        if (alternateHintsToggle != null) {
            alternateHintsToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || suppressAlternateHintsToggleCallback) {
                    return;
                }
                boolean enabled = alternateHintsButtonIdToEnabled(checkedId);
                KeyboardAlternatesHintsPrefs.write(requireContext(), enabled);
                notifyKeyboardAlternatesHintsRefresh();
                updateGamingKeyBehaviorSectionVisibility();
                if (gamingKeyBehaviorSection != null
                        && gamingKeyBehaviorSection.getVisibility() == View.VISIBLE) {
                    syncGamingKeyBehaviorToggleFromPrefs();
                }
            });
        }

        if (touchpadModeToggle != null) {
            touchpadModeToggle.addOnButtonCheckedListener(
                    (group, checkedId, isChecked) -> {
                        if (!isChecked || suppressTouchpadModeToggleCallback) {
                            return;
                        }
                        KmProTouchpadPrefs.writeMode(requireContext(), touchpadModeButtonIdToMode(checkedId));
                        notifyCompositeTouchpadChromeFromKmProSetup();
                    });
        }

        if (scrollStripSwitch != null) {
            scrollStripSwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (suppressScrollStripPrefsCallback) {
                            return;
                        }
                        KmProTouchpadPrefs.writeScrollStripEnabled(requireContext(), isChecked);
                        updateScrollStripSensitivityControlsEnabled();
                        notifyCompositeTouchpadChromeFromKmProSetup();
                    });
        }
        if (gestureStatusSwitch != null) {
            gestureStatusSwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (suppressGestureStatusPrefsCallback) {
                            return;
                        }
                        KmProTouchpadPrefs.writeGestureStatusLineVisible(requireContext(), isChecked);
                        notifyCompositeTouchpadChromeFromKmProSetup();
                    });
        }
        if (scrollStripSensitivitySeekBar != null) {
            scrollStripSensitivitySeekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (suppressScrollStripPrefsCallback || !fromUser) {
                                return;
                            }
                            int sensitivityPercent =
                                    progress + KmProTouchpadPrefs.STRIP_SCROLL_SENSITIVITY_MIN_PERCENT;
                            if (scrollStripSensitivityValueText != null) {
                                scrollStripSensitivityValueText.setText(
                                        String.format("%.1fx", sensitivityPercent / 100f));
                            }
                            KmProTouchpadPrefs.writeStripScrollSensitivityPercent(
                                    requireContext(), sensitivityPercent);
                            notifyCompositeTouchpadChromeFromKmProSetup();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });
        }

        if (longPressBehaviorToggle != null) {
            longPressBehaviorToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked || suppressGamingBehaviorToggleCallback) {
                    return;
                }
                String value =
                        checkedId == R.id.km_pro_long_press_hold
                                ? KmBasicKeyboardPrefs.VALUE_LONG_PRESS_HOLD
                                : KmBasicKeyboardPrefs.VALUE_LONG_PRESS_REPEAT;
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putString(KmBasicKeyboardPrefs.PREF_LONG_PRESS_BEHAVIOR, value)
                        .apply();
                notifyCompositeKeyboardLayoutFromKmProSetup();
            });
        }

        if (profileDropdown != null) {
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
        }

        loadingModifierPrefs = true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        RadioButton sticky = view.findViewById(R.id.km_pro_modifier_sticky);
        RadioButton chord = view.findViewById(R.id.km_pro_modifier_chord);
        if (sticky != null && chord != null) {
            if (KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD.equals(
                    prefs.getString(
                            KmBasicKeyboardPrefs.PREF_KEY, KmBasicKeyboardPrefs.PREF_DEFAULT_VALUE))) {
                chord.setChecked(true);
            } else {
                sticky.setChecked(true);
            }
            updateChordSustainCardVisibility(chord.isChecked());
        } else {
            updateChordSustainCardVisibility(false);
        }
        if (chordSustainSwitch != null) {
            chordSustainSwitch.setChecked(KmBasicKeyboardPrefs.isChordSustainHidEnabled(requireContext()));
        }
        loadingModifierPrefs = false;

        if (modifierBehaviorGroup != null) {
            modifierBehaviorGroup.setOnCheckedChangeListener(
                    (group, checkedId) -> {
                        if (loadingModifierPrefs || suppressModifierBehaviorCallback) {
                            return;
                        }
                        String value =
                                checkedId == R.id.km_pro_modifier_chord
                                        ? KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD
                                        : KmBasicKeyboardPrefs.VALUE_STICKY;
                        prefs.edit().putString(KmBasicKeyboardPrefs.PREF_KEY, value).apply();
                        updateChordSustainCardVisibility(checkedId == R.id.km_pro_modifier_chord);
                        notifyCompositeKeyboardLayoutFromKmProSetup();
                    });
        }
        if (chordSustainSwitch != null) {
            chordSustainSwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (loadingModifierPrefs || suppressChordSustainCallback) {
                            return;
                        }
                        prefs.edit()
                                .putBoolean(KmBasicKeyboardPrefs.PREF_CHORD_SUSTAIN_HID, isChecked)
                                .apply();
                    });
        }

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
        syncTouchpadModeToggleFromPrefs();
        syncGestureStatusSwitchFromPrefs();
        syncScrollStripControlsFromPrefs();
        updateGamingKeyBehaviorSectionVisibility();
        syncGamingKeyBehaviorToggleFromPrefs();
        syncModifierBehaviorFromPrefs();
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

    private void updateGamingKeyBehaviorSectionVisibility() {
        if (gamingKeyBehaviorSection == null) {
            return;
        }
        boolean hintsOn = KeyboardAlternatesHintsPrefs.read(requireContext());
        gamingKeyBehaviorSection.setVisibility(hintsOn ? View.GONE : View.VISIBLE);
    }

    private void syncGamingKeyBehaviorToggleFromPrefs() {
        if (longPressBehaviorToggle == null
                || gamingKeyBehaviorSection == null
                || gamingKeyBehaviorSection.getVisibility() != View.VISIBLE) {
            return;
        }
        int buttonId =
                KmBasicKeyboardPrefs.isLongPressSustainedHoldMode(requireContext())
                        ? R.id.km_pro_long_press_hold
                        : R.id.km_pro_long_press_repeat;
        suppressGamingBehaviorToggleCallback = true;
        longPressBehaviorToggle.check(buttonId);
        suppressGamingBehaviorToggleCallback = false;
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

    private void notifyCompositeKeyboardLayoutFromKmProSetup() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshCompositeKeyboardLayoutFromKmProSetup();
        }
    }

    private void notifyCompositeTouchpadChromeFromKmProSetup() {
        Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshCompositeTouchpadChromeFromKmProSetup();
        }
    }

    private void syncTouchpadModeToggleFromPrefs() {
        if (touchpadModeToggle == null) {
            return;
        }
        int mode = KmProTouchpadPrefs.readMode(requireContext());
        int buttonId = touchpadModeToButtonId(mode);
        suppressTouchpadModeToggleCallback = true;
        touchpadModeToggle.check(buttonId);
        suppressTouchpadModeToggleCallback = false;
    }

    private void syncScrollStripControlsFromPrefs() {
        if (scrollStripSwitch == null || scrollStripSensitivitySeekBar == null) {
            return;
        }
        suppressScrollStripPrefsCallback = true;
        scrollStripSwitch.setChecked(KmProTouchpadPrefs.isScrollStripEnabled(requireContext()));
        int stripPercent = KmProTouchpadPrefs.getStripScrollSensitivityPercent(requireContext());
        int stripSeekProgress =
                Math.max(
                        0,
                        Math.min(
                                KmProTouchpadPrefs.STRIP_SCROLL_SENSITIVITY_MAX_PERCENT
                                        - KmProTouchpadPrefs.STRIP_SCROLL_SENSITIVITY_MIN_PERCENT,
                                stripPercent - KmProTouchpadPrefs.STRIP_SCROLL_SENSITIVITY_MIN_PERCENT));
        scrollStripSensitivitySeekBar.setProgress(stripSeekProgress);
        if (scrollStripSensitivityValueText != null) {
            scrollStripSensitivityValueText.setText(String.format("%.1fx", stripPercent / 100f));
        }
        suppressScrollStripPrefsCallback = false;
        updateScrollStripSensitivityControlsEnabled();
    }

    private void syncGestureStatusSwitchFromPrefs() {
        if (gestureStatusSwitch == null) {
            return;
        }
        suppressGestureStatusPrefsCallback = true;
        gestureStatusSwitch.setChecked(KmProTouchpadPrefs.isGestureStatusLineVisible(requireContext()));
        suppressGestureStatusPrefsCallback = false;
    }

    private void updateScrollStripSensitivityControlsEnabled() {
        if (scrollStripSensitivitySeekBar == null) {
            return;
        }
        boolean on = scrollStripSwitch == null || scrollStripSwitch.isChecked();
        scrollStripSensitivitySeekBar.setEnabled(on);
    }

    private static int touchpadModeButtonIdToMode(int checkedButtonId) {
        if (checkedButtonId == R.id.km_pro_touchpad_mode_strip) {
            return KmProTouchpadPrefs.MODE_MOUSE_KEYS_BASIC;
        }
        if (checkedButtonId == R.id.km_pro_touchpad_mode_hybrid) {
            return KmProTouchpadPrefs.MODE_HYBRID;
        }
        return KmProTouchpadPrefs.MODE_GESTURES_ONLY;
    }

    private static int touchpadModeToButtonId(int mode) {
        switch (KmProTouchpadPrefs.clampMode(mode)) {
            case KmProTouchpadPrefs.MODE_MOUSE_KEYS_BASIC:
                return R.id.km_pro_touchpad_mode_strip;
            case KmProTouchpadPrefs.MODE_HYBRID:
                return R.id.km_pro_touchpad_mode_hybrid;
            case KmProTouchpadPrefs.MODE_GESTURES_ONLY:
            default:
                return R.id.km_pro_touchpad_mode_gestures;
        }
    }

    private void syncModifierBehaviorFromPrefs() {
        if (modifierBehaviorGroup == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int checkedId =
                KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD.equals(
                                prefs.getString(
                                        KmBasicKeyboardPrefs.PREF_KEY, KmBasicKeyboardPrefs.PREF_DEFAULT_VALUE))
                        ? R.id.km_pro_modifier_chord
                        : R.id.km_pro_modifier_sticky;
        suppressModifierBehaviorCallback = true;
        modifierBehaviorGroup.check(checkedId);
        suppressModifierBehaviorCallback = false;
        updateChordSustainCardVisibility(checkedId == R.id.km_pro_modifier_chord);
        if (chordSustainSwitch != null) {
            suppressChordSustainCallback = true;
            chordSustainSwitch.setChecked(KmBasicKeyboardPrefs.isChordSustainHidEnabled(requireContext()));
            suppressChordSustainCallback = false;
        }
    }

    private void updateChordSustainCardVisibility(boolean chordModeSelected) {
        if (chordSustainCard != null) {
            chordSustainCard.setVisibility(chordModeSelected ? View.VISIBLE : View.GONE);
        }
    }
}
