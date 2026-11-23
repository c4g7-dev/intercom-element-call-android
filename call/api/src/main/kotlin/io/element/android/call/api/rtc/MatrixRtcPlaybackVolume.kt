/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.api.rtc

import io.element.android.call.api.rtc.id.UserId
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One thing a call plays that has a volume of its own: a user's voice ([MatrixRtcStreamKind.MICROPHONE]) or the audio of
 * their screen share ([MatrixRtcStreamKind.SCREEN_SHARE_AUDIO]).
 *
 * By user rather than by member: a member id is new at every join, and a volume is kept from call to call.
 */
data class MatrixRtcPlaybackSource(
    val userId: UserId,
    val kind: MatrixRtcStreamKind,
) {
    companion object {
        /** What a tile's volume is about: a person tile's voice, a share tile's audio. */
        fun of(userId: UserId, tileKind: MatrixRtcTileKind) = MatrixRtcPlaybackSource(
            userId = userId,
            kind = when (tileKind) {
                MatrixRtcTileKind.PERSON -> MatrixRtcStreamKind.MICROPHONE
                MatrixRtcTileKind.SCREEN_SHARE -> MatrixRtcStreamKind.SCREEN_SHARE_AUDIO
            },
        )
    }
}

/**
 * How loud one [MatrixRtcPlaybackSource] is played on this device. Nobody else hears the difference.
 *
 * [volume] is the slider's position, 0 to [MAX_VOLUME] where 1 is as sent; [isMuted] silences it and keeps the position
 * for when it is unmuted. [gain] is what the samples are multiplied by.
 */
data class MatrixRtcPlaybackVolume(
    val volume: Float = 1f,
    val isMuted: Boolean = false,
) {
    /** The amplitude factor to play with: 0 when muted. */
    val gain: Float get() = if (isMuted) 0f else gainOf(volume)

    /** The gain in dB above what was sent, for a slider above 100 %; null at or below it and when muted. */
    val boostDb: Float? get() = if (isMuted || volume <= 1f) null else ((volume.coerceAtMost(MAX_VOLUME) - 1f) * MAX_BOOST_DB * 10f).roundToInt() / 10f

    val isDefault: Boolean get() = this == DEFAULT

    /** [volume] held to the slider's range, and a value that is no number read as unchanged. */
    fun normalized(): MatrixRtcPlaybackVolume = copy(volume = if (volume.isFinite()) volume.coerceIn(0f, MAX_VOLUME) else 1f)

    companion object {
        /** The top of the slider: 200 %. */
        const val MAX_VOLUME = 2f

        /** What the second half of the slider adds: 12 dB is four times the amplitude. */
        const val MAX_BOOST_DB = 12f

        val DEFAULT = MatrixRtcPlaybackVolume()

        /**
         * The same curve as InterCom on the desktop, in steps the ear takes as even: 0 to 100 % is the square of the
         * slider (half way is -12 dB, about half as loud), 100 to 200 % is 0 to +12 dB linear in dB (150 % is +6 dB).
         */
        fun gainOf(volume: Float): Float {
            if (!volume.isFinite() || volume <= 0f) return 0f
            val held = volume.coerceAtMost(MAX_VOLUME)
            return if (held <= 1f) held * held else 10f.pow((held - 1f) * MAX_BOOST_DB / 20f)
        }

        /** [gainOf] in dB; null for silence. */
        fun gainDbOf(volume: Float): Float? = gainOf(volume).takeIf { it > 0f }?.let { 20f * log10(it) }
    }
}
