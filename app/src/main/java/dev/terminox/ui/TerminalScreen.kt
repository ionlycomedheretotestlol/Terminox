package dev.terminox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView
import dev.terminox.core.Env
import dev.terminox.term.ExtraKeys
import dev.terminox.term.Sessions
import dev.terminox.term.TerminalPane

@Composable
fun TerminalScreen(env: Env) {
    var current by remember { mutableIntStateOf(-1) }
    var view by remember { mutableStateOf<TerminalView?>(null) }

    LaunchedEffect(Unit) { if (Sessions.terms.isEmpty()) current = Sessions.create(env).id }
    val active = Sessions.terms.firstOrNull { it.id == current } ?: Sessions.terms.lastOrNull()

    Column(Modifier.fillMaxSize().background(Palette.bg).systemBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Sessions.terms.forEach { t ->
                val selected = t === active
                Row(
                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Palette.surface else Color.Transparent)
                        .clickable { current = t.id }
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("term ${t.id}", color = if (selected) Palette.cyan else Palette.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Close, "Close", tint = Palette.dim, modifier = Modifier.size(16.dp).clickable { Sessions.close(t) })
                }
            }
            Icon(Icons.Rounded.Add, "New terminal", tint = Palette.green,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { current = Sessions.create(env).id }.padding(4.dp))
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(12.dp))
                .border(2.dp, Brush.linearGradient(listOf(Palette.cyan, Palette.green)), RoundedCornerShape(12.dp))
                .background(Color(0xFF101018)).padding(6.dp)
        ) {
            if (active != null) {
                androidx.compose.runtime.key(active.id) {
                    TerminalPane(active, fontSize = 28, modifier = Modifier.fillMaxSize(), onView = { view = it })
                }
            } else {
                Text("No terminals. Tap + to start one.", color = Palette.dim, modifier = Modifier.align(Alignment.Center))
            }
        }
        ExtraKeys(view = { view }, accent = Palette.cyan)
    }
}
