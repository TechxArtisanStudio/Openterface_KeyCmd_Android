package com.openterface.keymod.agent.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link PathHelper}.
 */
public class PathHelperTest {

    // ── getPathSetupCommand: macOS ─────────────────────────────────────

    @Test
    public void testMacOsPathContainsHomebrew() {
        String path = PathHelper.getPathSetupCommand("macos");
        assertTrue(path.contains("/opt/homebrew/bin"));
        assertTrue(path.contains("/opt/homebrew/sbin"));
        assertTrue(path.contains("/usr/local/bin"));
        assertTrue(path.contains("/usr/local/sbin"));
    }

    // ── getPathSetupCommand: Linux ─────────────────────────────────────

    @Test
    public void testLinuxPathContainsCommonDirs() {
        String path = PathHelper.getPathSetupCommand("linux");
        assertTrue(path.contains("/usr/local/bin"));
        assertTrue(path.contains("/usr/local/sbin"));
        assertTrue(path.contains("/snap/bin"));
        assertTrue(path.contains("$HOME/.local/bin"));
    }

    // ── getPathSetupCommand: Windows ───────────────────────────────────

    @Test
    public void testWindowsPathContainsChocolatey() {
        String path = PathHelper.getPathSetupCommand("windows");
        assertTrue(path.contains("chocolatey"));
        assertTrue(path.contains("Git\\bin"));
        assertTrue(path.contains("nodejs"));
    }

    // ── getPathSetupCommand: unknown OS ────────────────────────────────

    @Test
    public void testUnknownOsReturnsEmpty() {
        assertEquals("", PathHelper.getPathSetupCommand("freebsd"));
        assertEquals("", PathHelper.getPathSetupCommand(""));
    }

    // ── getPathSetupCommand: case insensitive ──────────────────────────

    @Test
    public void testGetPathSetupCaseInsensitive() {
        assertFalse(PathHelper.getPathSetupCommand("MacOS").isEmpty());
        assertFalse(PathHelper.getPathSetupCommand("LINUX").isEmpty());
        assertFalse(PathHelper.getPathSetupCommand("Windows").isEmpty());
    }

    // ── wrapWithPathVariable: Unix ─────────────────────────────────────

    @Test
    public void testWrapWithPathVariableMacOs() {
        String wrapped = PathHelper.wrapWithPathVariable("fastfetch", "macos");
        assertTrue("Should use PATH=... format", wrapped.startsWith("PATH=\""));
        assertTrue("Should contain $PATH", wrapped.contains(":$PATH\""));
        assertTrue("Should contain command", wrapped.contains("fastfetch"));
    }

    @Test
    public void testWrapWithPathVariableLinux() {
        String wrapped = PathHelper.wrapWithPathVariable("htop", "linux");
        assertTrue(wrapped.startsWith("PATH=\""));
        assertTrue(wrapped.contains(":$PATH\" htop"));
    }

    // ── wrapWithPathVariable: Windows ──────────────────────────────────

    @Test
    public void testWrapWithPathVariableWindows() {
        String wrapped = PathHelper.wrapWithPathVariable("node", "windows");
        assertTrue("Should use set PATH= format", wrapped.startsWith("set PATH="));
        assertTrue("Should contain &&", wrapped.contains(" && node"));
    }

    // ── wrapWithPathVariable: unknown OS ───────────────────────────────

    @Test
    public void testWrapWithPathVariableUnknownOsReturnsOriginal() {
        assertEquals("mycommand", PathHelper.wrapWithPathVariable("mycommand", "freebsd"));
    }

    // ── mayNeedPathAugmentation: macOS ─────────────────────────────────

    @Test
    public void testMacOsToolsNeedingPath() {
        assertTrue(PathHelper.mayNeedPathAugmentation("fastfetch", "macos"));
        assertTrue(PathHelper.mayNeedPathAugmentation("brew", "macos"));
        assertTrue(PathHelper.mayNeedPathAugmentation("node", "macos"));
        assertTrue(PathHelper.mayNeedPathAugmentation("npm", "macos"));
        assertTrue(PathHelper.mayNeedPathAugmentation("htop", "macos"));
    }

    @Test
    public void testMacOsCommonToolsDontNeedPath() {
        assertFalse(PathHelper.mayNeedPathAugmentation("ls", "macos"));
        assertFalse(PathHelper.mayNeedPathAugmentation("cd", "macos"));
        assertFalse(PathHelper.mayNeedPathAugmentation("cat", "macos"));
    }

    // ── mayNeedPathAugmentation: Linux ─────────────────────────────────

    @Test
    public void testLinuxToolsNeedingPath() {
        assertTrue(PathHelper.mayNeedPathAugmentation("snap", "linux"));
        assertTrue(PathHelper.mayNeedPathAugmentation("docker", "linux"));
        assertTrue(PathHelper.mayNeedPathAugmentation("flatpak", "linux"));
        assertTrue(PathHelper.mayNeedPathAugmentation("node", "linux"));
    }

    @Test
    public void testLinuxCommonToolsDontNeedPath() {
        assertFalse(PathHelper.mayNeedPathAugmentation("ls", "linux"));
        assertFalse(PathHelper.mayNeedPathAugmentation("grep", "linux"));
    }

    // ── mayNeedPathAugmentation: Windows ───────────────────────────────

    @Test
    public void testWindowsToolsNeedingPath() {
        assertTrue(PathHelper.mayNeedPathAugmentation("choco", "windows"));
        assertTrue(PathHelper.mayNeedPathAugmentation("winget", "windows"));
        assertTrue(PathHelper.mayNeedPathAugmentation("git", "windows"));
        assertTrue(PathHelper.mayNeedPathAugmentation("code", "windows"));
    }

    @Test
    public void testWindowsCommonToolsDontNeedPath() {
        assertFalse(PathHelper.mayNeedPathAugmentation("dir", "windows"));
        assertFalse(PathHelper.mayNeedPathAugmentation("type", "windows"));
    }

    // ── mayNeedPathAugmentation: edge cases ────────────────────────────

    @Test
    public void testEmptyCommandReturnsFalse() {
        assertFalse(PathHelper.mayNeedPathAugmentation("", "linux"));
        assertFalse(PathHelper.mayNeedPathAugmentation("", "macos"));
        assertFalse(PathHelper.mayNeedPathAugmentation("", "windows"));
    }

    @Test
    public void testUnknownOsReturnsFalse() {
        assertFalse(PathHelper.mayNeedPathAugmentation("node", "freebsd"));
    }

    @Test
    public void testCommandWithArgsExtractsFirstWord() {
        assertTrue(PathHelper.mayNeedPathAugmentation("node --version", "linux"));
        assertFalse(PathHelper.mayNeedPathAugmentation("ls /usr/local/bin", "linux"));
    }
}
