package com.openterface.keymod.compose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SavedTextRepositoryStarterTest {

    private SavedTextRepository repo;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("SavedTextPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        repo = new SavedTextRepository(ctx);
    }

    @Test
    public void loadSorted_emptyPrefs_seedsSevenStartersWithTwoPinned() {
        List<SavedTextItem> items = repo.loadSorted();
        assertEquals(7, items.size());
        assertEquals("prod-deploy", items.get(0).title);
        assertTrue(items.get(0).pinned);
        assertEquals("rotate-root-pass", items.get(1).title);
        assertTrue(items.get(1).pinned);
        long pinnedCount = 0;
        for (SavedTextItem it : items) {
            if (it.pinned) {
                pinnedCount++;
            }
        }
        assertEquals(2, pinnedCount);
    }

    @Test
    public void loadSorted_secondCall_doesNotDuplicateStarters() {
        assertEquals(7, repo.loadSorted().size());
        assertEquals(7, repo.loadSorted().size());
    }

    @Test
    public void loadSorted_existingUserItem_skipsStarters() {
        SavedTextItem existing = repo.addFromPlainText("my custom snippet");
        assertEquals(1, repo.loadSorted().size());
        assertEquals(existing.id, repo.loadSorted().get(0).id);
    }

    @Test
    public void buildStarterItems_includesExpectedTitles() {
        List<SavedTextItem> starters = ComposeSavedTextStarters.buildStarterItems(1_000L);
        assertEquals(7, starters.size());
        assertEquals("prod-deploy", starters.get(0).title);
        assertEquals("rotate-root-pass", starters.get(1).title);
        assertEquals("docker-prune-safe", starters.get(2).title);
        assertEquals("nas-mount-nfs", starters.get(3).title);
        assertEquals("k8s-rollout-status", starters.get(4).title);
        assertEquals("ufw-allow-ssh", starters.get(5).title);
        assertEquals("journalctl-boot-err", starters.get(6).title);
        assertFalse(starters.get(2).pinned);
    }
}
