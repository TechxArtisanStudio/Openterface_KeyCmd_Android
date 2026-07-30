package com.openterface.keymod.agent.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for AgentPlanParser.
 */
public class AgentPlanParserTest {

    private AgentPlanParser parser;

    @Before
    public void setUp() {
        parser = new AgentPlanParser();
    }

    // ── Valid plan parsing ───────────────────────────────────────────────

    @Test
    public void testParseValidPlan() throws Exception {
        String json = "{"
                + "\"summary\": \"Check disk space\","
                + "\"steps\": ["
                + "  {\"title\": \"Check root partition\", \"command\": \"df -h /\", \"kind\": \"terminal\"}"
                + "]"
                + "}";

        AgentPlan plan = parser.parse(json);

        assertEquals("Check disk space", plan.summary);
        assertEquals(1, plan.steps.size());
        assertEquals("Check root partition", plan.steps.get(0).title);
        assertEquals("df -h /", plan.steps.get(0).command);
        assertEquals("terminal", plan.steps.get(0).kind);
        assertEquals(0, plan.steps.get(0).index);
    }

    @Test
    public void testParsePlanWithMultipleSteps() throws Exception {
        String json = "{"
                + "\"summary\": \"Full cleanup\","
                + "\"steps\": ["
                + "  {\"title\": \"Check disk\", \"command\": \"df -h\", \"kind\": \"terminal\"},"
                + "  {\"title\": \"Find large files\", \"command\": \"du -sh /tmp/*\", \"kind\": \"terminal\"},"
                + "  {\"title\": \"Clean up\", \"command\": \"rm -rf /tmp/old\", \"kind\": \"terminal\"}"
                + "]"
                + "}";

        AgentPlan plan = parser.parse(json);

        assertEquals("Full cleanup", plan.summary);
        assertEquals(3, plan.steps.size());
        assertEquals(0, plan.steps.get(0).index);
        assertEquals(1, plan.steps.get(1).index);
        assertEquals(2, plan.steps.get(2).index);
    }

    @Test
    public void testParsePlanWithTerminalSteps() throws Exception {
        String json = "{"
                + "\"summary\": \"List files\","
                + "\"steps\": ["
                + "  {\"title\": \"List home\", \"command\": \"ls -la ~\", \"kind\": \"terminal\"}"
                + "]"
                + "}";

        AgentPlan plan = parser.parse(json);
        assertEquals("terminal", plan.steps.get(0).kind);
        assertEquals("ls -la ~", plan.steps.get(0).command);
        assertNull(plan.steps.get(0).keys);
        assertNull(plan.steps.get(0).macroId);
    }

    @Test
    public void testParsePlanWithHidSteps() throws Exception {
        String json = "{"
                + "\"summary\": \"Save file\","
                + "\"steps\": ["
                + "  {\"title\": \"Press save\", \"keys\": \"<CMD>s</CMD>\", \"kind\": \"hid\"}"
                + "]"
                + "}";

        AgentPlan plan = parser.parse(json);
        assertEquals("hid", plan.steps.get(0).kind);
        assertEquals("<CMD>s</CMD>", plan.steps.get(0).keys);
        assertNull(plan.steps.get(0).command);
    }

    @Test
    public void testParsePlanWithMacroSteps() throws Exception {
        String json = "{"
                + "\"summary\": \"Run cleanup macro\","
                + "\"steps\": ["
                + "  {\"title\": \"Cleanup\", \"macroId\": \"cleanup_macro_1\", \"kind\": \"macro\"}"
                + "]"
                + "}";

        AgentPlan plan = parser.parse(json);
        assertEquals("macro", plan.steps.get(0).kind);
        assertEquals("cleanup_macro_1", plan.steps.get(0).macroId);
        assertNull(plan.steps.get(0).command);
        assertNull(plan.steps.get(0).keys);
    }

