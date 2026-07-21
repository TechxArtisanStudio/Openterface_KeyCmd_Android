package com.openterface.keymod.agent.settings;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Unit tests for AIProvider data model.
 */
public class AIProviderTest {

    @Test
    public void testCreateOpenAI() {
        AIProvider provider = AIProvider.createOpenAI();
        assertNotNull("OpenAI provider should not be null", provider);
        assertEquals("OpenAI", provider.name);
        assertEquals("https://api.openai.com/v1", provider.apiBaseURL);
        assertEquals("gpt-4o", provider.modelName);
        assertFalse("OpenAI should require API key", provider.apiKeyOptional);
        assertNotNull("Provider should have an ID", provider.id);
    }

    @Test
    public void testCreateAnthropic() {
        AIProvider provider = AIProvider.createAnthropic();
        assertNotNull("Anthropic provider should not be null", provider);
        assertEquals("Anthropic", provider.name);
        assertEquals("https://api.anthropic.com/v1", provider.apiBaseURL);
        assertEquals("claude-3-5-sonnet-20241022", provider.modelName);
        assertFalse("Anthropic should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateGoogle() {
        AIProvider provider = AIProvider.createGoogle();
        assertNotNull("Google provider should not be null", provider);
        assertEquals("Google", provider.name);
        assertEquals("https://generativelanguage.googleapis.com/v1beta", provider.apiBaseURL);
        assertEquals("gemini-2.0-flash", provider.modelName);
        assertFalse("Google should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateMistral() {
        AIProvider provider = AIProvider.createMistral();
        assertNotNull("Mistral provider should not be null", provider);
        assertEquals("Mistral", provider.name);
        assertEquals("https://api.mistral.ai/v1", provider.apiBaseURL);
        assertEquals("mistral-large-latest", provider.modelName);
        assertFalse("Mistral should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateGroq() {
        AIProvider provider = AIProvider.createGroq();
        assertNotNull("Groq provider should not be null", provider);
        assertEquals("Groq", provider.name);
        assertEquals("https://api.groq.com/openai/v1", provider.apiBaseURL);
        assertEquals("llama-3.3-70b-versatile", provider.modelName);
        assertFalse("Groq should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateDashScope() {
        AIProvider provider = AIProvider.createDashScope();
        assertNotNull("DashScope provider should not be null", provider);
        assertEquals("DashScope", provider.name);
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", provider.apiBaseURL);
        assertEquals("qwen-max", provider.modelName);
        assertFalse("DashScope should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateDeepSeek() {
        AIProvider provider = AIProvider.createDeepSeek();
        assertNotNull("DeepSeek provider should not be null", provider);
        assertEquals("DeepSeek", provider.name);
        assertEquals("https://api.deepseek.com/v1", provider.apiBaseURL);
        assertEquals("deepseek-chat", provider.modelName);
        assertFalse("DeepSeek should require API key", provider.apiKeyOptional);
    }

    @Test
    public void testGetDefaultProviders() {
        List<AIProvider> providers = AIProvider.getDefaultProviders();
        assertNotNull("Default providers list should not be null", providers);
        assertEquals("Should have 7 default providers", 7, providers.size());

        // Verify all providers are present
        boolean hasOpenAI = false, hasAnthropic = false, hasGoogle = false;
        boolean hasMistral = false, hasGroq = false, hasDashScope = false, hasDeepSeek = false;

        for (AIProvider provider : providers) {
            switch (provider.name) {
                case "OpenAI": hasOpenAI = true; break;
                case "Anthropic": hasAnthropic = true; break;
                case "Google": hasGoogle = true; break;
                case "Mistral": hasMistral = true; break;
                case "Groq": hasGroq = true; break;
                case "DashScope": hasDashScope = true; break;
                case "DeepSeek": hasDeepSeek = true; break;
            }
        }

        assertTrue("Should have OpenAI", hasOpenAI);
        assertTrue("Should have Anthropic", hasAnthropic);
        assertTrue("Should have Google", hasGoogle);
        assertTrue("Should have Mistral", hasMistral);
        assertTrue("Should have Groq", hasGroq);
        assertTrue("Should have DashScope", hasDashScope);
        assertTrue("Should have DeepSeek", hasDeepSeek);
    }

    @Test
    public void testConstructor() {
        AIProvider provider = new AIProvider("TestProvider", "https://test.com", "test-model", true);
        assertNotNull("Provider should not be null", provider);
        assertEquals("TestProvider", provider.name);
        assertEquals("https://test.com", provider.apiBaseURL);
        assertEquals("test-model", provider.modelName);
        assertTrue("Provider should have optional API key", provider.apiKeyOptional);
        assertNotNull("Provider should have an ID", provider.id);
    }

    @Test
    public void testUniqueIds() {
        AIProvider provider1 = AIProvider.createOpenAI();
        AIProvider provider2 = AIProvider.createOpenAI();

        assertNotEquals("Each provider should have a unique ID",
                provider1.id, provider2.id);
    }
}
