package com.vr.androidrecordexoplayer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

class AudioRecorder {

    private var audioEncoder: MediaCodec? = null
    private val sampleRate = 44100
    private val channels = 2 // Stereo
    private val bitRate = 128000

    @Volatile
    private var isRecording = false
    private var encoderThread: Thread? = null

    fun start() {
        isRecording = true
        setupAudioEncoder()
        startEncoderLoop()
    }

    private fun setupAudioEncoder() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    fun queuePcmData(pcmBuffer: ByteBuffer, size: Int, presentationTimeUs: Long) {
        if (!isRecording) return
        if (size <= 0) return
        audioEncoder?.let { encoder ->
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(pcmBuffer)
                encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
            }
        }
    }

    fun stop() {
        isRecording = false
        try {
            audioEncoder?.let { encoder ->
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }
            encoderThread?.join()
        } catch (e: IllegalStateException) {
            Log.e("AudioRecorder", "Error signaling EOS: ${e.message}")
        }
    }

    private fun startEncoderLoop() {
        encoderThread = Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            var sawEOS = false
            while (!sawEOS && isRecording) {
                val encoder = audioEncoder ?: break
                try {
                    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val audioFormat = encoder.outputFormat
                        synchronized(SharedMuxerState.muxerLock) {
                            if (SharedMuxerState.audioTrackIndex == -1) {
                                SharedMuxerState.audioTrackIndex = SharedMuxerState.muxer.addTrack(audioFormat)
                                SharedMuxerState.tryStartMuxer()
                                Log.e("AudioRecorder", "Audio track added: ${SharedMuxerState.audioTrackIndex}")
                                if (SharedMuxerState.videoTrackIndex != -1 && !SharedMuxerState.muxerStarted) {
                                    SharedMuxerState.muxer.start()
                                    SharedMuxerState.muxerStarted = true
                                    Log.e("AudioRecorder", "Muxer started from audio side")
                                }
                            }
                        }
                        continue
                    }
                    if (outputIndex >= 0) {
                        val encodedData = encoder.getOutputBuffer(outputIndex)
                        if (encodedData != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                bufferInfo.size > 0 && SharedMuxerState.muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                synchronized(SharedMuxerState.muxerLock) {
                                    if (SharedMuxerState.audioTrackIndex != -1) {
                                        SharedMuxerState.muxer.writeSampleData(
                                            SharedMuxerState.audioTrackIndex,
                                            encodedData,
                                            bufferInfo
                                        )
                                        Log.e("AudioRecorder", "Audio sample written: size=${bufferInfo.size}")
                                    }
                                }
                            }
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawEOS = true
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                } catch (e: IllegalStateException) {
                    Log.e("AudioRecorder", "Encoder loop error: ${e.message}")
                    break
                }
            }
            release()
        }
        encoderThread?.start()
    }

    fun release() {
        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Release error: ${e.message}")
        } finally {
            audioEncoder = null
        }
    }
}
