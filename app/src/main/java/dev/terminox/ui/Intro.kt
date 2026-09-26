package dev.terminox.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminox.wm.Layout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Beat-locked timeline (seconds). The synth reads the same constants. */
object IntroTimeline {
    const val BEAT = 0.5f
    const val BAR = 2f
    const val SETUP = 4f
    const val SPAWN = 10f
    const val ARRANGE = 18f
    const val BLOB = 24f
    const val RISER = 32f
    const val DROP = 36f
    const val OUTRO = 48f
    const val END = 52f
    const val MORPH_AT = 1.4f
}

private typealias T = IntroTimeline

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun ease(x: Float) = x * x * (3 - 2 * x)

@Composable
fun Intro(onDone: () -> Unit) {
    val synth = remember { IntroSynth() }
    var t by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { synth.start(); onDispose { synth.stop() } }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    IntroFrame(t, onDone)
}

/** One frame of the intro at time [t]; pure function of t so it stays in sync with the music. */
@Composable
fun IntroFrame(t: Float, onDone: () -> Unit) {
    val montage = t >= T.DROP && t < T.OUTRO
    val theme = if (montage) Themes.all[((t - T.DROP) / T.BAR).toInt() % Themes.all.size] else Themes.current
    val beatPulse = if (montage) exp(-(t % T.BEAT) * 9f) else 0f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Aurora(Modifier.fillMaxSize().graphicsLayer { alpha = seg(t, 0f, 2.5f) }, theme.aurora)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f - 0.12f * beatPulse)))

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Captions(t, theme, Modifier.fillMaxWidth().height(150.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 18.dp)) {
                val w = maxWidth.value
                val h = maxHeight.value
                when {
                    t < T.SETUP -> Opening(t)
                    t < T.SPAWN -> Setup(t, w, h, theme)
                    t < T.DROP -> DesktopDemo(t, w, h, theme)
                    t < T.OUTRO -> Montage(t, w, h, theme, beatPulse)
                    t < T.END -> Outro(t, w, h, theme)
                    else -> ShallWeGo(t - T.END, onDone)
                }
                if (t in 4.5f..6.4f || t in 9.4f..34.9f) Finger(t, w, h)
            }
        }

        // white flash on the drop
        if (t >= T.DROP && t < T.DROP + 1.2f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = exp(-(t - T.DROP) * 4f))))
        }

        if (t < T.END) {
            Text("Skip", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)
                    .glass(RoundedCornerShape(50), alpha = 0.35f).clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 7.dp))
            Box(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(horizontal = 24.dp, vertical = 6.dp)
                .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(seg(t, 0f, T.END)).height(3.dp).background(Brush.horizontalGradient(listOf(theme.a, theme.b))))
            }
        }
    }
}

// ───────────────────────────── captions ─────────────────────────────

private val CAPTIONS = listOf(
    Triple(4.2f, 9.8f, "Step 1: make it yours."),
    Triple(10.1f, 17.8f, "Tap ＋ and terminals tile themselves."),
    Triple(18.1f, 23.8f, "Drag. Swap. Float.\nRearrange everything."),
    Triple(24.1f, 31.0f, "Tell the blob.\nIt does the work."),
    Triple(31.0f, 35.9f, "Now for the fun part…"),
)
private val HYPE = listOf("ROOT.", "SIX TERMINALS.", "LIVE LYRICS.", "EVERY THEME.", "RUNS FOREVER.", "ON YOUR PHONE.")

@Composable
private fun Captions(t: Float, theme: DeskTheme, modifier: Modifier) {
    Box(modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
        if (t >= T.DROP && t < T.OUTRO) {
            val i = ((t - T.DROP) / T.BAR).toInt().coerceIn(0, HYPE.lastIndex)
            val local = (t - T.DROP) - i * T.BAR
            val punch = 1f + 0.35f * exp(-local * 10f)
            Text(HYPE[i], fontSize = if (HYPE[i].length > 11) 34.sp else 46.sp, fontWeight = FontWeight.Black, maxLines = 1,
                style = TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))),
                modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = punch; scaleY = punch; alpha = seg(local, 0f, 0.08f) })
            return@Box
        }
        CAPTIONS.firstOrNull { t >= it.first && t < it.second }?.let { (a, b, text) ->
            val alpha = seg(t, a, a + 0.35f) * (1f - seg(t, b - 0.3f, b))
            val slide = (1f - ease(seg(t, a, a + 0.45f))) * 30f
            Text(text, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = slide * density })
        }
    }
}

