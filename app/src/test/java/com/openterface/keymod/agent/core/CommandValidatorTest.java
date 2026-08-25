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

    // ═══════════════════════════════════════════════════════════════════
    // Day 5: Command Auto-Correction Tests (correctCommandForOs)
    // ═══════════════════════════════════════════════════════════════════

    // ── Edge cases ────────────────────────────────────────────────────

    @Test
    public void testCorrectionEmptyCommandReturnsEmpty() {
        assertEquals("", CommandValidator.correctCommandForOs("", "windows"));
    }

    @Test
    public void testCorrectionWhitespaceCommandReturnsOriginal() {
        assertEquals("   ", CommandValidator.correctCommandForOs("   ", "windows"));
    }

    @Test
    public void testCorrectionUnknownOsReturnsOriginal() {
        assertEquals("ls -la", CommandValidator.correctCommandForOs("ls -la", "freebsd"));
    }

    @Test
    public void testCorrectionCorrectCommandUnchanged() {
        // "dir" on Windows is already correct — no correction needed
        assertEquals("dir", CommandValidator.correctCommandForOs("dir", "windows"));
        assertEquals("ipconfig", CommandValidator.correctCommandForOs("ipconfig", "windows"));
    }

    // ── Unix → Windows corrections ────────────────────────────────────

    @Test
    public void testCorrectionUnixToWindowsLsToDir() {
        assertEquals("dir", CommandValidator.correctCommandForOs("ls -la", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsLsWithPath() {
        assertEquals("dir /tmp", CommandValidator.correctCommandForOs("ls -la /tmp", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsCatToType() {
        assertEquals("type /etc/hosts", CommandValidator.correctCommandForOs("cat /etc/hosts", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsGrepToFindstr() {
        assertEquals("findstr pattern file.txt",
                CommandValidator.correctCommandForOs("grep pattern file.txt", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsPsToTasklist() {
        assertEquals("tasklist", CommandValidator.correctCommandForOs("ps aux", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsKillToTaskkill() {
        assertEquals("taskkill /F /PID 1234",
                CommandValidator.correctCommandForOs("kill -9 1234", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsHostnameIToIpconfig() {
        assertEquals("ipconfig", CommandValidator.correctCommandForOs("hostname -I", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsFreeToSysteminfo() {
        String result = CommandValidator.correctCommandForOs("free -h", "windows");
        assertTrue("Should contain 'systeminfo': " + result, result.contains("systeminfo"));
    }

    @Test
    public void testCorrectionUnixToWindowsDfToWmic() {
        String result = CommandValidator.correctCommandForOs("df -h", "windows");
        assertTrue("Should contain 'wmic': " + result, result.contains("wmic"));
    }

    @Test
    public void testCorrectionUnixToWindowsClearToCls() {
        assertEquals("cls", CommandValidator.correctCommandForOs("clear", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsPwdToCd() {
        assertEquals("cd", CommandValidator.correctCommandForOs("pwd", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsRmToDel() {
        assertEquals("del file.txt", CommandValidator.correctCommandForOs("rm file.txt", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsRmRfToRmdir() {
        assertEquals("rmdir /s /q /tmp/test",
                CommandValidator.correctCommandForOs("rm -rf /tmp/test", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsCaseInsensitive() {
        assertEquals("dir", CommandValidator.correctCommandForOs("LS -LA", "windows"));
    }

    @Test
    public void testCorrectionUnixToWindowsLongestMatchPriority() {
        // "ls -la" should match before bare "ls" — both resolve to "dir"
        assertEquals("dir", CommandValidator.correctCommandForOs("ls -la", "windows"));
        assertEquals("dir /home", CommandValidator.correctCommandForOs("ls /home", "windows"));
    }

    // ── Linux → macOS corrections ─────────────────────────────────────

    @Test
    public void testCorrectionLinuxToMacosHostnameIToIpconfig() {
        assertEquals("ipconfig getifaddr en0",
                CommandValidator.correctCommandForOs("hostname -I", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosFreeToVmStat() {
        assertEquals("vm_stat", CommandValidator.correctCommandForOs("free -h", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosLscpuToSysctl() {
        assertEquals("sysctl -n machdep.cpu.brand_string",
                CommandValidator.correctCommandForOs("lscpu", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosLsbReleaseToSwVers() {
        assertEquals("sw_vers", CommandValidator.correctCommandForOs("lsb_release -a", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosAptToBrew() {
        assertEquals("brew install vim",
                CommandValidator.correctCommandForOs("apt-get install vim", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosYumToBrew() {
        assertEquals("brew install wget",
                CommandValidator.correctCommandForOs("yum install wget", "macos"));
    }

    @Test
    public void testCorrectionLinuxToMacosSystemctlStatus() {
        String result = CommandValidator.correctCommandForOs("systemctl status nginx", "macos");
        assertTrue("Should use launchctl: " + result, result.contains("launchctl"));
    }

    // ── macOS → Linux corrections ─────────────────────────────────────

    @Test
    public void testCorrectionMacosToLinuxIpconfigToHostname() {
        // "ipconfig getifaddr" is replaced with "hostname -I", but "en0" is preserved
        assertEquals("hostname -I en0",
                CommandValidator.correctCommandForOs("ipconfig getifaddr en0", "linux"));
    }

    @Test
    public void testCorrectionMacosToLinuxVmStatToFree() {
        assertEquals("free -h", CommandValidator.correctCommandForOs("vm_stat", "linux"));
    }

    @Test
    public void testCorrectionMacosToLinuxSwVersToLsbRelease() {
        assertEquals("lsb_release -a", CommandValidator.correctCommandForOs("sw_vers", "linux"));
    }

    @Test
    public void testCorrectionMacosToLinuxSysctlToLscpu() {
        assertEquals("lscpu",
                CommandValidator.correctCommandForOs("sysctl -n machdep.cpu.brand_string", "linux"));
    }

    @Test
    public void testCorrectionMacosToLinuxBrewToApt() {
        assertEquals("apt-get install vim",
                CommandValidator.correctCommandForOs("brew install vim", "linux"));
    }

    @Test
    public void testCorrectionMacosToLinuxDiskutilToLsblk() {
        assertEquals("lsblk",
                CommandValidator.correctCommandForOs("diskutil list", "linux"));
    }

    // ── Commands that should NOT be corrected (already correct OS) ────

    @Test
    public void testCorrectionNoChangeOnCorrectOs() {
        // ls is correct on Linux
        assertEquals("ls -la /home", CommandValidator.correctCommandForOs("ls -la /home", "linux"));
        // dir is correct on Windows
        assertEquals("dir C:\\", CommandValidator.correctCommandForOs("dir C:\\", "windows"));
        // brew is correct on macOS
        assertEquals("brew list", CommandValidator.correctCommandForOs("brew list", "macos"));
    }

    // ── Correction preserves case of replacement ──────────────────────

    @Test
    public void testCorrectionUsesCanonicalCase() {
        // Input is uppercase but replacement should use canonical Windows case
        String result = CommandValidator.correctCommandForOs("LS -LA", "windows");
        assertEquals("dir", result);
    }

    // ── Word boundary: must not match inside substrings ─────────────────

    @Test
    public void testWordBoundary_RmNotMatchedInsideForm() {
        // "form" contains "rm" as substring — must NOT be corrected to "delo"
        assertEquals("form file.txt",
                CommandValidator.correctCommandForOs("form file.txt", "windows"));
    }

    @Test
    public void testWordBoundary_CatNotMatchedInsideCatalog() {
        // "catalog" contains "cat" as substring — must not mangle to "typealog".
        // "ls" is correctly converted to "dir"; "catalog" stays intact.
        assertEquals("dir /tmp/catalog/data",
                CommandValidator.correctCommandForOs("ls /tmp/catalog/data", "windows"));
    }

    @Test
    public void testWordBoundary_LsNotMatchedInsideAlso() {
        // "also" contains "ls" as substring — must not be corrected
        // Use a command where "also" appears in an argument
        String result = CommandValidator.correctCommandForOs("echo also", "windows");
        // "echo" is not in the correction map, so should be unchanged
        assertEquals("echo also", result);
    }

    @Test
    public void testWordBoundary_DfNotMatchedInsideDiff() {
        // "diff" contains "df" as substring — must NOT be corrected
        assertEquals("diff file1 file2",
                CommandValidator.correctCommandForOs("diff file1 file2", "windows"));
    }

    @Test
    public void testWordBoundary_FreeNotMatchedInsideFreedom() {
        // "freedom" contains "free" — must NOT be corrected
        assertEquals("echo freedom",
                CommandValidator.correctCommandForOs("echo freedom", "windows"));
    }

    @Test
    public void testWordBoundary_ScpNotConvertedOnWindows() {
        // "scp" contains "cp" as substring — must NOT be converted to "copy"
        assertEquals("scp file.txt user@host:/tmp/",
                CommandValidator.correctCommandForOs("scp file.txt user@host:/tmp/", "windows"));
    }

    @Test
    public void testWordBoundary_PsNotMatchedInsideCaps() {
        // "caps" contains "ps" as substring — must NOT be converted
        assertEquals("echo caps",
                CommandValidator.correctCommandForOs("echo caps", "windows"));
    }

    @Test
    public void testWordBoundary_ClearNotMatchedInsideClearance() {
        // "clearance" contains "clear" as substring — must NOT be converted to "cls"
        // Without word-boundary fix: "echo clearance" → "echo clsance" (bug)
        assertEquals("echo clearance",
                CommandValidator.correctCommandForOs("echo clearance", "windows"));
    }
}
