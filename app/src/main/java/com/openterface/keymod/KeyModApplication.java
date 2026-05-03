package com.openterface.keymod;

import android.app.Application;

/**
 * Applies persisted app locale before any activity is created.
 */
public class KeyModApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLocaleManager.applyPersistedLocales(this);
    }
}
