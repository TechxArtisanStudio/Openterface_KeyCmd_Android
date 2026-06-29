package com.openterface.keymod.help;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads help guide images/GIFs/MP4s from the remote server and caches them
 * via {@link RemoteResourceStore}.
 * <p>
 * Features:
 * <ul>
 *   <li>Concurrent download pool (2 threads)</li>
 *   <li>Deduplication — same URL only downloaded once; multiple listeners supported</li>
 *   <li>Retry with exponential backoff (1s / 2s / 4s, max 3 retries)</li>
 *   <li>Progress callback</li>
 *   <li>Versioned cache paths via {@link RemoteResourceStore}</li>
 *   <li>LRU eviction on write</li>
 *   <li>Thread-safe public API</li>
 *   <li>Callbacks fire on the main thread</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * HelpImageDownloader downloader = HelpImageDownloader.getInstance(context);
 * downloader.download(imageUrl, configVersion, new HelpImageDownloader.Callback() {
 *     public void onSuccess(File localFile) { ... }
 *     public void onError(Exception e) { ... }
 *     public void onProgress(long downloaded, long total) { ... }
 * });
 * </pre>
 */
public final class HelpImageDownloader {

    private static final String TAG = "HelpImageDownloader";

    /** Max concurrent downloads. */
    private static final int MAX_CONCURRENT = 2;

    /** Read timeout for downloads (15 seconds). */
    private static final int READ_TIMEOUT_MS = 15_000;

    /** Connect timeout for downloads (10 seconds). */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** Maximum number of retry attempts for IOException. */
    private static final int MAX_RETRIES = 3;

    /** Retry delays in ms: 1s, 2s, 4s (exponential backoff). */
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    /** In-flight URL deduplication set (shared for images and videos). */
    private final Set<String> downloading = new HashSet<>();

    /** Pending callbacks per URL — supports multiple listeners per download. */
    private final Map<String, List<Callback>> pendingCallbacks = new HashMap<>();

