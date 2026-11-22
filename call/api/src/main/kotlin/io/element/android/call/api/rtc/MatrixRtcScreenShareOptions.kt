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
) {
    /** The values held to what the capture supports, so a stored value from another version cannot ask for anything else. */
    fun normalized(): MatrixRtcScreenShareOptions = copy(
        maxEdge = MAX_EDGES.minByOrNull { kotlin.math.abs(it - maxEdge) } ?: DEFAULT.maxEdge,
        framesPerSecond = FRAME_RATES.minByOrNull { kotlin.math.abs(it - framesPerSecond) } ?: DEFAULT.framesPerSecond,
    )

    companion object {
        /** What the share sheet offers, smallest first. */
        val MAX_EDGES: List<Int> = listOf(1280, 1920, 2560)
        val FRAME_RATES: List<Int> = listOf(15, 30)

        /** A phone's own resolution up to 1440p, at 30 frames a second, with its audio. */
        val DEFAULT = MatrixRtcScreenShareOptions(maxEdge = 2560, framesPerSecond = 30, sharesDeviceAudio = true)
    }
}
