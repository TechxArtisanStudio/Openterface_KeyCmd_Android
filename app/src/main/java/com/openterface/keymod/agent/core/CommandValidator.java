package com.openterface.keymod.agent.core;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates that generated commands match the target OS.
 *
 * <p>This is a safety net to catch obviously wrong commands before execution.
 * It checks for OS-specific "red flag" commands that would fail or produce
 * unexpected results on the wrong OS.
 */
public final class CommandValidator {

    private static final String TAG = "CommandValidator";

    // Commands that are specific to Unix-like systems (Linux/macOS)
    private static final Set<String> UNIX_COMMANDS = new HashSet<>(Arrays.asList(
            "ls", "cat", "grep", "ps", "kill", "top", "df", "free", "chmod", "chown",
            "ip", "ifconfig", "systemctl", "journalctl", "apt", "yum", "dnf", "pacman",
            "brew", "sw_vers", "uname", "lscpu", "hostname", "ss", "netstat"
    ));

    // Commands that are specific to Windows
    private static final Set<String> WINDOWS_COMMANDS = new HashSet<>(Arrays.asList(
            "dir", "type", "findstr", "tasklist", "taskkill", "ipconfig", "ver",
            "wmic", "sc", "icacls", "winget", "choco", "powershell", "Get-",
            "Select-", "Where-", "Sort-", "Stop-"
    ));

    // Commands specific to Linux (not macOS)
    private static final Set<String> LINUX_ONLY_COMMANDS = new HashSet<>(Arrays.asList(
            "hostname -I", "ip addr", "free", "lscpu", "lsb_release", "apt", "yum",
            "dnf", "systemctl", "journalctl", "ss -tulpn"
    ));

    // Commands specific to macOS (not Linux)
    private static final Set<String> MACOS_ONLY_COMMANDS = new HashSet<>(Arrays.asList(
            "ipconfig getifaddr", "vm_stat", "sw_vers", "brew", "diskutil",
            "launchctl", "mdfind", "sysctl -n machdep"
    ));

    /**
     * Validation result.
     */
    public static class ValidationResult {
        public final boolean valid;
        public final String message;

        ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * Validate that a command is appropriate for the target OS.
     *
     * @param command the command to validate
     * @param targetOs the target OS ("linux", "macos", "windows")
     * @return validation result with error message if invalid
     */
    @NonNull
    public static ValidationResult validate(@NonNull String command, @NonNull String targetOs) {
        if (command.isEmpty()) {
            return ValidationResult.valid();
        }

        String cmdLower = command.trim().toLowerCase();
        String firstWord = cmdLower.split("\\s+")[0];

        switch (targetOs.toLowerCase()) {
            case "windows":
                return validateWindowsCommand(cmdLower, firstWord);

            case "linux":
                return validateLinuxCommand(cmdLower, firstWord);

            case "macos":
                return validateMacOsCommand(cmdLower, firstWord);

            default:
                Log.w(TAG, "Unknown target OS: " + targetOs + ", skipping validation");
                return ValidationResult.valid();
        }
    }

    /**
     * Validate command for Windows target.
     */
    private static ValidationResult validateWindowsCommand(String cmdLower, String firstWord) {
        // Check for Unix commands that won't work on Windows CMD
        if (UNIX_COMMANDS.contains(firstWord)) {
            // Some commands exist on both but are different (e.g., netstat)
            if (firstWord.equals("netstat") || firstWord.equals("ip") || firstWord.equals("hostname")) {
                return ValidationResult.valid();
            }
            return ValidationResult.invalid(
                    "Command '" + firstWord + "' is a Unix command and won't work on Windows CMD. " +
                    "Use Windows commands like: dir, type, findstr, tasklist, ipconfig, etc."
            );
        }
        return ValidationResult.valid();
    }

    /**
     * Validate command for Linux target.
     */
    private static ValidationResult validateLinuxCommand(String cmdLower, String firstWord) {
        // Check for Windows commands that won't work on Linux
        if (WINDOWS_COMMANDS.contains(firstWord) || cmdLower.startsWith("get-") ||
            cmdLower.startsWith("select-") || cmdLower.startsWith("where-") ||
            cmdLower.startsWith("sort-") || cmdLower.startsWith("stop-")) {
            return ValidationResult.invalid(
                    "Command '" + firstWord + "' is a Windows command and won't work on Linux. " +
                    "Use Unix commands like: ls, cat, grep, ps, df, free, etc."
            );
        }

        // Check for macOS-specific commands
        for (String macCmd : MACOS_ONLY_COMMANDS) {
            if (cmdLower.contains(macCmd.toLowerCase())) {
                return ValidationResult.invalid(
                        "Command '" + macCmd + "' is macOS-specific and won't work on Linux. " +
                        "Use Linux equivalents instead."
                );
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate command for macOS target.
     */
    private static ValidationResult validateMacOsCommand(String cmdLower, String firstWord) {
        // Check for Windows commands that won't work on macOS
        if (WINDOWS_COMMANDS.contains(firstWord) || cmdLower.startsWith("get-") ||
            cmdLower.startsWith("select-") || cmdLower.startsWith("where-") ||
            cmdLower.startsWith("sort-") || cmdLower.startsWith("stop-")) {
            return ValidationResult.invalid(
                    "Command '" + firstWord + "' is a Windows command and won't work on macOS. " +
                    "Use Unix commands like: ls, cat, grep, ps, df, etc."
            );
        }

        // Check for Linux-specific commands (not available on macOS)
        for (String linuxCmd : LINUX_ONLY_COMMANDS) {
            if (cmdLower.contains(linuxCmd.toLowerCase())) {
                return ValidationResult.invalid(
                        "Command '" + linuxCmd + "' is Linux-specific and not available on macOS. " +
                        "Use macOS equivalents instead."
                );
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Quick check if a command likely matches the target OS.
     * Returns true if the command is likely valid, false if it's likely wrong.
     * This is a fast heuristic check, not a full validation.
     */
    public static boolean quickCheck(@NonNull String command, @NonNull String targetOs) {
        return validate(command, targetOs).valid;
    }
}