    // ── Error cases ──────────────────────────────────────────────────────

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseMalformedJson() throws Exception {
        parser.parse("{this is not valid json");
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseMissingSteps() throws Exception {
        parser.parse("{\"summary\": \"No steps here\"}");
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseEmptySteps() throws Exception {
        parser.parse("{\"summary\": \"Empty steps\", \"steps\": []}");
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseTerminalStepMissingCommand() throws Exception {
        String json = "{"
                + "\"summary\": \"Bad step\","
                + "\"steps\": [{\"title\": \"No command\", \"kind\": \"terminal\"}]"
                + "}";
        parser.parse(json);
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseHidStepMissingKeys() throws Exception {
        String json = "{"
                + "\"summary\": \"Bad step\","
                + "\"steps\": [{\"title\": \"No keys\", \"kind\": \"hid\"}]"
                + "}";
        parser.parse(json);
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseMacroStepMissingMacroId() throws Exception {
        String json = "{"
                + "\"summary\": \"Bad step\","
                + "\"steps\": [{\"title\": \"No macro\", \"kind\": \"macro\"}]"
                + "}";
        parser.parse(json);
    }

    // ── Code fence stripping ─────────────────────────────────────────────

    @Test
    public void testStripCodeFences() throws Exception {
        String json = "```json\n"
                + "{\"summary\": \"Fenced\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]}\n"
                + "```";

        AgentPlan plan = parser.parse(json);
        assertEquals("Fenced", plan.summary);
        assertEquals(1, plan.steps.size());
    }

    @Test
    public void testStripCodeFencesWithoutLanguage() throws Exception {
        String json = "```\n"
                + "{\"summary\": \"Fenced\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]}\n"
                + "```";

        AgentPlan plan = parser.parse(json);
        assertEquals("Fenced", plan.summary);
    }

    /**
     * Regression test: some models (e.g. qwen2.5) output ```json { ... }```
     * with no newline after the opening fence. Must still parse.
     */
    @Test
    public void testJsonFenceNoNewlineAfterTag() throws Exception {
        String json = "```json {"
                + "\"intro\": \"Query hostname\","
                + "\"steps\": [{\"kind\": \"terminal\", \"title\": \"Check\", \"payload\": \"hostname\"}]"
                + "} ```";

        AgentPlan plan = parser.parse(json);
        assertEquals("Query hostname", plan.summary);
        assertEquals(1, plan.steps.size());
        assertEquals("hostname", plan.steps.get(0).command);
    }

    /**
     * Regression test: model returns fenced JSON with extra whitespace
     * and no trailing newline before closing fence.
     */
    @Test
    public void testJsonFenceWithExtraWhitespace() throws Exception {
        String json = "```json\n"
                + "  {\"summary\": \"Test\", \"steps\": [{\"kind\": \"terminal\", \"title\": \"S\", \"command\": \"ls\"}]}  \n"
                + "```";

        AgentPlan plan = parser.parse(json);
        assertEquals("Test", plan.summary);
    }

    // ── LlmResponse parsing ──────────────────────────────────────────────

    @Test
    public void testParseFromLlmResponse() throws Exception {
        String content = "{\"summary\": \"Test\", \"steps\": [{\"title\": \"Step 1\", \"command\": \"echo hi\", \"kind\": \"terminal\"}]}";
        com.openterface.keymod.agent.llm.LlmResponse response =
                new com.openterface.keymod.agent.llm.LlmResponse(content, "stop", 10, 20);

        AgentPlan plan = parser.parseFromResponse(response);
        assertEquals("Test", plan.summary);
        assertEquals(1, plan.steps.size());
    }

    @Test(expected = AgentPlanParser.PlanParseException.class)
    public void testParseFromLlmResponseEmptyContent() throws Exception {
        com.openterface.keymod.agent.llm.LlmResponse response =
                new com.openterface.keymod.agent.llm.LlmResponse("", "stop", 10, 20);
        parser.parseFromResponse(response);
    }

    // ── Strip code fences static method ──────────────────────────────────

    @Test
    public void testStripCodeFencesStatic() {
        assertEquals("hello", AgentPlanParser.stripCodeFences("```json\nhello\n```"));
        assertEquals("hello", AgentPlanParser.stripCodeFences("```\nhello\n```"));
        assertEquals("hello", AgentPlanParser.stripCodeFences("hello"));
        assertEquals("hello", AgentPlanParser.stripCodeFences("  hello  "));
    }

    // ── JSON field aliases ───────────────────────────────────────────────

    @Test
    public void testParseWithIntroAliasForSummary() throws Exception {
        // "intro" should work as alias for "summary"
        String json = "{"
                + "\"intro\": \"Intro description\","
                + "\"steps\": [{"
                + "  \"title\": \"Step\", \"payload\": \"ls -la\", \"kind\": \"terminal\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("Intro description", plan.summary);
        assertEquals("ls -la", plan.steps.get(0).command);
    }

    @Test
    public void testParseWithPayloadAliasForCommand() throws Exception {
        String json = "{"
                + "\"summary\": \"Test\","
                + "\"steps\": [{"
                + "  \"title\": \"Step\", \"payload\": \"echo hello\", \"kind\": \"terminal\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("echo hello", plan.steps.get(0).command);
    }

    @Test
    public void testParseWithShellAliasForCommand() throws Exception {
        String json = "{"
                + "\"summary\": \"Test\","
                + "\"steps\": [{"
                + "  \"title\": \"Step\", \"shell\": \"pwd\", \"kind\": \"terminal\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("pwd", plan.steps.get(0).command);
    }

    @Test
    public void testParseWithMacroIdAlias() throws Exception {
        // "macro_id" alternate name
        String json = "{"
                + "\"summary\": \"Test\","
                + "\"steps\": [{"
                + "  \"title\": \"Run\", \"macro_id\": \"my-macro\", \"kind\": \"macro\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("my-macro", plan.steps.get(0).macroId);
    }

    @Test
    public void testParseWithActionsArrayAlias() throws Exception {
        // "actions" as alternate for "steps"
        String json = "{"
                + "\"summary\": \"Test\","
                + "\"actions\": [{"
                + "  \"title\": \"Step\", \"command\": \"date\", \"kind\": \"terminal\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals(1, plan.steps.size());
        assertEquals("date", plan.steps.get(0).command);
    }

    @Test
    public void testParseWithPlanArrayAlias() throws Exception {
        // "plan" as alternate for "steps"
        String json = "{"
                + "\"summary\": \"Test\","
                + "\"plan\": [{"
                + "  \"title\": \"Step\", \"command\": \"whoami\", \"kind\": \"terminal\""
                + "}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals(1, plan.steps.size());
        assertEquals("whoami", plan.steps.get(0).command);
    }

    // ─ 5-level fallback parsing ─────────────────────────────────────────

    @Test
    public void testParseWithThinkBlock() throws Exception {
        // Reasoning models wrap thoughts in <think>...</think>
        String json = "<think>Let me think about this...</think>\n"
                + "```json\n"
                + "{\"summary\": \"After thinking\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]}\n"
                + "```";

        AgentPlan plan = parser.parse(json);
        assertEquals("After thinking", plan.summary);
    }

    @Test
    public void testParseWithExtraTextAroundJson() throws Exception {
        // LLM sometimes adds prose before/after JSON
        String json = "Here is the plan you requested:\n\n"
                + "```json\n"
                + "{\"summary\": \"Wrapped\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]}\n"
                + "```\n\n"
                + "Let me know if you need anything else!";

        AgentPlan plan = parser.parse(json);
        assertEquals("Wrapped", plan.summary);
    }

    @Test
    public void testParseBraceExtractionFallback() throws Exception {
        // Level 4: extract between first { and last }
        String json = "Some text {\"summary\": \"Braces\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]} more text";

        AgentPlan plan = parser.parse(json);
        assertEquals("Braces", plan.summary);
    }

    @Test
    public void testParseNoSummaryAutoGenerated() throws Exception {
        // When summary is missing, auto-generate
        String json = "{\"steps\": [{\"title\": \"Step\", \"command\": \"ls\", \"kind\": \"terminal\"}]}";

        AgentPlan plan = parser.parse(json);
        assertNotNull(plan.summary);
        assertTrue(plan.summary.contains("1"));
    }

    @Test
    public void testParseDefaultKindToTerminal() throws Exception {
        // When kind is missing, default to "terminal" if command exists
        String json = "{\"summary\": \"Test\", \"steps\": [{\"title\": \"Step\", \"command\": \"ls\"}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("terminal", plan.steps.get(0).kind);
    }

    @Test
    public void testParseAutoGeneratedTitle() throws Exception {
        // When title is missing, auto-generate "Step N"
        String json = "{\"summary\": \"Test\", \"steps\": [{\"command\": \"ls\", \"kind\": \"terminal\"}]}";

        AgentPlan plan = parser.parse(json);
        assertEquals("Step 1", plan.steps.get(0).title);
    }
}
