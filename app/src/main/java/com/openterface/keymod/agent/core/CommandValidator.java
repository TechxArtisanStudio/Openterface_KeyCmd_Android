package com.openterface.keymod.agent.core;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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
    // Expanded to cover common Agent-generated commands that would fail
    // on Windows CMD / PowerShell.  Note: commands like ip/netstat/hostname
    // are intentionally NOT here because they exist on both platforms.
    private static final Set<String> UNIX_COMMANDS = new HashSet<>(Arrays.asList(
            "ls", "cat", "grep", "ps", "kill", "top", "df", "free", "chmod", "chown",
            "ifconfig", "systemctl", "journalctl", "apt", "apt-get", "yum", "dnf", "pacman",
            "zypper", "brew", "sw_vers", "uname", "lscpu", "hostnamectl", "ss",
            "pgrep", "pkill", "find", "xargs", "sed", "awk", "tar", "gzip", "gunzip",
            "bzip2", "xz", "curl", "wget", "rsync", "scp", "rsyslog", "logrotate",
            "ln", "readlink", "mktemp", "mkfifo", "strace", "ltrace", "ldd", "file",
            "which", "whereis", "locate", "updatedb", "mount", "umount", "fdisk",
            "parted", "mkfs", "fsck", "tune2fs", "e2fsck", "blkid", "lsblk",
            "modprobe", "insmod", "rmmod", "lsmod", "dmesg", "sysctl",
            "iptables", "nft", "ufw", "firewall-cmd", "nmcli", "iwconfig", "iwlist",
            "traceroute", "mtr", "dig", "nslookup", "host", "tcpdump",
            "screen", "tmux", "nohup", "crontab", "at", "watch",
            "base64", "md5sum", "sha256sum", "sha1sum",
            "shred", "wipe", "srm"
    ));

    // Commands that are specific to Windows
    // Expanded to cover PowerShell cmdlets (Get-*, Set-*, etc.), Windows-native
    // tools (winget, choco, wmic), and CMD-only commands.
    private static final Set<String> WINDOWS_COMMANDS = new HashSet<>(Arrays.asList(
            "dir", "type", "findstr", "tasklist", "taskkill", "ipconfig", "ver",
            "wmic", "sc", "icacls", "winget", "choco", "powershell",
            "Get-", "Set-", "Select-", "Where-", "Sort-", "Stop-",
            "Start-", "New-", "Remove-", "Rename-", "Copy-", "Move-",
            "Invoke-", "Out-", "Write-", "Read-", "Test-", "Format-",
            "ConvertTo-", "ConvertFrom-", "Import-", "Export-",
            "ForEach-", "Group-", "Measure-", "Tee-",
            // Common CMD-only commands (excluding ones that also exist on Unix:
            // cd, mkdir, rmdir, more, sort, shutdown — these are intentionally NOT listed)
            "assoc", "attrib", "bcdedit", "cacls", "choice", "cipher",
            "cleanmgr", "clip", "cls", "cmdkey", "comp", "compact", "control",
            "defrag", "del", "diskpart", "doskey", "driverquery",
            "erase", "eventcreate", "eventvwr", "expand", "fltmc", "forfiles",
            "fsutil", "ftp", "ftype", "gpresult", "gpupdate", "makecab",
            "mklink", "mode", "openfiles", "pathping", "popd",
            "print", "prncnfg", "prompt", "pushd", "query", "qprocess",
            "quser", "qwinsta", "reg", "regedit", "regsvr32", "robocopy",
            "rwinsta", "schtasks", "sfc", "setx", "subst",
            "systeminfo", "takeown", "tree", "tskill", "typeperf", "vol",
            "xcopy"
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

    // ── Command Danger Classification (P0-1) ──────────────────────────

    /**
     * Danger level of a command.
     *
     * <ul>
     *   <li>{@link #SAFE} — normal command, no special handling.</li>
     *   <li>{@link #DANGEROUS} — potentially destructive; UI should ask for
     *       user confirmation before execution.</li>
     *   <li>{@link #BLOCKED} — extremely destructive; execution is always
     *       refused.  A {@link DangerousCommandException} is thrown.</li>
     * </ul>
     */
    public enum DangerLevel {
        SAFE,
        DANGEROUS,
        BLOCKED
    }

    /**
     * Thrown when a command matches a blocked (extremely destructive) pattern.
     * Callers should refuse to execute the command and surface
     * {@link #getDangerResult()} to the user.
     */
    public static class DangerousCommandException extends RuntimeException {
        private final DangerResult dangerResult;

        public DangerousCommandException(@NonNull DangerResult result) {
            super(result.reason);
            this.dangerResult = result;
        }

        @NonNull
        public DangerResult getDangerResult() {
            return dangerResult;
        }
    }

    /**
     * Result of a danger-level check.
     */
    public static class DangerResult {
        @NonNull public final DangerLevel level;
        @NonNull public final String reason;

        DangerResult(@NonNull DangerLevel level, @NonNull String reason) {
            this.level = level;
            this.reason = reason;
        }

        @NonNull
        public static DangerResult safe() {
            return new DangerResult(DangerLevel.SAFE, "");
        }

        @NonNull
        public static DangerResult dangerous(@NonNull String reason) {
            return new DangerResult(DangerLevel.DANGEROUS, reason);
        }

        @NonNull
        public static DangerResult blocked(@NonNull String reason) {
            return new DangerResult(DangerLevel.BLOCKED, reason);
        }
    }

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

        // ── Danger check (runs before OS validation) ──
        DangerResult danger = checkCommandDanger(command);
        if (danger.level == DangerLevel.BLOCKED) {
            return ValidationResult.invalid(danger.reason);
        }

        String cmdLower = command.trim().toLowerCase();
        String firstWord = cmdLower.split("\\s+")[0];

        // ── Path-style mismatch (e.g. /etc/nginx on Windows) ──
        ValidationResult pathCheck = checkPathMismatch(cmdLower, targetOs.toLowerCase());
        if (pathCheck != null) {
            return pathCheck;
        }

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
     * Check for path-style mismatches: Unix absolute paths (e.g. /etc/nginx)
     * on a Windows target, or Windows drive paths (e.g. C:\Windows) on a
     * Unix target.  Returns a non-null ValidationResult when a mismatch is
     * detected, or null when no path issue is found.
     */
    @Nullable
    private static ValidationResult checkPathMismatch(@NonNull String cmdLower,
                                                       @NonNull String targetOs) {
        if ("windows".equals(targetOs)) {
            // Unix absolute paths on Windows target — very likely wrong.
            // Exclude common false-positives: bare flags like -v, -h,
            // URL schemes (http://), and pipe redirects (| /dev/null).
            if (cmdLower.matches(".*\\s/[a-zA-Z][a-zA-Z0-9_/.-]+.*")) {
                // Ignore: http(s)://, ftp://, file://, ssh://
                if (!cmdLower.contains("://")) {
                    // Ignore: pipe to /dev/null, /dev/stderr, etc.
                    if (!cmdLower.contains("/dev/")) {
                        return ValidationResult.invalid(
                                "Unix-style path detected ('/...') but target is Windows. "
                                + "Use Windows paths like C:\\... instead.");
                    }
                }
            }
        } else {
            // Unix target (linux/macos): Windows drive paths (C:\, D:\) are wrong
            if (cmdLower.matches(".*[a-z]:\\\\.*")) {
                return ValidationResult.invalid(
                        "Windows-style path detected ('X:\\...') but target is "
                        + targetOs + ". Use Unix paths like /foo/bar instead.");
            }
        }
        return null;
    }

    /**
     * Validate command for Windows target.
     */
    private static ValidationResult validateWindowsCommand(String cmdLower, String firstWord) {
        // "sudo" prefix is a dead giveaway the command is Unix — sudo doesn't
        // exist on native Windows CMD / PowerShell.
        if ("sudo".equals(firstWord)) {
            return ValidationResult.invalid(
                    "'sudo' is a Unix command and won't work on Windows. "
                    + "Run the command directly, or use 'runas' on Windows."
            );
        }
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

    // ── Command Danger Checking ───────────────────────────────────────

    /**
     * Check the danger level of a command.
     *
     * <p>This method analyses the command string for patterns known to be
     * extremely destructive (BLOCKED) or potentially harmful (DANGEROUS).
     *
     * <ul>
     *   <li><b>BLOCKED</b> — caller MUST throw {@link DangerousCommandException}
     *       and refuse execution.</li>
     *   <li><b>DANGEROUS</b> — caller SHOULD ask the user for explicit
     *       confirmation before executing.</li>
     *   <li><b>SAFE</b> — no special action needed.</li>
     * </ul>
     *
     * @param command the raw command string
     * @return danger result (never null)
     */
    @NonNull
    public static DangerResult checkCommandDanger(@NonNull String command) {
        if (command.trim().isEmpty()) {
            return DangerResult.safe();
        }

        String cmdLower = command.trim().toLowerCase();
        String firstWord = cmdLower.split("\\s+")[0];

        // 1. Check blocked patterns (extremely destructive — always refuse)
        for (String pattern : BLOCKED_COMMAND_PATTERNS) {
            if (matchPattern(cmdLower, firstWord, pattern)) {
                return DangerResult.blocked(
                        "Command is extremely dangerous and has been blocked: " + command);
            }
        }

        // 2. Check dangerous commands (potentially destructive — need confirmation)
        for (String cmd : DANGEROUS_COMMANDS) {
            if (firstWord.equals(cmd)) {
                return DangerResult.dangerous(
                        "Command '" + cmd + "' is potentially destructive. "
                        + "Please confirm before executing.");
            }
        }

        return DangerResult.safe();
    }

    /**
     * Match a command against a pattern.
     *
     * <p>Simple patterns (no regex metacharacters) are matched as
     * {@code cmdLower.startsWith(pattern)}.  Patterns containing
     * {@code (}, {@code )}, {@code |}, {@code \}, or {@code {} are
     * compiled as case-insensitive regexes.
     */
    private static boolean matchPattern(
            @NonNull String cmdLower, @NonNull String firstWord,
            @NonNull String pattern) {
        if (pattern.contains("(") || pattern.contains(")")
                || pattern.contains("|") || pattern.contains("\\")
                || pattern.contains("{")) {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                    .matcher(cmdLower).find();
        }
        return cmdLower.startsWith(pattern) || firstWord.equals(pattern);
    }

    /**
     * Patterns for commands that are <b>always blocked</b> because they are
     * almost certainly destructive.  Each entry is either a simple prefix
     * string or a regex (detected by the presence of regex metacharacters).
     */
    private static final Set<String> BLOCKED_COMMAND_PATTERNS = new HashSet<>(Arrays.asList(
            // ── rm: recursive delete on root or broad wildcards (regex) ──
            // Match "rm -<flags-with-r> /" with optional trailing args
            "rm\\s+-[a-z]*r[a-z]*\\s+/(\\s|$)",
            // Match "rm -<flags-with-r> /*" (wildcard)
            "rm\\s+-[a-z]*r[a-z]*\\s+/\\*",
            // Match "rm --recursive /" with optional trailing args
            "rm\\s+--recursive\\s+/(\\s|$)",
            // Match "rm --recursive /*" (wildcard)
            "rm\\s+--recursive\\s+/\\*",
            // ── dd: overwrite with /dev/zero ──
            "dd\\s+if=/dev/zero",
            // ── dd: write directly to block device (exclude /dev/null, /dev/zero) ──
            "dd\\s+of=/dev/(?!null|zero)",
            // ── Disk format / wipe ──
            "mkfs\\.",
            "mkfs ",
            "format c:",
            "format d:",
            "format e:",
            "del /f /s /q",
            // ── Fork bombs (regex-escaped so matchPattern treats them as regex) ──
            ":\\s*\\(\\s*\\)\\s*\\{",
            "\\.\\s*\\(\\s*\\)\\s*\\{\\.\\s*\\|\\s*\\.\\s*&\\s*\\}\\s*;",
            // ── Raw block-device writes ──
            ">\\s*/dev/sd",
            ">\\s*/dev/nvme",
            ">\\s*/dev/hd",
            // ── Disk encryption wipe ──
            "cryptsetup\\s+erase",
            "cryptsetup\\s+lukserase"
    ));

    /**
     * First-words of commands that are <b>potentially destructive</b> and
     * should require user confirmation.
     */
    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
            "rm",               // file deletion
            "shutdown",         // system shutdown
            "reboot",           // system reboot
            "halt",             // system halt
            "poweroff",         // power off
            "init",             // init system (e.g. init 0)
            "dd",               // raw data copy (without if=/dev/zero — that's BLOCKED)
            "mkfs",             // format filesystem
            "chmod",            // change permissions
            "chown",            // change ownership
            "chgrp",            // change group
            "kill",             // send signal to process
            "killall",          // kill by name
            "pkill",            // kill by pattern
            "fdisk",            // partition table
            "parted",           // partition
            "diskpart",         // Windows partition
            "format",           // Windows disk format
            "del",              // Windows delete
            "erase",            // Windows erase
            "rmdir"             // remove directory
    ));
}
