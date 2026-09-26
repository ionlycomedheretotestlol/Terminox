package dev.terminox.music

import dev.terminox.ai.Gemini
import dev.terminox.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Finds YouTube video IDs for a track without showing YouTube: open-source front-end search APIs
 * (Piped, Invidious; live instance lists), then Gemini + Google Search as a last resort.
 */
object VideoFinder {
    private val http = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
    private val ID = Regex("[A-Za-z0-9_-]{11}")
    private val PIPED = listOf("https://api.piped.private.coffee", "https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de")
    private val INVIDIOUS = listOf("https://inv.nadeko.net", "https://invidious.nerdvpn.de", "https://yewtu.be", "https://invidious.f5.si")

    suspend fun find(t: Music.Track): List<String> = withContext(Dispatchers.IO) {
        val q = "${t.artist} ${t.title} official audio"
        for (base in PIPED) runCatching { piped(base, q) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val inv = (runCatching { invidiousInstances() }.getOrDefault(emptyList()) + INVIDIOUS).distinct().take(6)
        for (base in inv) runCatching { invidious(base, q) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        runCatching { gemini(t) }.getOrDefault(emptyList())
    }

    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
    }

    private fun piped(base: String, q: String): List<String> {
        val url = "$base/search".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("filter", "videos").build()
        val items = JSONObject(get(url.toString())).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { items.getJSONObject(it).optString("url").substringAfter("v=", "").takeIf { id -> ID.matches(id) } }.take(5)
    }

    private fun invidiousInstances(): List<String> {
        val arr = JSONArray(get("https://api.invidious.io/instances.json?sort_by=health"))
        return (0 until arr.length()).mapNotNull { i ->
            val info = arr.getJSONArray(i).optJSONObject(1) ?: return@mapNotNull null
            info.optString("uri").takeIf { it.startsWith("https://") && info.optBoolean("api", false) }
        }
    }

    private fun invidious(base: String, q: String): List<String> {
        val url = "$base/api/v1/search".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("type", "video").build()
        val arr = JSONArray(get(url.toString()))
        return (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("videoId").takeIf { id -> ID.matches(id) } }.take(5)
    }

    private fun gemini(t: Music.Track): List<String> {
        if (Prefs.geminiKey.isBlank()) return emptyList()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text",
                "Find YouTube video IDs for the official audio or official music video of \"${t.title}\" by ${t.artist}. " +
                    "Reply with up to 3 IDs (11 characters each) separated by spaces, nothing else.")))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        for (model in Gemini.CHAIN) {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", Prefs.geminiKey.trim())
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val text = runCatching {
                OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build().newCall(req).execute().use { it.body!!.string() }
            }.getOrNull() ?: continue
            val parts = runCatching { JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts") }.getOrNull() ?: continue
            val ids = (0 until parts.length()).flatMap { ID.findAll(parts.getJSONObject(it).optString("text")).map { m -> m.value }.toList() }
            if (ids.isNotEmpty()) return ids.take(3)
        }
        return emptyList()
    }
}
