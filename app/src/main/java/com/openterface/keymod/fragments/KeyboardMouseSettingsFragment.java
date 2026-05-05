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

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_keyboard_mouse, container, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        RadioButton sticky = view.findViewById(R.id.km_basic_modifier_sticky);
        RadioButton chord = view.findViewById(R.id.km_basic_modifier_chord);

        loading = true;
        if (KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD.equals(
                prefs.getString(KmBasicKeyboardPrefs.PREF_KEY, KmBasicKeyboardPrefs.VALUE_STICKY))) {
            chord.setChecked(true);
        } else {
            sticky.setChecked(true);
        }
        loading = false;

        ((RadioGroup) view.findViewById(R.id.km_basic_modifier_behavior_group))
                .setOnCheckedChangeListener((group, checkedId) -> {
                    if (loading) {
                        return;
                    }
                    String value =
                            checkedId == R.id.km_basic_modifier_chord
                                    ? KmBasicKeyboardPrefs.VALUE_MOMENTARY_CHORD
                                    : KmBasicKeyboardPrefs.VALUE_STICKY;
                    prefs.edit().putString(KmBasicKeyboardPrefs.PREF_KEY, value).apply();
                });

        return view;
    }
}
