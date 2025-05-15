package com.vr.androidrecordexoplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.Config
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.concurrent.thread

@UnstableApi
class MainActivityNew : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var progressBar: ProgressBar

    private val streamUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    private var isRecording = false
    private var recordStartMs: Long = 0
    private var recordEndMs: Long = 0

    private lateinit var localVideoFile: File

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.video_view)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        progressBar = findViewById(R.id.progressBar)

        requestStoragePermission()
        setupExoPlayer()

        startBtn.setOnClickListener { startRecording() }
        stopBtn.setOnClickListener { stopRecording() }
    }

    private fun requestStoragePermission() {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE_PERMISSION)
        }
    }

    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        recordStartMs = player.currentPosition
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        // Download video to local file in background
        progressBar.visibility = View.VISIBLE
        thread {
            try {
                val fileName = "downloaded_video.mp4"
                localVideoFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
                if (!localVideoFile.exists()) {
                    val url = URL(streamUrl)
                    url.openStream().use { input ->
                        FileOutputStream(localVideoFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                runOnUiThread { progressBar.visibility = View.GONE }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordEndMs = player.currentPosition

        if (!::localVideoFile.isInitialized || !localVideoFile.exists()) {
            Toast.makeText(this, "Video not downloaded yet!", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        val startSec = recordStartMs / 1000f
        val durationSec = (recordEndMs - recordStartMs) / 1000f

        val outputPath = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "clip_${System.currentTimeMillis()}.mp4").absolutePath
        val ffmpegCommand = arrayOf(
            "-ss", startSec.toString(),
            "-i", localVideoFile.absolutePath,
            "-t", durationSec.toString(),
            "-c:v", "copy",
            "-c:a", "copy",
            outputPath
        )

        thread {
            val rc = FFmpeg.execute(ffmpegCommand)
            runOnUiThread {
                progressBar.visibility = View.GONE
                when (rc) {
                    Config.RETURN_CODE_SUCCESS -> {
                        Toast.makeText(this, "Clip saved: $outputPath", Toast.LENGTH_LONG).show()
                    }
                    Config.RETURN_CODE_CANCEL -> {
                        Toast.makeText(this, "Operation canceled", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this, "Error: ${Config.getLastCommandOutput()}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
