package com.vr.androidrecordexoplayer

import AudioRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Main Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    private val streamUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private lateinit var audioRecorder: AudioRecorder
    @UnstableApi
    private lateinit var recordingAudioProcessor: RecordingAudioProcessor

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.video_view)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)

        // 1. Prepare custom RecordingAudioProcessor
        // 2. Prepare AudioRecorder
//        val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recorded_audio.aac")
//        recordingAudioProcessor = RecordingAudioProcessor()
//        audioRecorder = AudioRecorder(outputFile.absolutePath, recordingAudioProcessor)

        setupExoPlayerV2()

        startBtn.setOnClickListener { startRecording() }
        stopBtn.setOnClickListener { stopRecording() }
    }

//    @OptIn(UnstableApi::class)
//    private fun setupExoPlayer() {
//        pcmProcessor = PCMExtractorProcessor()
//
//        val audioSink: AudioSink = DefaultAudioSink.Builder()
//            .setAudioProcessors(arrayOf<AudioProcessor>(pcmProcessor))
//            .build()
//
//        val renderersFactory = DefaultRenderersFactory(this)
//            .setAudioSink(audioSink)
//
//        player = ExoPlayer.Builder(this, renderersFactory).build()
//        playerView.player = player
//
//        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
//        player.setMediaItem(mediaItem)
//        player.prepare()
//        player.playWhenReady = true
//    }
//
//    @OptIn(UnstableApi::class)
//    private fun setupExoPlayerV1() {
//        val renderersFactory = DefaultRenderersFactory(this)
//            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
//            .setEnableAudioTrackPlaybackParams(true)
//
//        player = ExoPlayer.Builder(this, renderersFactory)
//            .build()
//
//        // Inject audio processor AFTER player is built
//        val audioComponent = player.audioComponent
//        audioComponent?.apply {
//            setAudioProcessors(arrayOf(pcmProcessor))
//        }
//
//        playerView.player = player
//
//        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
//        player.setMediaItem(mediaItem)
//        player.prepare()
//        player.playWhenReady = true
//    }

    @OptIn(UnstableApi::class)
    private fun setupExoPlayerV2() {
//        pcmProcessor = PCMExtractorProcessor()

//        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "recorded_audio.mp4")

        val outputDir = getExternalFilesDir(null)
        if(outputDir?.exists()==false){
            Log.e("TAG-VAIBHAV","Output file createdddd")
            outputDir?.mkdir()
        }
        val file = File(outputDir, "recorded_audio.mp4")

        if (file.exists()) {
            Log.e("TAG-VAIBHAV","File exists")
            file.delete()
        }
        file.createNewFile()

        Log.e("TAG-VAIBHAV","Path-  ${file.absolutePath}")
        audioRecorder = AudioRecorder(file.absolutePath)

        recordingAudioProcessor = RecordingAudioProcessor()
        recordingAudioProcessor.setRecorder(audioRecorder)
        val renderersFactory = CustomRenderersFactory(this, recordingAudioProcessor)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView
        playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }



    @OptIn(UnstableApi::class)
    private fun startRecording() {
//        val outputFile = "${cacheDir}/recorded_output.mp4"
//        Log.e("TAG-VAIBHAV","FIle Path- ${outputFile}")
//        recorder = StreamRecorder(this, outputFile)
//        recorder.init()
//
//        // Send decoded audio PCM to audio encoder
//        pcmProcessor.onPcmData = { buffer, size, pts ->
//            recorder.queuePcmData(buffer, size, pts)
//        }
//
//        // Set ExoPlayer output surface to video encoder surface
//        player.setVideoSurface(recorder.getInputSurface())
//        audioRecorder.start()



        audioRecorder.start()
    }

    @OptIn(UnstableApi::class)
    private fun stopRecording() {
//        recorder.stop()
        recordingAudioProcessor.setRecorder(null)
        audioRecorder.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
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
//    @UnstableApi
//    private class RecordingAudioRenderer(
//        context: Context?,
//        codecAdapterFactory: MediaCodecAdapter.Factory?,
//        mediaCodecSelector: MediaCodecSelector?,
//        enableDecoderFallback: Boolean,
//        eventHandler: Handler?,
//        eventListener: AudioRendererEventListener?,
//        audioSink: AudioSink?
//    ) : MediaCodecAudioRenderer(
//        context!!,
//        codecAdapterFactory!!,
//        mediaCodecSelector!!,
//        enableDecoderFallback,
//        eventHandler,
//        eventListener,
//        audioSink!!
//    ) {
//        @Throws(ExoPlaybackException::class)
//        override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
//            super.onOutputFormatChanged(format, mediaFormat)
//            audioFormat = format
//            recordingHandler.post(Runnable {
//                if (mediaMuxer == null) {
//                    startMuxer()
//                }
//            })
//        }
//
//        @Throws(ExoPlaybackException::class)
//        override fun processOutputBuffer(
//            positionUs: Long, elapsedRealtimeUs: Long, codec: MediaCodecAdapter?,
//            buffer: ByteBuffer?, bufferIndex: Int, bufferFlags: Int, sampleCount: Int,
//            bufferPresentationTimeUs: Long, isDecodeOnlyBuffer: Boolean,
//            isLastBuffer: Boolean, format: Format
//        ): Boolean {
//            var myAudioCopy: ByteBuffer? = null
//            val myInfo = MediaCodec.BufferInfo()
//            if (!isDecodeOnlyBuffer) {
//                if (buffer != null) {
//                    //  Copy the buffer
//                    myAudioCopy = ByteBuffer.allocateDirect(buffer.capacity())
//                    myAudioCopy.put(buffer)
//                    myAudioCopy.rewind()
//
//                    //  Create a copy of BufferInfo
//                    myInfo[0, buffer.remaining(), (bufferPresentationTimeUs - audioPtsOffset).toInt()
//                        .toLong()] =
//                        bufferFlags
//                    if (myInfo.presentationTimeUs < 0) {
//                        myInfo.presentationTimeUs = 0
//                    }
//                }
//                val finalMyAudioCopy = myAudioCopy
//                recordingHandler.post(Runnable {
//                    if (muxerStarted) {
//                        writeAudioFrame(finalMyAudioCopy, myInfo)
//                    } else {
//                        audioFrames.add(
//                            AudioFrame(
//                                finalMyAudioCopy,
//                                myInfo
//                            )
//                        )
//                    }
//                })
//            }
//            val fullyConsumed = super.processOutputBuffer(
//                positionUs,
//                elapsedRealtimeUs,
//                codec,
//                buffer,
//                bufferIndex,
//                bufferFlags,
//                sampleCount,
//                bufferPresentationTimeUs,
//                isDecodeOnlyBuffer,
//                isLastBuffer,
//                format
//            )
//            if (codec != null && fullyConsumed) {
//                codec.releaseOutputBuffer(bufferIndex, false)
//            }
//            return fullyConsumed
//        }
//
//        override fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {
//            super.onOutputStreamOffsetUsChanged(outputStreamOffsetUs)
//            audioPtsOffset = outputStreamOffsetUs
//        }
//    }
//
//
//    fun release() {
//        recordingThread?.quitSafely()
//        stopMuxer()
//    }
//
//    private fun startMuxer() {
//        try {
//            mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//            val videoFormat = MediaFormat.createVideoFormat(
//                VIDEO_MIME_TYPE,
//                videoWidth,
//                videoHeight
//            )
//            videoFormat.setInteger(
//                MediaFormat.KEY_FRAME_RATE,
//                VIDEO_FRAME_RATE
//            )
//            videoFormat.setInteger(
//                MediaFormat.KEY_BIT_RATE,
//                VIDEO_BIT_RATE
//            )
//            videoTrackIndex = mediaMuxer?.addTrack(videoFormat)
//            val audioFormat = MediaFormat.createAudioFormat(
//                AUDIO_MIME_TYPE,
//                AUDIO_SAMPLE_RATE,
//                AUDIO_CHANNEL_COUNT
//            )
//            audioFormat.setInteger(
//                MediaFormat.KEY_BIT_RATE,
//                AUDIO_BIT_RATE
//            )
//            audioTrackIndex = mediaMuxer?.addTrack(audioFormat)
//            mediaMuxer?.start()
//            muxerStarted = true
//        } catch (e: IOException) {
//            Log.e(
//                TAG,
//                "Error starting muxer",
//                e
//            )
//        }
//    }
//
//    private fun stopMuxer() {
//        if (muxerStarted) {
//            mediaMuxer?.apply {
//                stop()
//                release()
//            }
//            muxerStarted = false
//        }
//    }
//
//    private fun writeVideoFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
//        if (!muxerStarted){
//            return
//        }
//        try {
//            data.position(info.offset)
//            data.limit(info.offset + info.size)
//            videoTrackIndex?.let {
//                mediaMuxer?.writeSampleData(it, data, info)
//            }
//        } catch (e: Exception) {
//            Log.e(
//                TAG,
//                "Error writing video frame",
//                e
//            )
//        }
//    }
//
//    private fun writeAudioFrame(data: ByteBuffer, info: MediaCodec.BufferInfo) {
//        if (!muxerStarted){
//            return
//        }
//        try {
//            data.position(info.offset)
//            data.limit(info.offset + info.size)
//            audioTrackIndex?.let {
//                mediaMuxer?.writeSampleData(it, data, info)
//            }
//        } catch (e: Exception) {
//            Log.e(
//                TAG,
//                "Error writing audio frame")
//        }
//    }
//
//
//    private class VideoFrame(var buffer: ByteBuffer?, var info: MediaCodec.BufferInfo)
//
//    private class AudioFrame(var buffer: ByteBuffer?, var info: MediaCodec.BufferInfo)


}