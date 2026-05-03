package com.openterface.keymod;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import java.util.HashMap;
import java.util.Map;

/**
 * Localized display names for shortcuts whose persisted {@link Shortcut#name} stays English
 * (stable IDs in the default profile and migrations).
 */
public final class ShortcutUiStrings {

    private static final Map<String, Integer> DEFAULT_ID_TO_NAME_RES = new HashMap<>();

    static {
        DEFAULT_ID_TO_NAME_RES.put("default_select_all", R.string.default_shortcut_select_all);
        DEFAULT_ID_TO_NAME_RES.put("default_copy", R.string.default_shortcut_copy);
        DEFAULT_ID_TO_NAME_RES.put("default_cut", R.string.default_shortcut_cut);
        DEFAULT_ID_TO_NAME_RES.put("default_paste", R.string.default_shortcut_paste);
        DEFAULT_ID_TO_NAME_RES.put("default_save", R.string.default_shortcut_save);
        DEFAULT_ID_TO_NAME_RES.put("default_undo", R.string.default_shortcut_undo);
        DEFAULT_ID_TO_NAME_RES.put("default_redo", R.string.default_shortcut_redo);
        DEFAULT_ID_TO_NAME_RES.put("default_find", R.string.default_shortcut_find);
        DEFAULT_ID_TO_NAME_RES.put("default_new", R.string.default_shortcut_new);
        DEFAULT_ID_TO_NAME_RES.put("default_open", R.string.default_shortcut_open);
        DEFAULT_ID_TO_NAME_RES.put("default_print", R.string.default_shortcut_print);
        DEFAULT_ID_TO_NAME_RES.put("default_close", R.string.default_shortcut_close);
        DEFAULT_ID_TO_NAME_RES.put("default_replace", R.string.default_shortcut_replace);
    }

    private ShortcutUiStrings() {
    }

    @NonNull
    public static String shortcutDisplayName(@NonNull Context context, @NonNull Shortcut shortcut) {
        if (shortcut.id != null) {
            Integer resId = DEFAULT_ID_TO_NAME_RES.get(shortcut.id);
            if (resId != null) {
                return context.getString(resId);
            }
        }
        if (!TextUtils.isEmpty(shortcut.name)) {
            return shortcut.name.trim();
        }
        if (shortcut.label != null && !shortcut.label.trim().isEmpty()) {
            return shortcut.label.trim();
        }
        return context.getString(R.string.shortcut_row_name_fallback);
    }
}
