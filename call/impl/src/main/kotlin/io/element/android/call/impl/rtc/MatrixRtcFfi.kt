/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc

import android.util.Log
import io.element.android.call.api.rtc.MatrixRtcLogLevel
import io.element.android.call.api.rtc.MatrixRtcLoggingConfiguration
import io.element.android.call.impl.util.runCatchingExceptions
import org.matrix.rtc.MatrixRtc
import org.matrix.rtc.RtcLogLevel
import org.matrix.rtc.RtcLogRecord
import org.matrix.rtc.RtcLogging
import org.matrix.rtc.uniffiEnsureInitialized
import timber.log.Timber

/**
 * Entry point for the `matrix-rust-rtc` native library.
 *
 * Nothing in this module may touch the FFI before [ensureInitialized] has returned.
 */
internal object MatrixRtcFfi {
    private var initialized = false
    private var loggingConfiguration: MatrixRtcLoggingConfiguration? = null

    /**
     * Record how the core should log. Called from app start, which is before the library loads, so
     * the configuration is only applied by [ensureInitialized].
     */
    @Synchronized
    fun setLoggingConfiguration(configuration: MatrixRtcLoggingConfiguration) {
        loggingConfiguration = configuration
        if (initialized) {
            // The core installs a process-wide tracing subscriber on first use and will not take a
            // second one, so a later change can only land on the next process start.
            Timber.w("MatrixRTC: core logging already initialised, new configuration ignored")
        }
    }

    /**
     * @throws Throwable if the native library cannot be loaded. Deliberately propagated: without it
     * libwebrtc has no `JavaVM*`, and the first media call would abort the process instead of
     * failing. Callers turn this into a `Result`.
     */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        // The AAR owns native loading: this is the only loader that runs the library's `JNI_OnLoad`,
        // which hands libwebrtc its `JavaVM*` and class loader. The generated uniffi bindings open
        // the library with JNA instead, and JNA is a plain `dlopen` that never triggers it - so
        // going straight to uniffi leaves media to die on a null dereference inside `RtcRuntime()`.
        // Idempotent, so it stays correct if the AAR is also initialised elsewhere.
        MatrixRtc.initialize()
        uniffiEnsureInitialized()
        initialized = true
        // Before anything else uses the FFI, so that the core's own account of the first join is in
        // the log rather than only our side of it.
        loggingConfiguration?.let(::applyLogging)
    }

    private fun applyLogging(configuration: MatrixRtcLoggingConfiguration) {
        if (configuration.writesToHostLog) {
            // InterCom: through the host's Timber trees, so the core's account of a call reaches the app's log files
            // (and logcat as the host sees fit), instead of logcat only, or nowhere in a release build.
            runCatchingExceptions {
                RtcLogging.init(
                    level = configuration.logLevel.map(),
                    filter = configuration.filter,
                    alsoToLogcat = configuration.writesToLogcat,
                    sink = ::logToHost,
                )
            }
                .onSuccess {
                    Timber.i(
                        "MatrixRTC: core logging to the host log (tag \"${MatrixRtcLoggingConfiguration.CORE_LOG_TAG}\") at " +
                            "${configuration.logLevel}, filter \"${configuration.filter}\", logcat too: ${configuration.writesToLogcat}"
                    )
                }
                .onFailure { Timber.w(it, "MatrixRTC: could not initialise core logging") }
            return
        }
        if (!configuration.writesToLogcat) {
            Timber.i("MatrixRTC: core logging off, enable \"Print logs to logcat\" in developer settings")
            return
        }
        // The native logcat sink filters in Rust and never crosses the FFI per record, unlike a
        // Kotlin sink - which matters because this stack logs from real-time media threads.
        runCatchingExceptions { RtcLogging.initLogcat(configuration.logLevel.map(), configuration.filter) }
            .onSuccess {
                Timber.i("MatrixRTC: core logging to logcat tag \"matrix-rtc\" at ${configuration.logLevel}, filter \"${configuration.filter}\"")
            }
            .onFailure { Timber.w(it, "MatrixRTC: could not initialise core logging") }
    }
}

/** One core record into Timber, on the core's logging thread. */
private fun logToHost(record: RtcLogRecord) {
    Timber.tag(MatrixRtcLoggingConfiguration.CORE_LOG_TAG).log(record.level.priority(), "${record.target}: ${record.message}")
}

private fun RtcLogLevel.priority(): Int = when (this) {
    RtcLogLevel.ERROR -> Log.ERROR
    RtcLogLevel.WARN -> Log.WARN
    RtcLogLevel.INFO -> Log.INFO
    RtcLogLevel.DEBUG -> Log.DEBUG
    RtcLogLevel.TRACE -> Log.VERBOSE
}

private fun MatrixRtcLogLevel.map(): RtcLogLevel = when (this) {
    MatrixRtcLogLevel.ERROR -> RtcLogLevel.ERROR
    MatrixRtcLogLevel.WARN -> RtcLogLevel.WARN
    MatrixRtcLogLevel.INFO -> RtcLogLevel.INFO
    MatrixRtcLogLevel.DEBUG -> RtcLogLevel.DEBUG
    MatrixRtcLogLevel.TRACE -> RtcLogLevel.TRACE
}
