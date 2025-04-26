import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

class AudioRecorder(private val outputFilePath: String) {

    private lateinit var mediaMuxer: MediaMuxer
    private lateinit var audioEncoder: MediaCodec
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private val sampleRate = 44100
    private val channels = 2 // Stereo
    private val bitRate = 128000

    init {
        // Ensure the output file path is valid and delete if it exists
        val file = File(outputFilePath)
        if (file.exists()) file.delete()
    }

    fun start() {
        // Initialize AudioEncoder and MediaMuxer
        setupAudioEncoder()
        mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Ensure file is ready
//        if (!mediaMuxer.isStarted) {
//        }
        mediaMuxer.start()
    }

    private fun setupAudioEncoder() {
        // Set up Audio Format
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        // Create the encoder
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()
    }

    fun queuePcmData(pcmBuffer: ByteBuffer, size: Int, presentationTimeUs: Long) {
        val inputIndex = audioEncoder.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(pcmBuffer)
            audioEncoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
        }
    }

    fun stop() {
        try {
            // Signal the end of input stream
            audioEncoder.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error signaling end of input stream: ${e.message}")
        }

        // Release resources
        Thread {
            try {
                // Wait for final data to be written
                Thread.sleep(1000)

                synchronized(muxerLock) {
                    if (muxerStarted) {
                        mediaMuxer.stop()
                        mediaMuxer.release()
                        muxerStarted = false
                    }
                }

                // Release the encoder
                audioEncoder.stop()
                audioEncoder.release()

                Log.d("AudioRecorder", "Audio recording stopped and resources released.")

            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error stopping recording: ${e.message}")
            }
        }.start()
    }

    private fun startEncoderLoop() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val encodedData = audioEncoder.getOutputBuffer(outputIndex) ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        audioEncoder.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        // Write the audio data to the muxer
                        synchronized(muxerLock) {
                            if (audioTrackIndex >= 0) {
                                mediaMuxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                            }
                        }
                    }

                    audioEncoder.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = audioEncoder.outputFormat
                    synchronized(muxerLock) {
                        audioTrackIndex = mediaMuxer.addTrack(newFormat)
                        if (!muxerStarted && audioTrackIndex != -1) {
                            mediaMuxer.start()
                            muxerStarted = true
                        }
                    }
                }
            }
        }.start()
    }
}
