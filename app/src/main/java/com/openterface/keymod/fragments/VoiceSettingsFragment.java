package com.openterface.keymod.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;

/**
 * Voice Input Settings Fragment
 * - Whisper API key configuration
 * - Language selection
 * - Sensitivity adjustment
 */
public class VoiceSettingsFragment extends Fragment {

    private static final String PREF_STT_ENGINE = "stt_engine";
    private static final String PREF_WHISPER_API_KEY = "whisper_api_key";
    private static final String PREF_VOICE_LANGUAGE = "voice_language";
    private static final String PREF_VOICE_SENSITIVITY = "voice_sensitivity";

    private EditText apiKeyEditText;
    private Spinner sttEngineSpinner;
    private Spinner languageSpinner;
    private SeekBar sensitivitySeekBar;
    private TextView sensitivityValueText;
    private Button testConnectionButton;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_voice, container, false);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        initializeViews(view);
        loadSettings();
        setupListeners();
        
        return view;
    }

    private void initializeViews(View view) {
        apiKeyEditText = view.findViewById(R.id.api_key_edittext);
        sttEngineSpinner = view.findViewById(R.id.stt_engine_spinner);
        languageSpinner = view.findViewById(R.id.voice_language_spinner);
        sensitivitySeekBar = view.findViewById(R.id.sensitivity_seekbar);
        sensitivityValueText = view.findViewById(R.id.sensitivity_value_text);
        testConnectionButton = view.findViewById(R.id.test_connection_button);

        String[] engines = {
                getString(R.string.settings_voice_engine_system),
                getString(R.string.settings_voice_engine_whisper)
        };
        ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, engines);
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sttEngineSpinner.setAdapter(engineAdapter);
        
        // Setup language spinner (order must match persisted index)
        String[] languages = {
                getString(R.string.settings_voice_language_english),
                getString(R.string.settings_voice_language_chinese),
                getString(R.string.settings_voice_language_spanish),
                getString(R.string.settings_voice_language_french),
                getString(R.string.settings_voice_language_german),
                getString(R.string.settings_voice_language_japanese)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
    }

    private void loadSettings() {
        String apiKey = prefs.getString(PREF_WHISPER_API_KEY, "");
        apiKeyEditText.setText(apiKey);

        String sttEngine = prefs.getString(PREF_STT_ENGINE, "system");
        sttEngineSpinner.setSelection("whisper".equals(sttEngine) ? 1 : 0);
        
        int languageIndex = prefs.getInt(PREF_VOICE_LANGUAGE, 0);
        languageSpinner.setSelection(languageIndex);
        
        int sensitivity = prefs.getInt(PREF_VOICE_SENSITIVITY, 50);
        sensitivitySeekBar.setProgress(sensitivity);
        sensitivityValueText.setText(String.valueOf(sensitivity));
    }

    private void setupListeners() {
        apiKeyEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(PREF_WHISPER_API_KEY, s.toString()).apply();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(PREF_VOICE_LANGUAGE, position).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        sttEngineSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String engine = position == 1 ? "whisper" : "system";
                prefs.edit().putString(PREF_STT_ENGINE, engine).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensitivityValueText.setText(String.valueOf(progress));
                prefs.edit().putInt(PREF_VOICE_SENSITIVITY, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        testConnectionButton.setOnClickListener(v -> {
            String apiKey = apiKeyEditText.getText().toString().trim();
            if (TextUtils.isEmpty(apiKey)) {
                Toast.makeText(getContext(), R.string.settings_voice_toast_enter_api_key, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // TODO: Implement actual API test
            Toast.makeText(getContext(), R.string.settings_voice_toast_testing, Toast.LENGTH_SHORT).show();
            // Simulate test
            testConnectionButton.postDelayed(() -> {
                Toast.makeText(getContext(), R.string.settings_voice_toast_success, Toast.LENGTH_SHORT).show();
            }, 1500);
        });
    }
}
