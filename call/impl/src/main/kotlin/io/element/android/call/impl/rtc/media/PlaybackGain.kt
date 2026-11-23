/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * One played stream's volume, applied to its PCM before it reaches the device: a gain, and above 1 a limiter.
 *
 * Done on the samples rather than with `AudioTrack.setVolume`, which stops at 1, or the platform's `LoudnessEnhancer`,
 * which attaches to an audio session, differs from device to device and would be one more native object per member to
 * keep alive and release. The samples are already in our hands on their way to the track, so the gain is exact, the
 * same on every device and costs a multiplication.
 *
 * The limiter is what makes 200 % usable: four times the amplitude clips anything louder than a quarter of full scale.
 * It turns the stream down for as long as a peak would pass [LIMIT], at once on the way down and over [RELEASE_MS] on
 * the way back, both channels by the same amount so the stereo image stays where it was. Below 1 nothing can exceed
 * full scale and the limiter is not in the path.
 *
 * A change of gain is spread over the buffer it arrives in, since a step in the middle of a waveform is a click.
 *
 * Not thread safe apart from [gain]: one playback loop owns [apply].
 */
internal class PlaybackGain(sampleRate: Int = AudioFormat.SAMPLE_RATE) {
    /** What the samples are multiplied by: 0 is silence, 1 leaves them as they are. Set from any thread. */
    @Volatile
    var gain: Float = 1f

    /** The gain the last buffer ended on, which the next one starts from. */
    private var applied = Float.NaN

    /** The limiter's own factor: 1 while it is letting everything through. */
    private var reduction = 1f

    /** Per sample, the share of the way back to 1 the limiter recovers. */
    private val release = 1f - exp(-1f / (RELEASE_MS / 1000f * sampleRate))

    /** Whether the limiter is holding the stream down right now, for a log line. */
    val isLimiting: Boolean get() = reduction < 1f

    /**
     * Applies the volume to [pcm] in place: interleaved little-endian 16 bit samples of [channels] channels.
     */
    fun apply(pcm: ByteArray, channels: Int) {
        val target = gain.takeIf { it.isFinite() && it > 0f } ?: 0f
        // The first buffer starts at its own gain rather than fading in from somewhere.
        val from = if (applied.isNaN()) target else applied
        applied = target
        if (from == 1f && target == 1f && reduction == 1f) return
        val frames = pcm.size / (Short.SIZE_BYTES * channels)
        if (frames == 0) return
        val step = (target - from) / frames
        var current = from
        for (frame in 0 until frames) {
            current += step
            val offset = frame * channels * Short.SIZE_BYTES
            var peak = 0f
            for (channel in 0 until channels) {
                peak = max(peak, abs(readSample(pcm, offset + channel * Short.SIZE_BYTES) * current))
            }
            val wanted = if (peak > LIMIT) LIMIT / peak else 1f
            // Down at once, so that no peak passes; back up slowly, so that the level does not pump with every wave.
            // Never further back than this frame allows, or the step of recovery itself would pass the limit.
            reduction = min(wanted, reduction + (1f - reduction) * release)
            if (reduction > NEARLY_ONE) reduction = 1f
            val factor = current * reduction
            for (channel in 0 until channels) {
                val at = offset + channel * Short.SIZE_BYTES
                writeSample(pcm, at, readSample(pcm, at) * factor)
            }
        }
    }

    private fun readSample(pcm: ByteArray, at: Int): Int = pcm[at].toInt() and BYTE_MASK or (pcm[at + 1].toInt() shl Byte.SIZE_BITS)

    private fun writeSample(pcm: ByteArray, at: Int, value: Float) {
        val sample = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[at] = sample.toByte()
        pcm[at + 1] = (sample shr Byte.SIZE_BITS).toByte()
    }

    companion object {
        /** Where the limiter holds peaks: 1 dB under full scale, which leaves room for the rounding. */
        const val LIMIT = 29_204f

        /** How long the limiter takes to let go, most of the way. */
        const val RELEASE_MS = 150f

        /** Within a hundredth of a dB of letting everything through, which is where the limiter steps out of the path. */
        private const val NEARLY_ONE = 0.999f
        private const val BYTE_MASK = 0xFF
    }
}

/**
 * [pcm] of [from] channels as [to] channels: one channel doubled, two averaged. The same array when there is nothing to do.
 * Only mono and stereo exist on this path.
 */
internal fun adaptChannels(pcm: ByteArray, from: Int, to: Int): ByteArray {
    if (from == to || from !in 1..2 || to !in 1..2) return pcm
    val frames = pcm.size / (Short.SIZE_BYTES * from)
    val out = ByteArray(frames * Short.SIZE_BYTES * to)
    for (frame in 0 until frames) {
        if (from == 1) {
            val low = pcm[frame * 2]
            val high = pcm[frame * 2 + 1]
            out[frame * 4] = low
            out[frame * 4 + 1] = high
            out[frame * 4 + 2] = low
            out[frame * 4 + 3] = high
        } else {
            val left = pcm[frame * 4].toInt() and 0xFF or (pcm[frame * 4 + 1].toInt() shl 8)
            val right = pcm[frame * 4 + 2].toInt() and 0xFF or (pcm[frame * 4 + 3].toInt() shl 8)
            val mixed = (left + right) / 2
            out[frame * 2] = mixed.toByte()
            out[frame * 2 + 1] = (mixed shr 8).toByte()
        }
    }
    return out
}
