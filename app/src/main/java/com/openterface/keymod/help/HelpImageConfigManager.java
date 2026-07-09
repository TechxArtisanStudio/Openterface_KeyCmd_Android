package com.openterface.keymod.help;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads, caches, and serves the remote help-image config.
 * <p>
 * Config is fetched from a fixed CDN URL on first use, then cached locally.
 * The cached config is returned immediately (fast startup), while a background
 * refresh checks for newer versions.
 * <p>
 * Thread-safe: all public methods work from any thread. Callbacks fire on the
 * main thread.
 */
public final class HelpImageConfigManager {

    private static final String TAG = "HelpImageConfigMgr";

    /** Where the config JSON is downloaded from. */
    private static final String CONFIG_URL =
            "https://cdn.openterface.com/help/config.json";

    /** Local cache file name. */
    private static final String CONFIG_FILE_NAME = "help_config.json";

    /** SharedPreferences key for the last-known config version. */
    private static final String PREF_KEY_VERSION = "help_config_version";
    private static final String PREFS_NAME = "HelpImageConfigPrefs";

    /** In-memory cache of the latest config (loaded from disk or freshly downloaded). */
    @Nullable
    private volatile HelpImageConfig cachedConfig;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static HelpImageConfigManager instance;

    private HelpImageConfigManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public static synchronized HelpImageConfigManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new HelpImageConfigManager(context);
        }
        return instance;
    }

    // ---- Public API ----

    /**
     * Load test config from assets (local-only, no network required).
     */
    @Nullable
    public HelpImageConfig loadLocalTestConfig() {
        try (InputStream is = appContext.getAssets().open("help_config.json")) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return HelpImageConfig.fromJson(sb.toString());
        } catch (IOException e) {
            Log.w(TAG, "Failed to load test config from assets", e);
            return null;
        }
    }

    /**
     * Returns the best available config synchronously.
     * <ol>
     *   <li>If a config is already cached in memory, return it immediately.</li>
     *   <li>Otherwise try to load from disk cache.</li>
     *   <li>If neither exists, fetch from the network (blocking, up to 5s timeout).</li>
     * </ol>
     */
    @Nullable
    public HelpImageConfig getConfig() {
        HelpImageConfig config = cachedConfig;
        if (config != null) return config;

        config = loadFromDisk();
        if (config != null) {
            cachedConfig = config;
            return config;
        }

        return fetchFromNetworkBlocking();
    }

    /**
     * Async variant that always tries a fresh download, then invokes the callback.
     * The callback receives the new config, or the cached one if download failed.
     */
    public void refreshAsync(@Nullable Callback callback) {
        new Thread(() -> {
            HelpImageConfig fresh = fetchFromNetworkBlocking();
            HelpImageConfig result = fresh != null ? fresh : getConfig();
            final HelpImageConfig finalResult = result;
            if (callback != null) {
                mainHandler.post(() -> callback.onConfigReady(finalResult));
            }
        }).start();
    }

    /**
     * Returns {@code true} if the remote config version is newer than the cached one.
     */
    public boolean isUpdateAvailable() {
        HelpImageConfig local = getConfig();
        String localVersion = local != null ? local.version : "0";
        String remoteVersion = readRemoteVersionBlocking();
        return remoteVersion != null && !remoteVersion.equals(localVersion);
    }

    /**
     * Returns the local cache directory where downloaded images should be stored.
     * <p>
     * Path: {@code <app-cache-dir>/help_images/}
     */
    @NonNull
    public File getCacheDir() {
        File dir = new File(appContext.getCacheDir(), "help_images");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Clear the config cache (for testing / debug).
     */
    public void clearCache() {
        cachedConfig = null;
        File file = getConfigFile();
        if (file.exists()) file.delete();
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    // ---- Internals ----

    @Nullable
    private HelpImageConfig loadFromDisk() {
        File file = getConfigFile();
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read <= 0) return null;
            String json = new String(data, StandardCharsets.UTF_8);
            HelpImageConfig config = HelpImageConfig.fromJson(json);
            if (config != null) {
                Log.v(TAG, "Loaded config from disk, version=" + config.version);
            }
            return config;
        } catch (IOException e) {
            Log.w(TAG, "Failed to load config from disk", e);
            return null;
        }
    }

    @Nullable
    private HelpImageConfig fetchFromNetworkBlocking() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(CONFIG_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "Config download failed, HTTP " + code);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String json = sb.toString();
                HelpImageConfig config = HelpImageConfig.fromJson(json);
                if (config != null) {
                    saveToDisk(config, json);
                    Log.v(TAG, "Fetched config from network, version=" + config.version);
                    cachedConfig = config;
                    return config;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Config download failed (network error)", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    @Nullable
    private String readRemoteVersionBlocking() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(CONFIG_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            // If the server supports ETag or Last-Modified, we could use those too.
            // For now, download the full config just to read the version.
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                HelpImageConfig config = HelpImageConfig.fromJson(sb.toString());
                return config != null ? config.version : null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to read remote version", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void saveToDisk(@NonNull HelpImageConfig config, @NonNull String json) {
        File file = getConfigFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            // Remember the version for update checks
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_KEY_VERSION, config.version)
                    .apply();
        } catch (IOException e) {
            Log.w(TAG, "Failed to save config to disk", e);
        }
    }

    @NonNull
    private File getConfigFile() {
        return new File(appContext.getFilesDir(), CONFIG_FILE_NAME);
    }

    // ---- Callback ----

    public interface Callback {
        void onConfigReady(@Nullable HelpImageConfig config);
    }
}
