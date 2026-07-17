package com.openterface.keymod.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link RemoteResourceStore}.
 *
 * <p>Test strategy:
 * <ul>
 *   <li>Robolectric provides a {@link Context} isolated to a sandbox filesystem.</li>
 *   <li>{@link #tearDown()} clears the singleton and sandbox root directory before each test.</li>
 *   <li>For async APIs ({@code onResourceWritten} / {@code touchCached} /
 *       {@code invalidateOldVersions} / {@code clearAll}), calls
 *       {@link RemoteResourceStore#awaitPendingForTest()} to block until the executor completes.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RemoteResourceStoreTest {

    /** Very small limits for testing, to trigger eviction easily. */
    private static final long IMAGE_LIMIT = 100L;  // 100 bytes
    private static final long VIDEO_LIMIT = 200L;  // 200 bytes

    private Context context;
    private RemoteResourceStore store;
    private File rootDir;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = RemoteResourceStore.getInstanceForTest(context, IMAGE_LIMIT, VIDEO_LIMIT);
        rootDir = new File(context.getFilesDir(), "help_resources");
        // Clean up before each test
        store.clearAll();
        store.awaitPendingForTest();
        deleteRecursive(rootDir);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.clearAll();
            store.awaitPendingForTest();
        }
        deleteRecursive(rootDir);
        RemoteResourceStore.resetForTest();
    }

    // =====================================================================
    // Paths and queries
    // =====================================================================

    @Test
    public void getImageFile_returnsPathUnderVersionedImagesDir() {
        File f = store.getImageFile("https://cdn.example.com/basic/connection.gif", "v1.1.0");

        String root = rootDir.getAbsolutePath();
        String path = f.getAbsolutePath();
        assertTrue("path should be under rootDir", path.startsWith(root));
        assertTrue("path should contain version dir",
                path.contains(File.separator + "v1.1.0" + File.separator));
        assertTrue("path should contain images subdir",
                path.contains(File.separator + "images" + File.separator));
        assertTrue("filename should preserve original name",
                path.endsWith("connection.gif"));
    }

    @Test
    public void getVideoFile_returnsPathUnderVersionedVideosDir() {
        File f = store.getVideoFile("https://cdn.example.com/basic/target_os.mp4", "v1.1.0");

        String path = f.getAbsolutePath();
        assertTrue("path should contain videos subdir",
                path.contains(File.separator + "videos" + File.separator));
        assertTrue("filename should preserve original name",
                path.endsWith("target_os.mp4"));
    }

    @Test
    public void sanitizeUrl_replacesQueryAndSpecialChars() {
        // URLs containing illegal characters like ?=&: should be replaced with '_'
        String fileName = RemoteResourceStore.sanitizeFileName(
                "https://cdn.example.com/foo-bar.gif?v=1.0&size=large");
        assertFalse("no '?' allowed in filename", fileName.contains("?"));
        assertFalse("no '&' allowed in filename", fileName.contains("&"));
        assertFalse("no '=' allowed in filename", fileName.contains("="));
        // Legal characters should be preserved
        assertTrue("should preserve dot", fileName.contains("."));
        assertTrue("should preserve hyphen", fileName.contains("-"));
    }

    @Test
    public void sanitizeUrl_takesLastPathSegment() {
        String fileName = RemoteResourceStore.sanitizeFileName(
                "https://cdn.example.com/a/b/c/foo.gif");
        assertEquals("foo.gif", fileName);
    }

    @Test
    public void isImageCached_falseWhenFileMissing() {
        assertFalse(store.isImageCached(
                "https://cdn.example.com/basic/connection.gif", "v1.1.0"));
    }

    @Test
    public void isImageCached_trueAfterFileCreated() throws IOException {
        File f = store.getImageFile(
                "https://cdn.example.com/basic/connection.gif", "v1.1.0");
        writeFile(f, new byte[]{1, 2, 3});

        assertTrue(store.isImageCached(
                "https://cdn.example.com/basic/connection.gif", "v1.1.0"));
    }

    @Test
    public void isVideoCached_falseByDefault_trueAfterFileCreated() throws IOException {
        String url = "https://cdn.example.com/basic/target_os.mp4";
        String version = "v1.1.0";
        assertFalse(store.isVideoCached(url, version));

        File f = store.getVideoFile(url, version);
        writeFile(f, new byte[]{1, 2, 3, 4});
        assertTrue(store.isVideoCached(url, version));
    }

    // =====================================================================
    // onResourceWritten -> index update + eviction
    // =====================================================================

    @Test
    public void onResourceWritten_addsEntryToIndex() throws IOException {
        File f = createImageFile("a.gif", "v1", 50);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, f, 50);
        store.awaitPendingForTest();

        assertEquals(1, store.getEntryCount());
        assertEquals(50, store.getImageTotalSize());
    }

    @Test
    public void onResourceWritten_accumulatesTotalSize() throws IOException {
        File f1 = createImageFile("a.gif", "v1", 30);
        File f2 = createImageFile("b.gif", "v1", 40);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, f1, 30);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, f2, 40);
        store.awaitPendingForTest();

        assertEquals(2, store.getEntryCount());
        assertEquals(70, store.getImageTotalSize());
    }

    @Test
    public void lruEviction_runsWhenImageOverLimit() throws IOException {
        // Limit is 100 bytes; writing 3 files (60 + 60 + 60 = 180) should trigger eviction
        // Eviction target: drop to 80% of limit = 80 bytes
        // Expected: 2 oldest entries are evicted, leaving 1 (60 bytes)
        File old = createImageFile("old.gif", "v1", 60);
        sleep(20);
        File mid = createImageFile("mid.gif", "v1", 60);
        sleep(20);
        File latest = createImageFile("latest.gif", "v1", 60);

        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, old, 60);
        sleep(20);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, mid, 60);
        sleep(20);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, latest, 60);
        store.awaitPendingForTest();

        // Verify: only the latest entry remains
        assertEquals("only latest should remain", 1, store.getEntryCount());
        assertEquals("total size should be 60", 60, store.getImageTotalSize());

        // Verify: the 2 oldest files have been physically deleted
        assertFalse("old.gif should be deleted", old.isFile());
        assertFalse("mid.gif should be deleted", mid.isFile());
        assertTrue("latest.gif should remain", latest.isFile());
    }

    @Test
    public void lruEviction_doesNotCrossKind() throws IOException {
        // Image limit 100, video limit 200; image over-limit should not evict videos
        File img = createImageFile("x.gif", "v1", 200); // single image exceeds limit
        File vid = createVideoFile("y.mp4", "v1", 50);

        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, img, 200);
        store.onResourceWritten(RemoteResourceStore.Kind.VIDEO, vid, 50);
        store.awaitPendingForTest();

        // Image entry should be completely evicted (200 > 100 limit, all deleted until <= 80)
        assertEquals("image entry should be evicted", 0, countByKind(RemoteResourceStore.Kind.IMAGE));
        // Video should not be affected
        assertEquals("video should remain", 1, countByKind(RemoteResourceStore.Kind.VIDEO));
        assertTrue("video file should still exist on disk", vid.isFile());
    }

    // =====================================================================
    // touchCached -- update lastAccess to influence eviction order
    // =====================================================================

    @Test
    public void touchCached_updatesLastAccess() throws IOException {
        File a = createImageFile("a.gif", "v1", 40);
        File b = createImageFile("b.gif", "v1", 40);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, a, 40);
        sleep(20);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, b, 40);
        store.awaitPendingForTest();

        // Now a is the oldest. Touch a to make it the most recent.
        store.touchCached(a);
        store.awaitPendingForTest();

        // Write another large file to trigger eviction (total 80 + 40 = 120 > 100 limit)
        File c = createImageFile("c.gif", "v1", 40);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, c, 40);
        store.awaitPendingForTest();

        // Expected: b is evicted (was LRU before touch; a became newest due to touch)
        assertFalse("b.gif should be evicted (was LRU before touch)", b.isFile());
        assertTrue("a.gif should survive (was touched)", a.isFile());
        assertTrue("c.gif should survive (newest)", c.isFile());
    }

    // =====================================================================
    // invalidateOldVersions
    // =====================================================================

    @Test
    public void invalidateOldVersions_deletesOtherVersionDirs() throws IOException {
        File old = createImageFile("old.gif", "v1.0.0", 30);
        File current = createImageFile("current.gif", "v2.0.0", 30);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, old, 30);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, current, 30);
        store.awaitPendingForTest();

        store.invalidateOldVersions("v2.0.0");
        store.awaitPendingForTest();

        assertFalse("old version dir should be deleted",
                new File(rootDir, "v1.0.0").exists());
        assertTrue("current version dir should remain",
                new File(rootDir, "v2.0.0").exists());
        assertFalse("old.gif should be deleted", old.isFile());
        assertTrue("current.gif should remain", current.isFile());
        assertEquals("only current version entry should remain",
                1, store.getEntryCount());
    }

    // =====================================================================
    // clearAll
    // =====================================================================

    @Test
    public void clearAll_removesEverything() throws IOException {
        File f = createImageFile("foo.gif", "v1", 10);
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, f, 10);
        store.awaitPendingForTest();
        assertTrue(store.getEntryCount() > 0);

        store.clearAll();
        store.awaitPendingForTest();

        assertEquals(0, store.getEntryCount());
        assertEquals(0, store.getImageTotalSize());
        assertEquals(0, store.getVideoTotalSize());
        assertEquals(0, store.getTotalSize());
        assertFalse("rootDir should be deleted", rootDir.exists());
    }

    // =====================================================================
    // Concurrent writes should not corrupt the index
    // =====================================================================

    @Test
    public void concurrentWrites_doNotCorruptIndex() throws Exception {
        int N = 20;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    File f = createImageFile("concurrent_" + idx + ".gif", "v1", 10);
                    store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, f, 10);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown(); // Release all tasks
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        store.awaitPendingForTest();
        executor.shutdown();

        // Index should have recorded all 20 entries (even with eviction, it should be a valid state)
        int count = store.getEntryCount();
        assertTrue("index should have entries, got " + count, count > 0);
        assertTrue("index should not have more entries than written", count <= N);

        // Index file should be readable and valid
        File indexFile = new File(rootDir, "index.json");
        assertTrue("index.json should exist", indexFile.isFile());
        assertTrue("index.json should be non-empty", indexFile.length() > 0);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private File createImageFile(String name, String version, int size) throws IOException {
        File dir = new File(new File(rootDir, version), "images");
        // noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, name);
        writeFile(f, new byte[size]);
        return f;
    }

    private File createVideoFile(String name, String version, int size) throws IOException {
        File dir = new File(new File(rootDir, version), "videos");
        // noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, name);
        writeFile(f, new byte[size]);
        return f;
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        // noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
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

    private int countByKind(RemoteResourceStore.Kind kind) {
        // Indirectly verify by checking total size; since test scenarios use mutually
        // exclusive kinds, checking total > 0 is sufficient.
        if (kind == RemoteResourceStore.Kind.IMAGE) {
            return store.getImageTotalSize() > 0 ? 1 : 0;
        } else {
            return store.getVideoTotalSize() > 0 ? 1 : 0;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
