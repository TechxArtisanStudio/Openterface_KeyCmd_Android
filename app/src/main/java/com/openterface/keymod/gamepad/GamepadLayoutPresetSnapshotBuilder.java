package com.openterface.keymod.gamepad;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openterface.keymod.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds export snapshot {@link GamepadLayoutPresetDocument} (uses inline layout JSON store).
 */
public final class GamepadLayoutPresetSnapshotBuilder {

    private GamepadLayoutPresetSnapshotBuilder() {}

    public static GamepadLayoutPresetDocument buildFrom(
            @NonNull Context context, String presetId, String displayName) {
        GamepadLayoutPresetDocument doc = GamepadLayoutDocumentStore.loadOrCreate(context);
        if (doc.meta == null) {
            doc.meta = new GamepadLayoutPresetDocument.Meta();
        }
        doc.meta.id = presetId != null ? presetId : UUID.randomUUID().toString();
        doc.meta.displayName = displayName != null ? displayName : "Preset";
        doc.meta.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;
        GamepadPresetExportCreator.stampMeta(context, doc.meta);
        return doc;
    }
}
