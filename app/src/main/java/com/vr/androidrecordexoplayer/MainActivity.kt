package com.vr.androidrecordexoplayer

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vr.androidrecordexoplayer.databinding.ActivityMainBinding


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

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
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
}