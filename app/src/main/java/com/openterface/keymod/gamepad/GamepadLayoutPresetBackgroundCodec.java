package com.openterface.keymod.gamepad;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Embeds gamepad canvas background bytes in preset JSON for sharing, and materializes embeds to files on import.
 */
public final class GamepadLayoutPresetBackgroundCodec {

    private GamepadLayoutPresetBackgroundCodec() {}

    @NonNull
    public static String bgFileNameForPresetId(@Nullable String presetId) {
        if (presetId == null || presetId.isEmpty()) {
            return "gamepad_bg_inline.png";
        }
        String safe = presetId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "gamepad_bg_" + safe + ".png";
    }

    public static void clearEmbedFields(@Nullable GamepadLayoutPresetDocument.LayoutGlobals L) {
        if (L == null) {
            return;
        }
        L.backgroundImageEncoding = null;
        L.backgroundImageMediaType = null;
        L.backgroundImageData = null;
    }

    public static boolean hasCompleteEmbed(@Nullable GamepadLayoutPresetDocument.LayoutGlobals L) {
        if (L == null) {
            return false;
        }
        String enc = trimToNull(L.backgroundImageEncoding);
        String mime = trimToNull(L.backgroundImageMediaType);
        String data = stripBase64Whitespace(L.backgroundImageData);
        return enc != null && mime != null && data != null && !data.isEmpty();
    }

    /**
     * If JSON embed is present, decode, verify magic bytes, write under {@code filesDir}, and set
     * {@code layout.backgroundImageFile}. Always clears embed fields afterward (complete or not).
     */
    public static void prepareForPersistence(@NonNull Context context, @NonNull GamepadLayoutPresetDocument doc) {
        prepareForPersistence(context.getFilesDir(), doc);
    }

    /**
     * Same as {@link #prepareForPersistence(Context, GamepadLayoutPresetDocument)}; package-visible for unit tests.
     */
    static void prepareForPersistence(@NonNull File filesDir, @NonNull GamepadLayoutPresetDocument doc) {
        GamepadLayoutPresetDocument.LayoutGlobals L = doc.layout;
        if (L == null) {
            return;
        }
        if (!hasCompleteEmbed(L)) {
            clearEmbedFields(L);
            return;
        }
        String presetId = doc.meta != null ? doc.meta.id : null;
        String outName = bgFileNameForPresetId(presetId);
        byte[] decoded = decodeEmbeddedBytesOrThrow(L);
        File out = new File(filesDir, outName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(decoded);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write background image: " + e.getMessage());
        }
        L.backgroundImageFile = outName;
        clearEmbedFields(L);
    }

    /**
     * Fills embed fields from an on-disk background file for export/share. Clears any prior embed first.
     *
     * @param backgroundBasenameOverride optional basename under {@code filesDir} (e.g. from prefs)
     */
    public static void injectEmbedForExport(
            @NonNull File filesDir,
            @NonNull GamepadLayoutPresetDocument doc,
            @Nullable String backgroundBasenameOverride) {
        GamepadLayoutPresetDocument.LayoutGlobals L = doc.layout;
        if (L == null) {
            return;
        }
        clearEmbedFields(L);
        String basename = trimToNull(backgroundBasenameOverride);
        if (basename == null) {
            basename = trimToNull(L.backgroundImageFile);
        }
        if (basename == null) {
            return;
        }
        if (basename.contains("..") || basename.contains(File.separator) || basename.contains("/")) {
            return;
        }
        File f = new File(filesDir, basename);
        if (!f.isFile()) {
            return;
        }
        long len = f.length();
        if (len <= 0 || len > GamepadLayoutPresetConstants.MAX_BACKGROUND_EMBED_DECODED_BYTES) {
            return;
        }
        if (len > Integer.MAX_VALUE - 8) {
            return;
        }
        byte[] raw = new byte[(int) len];
        try (FileInputStream in = new FileInputStream(f)) {
            int total = 0;
            while (total < raw.length) {
                int n = in.read(raw, total, raw.length - total);
                if (n < 0) {
                    return;
                }
                total += n;
            }
        } catch (IOException e) {
            return;
        }
        String mime = sniffMediaType(raw);
        if (mime == null) {
            return;
        }
        L.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        L.backgroundImageMediaType = mime;
        L.backgroundImageData = java.util.Base64.getEncoder().encodeToString(raw);
    }

    /** @see #injectEmbedForExport(File, GamepadLayoutPresetDocument, String) */
    public static void injectEmbedForExport(
            @NonNull Context context,
            @NonNull GamepadLayoutPresetDocument doc,
            @Nullable String backgroundBasenameOverride) {
        injectEmbedForExport(context.getFilesDir(), doc, backgroundBasenameOverride);
    }

    @Nullable
    private static String sniffMediaType(@NonNull byte[] data) {
        if (data.length >= 8
                && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_PNG;
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_WEBP;
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_JPEG;
        }
        return null;
    }

    private static byte[] decodeEmbeddedBytesOrThrow(GamepadLayoutPresetDocument.LayoutGlobals L) {
        String data = stripBase64Whitespace(L.backgroundImageData);
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Missing backgroundImageData");
        }
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid backgroundImageData base64");
        }
        if (decoded.length == 0 || decoded.length > GamepadLayoutPresetConstants.MAX_BACKGROUND_EMBED_DECODED_BYTES) {
            throw new IllegalArgumentException("backgroundImageData decoded size out of range");
        }
        String sniffed = sniffMediaType(decoded);
        if (sniffed == null) {
            throw new IllegalArgumentException("backgroundImageData is not a supported image");
        }
        String declared = trimToNull(L.backgroundImageMediaType);
        if (declared != null && !sniffed.equalsIgnoreCase(declared.trim())) {
            throw new IllegalArgumentException("backgroundImageMediaType does not match image bytes");
        }
        return decoded;
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String stripBase64Whitespace(@Nullable String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
