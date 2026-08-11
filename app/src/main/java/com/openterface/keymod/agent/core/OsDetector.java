package com.openterface.keymod.agent.core;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openterface.terminal.SshClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Auto-detects the target operating system of an SSH connection.
 *
 * <p>Detection strategy (in order):
 * <ol>
 *   <li>Execute {@code uname -s} — returns "Linux" or "Darwin" on Unix-like systems</li>
 *   <li>Execute {@code ver} — returns Windows version string on Windows CMD</li>
 *   <li>Execute {@code echo %OS%} — returns "Windows_NT" on Windows CMD</li>
 *   <li>Fallback to user-configured targetOs from CredentialProfile</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   OsDetector.detectOs(sshClient, "linux", detectedOs -> {
 *       Log.i(TAG, "Detected OS: " + detectedOs);
 *       // Use detectedOs for prompt building
 *   });
 * </pre>
 */
public final class OsDetector {

    private static final String TAG = "OsDetector";
    private static final long DETECTION_TIMEOUT_MS = 5000;

    /**
     * Detected operating system.
     */
    public enum DetectedOS {
        LINUX("linux", "Linux"),
        MACOS("macos", "macOS"),
        WINDOWS("windows", "Windows"),
        UNKNOWN("linux", "Linux");  // Default fallback

        private final String code;
        private final String displayName;

