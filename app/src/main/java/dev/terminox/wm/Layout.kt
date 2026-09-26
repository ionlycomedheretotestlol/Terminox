package dev.terminox.wm

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.sqrt

/** Tiling algorithms. All return one rect per window, in order, within [area]. */
object Layout {
    fun tile(kind: String, n: Int, area: Rect, gap: Float, masterRatio: Float, focused: Int): List<Rect> {
        if (n == 0) return emptyList()
        return when (kind) {
            "master" -> master(n, area, gap, masterRatio)
            "grid" -> grid(n, area, gap)
            "columns" -> columns(n, area, gap, focused)
            else -> dwindle(n, area, gap)
        }
    }

    private fun dwindle(n: Int, area: Rect, gap: Float): List<Rect> {
        val out = ArrayList<Rect>(n)
        var r = area
        for (i in 0 until n) {
            if (i == n - 1) { out += r; break }
            val (a, b) = split(r, r.width >= r.height, 0.5f, gap)
            out += a
            r = b
        }
        return out
    }

    private fun master(n: Int, area: Rect, gap: Float, ratio: Float): List<Rect> {
        if (n == 1) return listOf(area)
        val horizontal = area.width >= area.height
        val (m, stack) = split(area, horizontal, ratio, gap)
        return listOf(m) + stackOf(n - 1, stack, !horizontal, gap)
    }

    private fun grid(n: Int, area: Rect, gap: Float): List<Rect> {
        val portrait = area.height > area.width
        var cols = ceil(sqrt(n.toDouble())).toInt()
        var rows = ceil(n / cols.toDouble()).toInt()
        if (portrait) { val t = cols; cols = rows; rows = t }
        val out = ArrayList<Rect>(n)
        val rowRects = stackOf(rows, area, false, gap)
        var i = 0
        for ((ri, row) in rowRects.withIndex()) {
            val inRow = if (ri == rows - 1) n - i else cols
            stackOf(inRow, row, true, gap).forEach { if (i < n) { out += it; i++ } }
        }
        return out
    }

    /** Niri-style: columns in an endless horizontal strip, scrolled to keep focus centered. */
    private fun columns(n: Int, area: Rect, gap: Float, focused: Int): List<Rect> {
        val w = if (n == 1) area.width else area.width * 0.82f
        val f = focused.coerceIn(0, n - 1)
        val focusLeft = area.left + (area.width - w) / 2
        return List(n) { i ->
            val left = focusLeft + (i - f) * (w + gap)
            Rect(left, area.top, left + w, area.bottom)
        }
    }

    private fun stackOf(n: Int, area: Rect, horizontal: Boolean, gap: Float): List<Rect> {
        val total = (if (horizontal) area.width else area.height) - gap * (n - 1)
        val each = total / n
        return List(n) { i ->
            val start = i * (each + gap)
            if (horizontal) Rect(area.left + start, area.top, area.left + start + each, area.bottom)
            else Rect(area.left, area.top + start, area.right, area.top + start + each)
        }
    }

    private fun split(r: Rect, horizontal: Boolean, ratio: Float, gap: Float): Pair<Rect, Rect> =
        if (horizontal) {
            val x = r.left + (r.width - gap) * ratio
            Rect(r.left, r.top, x, r.bottom) to Rect(x + gap, r.top, r.right, r.bottom)
        } else {
            val y = r.top + (r.height - gap) * ratio
            Rect(r.left, r.top, r.right, y) to Rect(r.left, y + gap, r.right, r.bottom)
        }
}
