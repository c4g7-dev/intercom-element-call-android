/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.ElementCallConnection
import io.element.android.call.api.ElementCallFailure
import org.junit.Test

/** InterCom: the countdown's text, the camera button's four states, and when the connecting animation shows. */
class AloneAndCameraStateTest {
    @Test
    fun `the countdown is minutes and seconds, a started second counting whole`() {
        assertThat(formatAloneCountdown(300_000)).isEqualTo("5:00")
        assertThat(formatAloneCountdown(299_001)).isEqualTo("5:00")
        assertThat(formatAloneCountdown(299_000)).isEqualTo("4:59")
        assertThat(formatAloneCountdown(61_000)).isEqualTo("1:01")
        assertThat(formatAloneCountdown(9_000)).isEqualTo("0:09")
        assertThat(formatAloneCountdown(1)).isEqualTo("0:01")
        assertThat(formatAloneCountdown(0)).isEqualTo("0:00")
        assertThat(formatAloneCountdown(-5_000)).isEqualTo("0:00")
        assertThat(formatAloneCountdown(600_000)).isEqualTo("10:00")
    }

    @Test
    fun `a camera is on, off, blocked or not there`() {
        fun state(hasCamera: Boolean = true, refused: Boolean = false, granted: Boolean = false, enabled: Boolean = false) =
            ElementCallCameraState.of(hasCamera = hasCamera, isPermissionRefused = refused, isPermissionGranted = granted, isEnabled = enabled)
        // Never asked is off: a tap asks.
        assertThat(state()).isEqualTo(ElementCallCameraState.OFF)
        assertThat(state(granted = true)).isEqualTo(ElementCallCameraState.OFF)
        assertThat(state(granted = true, enabled = true)).isEqualTo(ElementCallCameraState.ON)
        assertThat(state(refused = true)).isEqualTo(ElementCallCameraState.BLOCKED)
        // Allowed in the settings after a refusal.
        assertThat(state(refused = true, granted = true)).isEqualTo(ElementCallCameraState.OFF)
        // No camera wins over everything else.
        assertThat(state(hasCamera = false)).isEqualTo(ElementCallCameraState.NO_DEVICE)
        assertThat(state(hasCamera = false, refused = true)).isEqualTo(ElementCallCameraState.NO_DEVICE)
        assertThat(state(hasCamera = false, granted = true, enabled = true)).isEqualTo(ElementCallCameraState.NO_DEVICE)
    }

    @Test
    fun `the connecting animation shows until the call is connected or over`() {
        assertThat(ElementCallConnection.RequestingPermission.isOnItsWay()).isTrue()
        assertThat(ElementCallConnection.Joining.isOnItsWay()).isTrue()
        assertThat(ElementCallConnection.ConnectingMedia.isOnItsWay()).isTrue()
        assertThat(ElementCallConnection.Calling(callee = null, isRinging = false).isOnItsWay()).isTrue()
        assertThat(ElementCallConnection.Connected.isOnItsWay()).isFalse()
        assertThat(ElementCallConnection.RingNotSent(callee = null).isOnItsWay()).isFalse()
        assertThat(ElementCallConnection.Failed(ElementCallFailure.JoinTimedOut).isOnItsWay()).isFalse()
        assertThat(ElementCallConnection.Ended.isOnItsWay()).isFalse()
    }
}
