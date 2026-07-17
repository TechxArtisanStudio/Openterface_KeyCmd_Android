package com.openterface.keymod.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for {@link HelpImageDownloader}.
 *
 * <p>Uses Robolectric for Android context and {@code file://} URLs for
 * deterministic download simulation without network dependencies.</p>
 *
 * <p>All callbacks fire on the main thread via {@code mainHandler.post()},
 * so we must call {@link ShadowLooper#idle()} to process them.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HelpImageDownloaderTest {

    private static final String VERSION = "v1.0.0";

    private Context context;
    private RemoteResourceStore store;
    private HelpImageDownloader downloader;
    private File rootDir;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        rootDir = new File(context.getFilesDir(), "help_resources");
        deleteRecursive(rootDir);

        // Reset singletons so each test starts with a clean state
        resetSingleton(RemoteResourceStore.class, "instance", null);
        resetSingleton(HelpImageDownloader.class, "instance", null);

        store = RemoteResourceStore.getInstance(context);
        downloader = HelpImageDownloader.getInstance(context);
    }

    @After
    public void tearDown() throws Exception {
        // Drain any remaining main looper tasks
        shadowMainLooper().idle();
        resetSingleton(HelpImageDownloader.class, "instance", null);
        resetSingleton(RemoteResourceStore.class, "instance", null);
        deleteRecursive(rootDir);
    }

    // =====================================================================
    // Image download
    // =====================================================================

    @Test
    public void download_writesToRemoteResourceStorePath() throws Exception {
        File source = createTempFile("source.gif", new byte[]{1, 2, 3, 4, 5});
        String fileUrl = source.toURI().toURL().toString();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<File> result = new AtomicReference<>();
        downloader.download(fileUrl, VERSION, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) {
                result.set(localFile);
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                latch.countDown();
            }
        });

        assertTrue("Download should complete within 10s",
                awaitWithLooper(latch, 10));
        assertNotNull("Callback should receive a file", result.get());
        assertTrue("Downloaded file should exist", result.get().exists());

        // Verify the file is inside the versioned image directory
        File expected = store.getImageFile(fileUrl, VERSION);
        assertEquals("File should be at RemoteResourceStore path",
                expected.getAbsolutePath(), result.get().getAbsolutePath());
        assertTrue("File at expected path should exist", expected.exists());
    }

    @Test
    public void download_cacheHit_returnsImmediately() throws Exception {
        // Pre-create the cached file via RemoteResourceStore's path
        File cachedFile = store.getImageFile("https://cdn.example.com/cached.gif", VERSION);
        writeFile(cachedFile, new byte[]{10, 20, 30});
        store.onResourceWritten(RemoteResourceStore.Kind.IMAGE, cachedFile, 3);
        store.awaitPendingForTest();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<File> result = new AtomicReference<>();
        downloader.download("https://cdn.example.com/cached.gif", VERSION,
                new HelpImageDownloader.Callback() {
                    @Override
                    public void onSuccess(File localFile) {
                        result.set(localFile);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception error) {
                        latch.countDown();
                    }
                });

        // Cache hit callback is posted to main handler — need to idle looper
        assertTrue("Cache hit callback should fire",
                awaitWithLooper(latch, 5));
        assertNotNull(result.get());
        assertEquals(cachedFile.getAbsolutePath(), result.get().getAbsolutePath());
    }

    @Test
    public void download_notifiesRemoteResourceStore() throws Exception {
        File source = createTempFile("notify.gif", new byte[50]);
        String fileUrl = source.toURI().toURL().toString();

        CountDownLatch latch = new CountDownLatch(1);
        downloader.download(fileUrl, VERSION, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) { latch.countDown(); }

            @Override
            public void onError(Exception error) { latch.countDown(); }
        });

        assertTrue(awaitWithLooper(latch, 10));
        store.awaitPendingForTest();

        assertTrue("Image total size should be > 0 after download",
                store.getImageTotalSize() > 0);
    }

    @Test
    public void download_multipleListeners_allNotified() throws Exception {
        File source = createTempFile("multi.gif", new byte[]{1, 2, 3});
        String fileUrl = source.toURI().toURL().toString();

        CountDownLatch latch = new CountDownLatch(2);
        List<File> results = new ArrayList<>();

        HelpImageDownloader.Callback cb1 = new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) {
                synchronized (results) { results.add(localFile); }
                latch.countDown();
            }

            @Override
            public void onError(Exception error) { latch.countDown(); }
        };
        HelpImageDownloader.Callback cb2 = new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) {
                synchronized (results) { results.add(localFile); }
                latch.countDown();
            }

            @Override
            public void onError(Exception error) { latch.countDown(); }
        };

        // Register both listeners for the same URL
        downloader.download(fileUrl, VERSION, cb1);
        downloader.download(fileUrl, VERSION, cb2);

        assertTrue("Both callbacks should fire within 10s",
                awaitWithLooper(latch, 10));
        assertEquals("Both listeners should receive the file", 2, results.size());
    }

    @Test
    public void download_retryOnFailure_eventuallyFails() throws Exception {
        // Use a non-routable address that should fail quickly (connection refused).
        // 10.255.255.1 is in the non-routable TEST-NET range.
        String badUrl = "http://127.0.0.1:1/nope.gif";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        long start = System.currentTimeMillis();

        downloader.download(badUrl, VERSION, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) { latch.countDown(); }

            @Override
            public void onError(Exception error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        // Retry: 3 attempts × (connect_timeout=10s) + delays (1+2+4=7s)
        // Worst case: ~37s. Use generous timeout.
        assertTrue("Should complete within 60s",
                awaitWithLooper(latch, 60));
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull("Should report an error", errorRef.get());
        // At minimum: 3 retries × delays = 1+2+4 = 7s
        assertTrue("Should have retried with backoff (>= 5s), elapsed=" + elapsed,
                elapsed >= 5000);
    }

    // =====================================================================
    // Video download
    // =====================================================================

    @Test
    public void downloadVideo_writesToVideoPath() throws Exception {
        File source = createTempFile("clip.mp4", new byte[]{1, 2, 3, 4, 5, 6});
        String fileUrl = source.toURI().toURL().toString();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<File> result = new AtomicReference<>();
        downloader.downloadVideo(fileUrl, VERSION, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) {
                result.set(localFile);
                latch.countDown();
            }

            @Override
            public void onError(Exception error) { latch.countDown(); }
        });

        assertTrue(awaitWithLooper(latch, 10));
        assertNotNull(result.get());

        File expected = store.getVideoFile(fileUrl, VERSION);
        assertEquals("Video should be in videos/ directory",
                expected.getAbsolutePath(), result.get().getAbsolutePath());
    }

    @Test
    public void downloadVideo_notifiesRemoteResourceStore() throws Exception {
        File source = createTempFile("clip2.mp4", new byte[80]);
        String fileUrl = source.toURI().toURL().toString();

        CountDownLatch latch = new CountDownLatch(1);
        downloader.downloadVideo(fileUrl, VERSION, new HelpImageDownloader.Callback() {
            @Override
            public void onSuccess(File localFile) { latch.countDown(); }

            @Override
            public void onError(Exception error) { latch.countDown(); }
        });

        assertTrue(awaitWithLooper(latch, 10));
        store.awaitPendingForTest();

        assertTrue("Video total size should be > 0 after download",
                store.getVideoTotalSize() > 0);
    }

    // =====================================================================
    // Cache query methods
    // =====================================================================

    @Test
    public void getCachedFile_returnsNullWhenNotCached() {
        assertNull(downloader.getCachedFile("https://cdn.example.com/missing.gif", VERSION));
    }

    @Test
    public void getCachedFile_returnsFileWhenCached() throws Exception {
        File f = store.getImageFile("https://cdn.example.com/present.gif", VERSION);
        writeFile(f, new byte[]{1, 2, 3});

        File result = downloader.getCachedFile("https://cdn.example.com/present.gif", VERSION);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    @Test
    public void isCached_returnsFalseThenTrue() throws Exception {
        String url = "https://cdn.example.com/check.gif";
        assertFalse(downloader.isCached(url, VERSION));

        File f = store.getImageFile(url, VERSION);
        writeFile(f, new byte[]{1});
        assertTrue(downloader.isCached(url, VERSION));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Wait for a latch while periodically idling the main looper.
     * Callbacks posted via {@code mainHandler.post()} only execute when the
     * main looper processes its message queue.
     */
    private boolean awaitWithLooper(CountDownLatch latch, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(100, TimeUnit.MILLISECONDS)) {
                // Drain any remaining posted callbacks
                shadowMainLooper().idle();
                return true;
            }
            shadowMainLooper().idle();
        }
        return false;
    }

    private ShadowLooper shadowMainLooper() {
        return ShadowLooper.shadowMainLooper();
    }

    /** Create a temp file with given content and return it. */
    private File createTempFile(String name, byte[] data) throws Exception {
        File dir = new File(context.getCacheDir(), "test_sources");
        // noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, name);
        writeFile(f, data);
        return f;
    }

    private static void writeFile(File f, byte[] data) throws Exception {
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

    /** Reset a singleton's instance field via reflection. */
    private static void resetSingleton(Class<?> clazz, String fieldName, Object newValue)
            throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, newValue);
    }
}
