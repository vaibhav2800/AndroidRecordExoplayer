package com.vr.androidrecordexoplayer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer

class StreamRecorder(
    private val context: Context,
    private val outputPath: String
) {

    private lateinit var mediaMuxer: MediaMuxer
    private lateinit var videoEncoder: MediaCodec
    private lateinit var audioEncoder: MediaCodec

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    private var videoPtsOffset = -1L
    private var audioPtsOffset = -1L

    private lateinit var videoInputSurface: Surface

    private val muxerLock = Object()

    @Volatile
    private var isRecording = false

    fun getInputSurface(): Surface = videoInputSurface

    fun init(sampleRate: Int = 44100, channels: Int = 2) {
        // Step 1: Setup Encoders
        setupVideoEncoder()
        setupAudioEncoder(sampleRate, channels)

        // Step 2: Setup MediaMuxer
        val file = File(outputPath)
        if (file.exists()) file.delete()

        mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        isRecording = true

        // Step 3: Now start encoder threads
        startEncoderLoop(videoEncoder, isVideo = true)
        startEncoderLoop(audioEncoder, isVideo = false)
    }

    private fun setupVideoEncoder() {
        val format = MediaFormat.createVideoFormat("video/avc", 1280, 720).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        videoEncoder = MediaCodec.createEncoderByType("video/avc")
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoInputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()
    }

    private fun setupAudioEncoder(sampleRate: Int, channels: Int) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()
    }

    private fun startEncoderLoop(codec: MediaCodec, isVideo: Boolean) {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRecording) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val encodedData = codec.getOutputBuffer(outputIndex) ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        val pts = bufferInfo.presentationTimeUs -
                                if (isVideo) videoPtsOffset else audioPtsOffset

                        bufferInfo.presentationTimeUs = pts

                        synchronized(muxerLock) {
                            val trackIndex = if (isVideo) videoTrackIndex else audioTrackIndex
                            if (trackIndex >= 0) {
                                mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    synchronized(muxerLock) {
                        if (isVideo) {
                            videoTrackIndex = mediaMuxer.addTrack(newFormat)
                            videoPtsOffset = System.nanoTime() / 1000
                        } else {
                            audioTrackIndex = mediaMuxer.addTrack(newFormat)
                            audioPtsOffset = System.nanoTime() / 1000
                        }

                        if (videoTrackIndex != -1 && audioTrackIndex != -1 && !muxerStarted) {
                            mediaMuxer.start()
                            muxerStarted = true
                        }
                    }
                }
            }
        }.start()
    }

    fun queuePcmData(pcmBuffer: ByteBuffer, size: Int, presentationTimeUs: Long) {
        val inputIndex = audioEncoder.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(pcmBuffer)
            audioEncoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
        }
    }

    fun stop() {
        isRecording = false
        try {
            videoEncoder.signalEndOfInputStream()
        } catch (_: Exception) {}

        Thread {
            Thread.sleep(1000)
            synchronized(muxerLock) {
                try {
                    mediaMuxer.stop()
                    mediaMuxer.release()
                } catch (_: Exception) {}
            }

            videoEncoder.stop()
            videoEncoder.release()

            audioEncoder.stop()
            audioEncoder.release()
        }.start()
    }
}
