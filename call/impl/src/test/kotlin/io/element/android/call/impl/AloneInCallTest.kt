/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.ElementCallData
import io.element.android.call.api.ElementCallEndReason
import io.element.android.call.api.ElementCallOptions
import io.element.android.call.api.ElementCallRoomContext
import io.element.android.call.api.analytics.NoOpElementCallAnalytics
import io.element.android.call.api.rtc.MatrixRtcCallEnd
import io.element.android.call.api.rtc.MatrixRtcCallKind
import io.element.android.call.api.rtc.MatrixRtcCallStatus
import io.element.android.call.api.rtc.MatrixRtcLocalState
import io.element.android.call.api.rtc.MatrixRtcMediaPhase
import io.element.android.call.api.rtc.MatrixRtcRingPhase
import io.element.android.call.api.rtc.id.UserId
import io.element.android.call.test.A_ROOM_ID
import io.element.android.call.test.A_USER_ID
import io.element.android.call.test.FakeElementCallLifecycleListener
import io.element.android.call.test.FakeElementCallPreferencesStore
import io.element.android.call.test.FakeElementCallRoomContextProvider
import io.element.android.call.test.FakeMatrixRtcClient
import io.element.android.call.test.aRoster
import io.element.android.call.test.aTile
import io.element.android.call.test.audio.FakeAudioFocus
import io.element.android.call.test.audio.FakeCallAudioDeviceController
import io.element.android.call.tests.testutils.WarmUpRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** InterCom: alone in a DM call the other person left, see `ElementCallOptions.aloneTimeout`. */
@OptIn(ExperimentalCoroutinesApi::class)
class AloneInCallTest {
    @get:Rule val warmUpRule = WarmUpRule()

    private val rtcClient = FakeMatrixRtcClient()
    private val lifecycleListener = FakeElementCallLifecycleListener()
    private val platform = FakeElementCallPlatform(elapsedRealtimeMs = 10_000L)

    @Test
    fun `a DM call asks the core to stay when the other person leaves, only with a time to wait`() = runTest {
        connectedCall(aloneTimeout = 5.minutes)
        assertThat(rtcClient.lastRoom?.lastKind).isEqualTo(MatrixRtcCallKind.Direct(ring = null, stayWhenAlone = true))
    }

    @Test
    fun `without a time to wait the core ends the call as before`() = runTest {
        val controller = connectedCall(aloneTimeout = null)
        assertThat(rtcClient.lastRoom?.lastKind).isEqualTo(MatrixRtcCallKind.Direct(ring = null, stayWhenAlone = false))
        rtcClient.lastCall!!.memberCount.value = 1
        advanceTimeBy(1.minutes)
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
    }

