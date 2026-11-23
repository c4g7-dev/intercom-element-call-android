/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PlaybackGainTest {
    @Test
    fun `at 1 the samples are not touched`() {
        val pcm = sine(amplitude = 20_000, frames = 480, channels = 2)
        val before = pcm.copyOf()

        PlaybackGain().apply(pcm, channels = 2)

        assertThat(pcm).isEqualTo(before)
    }

    @Test
    fun `below 1 every sample is scaled and nothing is limited`() {
        val gain = PlaybackGain().apply { gain = 0.25f }
        val pcm = sine(amplitude = 20_000, frames = 480, channels = 1)
        val before = samples(pcm)

        gain.apply(pcm, channels = 1)

        samples(pcm).forEachIndexed { index, sample -> assertThat(sample.toDouble()).isWithin(1.0).of(before[index] * 0.25) }
        assertThat(gain.isLimiting).isFalse()
    }

    @Test
    fun `zero is silence`() {
        val gain = PlaybackGain().apply { gain = 0f }
        val pcm = sine(amplitude = 30_000, frames = 480, channels = 2)

        gain.apply(pcm, channels = 2)

        assertThat(samples(pcm).all { it == 0 }).isTrue()
    }

    /** +12 dB on a quiet voice: four times the amplitude, well under the limit, so exactly that. */
    @Test
    fun `above 1 a quiet stream is simply louder`() {
        val gain = PlaybackGain().apply { gain = 4f }
        val pcm = sine(amplitude = 2_000, frames = 480, channels = 1)
        val before = samples(pcm)

        gain.apply(pcm, channels = 1)

        samples(pcm).forEachIndexed { index, sample -> assertThat(sample.toDouble()).isWithin(1.0).of(before[index] * 4.0) }
        assertThat(gain.isLimiting).isFalse()
    }

    /** What clipping would do is flatten every peak at full scale. The limiter turns the whole wave down instead. */
    @Test
    fun `above 1 a loud stream is held under the limit rather than clipped`() {
        val gain = PlaybackGain().apply { gain = 4f }
        val pcm = sine(amplitude = 20_000, frames = 4_800, channels = 2)

        gain.apply(pcm, channels = 2)

        val out = samples(pcm)
        assertThat(out.maxOf { abs(it) }).isAtMost(PlaybackGain.LIMIT.toInt() + 1)
        assertThat(out.none { abs(it) >= Short.MAX_VALUE }).isTrue()
        assertThat(gain.isLimiting).isTrue()
        // Still much louder than it came in: the peaks sit at the limit, not at the source's 20 000.
        assertThat(out.drop(out.size / 2).maxOf { abs(it) }).isAtLeast(25_000)
        // Both channels were turned down together.
        for (frame in 0 until out.size / 2) assertThat(out[frame * 2]).isEqualTo(out[frame * 2 + 1])
    }

    @Test
    fun `the limiter lets go once the stream is quiet again`() {
        val gain = PlaybackGain().apply { gain = 4f }
        gain.apply(sine(amplitude = 20_000, frames = 480, channels = 1), channels = 1)
        assertThat(gain.isLimiting).isTrue()

        // Two seconds of something quiet, in 10 ms buffers as playback delivers them.
        repeat(200) { gain.apply(sine(amplitude = 500, frames = 480, channels = 1), channels = 1) }

        assertThat(gain.isLimiting).isFalse()
        val pcm = sine(amplitude = 500, frames = 480, channels = 1)
        val before = samples(pcm)
        gain.apply(pcm, channels = 1)
        samples(pcm).forEachIndexed { index, sample -> assertThat(sample.toDouble()).isWithin(1.0).of(before[index] * 4.0) }
    }

    /** A step in the middle of a waveform is a click, so the buffer a change arrives in carries it from old to new. */
    @Test
    fun `a change of gain is spread over one buffer`() {
        val gain = PlaybackGain()
        gain.apply(constant(value = 10_000, frames = 480), channels = 1)
        gain.gain = 0f
        val pcm = constant(value = 10_000, frames = 480)

        gain.apply(pcm, channels = 1)

        val out = samples(pcm)
        assertThat(out.first()).isGreaterThan(9_900)
        assertThat(out[240]).isIn(4_900..5_100)
        assertThat(out.last()).isEqualTo(0)
        assertThat(out.zipWithNext().all { (a, b) -> b <= a }).isTrue()
    }

    @Test
    fun `one channel is doubled and two are averaged`() {
        val mono = bytes(100, -200, 300)
        assertThat(samples(adaptChannels(mono, from = 1, to = 2))).containsExactly(100, 100, -200, -200, 300, 300).inOrder()
        val stereo = bytes(100, 300, -200, -400, 32_767, 32_767)
        assertThat(samples(adaptChannels(stereo, from = 2, to = 1))).containsExactly(200, -300, 32_767).inOrder()
        assertThat(adaptChannels(mono, from = 1, to = 1)).isSameInstanceAs(mono)
        assertThat(adaptChannels(stereo, from = 2, to = 2)).isSameInstanceAs(stereo)
    }

    private fun sine(amplitude: Int, frames: Int, channels: Int): ByteArray {
        val values = IntArray(frames * channels) { index -> (amplitude * sin(2 * PI * 440 * (index / channels) / 48_000)).toInt() }
        return bytes(*values)
    }

    private fun constant(value: Int, frames: Int): ByteArray = bytes(*IntArray(frames) { value })

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size * 2).also { out ->
        values.forEachIndexed { index, value ->
            out[index * 2] = value.toByte()
            out[index * 2 + 1] = (value shr 8).toByte()
        }
    }

    private fun samples(pcm: ByteArray): List<Int> = List(pcm.size / 2) { index ->
        pcm[index * 2].toInt() and 0xFF or (pcm[index * 2 + 1].toInt() shl 8)
    }
}
