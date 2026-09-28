package com.repopilot.app.core

data class AiProviderSpec(
    val id: String,
    val label: String,
    val defaultModel: String,
    val requiresEndpoint: Boolean = false,
)

object AiProviderCatalog {
    val all = listOf(
        AiProviderSpec("gemini", "Gemini", "gemini-2.5-flash"),
        AiProviderSpec("groq", "Groq", "llama-3.3-70b-versatile"),
        AiProviderSpec("openrouter", "OpenRouter", "openai/gpt-oss-20b:free"),
        AiProviderSpec("custom", "Custom", "", requiresEndpoint = true),
    )

    fun byId(value: String): AiProviderSpec =
        all.firstOrNull { it.id == value.trim().lowercase() } ?: all.first()

    fun keyId(provider: String): String = "ai-key-${byId(provider).id}"
}
