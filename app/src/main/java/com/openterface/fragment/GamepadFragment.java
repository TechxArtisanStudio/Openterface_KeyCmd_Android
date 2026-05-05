package com.openterface.fragment;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import android.content.pm.ActivityInfo;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;
import com.openterface.keymod.gamepad.GamepadLayoutDocEditor;
import com.openterface.keymod.gamepad.GamepadLayoutDocumentStore;
import com.openterface.keymod.gamepad.GamepadLayoutPresetApplier;
import com.openterface.keymod.gamepad.GamepadLayoutPresetConstants;
import com.openterface.keymod.gamepad.GamepadLayoutPresetDocument;
import com.openterface.keymod.gamepad.GamepadLayoutPresetRepository;
import com.openterface.keymod.gamepad.GamepadLayoutPresetSnapshotBuilder;
import com.openterface.keymod.gamepad.GamepadPreferenceKeys;
import com.openterface.keymod.GamepadView.ComponentLongPressListener;
import com.openterface.keymod.GamepadView.DpadStateListener;

/**
 * Gamepad Fragment - Simple layout: left stick + right button
 */
public class GamepadFragment extends Fragment {

    private static final String TAG = "GamepadFragment";

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
    /** Extra gain for right-stick mouse (layout or prefs); 1.0 = built-in default curve only. */
    private float rightStickMouseGain = 1.0f;

    // Stick config
    private String stickMode = MODE_KEY;
    private int stickUpKey = DEFAULT_STICK_UP;
    private int stickLeftKey = DEFAULT_STICK_LEFT;
    private int stickDownKey = DEFAULT_STICK_DOWN;
    private int stickRightKey = DEFAULT_STICK_RIGHT;
    private float stickSizeScale = 1.0f;

    /** Which stick module id is being edited in the stick dialog ({@code stick_left} / {@code stick_right}). */
    private String stickConfigModuleId = "stick_left";
    /** Cardinal HID keys for {@code stick_right} (I/J/K/L defaults). */
    private int rightStickUpKey = 12;
    private int rightStickLeftKey = 13;
    private int rightStickDownKey = 14;
    private int rightStickRightKey = 15;
    /** Optional third STICK_KEY module (defaults: arrow HID usages). */
    private int extraStickUpKey = 82;
    private int extraStickLeftKey = 80;
    private int extraStickDownKey = 81;
    private int extraStickRightKey = 79;

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

    private GamepadLayoutPresetDocument layoutDoc;
    private final Map<String, Boolean> faceButtonPressed = new HashMap<>();
    private boolean keyRUpPressed;
    private boolean keyRLeftPressed;
    private boolean keyRDownPressed;
    private boolean keyRRightPressed;
    private boolean keyEUpPressed;
    private boolean keyELeftPressed;
    private boolean keyEDownPressed;
    private boolean keyERightPressed;

    private GamepadLayoutPresetRepository presetRepository;
    private ActivityResultLauncher<String[]> importPresetLauncher;

    @Nullable
    private TextView activePresetNameView;
    @Nullable
    private MaterialButton editModeMaterialButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presetRepository = new GamepadLayoutPresetRepository(requireContext());
        presetRepository.ensureMigratedFromLegacy();
        importPresetLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    String err = presetRepository.importFromUri(uri, true);
                    if (err == null) {
                        reloadFromPrefsAndApplyView();
                        Toast.makeText(requireContext(), R.string.gamepad_presets_import_ok, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                getString(R.string.gamepad_presets_import_fail, err),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gamepad, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        loadSavedSensitivity();
        layoutDoc = GamepadLayoutDocumentStore.loadOrCreate(requireContext());
        syncFieldsFromLayoutDoc();

        gamepadView = view.findViewById(R.id.gamepad_view);
        currentLayout = GamepadLayout.SIMPLE;
        gamepadView.setLayout(currentLayout);
        syncGamepadViewFromDoc();

        loadBackground();

        if (gamepadView != null) {
            gamepadView.setShowTwoButtons(twoButtonMode);
        }

        activePresetNameView = view.findViewById(R.id.gamepad_active_preset_name);

        MaterialButton presetsBtn = view.findViewById(R.id.gamepad_presets_btn);
        presetsBtn.setOnClickListener(v -> cycleToNextPreset());
        presetsBtn.setOnLongClickListener(v -> {
            showGamepadPresetsMenu();
            return true;
        });

        editModeMaterialButton = view.findViewById(R.id.edit_mode_toggle);
        editModeMaterialButton.addOnCheckedChangeListener((button, isChecked) -> {
            button.setText(isChecked
                    ? getString(R.string.gamepad_edit_mode_on)
                    : getString(R.string.gamepad_edit_mode_off));
            if (gamepadView != null) {
                gamepadView.setLongPressEnabled(isChecked);
                gamepadView.setEditMode(isChecked);
            }
            Log.d(TAG, "Edit mode: " + (isChecked ? "enabled" : "disabled"));
        });
        // Listener does not run for initial unchecked state; align view with play mode.
        if (gamepadView != null) {
            gamepadView.setLongPressEnabled(editModeMaterialButton.isChecked());
            gamepadView.setEditMode(editModeMaterialButton.isChecked());
        }

        MaterialButton editDoneBtn = view.findViewById(R.id.edit_done_btn);
        editDoneBtn.setOnClickListener(v -> {
            if (editModeMaterialButton != null) {
                editModeMaterialButton.setChecked(false);
            }
            editDoneBtn.setVisibility(View.GONE);
            View toggleRow = getView().findViewById(R.id.toggle_row);
            if (toggleRow != null) {
                toggleRow.setVisibility(View.VISIBLE);
            }
            Log.d(TAG, "Exited edit mode");
        });

        setupListeners();
        updateActivePresetNameUi();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Allow both landscape directions; LANDSCAPE alone locks to one side only.
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
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

            Integer mouseBtn = mouseButtonForComponentId(buttonId);
            if (mouseBtn != null) {
                sendMouseClick(semanticMouseButtonToHidMask(mouseBtn), true);
                return;
            }
            if (keyCode == 1001) {
                sendMouseClick(1, true);
                gamepadView.postDelayed(() -> sendMouseClick(1, false), 100);
            } else if (keyCode == 1002) {
                sendMouseClick(2, true);
                gamepadView.postDelayed(() -> sendMouseClick(2, false), 100);
            } else {
                if (buttonId != null && buttonId.startsWith("button_")) {
                    faceButtonPressed.put(buttonId, true);
                    if ("button_a".equals(buttonId)) {
                        buttonAPressed = true;
                    } else if ("button_b".equals(buttonId)) {
                        buttonBPressed = true;
                    }
                }
                sendCombinedKeyReport();
                if (vibrator != null && vibrator.hasVibrator()
                        && buttonId != null && buttonId.startsWith("button_")) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        });

        // Button release listener (face buttons, D-pad handled by dpadStateListener)
        gamepadView.setButtonReleaseListener((buttonId, keyCode) -> {
            if (keyCode == 1001 || keyCode == 1002) return;
            Integer mouseBtn = mouseButtonForComponentId(buttonId);
            if (mouseBtn != null) {
                sendMouseClick(semanticMouseButtonToHidMask(mouseBtn), false);
                return;
            }
            Log.d(TAG, "Button released: " + buttonId);
            if (buttonId != null && buttonId.startsWith("button_")) {
                faceButtonPressed.put(buttonId, false);
                if ("button_a".equals(buttonId)) {
                    buttonAPressed = false;
                } else if ("button_b".equals(buttonId)) {
                    buttonBPressed = false;
                }
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
            showEditBackgroundAndModulesMenu();
        });

        // Save positions when exiting edit mode
        gamepadView.setEditModeExitListener(positions -> {
            gamepadView.savePositions();
            if (layoutDoc != null) {
                mergeViewPositionsIntoLayoutDoc(positions);
                try {
                    GamepadLayoutPresetApplier.apply(requireContext(), layoutDoc);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Could not persist layout after edit", e);
                }
                syncFieldsFromLayoutDoc();
                syncGamepadViewFromDoc();
            }
            persistActivePresetSnapshot();
            Log.d(TAG, "Saved component positions");
        });
    }

    private void persistActivePresetSnapshot() {
        if (presetRepository != null) {
            presetRepository.persistActiveSnapshot();
        }
    }

    @Nullable
    private GamepadLayoutPresetDocument.GamepadModule findModuleById(String id) {
        if (layoutDoc == null || layoutDoc.modules == null || id == null) {
            return null;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }

    @Nullable
    private Integer mouseButtonForComponentId(@Nullable String componentId) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(componentId);
        if (m == null || !GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type)) {
            return null;
        }
        return m.mouseButton;
    }

