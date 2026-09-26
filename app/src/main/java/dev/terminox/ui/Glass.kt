package dev.terminox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted "liquid glass" surface: soft shadow, translucent tint, a specular sheen
 * from the top-left and a thin bright rim.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color(0xFF12121C),
    alpha: Float = 0.55f,
    rim: Color = Color.White.copy(alpha = 0.18f),
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 0.dp,
): Modifier = this
    // No drop shadow: it would show through the translucent fill as a dark slab.
    .clip(shape)
    .background(tint.copy(alpha = alpha))
    .drawWithContent {
        drawContent()
        // specular sheen
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.16f),
                0.35f to Color.White.copy(alpha = 0.04f),
                1f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width * 0.9f, size.height),
            )
        )
    }
    .border(1.dp, Brush.linearGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.25f), rim.copy(alpha = rim.alpha * 0.6f))), shape)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    alpha: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) = Box(modifier.glass(shape = shape, alpha = alpha), content = content)
