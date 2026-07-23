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
}
