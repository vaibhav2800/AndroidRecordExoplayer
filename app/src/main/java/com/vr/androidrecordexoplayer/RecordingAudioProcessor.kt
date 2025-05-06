package com.vr.androidrecordexoplayer

import AudioRecorder
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
        return inputAudioFormat // No changes to format
    }

    override fun isActive(): Boolean {
        return true // Always active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val presentationTimeUs = System.nanoTime() / 1000
        Log.e("TAG-VAIBHAV","Data called ${presentationTimeUs}")
        audioRecorder?.queuePcmData(inputBuffer, inputBuffer.remaining(), presentationTimeUs)

        inputBuffer.position(inputBuffer.limit()) // Mark buffer fully consumed
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        return EMPTY_BUFFER // We don't modify output for playback
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
