/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.call.api.rtc.MatrixRtcScreenShareOptions
import io.element.android.call.ui.preview.ElementCallPreview
import io.element.android.call.ui.preview.PreviewsDayNight
import io.element.android.call.ui.theme.ElementCallTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * What a share is asked before it starts: whether the phone's audio goes with it, and how sharp and how smooth the
 * picture is. The choices are kept for the next share; Start goes on to the system's capture dialog.
 */
@Composable
internal fun ElementCallScreenShareSheet(state: ElementCallScreenState) {
    val dismiss = { state.eventSink(ElementCallScreenEvent.DismissSettingsSheet) }
    ElementCallSettingsModalSheet(onDismiss = dismiss, testTag = ElementCallTestTags.SCREEN_SHARE_SHEET) {
        ElementCallScreenShareContent(state = state)
    }
}

@Composable
internal fun ElementCallScreenShareContent(
    state: ElementCallScreenState,
    modifier: Modifier = Modifier,
    /** Playback capture is Android 10's. */
    isDeviceAudioSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
) {
    val options = state.screenShare
    val change = { changed: MatrixRtcScreenShareOptions -> state.eventSink(ElementCallScreenEvent.SetScreenShareOptions(changed)) }
    ElementCallSettingsPage(
        title = stringResource(R.string.element_call_screen_share_options_title),
        modifier = modifier,
    ) {
        ElementCallSettingsSection(title = null) {
            ElementCallSettingsToggle(
                title = stringResource(R.string.element_call_screen_share_options_audio),
                description = stringResource(
                    if (isDeviceAudioSupported) {
                        R.string.element_call_screen_share_options_audio_description
                    } else {
                        R.string.element_call_screen_share_options_audio_unsupported
                    }
                ),
                checked = options.sharesDeviceAudio && isDeviceAudioSupported,
                enabled = isDeviceAudioSupported,
                onCheckedChange = { change(options.copy(sharesDeviceAudio = it)) },
                testTag = ElementCallTestTags.SCREEN_SHARE_AUDIO,
            )
        }
        val edges = MatrixRtcScreenShareOptions.MAX_EDGES.toImmutableList()
        val edgeLabels = edges.associateWith { stringResource(R.string.element_call_screen_share_options_resolution_value, it.shortEdgeOf16By9()) }
        ElementCallSettingsChoices(
            title = stringResource(R.string.element_call_screen_share_options_resolution),
            options = edges,
            selected = options.maxEdge,
            label = edgeLabels::getValue,
            description = stringResource(R.string.element_call_screen_share_options_resolution_description),
            testTag = ElementCallTestTags::screenShareMaxEdge,
            onSelect = { change(options.copy(maxEdge = it)) },
        )
        val rates = MatrixRtcScreenShareOptions.FRAME_RATES.toImmutableList()
        val rateLabels = rates.associateWith { stringResource(R.string.element_call_screen_share_options_frame_rate_value, it) }
        ElementCallSettingsChoices(
            title = stringResource(R.string.element_call_screen_share_options_frame_rate),
            options = rates,
            selected = options.framesPerSecond,
            label = rateLabels::getValue,
            description = stringResource(R.string.element_call_screen_share_options_frame_rate_description),
            testTag = ElementCallTestTags::screenShareFrameRate,
            onSelect = { change(options.copy(framesPerSecond = it)) },
        )
        Button(
            onClick = { state.eventSink(ElementCallScreenEvent.StartScreenShare) },
            enabled = state.isScreenShareStartEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElementCallTheme.colors.textPrimary,
                contentColor = ElementCallTheme.colors.bgSubtlePrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ElementCallTestTags.SCREEN_SHARE_START),
        ) {
            Text(text = stringResource(R.string.element_call_screen_share_options_start), style = ElementCallTheme.typography.bodyLgMedium)
        }
    }
}

/** The name a long edge goes by: 2560 is 1440p, 1920 is 1080p, 1280 is 720p. */
private fun Int.shortEdgeOf16By9(): Int = this * 9 / 16

// The sheet itself needs a real window, so its content is previewed instead, as the Preferences sheet's is.
@PreviewsDayNight
@Composable
internal fun ElementCallScreenShareContentPreview(@PreviewParameter(ScreenShareElementCallScreenStatePreviewParam::class) state: ElementCallScreenState) =
    ElementCallPreview {
        ElementCallScreenShareContent(
            state = state,
            modifier = Modifier.background(ElementCallTheme.colors.bgSubtlePrimary),
            isDeviceAudioSupported = true,
        )
    }

open class ScreenShareElementCallScreenStatePreviewParam : PreviewParameterProvider<ElementCallScreenState> {
    override val values: Sequence<ElementCallScreenState>
        get() = sequenceOf(
            anElementCallScreenState(isScreenShareAvailable = true),
            anElementCallScreenState(
                isScreenShareAvailable = true,
                screenShare = MatrixRtcScreenShareOptions(maxEdge = 1280, framesPerSecond = 15, sharesDeviceAudio = false),
            ),
        )
}
