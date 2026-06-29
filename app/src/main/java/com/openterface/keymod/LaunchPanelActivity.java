package com.openterface.keymod;

import com.openterface.fragment.KeyboardMouseFragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewConfiguration;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /** Second tap on the same mode card within this window acts like Start (see {@link #registerModeCardTap}). */
    private long lastModeTapTime;
    @Nullable
    private String lastModeTapMode;

    // Mode constants
    public static final String MODE_KEYBOARD_MOUSE = "keyboard_mouse";
    /** Advanced composite: strips, split layouts, IME workflows (legacy single &quot;keyboard_mouse&quot; experience). */
    public static final String MODE_KEYBOARD_MOUSE_PRO = "keyboard_mouse_pro";
    public static final String MODE_GAMEPAD = "gamepad";
    // Kept for backward compatibility with older intents/preferences.
    public static final String MODE_NUMPAD = "numpad";
    public static final String MODE_SHORTCUTS = "shortcuts";
    public static final String MODE_MACROS = "macros";
    public static final String MODE_VOICE = "voice";
    public static final String MODE_COMPOSE = "compose";
    public static final String MODE_PRESENTATION = "presentation";
    /** Terminal mode: interactive SSH terminal over USB ECM or BLE-Eth tunnel. */
    public static final String MODE_TERMINAL = "terminal";
    /** Agent mode: AI chat &amp; act (welcome placeholder). */
    public static final String MODE_AGENT = "agent";

    private CheckBox rememberChoiceCheckBox;
    private Button startButton;
    private Button skipButton;
    private TextView showTutorialLink;
    private SharedPreferences prefs;
    private String selectedMode = MODE_KEYBOARD_MOUSE;

    // Mode cards
    private CardView keyboardMouseCard;
    private CardView keyboardMouseProCard;
    private CardView gamepadCard;
    private CardView shortcutsCard;
    private CardView macrosCard;
    private CardView terminalCard;
    private CardView agentCard;
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
            launchModeInternal(lastMode, null);
            return;
        }

        setContentView(R.layout.activity_launch_panel);
        // Keep status bar neutral on launch panel (avoid accent-colored top bar on some OEM skins).
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.background_light));
        applyLaunchPanelRootWindowInsets();

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

    /**
     * Pads the Welcome root by system bar and display-cutout insets so content is not clipped by
     * status / gesture / camera cutout, without relying on fixed {@code Space} heights that break
     * balance across devices and orientations.
     */
    private void applyLaunchPanelRootWindowInsets() {
        View root = findViewById(R.id.launch_panel_root);
        if (root == null) {
            return;
        }
        final int defStart = ViewCompat.getPaddingStart(root);
        final int defTop = root.getPaddingTop();
        final int defEnd = ViewCompat.getPaddingEnd(root);
        final int defBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, windowInsets) -> {
                    Insets sys = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets cut = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    int left = defStart + Math.max(sys.left, cut.left);
                    int top = defTop + Math.max(sys.top, cut.top);
                    int right = defEnd + Math.max(sys.right, cut.right);
                    int bottom = defBottom + Math.max(sys.bottom, cut.bottom);
                    ViewCompat.setPaddingRelative(v, left, top, right, bottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(root);
    }

    private void initializeViews() {
        rememberChoiceCheckBox = findViewById(R.id.remember_choice_checkbox);
        startButton = findViewById(R.id.start_button);
        skipButton = findViewById(R.id.skip_button);
        showTutorialLink = findViewById(R.id.show_tutorial_link);

        // Mode cards
        keyboardMouseCard = findViewById(R.id.keyboard_mouse_card);
        keyboardMouseProCard = findViewById(R.id.keyboard_mouse_pro_card);
        gamepadCard = findViewById(R.id.gamepad_card);
        shortcutsCard = findViewById(R.id.shortcuts_card);
        macrosCard = findViewById(R.id.macros_card);
        terminalCard = findViewById(R.id.terminal_card);
        agentCard = findViewById(R.id.agent_card);
        presentationCard = findViewById(R.id.presentation_card);

        TextView credit = findViewById(R.id.launch_panel_credit);
        if (credit != null) {
            credit.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void updateCardSelections() {
        keyboardMouseCard.setSelected(selectedMode.equals(MODE_KEYBOARD_MOUSE));
        if (keyboardMouseProCard != null) {
            keyboardMouseProCard.setSelected(selectedMode.equals(MODE_KEYBOARD_MOUSE_PRO));
        }
        gamepadCard.setSelected(selectedMode.equals(MODE_GAMEPAD));
        shortcutsCard.setSelected(selectedMode.equals(MODE_SHORTCUTS));
        macrosCard.setSelected(selectedMode.equals(MODE_MACROS));
        if (terminalCard != null) {
            terminalCard.setSelected(selectedMode.equals(MODE_TERMINAL));
        }
        if (agentCard != null) {
            agentCard.setSelected(selectedMode.equals(MODE_AGENT));
        }
        presentationCard.setSelected(selectedMode.equals(MODE_PRESENTATION));
    }

    private void setupClickListeners() {
        registerModeCardTap(keyboardMouseCard, MODE_KEYBOARD_MOUSE);
        if (keyboardMouseProCard != null) {
            registerModeCardTap(keyboardMouseProCard, MODE_KEYBOARD_MOUSE_PRO);
        }
        registerModeCardTap(gamepadCard, MODE_GAMEPAD);
        registerModeCardTap(shortcutsCard, MODE_SHORTCUTS);
        registerModeCardTap(macrosCard, MODE_MACROS);
        if (terminalCard != null) {
            registerModeCardTap(terminalCard, MODE_TERMINAL);
        }
        if (agentCard != null) {
            registerModeCardTap(agentCard, MODE_AGENT);
        }
        registerModeCardTap(presentationCard, MODE_PRESENTATION);

        startButton.setOnClickListener(v -> launchMode(selectedMode));

        skipButton.setOnClickListener(v -> {
            prefs.edit().putBoolean(REMEMBER_CHOICE_KEY, false).apply();
            launchModeInternal(MODE_KEYBOARD_MOUSE, null);
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

    /**
     * Single tap selects the mode; a second tap on the same card within the system double-tap
     * interval confirms and launches (same as pressing Start).
     */
    private void registerModeCardTap(CardView card, String mode) {
        int doubleTapMs = ViewConfiguration.get(this).getDoubleTapTimeout();
        card.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (mode.equals(lastModeTapMode) && (now - lastModeTapTime) <= doubleTapMs) {
                selectedMode = mode;
                updateCardSelections();
                launchMode(mode);
                lastModeTapMode = null;
            } else {
                selectedMode = mode;
                updateCardSelections();
                lastModeTapTime = now;
                lastModeTapMode = mode;
            }
        });
    }

    private void launchMode(String selectedMode) {
        if (MODE_AGENT.equals(selectedMode)) {
            Toast.makeText(this, R.string.launch_panel_agent_coming_soon, Toast.LENGTH_SHORT).show();
            return;
        }

        String primary = primaryLaunchModeFor(selectedMode);
        if (rememberChoiceCheckBox.isChecked()) {
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, true)
                .putString(LAST_MODE_KEY, primary)
                .apply();

            Toast.makeText(
                this,
                getString(R.string.launch_panel_will_remember, getModeDisplayName(primary)),
                Toast.LENGTH_SHORT
            ).show();
        } else {
            prefs.edit()
                .putBoolean(REMEMBER_CHOICE_KEY, false)
                .apply();
        }

        String kbSub = kbMouseInitialSubmodeFor(selectedMode);
        launchModeInternal(primary, kbSub);
    }

    private void launchModeInternal(@NonNull String primaryLaunchMode, @Nullable String kbMouseSubmode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("launch_mode", primaryLaunchMode);
        if (kbMouseSubmode != null && MODE_KEYBOARD_MOUSE.equals(primaryLaunchMode)) {
            intent.putExtra(KeyboardMouseFragment.EXTRA_INITIAL_SUBMODE, kbMouseSubmode);
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @NonNull
    private String primaryLaunchModeFor(String selectedMode) {
        if (MODE_NUMPAD.equals(selectedMode) || MODE_COMPOSE.equals(selectedMode)) {
            return MODE_KEYBOARD_MOUSE;
        }
        return selectedMode;
    }

    @Nullable
    private String kbMouseInitialSubmodeFor(String selectedMode) {
        if (MODE_NUMPAD.equals(selectedMode)) {
            return KeyboardMouseFragment.SUBMODE_NUMPAD;
        }
        if (MODE_COMPOSE.equals(selectedMode)) {
            return null;
        }
        return null;
    }

    @StringRes
    private int modeTitleRes(String mode) {
        switch (mode) {
            case MODE_KEYBOARD_MOUSE:
                return R.string.top_mode_label_keyboard_mouse;
            case MODE_KEYBOARD_MOUSE_PRO:
                return R.string.top_mode_label_keyboard_mouse_pro;
            case MODE_GAMEPAD:
                return R.string.top_mode_label_gamepad;
            case MODE_NUMPAD:
                return R.string.top_mode_label_keyboard_mouse;
            case MODE_SHORTCUTS:
                return R.string.top_mode_label_shortcuts;
            case MODE_MACROS:
                return R.string.top_mode_label_macros;
            case MODE_TERMINAL:
                return R.string.top_mode_label_terminal;
            case MODE_AGENT:
                return R.string.top_mode_label_agent;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
