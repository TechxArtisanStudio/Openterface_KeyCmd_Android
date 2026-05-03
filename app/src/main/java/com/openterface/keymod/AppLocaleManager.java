package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;

/**
 * Persists UI language and applies it via AppCompat per-app locales.
 */
public final class AppLocaleManager {

    public static final String PREF_APP_LOCALE = "app_locale";
    /** Persisted value: use system / per-app default resolution. */
    public static final String LOCALE_FOLLOW_SYSTEM = "system";

    private static final String LEGACY_LANGUAGE_INDEX = "language_index";
    private static final String[] LEGACY_INDEX_TO_TAG = {"en", "zh-CN", "es", "fr", "de", "ja"};

    private AppLocaleManager() {
    }

    /**
     * Maps legacy {@code language_index} to {@link #PREF_APP_LOCALE} once, then removes the old key.
     */
    public static void migrateFromLanguageIndexIfNeeded(SharedPreferences prefs) {
        if (prefs.contains(PREF_APP_LOCALE) || !prefs.contains(LEGACY_LANGUAGE_INDEX)) {
            return;
        }
        int idx = prefs.getInt(LEGACY_LANGUAGE_INDEX, 0);
        String tag = (idx >= 0 && idx < LEGACY_INDEX_TO_TAG.length)
                ? LEGACY_INDEX_TO_TAG[idx]
                : "en";
        prefs.edit()
                .putString(PREF_APP_LOCALE, tag)
                .remove(LEGACY_LANGUAGE_INDEX)
                .apply();
    }

    /**
     * Tag stored in prefs, or {@link #LOCALE_FOLLOW_SYSTEM} if unset (new installs).
     */
    public static String getPersistedLocaleTag(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        migrateFromLanguageIndexIfNeeded(prefs);
        migrateBareZhTagToZhCnIfNeeded(prefs);
        migrateZhTwToZhHkIfNeeded(prefs);
        return prefs.getString(PREF_APP_LOCALE, LOCALE_FOLLOW_SYSTEM);
    }

    public static void applyPersistedLocales(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        migrateFromLanguageIndexIfNeeded(prefs);
        migrateBareZhTagToZhCnIfNeeded(prefs);
        migrateZhTwToZhHkIfNeeded(prefs);
        String tag = prefs.getString(PREF_APP_LOCALE, LOCALE_FOLLOW_SYSTEM);
        applyLocaleTag(tag);
    }

    /**
     * Older builds stored {@code zh} for Chinese UI; map to {@code zh-CN} so it matches
     * {@link androidx.appcompat.app.AppCompatDelegate} resource resolution with {@code values-zh-rCN}.
     */
    private static void migrateBareZhTagToZhCnIfNeeded(SharedPreferences prefs) {
        if (!"zh".equals(prefs.getString(PREF_APP_LOCALE, ""))) {
            return;
        }
        prefs.edit().putString(PREF_APP_LOCALE, "zh-CN").apply();
    }

    /**
     * Traditional Chinese UI moved from Taiwan ({@code zh-TW}) to Hong Kong ({@code zh-HK}) so
     * resources resolve from {@code values-zh-rHK}.
     */
    private static void migrateZhTwToZhHkIfNeeded(SharedPreferences prefs) {
        if (!"zh-TW".equals(prefs.getString(PREF_APP_LOCALE, ""))) {
            return;
        }
        prefs.edit().putString(PREF_APP_LOCALE, "zh-HK").apply();
    }

    public static void applyLocaleTag(String tag) {
        if (LOCALE_FOLLOW_SYSTEM.equals(tag)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    public static void persistLocaleTag(Context context, String tag) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_APP_LOCALE, tag)
                .apply();
    }

    /**
     * Saves preference and updates application locales.
     * Uses {@link SharedPreferences.Editor#commit()} so the value is persisted before
     * {@link AppCompatDelegate#setApplicationLocales} triggers activity recreation (avoids races with {@code apply()}).
     * <p>
     * On API 32 and below, {@code setApplicationLocales} is most reliable once an {@link android.app.Activity}
     * exists; call {@link #applyPersistedLocales(Context)} from each {@code AppCompatActivity} before
     * {@code super.onCreate()} as well as from {@link android.app.Application#onCreate()}.
     */
    public static void persistAndApplyLocales(Context context, String tag) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_APP_LOCALE, tag)
                .commit();
        applyLocaleTag(tag);
    }
}
