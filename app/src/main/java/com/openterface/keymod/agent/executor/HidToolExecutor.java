package com.openterface.keymod.agent.executor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;
import com.openterface.keymod.util.HidTextKeystrokeSender;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes HID steps by sending keystrokes via {@link ConnectionManager}.
 *
 * <p>Implements {@link AgentToolExecutor} for {@code kind="hid"} steps.
 * Delegates to {@link HidTextKeystrokeSender#send} for actual HID transmission.
 * Supports modifier keys, special keys, delays, and Unicode input.</p>
 *
 * <p>Token format examples:</p>
 * <ul>
 *   <li>{@code <CMD>s</CMD>} — Command+S (macOS save)</li>
 *   <li>{@code <CTRL><ALT>t</CTRL></ALT>} — Ctrl+Alt+T (Linux open terminal)</li>
 *   <li>{@code <WIN>r<DELAY1S>cmd<ENTER>} — Win+R, type "cmd", Enter (Windows)</li>
 *   <li>{@code Hello World<ENTER>} — Type text then press Enter</li>
 * </ul>
 *
 * <p>Cancellation is supported via both {@link AtomicBoolean} flag (checked between
 * keystrokes) and thread interruption. On cancellation, a key-release report is
 * always sent to prevent stuck keys.</p>
 */
public final class HidToolExecutor implements AgentToolExecutor {

    private static final String TAG = "HidToolExecutor";

    @Nullable private ConnectionManager connectionManager;
    @NonNull private String targetOs = "macos"; // TODO(release): revert default to "linux"
    @NonNull private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    @Nullable private volatile Thread sendThread;

    /** Set the ConnectionManager for HID sending. */
    public void setConnectionManager(@Nullable ConnectionManager cm) {
        this.connectionManager = cm;
    }

    /**
     * Set the target OS for Unicode input method selection.
     * Valid values: {@code "linux"}, {@code "windows"}, {@code "macos"}.
     */
    public void setTargetOs(@NonNull String os) {
        this.targetOs = os;
    }

    @NonNull
    @Override
    public String getType() {
        return "hid";
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        // Validate step kind
        if (!"hid".equals(step.kind)) {
            callback.onFailure("HidToolExecutor cannot handle kind=" + step.kind);
            return;
        }

        String keys = step.keys;
        if (keys == null || keys.trim().isEmpty()) {
            callback.onFailure("Step has no keys specified");
            return;
        }

        // Check HID connection
        ConnectionManager cm = this.connectionManager;
        if (cm == null || !cm.isConnected()) {
            callback.onFailure("HID device not connected. "
                    + "Please connect via USB or Bluetooth first.");
            return;
        }

        Log.i(TAG, "Executing HID step " + step.index + ": " + keys);
        callback.onProgress(step.index, -1);

        // Reset cancel flag before launching
        cancelFlag.set(false);

        new Thread(() -> {
            sendThread = Thread.currentThread();
            try {
                HidTextKeystrokeSender.Result result = HidTextKeystrokeSender.send(
                        keys,
                        cm,
                        targetOs,
                        true,           // allowUnicode
                        cancelFlag,
                        (completed, total) -> {
                            Log.v(TAG, "HID progress: " + completed + "/" + total);
                            // Check connection between keystrokes — abort if BLE/USB dropped
                            if (!cm.isConnected()) {
                                Log.w(TAG, "HID connection lost mid-execution");
                                cancelFlag.set(true);
                            }
                        }
                );

                sendThread = null;

                if (result == HidTextKeystrokeSender.Result.CANCELLED) {
                    Log.i(TAG, "HID step cancelled");
                    // Check if cancellation was due to disconnection
                    if (!cm.isConnected()) {
                        callback.onFailure("HID device disconnected. "
                                + "Please reconnect via USB or Bluetooth and try again.");
                    } else {
                        callback.onFailure("HID step cancelled");
                    }
                } else {
                    Log.i(TAG, "HID step completed: " + keys);
                    safeReleaseKeys(cm);
                    String display = "✅ Keystrokes sent: " + keys;
                    callback.onSuccess(display);
                }

            } catch (InterruptedException e) {
                sendThread = null;
                Log.i(TAG, "HID step interrupted");
                safeReleaseKeys(cm);
                callback.onFailure("HID step interrupted");
            } catch (Exception e) {
                sendThread = null;
                Log.e(TAG, "HID step failed", e);
                safeReleaseKeys(cm);
                callback.onFailure("HID send failed: " + e.getMessage());
            }
        }, "HidExec-" + step.index).start();
    }

    @Override
    public void cancel() {
        cancelFlag.set(true);
        Thread t = sendThread;
        if (t != null) {
            t.interrupt();
            sendThread = null;
        }
    }

    /**
     * Send key-release report, swallowing any exceptions.
     * Prevents stuck keys after interrupt or error.
     */
    private void safeReleaseKeys(@Nullable ConnectionManager cm) {
        if (cm == null) return;
        try {
            cm.sendKeyRelease();
        } catch (Exception ignored) {
            // Connection may have been lost
        }
    }
}
