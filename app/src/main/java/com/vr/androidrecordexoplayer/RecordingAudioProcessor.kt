package com.vr.androidrecordexoplayer

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@UnstableApi
class RecordingAudioProcessor : AudioProcessor {
    @Volatile
    private var audioRecorder: AudioRecorder? = null
    private var inputEnded = false

    fun setRecorder(recorder: AudioRecorder?) {
        this.audioRecorder = recorder
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return audioRecorder != null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val presentationTimeUs = System.nanoTime() / 1000
        val bufferCopy = inputBuffer.duplicate()
        Log.d("RecordingAudioProcessor", "queueInput called, size: ${bufferCopy.remaining()}")
        audioRecorder?.queuePcmData(bufferCopy, bufferCopy.remaining(), presentationTimeUs)
        inputBuffer.position(inputBuffer.limit())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        return EMPTY_BUFFER
    }

    override fun isEnded(): Boolean {
        return inputEnded
    }

    override fun flush() {
        inputEnded = false
    }

    override fun reset() {
        flush()
        audioRecorder = null
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
