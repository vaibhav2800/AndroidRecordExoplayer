package com.vr.androidrecordexoplayer

import android.media.MediaMuxer

object SharedMuxerState {
    lateinit var muxer: MediaMuxer
    var audioTrackIndex = -1
    var videoTrackIndex = -1
    var muxerStarted = false
    val muxerLock = Any()

    fun tryStartMuxer() {
        synchronized(muxerLock) {
            if (audioTrackIndex != -1 && videoTrackIndex != -1 && !muxerStarted) {
                muxer.start()
                muxerStarted = true
            }
        }
    }

}
