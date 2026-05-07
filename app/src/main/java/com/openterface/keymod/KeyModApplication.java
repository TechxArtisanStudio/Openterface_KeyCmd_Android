package com.openterface.keymod;

import android.app.Application;

import com.openterface.keymod.gamepad.GamepadLayoutPresetRepository;

/**
 * Applies persisted app locale before any activity is created.
 */
public class KeyModApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLocaleManager.applyPersistedLocales(this);
        GamepadLayoutPresetRepository presetRepository = new GamepadLayoutPresetRepository(this);
        presetRepository.ensureMigratedFromLegacy();
        presetRepository.syncBundledPresetsFromAssets();
    }
}
