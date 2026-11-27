/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.element.android.call.api.ElementCallPreferences
import io.element.android.call.api.ElementCallPreferencesStore
import io.element.android.call.api.ElementCallStageLayout
import io.element.android.call.api.ElementCallVoiceSettings
import io.element.android.call.api.rtc.MatrixRtcMembershipFormat
import io.element.android.call.api.rtc.MatrixRtcPlaybackSource
import io.element.android.call.api.rtc.MatrixRtcPlaybackVolume
import io.element.android.call.api.rtc.MatrixRtcScreenShareOptions
import io.element.android.call.api.rtc.MatrixRtcStreamKind
import io.element.android.call.api.rtc.MatrixRtcVideoCodec
import io.element.android.call.api.rtc.id.UserId
import io.element.android.call.impl.util.runCatchingExceptions

/**
 * The preferences on this device, surviving a restart, shared with nothing (feature-hq 037 R14, 043 R8, R13, R16).
 *
 * The 037 `reactions` key (On / Silent / Off) is not read: 043 starts everyone from the defaults.
 */
internal class DefaultElementCallPreferencesStore(context: Context) : ElementCallPreferencesStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var showsReactions: Boolean
        get() = prefs.getBoolean(KEY_SHOWS_REACTIONS, ElementCallPreferences.DEFAULT.showsReactions)
        set(value) = prefs.edit { putBoolean(KEY_SHOWS_REACTIONS, value) }

    override var playsReactionSounds: Boolean
        get() = prefs.getBoolean(KEY_PLAYS_REACTION_SOUNDS, ElementCallPreferences.DEFAULT.playsReactionSounds)
        set(value) = prefs.edit { putBoolean(KEY_PLAYS_REACTION_SOUNDS, value) }

    override var soundEffectsVolume: Float
        get() = prefs.getFloat(KEY_SOUND_EFFECTS_VOLUME, ElementCallPreferences.DEFAULT.soundEffectsVolume).coerceIn(0f, 1f)
        set(value) = prefs.edit { putFloat(KEY_SOUND_EFFECTS_VOLUME, value.coerceIn(0f, 1f)) }

    override var membershipFormat: MatrixRtcMembershipFormat
        get() = prefs.getEnum(KEY_MEMBERSHIP_FORMAT, ElementCallPreferences.DEFAULT.membershipFormat)
        set(value) = prefs.edit { putString(KEY_MEMBERSHIP_FORMAT, value.name) }

    override var videoCodec: MatrixRtcVideoCodec
        get() = prefs.getEnum(KEY_VIDEO_CODEC, ElementCallPreferences.DEFAULT.videoCodec)
        set(value) = prefs.edit { putString(KEY_VIDEO_CODEC, value.name) }

    override var screenShare: MatrixRtcScreenShareOptions
        get() = MatrixRtcScreenShareOptions(
            maxEdge = prefs.getInt(KEY_SCREEN_SHARE_MAX_EDGE, MatrixRtcScreenShareOptions.DEFAULT.maxEdge),
            framesPerSecond = prefs.getInt(KEY_SCREEN_SHARE_FPS, MatrixRtcScreenShareOptions.DEFAULT.framesPerSecond),
            sharesDeviceAudio = prefs.getBoolean(KEY_SCREEN_SHARE_DEVICE_AUDIO, MatrixRtcScreenShareOptions.DEFAULT.sharesDeviceAudio),
            audioBitrate = prefs.getInt(KEY_SCREEN_SHARE_AUDIO_BITRATE, MatrixRtcScreenShareOptions.DEFAULT.audioBitrate),
        ).normalized()
        set(value) = prefs.edit {
            putInt(KEY_SCREEN_SHARE_MAX_EDGE, value.maxEdge)
            putInt(KEY_SCREEN_SHARE_FPS, value.framesPerSecond)
            putBoolean(KEY_SCREEN_SHARE_DEVICE_AUDIO, value.sharesDeviceAudio)
            putInt(KEY_SCREEN_SHARE_AUDIO_BITRATE, value.audioBitrate)
        }

    override var stageLayout: ElementCallStageLayout
        get() = prefs.getEnum(KEY_STAGE_LAYOUT, ElementCallPreferences.DEFAULT.stageLayout)
        set(value) = prefs.edit { putString(KEY_STAGE_LAYOUT, value.name) }

    override var playbackVolumes: Map<MatrixRtcPlaybackSource, MatrixRtcPlaybackVolume>
        get() = PlaybackVolumesCodec.decode(prefs.getStringSet(KEY_PLAYBACK_VOLUMES, null).orEmpty())
        set(value) = prefs.edit { putStringSet(KEY_PLAYBACK_VOLUMES, PlaybackVolumesCodec.encode(value)) }

    override var voiceState: ElementCallVoiceSettings
        get() = ElementCallVoiceSettings(
            isMicrophoneMuted = prefs.getBoolean(KEY_MICROPHONE_MUTED, false),
            isDeafened = prefs.getBoolean(KEY_DEAFENED, false),
            wasMutedBeforeDeafen = prefs.getBoolean(KEY_MUTED_BEFORE_DEAFEN, false),
        ).normalized()
        set(value) = prefs.edit {
            putBoolean(KEY_MICROPHONE_MUTED, value.isMicrophoneMuted)
            putBoolean(KEY_DEAFENED, value.isDeafened)
            putBoolean(KEY_MUTED_BEFORE_DEAFEN, value.wasMutedBeforeDeafen)
        }

    /** By name; a name this version does not know reads as [default]. */
    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(key: String, default: T): T =
        getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    companion object {
        /** Named in CHANGES.md, for a host whose backup rules should leave it on the device. */
        const val FILE_NAME = "element_call_preferences"
        private const val KEY_SHOWS_REACTIONS = "shows_reactions"
        private const val KEY_PLAYS_REACTION_SOUNDS = "plays_reaction_sounds"
        private const val KEY_SOUND_EFFECTS_VOLUME = "sound_effects_volume"
        private const val KEY_MEMBERSHIP_FORMAT = "membership_format"
        private const val KEY_VIDEO_CODEC = "video_codec"
        private const val KEY_SCREEN_SHARE_MAX_EDGE = "screen_share_max_edge"
        private const val KEY_SCREEN_SHARE_FPS = "screen_share_fps"
        private const val KEY_SCREEN_SHARE_DEVICE_AUDIO = "screen_share_device_audio"
        private const val KEY_STAGE_LAYOUT = "stage_layout"
        private const val KEY_SCREEN_SHARE_AUDIO_BITRATE = "screen_share_audio_bitrate"
        private const val KEY_PLAYBACK_VOLUMES = "playback_volumes"
        private const val KEY_MICROPHONE_MUTED = "microphone_muted"
        private const val KEY_DEAFENED = "deafened"
        private const val KEY_MUTED_BEFORE_DEAFEN = "muted_before_deafen"
    }
}

