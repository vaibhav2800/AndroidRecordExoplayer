package com.vr.androidrecordexoplayer

import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var progressBar: ProgressBar

    private val streamUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var recordingAudioProcessor: RecordingAudioProcessor
    private lateinit var streamRecorder: StreamRecorder

    private var isRecording = false
    private lateinit var outputFile: File

    private lateinit var glPreviewView: GLPreviewView
    private lateinit var glSurfaceRelay: GLSurfaceRelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.video_view)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        progressBar = findViewById(R.id.progressBar)

        setupExoPlayerWithRecording()

        startBtn.setOnClickListener { startRecording() }
        stopBtn.setOnClickListener { stopRecording() }
    }

    private fun setupExoPlayerWithRecording() {
        outputFile = File(getExternalFilesDir(null), "recorded_output.mp4")
        if (outputFile.exists()) outputFile.delete()

        SharedMuxerState.muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        SharedMuxerState.audioTrackIndex = -1
        SharedMuxerState.videoTrackIndex = -1
        SharedMuxerState.muxerStarted = false

        audioRecorder = AudioRecorder()
        recordingAudioProcessor = RecordingAudioProcessor()
        streamRecorder = StreamRecorder(this)
        streamRecorder.init()
        recordingAudioProcessor.setRecorder(audioRecorder)

  /*      val renderersFactory = CustomRenderersFactory(this, recordingAudioProcessor)
        player = ExoPlayer.Builder(this, renderersFactory).build()
        player.setVideoSurface(streamRecorder.getInputSurface())
        playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

     */


     /*   glPreviewView = findViewById(R.id.gl_preview_view)
        // Wait until previewSurface is available (surfaceCreated called)
        glPreviewView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                glSurfaceRelay = GLSurfaceRelay(
                    encoderSurface = streamRecorder.getInputSurface(),
                    previewSurface = holder.surface
                )
                glSurfaceRelay.start()

                val renderersFactory = CustomRenderersFactory(this@MainActivity, recordingAudioProcessor)
                player = ExoPlayer.Builder(this@MainActivity, renderersFactory).build()
                player.setVideoSurface(glSurfaceRelay.getSurface())
                // No need to set playerView.player, as preview is handled by GLPreviewView

                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })*/
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        lifecycleScope.launch(Dispatchers.IO) {
            audioRecorder.start()
//            recordingAudioProcessor.setRecorder(audioRecorder)
            streamRecorder.start()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        SharedMuxerState.audioTrackIndex = -1
        SharedMuxerState.videoTrackIndex = -1

        runOnUiThread { progressBar.visibility = View.VISIBLE }

        lifecycleScope.launch(Dispatchers.IO) {
            audioRecorder.stop()
            streamRecorder.stop()
            audioRecorder.release()
            streamRecorder.release()

            synchronized(SharedMuxerState.muxerLock) {
                if (SharedMuxerState.muxerStarted) {
                    SharedMuxerState.muxer.stop()
                    SharedMuxerState.muxer.release()
                    SharedMuxerState.muxerStarted = false
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Recording saved: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        if (isRecording) {
            stopRecording()
        }
    }
}
