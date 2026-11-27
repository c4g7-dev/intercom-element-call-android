/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.ElementCallVoiceSettings
import io.element.android.call.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

/** InterCom: the microphone and deafen survive from one call, and one run of the app, to the next. */
class VoiceStateStoreTest : RobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `nothing kept is unmuted and hearing`() {
        assertThat(DefaultElementCallPreferencesStore(context).voiceState).isEqualTo(ElementCallVoiceSettings())
    }

    @Test
    fun `what was written is read by a store made later`() {
        val deafened = ElementCallVoiceSettings().withDeafened(true)
        DefaultElementCallPreferencesStore(context).voiceState = deafened
        assertThat(DefaultElementCallPreferencesStore(context).voiceState).isEqualTo(deafened)

        val muted = ElementCallVoiceSettings(isMicrophoneMuted = true)
        DefaultElementCallPreferencesStore(context).voiceState = muted
        assertThat(StoredElementCallPreferences(DefaultElementCallPreferencesStore(context)).voiceState()).isEqualTo(muted)
    }
}
