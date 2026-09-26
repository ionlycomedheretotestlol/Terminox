package dev.terminox.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import dev.terminox.term.Sessions

/** Window order, focus and floating state for the terminals in [Sessions]. */
object WindowManager {
    class Win(val id: Int) {
        var floating by mutableStateOf(false)
        var floatRect by mutableStateOf(Rect.Zero)
    }

    val wins = mutableStateListOf<Win>()
    var focused by mutableIntStateOf(-1)
    var positioning by mutableStateOf(false)

    /** Keeps [wins] in sync with running sessions, preserving user ordering. */
    fun sync() {
        val ids = Sessions.terms.map { it.id }.toSet()
        wins.removeAll { it.id !in ids }
        Sessions.terms.forEach { t -> if (wins.none { it.id == t.id }) wins += Win(t.id) }
        if (wins.none { it.id == focused }) focused = wins.lastOrNull()?.id ?: -1
    }

    fun swap(a: Int, b: Int) {
        val i = wins.indexOfFirst { it.id == a }
        val j = wins.indexOfFirst { it.id == b }
        if (i < 0 || j < 0 || i == j) return
        val t = wins[i]; wins[i] = wins[j]; wins[j] = t
    }

    fun tileAll() = wins.forEach { it.floating = false }
}
