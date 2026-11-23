/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.call.api.rtc.MatrixRtcPlaybackSource
import io.element.android.call.api.rtc.MatrixRtcPlaybackVolume
import io.element.android.call.api.rtc.MatrixRtcStreamKind
import io.element.android.call.api.rtc.personTile
import io.element.android.call.ui.preview.ElementCallPreview
import io.element.android.call.ui.preview.PreviewsDayNight
import io.element.android.call.ui.theme.ElementCallTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How loud one tile is played on this phone, opened by a long press on it: a person's voice, or the audio of a shared
 * screen. 0 to 200 %, a mute, and the way back to 100 %. Nobody else hears the difference, and the choice is kept for
 * that person from call to call.
 */
@Composable
internal fun ElementCallTileVolumeSheet(state: ElementCallScreenState, tile: CallTileData) {
    val dismiss = { state.eventSink(ElementCallScreenEvent.DismissTileVolume) }
    ElementCallSettingsModalSheet(onDismiss = dismiss, testTag = ElementCallTestTags.TILE_VOLUME_SHEET) {
        ElementCallTileVolumeContent(state = state, tile = tile)
    }
}

@Composable
internal fun ElementCallTileVolumeContent(
    state: ElementCallScreenState,
    tile: CallTileData,
    modifier: Modifier = Modifier,
) {
    val source = tile.playbackSource
    val volume = state.playbackVolume(tile)
    val change = { changed: MatrixRtcPlaybackVolume -> state.eventSink(ElementCallScreenEvent.SetPlaybackVolume(source, changed)) }
    ElementCallSettingsPage(
        title = stringResource(
            if (tile.isScreenShare) R.string.element_call_tile_volume_title_screen_share else R.string.element_call_tile_volume_title_voice,
            tile.displayName,
        ),
        modifier = modifier,
    ) {
        ElementCallSettingsSection(title = null) {
            TileVolumeRow(
                volume = volume,
                // A slider moved while muted is a wish to hear them again.
                onChange = { change(volume.copy(volume = snapToDefault(it), isMuted = false)) },
            )
            ElementCallSettingsToggle(
                title = stringResource(R.string.element_call_tile_volume_mute),
                description = stringResource(R.string.element_call_tile_volume_mute_description),
                checked = volume.isMuted,
                onCheckedChange = { change(volume.copy(isMuted = it)) },
                testTag = ElementCallTestTags.TILE_VOLUME_MUTE,
            )
        }
        Text(
            text = stringResource(R.string.element_call_tile_volume_description),
            style = ElementCallTheme.typography.bodyMdRegular,
            color = ElementCallTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { change(MatrixRtcPlaybackVolume.DEFAULT) },
            enabled = !volume.isDefault,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ElementCallTestTags.TILE_VOLUME_RESET),
        ) {
            Text(
                text = stringResource(R.string.element_call_tile_volume_reset),
                style = ElementCallTheme.typography.bodyLgMedium,
                color = ElementCallTheme.colors.textPrimary.copy(alpha = if (volume.isDefault) DISABLED_RESET_ALPHA else 1f),
            )
        }
    }
}

@Composable
private fun TileVolumeRow(volume: MatrixRtcPlaybackVolume, onChange: (Float) -> Unit) {
    val description = stringResource(R.string.element_call_tile_volume_slider)
    val label = if (volume.isMuted) stringResource(R.string.element_call_tile_volume_muted) else volume.label()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = ElementCallTheme.icons.speaker,
            contentDescription = null,
            tint = ElementCallTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = volume.volume,
            onValueChange = onChange,
            valueRange = 0f..MatrixRtcPlaybackVolume.MAX_VOLUME,
            colors = SliderDefaults.colors(
                thumbColor = ElementCallTheme.colors.textPrimary,
                activeTrackColor = ElementCallTheme.colors.textPrimary,
                inactiveTrackColor = ElementCallTheme.colors.borderControl,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = description
                    stateDescription = label
                }
                .testTag(ElementCallTestTags.TILE_VOLUME_SLIDER),
        )
        Column(modifier = Modifier.widthIn(min = 64.dp)) {
            Text(
                text = label,
                style = ElementCallTheme.typography.bodyMdMedium,
                color = ElementCallTheme.colors.textPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ElementCallTestTags.TILE_VOLUME_VALUE),
            )
        }
    }
}

/** The slider's position as it is shown: the percentage, and above 100 % what it adds, as "150% · +6 dB". */
internal fun MatrixRtcPlaybackVolume.label(): String {
    val percent = "${(volume * PERCENT).roundToInt()}%"
    val boost = boostDb ?: return percent
    val decibels = if (boost == boost.roundToInt().toFloat()) boost.roundToInt().toString() else boost.toString()
    return "$percent · +$decibels dB"
}

/** 100 % is where most people want to land again, and a finger does not find it to the percent. */
internal fun snapToDefault(volume: Float): Float = if (abs(volume - 1f) < SNAP) 1f else volume

/** What a tile's volume is about: its user's voice, or their share's audio. */
internal val CallTileData.playbackSource: MatrixRtcPlaybackSource
    get() = MatrixRtcPlaybackSource(userId, if (isScreenShare) MatrixRtcStreamKind.SCREEN_SHARE_AUDIO else MatrixRtcStreamKind.MICROPHONE)

private const val PERCENT = 100f
private const val SNAP = 0.03f
private const val DISABLED_RESET_ALPHA = 0.38f

// The sheet itself needs a real window, so its content is previewed instead, as the Preferences sheet's is.
@PreviewsDayNight
@Composable
internal fun ElementCallTileVolumeContentPreview(@PreviewParameter(MatrixRtcPlaybackVolumePreviewParam::class) volume: MatrixRtcPlaybackVolume) =
    ElementCallPreview {
        val tile = aRemoteParticipant().personTile().toCallTileData(roomMembers = emptyMap(), isLocal = false, isFrontCamera = true)
        ElementCallTileVolumeContent(
            state = anElementCallScreenState(playbackVolumes = mapOf(tile.playbackSource to volume), volumeTile = tile),
            tile = tile,
            modifier = Modifier.background(ElementCallTheme.colors.bgSubtlePrimary),
        )
    }

open class MatrixRtcPlaybackVolumePreviewParam : PreviewParameterProvider<MatrixRtcPlaybackVolume> {
    override val values: Sequence<MatrixRtcPlaybackVolume>
        get() = sequenceOf(
            MatrixRtcPlaybackVolume.DEFAULT,
            MatrixRtcPlaybackVolume(volume = 1.5f),
            MatrixRtcPlaybackVolume(volume = 0.4f, isMuted = true),
        )
}
