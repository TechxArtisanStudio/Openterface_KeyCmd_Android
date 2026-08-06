package com.openterface.keymod.agent.settings;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Unit tests for AIProvider data model.
 */
public class AIProviderTest {

    // ========== Factory Method Tests ==========

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
    public void testCreateOllamaLocal() {
        AIProvider provider = AIProvider.createOllamaLocal();
        assertNotNull("Ollama provider should not be null", provider);
        assertEquals("Ollama (Local)", provider.name);
        assertEquals("http://localhost:11434/v1", provider.apiBaseURL);
        assertEquals("llama3", provider.modelName);
        assertTrue("Ollama should have optional API key", provider.apiKeyOptional);
        assertNotNull("Provider should have an ID", provider.id);
    }

    @Test
    public void testCreateLocalQwen06B() {
        AIProvider provider = AIProvider.createLocalQwen06B();
        assertNotNull("Local Qwen 0.6B provider should not be null", provider);
        assertEquals("Local Qwen 0.6B", provider.name);
        assertEquals("local://qwen3-0.6b", provider.apiBaseURL);
        assertEquals("Qwen3-0.6B-4bit", provider.modelName);
        assertTrue("Local Qwen should have optional API key", provider.apiKeyOptional);
    }

    @Test
    public void testCreateLocalQwen17B() {
        AIProvider provider = AIProvider.createLocalQwen17B();
        assertNotNull("Local Qwen 1.7B provider should not be null", provider);
        assertEquals("Local Qwen 1.7B", provider.name);
        assertEquals("local://qwen3-1.7b", provider.apiBaseURL);
        assertEquals("Qwen3-1.7B-4bit", provider.modelName);
        assertTrue("Local Qwen should have optional API key", provider.apiKeyOptional);
    }

    // ========== Default Providers List ==========

    @Test
    public void testGetDefaultProviders() {
        List<AIProvider> providers = AIProvider.getDefaultProviders();
        assertNotNull("Default providers list should not be null", providers);
        assertEquals("Should have 10 default providers", 10, providers.size());
    }

    @Test
    public void testGetDefaultProvidersContainsAll() {
        List<AIProvider> providers = AIProvider.getDefaultProviders();

        boolean hasOpenAI = false, hasAnthropic = false, hasGoogle = false;
        boolean hasMistral = false, hasGroq = false, hasDashScope = false, hasDeepSeek = false;
        boolean hasOllama = false, hasLocalQwen06 = false, hasLocalQwen17 = false;

        for (AIProvider provider : providers) {
            switch (provider.name) {
                case "OpenAI": hasOpenAI = true; break;
                case "Anthropic": hasAnthropic = true; break;
                case "Google": hasGoogle = true; break;
                case "Mistral": hasMistral = true; break;
                case "Groq": hasGroq = true; break;
                case "DashScope": hasDashScope = true; break;
                case "DeepSeek": hasDeepSeek = true; break;
                case "Ollama (Local)": hasOllama = true; break;
                case "Local Qwen 0.6B": hasLocalQwen06 = true; break;
                case "Local Qwen 1.7B": hasLocalQwen17 = true; break;
            }
        }

        assertTrue("Should have OpenAI", hasOpenAI);
        assertTrue("Should have Anthropic", hasAnthropic);
        assertTrue("Should have Google", hasGoogle);
        assertTrue("Should have Mistral", hasMistral);
        assertTrue("Should have Groq", hasGroq);
        assertTrue("Should have DashScope", hasDashScope);
        assertTrue("Should have DeepSeek", hasDeepSeek);
        assertTrue("Should have Ollama (Local)", hasOllama);
        assertTrue("Should have Local Qwen 0.6B", hasLocalQwen06);
        assertTrue("Should have Local Qwen 1.7B", hasLocalQwen17);
    }

    // ========== Constructor Tests ==========

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

    // ========== isLocalModel() Tests ==========

    @Test
    public void testIsLocalModel_true_localProtocol() {
        AIProvider provider = AIProvider.createLocalQwen06B();
        assertTrue("local:// URL should be detected as local model", provider.isLocalModel());
    }

    @Test
    public void testIsLocalModel_true_localProtocol17B() {
        AIProvider provider = AIProvider.createLocalQwen17B();
        assertTrue("local:// URL should be detected as local model", provider.isLocalModel());
    }

