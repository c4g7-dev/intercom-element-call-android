/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

/** What we ask the screen capture for. The camera's format is [CameraCaptureFormat]'s. */
internal object VideoFormat {
    /**
     * The bitrate a screen share may use at most, for a picture of [width] by [height] at [framesPerSecond].
     *
     * One layer carries the whole share, so this is the cap on everything it sends: about 8 Mbit/s for a phone's
     * own 1080 by 2400 and 10 at 1440p, where text stays sharp while scrolling. The encoder uses less on a still screen.
     */
    fun screenBitrate(width: Int, height: Int, framesPerSecond: Int): Long {
        val atFullRate = (width.toLong() * height * SCREEN_BITS_PER_PIXEL_TENTHS / 10).coerceIn(SCREEN_MIN_BITRATE, SCREEN_MAX_BITRATE)
        return if (framesPerSecond >= SCREEN_FULL_FPS) atFullRate else atFullRate * SCREEN_LOW_FPS_SHARE_PERCENT / 100
    }

    /** Where everything is counted in frames, at the higher of the two rates a share can run at. */
    const val SCREEN_FULL_FPS = 30
    private const val SCREEN_BITS_PER_PIXEL_TENTHS = 32L
    private const val SCREEN_MIN_BITRATE = 2_500_000L
    private const val SCREEN_MAX_BITRATE = 10_000_000L
    private const val SCREEN_LOW_FPS_SHARE_PERCENT = 65L
}
