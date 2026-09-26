package dev.terminox.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal Gemini REST client with function calling. */
object Gemini {
    private val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()

    /** Returns the model's content object ({role, parts}). Throws with the API's message on failure. */
    suspend fun generate(key: String, model: String, system: String, contents: JSONArray, tools: JSONArray): JSONObject {
        // Free-tier keys hit 429 quickly; back off and retry instead of failing the whole task.
        var wait = 3_000L
        repeat(3) {
            try { return once(key, model, system, contents, tools) } catch (e: Busy) { kotlinx.coroutines.delay(wait); wait *= 2 }
        }
        // Still overloaded: fall back to a stable model rather than giving up.
        return once(key, if (model == FALLBACK) model else FALLBACK, system, contents, tools)
    }

    private const val FALLBACK = "gemini-3.8-flash"

    private class Busy : Exception()

    private suspend fun once(key: String, model: String, system: String, contents: JSONArray, tools: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                // New "AQ." keys only work in this header, not as ?key=
                .header("x-goog-api-key", key.trim())
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (resp.code == 429 || resp.code == 503) throw Busy()
                if (!resp.isSuccessful) {
                    val msg = json?.optJSONObject("error")?.optString("message") ?: "HTTP ${resp.code}"
                    error(msg)
                }
                json?.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?: error("Empty reply (${json?.optJSONObject("promptFeedback")?.optString("blockReason") ?: "no candidates"})")
            }
        }

    fun fn(name: String, description: String, vararg params: Triple<String, String, String>, required: List<String> = emptyList()) =
        JSONObject().put("name", name).put("description", description).apply {
            if (params.isNotEmpty()) {
                val props = JSONObject()
                params.forEach { (n, type, desc) -> props.put(n, JSONObject().put("type", type).put("description", desc)) }
                put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)))
            }
        }
}