// ───────────────────────────── 0–4s opening ─────────────────────────────

@Composable
private fun BoxScope.Opening(t: Float) {
    val a = "your phone"
    val b = "is about to get way cooler."
    val shownA = (t / 0.09f).toInt().coerceIn(0, a.length)
    val shownB = ((t - 1.4f) / 0.05f).toInt().coerceIn(0, b.length)
    val cursor = if ((t * 3).toInt() % 2 == 0) "▍" else " "
    Column(Modifier.align(Alignment.Center).offset(y = (-60).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(a.take(shownA) + if (shownB == 0) cursor else "", color = Color.White, fontSize = 44.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(10.dp))
        Text(b.take(shownB) + if (shownB in 1 until b.length) cursor else "", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, style = TextStyle(brush = Brush.linearGradient(listOf(Themes.current.a, Themes.current.b))))
    }
}

// ───────────────────────────── 4–10s setup ─────────────────────────────

private val JOKES = listOf("Cooking these sudos up..", "Negotiating with SELinux..", "Summoning Tux..",
    "chmod +x'ing your vibes..", "Asking apt nicely..", "Untangling symlinks..", "Warming up the pty..",
    "rm -rf /boredom..", "Polishing gradients..", "Convincing the kernel we're cool..", "Almost there..")

@Composable
private fun BoxScope.Setup(t: Float, w: Float, h: Float, theme: DeskTheme) {
    val local = t - T.SETUP
    if (local < 3f) {
        val picked = t >= 5.8f
        val fade = 1f - seg(local, 2.6f, 3f)
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = fade }, verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniCard(Icons.Rounded.Image, "Image", false, theme, Modifier.weight(1f))
                MiniCard(Icons.Rounded.Movie, "Video", false, theme, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            MiniCard(Icons.Rounded.AutoAwesome, "Aurora", picked, theme, Modifier.fillMaxWidth())
        }
    } else {
        val p = ease(seg(local, 3f, 5.8f))
        val joke = JOKES[((local - 3f) / 0.45f).toInt().coerceIn(0, JOKES.lastIndex)]
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("terminox", fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                style = TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))))
            Spacer(Modifier.height(18.dp))
            Text(joke, color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(p).height(8.dp).background(Brush.horizontalGradient(listOf(theme.a, theme.b))))
            }
            Spacer(Modifier.height(10.dp))
            Text("unpacking Debian · ${(p * 31337).toInt()} files", color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MiniCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, picked: Boolean, theme: DeskTheme, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(22.dp), alpha = 0.45f)
        .border(2.dp, if (picked) Brush.linearGradient(listOf(theme.a, theme.b)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(22.dp))
        .padding(20.dp)) {
        Icon(icon, null, tint = theme.a, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────────── 10–36s desktop, blob, root ─────────────────────────────

private val SPAWNS = listOf(10.4f, 11.4f, 12.4f, 13.4f)

/** Terminal scripts: (time, line). Lines starting with "$ " are typed out. */
private val SCRIPTS = listOf(
    listOf(10.7f to "$ neofetch", 11.3f to "  _,met\$\$\$gg.   root@terminox", 11.35f to " ,g\$\$\$\$\$\$\$P.  OS: Debian 13",
        11.4f to ",g\$\$P\"  \"\"Y\$.  Kernel: android", 11.45f to "\$\$P'     `\$\$.  WM: terminox",
        25.8f to "$ apt install -y python3 nodejs git", 26.6f to "Get:1 deb.debian.org trixie", 27.0f to "Setting up python3 ✓"),
    listOf(11.7f to "$ apt update", 12.3f to "Hit:1 deb.debian.org trixie", 12.6f to "All packages up to date.",
        27.3f to "$ git clone github.com/you/dream-app", 28.0f to "Receiving objects: 100% ✓"),
    listOf(12.7f to "$ python3 -c 'print(\"hi\")'", 13.4f to "hi",
        28.5f to "$ npm i && npm run dev", 29.3f to "➜ Local: http://localhost:3000"),
    listOf(13.7f to "$ uptime", 14.3f to " up 3 days, load 0.12", 15.0f to "$ ls /sdcard", 15.4f to "DCIM  Download  Music"),
)

@Composable
private fun TermText(t: Float, script: List<Pair<Float, String>>) {
    val visible = script.filter { it.first <= t }.takeLast(7)
    Column(Modifier.padding(horizontal = 8.dp)) {
        visible.forEach { (start, line) ->
            val text = if (line.startsWith("$ ")) line.take(2 + ((t - start) / 0.03f).toInt().coerceAtLeast(0)) else line
            Text(text, color = if (line.startsWith("$ ")) Color(0xFF7DFFC4) else Color(0xFFE6E9F5),
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

private data class DemoGeo(val area: Rect, val rects: List<Rect>, val order: List<Int>)

private fun demoGeo(t: Float, w: Float, h: Float): DemoGeo {
    val area = Rect(4f, 44f, w - 4f, h - 74f)
    val n = SPAWNS.count { it <= t }
    val layout = when {
        t in 21f..22f -> "master"
        t in 22f..23f -> "grid"
        else -> "dwindle"
    }
    val rects = Layout.tile(layout, n, area, 8f, 0.58f, 0)
    val order = if (t >= 20.2f && n >= 2) listOf(1, 0) + (2 until n) else (0 until n).toList()
    return DemoGeo(area, rects, order)
}

@Composable
private fun BoxScope.DesktopDemo(t: Float, w: Float, h: Float, theme: DeskTheme) {
    val geo = demoGeo(t, w, h)
    val positioning = t in 18.4f..23.6f
    // mini top bar
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        val layoutName = when { t in 21f..22f -> "master"; t in 22f..23f -> "grid"; else -> "dwindle" }
        Text(layoutName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.glass(RoundedCornerShape(50), alpha = 0.5f).padding(horizontal = 12.dp, vertical = 6.dp))
        Spacer(Modifier.weight(1f))
        if (positioning) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(theme.b), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        } else {
            Text("21:37", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.glass(RoundedCornerShape(50), alpha = 0.5f).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    // windows
    geo.order.forEachIndexed { slot, win ->
        val target = geo.rects[slot]
        val r by animateRectAsState(target, spring(0.62f, Spring.StiffnessMediumLow), label = "w$win")
        val born = SPAWNS[win]
        val pop = ease(seg(t, born, born + 0.35f))
        val dragging = win == 0 && t in 19.2f..20.2f
        val drag = if (dragging) {
            val p = ease(seg(t, 19.2f, 20.2f))
            val from = geo.rects[0].center
            val to = geo.rects.getOrElse(1) { geo.rects[0] }.center
            Offset((to.x - from.x) * p, (to.y - from.y) * p)
        } else Offset.Zero
        DemoWin(Rect(r.topLeft + drag, r.size), theme, "term ${win + 1}", focused = win == (if (t < 24f) SPAWNS.count { it <= t } - 1 else 0),
            alpha = pop, scale = 0.85f + 0.15f * pop, tint = if (positioning) theme.a.copy(alpha = 0.12f) else Color.Transparent) {
            TermText(t, SCRIPTS[win])
        }
    }
    // dock
    Row(Modifier.align(Alignment.BottomStart).glass(RoundedCornerShape(24.dp), alpha = 0.5f).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DockBtn(false) { Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        DockBtn(positioning, theme) { Icon(Icons.Rounded.SpaceDashboard, null, tint = if (positioning) theme.a else Color.White, modifier = Modifier.size(20.dp)) }
        DockBtn(false) { Blob(Modifier.size(32.dp), theme.a, theme.b) }
    }
    Box(Modifier.align(Alignment.BottomEnd).size(54.dp).glass(CircleShape, alpha = 0.5f), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Settings, null, tint = Color.White)
    }
    // blob panel
    if (t >= 24.4f) BlobPanel(t, h, theme)
}

@Composable
private fun DockBtn(active: Boolean, theme: DeskTheme? = null, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(20.dp))
        .background(if (active && theme != null) theme.a.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun DemoWin(r: Rect, theme: DeskTheme, title: String, focused: Boolean, alpha: Float = 1f, scale: Float = 1f,
                    tint: Color = Color.Transparent, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.offset(r.left.dp, r.top.dp).size(r.width.dp, r.height.dp)
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .glass(shape, tint = Color(0xFF0A0A14), alpha = 0.55f)
            .background(tint)
            .border(if (focused) 2.dp else 1.dp,
                if (focused) Brush.linearGradient(listOf(theme.a, theme.b)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))),
                shape)
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (focused) theme.a else Color.White.copy(alpha = 0.3f)))
            Spacer(Modifier.width(5.dp))
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

private sealed class Chat(val at: Float) {
    class User(at: Float, val text: String) : Chat(at)
    class Ai(at: Float, val text: String) : Chat(at)
    class Tool(at: Float, val text: String, val doneAt: Float) : Chat(at)
}

private val CHAT = listOf(
    Chat.User(25.0f, "set me up a dev machine 🔥"),
    Chat.Tool(25.7f, "apt install -y python3 nodejs git", 27.0f),
    Chat.Tool(27.2f, "git clone github.com/you/dream-app", 28.1f),
    Chat.Tool(28.4f, "npm i && npm run dev", 29.5f),
    Chat.Ai(29.8f, "Done. Python, Node, git. Your app is live on :3000 🚀"),
    Chat.User(31.0f, "ok but… can I get root?"),
    Chat.Ai(31.7f, "say less."),
)

@Composable
private fun BoxScope.BlobPanel(t: Float, h: Float, theme: DeskTheme) {
    val slide = 1f - ease(seg(t, 24.4f, 25.0f))
    val panelH = h * 0.64f
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelH.dp)
            .graphicsLayer { translationY = slide * panelH * density }
            .glass(RoundedCornerShape(26.dp), tint = Color(0xFF0C0C16), alpha = 0.97f).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Blob(Modifier.size(34.dp), theme.a, theme.b)
            Spacer(Modifier.width(8.dp))
            Text("the blob", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(if (t < 29.8f && t > 25.2f) "working…" else "gemini 3.8 flash", color = Palette.dim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = CHAT.filter { it.at <= t }.let { if (t >= 32.2f) it.takeLast(3) else it.takeLast(6) }
            shown.forEach { m ->
                when (m) {
                    is Chat.User -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(m.text, color = Color.Black, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(theme.a, theme.b))).padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    is Chat.Ai -> Text(m.text, color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp))
                    is Chat.Tool -> Text((if (t >= m.doneAt) "✓ " else "▸ ") + m.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = if (t >= m.doneAt) theme.b else Palette.dim, maxLines = 1)
                }
            }
            if (t >= 32.2f) RootButton(t)
        }
    }
}

@Composable
private fun RootButton(t: Float) {
    val pressed = t >= 34.2f
    val granted = t >= 35.2f
    val pulse = if (!pressed) 1f + 0.05f * sin(t * 2 * PI.toFloat() * 2) else 1f
    val appear = ease(seg(t, 32.2f, 32.6f))
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.graphicsLayer { scaleX = pulse * appear; scaleY = pulse * appear }.fillMaxWidth(0.94f).height(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFFFFB443))))
                .border(2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                granted -> Text("welcome.", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                pressed -> CircularProgressIndicator(color = Color.White)
                else -> Text("GET ROOT", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            }
        }
    }
}

