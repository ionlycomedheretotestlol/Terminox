package dev.terminox.ui

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WallpaperSetup(onPick: (String) -> Unit, onAurora: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Aurora(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Pick your vibe.", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Choose one wallpaper. Windows turn into frosted glass over it.", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(Icons.Rounded.Image, "Image", "Any photo", Modifier.weight(1f)) { onPick("image") }
                Card(Icons.Rounded.Movie, "Video", "Loops, muted", Modifier.weight(1f)) { onPick("video") }
            }
            Spacer(Modifier.height(14.dp))
            Card(Icons.Rounded.AutoAwesome, "Aurora", "Animated, matches your theme", Modifier.fillMaxWidth(), onAurora)
        }
    }
}

@Composable
private fun Card(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.glass(RoundedCornerShape(26.dp), alpha = 0.45f).clickable(onClick = onClick).padding(22.dp)) {
        Icon(icon, null, tint = Themes.current.a, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
    }
}
