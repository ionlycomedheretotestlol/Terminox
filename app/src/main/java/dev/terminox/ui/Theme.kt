package dev.terminox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFF0B0B12)
    val surface = Color(0xFF15151F)
    val cyan = Color(0xFF33CCFF)
    val green = Color(0xFF00FF99)
    val text = Color(0xFFE6E6F0)
    val dim = Color(0xFF8A8AA0)
}

@Composable
fun TerminoxTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Palette.cyan, secondary = Palette.green,
        background = Palette.bg, surface = Palette.surface,
        onBackground = Palette.text, onSurface = Palette.text,
    ),
    content = content
)
