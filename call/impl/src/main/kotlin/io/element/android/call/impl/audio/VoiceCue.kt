/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.audio

import io.element.android.call.api.ElementCallVoiceSettings

/**
 * InterCom: the cue for the user's own change of microphone or deafen, or null when nothing audible changed.
 *
 * One cue per tap: deafening also mutes and says only "deafened"; unmuting while deafened also undeafens and says
 * only "undeafened", as the desktop does.
 */
internal fun voiceCue(before: ElementCallVoiceSettings, after: ElementCallVoiceSettings): CallSoundEffect? = when {
    before.isDeafened != after.isDeafened -> if (after.isDeafened) CallSoundEffect.DEAFEN else CallSoundEffect.UNDEAFEN
    before.isMicrophoneMuted != after.isMicrophoneMuted -> if (after.isMicrophoneMuted) CallSoundEffect.MUTE else CallSoundEffect.UNMUTE
    else -> null
}
