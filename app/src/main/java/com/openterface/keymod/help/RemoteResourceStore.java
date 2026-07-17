package com.openterface.keymod.help;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified cache manager: versioned storage and LRU eviction for image and video resources.
 *
 * <p>Cache directory structure:
 * <pre>
 *   &lt;filesDir&gt;/help_resources/
 *     +-- index.json                                  &lt;-- LRU index
 *     +-- {version}/
 *          +-- images/   &lt;-- images/GIF
 *          +-- videos/   &lt;-- MP4 videos
 * </pre>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link HelpImageConfigManager}: downloads and version-manages the config JSON.</li>
 *   <li>{@link HelpImageDownloader}: performs HTTP downloads; after writing a resource,
 *       calls {@link #onResourceWritten(Kind, File, long)} to register it in the index.</li>
 *   <li>This class: only handles path resolution, cache directory management, LRU index
 *       maintenance, and version-based invalidation.</li>
 * </ul>
 *
 * <p>Threading model:
 * <ul>
 *   <li>Path computation / file-exists checks: lock-free, callable from any thread.</li>
 *   <li>Index load / update / persistence / eviction: all serialized to the
 *       {@link #ioExecutor} single-thread executor, guaranteeing concurrent writes never
 *       corrupt index.json.</li>
 *   <li>The {@link #index} field is {@code volatile}, used only for cross-thread read
 *       visibility; all modifications happen inside the single-thread executor.</li>
 * </ul>
 */
public final class RemoteResourceStore {

    private static final String TAG = "RemoteResourceStore";

    /** Root directory name under cacheDir. */
    private static final String ROOT_DIR = "help_resources";

    /** Index file name. */
    private static final String INDEX_FILE = "index.json";

    /** Image subdirectory name. */
    private static final String DIR_IMAGES = "images";

    /** Video subdirectory name. */
    private static final String DIR_VIDEOS = "videos";

    /** Default image cache limit: 100MB. */
    public static final long DEFAULT_IMAGE_MAX_BYTES = 100L * 1024 * 1024;

    /** Default video cache limit: 200MB. */
    public static final long DEFAULT_VIDEO_MAX_BYTES = 200L * 1024 * 1024;

    /** Ratio to fall back to after eviction is triggered. */
    private static final double EVICT_TARGET_RATIO = 0.8;

    /** Regex for illegal characters in URL-derived filenames (matches HelpImageDownloader). */
    private static final String FILENAME_SANITIZE_PATTERN = "[^a-zA-Z0-9._\\-]";

    private final File rootDir;
    private final File indexFile;
    private final long imageMaxBytes;
    private final long videoMaxBytes;

    /** Single-thread executor: serializes all index write operations (load/update/persist/evict). */
    private final ExecutorService ioExecutor;

    private final Gson gson;

    /**
     * In-memory index. volatile ensures cross-thread read visibility; all mutations happen
     * inside {@link #ioExecutor}, providing natural serialization without extra write locks.
     */
    private volatile CacheIndex index;

    /** Whether the index has been loaded from disk. volatile ensures cross-thread visibility. */
    private volatile boolean indexLoaded;

    /** Monitor lock used for first-time index loading. */
    private final Object loadLock = new Object();

    @Nullable
    private static RemoteResourceStore instance;

    /**
     * Resource kind.
     */
    public enum Kind {
        IMAGE,
        VIDEO
    }

    private RemoteResourceStore(Context context, long imageMaxBytes, long videoMaxBytes) {
        Context appContext = context.getApplicationContext();
        this.rootDir = new File(appContext.getCacheDir(), ROOT_DIR);
        this.indexFile = new File(rootDir, INDEX_FILE);
        this.imageMaxBytes = imageMaxBytes;
        this.videoMaxBytes = videoMaxBytes;
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RemoteResourceStore-IO");
            t.setDaemon(true);
            return t;
        });
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Lazy-load index on first access
        ensureIndexLoaded();
    }

    /**
     * Singleton accessor (uses default limits: image 100MB / video 200MB).
     */
    @NonNull
    public static synchronized RemoteResourceStore getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new RemoteResourceStore(
                    context, DEFAULT_IMAGE_MAX_BYTES, DEFAULT_VIDEO_MAX_BYTES);
        }
        return instance;
    }

    /**
     * Singleton accessor with custom limits (test only).
     * Note: since this is a singleton, subsequent calls reuse the previously set limits.
     */
    @VisibleForTesting
    @NonNull
    public static synchronized RemoteResourceStore getInstanceForTest(
            @NonNull Context context, long imageMaxBytes, long videoMaxBytes) {
        if (instance != null) {
            // Allow rebuild during tests to switch limits
            instance.ioExecutor.shutdownNow();
        }
        instance = new RemoteResourceStore(context, imageMaxBytes, videoMaxBytes);
        return instance;
    }

    @VisibleForTesting
    static synchronized void resetForTest() {
        if (instance != null) {
            instance.ioExecutor.shutdownNow();
            instance = null;
        }
    }

    /**
     * Wait for all pending IO tasks to complete (test utility).
     * Submits a sentinel task to the single-thread executor and blocks until it finishes,
     * guaranteeing all previously submitted tasks have executed.
     */
    @VisibleForTesting
    public void awaitPendingForTest() {
        try {
            ioExecutor.submit(() -> { /* barrier */ }).get();
        } catch (Exception e) {
            // Ignore interrupts or execution exceptions; used for synchronization only
        }
    }

    // =====================================================================
    // Public API -- paths and queries (lock-free, callable from any thread)
    // =====================================================================

    /**
     * Get the image cache path for a URL under the given config version.
     * <p>The path may not exist yet; the caller should invoke
     * {@link #onResourceWritten(Kind, File, long)} after writing to it.
     */
    @NonNull
    public File getImageFile(@NonNull String url, @NonNull String configVersion) {
        return new File(versionedDir(configVersion, DIR_IMAGES), sanitizeFileName(url));
    }

    /**
     * Get the video cache path for a URL under the given config version.
     */
    @NonNull
    public File getVideoFile(@NonNull String url, @NonNull String configVersion) {
        return new File(versionedDir(configVersion, DIR_VIDEOS), sanitizeFileName(url));
    }

    /**
     * Check whether the given URL is cached as an image under the current config version.
     */
    public boolean isImageCached(@NonNull String url, @NonNull String configVersion) {
        return getImageFile(url, configVersion).isFile();
    }

    /**
     * Check whether the given URL is cached as a video under the current config version.
     */
    public boolean isVideoCached(@NonNull String url, @NonNull String configVersion) {
        return getVideoFile(url, configVersion).isFile();
    }

    // =====================================================================
    // Public API -- index maintenance (async, serialized to single-thread executor)
    // =====================================================================

    /**
     * Notify that a resource file has been successfully written to disk, so it should be
     * registered in the LRU index and trigger an eviction check.
     *
     * @param kind      IMAGE or VIDEO
     * @param file      the written file (must be under {@link #rootDir})
     * @param sizeBytes file size in bytes
     */
    public void onResourceWritten(
            @NonNull Kind kind, @NonNull File file, long sizeBytes) {
        ioExecutor.execute(() -> {
            ensureIndexLoadedLocked();
            CacheIndex idx = index;
            if (idx == null) return;

            String relativePath = relativize(file);
            if (relativePath == null) {
                Log.w(TAG, "onResourceWritten: file outside rootDir, ignored: " + file);
                return;
            }

            CacheEntry entry = findEntryByUrl(idx, relativePath);
            if (entry == null) {
                entry = new CacheEntry();
                entry.path = relativePath;
                entry.kind = kind.name();
                entry.version = extractVersion(relativePath);
                idx.entries.add(entry);
            }
            entry.size = sizeBytes;
            entry.lastAccess = System.currentTimeMillis();

            persistIndexLocked(idx);
            evictIfNeededLocked(idx);
        });
    }

    /**
     * Touch a cached resource (update lastAccess for LRU ordering).
     * The caller should invoke this method after successfully reading a cached file.
     */
    public void touchCached(@NonNull File file) {
        ioExecutor.execute(() -> {
            ensureIndexLoadedLocked();
            CacheIndex idx = index;
            if (idx == null) return;
            String relativePath = relativize(file);
            if (relativePath == null) return;
            CacheEntry entry = findEntryByPath(idx, relativePath);
            if (entry != null) {
                entry.lastAccess = System.currentTimeMillis();
                persistIndexLocked(idx);
            }
        });
    }

    /**
     * Clean up resource directories for non-current versions to reclaim disk space.
     * <p>Resources for the current version are preserved; all other version directories
     * are deleted recursively. Index entries for non-current versions are also cleared.
     *
     * @param currentVersion the active help_config.json version
     */
    public void invalidateOldVersions(@NonNull String currentVersion) {
        ioExecutor.execute(() -> {
            ensureIndexLoadedLocked();
            if (!rootDir.isDirectory()) return;

            File[] versionDirs = rootDir.listFiles(File::isDirectory);
            if (versionDirs == null) return;

            for (File versionDir : versionDirs) {
                String dirName = versionDir.getName();
                // Skip non-version entries (index.json is a file, won't appear here)
                if (dirName.equals(currentVersion)) continue;
                deleteRecursive(versionDir);
            }

            // Synchronize the index cleanup
            CacheIndex idx = index;
            if (idx != null) {
                Iterator<CacheEntry> it = idx.entries.iterator();
                while (it.hasNext()) {
                    CacheEntry e = it.next();
                    if (!currentVersion.equals(e.version)) {
                        it.remove();
                    }
                }
                recomputeTotalsLocked(idx);
                persistIndexLocked(idx);
            }
        });
    }

    /**
     * Clear all cached data: resource files and index.json.
     * After this call the in-memory index is also reset to empty.
     */
    public void clearAll() {
        ioExecutor.execute(() -> {
            if (rootDir.exists()) {
                deleteRecursive(rootDir);
            }
            synchronized (loadLock) {
                index = new CacheIndex();
                indexLoaded = true;
            }
        });
    }

    /**
     * Total cache size (images + videos) in bytes.
     * Computed from index entries and returned immediately (no disk stat wait).
     */
    public long getTotalSize() {
        return getImageTotalSize() + getVideoTotalSize();
    }

    /** Current total image cache size in bytes (visible for testing). */
    @VisibleForTesting
    public long getImageTotalSize() {
        CacheIndex idx = index;
        if (idx == null) return 0;
        long total = 0;
        for (CacheEntry e : idx.entries) {
            if (Kind.IMAGE.name().equals(e.kind)) total += e.size;
        }
        return total;
    }

    /** Current total video cache size in bytes (visible for testing). */
    @VisibleForTesting
    public long getVideoTotalSize() {
        CacheIndex idx = index;
        if (idx == null) return 0;
        long total = 0;
        for (CacheEntry e : idx.entries) {
            if (Kind.VIDEO.name().equals(e.kind)) total += e.size;
        }
        return total;
    }

    /** Current number of index entries (visible for testing). */
    @VisibleForTesting
    public int getEntryCount() {
        CacheIndex idx = index;
        return idx == null ? 0 : idx.entries.size();
    }

    // =====================================================================
    // Internal: path construction
    // =====================================================================

    /**
     * Return the {rootDir}/{version}/{subDir}/ directory; create it if it doesn't exist.
     */
    @NonNull
    private File versionedDir(@NonNull String version, @NonNull String subDir) {
        File dir = new File(new File(rootDir, version), subDir);
        if (!dir.exists()) {
            // mkdirs may fail under concurrent creation; check again
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Convert a URL to a valid filename.
     * Uses the same sanitization rules as {@link HelpImageDownloader} to ensure the same
     * URL maps to the same filename in both places.
     */
    @NonNull
    static String sanitizeFileName(@NonNull String url) {
        String fileName = url;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            fileName = url.substring(lastSlash + 1);
        }
        return fileName.replaceAll(FILENAME_SANITIZE_PATTERN, "_");
    }

    /**
     * Convert an absolute path to a rootDir-relative path string (using "/" separators).
     */
    @Nullable
    private String relativize(@NonNull File file) {
        String rootPath = rootDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) return null;
        String relative = filePath.substring(rootPath.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        // Normalize to "/"
        return relative.replace(File.separatorChar, '/');
    }

    /**
     * Extract the version (first segment) from a relative path.
     * For example "v1.1.0/images/foo.gif" returns "v1.1.0".
     */
    @Nullable
    private static String extractVersion(@NonNull String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash <= 0) return null;
        return relativePath.substring(0, slash);
    }

    // =====================================================================
    // Internal: index load / persistence (only called inside ioExecutor, or under lock for first load)
    // =====================================================================

    /**
     * Lazy-load the index. On first call, synchronously acquires the lock and loads from disk;
     * subsequent calls return immediately.
     */
    private void ensureIndexLoaded() {
        if (indexLoaded) return;
        synchronized (loadLock) {
            if (indexLoaded) return;
            index = loadIndexFromDisk();
            indexLoaded = true;
        }
    }

    /**
     * Version used inside ioExecutor: ensures the index is loaded.
     */
    private void ensureIndexLoadedLocked() {
        ensureIndexLoaded();
    }

    @NonNull
    private CacheIndex loadIndexFromDisk() {
        if (!indexFile.isFile()) {
            return new CacheIndex();
        }
        try (Reader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
            CacheIndex loaded = gson.fromJson(reader, CacheIndex.class);
            if (loaded == null) return new CacheIndex();
            if (loaded.entries == null) loaded.entries = new ArrayList<>();
            return loaded;
        } catch (IOException e) {
            Log.w(TAG, "Failed to load index, starting fresh", e);
            return new CacheIndex();
        }
    }

    private void persistIndexLocked(@NonNull CacheIndex idx) {
        File parent = indexFile.getParentFile();
        if (parent != null && !parent.exists()) {
            // noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        // Write to a temp file first, then rename (atomic -- prevents a mid-write crash
        // from corrupting the index)
        File tempFile = new File(parent, INDEX_FILE + ".tmp");
        try (Writer writer = new java.io.OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            gson.toJson(idx, writer);
            writer.flush();
        } catch (IOException e) {
            Log.w(TAG, "Failed to persist index", e);
            // noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            return;
        }
        // renameTo is atomic on the same filesystem
        if (!tempFile.renameTo(indexFile)) {
            Log.w(TAG, "Failed to rename temp index to index.json");
            // noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    // =====================================================================
    // Internal: LRU eviction
    // =====================================================================

    private void evictIfNeededLocked(@NonNull CacheIndex idx) {
        boolean evicted = false;
        evicted |= evictKindLocked(idx, Kind.IMAGE, imageMaxBytes);
        evicted |= evictKindLocked(idx, Kind.VIDEO, videoMaxBytes);
        if (evicted) {
            recomputeTotalsLocked(idx);
            persistIndexLocked(idx);
            // Remove entries whose physical files are missing (whole directories may
            // already have been handled by invalidateOldVersions, but individual files
            // could be lost for other reasons). Keeps index and disk eventually consistent.
            deleteEntriesMissingOnDisk(idx);
        }
    }

    /**
     * Perform LRU eviction for a single kind: when the total exceeds the limit, delete
     * entries sorted by lastAccess ascending until below 80% of the limit.
     *
     * @return true if any eviction occurred
     */
    private boolean evictKindLocked(
            @NonNull CacheIndex idx, @NonNull Kind kind, long maxBytes) {
        long total = sumByKind(idx, kind);
        if (total <= maxBytes) return false;

        // Collect entries of this kind, sorted by lastAccess ascending
        List<CacheEntry> candidates = new ArrayList<>();
        for (CacheEntry e : idx.entries) {
            if (kind.name().equals(e.kind)) candidates.add(e);
        }
        Collections.sort(candidates, Comparator.comparingLong(e -> e.lastAccess));

        long target = (long) (maxBytes * EVICT_TARGET_RATIO);
        boolean evicted = false;
        Iterator<CacheEntry> it = candidates.iterator();
        while (it.hasNext() && total > target) {
            CacheEntry victim = it.next();
            File victimFile = new File(rootDir, victim.path.replace('/', File.separatorChar));
            if (victimFile.isFile()) {
                // noinspection ResultOfMethodCallIgnored
                victimFile.delete();
            }
            idx.entries.remove(victim);
            total -= victim.size;
            evicted = true;
        }
        return evicted;
    }

    private long sumByKind(@NonNull CacheIndex idx, @NonNull Kind kind) {
        long total = 0;
        String kindName = kind.name();
        for (CacheEntry e : idx.entries) {
            if (kindName.equals(e.kind)) total += e.size;
        }
        return total;
    }

    private void recomputeTotalsLocked(@NonNull CacheIndex idx) {
        idx.totalImageSize = sumByKind(idx, Kind.IMAGE);
        idx.totalVideoSize = sumByKind(idx, Kind.VIDEO);
    }

    /**
     * Remove index entries whose physical files no longer exist (only called after eviction,
     * to avoid unnecessary stat calls).
     */
    private void deleteEntriesMissingOnDisk(@NonNull CacheIndex idx) {
        Iterator<CacheEntry> it = idx.entries.iterator();
        while (it.hasNext()) {
            CacheEntry e = it.next();
            File f = new File(rootDir, e.path.replace('/', File.separatorChar));
            if (!f.isFile()) {
                it.remove();
            }
        }
    }

    // =====================================================================
    // Internal: directory / index utilities
    // =====================================================================

    @Nullable
    private static CacheEntry findEntryByUrl(
            @NonNull CacheIndex idx, @NonNull String relativePath) {
        // In this implementation "url" uses path as the primary key (URL -> filename
        // is deterministic). The url field is kept only for future extensibility.
        return findEntryByPath(idx, relativePath);
    }

    @Nullable
    private static CacheEntry findEntryByPath(
            @NonNull CacheIndex idx, @NonNull String relativePath) {
        for (CacheEntry e : idx.entries) {
            if (relativePath.equals(e.path)) return e;
        }
        return null;
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private static void deleteRecursive(@NonNull File file) {
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

    // =====================================================================
    // Index data structures (Gson serialization)
    // =====================================================================

    /**
     * In-memory representation of the cache index file.
     */
    static final class CacheIndex {
        @SerializedName("entries")
        @NonNull
        List<CacheEntry> entries = new ArrayList<>();

        /** Aggregate fields for fast lookup; non-authoritative, can be recomputed from entries. */
        @SerializedName("totalImageSize")
        long totalImageSize;

        @SerializedName("totalVideoSize")
        long totalVideoSize;
    }

    /**
     * Metadata for a single cache entry.
     */
    static final class CacheEntry {
        /** Path relative to rootDir, using "/" separators. */
        @SerializedName("path")
        @NonNull
        String path = "";

        /** Resource kind: IMAGE or VIDEO. */
        @SerializedName("kind")
        @NonNull
        String kind = "";

        /** File size in bytes. */
        @SerializedName("size")
        long size;

        /** Last access time in milliseconds (System.currentTimeMillis()). */
        @SerializedName("lastAccess")
        long lastAccess;

        /** Config version this entry belongs to. */
        @SerializedName("version")
        @Nullable
        String version;
    }
}
