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
 * iOS-style "liquid glass" surfaces without real blur (which is expensive /
 * unstable on older Android): translucent fills, soft top-left highlight,
 * thin white border. Looks convincingly frosted and stays cheap to render.
 */

private val GlassShape = RoundedCornerShape(28.dp)

@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.55f,
    borderWidth: Dp = 1.dp,
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
        .border(BorderStroke(borderWidth, Color.White.copy(alpha = 0.18f)), shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    alpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassSurface(shape = shape, alpha = alpha),
        content = content,
    )
}
