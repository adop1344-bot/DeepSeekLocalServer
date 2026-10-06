package com.rikkahub.deepseeklocal.presentation.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS-style "liquid glass" surfaces without real blur (expensive/unstable on older
 * Android): translucent fills, soft top-left highlight, thin white border, gentle
 * bottom shadow. Cheap to render, convincing frosted look.
 */

val GlassShapeLg = RoundedCornerShape(28.dp)
val GlassShapeMd = RoundedCornerShape(22.dp)
val GlassShapeSm = RoundedCornerShape(16.dp)

@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassShapeLg,
    tint: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.55f,
    borderWidth: Dp = 1.dp,
    borderAlpha: Float = 0.18f,
): Modifier {
    val highlight = Color.White.copy(alpha = 0.10f)
    val shadow = Color.Black.copy(alpha = 0.06f)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = (alpha + 0.10f).coerceAtMost(1f)),
                    tint.copy(alpha = alpha),
                    tint.copy(alpha = (alpha - 0.05f).coerceAtLeast(0f)),
                ),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        )
        .background(
            Brush.linearGradient(
                colors = listOf(highlight, Color.Transparent),
                start = Offset.Zero,
                end = Offset(600f, 260f),
            ),
        )
        .background(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, shadow),
                startY = 0f,
                endY = 900f,
            ),
        )
        .border(BorderStroke(borderWidth, Color.White.copy(alpha = borderAlpha)), shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapeLg,
    alpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassSurface(shape = shape, alpha = alpha),
        content = content,
    )
}

/** Fullscreen ambient gradient backdrop used by every tab. */
@Composable
fun Modifier.ambientBackground(): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val p = MaterialTheme.colorScheme.primary
    val t = MaterialTheme.colorScheme.tertiary
    return this.background(
        Brush.radialGradient(
            colors = listOf(p.copy(alpha = 0.16f), bg),
            radius = 1100f,
        ),
    ).background(
        Brush.radialGradient(
            colors = listOf(t.copy(alpha = 0.10f), Color.Transparent),
            radius = 900f,
            center = Offset(1200f, 1800f),
        ),
    )
}
