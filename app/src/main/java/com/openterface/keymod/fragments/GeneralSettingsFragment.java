package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.AppLocaleManager;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;

/**
 * General Settings Fragment
 * - Connection preferences
 * - Display options
 * - Auto-connect settings
 */
public class GeneralSettingsFragment extends Fragment {

    private static final String PREF_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String PREF_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String PREF_ORIENTATION_LOCK = "orientation_lock";
    private static final String PREF_TOUCHPAD_SCROLL_SENSITIVITY = "touchpad_scroll_sensitivity";
    private static final String[] THEME_FAMILY_VALUES = {
            ThemeManager.FAMILY_ORANGE,
            ThemeManager.FAMILY_BLUE,
            ThemeManager.FAMILY_GREEN,
            ThemeManager.FAMILY_PINK,
            ThemeManager.FAMILY_PURPLE,
            ThemeManager.FAMILY_RED,
            ThemeManager.FAMILY_TEAL,
            ThemeManager.FAMILY_INDIGO
    };

    private static final int[] THEME_FAMILY_SWATCH_COLORS = {
            R.color.theme_accent_orange,
            R.color.theme_accent_blue,
            R.color.theme_accent_green,
            R.color.theme_accent_pink,
            R.color.theme_accent_purple,
            R.color.theme_accent_red,
            R.color.theme_accent_teal,
            R.color.theme_accent_indigo
    };

    private CheckBox autoConnectCheckBox;
    private CheckBox keepScreenOnCheckBox;
    private CheckBox hapticFeedbackCheckBox;
    private CheckBox orientationLockCheckBox;
    private Spinner languageSpinner;
    private LinearLayout themeFamilySwatches;
    private CheckBox themeFollowSystemCheckBox;
    private RadioGroup themeModeGroup;
    private SeekBar touchpadScrollSensitivitySeekBar;
    private TextView touchpadScrollSensitivityValueText;
    private boolean isLoadingSettings;
    private int themeFamilySelectedIndex;
    /** Spinner index 0 = follow system; 1..n match R.array.language_codes order. */
    private String[] localeSpinnerTags;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_general, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        initializeViews(view);
        loadSettings();
        setupListeners();

