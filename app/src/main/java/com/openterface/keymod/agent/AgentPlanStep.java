package com.openterface.keymod.agent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
}
