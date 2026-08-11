package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link CommandValidator}.
 */
@RunWith(RobolectricTestRunner.class)
public class CommandValidatorTest {

    // ── Empty / edge cases ─────────────────────────────────────────────

    @Test
    public void testEmptyCommandIsValid() {
        assertTrue(CommandValidator.validate("", "linux").valid);
        assertTrue(CommandValidator.validate("", "windows").valid);
        assertTrue(CommandValidator.validate("", "macos").valid);
    }

    @Test
    public void testUnknownOsSkipsValidation() {
        CommandValidator.ValidationResult result = CommandValidator.validate("ls -la", "freebsd");
        assertTrue("Unknown OS should pass validation", result.valid);
        assertNull(result.message);
    }

    // ── Windows target: Unix commands should fail ──────────────────────

    @Test
    public void testWindowsRejectsLs() {
        CommandValidator.ValidationResult result = CommandValidator.validate("ls -la", "windows");
        assertFalse(result.valid);
        assertNotNull(result.message);
        assertTrue(result.message.contains("ls"));
    }

    @Test
    public void testWindowsRejectsCat() {
        assertFalse(CommandValidator.validate("cat /etc/passwd", "windows").valid);
    }

    @Test
    public void testWindowsRejectsGrep() {
        assertFalse(CommandValidator.validate("grep pattern file", "windows").valid);
    }

    @Test
    public void testWindowsRejectsPs() {
        assertFalse(CommandValidator.validate("ps aux", "windows").valid);
    }

    @Test
    public void testWindowsRejectsSystemctl() {
        assertFalse(CommandValidator.validate("systemctl status nginx", "windows").valid);
    }

    @Test
    public void testWindowsRejectsApt() {
        assertFalse(CommandValidator.validate("apt install vim", "windows").valid);
    }

    // ── Windows target: cross-platform commands pass ───────────────────

    @Test
    public void testWindowsAllowsNetstat() {
        assertTrue(CommandValidator.validate("netstat -an", "windows").valid);
    }

    @Test
    public void testWindowsAllowsIp() {
        assertTrue(CommandValidator.validate("ip config", "windows").valid);
    }

    @Test
    public void testWindowsAllowsHostname() {
        assertTrue(CommandValidator.validate("hostname", "windows").valid);
    }

    @Test
    public void testWindowsAllowsDirCommand() {
        assertTrue(CommandValidator.validate("dir C:\\Users", "windows").valid);
    }

    // ── Linux target: Windows commands should fail ─────────────────────

    @Test
    public void testLinuxRejectsDir() {
        CommandValidator.ValidationResult result = CommandValidator.validate("dir", "linux");
        assertFalse(result.valid);
        assertTrue(result.message.contains("dir"));
    }

    @Test
    public void testLinuxRejectsTasklist() {
        assertFalse(CommandValidator.validate("tasklist", "linux").valid);
    }

    @Test
    public void testLinuxRejectsIpconfig() {
        assertFalse(CommandValidator.validate("ipconfig", "linux").valid);
    }

    @Test
    public void testLinuxRejectsFindstr() {
        assertFalse(CommandValidator.validate("findstr pattern file", "linux").valid);
    }

    @Test
    public void testLinuxRejectsWinget() {
        assertFalse(CommandValidator.validate("winget install vim", "linux").valid);
    }

    @Test
    public void testLinuxRejectsPowerShellGetCmdlets() {
        assertFalse(CommandValidator.validate("Get-Process", "linux").valid);
    }

    @Test
    public void testLinuxRejectsPowerShellSelectCmdlets() {
        assertFalse(CommandValidator.validate("Select-Object Name", "linux").valid);
    }

    @Test
    public void testLinuxRejectsPowerShellSortCmdlets() {
        assertFalse(CommandValidator.validate("Sort-Object Name", "linux").valid);
    }

    // ── Linux target: macOS-only commands should fail ──────────────────

    @Test
    public void testLinuxRejectsBrew() {
        assertFalse(CommandValidator.validate("brew install wget", "linux").valid);
    }

    @Test
    public void testLinuxRejectsSwVers() {
        assertFalse(CommandValidator.validate("sw_vers", "linux").valid);
    }

    // ── Linux target: valid Unix commands pass ─────────────────────────

    @Test
    public void testLinuxAllowsLs() {
        assertTrue(CommandValidator.validate("ls -la", "linux").valid);
    }

    @Test
    public void testLinuxAllowsDf() {
        assertTrue(CommandValidator.validate("df -h", "linux").valid);
    }

    @Test
    public void testLinuxAllowsGrep() {
        assertTrue(CommandValidator.validate("grep pattern file.txt", "linux").valid);
    }

    // ── macOS target: Windows commands should fail ─────────────────────

    @Test
    public void testMacOSRejectsDir() {
        assertFalse(CommandValidator.validate("dir", "macos").valid);
    }

    @Test
    public void testMacOSRejectsTasklist() {
        assertFalse(CommandValidator.validate("tasklist", "macos").valid);
    }

    @Test
    public void testMacOSRejectsPowerShellStopCmdlets() {
        assertFalse(CommandValidator.validate("Stop-Process -Id 1234", "macos").valid);
    }

    // ── macOS target: Linux-only commands should fail ──────────────────

    @Test
    public void testMacOSRejectsApt() {
        assertFalse(CommandValidator.validate("apt install vim", "macos").valid);
    }

    @Test
    public void testMacOSRejectsSystemctl() {
        assertFalse(CommandValidator.validate("systemctl status nginx", "macos").valid);
    }

    @Test
    public void testMacOSRejectsFree() {
        assertFalse(CommandValidator.validate("free -m", "macos").valid);
    }

    // ── macOS target: valid commands pass ──────────────────────────────

    @Test
    public void testMacOSAllowsLs() {
        assertTrue(CommandValidator.validate("ls -la", "macos").valid);
    }

    @Test
    public void testMacOSAllowsBrew() {
        assertTrue(CommandValidator.validate("brew install wget", "macos").valid);
    }

    // ── Case insensitivity ─────────────────────────────────────────────

    @Test
    public void testValidateCaseInsensitiveOs() {
        assertTrue(CommandValidator.validate("ls", "Linux").valid);
        assertTrue(CommandValidator.validate("ls", "LINUX").valid);
        assertFalse(CommandValidator.validate("dir", "Linux").valid);
    }

    @Test
    public void testValidateCommandWithLeadingSpaces() {
        assertFalse(CommandValidator.validate("   ls -la", "windows").valid);
    }

    // ── quickCheck ─────────────────────────────────────────────────────

    @Test
    public void testQuickCheckDelegatesToValidate() {
        assertTrue(CommandValidator.quickCheck("ls", "linux"));
        assertFalse(CommandValidator.quickCheck("ls", "windows"));
        assertTrue(CommandValidator.quickCheck("dir", "windows"));
        assertFalse(CommandValidator.quickCheck("dir", "linux"));
    }

    // ── ValidationResult factories ─────────────────────────────────────

    @Test
    public void testValidationResultValid() {
        CommandValidator.ValidationResult r = CommandValidator.ValidationResult.valid();
        assertTrue(r.valid);
        assertNull(r.message);
    }

    @Test
    public void testValidationResultInvalid() {
        CommandValidator.ValidationResult r = CommandValidator.ValidationResult.invalid("bad cmd");
        assertFalse(r.valid);
        assertEquals("bad cmd", r.message);
    }
}
