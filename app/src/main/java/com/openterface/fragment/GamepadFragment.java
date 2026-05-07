package com.openterface.fragment;

import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.view.Window;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import androidx.core.widget.NestedScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.R;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.GamepadView;
import com.openterface.keymod.gamepad.GamepadCapLabels;
import com.openterface.keymod.gamepad.GamepadLayoutDocEditor;
import com.openterface.keymod.gamepad.GamepadLayoutDocumentStore;
import com.openterface.keymod.gamepad.GamepadLayoutPresetApplier;
import com.openterface.keymod.gamepad.GamepadLayoutPresetConstants;
import com.openterface.keymod.gamepad.GamepadLayoutPresetDocument;
import com.openterface.keymod.gamepad.GamepadLayoutPresetRepository;
import com.openterface.keymod.gamepad.GamepadLayoutPresetSnapshotBuilder;
import com.openterface.keymod.gamepad.GamepadModuleAccent;
import com.openterface.keymod.gamepad.GamepadPreferenceKeys;
import com.openterface.keymod.gamepad.GamepadPresetListAdapter;
import com.openterface.keymod.widget.MaxHeightNestedScrollView;
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
    /** Left stick: D-pad cross (digital); same HID behavior as {@link #MODE_KEY}. */
    private static final String MODE_DPAD_CROSS = "dpad_cross";
    /** Left stick: split D-pad (discrete {@code dpad_*} hit targets). */
    private static final String MODE_DPAD_SPLIT = "dpad_split";

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
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private long lastGyroEventNs;
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_GYROSCOPE) {
                return;
            }
            onGyroscopeSample(event);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
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
    /** Split D-pad only: gap ratio (see {@link GamepadLayoutPresetConstants#DPAD_SPLIT_GAP_RATIO_DEFAULT}). */
    private float dpadSplitGapRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT;
    /** Split D-pad only: center-to-outer-edge as a fraction of radius (see {@link GamepadLayoutPresetConstants#DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT}). */
    private float dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;

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
    /** Virtual cardinal keys for arrow sticks ({@code stick_key_extra}, {@code stick_aux_*}) during analog deflection. */
    private final java.util.HashMap<String, boolean[]> arrowStickVirtKeys = new java.util.HashMap<>();
    /** Edited in stick dialog for arrow sticks in mouse-direction mode; persisted as {@code stickMouseSensitivity}. */
    private float arrowStickPointerSensitivity = 1.0f;

    private GamepadLayoutPresetRepository presetRepository;
    private ActivityResultLauncher<String[]> importPresetLauncher;

    @Nullable
    private MaterialButton activePresetChipButton;
    @Nullable
    private MaterialButton editModeMaterialButton;
    @Nullable
    private ImageButton gamepadChromeMenu;
    @Nullable
    private LinearLayout gamepadChromeConnectionWrap;
    @Nullable
    private ImageView gamepadChromeConnectionIcon;

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

        activePresetChipButton = view.findViewById(R.id.gamepad_active_preset_chip);
        if (activePresetChipButton != null) {
            activePresetChipButton.setOnClickListener(v -> showGamepadPresetsBottomSheet());
        }

        MaterialButton presetsBtn = view.findViewById(R.id.gamepad_presets_btn);
        presetsBtn.setOnClickListener(v -> cycleToNextPreset());

        MaterialButton mappingHintsToggle = view.findViewById(R.id.gamepad_mapping_hints_toggle);
        if (mappingHintsToggle != null && prefs != null && gamepadView != null) {
            boolean hintsOn = prefs.getBoolean(GamepadPreferenceKeys.SHOW_KEY_MAPPING_HINTS, true);
            mappingHintsToggle.setChecked(hintsOn);
            mappingHintsToggle.setIconResource(
                    hintsOn ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
            gamepadView.setKeyMappingHintsVisible(hintsOn);
            mappingHintsToggle.addOnCheckedChangeListener((button, isChecked) -> {
                prefs.edit().putBoolean(GamepadPreferenceKeys.SHOW_KEY_MAPPING_HINTS, isChecked).apply();
                button.setIconResource(
                        isChecked ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
                if (gamepadView != null) {
                    gamepadView.setKeyMappingHintsVisible(isChecked);
                }
            });
        }

        editModeMaterialButton = view.findViewById(R.id.edit_mode_toggle);
        editModeMaterialButton.addOnCheckedChangeListener((button, isChecked) -> {
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

        MaterialButton sessionDone = view.findViewById(R.id.gamepad_edit_session_done);
        if (sessionDone != null) {
            sessionDone.setOnClickListener(v -> exitLayoutEditSession());
        }

        wireGamepadEmbeddedChrome(view);
        applyGamepadToolbarTopInsets(view);

        setupListeners();
        updateActivePresetNameUi();
        applyGamepadEditTouchPreferences();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Allow both landscape directions; LANDSCAPE alone locks to one side only.
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        updateGyroListenerRegistration();
        applyGamepadEditTouchPreferences();
        refreshGamepadEmbeddedChrome();
    }

    @Override
    public void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(gyroListener);
        }
        super.onPause();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void wireGamepadEmbeddedChrome(@NonNull View root) {
        gamepadChromeMenu = root.findViewById(R.id.gamepad_chrome_menu);
        gamepadChromeConnectionWrap = root.findViewById(R.id.gamepad_chrome_connection_wrap);
        gamepadChromeConnectionIcon = root.findViewById(R.id.gamepad_chrome_connection_icon);
        if (gamepadChromeMenu != null) {
            gamepadChromeMenu.setOnClickListener(
                    v -> {
                        MainActivity ma = mainActivity();
                        if (ma != null) {
                            ma.openDrawerForBasic();
                        }
                    });
        }
        if (gamepadChromeConnectionWrap != null) {
            gamepadChromeConnectionWrap.setOnClickListener(
                    v -> {
                        MainActivity ma = mainActivity();
                        if (ma != null) {
                            ma.showConnectionDialogFromBasic();
                        }
                    });
        }
        refreshGamepadEmbeddedChrome();
    }

    private void applyGamepadToolbarTopInsets(@NonNull View root) {
        View chromeBar = root.findViewById(R.id.gamepad_chrome_bar);
        View toggleRow = root.findViewById(R.id.toggle_row);
        View editBar = root.findViewById(R.id.gamepad_edit_session_bar);
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    int top =
                            bars.top
                                    + getResources()
                                            .getDimensionPixelSize(R.dimen.gamepad_toolbar_margin_top);
                    setFrameLayoutTopMargin(chromeBar, top);
                    setFrameLayoutTopMargin(toggleRow, top);
                    setFrameLayoutTopMargin(editBar, top);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(root);
    }

    private static void setFrameLayoutTopMargin(@Nullable View child, int topMargin) {
        if (child == null) {
            return;
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).topMargin = topMargin;
            child.setLayoutParams(lp);
        }
    }

    /** Called from {@link MainActivity#notifyBasicChromeFragments()} when connection state changes. */
    public void refreshGamepadEmbeddedChrome() {
        if (gamepadChromeConnectionIcon == null) {
            return;
        }
        MainActivity ma = mainActivity();
        if (ma == null) {
            return;
        }
        ConnectionManager cm = ma.getConnectionManager();
        if (cm != null) {
            ma.applyBasicConnectionIcon(
                    gamepadChromeConnectionIcon,
                    cm.getCurrentConnectionType(),
                    cm.getCurrentConnectionState());
        }
    }

    @Nullable
    private MainActivity mainActivity() {
        return getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
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
        if (GamepadLayoutPresetConstants.isArrowStickModuleId(moduleId)) {
            stickMode = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                    ? MODE_KEY : MODE_ANALOG;
            int defU = 82;
            int defL = 80;
            int defD = 81;
            int defR = 79;
            if (!GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(moduleId)) {
                defU = 26;
                defL = 4;
                defD = 22;
                defR = 7;
            }
            stickUpKey = intOr(m.stickUpKey, defU);
            stickLeftKey = intOr(m.stickLeftKey, defL);
            stickDownKey = intOr(m.stickDownKey, defD);
            stickRightKey = intOr(m.stickRightKey, defR);
            arrowStickPointerSensitivity = effectiveArrowStickMouseGain(m);
        } else {
            if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                stickMode = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(
                        com.openterface.keymod.gamepad.GamepadDpadVariantArt.normalizeVariant(m.dpadVariant))
                        ? MODE_DPAD_SPLIT
                        : MODE_DPAD_CROSS;
            } else {
                stickMode = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                        ? MODE_KEY : MODE_ANALOG;
            }
            stickUpKey = intOr(m.stickUpKey, DEFAULT_STICK_UP);
            stickLeftKey = intOr(m.stickLeftKey, DEFAULT_STICK_LEFT);
            stickDownKey = intOr(m.stickDownKey, DEFAULT_STICK_DOWN);
            stickRightKey = intOr(m.stickRightKey, DEFAULT_STICK_RIGHT);
        }
        stickSizeScale = m.scale;
        if ("stick_left".equals(moduleId)) {
            dpadSplitGapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(m.dpadSplitGapRatio);
            dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(
                    m.dpadSplitOuterReachRatio);
            reconcileDpadSplitOuterToGap();
        }
    }

    private void syncFieldsFromLayoutDoc() {
        if (layoutDoc == null || layoutDoc.layout == null || layoutDoc.modules == null) {
            return;
        }
        twoButtonMode = layoutDoc.layout.showTwoButtons;
        mouseSensitivity = layoutDoc.layout.mouseSensitivity;

        GamepadLayoutPresetDocument.GamepadModule left = findModuleById("stick_left");
        if (left != null) {
            if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(left.type)) {
                if (GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(
                        com.openterface.keymod.gamepad.GamepadDpadVariantArt.normalizeVariant(left.dpadVariant))) {
                    stickMode = MODE_DPAD_SPLIT;
                    dpadSplitGapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(left.dpadSplitGapRatio);
                    dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(
                            left.dpadSplitOuterReachRatio);
                    reconcileDpadSplitOuterToGap();
                } else {
                    stickMode = MODE_DPAD_CROSS;
                    dpadSplitGapRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT;
                    dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(left.type)) {
                stickMode = MODE_KEY;
                dpadSplitGapRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT;
                dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;
            } else {
                stickMode = MODE_ANALOG;
                dpadSplitGapRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT;
                dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;
            }
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
        boolean presetModules = layoutDoc != null && layoutDoc.modules != null;
        // Per-module scale from layout JSON; global stick multiplier only for legacy SIMPLE without preset.
        gamepadView.setStickSizeScale(presetModules ? 1.0f : stickSizeScale);
        gamepadView.setButtonSizeScale(presetModules ? 1.0f : buttonSizeScale);
        float touchpadMouseBtnLayout = 1f;
        if (layoutDoc != null && layoutDoc.layout != null
                && layoutDoc.layout.touchpadMouseButtonScale != null) {
            touchpadMouseBtnLayout = layoutDoc.layout.touchpadMouseButtonScale;
        }
        gamepadView.setTouchpadMouseButtonLayoutScale(touchpadMouseBtnLayout);
        if (prefs != null) {
            gamepadView.setKeyMappingHintsVisible(
                    prefs.getBoolean(GamepadPreferenceKeys.SHOW_KEY_MAPPING_HINTS, true));
        }
        updateGamepadLabels();
        updateGyroListenerRegistration();
    }

    private void updateGyroListenerRegistration() {
        if (!isAdded()) {
            return;
        }
        if (sensorManager == null) {
            sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        }
        sensorManager.unregisterListener(gyroListener);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (layoutDoc != null && layoutDoc.layout != null
                && Boolean.TRUE.equals(layoutDoc.layout.gyroEnabled)
                && gyroSensor != null) {
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
            lastGyroEventNs = 0L;
        }
    }

    private void onGyroscopeSample(SensorEvent e) {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }
        if (layoutDoc == null || layoutDoc.layout == null
                || !Boolean.TRUE.equals(layoutDoc.layout.gyroEnabled)) {
            return;
        }
        ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            return;
        }
        long ns = e.timestamp;
        float dt = lastGyroEventNs <= 0 ? 0.016f : (ns - lastGyroEventNs) * 1e-9f;
        lastGyroEventNs = ns;
        if (dt <= 0f || dt > 0.08f) {
            return;
        }
        float gx = e.values[0];
        float gy = e.values[1];
        float k = mouseSensitivity * 220f * dt;
        int mx = (int) Math.max(-127, Math.min(127, -gy * k));
        int my = (int) Math.max(-127, Math.min(127, gx * k));
        if (mx != 0 || my != 0) {
            cm.sendMouseMovement(mx, my, 0);
        }
    }

    private int hidKeyFromLayoutDoc(String componentId) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(componentId);
        if (m != null && m.hidKey != null) {
            if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER.equals(m.type)) {
                return m.hidKey;
            }
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
        if (activePresetChipButton == null || presetRepository == null) {
            return;
        }
        String activeId = presetRepository.getActivePresetId();
        activePresetChipButton.setText(presetDisplayName(activeId));
    }

    /** Display label for a preset id (falls back to id). */
    @NonNull
    private String presetDisplayName(@Nullable String presetId) {
        if (presetRepository == null || presetId == null) {
            return "";
        }
        for (GamepadLayoutPresetRepository.PresetRef r : presetRepository.listPresets()) {
            if (r != null && presetId.equals(r.id)) {
                if (r.displayName != null && !r.displayName.isEmpty()) {
                    return r.displayName;
                }
                break;
            }
        }
        return presetId;
    }

    /** Applies optional prefs for customize-mode long-press (see {@link GamepadPreferenceKeys}). */
    private void applyGamepadEditTouchPreferences() {
        if (gamepadView == null || prefs == null) {
            return;
        }
        int ms = prefs.getInt(GamepadPreferenceKeys.EDIT_LONG_PRESS_MS, 600);
        float movePx;
        if (prefs.contains(GamepadPreferenceKeys.EDIT_LONG_PRESS_CANCEL_DP)) {
            float dp = prefs.getFloat(GamepadPreferenceKeys.EDIT_LONG_PRESS_CANCEL_DP, 10f);
            movePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                    requireContext().getResources().getDisplayMetrics());
        } else {
            movePx = 15f;
        }
        gamepadView.setEditLongPressConfig(ms, movePx);
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

    private void showGamepadPresetsBottomSheet() {
        Context ctx = requireContext();
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        View sheet = LayoutInflater.from(ctx).inflate(R.layout.bottom_sheet_gamepad_presets, null, false);
        dialog.setContentView(sheet);
        dialog.setDismissWithAnimation(true);
        dialog.setOnShowListener(d -> {
            View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottom != null) {
                BottomSheetBehavior.from(bottom).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        RecyclerView recycler = sheet.findViewById(R.id.gamepad_presets_recycler);
        // Do not call setHasFixedSize(true): layout uses wrap_content height (lint InvalidSetHasFixedSize).
        recycler.setItemAnimator(null);
        MaterialButton newLayoutBtn = sheet.findViewById(R.id.gamepad_presets_new_layout);
        MaterialButton importBtn = sheet.findViewById(R.id.gamepad_presets_import_btn);

        final GamepadPresetListAdapter[] presetListAdapterRef = new GamepadPresetListAdapter[1];
        presetListAdapterRef[0] = new GamepadPresetListAdapter(new GamepadPresetListAdapter.Listener() {
            @Override
            public void onActivatePreset(@NonNull String id) {
                presetRepository.persistActiveSnapshot();
                String err = presetRepository.activateAndApply(id);
                if (err == null) {
                    reloadFromPrefsAndApplyView();
                    Toast.makeText(ctx, R.string.gamepad_presets_activated, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onSharePreset(@NonNull String id) {
                dialog.dismiss();
                sharePresetJson(id);
            }

            @Override
            public void onOverflow(@NonNull String id, @NonNull View anchor) {
                showPresetOverflowMenu(id, anchor, dialog, presetListAdapterRef[0]);
            }
        });
        GamepadPresetListAdapter adapter = presetListAdapterRef[0];
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);
        refreshPresetSheetAdapter(adapter);

        newLayoutBtn.setOnClickListener(v -> {
            dialog.dismiss();
            promptNewUserPreset();
        });
        importBtn.setOnClickListener(v -> {
            dialog.dismiss();
            importPresetLauncher.launch(new String[]{"application/json"});
        });

        dialog.show();
    }

    private void refreshPresetSheetAdapter(@NonNull GamepadPresetListAdapter adapter) {
        adapter.setData(presetRepository.listPresets(), presetRepository.getActivePresetId());
    }

    private void showPresetOverflowMenu(
            @NonNull String presetId,
            @NonNull View anchor,
            @NonNull BottomSheetDialog hostDialog,
            @NonNull GamepadPresetListAdapter adapter) {
        PopupMenu pm = new PopupMenu(requireContext(), anchor);
        pm.getMenuInflater().inflate(R.menu.menu_gamepad_preset_row, pm.getMenu());
        if (GamepadLayoutPresetConstants.isPresetDeletionProtected(presetId)) {
            pm.getMenu().findItem(R.id.gamepad_preset_delete).setVisible(false);
        }
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.gamepad_preset_rename) {
                promptRenamePreset(presetId, () -> {
                    refreshPresetSheetAdapter(adapter);
                    updateActivePresetNameUi();
                });
                return true;
            }
            if (id == R.id.gamepad_preset_duplicate) {
                GamepadLayoutPresetRepository.DuplicateResult dup = presetRepository.duplicatePreset(presetId);
                if (dup.isSuccess()) {
                    Toast.makeText(requireContext(), R.string.gamepad_preset_duplicated, Toast.LENGTH_SHORT).show();
                    refreshPresetSheetAdapter(adapter);
                } else {
                    Toast.makeText(requireContext(),
                            dup.error != null ? dup.error : getString(R.string.gamepad_preset_action_failed),
                            Toast.LENGTH_LONG).show();
                }
                return true;
            }
            if (id == R.id.gamepad_preset_share) {
                hostDialog.dismiss();
                sharePresetJson(presetId);
                return true;
            }
            if (id == R.id.gamepad_preset_delete) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.gamepad_preset_delete_title)
                        .setMessage(R.string.gamepad_preset_delete_message)
                        .setPositiveButton(R.string.gamepad_preset_delete_confirm, (d, w) -> {
                            String err = presetRepository.deletePreset(presetId);
                            if (err != null) {
                                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                                refreshPresetSheetAdapter(adapter);
                            } else {
                                hostDialog.dismiss();
                                reloadFromPrefsAndApplyView();
                                updateActivePresetNameUi();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void promptRenamePreset(@NonNull String presetId, @NonNull Runnable onOk) {
        Context ctx = requireContext();
        View wrap = LayoutInflater.from(ctx).inflate(R.layout.dialog_gamepad_text_field, null, false);
        TextInputLayout til = wrap.findViewById(R.id.gamepad_text_input_layout);
        TextInputEditText input = wrap.findViewById(R.id.gamepad_text_input);
        til.setHint(R.string.gamepad_preset_rename_hint);
        String current = presetDisplayName(presetId);
        input.setText(current);
        input.setSelection(current.length());
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_preset_rename_title)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String err = presetRepository.renamePreset(presetId, input.getText().toString());
                    if (err == null) {
                        onOk.run();
                    } else {
                        Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptNewUserPreset() {
        Context ctx = requireContext();
        View wrap = LayoutInflater.from(ctx).inflate(R.layout.dialog_gamepad_text_field, null, false);
        TextInputLayout til = wrap.findViewById(R.id.gamepad_text_input_layout);
        TextInputEditText input = wrap.findViewById(R.id.gamepad_text_input);
        til.setHint(R.string.gamepad_preset_new_hint);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gamepad_preset_new_title)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(ctx, R.string.gamepad_preset_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    presetRepository.persistActiveSnapshot();
                    String active = presetRepository.getActivePresetId();
                    GamepadLayoutPresetRepository.DuplicateResult dup =
                            presetRepository.duplicatePreset(active);
                    if (!dup.isSuccess()) {
                        Toast.makeText(ctx,
                                dup.error != null ? dup.error
                                        : getString(R.string.gamepad_preset_action_failed),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    String newId = dup.newId;
                    String err = presetRepository.renamePreset(newId, name);
                    if (err != null) {
                        Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String applyErr = presetRepository.activateAndApply(newId);
                    if (applyErr != null) {
                        Toast.makeText(ctx, applyErr, Toast.LENGTH_LONG).show();
                        return;
                    }
                    reloadFromPrefsAndApplyView();
                    Toast.makeText(ctx, R.string.gamepad_preset_created, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddModuleMenu() {
        if (layoutDoc == null) {
            layoutDoc = GamepadLayoutDocumentStore.loadOrCreate(requireContext());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gamepad_add_module_title)
                .setItems(new String[]{
                        getString(R.string.gamepad_add_touchpad),
                        getString(R.string.gamepad_add_arrow_stick),
                        getString(R.string.gamepad_add_dpad_cross_left),
                        getString(R.string.gamepad_add_extra_button),
                }, (d, which) -> {
                    if (which == 0) {
                        GamepadLayoutDocEditor.addTouchpad(layoutDoc);
                    } else if (which == 1) {
                        GamepadLayoutDocEditor.addStickKeyExtra(layoutDoc);
                    } else if (which == 2) {
                        GamepadLayoutDocEditor.setLeftStickDpadCross(layoutDoc);
                    } else {
                        GamepadLayoutDocEditor.addButton(layoutDoc);
                    }
                    applyLayoutDocFromMemory();
                })
                .show();
    }

    /** Share JSON for the given preset (active = live prefs snapshot; others = file on disk). */
    private void sharePresetJson(@Nullable String presetId) {
        if (presetRepository == null) {
            return;
        }
        String id = presetId != null ? presetId : presetRepository.getActivePresetId();
        String active = presetRepository.getActivePresetId();
        String displayName = presetDisplayName(id);
        if (displayName.isEmpty()) {
            displayName = id != null ? id : "layout";
        }
        try {
            GamepadLayoutPresetDocument doc;
            if (id != null && id.equals(active)) {
                doc = GamepadLayoutPresetSnapshotBuilder.buildFrom(
                        requireContext(),
                        id != null ? id : GamepadLayoutPresetConstants.DEFAULT_PRESET_ID,
                        displayName);
            } else {
                doc = presetRepository.loadDocument(id != null ? id : GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
                if (doc == null) {
                    Toast.makeText(requireContext(), R.string.gamepad_presets_export_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (doc.meta == null) {
                    doc.meta = new GamepadLayoutPresetDocument.Meta();
                }
                doc.meta.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
                doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;
            }
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
            if (layoutDoc != null && layoutDoc.modules != null) {
                for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
                    if (m == null || m.id == null) {
                        continue;
                    }
                    if (!GamepadLayoutPresetConstants.isArrowStickModuleId(m.id)) {
                        continue;
                    }
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)) {
                        continue;
                    }
                    boolean[] st = arrowStickVirtKeys.get(m.id);
                    if (st == null) {
                        continue;
                    }
                    if (st[0] && m.stickUpKey != null) {
                        addKeyOrMod(regularKeys, m.stickUpKey);
                    }
                    if (st[1] && m.stickLeftKey != null) {
                        addKeyOrMod(regularKeys, m.stickLeftKey);
                    }
                    if (st[2] && m.stickDownKey != null) {
                        addKeyOrMod(regularKeys, m.stickDownKey);
                    }
                    if (st[3] && m.stickRightKey != null) {
                        addKeyOrMod(regularKeys, m.stickRightKey);
                    }
                }
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
                " arrowVirt=" + arrowStickVirtKeys +
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

    /**
     * Split D-pad hit targets use ids {@code dpad_*}; long-press should open the same menu as
     * {@code stick_left}.
     */
    @Nullable
    private static String resolveLongPressMenuModuleId(@Nullable String componentId) {
        if (componentId != null && componentId.startsWith("dpad_")) {
            return "stick_left";
        }
        return componentId;
    }

    /** Confirms then removes a module and dismisses {@code parentDialog} if non-null. */
    private void confirmRemoveGamepadModule(
            @NonNull String moduleId,
            @Nullable DialogInterface parentDialog) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.gamepad_module_remove_confirm_message)
                .setPositiveButton(R.string.gamepad_menu_remove, (d2, w2) -> {
                    GamepadLayoutDocEditor.removeModule(layoutDoc, moduleId);
                    faceButtonPressed.remove(moduleId);
                    applyLayoutDocFromMemory();
                    if (parentDialog != null) {
                        parentDialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Updates selection state on the accent color row so the current choice matches
     * {@link GamepadLayoutPresetDocument.GamepadModule#moduleAccentArgb}.
     * Preset swatches use a centered color dot inside a ring wrapper when selected.
     */
    private void refreshGamepadAccentSectionUi(
            @NonNull GamepadLayoutPresetDocument.GamepadModule module,
            @Nullable MaterialButton themeDefaultBtn,
            @Nullable MaterialButton customBtn,
            @NonNull List<View> swatchViews,
            @NonNull Context ctx) {
        int primary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.primary));
        int outline = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline,
                ContextCompat.getColor(ctx, R.color.gray_600));
        int outlineSoft = Color.argb(140, Color.red(outline), Color.green(outline), Color.blue(outline));

        boolean themeSelected = module.moduleAccentArgb == null;
        if (themeDefaultBtn != null) {
            themeDefaultBtn.setStrokeWidth(0);
            themeDefaultBtn.setIcon(themeSelected ? ContextCompat.getDrawable(ctx, R.drawable.ic_check) : null);
            themeDefaultBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            themeDefaultBtn.setIconPadding(dp(8));
            if (themeSelected) {
                int onTonal = MaterialColors.getColor(themeDefaultBtn,
                        com.google.android.material.R.attr.colorOnSecondaryContainer, primary);
                themeDefaultBtn.setIconTint(ColorStateList.valueOf(onTonal));
            } else {
                themeDefaultBtn.setIconTint(null);
            }
        }

        Integer ma = module.moduleAccentArgb;
        int opaque = ma != null ? GamepadModuleAccent.toOpaqueArgb(ma) : 0;
        boolean matchedPreset = false;
        int[] presets = GamepadModuleAccent.PRESET_ARGB;
        for (int i = 0; i < swatchViews.size() && i < presets.length; i++) {
            int fill = GamepadModuleAccent.toOpaqueArgb(presets[i]);
            boolean sel = ma != null && opaque == fill;
            if (sel) {
                matchedPreset = true;
            }
            View wrap = swatchViews.get(i);
            View dotView = wrap;
            if (wrap instanceof ViewGroup && ((ViewGroup) wrap).getChildCount() > 0) {
                dotView = ((ViewGroup) wrap).getChildAt(0);
            }
            GradientDrawable dotGd = new GradientDrawable();
            dotGd.setShape(GradientDrawable.OVAL);
            dotGd.setColor(fill);
            dotGd.setStroke(sel ? 0 : dp(1), outlineSoft);
            dotView.setBackground(dotGd);
            if (wrap instanceof FrameLayout) {
                if (sel) {
                    GradientDrawable ring = new GradientDrawable();
                    ring.setShape(GradientDrawable.OVAL);
                    ring.setColor(Color.TRANSPARENT);
                    ring.setStroke(dp(3), primary);
                    wrap.setBackground(ring);
                    wrap.setScaleX(1.06f);
                    wrap.setScaleY(1.06f);
                } else {
                    wrap.setBackground(null);
                    wrap.setScaleX(1f);
                    wrap.setScaleY(1f);
                }
            }
            wrap.setContentDescription(ctx.getString(sel
                    ? R.string.gamepad_module_color_swatch_selected_cd
                    : R.string.gamepad_module_color_swatch_cd));
        }

        boolean customSelected = ma != null && !matchedPreset;
        if (customBtn != null) {
            customBtn.setStrokeWidth(customSelected ? dp(2) : dp(1));
            customBtn.setStrokeColor(ColorStateList.valueOf(customSelected ? primary : outline));
            customBtn.setIcon(customSelected ? ContextCompat.getDrawable(ctx, R.drawable.ic_check) : null);
            customBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            customBtn.setIconPadding(dp(8));
            if (customSelected) {
                customBtn.setIconTint(ColorStateList.valueOf(primary));
            } else {
                customBtn.setIconTint(null);
            }
            customBtn.setContentDescription(ctx.getString(customSelected
                    ? R.string.gamepad_module_color_custom_selected_cd
                    : R.string.gamepad_module_color_custom_cd));
        }

        if (themeDefaultBtn != null) {
            themeDefaultBtn.setContentDescription(ctx.getString(themeSelected
                    ? R.string.gamepad_module_color_theme_default_selected_cd
                    : R.string.gamepad_module_color_theme_default_cd));
        }
    }

    private void bindGamepadModuleColorSection(
            @NonNull View colorSection,
            @NonNull GamepadLayoutPresetDocument.GamepadModule module) {
        Context ctx = colorSection.getContext();
        MaterialButton themeDefaultBtn = colorSection.findViewById(R.id.module_color_theme_default);
        MaterialButton customBtn = colorSection.findViewById(R.id.module_color_custom);
        LinearLayout swatchRow = colorSection.findViewById(R.id.module_color_swatches);
        if (swatchRow == null) {
            return;
        }
        swatchRow.removeAllViews();
        int ringOuter = dp(46);
        int colorInner = dp(34);
        int marginH = dp(4);
        int marginV = dp(6);
        List<View> swatchViews = new ArrayList<>();
        for (int c : GamepadModuleAccent.PRESET_ARGB) {
            FrameLayout wrap = new FrameLayout(ctx);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ringOuter, ringOuter);
            wlp.setMargins(marginH, marginV, marginH, marginV);
            wrap.setLayoutParams(wlp);
            View colorDot = new View(ctx);
            FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(
                    colorInner, colorInner, Gravity.CENTER);
            colorDot.setLayoutParams(innerLp);
            wrap.addView(colorDot);
            wrap.setOnClickListener(v -> {
                module.moduleAccentArgb = GamepadModuleAccent.toOpaqueArgb(c);
                syncGamepadViewFromDoc();
                refreshGamepadAccentSectionUi(module, themeDefaultBtn, customBtn, swatchViews, ctx);
            });
            swatchRow.addView(wrap);
            swatchViews.add(wrap);
        }
        Runnable refreshSelection = () ->
                refreshGamepadAccentSectionUi(module, themeDefaultBtn, customBtn, swatchViews, ctx);
        if (themeDefaultBtn != null) {
            themeDefaultBtn.setOnClickListener(v -> {
                module.moduleAccentArgb = null;
                syncGamepadViewFromDoc();
                refreshSelection.run();
            });
        }
        if (customBtn != null) {
            customBtn.setOnClickListener(v -> showGamepadModuleCustomColorDialog(module, refreshSelection));
        }
        refreshSelection.run();
    }

    /**
     * Parses {@code #RRGGBB}, {@code RRGGBB}, or {@code 0xRRGGBB} (6 hex digits). Returns opaque ARGB or null.
     */
    @Nullable
    private static Integer parseUserHexColor(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.toUpperCase(Locale.ROOT);
        if (t.startsWith("#")) {
            t = t.substring(1);
        } else if (t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.length() != 6) {
            return null;
        }
        for (int i = 0; i < 6; i++) {
            char c = t.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) {
                return null;
            }
        }
        try {
            return 0xFF000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showGamepadModuleCustomColorDialog(
            @NonNull GamepadLayoutPresetDocument.GamepadModule module,
            @Nullable Runnable onAccentApplied) {
        Context ctx = requireContext();
        int cur = module.moduleAccentArgb != null
                ? GamepadModuleAccent.toOpaqueArgb(module.moduleAccentArgb)
                : GamepadModuleAccent.toOpaqueArgb(MaterialColors.getColor(ctx,
                        com.google.android.material.R.attr.colorPrimary,
                        ContextCompat.getColor(ctx, R.color.primary)));
        int r0 = Color.red(cur);
        int g0 = Color.green(cur);
        int b0 = Color.blue(cur);
        int outline = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline,
                ContextCompat.getColor(ctx, R.color.gray_600));
        int errorColor = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(ctx, R.color.primary));

        LinearLayout shell = new LinearLayout(ctx);
        shell.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        shell.setPadding(pad, dp(12), pad, dp(16));

        TextView summary = new TextView(ctx);
        summary.setText(R.string.gamepad_module_color_custom_summary);
        summary.setTextAppearance(ctx, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sumLp.bottomMargin = dp(12);
        shell.addView(summary, sumLp);

        View preview = new View(ctx);
        final GradientDrawable previewShape = new GradientDrawable();
        previewShape.setCornerRadius(dp(12));
        previewShape.setStroke(dp(1), outline);
        previewShape.setColor(Color.rgb(r0, g0, b0));
        preview.setBackground(previewShape);
        LinearLayout.LayoutParams preLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88));
        preLp.bottomMargin = dp(16);
        preview.setLayoutParams(preLp);
        shell.addView(preview);

        SeekBar rSeek = new SeekBar(ctx);
        SeekBar gSeek = new SeekBar(ctx);
        SeekBar bSeek = new SeekBar(ctx);
        rSeek.setMax(255);
        gSeek.setMax(255);
        bSeek.setMax(255);
        rSeek.setProgress(r0);
        gSeek.setProgress(g0);
        bSeek.setProgress(b0);
        rSeek.setProgressTintList(ColorStateList.valueOf(0xFFE57373));
        rSeek.setThumbTintList(ColorStateList.valueOf(0xFFE53935));
        gSeek.setProgressTintList(ColorStateList.valueOf(0xFF81C784));
        gSeek.setThumbTintList(ColorStateList.valueOf(0xFF43A047));
        bSeek.setProgressTintList(ColorStateList.valueOf(0xFF64B5F6));
        bSeek.setThumbTintList(ColorStateList.valueOf(0xFF1E88E5));

        TextView rVal = newTextViewMonoValue(ctx);
        TextView gVal = newTextViewMonoValue(ctx);
        TextView bVal = newTextViewMonoValue(ctx);
        rVal.setText(String.valueOf(r0));
        gVal.setText(String.valueOf(g0));
        bVal.setText(String.valueOf(b0));

        shell.addView(buildRgbSliderRow(ctx, ctx.getString(R.string.gamepad_module_color_channel_r), rSeek, rVal));
        shell.addView(buildRgbSliderRow(ctx, ctx.getString(R.string.gamepad_module_color_channel_g), gSeek, gVal));
        shell.addView(buildRgbSliderRow(ctx, ctx.getString(R.string.gamepad_module_color_channel_b), bSeek, bVal));

        TextView hexLabel = new TextView(ctx);
        hexLabel.setText(R.string.gamepad_module_color_hex_label);
        hexLabel.setTextAppearance(ctx, R.style.TextAppearance_KeyMod_GamepadConfig_Section);
        LinearLayout.LayoutParams hexLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hexLabelLp.topMargin = dp(12);
        hexLabelLp.bottomMargin = dp(6);
        shell.addView(hexLabel, hexLabelLp);

        EditText hexEdit = new EditText(ctx);
        hexEdit.setHint(R.string.gamepad_module_color_hex_hint);
        hexEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hexEdit.setText(String.format(Locale.US, "#%06X", Color.rgb(r0, g0, b0) & 0xFFFFFF));
        hexEdit.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        shell.addView(hexEdit, hexLp);

        TextView hexError = new TextView(ctx);
        hexError.setTextAppearance(ctx, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hexError.setTextColor(errorColor);
        hexError.setVisibility(View.GONE);
        hexError.setPadding(0, dp(4), 0, 0);
        shell.addView(hexError, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final boolean[] syncFromHex = { false };

        Runnable updatePreviewAndHexFromSliders = () -> {
            int rr = rSeek.getProgress();
            int gg = gSeek.getProgress();
            int bb = bSeek.getProgress();
            previewShape.setColor(Color.rgb(rr, gg, bb));
            preview.invalidate();
            rVal.setText(String.valueOf(rr));
            gVal.setText(String.valueOf(gg));
            bVal.setText(String.valueOf(bb));
            syncFromHex[0] = true;
            hexEdit.setText(String.format(Locale.US, "#%06X", Color.rgb(rr, gg, bb) & 0xFFFFFF));
            hexEdit.setSelection(hexEdit.getText().length());
            syncFromHex[0] = false;
            hexError.setVisibility(View.GONE);
        };

        SeekBar.OnSeekBarChangeListener sliderListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || syncFromHex[0]) {
                    return;
                }
                updatePreviewAndHexFromSliders.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        rSeek.setOnSeekBarChangeListener(sliderListener);
        gSeek.setOnSeekBarChangeListener(sliderListener);
        bSeek.setOnSeekBarChangeListener(sliderListener);

        hexEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncFromHex[0]) {
                    return;
                }
                Integer v = parseUserHexColor(s.toString());
                hexError.setVisibility(View.GONE);
                if (v == null) {
                    return;
                }
                syncFromHex[0] = true;
                rSeek.setProgress(Color.red(v));
                gSeek.setProgress(Color.green(v));
                bSeek.setProgress(Color.blue(v));
                rVal.setText(String.valueOf(Color.red(v)));
                gVal.setText(String.valueOf(Color.green(v)));
                bVal.setText(String.valueOf(Color.blue(v)));
                previewShape.setColor(Color.rgb(Color.red(v), Color.green(v), Color.blue(v)));
                preview.invalidate();
                syncFromHex[0] = false;
            }
        });

        MaxHeightNestedScrollView scrollRoot = new MaxHeightNestedScrollView(ctx);
        scrollRoot.setFillViewport(false);
        scrollRoot.setClipToPadding(false);
        scrollRoot.addView(shell, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyGamepadModuleConfigScrollMaxHeight(scrollRoot);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.gamepad_module_color_custom_title)
                .setView(scrollRoot)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) {
                ok.setOnClickListener(v -> {
                    String hexRaw = hexEdit.getText().toString();
                    Integer fromHex = parseUserHexColor(hexRaw);
                    if (!hexRaw.trim().isEmpty() && fromHex == null) {
                        hexError.setText(R.string.gamepad_module_color_hex_invalid);
                        hexError.setVisibility(View.VISIBLE);
                        return;
                    }
                    int color = fromHex != null ? fromHex
                            : Color.rgb(rSeek.getProgress(), gSeek.getProgress(), bSeek.getProgress());
                    module.moduleAccentArgb = GamepadModuleAccent.toOpaqueArgb(color);
                    syncGamepadViewFromDoc();
                    if (onAccentApplied != null) {
                        onAccentApplied.run();
                    }
                    dialog.dismiss();
                });
            }
        });
        dialog.show();
    }

    private static TextView newTextViewMonoValue(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setTextAppearance(ctx, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tv.setMinEms(3);
        return tv;
    }

    private static int dpForContext(Context ctx, int dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout buildRgbSliderRow(Context ctx, String label, SeekBar seek, TextView valueTv) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vPad = dpForContext(ctx, 6);
        row.setPadding(0, vPad, 0, vPad);
        TextView lab = new TextView(ctx);
        lab.setText(label);
        lab.setTextAppearance(ctx, R.style.TextAppearance_KeyMod_GamepadConfig_Section);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                dpForContext(ctx, 28), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(lab, labLp);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.setMargins(dpForContext(ctx, 8), 0, dpForContext(ctx, 8), 0);
        row.addView(seek, seekLp);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                dpForContext(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(valueTv, valLp);
        return row;
    }

    /** Standalone accent editor (e.g. touchpad long-press). */
    private void showGamepadModuleAccentDialog(@Nullable String title,
            @Nullable GamepadLayoutPresetDocument.GamepadModule module) {
        if (module == null) {
            return;
        }
        Context ctx = requireContext();
        LinearLayout shell = new LinearLayout(ctx);
        shell.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        shell.setPadding(pad, pad, pad, pad);
        LayoutInflater.from(ctx).inflate(R.layout.include_gamepad_module_color_section, shell, true);
        View section = shell.findViewById(R.id.module_color_section);
        if (section != null) {
            bindGamepadModuleColorSection(section, module);
        }
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx).setView(shell);
        if (title != null && !title.isEmpty()) {
            b.setTitle(title);
        }
        b.setPositiveButton(android.R.string.ok, (d, w) -> applyLayoutDocFromMemory())
                .show();
    }

    private void handleLongPressMenuChoice(
            String choice,
            String componentId,
            String moduleId,
            boolean hasKeyMapping) {
        if (choice.equals(getString(R.string.gamepad_menu_touchpad_resize))) {
            showTouchpadResizeDialog();
        } else if (choice.equals(getString(R.string.gamepad_menu_touchpad_color))) {
            showGamepadModuleAccentDialog(getString(R.string.gamepad_touchpad_color_title),
                    findModuleById("touchpad_1"));
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
        } else if (choice.equals(getString(R.string.gamepad_menu_configure_stick))) {
            showConfigDialog(moduleId);
        } else if (choice.equals(getString(R.string.gamepad_menu_edit_keys)) && hasKeyMapping) {
            showConfigDialog(moduleId);
        } else if (choice.equals(getString(R.string.gamepad_menu_remove))) {
            GamepadLayoutDocEditor.removeModule(layoutDoc, moduleId);
            faceButtonPressed.remove(moduleId);
            applyLayoutDocFromMemory();
        }
    }

    private void showLongPressMenu(String componentId) {
        final String moduleId = resolveLongPressMenuModuleId(componentId);
        String componentName;
        final boolean hasKeyMapping;
        if ("stick_left".equals(moduleId)) {
            GamepadLayoutPresetDocument.GamepadModule left = findModuleById("stick_left");
            if (left != null && GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(left.type)) {
                componentName = getString(R.string.gamepad_component_left_dpad);
            } else {
                componentName = getString(R.string.gamepad_component_left_stick);
            }
            hasKeyMapping = true;
        } else if ("stick_right".equals(moduleId)) {
            componentName = getString(R.string.gamepad_component_right_stick);
            hasKeyMapping = true;
        } else if (GamepadLayoutPresetConstants.isArrowStickModuleId(moduleId)) {
            componentName = getString(R.string.gamepad_component_arrow_stick);
            hasKeyMapping = true;
        } else if (moduleId != null && moduleId.startsWith("stick_")) {
            componentName = moduleId;
            hasKeyMapping = true;
        } else if ("button_a".equals(moduleId)) {
            componentName = getString(R.string.gamepad_component_button_a);
            hasKeyMapping = true;
        } else if ("button_b".equals(moduleId)) {
            componentName = getString(R.string.gamepad_component_button_b);
            hasKeyMapping = true;
        } else if (moduleId != null && moduleId.startsWith("button_")) {
            componentName = moduleId;
            hasKeyMapping = true;
        } else if ("touchpad_1".equals(componentId)) {
            componentName = getString(R.string.gamepad_component_touchpad);
            hasKeyMapping = false;
        } else if (mouseButtonForComponentId(componentId) != null) {
            componentName = getString(R.string.gamepad_component_mouse_button);
            hasKeyMapping = false;
        } else {
            componentName = moduleId;
            hasKeyMapping = false;
        }

        final boolean isStickSurfaceConfig = "stick_left".equals(moduleId)
                || "stick_right".equals(moduleId)
                || GamepadLayoutPresetConstants.isArrowStickModuleId(moduleId);

        ArrayList<String> opts = new ArrayList<>();
        if ("touchpad_1".equals(componentId)) {
            opts.add(getString(R.string.gamepad_menu_touchpad_resize));
            opts.add(getString(R.string.gamepad_menu_touchpad_color));
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
            if (isStickSurfaceConfig) {
                opts.add(getString(R.string.gamepad_menu_configure_stick));
            } else {
                opts.add(getString(R.string.gamepad_menu_edit_keys));
            }
        }
        if (GamepadLayoutDocEditor.canRemove(moduleId)) {
            opts.add(getString(R.string.gamepad_menu_remove));
        }

        if (opts.isEmpty()) {
            return;
        }

        final String configureStick = getString(R.string.gamepad_menu_configure_stick);
        final String editKeys = getString(R.string.gamepad_menu_edit_keys);
        final String removeLabel = getString(R.string.gamepad_menu_remove);

        if (opts.size() == 1) {
            handleLongPressMenuChoice(opts.get(0), componentId, moduleId, hasKeyMapping);
            return;
        }
        if (opts.size() == 2 && opts.contains(removeLabel)
                && (opts.contains(configureStick) || opts.contains(editKeys))) {
            showConfigDialog(moduleId);
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(componentName)
                .setItems(opts.toArray(new String[0]), (dialog, which) ->
                        handleLongPressMenuChoice(
                                opts.get(which), componentId, moduleId, hasKeyMapping))
                .show();
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

        new MaterialAlertDialogBuilder(ctx)
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

        new MaterialAlertDialogBuilder(ctx)
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

        LayoutInflater.from(ctx).inflate(R.layout.include_gamepad_module_color_section, root, true);
        View mouseColorSection = root.findViewById(R.id.module_color_section);
        if (mouseColorSection != null) {
            bindGamepadModuleColorSection(mouseColorSection, m);
        }

        TextView title = new TextView(ctx);
        title.setText(R.string.gamepad_mouse_btn_size_pct);
        android.widget.SeekBar seek = new android.widget.SeekBar(ctx);
        seek.setMax(150);
        seek.setProgress(Math.max(0, Math.min(150, Math.round(m.scale * 100f) - 50)));

        root.addView(title);
        root.addView(seek);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.gamepad_mouse_btn_module_size_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    m.scale = (seek.getProgress() + 50) / 100f;
                    applyLayoutDocFromMemory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exitLayoutEditSession() {
        if (editModeMaterialButton != null) {
            editModeMaterialButton.setChecked(false);
        }
        View v = getView();
        if (v != null) {
            View sessionBar = v.findViewById(R.id.gamepad_edit_session_bar);
            View toggleRow = v.findViewById(R.id.toggle_row);
            if (sessionBar != null) {
                sessionBar.setVisibility(View.GONE);
            }
            if (toggleRow != null) {
                toggleRow.setVisibility(View.VISIBLE);
            }
        }
        Log.d(TAG, "Exited layout edit / move session");
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stick_config, null);
        applyGamepadModuleConfigScrollMaxHeight(dialogView);
        builder.setView(dialogView);
        if (GamepadLayoutDocEditor.canRemove(stickConfigModuleId)) {
            builder.setNeutralButton(R.string.gamepad_menu_remove, (d, which) ->
                    confirmRemoveGamepadModule(stickConfigModuleId, d));
        }

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final boolean[] stickDialogCommitted = { false };
        final boolean[] rollbackDpadSplitGapNull = { true };
        final float[] rollbackDpadSplitGapValue = { GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT };
        final boolean[] rollbackDpadOuterNull = { true };
        final float[] rollbackDpadOuterValue = { GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT };
        if ("stick_left".equals(stickConfigModuleId)) {
            GamepadLayoutPresetDocument.GamepadModule snapLeft = findModuleById("stick_left");
            if (snapLeft != null && snapLeft.dpadSplitGapRatio != null) {
                rollbackDpadSplitGapNull[0] = false;
                rollbackDpadSplitGapValue[0] = snapLeft.dpadSplitGapRatio;
            }
            if (snapLeft != null && snapLeft.dpadSplitOuterReachRatio != null) {
                rollbackDpadOuterNull[0] = false;
                rollbackDpadOuterValue[0] = snapLeft.dpadSplitOuterReachRatio;
            }
        }
        dialog.setOnDismissListener(d -> {
            if (stickDialogCommitted[0]) {
                return;
            }
            if (!"stick_left".equals(stickConfigModuleId)) {
                return;
            }
            GamepadLayoutPresetDocument.GamepadModule lm = findModuleById("stick_left");
            if (lm == null) {
                return;
            }
            if (rollbackDpadSplitGapNull[0]) {
                lm.dpadSplitGapRatio = null;
            } else {
                lm.dpadSplitGapRatio = rollbackDpadSplitGapValue[0];
            }
            if (rollbackDpadOuterNull[0]) {
                lm.dpadSplitOuterReachRatio = null;
            } else {
                lm.dpadSplitOuterReachRatio = rollbackDpadOuterValue[0];
            }
            syncGamepadViewFromDoc();
        });

        TextView titleTv = dialogView.findViewById(R.id.stick_config_title);
        if (titleTv != null) {
            if ("stick_left".equals(stickConfigModuleId)) {
                titleTv.setText(R.string.gamepad_stick_config_title_left);
            } else if ("stick_right".equals(stickConfigModuleId)) {
                titleTv.setText(R.string.gamepad_stick_config_title_right);
            } else if (GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)) {
                titleTv.setText(R.string.gamepad_component_arrow_stick);
            } else {
                titleTv.setText(R.string.gamepad_stick_config_title);
            }
        }
        TextView dpadHint = dialogView.findViewById(R.id.stick_config_dpad_hint);
        if (dpadHint != null) {
            boolean showDpadHint = "stick_left".equals(stickConfigModuleId);
            dpadHint.setVisibility(showDpadHint ? View.VISIBLE : View.GONE);
        }

        final RadioGroup modeGroup = dialogView.findViewById(R.id.stick_mode_group);
        final LinearLayout keySection = dialogView.findViewById(R.id.key_mapping_section);
        final LinearLayout splitGapSection = dialogView.findViewById(R.id.dpad_split_gap_section);
        final SeekBar splitGapSeek = dialogView.findViewById(R.id.dpad_split_gap_seek);
        final LinearLayout splitOuterSection = dialogView.findViewById(R.id.dpad_split_outer_section);
        final SeekBar splitOuterSeek = dialogView.findViewById(R.id.dpad_split_outer_seek);
        final Button keyUp = dialogView.findViewById(R.id.key_up);
        final Button keyLeft = dialogView.findViewById(R.id.key_left);
        final Button keyRight = dialogView.findViewById(R.id.key_right);
        final Button keyDown = dialogView.findViewById(R.id.key_down);
        View dpadCrossMode = dialogView.findViewById(R.id.stick_mode_dpad_cross);
        if (dpadCrossMode != null) {
            dpadCrossMode.setVisibility("stick_left".equals(stickConfigModuleId) ? View.VISIBLE : View.GONE);
        }
        View dpadSplitMode = dialogView.findViewById(R.id.stick_mode_dpad_split);
        if (dpadSplitMode != null) {
            dpadSplitMode.setVisibility("stick_left".equals(stickConfigModuleId) ? View.VISIBLE : View.GONE);
        }

        final LinearLayout arrowMouseSensSection =
                dialogView.findViewById(R.id.arrow_stick_mouse_sensitivity_section);
        final SeekBar arrowMouseSeek = dialogView.findViewById(R.id.arrow_stick_mouse_sensitivity_seek);
        if (arrowMouseSeek != null && GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)) {
            arrowMouseSeek.setProgress(arrowStickMouseGainToSeek(arrowStickPointerSensitivity));
            arrowMouseSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) {
                        return;
                    }
                    arrowStickPointerSensitivity = arrowStickSeekToMouseGain(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        // Set initial mode and key labels
        if (MODE_DPAD_CROSS.equals(stickMode)) {
            modeGroup.check(R.id.stick_mode_dpad_cross);
        } else if (MODE_DPAD_SPLIT.equals(stickMode)) {
            modeGroup.check(R.id.stick_mode_dpad_split);
        } else if (MODE_KEY.equals(stickMode)) {
            modeGroup.check(R.id.stick_mode_key);
        } else {
            modeGroup.check(R.id.stick_mode_analog);
        }
        updateStickConfigSections(modeGroup, keySection, splitGapSection, splitOuterSection, arrowMouseSensSection);
        if ("stick_left".equals(stickConfigModuleId)
                && modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_dpad_split) {
            if (splitGapSeek != null) {
                splitGapSeek.setProgress(dpadSplitGapRatioToSeekProgress(dpadSplitGapRatio));
            }
            if (splitOuterSeek != null) {
                splitOuterSeek.setProgress(dpadSplitOuterReachToSeekProgress(dpadSplitOuterReachRatio));
            }
        }
        updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);

        View colorSection = dialogView.findViewById(R.id.module_color_section);
        if (colorSection != null) {
            GamepadLayoutPresetDocument.GamepadModule cm = findModuleById(stickConfigModuleId);
            if (cm != null) {
                bindGamepadModuleColorSection(colorSection, cm);
            }
        }

        // Stick size seekbar
        android.widget.SeekBar sizeSeekbar = dialogView.findViewById(R.id.stick_size_seekbar);
        sizeSeekbar.setProgress((int) (stickSizeScale * 100));
        sizeSeekbar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                stickSizeScale = progress / 100f;
                if (layoutDoc != null && layoutDoc.modules != null) {
                    GamepadLayoutPresetDocument.GamepadModule m = findModuleById(stickConfigModuleId);
                    if (m != null) {
                        m.scale = stickSizeScale;
                    }
                    syncGamepadViewFromDoc();
                } else if (gamepadView != null) {
                    gamepadView.setStickSizeScale(stickSizeScale);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // Mode change
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateStickConfigSections(group, keySection, splitGapSection, splitOuterSection, arrowMouseSensSection);
            if ("stick_left".equals(stickConfigModuleId) && checkedId == R.id.stick_mode_dpad_split) {
                if (splitGapSeek != null) {
                    splitGapSeek.setProgress(dpadSplitGapRatioToSeekProgress(dpadSplitGapRatio));
                }
                if (splitOuterSeek != null) {
                    splitOuterSeek.setProgress(dpadSplitOuterReachToSeekProgress(dpadSplitOuterReachRatio));
                }
            }
        });

        if (splitGapSeek != null) {
            splitGapSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || !"stick_left".equals(stickConfigModuleId)) {
                        return;
                    }
                    dpadSplitGapRatio = dpadSplitSeekProgressToRatio(progress);
                    reconcileDpadSplitOuterToGap();
                    GamepadLayoutPresetDocument.GamepadModule lm = findModuleById("stick_left");
                    if (lm != null) {
                        lm.dpadSplitGapRatio = dpadSplitGapRatio;
                        lm.dpadSplitOuterReachRatio = dpadSplitOuterReachRatio;
                        syncGamepadViewFromDoc();
                    }
                    if (splitOuterSeek != null) {
                        splitOuterSeek.setProgress(dpadSplitOuterReachToSeekProgress(dpadSplitOuterReachRatio));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (splitOuterSeek != null) {
            splitOuterSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || !"stick_left".equals(stickConfigModuleId)) {
                        return;
                    }
                    dpadSplitOuterReachRatio = dpadSplitOuterSeekToReach(progress);
                    reconcileDpadSplitGapToOuter();
                    GamepadLayoutPresetDocument.GamepadModule lm = findModuleById("stick_left");
                    if (lm != null) {
                        lm.dpadSplitGapRatio = dpadSplitGapRatio;
                        lm.dpadSplitOuterReachRatio = dpadSplitOuterReachRatio;
                        syncGamepadViewFromDoc();
                    }
                    splitOuterSeek.setProgress(dpadSplitOuterReachToSeekProgress(dpadSplitOuterReachRatio));
                    if (splitGapSeek != null) {
                        splitGapSeek.setProgress(dpadSplitGapRatioToSeekProgress(dpadSplitGapRatio));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

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
            } else if (GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)) {
                if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
                    stickUpKey = 82;
                    stickLeftKey = 80;
                    stickDownKey = 81;
                    stickRightKey = 79;
                } else {
                    stickUpKey = 26;
                    stickLeftKey = 4;
                    stickDownKey = 22;
                    stickRightKey = 7;
                }
                arrowStickPointerSensitivity = 1.0f;
                if (arrowMouseSeek != null) {
                    arrowMouseSeek.setProgress(arrowStickMouseGainToSeek(arrowStickPointerSensitivity));
                }
            } else {
                stickUpKey = DEFAULT_STICK_UP;
                stickLeftKey = DEFAULT_STICK_LEFT;
                stickDownKey = DEFAULT_STICK_DOWN;
                stickRightKey = DEFAULT_STICK_RIGHT;
            }
            stickSizeScale = 1.0f;
            dpadSplitGapRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_DEFAULT;
            dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;
            modeGroup.check(R.id.stick_mode_key);
            updateStickConfigSections(modeGroup, keySection, splitGapSection, splitOuterSection, arrowMouseSensSection);
            if (splitGapSeek != null) {
                splitGapSeek.setProgress(dpadSplitGapRatioToSeekProgress(dpadSplitGapRatio));
            }
            if (splitOuterSeek != null) {
                splitOuterSeek.setProgress(dpadSplitOuterReachToSeekProgress(dpadSplitOuterReachRatio));
            }
            updateKeyLabels(keyUp, keyLeft, keyRight, keyDown);
            sizeSeekbar.setProgress(100);
            if (layoutDoc != null && layoutDoc.modules != null) {
                GamepadLayoutPresetDocument.GamepadModule m = findModuleById(stickConfigModuleId);
                if (m != null) {
                    m.scale = 1.0f;
                    m.moduleAccentArgb = null;
                }
                syncGamepadViewFromDoc();
            } else if (gamepadView != null) {
                gamepadView.setStickSizeScale(1.0f);
            }
        });

        // Done
        dialogView.findViewById(R.id.stick_done_btn).setOnClickListener(v -> {
            if (GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)) {
                stickMode = modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key ? MODE_KEY : MODE_ANALOG;
            } else if ("stick_left".equals(stickConfigModuleId)) {
                int checked = modeGroup.getCheckedRadioButtonId();
                if (checked == R.id.stick_mode_dpad_cross) {
                    stickMode = MODE_DPAD_CROSS;
                } else if (checked == R.id.stick_mode_dpad_split) {
                    stickMode = MODE_DPAD_SPLIT;
                } else if (checked == R.id.stick_mode_key) {
                    stickMode = MODE_KEY;
                } else {
                    stickMode = MODE_ANALOG;
                }
            } else {
                stickMode = modeGroup.getCheckedRadioButtonId() == R.id.stick_mode_key ? MODE_KEY : MODE_ANALOG;
            }
            stickDialogCommitted[0] = true;
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
        final float[] buttonCornerNorm = {
                GamepadLayoutPresetConstants.clampButtonCornerRadiusNorm(m.buttonCornerRadiusNorm) };

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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_button_config, null);
        applyGamepadModuleConfigScrollMaxHeight(dialogView);
        builder.setView(dialogView);
        if (GamepadLayoutDocEditor.canRemove(moduleId)) {
            builder.setNeutralButton(R.string.gamepad_menu_remove, (d, which) ->
                    confirmRemoveGamepadModule(moduleId, d));
        }

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(title + " Configuration");

        TextInputEditText capLabelEdit = dialogView.findViewById(R.id.button_cap_label_edit);
        if (capLabelEdit != null) {
            capLabelEdit.setText(m.displayLabel != null ? m.displayLabel : "");
            capLabelEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String raw = s.toString();
                    String clamped = GamepadCapLabels.clampToMaxCodePoints(
                            raw, GamepadCapLabels.MAX_CAP_LABEL_CODE_POINTS);
                    if (!clamped.equals(raw)) {
                        int sel = capLabelEdit.getSelectionStart();
                        s.replace(0, s.length(), clamped);
                        int ns = Math.min(Math.max(sel, 0), clamped.length());
                        capLabelEdit.setSelection(ns);
                    }
                }
            });
        }

        View btnColorSection = dialogView.findViewById(R.id.module_color_section);
        if (btnColorSection != null) {
            bindGamepadModuleColorSection(btnColorSection, m);
        }

        final int[] selectedKey = { currentKey };
        final int[] selectedModifiers = { currentModifiers };

        final MaterialButton keyLabel = dialogView.findViewById(R.id.button_key_label);
        updateButtonLabel(keyLabel, currentKey, currentModifiers);

        final MaterialSwitch mappedKeyLabelSwitch = dialogView.findViewById(R.id.button_show_mapped_key_label_switch);
        if (mappedKeyLabelSwitch != null) {
            boolean showMappedKeyLabel = m.mappedKeyLabelVisible == null
                    || Boolean.TRUE.equals(m.mappedKeyLabelVisible);
            mappedKeyLabelSwitch.setChecked(showMappedKeyLabel);
            mappedKeyLabelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                m.mappedKeyLabelVisible = isChecked ? null : Boolean.FALSE;
                syncGamepadViewFromDoc();
            });
        }

        android.widget.SeekBar sizeSeekbar = dialogView.findViewById(R.id.button_size_seekbar);
        sizeSeekbar.setProgress((int) (buttonSizeScale * 100));
        sizeSeekbar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                buttonSizeScale = progress / 100f;
                if (layoutDoc != null && layoutDoc.modules != null) {
                    m.scale = buttonSizeScale;
                    syncGamepadViewFromDoc();
                } else if (gamepadView != null) {
                    gamepadView.setButtonSizeScale(buttonSizeScale);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        android.widget.SeekBar cornerSeek = dialogView.findViewById(R.id.button_corner_seekbar);
        if (cornerSeek != null) {
            cornerSeek.setProgress(Math.round(buttonCornerNorm[0] * 100f));
            cornerSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    buttonCornerNorm[0] = Math.max(0f, Math.min(1f, progress / 100f));
                    m.buttonCornerRadiusNorm = buttonCornerNorm[0];
                    syncGamepadViewFromDoc();
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                }
            });
        }

        keyLabel.setOnClickListener(v -> showKeyPickerWithModifiers(keyLabel, selectedKey[0], selectedModifiers[0], (key, mod) -> {
            selectedKey[0] = key;
            selectedModifiers[0] = mod;
            updateButtonLabel(keyLabel, key, mod);
        }));

        dialogView.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            selectedKey[0] = defaultKey;
            selectedModifiers[0] = 0;
            updateButtonLabel(keyLabel, defaultKey, 0);
            buttonCornerNorm[0] = GamepadLayoutPresetConstants.BUTTON_CORNER_RADIUS_NORM_DEFAULT;
            m.buttonCornerRadiusNorm = buttonCornerNorm[0];
            m.moduleAccentArgb = null;
            m.displayLabel = null;
            m.mappedKeyLabelVisible = null;
            if (mappedKeyLabelSwitch != null) {
                mappedKeyLabelSwitch.setChecked(true);
            }
            if (capLabelEdit != null) {
                capLabelEdit.setText("");
            }
            if (cornerSeek != null) {
                cornerSeek.setProgress(100);
            }
            syncGamepadViewFromDoc();
        });

        dialogView.findViewById(R.id.btn_done).setOnClickListener(v -> {
            m.hidKey = selectedKey[0];
            m.modifierMask = selectedModifiers[0];
            m.scale = buttonSizeScale;
            m.buttonCornerRadiusNorm = buttonCornerNorm[0];
            if (mappedKeyLabelSwitch != null) {
                m.mappedKeyLabelVisible = mappedKeyLabelSwitch.isChecked() ? null : Boolean.FALSE;
            }
            if (capLabelEdit != null) {
                String capRaw = capLabelEdit.getText().toString().trim();
                m.displayLabel = capRaw.isEmpty()
                        ? null
                        : GamepadCapLabels.clampToMaxCodePoints(
                                capRaw, GamepadCapLabels.MAX_CAP_LABEL_CODE_POINTS);
            }
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
        android.content.Context ctx = requireContext();
        final int colorPrimary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.primary));
        final int colorUnsel = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.gray_600));
        final int colorOnPrimary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnPrimary,
                ContextCompat.getColor(ctx, R.color.white));
        final int colorOnSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(ctx, R.color.text_primary));
        final int colorTextSecondary = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.text_secondary));
        final int colorStrokeAccent = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimaryContainer,
                ContextCompat.getColor(ctx, R.color.theme_accent_orange_container));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        final int[] allKeyCodes = new int[KEY_OPTIONS.length];
        final View[] allKeyCells = new View[KEY_OPTIONS.length];
        final int[] selectedKeyCode = { initialKeyCode };
        final int[] selectedModifiers = { initialModifiers };
        final String[] selectedLabel = { keyCodeToLabel(initialKeyCode) };

        int pad = dp(16);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.gamepad_config_dialog_surface));

        LinearLayout shell = new LinearLayout(requireContext());
        shell.setOrientation(LinearLayout.VERTICAL);

        TextView pickerTitle = new TextView(requireContext());
        pickerTitle.setText(R.string.gamepad_key_picker_title);
        pickerTitle.setTextAppearance(requireContext(), R.style.TextAppearance_KeyMod_GamepadConfig_Title);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(10);
        shell.addView(pickerTitle, titleLp);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 0);

        // Modifier checkboxes at the top
        LinearLayout modifierRow = new LinearLayout(requireContext());
        modifierRow.setOrientation(LinearLayout.HORIZONTAL);
        modifierRow.setGravity(android.view.Gravity.CENTER);
        modifierRow.setPadding(0, 0, 0, dp(12));
        final android.widget.CheckBox[] modChecks = new android.widget.CheckBox[MODIFIER_KEYS.length];
        for (int i = 0; i < MODIFIER_KEYS.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(requireContext());
            cb.setText(MODIFIER_KEYS[i][0]);
            cb.setTextColor(colorTextSecondary);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(colorPrimary));
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
        numLabel.setTextColor(colorTextSecondary);
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
            normalBtn.setTextColor((initialNumIdx == n && !initialIsNumpad) ? colorOnPrimary : colorOnSurface);
            android.graphics.drawable.GradientDrawable normalBg = new android.graphics.drawable.GradientDrawable();
            normalBg.setColor((initialNumIdx == n && !initialIsNumpad) ? colorPrimary : colorUnsel);
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
            numpadBtn.setTextColor((initialNumIdx == n && initialIsNumpad) ? colorOnPrimary : colorOnSurface);
            android.graphics.drawable.GradientDrawable numpadBg = new android.graphics.drawable.GradientDrawable();
            numpadBg.setColor((initialNumIdx == n && initialIsNumpad) ? colorPrimary : colorUnsel);
            numpadBg.setCornerRadius(dp(6));
            numpadBg.setStroke((initialNumIdx == n && initialIsNumpad) ? dp(2) : 0,
                (initialNumIdx == n && initialIsNumpad) ? colorStrokeAccent : 0x00000000);
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
                updateNumButtons(normalNumBtns, numpadNumBtns, num, true, colorPrimary, colorUnsel, colorStrokeAccent,
                        colorOnPrimary, colorOnSurface);
                // Deselect all character key buttons
                for (int k = 0; k < allKeyCells.length; k++) {
                    if (allKeyCells[k] == null) continue;
                    applyKeyPickerCellStyle(allKeyCells[k], false, colorPrimary, colorUnsel, colorOnPrimary, colorOnSurface);
                }
            });
            numpadBtn.setOnClickListener(v -> {
                selectedKeyCode[0] = numpadCode;
                selectedLabel[0] = "Num" + num;
                updateNumButtons(normalNumBtns, numpadNumBtns, num, false, colorPrimary, colorUnsel, colorStrokeAccent,
                        colorOnPrimary, colorOnSurface);
                // Deselect all character key buttons
                for (int k = 0; k < allKeyCells.length; k++) {
                    if (allKeyCells[k] == null) continue;
                    applyKeyPickerCellStyle(allKeyCells[k], false, colorPrimary, colorUnsel, colorOnPrimary, colorOnSurface);
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
                final int keyCode = Integer.parseInt(opt[1]);
                allKeyCodes[idx] = keyCode;
                boolean isSelected = keyCode == initialKeyCode && selectedModifiers[0] == initialModifiers;
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(isSelected ? colorPrimary : colorUnsel);
                bg.setCornerRadius(dp(6));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
                int dp8 = dp(8);
                params.setMargins(dp8, dp8, dp8, dp8);

                View cell;
                int arrowRes = gamepadKeyPickerArrowIconRes(keyCode);
                if (arrowRes != 0) {
                    AppCompatImageButton ib = new AppCompatImageButton(requireContext());
                    android.graphics.drawable.Drawable icon =
                            ContextCompat.getDrawable(requireContext(), arrowRes);
                    if (icon != null) {
                        icon = icon.mutate();
                    }
                    ib.setImageDrawable(icon);
                    ib.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    ib.setBackground(bg);
                    ib.setPadding(dp(6), dp(6), dp(6), dp(6));
                    ib.setContentDescription(gamepadKeyPickerArrowContentDescription(keyCode));
                    ImageViewCompat.setImageTintList(ib,
                            ColorStateList.valueOf(isSelected ? colorOnPrimary : colorOnSurface));
                    ib.setLayoutParams(params);
                    cell = ib;
                } else {
                    Button btn = new Button(requireContext());
                    btn.setText(opt[0]);
                    btn.setTextColor(isSelected ? colorOnPrimary : colorOnSurface);
                    btn.setBackground(bg);
                    btn.setLayoutParams(params);
                    cell = btn;
                }
                allKeyCells[idx] = cell;

                cell.setOnClickListener(v -> {
                    selectedKeyCode[0] = keyCode;
                    selectedLabel[0] = opt[0];
                    for (int k = 0; k < allKeyCells.length; k++) {
                        if (allKeyCells[k] == null) continue;
                        boolean sel = allKeyCodes[k] == keyCode;
                        applyKeyPickerCellStyle(allKeyCells[k], sel, colorPrimary, colorUnsel, colorOnPrimary, colorOnSurface);
                    }
                    // Deselect all number buttons
                    for (int k = 0; k < normalNumBtns.length; k++) {
                        android.graphics.drawable.GradientDrawable nb =
                            (android.graphics.drawable.GradientDrawable) normalNumBtns[k].getBackground();
                        nb.setColor(colorUnsel);
                        nb.setStroke(0, 0x00000000);
                        normalNumBtns[k].setTextColor(colorOnSurface);
                    }
                    for (int k = 0; k < numpadNumBtns.length; k++) {
                        android.graphics.drawable.GradientDrawable nb =
                            (android.graphics.drawable.GradientDrawable) numpadNumBtns[k].getBackground();
                        nb.setColor(colorUnsel);
                        nb.setStroke(0, 0x00000000);
                        numpadNumBtns[k].setTextColor(colorOnSurface);
                    }
                });
                row.addView(cell);
            }
            grid.addView(row);
        }
        container.addView(grid);

        shell.addView(container);

        NestedScrollView scrollView = new NestedScrollView(requireContext());
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(true);
        scrollView.addView(shell);

        View scrollDivider = new View(requireContext());
        scrollDivider.setBackgroundColor(MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOutlineVariant,
                ContextCompat.getColor(ctx, R.color.divider)));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, dp(12), 0, dp(8));

        LinearLayout footer = new LinearLayout(requireContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        MaterialButton cancelBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelBtn.setText(android.R.string.cancel);
        cancelBtn.setAllCaps(false);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        MaterialButton saveBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonStyle);
        saveBtn.setText(R.string.gamepad_key_picker_save);
        saveBtn.setAllCaps(false);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.setMarginStart(dp(8));

        footer.addView(cancelBtn, cancelLp);
        footer.addView(saveBtn, saveLp);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(scrollView, scrollLp);
        root.addView(scrollDivider, divLp);
        root.addView(footer, footerLp);

        builder.setView(root);

        final AlertDialog dialog = builder.create();
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            String displayLabel = selectedLabel[0];
            if (selectedModifiers[0] != 0) {
                displayLabel = keyCodeToLabel(selectedKeyCode[0]);
            }
            listener.onKeySelected(new KeyInfo(selectedKeyCode[0], displayLabel, selectedModifiers[0]));
            dialog.dismiss();
        });

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setOnShowListener(d -> {
            Window win = dialog.getWindow();
            if (win != null) {
                android.view.WindowManager.LayoutParams attrs = win.getAttributes();
                attrs.dimAmount = 0.55f;
                win.setAttributes(attrs);
            }
            scrollView.post(() -> {
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                int reservedFooter = dp(110);
                int maxScrollH = Math.max(dp(200), (int) (screenH * 0.72f) - reservedFooter);
                int wPx = scrollView.getWidth();
                if (wPx <= 0) {
                    wPx = ctx.getResources().getDisplayMetrics().widthPixels - dp(32);
                }
                int wSpec = MeasureSpec.makeMeasureSpec(wPx, MeasureSpec.EXACTLY);
                int hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                shell.measure(wSpec, hSpec);
                if (shell.getMeasuredHeight() > maxScrollH) {
                    ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
                    lp.height = maxScrollH;
                    scrollView.setLayoutParams(lp);
                }
            });
        });
        dialog.show();
    }

    /**
     * Caps module config dialog body height so content scrolls inside the dialog on landscape /
     * small windows. Material custom views do not reliably scroll a plain {@link LinearLayout}.
     */
    private void applyGamepadModuleConfigScrollMaxHeight(@NonNull MaxHeightNestedScrollView scroll) {
        android.util.DisplayMetrics dm = scroll.getResources().getDisplayMetrics();
        int shortest = Math.min(dm.heightPixels, dm.widthPixels);
        int cap = (int) (shortest * 0.62f);
        scroll.setMaxHeightPx(Math.max(dp(280), cap));
    }

    private void applyGamepadModuleConfigScrollMaxHeight(@NonNull View dialogRoot) {
        View scroll = dialogRoot.findViewById(R.id.gamepad_module_config_scroll);
        if (scroll instanceof MaxHeightNestedScrollView) {
            applyGamepadModuleConfigScrollMaxHeight((MaxHeightNestedScrollView) scroll);
        }
    }

    private int dp(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateNumButtons(Button[] normalBtns, Button[] numpadBtns, int num, boolean isNormal,
            int sel, int unsel, int stroke, int onSelText, int onUnselText) {
        for (int i = 0; i < normalBtns.length; i++) {
            boolean isSelected = (i + 1) == num && isNormal;
            android.graphics.drawable.GradientDrawable bg =
                (android.graphics.drawable.GradientDrawable) normalBtns[i].getBackground();
            bg.setColor(isSelected ? sel : unsel);
            bg.setStroke(0, 0x00000000);
            normalBtns[i].setTextColor(isSelected ? onSelText : onUnselText);
        }
        for (int i = 0; i < numpadBtns.length; i++) {
            boolean isSelected = (i + 1) == num && !isNormal;
            android.graphics.drawable.GradientDrawable bg =
                (android.graphics.drawable.GradientDrawable) numpadBtns[i].getBackground();
            bg.setColor(isSelected ? sel : unsel);
            bg.setStroke(isSelected ? dp(2) : 0, isSelected ? stroke : 0x00000000);
            numpadBtns[i].setTextColor(isSelected ? onSelText : onUnselText);
        }
    }

    private static int gamepadKeyPickerArrowIconRes(int keyCode) {
        switch (keyCode) {
            case 82:
                return R.drawable.keyboard_arrow_up_24;
            case 81:
                return R.drawable.keyboard_arrow_down_24;
            case 80:
                return R.drawable.keyboard_arrow_left_24;
            case 79:
                return R.drawable.keyboard_arrow_right_24;
            default:
                return 0;
        }
    }

    private String gamepadKeyPickerArrowContentDescription(int keyCode) {
        switch (keyCode) {
            case 82:
                return getString(R.string.gamepad_key_picker_cd_arrow_up);
            case 81:
                return getString(R.string.gamepad_key_picker_cd_arrow_down);
            case 80:
                return getString(R.string.gamepad_key_picker_cd_arrow_left);
            case 79:
                return getString(R.string.gamepad_key_picker_cd_arrow_right);
            default:
                return "";
        }
    }

    private static void applyKeyPickerCellStyle(View v, boolean selected,
            int colorPrimary, int colorUnsel, int colorOnPrimary, int colorOnSurface) {
        android.graphics.drawable.Drawable d = v.getBackground();
        if (d instanceof android.graphics.drawable.GradientDrawable) {
            ((android.graphics.drawable.GradientDrawable) d)
                    .setColor(selected ? colorPrimary : colorUnsel);
        }
        if (v instanceof Button) {
            ((Button) v).setTextColor(selected ? colorOnPrimary : colorOnSurface);
        } else if (v instanceof AppCompatImageButton) {
            ImageViewCompat.setImageTintList((ImageView) v,
                    ColorStateList.valueOf(selected ? colorOnPrimary : colorOnSurface));
        }
    }

    private static int dpadSplitGapRatioToSeekProgress(float ratio) {
        float min = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MIN;
        float max = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MAX;
        float c = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(ratio);
        return Math.round((c - min) / (max - min) * 100f);
    }

    private static float dpadSplitSeekProgressToRatio(int progress) {
        float min = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MIN;
        float max = GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MAX;
        int p = Math.max(0, Math.min(100, progress));
        return min + (max - min) * (p / 100f);
    }

    private static int dpadSplitOuterReachToSeekProgress(float ratio) {
        float min = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MIN;
        float max = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MAX;
        float c = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(ratio);
        return Math.round((c - min) / (max - min) * 100f);
    }

    private static float dpadSplitOuterSeekToReach(int progress) {
        float min = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MIN;
        float max = GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MAX;
        int p = Math.max(0, Math.min(100, progress));
        return min + (max - min) * (p / 100f);
    }

    /**
     * After widening split spacing: outer reach must be at least {@link GamepadLayoutPresetConstants#minOuterReachRatioForGapRatio}
     * for the fixed segment depth, so bump outer only (pad size stays fixed).
     */
    private void reconcileDpadSplitOuterToGap() {
        dpadSplitGapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(dpadSplitGapRatio);
        dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(
                dpadSplitOuterReachRatio);
        float minO = GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(dpadSplitGapRatio);
        if (dpadSplitOuterReachRatio < minO) {
            dpadSplitOuterReachRatio = minO;
        }
    }

    /**
     * After moving “distance to keys” inward: prefer shrinking the center gap so the chosen outer reach is kept
     * when possible; only then bump outer reach to the geometric minimum.
     */
    private void reconcileDpadSplitGapToOuter() {
        dpadSplitGapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(dpadSplitGapRatio);
        dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(
                dpadSplitOuterReachRatio);
        float minO = GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(dpadSplitGapRatio);
        if (dpadSplitOuterReachRatio >= minO) {
            return;
        }
        dpadSplitGapRatio = GamepadLayoutPresetConstants.largestGapRatioUpToOuterReach(
                dpadSplitOuterReachRatio, dpadSplitGapRatio);
        minO = GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(dpadSplitGapRatio);
        if (dpadSplitOuterReachRatio < minO) {
            dpadSplitOuterReachRatio = minO;
        }
    }

    private void updateStickConfigSections(
            RadioGroup modeGroup, LinearLayout keySection, LinearLayout splitGapSection,
            LinearLayout splitOuterSection, LinearLayout arrowMouseSensSection) {
        int checked = modeGroup.getCheckedRadioButtonId();
        boolean keys = checked == R.id.stick_mode_key || checked == R.id.stick_mode_dpad_cross
                || checked == R.id.stick_mode_dpad_split;
        keySection.setVisibility(keys ? View.VISIBLE : View.GONE);
        boolean mouseLike = checked == R.id.stick_mode_analog;
        boolean showArrowMouse = GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)
                && mouseLike;
        if (arrowMouseSensSection != null) {
            arrowMouseSensSection.setVisibility(showArrowMouse ? View.VISIBLE : View.GONE);
        }
        boolean showSplitGap = "stick_left".equals(stickConfigModuleId)
                && checked == R.id.stick_mode_dpad_split;
        if (splitGapSection != null) {
            splitGapSection.setVisibility(showSplitGap ? View.VISIBLE : View.GONE);
        }
        if (splitOuterSection != null) {
            splitOuterSection.setVisibility(showSplitGap ? View.VISIBLE : View.GONE);
        }
    }

    // ── Persistence ─────────────────────────────────────────────────

    private void saveStickConfig() {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(stickConfigModuleId);
        if (m == null) {
            return;
        }
        if (GamepadLayoutPresetConstants.isArrowStickModuleId(stickConfigModuleId)) {
            m.type = MODE_KEY.equals(stickMode)
                    ? GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY
                    : GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
            if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                m.stickMouseSensitivity = arrowStickPointerSensitivity;
            } else {
                m.stickMouseSensitivity = null;
            }
        } else if ("stick_left".equals(stickConfigModuleId)) {
            if (MODE_DPAD_CROSS.equals(stickMode)) {
                m.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
                m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
            } else if (MODE_DPAD_SPLIT.equals(stickMode)) {
                m.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
                m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT;
            } else if (MODE_KEY.equals(stickMode)) {
                m.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
                m.dpadVariant = null;
            } else {
                m.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
                m.dpadVariant = null;
            }
            if (MODE_DPAD_SPLIT.equals(stickMode)) {
                reconcileDpadSplitGapToOuter();
                reconcileDpadSplitOuterToGap();
                m.dpadSplitGapRatio = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(dpadSplitGapRatio);
                m.dpadSplitOuterReachRatio = GamepadLayoutPresetConstants.clampDpadSplitOuterReachRatio(
                        dpadSplitOuterReachRatio);
            } else {
                m.dpadSplitGapRatio = null;
                m.dpadSplitOuterReachRatio = null;
            }
        } else {
            m.type = MODE_KEY.equals(stickMode)
                    ? GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY
                    : GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        }
        m.stickUpKey = stickUpKey;
        m.stickLeftKey = stickLeftKey;
        m.stickDownKey = stickDownKey;
        m.stickRightKey = stickRightKey;
        m.scale = stickSizeScale;
        if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(stickConfigModuleId)) {
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
                if (mod != null && mod.id != null
                        && GamepadLayoutPresetConstants.isArrowStickModuleId(mod.id)
                        && GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(mod.type)) {
                    int defU = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(mod.id) ? 82 : 26;
                    int defL = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(mod.id) ? 80 : 4;
                    int defD = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(mod.id) ? 81 : 22;
                    int defR = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(mod.id) ? 79 : 7;
                    String p = mod.id + "_";
                    labels.put(p + "up", keyCodeToLabel(intOr(mod.stickUpKey, defU)));
                    labels.put(p + "down", keyCodeToLabel(intOr(mod.stickDownKey, defD)));
                    labels.put(p + "left", keyCodeToLabel(intOr(mod.stickLeftKey, defL)));
                    labels.put(p + "right", keyCodeToLabel(intOr(mod.stickRightKey, defR)));
                }
            }
            for (GamepadLayoutPresetDocument.GamepadModule mod : layoutDoc.modules) {
                if (mod == null || mod.id == null || mod.hidKey == null) {
                    continue;
                }
                if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(mod.type)
                        || GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER.equals(mod.type)
                        || GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER.equals(mod.type)) {
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
            } else if (GamepadLayoutPresetConstants.isArrowStickModuleId(stickId)) {
                GamepadLayoutPresetDocument.GamepadModule em = findModuleById(stickId);
                if (em == null) {
                    return;
                }
                if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(em.type)) {
                    sendArrowStickKeysFromAnalog(stickId, x, y);
                } else if (isConnected) {
                    sendStickPointerMouse(connectionManager, em, x, y);
                }
            } else if ("l".equals(stickId)) {
                if (MODE_DPAD_SPLIT.equals(stickMode)) {
                    return;
                }
                if (MODE_KEY.equals(stickMode) || MODE_DPAD_CROSS.equals(stickMode)) {
                    sendLeftStickKeys(connectionManager, x, y, isConnected);
                } else if (isConnected) {
                    sendLeftStickMouse(connectionManager, x, y);
                }
            }
        }
    }

    private void sendRightStickMouse(ConnectionManager cm, float x, float y) {
        GamepadLayoutPresetDocument.GamepadModule rm = findModuleById("stick_right");
        if (rm != null) {
            sendStickPointerMouse(cm, rm, x, y);
        }
    }

    private void sendStickPointerMouse(ConnectionManager cm, GamepadLayoutPresetDocument.GamepadModule m,
                                        float x, float y) {
        float deadZone = 0.12f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);
        if (xAdj == 0 && yAdj == 0) {
            return;
        }

        float gamma = 1.35f;
        xAdj = Math.copySign((float) Math.pow(Math.abs(xAdj), gamma), xAdj);
        yAdj = Math.copySign((float) Math.pow(Math.abs(yAdj), gamma), yAdj);

        float gain = effectiveArrowStickMouseGain(m);
        xAdj *= mouseSensitivity * gain;
        yAdj *= mouseSensitivity * gain;
        xAdj = Math.max(-1.0f, Math.min(1.0f, xAdj));
        yAdj = Math.max(-1.0f, Math.min(1.0f, yAdj));

        cm.sendMouseMovement((int) (xAdj * 127), (int) (yAdj * 127), 0);
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

    private void sendArrowStickKeysFromAnalog(String moduleId, float x, float y) {
        GamepadLayoutPresetDocument.GamepadModule m = findModuleById(moduleId);
        if (m == null || !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)) {
            return;
        }
        float deadZone = 0.2f;
        float xAdj = applyDeadZone(x, deadZone);
        float yAdj = applyDeadZone(y, deadZone);

        boolean wantUp = yAdj < -0.3f;
        boolean wantDown = yAdj > 0.3f;
        boolean wantLeft = xAdj < -0.3f;
        boolean wantRight = xAdj > 0.3f;

        boolean[] st = arrowVirtDirs(moduleId);
        boolean prevU = st[0];
        boolean prevL = st[1];
        boolean prevD = st[2];
        boolean prevR = st[3];

        boolean changed = false;
        if (wantUp != st[0]) {
            st[0] = wantUp;
            changed = true;
        }
        if (wantLeft != st[1]) {
            st[1] = wantLeft;
            changed = true;
        }
        if (wantDown != st[2]) {
            st[2] = wantDown;
            changed = true;
        }
        if (wantRight != st[3]) {
            st[3] = wantRight;
            changed = true;
        }
        boolean newlyEngaged = (st[0] && !prevU) || (st[1] && !prevL)
                || (st[2] && !prevD) || (st[3] && !prevR);
        if (newlyEngaged) {
            vibrateGamepadTick();
        }
        if (changed) {
            sendCombinedKeyReport();
        }

        java.util.Set<String> activeDirs = new java.util.HashSet<>();
        if (st[0]) {
            activeDirs.add("up");
        }
        if (st[2]) {
            activeDirs.add("down");
        }
        if (st[1]) {
            activeDirs.add("left");
        }
        if (st[3]) {
            activeDirs.add("right");
        }
        if (gamepadView != null) {
            if (activeDirs.isEmpty()) {
                gamepadView.clearStickDirections(moduleId);
            } else {
                gamepadView.setActiveStickDirections(moduleId, activeDirs);
            }
        }
    }

    private boolean[] arrowVirtDirs(String moduleId) {
        return arrowStickVirtKeys.computeIfAbsent(moduleId, k -> new boolean[4]);
    }

    private float effectiveArrowStickMouseGain(@Nullable GamepadLayoutPresetDocument.GamepadModule m) {
        if (m != null && m.stickMouseSensitivity != null) {
            float g = m.stickMouseSensitivity;
            if (!Float.isNaN(g) && !Float.isInfinite(g) && g >= 0.25f && g <= 4.0f) {
                return g;
            }
        }
        if (layoutDoc != null && layoutDoc.layout != null && layoutDoc.layout.rightStickMouseGain != null) {
            float g = layoutDoc.layout.rightStickMouseGain;
            if (!Float.isNaN(g) && g >= 0.25f && g <= 4.0f) {
                return g;
            }
        }
        return (rightStickMouseGain > 0f && rightStickMouseGain <= 4.0f) ? rightStickMouseGain : 1.0f;
    }

    private static int arrowStickMouseGainToSeek(float gain) {
        float g = Math.max(0.25f, Math.min(4.0f, gain));
        return Math.round((g - 0.25f) / 3.75f * 150f);
    }

    private static float arrowStickSeekToMouseGain(int progress) {
        float p = Math.max(0, Math.min(150, progress)) / 150f;
        return 0.25f + p * 3.75f;
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
        boolean anyArrowVirt = false;
        for (boolean[] st : arrowStickVirtKeys.values()) {
            if (st != null && (st[0] || st[1] || st[2] || st[3])) {
                anyArrowVirt = true;
                break;
            }
        }
        boolean anyKey = keyUpPressed || keyLeftPressed || keyDownPressed || keyRightPressed
                || keyRUpPressed || keyRLeftPressed || keyRDownPressed || keyRRightPressed
                || anyArrowVirt
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
        buttonAPressed = false;
        buttonBPressed = false;
        faceButtonPressed.clear();
        arrowStickVirtKeys.clear();
        if (gamepadView != null) {
            gamepadView.clearStickDirections("l");
            gamepadView.clearStickDirections("r");
            if (layoutDoc != null && layoutDoc.modules != null) {
                for (GamepadLayoutPresetDocument.GamepadModule m : layoutDoc.modules) {
                    if (m != null && m.id != null
                            && GamepadLayoutPresetConstants.isArrowStickModuleId(m.id)) {
                        gamepadView.clearStickDirections(m.id);
                    }
                }
            } else {
                gamepadView.clearStickDirections(GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID);
            }
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

    /** Edit-mode empty-area long-press: background actions plus add-module entry. */
    private void showEditBackgroundAndModulesMenu() {
        String[] options = new String[]{
                getString(R.string.gamepad_bg_pick_gallery),
                getString(R.string.gamepad_bg_remove),
                getString(R.string.gamepad_presets_add_module),
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
                        showAddModuleMenu();
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