// ───────────────────────────── the finger ─────────────────────────────

private class Key(val t: Float, val x: Float, val y: Float, val tap: Boolean = false, val drag: Boolean = false)

@Composable
private fun Finger(t: Float, w: Float, h: Float) {
    val plus = Offset(27f, h - 27f)
    val widget = Offset(77f, h - 27f)
    val blob = Offset(127f, h - 27f)
    val geo = demoGeo(19.2f, w, h)
    val g0 = geo.rects.getOrNull(0)?.center ?: Offset(w / 2, h / 3)
    val g1 = geo.rects.getOrNull(1)?.center ?: g0
    val keys = listOf(
        Key(4.5f, w * 0.8f, h * 0.95f),
        Key(5.8f, w / 2, h * 0.63f, tap = true),
        Key(9.6f, w * 0.6f, h * 0.9f),
        Key(10.4f, plus.x, plus.y, tap = true), Key(11.4f, plus.x, plus.y, tap = true),
        Key(12.4f, plus.x, plus.y, tap = true), Key(13.4f, plus.x, plus.y, tap = true),
        Key(16.5f, w * 0.6f, h * 0.92f),
        Key(18.4f, widget.x, widget.y, tap = true),
        Key(19.2f, g0.x, g0.y, drag = true),
        Key(20.2f, g1.x, g1.y),
        Key(21.0f, 36f, 17f, tap = true), Key(22.0f, 36f, 17f, tap = true), Key(23.0f, 36f, 17f, tap = true),
        Key(23.6f, w - 15f, 17f, tap = true),
        Key(24.4f, blob.x, blob.y, tap = true),
        Key(26.0f, w * 0.85f, h * 0.97f),
        Key(34.2f, w / 2, h * 0.36f + h * 0.64f * 0.62f, tap = true),
        Key(35.0f, w * 0.85f, h * 1.05f),
    )
    val i = keys.indexOfLast { it.t <= t }.coerceAtLeast(0)
    val k = keys[i]
    val next = keys.getOrNull(i + 1)
    val pos = when {
        next == null -> Offset(k.x, k.y)
        k.drag -> Offset(k.x + (next.x - k.x) * ease(seg(t, k.t, next.t)), k.y + (next.y - k.y) * ease(seg(t, k.t, next.t)))
        else -> {
            val p = ease(seg(t, next.t - 0.45f, next.t))
            Offset(k.x + (next.x - k.x) * p, k.y + (next.y - k.y) * p)
        }
    }
    val sinceTap = keys.filter { it.tap && it.t <= t }.maxOfOrNull { it.t }?.let { t - it } ?: 9f
    val pressed = sinceTap < 0.18f || k.drag
    val ring = if (sinceTap < 0.5f) sinceTap / 0.5f else -1f
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(pos.x.dp.toPx(), pos.y.dp.toPx())
        if (ring >= 0f) drawCircle(Color.White.copy(alpha = 0.6f * (1 - ring)), 16.dp.toPx() + 30.dp.toPx() * ring, c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.25f), (if (pressed) 16 else 19).dp.toPx(), c + Offset(0f, 3.dp.toPx()))
        drawCircle(Color.White.copy(alpha = if (pressed) 0.95f else 0.8f), (if (pressed) 15 else 18).dp.toPx(), c)
    }
}

