/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import io.element.android.call.impl.util.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.matrix.rtc.FfiAudioFrame
import org.matrix.rtc.FfiLocalTrack
import timber.log.Timber
import android.media.AudioFormat as AndroidAudioFormat

/**
 * Captures what the device plays and feeds it to a published screen-share audio track, one 10 ms stereo frame at a time.
 *
 * The platform's playback capture, on the projection the screen share already holds: a second grant cannot be asked
 * for, and the token is spent. It hears media, games and untagged playback from apps that allow capture; an app
 * that opts out, and everything played as a call, comes through as silence. Our own playback is left out, so a call
 * sound never reaches the share.
 *
 * Unprocessed on purpose: no echo canceller, noise suppressor or gain control sits on this path, since it carries
 * music rather than a voice.
 */
internal class ScreenAudioCapture(private val scope: CoroutineScope) {
    private var recorder: Recorder? = null

    /**
     * @return whether capture started. False means nothing is held and nothing needs stopping.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    // A released recorder and a failed read are separate exits on purpose, so the log can say which.
    @Suppress("LoopWithTooManyJumpStatements")
    fun start(track: FfiLocalTrack, projection: MediaProjection): Boolean {
        if (recorder != null) return true
        val record = runCatchingExceptions { createRecord(projection) }
            .onFailure { Timber.w(it, "MatrixRTC: cannot capture the device's audio, sharing the screen without it") }
            .getOrNull()
            ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Timber.w("MatrixRTC: the playback capture AudioRecord failed to initialise, sharing the screen without audio")
            record.release()
            return false
        }
        val newRecorder = Recorder(record)
        if (!newRecorder.startRecording()) {
            newRecorder.release()
            return false
        }
        val dispatcher = audioDispatcher("MatrixRtcScreenAudioIn")
        val job = scope.launch(dispatcher) {
            val frame = ByteArray(ScreenAudioFormat.BYTES_PER_FRAME)
            var frames = 0L
            while (isActive) {
                val read = newRecorder.read(frame)
                if (read == Recorder.RELEASED) break
                if (read < 0) {
                    Timber.w("MatrixRTC: playback capture read failed with $read")
                    break
                }
                // Blocking reads fill the frame; a short one only happens as the record stops.
                if (read < frame.size) continue
                val sent = runCatchingExceptions {
                    track.captureAudio(
                        FfiAudioFrame(
                            // The frame is copied on its way through the FFI, so the buffer is reused.
                            data = frame,
                            sampleRate = ScreenAudioFormat.SAMPLE_RATE.toUInt(),
                            numChannels = ScreenAudioFormat.CHANNEL_COUNT.toUInt(),
                            samplesPerChannel = ScreenAudioFormat.SAMPLES_PER_FRAME.toUInt(),
                        )
                    )
                }
                if (sent.isFailure) {
                    Timber.w(sent.exceptionOrNull(), "MatrixRTC: the screen share audio track no longer takes frames, stopping capture")
                    break
                }
                if (++frames == 1L || frames % FRAMES_PER_LOG == 0L) Timber.i("MatrixRTC: captured $frames device audio frame(s)")
            }
        }
        job.invokeOnCompletion { dispatcher.close() }
        newRecorder.attach(job)
        recorder = newRecorder
        Timber.i("MatrixRTC: capturing the device's audio at ${ScreenAudioFormat.SAMPLE_RATE} Hz, ${ScreenAudioFormat.CHANNEL_COUNT} channels")
        return true
    }

    fun stop() {
        recorder?.release()
        recorder = null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun createRecord(projection: MediaProjection): AudioRecord {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()
        val format = AndroidAudioFormat.Builder()
            .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(ScreenAudioFormat.SAMPLE_RATE)
            .setChannelMask(AndroidAudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBufferSize = AudioRecord.getMinBufferSize(
            ScreenAudioFormat.SAMPLE_RATE,
            AndroidAudioFormat.CHANNEL_IN_STEREO,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBufferSize, ScreenAudioFormat.BYTES_PER_FRAME * AudioFormat.BUFFER_FRAMES))
            .setAudioPlaybackCaptureConfig(capture)
            .build()
    }

    /** One record, with reading from it and releasing it made mutually exclusive, as [AudioCapture] does for the microphone. */
    private class Recorder(private val record: AudioRecord) {
        private val lock = Any()
        private var job: Job? = null
        private var isReleased = false

        fun attach(job: Job) = synchronized(lock) {
            if (isReleased) job.cancel() else this.job = job
        }

        fun startRecording(): Boolean = synchronized(lock) {
            if (isReleased) return false
            runCatchingExceptions { record.startRecording() }
                .onFailure { Timber.w(it, "MatrixRTC: playback capture did not start") }
                .isSuccess && record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }

        fun read(frame: ByteArray): Int = synchronized(lock) {
            if (isReleased) return RELEASED
            runCatchingExceptions { record.read(frame, 0, frame.size) }
                .onFailure { Timber.w(it, "MatrixRTC: playback capture read failed, stopping capture") }
                .getOrDefault(RELEASED)
        }

        fun release() {
            synchronized(lock) {
                if (isReleased) return
                isReleased = true
                job?.cancel()
                runCatchingExceptions { if (record.state == AudioRecord.STATE_INITIALIZED) record.stop() }
                record.release()
            }
        }

        companion object {
            const val RELEASED = Int.MIN_VALUE
        }
    }

    private companion object {
        /** Ten seconds of frames. */
        const val FRAMES_PER_LOG = 1_000L
    }
}

/** What the device's audio is captured and published as: 48 kHz stereo, in the 10 ms frames the FFI takes. */
internal object ScreenAudioFormat {
    const val SAMPLE_RATE = 48_000
    const val CHANNEL_COUNT = 2
    const val SAMPLES_PER_FRAME = SAMPLE_RATE / 1000 * AudioFormat.FRAME_DURATION_MS
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * CHANNEL_COUNT * Short.SIZE_BYTES
}
