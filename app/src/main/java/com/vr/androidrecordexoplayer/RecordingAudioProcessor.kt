package com.vr.androidrecordexoplayer

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Taps PCM audio out of ExoPlayer's audio chain and forwards it to [StreamRecorder], while
 * passing the same PCM through unchanged so playback stays audible.
 */
@UnstableApi
class RecordingAudioProcessor : BaseAudioProcessor() {

    @Volatile private var streamRecorder: StreamRecorder? = null
    @Volatile private var totalBytesQueued = 0L

    private var currentFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    fun setStreamRecorder(recorder: StreamRecorder?) {
        if (recorder != null) totalBytesQueued = 0L
        streamRecorder = recorder
    }

    fun getAudioFormat(): AudioProcessor.AudioFormat = currentFormat

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        currentFormat = inputAudioFormat
        Log.d(
            "RecordingAudioProcessor",
            "Configured: ${inputAudioFormat.sampleRate}Hz ch=${inputAudioFormat.channelCount}"
        )
        // Return the input format unchanged → this processor is a pass-through tap.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val recorder = streamRecorder
        if (recorder != null && currentFormat.sampleRate > 0) {
            // PCM_16BIT = 2 bytes per sample per channel.
            val bytesPerFrame = currentFormat.channelCount * 2
            val pts = totalBytesQueued * 1_000_000L /
                    (currentFormat.sampleRate.toLong() * bytesPerFrame)
            recorder.feedAudio(inputBuffer.duplicate(), remaining, pts)
            totalBytesQueued += remaining
        }

        // Copy the PCM into the output buffer so it continues to the audio sink.
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onReset() {
        streamRecorder = null
        totalBytesQueued = 0L
        currentFormat = AudioProcessor.AudioFormat.NOT_SET
    }
}