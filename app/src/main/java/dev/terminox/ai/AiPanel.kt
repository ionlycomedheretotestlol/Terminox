package dev.terminox.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminox.core.Prefs
import dev.terminox.music.GlassField
import dev.terminox.ui.Blob
import dev.terminox.ui.Palette
import dev.terminox.ui.Themes
import dev.terminox.ui.glass
import kotlinx.coroutines.launch

@Composable
fun AiPanel(agent: Agent, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var rootResult by remember { mutableStateOf<Boolean?>(null) }
    val list = rememberLazyListState()
    val theme = Themes.current
    LaunchedEffect(agent.messages.size) { if (agent.messages.isNotEmpty()) list.animateScrollToItem(agent.messages.lastIndex) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f).navigationBarsPadding().imePadding().padding(10.dp)
                .glass(RoundedCornerShape(30.dp), alpha = 0.82f).clickable(enabled = false) {}
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Blob(Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("the blob", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(if (agent.busy) "working…" else Prefs.geminiModel, color = Palette.dim, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.DeleteSweep, "Clear", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { agent.reset() }.padding(6.dp))
                Icon(Icons.Rounded.Close, "Close", tint = Color.White,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose).padding(6.dp))
            }

            if (Prefs.geminiKey.isBlank()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Put a Gemini API key and you're in.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Get one free at aistudio.google.com (AQ… or AIza… keys both work).", color = Palette.dim, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    GlassField(key, { key = it }, "Gemini API key", ImeAction.Done) { Prefs.geminiKey = key.trim() }
                    Spacer(Modifier.height(10.dp))
                    Pill("Save key", theme.a) { Prefs.geminiKey = key.trim() }
                }
                return@Column
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (agent.messages.isEmpty()) item {
                    Text("Try: “open a terminal and do neofetch”", color = Palette.dim, modifier = Modifier.padding(8.dp))
                }
                items(agent.messages) { m ->
                    when (m) {
                        is Agent.Msg.User -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(m.text, color = Color.Black, modifier = Modifier.widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(Brush.linearGradient(listOf(theme.a, theme.b))).padding(12.dp))
                        }
                        is Agent.Msg.Ai -> Text(m.text, color = Color.White, modifier = Modifier.widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp))
                        is Agent.Msg.Tool -> Text(
                            (when (m.ok) { true -> "✓ "; false -> "✗ "; null -> "· " }) + m.label,
                            color = when (m.ok) { true -> theme.b; false -> Color(0xFFFF6B8A); null -> Palette.dim },
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 3)
                        is Agent.Msg.Error -> Text(m.text, color = Color(0xFFFF6B8A), fontSize = 13.sp)
                        Agent.Msg.RootButton -> GetRoot { granted ->
                            rootResult = granted
                            Prefs.rootGranted = granted
                        }
                    }
                }
                if (agent.busy) item { CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), color = theme.a, strokeWidth = 2.dp) }
            }

            agent.confirm?.let { (cmd, _) ->
                Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x33FF6B8A)).padding(14.dp)) {
                    Text("This looks destructive. Run it?", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(cmd, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Run it", Color(0xFFFF6B8A)) { agent.answerConfirm(true) }
                        Pill("No way", Color.White) { agent.answerConfirm(false) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GlassField(input, { input = it }, "Tell the blob something…", ImeAction.Send) {
                        val t = input; input = ""; scope.launch { agent.send(t) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(52.dp).clip(CircleShape).background(if (agent.busy) Color.White.copy(alpha = 0.1f) else theme.a)
                    .clickable(enabled = !agent.busy) { val t = input; input = ""; scope.launch { agent.send(t) } },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.Black)
                }
            }
        }

        AnimatedVisibility(rootResult != null, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
            val granted = rootResult == true
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable { rootResult = null }, contentAlignment = Alignment.Center) {
                Text(
                    if (granted) "welcome." else "continue.",
                    fontSize = 64.sp,
                    fontWeight = if (granted) FontWeight.Black else FontWeight.Light,
                    fontStyle = if (granted) FontStyle.Normal else FontStyle.Italic,
                    color = if (granted) Color.White else Color.White.copy(alpha = 0.8f),
                    style = if (granted) androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(theme.a, theme.b))) else androidx.compose.ui.text.TextStyle.Default,
                )
            }
        }
    }
}

@Composable
private fun GetRoot(onResult: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val theme = Themes.current
    val pulse by rememberInfiniteTransition(label = "root").animateFloat(1f, 1.05f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.scale(if (checking) 1f else pulse).fillMaxWidth(0.9f).height(96.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFFFFB443))))
                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .clickable(enabled = !checking) { checking = true; scope.launch { onResult(Root.check()); checking = false } },
            contentAlignment = Alignment.Center
        ) {
            if (checking) CircularProgressIndicator(color = Color.White)
            else Text("GET ROOT", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) =
    Text(text, color = Color.Black, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp))
