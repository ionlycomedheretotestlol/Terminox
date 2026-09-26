package dev.terminox.ui

import androidx.compose.ui.graphics.Color
import dev.terminox.core.Prefs

/** Desktop presets inspired by popular Linux compositors and color schemes. */
data class DeskTheme(
    val name: String,
    val a: Color,
    val b: Color,
    val aurora: List<Color>,
    val rounding: Int,
    val gapsIn: Int,
    val gapsOut: Int,
    val layout: String,
    val bounce: Float,
)

object Themes {
    val all = listOf(
        DeskTheme("Hyprland", Color(0xFF33CCFF), Color(0xFF00FF99),
            listOf(Color(0xFF070B1A), Color(0xFF1565C0), Color(0xFF00A884), Color(0xFF7B2FF7)), 18, 6, 12, "dwindle", 0.65f),
        DeskTheme("Niri", Color(0xFFFFC87F), Color(0xFFFF8F6B),
            listOf(Color(0xFF1A1423), Color(0xFF3D1E3C), Color(0xFF8E4A49), Color(0xFFE09F3E)), 14, 8, 14, "columns", 0.85f),
        DeskTheme("Catppuccin", Color(0xFFCBA6F7), Color(0xFF89B4FA),
            listOf(Color(0xFF11111B), Color(0xFF8839EF), Color(0xFF1E66F5), Color(0xFFEA76CB)), 20, 6, 14, "dwindle", 0.72f),
        DeskTheme("Tokyo Night", Color(0xFF7AA2F7), Color(0xFFBB9AF7),
            listOf(Color(0xFF0F0F17), Color(0xFF3D59A1), Color(0xFF7A3DB8), Color(0xFF1A7F9C)), 16, 5, 10, "master", 0.7f),
        DeskTheme("Rosé Pine", Color(0xFFEBBCBA), Color(0xFFC4A7E7),
            listOf(Color(0xFF191724), Color(0xFF26233A), Color(0xFF524F67), Color(0xFFB4637A)), 22, 7, 16, "dwindle", 0.75f),
        DeskTheme("Nord", Color(0xFF88C0D0), Color(0xFF81A1C1),
            listOf(Color(0xFF1F242D), Color(0xFF5E81AC), Color(0xFF88C0D0), Color(0xFFB48EAD)), 12, 6, 12, "grid", 0.8f),
        DeskTheme("Sway", Color(0xFF4C7899), Color(0xFF285577),
            listOf(Color(0xFF0B0F14), Color(0xFF1A2530), Color(0xFF223344), Color(0xFF285577)), 4, 4, 6, "master", 0.95f),
    )

    val current: DeskTheme get() = all.firstOrNull { it.name == Prefs.theme } ?: all[0]

    fun apply(t: DeskTheme) {
        Prefs.theme = t.name
        Prefs.rounding = t.rounding
        Prefs.gapsIn = t.gapsIn
        Prefs.gapsOut = t.gapsOut
        Prefs.layout = t.layout
        Prefs.bounce = t.bounce
    }
}
