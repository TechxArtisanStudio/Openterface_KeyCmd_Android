package com.openterface.keymod.gamepad;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Disk-backed thumbnails for gamepad layout presets (keyed by preset id + on-disk content signature).
 */
public final class GamepadLayoutPreviewCache {

    private static final String TAG = "GamepadLayoutPreview";
    private static final String DIR = "gamepad_preset_previews";

    private final android.content.Context appCtx;
    private final GamepadLayoutPresetRepository repository;
    private final File cacheDir;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    public GamepadLayoutPreviewCache(
            @NonNull android.content.Context context,
            @NonNull GamepadLayoutPresetRepository repository) {
        this.appCtx = context.getApplicationContext();
        this.repository = repository;
        this.cacheDir = new File(appCtx.getCacheDir(), DIR);
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            Log.w(TAG, "Could not mkdir preview cache");
        }
    }

    public interface BitmapCallback {
        void onReady(@Nullable Bitmap bitmap);
    }

    private File cacheFile(@NonNull String presetId, long contentSignature) {
        String safe = presetId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return new File(cacheDir, safe + "_" + Long.toHexString(contentSignature)
                + "_v" + GamepadLayoutPreviewRenderer.CACHE_FORMAT_VERSION + ".png");
    }

    /**
     * Invokes {@code callback} on the main thread. Bitmap may be large; caller displays in an {@code ImageView}.
     */
    public void loadPreview(@NonNull String presetId, long contentSignature, @NonNull BitmapCallback callback) {
        if (contentSignature == 0L) {
            main.post(() -> callback.onReady(null));
            return;
        }
        File f = cacheFile(presetId, contentSignature);
        if (f.isFile()) {
            executor.execute(() -> {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                main.post(() -> callback.onReady(bmp));
            });
            return;
        }
        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                GamepadLayoutPresetDocument doc = repository.loadDocument(presetId);
                if (doc != null) {
                    android.content.Context themed = GamepadLayoutPreviewRenderer.wrapPreviewContext(appCtx);
                    bmp = GamepadLayoutPreviewRenderer.render(themed, doc);
                    if (bmp != null) {
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            if (!bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)) {
                                Log.w(TAG, "compress failed " + presetId);
                            }
                        } catch (IOException e) {
                            Log.w(TAG, "write preview " + presetId, e);
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "render preview " + presetId, e);
            }
            Bitmap toDeliver = bmp;
            if (toDeliver == null && f.isFile()) {
                toDeliver = BitmapFactory.decodeFile(f.getAbsolutePath());
            }
            Bitmap finalBmp = toDeliver;
            main.post(() -> callback.onReady(finalBmp));
        });
    }

    public void invalidatePreset(@NonNull String presetId) {
        executor.execute(() -> {
            String prefix = presetId.replaceAll("[^a-zA-Z0-9_.-]", "_") + "_";
            File[] list = cacheDir.listFiles();
            if (list == null) {
                return;
            }
            for (File f : list) {
                if (f.isFile() && f.getName().startsWith(prefix)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        });
    }

    public void invalidateAll() {
        executor.execute(() -> {
            File[] list = cacheDir.listFiles();
            if (list == null) {
                return;
            }
            for (File f : list) {
                if (f.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        });
    }
}
