package com.openterface.keymod.agent.util;

import androidx.annotation.NonNull;

/**
 * PATH environment setup for SSH non-interactive sessions.
 *
 * <p>SSH non-interactive shells (ChannelExec) do NOT load shell profile files
 * ({@code .zshrc}, {@code .bashrc}, {@code .profile}), so many common tools
 * are not in the default PATH.</p>
 *
 * <p>This class provides OS-specific PATH prefixes that should be prepended
 * to commands to ensure common tools are accessible.</p>
 *
 * <h3>PATH locations by OS</h3>
 *
 * <h4>macOS</h4>
 * <ul>
 *   <li>{@code /opt/homebrew/bin} - Homebrew (Apple Silicon)</li>
 *   <li>{@code /opt/homebrew/sbin} - Homebrew sbin (Apple Silicon)</li>
 *   <li>{@code /usr/local/bin} - Homebrew (Intel) / user-installed</li>
 *   <li>{@code /usr/local/sbin} - Local sbin</li>
 * </ul>
 *
 * <h4>Linux</h4>
 * <ul>
 *   <li>{@code /usr/local/bin} - User-installed packages</li>
 *   <li>{@code /usr/local/sbin} - Local admin tools</li>
 *   <li>{@code ~/.local/bin} - User-specific tools (pip, npm global, etc.)</li>
 *   <li>{@code /snap/bin} - Snap packages (Ubuntu)</li>
 *   <li>{@code /var/lib/snapd/snap/bin} - Snap alternative path</li>
 * </ul>
 *
 * <h4>Windows (CMD)</h4>
 * <ul>
 *   <li>{@code C:\\ProgramData\\chocolatey\\bin} - Chocolatey</li>
 *   <li>{@code C:\\Program Files\\Git\\bin} - Git for Windows</li>
 *   <li>{@code C:\\Program Files\\nodejs} - Node.js</li>
 *   <li>{@code %USERPROFILE%\\AppData\\Roaming\\npm} - npm global</li>
 * </ul>
 */
public final class PathHelper {

    private PathHelper() {
        // Static utility class
    }

    /**
     * Get the PATH prefix command for the given OS.
     *
     * <p>Returns a shell command that sets PATH to include common tool locations.
     * This should be prepended to the actual command using {@code &&} or inline.</p>
     *
     * @param targetOs the target OS: "macos", "linux", or "windows"
     * @return PATH setup command, or empty string if not needed
     */
    @NonNull
    public static String getPathSetupCommand(@NonNull String targetOs) {
        switch (targetOs.toLowerCase()) {
            case "macos":
                return getMacOsPathSetup();
            case "linux":
                return getLinuxPathSetup();
            case "windows":
                return getWindowsPathSetup();
            default:
                return "";
        }
    }

    /**
     * Wrap a command with PATH initialization for the given OS.
     *
     * <p>Example output for macOS:
     * <pre>
     * PATH="/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/local/sbin:$PATH" /opt/homebrew/bin/fastfetch
     * </pre>
     * </p>
     *
     * @param command the original command to execute
     * @param targetOs the target OS
     * @return the command wrapped with PATH setup
     */
    @NonNull
    public static String wrapWithPathVariable(@NonNull String command, @NonNull String targetOs) {
        String pathSetup = getPathSetupCommand(targetOs);
        if (pathSetup.isEmpty()) {
            return command;
        }

        // For Windows, use set command
        if (targetOs.toLowerCase().equals("windows")) {
            return "set PATH=" + pathSetup + " && " + command;
        }

        // For Unix-like systems, inline PATH assignment
        return "PATH=\"" + pathSetup + ":$PATH\" " + command;
    }

    /**
     * Check if a command likely needs PATH augmentation.
     *
     * <p>Commands that are commonly not in default PATH:
     * fastfetch, neofetch, htop, brew, npm, yarn, pip, etc.</p>
     *
     * @param command the command to check
     * @param targetOs the target OS
     * @return true if the command might need PATH augmentation
     */
    public static boolean mayNeedPathAugmentation(@NonNull String command, @NonNull String targetOs) {
        if (command.isEmpty()) return false;

        String firstWord = command.trim().split("\\s+")[0].toLowerCase();

        // Known tools that are often not in default PATH
        switch (targetOs.toLowerCase()) {
            case "macos":
                return isMacOsToolNeedingPath(firstWord);
            case "linux":
                return isLinuxToolNeedingPath(firstWord);
            case "windows":
                return isWindowsToolNeedingPath(firstWord);
            default:
                return false;
        }
    }

    // ── macOS ──────────────────────────────────────────────────────────

    @NonNull
    private static String getMacOsPathSetup() {
        return "/opt/homebrew/bin"
                + ":/opt/homebrew/sbin"
                + ":/usr/local/bin"
                + ":/usr/local/sbin";
    }

    private static boolean isMacOsToolNeedingPath(@NonNull String tool) {
        return tool.equals("fastfetch")
                || tool.equals("neofetch")
                || tool.equals("htop")
                || tool.equals("brew")
                || tool.equals("tree")
                || tool.equals("wget")
                || tool.equals("curl")  // sometimes not in PATH
                || tool.equals("jq")
                || tool.equals("yq")
                || tool.equals("node")
                || tool.equals("npm")
                || tool.equals("yarn")
                || tool.equals("python3")
                || tool.equals("pip3")
                || tool.equals("ruby")
                || tool.equals("gem");
    }

    // ── Linux ──────────────────────────────────────────────────────────

    @NonNull
    private static String getLinuxPathSetup() {
        return "/usr/local/bin"
                + ":/usr/local/sbin"
                + ":/snap/bin"
                + ":/var/lib/snapd/snap/bin"
                + ":$HOME/.local/bin";
    }

    private static boolean isLinuxToolNeedingPath(@NonNull String tool) {
        return tool.equals("fastfetch")
                || tool.equals("neofetch")
                || tool.equals("htop")
                || tool.equals("tree")
                || tool.equals("jq")
                || tool.equals("yq")
                || tool.equals("node")
                || tool.equals("npm")
                || tool.equals("yarn")
                || tool.equals("python3")
                || tool.equals("pip3")
                || tool.equals("snap")
                || tool.equals("flatpak")
                || tool.equals("docker")
                || tool.equals("docker-compose");
    }

    // ── Windows ────────────────────────────────────────────────────────

    @NonNull
    private static String getWindowsPathSetup() {
        return "C:\\ProgramData\\chocolatey\\bin"
                + ";C:\\Program Files\\Git\\bin"
                + ";C:\\Program Files\\nodejs"
                + ";%USERPROFILE%\\AppData\\Roaming\\npm"
                + ";C:\\Program Files\\Python39"
                + ";C:\\Program Files\\Python39\\Scripts";
    }

    private static boolean isWindowsToolNeedingPath(@NonNull String tool) {
        return tool.equals("choco")
                || tool.equals("winget")
                || tool.equals("node")
                || tool.equals("npm")
                || tool.equals("yarn")
                || tool.equals("python")
                || tool.equals("pip")
                || tool.equals("git")
                || tool.equals("code")  // VS Code
                || tool.equals("docker");
    }
}
