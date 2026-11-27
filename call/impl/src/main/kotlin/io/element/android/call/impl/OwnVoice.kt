/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl

import io.element.android.call.api.ElementCallSnapshot
import io.element.android.call.api.ElementCallVoiceSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * InterCom: the user's own microphone and deafen across calls. What is shown lives on the call's snapshot; this adds
 * what deafen returns the microphone to, keeps every change for the next call, and lets one change through at a time,
 * so that two taps cannot cross on their way to the transport.
 */
internal class OwnVoice(private val stored: StoredElementCallPreferences) {
    private var wasMutedBeforeDeafen = false
    private val mutex = Mutex()

    /** What a call starts with: the microphone and deafen as the last call left them. */
    fun atStart(): ElementCallVoiceSettings = stored.voiceState().also { wasMutedBeforeDeafen = it.wasMutedBeforeDeafen }

    /**
     * Applies [change] to the voice of the call [current] shows, keeps the result and hands both to [apply]. Nothing
     * without a call, or when nothing changes.
     */
    suspend fun change(
        current: () -> ElementCallSnapshot?,
        change: (ElementCallVoiceSettings) -> ElementCallVoiceSettings,
        apply: suspend (before: ElementCallVoiceSettings, after: ElementCallVoiceSettings) -> Unit,
    ) = mutex.withLock {
        // Read under the lock: the tap before this one has been applied by now.
        val snapshot = current() ?: return@withLock
        val before = ElementCallVoiceSettings(
            isMicrophoneMuted = snapshot.isMicrophoneMuted,
            isDeafened = snapshot.isDeafened,
            wasMutedBeforeDeafen = wasMutedBeforeDeafen,
        ).normalized()
        val after = change(before)
        if (after == before) return@withLock
        wasMutedBeforeDeafen = after.wasMutedBeforeDeafen
        stored.setVoiceState(after)
        apply(before, after)
    }
}
