/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.api.rtc

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.rtc.id.UserId
import org.junit.Test

class MatrixRtcPlaybackVolumeTest {
    /** The desktop's curve: the square below 100 %, 0 to +12 dB linear in dB above it. */
    @Test
    fun `the gain follows the desktop's curve`() {
        assertThat(MatrixRtcPlaybackVolume.gainOf(0f)).isEqualTo(0f)
        assertThat(MatrixRtcPlaybackVolume.gainOf(0.5f)).isWithin(TOLERANCE).of(0.25f)
        assertThat(MatrixRtcPlaybackVolume.gainOf(1f)).isEqualTo(1f)
        // +6 dB is twice the amplitude, +12 dB four times.
        assertThat(MatrixRtcPlaybackVolume.gainOf(1.5f)).isWithin(TOLERANCE).of(1.9953f)
        assertThat(MatrixRtcPlaybackVolume.gainOf(2f)).isWithin(TOLERANCE).of(3.9811f)
        assertThat(MatrixRtcPlaybackVolume.gainDbOf(0.5f)).isWithin(0.05f).of(-12.04f)
        assertThat(MatrixRtcPlaybackVolume.gainDbOf(2f)).isWithin(TOLERANCE).of(12f)
        assertThat(MatrixRtcPlaybackVolume.gainDbOf(0f)).isNull()
    }

    @Test
    fun `a volume outside the slider is held to it`() {
        assertThat(MatrixRtcPlaybackVolume.gainOf(5f)).isEqualTo(MatrixRtcPlaybackVolume.gainOf(2f))
        assertThat(MatrixRtcPlaybackVolume.gainOf(-1f)).isEqualTo(0f)
        assertThat(MatrixRtcPlaybackVolume.gainOf(Float.NaN)).isEqualTo(0f)
        assertThat(MatrixRtcPlaybackVolume(volume = 5f).normalized().volume).isEqualTo(2f)
        assertThat(MatrixRtcPlaybackVolume(volume = -1f).normalized().volume).isEqualTo(0f)
        assertThat(MatrixRtcPlaybackVolume(volume = Float.NaN).normalized().volume).isEqualTo(1f)
    }

    @Test
    fun `muted plays nothing and keeps the slider where it was`() {
        val muted = MatrixRtcPlaybackVolume(volume = 1.5f, isMuted = true)
        assertThat(muted.gain).isEqualTo(0f)
        assertThat(muted.boostDb).isNull()
        assertThat(muted.copy(isMuted = false).gain).isWithin(TOLERANCE).of(1.9953f)
        assertThat(muted.isDefault).isFalse()
    }

    @Test
    fun `the boost is said only above 100 percent`() {
        assertThat(MatrixRtcPlaybackVolume(volume = 1f).boostDb).isNull()
        assertThat(MatrixRtcPlaybackVolume(volume = 0.4f).boostDb).isNull()
        assertThat(MatrixRtcPlaybackVolume(volume = 1.5f).boostDb).isEqualTo(6f)
        assertThat(MatrixRtcPlaybackVolume(volume = 1.8f).boostDb).isEqualTo(9.6f)
        assertThat(MatrixRtcPlaybackVolume(volume = 2f).boostDb).isEqualTo(12f)
    }

    @Test
    fun `a person tile is about a voice and a share tile about its audio`() {
        val user = UserId("@alice:example.org")
        assertThat(MatrixRtcPlaybackSource.of(user, MatrixRtcTileKind.PERSON))
            .isEqualTo(MatrixRtcPlaybackSource(user, MatrixRtcStreamKind.MICROPHONE))
        assertThat(MatrixRtcPlaybackSource.of(user, MatrixRtcTileKind.SCREEN_SHARE))
            .isEqualTo(MatrixRtcPlaybackSource(user, MatrixRtcStreamKind.SCREEN_SHARE_AUDIO))
    }

    @Test
    fun `a share's audio bitrate is held to what is offered`() {
        val options = MatrixRtcScreenShareOptions.DEFAULT
        assertThat(options.audioBitrate).isEqualTo(160_000)
        assertThat(options.copy(audioBitrate = 48_000).normalized().audioBitrate).isEqualTo(128_000)
        assertThat(options.copy(audioBitrate = 170_000).normalized().audioBitrate).isEqualTo(160_000)
        assertThat(options.copy(audioBitrate = 510_000).normalized().audioBitrate).isEqualTo(192_000)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
