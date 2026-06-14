package com.vr.androidrecordexoplayer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class StreamRecorder(private val outputPath: String) {

    companion object {
        private const val TAG = "StreamRecorder"
    }

    private lateinit var mediaMuxer: MediaMuxer
    private lateinit var videoEncoder: MediaCodec
    private lateinit var audioEncoder: MediaCodec
    private lateinit var encoderInputSurface: Surface

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    @Volatile private var muxerStarted = false
    @Volatile private var isRecording = false
    @Volatile private var hasAudio = true

    private var videoPtsOffset = -1L
    private var audioPtsOffset = -1L

    private val muxerLock = Any()
    private val finishedEncoders = AtomicInteger(0)
    private var expectedEncoders = 2

    fun getInputSurface(): Surface = encoderInputSurface

    /**
     * @param hasAudio set false when the source stream has no audio track. The muxer then
     *                 starts as soon as the video track is ready instead of waiting forever
     *                 for an audio track that will never arrive.
     */
    fun start(
        width: Int = 1920,
        height: Int = 1080,
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        hasAudio: Boolean = true
    ) {
        videoTrackIndex = -1
        audioTrackIndex = -1
        videoPtsOffset = -1L
        audioPtsOffset = -1L
        muxerStarted = false
        finishedEncoders.set(0)
        this.hasAudio = hasAudio
        expectedEncoders = if (hasAudio) 2 else 1

        mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        setupVideoEncoder(width, height)
        if (hasAudio) setupAudioEncoder(sampleRate, channelCount)

        isRecording = true
        startVideoEncoderLoop()
        if (hasAudio) startAudioEncoderLoop()

        Log.d(TAG, "Recording started → $outputPath (hasAudio=$hasAudio)")
    }

    private fun setupVideoEncoder(width: Int, height: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()
    }

    private fun setupAudioEncoder(sampleRate: Int, channelCount: Int) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()
    }

    // Called from RecordingAudioProcessor on ExoPlayer's audio thread.
    fun feedAudio(buffer: ByteBuffer, size: Int, presentationTimeUs: Long) {
        if (!isRecording || !hasAudio) return
        try {
            val inputIndex = audioEncoder.dequeueInputBuffer(5_000)
            if (inputIndex >= 0) {
                val inputBuffer = audioEncoder.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                val bytesToCopy = minOf(size, inputBuffer.remaining())
                val slice = buffer.duplicate()
                slice.limit(slice.position() + bytesToCopy)
                inputBuffer.put(slice)
                audioEncoder.queueInputBuffer(inputIndex, 0, bytesToCopy, presentationTimeUs, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "feedAudio error: ${e.message}")
        }
    }

    private fun startVideoEncoderLoop() {
        Thread({
            val info = MediaCodec.BufferInfo()
            while (true) {
                val idx = videoEncoder.dequeueOutputBuffer(info, 10_000)
                when {
                    idx >= 0 -> {
                        val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val buf = videoEncoder.getOutputBuffer(idx)

                        if (!isConfig && buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            if (videoPtsOffset < 0) videoPtsOffset = info.presentationTimeUs
                            info.presentationTimeUs = maxOf(0L, info.presentationTimeUs - videoPtsOffset)
                            synchronized(muxerLock) {
                                if (muxerStarted && videoTrackIndex >= 0) {
                                    mediaMuxer.writeSampleData(videoTrackIndex, buf, info)
                                }
                            }
                        }
                        videoEncoder.releaseOutputBuffer(idx, false)
                        if (isEos) break
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            videoTrackIndex = mediaMuxer.addTrack(videoEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                    }
                }
            }
            videoEncoder.stop()
            videoEncoder.release()
            onEncoderFinished()
        }, "StreamRecorder-Video").start()
    }

    private fun startAudioEncoderLoop() {
        Thread({
            val info = MediaCodec.BufferInfo()
            while (true) {
                val idx = audioEncoder.dequeueOutputBuffer(info, 10_000)
                when {
                    idx >= 0 -> {
                        val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val buf = audioEncoder.getOutputBuffer(idx)

                        if (!isConfig && buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            if (audioPtsOffset < 0) audioPtsOffset = info.presentationTimeUs
                            info.presentationTimeUs = maxOf(0L, info.presentationTimeUs - audioPtsOffset)
                            synchronized(muxerLock) {
                                if (muxerStarted && audioTrackIndex >= 0) {
                                    mediaMuxer.writeSampleData(audioTrackIndex, buf, info)
                                }
                            }
                        }
                        audioEncoder.releaseOutputBuffer(idx, false)
                        if (isEos) break
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            audioTrackIndex = mediaMuxer.addTrack(audioEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                    }
                }
            }
            audioEncoder.stop()
            audioEncoder.release()
            onEncoderFinished()
        }, "StreamRecorder-Audio").start()
    }

    private fun maybeStartMuxer() {
        val audioReady = !hasAudio || audioTrackIndex >= 0
        if (!muxerStarted && videoTrackIndex >= 0 && audioReady) {
            mediaMuxer.start()
            muxerStarted = true
            Log.d(TAG, "MediaMuxer started — tracks ready (hasAudio=$hasAudio)")
        }
    }

    private fun onEncoderFinished() {
        if (finishedEncoders.incrementAndGet() >= expectedEncoders) {
            synchronized(muxerLock) {
                try {
                    if (muxerStarted) mediaMuxer.stop()
                    mediaMuxer.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Muxer release error: ${e.message}")
                }
                muxerStarted = false
            }
            Log.d(TAG, "Recording saved → $outputPath")
        }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false

        // Signal video EOS via surface
        try {
            videoEncoder.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.e(TAG, "signalEndOfInputStream error: ${e.message}")
        }

        // Signal audio EOS via input buffer
        if (hasAudio) {
            try {
                val idx = audioEncoder.dequeueInputBuffer(5_000)
                if (idx >= 0) {
                    audioEncoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio EOS error: ${e.message}")
            }
        }
    }
}