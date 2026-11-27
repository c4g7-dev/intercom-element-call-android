/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.audio

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.ElementCallVoiceSettings
import org.junit.Test

class VoiceCueTest {
    private val hearing = ElementCallVoiceSettings()
    private val muted = ElementCallVoiceSettings(isMicrophoneMuted = true)

    @Test
    fun `muting and unmuting have their cues`() {
        assertThat(voiceCue(hearing, hearing.withMicrophoneMuted(true))).isEqualTo(CallSoundEffect.MUTE)
        assertThat(voiceCue(muted, muted.withMicrophoneMuted(false))).isEqualTo(CallSoundEffect.UNMUTE)
    }

    @Test
    fun `deafening says deafened only, although it mutes too`() {
        assertThat(voiceCue(hearing, hearing.withDeafened(true))).isEqualTo(CallSoundEffect.DEAFEN)
        assertThat(voiceCue(muted, muted.withDeafened(true))).isEqualTo(CallSoundEffect.DEAFEN)
    }

    @Test
    fun `undeafening says undeafened, by either button`() {
        val deafened = hearing.withDeafened(true)
        assertThat(voiceCue(deafened, deafened.withDeafened(false))).isEqualTo(CallSoundEffect.UNDEAFEN)
        assertThat(voiceCue(deafened, deafened.withMicrophoneMuted(false))).isEqualTo(CallSoundEffect.UNDEAFEN)
    }

    @Test
    fun `nothing changed, nothing said`() {
        assertThat(voiceCue(muted, muted.withMicrophoneMuted(true))).isNull()
        assertThat(voiceCue(hearing, hearing.withDeafened(false))).isNull()
    }
}