        return view;
    }

    private void initializeViews(View view) {
        autoConnectCheckBox = view.findViewById(R.id.auto_connect_checkbox);
        keepScreenOnCheckBox = view.findViewById(R.id.keep_screen_on_checkbox);
        hapticFeedbackCheckBox = view.findViewById(R.id.haptic_feedback_checkbox);
        orientationLockCheckBox = view.findViewById(R.id.orientation_lock_checkbox);
        languageSpinner = view.findViewById(R.id.language_spinner);
        themeFamilySwatches = view.findViewById(R.id.theme_family_swatches);
        themeFollowSystemCheckBox = view.findViewById(R.id.theme_follow_system_checkbox);
        themeModeGroup = view.findViewById(R.id.theme_mode_group);
        touchpadScrollSensitivitySeekBar = view.findViewById(R.id.touchpad_scroll_sensitivity_seekbar);
        touchpadScrollSensitivityValueText = view.findViewById(R.id.touchpad_scroll_sensitivity_value_text);

        String followLabel = getString(R.string.app_language_follow_system);
        String[] langNames = getResources().getStringArray(R.array.language_names);
        String[] langCodes = getResources().getStringArray(R.array.language_codes);
        localeSpinnerTags = new String[1 + langCodes.length];
        localeSpinnerTags[0] = AppLocaleManager.LOCALE_FOLLOW_SYSTEM;
        System.arraycopy(langCodes, 0, localeSpinnerTags, 1, langCodes.length);
        String[] labels = new String[1 + langNames.length];
        labels[0] = followLabel;
        System.arraycopy(langNames, 0, labels, 1, langNames.length);
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(langAdapter);

        populateThemeFamilySwatches();
    }

    private void populateThemeFamilySwatches() {
        themeFamilySwatches.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String[] themeFamilyLabels = getResources().getStringArray(R.array.theme_color_family_names);
        for (int i = 0; i < THEME_FAMILY_VALUES.length; i++) {
            View swatch = inflater.inflate(R.layout.item_theme_family_swatch, themeFamilySwatches, false);
            View root = swatch.findViewById(R.id.theme_family_swatch_root);
            View colorView = swatch.findViewById(R.id.theme_family_swatch_color);
            int color = ContextCompat.getColor(requireContext(), THEME_FAMILY_SWATCH_COLORS[i]);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            colorView.setBackground(dot);
            root.setContentDescription(themeFamilyLabels[i]);
            int index = i;
            root.setOnClickListener(v -> {
                if (isLoadingSettings || themeFamilySelectedIndex == index) {
                    return;
                }
                themeFamilySelectedIndex = index;
                updateThemeFamilySwatchSelection();
                applyThemeFromUi();
            });
            themeFamilySwatches.addView(swatch);
        }
    }

    private void loadSettings() {
        isLoadingSettings = true;
        ConnectionManager cm = new ConnectionManager(requireContext());
        autoConnectCheckBox.setChecked(cm.isAutoConnectEnabled());
        keepScreenOnCheckBox.setChecked(prefs.getBoolean(PREF_KEEP_SCREEN_ON, true));
        hapticFeedbackCheckBox.setChecked(prefs.getBoolean(PREF_HAPTIC_FEEDBACK, true));
        orientationLockCheckBox.setChecked(prefs.getBoolean(PREF_ORIENTATION_LOCK, false));

        int localeIndex = indexForLocaleTag(AppLocaleManager.getPersistedLocaleTag(requireContext()));
        languageSpinner.setSelection(localeIndex);

        // Sensitivity is stored as an int percentage from 20..200; 100 means 1.0x.
        int sensitivityPercent = prefs.getInt(PREF_TOUCHPAD_SCROLL_SENSITIVITY, 100);
        int seekProgress = Math.max(0, Math.min(180, sensitivityPercent - 20));
        touchpadScrollSensitivitySeekBar.setProgress(seekProgress);
        touchpadScrollSensitivityValueText.setText(String.format("%.1fx", sensitivityPercent / 100f));

        String family = prefs.getString(ThemeManager.PREF_THEME_COLOR_FAMILY, ThemeManager.FAMILY_ORANGE);
        themeFamilySelectedIndex = getThemeFamilyIndex(family);
        updateThemeFamilySwatchSelection();

        boolean followSystem = prefs.getBoolean(ThemeManager.PREF_THEME_FOLLOW_SYSTEM, false);
        themeFollowSystemCheckBox.setChecked(followSystem);

        String mode = prefs.getString(ThemeManager.PREF_THEME_MODE_OVERRIDE, ThemeManager.MODE_DARK);
        themeModeGroup.check(ThemeManager.MODE_LIGHT.equals(mode) ? R.id.theme_mode_light : R.id.theme_mode_dark);
        themeModeGroup.setEnabled(!followSystem);
        viewModeChildrenEnabled(!followSystem);
        isLoadingSettings = false;
    }

    private void updateThemeFamilySwatchSelection() {
        if (themeFamilySwatches == null) {
            return;
        }
        int n = themeFamilySwatches.getChildCount();
        for (int i = 0; i < n; i++) {
            View root = themeFamilySwatches.getChildAt(i).findViewById(R.id.theme_family_swatch_root);
            if (root == null) {
                continue;
            }
            boolean selected = i == themeFamilySelectedIndex;
            root.setBackgroundResource(selected
                    ? R.drawable.theme_family_swatch_ring_selected
                    : R.drawable.theme_family_swatch_ring_idle);
        }
    }

    private void setupListeners() {
        autoConnectCheckBox.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        new ConnectionManager(requireContext()).setAutoConnectEnabled(isChecked));

        keepScreenOnCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_KEEP_SCREEN_ON, isChecked).apply();
            // Apply immediately if needed
            if (getActivity() != null) {
                getActivity().getWindow().setFlags(
                    isChecked ? android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0,
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            }
        });

        hapticFeedbackCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_HAPTIC_FEEDBACK, isChecked).apply();
        });

        orientationLockCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_ORIENTATION_LOCK, isChecked).apply();
        });

        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (isLoadingSettings) {
                    return;
                }
                String tag = localeSpinnerTags[position];
                if (tag.equals(AppLocaleManager.getPersistedLocaleTag(requireContext()))) {
                    return;
                }
                AppLocaleManager.persistAndApplyLocales(requireContext(), tag);
                // Defer so AppCompatDelegate can apply locales before we recreate (avoids stale configuration).
                parent.post(() -> {
                    if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
                        return;
                    }
                    getActivity().recreate();
                });
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        themeFollowSystemCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isLoadingSettings) {
                return;
            }
            themeModeGroup.setEnabled(!isChecked);
            viewModeChildrenEnabled(!isChecked);
            applyThemeFromUi();
        });

        themeModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) {
                return;
            }
            applyThemeFromUi();
        });

        touchpadScrollSensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sensitivityPercent = progress + 20;
                float multiplier = sensitivityPercent / 100f;
                touchpadScrollSensitivityValueText.setText(String.format("%.1fx", multiplier));
                prefs.edit().putInt(PREF_TOUCHPAD_SCROLL_SENSITIVITY, sensitivityPercent).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void viewModeChildrenEnabled(boolean enabled) {
        for (int i = 0; i < themeModeGroup.getChildCount(); i++) {
            themeModeGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    private int getThemeFamilyIndex(String family) {
        for (int i = 0; i < THEME_FAMILY_VALUES.length; i++) {
            if (THEME_FAMILY_VALUES[i].equals(family)) {
                return i;
            }
        }
        return 0;
    }

    private String getThemeFamilyValue(int index) {
        if (index < 0 || index >= THEME_FAMILY_VALUES.length) {
            return ThemeManager.FAMILY_ORANGE;
        }
        return THEME_FAMILY_VALUES[index];
    }

    private int indexForLocaleTag(String tag) {
        if (localeSpinnerTags == null) {
            return 0;
        }
        for (int i = 0; i < localeSpinnerTags.length; i++) {
            if (localeSpinnerTags[i].equals(tag)) {
                return i;
            }
        }
        return 0;
    }

    private void applyThemeFromUi() {
        String family = getThemeFamilyValue(themeFamilySelectedIndex);
        boolean followSystem = themeFollowSystemCheckBox.isChecked();
        String mode = themeModeGroup.getCheckedRadioButtonId() == R.id.theme_mode_light
                ? ThemeManager.MODE_LIGHT : ThemeManager.MODE_DARK;

        String currentFamily = prefs.getString(ThemeManager.PREF_THEME_COLOR_FAMILY, ThemeManager.FAMILY_ORANGE);
        boolean currentFollowSystem = prefs.getBoolean(ThemeManager.PREF_THEME_FOLLOW_SYSTEM, false);
        String currentMode = prefs.getString(ThemeManager.PREF_THEME_MODE_OVERRIDE, ThemeManager.MODE_DARK);

        if (family.equals(currentFamily)
                && followSystem == currentFollowSystem
                && mode.equals(currentMode)) {
            return;
        }

        ThemeManager.savePreferences(requireContext(), family, followSystem, mode);
        requireActivity().recreate();
    }
}
