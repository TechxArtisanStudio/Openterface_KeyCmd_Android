package com.openterface.keymod.agent.core;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
     * {@code cmdLower.startsWith(patternLower)}.  Patterns containing
     * {@code (}, {@code )}, {@code |}, {@code \}, or {@code {} are
     * compiled as case-insensitive regexes.
     *
     * <p>The pattern is always lowercased before the simple-string check so
     * that a future uppercase pattern cannot silently bypass matching —
     * {@code cmdLower} is already lowercased by the caller.
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
        // Defensive lowercase — patterns in the static sets are currently
        // already lowercase, but lowercasing here prevents a silent bypass
        // if someone adds a mixed-case entry later.
        String patternLower = pattern.toLowerCase();
        return cmdLower.startsWith(patternLower) || firstWord.equals(patternLower);
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

    // ── Command Auto-Correction Maps ─────────────────────────────────────

    /**
     * Correction map: Unix command → Windows equivalent.
     * Keys are lowercase; values are the corrected command prefix.
     *
     * <p>Rules are kept conservative on purpose: only patterns whose
     * replacement preserves semantics are listed.  Complex commands that
     * don't match a clean rule are left untouched — the OS validation gate
     * plus the LLM retry mechanism handle those.
     */
    private static final Map<String, String> UNIX_TO_WINDOWS = new HashMap<>();
    static {
        UNIX_TO_WINDOWS.put("ls -la", "dir");
        UNIX_TO_WINDOWS.put("ls -l", "dir");
        UNIX_TO_WINDOWS.put("ls -al", "dir");
        UNIX_TO_WINDOWS.put("ls", "dir");
        UNIX_TO_WINDOWS.put("cat ", "type ");
        UNIX_TO_WINDOWS.put("cat", "type");
        UNIX_TO_WINDOWS.put("grep ", "findstr ");
        UNIX_TO_WINDOWS.put("grep", "findstr");
        UNIX_TO_WINDOWS.put("ps aux", "tasklist");
        UNIX_TO_WINDOWS.put("ps -ef", "tasklist");
        UNIX_TO_WINDOWS.put("ps", "tasklist");
        UNIX_TO_WINDOWS.put("kill -9", "taskkill /F /PID");
        UNIX_TO_WINDOWS.put("kill", "taskkill /PID");
        UNIX_TO_WINDOWS.put("df -h", "wmic logicaldisk get size,freesize,caption");
        UNIX_TO_WINDOWS.put("df -H", "wmic logicaldisk get size,freesize,caption");
        UNIX_TO_WINDOWS.put("df", "wmic logicaldisk get size,freesize,caption");
        UNIX_TO_WINDOWS.put("free -h", "systeminfo | findstr /C:\"Total Physical Memory\"");
        UNIX_TO_WINDOWS.put("free", "systeminfo | findstr /C:\"Total Physical Memory\"");
        UNIX_TO_WINDOWS.put("hostname -I", "ipconfig");
        UNIX_TO_WINDOWS.put("ip addr", "ipconfig");
        UNIX_TO_WINDOWS.put("ifconfig", "ipconfig");
        UNIX_TO_WINDOWS.put("wc -l", "find /c /v \"\"");
        UNIX_TO_WINDOWS.put("clear", "cls");
        UNIX_TO_WINDOWS.put("which ", "where ");
        UNIX_TO_WINDOWS.put("which", "where");
        UNIX_TO_WINDOWS.put("pwd", "cd");
        UNIX_TO_WINDOWS.put("mkdir -p", "mkdir");
        UNIX_TO_WINDOWS.put("rm -rf", "rmdir /s /q");
        UNIX_TO_WINDOWS.put("rm -r", "rmdir /s /q");
        UNIX_TO_WINDOWS.put("rm ", "del ");
        UNIX_TO_WINDOWS.put("rm", "del");
        UNIX_TO_WINDOWS.put("cp ", "copy ");
        UNIX_TO_WINDOWS.put("cp", "copy");
        UNIX_TO_WINDOWS.put("mv ", "move ");
        UNIX_TO_WINDOWS.put("mv", "move");
        UNIX_TO_WINDOWS.put("touch ", "echo. > ");
        UNIX_TO_WINDOWS.put("touch", "echo. >");
        UNIX_TO_WINDOWS.put("man ", "help ");
        UNIX_TO_WINDOWS.put("man", "help");
    }

    /**
     * Correction map: Linux command → macOS equivalent.
     * Same conservative policy as {@link #UNIX_TO_WINDOWS}.
     */
    private static final Map<String, String> LINUX_TO_MACOS = new HashMap<>();
    static {
        LINUX_TO_MACOS.put("hostname -I", "ipconfig getifaddr en0");
        LINUX_TO_MACOS.put("ip addr show", "ifconfig");
        LINUX_TO_MACOS.put("ip addr", "ifconfig");
        LINUX_TO_MACOS.put("free -h", "vm_stat");
        LINUX_TO_MACOS.put("free", "vm_stat");
        LINUX_TO_MACOS.put("lscpu", "sysctl -n machdep.cpu.brand_string");
        LINUX_TO_MACOS.put("lsb_release -a", "sw_vers");
        LINUX_TO_MACOS.put("lsb_release", "sw_vers");
        LINUX_TO_MACOS.put("cat /etc/os-release", "sw_vers");
        LINUX_TO_MACOS.put("apt-get install", "brew install");
        LINUX_TO_MACOS.put("apt install", "brew install");
        LINUX_TO_MACOS.put("apt-get", "brew");
        LINUX_TO_MACOS.put("apt", "brew");
        LINUX_TO_MACOS.put("yum install", "brew install");
        LINUX_TO_MACOS.put("yum", "brew");
        LINUX_TO_MACOS.put("dnf install", "brew install");
        LINUX_TO_MACOS.put("dnf", "brew");
        LINUX_TO_MACOS.put("systemctl status", "launchctl list | grep");
        LINUX_TO_MACOS.put("ss -tulpn", "netstat -an");
        LINUX_TO_MACOS.put("ss", "netstat");
    }

    /**
     * Correction map: macOS command → Linux equivalent.
     * Same conservative policy as {@link #UNIX_TO_WINDOWS}.
     */
    private static final Map<String, String> MACOS_TO_LINUX = new HashMap<>();
    static {
        MACOS_TO_LINUX.put("ipconfig getifaddr", "hostname -I");
        MACOS_TO_LINUX.put("vm_stat", "free -h");
        MACOS_TO_LINUX.put("sw_vers", "lsb_release -a");
        MACOS_TO_LINUX.put("sysctl -n machdep.cpu.brand_string", "lscpu");
        MACOS_TO_LINUX.put("sysctl -a", "cat /proc/cpuinfo");
        MACOS_TO_LINUX.put("brew install", "apt-get install");
        MACOS_TO_LINUX.put("brew", "apt-get");
        MACOS_TO_LINUX.put("diskutil list", "lsblk");
        MACOS_TO_LINUX.put("diskutil", "lsblk");
        MACOS_TO_LINUX.put("launchctl list", "systemctl list-units");
        MACOS_TO_LINUX.put("log show", "journalctl");
        MACOS_TO_LINUX.put("mdfind", "find /");
    }

    /**
     * Correct a command for the target OS by replacing common OS-specific
     * commands with their equivalents.
     *
     * <p>This is a best-effort transformation. If no correction is needed
     * (or no rule matches), the original command is returned unchanged.
     *
     * @param command  the original command string
     * @param targetOs the target OS ("linux", "macos", "windows")
     * @return the corrected command, or the original if no correction applied
     */
    @NonNull
    public static String correctCommandForOs(@NonNull String command, @NonNull String targetOs) {
        if (command.trim().isEmpty()) {
            return command;
        }

        String cmdLower = command.toLowerCase();
        String os = targetOs.toLowerCase();

        Map<String, String> correctionMap;
        switch (os) {
            case "windows":
                correctionMap = UNIX_TO_WINDOWS;
                break;
            case "macos":
                correctionMap = LINUX_TO_MACOS;
                break;
            case "linux":
                correctionMap = MACOS_TO_LINUX;
                break;
            default:
                return command; // Unknown OS, no correction
        }

        String corrected = command;
        // Apply corrections in order of specificity (longer patterns first)
        List<Map.Entry<String, String>> entries =
                new ArrayList<>(correctionMap.entrySet());
        // Sort by key length descending so longer matches take priority
        Collections.sort(entries, (a, b) ->
                Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, String> entry : entries) {
            String pattern = entry.getKey();
            String replacement = entry.getValue();
            if (cmdLower.contains(pattern.toLowerCase())) {
                // Case-insensitive replacement with word boundaries to
                // prevent substring matches (e.g. "rm" inside "form",
                // "cat" inside "catalog", "ls" inside "also").
                String regex = buildWordBoundaryRegex(pattern);
                String newCorrected = corrected.replaceAll(
                        regex,
                        Matcher.quoteReplacement(replacement));
                // contains() may pass (substring) but regex may not match
                // (word boundary prevents it).  Only return when the
                // replacement actually changed the string.
                if (!newCorrected.equals(corrected)) {
                    corrected = newCorrected;
                    Log.d(TAG, "Auto-corrected command for " + targetOs + ": '"
                            + command + "' → '" + corrected + "'");
                    return corrected;
                }
            }
        }

        return corrected;
    }

    /**
     * Build a case-insensitive regex for command replacement with word
     * boundaries.  Prevents substring matches like {@code rm} inside
     * "form" or {@code cat} inside "catalog".
     *
     * <p>Rules:
     * <ul>
     *   <li>If the pattern starts with a word character (letter/digit),
     *       prepend {@code \b}.</li>
     *   <li>If the pattern ends with a word character, append {@code \b}.</li>
     *   <li>The pattern body is quoted via {@link Pattern#quote} so that
     *       metacharacters in the replacement keys are treated literally.</li>
     * </ul>
     */
    private static String buildWordBoundaryRegex(@NonNull String pattern) {
        StringBuilder sb = new StringBuilder("(?i)");
        if (pattern.length() > 0 && Character.isLetterOrDigit(pattern.charAt(0))) {
            sb.append("\\b");
        }
        sb.append(Pattern.quote(pattern));
        if (pattern.length() > 0
                && Character.isLetterOrDigit(pattern.charAt(pattern.length() - 1))) {
            sb.append("\\b");
        }
        return sb.toString();
    }
}
