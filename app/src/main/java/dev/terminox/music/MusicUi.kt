package dev.terminox.music

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import dev.terminox.core.Prefs
import dev.terminox.ui.Palette
import dev.terminox.ui.Themes
import dev.terminox.ui.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * The player: YouTube's mobile site. Big while the user taps the song, then a glass mini player.
 * Its video clock drives the synced lyrics.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MusicPlayerHost() {
    val track = Music.current ?: return
    val ctx = LocalContext.current
    val web = remember(track) {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    if ("/watch" in url) postDelayed({ Music.expanded = false }, 1200)
                }
            }
            loadUrl(Music.youtubeSearchUrl(track))
        }
    }
    DisposableEffect(web) {
        Music.js = { code -> web.evaluateJavascript(code, null) }
        onDispose { Music.js = null; web.destroy() }
    }
    LaunchedEffect(web) {
        while (true) {
            // YouTube marks the player with ad-showing/ad-interrupting while an ad runs.
            web.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return null;" +
                "var ad=!!document.querySelector('.ad-showing,.ad-interrupting,.ytp-ad-player-overlay,.ytm-promoted-video-renderer .ad-showing');" +
                "return [v.currentTime,v.duration||0,!v.paused,ad];})()") { r ->
                runCatching {
                    val a = JSONArray(r)
                    Music.tick((a.getDouble(0) * 1000).toLong(), (a.getDouble(1) * 1000).toLong(), a.getBoolean(2), a.getBoolean(3))
                }
            }
            delay(250)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val big = Music.expanded
        val w by animateDpAsState(if (big) maxWidth - 24.dp else 200.dp, spring(0.8f), label = "w")
        val h by animateDpAsState(if (big) maxHeight * 0.72f else 150.dp, spring(0.8f), label = "h")
        val x by animateDpAsState(if (big) 12.dp else maxWidth - 214.dp, spring(0.8f), label = "x")
        val y by animateDpAsState(if (big) maxHeight * 0.12f else maxHeight - 250.dp, spring(0.8f), label = "y")
        Column(Modifier.offset(x, y).width(w).height(h).glass(RoundedCornerShape(if (big) 26.dp else 20.dp), alpha = 0.8f)) {
            Row(Modifier.fillMaxWidth().height(if (big) 48.dp else 34.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, tint = Themes.current.a, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (big) "Tap your song ↓" else track.title, color = Color.White, fontSize = if (big) 16.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!big) {
                    Icon(if (Music.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = Color.White,
                        modifier = Modifier.size(26.dp).clip(CircleShape).clickable { Music.toggle() }.padding(3.dp))
                }
                Icon(Icons.Rounded.OpenInFull, "Expand", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable { Music.expanded = !big }.padding(4.dp))
                Icon(Icons.Rounded.Close, "Stop", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable { Music.stop() }.padding(3.dp))
            }
            AndroidView({ web }, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp).padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)))
        }
    }
}

/** Big synced lyrics drawn over the wallpaper, behind the windows ("background" mode). */
@Composable
fun LyricsLayer(modifier: Modifier = Modifier) {
    val track = Music.current ?: return
    if (Prefs.musicMode != "background") return
    val i = Music.lineIndex()
    val theme = Themes.current
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${track.title} · ${track.artist}", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(18.dp))
        Text(Music.lyrics.getOrNull(i - 1)?.text.orEmpty(), color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        AnimatedContent(i, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "lyric") { k ->
            val text = if (Music.adPlaying) "Ad playing, lyrics will wait" else if (Music.lyrics.isEmpty()) Music.lyricsStatus else Music.lyrics.getOrNull(k)?.text?.ifEmpty { "♪" } ?: "♪"
            Text(text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))))
        }
        Spacer(Modifier.height(12.dp))
        Text(Music.lyrics.getOrNull(i + 1)?.text.orEmpty(), color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun MusicSearchSheet(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var song by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val theme = Themes.current
    fun go() {
        if (song.isBlank()) return
        status = "Searching…"
        scope.launch { status = runCatching { Music.search("$song $artist".trim()); "" }.getOrElse { "Search failed: ${it.message}" } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)
                .glass(RoundedCornerShape(28.dp), alpha = 0.8f).clickable(enabled = false) {}.padding(18.dp)
        ) {
            Text("Music", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            GlassField(song, { song = it }, "Song name", ImeAction.Next) {}
            Spacer(Modifier.height(8.dp))
            GlassField(artist, { artist = it }, "Artist (optional)", ImeAction.Search) { go() }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(theme.a).clickable { go() }.padding(12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Color.Black); Spacer(Modifier.width(6.dp))
                Text("Search", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (status.isNotEmpty()) Text(status, color = Palette.dim, modifier = Modifier.padding(top = 10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(if (Music.results.isEmpty()) 0.dp else 320.dp).padding(top = 10.dp)) {
                items(Music.results) { t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Music.play(t); onClose() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(t.artwork, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.artist} · ${Music.fmt(t.durationSec * 1000L)}", color = Palette.dim, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassField(value: String, onChange: (String) -> Unit, hint: String, action: ImeAction, onAction: () -> Unit) {
    TextField(
        value, onChange, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        placeholder = { Text(hint, color = Palette.dim) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = action),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Themes.current.a,
        ),
    )
}
