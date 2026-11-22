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
import io.element.android.call.api.rtc.MatrixRtcMembershipFormat
import io.element.android.call.api.rtc.MatrixRtcScreenShareOptions
import io.element.android.call.api.rtc.MatrixRtcVideoCodec

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
        ).normalized()
        set(value) = prefs.edit {
            putInt(KEY_SCREEN_SHARE_MAX_EDGE, value.maxEdge)
            putInt(KEY_SCREEN_SHARE_FPS, value.framesPerSecond)
            putBoolean(KEY_SCREEN_SHARE_DEVICE_AUDIO, value.sharesDeviceAudio)
        }

    override var stageLayout: ElementCallStageLayout
        get() = prefs.getEnum(KEY_STAGE_LAYOUT, ElementCallPreferences.DEFAULT.stageLayout)
        set(value) = prefs.edit { putString(KEY_STAGE_LAYOUT, value.name) }

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
    }
}
