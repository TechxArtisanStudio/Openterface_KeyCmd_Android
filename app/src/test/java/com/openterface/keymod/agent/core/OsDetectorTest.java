package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link OsDetector} — covers the {@code DetectedOS} inner enum
 * (static factory methods, code/display accessors).
 *
 * <p>Async detection methods ({@code detectOs}, {@code detectOsSync}) require
 * a live {@code SshClient} and are not unit-testable.</p>
 */
public class OsDetectorTest {

    // ── DetectedOS.fromUname ───────────────────────────────────────────

    @Test
    public void testFromUnameLinux() {
        assertEquals(OsDetector.DetectedOS.LINUX, OsDetector.DetectedOS.fromUname("Linux"));
    }

    @Test
    public void testFromUnameLinuxLowercase() {
        assertEquals(OsDetector.DetectedOS.LINUX, OsDetector.DetectedOS.fromUname("linux"));
    }

    @Test
    public void testFromUnameLinuxWithNewline() {
        assertEquals(OsDetector.DetectedOS.LINUX, OsDetector.DetectedOS.fromUname("Linux\n"));
    }

    @Test
    public void testFromUnameDarwin() {
        assertEquals(OsDetector.DetectedOS.MACOS, OsDetector.DetectedOS.fromUname("Darwin"));
    }

    @Test
    public void testFromUnameDarwinLowercase() {
        assertEquals(OsDetector.DetectedOS.MACOS, OsDetector.DetectedOS.fromUname("darwin"));
    }

    @Test
    public void testFromUnameUnknown() {
        assertEquals(OsDetector.DetectedOS.UNKNOWN, OsDetector.DetectedOS.fromUname("FreeBSD"));
    }

    @Test
    public void testFromUnameEmpty() {
        assertEquals(OsDetector.DetectedOS.UNKNOWN, OsDetector.DetectedOS.fromUname(""));
    }

    // ── DetectedOS.fromWindowsOutput ───────────────────────────────────

    @Test
    public void testFromWindowsOutputMicrosoft() {
        assertEquals(OsDetector.DetectedOS.WINDOWS,
                OsDetector.DetectedOS.fromWindowsOutput("Microsoft Windows [Version 10.0.19045]"));
    }

    @Test
    public void testFromWindowsOutputWindowsNt() {
        assertEquals(OsDetector.DetectedOS.WINDOWS,
                OsDetector.DetectedOS.fromWindowsOutput("Windows_NT"));
    }

    @Test
    public void testFromWindowsOutputWindowsLowercase() {
        assertEquals(OsDetector.DetectedOS.WINDOWS,
                OsDetector.DetectedOS.fromWindowsOutput("windows 11"));
    }

    @Test
    public void testFromWindowsOutputNotWindows() {
        assertEquals(OsDetector.DetectedOS.UNKNOWN,
                OsDetector.DetectedOS.fromWindowsOutput("Linux"));
    }

    @Test
    public void testFromWindowsOutputEmpty() {
        assertEquals(OsDetector.DetectedOS.UNKNOWN,
                OsDetector.DetectedOS.fromWindowsOutput(""));
    }

    // ── DetectedOS properties ──────────────────────────────────────────

    @Test
    public void testLinuxProperties() {
        assertEquals("linux", OsDetector.DetectedOS.LINUX.getCode());
        assertEquals("Linux", OsDetector.DetectedOS.LINUX.getDisplayName());
    }

    @Test
    public void testMacOsProperties() {
        assertEquals("macos", OsDetector.DetectedOS.MACOS.getCode());
        assertEquals("macOS", OsDetector.DetectedOS.MACOS.getDisplayName());
    }

    @Test
    public void testWindowsProperties() {
        assertEquals("windows", OsDetector.DetectedOS.WINDOWS.getCode());
        assertEquals("Windows", OsDetector.DetectedOS.WINDOWS.getDisplayName());
    }

    @Test
    public void testUnknownDefaultsToMacos() {
        assertEquals("macos", OsDetector.DetectedOS.UNKNOWN.getCode());
        assertEquals("macOS", OsDetector.DetectedOS.UNKNOWN.getDisplayName());
    }

    // ── Enum completeness ──────────────────────────────────────────────

    @Test
    public void testEnumHasFourValues() {
        assertEquals(4, OsDetector.DetectedOS.values().length);
    }
}
