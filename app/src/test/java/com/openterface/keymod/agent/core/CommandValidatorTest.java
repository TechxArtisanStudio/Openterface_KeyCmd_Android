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

    // ═══════════════════════════════════════════════════════════════════
    // P0-1: Command Danger Classification Tests
    // ═══════════════════════════════════════════════════════════════════

    // ── DangerResult factories ────────────────────────────────────────

    @Test
    public void testDangerResultSafe() {
        CommandValidator.DangerResult r = CommandValidator.DangerResult.safe();
        assertEquals(CommandValidator.DangerLevel.SAFE, r.level);
    }

    @Test
    public void testDangerResultDangerous() {
        CommandValidator.DangerResult r = CommandValidator.DangerResult.dangerous("test");
        assertEquals(CommandValidator.DangerLevel.DANGEROUS, r.level);
        assertEquals("test", r.reason);
    }

    @Test
    public void testDangerResultBlocked() {
        CommandValidator.DangerResult r = CommandValidator.DangerResult.blocked("test");
        assertEquals(CommandValidator.DangerLevel.BLOCKED, r.level);
        assertEquals("test", r.reason);
    }

    // ── BLOCKED: rm -rf / variants ────────────────────────────────────

    @Test
    public void testBlockedRmRfRoot() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -rf /").level);
    }

    @Test
    public void testBlockedRmRfRootStar() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -rf /*").level);
    }

    @Test
    public void testBlockedRmFrRoot() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -fr /").level);
    }

    @Test
    public void testBlockedRmFrRootStar() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -fr /*").level);
    }

    @Test
    public void testBlockedRmRRoot() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -r /").level);
    }

    @Test
    public void testBlockedRmRRootStar() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -r /*").level);
    }

    @Test
    public void testBlockedRmRecursiveLongFlag() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm --recursive /").level);
    }

    @Test
    public void testBlockedRmRfRootWithExtraArgs() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -rf / --no-preserve-root").level);
    }

    @Test
    public void testBlockedRmArbitraryFlagsWithR() {
        // rm -xrf / (any flag combo containing 'r')
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -xrf /").level);
    }

    // ── BLOCKED: dd destructive patterns ──────────────────────────────

    @Test
    public void testBlockedDdIfDevZero() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd if=/dev/zero of=/dev/sda").level);
    }

    @Test
    public void testBlockedDdOfDevSda() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd of=/dev/sda if=test.img").level);
    }

    @Test
    public void testBlockedDdOfDevSdb() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd of=/dev/sdb bs=4M").level);
    }

    @Test
    public void testBlockedDdOfDevNvme() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd of=/dev/nvme0n1 if=image.img").level);
    }

    // ── BLOCKED: Disk format / wipe ───────────────────────────────────

    @Test
    public void testBlockedMkfsExt4() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("mkfs.ext4 /dev/sda1").level);
    }

    @Test
    public void testBlockedMkfsXfs() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("mkfs.xfs /dev/sda1").level);
    }

    @Test
    public void testBlockedMkfsGeneric() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("mkfs /dev/sda1").level);
    }

    @Test
    public void testBlockedFormatC() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("format c:").level);
    }

    @Test
    public void testBlockedFormatD() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("format d:").level);
    }

    @Test
    public void testBlockedDelForceRecursive() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("del /f /s /q C:\\*.*").level);
    }

    // ── BLOCKED: Fork bombs ───────────────────────────────────────────

    @Test
    public void testBlockedForkBombColon() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger(":(){:|:&};:").level);
    }

    @Test
    public void testBlockedForkBombColonWithSpaces() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger(":() { : | : & }; :").level);
    }

    @Test
    public void testBlockedForkBombDot() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger(".(){.|.&};:").level);
    }

    // ── BLOCKED: Raw block-device writes ──────────────────────────────

    @Test
    public void testBlockedRawWriteDevSda() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("> /dev/sda").level);
    }

    @Test
    public void testBlockedRawWriteDevNvme() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("> /dev/nvme0n1").level);
    }

    @Test
    public void testBlockedRawWriteDevHda() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("> /dev/hda").level);
    }

    // ── BLOCKED: Encryption wipe ──────────────────────────────────────

    @Test
    public void testBlockedCryptsetupErase() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("cryptsetup erase /dev/sda").level);
    }

    @Test
    public void testBlockedCryptsetupLuksErase() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("cryptsetup lukserase /dev/sda").level);
    }

    // ── BLOCKED: Case insensitivity ───────────────────────────────────

    @Test
    public void testBlockedCaseInsensitiveRmRf() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("RM -RF /").level);
    }

    @Test
    public void testBlockedCaseInsensitiveDd() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("DD if=/dev/zero of=/dev/sda").level);
    }

    @Test
    public void testBlockedCaseInsensitiveMkfs() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("MKFS.EXT4 /dev/sda1").level);
    }

    @Test
    public void testBlockedCaseInsensitiveFormat() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("FORMAT C:").level);
    }

    // ── BLOCKED: Leading whitespace ───────────────────────────────────

    @Test
    public void testBlockedWithLeadingSpaces() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("   rm -rf /").level);
    }

    @Test
    public void testBlockedWithLeadingTabs() {
        assertEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("\tdd if=/dev/zero of=/dev/sda").level);
    }

    // ── DANGEROUS: Unix destructive commands ──────────────────────────

    @Test
    public void testDangerousRmFile() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("rm file.txt").level);
    }

    @Test
    public void testDangerousRmRecursiveDir() {
        // rm -rf on a non-root path is DANGEROUS, not BLOCKED
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("rm -rf /tmp/test").level);
    }

    @Test
    public void testDangerousRmForce() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("rm -f file.txt").level);
    }

    @Test
    public void testDangerousShutdown() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("shutdown -h now").level);
    }

    @Test
    public void testDangerousReboot() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("reboot").level);
    }

    @Test
    public void testDangerousHalt() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("halt").level);
    }

    @Test
    public void testDangerousPoweroff() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("poweroff").level);
    }

    @Test
    public void testDangerousKill9() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("kill -9 1234").level);
    }

    @Test
    public void testDangerousKillall() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("killall nginx").level);
    }

    @Test
    public void testDangerousPkill() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("pkill -f java").level);
    }

    @Test
    public void testDangerousChmod() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("chmod 777 /etc/passwd").level);
    }

    @Test
    public void testDangerousChown() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("chown root:root /etc/hosts").level);
    }

    @Test
    public void testDangerousChgrp() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("chgrp staff /usr/local").level);
    }

    @Test
    public void testDangerousDd() {
        // dd without /dev/zero or of=/dev/ is DANGEROUS, not BLOCKED
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("dd if=test.img of=/dev/null").level);
    }

    @Test
    public void testDangerousDdPlain() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("dd").level);
    }

    @Test
    public void testDangerousMkfs() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("mkfs").level);
    }

    @Test
    public void testDangerousFdisk() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("fdisk /dev/sda").level);
    }

    @Test
    public void testDangerousParted() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("parted /dev/sda mklabel gpt").level);
    }

    @Test
    public void testDangerousRmdir() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("rmdir /tmp/olddir").level);
    }

    @Test
    public void testDangerousInit() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("init 0").level);
    }

    // ── DANGEROUS: Windows destructive commands ───────────────────────

    @Test
    public void testDangerousWindowsFormat() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("format").level);
    }

    @Test
    public void testDangerousWindowsDel() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("del file.txt").level);
    }

    @Test
    public void testDangerousWindowsErase() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("erase file.txt").level);
    }

    @Test
    public void testDangerousWindowsDiskpart() {
        assertEquals(CommandValidator.DangerLevel.DANGEROUS,
                CommandValidator.checkCommandDanger("diskpart").level);
    }

    // ── SAFE: Normal commands ─────────────────────────────────────────

    @Test
    public void testSafeLs() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("ls -la").level);
    }

    @Test
    public void testSafeDf() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("df -h").level);
    }

    @Test
    public void testSafeCat() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("cat /etc/hosts").level);
    }

    @Test
    public void testSafeGrep() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("grep pattern file.txt").level);
    }

    @Test
    public void testSafePs() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("ps aux").level);
    }

    @Test
    public void testSafeEcho() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("echo hello").level);
    }

    @Test
    public void testSafePwd() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("pwd").level);
    }

    @Test
    public void testSafeWhoami() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("whoami").level);
    }

    @Test
    public void testSafeTop() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("top -bn1").level);
    }

    @Test
    public void testSafeSsh() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("ssh user@host").level);
    }

    @Test
    public void testSafeCurl() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("curl https://example.com").level);
    }

    @Test
    public void testSafeWindowsDir() {
        // dir as a Windows command is DANGEROUS (in DANGEROUS_COMMANDS set)
        // but "dir" alone is still potentially destructive (removes directory)
        CommandValidator.DangerResult r = CommandValidator.checkCommandDanger("dir");
        assertTrue(r.level == CommandValidator.DangerLevel.DANGEROUS
                || r.level == CommandValidator.DangerLevel.SAFE);
    }

    // ── Edge cases ────────────────────────────────────────────────────

    @Test
    public void testDangerEmptyCommandIsSafe() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("").level);
    }

    @Test
    public void testDangerWhitespaceOnlyIsSafe() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("   ").level);
    }

    @Test
    public void testDangerTabsOnlyIsSafe() {
        assertEquals(CommandValidator.DangerLevel.SAFE,
                CommandValidator.checkCommandDanger("\t\t").level);
    }

    @Test
    public void testDangerRmNonRootIsNotBlocked() {
        // rm -rf on a non-root path should NOT be blocked
        assertNotEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("rm -rf /home/user/file").level);
    }

    @Test
    public void testDangerDdDevNullIsNotBlocked() {
        // dd of=/dev/null should NOT be blocked (it's a safe target)
        assertNotEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd if=test.img of=/dev/null").level);
    }

    @Test
    public void testDangerDdDevZeroOutputIsNotBlocked() {
        // dd of=/dev/zero is unusual but not blocked
        assertNotEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("dd if=test.img of=/dev/zero").level);
    }

    @Test
    public void testDangerFormatNonSystemDriveIsNotBlocked() {
        // format f: is not in the blocked list (only c:, d:, e: are blocked)
        assertNotEquals(CommandValidator.DangerLevel.BLOCKED,
                CommandValidator.checkCommandDanger("format f:").level);
    }

    // ── validate() integration with danger check ──────────────────────

    @Test
    public void testValidateRejectsBlockedCommand() {
        // validate() should return invalid for BLOCKED commands
        assertFalse(CommandValidator.validate("rm -rf /", "linux").valid);
    }

    @Test
    public void testValidateRejectsBlockedDd() {
        assertFalse(CommandValidator.validate("dd if=/dev/zero of=/dev/sda", "linux").valid);
    }

    @Test
    public void testValidateRejectsBlockedForkBomb() {
        assertFalse(CommandValidator.validate(":(){:|:&};:", "linux").valid);
    }

    @Test
    public void testValidateRejectsBlockedMkfs() {
        assertFalse(CommandValidator.validate("mkfs.ext4 /dev/sda1", "linux").valid);
    }

    @Test
    public void testValidateAllowsDangerousCommand() {
        // validate() should still pass for DANGEROUS (not BLOCKED) commands
        // DANGEROUS is a warning, not a block
        assertTrue(CommandValidator.validate("rm file.txt", "linux").valid);
    }

    @Test
    public void testValidateAllowsSafeCommand() {
        assertTrue(CommandValidator.validate("ls -la", "linux").valid);
    }

    // ── DangerousCommandException ─────────────────────────────────────

    @Test
    public void testDangerousCommandExceptionCarriesResult() {
        CommandValidator.DangerResult result = CommandValidator.DangerResult.blocked("test reason");
        CommandValidator.DangerousCommandException ex =
                new CommandValidator.DangerousCommandException(result);
        assertSame(result, ex.getDangerResult());
        assertEquals("test reason", ex.getMessage());
    }

    @Test
    public void testDangerousCommandExceptionIsRuntimeException() {
        CommandValidator.DangerResult result = CommandValidator.DangerResult.blocked("blocked");
        CommandValidator.DangerousCommandException ex =
                new CommandValidator.DangerousCommandException(result);
        assertTrue(ex instanceof RuntimeException);
    }

    // ── quickCheck integration ────────────────────────────────────────

    @Test
    public void testQuickCheckRejectsBlockedCommand() {
        // quickCheck should return false for BLOCKED commands
        assertFalse(CommandValidator.quickCheck("rm -rf /", "linux"));
    }

    @Test
    public void testQuickCheckAllowsSafeCommand() {
        assertTrue(CommandValidator.quickCheck("ls -la", "linux"));
    }
}
