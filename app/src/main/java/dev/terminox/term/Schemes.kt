package dev.terminox.term

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import dev.terminox.core.Prefs

/** Terminal palettes. Background is always transparent so window glass shows through. */
object Schemes {
    class Scheme(val name: String, val fg: Long, val cursor: Long, val ansi: LongArray)

    val all = listOf(
        Scheme("Terminox", 0xFFE6E9F5, 0xFF33CCFF, longArrayOf(
            0xFF1B1D2B, 0xFFFF5C8A, 0xFF3DFFA8, 0xFFFFD166, 0xFF4DA8FF, 0xFFC792EA, 0xFF33CCFF, 0xFFD6DAE8,
            0xFF4B5068, 0xFFFF7FA3, 0xFF7DFFC4, 0xFFFFE08A, 0xFF7CC0FF, 0xFFDDB3FF, 0xFF7FE3FF, 0xFFFFFFFF)),
        Scheme("Catppuccin", 0xFFCDD6F4, 0xFFF5E0DC, longArrayOf(
            0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE,
            0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8)),
        Scheme("Tokyo Night", 0xFFC0CAF5, 0xFFC0CAF5, longArrayOf(
            0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6,
            0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5)),
        Scheme("Dracula", 0xFFF8F8F2, 0xFFF8F8F2, longArrayOf(
            0xFF21222C, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C, 0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
            0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5, 0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF)),
        Scheme("Gruvbox", 0xFFEBDBB2, 0xFFEBDBB2, longArrayOf(
            0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921, 0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
            0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F, 0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2)),
        Scheme("Nord", 0xFFD8DEE9, 0xFFD8DEE9, longArrayOf(
            0xFF3B4252, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFE5E9F0,
            0xFF4C566A, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4)),
    )

    fun current() = all.firstOrNull { it.name == Prefs.termScheme } ?: all[0]

    fun apply(emulator: TerminalEmulator?) {
        val c = emulator?.mColors?.mCurrentColors ?: return
        val s = current()
        for (i in 0 until 16) c[i] = s.ansi[i].toInt()
        c[TextStyle.COLOR_INDEX_FOREGROUND] = s.fg.toInt()
        c[TextStyle.COLOR_INDEX_BACKGROUND] = 0x00000000
        c[TextStyle.COLOR_INDEX_CURSOR] = s.cursor.toInt()
    }
}
