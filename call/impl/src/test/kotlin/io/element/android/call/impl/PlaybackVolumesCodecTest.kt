/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.rtc.MatrixRtcPlaybackSource
import io.element.android.call.api.rtc.MatrixRtcPlaybackVolume
import io.element.android.call.api.rtc.MatrixRtcStreamKind
import io.element.android.call.api.rtc.id.UserId
import io.element.android.call.test.FakeElementCallPreferencesStore
import org.junit.Test

class PlaybackVolumesCodecTest {
    private val alice = UserId("@alice:example.org")
    private val bob = UserId("@bob:example.org:8448")

    @Test
    fun `volumes come back as they were kept, per user and per source`() {
        val volumes = mapOf(
            MatrixRtcPlaybackSource(alice, MatrixRtcStreamKind.MICROPHONE) to MatrixRtcPlaybackVolume(volume = 1.5f),
            MatrixRtcPlaybackSource(alice, MatrixRtcStreamKind.SCREEN_SHARE_AUDIO) to MatrixRtcPlaybackVolume(volume = 0.4f, isMuted = true),
            MatrixRtcPlaybackSource(bob, MatrixRtcStreamKind.MICROPHONE) to MatrixRtcPlaybackVolume(volume = 1f, isMuted = true),
        )

        assertThat(PlaybackVolumesCodec.decode(PlaybackVolumesCodec.encode(volumes))).isEqualTo(volumes)
    }

    @Test
    fun `an entry that cannot be read is left out and the rest are kept`() {
        val entries = setOf(
            "MICROPHONE 1.5 0 @alice:example.org",
            "CAMERA 1.5 0 @alice:example.org",
            "MICROPHONE loud 0 @bob:example.org",
            "MICROPHONE 1.5 0 not a user id",
            "SCREEN_SHARE_AUDIO 9 1 @bob:example.org:8448",
            "",
        )

        assertThat(PlaybackVolumesCodec.decode(entries)).containsExactly(
            MatrixRtcPlaybackSource(alice, MatrixRtcStreamKind.MICROPHONE),
            MatrixRtcPlaybackVolume(volume = 1.5f),
            // Held to the slider's range.
            MatrixRtcPlaybackSource(bob, MatrixRtcStreamKind.SCREEN_SHARE_AUDIO),
            MatrixRtcPlaybackVolume(volume = 2f, isMuted = true),
        )
    }

    @Test
    fun `the default is not worth an entry`() {
        val store = FakeElementCallPreferencesStore()
        val stored = StoredElementCallPreferences(store)
        val voice = MatrixRtcPlaybackSource(alice, MatrixRtcStreamKind.MICROPHONE)

        assertThat(stored.setPlaybackVolume(voice, MatrixRtcPlaybackVolume(volume = 3f))).containsExactly(voice, MatrixRtcPlaybackVolume(volume = 2f))
        assertThat(store.playbackVolumes).containsExactly(voice, MatrixRtcPlaybackVolume(volume = 2f))
        assertThat(stored.value.value.playbackVolumes).isEqualTo(store.playbackVolumes)

        assertThat(stored.setPlaybackVolume(voice, MatrixRtcPlaybackVolume.DEFAULT)).isEmpty()
        assertThat(store.playbackVolumes).isEmpty()
    }
}
