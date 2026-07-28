package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Macros Manager - Handles recording, playback, and management of key sequences
 * Matches iOS MacrosManager.swift functionality
 */
public class MacrosManager {

    private static final String TAG = "MacrosManager";
    private static final String PREFS_NAME = "MacrosPrefs";
    private static final String KEY_MACROS_LIST = "macros_list";
    private static final String KEY_RECORDING = "is_recording";
    private static final String KEY_PLAYING = "is_playing";
    private static volatile MacrosManager instance;

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<Macro> macros;
    private Macro currentRecordingMacro;
    private boolean isRecording;
    private boolean isPlaying;
    private MacrosListener listener;

    public static MacrosManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MacrosManager.class) {
                if (instance == null) {
                    instance = new MacrosManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public MacrosManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        macros = loadMacros();
        isRecording = false;
        isPlaying = false;
    }

    /**
     * Load macros from SharedPreferences
     */
    private List<Macro> loadMacros() {
        String json = prefs.getString(KEY_MACROS_LIST, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Macro>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Save macros to SharedPreferences
     */
    private void saveMacros() {
        String json = gson.toJson(macros);
        prefs.edit().putString(KEY_MACROS_LIST, json).apply();
        Log.v(TAG, "Saved " + macros.size() + " macros");
    }

    /**
     * Start recording a new macro
     */
    public void startRecording(String name) {
        if (isRecording) {
            Log.w(TAG, "Already recording a macro");
            return;
        }

        currentRecordingMacro = new Macro();
        currentRecordingMacro.name = name;
        currentRecordingMacro.id = System.currentTimeMillis();
        currentRecordingMacro.createdAt = System.currentTimeMillis();
        currentRecordingMacro.keyEvents = new ArrayList<>();
        isRecording = true;
        
        prefs.edit().putBoolean(KEY_RECORDING, true).apply();
        Log.v(TAG, "Started recording macro: " + name);
        
        if (listener != null) {
            listener.onRecordingStarted(currentRecordingMacro);
        }
    }

    /**
     * Stop recording and save the macro
     */
    public void stopRecording() {
        if (!isRecording || currentRecordingMacro == null) {
            return;
        }

        isRecording = false;
        prefs.edit().putBoolean(KEY_RECORDING, false).apply();
        
        if (currentRecordingMacro.keyEvents != null && !currentRecordingMacro.keyEvents.isEmpty()) {
            // Calculate total duration
            long lastTime = currentRecordingMacro.keyEvents.get(currentRecordingMacro.keyEvents.size() - 1).timestamp;
            currentRecordingMacro.duration = lastTime;
            
            macros.add(currentRecordingMacro);
            saveMacros();
            Log.v(TAG, "Saved macro: " + currentRecordingMacro.name + " with " + 
                  currentRecordingMacro.keyEvents.size() + " events");
            
            if (listener != null) {
                listener.onMacroSaved(currentRecordingMacro);
            }
        } else {
            Log.w(TAG, "Discarded empty macro");
        }
        
        currentRecordingMacro = null;
    }

    /**
     * Record a key event during macro recording
     */
    public void recordKeyEvent(int keyCode, int modifiers) {
        if (!shouldRecordKeyEvent()) {
            return;
        }

        long timestamp = System.currentTimeMillis() - currentRecordingMacro.createdAt;
        KeyEvent event = new KeyEvent(keyCode, modifiers, timestamp);
        currentRecordingMacro.keyEvents.add(event);
        
        Log.v(TAG, "Recorded key event: keyCode=" + keyCode + ", timestamp=" + timestamp);
        
        if (listener != null) {
            listener.onKeyEventRecorded(event);
        }
    }

    /**
     * Recording should only capture user-originated key events.
     */
    public boolean shouldRecordKeyEvent() {
        return isRecording && !isPlaying && currentRecordingMacro != null;
    }

    /**
     * Play back a macro
     */
    public void playMacro(Macro macro, ConnectionManager connectionManager) {
        if (isPlaying) {
            Log.w(TAG, "Already playing a macro");
            return;
        }

        isPlaying = true;
        prefs.edit().putBoolean(KEY_PLAYING, true).apply();
        Log.v(TAG, "Playing macro: " + macro.name);
        
        if (listener != null) {
            listener.onPlaybackStarted(macro);
        }

        // Play back key events with timing
        playMacroEvents(macro, connectionManager, 0);
    }

    private void playMacroEvents(final Macro macro, final ConnectionManager connectionManager, 
                                  final int index) {
        if (!isPlaying || index >= macro.keyEvents.size()) {
            // Playback complete
            isPlaying = false;
            prefs.edit().putBoolean(KEY_PLAYING, false).apply();
            Log.v(TAG, "Macro playback complete: " + macro.name);
            
            if (listener != null) {
                listener.onPlaybackComplete(macro);
            }
            return;
        }

        final KeyEvent event = macro.keyEvents.get(index);
        
        // Schedule next event based on timing
        long delay;
        if (index == 0) {
            delay = event.timestamp;
        } else {
            long prevTimestamp = macro.keyEvents.get(index - 1).timestamp;
            delay = event.timestamp - prevTimestamp;
        }

        // Ensure minimum spacing between macro events.
        // BLE links can drop very short press/release sequences.
        delay = Math.max(delay, 80);

        final long finalDelay = delay;
        if (connectionManager != null && connectionManager.isConnected()) {
            connectionManager.sendKeyEvent(event.modifiers, event.keyCode);
            Log.v(TAG, "Macro send key: keyCode=" + event.keyCode + ", modifiers=" + event.modifiers);
            
            // Hold key a little longer to improve combo reliability (e.g., Ctrl+A).
            final long releaseDelay = finalDelay;
            new android.os.Handler().postDelayed(() -> {
                if (isPlaying && connectionManager.isConnected()) {
                    connectionManager.sendKeyRelease();
                    Log.v(TAG, "Macro sent key release");
                    
                    // Schedule next event
                    new android.os.Handler().postDelayed(() -> {
                        playMacroEvents(macro, connectionManager, index + 1);
                    }, releaseDelay);
                } else if (isPlaying) {
                    // Connection lost during playback
                    Log.w(TAG, "Connection lost during macro playback at index " + index);
                    isPlaying = false;
                    prefs.edit().putBoolean(KEY_PLAYING, false).apply();
                }
            }, 120);
        } else {
            // Connection lost or not available
            Log.w(TAG, "Cannot play macro: connectionManager=" + (connectionManager != null) 
                    + ", isConnected=" + (connectionManager != null ? connectionManager.isConnected() : false));
            isPlaying = false;
            prefs.edit().putBoolean(KEY_PLAYING, false).apply();
        }
    }

    /**
     * Stop macro playback
     */
    public void stopPlayback() {
        isPlaying = false;
        prefs.edit().putBoolean(KEY_PLAYING, false).apply();
        Log.v(TAG, "Stopped macro playback");
        
        if (listener != null) {
            listener.onPlaybackStopped();
        }
    }

    /**
     * Delete a macro
     */
    public void deleteMacro(Macro macro) {
        macros.remove(macro);
        saveMacros();
        Log.v(TAG, "Deleted macro: " + macro.name);
        
        if (listener != null) {
            listener.onMacroDeleted(macro);
        }
    }

    /**
     * Update an existing macro name.
     */
    public boolean renameMacro(long macroId, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }

        for (Macro macro : macros) {
            if (macro.id == macroId) {
                macro.name = newName.trim();
                saveMacros();
                return true;
            }
        }
        return false;
    }

    /**
     * Replace key events for an existing macro and recalculate duration.
     */
    public boolean updateMacroEvents(long macroId, List<KeyEvent> newEvents) {
        if (newEvents == null) {
            return false;
        }

        for (Macro macro : macros) {
            if (macro.id == macroId) {
                macro.keyEvents = new ArrayList<>(newEvents);
                if (!macro.keyEvents.isEmpty()) {
                    macro.duration = macro.keyEvents.get(macro.keyEvents.size() - 1).timestamp;
                } else {
                    macro.duration = 0;
                }
                saveMacros();
                return true;
            }
        }

        return false;
    }

    /**
     * Create a new macro directly from editor content.
     */
    public Macro createMacro(String name, List<KeyEvent> events) {
        return createMacro(name, "", 100, false, 0L, 0, 0L, events);
    }

    /**
     * Create a new macro from the editor fields.
     */
    public Macro createMacro(String name,
                             String data,
                             int intervalMs,
                             boolean isScheduled,
                             long scheduledAtMillis,
                             int repeatCount,
                             long repeatIntervalSeconds,
                             List<KeyEvent> events) {
        Macro macro = new Macro();
        macro.id = System.currentTimeMillis();
        macro.name = name;
        macro.createdAt = System.currentTimeMillis();
        macro.data = data;
        macro.intervalMs = intervalMs;
        macro.isScheduled = isScheduled;
        macro.scheduledAtMillis = scheduledAtMillis;
        macro.repeatCount = repeatCount;
        macro.repeatIntervalSeconds = repeatIntervalSeconds;
        macro.keyEvents = events != null ? new ArrayList<>(events) : new ArrayList<>();
        macro.duration = macro.keyEvents.isEmpty() ? 0 : macro.keyEvents.get(macro.keyEvents.size() - 1).timestamp;

        macros.add(macro);
        saveMacros();
        return macro;
    }

    /**
     * Update name and key events of an existing macro.
     */
    public boolean updateMacro(long macroId, String name, List<KeyEvent> events) {
        return updateMacro(macroId, name, "", 100, false, 0L, 0, 0L, events);
    }

    /**
     * Update all editor fields of an existing macro.
     */
    public boolean updateMacro(long macroId,
                               String name,
                               String data,
                               int intervalMs,
                               boolean isScheduled,
                               long scheduledAtMillis,
                               int repeatCount,
                               long repeatIntervalSeconds,
                               List<KeyEvent> events) {
        if (name == null || name.trim().isEmpty() || events == null) {
            return false;
        }

        for (Macro macro : macros) {
            if (macro.id == macroId) {
                macro.name = name.trim();
                macro.data = data;
                macro.intervalMs = intervalMs;
                macro.isScheduled = isScheduled;
                macro.scheduledAtMillis = scheduledAtMillis;
                macro.repeatCount = repeatCount;
                macro.repeatIntervalSeconds = repeatIntervalSeconds;
                macro.keyEvents = new ArrayList<>(events);
                macro.duration = macro.keyEvents.isEmpty() ? 0 : macro.keyEvents.get(macro.keyEvents.size() - 1).timestamp;
                saveMacros();
                return true;
            }
        }

        return false;
    }

    /**
     * Get all macros
     */
    public List<Macro> getAllMacros() {
        return new ArrayList<>(macros);
    }

    /**
     * Get macro by ID
     */
    public Macro getMacroById(long id) {
        for (Macro macro : macros) {
            if (macro.id == id) {
                return macro;
            }
        }
        return null;
    }

    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Get current recording macro
     */
    public Macro getCurrentRecordingMacro() {
        return currentRecordingMacro;
    }

    /**
     * Set listener for macro events
     */
    public void setListener(MacrosListener listener) {
        this.listener = listener;
    }

    /**
     * Get the current listener for macro events.
     * Used by Agent executor to save/restore listener during playback.
     */
    @Nullable
    public MacrosListener getListener() {
        return listener;
    }

    /**
     * Export macros to JSON string
     */
    public String exportMacros() {
        return gson.toJson(macros);
    }

    /**
     * Import macros from JSON string
     */
    public void importMacros(String json) {
        Type type = new TypeToken<List<Macro>>(){}.getType();
        List<Macro> importedMacros = gson.fromJson(json, type);
        
        if (importedMacros != null) {
            macros.addAll(importedMacros);
            saveMacros();
            Log.v(TAG, "Imported " + importedMacros.size() + " macros");
            
            if (listener != null) {
                listener.onMacrosImported(importedMacros);
            }
        }
    }

    /**
     * Clear all macros
     */
    public void clearAllMacros() {
        macros.clear();
        saveMacros();
        Log.v(TAG, "Cleared all macros");
        
        if (listener != null) {
            listener.onMacrosCleared();
        }
    }

    // Listener interface
    public interface MacrosListener {
        void onRecordingStarted(Macro macro);
        void onKeyEventRecorded(KeyEvent event);
        void onMacroSaved(Macro macro);
        void onPlaybackStarted(Macro macro);
        void onPlaybackComplete(Macro macro);
        void onPlaybackStopped();
        void onMacroDeleted(Macro macro);
        void onMacrosImported(List<Macro> macros);
        void onMacrosCleared();
    }

    // Data classes
    public static class Macro {
        public long id;
        public String name;
        public String data;
        public int intervalMs = 100;
        public boolean isScheduled = false;
        public long scheduledAtMillis = 0L;
        public int repeatCount = 0;
        public long repeatIntervalSeconds = 0L;
        public long createdAt;
        public long duration;
        public List<KeyEvent> keyEvents;

        public Macro() {}

        public int getKeyCount() {
            return keyEvents != null ? keyEvents.size() : 0;
        }

        public String getFormattedDuration() {
            long seconds = duration / 1000;
            long millis = duration % 1000;
            return String.format("%d.%03ds", seconds, millis);
        }
    }

    public static class KeyEvent {
        public int keyCode;
        public int modifiers;
        public long timestamp;

        public KeyEvent() {}

        public KeyEvent(int keyCode, int modifiers, long timestamp) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.timestamp = timestamp;
        }
    }
}
