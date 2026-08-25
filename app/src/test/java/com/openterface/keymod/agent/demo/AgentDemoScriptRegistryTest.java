package com.openterface.keymod.agent.demo;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link AgentDemoScriptRegistry}.
 */
public class AgentDemoScriptRegistryTest {

    // ── all() ──────────────────────────────────────────────────────────

    @Test
    public void testAllReturnsThreeScripts() {
        List<AgentDemoScript> scripts = AgentDemoScriptRegistry.all();
        assertNotNull(scripts);
        assertEquals(3, scripts.size());
    }

    @Test
    public void testAllContainsExpectedIds() {
        List<AgentDemoScript> scripts = AgentDemoScriptRegistry.all();
        assertEquals(AgentDemoScript.ID_HERO_MIXED, scripts.get(0).id);
        assertEquals(AgentDemoScript.ID_CLI_ONLY, scripts.get(1).id);
        assertEquals(AgentDemoScript.ID_MACRO_ONLY, scripts.get(2).id);
    }

    // ── get() ──────────────────────────────────────────────────────────

    @Test
    public void testGetHeroMixed() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_HERO_MIXED);
        assertNotNull(script);
        assertEquals(AgentDemoScript.ID_HERO_MIXED, script.id);
    }

    @Test
    public void testGetCliOnly() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_CLI_ONLY);
        assertNotNull(script);
        assertEquals(AgentDemoScript.ID_CLI_ONLY, script.id);
    }

    @Test
    public void testGetMacroOnly() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_MACRO_ONLY);
        assertNotNull(script);
        assertEquals(AgentDemoScript.ID_MACRO_ONLY, script.id);
    }

    @Test
    public void testGetNull() {
        assertNull(AgentDemoScriptRegistry.get(null));
    }

    @Test
    public void testGetNonexistent() {
        assertNull(AgentDemoScriptRegistry.get("nonexistent_id"));
    }

    // ── defaultScript() ────────────────────────────────────────────────

    @Test
    public void testDefaultScriptIsHeroMixed() {
        AgentDemoScript script = AgentDemoScriptRegistry.defaultScript();
        assertNotNull(script);
        assertEquals(AgentDemoScript.ID_HERO_MIXED, script.id);
    }

    // ── Script content integrity ───────────────────────────────────────

    @Test
    public void testHeroMixedHasPlanSteps() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_HERO_MIXED);
        assertFalse(script.planSteps.isEmpty());
        assertTrue(script.hasCliExecution);
        assertTrue(script.hasMacroExecution);
    }

    @Test
    public void testCliOnlyHasTerminalLines() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_CLI_ONLY);
        assertFalse(script.terminalOutputLines.isEmpty());
        assertTrue(script.hasCliExecution);
        assertFalse(script.hasMacroExecution);
        assertTrue(script.macroChecklist.isEmpty());
    }

    @Test
    public void testMacroOnlyHasMacroChecklist() {
        AgentDemoScript script = AgentDemoScriptRegistry.get(AgentDemoScript.ID_MACRO_ONLY);
        assertFalse(script.macroChecklist.isEmpty());
        assertFalse(script.macroStatusChips.isEmpty());
        assertFalse(script.hasCliExecution);
        assertTrue(script.hasMacroExecution);
        assertTrue(script.terminalOutputLines.isEmpty());
    }

    @Test
    public void testAllScriptsHaveNonEmptyFields() {
        for (AgentDemoScript script : AgentDemoScriptRegistry.all()) {
            assertFalse("pickerTitle should not be empty: " + script.id,
                    script.pickerTitle.isEmpty());
            assertFalse("pickerTagline should not be empty: " + script.id,
                    script.pickerTagline.isEmpty());
            assertFalse("userPrompt should not be empty: " + script.id,
                    script.userPrompt.isEmpty());
            assertFalse("assistantIntro should not be empty: " + script.id,
                    script.assistantIntro.isEmpty());
            assertFalse("summaryMessage should not be empty: " + script.id,
                    script.summaryMessage.isEmpty());
            assertFalse("planSteps should not be empty: " + script.id,
                    script.planSteps.isEmpty());
        }
    }
}