    @Test
    public void testIsLocalModel_false_httpURL() {
        AIProvider provider = AIProvider.createOpenAI();
        assertFalse("HTTP URL should not be detected as local model", provider.isLocalModel());
    }

    @Test
    public void testIsLocalModel_false_localhost() {
        AIProvider provider = AIProvider.createOllamaLocal();
        assertFalse("localhost URL should not be detected as local model", provider.isLocalModel());
    }

    @Test
    public void testIsLocalModel_false_nullURL() {
        AIProvider provider = new AIProvider("Test", null, "model", false);
        assertFalse("Null URL should not crash and return false", provider.isLocalModel());
    }

    @Test
    public void testIsLocalModel_false_emptyURL() {
        AIProvider provider = new AIProvider("Test", "", "model", false);
        assertFalse("Empty URL should return false", provider.isLocalModel());
    }

    // ========== isValid() Tests ==========

    @Test
    public void testIsValid_valid() {
        AIProvider provider = new AIProvider("Test", "https://test.com", "model", false);
        assertTrue("Provider with all fields should be valid", provider.isValid());
    }

    @Test
    public void testIsValid_emptyName() {
        AIProvider provider = new AIProvider("", "https://test.com", "model", false);
        assertFalse("Provider with empty name should be invalid", provider.isValid());
    }

    @Test
    public void testIsValid_nullName() {
        AIProvider provider = new AIProvider();
        provider.name = null;
        provider.apiBaseURL = "https://test.com";
        provider.modelName = "model";
        assertFalse("Provider with null name should be invalid", provider.isValid());
    }

    @Test
    public void testIsValid_emptyURL() {
        AIProvider provider = new AIProvider("Test", "", "model", false);
        assertFalse("Provider with empty URL should be invalid", provider.isValid());
    }

    @Test
    public void testIsValid_emptyModel() {
        AIProvider provider = new AIProvider("Test", "https://test.com", "", false);
        assertFalse("Provider with empty model should be invalid", provider.isValid());
    }

    @Test
    public void testIsValid_whitespaceOnly() {
        AIProvider provider = new AIProvider("  ", "https://test.com", "model", false);
        assertFalse("Provider with whitespace-only name should be invalid", provider.isValid());
    }

    @Test
    public void testIsValid_allWhitespace() {
        AIProvider provider = new AIProvider("  ", "  ", "  ", false);
        assertFalse("Provider with all whitespace fields should be invalid", provider.isValid());
    }

    // ========== copy() Tests ==========

    @Test
    public void testCopy_fieldsMatch() {
        AIProvider original = AIProvider.createOpenAI();
        AIProvider copy = original.copy();

        assertEquals("Name should match", original.name, copy.name);
        assertEquals("URL should match", original.apiBaseURL, copy.apiBaseURL);
        assertEquals("Model should match", original.modelName, copy.modelName);
        assertEquals("apiKeyOptional should match", original.apiKeyOptional, copy.apiKeyOptional);
    }

    @Test
    public void testCopy_uniqueId() {
        AIProvider original = AIProvider.createOpenAI();
        AIProvider copy = original.copy();

        assertNotEquals("Copy should have a different ID", original.id, copy.id);
    }

    @Test
    public void testCopy_notSameReference() {
        AIProvider original = AIProvider.createOpenAI();
        AIProvider copy = original.copy();

        assertNotSame("Copy should be a different object", original, copy);
    }

    @Test
    public void testCopy_localProvider() {
        AIProvider original = AIProvider.createLocalQwen06B();
        AIProvider copy = original.copy();

        assertEquals("Local Qwen 0.6B", copy.name);
        assertEquals("local://qwen3-0.6b", copy.apiBaseURL);
        assertTrue("Copy should preserve apiKeyOptional", copy.apiKeyOptional);
        assertTrue("Copy should preserve isLocalModel", copy.isLocalModel());
    }

    // ========== toString() Test ==========

    @Test
    public void testToString() {
        AIProvider provider = AIProvider.createOpenAI();
        String str = provider.toString();
        assertTrue("toString should contain name", str.contains("OpenAI"));
        assertTrue("toString should contain URL", str.contains("api.openai.com"));
        assertTrue("toString should contain ID", str.contains(provider.id));
    }
}
