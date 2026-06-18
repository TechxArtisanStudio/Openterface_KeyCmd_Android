package com.openterface.keymod.fragments;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.android.material.button.MaterialButton;
import com.openterface.keymod.AppLocaleManager;
import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;
import com.openterface.keymod.UsbModeManager;
import com.openterface.keymod.hid.Ch9329InboundParser;

/**
 * General Settings Fragment
 * - Connection preferences
 * - Display options
 * - Device / USB firmware mode
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

    // Device / USB mode UI
    private LinearLayout deviceStatusContainer;
    private TextView deviceCurrentModeText;
    private TextView deviceStoredModeText;
    private TextView deviceSupportedModesText;
    private TextView deviceDisconnectedText;
    private TextView deviceSelectModeLabel;
    private RadioGroup deviceModeRadioGroup;
    private MaterialButton deviceRefreshButton;
    private MaterialButton deviceApplyButton;
    private MaterialButton deviceClearButton;

    private SharedPreferences prefs;

    // USB mode state
    private UsbModeManager.UsbModeStatus currentStatus;
    private int selectedMode = UsbModeManager.MODE_HID_UAC_ACM;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Connection state tracking
    private boolean deviceConnected = false;

    // Connection state listener to track USB/BLE connectivity
    private final ConnectionManager.ConnectionStateListener connectionStateListener =
            new ConnectionManager.ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionManager.ConnectionType type,
                                                     ConnectionManager.ConnectionState state) {
                    boolean connected = (state == ConnectionManager.ConnectionState.CONNECTED);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> setDeviceConnected(connected));
                    }
                }

                @Override
                public void onConnectionError(String error) {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> setDeviceConnected(false));
                    }
                }
            };

    // USB mode response listener
    private final Ch9329InboundParser.UsbModeResponseListener usbModeResponseListener = frame -> {
        UsbModeManager.UsbModeStatus status = UsbModeManager.parseStatusResponse(frame);
        if (status != null && isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(() -> onUsbModeStatusReceived(status));
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_general, container, false);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        initializeViews(view);
        loadSettings();
        setupListeners();
        setupDeviceViews(view);

        // Register for connection state changes — only when hosted by MainActivity
        if (isMainActivity() && getConnectionManager() != null) {
            getConnectionManager().addConnectionStateListener(connectionStateListener);
            // Set initial connection state
            setDeviceConnected(getConnectionManager().getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-register connection listener and USB mode listener in case activity was recreated
        if (isMainActivity() && getConnectionManager() != null) {
            getConnectionManager().addConnectionStateListener(connectionStateListener);
            setDeviceConnected(getConnectionManager().getCurrentConnectionState() == ConnectionManager.ConnectionState.CONNECTED);
        }
        registerUsbModeResponseListener();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isMainActivity() && getConnectionManager() != null) {
            getConnectionManager().removeConnectionStateListener(connectionStateListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isMainActivity() && getConnectionManager() != null) {
            getConnectionManager().removeConnectionStateListener(connectionStateListener);
        }
    }

    /** Helper: return MainActivity only when host is actually MainActivity, otherwise null. */
    @Nullable
    private MainActivity getMainActivity() {
        if (getActivity() instanceof MainActivity) {
            return (MainActivity) getActivity();
        }
        return null;
    }

    /** Helper: true when host is MainActivity. */
    private boolean isMainActivity() {
        return getActivity() instanceof MainActivity;
    }

    /** Helper: get ConnectionManager from host MainActivity, or null. */
    @Nullable
    private ConnectionManager getConnectionManager() {
        MainActivity activity = getMainActivity();
        if (activity != null) {
            return activity.getConnectionManager();
        }
        return null;
    }

    /** Helper: get BluetoothService from host MainActivity, or null. */
    @Nullable
    private BluetoothService getBluetoothService() {
        MainActivity activity = getMainActivity();
        if (activity != null) {
            return activity.getBluetoothService();
        }
        return null;
    }

    private void setDeviceConnected(boolean connected) {
        this.deviceConnected = connected;
        updateDeviceUiForConnectionState();
    }

    private void updateDeviceUiForConnectionState() {
        if (deviceStatusContainer == null) return;

        boolean connected = deviceConnected;
        deviceStatusContainer.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceDisconnectedText.setVisibility(connected ? View.GONE : View.VISIBLE);
        deviceSelectModeLabel.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceModeRadioGroup.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceRefreshButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceApplyButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceClearButton.setVisibility(connected ? View.VISIBLE : View.GONE);

        deviceModeRadioGroup.setEnabled(connected);
        deviceRefreshButton.setEnabled(connected);
        deviceApplyButton.setEnabled(connected);
        deviceClearButton.setEnabled(connected);
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

    // ---------- Device / USB Mode ----------

    private void setupDeviceViews(View view) {
        deviceStatusContainer = view.findViewById(R.id.device_status_container);
        deviceCurrentModeText = view.findViewById(R.id.device_current_mode_text);
        deviceStoredModeText = view.findViewById(R.id.device_stored_mode_text);
        deviceSupportedModesText = view.findViewById(R.id.device_supported_modes_text);
        deviceDisconnectedText = view.findViewById(R.id.device_disconnected_text);
        deviceSelectModeLabel = view.findViewById(R.id.device_select_mode_label);
        deviceModeRadioGroup = view.findViewById(R.id.device_mode_radio_group);
        deviceRefreshButton = view.findViewById(R.id.device_refresh_button);
        deviceApplyButton = view.findViewById(R.id.device_apply_button);
        deviceClearButton = view.findViewById(R.id.device_clear_button);

        // Default selection: Mode 2 (firmware default)
        deviceModeRadioGroup.check(R.id.device_mode_2);
        deviceModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) return;
            selectedMode = radioIdToMode(checkedId);
        });

        deviceRefreshButton.setOnClickListener(v -> sendReadStatus());

        deviceApplyButton.setOnClickListener(v -> showApplyRebootDialog());

        deviceClearButton.setOnClickListener(v -> showClearConfigDialog());
    }

    private void sendReadStatus() {
        MainActivity activity = getMainActivity();
        if (activity == null) return;
        ConnectionManager cm = activity.getConnectionManager();
        if (cm == null || !cm.isConnected()) {
            com.google.android.material.snackbar.Snackbar.make(requireView(),
                    R.string.settings_device_error_not_connected,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            return;
        }

        deviceCurrentModeText.setText(R.string.settings_device_status_refreshing);

        // Register the response listener before sending
        registerUsbModeResponseListener();

        UsbModeManager.readStatus(cm.getUsbPort(), activity.getBluetoothService());

        // Timeout: if no response after 3s, show error
        mainHandler.postDelayed(() -> {
            if (currentStatus == null && isAdded()) {
                deviceCurrentModeText.setText(R.string.settings_device_status_unknown);
                com.google.android.material.snackbar.Snackbar.make(requireView(),
                        R.string.settings_device_error_read_failed,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            }
        }, 3000);
    }

    private void registerUsbModeResponseListener() {
        BluetoothService bt = getBluetoothService();
        if (bt != null) {
            bt.setUsbModeResponseListener(usbModeResponseListener);
        }
    }

    private void onUsbModeStatusReceived(UsbModeManager.UsbModeStatus status) {
        this.currentStatus = status;
        if (deviceCurrentModeText != null) {
            deviceCurrentModeText.setText(
                    getString(R.string.settings_device_current_mode, UsbModeManager.modeName(status.activeMode)));
        }
        if (deviceStoredModeText != null) {
            deviceStoredModeText.setText(
                    getString(R.string.settings_device_stored_mode, UsbModeManager.modeName(status.storedMode)));
        }
        if (deviceSupportedModesText != null) {
            deviceSupportedModesText.setText(
                    getString(R.string.settings_device_supported_modes, status.capabilityMaskString()));
        }

        // Update radio group to match stored mode
        if (status.storedMode >= 1 && status.storedMode <= 4) {
            selectedMode = status.storedMode;
            int radioId = modeToRadioId(status.storedMode);
            if (deviceModeRadioGroup != null) {
                isLoadingSettings = true;
                deviceModeRadioGroup.check(radioId);
                isLoadingSettings = false;
            }
        }

        // Disable radio buttons for unsupported modes
        if (deviceModeRadioGroup != null) {
            setModeRadioEnabled(R.id.device_mode_1, status.isModeSupported(1));
            setModeRadioEnabled(R.id.device_mode_2, status.isModeSupported(2));
            setModeRadioEnabled(R.id.device_mode_3, status.isModeSupported(3));
            setModeRadioEnabled(R.id.device_mode_4, status.isModeSupported(4));
        }
    }

    private void setModeRadioEnabled(int radioId, boolean enabled) {
        View radio = requireView().findViewById(radioId);
        if (radio != null) {
            radio.setEnabled(enabled);
            radio.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    private void showApplyRebootDialog() {
        if (!deviceConnected) {
            com.google.android.material.snackbar.Snackbar.make(requireView(),
                    R.string.settings_device_error_not_connected,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            return;
        }

        String targetName = UsbModeManager.modeName(selectedMode);

        // If the selected mode equals the current stored mode, offer just a reboot
        if (currentStatus != null && currentStatus.storedMode == selectedMode) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_device_reboot_confirm_title)
                    .setMessage(R.string.settings_device_reboot_only)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> sendReboot())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        String message = getString(R.string.settings_device_reboot_confirm_message, targetName);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_device_reboot_confirm_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> sendWriteAndReboot())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void sendWriteAndReboot() {
        MainActivity activity = getMainActivity();
        if (activity == null) return;
        ConnectionManager cm = activity.getConnectionManager();
        if (cm == null || !cm.isConnected()) return;

        UsbModeManager.writeMode(cm.getUsbPort(), activity.getBluetoothService(), selectedMode);

        com.google.android.material.snackbar.Snackbar.make(requireView(),
                R.string.settings_device_mode_applied,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();

        // Wait a bit then send reboot
        mainHandler.postDelayed(() -> sendReboot(), 500);
    }

    private void sendReboot() {
        MainActivity activity = getMainActivity();
        if (activity == null) return;
        ConnectionManager cm = activity.getConnectionManager();
        if (cm == null || !cm.isConnected()) return;

        UsbModeManager.reboot(cm.getUsbPort(), activity.getBluetoothService());

        com.google.android.material.snackbar.Snackbar.make(requireView(),
                R.string.settings_device_reboot_sent,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();

        // Reset status so stale data isn't shown
        currentStatus = null;
    }

    private void showClearConfigDialog() {
        if (!deviceConnected) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_device_clear_confirm_title)
                .setMessage(R.string.settings_device_clear_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> sendClearConfig())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void sendClearConfig() {
        MainActivity activity = getMainActivity();
        if (activity == null) return;
        ConnectionManager cm = activity.getConnectionManager();
        if (cm == null || !cm.isConnected()) return;

        UsbModeManager.clearMode(cm.getUsbPort(), activity.getBluetoothService());

        com.google.android.material.snackbar.Snackbar.make(requireView(),
                R.string.settings_device_config_cleared,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();

        currentStatus = null;
    }

    private static int radioIdToMode(int radioId) {
        if (radioId == R.id.device_mode_1) return UsbModeManager.MODE_HID_ACM;
        if (radioId == R.id.device_mode_2) return UsbModeManager.MODE_HID_UAC_ACM;
        if (radioId == R.id.device_mode_3) return UsbModeManager.MODE_HID_ACM_BRIDGE;
        if (radioId == R.id.device_mode_4) return UsbModeManager.MODE_HID_ECM_BRIDGE;
        return UsbModeManager.MODE_HID_UAC_ACM;
    }

    private static int modeToRadioId(int mode) {
        if (mode == UsbModeManager.MODE_HID_ACM) return R.id.device_mode_1;
        if (mode == UsbModeManager.MODE_HID_UAC_ACM) return R.id.device_mode_2;
        if (mode == UsbModeManager.MODE_HID_ACM_BRIDGE) return R.id.device_mode_3;
        if (mode == UsbModeManager.MODE_HID_ECM_BRIDGE) return R.id.device_mode_4;
        return R.id.device_mode_2;
    }

    // ---------- Theme helpers ----------

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
