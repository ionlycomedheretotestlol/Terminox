package dev.terminox.music

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * The player: YouTube's official embedded player, kept out of sight. It tries to autoplay; if Android
 * wants a tap, a small popup with just the video appears and closes itself once the song starts.
 * A glass mini player shows the song, time and controls.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun MusicPlayerHost() {
    val track = Music.current ?: return
    val ids = Music.videoIds
    val theme = Themes.current
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (ids.isNotEmpty()) {
            val ctx = LocalContext.current
            val web = remember(ids) {
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addJavascriptInterface(object {
                        @JavascriptInterface fun state(pos: Double, dur: Double, isPlaying: Boolean) =
                            post { Music.tick((pos * 1000).toLong(), (dur * 1000).toLong(), isPlaying, false) }
                        @JavascriptInterface fun playing() = post { Music.needsTap = false; Music.status = "" }
                        @JavascriptInterface fun failed(code: Int) = post { Music.usePreview("YouTube wouldn't play this one ($code), playing the preview") }
                    }, "Terminox")
                    loadDataWithBaseURL("https://terminox.app/", playerHtml(ids), "text/html", "utf-8", null)
                }
            }
            DisposableEffect(web) {
                Music.js = { code -> web.evaluateJavascript(code, null) }
                onDispose { Music.js = null; web.destroy() }
            }
            LaunchedEffect(web) {
                delay(3500)
                if (!Music.playing && Music.backend == "youtube") Music.needsTap = true
            }
            val tap = Music.needsTap
            // Same view either way: a small popup when a tap is needed, otherwise invisible.
            Column(
                Modifier.align(if (tap) Alignment.Center else Alignment.BottomEnd)
                    .width(if (tap) 300.dp else 200.dp)
                    .graphicsLayer { alpha = if (tap) 1f else 0f }
                    .then(if (tap) Modifier.glass(RoundedCornerShape(24.dp), alpha = 0.9f).padding(10.dp) else Modifier)
            ) {
                if (tap) {
                    Text("Tap ▶ to start the song", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
                AndroidView({ web }, Modifier.fillMaxWidth().height(if (tap) 158.dp else 113.dp).clip(RoundedCornerShape(14.dp)))
            }
        }

        if (!Music.needsTap) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 88.dp).width(240.dp)
                    .glass(RoundedCornerShape(22.dp), alpha = 0.75f).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(track.artwork, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Music.status.ifEmpty { "${Music.fmt(Music.positionMs)} / ${Music.fmt(Music.durationMs)}" },
                        color = Palette.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (Music.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = theme.a,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { Music.toggle() }.padding(4.dp))
                Icon(Icons.Rounded.Close, "Stop", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { Music.stop() }.padding(4.dp))
            }
        }
    }
}

private fun playerHtml(ids: List<String>) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var ids=${ids.joinToString(",", "[", "]") { "\"$it\"" }},i=0,player;
var s=document.createElement('script');s.src='https://www.youtube.com/iframe_api';document.head.appendChild(s);
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{videoId:ids[0],playerVars:{autoplay:1,playsinline:1,rel:0,origin:'https://terminox.app'},
    events:{onReady:function(e){e.target.playVideo();},
      onStateChange:function(e){if(e.data==1)Terminox.playing();},
      onError:function(e){i++;if(i<ids.length){player.loadVideoById(ids[i]);}else{Terminox.failed(e.data);}}}});
  setInterval(function(){if(player&&player.getCurrentTime){Terminox.state(player.getCurrentTime()||0,player.getDuration()||0,player.getPlayerState()==1);}},250);
}
function toggle(){if(player)(player.getPlayerState()==1?player.pauseVideo():player.playVideo());}
function stop(){if(player&&player.stopVideo)player.stopVideo();}
</script></body></html>
"""

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
            val text = if (Music.adPlaying) "Ad playing, lyrics will wait" else if (Music.status.isNotEmpty() && !Music.playing) Music.status else if (Music.lyrics.isEmpty()) Music.lyricsStatus else Music.lyrics.getOrNull(k)?.text?.ifEmpty { "♪" } ?: "♪"
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
