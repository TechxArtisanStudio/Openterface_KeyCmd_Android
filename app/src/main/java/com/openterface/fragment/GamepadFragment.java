package com.openterface.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.content.pm.ActivityInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;
import com.openterface.keymod.GamepadView.ComponentLongPressListener;
import com.openterface.keymod.GamepadView.DpadStateListener;

/**
 * Gamepad Fragment - Simple layout: left stick + right button
 */
public class GamepadFragment extends Fragment {

    private static final String TAG = "GamepadFragment";

    // Config preference keys
    private static final String PREF_STICK_MODE = "gamepad_stick_mode";
    private static final String PREF_STICK_UP = "gamepad_stick_up";
    private static final String PREF_STICK_LEFT = "gamepad_stick_left";
    private static final String PREF_STICK_DOWN = "gamepad_stick_down";
    private static final String PREF_STICK_RIGHT = "gamepad_stick_right";
    private static final String PREF_BUTTON_A_KEY = "gamepad_button_a_key";
    private static final String PREF_BUTTON_B_KEY = "gamepad_button_b_key";
    private static final String PREF_BUTTON_A_MOD = "gamepad_button_a_mod";
    private static final String PREF_BUTTON_B_MOD = "gamepad_button_b_mod";
    private static final String PREF_BUTTON_SIZE = "gamepad_button_size";
    private static final String PREF_STICK_SIZE = "gamepad_stick_size";
    private static final String PREF_BG_IMAGE = "gamepad_bg_image";
    private static final String PREF_BG_SCALE = "gamepad_bg_scale";
    private static final String PREF_BG_OFFSET_X = "gamepad_bg_offset_x";
    private static final String PREF_BG_OFFSET_Y = "gamepad_bg_offset_y";

    // Stick modes
    private static final String MODE_ANALOG = "analog";
    private static final String MODE_KEY = "key";

    // Key picker: label -> HID keycode (as string)
    private static final String[][] KEY_OPTIONS = {
        {"W", "26"}, {"A", "4"}, {"S", "22"}, {"D", "7"},
        {"J", "13"}, {"K", "14"}, {"L", "15"}, {"I", "12"},
        {"U", "24"}, {"O", "18"}, {"P", "19"}, {"H", "11"},
        {"G", "10"}, {"F", "9"}, {"Q", "20"}, {"E", "8"},
        {"R", "21"}, {"T", "23"}, {"Y", "28"}, {"Z", "29"},
        {"X", "27"}, {"C", "6"}, {"V", "25"}, {"B", "5"},
        {"N", "17"}, {"M", "16"},
        {"Space", "44"}, {"Enter", "40"}, {"Esc", "41"}, {"Tab", "43"},
        {"↑", "82"}, {"↓", "81"}, {"←", "80"}, {"→", "79"},
    };

    // Default stick key codes
    private static final int DEFAULT_STICK_UP = 26;    // W
    private static final int DEFAULT_STICK_LEFT = 4;   // A
    private static final int DEFAULT_STICK_DOWN = 22;  // S
    private static final int DEFAULT_STICK_RIGHT = 7;  // D
    private static final int DEFAULT_BUTTON_A = 40;    // Enter
    private static final int DEFAULT_BUTTON_B = 41;    // Escape

    private GamepadView gamepadView;

    private Vibrator vibrator;
    private GamepadLayout currentLayout;
    private SharedPreferences prefs;
    private float mouseSensitivity = 1.0f;

    // Stick config
    private String stickMode = MODE_KEY;
    private int stickUpKey = DEFAULT_STICK_UP;
    private int stickLeftKey = DEFAULT_STICK_LEFT;
    private int stickDownKey = DEFAULT_STICK_DOWN;
    private int stickRightKey = DEFAULT_STICK_RIGHT;
    private float stickSizeScale = 1.0f;

    // Button config
    private int buttonAKey = DEFAULT_BUTTON_A;
    private int buttonBKey = 41;
    private int buttonAModifiers = 0;
    private int buttonBModifiers = 0;
    private float buttonSizeScale = 1.0f;

    // Modifier key definitions for checkboxes (label -> HID keycode)
    private static final String[][] MODIFIER_KEYS = {
        {"Ctrl", "224"}, {"Shift", "225"}, {"Alt", "226"}, {"Fn", "227"},
    };

    // Track currently pressed keys to avoid repeat events
    private boolean keyUpPressed = false;
    private boolean keyLeftPressed = false;
    private boolean keyDownPressed = false;
    private boolean keyRightPressed = false;
    private boolean buttonAPressed = false;
    private boolean buttonBPressed = false;
    private boolean twoButtonMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gamepad, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        loadSavedSensitivity();
        loadStickConfig();
        loadButtonConfig();

        gamepadView = view.findViewById(R.id.gamepad_view);
        currentLayout = GamepadLayout.SIMPLE;
        gamepadView.setLayout(currentLayout);

        loadBackground();

