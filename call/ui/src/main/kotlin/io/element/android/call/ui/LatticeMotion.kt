/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.call.ui.preview.ElementCallPreview
import io.element.android.call.ui.theme.ElementCallTheme
import kotlin.math.PI
import kotlin.math.cos

/**
 * Lattice: the animation shown while a call connects (`lattice-connecting.svg` of the app's icon set): a light dot
 * with eight round it, lit one after the other, clockwise. Standing still with reduce motion on, and in previews.
 */
@Composable
internal fun LatticeConnecting(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val seconds = rememberLatticeSeconds(LatticeMotion.CONNECTING_PERIOD)
    val ring = LatticeMotion.ACCENT
    val centre = ElementCallTheme.colors.textPrimary
    Canvas(modifier.size(size)) {
        // The eight and the one, in the units of the icon's 512 square: 252 across with the dots' own radius.
        val unit = this.size.minDimension / 252f
        fun at(x: Float, y: Float) = Offset(center.x + (x - 256f) * unit, center.y + (y - 256f) * unit)
        val now = seconds?.value
        LatticeMotion.connectingDots.forEachIndexed { index, (x, y) ->
            drawCircle(ring, radius = 26f * unit, center = at(x, y), alpha = LatticeMotion.connectingAlpha(now, index))
        }
        drawCircle(centre, radius = 34f * unit, center = at(256f, 256f), alpha = 0.9f)
    }
}

/**
 * Lattice: three bars rising and falling, beside the name of somebody who is speaking (`lattice-speaking.svg`).
 * Standing still, at their full height, with reduce motion on and in previews.
 */
@Composable
internal fun LatticeSpeakingBars(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val seconds = rememberLatticeSeconds(LatticeMotion.SPEAKING_PERIOD)
    Canvas(modifier.size(size)) {
        // In the units of the icon's 64 square, of which the bars take the middle 40 by 40.
        val unit = this.size.minDimension / 40f
        val now = seconds?.value
        LatticeMotion.speakingBars.forEachIndexed { index, bar ->
            val height = bar.height * LatticeMotion.speakingScale(now, index) * unit
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x + (bar.x - 32f) * unit, center.y - height / 2),
                size = Size(9f * unit, height),
                cornerRadius = CornerRadius(4.5f * unit),
            )
        }
    }
}

/** Seconds into a period that repeats, or null when nothing should move: reduce motion, previews and screenshots. */
@Composable
private fun rememberLatticeSeconds(period: Float): State<Float>? {
    if (LocalInspectionMode.current || rememberReduceMotion()) return null
    return rememberInfiniteTransition(label = "lattice").animateFloat(
        initialValue = 0f,
        targetValue = period,
        animationSpec = infiniteRepeatable(tween((period * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "latticeSeconds",
    )
}

/** The measures and the timing of the two animations, as the icon set's SVGs have them. */
internal object LatticeMotion {
    /** The accent of the app's icon, which the animation is a part of. */
    val ACCENT = Color(0xFF6875F5)

    const val CONNECTING_PERIOD = 2.4f

    /** Up and down again: the SVG alternates a 0.7 s rise. */
    const val SPEAKING_PERIOD = 1.4f

    /** Clockwise from the top left, round the dot in the middle. */
    val connectingDots = listOf(
        156f to 156f,
        256f to 156f,
        356f to 156f,
        356f to 256f,
        356f to 356f,
        256f to 356f,
        156f to 356f,
        156f to 256f,
    )

    /** A ninth of the period apart, so the light has gone round once when the first is lit again. */
    private const val CONNECTING_STEP = CONNECTING_PERIOD / 9f
    private const val CONNECTING_DIM = 0.15f
    private const val CONNECTING_STILL = 0.5f

    /** The opacity of dot [index]: dim, lit 8 % into its period, dim again at 30 %. Still, half lit. */
    fun connectingAlpha(seconds: Float?, index: Int): Float {
        if (seconds == null) return CONNECTING_STILL
        val phase = phase(seconds, index * CONNECTING_STEP, CONNECTING_PERIOD)
        val lit = when {
            phase < 0.08f -> phase / 0.08f
            phase < 0.3f -> 1f - (phase - 0.08f) / 0.22f
            else -> 0f
        }
        return CONNECTING_DIM + (1f - CONNECTING_DIM) * lit
    }

    data class SpeakingBar(val x: Float, val height: Float, val delay: Float)

    val speakingBars = listOf(SpeakingBar(12f, 24f, -0.2f), SpeakingBar(27.5f, 40f, 0f), SpeakingBar(43f, 30f, -0.45f))

    /** The height of bar [index] as a share of its full one, between 0.35 and 1. Still, full. */
    fun speakingScale(seconds: Float?, index: Int): Float {
        if (seconds == null) return 1f
        val phase = phase(seconds, speakingBars[index].delay, SPEAKING_PERIOD)
        return 0.35f + 0.65f * (1f - cos(2 * PI.toFloat() * phase)) / 2f
    }

    private fun phase(seconds: Float, delay: Float, period: Float): Float = ((seconds - delay) % period + period) % period / period
}

@Preview
@Composable
internal fun LatticeMotionPreview() = ElementCallPreview {
    Row(
        modifier = Modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LatticeConnecting()
        LatticeSpeakingBars(color = ElementCallTheme.colors.onOverlay, size = 32.dp)
    }
}
