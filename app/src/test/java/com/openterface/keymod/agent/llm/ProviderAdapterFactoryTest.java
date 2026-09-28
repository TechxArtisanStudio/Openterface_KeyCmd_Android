package com.openterface.keymod.agent.llm;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ProviderAdapterFactory}.
 */
public class ProviderAdapterFactoryTest {

    // ── get() returns correct adapter types ────────────────────────────

    @Test
    public void testGetOpenAI() {
        ProviderAdapter adapter = ProviderAdapterFactory.get("OpenAI");
        assertNotNull(adapter);
        assertEquals("OpenAI", adapter.name());
    }

    @Test
    public void testGetAnthropic() {
        ProviderAdapter adapter = ProviderAdapterFactory.get("Anthropic");
        assertNotNull(adapter);
        assertEquals("Anthropic", adapter.name());
    }

    @Test
    public void testGetGoogle() {
        ProviderAdapter adapter = ProviderAdapterFactory.get("Google");
        assertNotNull(adapter);
        assertEquals("Google", adapter.name());
    }

    // ── OpenAI-compatible providers share same adapter ─────────────────

    @Test
    public void testMistralSharesOpenAIAdapter() {
        assertSame(ProviderAdapterFactory.get("OpenAI"),
                ProviderAdapterFactory.get("Mistral"));
    }

    @Test
    public void testGroqSharesOpenAIAdapter() {
        assertSame(ProviderAdapterFactory.get("OpenAI"),
                ProviderAdapterFactory.get("Groq"));
    }

    @Test
    public void testDashScopeSharesOpenAIAdapter() {
        assertSame(ProviderAdapterFactory.get("OpenAI"),
                ProviderAdapterFactory.get("DashScope"));
    }

    @Test
    public void testDeepSeekSharesOpenAIAdapter() {
        assertSame(ProviderAdapterFactory.get("OpenAI"),
                ProviderAdapterFactory.get("DeepSeek"));
    }

    @Test
    public void testCustomSharesOpenAIAdapter() {
        assertSame(ProviderAdapterFactory.get("OpenAI"),
                ProviderAdapterFactory.get("Custom"));
    }

    // ── get() with unknown name throws ─────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testGetUnknownProviderThrows() {
        ProviderAdapterFactory.get("NonExistent");
    }

    // ── isSupported() ──────────────────────────────────────────────────

    @Test
    public void testIsSupportedKnownProviders() {
        assertTrue(ProviderAdapterFactory.isSupported("OpenAI"));
        assertTrue(ProviderAdapterFactory.isSupported("Anthropic"));
        assertTrue(ProviderAdapterFactory.isSupported("Google"));
        assertTrue(ProviderAdapterFactory.isSupported("Mistral"));
        assertTrue(ProviderAdapterFactory.isSupported("Groq"));
        assertTrue(ProviderAdapterFactory.isSupported("DashScope"));
        assertTrue(ProviderAdapterFactory.isSupported("DeepSeek"));
        assertTrue(ProviderAdapterFactory.isSupported("Custom"));
    }

    @Test
    public void testIsSupportedUnknownReturnsFalse() {
        assertFalse(ProviderAdapterFactory.isSupported("NonExistent"));
    }

    // ── ADAPTER_NAMES ──────────────────────────────────────────────────

    @Test
    public void testAdapterNamesLength() {
        assertEquals(8, ProviderAdapterFactory.ADAPTER_NAMES.length);
    }

    @Test
    public void testAdapterNamesOrder() {
        String[] names = ProviderAdapterFactory.ADAPTER_NAMES;
        assertEquals("OpenAI", names[0]);
        assertEquals("Anthropic", names[1]);
        assertEquals("Google", names[2]);
        assertEquals("Mistral", names[3]);
        assertEquals("Groq", names[4]);
        assertEquals("DashScope", names[5]);
        assertEquals("DeepSeek", names[6]);
        assertEquals("Custom", names[7]);
    }
}
