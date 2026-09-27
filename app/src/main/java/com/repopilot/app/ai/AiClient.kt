package com.repopilot.app.ai

import com.repopilot.app.core.PatchValidator
import com.repopilot.app.core.SecretRedactor
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AiConfig(val provider: String, val model: String, val endpoint: String = "")

object UnifiedDiffExtractor {
    fun extract(text: String): String? {
        val clean = SecretRedactor.redact(text)
        val fenced = Regex("```(?:diff)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: clean.substringAfter("diff --git", "").let { if (it.isBlank()) null else "diff --git$it" }
        return candidate?.takeIf { PatchValidator.validate(it).isSuccess }
    }
}

object AiClient {
    fun request(config: AiConfig, apiKey: String, prompt: String): Result<String> = runCatching {
        val provider = config.provider.lowercase()
        val endpoint = when (provider) {
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/${config.model.ifBlank { "gemini-2.5-flash" }}:generateContent?key=$apiKey"
            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            else -> config.endpoint.ifBlank { error("Custom endpoint required") }
        }
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 45_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (provider != "gemini") setRequestProperty("Authorization", "Bearer $apiKey")
            if (provider == "openrouter") setRequestProperty("X-Title", "RepoPilot")
        }
        val body = if (provider == "gemini") {
            JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        } else {
            JSONObject().put("model", config.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).put("temperature", 0.1)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) error("HTTP $code: ${SecretRedactor.redact(raw).take(500)}")
        val json = JSONObject(raw)
        if (provider == "gemini") json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        else json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
