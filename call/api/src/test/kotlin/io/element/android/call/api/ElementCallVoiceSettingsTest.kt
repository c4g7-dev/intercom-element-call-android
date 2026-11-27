/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ElementCallVoiceSettingsTest {
    @Test
    fun `deafening mutes and remembers the microphone that was on`() {
        val deafened = ElementCallVoiceSettings().withDeafened(true)
        assertThat(deafened).isEqualTo(ElementCallVoiceSettings(isMicrophoneMuted = true, isDeafened = true, wasMutedBeforeDeafen = false))
        assertThat(deafened.withDeafened(false)).isEqualTo(ElementCallVoiceSettings())
    }

    @Test
    fun `undeafening leaves a microphone muted that was muted before`() {
        val deafened = ElementCallVoiceSettings(isMicrophoneMuted = true).withDeafened(true)
        assertThat(deafened.wasMutedBeforeDeafen).isTrue()
        assertThat(deafened.withDeafened(false)).isEqualTo(ElementCallVoiceSettings(isMicrophoneMuted = true))
    }

    @Test
    fun `unmuting while deafened undeafens, whatever the microphone was before`() {
        val deafened = ElementCallVoiceSettings(isMicrophoneMuted = true).withDeafened(true)
        assertThat(deafened.withMicrophoneMuted(false)).isEqualTo(ElementCallVoiceSettings())
    }

    @Test
    fun `muting while deafened changes nothing, and neither does a state asked for twice`() {
        val deafened = ElementCallVoiceSettings().withDeafened(true)
        assertThat(deafened.withMicrophoneMuted(true)).isSameInstanceAs(deafened)
        assertThat(deafened.withDeafened(true)).isSameInstanceAs(deafened)
        val hearing = ElementCallVoiceSettings()
        assertThat(hearing.withDeafened(false)).isSameInstanceAs(hearing)
        assertThat(hearing.withMicrophoneMuted(false)).isSameInstanceAs(hearing)
    }

    @Test
    fun `a stored state that cannot be is put right`() {
        assertThat(ElementCallVoiceSettings(isDeafened = true).normalized()).isEqualTo(ElementCallVoiceSettings(isMicrophoneMuted = true, isDeafened = true))
        assertThat(ElementCallVoiceSettings(wasMutedBeforeDeafen = true).normalized()).isEqualTo(ElementCallVoiceSettings())
    }
}