// ───────────────────────────── 36–48s the drop ─────────────────────────────

private val LYRICS = listOf("we built it on a phone", "tiling through the night", "root access, lights on", "every window burning bright")

@Composable
private fun BoxScope.Montage(t: Float, w: Float, h: Float, theme: DeskTheme, pulse: Float) {
    val local = t - T.DROP
    val rects = Layout.tile("grid", 6, Rect(0f, 0f, w, h), 10f, 0.5f, 0)
    val titles = listOf("cmatrix", "htop", "lyrics", "fastfetch", "fireworks", "terminox-lock")
    rects.forEachIndexed { i, r ->
        val inT = ease(seg(local, i * 0.12f, i * 0.12f + 0.4f))
        val s = (0.6f + 0.4f * inT) * (1f + 0.035f * pulse)
        DemoWin(r, theme, titles[i], focused = (local / T.BEAT).toInt() % 6 == i, alpha = inT, scale = s) {
            when (i) {
                0 -> MatrixRain(t, theme)
                1 -> HtopBars(t, theme)
                2 -> Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(LYRICS[(local / 1f).toInt() % LYRICS.size], fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, style = TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))))
                }
                3 -> TermText(99f, listOf(0f to "root@terminox", 0f to "-------------", 0f to "OS: Debian 13 trixie",
                    0f to "Host: your phone", 0f to "Root: ✓ welcome.", 0f to "Theme: ${theme.name}", 0f to "Vibes: immaculate"))
                4 -> Fireworks(t, theme)
                else -> Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
                    Text("🔒 locked", color = theme.b, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    val up = (local * 7200).toInt()
                    Text("up %dd %02dh %02dm".format(up / 86400, up / 3600 % 24, up / 60 % 60), color = Color.White,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("running forever", color = Palette.dim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MatrixRain(t: Float, theme: DeskTheme) = Canvas(Modifier.fillMaxSize()) {
    val cols = 12
    val cw = size.width / cols
    val cell = 7.dp.toPx()
    for (c in 0 until cols) {
        val seed = (c * 7919 % 97) / 97f
        val speed = 0.5f + seed
        val head = ((t * speed * size.height * 0.9f + seed * size.height * 3) % (size.height * 1.5f))
        for (k in 0 until 10) {
            val y = head - k * cell * 1.2f
            if (y < 0 || y > size.height) continue
            drawRoundRect(if (k == 0) Color.White else theme.b.copy(alpha = 1f - k / 10f),
                Offset(c * cw + cw * 0.25f, y), Size(cw * 0.5f, cell), CornerRadius(2f))
        }
    }
}

@Composable
private fun HtopBars(t: Float, theme: DeskTheme) = Canvas(Modifier.fillMaxSize().padding(8.dp)) {
    val rows = 8
    val rh = size.height / rows
    for (i in 0 until rows) {
        val v = 0.25f + 0.7f * abs(sin(t * (2.2f + i * 0.3f) + i))
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, i * rh + rh * 0.2f), Size(size.width, rh * 0.6f), CornerRadius(4f))
        drawRoundRect(lerp(theme.b, theme.a, v), Offset(0f, i * rh + rh * 0.2f), Size(size.width * v, rh * 0.6f), CornerRadius(4f))
    }
}

@Composable
private fun Fireworks(t: Float, theme: DeskTheme) = Canvas(Modifier.fillMaxSize()) {
    for (b in 0 until 3) {
        val period = 1.0f
        val phase = (t + b * 0.33f) % period
        val burst = ((t + b * 0.33f) / period).toInt()
        val cx = size.width * (0.2f + 0.6f * ((burst * 37 + b * 11) % 10) / 10f)
        val cy = size.height * (0.25f + 0.5f * ((burst * 53 + b * 7) % 10) / 10f)
        val r = size.minDimension * 0.4f * ease(phase / period)
        val a = 1f - phase / period
        val color = if (b % 2 == 0) theme.a else theme.b
        for (p in 0 until 14) {
            val ang = p / 14f * 2 * PI.toFloat()
            drawCircle(color.copy(alpha = a), 2.5.dp.toPx() * (0.5f + a), Offset(cx + cos(ang) * r, cy + sin(ang) * r))
        }
    }
}

// ───────────────────────────── 48–52s outro ─────────────────────────────

@Composable
private fun BoxScope.Outro(t: Float, w: Float, h: Float, theme: DeskTheme) {
    val local = t - T.OUTRO
    val out = ease(seg(local, 0f, 0.8f))
    val rects = Layout.tile("grid", 6, Rect(0f, 0f, w, h), 10f, 0.5f, 0)
    rects.forEachIndexed { i, r ->
        val dy = -h * out * (1f + i * 0.15f)
        DemoWin(Rect(r.left, r.top + dy, r.right, r.bottom + dy), theme, "", focused = false, alpha = 1f - out) {}
    }
    val a = seg(local, 0.6f, 1.2f)
    Column(Modifier.align(Alignment.Center).offset(y = (-40).dp).graphicsLayer { alpha = a }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("This is yours now.", fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            style = TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))))
        Spacer(Modifier.height(10.dp))
        Text("real Debian · real apt · an AI that gets it done", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

// ───────────────────────────── 52s+ Shall we go? ─────────────────────────────

@Composable
private fun BoxScope.ShallWeGo(local: Float, onDone: () -> Unit) {
    val theme = Themes.current
    val appear = seg(local, 0f, 0.8f)
    val morph = ease(seg(local, T.MORPH_AT - 0.2f, T.MORPH_AT + 0.4f))
    val bounce = 1f + 0.12f * exp(-(local - T.MORPH_AT).coerceAtLeast(0f) * 5f) * sin((local - T.MORPH_AT).coerceAtLeast(0f) * 14f)
    Box(
        Modifier.align(Alignment.Center).offset(y = (-60).dp)
            .graphicsLayer { alpha = appear; scaleX = bounce; scaleY = bounce }
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(theme.a.copy(alpha = morph), theme.b.copy(alpha = morph))))
            .border(1.dp, Color.White.copy(alpha = 0.35f * morph), RoundedCornerShape(50))
            .clickable(enabled = morph > 0.5f, onClick = onDone)
            .padding(horizontal = (24 + 16 * morph).dp, vertical = (8 + 10 * morph).dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Shall we go?", fontSize = (40 - 14 * morph).sp, fontWeight = FontWeight.Black,
            color = lerp(Color.White, Color(0xFF0B0B12), morph))
    }
}
