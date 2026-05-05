package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;
import com.openterface.keymod.basic.KmBasicKeyboardPrefs;

/**
 * Keyboard and mouse related settings (KM Basic modifier behavior).
 */
public class KeyboardMouseSettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private boolean loading;
    private View sustainCard;
    private SwitchCompat sustainSwitch;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_keyboard_mouse, container, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        RadioButton sticky = view.findViewById(R.id.km_basic_modifier_sticky);
        RadioButton chord = view.findViewById(R.id.km_basic_modifier_chord);
        RadioButton longPressRepeat = view.findViewById(R.id.km_basic_long_press_repeat);
        RadioButton longPressHold = view.findViewById(R.id.km_basic_long_press_hold);
        sustainCard = view.findViewById(R.id.km_basic_chord_sustain_card);
        sustainSwitch = view.findViewById(R.id.km_basic_chord_sustain_switch);

        loading = true;
        if (KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD.equals(
                prefs.getString(KmBasicKeyboardPrefs.PREF_KEY, KmBasicKeyboardPrefs.PREF_DEFAULT_VALUE))) {
            chord.setChecked(true);
        } else {
            sticky.setChecked(true);
        }
        if (KmBasicKeyboardPrefs.VALUE_LONG_PRESS_HOLD.equals(
                prefs.getString(
                        KmBasicKeyboardPrefs.PREF_LONG_PRESS_BEHAVIOR,
                        KmBasicKeyboardPrefs.PREF_LONG_PRESS_DEFAULT))) {
            longPressHold.setChecked(true);
        } else {
            longPressRepeat.setChecked(true);
        }
        sustainSwitch.setChecked(KmBasicKeyboardPrefs.isChordSustainHidEnabled(requireContext()));
        loading = false;

        updateSustainCardVisibility(chord.isChecked());

        RadioGroup behaviorGroup = view.findViewById(R.id.km_basic_modifier_behavior_group);
        behaviorGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    if (loading) {
                        return;
                    }
                    String value =
                            checkedId == R.id.km_basic_modifier_chord
                                    ? KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD
                                    : KmBasicKeyboardPrefs.VALUE_STICKY;
                    prefs.edit().putString(KmBasicKeyboardPrefs.PREF_KEY, value).apply();
                    updateSustainCardVisibility(checkedId == R.id.km_basic_modifier_chord);
                });

        sustainSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        prefs.edit().putBoolean(KmBasicKeyboardPrefs.PREF_CHORD_SUSTAIN_HID, isChecked).apply());

        RadioGroup longPressGroup = view.findViewById(R.id.km_basic_long_press_behavior_group);
        longPressGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    if (loading) {
                        return;
                    }
                    String value =
                            checkedId == R.id.km_basic_long_press_hold
                                    ? KmBasicKeyboardPrefs.VALUE_LONG_PRESS_HOLD
                                    : KmBasicKeyboardPrefs.VALUE_LONG_PRESS_REPEAT;
                    prefs.edit().putString(KmBasicKeyboardPrefs.PREF_LONG_PRESS_BEHAVIOR, value).apply();
                });

        return view;
    }

    private void updateSustainCardVisibility(boolean chordModeSelected) {
        if (sustainCard != null) {
            sustainCard.setVisibility(chordModeSelected ? View.VISIBLE : View.GONE);
        }
    }
}
