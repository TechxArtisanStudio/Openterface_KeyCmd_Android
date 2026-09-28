package com.openterface.keymod.agent.settings;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Instrumented tests for AIKeyManager.
 * Runs on a real device/emulator with actual Android Keystore support.
 */
@RunWith(AndroidJUnit4.class)
public class AIKeyManagerTest {

    private AIKeyManager manager;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        // Reset singleton for clean state
        resetSingleton();

        manager = AIKeyManager.getInstance(context);
    }

    // =====================================================================
    // Save and Get Tests
    // =====================================================================

    @Test
    public void testSaveAndGetKey() {
        manager.saveKey("provider1", "sk-test-key-123");
        String key = manager.getKey("provider1");
        assertEquals("Should retrieve saved key", "sk-test-key-123", key);
    }

    @Test
    public void testSaveKey_overwrite() {
        manager.saveKey("provider1", "first-key");
        manager.saveKey("provider1", "second-key");
        String key = manager.getKey("provider1");
        assertEquals("Should retrieve overwritten key", "second-key", key);
    }

    @Test
    public void testGetKey_notExists() {
        String key = manager.getKey("non-existent-provider");
        assertNull("Non-existent key should return null", key);
    }

    @Test
    public void testGetKey_nullProviderId() {
        String key = manager.getKey(null);
        assertNull("Null provider ID should return null", key);
    }

    @Test
    public void testSaveKey_nullProviderId() {
        // Should not throw
        manager.saveKey(null, "some-key");
    }

    @Test
    public void testSaveKey_nullKey() {
        manager.saveKey("provider1", "some-key");
        manager.saveKey("provider1", null);
        String key = manager.getKey("provider1");
        assertNull("Saving null key should delete it", key);
    }

    @Test
    public void testSaveKey_emptyKey() {
        manager.saveKey("provider1", "some-key");
        manager.saveKey("provider1", "");
        String key = manager.getKey("provider1");
        assertNull("Saving empty key should delete it", key);
    }

    // =====================================================================
    // HasKey Tests
    // =====================================================================

    @Test
    public void testHasKey_true() {
        manager.saveKey("provider1", "sk-test-key");
        assertTrue("Should return true for saved key", manager.hasKey("provider1"));
    }

    @Test
    public void testHasKey_false_notSaved() {
        assertFalse("Should return false for non-existent key", manager.hasKey("provider1"));
    }

    @Test
    public void testHasKey_false_afterDelete() {
        manager.saveKey("provider1", "sk-test-key");
        manager.deleteKey("provider1");
        assertFalse("Should return false after deletion", manager.hasKey("provider1"));
    }

    @Test
    public void testHasKey_false_afterSaveEmpty() {
        manager.saveKey("provider1", "sk-test-key");
        manager.saveKey("provider1", "");
        assertFalse("Should return false after saving empty", manager.hasKey("provider1"));
    }

    @Test
    public void testHasKey_nullProviderId() {
        assertFalse("Should return false for null ID", manager.hasKey(null));
    }

    // =====================================================================
    // Delete Tests
    // =====================================================================

    @Test
    public void testDeleteKey() {
        manager.saveKey("provider1", "sk-test-key");
        manager.deleteKey("provider1");
        String key = manager.getKey("provider1");
        assertNull("Deleted key should return null", key);
    }

    @Test
    public void testDeleteKey_notExists() {
        // Should not throw
        manager.deleteKey("non-existent-provider");
    }

    @Test
    public void testDeleteKey_nullProviderId() {
        // Should not throw
        manager.deleteKey(null);
    }

    // =====================================================================
    // Multiple Providers Tests
    // =====================================================================

    @Test
    public void testMultipleProviders_independent() {
        manager.saveKey("provider1", "key1");
        manager.saveKey("provider2", "key2");
        manager.saveKey("provider3", "key3");

        assertEquals("Provider 1 key", "key1", manager.getKey("provider1"));
        assertEquals("Provider 2 key", "key2", manager.getKey("provider2"));
        assertEquals("Provider 3 key", "key3", manager.getKey("provider3"));
    }

    @Test
    public void testMultipleProviders_deleteOne() {
        manager.saveKey("provider1", "key1");
        manager.saveKey("provider2", "key2");

        manager.deleteKey("provider1");

        assertNull("Provider 1 should be deleted", manager.getKey("provider1"));
        assertEquals("Provider 2 should remain", "key2", manager.getKey("provider2"));
    }

    // =====================================================================
    // Singleton Tests
    // =====================================================================

    @Test
    public void testSingletonInstance() {
        AIKeyManager manager2 = AIKeyManager.getInstance(context);
        assertSame("getInstance should return same instance", manager, manager2);
    }

    @Test
    public void testSingleton_persistsData() throws Exception {
        manager.saveKey("provider1", "persistent-key");

        // Reset singleton and get new instance
        resetSingleton();
        AIKeyManager newManager = AIKeyManager.getInstance(context);

        assertEquals("Data should persist across instances",
                "persistent-key", newManager.getKey("provider1"));
    }

    // =====================================================================
    // Edge Cases
    // =====================================================================

    @Test
    public void testSaveKey_specialCharacters() {
        String specialKey = "sk-test-key-with-special-chars!@#$%^&*()_+-=[]{}|;':\",./<>?";
        manager.saveKey("provider1", specialKey);
        assertEquals("Should handle special characters", specialKey, manager.getKey("provider1"));
    }

    @Test
    public void testSaveKey_unicodeCharacters() {
        String unicodeKey = "sk-test-key-中文-テスト-한국어";
        manager.saveKey("provider1", unicodeKey);
        assertEquals("Should handle unicode characters", unicodeKey, manager.getKey("provider1"));
    }

    @Test
    public void testSaveKey_longKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        String longKey = sb.toString();
        manager.saveKey("provider1", longKey);
        assertEquals("Should handle long keys", longKey, manager.getKey("provider1"));
    }

    @Test
    public void testSaveKey_emptyProviderId() {
        manager.saveKey("", "some-key");
        // Empty string is a valid key in SharedPreferences
        String key = manager.getKey("");
        assertEquals("Should handle empty provider ID", "some-key", key);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void resetSingleton() throws Exception {
        Field field = AIKeyManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