    /**
     * Preset layout stores mouse buttons as 1=left, 2=middle, 3=right.
     * HID relative-mouse reports use a bitmask (left=1, right=2, middle=4).
     */
    private static int semanticMouseButtonToHidMask(int semantic) {
        switch (semantic) {
            case 1:
                return 1;
            case 2:
                return 4;
            case 3:
                return 2;
            default:
                return 0;
        }
    }

    private static int intOr(@Nullable Integer v, int def) {
        return v != null ? v : def;
    }

    private void loadStickModuleIntoEditorState(String moduleId) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(moduleId);
        if (m == null) {
            return;
        }
        stickMode = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type) ? MODE_KEY : MODE_ANALOG;
        if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(moduleId)) {
            stickMode = MODE_KEY;
            stickUpKey = intOr(m.stickUpKey, 82);
            stickLeftKey = intOr(m.stickLeftKey, 80);
            stickDownKey = intOr(m.stickDownKey, 81);
            stickRightKey = intOr(m.stickRightKey, 79);
        } else {
            stickUpKey = intOr(m.stickUpKey, DEFAULT_STICK_UP);
            stickLeftKey = intOr(m.stickLeftKey, DEFAULT_STICK_LEFT);
            stickDownKey = intOr(m.stickDownKey, DEFAULT_STICK_DOWN);
            stickRightKey = intOr(m.stickRightKey, DEFAULT_STICK_RIGHT);
        }
        stickSizeScale = m.scale;
    }

    private void syncFieldsFromLayoutDoc() {
        if (layoutDoc == null || layoutDoc.layout == null || layoutDoc.modules == null) {
            return;
        }
        twoButtonMode = layoutDoc.layout.showTwoButtons;
        mouseSensitivity = layoutDoc.layout.mouseSensitivity;

        GamepadLayoutPresetDocument.GamepadModule left = findModuleById("stick_left");
        if (left != null) {
            stickMode = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(left.type) ? MODE_KEY : MODE_ANALOG;
            stickUpKey = intOr(left.stickUpKey, DEFAULT_STICK_UP);
            stickLeftKey = intOr(left.stickLeftKey, DEFAULT_STICK_LEFT);
            stickDownKey = intOr(left.stickDownKey, DEFAULT_STICK_DOWN);
            stickRightKey = intOr(left.stickRightKey, DEFAULT_STICK_RIGHT);
            stickSizeScale = left.scale;
        }

        GamepadLayoutPresetDocument.GamepadModule right = findModuleById("stick_right");
        if (right != null) {
            rightStickUpKey = intOr(right.stickUpKey, 12);
            rightStickLeftKey = intOr(right.stickLeftKey, 13);
            rightStickDownKey = intOr(right.stickDownKey, 14);
            rightStickRightKey = intOr(right.stickRightKey, 15);
        }

        GamepadLayoutPresetDocument.GamepadModule extraStick =
                findModuleById(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID);
        if (extraStick != null) {
            extraStickUpKey = intOr(extraStick.stickUpKey, 82);
            extraStickLeftKey = intOr(extraStick.stickLeftKey, 80);
            extraStickDownKey = intOr(extraStick.stickDownKey, 81);
            extraStickRightKey = intOr(extraStick.stickRightKey, 79);
        }

        if (layoutDoc.layout.rightStickMouseGain != null) {
            rightStickMouseGain = layoutDoc.layout.rightStickMouseGain;
        } else if (prefs != null && prefs.contains(GamepadPreferenceKeys.RIGHT_STICK_MOUSE_GAIN)) {
            rightStickMouseGain = prefs.getFloat(GamepadPreferenceKeys.RIGHT_STICK_MOUSE_GAIN, 1.0f);
        } else {
            rightStickMouseGain = 1.0f;
        }

        GamepadLayoutPresetDocument.GamepadModule btnA = findModuleById("button_a");
        if (btnA != null && btnA.hidKey != null) {
            buttonAKey = btnA.hidKey;
            buttonAModifiers = intOr(btnA.modifierMask, 0);
            buttonSizeScale = btnA.scale;
        }
        GamepadLayoutPresetDocument.GamepadModule btnB = findModuleById("button_b");
        if (btnB != null && btnB.hidKey != null) {
            buttonBKey = btnB.hidKey;
            buttonBModifiers = intOr(btnB.modifierMask, 0);
        }
    }

    private void syncGamepadViewFromDoc() {
        if (gamepadView == null) {
            return;
        }
        gamepadView.setLayoutDocument(layoutDoc);
        gamepadView.setKeyCodeProvider(this::hidKeyFromLayoutDoc);
        gamepadView.setTouchpadDeltaListener(this::onTouchpadPixelDelta);
        gamepadView.setButtonAKeyCode(buttonAKey);
        gamepadView.setShowTwoButtons(twoButtonMode);
        gamepadView.setStickSizeScale(stickSizeScale);
        gamepadView.setButtonSizeScale(buttonSizeScale);
        float touchpadMouseBtnLayout = 1f;
        if (layoutDoc != null && layoutDoc.layout != null
                && layoutDoc.layout.touchpadMouseButtonScale != null) {
            touchpadMouseBtnLayout = layoutDoc.layout.touchpadMouseButtonScale;
        }
        gamepadView.setTouchpadMouseButtonLayoutScale(touchpadMouseBtnLayout);
        updateGamepadLabels();
    }

    private int hidKeyFromLayoutDoc(String componentId) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(componentId);
        if (m != null && GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type) && m.hidKey != null) {
            return m.hidKey;
        }
        return 0;
    }

    private void onTouchpadPixelDelta(float dx, float dy) {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }
        ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            return;
        }
        float s = mouseSensitivity * 1.2f;
        int cdx = (int) Math.max(-127, Math.min(127, dx * s));
        int cdy = (int) Math.max(-127, Math.min(127, dy * s));
        if (cdx != 0 || cdy != 0) {
            cm.sendMouseMovement(cdx, cdy, 0);
        }
    }

    private void applyLayoutDocFromMemory() {
        if (layoutDoc == null) {
            return;
        }
        try {
            GamepadLayoutPresetDocument.validateOrThrow(layoutDoc);
            GamepadLayoutPresetApplier.apply(requireContext(), layoutDoc);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Layout document invalid", e);
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        syncFieldsFromLayoutDoc();
        syncGamepadViewFromDoc();
        applyBackgroundFromPrefsOnly();
        persistActivePresetSnapshot();
        updateActivePresetNameUi();
    }

    /** Updates the toolbar label showing the active gamepad preset display name. */
    private void updateActivePresetNameUi() {
        if (activePresetNameView == null || presetRepository == null) {
            return;
        }
        String activeId = presetRepository.getActivePresetId();
        String label = activeId;
        for (GamepadLayoutPresetRepository.PresetRef r : presetRepository.listPresets()) {
            if (r != null && activeId != null && activeId.equals(r.id)) {
                if (r.displayName != null && !r.displayName.isEmpty()) {
                    label = r.displayName;
                }
                break;
            }
        }
        activePresetNameView.setText(label);
    }

    private void mergeViewPositionsIntoLayoutDoc(Map<String, GamepadConfigManager.ComponentPosition> positions) {
        if (layoutDoc == null || layoutDoc.modules == null || positions == null) {
            return;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
            GamepadConfigManager.ComponentPosition p = positions.get(m.id);
            if (p != null) {
                m.anchorX = p.x;
                m.anchorY = p.y;
            }
        }
    }

    private void reloadFromPrefsAndApplyView() {
        loadSavedSensitivity();
        layoutDoc = GamepadLayoutDocumentStore.loadOrCreate(requireContext());
        syncFieldsFromLayoutDoc();
        if (gamepadView != null) {
            gamepadView.setLayout(GamepadLayout.SIMPLE);
            syncGamepadViewFromDoc();
        }
        applyBackgroundFromPrefsOnly();
        updateActivePresetNameUi();
    }

    /** Short tap on preset control: save current layout to active preset file, then activate the next preset in index order. */
    private void cycleToNextPreset() {
        List<GamepadLayoutPresetRepository.PresetRef> refs = presetRepository.listPresets();
        if (refs.isEmpty()) {
            return;
        }
        String active = presetRepository.getActivePresetId();
        int idx = 0;
        for (int i = 0; i < refs.size(); i++) {
            if (refs.get(i) != null && refs.get(i).id != null && refs.get(i).id.equals(active)) {
                idx = i;
                break;
            }
        }
        int next = (idx + 1) % refs.size();
        String nextId = refs.get(next).id;
        presetRepository.persistActiveSnapshot();
        String err = presetRepository.activateAndApply(nextId);
        if (err != null) {
            Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
            return;
        }
        reloadFromPrefsAndApplyView();
        String label = refs.get(next).displayName != null ? refs.get(next).displayName : nextId;
        Toast.makeText(requireContext(),
                getString(R.string.gamepad_preset_cycled_toast, label),
                Toast.LENGTH_SHORT).show();
    }

    private void applyBackgroundFromPrefsOnly() {
        if (gamepadView == null) {
            return;
        }
        currentBgPath = prefs.getString(GamepadPreferenceKeys.BG_IMAGE, null);
        if (currentBgPath != null) {
            java.io.File file = new java.io.File(requireContext().getFilesDir(), currentBgPath);
            if (file.exists()) {
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bm != null) {
                    gamepadView.setBackgroundBitmap(bm);
                    float scale = prefs.getFloat(GamepadPreferenceKeys.BG_SCALE, 1.0f);
                    float offsetX = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_X, 0f);
                    float offsetY = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_Y, 0f);
                    gamepadView.setBackgroundViewport(scale, offsetX, offsetY);
                    return;
                }
            }
            prefs.edit()
                    .remove(GamepadPreferenceKeys.BG_IMAGE)
                    .remove(GamepadPreferenceKeys.BG_SCALE)
                    .remove(GamepadPreferenceKeys.BG_OFFSET_X)
                    .remove(GamepadPreferenceKeys.BG_OFFSET_Y)
                    .apply();
            currentBgPath = null;
            if (layoutDoc != null && layoutDoc.layout != null) {
                layoutDoc.layout.backgroundImageFile = null;
                layoutDoc.layout.backgroundScale = 1.0f;
                layoutDoc.layout.backgroundOffsetX = 0f;
                layoutDoc.layout.backgroundOffsetY = 0f;
                try {
                    GamepadLayoutDocumentStore.save(requireContext(), layoutDoc);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        gamepadView.setBackgroundBitmap(null);
    }

    private void showGamepadPresetsMenu() {
        List<GamepadLayoutPresetRepository.PresetRef> refs = presetRepository.listPresets();
        int n = refs.size();
        String[] items = new String[n + 3];
        for (int i = 0; i < n; i++) {
            items[i] = refs.get(i).displayName != null ? refs.get(i).displayName : refs.get(i).id;
        }
        items[n] = getString(R.string.gamepad_presets_import);
        items[n + 1] = getString(R.string.gamepad_presets_add_module);
        items[n + 2] = getString(R.string.gamepad_presets_export_share);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gamepad_presets_title)
                .setItems(items, (d, which) -> {
                    if (which < n) {
                        String id = refs.get(which).id;
                        presetRepository.persistActiveSnapshot();
                        String err = presetRepository.activateAndApply(id);
                        if (err == null) {
                            reloadFromPrefsAndApplyView();
                            Toast.makeText(requireContext(), R.string.gamepad_presets_activated, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                        }
                    } else if (which == n) {
                        importPresetLauncher.launch(new String[]{"application/json"});
                    } else if (which == n + 1) {
                        showAddModuleMenu();
                    } else {
                        shareCurrentPresetJson();
                    }
                })
                .show();
    }

    private void showAddModuleMenu() {
        if (layoutDoc == null) {
            layoutDoc = GamepadLayoutDocumentStore.loadOrCreate(requireContext());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gamepad_add_module_title)
                .setItems(new String[]{
                        getString(R.string.gamepad_add_right_stick),
                        getString(R.string.gamepad_add_touchpad),
                        getString(R.string.gamepad_add_arrow_stick),
                        getString(R.string.gamepad_add_extra_button),
                }, (d, which) -> {
                    if (which == 0) {
                        GamepadLayoutDocEditor.addStickRight(layoutDoc);
                    } else if (which == 1) {
                        GamepadLayoutDocEditor.addTouchpad(layoutDoc);
                    } else if (which == 2) {
                        GamepadLayoutDocEditor.addStickKeyExtra(layoutDoc);
                    } else {
                        GamepadLayoutDocEditor.addButton(layoutDoc);
                    }
                    applyLayoutDocFromMemory();
                })
                .show();
    }

    private void shareCurrentPresetJson() {
        String active = presetRepository.getActivePresetId();
        String displayName = "layout";
        for (GamepadLayoutPresetRepository.PresetRef r : presetRepository.listPresets()) {
            if (active != null && active.equals(r.id)) {
                displayName = r.displayName != null ? r.displayName : r.id;
                break;
            }
        }
        try {
            GamepadLayoutPresetDocument doc = GamepadLayoutPresetSnapshotBuilder.buildFrom(
                    requireContext(), active != null ? active : GamepadLayoutPresetConstants.DEFAULT_PRESET_ID,
                    displayName);
            String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
            File shareDir = new File(requireContext().getCacheDir(), "share");
            if (!shareDir.isDirectory() && !shareDir.mkdirs()) {
                Toast.makeText(requireContext(), R.string.gamepad_presets_export_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String safe = displayName.replaceAll("[^a-zA-Z0-9_-]", "_");
            File outFile = new File(shareDir, "keymod_gamepad_" + safe + "_" + System.currentTimeMillis() + ".json");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", outFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.putExtra(Intent.EXTRA_SUBJECT, displayName);
            share.setClipData(ClipData.newUri(requireContext().getContentResolver(),
                    getString(R.string.app_name), uri));
            startActivity(Intent.createChooser(share, getString(R.string.gamepad_presets_share_chooser)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.gamepad_presets_export_failed, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "share gamepad preset", e);
            Toast.makeText(requireContext(), getString(R.string.gamepad_presets_export_failed_detail, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
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

            int modifiers = 0;
            if (layoutDoc != null && layoutDoc.modules != null) {
                for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
                    if (m == null || m.id == null || !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(faceButtonPressed.get(m.id))) {
                        modifiers |= intOr(m.modifierMask, 0);
                    }
                }
            } else {
                if (buttonAPressed) {
                    modifiers |= buttonAModifiers;
                }
                if (buttonBPressed) {
                    modifiers |= buttonBModifiers;
                }
            }

            java.util.List<Integer> regularKeys = new java.util.ArrayList<>();
            if (keyUpPressed) {
                addKeyOrMod(regularKeys, stickUpKey);
            }
            if (keyLeftPressed) {
                addKeyOrMod(regularKeys, stickLeftKey);
            }
            if (keyDownPressed) {
                addKeyOrMod(regularKeys, stickDownKey);
            }
            if (keyRightPressed) {
                addKeyOrMod(regularKeys, stickRightKey);
            }
            if (keyRUpPressed) {
                addKeyOrMod(regularKeys, rightStickUpKey);
            }
            if (keyRLeftPressed) {
                addKeyOrMod(regularKeys, rightStickLeftKey);
            }
            if (keyRDownPressed) {
                addKeyOrMod(regularKeys, rightStickDownKey);
            }
            if (keyRRightPressed) {
                addKeyOrMod(regularKeys, rightStickRightKey);
            }
            if (keyEUpPressed) {
                addKeyOrMod(regularKeys, extraStickUpKey);
            }
            if (keyELeftPressed) {
                addKeyOrMod(regularKeys, extraStickLeftKey);
            }
            if (keyEDownPressed) {
                addKeyOrMod(regularKeys, extraStickDownKey);
            }
            if (keyERightPressed) {
                addKeyOrMod(regularKeys, extraStickRightKey);
            }

            if (layoutDoc != null && layoutDoc.modules != null) {
                for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
                    if (m == null || m.id == null || !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)
                            || m.hidKey == null) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(faceButtonPressed.get(m.id))) {
                        addKeyOrMod(regularKeys, m.hidKey);
                    }
                }
            } else {
                if (buttonAPressed) {
                    addKeyOrMod(regularKeys, buttonAKey);
                }
                if (buttonBPressed) {
                    addKeyOrMod(regularKeys, buttonBKey);
                }
            }

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
                " RU=" + keyRUpPressed + " RL=" + keyRLeftPressed +
                " RD=" + keyRDownPressed + " RR=" + keyRRightPressed +
                " EU=" + keyEUpPressed + " EL=" + keyELeftPressed +
                " ED=" + keyEDownPressed + " ER=" + keyERightPressed +
                " face=" + faceButtonPressed + ")");
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
        if ("stick_left".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_left_stick);
            hasKeyMapping = true;
        } else if ("stick_right".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_right_stick);
            hasKeyMapping = true;
        } else if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(componentId)) {
            componentName = getString(R.string.gamepad_component_arrow_stick);
            hasKeyMapping = true;
        } else if (componentId != null && componentId.startsWith("stick_")) {
            componentName = componentId;
            hasKeyMapping = true;
        } else if ("button_a".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_button_a);
            hasKeyMapping = true;
        } else if ("button_b".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_button_b);
            hasKeyMapping = true;
        } else if (componentId != null && componentId.startsWith("button_")) {
            componentName = componentId;
            hasKeyMapping = true;
        } else if ("touchpad_1".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_touchpad);
            hasKeyMapping = false;
        } else if (mouseButtonForComponentId(componentId) != null) {
            componentName = getString(R.string.gamepad_component_mouse_button);
            hasKeyMapping = false;
        } else {
            componentName = componentId;
            hasKeyMapping = false;
        }

        ArrayList<String> opts = new ArrayList<>();
        opts.add(getString(R.string.gamepad_menu_move));
        if ("touchpad_1".equals(componentId)) {
            opts.add(getString(R.string.gamepad_menu_touchpad_resize));
            opts.add(getString(R.string.gamepad_menu_touchpad_mouse_btn_size));
            if (findModuleById(GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID) == null) {
                opts.add(getString(R.string.gamepad_menu_add_touchpad_mouse_l));
            }
            if (findModuleById(GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID) == null) {
                opts.add(getString(R.string.gamepad_menu_add_touchpad_mouse_m));
            }
            if (findModuleById(GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID) == null) {
                opts.add(getString(R.string.gamepad_menu_add_touchpad_mouse_r));
            }
        }
        if (mouseButtonForComponentId(componentId) != null) {
            opts.add(getString(R.string.gamepad_menu_mouse_btn_module_size));
            if (GamepadLayoutDocEditor.hasTouchpad(layoutDoc)) {
                opts.add(getString(R.string.gamepad_menu_touchpad_mouse_btn_size));
            }
        }
        if (hasKeyMapping) {
            opts.add(getString(R.string.gamepad_menu_edit_keys));
        }
        if (GamepadLayoutDocEditor.canRemove(componentId)) {
            opts.add(getString(R.string.gamepad_menu_remove));
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(componentName);
        builder.setItems(opts.toArray(new String[0]), (dialog, which) -> {
            String choice = opts.get(which);
            if (choice.equals(getString(R.string.gamepad_menu_move))) {
                enterMoveMode(componentId);
            } else if (choice.equals(getString(R.string.gamepad_menu_touchpad_resize))) {
                showTouchpadResizeDialog();
            } else if (choice.equals(getString(R.string.gamepad_menu_touchpad_mouse_btn_size))) {
                showTouchpadMouseButtonsLayoutSizeDialog();
            } else if (choice.equals(getString(R.string.gamepad_menu_add_touchpad_mouse_l))) {
                if (GamepadLayoutDocEditor.addTouchpadMouseButtonLeft(layoutDoc)) {
                    applyLayoutDocFromMemory();
                }
            } else if (choice.equals(getString(R.string.gamepad_menu_add_touchpad_mouse_m))) {
                if (GamepadLayoutDocEditor.addTouchpadMouseButtonMiddle(layoutDoc)) {
                    applyLayoutDocFromMemory();
                }
            } else if (choice.equals(getString(R.string.gamepad_menu_add_touchpad_mouse_r))) {
                if (GamepadLayoutDocEditor.addTouchpadMouseButtonRight(layoutDoc)) {
                    applyLayoutDocFromMemory();
                }
            } else if (choice.equals(getString(R.string.gamepad_menu_mouse_btn_module_size))) {
                showMouseButtonModuleSizeDialog(componentId);
            } else if (choice.equals(getString(R.string.gamepad_menu_edit_keys)) && hasKeyMapping) {
                showConfigDialog(componentId);
            } else if (choice.equals(getString(R.string.gamepad_menu_remove))) {
                GamepadLayoutDocEditor.removeModule(layoutDoc, componentId);
                faceButtonPressed.remove(componentId);
                applyLayoutDocFromMemory();
            }
        });
        builder.show();
    }

    private void showTouchpadResizeDialog() {
        GamepadLayoutPresetDocument.GamepadModule tp = findModuleById("touchpad_1");
        if (tp == null) {
            return;
        }
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView wTitle = new TextView(ctx);
        wTitle.setText(R.string.gamepad_touchpad_width_pct);
        android.widget.SeekBar wSeek = new android.widget.SeekBar(ctx);
        wSeek.setMax(65);
        int wp = Math.round((tp.widthNorm != null ? tp.widthNorm : GamepadLayoutDocEditor.TOUCHPAD_DEFAULT_SIZE_NORM) * 100f);
        wSeek.setProgress(Math.max(10, Math.min(65, wp)));

        TextView hTitle = new TextView(ctx);
        hTitle.setText(R.string.gamepad_touchpad_height_pct);
        android.widget.SeekBar hSeek = new android.widget.SeekBar(ctx);
        hSeek.setMax(65);
        int hp = Math.round((tp.heightNorm != null ? tp.heightNorm : GamepadLayoutDocEditor.TOUCHPAD_DEFAULT_SIZE_NORM) * 100f);
        hSeek.setProgress(Math.max(10, Math.min(65, hp)));

        root.addView(wTitle);
        root.addView(wSeek);
        root.addView(hTitle);
        root.addView(hSeek);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_touchpad_size_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    tp.widthNorm = wSeek.getProgress() / 100f;
                    tp.heightNorm = hSeek.getProgress() / 100f;
                    applyLayoutDocFromMemory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Layout-level scale for all touchpad L/M/R mouse buttons (50%–200%). */
    private void showTouchpadMouseButtonsLayoutSizeDialog() {
        if (layoutDoc == null || layoutDoc.layout == null) {
            return;
        }
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText(R.string.gamepad_mouse_btn_size_pct);
        android.widget.SeekBar seek = new android.widget.SeekBar(ctx);
        seek.setMax(150);
        float cur = layoutDoc.layout.touchpadMouseButtonScale != null
                ? layoutDoc.layout.touchpadMouseButtonScale : 1.0f;
        seek.setProgress(Math.max(0, Math.min(150, Math.round(cur * 100f) - 50)));

        root.addView(title);
        root.addView(seek);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_touchpad_mouse_btn_size_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    layoutDoc.layout.touchpadMouseButtonScale = (seek.getProgress() + 50) / 100f;
                    applyLayoutDocFromMemory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Per-module radius scale for one MOUSE_BUTTON (50%–200%). */
    private void showMouseButtonModuleSizeDialog(String componentId) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(componentId);
        if (m == null || layoutDoc == null || layoutDoc.layout == null) {
            return;
        }
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText(R.string.gamepad_mouse_btn_size_pct);
        android.widget.SeekBar seek = new android.widget.SeekBar(ctx);
        seek.setMax(150);
        seek.setProgress(Math.max(0, Math.min(150, Math.round(m.scale * 100f) - 50)));

        root.addView(title);
        root.addView(seek);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_mouse_btn_module_size_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    m.scale = (seek.getProgress() + 50) / 100f;
                    applyLayoutDocFromMemory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        if (componentId != null && componentId.startsWith("stick_")) {
            stickConfigModuleId = componentId;
            showStickConfigDialog();
        } else if (componentId != null && componentId.startsWith("button_")) {
            openButtonConfigDialog(componentId);
        }
    }

    private void showStickConfigDialog() {
        loadStickModuleIntoEditorState(stickConfigModuleId);
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

        if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
            modeGroup.setVisibility(View.GONE);
            stickMode = MODE_KEY;
            modeGroup.check(R.id.stick_mode_key);
            keySection.setVisibility(View.VISIBLE);
        }

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
            if ("stick_right".equals(stickConfigModuleId)) {
                stickUpKey = 12;
                stickLeftKey = 13;
                stickDownKey = 14;
                stickRightKey = 15;
            } else if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
                stickUpKey = 82;
                stickLeftKey = 80;
                stickDownKey = 81;
                stickRightKey = 79;
            } else {
                stickUpKey = DEFAULT_STICK_UP;
                stickLeftKey = DEFAULT_STICK_LEFT;
                stickDownKey = DEFAULT_STICK_DOWN;
                stickRightKey = DEFAULT_STICK_RIGHT;
            }
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
            if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
                stickMode = MODE_KEY;
            } else {
                stickMode = modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key ? MODE_KEY : MODE_ANALOG;
            }
            saveStickConfig();
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

    private void openButtonConfigDialog(String moduleId) {
        final GamepadLayoutPresetDocument.GamepadModule m = findModuleById(moduleId);
        if (m == null || m.hidKey == null) {
            return;
        }
        int currentKey = m.hidKey;
        int currentModifiers = intOr(m.modifierMask, 0);
        buttonSizeScale = m.scale;

        String title;
        if ("button_a".equals(moduleId)) {
            title = "Button A";
        } else if ("button_b".equals(moduleId)) {
            title = "Button B";
        } else {
            title = m.displayLabel != null ? m.displayLabel : moduleId;
        }
        final int defaultKey = "button_a".equals(moduleId) ? DEFAULT_BUTTON_A
                : "button_b".equals(moduleId) ? DEFAULT_BUTTON_B : 40;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_button_config, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(title + " Configuration");

        final int[] selectedKey = { currentKey };
        final int[] selectedModifiers = { currentModifiers };

        final Button keyLabel = dialogView.findViewById(R.id.button_key_label);
        updateButtonLabel(keyLabel, currentKey, currentModifiers);

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

        LinearLayout parent = (LinearLayout) keyLabel.getParent();
        parent.addView(modifierRow, parent.indexOfChild(keyLabel));

        keyLabel.setOnClickListener(v -> showKeyPickerWithModifiers(keyLabel, selectedKey[0], selectedModifiers[0], (key, mod) -> {
            selectedKey[0] = key;
            selectedModifiers[0] = mod;
            updateButtonLabel(keyLabel, key, mod);
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
            m.hidKey = selectedKey[0];
            m.modifierMask = selectedModifiers[0];
            m.scale = buttonSizeScale;
            if ("button_a".equals(moduleId)) {
                buttonAKey = selectedKey[0];
                buttonAModifiers = selectedModifiers[0];
            } else if ("button_b".equals(moduleId)) {
                buttonBKey = selectedKey[0];
                buttonBModifiers = selectedModifiers[0];
            }
            applyLayoutDocFromMemory();
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

    private void saveStickConfig() {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(stickConfigModuleId);
        if (m == null) {
            return;
        }
        m.type = MODE_KEY.equals(stickMode)
                ? GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY
                : GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        m.stickUpKey = stickUpKey;
        m.stickLeftKey = stickLeftKey;
        m.stickDownKey = stickDownKey;
        m.stickRightKey = stickRightKey;
        m.scale = stickSizeScale;
        if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
            m.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
            extraStickUpKey = stickUpKey;
            extraStickLeftKey = stickLeftKey;
            extraStickDownKey = stickDownKey;
            extraStickRightKey = stickRightKey;
        } else if ("stick_right".equals(stickConfigModuleId)) {
            rightStickUpKey = stickUpKey;
            rightStickLeftKey = stickLeftKey;
            rightStickDownKey = stickDownKey;
            rightStickRightKey = stickRightKey;
        }
        applyLayoutDocFromMemory();
    }

    // ── Label Sync ──────────────────────────────────────────────────

    private void updateGamepadLabels() {
        if (gamepadView == null) {
            return;
        }
        Map<String, String> labels = new HashMap<>();
        labels.put("stick_up", keyCodeToLabel(stickUpKey));
        labels.put("stick_down", keyCodeToLabel(stickDownKey));
        labels.put("stick_left", keyCodeToLabel(stickLeftKey));
        labels.put("stick_right", keyCodeToLabel(stickRightKey));
        labels.put("stick_r_up", keyCodeToLabel(rightStickUpKey));
        labels.put("stick_r_down", keyCodeToLabel(rightStickDownKey));
        labels.put("stick_r_left", keyCodeToLabel(rightStickLeftKey));
        labels.put("stick_r_right", keyCodeToLabel(rightStickRightKey));
        labels.put("stick_e_up", keyCodeToLabel(extraStickUpKey));
        labels.put("stick_e_down", keyCodeToLabel(extraStickDownKey));
        labels.put("stick_e_left", keyCodeToLabel(extraStickLeftKey));
        labels.put("stick_e_right", keyCodeToLabel(extraStickRightKey));
        if (layoutDoc != null && layoutDoc.modules != null) {
            for (GamepadLayoutPresetDocument.GamepadModule mod : layoutDoc.modules) {
                if (mod != null && GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(mod.type)
                        && mod.id != null && mod.hidKey != null) {
                    labels.put(mod.id, buildFullLabel(mod.hidKey, intOr(mod.modifierMask, 0)));
                }
            }
        } else {
            labels.put("button_a", buildFullLabel(buttonAKey, buttonAModifiers));
            labels.put("button_b", buildFullLabel(buttonBKey, buttonBModifiers));
        }
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

            if ("r".equals(stickId)) {
                GamepadLayoutPresetDocument.GamepadModule rm = findModuleById("stick_right");
                if (rm == null) {
                    return;
                }
                if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(rm.type)) {
                    sendRightStickKeys(x, y);
                } else if (isConnected) {
                    sendRightStickMouse(connectionManager, x, y);
                }
            } else if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickId)) {
                GamepadLayoutPresetDocument.GamepadModule em =
                        findModuleById(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID);
                if (em != null && GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(em.type)) {
                    sendExtraStickKeys(x, y);
                }
            } else if ("l".equals(stickId)) {
                if (MODE_KEY.equals(stickMode)) {
                    sendLeftStickKeys(connectionManager, x, y, isConnected);
                } else if (isConnected) {
                    sendLeftStickMouse(connectionManager, x, y);
                }
            }
        }
    }

    private void sendRightStickMouse(ConnectionManager cm, float x, float y) {
        float deadZone = 0.12f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) return;

        // Slightly super-linear curve so small deflections are easier to aim with.
        float gamma = 1.35f;
        xAdj = Math.copySign((float) Math.pow(Math.abs(xAdj), gamma), xAdj);
        yAdj = Math.copySign((float) Math.pow(Math.abs(yAdj), gamma), yAdj);

        float gain = (rightStickMouseGain > 0f && rightStickMouseGain <= 4.0f) ? rightStickMouseGain : 1.0f;
        xAdj *= mouseSensitivity * gain;
        yAdj *= mouseSensitivity * gain;
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

        boolean prevU = keyUpPressed;
        boolean prevL = keyLeftPressed;
        boolean prevD = keyDownPressed;
        boolean prevR = keyRightPressed;

        boolean changed = false;

        // Up key
        if (wantUp != keyUpPressed) { keyUpPressed = wantUp; changed = true; }
        // Left key
        if (wantLeft != keyLeftPressed) { keyLeftPressed = wantLeft; changed = true; }
        // Down key
        if (wantDown != keyDownPressed) { keyDownPressed = wantDown; changed = true; }
        // Right key
        if (wantRight != keyRightPressed) { keyRightPressed = wantRight; changed = true; }

        boolean newlyEngaged = (keyUpPressed && !prevU) || (keyLeftPressed && !prevL)
                || (keyDownPressed && !prevD) || (keyRightPressed && !prevR);
        if (newlyEngaged) {
            vibrateGamepadTick();
        }

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

    private void sendRightStickKeys(float x, float y) {
        float deadZone = 0.2f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);

        boolean wantUp = yAdj < -0.3f;
        boolean wantDown = yAdj > 0.3f;
        boolean wantLeft = xAdj < -0.3f;
        boolean wantRight = xAdj > 0.3f;

        boolean prevU = keyRUpPressed;
        boolean prevL = keyRLeftPressed;
        boolean prevD = keyRDownPressed;
        boolean prevR = keyRRightPressed;

        boolean changed = false;
        if (wantUp != keyRUpPressed) {
            keyRUpPressed = wantUp;
            changed = true;
        }
        if (wantLeft != keyRLeftPressed) {
            keyRLeftPressed = wantLeft;
            changed = true;
        }
        if (wantDown != keyRDownPressed) {
            keyRDownPressed = wantDown;
            changed = true;
        }
        if (wantRight != keyRRightPressed) {
            keyRRightPressed = wantRight;
            changed = true;
        }
        boolean newlyEngaged = (keyRUpPressed && !prevU) || (keyRLeftPressed && !prevL)
                || (keyRDownPressed && !prevD) || (keyRRightPressed && !prevR);
        if (newlyEngaged) {
            vibrateGamepadTick();
        }
        if (changed) {
            sendCombinedKeyReport();
        }

        java.util.Set<String> activeDirs = new java.util.HashSet<>();
        if (keyRUpPressed) {
            activeDirs.add("up");
        }
        if (keyRDownPressed) {
            activeDirs.add("down");
        }
        if (keyRLeftPressed) {
            activeDirs.add("left");
        }
        if (keyRRightPressed) {
            activeDirs.add("right");
        }
        if (gamepadView != null) {
            if (activeDirs.isEmpty()) {
                gamepadView.clearStickDirections("r");
            } else {
                gamepadView.setActiveStickDirections("r", activeDirs);
            }
        }
    }

    private void sendExtraStickKeys(float x, float y) {
        float deadZone = 0.2f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);

        boolean wantUp = yAdj < -0.3f;
        boolean wantDown = yAdj > 0.3f;
        boolean wantLeft = xAdj < -0.3f;
        boolean wantRight = xAdj > 0.3f;

        boolean prevU = keyEUpPressed;
        boolean prevL = keyELeftPressed;
        boolean prevD = keyEDownPressed;
        boolean prevR = keyERightPressed;

        boolean changed = false;
        if (wantUp != keyEUpPressed) {
            keyEUpPressed = wantUp;
            changed = true;
        }
        if (wantLeft != keyELeftPressed) {
            keyELeftPressed = wantLeft;
            changed = true;
        }
        if (wantDown != keyEDownPressed) {
            keyEDownPressed = wantDown;
            changed = true;
        }
        if (wantRight != keyERightPressed) {
            keyERightPressed = wantRight;
            changed = true;
        }
        boolean newlyEngaged = (keyEUpPressed && !prevU) || (keyELeftPressed && !prevL)
                || (keyEDownPressed && !prevD) || (keyERightPressed && !prevR);
        if (newlyEngaged) {
            vibrateGamepadTick();
        }
        if (changed) {
            sendCombinedKeyReport();
        }

        java.util.Set<String> activeDirs = new java.util.HashSet<>();
        if (keyEUpPressed) {
            activeDirs.add("up");
        }
        if (keyEDownPressed) {
            activeDirs.add("down");
        }
        if (keyELeftPressed) {
            activeDirs.add("left");
        }
        if (keyERightPressed) {
            activeDirs.add("right");
        }
        if (gamepadView != null) {
            if (activeDirs.isEmpty()) {
                gamepadView.clearStickDirections(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID);
            } else {
                gamepadView.setActiveStickDirections(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID, activeDirs);
            }
        }
    }

    private float applyDeadZone(float value, float deadZone) {
        if (Math.abs(value) < deadZone) return 0;
        return (value - Math.copySign(deadZone, value)) / (1.0f - deadZone);
    }

    // ── Mouse sensitivity ───────────────────────────────────────────

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.5f, Math.min(2.0f, sensitivity));
        prefs.edit().putFloat(GamepadPreferenceKeys.MOUSE_SENSITIVITY, this.mouseSensitivity).apply();
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    private void loadSavedSensitivity() {
        mouseSensitivity = prefs.getFloat(GamepadPreferenceKeys.MOUSE_SENSITIVITY, 1.0f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vibrator != null) vibrator.cancel();
        releaseAllKeys();
    }

    private void releaseAllKeys() {
        boolean anyFace = false;
        for (Boolean v : faceButtonPressed.values()) {
            if (Boolean.TRUE.equals(v)) {
                anyFace = true;
                break;
            }
        }
        boolean anyKey = keyUpPressed || keyLeftPressed || keyDownPressed || keyRightPressed
                || keyRUpPressed || keyRLeftPressed || keyRDownPressed || keyRRightPressed
                || keyEUpPressed || keyELeftPressed || keyEDownPressed || keyERightPressed
                || buttonAPressed || buttonBPressed || anyFace;
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected() && anyKey) {
                cm.sendKeyRelease();
            }
        }
        keyUpPressed = false;
        keyLeftPressed = false;
        keyDownPressed = false;
        keyRightPressed = false;
        keyRUpPressed = false;
        keyRLeftPressed = false;
        keyRDownPressed = false;
        keyRRightPressed = false;
        keyEUpPressed = false;
        keyELeftPressed = false;
        keyEDownPressed = false;
        keyERightPressed = false;
        buttonAPressed = false;
        buttonBPressed = false;
        faceButtonPressed.clear();
        if (gamepadView != null) {
            gamepadView.clearStickDirections("l");
            gamepadView.clearStickDirections("r");
            gamepadView.clearStickDirections(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID);
        }
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
        // HID F1–F12 (0x3A–0x45)
        if (keyCode >= 58 && keyCode <= 69) return "F" + (keyCode - 57);
        return String.valueOf(keyCode);
    }

    // ── Background Image ────────────────────────────────────────────

    private static final int PICK_BG_IMAGE = 1001;
    private String currentBgPath = null;

    /** One background file per preset under app filesDir (not portable in shared JSON). */
    static String bgFileNameForPresetId(@Nullable String presetId) {
        if (presetId == null || presetId.isEmpty()) {
            return "gamepad_bg_inline.png";
        }
        String safe = presetId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "gamepad_bg_" + safe + ".png";
    }

    private void loadBackground() {
        currentBgPath = prefs.getString(GamepadPreferenceKeys.BG_IMAGE, null);
        if (currentBgPath != null) {
            java.io.File file = new java.io.File(requireContext().getFilesDir(), currentBgPath);
            if (file.exists()) {
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bm != null && gamepadView != null) {
                    gamepadView.setBackgroundBitmap(bm);
                    // Restore viewport
                    float scale = prefs.getFloat(GamepadPreferenceKeys.BG_SCALE, 1.0f);
                    float offsetX = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_X, 0f);
                    float offsetY = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_Y, 0f);
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
                        String name = bgFileNameForPresetId(
                                presetRepository != null ? presetRepository.getActivePresetId() : null);
                        java.io.File file = new java.io.File(dir, name);
                        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                        bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        out.flush();
                        out.close();
                        prefs.edit().putString(GamepadPreferenceKeys.BG_IMAGE, name).apply();
                        currentBgPath = name;
                        persistActivePresetSnapshot();
                    } catch (java.io.IOException e) {
                        Log.e(TAG, "Failed to save background image", e);
                    }
                } else {
                    prefs.edit()
                            .remove(GamepadPreferenceKeys.BG_IMAGE)
                            .remove(GamepadPreferenceKeys.BG_SCALE)
                            .remove(GamepadPreferenceKeys.BG_OFFSET_X)
                            .remove(GamepadPreferenceKeys.BG_OFFSET_Y)
                            .apply();
                    currentBgPath = null;
                    persistActivePresetSnapshot();
                }
            });
            gamepadView.setBackgroundViewportCallback(() -> {
                prefs.edit()
                    .putFloat(GamepadPreferenceKeys.BG_SCALE, gamepadView.getBackgroundScale())
                    .putFloat(GamepadPreferenceKeys.BG_OFFSET_X, gamepadView.getBackgroundOffsetX())
                    .putFloat(GamepadPreferenceKeys.BG_OFFSET_Y, gamepadView.getBackgroundOffsetY())
                    .apply();
            });
        }
    }

    private void saveBackground() {
        // Triggered by the callback
    }

    /** Edit-mode empty-area long-press: background actions plus add-module shortcuts. */
    private void showEditBackgroundAndModulesMenu() {
        String[] options = new String[]{
                getString(R.string.gamepad_bg_pick_gallery),
                getString(R.string.gamepad_bg_remove),
                getString(R.string.gamepad_add_right_stick),
                getString(R.string.gamepad_add_touchpad),
                getString(R.string.gamepad_add_arrow_stick),
                getString(R.string.gamepad_add_extra_button),
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gamepad_edit_canvas_menu_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_PICK,
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        intent.setType("image/*");
                        startActivityForResult(intent, PICK_BG_IMAGE);
                    } else if (which == 1) {
                        if (gamepadView != null) {
                            gamepadView.setBackgroundBitmap(null);
                        }
                        if (currentBgPath != null) {
                            java.io.File file = new java.io.File(requireContext().getFilesDir(), currentBgPath);
                            if (file.exists()) {
                                file.delete();
                            }
                            prefs.edit()
                                    .remove(GamepadPreferenceKeys.BG_IMAGE)
                                    .remove(GamepadPreferenceKeys.BG_SCALE)
                                    .remove(GamepadPreferenceKeys.BG_OFFSET_X)
                                    .remove(GamepadPreferenceKeys.BG_OFFSET_Y)
                                    .apply();
                            currentBgPath = null;
                            persistActivePresetSnapshot();
                        }
                    } else {
                        if (layoutDoc == null) {
                            layoutDoc = GamepadLayoutDocumentStore.loadOrCreate(requireContext());
                        }
                        if (which == 2) {
                            GamepadLayoutDocEditor.addStickRight(layoutDoc);
                        } else if (which == 3) {
                            GamepadLayoutDocEditor.addTouchpad(layoutDoc);
                        } else if (which == 4) {
                            GamepadLayoutDocEditor.addStickKeyExtra(layoutDoc);
                        } else {
                            GamepadLayoutDocEditor.addButton(layoutDoc);
                        }
                        applyLayoutDocFromMemory();
                    }
                })
                .show();
    }

    private void vibrateGamepadTick() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
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
