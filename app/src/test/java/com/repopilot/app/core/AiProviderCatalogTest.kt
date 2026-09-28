package com.repopilot.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {
    @Test fun providerIdsAreNormalizedAndUnknownFallsBackToGemini() {
        assertEquals("groq", AiProviderCatalog.byId(" GROQ ").id)
        assertEquals("gemini", AiProviderCatalog.byId("unknown").id)
    }

    @Test fun apiKeysAreSeparatedPerProvider() {
        assertEquals("ai-key-gemini", AiProviderCatalog.keyId("gemini"))
        assertEquals("ai-key-openrouter", AiProviderCatalog.keyId("openrouter"))
        assertTrue(AiProviderCatalog.keyId("gemini") != AiProviderCatalog.keyId("groq"))
    }
}
