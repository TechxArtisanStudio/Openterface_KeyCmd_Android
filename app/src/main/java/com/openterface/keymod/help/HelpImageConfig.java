package com.openterface.keymod.help;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Remote help-image configuration data model.
 * <p>
 * Deserialized from the server's JSON config that maps each tutorial mode to
 * image/GIF assets for its steps.
 *
 * <pre>
 * {
 *   "version": "1.0.0",
 *   "baseUrl": "https://cdn.openterface.com/help/",
 *   "modes": {
 *     "km_basic": {
 *       "steps": [
 *         { "id": "connection", "image": "basic/connection.gif" }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
public final class HelpImageConfig {

    /** Config version — bump to trigger re-download. */
    @NonNull
    public String version = "0";

    /** Base URL that all image paths resolve against. */
    @NonNull
    public String baseUrl = "";

    /** Per-mode step→image mappings (mode key → mode config). */
    @NonNull
    public Map<String, ModeConfig> modes = Collections.emptyMap();

    /**
     * Parse a JSON string into a {@link HelpImageConfig}.
     *
     * @return parsed config, or {@code null} on any error.
     */
    @Nullable
    public static HelpImageConfig fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            HelpImageConfig config = new Gson().fromJson(json, HelpImageConfig.class);
            if (config == null) return null;
            if (config.version == null) config.version = "0";
            if (config.baseUrl == null) config.baseUrl = "";
            if (config.modes == null) config.modes = Collections.emptyMap();
            return config;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * Find the image path for a specific mode + step.
     *
     * @param modeKey e.g. "km_basic", "km_pro"
     * @param stepId  e.g. "connection"
     * @return image path (relative to baseUrl), or null if not found
     */
    @Nullable
    public String getImagePath(@NonNull String modeKey, @NonNull String stepId) {
        ModeConfig mode = modes.get(modeKey);
        if (mode == null || mode.steps == null) return null;
        for (StepConfig step : mode.steps) {
            if (stepId.equals(step.id)) {
                if (step.image != null && !step.image.isEmpty()) return step.image;
                if (step.fallback != null && !step.fallback.isEmpty()) return step.fallback;
            }
        }
        return null;
    }

    /**
     * Build the full URL for a mode + step image.
     */
    @Nullable
    public String getImageUrl(@NonNull String modeKey, @NonNull String stepId) {
        String path = getImagePath(modeKey, stepId);
        if (path == null) return null;
        String base = baseUrl;
        if (!base.endsWith("/") && !path.startsWith("http")) {
            base = base + "/";
        }
        return base + path;
    }

    // ---- nested models ----

    /**
     * Configuration for a single tutorial mode (km_basic, km_pro, etc.).
     */
    public static final class ModeConfig {
        @NonNull
        public List<StepConfig> steps = Collections.emptyList();
    }

    /**
     * Configuration for a single tutorial step within a mode.
     */
    public static final class StepConfig {
        /** Step identifier matching ModeTutorialSteps. */
        @NonNull
        public String id = "";

        /** Primary image path (GIF preferred). */
        @Nullable
        public String image;

        /** Fallback static image if GIF fails. */
        @Nullable
        public String fallback;
    }
}
