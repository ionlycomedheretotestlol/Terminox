package dev.terminox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminox.wm.Layout

private const val BEAT = 60f / 96f
private const val BAR = BEAT * 4
private val SCENE_LEN = BAR * 1.5f // ~3.75s per scene
private const val SCENES = 5

@Composable
fun Intro(onDone: () -> Unit) {
    val synth = remember { IntroSynth() }
    var t by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { synth.start(); onDispose { synth.stop() } }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    val scene = (t / SCENE_LEN).toInt().coerceAtMost(SCENES)
    val local = t - scene * SCENE_LEN
    val theme = Themes.current

    Box(Modifier.fillMaxSize()) {
        Aurora(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        AnimatedContent(scene, transitionSpec = {
            (fadeIn(tween(500)) + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut(tween(400)) + scaleOut(targetScale = 1.06f))
        }, modifier = Modifier.fillMaxSize().systemBarsPadding(), label = "scene") { s ->
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                when (s) {
                    0 -> Logo(local)
                    1 -> Caption("Tap ＋ to spawn a terminal.", "They tile themselves.") { DemoTiles(n = 1 + (local / 0.9f).toInt().coerceAtMost(3), swap = false) }
                    2 -> Caption("Grab the widget button.", "Drag, swap, float, scale.") { DemoTiles(n = 3, swap = (local / 0.9f).toInt() % 2 == 1) }
                    3 -> Caption("Meet the blob.", "Your AI sidekick.") { Blob(Modifier.size(150.dp)) }
                    4 -> Caption("Real Debian. Real apt.", "No root needed.") { TypedTerminal(local) }
                    else -> ShallWeGo(local, onDone)
                }
            }
        }

        if (scene < SCENES) {
            Text("Skip", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50), alpha = 0.4f, elevation = 0.dp)
                    .clickable(onClick = onDone).padding(horizontal = 18.dp, vertical = 8.dp))
            // progress bar
            Box(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth((t / (SCENE_LEN * SCENES)).coerceIn(0f, 1f)).height(3.dp)
                    .background(Brush.horizontalGradient(listOf(theme.a, theme.b))))
            }
        }
    }
}

@Composable
private fun Logo(local: Float) {
    val word = "terminox"
    val shown = (local / 0.18f).toInt().coerceIn(0, word.length)
    val theme = Themes.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.take(shown) + if ((local * 2).toInt() % 2 == 0) "▍" else " ",
            fontSize = 52.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))))
        Spacer(Modifier.height(12.dp))
        val a by animateFloatAsState(if (local > 1.6f) 1f else 0f, tween(600), label = "sub")
        Text("a tiling terminal for your phone", color = Color.White.copy(alpha = 0.85f * a), fontSize = 18.sp)
    }
}

@Composable
private fun Caption(title: String, sub: String, visual: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { visual() }
        Spacer(Modifier.height(28.dp))
        Text(title, color = Color.White, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

/** Miniature desktop using the real tiling code. */
@Composable
private fun DemoTiles(n: Int, swap: Boolean) {
    val theme = Themes.current
    Box(Modifier.fillMaxWidth(0.8f).height(280.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val rects = Layout.tile("dwindle", n, Rect(0f, 0f, w, h), 18f, 0.5f, 0)
            val order = if (swap && n >= 2) listOf(1, 0) + (2 until n) else (0 until n).toList()
            order.forEachIndexed { slot, win ->
                val target by animateRectAsState(rects[slot], spring(0.6f, Spring.StiffnessLow), label = "r$win")
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(Color.White.copy(alpha = 0.12f), target.topLeft, target.size, CornerRadius(28f))
                    drawRoundRect(Brush.linearGradient(listOf(theme.a, theme.b), target.topLeft, target.bottomRight),
                        target.topLeft, target.size, CornerRadius(28f), style = Stroke(5f))
                    for (line in 0 until 3) {
                        val y = target.top + 40f + line * 30f
                        if (y < target.bottom - 20f) drawRoundRect(Color.White.copy(alpha = 0.35f),
                            androidx.compose.ui.geometry.Offset(target.left + 28f, y),
                            androidx.compose.ui.geometry.Size((target.width - 56f) * (0.8f - line * 0.2f), 10f), CornerRadius(5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypedTerminal(local: Float) {
    val lines = listOf("$ apt install neofetch", "Reading package lists... Done", "Setting up neofetch ✓", "$ neofetch")
    val chars = (local * 28).toInt()
    var left = chars
    Column(Modifier.fillMaxWidth(0.9f).glass(RoundedCornerShape(22.dp), alpha = 0.6f).padding(20.dp)) {
        lines.forEach { l ->
            if (left > 0) {
                Text(l.take(left), color = if (l.startsWith("$")) Themes.current.b else Color.White.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                left -= l.length
            }
        }
    }
}

@Composable
private fun ShallWeGo(local: Float, onDone: () -> Unit) {
    val theme = Themes.current
    val morph by animateFloatAsState(if (local > 1.4f) 1f else 0f, spring(0.55f, Spring.StiffnessLow), label = "morph")
    val appear by animateFloatAsState(1f, tween(900), label = "appear")
    Box(
        Modifier
            .graphicsLayer { alpha = appear }
            .scale(1f + 0.06f * morph)
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(theme.a.copy(alpha = morph), theme.b.copy(alpha = morph))))
            .border(1.dp, Color.White.copy(alpha = 0.35f * morph), RoundedCornerShape(50))
            .clickable(enabled = morph > 0.5f, onClick = onDone)
            .padding(horizontal = 24.dp + 16.dp * morph, vertical = 8.dp + 10.dp * morph),
        contentAlignment = Alignment.Center
    ) {
        Text("Shall we go?", fontSize = (40 - 14 * morph).sp, fontWeight = FontWeight.Black,
            color = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF0B0B12), morph))
    }
}
