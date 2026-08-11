package com.openterface.keymod.agent.core;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;
import android.content.SharedPreferences;

import com.openterface.keymod.agent.llm.LlmRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link AgentPromptBuilder}.
 */
@RunWith(RobolectricTestRunner.class)
public class AgentPromptBuilderTest {

    private AgentPromptBuilder builder;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        builder = new AgentPromptBuilder(context);
    }

    // ── Default state ──────────────────────────────────────────────────

    @Test
    public void testDefaultExecutionMode() {
        assertEquals("terminal", builder.getExecutionMode());
    }

    @Test
    public void testSetExecutionMode() {
        builder.setExecutionMode("hid");
        assertEquals("hid", builder.getExecutionMode());
    }

    @Test
    public void testSetExecutionModeMacro() {
        builder.setExecutionMode("macro");
        assertEquals("macro", builder.getExecutionMode());
    }

    // ── buildSystemPrompt() ────────────────────────────────────────────

    @Test
    public void testBuildSystemPromptTerminalMode() {
        builder.setExecutionMode("terminal");
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
        // Should contain terminal-related content (from fallback or assets)
        assertTrue("Terminal prompt should mention JSON or command",
                prompt.contains("JSON") || prompt.contains("command") || prompt.contains("terminal"));
    }

    @Test
    public void testBuildSystemPromptHidMode() {
        builder.setExecutionMode("hid");
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
        // HID prompt should mention keyboard/HID concepts
        assertTrue("HID prompt should mention HID or keyboard",
                prompt.contains("HID") || prompt.contains("keyboard") || prompt.contains("BLE"));
    }

    @Test
    public void testBuildSystemPromptMacroMode() {
        builder.setExecutionMode("macro");
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
    }

    @Test
    public void testBuildSystemPromptNeverReturnsEmpty() {
        for (String mode : new String[]{"terminal", "hid", "macro", "unknown"}) {
            builder.setExecutionMode(mode);
            String prompt = builder.buildSystemPrompt();
            assertNotNull("Prompt for mode '" + mode + "' should not be null", prompt);
            assertFalse("Prompt for mode '" + mode + "' should not be empty", prompt.isEmpty());
        }
    }

    // ── buildUserPrompt() ──────────────────────────────────────────────

    @Test
    public void testBuildUserPromptPassesThrough() {
        assertEquals("Hello world", builder.buildUserPrompt("Hello world"));
        assertEquals("Check disk space", builder.buildUserPrompt("Check disk space"));
    }

    // ── buildRequest() ─────────────────────────────────────────────────

    @Test
    public void testBuildRequestDefault() {
        LlmRequest request = builder.buildRequest("gpt-4o", "Check disk");
        assertNotNull(request);
        assertEquals("gpt-4o", request.model);
        assertEquals(0.3, request.temperature, 0.001);
        assertEquals(2048, request.maxTokens);
        // Should have at least system + user messages
        assertTrue(request.messages.size() >= 2);
    }

    @Test
    public void testBuildRequestHasSystemAndUserMessages() {
        LlmRequest request = builder.buildRequest("gpt-4o", "List files");
        assertEquals("system", request.messages.get(0).role);
        assertEquals("user", request.messages.get(1).role);
        assertEquals("List files", request.messages.get(1).content);
    }

    @Test
    public void testBuildRequestWithCustomPrompt() {
        String custom = "You are a custom assistant. {{TERMINAL_MODE_CONTEXT}}";
        LlmRequest request = builder.buildRequest("gpt-4o", "Hello", custom);

        assertNotNull(request);
        // Custom prompt should be used as system message (with placeholders replaced)
        String systemContent = request.messages.get(0).content;
        assertTrue("System should contain custom text",
                systemContent.contains("custom assistant"));
        // Placeholder should be replaced
        assertFalse("Placeholder should be replaced",
                systemContent.contains("{{TERMINAL_MODE_CONTEXT}}"));
    }

    @Test
    public void testBuildRequestWithEmptyCustomPromptUsesDefault() {
        LlmRequest request = builder.buildRequest("gpt-4o", "Hello", "");
        assertNotNull(request);
        // Empty string → use default system prompt
        assertTrue(request.messages.get(0).content.length() > 50);
    }

    @Test
    public void testBuildRequestWithNullCustomPromptUsesDefault() {
        LlmRequest request = builder.buildRequest("gpt-4o", "Hello", null);
        assertNotNull(request);
        assertTrue(request.messages.get(0).content.length() > 50);
    }

    // ── buildRetryPrompt() ─────────────────────────────────────────────

    @Test
    public void testBuildRetryPrompt() {
        List<String[]> failedSteps = Arrays.asList(
                new String[]{"ls -la", "Permission denied"},
                new String[]{"cat /etc/shadow", "No such file"});

        String prompt = builder.buildRetryPrompt("Check files", failedSteps, "Linux");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Check files"));
        assertTrue(prompt.contains("ls -la"));
        assertTrue(prompt.contains("Permission denied"));
        assertTrue(prompt.contains("cat /etc/shadow"));
        assertTrue(prompt.contains("Linux"));
        assertTrue(prompt.contains("alternative"));
    }

    @Test
    public void testBuildRetryPromptSingleFailure() {
        List<String[]> failedSteps = Collections.singletonList(
                new String[]{"badcommand", "command not found"});
        String prompt = builder.buildRetryPrompt("Run thing", failedSteps, "macOS");
        assertTrue(prompt.contains("macOS"));
        assertTrue(prompt.contains("badcommand"));
    }

    // ── buildSummarizePrompt() ─────────────────────────────────────────

    @Test
    public void testBuildSummarizePrompt() {
        List<String[]> results = Arrays.asList(
                new String[]{"df -h", "Filesystem  Size  Used  Avail"},
                new String[]{"free -m", "Mem:  16384  8192  8192"});

        String prompt = builder.buildSummarizePrompt("Check resources", results);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Check resources"));
        assertTrue(prompt.contains("df -h"));
        assertTrue(prompt.contains("Filesystem"));
        assertTrue(prompt.contains("free -m"));
        assertTrue(prompt.contains("summary"));
    }

    @Test
    public void testBuildSummarizePromptEmptyResults() {
        List<String[]> results = Arrays.asList();
        String prompt = builder.buildSummarizePrompt("Do nothing", results);
        assertTrue(prompt.contains("Do nothing"));
    }

    // ── buildTerminalModeContext() ─────────────────────────────────────

    @Test
    public void testBuildTerminalModeContextWithoutProfile() {
        // No active profile → should show HID context
        builder.setActiveProfile(null);
        builder.setExecutionMode("terminal");
        String context = builder.buildTerminalModeContext();
        assertNotNull(context);
        assertTrue("Should mention no terminal or HID",
                context.contains("No terminal") || context.contains("HID") || context.contains("not captured"));
    }

    @Test
    public void testBuildTerminalModeContextContainsMaxSteps() {
        String context = builder.buildTerminalModeContext();
        assertTrue("Should contain max steps info",
                context.contains("Max steps") || context.contains("steps"));
    }
}
