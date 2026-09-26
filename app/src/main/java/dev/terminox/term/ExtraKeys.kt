package dev.terminox.term

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView

private sealed class Key(val label: String) {
    class Text(label: String, val text: String) : Key(label)
    class Code(label: String, val code: Int) : Key(label)
    object Ctrl : Key("CTRL")
    object Alt : Key("ALT")
}

private val KEYS = listOf(
    Key.Text("ESC", "\u001b"), Key.Text("TAB", "\t"), Key.Ctrl, Key.Alt,
    Key.Code("←", KeyEvent.KEYCODE_DPAD_LEFT), Key.Code("↓", KeyEvent.KEYCODE_DPAD_DOWN),
    Key.Code("↑", KeyEvent.KEYCODE_DPAD_UP), Key.Code("→", KeyEvent.KEYCODE_DPAD_RIGHT),
    Key.Text("-", "-"), Key.Text("/", "/"), Key.Text("|", "|"), Key.Text("~", "~"),
    Key.Code("HOME", KeyEvent.KEYCODE_MOVE_HOME), Key.Code("END", KeyEvent.KEYCODE_MOVE_END),
    Key.Code("PGUP", KeyEvent.KEYCODE_PAGE_UP), Key.Code("PGDN", KeyEvent.KEYCODE_PAGE_DOWN),
)

@Composable
fun ExtraKeys(view: () -> TerminalView?, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xE6101018))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KEYS.forEach { key ->
            val active = (key is Key.Ctrl && Modifiers.ctrl) || (key is Key.Alt && Modifiers.alt)
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .widthIn(min = 40.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent.copy(alpha = 0.35f) else Color(0x22FFFFFF))
                    .clickable {
                        val v = view() ?: return@clickable
                        when (key) {
                            is Key.Ctrl -> Modifiers.ctrl = !Modifiers.ctrl
                            is Key.Alt -> Modifiers.alt = !Modifiers.alt
                            is Key.Text -> v.currentSession?.write(key.text)
                            is Key.Code -> v.handleKeyCode(key.code, 0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(key.label, color = if (active) accent else Color(0xFFE0E0F0), fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
