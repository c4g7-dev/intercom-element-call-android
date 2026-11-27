/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.audio

/**
 * Whether an announced join or leave makes a sound.
 *
 * Which changes are announced at all is the core's: nothing for the roster we arrive to, nothing while
 * our own membership is missing, nothing after the call ended. What is left here is the participant cap
 * and the throttle per kind.
 */
internal class JoinLeaveSoundPolicy(private val participantCap: Int) {
    private val lastPlayedAtMs = mutableMapOf<CallSoundEffect, Long>()
    private var isSilenced = false

    /** [participantCount] is the core's count after the change, ourselves included. Records the start of a sound it allows. */
    fun shouldPlay(sound: CallSoundEffect, participantCount: Int, atMs: Long): Boolean {
        if (isSilenced || participantCount > participantCap) return false
        val last = lastPlayedAtMs[sound]
        if (last != null && atMs - last < THROTTLE_MS) return false
        lastPlayedAtMs[sound] = atMs
        return true
    }

    /** For good: the call has ended for us, and teardown must not sound like everyone leaving. */
    fun silence() {
        isSilenced = true
    }

    /**
     * InterCom: whether the end of the call for us makes the leave sound: once, and not on top of the leave of the
     * other side that ended it (a DM call ends when the other hangs up, and their leave has just been heard).
     * Allowed after [silence], which holds back everyone else's while the call is taken down.
     */
    fun shouldPlayOwnLeave(atMs: Long): Boolean {
        val last = lastPlayedAtMs[CallSoundEffect.LEAVE]
        if (last != null && atMs - last < OWN_LEAVE_GAP_MS) return false
        lastPlayedAtMs[CallSoundEffect.LEAVE] = atMs
        return true
    }

    companion object {
        /** Web's `THROTTLE_SOUND_EFFECT_MS`, counted from the last sound of the same kind starting. */
        const val THROTTLE_MS = 500L

        /** InterCom: one leave sound for an end, whoever left first. */
        const val OWN_LEAVE_GAP_MS = 2_000L
    }
}
