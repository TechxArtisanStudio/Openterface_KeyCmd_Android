package com.openterface.keymod.agent.settings;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for AIProviderManager.
 * Tests provider list management: CRUD operations, selection, persistence, edge cases.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AIProviderManagerTest {

    private AIProviderManager manager;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();

        // Clear SharedPreferences for clean state
        context.getSharedPreferences("ai_provider_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit();

        // Reset singleton
        resetSingleton();

        manager = AIProviderManager.getInstance(context);
    }

    // =====================================================================
    // Initialization Tests
    // =====================================================================

    @Test
    public void testInitWithDefaults() {
        List<AIProvider> providers = manager.getProviders();
        assertNotNull("Providers list should not be null", providers);
        assertEquals("Should have 10 default providers", 10, providers.size());
    }

    @Test
    public void testInitSelectsFirst() {
        AIProvider selected = manager.getSelectedProvider();
        assertNotNull("Should have a selected provider", selected);
        assertEquals("Should select first provider", "OpenAI", selected.name);
    }

    @Test
    public void testInitSelectedIndex() {
        assertEquals("Selected index should be 0", 0, manager.getSelectedProviderIndex());
    }

    // =====================================================================
    // Get Tests
    // =====================================================================

    @Test
    public void testGetProviders_returnsCopy() {
        List<AIProvider> list1 = manager.getProviders();
        List<AIProvider> list2 = manager.getProviders();
        assertNotSame("getProviders should return a new list each time", list1, list2);
    }

    @Test
    public void testGetProvider_validIndex() {
        AIProvider provider = manager.getProvider(0);
        assertNotNull("Should return provider at index 0", provider);
        assertEquals("OpenAI", provider.name);
    }

    @Test
    public void testGetProvider_lastIndex() {
        AIProvider provider = manager.getProvider(9);
        assertNotNull("Should return provider at last index", provider);
        assertEquals("Local Qwen 1.7B", provider.name);
    }

    @Test
    public void testGetProvider_invalidIndex_negative() {
        AIProvider provider = manager.getProvider(-1);
        assertNull("Negative index should return null", provider);
    }

    @Test
    public void testGetProvider_invalidIndex_tooLarge() {
        AIProvider provider = manager.getProvider(100);
        assertNull("Too large index should return null", provider);
    }

    @Test
    public void testGetProviderById_exists() {
        AIProvider first = manager.getProvider(0);
        AIProvider found = manager.getProviderById(first.id);
        assertNotNull("Should find provider by ID", found);
        assertEquals("Should return same provider", first.name, found.name);
    }

    @Test
    public void testGetProviderById_notFound() {
        AIProvider found = manager.getProviderById("non-existent-id");
        assertNull("Non-existent ID should return null", found);
    }

    @Test
    public void testGetProviderById_null() {
        AIProvider found = manager.getProviderById(null);
        assertNull("Null ID should return null", found);
    }

    @Test
    public void testGetProviderIndexById_exists() {
        AIProvider provider = manager.getProvider(3);
        int index = manager.getProviderIndexById(provider.id);
        assertEquals("Should return correct index", 3, index);
    }

    @Test
    public void testGetProviderIndexById_notFound() {
        int index = manager.getProviderIndexById("non-existent-id");
        assertEquals("Non-existent ID should return -1", -1, index);
    }

    @Test
    public void testGetProviderIndexById_null() {
        int index = manager.getProviderIndexById(null);
        assertEquals("Null ID should return -1", -1, index);
    }

    @Test
    public void testGetProviderCount() {
        assertEquals("Should have 10 providers", 10, manager.getProviderCount());
    }

    // =====================================================================
    // Selection Tests
    // =====================================================================

    @Test
    public void testSetSelectedProvider() {
        AIProvider provider = manager.getProvider(5);
        manager.setSelectedProvider(provider.id);

        AIProvider selected = manager.getSelectedProvider();
        assertNotNull(selected);
        assertEquals("Should select the specified provider", provider.name, selected.name);
    }

    @Test
    public void testSetSelectedProviderIndex() {
        manager.setSelectedProviderIndex(3);
        assertEquals("Selected index should be 3", 3, manager.getSelectedProviderIndex());

        AIProvider selected = manager.getSelectedProvider();
        assertEquals("Should select provider at index 3", manager.getProvider(3).name, selected.name);
    }

    @Test
    public void testSetSelectedProvider_invalidId() {
        manager.setSelectedProviderIndex(0);
        String originalId = manager.getSelectedProvider().id;

        manager.setSelectedProvider("non-existent-id");

        assertEquals("Invalid ID should not change selection",
                originalId, manager.getSelectedProvider().id);
    }

    @Test
    public void testSetSelectedProviderIndex_invalidIndex() {
        manager.setSelectedProviderIndex(0);
        String originalId = manager.getSelectedProvider().id;

        manager.setSelectedProviderIndex(-1);
        assertEquals("Negative index should not change selection",
                originalId, manager.getSelectedProvider().id);

        manager.setSelectedProviderIndex(100);
        assertEquals("Too large index should not change selection",
                originalId, manager.getSelectedProvider().id);
    }

    @Test
    public void testSelectionPersisted() throws Exception {
        manager.setSelectedProviderIndex(3);
        String selectedId = manager.getSelectedProvider().id;

        // Reset singleton and re-get instance
        resetSingleton();
        AIProviderManager newManager = AIProviderManager.getInstance(context);

        assertEquals("Selection should persist across instances",
                selectedId, newManager.getSelectedProvider().id);
    }

    // =====================================================================
    // Add Tests
    // =====================================================================

    @Test
    public void testAddProvider() {
        int initialCount = manager.getProviderCount();
        AIProvider newProvider = new AIProvider("My Provider", "https://my.api.com/v1", "my-model", false);

        manager.addProvider(newProvider);

        assertEquals("Count should increase by 1", initialCount + 1, manager.getProviderCount());
    }

    @Test
    public void testAddProvider_autoSelects() {
        AIProvider newProvider = new AIProvider("My Provider", "https://my.api.com/v1", "my-model", false);
        manager.addProvider(newProvider);

        AIProvider selected = manager.getSelectedProvider();
        assertEquals("Should auto-select the new provider", "My Provider", selected.name);
    }

    @Test
    public void testAddProvider_persisted() throws Exception {
        AIProvider newProvider = new AIProvider("My Provider", "https://my.api.com/v1", "my-model", false);
        manager.addProvider(newProvider);
        int newCount = manager.getProviderCount();

        resetSingleton();
        AIProviderManager newManager = AIProviderManager.getInstance(context);

        assertEquals("Added provider should persist", newCount, newManager.getProviderCount());
    }

    @Test
    public void testAddProvider_null() {
        int initialCount = manager.getProviderCount();
        manager.addProvider(null);
        assertEquals("Adding null should not change count", initialCount, manager.getProviderCount());
    }

    @Test
    public void testAddMultipleProviders() {
        int initialCount = manager.getProviderCount();

        manager.addProvider(new AIProvider("Provider A", "https://a.com", "model-a", false));
        manager.addProvider(new AIProvider("Provider B", "https://b.com", "model-b", false));
        manager.addProvider(new AIProvider("Provider C", "https://c.com", "model-c", true));

        assertEquals("Should have 3 more providers", initialCount + 3, manager.getProviderCount());
    }

    // =====================================================================
    // Remove Tests
    // =====================================================================

    @Test
    public void testRemoveProvider_byId() {
        int initialCount = manager.getProviderCount();
        String idToRemove = manager.getProvider(1).id;

        boolean result = manager.removeProvider(idToRemove);

        assertTrue("Remove should succeed", result);
        assertEquals("Count should decrease by 1", initialCount - 1, manager.getProviderCount());
    }

    @Test
    public void testRemoveProvider_byIndex() {
        int initialCount = manager.getProviderCount();

        boolean result = manager.removeProvider(1);

        assertTrue("Remove should succeed", result);
        assertEquals("Count should decrease by 1", initialCount - 1, manager.getProviderCount());
    }

    @Test
    public void testRemoveProvider_lastProvider_fails() {
        // Remove all but one
        while (manager.getProviderCount() > 1) {
            manager.removeProvider(manager.getProviderCount() - 1);
        }

        assertEquals("Should have 1 provider left", 1, manager.getProviderCount());

        boolean result = manager.removeProvider(0);
        assertFalse("Should not be able to remove last provider", result);
        assertEquals("Should still have 1 provider", 1, manager.getProviderCount());
    }

    @Test
    public void testRemoveProvider_selected_autoSelectsFirst() {
        // Select provider at index 2
        manager.setSelectedProviderIndex(2);
        String removedId = manager.getSelectedProvider().id;

        manager.removeProvider(removedId);

        AIProvider newSelected = manager.getSelectedProvider();
        assertNotNull("Should have a selected provider after removal", newSelected);
        assertNotEquals("Should not still reference removed provider",
                removedId, newSelected.id);
    }

    @Test
    public void testRemoveProvider_notSelected_keepsSelection() {
        manager.setSelectedProviderIndex(0);
        String selectedId = manager.getSelectedProvider().id;

        // Remove provider at index 5 (not selected)
        manager.removeProvider(5);

        assertEquals("Selection should not change",
                selectedId, manager.getSelectedProvider().id);
    }

    @Test
    public void testRemoveProvider_invalidId() {
        int initialCount = manager.getProviderCount();
        boolean result = manager.removeProvider("non-existent-id");
        assertFalse("Remove non-existent should return false", result);
        assertEquals("Count should not change", initialCount, manager.getProviderCount());
    }

    @Test
    public void testRemoveProvider_invalidIndex() {
        int initialCount = manager.getProviderCount();

        assertFalse("Negative index should fail", manager.removeProvider(-1));
        assertFalse("Too large index should fail", manager.removeProvider(100));
        assertEquals("Count should not change", initialCount, manager.getProviderCount());
    }

    @Test
    public void testRemoveProvider_persisted() throws Exception {
        int initialCount = manager.getProviderCount();
        manager.removeProvider(1);

        resetSingleton();
        AIProviderManager newManager = AIProviderManager.getInstance(context);

        assertEquals("Removal should persist", initialCount - 1, newManager.getProviderCount());
    }

    // =====================================================================
    // Update Tests
    // =====================================================================

    @Test
    public void testUpdateProvider() {
        AIProvider provider = manager.getProvider(0);
        provider.name = "Updated OpenAI";
        provider.apiBaseURL = "https://custom.openai.com/v1";

        manager.updateProvider(provider);

        AIProvider updated = manager.getProvider(0);
        assertEquals("Name should be updated", "Updated OpenAI", updated.name);
        assertEquals("URL should be updated", "https://custom.openai.com/v1", updated.apiBaseURL);
    }

    @Test
    public void testUpdateProvider_persisted() throws Exception {
        AIProvider provider = manager.getProvider(0);
        provider.name = "Updated OpenAI";
        manager.updateProvider(provider);

        resetSingleton();
        AIProviderManager newManager = AIProviderManager.getInstance(context);

        assertEquals("Update should persist", "Updated OpenAI", newManager.getProvider(0).name);
    }

    @Test
    public void testUpdateProvider_null() {
        String originalName = manager.getProvider(0).name;
        manager.updateProvider(null);
        assertEquals("Null update should not change anything", originalName, manager.getProvider(0).name);
    }

    @Test
    public void testUpdateProvider_nullId() {
        AIProvider provider = new AIProvider();
        provider.name = "No ID";
        provider.id = null;

        int initialCount = manager.getProviderCount();
        manager.updateProvider(provider);
        assertEquals("Null ID update should not change count", initialCount, manager.getProviderCount());
    }

    // =====================================================================
    // Reset Tests
    // =====================================================================

    @Test
    public void testResetToDefaults() {
        // Add some providers
        manager.addProvider(new AIProvider("Custom1", "https://c1.com", "m1", false));
        manager.addProvider(new AIProvider("Custom2", "https://c2.com", "m2", false));
        assertEquals("Should have 12 providers", 12, manager.getProviderCount());

        manager.resetToDefaults();

        assertEquals("Should have 10 providers after reset", 10, manager.getProviderCount());
        assertEquals("Should select first provider", "OpenAI", manager.getSelectedProvider().name);
    }

    @Test
    public void testResetToDefaults_persisted() throws Exception {
        manager.addProvider(new AIProvider("Custom", "https://c.com", "m", false));
        manager.resetToDefaults();

        resetSingleton();
        AIProviderManager newManager = AIProviderManager.getInstance(context);

        assertEquals("Reset should persist", 10, newManager.getProviderCount());
    }

    // =====================================================================
    // Singleton Tests
    // =====================================================================

    @Test
    public void testSingletonInstance() {
        AIProviderManager manager2 = AIProviderManager.getInstance(context);
        assertSame("getInstance should return same instance", manager, manager2);
    }

    // =====================================================================
    // Edge Cases
    // =====================================================================

    @Test
    public void testAddAndRemoveCycle() {
        int initialCount = manager.getProviderCount();

        // Add 5 providers
        for (int i = 0; i < 5; i++) {
            manager.addProvider(new AIProvider("Temp " + i, "https://temp" + i + ".com", "model", false));
        }
        assertEquals("Should have initial + 5", initialCount + 5, manager.getProviderCount());

        // Remove them all (from the end)
        for (int i = 0; i < 5; i++) {
            manager.removeProvider(manager.getProviderCount() - 1);
        }
        assertEquals("Should return to initial count", initialCount, manager.getProviderCount());
    }

    @Test
    public void testRapidAddRemove() {
        int initialCount = manager.getProviderCount();

        for (int i = 0; i < 20; i++) {
            manager.addProvider(new AIProvider("Rapid " + i, "https://r" + i + ".com", "m", false));
        }
        assertEquals("Should have initial + 20", initialCount + 20, manager.getProviderCount());

        // Remove back to initial
        while (manager.getProviderCount() > initialCount) {
            manager.removeProvider(manager.getProviderCount() - 1);
        }
        assertEquals("Should return to initial", initialCount, manager.getProviderCount());
    }

    @Test
    public void testCreateNewEmptyProvider() {
        AIProvider empty = manager.createNewEmptyProvider();
        assertNotNull("Should create a non-null provider", empty);
        assertEquals("Name should be 'New Provider'", "New Provider", empty.name);
        assertEquals("URL should be empty", "", empty.apiBaseURL);
        assertEquals("Model should be empty", "", empty.modelName);
        assertFalse("apiKeyOptional should be false", empty.apiKeyOptional);
        assertNotNull("Should have an ID", empty.id);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void resetSingleton() throws Exception {
        Field field = AIProviderManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
