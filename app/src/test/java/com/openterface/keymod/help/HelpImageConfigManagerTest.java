package com.openterface.keymod.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Unit tests for {@link HelpImageConfigManager}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>{@code getCachedVersion()} returns "0" before loading and actual version after.</li>
 *   <li>{@code getConfig()} loads from disk cache.</li>
 *   <li>{@code clearCache()} resets state.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HelpImageConfigManagerTest {

    private Context context;
    private HelpImageConfigManager manager;
    private File configFile;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        configFile = new File(context.getFilesDir(), "help_config.json");

        // Reset singleton for clean state
        resetSingleton();

        manager = HelpImageConfigManager.getInstance(context);
        // Clear any persisted state from previous tests
        manager.clearCache();
    }

    @After
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.clearCache();
        }
        deleteRecursive(configFile);
        resetSingleton();
    }

    // =====================================================================
    // getCachedVersion()
    // =====================================================================

    @Test
    public void getCachedVersion_returnsZeroWhenNoConfig() {
        assertEquals("0", manager.getCachedVersion());
    }

    @Test
    public void getCachedVersion_returnsVersionAfterGetConfig() throws Exception {
        // Write a known config to disk so getConfig() can load it
        String json = "{\"version\":\"2.0.0\",\"baseUrl\":\"https://example.com/\",\"modes\":{}}";
        writeFile(configFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Reset cachedConfig in memory so getConfig() falls through to loadFromDisk
        resetSingleton();
        manager = HelpImageConfigManager.getInstance(context);

        HelpImageConfig config = manager.getConfig();
        assertNotNull(config);
        assertEquals("2.0.0", config.version);
        assertEquals("2.0.0", manager.getCachedVersion());
    }

    @Test
    public void getCachedVersion_returnsZeroAfterClearCache() throws Exception {
        // Load a config
        String json = "{\"version\":\"3.0.0\",\"baseUrl\":\"\",\"modes\":{}}";
        writeFile(configFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        resetSingleton();
        manager = HelpImageConfigManager.getInstance(context);
        manager.getConfig(); // populate cachedConfig
        assertEquals("3.0.0", manager.getCachedVersion());

        // Clear
        manager.clearCache();
        assertEquals("0", manager.getCachedVersion());
    }

    // =====================================================================
    // getConfig()
    // =====================================================================

    @Test
    public void getConfig_returnsNullWhenNoDiskOrNetwork() {
        // No config on disk, no network (Robolectric can't reach real CDN)
        HelpImageConfig config = manager.getConfig();
        assertNull("Should return null when no config available", config);
    }

    @Test
    public void getConfig_loadsFromDisk() throws Exception {
        String json = "{\"version\":\"4.0.0\",\"baseUrl\":\"https://test.com/\",\"modes\":{}}";
        writeFile(configFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        resetSingleton();
        manager = HelpImageConfigManager.getInstance(context);

        HelpImageConfig config = manager.getConfig();
        assertNotNull(config);
        assertEquals("4.0.0", config.version);
        assertEquals("https://test.com/", config.baseUrl);
    }

    @Test
    public void getConfig_returnsCachedConfigFromMemory() throws Exception {
        String json = "{\"version\":\"5.0.0\",\"baseUrl\":\"\",\"modes\":{}}";
        writeFile(configFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        resetSingleton();
        manager = HelpImageConfigManager.getInstance(context);

        // First call loads from disk
        HelpImageConfig config1 = manager.getConfig();
        assertNotNull(config1);

        // Delete the file — second call should still return cached version
        configFile.delete();
        HelpImageConfig config2 = manager.getConfig();
        assertNotNull("Should return cached config from memory", config2);
        assertEquals("5.0.0", config2.version);
    }

    // =====================================================================
    // clearCache()
    // =====================================================================

    @Test
    public void clearCache_deletesFileAndResetsVersion() throws Exception {
        String json = "{\"version\":\"6.0.0\",\"baseUrl\":\"\",\"modes\":{}}";
        writeFile(configFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        resetSingleton();
        manager = HelpImageConfigManager.getInstance(context);
        manager.getConfig();
        assertEquals("6.0.0", manager.getCachedVersion());
        assertTrue("Config file should exist before clear", configFile.exists());

        manager.clearCache();

        assertEquals("0", manager.getCachedVersion());
        assertTrue("Config file should be deleted", !configFile.exists());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void resetSingleton() throws Exception {
        Field field = HelpImageConfigManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void writeFile(File f, byte[] data) throws Exception {
        // noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            fos.write(data);
            fos.flush();
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        // noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
