package com.openterface.terminal;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Terminal preferences: font size, terminal dimensions, connection settings.
 */
public class TerminalPrefs {

    private static final String PREFS = "terminal_prefs";
    private final SharedPreferences prefs;

    public TerminalPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public float getFontSize() {
        return prefs.getFloat("font_size", 12f);
    }

    public void setFontSize(float size) {
        prefs.edit().putFloat("font_size", size).apply();
    }

    public int getTerminalRows() { return prefs.getInt("rows", 24); }
    public int getTerminalCols() { return prefs.getInt("cols", 80); }
    public int getScrollbackSize() { return prefs.getInt("scrollback", 2000); }

    public String getLastHost() { return prefs.getString("last_host", "192.168.11.1"); }
    public void setLastHost(String host) {
        prefs.edit().putString("last_host", host).apply();
    }

    public String getLastUsername() { return prefs.getString("last_user", "root"); }
    public void setLastUsername(String user) {
        prefs.edit().putString("last_user", user).apply();
    }

    public String getLastPassword() { return prefs.getString("last_password", ""); }
    public void setLastPassword(String password) {
        prefs.edit().putString("last_password", password).apply();
    }

    public int getColorScheme() { return prefs.getInt("color_scheme", 0); }
}