    /** Executor for background download threads. */
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT);

    /** Main-thread handler for callbacks. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Unified cache store (versioned paths + LRU). */
    private final RemoteResourceStore store;

    private static HelpImageDownloader instance;

    private HelpImageDownloader(Context context) {
        Context appContext = context.getApplicationContext();
        this.store = RemoteResourceStore.getInstance(appContext);
    }

    @NonNull
    public static synchronized HelpImageDownloader getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new HelpImageDownloader(context);
        }
        return instance;
    }

    // =====================================================================
    // Public API — Image
    // =====================================================================

    /**
     * Download an image from the given URL and cache it locally under the versioned path.
     * <p>
     * If the file is already cached, the callback fires immediately with the cached file
     * and {@link RemoteResourceStore#touchCached} is called to update LRU order.
     * If the same URL is already being downloaded, the callback is registered and will
     * be notified when the in-flight download completes.
     *
     * @param url           the image URL (http/https/file)
     * @param configVersion the config version for cache path resolution
     * @param callback      receives the local file on success, error, or progress
     */
    public void download(@NonNull String url, @NonNull String configVersion,
                         @Nullable Callback callback) {
        File localFile = store.getImageFile(url, configVersion);

        // Already cached — return immediately
        if (localFile.exists()) {
            Log.d(TAG, "Image cache hit: " + url);
            store.touchCached(localFile);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(localFile));
            }
            return;
        }

        // Already in flight — register callback for notification
        synchronized (pendingCallbacks) {
            if (downloading.contains(url)) {
                Log.d(TAG, "Image already downloading, registering listener: " + url);
                if (callback != null) {
                    pendingCallbacks.computeIfAbsent(url, k -> new ArrayList<>()).add(callback);
                }
                return;
            }
            downloading.add(url);
            if (callback != null) {
                pendingCallbacks.computeIfAbsent(url, k -> new ArrayList<>()).add(callback);
            }
        }

        // Start a new download
        executor.execute(new DownloadTask(url, localFile, RemoteResourceStore.Kind.IMAGE));
    }

    /**
     * Pre-load a batch of images into the cache (fire and forget).
     * Useful for warming the cache before a user visits a mode.
     */
    public void preload(@NonNull Iterable<String> urls, @NonNull String configVersion) {
        for (String url : urls) {
            download(url, configVersion, null);
        }
    }

    /**
     * Pre-load a batch of videos into the cache (fire and forget).
     * Useful for warming the cache before a user visits a step with video.
     */
    public void preloadVideo(@NonNull Iterable<String> urls, @NonNull String configVersion) {
        for (String url : urls) {
            downloadVideo(url, configVersion, null);
        }
    }

    /**
     * Check if a URL's image is already cached locally.
     */
    public boolean isCached(@NonNull String url, @NonNull String configVersion) {
        return store.isImageCached(url, configVersion);
    }

    /**
     * Get the local file path for a URL, without downloading.
     * Returns null if not cached.
     */
    @Nullable
    public File getCachedFile(@NonNull String url, @NonNull String configVersion) {
        File file = store.getImageFile(url, configVersion);
        return file.exists() ? file : null;
    }

    // =====================================================================
    // Public API — Video
    // =====================================================================

    /**
     * Download a video from the given URL and cache it locally under the versioned path.
     * <p>
     * Same semantics as {@link #download} but for video (MP4) resources.
     *
     * @param url           the video URL (http/https/file)
     * @param configVersion the config version for cache path resolution
     * @param callback      receives the local file on success, error, or progress
     */
    public void downloadVideo(@NonNull String url, @NonNull String configVersion,
                              @Nullable Callback callback) {
        File localFile = store.getVideoFile(url, configVersion);

        // Already cached — return immediately
        if (localFile.exists()) {
            Log.d(TAG, "Video cache hit: " + url);
            store.touchCached(localFile);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(localFile));
            }
            return;
        }

        // Already in flight — register callback for notification
        synchronized (pendingCallbacks) {
            if (downloading.contains(url)) {
                Log.d(TAG, "Video already downloading, registering listener: " + url);
                if (callback != null) {
                    pendingCallbacks.computeIfAbsent(url, k -> new ArrayList<>()).add(callback);
                }
                return;
            }
            downloading.add(url);
            if (callback != null) {
                pendingCallbacks.computeIfAbsent(url, k -> new ArrayList<>()).add(callback);
            }
        }

        // Start a new video download
        executor.execute(new DownloadTask(url, localFile, RemoteResourceStore.Kind.VIDEO));
    }

    /**
     * Check if a URL's video is already cached locally.
     */
    public boolean isVideoCached(@NonNull String url, @NonNull String configVersion) {
        return store.isVideoCached(url, configVersion);
    }

    /**
     * Get the local file path for a video URL, without downloading.
     * Returns null if not cached.
     */
    @Nullable
    public File getCachedVideoFile(@NonNull String url, @NonNull String configVersion) {
        File file = store.getVideoFile(url, configVersion);
        return file.exists() ? file : null;
    }

    // =====================================================================
    // Public API — Cache management
    // =====================================================================

    /**
     * Clear all cached resources (images + videos + index).
     * Delegates to {@link RemoteResourceStore#clearAll()}.
     */
    public void clearCache() {
        store.clearAll();
    }

    // =====================================================================
    // Internals — listener notification
    // =====================================================================

    /**
     * Notify all registered listeners for a URL and clean up tracking state.
     * Called on the download executor thread; posts callbacks to the main thread.
     */
    private void notifyAllListeners(@NonNull String url, @Nullable File file,
                                    @Nullable Exception error) {
        List<Callback> callbacks;
        synchronized (pendingCallbacks) {
            callbacks = pendingCallbacks.remove(url);
            downloading.remove(url);
        }
        if (callbacks == null || callbacks.isEmpty()) return;

        if (error != null) {
            for (Callback cb : callbacks) {
                mainHandler.post(() -> cb.onError(error));
            }
        } else if (file != null) {
            for (Callback cb : callbacks) {
                mainHandler.post(() -> cb.onSuccess(file));
            }
        }
    }

    // =====================================================================
    // Internals — HTTP download with retry
    // =====================================================================

    /**
     * Perform the actual HTTP download with retry logic.
     * Downloads to a temp file first, then atomically renames to the destination.
     *
     * @return total bytes downloaded
     * @throws IOException if all retry attempts fail
     */
    private long doDownload(@NonNull String url, @NonNull File destFile,
                            @Nullable Callback progressCallback) throws IOException {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            URLConnection conn = null;
            try {
                URL target = new URL(url);
                conn = target.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                // HTTP-specific checks (only for http/https connections)
                if (conn instanceof HttpURLConnection) {
                    HttpURLConnection httpConn = (HttpURLConnection) conn;
                    httpConn.setRequestMethod("GET");
                    httpConn.setInstanceFollowRedirects(true);
                    int code = httpConn.getResponseCode();
                    if (code != 200) {
                        throw new IOException("HTTP " + code + " for " + url);
                    }
                }

                long totalBytes = conn.getContentLength(); // -1 if unknown

                // Download to a temp file first, then rename (atomic swap)
                File tempFile = new File(destFile.getParentFile(),
                        destFile.getName() + ".download");
                // Ensure parent directory exists
                File parent = tempFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    // noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }

                long downloaded = 0;
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        downloaded += read;
                        // Report progress on main thread
                        if (progressCallback != null) {
                            final long d = downloaded, t = totalBytes;
                            mainHandler.post(() -> progressCallback.onProgress(d, t));
                        }
                    }
                    fos.flush();
                }

                // Atomic rename
                if (tempFile.renameTo(destFile)) {
                    Log.d(TAG, "Downloaded: " + url + " (" + destFile.length() + " bytes)");
                    return downloaded;
                } else {
                    // Clean up temp file
                    // noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    throw new IOException("Failed to rename temp file: " + tempFile);
                }
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    Log.w(TAG, "Download attempt " + (attempt + 1) + "/" + (MAX_RETRIES + 1)
                            + " failed, retrying in " + delay + "ms: " + url, e);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    Log.w(TAG, "Download failed after " + (MAX_RETRIES + 1)
                            + " attempts: " + url, e);
                    throw e;
                }
            } finally {
                if (conn instanceof HttpURLConnection) {
                    ((HttpURLConnection) conn).disconnect();
                }
            }
        }
        // Unreachable, but compiler requires a return/throw
        throw new IOException("Unreachable");
    }

    // =====================================================================
    // Download Task (unified for images and videos)
    // =====================================================================

    /**
     * Unified download task for both images and videos.
     * Handles retry, progress, RemoteResourceStore notification, and multi-listener dispatch.
     */
    private final class DownloadTask implements Runnable {
        private final String url;
        private final File destFile;
        private final RemoteResourceStore.Kind kind;

        DownloadTask(@NonNull String url, @NonNull File destFile,
                     @NonNull RemoteResourceStore.Kind kind) {
            this.url = url;
            this.destFile = destFile;
            this.kind = kind;
        }

        @Override
        public void run() {
            // Collect all pending callbacks for progress reporting
            List<Callback> progressListeners;
            synchronized (pendingCallbacks) {
                List<Callback> list = pendingCallbacks.get(url);
                progressListeners = list != null ? new ArrayList<>(list) : null;
            }

            // Create a composite callback that fans out progress to all listeners
            Callback progressFanOut = progressListeners != null ? new Callback() {
                @Override
                public void onSuccess(@NonNull File localFile) { /* handled by notifyAll */ }

                @Override
                public void onError(@NonNull Exception error) { /* handled by notifyAll */ }

                @Override
                public void onProgress(long downloadedBytes, long totalBytes) {
                    for (Callback cb : progressListeners) {
                        cb.onProgress(downloadedBytes, totalBytes);
                    }
                }
            } : null;

            Exception lastError = null;
            try {
                long bytes = doDownload(url, destFile, progressFanOut);
                // Register in LRU index
                store.onResourceWritten(kind, destFile, bytes);
                // Notify all listeners of success
                notifyAllListeners(url, destFile, null);
            } catch (IOException e) {
                lastError = e;
                // Clean up partial file
                if (destFile.exists()) {
                    // noinspection ResultOfMethodCallIgnored
                    destFile.delete();
                }
                // Notify all listeners of failure
                notifyAllListeners(url, null, lastError);
            }
        }
    }

    // =====================================================================
    // Callback interface
    // =====================================================================

    /**
     * Callback for download operations.
     * All methods are called on the main thread.
     */
    public interface Callback {
        /** Called when download succeeds. */
        void onSuccess(@NonNull File localFile);

        /** Called when download fails (after all retries exhausted). */
        void onError(@NonNull Exception error);

        /**
         * Called periodically during download to report progress.
         *
         * @param downloadedBytes bytes downloaded so far
         * @param totalBytes      total size in bytes, or -1 if unknown (no Content-Length)
         */
        default void onProgress(long downloadedBytes, long totalBytes) {
            // Default no-op — override to receive progress updates
        }
    }
}
