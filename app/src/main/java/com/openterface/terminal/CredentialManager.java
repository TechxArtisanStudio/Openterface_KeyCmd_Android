package com.openterface.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages SSH credential profiles with encrypted storage via EncryptedSharedPreferences.
 */
public class CredentialManager {

    private static final String PREFS_NAME = "credentials_prefs";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_ID = "active_profile_id";
    public static final String DEFAULT_KEYCMD_HOST = "192.168.12.1";

    private SharedPreferences prefs;
    private final Gson gson;

    public CredentialManager(Context context) {
        this.gson = new Gson();
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Fallback to plain SharedPreferences if encryption fails (should not happen on API 26+)
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * Add a new credential profile. If this is the first profile, it becomes active automatically.
     */
    public void addProfile(CredentialProfile profile) {
        List<CredentialProfile> profiles = getAllProfiles();
        if (profiles.isEmpty()) {
            profile.setActive(true);
            prefs.edit().putString(KEY_ACTIVE_ID, profile.getId()).apply();
        }
        profiles.add(profile);
        saveProfiles(profiles);
    }

    /**
     * Update an existing credential profile.
     */
    public void updateProfile(CredentialProfile profile) {
        List<CredentialProfile> profiles = getAllProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profile.getId())) {
                profile.setUpdatedAt(System.currentTimeMillis());
                profiles.set(i, profile);
                saveProfiles(profiles);
                return;
            }
        }
        // Profile not found — add it
        addProfile(profile);
    }

    /**
     * Delete a credential profile by ID. If the deleted profile was active, clears the active ID
     * or promotes another profile to active.
     */
    public void deleteProfile(String id) {
        List<CredentialProfile> profiles = getAllProfiles();
        boolean wasActive = false;
        for (Iterator<CredentialProfile> it = profiles.iterator(); it.hasNext(); ) {
            CredentialProfile p = it.next();
            if (p.getId().equals(id)) {
                if (p.isActive()) wasActive = true;
                it.remove();
                break;
            }
        }
        if (wasActive) {
            if (!profiles.isEmpty()) {
                profiles.get(0).setActive(true);
                prefs.edit().putString(KEY_ACTIVE_ID, profiles.get(0).getId()).apply();
            } else {
                prefs.edit().remove(KEY_ACTIVE_ID).apply();
            }
        }
        saveProfiles(profiles);
    }

    /**
     * Get a single credential profile by ID, or null if not found.
     */
    @Nullable
    public CredentialProfile getProfile(String id) {
        for (CredentialProfile p : getAllProfiles()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Get all credential profiles.
     */
    public List<CredentialProfile> getAllProfiles() {
        String json = prefs.getString(KEY_PROFILES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            return gson.fromJson(json, new TypeToken<List<CredentialProfile>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Seed a default KeyCmd hardware SSH profile for first-run UX. The default IP is provisional
     * until firmware/networking confirms the assigned address.
     */
    public void ensureDefaultKeyCmdProfile() {
        if (!getAllProfiles().isEmpty()) {
            return;
        }
        CredentialProfile profile = new CredentialProfile();
        profile.setName("KeyCmd default");
        profile.setHost(DEFAULT_KEYCMD_HOST);
        profile.setPort(22);
        profile.setUsername("root");
        profile.setPassword("");
        profile.setTargetOs("linux");
        profile.setAuthMethod("password");
        profile.setPrivateKey("");
        profile.setNotes("Default KeyCmd hardware SSH endpoint. Confirm IP with firmware team.");
        addProfile(profile);
    }

    /**
     * Get the active credential profile, or null if none is set.
     */
    @Nullable
    public CredentialProfile getActiveProfile() {
        String activeId = prefs.getString(KEY_ACTIVE_ID, null);
        if (activeId == null) {
            List<CredentialProfile> profiles = getAllProfiles();
            for (CredentialProfile p : profiles) {
                if (p.isActive()) {
                    prefs.edit().putString(KEY_ACTIVE_ID, p.getId()).apply();
                    return p;
                }
            }
            return null;
        }
        return getProfile(activeId);
    }

    /**
     * Set the active profile ID. Updates the active flag on all profiles.
     */
    public void setActiveProfileId(String id) {
        List<CredentialProfile> profiles = getAllProfiles();
        for (CredentialProfile p : profiles) {
            p.setActive(p.getId().equals(id));
        }
        saveProfiles(profiles);
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply();
    }

    /**
     * Get all unique tags across all profiles, sorted alphabetically.
     */
    public List<String> getAllTags() {
        Set<String> tagSet = new LinkedHashSet<>();
        for (CredentialProfile p : getAllProfiles()) {
            tagSet.addAll(p.getTags());
        }
        List<String> result = new ArrayList<>(tagSet);
        Collections.sort(result);
        return result;
    }

    /**
     * Remove a tag from all profiles. Saves changes immediately.
     */
    public void removeTagFromAllProfiles(String tag) {
        List<CredentialProfile> profiles = getAllProfiles();
        boolean changed = false;
        for (CredentialProfile p : profiles) {
            if (p.getTags().remove(tag)) {
                changed = true;
            }
        }
        if (changed) {
            saveProfiles(profiles);
        }
    }

    /**
     * Migrate from legacy TerminalPrefs plaintext credentials. If the old prefs contain a
     * non-empty host or password, create an encrypted profile and clear the plaintext values.
     */
    public void migrateFromTerminalPrefs(Context context) {
        String migratedKey = "credentials_migrated";
        SharedPreferences check = context.getSharedPreferences(PREFS_NAME + "_migration", Context.MODE_PRIVATE);
        if (check.getBoolean(migratedKey, false)) {
            return;
        }

        TerminalPrefs legacyPrefs = new TerminalPrefs(context);
        String host = legacyPrefs.getLastHost();
        String user = legacyPrefs.getLastUsername();
        String pass = legacyPrefs.getLastPassword();

        // Only migrate if there's meaningful data
        if ((host != null && !host.isEmpty()
                    && !"192.168.11.1".equals(host)
                    && !DEFAULT_KEYCMD_HOST.equals(host))
                || (pass != null && !pass.isEmpty())) {
            CredentialProfile migrated = new CredentialProfile();
            migrated.setName("Default (migrated)");
            migrated.setHost(host != null ? host : "");
            migrated.setPort(22);
            migrated.setUsername(user != null ? user : "");
            migrated.setPassword(pass != null ? pass : "");
            addProfile(migrated);

            // Clear plaintext credentials
            context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("last_password")
                    .apply();
        }

        check.edit().putBoolean(migratedKey, true).apply();
    }

    private void saveProfiles(List<CredentialProfile> profiles) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply();
    }
}