        DetectedOS(@NonNull String code, @NonNull String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        @NonNull
        public String getCode() {
            return code;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Parse OS from uname -s output.
         */
        @NonNull
        public static DetectedOS fromUname(@NonNull String output) {
            String lower = output.trim().toLowerCase();
            if (lower.contains("linux")) {
                return LINUX;
            } else if (lower.contains("darwin")) {
                return MACOS;
            }
            return UNKNOWN;
        }

        /**
         * Parse OS from Windows ver or echo %OS% output.
         */
        @NonNull
        public static DetectedOS fromWindowsOutput(@NonNull String output) {
            String lower = output.trim().toLowerCase();
            if (lower.contains("windows") || lower.contains("microsoft") || lower.contains("windows_nt")) {
                return WINDOWS;
            }
            return UNKNOWN;
        }
    }

    /**
     * Detect the target OS by executing commands on the SSH connection.
     *
     * <p>Runs detection on a background thread and invokes the callback with the result.
     * If detection fails or times out, returns UNKNOWN (caller should fallback to user config).
     *
     * @param sshClient the connected SSH client
     * @param fallbackOs the user-configured OS to fallback to (from CredentialProfile)
     * @param callback called on a background thread with the detected OS
     */
    public static void detectOs(@NonNull SshClient sshClient,
                                 @NonNull String fallbackOs,
                                 @NonNull Consumer<DetectedOS> callback) {
        new Thread(() -> {
            DetectedOS detected = DetectedOS.UNKNOWN;

            try {
                // Strategy 1: Try uname -s (Linux/macOS)
                detected = tryDetectWithUname(sshClient);

                if (detected == DetectedOS.UNKNOWN) {
                    // Strategy 2: Try ver (Windows CMD)
                    detected = tryDetectWithVer(sshClient);
                }

                if (detected == DetectedOS.UNKNOWN) {
                    // Strategy 3: Try echo %OS% (Windows CMD alternative)
                    detected = tryDetectWithEchoOs(sshClient);
                }

                if (detected == DetectedOS.UNKNOWN) {
                    Log.i(TAG, "OS detection failed, using fallback: " + fallbackOs);
                    // Map fallback to DetectedOS
                    switch (fallbackOs.toLowerCase()) {
                        case "windows":
                            detected = DetectedOS.WINDOWS;
                            break;
                        case "macos":
                            detected = DetectedOS.MACOS;
                            break;
                        case "linux":
                        default:
                            detected = DetectedOS.LINUX;
                            break;
                    }
                } else {
                    Log.i(TAG, "OS auto-detected: " + detected.getDisplayName());
                }

            } catch (Exception e) {
                Log.w(TAG, "OS detection error", e);
            }

            callback.accept(detected);

        }, "OsDetector").start();
    }

    /**
     * Detect OS synchronously (blocks until detection completes or times out).
     *
     * @param sshClient the connected SSH client
     * @param fallbackOs the user-configured OS to fallback to
     * @return the detected OS, or fallback if detection fails
     */
    @NonNull
    public static DetectedOS detectOsSync(@NonNull SshClient sshClient, @NonNull String fallbackOs) {
        final DetectedOS[] result = {DetectedOS.UNKNOWN};
        CountDownLatch latch = new CountDownLatch(1);

        detectOs(sshClient, fallbackOs, os -> {
            result[0] = os;
            latch.countDown();
        });

        try {
            latch.await(DETECTION_TIMEOUT_MS * 3, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result[0];
    }

    // ── Detection strategies ────────────────────────────────────────────

    /**
     * Try to detect OS using {@code uname -s}.
     * Works on Linux and macOS.
     */
    @NonNull
    private static DetectedOS tryDetectWithUname(@NonNull SshClient sshClient) {
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();

        try {
            sshClient.executeCommand("uname -s", (int) DETECTION_TIMEOUT_MS, new SshClient.ExecCallback() {
                @Override
                public void onOutput(String line) {
                    output.append(line).append("\n");
                }

                @Override
                public void onComplete(int exitCode, String fullOutput) {
                    if (exitCode == 0 && !fullOutput.isEmpty()) {
                        output.append(fullOutput);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "uname -s failed: " + message);
                    latch.countDown();
                }
            });

            latch.await(DETECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (output.length() > 0) {
                DetectedOS os = DetectedOS.fromUname(output.toString());
                if (os != DetectedOS.UNKNOWN) {
                    Log.d(TAG, "uname -s detected: " + os.getDisplayName());
                    return os;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "uname detection error", e);
        }

        return DetectedOS.UNKNOWN;
    }

    /**
     * Try to detect OS using {@code ver} command.
     * Works on Windows CMD.
     */
    @NonNull
    private static DetectedOS tryDetectWithVer(@NonNull SshClient sshClient) {
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();

        try {
            sshClient.executeCommand("ver", (int) DETECTION_TIMEOUT_MS, new SshClient.ExecCallback() {
                @Override
                public void onOutput(String line) {
                    output.append(line).append("\n");
                }

                @Override
                public void onComplete(int exitCode, String fullOutput) {
                    if (exitCode == 0 && !fullOutput.isEmpty()) {
                        output.append(fullOutput);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "ver failed: " + message);
                    latch.countDown();
                }
            });

            latch.await(DETECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (output.length() > 0) {
                DetectedOS os = DetectedOS.fromWindowsOutput(output.toString());
                if (os != DetectedOS.UNKNOWN) {
                    Log.d(TAG, "ver detected: " + os.getDisplayName());
                    return os;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ver detection error", e);
        }

        return DetectedOS.UNKNOWN;
    }

    /**
     * Try to detect OS using {@code echo %OS%} command.
     * Works on Windows CMD as an alternative to ver.
     */
    @NonNull
    private static DetectedOS tryDetectWithEchoOs(@NonNull SshClient sshClient) {
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder output = new StringBuilder();

        try {
            sshClient.executeCommand("echo %OS%", (int) DETECTION_TIMEOUT_MS, new SshClient.ExecCallback() {
                @Override
                public void onOutput(String line) {
                    output.append(line).append("\n");
                }

                @Override
                public void onComplete(int exitCode, String fullOutput) {
                    if (exitCode == 0 && !fullOutput.isEmpty()) {
                        output.append(fullOutput);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    Log.d(TAG, "echo %OS% failed: " + message);
                    latch.countDown();
                }
            });

            latch.await(DETECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (output.length() > 0) {
                DetectedOS os = DetectedOS.fromWindowsOutput(output.toString());
                if (os != DetectedOS.UNKNOWN) {
                    Log.d(TAG, "echo %OS% detected: " + os.getDisplayName());
                    return os;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "echo %OS% detection error", e);
        }

        return DetectedOS.UNKNOWN;
    }
}
