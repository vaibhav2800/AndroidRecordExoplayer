package com.vr.androidrecordexoplayer

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
class PCMExtractorProcessor : BaseAudioProcessor() {
    var onPcmData: ((ByteBuffer, Int, Long) -> Unit)? = null


    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        // Pass through the same format
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val presentationTimeUs = System.nanoTime() / 1000
        val bufferCopy = ByteBuffer.allocateDirect(inputBuffer.remaining())
        bufferCopy.put(inputBuffer)
        bufferCopy.flip()

        onPcmData?.invoke(bufferCopy, bufferCopy.remaining(), presentationTimeUs)
//        super.queueInput(bufferCopy)
    }
}
