package com.openterface.keymod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

/**
 * Welcome & Guide screen (user-facing name): mode picker, remember choice, and tutorial link.
 * Entry point activity; matches iOS LaunchPanelView.swift functionality.
 */
public class LaunchPanelActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "LaunchPanelPrefs";
    private static final String REMEMBER_CHOICE_KEY = "rememberChoice";
    private static final String LAST_MODE_KEY = "lastMode";
    public static final String SHOW_PANEL = "show_panel";

    private static final String STATE_SELECTED_MODE = "state_selected_mode";
    private static final String STATE_REMEMBER_CHECKED = "state_remember_checked";

    // Mode constants
    public static final String MODE_KEYBOARD_MOUSE = "keyboard_mouse";
    public static final String MODE_GAMEPAD = "gamepad";
    // Kept for backward compatibility with older intents/preferences.
    public static final String MODE_NUMPAD = "numpad";
    public static final String MODE_SHORTCUTS = "shortcuts";
    public static final String MODE_MACROS = "macros";
    public static final String MODE_VOICE = "voice";
    public static final String MODE_COMPOSE = "compose";
    public static final String MODE_PRESENTATION = "presentation";

    private CheckBox rememberChoiceCheckBox;
    private Button startButton;
    private Button skipButton;
    private TextView showTutorialLink;
    private SharedPreferences prefs;
    private String selectedMode = MODE_KEYBOARD_MOUSE;

    // Mode cards
    private CardView keyboardMouseCard;
    private CardView gamepadCard;
    private CardView shortcutsCard;
    private CardView macrosCard;
    private CardView voiceCard;
    private CardView presentationCard;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppLocaleManager.applyPersistedLocales(this);
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Skip launch panel only if auto-launch is enabled AND not explicitly requesting the panel
        boolean rememberChoice = prefs.getBoolean(REMEMBER_CHOICE_KEY, false);
        boolean showPanel = getIntent().getBooleanExtra(SHOW_PANEL, false);
        if (rememberChoice && !showPanel) {
            String lastMode = prefs.getString(LAST_MODE_KEY, MODE_KEYBOARD_MOUSE);
            launchModeInternal(normalizeMode(lastMode));
            return;
        }

        setContentView(R.layout.activity_launch_panel);
        // Keep status bar neutral on launch panel (avoid accent-colored top bar on some OEM skins).
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.background_light));

        initializeViews();
        if (savedInstanceState != null) {
            selectedMode = savedInstanceState.getString(STATE_SELECTED_MODE, MODE_KEYBOARD_MOUSE);
            rememberChoiceCheckBox.setChecked(
                savedInstanceState.getBoolean(STATE_REMEMBER_CHECKED, false));
        }
        setupClickListeners();
        updateCardSelections();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_MODE, selectedMode);
        if (rememberChoiceCheckBox != null) {
            outState.putBoolean(STATE_REMEMBER_CHECKED, rememberChoiceCheckBox.isChecked());
        }
    }

    private void initializeViews() {
        rememberChoiceCheckBox = findViewById(R.id.remember_choice_checkbox);
        startButton = findViewById(R.id.start_button);
        skipButton = findViewById(R.id.skip_button);
        showTutorialLink = findViewById(R.id.show_tutorial_link);

        // Mode cards
        keyboardMouseCard = findViewById(R.id.keyboard_mouse_card);
        gamepadCard = findViewById(R.id.gamepad_card);
        shortcutsCard = findViewById(R.id.shortcuts_card);
        macrosCard = findViewById(R.id.macros_card);
        voiceCard = findViewById(R.id.voice_card);
        presentationCard = findViewById(R.id.presentation_card);

        TextView credit = findViewById(R.id.launch_panel_credit);
        if (credit != null) {
            credit.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void updateCardSelections() {
        keyboardMouseCard.setSelected(selectedMode.equals(MODE_KEYBOARD_MOUSE));
        gamepadCard.setSelected(selectedMode.equals(MODE_GAMEPAD));
        shortcutsCard.setSelected(selectedMode.equals(MODE_SHORTCUTS));
        macrosCard.setSelected(selectedMode.equals(MODE_MACROS));
        voiceCard.setSelected(selectedMode.equals(MODE_VOICE));
        presentationCard.setSelected(selectedMode.equals(MODE_PRESENTATION));
    }

    private void setupClickListeners() {
        keyboardMouseCard.setOnClickListener(v -> {
            selectedMode = MODE_KEYBOARD_MOUSE;
            updateCardSelections();
        });

        gamepadCard.setOnClickListener(v -> {
            selectedMode = MODE_GAMEPAD;
            updateCardSelections();
        });

        shortcutsCard.setOnClickListener(v -> {
            selectedMode = MODE_SHORTCUTS;
            updateCardSelections();
        });

        macrosCard.setOnClickListener(v -> {
            selectedMode = MODE_MACROS;
            updateCardSelections();
        });

        voiceCard.setOnClickListener(v -> {
            selectedMode = MODE_VOICE;
            updateCardSelections();
        });

        presentationCard.setOnClickListener(v -> {
            selectedMode = MODE_PRESENTATION;
            updateCardSelections();
        });

        startButton.setOnClickListener(v -> launchMode(selectedMode));

        skipButton.setOnClickListener(v -> {
            prefs.edit().putBoolean(REMEMBER_CHOICE_KEY, false).apply();
            launchModeInternal(MODE_KEYBOARD_MOUSE);
        });

        if (showTutorialLink != null) {
            showTutorialLink.setOnClickListener(v -> {
                getSharedPreferences(TutorialOverlay.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(TutorialOverlay.KEY_TUTORIAL_SHOWN, false).apply();
                finish();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
    }

    private void launchMode(String mode) {
        String normalizedMode = normalizeMode(mode);
        // Save preference if checkbox is checked
        if (rememberChoiceCheckBox.isChecked()) {
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, true)
                .putString(LAST_MODE_KEY, normalizedMode)
                .apply();
            
            Toast.makeText(
                this,
                getString(R.string.launch_panel_will_remember, getModeDisplayName(normalizedMode)),
                Toast.LENGTH_SHORT
            ).show();
        } else {
            // Clear remembered choice
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, false)
                .apply();
        }

        launchModeInternal(normalizedMode);
    }

    private void launchModeInternal(String mode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("launch_mode", normalizeMode(mode));
        
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @StringRes
    private int modeTitleRes(String mode) {
        switch (normalizeMode(mode)) {
            case MODE_KEYBOARD_MOUSE:
                return R.string.top_mode_label_keyboard_mouse;
            case MODE_GAMEPAD:
                return R.string.top_mode_label_gamepad;
            case MODE_NUMPAD:
                return R.string.top_mode_label_keyboard_mouse;
            case MODE_SHORTCUTS:
                return R.string.top_mode_label_shortcuts;
            case MODE_MACROS:
                return R.string.top_mode_label_macros;
            case MODE_VOICE:
                return R.string.voice_hub_title;
            case MODE_PRESENTATION:
                return R.string.presentation_mode;
            default:
                return R.string.top_mode_label_keyboard_mouse;
        }
    }

    private String getModeDisplayName(String mode) {
        return getString(modeTitleRes(mode));
    }

    private String normalizeMode(String mode) {
        if (MODE_NUMPAD.equals(mode) || MODE_COMPOSE.equals(mode)) {
            return MODE_KEYBOARD_MOUSE;
        }
        return mode;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
