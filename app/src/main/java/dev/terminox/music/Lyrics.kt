package dev.terminox.music

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Lyrics lookup with fallbacks. Blocking: call from Dispatchers.IO.
 * 1. LRCLIB exact match (original, then cleaned-up names)
 * 2. LRCLIB search, picking the synced version whose length is closest to the track
 * 3. NetEase Cloud Music (synced)
 * 4. Plain lyrics (LRCLIB, then lyrics.ovh), spread evenly over the song
 */
object Lyrics {
    data class Result(val lines: List<Music.Line>, val synced: Boolean, val source: String)

    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private const val UA = "Terminox/1.0 (https://github.com/ionlycomedheretotestlol/terminox)"

    fun find(t: Music.Track): Result? {
        val title = cleanTitle(t.title)
        val artist = mainArtist(t.artist)
        val dur = t.durationSec
        var plain: String? = null

        fun fromLrclib(o: JSONObject): Result? {
            o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { if (plain == null) plain = it }
            val s = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
            return parseLrc(s).takeIf { it.isNotEmpty() }?.let { Result(it, true, "LRCLIB") }
        }

        // 1. exact matches
        for ((tn, an, album) in listOf(Triple(t.title, t.artist, t.album), Triple(title, artist, ""), Triple(title, t.artist, ""))) {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", tn).addQueryParameter("artist_name", an)
                .apply { if (album.isNotEmpty()) addQueryParameter("album_name", album) }
                .apply { if (dur > 0) addQueryParameter("duration", dur.toString()) }.build()
            runCatching { fromLrclib(JSONObject(get(url.toString()))) }.getOrNull()?.let { return it }
        }

        // 2. search, closest duration wins
        val searches = listOf(
            listOf("track_name" to t.title, "artist_name" to t.artist),
            listOf("track_name" to title, "artist_name" to artist),
            listOf("q" to "$artist $title"),
        )
        for (params in searches) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val arr = runCatching { JSONArray(get(url.toString())) }.getOrNull() ?: continue
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            items.forEach { o -> o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { if (plain == null) plain = it } }
            items.filter { it.optString("syncedLyrics").let { s -> s.isNotBlank() && s != "null" } }
                .minByOrNull { if (dur > 0) abs(it.optDouble("duration", 0.0) - dur) else 0.0 }
                ?.let { best -> fromLrclib(best)?.let { return it } }
        }

        // 3. NetEase
        runCatching { netease(title, artist, dur) }.getOrNull()?.let { return it }

        // 4. plain text, evenly timed
        val text = plain ?: runCatching {
            JSONObject(get("https://api.lyrics.ovh/v1/${enc(artist)}/${enc(title)}")).optString("lyrics")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return text?.let { spread(it, dur) }
    }

    private fun netease(title: String, artist: String, dur: Int): Result? {
        val search = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", "$title $artist").addQueryParameter("type", "1").addQueryParameter("limit", "8").build()
        val songs = JSONObject(get(search.toString())).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val ranked = (0 until songs.length()).map { songs.getJSONObject(it) }
            .sortedBy { if (dur > 0) abs(it.optLong("duration") / 1000 - dur) else 0L }
        for (song in ranked.take(4)) {
            val lrc = JSONObject(get("https://music.163.com/api/song/lyric?id=${song.optLong("id")}&lv=1"))
                .optJSONObject("lrc")?.optString("lyric").orEmpty()
            // Drop credit lines like "作曲 : Name".
            val lines = parseLrc(lrc).filterNot { it.text.contains(" : ") && it.timeMs < 15_000 }
            if (lines.size > 3) return Result(lines, true, "NetEase")
        }
        return null
    }

    private fun spread(text: String, dur: Int): Result {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("(feat") }
        val total = (if (dur > 0) dur else 200) * 1000L
        val start = (total * 0.06).toLong()
        val step = if (lines.isEmpty()) 0 else (total * 0.9 - start).toLong() / lines.size
        return Result(lines.mapIndexed { i, l -> Music.Line(start + i * step, l) }, false, "plain")
    }

    fun parseLrc(lrc: String): List<Music.Line> {
        val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        return lrc.lines().flatMap { line ->
            val text = line.replace(re, "").trim()
            re.findAll(line).map { m -> Music.Line((m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toDouble() * 1000).toLong(), text) }.toList()
        }.sortedBy { it.timeMs }
    }

    fun cleanTitle(s: String) = s
        .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|prod\.?)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*\b(remaster(ed)?|version|edit|mono|stereo|single)\b.*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    fun mainArtist(s: String) = s.split(Regex("""\s*(,|&|\bfeat\.?|\bft\.?|\sx\s|\band\b|/)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull { it.isNotBlank() }?.trim() ?: s

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.string()
        }
}
