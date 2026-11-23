/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.api.rtc

/**
 * How a screen share is captured and published. Read when the share starts; a running share keeps what it started with.
 */
data class MatrixRtcScreenShareOptions(
    /** The longest edge of the published picture, in pixels. A screen smaller than this is sent at its own size. */
    val maxEdge: Int,
    /** The capture and encoder frame rate. */
    val framesPerSecond: Int,
    /**
     * Whether what the device plays (media, games) is published with the share, as a stream of its own. Needs Android 10;
     * an app that opts out of playback capture, and anything played as a call, is silent in it.
     */
    val sharesDeviceAudio: Boolean,
    /**
     * The bitrate the device's audio is encoded at, in bit/s: stereo Opus, sent whole (no DTX, no RED). One of
     * [AUDIO_BITRATES].
     */
    val audioBitrate: Int = DEFAULT_AUDIO_BITRATE,
) {
    /** The values held to what the capture supports, so a stored value from another version cannot ask for anything else. */
    fun normalized(): MatrixRtcScreenShareOptions = copy(
        maxEdge = MAX_EDGES.minByOrNull { kotlin.math.abs(it - maxEdge) } ?: DEFAULT.maxEdge,
        framesPerSecond = FRAME_RATES.minByOrNull { kotlin.math.abs(it - framesPerSecond) } ?: DEFAULT.framesPerSecond,
        audioBitrate = AUDIO_BITRATES.minByOrNull { kotlin.math.abs(it - audioBitrate) } ?: DEFAULT_AUDIO_BITRATE,
    )

    companion object {
        /** What the share sheet offers, smallest first. */
        val MAX_EDGES: List<Int> = listOf(1280, 1920, 2560)
        val FRAME_RATES: List<Int> = listOf(15, 30)

        /** Music at 128, 160 and 192 kbit/s; the microphone's speech encoding is 48. */
        val AUDIO_BITRATES: List<Int> = listOf(128_000, 160_000, 192_000)
        const val DEFAULT_AUDIO_BITRATE = 160_000

        /** A phone's own resolution up to 1440p, at 30 frames a second, with its audio at 160 kbit/s. */
        val DEFAULT = MatrixRtcScreenShareOptions(maxEdge = 2560, framesPerSecond = 30, sharesDeviceAudio = true)
    }
}
