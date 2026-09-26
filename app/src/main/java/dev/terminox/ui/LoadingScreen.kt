package dev.terminox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminox.core.Installer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val JOKES = listOf(
    "Cooking these sudos up..",
    "Teaching bash to speak Android..",
    "Negotiating with SELinux..",
    "Summoning Tux from the penguin realm..",
    "chmod +x'ing your vibes..",
    "Untangling symlinks (there are so many)..",
    "Asking apt nicely..",
    "Polishing gradients for your future screenshots..",
    "rm -rf /boredom..",
    "Warming up the pty..",
    "Aligning windows to the golden ratio..",
    "Convincing the kernel we're cool..",
)

@Composable
fun LoadingScreen(installer: Installer, onDone: () -> Unit) {
    val p by installer.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var joke by remember { mutableIntStateOf(0) }
    val fraction by animateFloatAsState(p.fraction, label = "progress")
    val logState = rememberLazyListState()

    LaunchedEffect(Unit) { installer.install() }
    LaunchedEffect(Unit) { while (true) { delay(2600); joke = (joke + 1) % JOKES.size } }
    LaunchedEffect(p.fraction, p.error) { if (p.fraction >= 1f && p.error == null) { delay(600); onDone() } }
    LaunchedEffect(p.log.size) { if (p.log.isNotEmpty()) logState.scrollToItem(p.log.size - 1) }

    Column(
        Modifier.fillMaxSize().background(Palette.bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("terminox", color = Palette.cyan, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(28.dp))
        AnimatedContent(JOKES[joke], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "joke") {
            Text(it, color = Palette.text, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Palette.green, trackColor = Palette.surface,
        )
        Spacer(Modifier.height(8.dp))
        Text("${p.step} · ${(fraction * 100).toInt()}%", color = Palette.dim, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            state = logState,
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Palette.surface).padding(10.dp)
        ) {
            items(p.log) { Text(it, color = Palette.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1) }
        }
        p.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text("Something broke: $err", color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { installer.install() } }) { Text("Retry") }
        }
    }
}
