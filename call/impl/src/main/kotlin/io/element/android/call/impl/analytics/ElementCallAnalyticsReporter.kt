/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.analytics

import io.element.android.call.api.ElementCallEndReason
import io.element.android.call.api.analytics.ElementCallAnalytics
import io.element.android.call.api.analytics.ElementCallAnalyticsCallKind
import io.element.android.call.api.analytics.ElementCallAnalyticsEvent
import io.element.android.call.api.analytics.ElementCallAnalyticsFailureCause
import io.element.android.call.api.analytics.ElementCallAnalyticsFailurePhase
import io.element.android.call.api.analytics.ElementCallAnalyticsOutcome
import io.element.android.call.api.rtc.MatrixRtcCallEnd
import io.element.android.call.api.rtc.MatrixRtcCallEvent
import io.element.android.call.api.rtc.MatrixRtcCallStatus
import io.element.android.call.api.rtc.MatrixRtcMediaPhase
import io.element.android.call.impl.toEndReason
import io.element.android.call.impl.util.runCatchingExceptions
import timber.log.Timber
import java.util.UUID

/**
 * The analytics of one join attempt (feature-hq 044 C2): Requested → Joining → Connected → Over, never backwards.
 *
 * Every transition is a no-op outside the phases it leaves, so however many paths reach the same end there is one outcome
 * and at most one end. Events go to [analytics] in line, from the controller's own transitions; nothing here waits or
 * launches (R20). Synchronized, because the controller's collectors and its locked teardown both drive it.
 *
 * Until the join decides DM or group, an outcome is held, not sent: `CallJoinAttempted` has to carry the kind (R25), and
 * the call must not wait for it. A held outcome goes out with the early read's answer, or at [attemptOver] as a group call,
 * the join's own fallback (C10).
 */
