package dev.terminox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val STEPS = listOf(
    "Welcome home." to "This is your desktop. Terminals tile themselves, Hyprland style.",
    "＋  Spawn" to "The plus button (bottom-left) opens a new terminal. It's real Debian: apt install anything.",
    "▦  Position & scale" to "The widget button lets you drag windows to swap them, float them, and resize from the corner.",
    "●  The blob" to "Your AI sidekick lives in the blob.",
    "⚙  Make it yours" to "Settings: themes, gaps, rounding, blur, animations, layouts, fonts, wallpaper.",
)

@Composable
fun Guide(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val theme = Themes.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.padding(24.dp).fillMaxWidth(), alpha = 0.72f) {
            Column(Modifier.padding(26.dp)) {
                AnimatedContent(step, transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                }, label = "guide") { s ->
                    Column {
                        Text(STEPS[s].first, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(10.dp))
                        Text(STEPS[s].second, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 22.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        STEPS.indices.forEach { i ->
                            Box(Modifier.height(6.dp).width(if (i == step) 22.dp else 6.dp).clip(CircleShape)
                                .background(if (i == step) theme.a else Color.White.copy(alpha = 0.3f)))
                        }
                    }
                    Text("Skip", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onDone).padding(10.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (step == STEPS.lastIndex) "Let's go" else "Next", color = Color.Black, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(theme.a)
                            .clickable { if (step == STEPS.lastIndex) onDone() else step++ }
                            .padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
    }
}
