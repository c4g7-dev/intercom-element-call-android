/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatticeMotionTest {
    @Test
    fun `a connecting dot is lit shortly after its turn begins and dim for most of the round`() {
        assertThat(LatticeMotion.connectingAlpha(0f, index = 0)).isWithin(TOLERANCE).of(0.15f)
        assertThat(LatticeMotion.connectingAlpha(0.08f * LatticeMotion.CONNECTING_PERIOD, index = 0)).isWithin(TOLERANCE).of(1f)
        assertThat(LatticeMotion.connectingAlpha(0.3f * LatticeMotion.CONNECTING_PERIOD, index = 0)).isWithin(TOLERANCE).of(0.15f)
        assertThat(LatticeMotion.connectingAlpha(0.9f * LatticeMotion.CONNECTING_PERIOD, index = 0)).isWithin(TOLERANCE).of(0.15f)
    }

    @Test
    fun `the light goes round, one dot after the other`() {
        val step = LatticeMotion.CONNECTING_PERIOD / 9f
        val peak = 0.08f * LatticeMotion.CONNECTING_PERIOD
        LatticeMotion.connectingDots.indices.forEach { index ->
            val seconds = index * step + peak
            val brightest = LatticeMotion.connectingDots.indices.maxBy { LatticeMotion.connectingAlpha(seconds, it) }
            assertThat(brightest).isEqualTo(index)
        }
    }

    @Test
    fun `a speaking bar moves between a third and its full height`() {
        assertThat(LatticeMotion.speakingScale(0f, index = 1)).isWithin(TOLERANCE).of(0.35f)
        assertThat(LatticeMotion.speakingScale(LatticeMotion.SPEAKING_PERIOD / 2, index = 1)).isWithin(TOLERANCE).of(1f)
        var seconds = 0f
        while (seconds < LatticeMotion.SPEAKING_PERIOD) {
            LatticeMotion.speakingBars.indices.forEach { index ->
                assertThat(LatticeMotion.speakingScale(seconds, index)).isAtLeast(0.35f - TOLERANCE)
                assertThat(LatticeMotion.speakingScale(seconds, index)).isAtMost(1f + TOLERANCE)
            }
            seconds += 0.05f
        }
    }

    @Test
    fun `nothing moves without a clock`() {
        assertThat(LatticeMotion.connectingDots.indices.map { LatticeMotion.connectingAlpha(null, it) }.distinct()).containsExactly(0.5f)
        assertThat(LatticeMotion.speakingBars.indices.map { LatticeMotion.speakingScale(null, it) }.distinct()).containsExactly(1f)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
