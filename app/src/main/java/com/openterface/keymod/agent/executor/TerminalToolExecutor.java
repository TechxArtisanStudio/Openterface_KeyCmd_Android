package com.openterface.keymod.agent.executor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.keymod.agent.core.AgentPlan;
import com.openterface.keymod.agent.core.AgentToolExecutor;
import com.openterface.terminal.SshClient;

/**
 * Executes terminal steps via SSH ChannelExec.
 *
 * <p>Implements {@link AgentToolExecutor} for {@code kind="terminal"} steps.
 * Delegates to {@link SshClient#executeCommand} for actual command execution.</p>
 */
public final class TerminalToolExecutor implements AgentToolExecutor {

    private static final String TAG = "TerminalToolExecutor";
    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int MAX_OUTPUT_CHARS = 2000;
    private static final int MAX_OUTPUT_LINES = 50;

    @Nullable private SshClient sshClient;
    @Nullable private volatile Thread currentThread;

    /** Set the SSH client to use for command execution. */
    public void setSshClient(@Nullable SshClient client) {
        this.sshClient = client;
    }

    @NonNull
    @Override
    public String getType() {
        return "terminal";
    }

    @Override
    public void execute(@NonNull AgentPlan.Step step,
                        @NonNull ExecutionCallback callback) {
        if (!"terminal".equals(step.kind)) {
            callback.onFailure("TerminalToolExecutor cannot handle kind=" + step.kind);
            return;
        }

        String command = step.command;
        if (command == null || command.trim().isEmpty()) {
            callback.onFailure("Step has no command");
            return;
        }

        SshClient client = this.sshClient;
        if (client == null || !client.isSessionConnected()) {
            callback.onFailure("SSH not connected. Please connect in Terminal tab first.");
            return;
        }

        Log.i(TAG, "Executing: " + command);
        callback.onProgress(step.index, -1);

        currentThread = Thread.currentThread();

        client.executeCommand(command, DEFAULT_TIMEOUT_MS, new SshClient.ExecCallback() {
            @Override
            public void onOutput(@NonNull String line) {
                Log.v(TAG, "output: " + line);
            }

            @Override
            public void onComplete(int exitCode, @NonNull String output) {
                currentThread = null;
                String truncated = truncateOutput(output);

                if (exitCode == 0) {
                    Log.i(TAG, "Command succeeded: " + command);
                    callback.onSuccess(truncated);
                } else {
                    String errorOutput = "Exit code: " + exitCode;
                    if (!truncated.isEmpty()) {
                        errorOutput += "\n" + truncated;
                    }
                    Log.w(TAG, "Command failed (exit=" + exitCode + "): " + command);
                    callback.onFailure(errorOutput);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                currentThread = null;
                Log.e(TAG, "Exec error: " + message);
                callback.onFailure(message);
            }
        });
    }

    @Override
    public void cancel() {
        Thread t = currentThread;
        if (t != null) {
            t.interrupt();
            currentThread = null;
        }
    }

    /**
     * Truncate output to prevent UI overflow.
     * Caps at {@value #MAX_OUTPUT_CHARS} chars or {@value #MAX_OUTPUT_LINES} lines,
     * whichever is hit first.
     */
    @NonNull
    String truncateOutput(@NonNull String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) {
            String[] lines = output.split("\n", -1);
            if (lines.length <= MAX_OUTPUT_LINES) {
                return output;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_OUTPUT_LINES; i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            sb.append("\n... (").append(lines.length - MAX_OUTPUT_LINES)
              .append(" more lines truncated)");
            return sb.toString();
        }
        return output.substring(0, MAX_OUTPUT_CHARS)
                + "\n... (" + (output.length() - MAX_OUTPUT_CHARS) + " chars truncated)";
    }
}