        // Button mode toggle
        ToggleButton buttonModeToggle = view.findViewById(R.id.button_mode_toggle);
        buttonModeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            twoButtonMode = isChecked;
            if (gamepadView != null) {
                gamepadView.setShowTwoButtons(isChecked);
            }
            Log.d(TAG, "Button mode: " + (twoButtonMode ? "2 buttons (A+B)" : "1 button (A)"));
        });

        // Edit mode toggle (controls whether long-press triggers config menu)
        ToggleButton editModeToggle = view.findViewById(R.id.edit_mode_toggle);
        editModeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (gamepadView != null) {
                gamepadView.setLongPressEnabled(isChecked);
            }
            Log.d(TAG, "Edit mode: " + (isChecked ? "enabled" : "disabled"));
        });

        // Done button for edit mode
        Button editDoneBtn = view.findViewById(R.id.edit_done_btn);
        editDoneBtn.setOnClickListener(v -> {
            gamepadView.setEditMode(false);
            editDoneBtn.setVisibility(View.GONE);
            View toggleRow = getView().findViewById(R.id.toggle_row);
            if (toggleRow != null) toggleRow.setVisibility(View.VISIBLE);
            Log.d(TAG, "Exited edit mode");
        });

        // Pass configured keycode and labels to the gamepad view
        if (gamepadView != null) {
            gamepadView.setButtonAKeyCode(buttonAKey);
            gamepadView.setButtonSizeScale(buttonSizeScale);
            gamepadView.setStickSizeScale(stickSizeScale);
            updateGamepadLabels();
        }

        setupListeners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void setupListeners() {
        // Button press listener (face buttons only, D-pad handled by dpadStateListener)
        gamepadView.setButtonPressListener((buttonId, keyCode) -> {
            Log.d(TAG, "Button pressed: " + buttonId + " -> keyCode: " + keyCode);

            if (keyCode == 1001) {
                sendMouseClick(1, true);
                gamepadView.postDelayed(() -> sendMouseClick(1, false), 100);
            } else if (keyCode == 1002) {
                sendMouseClick(2, true);
                gamepadView.postDelayed(() -> sendMouseClick(2, false), 100);
            } else {
                // Track only the pressed button
                if (buttonId.equals("button_a")) {
                    buttonAPressed = true;
                } else if (buttonId.equals("button_b")) {
                    buttonBPressed = true;
                }
                sendCombinedKeyReport();
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        });

        // Button release listener (face buttons, D-pad handled by dpadStateListener)
        gamepadView.setButtonReleaseListener((buttonId, keyCode) -> {
            if (keyCode == 1001 || keyCode == 1002) return;
            Log.d(TAG, "Button released: " + buttonId);
            // Track only the released button
            if (buttonId.equals("button_a")) {
                buttonAPressed = false;
            } else if (buttonId.equals("button_b")) {
                buttonBPressed = false;
            }
            sendCombinedKeyReport();
        });

        // D-pad state listener for hold behavior (multi-key support)
        gamepadView.setDpadStateListener(new DpadStateListener() {
            @Override
            public void onDpadStateChanged(int[] keyCodes) {
                // Update D-pad state booleans from the incoming keys
                keyUpPressed = false;
                keyLeftPressed = false;
                keyDownPressed = false;
                keyRightPressed = false;
                if (keyCodes != null) {
                    for (int kc : keyCodes) {
                        if (kc == stickUpKey) keyUpPressed = true;
                        else if (kc == stickLeftKey) keyLeftPressed = true;
                        else if (kc == stickDownKey) keyDownPressed = true;
                        else if (kc == stickRightKey) keyRightPressed = true;
                    }
                }
                // Send combined report including button A if pressed
                sendCombinedKeyReport();
            }
        });

        // Analog stick listener
        gamepadView.setAnalogStickListener((stickId, x, y) -> {
            Log.d(TAG, "Analog stick: " + stickId + " -> x: " + x + ", y: " + y);
            sendAnalogInput(stickId.toString(), x, y);
        });

        // Long press listener for config
        gamepadView.setComponentLongPressListener(componentId -> {
            Log.d(TAG, "Long press on: " + componentId);
            showLongPressMenu(componentId);
        });

        // Long press on empty area (in edit mode) to change background
        gamepadView.setEmptyAreaLongPressListener(() -> {
            Log.d(TAG, "Long press on empty area");
            showBackgroundPicker();
        });

        // Save positions when exiting edit mode
        gamepadView.setEditModeExitListener(positions -> {
            gamepadView.savePositions();
            Log.d(TAG, "Saved component positions");
        });
    }

    /**
     * Send a keyboard report with all currently pressed keys (D-pad + button A).
     * Modifier keys stored per-button are applied via the HID modifier byte,
     * allowing them to be combined with regular keys.
     */
    private void sendCombinedKeyReport() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm == null || !cm.isConnected()) {
                Log.w(TAG, "Not connected - cannot send combined key report");
                return;
            }

            // Combine modifier bits from all pressed buttons
            int modifiers = 0;
            if (buttonAPressed) modifiers |= buttonAModifiers;
            if (buttonBPressed) modifiers |= buttonBModifiers;

            // Build list of regular (non-modifier) keys
            java.util.List<Integer> regularKeys = new java.util.ArrayList<>();
            if (keyUpPressed) addKeyOrMod(regularKeys, stickUpKey);
            if (keyLeftPressed) addKeyOrMod(regularKeys, stickLeftKey);
            if (keyDownPressed) addKeyOrMod(regularKeys, stickDownKey);
            if (keyRightPressed) addKeyOrMod(regularKeys, stickRightKey);
            if (buttonAPressed) addKeyOrMod(regularKeys, buttonAKey);
            if (buttonBPressed) addKeyOrMod(regularKeys, buttonBKey);

            if (regularKeys.isEmpty() && modifiers == 0) {
                cm.sendKeyRelease();
            } else {
                int[] keyCodeArray = new int[regularKeys.size()];
                for (int i = 0; i < regularKeys.size(); i++) {
                    keyCodeArray[i] = regularKeys.get(i);
                }
                cm.sendKeyboardReport(modifiers, keyCodeArray);
            }
            Log.d(TAG, "Combined report: modifiers=0x" + Integer.toHexString(modifiers) +
                " keys=" + regularKeys +
                " (U=" + keyUpPressed + " L=" + keyLeftPressed +
                " D=" + keyDownPressed + " R=" + keyRightPressed +
                " A=" + buttonAPressed + " B=" + buttonBPressed + ")");
        }
    }

    /**
     * Add a keycode to the list if it's not a modifier key.
     */
    private void addKeyOrMod(java.util.List<Integer> keys, int keyCode) {
        if (!isModifierKey(keyCode)) {
            keys.add(keyCode);
        }
    }

    /**
     * Check if a HID keycode is a modifier key (Ctrl/Shift/Alt/GUI).
     */
    private boolean isModifierKey(int keyCode) {
        return keyCode >= 224 && keyCode <= 231;
    }

    /**
     * Get the HID modifier bit for a modifier keycode.
     */
    private int modifierBit(int keyCode) {
        switch (keyCode) {
            case 224: return 0x01; // Left Ctrl
            case 225: return 0x02; // Left Shift
            case 226: return 0x04; // Left Alt
            case 227: return 0x08; // Left GUI (Fn)
            case 228: return 0x10; // Right Ctrl
            case 229: return 0x20; // Right Shift
            case 230: return 0x40; // Right Alt
            case 231: return 0x80; // Right GUI
            default: return 0;
        }
    }

    // ── Config Dialogs ──────────────────────────────────────────────

    private void showLongPressMenu(String componentId) {
        String componentName;
        final boolean hasKeyMapping;
        if (componentId.startsWith("stick_")) {
            componentName = "Stick";
            hasKeyMapping = true;
        } else if (componentId.equals("button_a")) {
            componentName = "Button 1 (A)";
            hasKeyMapping = true;
        } else if (componentId.equals("button_b")) {
            componentName = "Button 2 (B)";
            hasKeyMapping = true;
        } else {
            componentName = componentId;
            hasKeyMapping = false;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(componentName);
        String[] options = {"Move", "Edit Key Mapping"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Move mode - enter edit mode briefly
                enterMoveMode(componentId);
            } else if (which == 1 && hasKeyMapping) {
                showConfigDialog(componentId);
            }
        });
        builder.show();
    }

    private void enterMoveMode(String componentId) {
        if (gamepadView != null) {
            gamepadView.setEditMode(true);
            // Hide the toggle row, show the done button
            View toggleRow = getView().findViewById(R.id.toggle_row);
            View doneBtn = getView().findViewById(R.id.edit_done_btn);
            if (toggleRow != null) toggleRow.setVisibility(View.GONE);
            if (doneBtn != null) doneBtn.setVisibility(View.VISIBLE);
            gamepadView.invalidate();
            Toast.makeText(requireContext(), R.string.gamepad_toast_edit_layout_hint, Toast.LENGTH_LONG).show();
        }
    }

    private void showConfigDialog(String componentId) {
        if (componentId.startsWith("stick_")) {
            showStickConfigDialog();
        } else if (componentId.equals("button_a")) {
            showButtonConfigDialog("Button A", buttonAKey, buttonAModifiers, DEFAULT_BUTTON_A, (key, mod) -> {
                buttonAKey = key;
                buttonAModifiers = mod;
                saveButtonConfig();
                updateGamepadLabels();
            });
        } else if (componentId.equals("button_b")) {
            showButtonConfigDialog("Button B", buttonBKey, buttonBModifiers, DEFAULT_BUTTON_B, (key, mod) -> {
                buttonBKey = key;
                buttonBModifiers = mod;
                saveButtonConfig();
            });
        }
    }

    private void showStickConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stick_config, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final RadioGroup modeGroup = dialogView.findViewById(R.id.stick_mode_group);
        final LinearLayout keySection = dialogView.findViewById(R.id.key_mapping_section);
        final Button keyUp = dialogView.findViewById(R.id.key_up);
        final Button keyLeft = dialogView.findViewById(R.id.key_left);
        final Button keyRight = dialogView.findViewById(R.id.key_right);
        final Button keyDown = dialogView.findViewById(R.id.key_down);

        // Set initial mode and key labels
        if (MODE_KEY.equals(stickMode)) {
            modeGroup.check(R.id.stick_mode_key);
        } else {
            modeGroup.check(R.id.stick_mode_analog);
        }
        updateKeySectionVisibility(modeGroup, keySection);
        updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);

        // Stick size seekbar
        android.widget.SeekBar sizeSeekbar = dialogView.findViewById(R.id.stick_size_seekbar);
        sizeSeekbar.setProgress((int) (stickSizeScale * 100));
        sizeSeekbar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                stickSizeScale = progress / 100f;
                if (gamepadView != null) {
                    gamepadView.setStickSizeScale(stickSizeScale);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // Mode change
        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
            updateKeySectionVisibility(group, keySection));

        // Cardinal key pickers
        keyUp.setOnClickListener(v -> showKeyPicker(keyUp, stickUpKey, code -> {
            stickUpKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyLeft.setOnClickListener(v -> showKeyPicker(keyLeft, stickLeftKey, code -> {
            stickLeftKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyDown.setOnClickListener(v -> showKeyPicker(keyDown, stickDownKey, code -> {
            stickDownKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));
        keyRight.setOnClickListener(v -> showKeyPicker(keyRight, stickRightKey, code -> {
            stickRightKey = code;
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
        }));

        // Reset
        dialogView.findViewById(R.id.stick_reset_btn).setOnClickListener(v -> {
            stickMode = MODE_KEY;
            stickUpKey = DEFAULT_STICK_UP;
            stickLeftKey = DEFAULT_STICK_LEFT;
            stickDownKey = DEFAULT_STICK_DOWN;
            stickRightKey = DEFAULT_STICK_RIGHT;
            stickSizeScale = 1.0f;
            modeGroup.check(R.id.stick_mode_key);
            updateKeySectionVisibility(modeGroup, keySection);
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
            sizeSeekbar.setProgress(100);
            if (gamepadView != null) {
                gamepadView.setStickSizeScale(1.0f);
            }
        });

        // Done
        dialogView.findViewById(R.id.stick_done_btn).setOnClickListener(v -> {
            stickMode = modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key ? MODE_KEY : MODE_ANALOG;
            saveStickConfig();
            updateGamepadLabels();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateKeyLabels(Button up, Button left, Button right, Button down) {
        up.setText(keyCodeToLabel(stickUpKey));
        left.setText(keyCodeToLabel(stickLeftKey));
        down.setText(keyCodeToLabel(stickDownKey));
        right.setText(keyCodeToLabel(stickRightKey));
    }

    private void showButtonConfigDialog(String buttonName, int currentKey, int currentModifiers, int defaultKey, DualKeyModSelectedListener onSave) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_button_config, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(buttonName + " Configuration");

        final int[] selectedKey = { currentKey };
        final int[] selectedModifiers = { currentModifiers };

        final Button keyLabel = dialogView.findViewById(R.id.button_key_label);
        updateButtonLabel(keyLabel, currentKey, currentModifiers);

        // Button size seekbar
        android.widget.SeekBar sizeSeekbar = dialogView.findViewById(R.id.button_size_seekbar);
        sizeSeekbar.setProgress((int) (buttonSizeScale * 100));
        sizeSeekbar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                buttonSizeScale = progress / 100f;
                if (gamepadView != null) {
                    gamepadView.setButtonSizeScale(buttonSizeScale);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // Build modifier checkboxes row above the key label
        LinearLayout modifierRow = new LinearLayout(requireContext());
        modifierRow.setOrientation(LinearLayout.HORIZONTAL);
        modifierRow.setGravity(android.view.Gravity.CENTER);
        android.widget.CheckBox[] modifierChecks = new android.widget.CheckBox[MODIFIER_KEYS.length];
        for (int i = 0; i < MODIFIER_KEYS.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(requireContext());
            cb.setText(MODIFIER_KEYS[i][0]);
            cb.setTextColor(0xFFFFFFFF);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            int bit = modifierBit(Integer.parseInt(MODIFIER_KEYS[i][1]));
            cb.setChecked((currentModifiers & bit) != 0);
            cb.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    selectedModifiers[0] |= bit;
                } else {
                    selectedModifiers[0] &= ~bit;
                }
                updateButtonLabel(keyLabel, selectedKey[0], selectedModifiers[0]);
            });
            modifierChecks[i] = cb;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), dp(12));
            modifierRow.addView(cb, lp);
        }

        // Insert modifier row before the key label
        LinearLayout parent = (LinearLayout) keyLabel.getParent();
        parent.addView(modifierRow, parent.indexOfChild(keyLabel));

        keyLabel.setOnClickListener(v -> showKeyPickerWithModifiers(keyLabel, selectedKey[0], selectedModifiers[0], (key, mod) -> {
            selectedKey[0] = key;
            selectedModifiers[0] = mod;
            updateButtonLabel(keyLabel, key, mod);
            // Update checkbox states to match the picked combination
            for (int i = 0; i < MODIFIER_KEYS.length; i++) {
                int bit = modifierBit(Integer.parseInt(MODIFIER_KEYS[i][1]));
                modifierChecks[i].setChecked((mod & bit) != 0);
            }
        }));

        dialogView.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            selectedKey[0] = defaultKey;
            selectedModifiers[0] = 0;
            updateButtonLabel(keyLabel, defaultKey, 0);
            for (android.widget.CheckBox cb : modifierChecks) {
                cb.setChecked(false);
            }
        });

        dialogView.findViewById(R.id.btn_done).setOnClickListener(v -> {
            onSave.accept(selectedKey[0], selectedModifiers[0]);
            saveButtonConfig();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateButtonLabel(Button label, int key, int modifiers) {
        StringBuilder sb = new StringBuilder(keyCodeToLabel(key));
        if (modifiers != 0) {
            sb.insert(0, "+");
            if ((modifiers & 0x01) != 0) sb.insert(0, "Ctrl");
            if ((modifiers & 0x02) != 0) { if (sb.length() > 1) sb.insert(0, "+"); sb.insert(0, "Shift"); }
            if ((modifiers & 0x04) != 0) { if (sb.length() > 1) sb.insert(0, "+"); sb.insert(0, "Alt"); }
            if ((modifiers & 0x08) != 0) { if (sb.length() > 1) sb.insert(0, "+"); sb.insert(0, "Fn"); }
        }
        label.setText(sb.toString());
    }

    private void showKeyPicker(Button displayButton, int currentKeyCode, java.util.function.IntConsumer listener) {
        buildKeyPickerDialog(currentKeyCode, 0, keyInfo -> {
            listener.accept(keyInfo.keyCode);
            displayButton.setText(keyInfo.label);
        });
    }

    private void showKeyPickerWithModifiers(Button displayButton, int currentKeyCode, int currentModifiers,
                                            DualKeyModSelectedListener listener) {
        buildKeyPickerDialog(currentKeyCode, currentModifiers, keyInfo -> {
            listener.accept(keyInfo.keyCode, keyInfo.modifiers);
            updateButtonLabel(displayButton, keyInfo.keyCode, keyInfo.modifiers);
        });
    }

    private void buildKeyPickerDialog(int initialKeyCode, int initialModifiers, final KeySelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Key");

        final int[] allKeyCodes = new int[KEY_OPTIONS.length];
        final Button[] allButtons = new Button[KEY_OPTIONS.length];
        final int[] selectedKeyCode = { initialKeyCode };
        final int[] selectedModifiers = { initialModifiers };
        final String[] selectedLabel = { keyCodeToLabel(initialKeyCode) };

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, dp(8));

        // Modifier checkboxes at the top
        LinearLayout modifierRow = new LinearLayout(requireContext());
        modifierRow.setOrientation(LinearLayout.HORIZONTAL);
        modifierRow.setGravity(android.view.Gravity.CENTER);
        modifierRow.setPadding(0, 0, 0, dp(12));
        final android.widget.CheckBox[] modChecks = new android.widget.CheckBox[MODIFIER_KEYS.length];
        for (int i = 0; i < MODIFIER_KEYS.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(requireContext());
            cb.setText(MODIFIER_KEYS[i][0]);
            cb.setTextColor(0xFFAAAAAA);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            int bit = modifierBit(Integer.parseInt(MODIFIER_KEYS[i][1]));
            cb.setChecked((initialModifiers & bit) != 0);
            cb.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    selectedModifiers[0] |= bit;
                } else {
                    selectedModifiers[0] &= ~bit;
                }
            });
            modChecks[i] = cb;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(6), 0, dp(6), 0);
            modifierRow.addView(cb, lp);
        }
        container.addView(modifierRow);

        // Number section: two rows (normal 1-9 and numpad Num1-Num9)
        TextView numLabel = new TextView(requireContext());
        numLabel.setText("Number");
        numLabel.setTextColor(0xFFAAAAAA);
        numLabel.setTextSize(12);
        LinearLayout.LayoutParams numLabelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        numLabelLp.setMargins(0, dp(4), 0, dp(4));
        container.addView(numLabel, numLabelLp);

        // HID keycodes: normal digits 1-9 = 30-38, numpad 1-9 = 89-97
        final int[] normalNumCodes = {30, 31, 32, 33, 34, 35, 36, 37, 38};
        final int[] numpadNumCodes = {89, 90, 91, 92, 93, 94, 95, 96, 97};

        LinearLayout normalRow = new LinearLayout(requireContext());
        normalRow.setOrientation(LinearLayout.HORIZONTAL);
        normalRow.setGravity(android.view.Gravity.CENTER);
        normalRow.setPadding(0, 0, 0, dp(4));

        LinearLayout numpadRow = new LinearLayout(requireContext());
        numpadRow.setOrientation(LinearLayout.HORIZONTAL);
        numpadRow.setGravity(android.view.Gravity.CENTER);
        numpadRow.setPadding(0, 0, 0, dp(12));

        Button[] normalNumBtns = new Button[9];
        Button[] numpadNumBtns = new Button[9];

        // Determine if initial key is a number
        int initialNumIdx = -1;
        boolean initialIsNumpad = false;
        if (initialKeyCode >= 30 && initialKeyCode <= 38) {
            initialNumIdx = initialKeyCode - 30;
            initialIsNumpad = false;
        } else if (initialKeyCode >= 89 && initialKeyCode <= 97) {
            initialNumIdx = initialKeyCode - 89;
            initialIsNumpad = true;
        }

        for (int n = 0; n < 9; n++) {
            final int num = n + 1;
            final int normalCode = normalNumCodes[n];
            final int numpadCode = numpadNumCodes[n];

            // Normal number button
            Button normalBtn = new Button(requireContext());
            normalBtn.setText(String.valueOf(num));
            normalBtn.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable normalBg = new android.graphics.drawable.GradientDrawable();
            normalBg.setColor((initialNumIdx == n && !initialIsNumpad) ? 0xFF4CAF50 : 0xFF444444);
            normalBg.setCornerRadius(dp(6));
            normalBtn.setBackground(normalBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.setMargins(dp(4), 0, dp(2), 0);
            normalBtn.setLayoutParams(lp);
            normalBtn.setPadding(0, 0, 0, 0);
            normalBtn.setTextSize(14);
            normalNumBtns[n] = normalBtn;

            // Numpad number button
            Button numpadBtn = new Button(requireContext());
            numpadBtn.setText("Num" + (n + 1));
            numpadBtn.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable numpadBg = new android.graphics.drawable.GradientDrawable();
            numpadBg.setColor((initialNumIdx == n && initialIsNumpad) ? 0xFF4CAF50 : 0xFF444444);
            numpadBg.setCornerRadius(dp(6));
            numpadBg.setStroke((initialNumIdx == n && initialIsNumpad) ? dp(2) : 0,
                (initialNumIdx == n && initialIsNumpad) ? 0xFF81C784 : 0x00000000);
            numpadBtn.setBackground(numpadBg);
            LinearLayout.LayoutParams numpadLp = new LinearLayout.LayoutParams(0, dp(40), 1);
            numpadLp.setMargins(dp(4), 0, dp(2), 0);
            numpadBtn.setLayoutParams(numpadLp);
            numpadBtn.setPadding(0, 0, 0, 0);
            numpadBtn.setTextSize(14);
            numpadNumBtns[n] = numpadBtn;

            normalBtn.setOnClickListener(v -> {
                selectedKeyCode[0] = normalCode;
                selectedLabel[0] = String.valueOf(num);
                updateNumButtons(normalNumBtns, numpadNumBtns, num, true);
                // Deselect all character key buttons
                for (int k = 0; k < allButtons.length; k++) {
                    if (allButtons[k] == null) continue;
                    ((android.graphics.drawable.GradientDrawable) allButtons[k].getBackground())
                        .setColor(0xFF444444);
                }
            });
            numpadBtn.setOnClickListener(v -> {
                selectedKeyCode[0] = numpadCode;
                selectedLabel[0] = "Num" + num;
                updateNumButtons(normalNumBtns, numpadNumBtns, num, false);
                // Deselect all character key buttons
                for (int k = 0; k < allButtons.length; k++) {
                    if (allButtons[k] == null) continue;
                    ((android.graphics.drawable.GradientDrawable) allButtons[k].getBackground())
                        .setColor(0xFF444444);
                }
            });

            normalRow.addView(normalBtn);
            numpadRow.addView(numpadBtn);
        }
        container.addView(normalRow);
        container.addView(numpadRow);

        // Key grid
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < KEY_OPTIONS.length; i += 4) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = 0; j < 4; j++) {
                if (i + j >= KEY_OPTIONS.length) break;
                String[] opt = KEY_OPTIONS[i + j];
                final int idx = i + j;
                Button btn = new Button(requireContext());
                btn.setText(opt[0]);
                btn.setTextColor(0xFFFFFFFF);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                final int keyCode = Integer.parseInt(opt[1]);
                allKeyCodes[idx] = keyCode;
                boolean isSelected = keyCode == initialKeyCode && selectedModifiers[0] == initialModifiers;
                bg.setColor(isSelected ? 0xFF4CAF50 : 0xFF444444);
                bg.setCornerRadius(dp(6));
                btn.setBackground(bg);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
                int dp8 = dp(8);
                params.setMargins(dp8, dp8, dp8, dp8);
                btn.setLayoutParams(params);
                allButtons[idx] = btn;

                btn.setOnClickListener(v -> {
                    selectedKeyCode[0] = keyCode;
                    selectedLabel[0] = opt[0];
                    for (int k = 0; k < allButtons.length; k++) {
                        if (allButtons[k] == null) continue;
                        android.graphics.drawable.GradientDrawable b =
                            (android.graphics.drawable.GradientDrawable) allButtons[k].getBackground();
                        b.setColor(allKeyCodes[k] == keyCode ? 0xFF4CAF50 : 0xFF444444);
                    }
                    // Deselect all number buttons
                    for (int k = 0; k < normalNumBtns.length; k++) {
                        android.graphics.drawable.GradientDrawable nb =
                            (android.graphics.drawable.GradientDrawable) normalNumBtns[k].getBackground();
                        nb.setColor(0xFF444444);
                        nb.setStroke(0, 0x00000000);
                    }
                    for (int k = 0; k < numpadNumBtns.length; k++) {
                        android.graphics.drawable.GradientDrawable nb =
                            (android.graphics.drawable.GradientDrawable) numpadNumBtns[k].getBackground();
                        nb.setColor(0xFF444444);
                        nb.setStroke(0, 0x00000000);
                    }
                });
                row.addView(btn);
            }
            grid.addView(row);
        }
        container.addView(grid);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(container);
        builder.setView(scrollView);

        builder.setPositiveButton("OK", (d, w) -> {
            // Append modifier labels to the key label for display
            String displayLabel = selectedLabel[0];
            if (selectedModifiers[0] != 0) {
                displayLabel = keyCodeToLabel(selectedKeyCode[0]);
            }
            listener.onKeySelected(new KeyInfo(selectedKeyCode[0], displayLabel, selectedModifiers[0]));
        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateNumButtons(Button[] normalBtns, Button[] numpadBtns, int num, boolean isNormal) {
        for (int i = 0; i < normalBtns.length; i++) {
            boolean isSelected = (i + 1) == num && isNormal;
            android.graphics.drawable.GradientDrawable bg =
                (android.graphics.drawable.GradientDrawable) normalBtns[i].getBackground();
            bg.setColor(isSelected ? 0xFF4CAF50 : 0xFF444444);
            bg.setStroke(0, 0x00000000);
        }
        for (int i = 0; i < numpadBtns.length; i++) {
            boolean isSelected = (i + 1) == num && !isNormal;
            android.graphics.drawable.GradientDrawable bg =
                (android.graphics.drawable.GradientDrawable) numpadBtns[i].getBackground();
            bg.setColor(isSelected ? 0xFF4CAF50 : 0xFF444444);
            bg.setStroke(isSelected ? dp(2) : 0, isSelected ? 0xFF81C784 : 0x00000000);
        }
    }

    private void updateKeySectionVisibility(RadioGroup modeGroup, LinearLayout keySection) {
        keySection.setVisibility(modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key
            ? View.VISIBLE : View.GONE);
    }

    // ── Persistence ─────────────────────────────────────────────────

    private void loadStickConfig() {
        stickMode = prefs.getString(PREF_STICK_MODE, MODE_KEY);
        stickUpKey = prefs.getInt(PREF_STICK_UP, DEFAULT_STICK_UP);
        stickLeftKey = prefs.getInt(PREF_STICK_LEFT, DEFAULT_STICK_LEFT);
        stickDownKey = prefs.getInt(PREF_STICK_DOWN, DEFAULT_STICK_DOWN);
        stickRightKey = prefs.getInt(PREF_STICK_RIGHT, DEFAULT_STICK_RIGHT);
        stickSizeScale = prefs.getFloat(PREF_STICK_SIZE, 1.0f);
    }

    private void saveStickConfig() {
        prefs.edit()
            .putString(PREF_STICK_MODE, stickMode)
            .putInt(PREF_STICK_UP, stickUpKey)
            .putInt(PREF_STICK_LEFT, stickLeftKey)
            .putInt(PREF_STICK_DOWN, stickDownKey)
            .putInt(PREF_STICK_RIGHT, stickRightKey)
            .putFloat(PREF_STICK_SIZE, stickSizeScale)
            .apply();
    }

    private void loadButtonConfig() {
        buttonAKey = prefs.getInt(PREF_BUTTON_A_KEY, DEFAULT_BUTTON_A);
        buttonBKey = prefs.getInt(PREF_BUTTON_B_KEY, DEFAULT_BUTTON_B);
        buttonAModifiers = prefs.getInt(PREF_BUTTON_A_MOD, 0);
        buttonBModifiers = prefs.getInt(PREF_BUTTON_B_MOD, 0);
        buttonSizeScale = prefs.getFloat(PREF_BUTTON_SIZE, 1.0f);
    }

    private void saveButtonConfig() {
        prefs.edit()
            .putInt(PREF_BUTTON_A_KEY, buttonAKey)
            .putInt(PREF_BUTTON_B_KEY, buttonBKey)
            .putInt(PREF_BUTTON_A_MOD, buttonAModifiers)
            .putInt(PREF_BUTTON_B_MOD, buttonBModifiers)
            .putFloat(PREF_BUTTON_SIZE, buttonSizeScale)
            .apply();
        updateGamepadLabels();
    }

    // ── Label Sync ──────────────────────────────────────────────────

    private void updateGamepadLabels() {
        if (gamepadView == null) return;
        Map<String, String> labels = new HashMap<>();
        labels.put("stick_up", keyCodeToLabel(stickUpKey));
        labels.put("stick_down", keyCodeToLabel(stickDownKey));
        labels.put("stick_left", keyCodeToLabel(stickLeftKey));
        labels.put("stick_right", keyCodeToLabel(stickRightKey));
        labels.put("button_a", buildFullLabel(buttonAKey, buttonAModifiers));
        labels.put("button_b", buildFullLabel(buttonBKey, buttonBModifiers));
        gamepadView.setComponentDisplayLabels(labels);
        gamepadView.setButtonAKeyCode(buttonAKey);
    }

    private String buildFullLabel(int key, int modifiers) {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & 0x02) != 0) sb.append("Shift+");
        if ((modifiers & 0x01) != 0) sb.append("Ctrl+");
        if ((modifiers & 0x04) != 0) sb.append("Alt+");
        if ((modifiers & 0x08) != 0) sb.append("Fn+");
        sb.append(keyCodeToLabel(key));
        return sb.toString();
    }

    // ── Input handling ──────────────────────────────────────────────

    private void sendKeyEvent(int keyCode) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyEvent(0, keyCode);
                Log.d(TAG, "Sent key event: " + keyCode);
            } else {
                Log.w(TAG, "Not connected - cannot send key event");
            }
        }
    }

    private void sendKeyRelease() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendKeyRelease();
                Log.d(TAG, "Sent key release");
            }
        }
    }

    private void sendMouseClick(int button, boolean press) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendMouseClick(button, press);
            }
        }
    }

    private void sendAnalogInput(String stickId, float x, float y) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager connectionManager =
                ((MainActivity) getActivity()).getConnectionManager();
            boolean isConnected = connectionManager != null && connectionManager.isConnected();

            if (stickId.equals("r")) {
                if (isConnected) sendRightStickMouse(connectionManager, x, y);
            } else if (stickId.equals("l")) {
                if (MODE_KEY.equals(stickMode)) {
                    sendLeftStickKeys(connectionManager, x, y, isConnected);
                } else {
                    if (isConnected) sendLeftStickMouse(connectionManager, x, y);
                }
            }
        }
    }

    private void sendRightStickMouse(ConnectionManager cm, float x, float y) {
        float deadZone = 0.15f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) return;

        xAdj *= mouseSensitivity;
        yAdj *= mouseSensitivity;
        xAdj = Math.max(-1.0f, Math.min(1.0f, xAdj));
        yAdj = Math.max(-1.0f, Math.min(1.0f, yAdj));

        cm.sendMouseMovement((int)(xAdj * 127), (int)(yAdj * 127), 0);
    }

    private void sendLeftStickMouse(ConnectionManager cm, float x, float y) {
        float deadZone = 0.15f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) return;

        xAdj *= mouseSensitivity * 0.6f;
        yAdj *= mouseSensitivity * 0.6f;
        xAdj = Math.max(-1.0f, Math.min(1.0f, xAdj));
        yAdj = Math.max(-1.0f, Math.min(1.0f, yAdj));

        cm.sendMouseMovement((int)(xAdj * 127), (int)(yAdj * 127), 0);
    }

    private void sendLeftStickKeys(ConnectionManager cm, float x, float y, boolean isConnected) {
        float deadZone = 0.2f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);

        boolean wantUp = yAdj < -0.3f;
        boolean wantDown = yAdj > 0.3f;
        boolean wantLeft = xAdj < -0.3f;
        boolean wantRight = xAdj > 0.3f;

        boolean changed = false;

        // Up key
        if (wantUp != keyUpPressed) { keyUpPressed = wantUp; changed = true; }
        // Left key
        if (wantLeft != keyLeftPressed) { keyLeftPressed = wantLeft; changed = true; }
        // Down key
        if (wantDown != keyDownPressed) { keyDownPressed = wantDown; changed = true; }
        // Right key
        if (wantRight != keyRightPressed) { keyRightPressed = wantRight; changed = true; }

        // Send combined report with all active keys (including button A)
        if (changed) {
            sendCombinedKeyReport();
        }

        // Update stick direction labels highlighting
        java.util.Set<String> activeDirs = new java.util.HashSet<>();
        if (keyUpPressed) activeDirs.add("up");
        if (keyDownPressed) activeDirs.add("down");
        if (keyLeftPressed) activeDirs.add("left");
        if (keyRightPressed) activeDirs.add("right");
        if (activeDirs.isEmpty()) {
            gamepadView.clearStickDirections("l");
        } else {
            gamepadView.setActiveStickDirections("l", activeDirs);
        }

        if (xAdj != 0 || yAdj != 0) {
            Log.d(TAG, "Stick keys pressed: " +
                (wantUp ? "U " : ". ") + (wantDown ? "D " : ". ") +
                (wantLeft ? "L " : ". ") + (wantRight ? "R" : "."));
        }
    }

    private float applyDeadZone(float value, float deadZone) {
        if (Math.abs(value) < deadZone) return 0;
        return (value - Math.copySign(deadZone, value)) / (1.0f - deadZone);
    }

    // ── Mouse sensitivity ───────────────────────────────────────────

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.5f, Math.min(2.0f, sensitivity));
        prefs.edit().putFloat("mouse_sensitivity", this.mouseSensitivity).apply();
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    private void loadSavedSensitivity() {
        mouseSensitivity = prefs.getFloat("mouse_sensitivity", 1.0f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vibrator != null) vibrator.cancel();
        releaseAllKeys();
    }

    private void releaseAllKeys() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected() && (keyUpPressed || keyLeftPressed || keyDownPressed || keyRightPressed || buttonAPressed)) {
                cm.sendKeyRelease();
            }
        }
        keyUpPressed = false;
        keyLeftPressed = false;
        keyDownPressed = false;
        keyRightPressed = false;
        buttonAPressed = false;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String keyCodeToLabel(int keyCode) {
        for (String[] opt : KEY_OPTIONS) {
            if (Integer.parseInt(opt[1]) == keyCode) return opt[0];
        }
        // Normal digits 1-9 (HID 30-38)
        if (keyCode >= 30 && keyCode <= 38) return String.valueOf(keyCode - 29);
        // Numpad digits 1-9 (HID 89-97)
        if (keyCode >= 89 && keyCode <= 97) return "Num" + (keyCode - 88);
        return String.valueOf(keyCode);
    }

    // ── Background Image ────────────────────────────────────────────

    private static final int PICK_BG_IMAGE = 1001;
    private String currentBgPath = null;

    private void loadBackground() {
        currentBgPath = prefs.getString(PREF_BG_IMAGE, null);
        if (currentBgPath != null) {
            java.io.File file = new java.io.File(requireContext().getFilesDir(), currentBgPath);
            if (file.exists()) {
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bm != null && gamepadView != null) {
                    gamepadView.setBackgroundBitmap(bm);
                    // Restore viewport
                    float scale = prefs.getFloat(PREF_BG_SCALE, 1.0f);
                    float offsetX = prefs.getFloat(PREF_BG_OFFSET_X, 0f);
                    float offsetY = prefs.getFloat(PREF_BG_OFFSET_Y, 0f);
                    gamepadView.setBackgroundViewport(scale, offsetX, offsetY);
                }
            }
        }
        if (gamepadView != null) {
            gamepadView.setBackgroundChangedCallback(() -> {
                android.graphics.Bitmap bm = gamepadView.getBackgroundBitmap();
                if (bm != null) {
                    // Save bitmap to files dir
                    try {
                        java.io.File dir = requireContext().getFilesDir();
                        java.io.File file = new java.io.File(dir, "gamepad_bg.png");
                        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                        bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        out.flush();
                        out.close();
                        prefs.edit().putString(PREF_BG_IMAGE, "gamepad_bg.png").apply();
                        currentBgPath = "gamepad_bg.png";
                    } catch (java.io.IOException e) {
                        Log.e(TAG, "Failed to save background image", e);
                    }
                } else {
                    prefs.edit().remove(PREF_BG_IMAGE).apply();
                    prefs.edit().remove(PREF_BG_SCALE).apply();
                    prefs.edit().remove(PREF_BG_OFFSET_X).apply();
                    prefs.edit().remove(PREF_BG_OFFSET_Y).apply();
                    currentBgPath = null;
                }
            });
            gamepadView.setBackgroundViewportCallback(() -> {
                prefs.edit()
                    .putFloat(PREF_BG_SCALE, gamepadView.getBackgroundScale())
                    .putFloat(PREF_BG_OFFSET_X, gamepadView.getBackgroundOffsetX())
                    .putFloat(PREF_BG_OFFSET_Y, gamepadView.getBackgroundOffsetY())
                    .apply();
            });
        }
    }

    private void saveBackground() {
        // Triggered by the callback
    }

    private void showBackgroundPicker() {
        String[] options = {"Pick from Gallery", "Remove Background"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Background Image");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Open image picker
                android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_BG_IMAGE);
            } else {
                // Remove background
                gamepadView.setBackgroundBitmap(null);
                if (currentBgPath != null) {
                    java.io.File file = new java.io.File(requireContext().getFilesDir(), currentBgPath);
                    if (file.exists()) file.delete();
                    prefs.edit().remove(PREF_BG_IMAGE).apply();
                    currentBgPath = null;
                }
            }
        });
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BG_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    android.graphics.Bitmap bm = android.provider.MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), imageUri);
                    if (bm != null && gamepadView != null) {
                        gamepadView.setBackgroundBitmap(bm);
                    }
                } catch (java.io.IOException e) {
                    Log.e(TAG, "Failed to load background image", e);
                    Toast.makeText(requireContext(), R.string.gamepad_toast_image_load_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ── Interfaces ──────────────────────────────────────────────────

    private interface KeySelectedListener {
        void onKeySelected(KeyInfo keyInfo);
    }

    private interface DualKeySelectedListener {
        void onKeysSelected(int key1, int key2);
    }

    private interface DualKeyModSelectedListener {
        void accept(int key, int modifiers);
    }

    private static class KeyInfo {
        final int keyCode;
        final String label;
        final int modifiers;
        KeyInfo(int keyCode, String label) {
            this(keyCode, label, 0);
        }
        KeyInfo(int keyCode, String label, int modifiers) {
            this.keyCode = keyCode;
            this.label = label;
            this.modifiers = modifiers;
        }
    }
}
