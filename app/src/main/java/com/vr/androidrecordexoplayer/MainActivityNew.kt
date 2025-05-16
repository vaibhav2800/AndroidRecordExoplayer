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
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread


@UnstableApi
class MainActivityNew : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var progressBar: ProgressBar

    //        private val streamUrl = "rtsps://wowza2.platform.quboworld.com:443/hero-iot-live/3d3b28e6-7016-4aa6-9bc6-d169549f9195?wowzatoken=Z8A4KyOolVs7O2Oyg3dVtQygatJ1OSh1JDonkXHy4guzJSEKAaEV+k4xk5S8csmsvpmR7ZcUSLYPAI67iP0qqJk/8ix+bMG3T9EHYQAu1ww=&sourceDevice=4495db22-d28b-490c-a1a9-62e2370262e6&sessionId=1d74e71c-b16c-47ba-a47f-4cb4cf8c93fe&userUUID=29ddf8f1-ea4e-4925-9174-c246df62fc5e&unitUUID=2994f55b-9acb-4692-b0fe-d7e5c04a6648&?vodContent=true"
    private val streamUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private var isRecording = false
    private var recordStartMs: Long = 0
    private var recordEndMs: Long = 0

    private lateinit var localVideoFile: File
    private var sslSocketFactory: SSLSocketFactory? = null

    private var ffmpegExecutionId: Long? = null
    private lateinit var tempOutputFile: File

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

        startBtn.setOnClickListener {
            if (streamUrl.startsWith("rtsp")) {
                startRecordingRTSP()
            } else {
                startRecordingHttp()
            }
        }
        stopBtn.setOnClickListener {
            if (streamUrl.startsWith("rtsp")) {
                stopRecordingRTSP()
            } else {
                stopRecordingHTTP()
            }
        }
    }

    private fun requestStoragePermission() {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE_PERMISSION)
        }
    }

    private fun setupExoPlayer() {
        val df: DefaultRenderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this).setRenderersFactory(df).build()
        playerView.player = player
        if (streamUrl.startsWith("rtsp")) {
            rtspMediaItem(streamUrl)
        } else {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun startRecordingHttp() {
        if (isRecording) return
        isRecording = true
        recordStartMs = player.currentPosition
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        // Download video to local file in background
        progressBar.visibility = View.VISIBLE
        thread {
            try {
                val fileName = "downloaded_temp_${System.currentTimeMillis()}_video.mp4"
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

    private fun startRecordingRTSP() {
        if (isRecording) return
        isRecording = true
        recordStartMs = player.currentPosition

        // Create temporary output file
        tempOutputFile = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "temp_rec_${System.currentTimeMillis()}.mp4"
        )

        val ffmpegCommand = arrayOf(
            "-rtsp_transport", "tcp",        // Force TCP transport
//            "-http_ssl_verify", "0",         // Bypass SSL verification
            "-i", streamUrl,
//            "-t", "3600",                   // Maximum record time (1 hour)
            "-c", "copy",                   // Direct stream copy
            "-f", "mp4",
            "-y",                           // Overwrite output file
            tempOutputFile.absolutePath
        )

        progressBar.visibility = View.VISIBLE
        thread {
            ffmpegExecutionId = FFmpeg.executeAsync(ffmpegCommand) { _, returnCode ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (returnCode == Config.RETURN_CODE_SUCCESS) {
                        Log.d("FFmpeg", "Recording saved temporarily")
                    } else {
                        Log.d("FFmpeg", "Recording failed -- Do not trust yet (check for file)")
                    }
                }
            }
        }
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingHTTP() {
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

        val outputPath = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "httpstreamclip_${System.currentTimeMillis()}.mp4").absolutePath
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
                        localVideoFile.delete()
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

    private fun stopRecordingRTSP() {
        if (!isRecording) return
        isRecording = false
        recordEndMs = player.currentPosition

        // Cancel ongoing FFmpeg process
        ffmpegExecutionId?.let { FFmpeg.cancel(it) }

        // Wait for file finalization
        progressBar.visibility = View.VISIBLE
        thread {
            Thread.sleep(1000) // Allow file write completion

            if (!tempOutputFile.exists()) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "No recording found", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            val startSec = recordStartMs / 1000f
            val durationSec = (recordEndMs - recordStartMs) / 1000f

            val finalOutput = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "rtspstreamclip__${System.currentTimeMillis()}.mp4"
            )

            val trimCommand = arrayOf(
                "-ss", startSec.toString(),    // Start position
                "-i", tempOutputFile.absolutePath,
                "-t", durationSec.toString(),  // Duration
                "-c", "copy",                  // No re-encoding
                "-y",
                finalOutput.absolutePath
            )

            val rc = FFmpeg.execute(trimCommand)
            runOnUiThread {
                progressBar.visibility = View.GONE
                when (rc) {
                    Config.RETURN_CODE_SUCCESS -> {
                        tempOutputFile.delete() // Cleanup temp file
                        Toast.makeText(this, "Saved: ${finalOutput.name}", Toast.LENGTH_LONG).show()
                    }

                    else -> Toast.makeText(this, "Trim failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private fun rtspMediaItem(url: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))

        val rtspMediaSource = RtspMediaSource.Factory()
        rtspMediaSource.setDebugLoggingEnabled(true)
        rtspMediaSource.setSocketFactory(getTrustAllSocketFactory())
        rtspMediaSource.setForceUseRtpTcp(true)

        player.setMediaSource(rtspMediaSource.createMediaSource(mediaItem))
        player.prepare()
        player.setPlayWhenReady(true)
    }

    private fun getTrustAllSocketFactory(): SSLSocketFactory {
        if (sslSocketFactory != null) {
            return sslSocketFactory!!
        }
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                }

                override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
                    Log.e("SSL", "⚠️ Trusting all certs. Not recommended for production.")
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf<X509Certificate>()
                }
            }
            )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            sslSocketFactory = sslContext.socketFactory
        } catch (ex: java.lang.Exception) {

        }
        return sslSocketFactory!!
    }
}
