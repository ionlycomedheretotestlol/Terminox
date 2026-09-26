package dev.terminox.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The AI mascot: a wobbly gradient blob with tall pill eyes glancing right. */
@Composable
fun Blob(modifier: Modifier = Modifier, a: Color = Themes.current.a, b: Color = Themes.current.b) {
    val tr = rememberInfiniteTransition(label = "blob")
    val phase by tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3200)), label = "phase")
    val blink by tr.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4200
        1f at 3900; 0.1f at 4000; 1f at 4150
    }), label = "blink")
    val look by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")

    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.42f
        val path = Path()
        val steps = 48
        for (i in 0..steps) {
            val t = i / steps.toFloat() * 2 * PI.toFloat()
            val rr = r * (1f + 0.06f * sin(3 * t + phase) + 0.04f * cos(2 * t - phase))
            val p = Offset(c.x + rr * cos(t), c.y + rr * sin(t))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, Brush.linearGradient(listOf(a, b), Offset(0f, 0f), Offset(size.width, size.height)))
        drawPath(path, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
            Offset(c.x - r * 0.4f, c.y - r * 0.5f), r * 0.9f))
        // pill eyes, looking right
        val eyeW = r * 0.2f
        val eyeH = r * 0.52f * blink
        val dx = r * 0.22f * look
        listOf(-0.3f, 0.22f).forEach { ex ->
            drawRoundRect(
                Color(0xFF0B0B12),
                Offset(c.x + r * ex + dx - eyeW / 2, c.y - eyeH / 2 - r * 0.05f),
                Size(eyeW, eyeH), CornerRadius(eyeW / 2)
            )
        }
    }
}
