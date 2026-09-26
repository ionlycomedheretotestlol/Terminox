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
    /** Tried in order when a model is overloaded, rate-limited or unavailable. 3.6 is the last stop. */
    val CHAIN = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash")

    suspend fun generate(key: String, model: String, system: String, contents: JSONArray, tools: JSONArray): JSONObject {
        // Start at the chosen model and walk down the chain (3.8 → 3.7 → 3.6).
        val order = if (model in CHAIN) CHAIN.drop(CHAIN.indexOf(model)) else listOf(model) + CHAIN
        var last: Throwable? = null
        for (m in order) {
            var wait = 2_000L
            repeat(2) {
                try { return once(key, m, system, contents, tools) }
                catch (e: Busy) { last = e; kotlinx.coroutines.delay(wait); wait *= 2 }
                catch (e: Gone) { last = e; return@repeat }
            }
        }
        error(when (last) {
            is Busy -> "All Gemini models are busy or out of quota (tried ${order.joinToString()}). Try again in a minute."
            else -> last?.message ?: "No Gemini model answered."
        })
    }

    private class Busy : Exception()
    private class Gone(msg: String) : Exception(msg)

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
                if (resp.code == 404) throw Gone(json?.optJSONObject("error")?.optString("message") ?: "Model $model not found")
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
