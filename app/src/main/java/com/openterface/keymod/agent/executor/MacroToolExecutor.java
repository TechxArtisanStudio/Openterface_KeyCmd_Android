package com.openterface.keymod.agent.executor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MacrosManager;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes macro steps by looking up and playing macros via {@link MacrosManager}.
 *
 * <p>Implements {@link AgentToolExecutor} for {@code kind="macro"} steps.
 * Macros are looked up by {@code step.macroId} (preferred) or by name matching
 * against {@code step.command} (fallback for LLM convenience).</p>
 *
 * <p>Playback completion is detected via {@link MacrosManager.MacrosListener#onPlaybackComplete}
 * using a {@link CountDownLatch} to convert the async callback to a blocking wait.
 * A 2-minute timeout prevents the executor from hanging indefinitely.</p>
 *
 * <p><b>Known limitation:</b> {@link MacrosManager} only supports a single listener slot.
 * This executor temporarily replaces the listener during playback and restores it afterward.
 * Events from unrelated macro operations during Agent playback may be missed by the original
 * listener.</p>
 */
public final class MacroToolExecutor implements AgentToolExecutor {

    private static final String TAG = "MacroToolExecutor";
    private static final int MAX_WAIT_MS = 120_000; // 2 min timeout

    private final Context context;
    @Nullable private ConnectionManager connectionManager;
    /** Saved listener from before we replaced it — restored after playback completes. */
    @Nullable private MacrosManager.MacrosListener savedListener;

    public MacroToolExecutor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Set the ConnectionManager for HID sending during macro playback. */
    public void setConnectionManager(@Nullable ConnectionManager cm) {
        this.connectionManager = cm;
    }

    @NonNull
    @Override
    public String getType() {
        return "macro";
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        // Validate step kind
        if (!"macro".equals(step.kind)) {
            callback.onFailure("MacroToolExecutor cannot handle kind=" + step.kind);
            return;
        }

        // Look up the macro
        MacrosManager macrosManager = MacrosManager.getInstance(context);
        MacrosManager.Macro macro = findMacro(macrosManager, step);

        if (macro == null) {
            String query = step.macroId != null ? step.macroId
                    : (step.command != null ? step.command : "(unknown)");
            callback.onFailure("Macro not found: " + query);
            return;
        }

        // Check HID connection
        ConnectionManager cm = this.connectionManager;
        if (cm == null || !cm.isConnected()) {
            callback.onFailure("HID device not connected. Cannot play macro.");
            return;
        }

        if (macro.keyEvents == null || macro.keyEvents.isEmpty()) {
            callback.onFailure("Macro \"" + macro.name + "\" has no key events.");
            return;
        }

        Log.i(TAG, "Playing macro: " + macro.name
                + " (id=" + macro.id + ", keys=" + macro.keyEvents.size() + ")");
        callback.onProgress(step.index, macro.keyEvents.size());

        // Use CountDownLatch to wait for async playback completion
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>(null);
        final AtomicReference<Boolean> successRef = new AtomicReference<>(false);

        // Save the original listener and install ours
        savedListener = macrosManager.getListener();
        final MacrosManager.Macro capturedMacro = macro;

        final MacrosManager.MacrosListener ourListener = new MacrosManager.MacrosListener() {
            @Override
            public void onRecordingStarted(MacrosManager.Macro m) {
                forward(m2 -> m2.onRecordingStarted(m));
            }

            @Override
            public void onKeyEventRecorded(MacrosManager.KeyEvent event) {
                forward(m2 -> m2.onKeyEventRecorded(event));
            }

            @Override
            public void onMacroSaved(MacrosManager.Macro m) {
                forward(m2 -> m2.onMacroSaved(m));
            }

            @Override
            public void onPlaybackStarted(MacrosManager.Macro m) {
                // Our playback — don't forward
            }

            @Override
            public void onPlaybackComplete(MacrosManager.Macro m) {
                successRef.set(true);
                latch.countDown();
                restoreListener(macrosManager);
                forward(m2 -> m2.onPlaybackComplete(m));
            }

            @Override
            public void onPlaybackStopped() {
                errorRef.set("Macro playback stopped");
                latch.countDown();
                restoreListener(macrosManager);
                forward(m2 -> m2.onPlaybackStopped());
            }

            @Override
            public void onMacroDeleted(MacrosManager.Macro m) {
                forward(m2 -> m2.onMacroDeleted(m));
            }

            @Override
            public void onMacrosImported(List<MacrosManager.Macro> macros) {
                forward(m2 -> m2.onMacrosImported(macros));
            }

            @Override
            public void onMacrosCleared() {
                forward(m2 -> m2.onMacrosCleared());
            }

            private void forward(java.util.function.Consumer<MacrosManager.MacrosListener> action) {
                MacrosManager.MacrosListener orig = savedListener;
                if (orig != null) {
                    try { action.accept(orig); } catch (Exception ignored) {}
                }
            }
        };

        macrosManager.setListener(ourListener);

        // Start playback (async — plays via Handler.postDelayed recursion)
        macrosManager.playMacro(macro, cm);

        // Wait for completion on a background thread
        new Thread(() -> {
            try {
                boolean completed = latch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS);

                if (!completed) {
                    // Timeout — stop playback
                    Log.w(TAG, "Macro playback timed out after " + MAX_WAIT_MS + "ms");
                    macrosManager.stopPlayback();
                    restoreListener(macrosManager);
                    callback.onFailure("Macro playback timed out after "
                            + (MAX_WAIT_MS / 1000) + "s");
                    return;
                }

                String error = errorRef.get();
                if (error != null) {
                    callback.onFailure(error);
                } else if (successRef.get()) {
                    String display = "✅ Macro played: " + macro.name
                            + " (" + macro.keyEvents.size() + " keys)";
                    Log.i(TAG, "Macro playback completed: " + macro.name);
                    callback.onSuccess(display);
                } else {
                    callback.onFailure("Macro playback failed");
                }
            } catch (InterruptedException e) {
                macrosManager.stopPlayback();
                restoreListener(macrosManager);
                callback.onFailure("Macro playback interrupted");
            }
        }, "MacroExec-" + step.index).start();
    }

    @Override
    public void cancel() {
        MacrosManager mm = MacrosManager.getInstance(context);
        if (mm.isPlaying()) {
            mm.stopPlayback();
        }
    }

    /**
     * Find a macro by ID or name.
     * Tries macroId first, then falls back to name matching.
     */
    @Nullable
    private MacrosManager.Macro findMacro(@NonNull MacrosManager mm,
                                          @NonNull AgentPlan.Step step) {
        // Try by ID first
        if (step.macroId != null && !step.macroId.isEmpty()) {
            try {
                long id = Long.parseLong(step.macroId);
                MacrosManager.Macro byId = mm.getMacroById(id);
                if (byId != null) return byId;
            } catch (NumberFormatException ignored) {
                // macroId is not numeric — try as name below
            }
        }

        // Fall back to name matching using macroId or command as query
        String nameQuery = step.macroId != null ? step.macroId : step.command;
        if (nameQuery != null && !nameQuery.isEmpty()) {
            return findMacroByName(mm, nameQuery);
        }

        return null;
    }

    /**
     * Find a macro by name (case-insensitive partial match).
     * Tries exact match first, then partial.
     */
    @Nullable
    private MacrosManager.Macro findMacroByName(@NonNull MacrosManager mm,
                                                 @NonNull String query) {
        List<MacrosManager.Macro> all = mm.getAllMacros();
        String lowerQuery = query.toLowerCase();

        // Exact match first
        for (MacrosManager.Macro m : all) {
            if (m.name != null && m.name.equalsIgnoreCase(query)) {
                return m;
            }
        }

        // Partial match (case-insensitive)
        for (MacrosManager.Macro m : all) {
            if (m.name != null && m.name.toLowerCase().contains(lowerQuery)) {
                return m;
            }
        }

        return null;
    }

    /** Restore the original listener, if we saved one. */
    private void restoreListener(@NonNull MacrosManager mm) {
        MacrosManager.MacrosListener orig = savedListener;
        if (orig != null) {
            mm.setListener(orig);
            savedListener = null;
        }
    }
}
