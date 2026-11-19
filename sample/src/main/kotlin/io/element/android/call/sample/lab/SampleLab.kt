/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.sample.lab

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.element.android.call.api.rtc.MatrixRtcMembershipFormat
import io.element.android.call.sample.SampleCallController
import io.element.android.call.sample.matrix.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The lab on the home screen: the account kept on this phone, back into its call or logged out, and a code opened from
 * the camera. The session in hand is reused for the same account and room; otherwise [SampleLabSession.login] decides
 * between the kept session and a new login.
 */
class SampleLab(private val context: Context, private val scope: CoroutineScope, private val controller: SampleCallController) {
    private val accountFlow = MutableStateFlow(SampleLabSession.Saved.load(context)?.userId)

    /** The kept account's Matrix ID, null when logged out. */
    val account: StateFlow<String?> = accountFlow

    private val formatFlow = MutableStateFlow(SampleLabSession.Saved.load(context)?.format)

    /** The kept session's call format, which the lab page's code or Developer options set, null when logged out. */
    val format: StateFlow<MatrixRtcMembershipFormat?> = formatFlow

    // Held, never read: SharedPreferences keeps its listeners weakly.
    @Suppress("UnusedPrivateProperty")
    private val savedListener = SampleLabSession.Saved.observe(context) { formatFlow.value = SampleLabSession.Saved.load(context)?.format }

    private val progressFlow = MutableStateFlow<String?>(null)

    /** What a login or rejoin is doing while it runs, which the home screen shows over everything; null when idle. */
    val progress: StateFlow<String?> = progressFlow

    private val errorFlow = MutableStateFlow<String?>(null)

    /** Why the last lab action failed, until dismissed. */
    val error: StateFlow<String?> = errorFlow

    private val avatarsFlow = MutableStateFlow<SampleLabAvatars?>(null)

    /** The session's avatars, which the call's style draws with; null with no session. */
    val avatars: StateFlow<SampleLabAvatars?> = avatarsFlow

    private var session: SampleLabSession? = null
    private var job: Job? = null

    fun open(join: SampleLabJoin) = start("Logging in as ${join.user} on ${join.homeserver}…") { current ->
        val sameAccount = SampleLabSession.Saved.load(context)?.isAccount(join.homeserver, join.user) == true
        if (current != null && sameAccount && current.serves(join)) {
            SampleLabSession.Saved.storeFormat(context, join.format)
            current
        } else {
            if (current != null) {
                session = null
                if (sameAccount) current.stop() else current.logOut(context)
            }
            SampleLabSession.login(context, join, scope)
        }
    }

    /**
     * The lab page's code, scanned inside the app: the camera app hands a scanned `elementcall-lab://` link to the
     * browser, which does not open custom schemes. Play services draws the scanner.
     */
    fun scan(activity: Activity) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                val join = barcode.rawValue?.let(Uri::parse)?.let(SampleLabJoin::parse)
                if (join != null) open(join) else errorFlow.value = "That code is not a lab code."
            }
            .addOnFailureListener {
                Timber.w(it, "Lab: the scanner failed")
                errorFlow.value = "The scanner failed: ${it.message}"
            }
    }

    fun rejoin() = start("Rejoining as ${accountFlow.value ?: "the kept account"}…") { current -> current ?: SampleLabSession.restoreSaved(context, scope) }

    fun logOut() {
        controller.endLiveCall()
        job?.cancel()
        val current = session
        session = null
        avatarsFlow.value = null
        accountFlow.value = null
        formatFlow.value = null
        progressFlow.value = null
        errorFlow.value = null
        job = scope.launch {
            if (current != null) current.logOut(context) else SampleLabSession.logOutSaved(context)
        }
    }

    /**
     * Gives up on the login or rejoin in progress. A login against a laptop that has moved can otherwise hang on its
     * timeouts, with nothing to do but wait.
     */
    fun cancel() {
        job?.cancel()
        job = null
        progressFlow.value = null
    }

    fun dismissError() {
        errorFlow.value = null
    }

    private fun start(progress: String, makeSession: suspend (SampleLabSession?) -> SampleLabSession) {
        controller.endLiveCall()
        job?.cancel()
        errorFlow.value = null
        progressFlow.value = progress
        job = scope.launch {
            runCatchingExceptions { makeSession(session) }
                .onSuccess {
                    session = it
                    avatarsFlow.value = it.avatars
                    accountFlow.value = SampleLabSession.Saved.load(context)?.userId
                    formatFlow.value = SampleLabSession.Saved.load(context)?.format
                    progressFlow.value = null
                    controller.startLab(SampleLabCall(it))
                }
                .onFailure {
                    Timber.w(it, "Lab: could not join")
                    accountFlow.value = SampleLabSession.Saved.load(context)?.userId
                    formatFlow.value = SampleLabSession.Saved.load(context)?.format
                    progressFlow.value = null
                    errorFlow.value = (it.message ?: it.toString()).replaceFirstChar(Char::uppercaseChar)
                }
        }
    }
}
