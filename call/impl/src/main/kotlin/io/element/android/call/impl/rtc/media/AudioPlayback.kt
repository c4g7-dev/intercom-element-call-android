/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc.media

import android.media.AudioAttributes
import android.media.AudioTrack
import io.element.android.call.api.rtc.MatrixRtcAudioLevel
import io.element.android.call.api.rtc.MatrixRtcStreamKind
import io.element.android.call.api.rtc.MatrixRtcStreamRef
import io.element.android.call.impl.util.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.matrix.rtc.AudioFrameStream
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import android.media.AudioFormat as AndroidAudioFormat

/**
 * Plays remote audio streams, one [AudioTrack] per stream: a member's voice, and the audio of their screen share
 * beside it.
 *
 * Letting the platform mix several tracks is simpler than mixing ourselves and is fine at the
 * handful of participants a 1:1 or small call has.
 *
 * Every track is a call's: the same usage for a voice and for a share's audio, so both follow the call's route
 * (earpiece, speaker, headset) and its volume keys, and the platform's echo canceller hears both.
 */
internal class AudioPlayback(
    private val scope: CoroutineScope,
    private val onLevel: (memberId: String, MatrixRtcAudioLevel) -> Unit = { _, _ -> },
) {
    private val players = ConcurrentHashMap<MatrixRtcStreamRef, Player>()

    // The loop leaves on either side going away - the stream ending or the track being released -
    // and each is its own exit on purpose, so the log can say which.
    @Suppress("LoopWithTooManyJumpStatements")
    fun start(ref: MatrixRtcStreamRef, stream: AudioFrameStream, gain: Float = 1f) {
        if (players.containsKey(ref)) return

        val memberId = ref.memberId
        // A share's audio is its own stream beside the voice, and the levels are per member: only the voice reports.
        val isVoice = ref.kind == MatrixRtcStreamKind.MICROPHONE
        val name = if (isVoice) memberId else "$memberId/${ref.kind}"
        val channels = if (isVoice) AudioFormat.CHANNEL_COUNT else ScreenAudioFormat.CHANNEL_COUNT
        val track = createAudioTrack(channels) ?: return
        val player = Player(track, PlaybackGain().also { it.gain = gain })
        // Under-runs are reported on the meter's cadence and only when the count moves, so a healthy
        // call stays quiet and a crackling one says so in the same place the levels already are.
        var loggedUnderruns = 0
        val meter = AudioLevelMeter(label = "in $name") { level ->
            val underruns = player.underrunCount()
            if (underruns > loggedUnderruns) {
                Timber.w(
                    "MatrixRTC: audio in $name - ${underruns - loggedUnderruns} new AudioTrack " +
                        "under-runs ($underruns total), playback is not keeping up"
                )
                loggedUnderruns = underruns
            }
            if (isVoice) onLevel(memberId, level.copy(underrunCount = underruns))
        }
        // On a thread of its own at audio priority, see audioDispatcher: on the shared IO pool this
        // loop lost to video work and the track under-ran, which is what a crackle is.
        val dispatcher = audioDispatcher("MatrixRtcAudioOut")
        val job = scope.launch(dispatcher) {
            player.play()
            while (isActive) {
                val frame = stream.next() ?: break
                // The frame is already the little-endian PCM16 AudioTrack wants. The meter reads what
                // arrived, before our own volume: a member turned down here is still speaking.
                val bytes = adaptChannels(frame.data, from = frame.numChannels.toInt(), to = channels)
                meter.onFrame(bytes)
                player.gain.apply(bytes, channels)
                // Stops on a released track rather than writing to one: the write and the release
                // are mutually exclusive, so this can only be false once the track is really gone.
                if (!player.write(bytes)) break
            }
            Timber.d("MatrixRTC: audio stream for $name ended")
        }
        // Closed on completion rather than in release(): release does not wait for the loop, and the
        // thread has to outlive the write that release is letting finish.
        job.invokeOnCompletion { dispatcher.close() }
        // Attached before the player is reachable, so nothing can release it without a job to cancel.
        player.attach(job)
        players[ref] = player
        Timber.d("MatrixRTC: playing audio for $name, $channels channel(s), gain $gain")
    }

    /** The gain [ref] is played with from its next buffer on; nothing if it is not playing. */
    fun setGain(ref: MatrixRtcStreamRef, gain: Float) {
        players[ref]?.gain?.gain = gain
    }

    fun isPlaying(ref: MatrixRtcStreamRef): Boolean = players.containsKey(ref)

    fun stop(ref: MatrixRtcStreamRef) {
        players.remove(ref)?.release()
    }

    fun stopAll() {
        players.keys.toList().forEach(::stop)
    }

    private fun createAudioTrack(channels: Int): AudioTrack? {
        val channelMask = if (channels == 2) AndroidAudioFormat.CHANNEL_OUT_STEREO else AndroidAudioFormat.CHANNEL_OUT_MONO
        val minBufferSize = AudioTrack.getMinBufferSize(
            AudioFormat.SAMPLE_RATE,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            Timber.w("MatrixRTC: audio playback unavailable, getMinBufferSize returned $minBufferSize")
            return null
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Routes to the earpiece/communication device and engages the platform's
                    // echo cancellation against our capture.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AudioFormat.SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .build()
            )
            // Floored at a few frames of slack rather than taken as-is, the way capture already
            // does it: at exactly the minimum the track has to be refilled before it drains, and a
            // GC pause - up to 2.3 ms on a loaded device - is enough to miss that.
            .setBufferSizeInBytes(maxOf(minBufferSize, AudioFormat.MIN_DEVICE_BUFFER_BYTES * channels))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * One member's track, with writing to it and releasing it made mutually exclusive.
     *
     * They raced before, and it killed the process. `Job.cancel()` does not wait, and the playback
     * loop blocks inside `AudioTrack.write(WRITE_BLOCKING)` - a blocking call rather than a
     * suspension point, so cancellation cannot interrupt it. Release therefore freed the native
     * track while a write was still in flight, and the write came back with
     * `IllegalStateException: Unable to retrieve AudioTrack pointer for write()` on an unsupervised
     * coroutine, which takes the whole app down. It needed a member to leave or a call to end at the
     * wrong moment, which is why it took a three-party call to find.
     *
     * The lock is held across the write, so release waits for one buffer - bounded by
     * `getMinBufferSize`, on the order of tens of milliseconds - rather than proceeding underneath it.
     */
    private class Player(private val track: AudioTrack, val gain: PlaybackGain) {
        private val lock = Any()
        private var job: Job? = null
        private var isReleased = false

        fun attach(job: Job) = synchronized(lock) {
            if (isReleased) job.cancel() else this.job = job
        }

        fun play() = synchronized(lock) {
            if (!isReleased) track.play()
        }

        /**
         * Buffers the platform wanted and we had not refilled, each one audible. Zero once the
         * track is gone, so a released player cannot report a number that will never move again.
         */
        fun underrunCount(): Int = synchronized(lock) {
            if (isReleased) 0 else track.underrunCount
        }

        /** Returns false once the track is gone, which tells the loop to stop. */
        fun write(bytes: ByteArray): Boolean = synchronized(lock) {
            if (isReleased) return false
            // Caught rather than thrown: playback failing for one member should end that member's
            // audio, not the call and not the process.
            runCatchingExceptions { track.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING) }
                .onFailure { Timber.w(it, "MatrixRTC: audio write failed, stopping playback") }
                .isSuccess
        }

        fun release() {
            synchronized(lock) {
                if (isReleased) return
                isReleased = true
                job?.cancel()
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                }
                track.release()
            }
        }
    }
}
