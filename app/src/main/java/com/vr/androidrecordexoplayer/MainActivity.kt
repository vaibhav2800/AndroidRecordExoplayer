package com.vr.androidrecordexoplayer

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var player: ExoPlayer
    private lateinit var videoView: TextureView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var recordingAudioProcessor: RecordingAudioProcessor

    private var pipeline: GlRecordingPipeline? = null
    private var displaySurface: Surface? = null
    private var streamRecorder: StreamRecorder? = null
    private var isRecording = false

    // Source resolution, updated from ExoPlayer; used to size the encoder.
    private var videoWidth = 1280
    private var videoHeight = 720

    // View-level zoom/pan applied to the TextureView (does not affect the recorded video).
    private val displayMatrix = Matrix()
    private var scale = 1f
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val streamUrl =
        "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoView = findViewById(R.id.video_view)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)

        setupExoPlayer()
        setupGestures()

        // The GL pipeline needs a display Surface; build it once the TextureView is ready
        // and tear it down when its SurfaceTexture goes away.
        videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                val surface = Surface(st)
                displaySurface = surface
                val gl = GlRecordingPipeline(surface, videoWidth, videoHeight)
                gl.init()
                gl.setVideoSize(videoWidth, videoHeight)
                // Route ExoPlayer's video into the GL pipeline; the pipeline draws to screen.
                player.setVideoSurface(gl.inputSurface)
                pipeline = gl
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                player.setVideoSurface(null)
                pipeline?.release()
                pipeline = null
                displaySurface?.release()
                displaySurface = null
                // We released the Surface above; let the framework reclaim the SurfaceTexture.
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        startBtn.setOnClickListener { startRecording() }
        stopBtn.setOnClickListener { stopRecording() }
    }

    @OptIn(UnstableApi::class)
    private fun setupExoPlayer() {
        recordingAudioProcessor = RecordingAudioProcessor()
        val renderersFactory = CustomRenderersFactory(this, recordingAudioProcessor)

        player = ExoPlayer.Builder(this, renderersFactory).build()

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    pipeline?.setVideoSize(videoWidth, videoHeight)
                }
            }
        })

        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newScale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
                    val applied = newScale / scale
                    scale = newScale
                    displayMatrix.postScale(applied, applied, detector.focusX, detector.focusY)
                    applyTransform()
                    return true
                }
            }
        )

        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    // Only pan when zoomed in.
                    if (scale > 1f) {
                        displayMatrix.postTranslate(-distanceX, -distanceY)
                        applyTransform()
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (scale > 1f) {
                        // Reset to fit.
                        scale = 1f
                        displayMatrix.reset()
                    } else {
                        // Zoom to 2x centered on the tap point.
                        scale = 2f
                        displayMatrix.reset()
                        displayMatrix.postScale(scale, scale, e.x, e.y)
                    }
                    applyTransform()
                    return true
                }
            }
        )

        videoView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun applyTransform() {
        videoView.setTransform(displayMatrix)
        videoView.invalidate()
    }

    @OptIn(UnstableApi::class)
    private fun startRecording() {
        if (isRecording) return
        val gl = pipeline
        if (gl == null) {
            Toast.makeText(this, "Preview not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        val outputFile = File(getExternalFilesDir(null), "recorded_video.mp4")
        if (outputFile.exists()) outputFile.delete()

        Log.d(TAG, "Recording output: ${outputFile.absolutePath}")

        val fmt = recordingAudioProcessor.getAudioFormat()
        // The audio processor is only configured once ExoPlayer's audio renderer sees an
        // audio track. If sampleRate is still 0 here, the source has no audio — record video
        // only so the muxer can start instead of waiting forever for an audio track.
        val hasAudio = fmt.sampleRate > 0
        val sampleRate = if (hasAudio) fmt.sampleRate else 44100
        val channelCount = if (hasAudio) fmt.channelCount else 2

        Log.d(
            TAG,
            "Recording ${videoWidth}x$videoHeight hasAudio=$hasAudio (${sampleRate}Hz ch=$channelCount)"
        )

        val recorder = StreamRecorder(outputFile.absolutePath)
        recorder.start(
            width = videoWidth,
            height = videoHeight,
            sampleRate = sampleRate,
            channelCount = channelCount,
            hasAudio = hasAudio
        )

        // Fan decoded frames out to the encoder; the preview keeps rendering meanwhile.
        gl.startEncoder(recorder.getInputSurface())
        recordingAudioProcessor.setStreamRecorder(recorder)

        streamRecorder = recorder
        isRecording = true

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    @OptIn(UnstableApi::class)
    private fun stopRecording() {
        if (!isRecording) return

        // Stop feeding the encoder, then finalize.
        recordingAudioProcessor.setStreamRecorder(null)
        pipeline?.stopEncoder()

        streamRecorder?.stop()
        streamRecorder = null

        isRecording = false

        Toast.makeText(this, "Recording stopped — saved to app storage", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
        player.release()
    }
}