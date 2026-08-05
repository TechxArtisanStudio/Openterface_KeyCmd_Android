package com.openterface.keymod.agent.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the list of AI providers with persistence.
 * Handles adding, removing, updating providers and tracks the selected provider.
 */
public class AIProviderManager {
    private static final String TAG = "AIProviderManager";
    private static final String PREFS_NAME = "ai_provider_prefs";
    private static final String KEY_PROVIDERS_JSON = "providers_json";
    private static final String KEY_SELECTED_PROVIDER_ID = "selected_provider_id";

    private static volatile AIProviderManager instance;

    private final SharedPreferences prefs;
    private final Gson gson;
    // NOTE: All access to providers must happen on the main (UI) thread.
    // Currently safe because all callers are UI-driven (Fragment/Activity).
    private List<AIProvider> providers;
    private String selectedProviderId;

    private AIProviderManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        providers = new ArrayList<>();
        loadProviders();
    }

    /**
     * Returns the singleton instance, creating it if necessary.
     */
    public static AIProviderManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AIProviderManager.class) {
                if (instance == null) {
                    instance = new AIProviderManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Loads providers from SharedPreferences, or initializes with defaults if empty.
     */
    private void loadProviders() {
        String json = prefs.getString(KEY_PROVIDERS_JSON, null);
        if (json != null) {
            try {
                Type type = new TypeToken<ArrayList<AIProvider>>() {}.getType();
                providers = gson.fromJson(json, type);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse providers JSON", e);
                providers = new ArrayList<>();
            }
        }

        // Initialize with defaults if empty
        if (providers.isEmpty()) {
            initializeDefaultProviders();
        }

        // Load selected provider ID
        selectedProviderId = prefs.getString(KEY_SELECTED_PROVIDER_ID, null);
        if (selectedProviderId == null || getProviderById(selectedProviderId) == null) {
            // Select first provider if none selected or selected one doesn't exist
            if (!providers.isEmpty()) {
                selectedProviderId = providers.get(0).id;
            }
        }
    }

    /**
     * Initializes the provider list with default presets.
     */
    private void initializeDefaultProviders() {
        providers = AIProvider.getDefaultProviders();
        saveProviders();
    }

    /**
     * Saves the current provider list to SharedPreferences.
     */
    private void saveProviders() {
        String json = gson.toJson(providers);
        prefs.edit().putString(KEY_PROVIDERS_JSON, json).apply();
    }

    /**
     * Returns all providers.
     */
    public List<AIProvider> getProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Returns the number of providers.
     */
    public int getProviderCount() {
        return providers.size();
    }

    /**
     * Returns the provider at the given index.
     */
    public AIProvider getProvider(int index) {
        if (index < 0 || index >= providers.size()) {
            return null;
        }
        return providers.get(index);
    }

    /**
     * Returns the provider with the given ID, or null if not found.
     */
    public AIProvider getProviderById(String id) {
        if (id == null) return null;
        for (AIProvider provider : providers) {
            if (id.equals(provider.id)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Returns the index of the provider with the given ID, or -1 if not found.
     */
    public int getProviderIndexById(String id) {
        if (id == null) return -1;
        for (int i = 0; i < providers.size(); i++) {
            if (id.equals(providers.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the currently selected provider.
     */
    public AIProvider getSelectedProvider() {
        return getProviderById(selectedProviderId);
    }

    /**
     * Returns the index of the currently selected provider.
     */
    public int getSelectedProviderIndex() {
        return getProviderIndexById(selectedProviderId);
    }

    /**
     * Sets the selected provider by ID.
     */
    public void setSelectedProvider(String providerId) {
        if (getProviderById(providerId) != null) {
            selectedProviderId = providerId;
            prefs.edit().putString(KEY_SELECTED_PROVIDER_ID, providerId).apply();
        }
    }

    /**
     * Sets the selected provider by index.
     */
    public void setSelectedProviderIndex(int index) {
        if (index >= 0 && index < providers.size()) {
            selectedProviderId = providers.get(index).id;
            prefs.edit().putString(KEY_SELECTED_PROVIDER_ID, selectedProviderId).apply();
        }
    }

    /**
     * Adds a new provider to the list.
     * Allows empty providers (user fills in details later).
     */
    public void addProvider(AIProvider provider) {
        if (provider != null) {
            providers.add(provider);
            saveProviders();
            // Auto-select the new provider
            setSelectedProvider(provider.id);
            Log.d(TAG, "Added provider: " + (provider.name != null ? provider.name : "unnamed"));
        }
    }

    /**
     * Removes a provider from the list.
     * Returns true if successful, false if this is the last provider (cannot be removed).
     */
    public boolean removeProvider(String providerId) {
        if (providers.size() <= 1) {
            Log.w(TAG, "Cannot remove the last provider");
            return false;
        }

        int index = getProviderIndexById(providerId);
        if (index >= 0) {
            AIProvider removed = providers.remove(index);
            saveProviders();

            // If we removed the selected provider, select the first one
            if (providerId.equals(selectedProviderId)) {
                setSelectedProviderIndex(0);
            }

            Log.d(TAG, "Removed provider: " + removed.name);
            return true;
        }
        return false;
    }

    /**
     * Removes a provider at the given index.
     * Returns true if successful, false if this is the last provider.
     */
    public boolean removeProvider(int index) {
        if (index < 0 || index >= providers.size()) {
            return false;
        }
        return removeProvider(providers.get(index).id);
    }

    /**
     * Updates an existing provider.
     */
    public void updateProvider(AIProvider provider) {
        if (provider == null || provider.id == null) return;

        for (int i = 0; i < providers.size(); i++) {
            if (provider.id.equals(providers.get(i).id)) {
                providers.set(i, provider);
                saveProviders();
                Log.d(TAG, "Updated provider: " + provider.name);
                return;
            }
        }
    }

    /**
     * Resets the provider list to defaults.
     */
    public void resetToDefaults() {
        providers = AIProvider.getDefaultProviders();
        selectedProviderId = providers.isEmpty() ? null : providers.get(0).id;
        saveProviders();
        prefs.edit().putString(KEY_SELECTED_PROVIDER_ID, selectedProviderId).apply();
        Log.d(TAG, "Reset providers to defaults");
    }

    /**
     * Checks if a provider at the given index has an API key configured.
     */
    public boolean hasApiKey(int index, AIKeyManager keyManager) {
        AIProvider provider = getProvider(index);
        if (provider == null) return false;
        return keyManager.hasKey(provider.id);
    }

    /**
     * Creates a new empty provider (for "Add New Provider" flow).
     */
    public AIProvider createNewEmptyProvider() {
        return new AIProvider("New Provider", "", "", false);
    }
}
