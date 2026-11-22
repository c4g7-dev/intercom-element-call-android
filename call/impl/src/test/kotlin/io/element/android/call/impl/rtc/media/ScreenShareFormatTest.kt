/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.rtc.MatrixRtcScreenShareOptions
import org.junit.Test

class ScreenShareFormatTest {
    @Test
    fun `a screen within the limit is captured at its own size`() {
        assertThat(ScreenVideoCapture.scaledToFit(1080, 2400, maxEdge = 2560)).isEqualTo(1080 to 2400)
        assertThat(ScreenVideoCapture.scaledToFit(2400, 1080, maxEdge = 2560)).isEqualTo(2400 to 1080)
    }

    @Test
    fun `a larger screen is scaled to the limit with its shape kept and even dimensions`() {
        assertThat(ScreenVideoCapture.scaledToFit(1440, 3200, maxEdge = 2560)).isEqualTo(1152 to 2560)
        assertThat(ScreenVideoCapture.scaledToFit(1080, 2400, maxEdge = 1280)).isEqualTo(576 to 1280)
        val (width, height) = ScreenVideoCapture.scaledToFit(1079, 2399, maxEdge = 1920)
        assertThat(width % 2).isEqualTo(0)
        assertThat(height % 2).isEqualTo(0)
    }

    @Test
    fun `a screen of no size is a square of the limit`() {
        assertThat(ScreenVideoCapture.scaledToFit(0, 0, maxEdge = 1280)).isEqualTo(1280 to 1280)
    }

    @Test
    fun `the bitrate follows the picture, between its floor and ten megabits`() {
        assertThat(VideoFormat.screenBitrate(576, 1280, 30)).isEqualTo(2_500_000L)
        assertThat(VideoFormat.screenBitrate(1080, 2400, 30)).isEqualTo(8_294_400L)
        assertThat(VideoFormat.screenBitrate(1440, 2560, 30)).isEqualTo(10_000_000L)
        assertThat(VideoFormat.screenBitrate(1440, 2560, 15)).isEqualTo(6_500_000L)
    }

    @Test
    fun `the device audio is 48 kHz stereo in 10 ms frames`() {
        assertThat(ScreenAudioFormat.SAMPLES_PER_FRAME).isEqualTo(480)
        assertThat(ScreenAudioFormat.BYTES_PER_FRAME).isEqualTo(1920)
    }

    @Test
    fun `stored options are held to what the share offers`() {
        assertThat(MatrixRtcScreenShareOptions(maxEdge = 1, framesPerSecond = 60, sharesDeviceAudio = true).normalized())
            .isEqualTo(MatrixRtcScreenShareOptions(maxEdge = 1280, framesPerSecond = 30, sharesDeviceAudio = true))
        assertThat(MatrixRtcScreenShareOptions.DEFAULT.normalized()).isEqualTo(MatrixRtcScreenShareOptions.DEFAULT)
    }
}
