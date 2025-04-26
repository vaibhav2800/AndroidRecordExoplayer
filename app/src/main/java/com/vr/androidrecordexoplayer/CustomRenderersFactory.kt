package com.vr.androidrecordexoplayer

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.os.Handler
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.Renderer

@OptIn(UnstableApi::class)
class CustomRenderersFactory(
    context: Context,
    private val audioProcessor: AudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: java.util.ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )

        val audioSink: AudioSink = DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf(audioProcessor))
            .build()

        out.add(
            MediaCodecAudioRenderer(
                context,
                mediaCodecSelector,
                eventHandler,
                eventListener,
                audioSink
            )
        )
    }

}