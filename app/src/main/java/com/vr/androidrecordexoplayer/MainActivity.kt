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

    private lateinit var recorder: StreamRecorder
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

        setupStreamRecorder()
        setupExoPlayerV2()


        startBtn.setOnClickListener { startRecording() }
        stopBtn.setOnClickListener { stopRecording() }
    }

    @OptIn(UnstableApi::class)
    private fun setupExoPlayerV2() {
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
    

    private fun setupStreamRecorder(){
        val outputFile = "${cacheDir}/recorded_output.mp4"
        Log.e("TAG-VAIBHAV","FIle Path- ${outputFile}")
        recorder = StreamRecorder(this, outputFile)
        recorder.init()

        // Set ExoPlayer output surface to video encoder surface
         if (::player.isInitialized) {
            player.setVideoSurface(recorder.getInputSurface())
         }
    }

    @OptIn(UnstableApi::class)

    private fun startRecording() {
        recorder.start()
        recordingAudioProcessor.onPcmData = { buffer, size, pts ->
            recorder.queuePcmData(buffer, size, pts)
        }
        audioRecorder.start()
    }

    @OptIn(UnstableApi::class)
    private fun stopRecording() {
        recorder.stop()
        audioRecorder.stop()
    }

    override fun onDestroy() {
        if(::recorder.isInitialized){
            recorder.release()
        }

        super.onDestroy()
        player.release()
    }

}