    @Test
    fun `a room's call is never left for being alone in it`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes, roomIsDm = false)
        assertThat(rtcClient.lastRoom?.lastKind).isInstanceOf(MatrixRtcCallKind.Group::class.java)
        rtcClient.lastCall!!.memberCount.value = 1
        advanceTimeBy(10.minutes)
        assertThat(controller.state.value).isNotNull()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
        assertThat(lifecycleListener.endReasons).isEmpty()
    }

    @Test
    fun `alone, the call says when it will be left, and is left then`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()

        rtcClient.lastCall!!.memberCount.value = 1
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isEqualTo(10_000L + 300_000L)

        advanceTimeBy(5.minutes - 1.seconds)
        assertThat(controller.state.value).isNotNull()
        assertThat(rtcClient.lastCall?.leaveCount).isEqualTo(0)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertThat(controller.state.value).isNull()
        assertThat(rtcClient.lastCall?.leaveCount).isEqualTo(1)
        assertThat(lifecycleListener.endReasons).containsExactly(ElementCallEndReason.ALONE)
    }

    @Test
    fun `somebody coming back ends the wait, and leaving again starts a whole one`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        val call = rtcClient.lastCall!!
        call.memberCount.value = 1
        advanceTimeBy(4.minutes)

        call.memberCount.value = 2
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
        advanceTimeBy(10.minutes)
        assertThat(controller.state.value).isNotNull()

        platform.elapsedRealtimeMs = 900_000L
        call.memberCount.value = 1
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isEqualTo(900_000L + 300_000L)
        advanceTimeBy(4.minutes)
        assertThat(controller.state.value).isNotNull()
    }

    @Test
    fun `staying starts the wait over`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        rtcClient.lastCall!!.memberCount.value = 1
        advanceTimeBy(4.minutes)

        platform.elapsedRealtimeMs = 250_000L
        controller.stayInCall()
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isEqualTo(250_000L + 300_000L)

        advanceTimeBy(4.minutes)
        assertThat(controller.state.value).isNotNull()
        advanceTimeBy(1.minutes + 1.seconds)
        runCurrent()
        assertThat(lifecycleListener.endReasons).containsExactly(ElementCallEndReason.ALONE)
    }

    @Test
    fun `staying does nothing while somebody is here`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        controller.stayInCall()
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
    }

    @Test
    fun `leaving now is a hang-up of our own`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        rtcClient.lastCall!!.memberCount.value = 1
        runCurrent()

        controller.hangUp()
        runCurrent()
        advanceTimeBy(10.minutes)
        assertThat(lifecycleListener.endReasons).containsExactly(ElementCallEndReason.LEFT)
    }

    @Test
    fun `a call that still rings is not alone`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes, isStartingCall = true)
        val call = rtcClient.lastCall!!
        call.status.value = MatrixRtcCallStatus.Calling(
            callee = UserId("@callee:example.org"),
            ring = MatrixRtcRingPhase.Ringing(sentAtMs = 0L, lifetimeMs = 90_000L),
            media = MatrixRtcMediaPhase.Connected(degraded = false),
        )
        call.memberCount.value = 1
        advanceTimeBy(10.minutes)
        assertThat(controller.state.value).isNotNull()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
    }

    @Test
    fun `with nobody but another device of our own in the call we are alone`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        val session = rtcClient.lastCall!!.lastMediaSession!!
        session.localState.value = MatrixRtcLocalState(aTile("own-phone", userId = A_USER_ID), isScreenSharing = false)
        session.tiles.value = aRoster(aTile("own-laptop", userId = A_USER_ID))
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNotNull()

        session.tiles.value = aRoster(aTile("own-laptop", userId = A_USER_ID), aTile("them", userId = UserId("@them:example.org")))
        runCurrent()
        assertThat(controller.state.value?.aloneDeadlineElapsedMs).isNull()
    }

    @Test
    fun `an end the core reports while we wait is the core's`() = runTest {
        val controller = connectedCall(aloneTimeout = 5.minutes)
        val call = rtcClient.lastCall!!
        call.memberCount.value = 1
        advanceTimeBy(1.minutes)

        call.status.value = MatrixRtcCallStatus.Ended(MatrixRtcCallEnd.SlotClosed)
        runCurrent()
        advanceTimeBy(10.minutes)
        assertThat(controller.state.value).isNull()
        assertThat(lifecycleListener.endReasons).containsExactly(ElementCallEndReason.SLOT_CLOSED)
    }

    /** A call that has joined and connected, with one other member in it. */
    private fun TestScope.connectedCall(
        aloneTimeout: Duration?,
        roomIsDm: Boolean = true,
        isStartingCall: Boolean = false,
    ): DefaultElementCallController {
        val controller = DefaultElementCallController(
            scope = backgroundScope,
            platform = platform,
            rtcClient = rtcClient,
            audioDeviceController = FakeCallAudioDeviceController(),
            audioFocus = FakeAudioFocus(requestAudioFocusResult = {}, releaseAudioFocusResult = {}),
            lifecycleListener = lifecycleListener,
            roomContextProvider = FakeElementCallRoomContextProvider(
                roomContext = ElementCallRoomContext(displayName = "A room", isDm = roomIsDm, members = emptyMap()),
            ),
            options = ElementCallOptions(aloneTimeout = aloneTimeout),
            preferencesStore = FakeElementCallPreferencesStore(),
            analytics = NoOpElementCallAnalytics,
        )
        controller.startCall(ElementCallData(roomId = A_ROOM_ID, isAudioCall = true, isStartingCall = isStartingCall))
        runCurrent()
        controller.setMicrophonePermissionGranted(true)
        runCurrent()
        val call = rtcClient.lastCall!!
        call.memberCount.value = 2
        call.status.value = MatrixRtcCallStatus.Connected(sinceMs = 0L, degraded = false)
        runCurrent()
        return controller
    }
}