/**
 * The kept volumes as a set of strings, one per source: `<kind> <volume> <muted> <user id>`. The user id is last and
 * taken whole, whatever it contains. An entry this version cannot read is left out, and plays as sent.
 */
internal object PlaybackVolumesCodec {
    fun encode(volumes: Map<MatrixRtcPlaybackSource, MatrixRtcPlaybackVolume>): Set<String> =
        volumes.mapTo(mutableSetOf()) { (source, volume) ->
            "${source.kind.name} ${volume.volume} ${if (volume.isMuted) 1 else 0} ${source.userId.value}"
        }

    fun decode(entries: Set<String>): Map<MatrixRtcPlaybackSource, MatrixRtcPlaybackVolume> =
        entries.mapNotNull(::decodeEntry).toMap()

    private fun decodeEntry(entry: String): Pair<MatrixRtcPlaybackSource, MatrixRtcPlaybackVolume>? {
        val parts = entry.split(' ', limit = FIELDS)
        if (parts.size != FIELDS) return null
        val kind = MatrixRtcStreamKind.entries.firstOrNull { it.name == parts[0] }
            ?.takeIf { it == MatrixRtcStreamKind.MICROPHONE || it == MatrixRtcStreamKind.SCREEN_SHARE_AUDIO }
            ?: return null
        val volume = parts[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        val userId = runCatchingExceptions { UserId(parts[3]) }.getOrNull() ?: return null
        return MatrixRtcPlaybackSource(userId, kind) to MatrixRtcPlaybackVolume(volume = volume, isMuted = parts[2] == "1").normalized()
    }

    private const val FIELDS = 4
}
