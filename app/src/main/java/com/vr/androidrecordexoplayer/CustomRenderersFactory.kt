package com.vr.androidrecordexoplayer

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

@OptIn(UnstableApi::class)
class CustomRenderersFactory(
    context: Context,
    private val audioProcessor: RecordingAudioProcessor
) : DefaultRenderersFactory(context) {

    private val TAG = "CustomRenderersFactory"

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        val interimAudioSink: AudioSink = DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf(audioProcessor))
            .build()

        val audioRenderer = MediaCodecAudioRenderer(
            context,
            codecAdapterFactory,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            interimAudioSink
        )
        out.add(audioRenderer)

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return
        }
        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.midi.MidiRenderer")
            val constructor = clazz.getConstructor(
                Context::class.java,
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(context, eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded MidiRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating MIDI extension", e)
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.opus.LibopusAudioRenderer")
            val constructor = clazz.getConstructor(
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded LibopusAudioRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating Opus extension", e)
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.flac.LibflacAudioRenderer")
            val constructor = clazz.getConstructor(
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded LibflacAudioRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating FLAC extension", e)
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
            val constructor = clazz.getConstructor(
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded FfmpegAudioRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating FFmpeg extension", e)
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.iamf.LibiamfAudioRenderer")
            val constructor = clazz.getConstructor(
                Context::class.java,
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(context, eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded LibiamfAudioRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating IAMF extension", e)
        }

        try {
            val clazz = Class.forName("androidx.media3.decoder.mpegh.MpeghAudioRenderer")
            val constructor = clazz.getConstructor(
                Handler::class.java,
                AudioRendererEventListener::class.java,
                AudioSink::class.java
            )
            val renderer =
                constructor.newInstance(eventHandler, eventListener, audioSink) as Renderer
            out.add(extensionRendererIndex++, renderer)
            Log.i(TAG, "Loaded MpeghAudioRenderer.")
        } catch (e: ClassNotFoundException) {
            // Expected if the app was built without the extension.
        } catch (e: Exception) {
            throw IllegalStateException("Error instantiating MPEG-H extension", e)
        }
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        val videoRenderer = MediaCodecVideoRenderer(
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
        )
        out.add(videoRenderer)

        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}