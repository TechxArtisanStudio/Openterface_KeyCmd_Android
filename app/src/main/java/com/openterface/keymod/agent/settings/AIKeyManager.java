package com.openterface.keymod.agent.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manages encrypted storage of API keys.
 * Uses EncryptedSharedPreferences with AES256-GCM for secure storage.
 */
public class AIKeyManager {
    private static final String TAG = "AIKeyManager";
    private static final String PREFS_NAME = "ai_keys_prefs";
    private static final String KEY_PREFIX = "key_";

    private static volatile AIKeyManager instance;

    private final SharedPreferences prefs;

    private AIKeyManager(Context context) {
        SharedPreferences tempPrefs = null;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            tempPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize encrypted storage", e);
            throw new RuntimeException("Secure storage unavailable", e);
        }
        prefs = tempPrefs;
    }

    /**
     * Returns the singleton instance, creating it if necessary.
     */
    public static AIKeyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AIKeyManager.class) {
                if (instance == null) {
                    instance = new AIKeyManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Saves an API key for the given provider.
     */
    public void saveKey(String providerId, String key) {
        if (providerId == null) {
            return;
        }
        String keyName = KEY_PREFIX + providerId;
        if (key == null || key.isEmpty()) {
            prefs.edit().remove(keyName).apply();
            Log.d(TAG, "Cleared API key for provider: " + providerId);
        } else {
            prefs.edit().putString(keyName, key).apply();
            Log.d(TAG, "Saved API key for provider: " + providerId);
        }
    }

    /**
     * Returns the API key for the given provider, or null if not set.
     */
    public String getKey(String providerId) {
        if (providerId == null) {
            return null;
        }
        return prefs.getString(KEY_PREFIX + providerId, null);
    }

    /**
     * Deletes the API key for the given provider.
     */
    public void deleteKey(String providerId) {
        saveKey(providerId, null);
    }

    /**
     * Checks whether an API key is configured for the given provider.
     */
    public boolean hasKey(String providerId) {
        String key = getKey(providerId);
        return key != null && !key.isEmpty();
    }
}
