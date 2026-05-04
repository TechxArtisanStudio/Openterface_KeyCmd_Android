package com.openterface.keymod.preset;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Factory shells for non-deletable Rows 2–3 strip built-ins. Themed Unicode-heavy presets were
 * removed; only {@link #buildPersonalStripProfile()} (display name {@code Mine}) remains alongside {@code strip_default}
 * (created by {@link Rows23StripProfileManager} migration).
 */
public final class Rows23StripProfileBuiltins {

    private Rows23StripProfileBuiltins() {
    }

    /**
     * Same factory canvas as Default: no strip overrides until the user edits; page 2 row 2
     * paren / grave–tilde bindings are seeded by {@link Rows23StripProfileManager} on load.
     */
    @NonNull
    public static Rows23StripProfile buildPersonalStripProfile() {
        Rows23StripProfile p = new Rows23StripProfile();
        p.id = Rows23StripProfileConstants.PERSONAL_PROFILE_ID;
        p.name = "Mine";
        p.createdAt = System.currentTimeMillis();
        p.shortcuts = new ArrayList<>();
        p.slotMap = new HashMap<>();
        return p;
    }
}