internal class ElementCallAnalyticsReporter(
    private val analytics: ElementCallAnalytics,
    private val elapsedRealtimeMs: () -> Long,
    /** Random, from nothing about the user, the room or the device (R19). */
    val id: String = UUID.randomUUID().toString(),
) {
    private sealed interface Held {
        data object PermissionRefused : Held

        data class Failed(val report: FailureReport) : Held

        data class Abandoned(val endReason: ElementCallEndReason?) : Held
    }

    private sealed interface Phase {
        /** Before the join's DM decision. [readKind] is the early read's answer; [held] the first outcome reached meanwhile. */
        data class Requested(val readKind: ElementCallAnalyticsCallKind?, val held: Held?) : Phase

        data class Joining(val kind: ElementCallAnalyticsCallKind, val isOwnMediaConnected: Boolean, val isMicrophonePublished: Boolean) : Phase

        data class Connected(val kind: ElementCallAnalyticsCallKind, val atMs: Long, val participantsMax: Int, val hasFailed: Boolean) : Phase

        data object Over : Phase
    }

    private var phase: Phase = Phase.Requested(readKind = null, held = null)

    /** The join decided DM or group, before anything is joined (R6, R25). Sends any outcome held until then. */
    @Synchronized
    fun attempted(callKind: ElementCallAnalyticsCallKind) {
        val requested = phase as? Phase.Requested ?: return skipped("attempted")
        sendAttempted(callKind, requested.held)
    }

    /** The early read answered. Used only for an outcome reached before the join decided (R25). */
    @Synchronized
    fun roomKindRead(callKind: ElementCallAnalyticsCallKind) {
        val requested = phase as? Phase.Requested ?: return
        if (requested.held != null) sendAttempted(callKind, requested.held) else phase = requested.copy(readKind = callKind)
    }

    /** The attempt is over: an outcome still held for want of a kind goes out, as a group call if nothing answered. */
    @Synchronized
    fun attemptOver() {
        val requested = phase as? Phase.Requested
        if (requested?.held != null) {
            sendAttempted(requested.readKind ?: ElementCallAnalyticsCallKind.GROUP, requested.held)
        }
        phase = Phase.Over
    }

    /** Our own media is up, as the call's status says (R8, C5). [participants] is the membership count now, ourselves included. */
    @Synchronized
    fun ownMediaConnected(participants: Int) {
        val joining = phase as? Phase.Joining ?: return
        if (joining.isOwnMediaConnected) return
        connectIfComplete(joining.copy(isOwnMediaConnected = true), participants)
    }

    /** Our microphone is published, muted or not (R8, C5). */
    @Synchronized
    fun microphonePublished(participants: Int) {
        val joining = phase as? Phase.Joining ?: return skipped("microphonePublished")
        connectIfComplete(joining.copy(isMicrophonePublished = true), participants)
    }

    /** A membership count after someone joined or left; the largest is kept from connected to the end (R26). */
    @Synchronized
    fun participants(count: Int) {
        val connected = phase as? Phase.Connected ?: return
        if (count > connected.participantsMax) phase = connected.copy(participantsMax = count)
    }

    /** The microphone permission was refused (R10). */
    @Synchronized
    fun permissionRefused() = reachOutcome(Held.PermissionRefused, "permissionRefused")

    /** A failure; joining or in the call from our own history, never from the failure's type (R15). */
    @Synchronized
    fun failed(report: FailureReport) {
        when (val current = phase) {
            is Phase.Connected -> {
                if (current.hasFailed) return skipped("failed")
                phase = current.copy(hasFailed = true)
                send(
                    ElementCallAnalyticsEvent.CallFailure(id, current.kind, ElementCallAnalyticsFailurePhase.IN_CALL, report.cause, null, null, null),
                )
            }
            else -> reachOutcome(Held.Failed(report), "failed")
        }
    }

    /** The user or the host ended the call (R9, R24). */
    @Synchronized
    fun hungUp() {
        when (val current = phase) {
            is Phase.Connected -> end(current, ElementCallEndReason.LEFT)
            else -> reachOutcome(Held.Abandoned(endReason = null), "hungUp")
        }
    }

    /** The core ended the call. A plain leave is ours (C8), so before connected it carries no end reason. */
    @Synchronized
    fun ended(reason: ElementCallEndReason) {
        when (val current = phase) {
            is Phase.Connected -> end(current, reason)
            else -> reachOutcome(Held.Abandoned(endReason = reason.takeUnless { it == ElementCallEndReason.LEFT }), "ended")
        }
    }

    /**
     * The call's status, as the core reports it. Our media up, while it rings or not, is half of connected, never the
     * pickup (R8, C5); an end is the core's, a lost connection reporting its failure first (C4).
     */
    @Synchronized
    fun status(status: MatrixRtcCallStatus, participants: Int) {
        when (status) {
            is MatrixRtcCallStatus.Connected -> ownMediaConnected(participants)
            is MatrixRtcCallStatus.Calling -> if (status.media is MatrixRtcMediaPhase.Connected) ownMediaConnected(participants)
            is MatrixRtcCallStatus.Ended -> coreEnded(status.reason)
            MatrixRtcCallStatus.Connecting -> Unit
        }
    }

    /** Someone joined or left: the membership count after it (R26). */
    @Synchronized
    fun callEvent(event: MatrixRtcCallEvent) {
        when (event) {
            is MatrixRtcCallEvent.ParticipantJoined -> participants(event.participantCount)
            is MatrixRtcCallEvent.ParticipantLeft -> participants(event.participantCount)
            else -> Unit
        }
    }

    /** The call is torn down: by the core's end when it has one the status observer has not reached, else by a hang-up. */
    @Synchronized
    fun callOver(coreEnd: MatrixRtcCallEnd?) {
        if (coreEnd == null) hungUp() else coreEnded(coreEnd)
        attemptOver()
    }

    private fun coreEnded(end: MatrixRtcCallEnd) {
        if (end is MatrixRtcCallEnd.ConnectionClosed) failed(FailureReport.connectionClosed(end.reason))
        ended(end.toEndReason())
    }

    private fun reachOutcome(outcome: Held, transition: String) {
        when (val current = phase) {
            is Phase.Requested -> when {
                current.held != null -> skipped(transition)
                current.readKind != null -> sendAttempted(current.readKind, outcome)
                else -> phase = current.copy(held = outcome)
            }
            is Phase.Joining -> sendOutcome(current.kind, outcome)
            is Phase.Connected, Phase.Over -> skipped(transition)
        }
    }

    private fun sendAttempted(kind: ElementCallAnalyticsCallKind, held: Held?) {
        send(ElementCallAnalyticsEvent.CallJoinAttempted(id, kind))
        if (held != null) {
            sendOutcome(kind, held)
        } else {
            phase = Phase.Joining(kind, isOwnMediaConnected = false, isMicrophonePublished = false)
        }
    }

    /** The outcome, then its failure (C4). */
    private fun sendOutcome(kind: ElementCallAnalyticsCallKind, outcome: Held) {
        phase = Phase.Over
        val event = when (outcome) {
            Held.PermissionRefused -> ElementCallAnalyticsEvent.CallJoinOutcome(id, kind, ElementCallAnalyticsOutcome.PERMISSION_REFUSED, endReason = null)
            is Held.Failed -> ElementCallAnalyticsEvent.CallJoinOutcome(id, kind, ElementCallAnalyticsOutcome.FAILED, endReason = null)
            is Held.Abandoned -> ElementCallAnalyticsEvent.CallJoinOutcome(id, kind, ElementCallAnalyticsOutcome.ABANDONED, outcome.endReason)
        }
        send(event)
        if (outcome is Held.Failed) {
            val report = outcome.report
            send(
                ElementCallAnalyticsEvent.CallFailure(
                    attemptId = id,
                    callKind = kind,
                    phase = ElementCallAnalyticsFailurePhase.JOINING,
                    cause = report.cause,
                    stage = report.stage,
                    step = report.step,
                    errcode = report.errcode.takeIf { report.cause == ElementCallAnalyticsFailureCause.JOIN_REFUSED }.asReportableErrcode(),
                )
            )
        }
    }

    private fun connectIfComplete(joining: Phase.Joining, participants: Int) {
        if (!joining.isOwnMediaConnected || !joining.isMicrophonePublished) {
            phase = joining
            return
        }
        phase = Phase.Connected(joining.kind, atMs = elapsedRealtimeMs(), participantsMax = participants.coerceAtLeast(1), hasFailed = false)
        send(ElementCallAnalyticsEvent.CallJoinOutcome(id, joining.kind, ElementCallAnalyticsOutcome.CONNECTED, endReason = null))
    }

    private fun end(connected: Phase.Connected, reason: ElementCallEndReason) {
        phase = Phase.Over
        val durationMs = (elapsedRealtimeMs() - connected.atMs).coerceAtLeast(0)
        send(ElementCallAnalyticsEvent.CallParticipationEnded(id, connected.kind, reason, durationMs, connected.participantsMax))
    }

    private fun send(event: ElementCallAnalyticsEvent) {
        Timber.d("ElementCall: analytics ${event.name} ${event.properties}")
        runCatchingExceptions { analytics.track(event) }
            .onFailure { Timber.w(it, "ElementCall: the host's analytics receiver threw on ${event.name}") }
    }

    private fun skipped(transition: String) {
        Timber.d("ElementCall: analytics $transition skipped in ${phase::class.simpleName}")
    }
}

/** `dm` for a direct-message room, `group` otherwise (R25). */
internal fun callKindOf(isDm: Boolean) = if (isDm) ElementCallAnalyticsCallKind.DM else ElementCallAnalyticsCallKind.GROUP
