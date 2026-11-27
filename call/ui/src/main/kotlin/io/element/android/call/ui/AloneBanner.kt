/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.element.android.call.api.ElementCallConnection
import io.element.android.call.ui.preview.ElementCallPreview
import io.element.android.call.ui.theme.ElementCallTheme
import kotlinx.coroutines.delay

/**
 * InterCom: alone in a DM call the other person left, how long until it is left for the user, and the two ways out
 * of waiting: stay, which starts the time over, or leave now.
 */
@Composable
internal fun AloneBanner(deadlineElapsedMs: Long, onStay: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    val remaining = rememberAloneCountdown(deadlineElapsedMs)
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ElementCallTheme.colors.bgSubtleSecondary)
            .border(1.dp, ElementCallTheme.colors.borderControl, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(ElementCallTestTags.ALONE_BANNER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.element_call_alone_countdown, remaining),
            style = ElementCallTheme.typography.bodyMdMedium,
            color = ElementCallTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            // Read once, not every second.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AloneBannerButton(R.string.element_call_alone_stay, isPrimary = true, onClick = onStay, testTag = ElementCallTestTags.ALONE_STAY)
            AloneBannerButton(R.string.element_call_alone_leave, isPrimary = false, onClick = onLeave, testTag = ElementCallTestTags.ALONE_LEAVE)
        }
    }
}

@Composable
private fun AloneBannerButton(@StringRes text: Int, isPrimary: Boolean, onClick: () -> Unit, testTag: String) {
    Text(
        text = stringResource(text),
        style = ElementCallTheme.typography.bodyMdMedium,
        color = if (isPrimary) ElementCallTheme.colors.iconOnSolidPrimary else ElementCallTheme.colors.textPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isPrimary) ElementCallTheme.colors.bgActionPrimaryRest else ElementCallTheme.colors.bgCanvas)
            .then(if (isPrimary) Modifier else Modifier.border(1.dp, ElementCallTheme.colors.borderControl, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(testTag),
    )
}

/** What is left until [deadlineElapsedMs] as `m:ss`, counted down each second. In previews, five minutes. */
@Composable
private fun rememberAloneCountdown(deadlineElapsedMs: Long): String {
    if (LocalInspectionMode.current) return formatAloneCountdown(PREVIEW_ALONE_MS)
    var remainingMs by remember(deadlineElapsedMs) { mutableLongStateOf(deadlineElapsedMs - SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadlineElapsedMs) {
        while (true) {
            remainingMs = deadlineElapsedMs - SystemClock.elapsedRealtime()
            delay(COUNTDOWN_TICK_MS)
        }
    }
    return formatAloneCountdown(remainingMs)
}

/** `m:ss` of [remainingMs], a started second counting as a whole one, and never below `0:00`. */
internal fun formatAloneCountdown(remainingMs: Long): String {
    val seconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val PREVIEW_ALONE_MS = 5 * 60 * 1000L
private const val COUNTDOWN_TICK_MS = 250L

/** A DM call the other person has left: our own tile, the countdown, and a camera that was refused. */
@Preview
@Composable
internal fun ElementCallScreenAlonePreview() = ElementCallPreview {
    ElementCallScreen(
        state = anElementCallScreenState(
            connection = ElementCallConnection.Connected,
            memberCount = 1,
            participants = listOf(aLocalParticipant()),
            isDm = true,
            cameraState = ElementCallCameraState.BLOCKED,
            aloneDeadlineElapsedMs = 0L,
        ),
    )
}

@Preview
@Composable
internal fun AloneBannerPreview() = ElementCallPreview {
    AloneBanner(deadlineElapsedMs = 0, onStay = {}, onLeave = {}, modifier = Modifier.padding(vertical = 16.dp))
}
