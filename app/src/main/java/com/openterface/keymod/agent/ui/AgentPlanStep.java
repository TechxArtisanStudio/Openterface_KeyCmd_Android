package com.openterface.keymod.agent.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/** One step in an Agent plan card (marketing demo). */
public final class AgentPlanStep {

    public enum Kind {
        TERMINAL,
        MACRO,
        HID
    }

    public final int index;
    @NonNull public final String title;
    @Nullable public final String subtitle;
    @NonNull public final Kind kind;

    public AgentPlanStep(int index, @NonNull String title, @Nullable String subtitle, @NonNull Kind kind) {
        this.index = index;
        this.title = title;
        this.subtitle = subtitle;
        this.kind = kind;
    }

    // ── Serialization (P2-20) ─────────────────────────────────────────────

    @NonNull
    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("index", index);
            obj.put("title", title);
            // put(key, null) removes the key in org.json — optString returns null on missing key
            obj.put("subtitle", subtitle);
            obj.put("kind", kind.name());
        } catch (JSONException e) {
            // Should not happen with simple types
        }
        return obj;
    }

    @NonNull
    public static AgentPlanStep fromJSONObject(@NonNull JSONObject obj) {
        int idx = obj.optInt("index", 0);
        String title = obj.optString("title", "Step");
        String subtitle = obj.optString("subtitle", null);
        Kind kind;
        try {
            kind = Kind.valueOf(obj.optString("kind", "TERMINAL"));
        } catch (IllegalArgumentException e) {
            kind = Kind.TERMINAL;
        }
        return new AgentPlanStep(idx, title, subtitle, kind);
    }
}
