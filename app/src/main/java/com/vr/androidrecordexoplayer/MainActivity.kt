package com.vr.androidrecordexoplayer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.vr.androidrecordexoplayer.databinding.ActivityMainBinding
import java.io.IOException
import java.nio.ByteBuffer
import java.util.LinkedList


/**
 * Main Activity
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private val HTTP_URL =
        "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private lateinit var binding: ActivityMainBinding

    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition: Long = 0

    private val outputFilePath: String? = null
    private val recordingThread: HandlerThread? = null
    private val recordingHandler: Handler? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex :Int? = -1
    private var audioTrackIndex :Int? = -1
    private var muxerStarted = false
    private val videoFrameCount: Long = 0
    private val audioFrameCount: Long = 0
    private val videoWidth = 0
    private val videoHeight = 0
    private val videoFormat: Format? = null
    private val audioFormat: Format? = null
    private val videoPtsOffset: Long = 0
    private val audioPtsOffset: Long = 0

    private val videoFrames: List<VideoFrame> = LinkedList()
    private val audioFrames: List<AudioFrame> = LinkedList()

    private val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264

    private val VIDEO_FRAME_RATE = 30
    private val VIDEO_BIT_RATE = 2000000 // 2 Mbps (adjust as needed)

    private val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
    private val AUDIO_BIT_RATE = 128000 // 128 kbps (adjust as needed)

    private val AUDIO_SAMPLE_RATE = 44100
    private val AUDIO_CHANNEL_COUNT = 2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startBtn.setOnClickListener {

        }

        binding.stopBtn.setOnClickListener {

        }

    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        player?.let {
            it.playWhenReady = playWhenReady
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let {
            playWhenReady = it.playWhenReady
            playbackPosition = it.currentPosition
            it.release()
            player = null
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        Log.setLogLevel(Log.LOG_LEVEL_ALL)

        player = ExoPlayer.Builder(this)
            .
            .build().also { exoPlayer ->
            binding.videoView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(Uri.parse(HTTP_URL))

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Error- ${error.errorCode} , Name- ${error.errorCodeName}")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)

                    Log.e(TAG, "Playback State-  ${playbackState}")
                }


            })
        }
    }


    //
    //    @UnstableApi
    //    private class RecordingRenderersFactory extends DefaultRenderersFactory {
    //
    //        public RecordingRenderersFactory(Context context) {
    //            super(context);
    //        }
    //
    //        @Override
    //        protected void buildVideoRenderers(
    //                Context context,
    //                @ExtensionRendererMode int extensionRendererMode,
    //                MediaCodecSelector mediaCodecSelector,
    //                boolean enableDecoderFallback,
    //                Handler eventHandler,
    //                VideoRendererEventListener eventListener,
    //                long allowedVideoJoiningTimeMs,
    //                ArrayList<Renderer> out) {
    //            RecordingVideoRenderer videoRenderer =
    //                    new RecordingVideoRenderer(
    //                            context,
    //                            getCodecAdapterFactory(),
    //                            mediaCodecSelector,
    //                            allowedVideoJoiningTimeMs,
    //                            enableDecoderFallback,
    //                            eventHandler,
    //                            eventListener,
    //                            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //            out.add(videoRenderer);
    //        }
    //
    //        @Override
    //        protected void buildAudioRenderers(
    //                Context context,
    //                @ExtensionRendererMode int extensionRendererMode,
    //                MediaCodecSelector mediaCodecSelector,
    //                boolean enableDecoderFallback,
    //                AudioSink audioSink,
    //                Handler eventHandler,
    //                AudioRendererEventListener eventListener,
    //                ArrayList<Renderer> out) {
    //            RecordingAudioRenderer audioRenderer =
    //                    new RecordingAudioRenderer(
    //                            context,
    //                            getCodecAdapterFactory(),
    //                            mediaCodecSelector,
    //                            enableDecoderFallback,
    //                            eventHandler,
    //                            eventListener,
    //                            audioSink);
    //            out.add(audioRenderer);
    //        }
    //    }
    //
    //    @UnstableApi
    //    private class RecordingVideoRenderer extends MediaCodecVideoRenderer {
    //
    //        public RecordingVideoRenderer(
    //                Context context,
    //                MediaCodecAdapter.Factory codecAdapterFactory,
    //                MediaCodecSelector mediaCodecSelector,
    //                long allowedVideoJoiningTimeMs,
    //                boolean enableDecoderFallback,
    //                Handler eventHandler,
    //                VideoRendererEventListener eventListener,
    //                int maxDroppedFramesToNotify) {
    //            super(
    //                    context,
    //                    codecAdapterFactory,
    //                    mediaCodecSelector,
    //                    allowedVideoJoiningTimeMs,
    //                    enableDecoderFallback,
    //                    eventHandler,
    //                    eventListener,
    //                    maxDroppedFramesToNotify);
    //        }
    //
    //        @Override
    //        public void onFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    //            super.onFormatChanged(formatHolder);
    //            videoFormat = formatHolder.format;
    //            videoWidth = videoFormat.width;
    //            videoHeight = videoFormat.height;
    //
    //            recordingHandler.post(() -> {
    //                if (mediaMuxer == null) {
    //                    startMuxer();
    //                }
    //            });
    //        }
    //
    //
    //        @Override
    //        protected void onOutputFrameAvailable(MediaCodec codec, int bufferIndex, MediaCodec.BufferInfo info) {
    //            ByteBuffer buffer = codec.getOutputBuffer(bufferIndex);
    //            if (buffer != null) {
    //                ByteBuffer myCopy = ByteBuffer.allocateDirect(buffer.capacity());
    //                myCopy.put(buffer);
    //                myCopy.rewind();
    //
    //                long pts = info.presentationTimeUs - videoPtsOffset;
    //                info.presentationTimeUs = pts < 0 ? 0 : pts;
    //
    //                recordingHandler.post(() -> {
    //                    if (muxerStarted) {
    //                        writeVideoFrame(myCopy, info);
    //                    } else {
    //                        videoFrames.add(new VideoFrame(myCopy, info));
    //                    }
    //                });
    //            }
    //
    //            codec.releaseOutputBuffer(bufferIndex, false);
    //            videoFrameCount++;
    //        }
    //
    //        @Override
    //        protected void onOutputStreamOffsetUs(long offsetUs) {
    //            super.onOutputStreamOffsetUs(offsetUs);
    //            videoPtsOffset = offsetUs;
    //        }
    //    }
    @UnstableApi
    private class RecordingAudioRenderer(
        context: Context?,
        codecAdapterFactory: MediaCodecAdapter.Factory?,
        mediaCodecSelector: MediaCodecSelector?,
        enableDecoderFallback: Boolean,
        eventHandler: Handler?,
        eventListener: AudioRendererEventListener?,
        audioSink: AudioSink?
    ) : MediaCodecAudioRenderer(
        context!!,
        codecAdapterFactory!!,
        mediaCodecSelector!!,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        audioSink!!
    ) {
        @Throws(ExoPlaybackException::class)
        override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
            super.onOutputFormatChanged(format, mediaFormat)
            audioFormat = format
            recordingHandler.post(Runnable {
                if (mediaMuxer == null) {
                    startMuxer()
                }
            })
        }

        @Throws(ExoPlaybackException::class)
        override fun processOutputBuffer(
            positionUs: Long, elapsedRealtimeUs: Long, codec: MediaCodecAdapter?,
            buffer: ByteBuffer?, bufferIndex: Int, bufferFlags: Int, sampleCount: Int,
            bufferPresentationTimeUs: Long, isDecodeOnlyBuffer: Boolean,
            isLastBuffer: Boolean, format: Format
        ): Boolean {
            var myAudioCopy: ByteBuffer? = null
            val myInfo = MediaCodec.BufferInfo()
            if (!isDecodeOnlyBuffer) {
                if (buffer != null) {
                    //  Copy the buffer
                    myAudioCopy = ByteBuffer.allocateDirect(buffer.capacity())
                    myAudioCopy.put(buffer)
                    myAudioCopy.rewind()

                    //  Create a copy of BufferInfo
                    myInfo[0, buffer.remaining(), (bufferPresentationTimeUs - audioPtsOffset).toInt()
                        .toLong()] =
                        bufferFlags
                    if (myInfo.presentationTimeUs < 0) {
                        myInfo.presentationTimeUs = 0
                    }
                }
                val finalMyAudioCopy = myAudioCopy
                recordingHandler.post(Runnable {
                    if (muxerStarted) {
                        writeAudioFrame(finalMyAudioCopy, myInfo)
                    } else {
                        audioFrames.add(
                            AudioFrame(
                                finalMyAudioCopy,
                                myInfo
                            )
                        )
                    }
                })
            }
            val fullyConsumed = super.processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                buffer,
                bufferIndex,
                bufferFlags,
                sampleCount,
                bufferPresentationTimeUs,
                isDecodeOnlyBuffer,
                isLastBuffer,
                format
            )
            if (codec != null && fullyConsumed) {
                codec.releaseOutputBuffer(bufferIndex, false)
            }
            return fullyConsumed
        }

        override fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {
            super.onOutputStreamOffsetUsChanged(outputStreamOffsetUs)
            audioPtsOffset = outputStreamOffsetUs
        }
    }


    fun release() {
        recordingThread?.quitSafely()
        stopMuxer()
    }

    private fun startMuxer() {
        try {
            mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoFormat = MediaFormat.createVideoFormat(
                VIDEO_MIME_TYPE,
                videoWidth,
                videoHeight
            )
            videoFormat.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                VIDEO_FRAME_RATE
            )
            videoFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                VIDEO_BIT_RATE
            )
            videoTrackIndex = mediaMuxer?.addTrack(videoFormat)
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT
            )
            audioFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                AUDIO_BIT_RATE
            )
            audioTrackIndex = mediaMuxer?.addTrack(audioFormat)
            mediaMuxer?.start()
            muxerStarted = true
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Error starting muxer",
                e
            )
        }
    }

    private fun stopMuxer() {
        if (muxerStarted) {
            mediaMuxer?.apply {
                stop()
                release()
            }
            muxerStarted = false
        }
    }

    private fun writeVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!muxerStarted){
            return
        }
        try {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            videoTrackIndex?.let {
                mediaMuxer?.writeSampleData(it, data, info)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error writing video frame",
                e
            )
        }
    }

    private fun writeAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!muxerStarted){
            return
        }
        try {
            data.position(info.offset)
            data.limit(info.offset + info.size)
            audioTrackIndex?.let {
                mediaMuxer?.writeSampleData(it, data, info)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error writing audio frame")
        }
    }


    private class VideoFrame(var buffer: ByteBuffer?, var info: MediaCodec.BufferInfo)

    private class AudioFrame(var buffer: ByteBuffer?, var info: MediaCodec.BufferInfo)


}