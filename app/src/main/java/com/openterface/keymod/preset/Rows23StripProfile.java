package com.openterface.keymod.preset;

import com.openterface.keymod.ShortcutProfileManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Rows 2–3 strip profile: slot overrides and shortcut definitions referenced by those slots.
 */
@SuppressWarnings("unused")
public class Rows23StripProfile {

    public String id;
    public String name;
    public long createdAt;
    /** Keys {@link StripSlotMapStore#slotKey}; values shortcut ids in {@link #shortcuts}. */
    public Map<String, String> slotMap;
    public List<ShortcutProfileManager.Shortcut> shortcuts;

    public Rows23StripProfile() {
        slotMap = new HashMap<>();
        shortcuts = new ArrayList<>();
    }

    public Rows23StripProfile copyShell() {
        Rows23StripProfile c = new Rows23StripProfile();
        c.id = id;
        c.name = name;
        c.createdAt = createdAt;
        return c;
    }
}
