package com.vr.androidrecordexoplayer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

class StreamRecorder(private val context: Context) {

    private lateinit var videoEncoder: MediaCodec
    private lateinit var videoInputSurface: Surface

    @Volatile
    private var isRecording = false
    private var encoderThread: Thread? = null

    fun getInputSurface(): Surface = videoInputSurface

    fun init() {
        setupVideoEncoder()
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

    fun start() {
        isRecording = true
        startEncoderLoop()
    }

    fun stop() {
        isRecording = false
        try {
            videoEncoder.signalEndOfInputStream()
            encoderThread?.join()
        } catch (e: Exception) {
            Log.e("StreamRecorder", "Error signaling end of video stream: ${e.message}")
        }
    }

    private fun startEncoderLoop() {
        encoderThread = Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            var sawEOS = false
            while (!sawEOS && isRecording) {
                try {
                    val outputIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = videoEncoder.outputFormat
                        synchronized(SharedMuxerState.muxerLock) {
                            if (SharedMuxerState.videoTrackIndex == -1) {
                                SharedMuxerState.videoTrackIndex = SharedMuxerState.muxer.addTrack(newFormat)
                                SharedMuxerState.tryStartMuxer()
                                Log.e("StreamRecorder", "Video track added: ${SharedMuxerState.videoTrackIndex}")
                                if (SharedMuxerState.audioTrackIndex != -1 && !SharedMuxerState.muxerStarted) {
                                    SharedMuxerState.muxer.start()
                                    SharedMuxerState.muxerStarted = true
                                    Log.e("StreamRecorder", "Muxer started from video side")
                                }
                            }
                        }
                        continue
                    }
                    if (outputIndex >= 0) {
                        val encodedData = videoEncoder.getOutputBuffer(outputIndex)
                        if (encodedData != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                bufferInfo.size > 0 && SharedMuxerState.muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                synchronized(SharedMuxerState.muxerLock) {
                                    if (SharedMuxerState.videoTrackIndex != -1) {
                                        SharedMuxerState.muxer.writeSampleData(
                                            SharedMuxerState.videoTrackIndex,
                                            encodedData,
                                            bufferInfo
                                        )
                                        Log.e("StreamRecorder", "Writing video sample: size=${bufferInfo.size}")
                                    }
                                }
                            }
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawEOS = true
                        }
                        videoEncoder.releaseOutputBuffer(outputIndex, false)
                    }
                } catch (e: IllegalStateException) {
                    Log.e("StreamRecorder", "Encoder loop error: ${e.message}")
                    break
                }
            }
            release()
        }
        encoderThread?.start()
    }

    fun release() {
        try {
            videoEncoder.stop()
            videoEncoder.release()
        } catch (e: Exception) {
            Log.e("StreamRecorder", "Release error: ${e.message}")
        }
    }
}
