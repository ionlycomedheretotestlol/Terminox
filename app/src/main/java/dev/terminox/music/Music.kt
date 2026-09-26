package dev.terminox.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.terminox.core.Ipc
import dev.terminox.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Music player state. Search: iTunes Search API. Lyrics: LRCLIB (time-synced LRC).
 * Playback: YouTube's own mobile site in a WebView (see MusicUi), whose clock drives the lyrics.
 */
object Music {
    data class Track(val title: String, val artist: String, val album: String, val durationSec: Int, val artwork: String)
    data class Line(val timeMs: Long, val text: String)

    val results = mutableStateListOf<Track>()
    var current by mutableStateOf<Track?>(null)
    var lyrics by mutableStateOf<List<Line>>(emptyList())
    var lyricsStatus by mutableStateOf("")
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    /** An ad is running in the player: lyrics hold still until the song itself plays. */
    var adPlaying by mutableStateOf(false)
    /** true while the YouTube page is shown big so the user can tap the song. */
    var expanded by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    /** Set by the UI: runs JS in the player page. */
    var js: ((String) -> Unit)? = null
    var ipc: Ipc? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private var lastWritten = ""

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query).addQueryParameter("entity", "song").addQueryParameter("limit", "15").build()
        val json = JSONObject(get(url.toString()))
        val arr = json.optJSONArray("results") ?: JSONArray()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Track(o.optString("trackName"), o.optString("artistName"), o.optString("collectionName"),
                o.optInt("trackTimeMillis") / 1000, o.optString("artworkUrl100").replace("100x100", "300x300"))
        }
    }.also { withContext(Dispatchers.Main) { results.clear(); results.addAll(it) } }

    fun play(track: Track) {
        current = track
        lyrics = emptyList()
        lyricsStatus = "Finding lyrics…"
        positionMs = 0; durationMs = track.durationSec * 1000L; playing = false
        expanded = true
        searchOpen = false
        scope.launch {
            val found = runCatching { fetchLyrics(track) }.getOrNull()
            lyrics = found.orEmpty()
            lyricsStatus = if (found.isNullOrEmpty()) "No synced lyrics for this one" else ""
        }
    }

    suspend fun quickPlay(query: String): String {
        val t = search(query).firstOrNull() ?: error("No results for \"$query\"")
        play(t)
        return "${t.title} — ${t.artist}"
    }

    fun toggle() = js?.invoke("(function(){var v=document.querySelector('video');if(v){v.paused?v.play():v.pause();}})()")

    fun stop() {
        js?.invoke("(function(){var v=document.querySelector('video');if(v)v.pause();})()")
        current = null; lyrics = emptyList(); playing = false; adPlaying = false; expanded = false
        ipc?.let { runCatching { it.reply("music-now", listOf("", "", "0", "", "", "Stopped.", "", "", "stopped")) } }
    }

    fun youtubeSearchUrl(t: Track) =
        "https://m.youtube.com/results".toHttpUrl().newBuilder()
            .addQueryParameter("search_query", "${t.artist} ${t.title} audio").build().toString()

    /** Called ~4x/second by the player with the video's clock. */
    fun tick(posMs: Long, durMs: Long, isPlaying: Boolean, adFlag: Boolean) {
        val expected = (current?.durationSec ?: 0) * 1000L
        // Belt and braces: a video much shorter than the song is almost certainly an ad.
        val lengthMismatch = expected > 0 && durMs in 1 until (expected * 0.6).toLong()
        adPlaying = adFlag || lengthMismatch
        if (adPlaying) { playing = false; writeNow(); return }
        positionMs = posMs; playing = isPlaying
        if (durMs > 0) durationMs = durMs
        writeNow()
    }

    fun lineIndex(): Int {
        val p = positionMs + Prefs.lyricOffsetMs
        var idx = -1
        for (i in lyrics.indices) if (lyrics[i].timeMs <= p) idx = i else break
        return idx
    }

    private fun writeNow() {
        val t = current ?: return
        val ipc = ipc ?: return
        val i = lineIndex()
        fun at(k: Int) = lyrics.getOrNull(k)?.text.orEmpty()
        val cur = if (adPlaying) "Ad playing, lyrics will wait…" else if (lyrics.isEmpty()) lyricsStatus.ifEmpty { "♪" } else if (i < 0) "♪" else at(i).ifEmpty { "♪" }
        val pct = if (durationMs > 0) (positionMs * 100 / durationMs).toInt().coerceIn(0, 100) else 0
        val lines = listOf(t.title, t.artist, "$pct", "${fmt(positionMs)} / ${fmt(durationMs)}",
            at(i - 1), cur, at(i + 1), at(i + 2), if (adPlaying) "ad" else if (playing) "playing" else "paused")
        val key = lines.joinToString("|")
        if (key == lastWritten) return
        lastWritten = key
        runCatching { ipc.reply("music-now", lines) }
    }

    fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    private fun fetchLyrics(t: Track): List<Line>? {
        val get = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", t.title).addQueryParameter("artist_name", t.artist)
            .addQueryParameter("album_name", t.album).addQueryParameter("duration", t.durationSec.toString()).build()
        runCatching { JSONObject(get(get.toString())).optString("syncedLyrics") }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }?.let { return parseLrc(it) }
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "${t.artist} ${t.title}").build()
        val arr = JSONArray(get(search.toString()))
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i).optString("syncedLyrics")
            if (s.isNotBlank() && s != "null") return parseLrc(s)
        }
        return null
    }

    private fun parseLrc(lrc: String): List<Line> {
        val re = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        return lrc.lines().flatMap { line ->
            val stamps = re.findAll(line).toList()
            val text = line.replace(re, "").trim()
            stamps.map { m -> Line((m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toDouble() * 1000).toLong(), text) }
        }.sortedBy { it.timeMs }
    }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", "Terminox/1.0 (https://github.com/ionlycomedheretotestlol/multi-terminal-app)").build())
            .execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.string() }
}
