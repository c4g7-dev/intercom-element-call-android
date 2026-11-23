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

/** What the share's audio track is published with: the encoder settings the core is handed, read back field by field. */
class ScreenAudioEncodingTest {
    @Test
    fun `a share's audio is stereo at 160 kbit per second, with neither DTX nor RED`() {
        val encoding = ScreenAudioEncoding.of(MatrixRtcScreenShareOptions.DEFAULT.audioBitrate)

        assertThat(encoding.maxBitrateBps).isEqualTo(160_000uL)
        assertThat(encoding.stereo).isTrue()
        assertThat(encoding.dtx).isFalse()
        assertThat(encoding.red).isFalse()
    }

    @Test
    fun `every bitrate the sheet offers is passed as it is`() {
        MatrixRtcScreenShareOptions.AUDIO_BITRATES.forEach { bitrate ->
            assertThat(ScreenAudioEncoding.of(bitrate).maxBitrateBps).isEqualTo(bitrate.toULong())
        }
    }

    @Test
    fun `a bitrate from elsewhere is held within 128 and 192 kbit per second`() {
        assertThat(ScreenAudioEncoding.of(48_000).maxBitrateBps).isEqualTo(128_000uL)
        assertThat(ScreenAudioEncoding.of(510_000).maxBitrateBps).isEqualTo(192_000uL)
    }

    @Test
    fun `the capture feeds two channels at 48 kHz`() {
        assertThat(ScreenAudioFormat.CHANNEL_COUNT).isEqualTo(2)
        assertThat(ScreenAudioFormat.SAMPLE_RATE).isEqualTo(48_000)
    }
}
