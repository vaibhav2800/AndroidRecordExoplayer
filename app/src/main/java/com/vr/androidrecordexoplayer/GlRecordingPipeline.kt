package com.vr.androidrecordexoplayer

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

/**
 * Renders ExoPlayer's decoded video to BOTH the on-screen display surface (always) and a
 * MediaCodec encoder input surface (only while recording), using one shared EGL context.
 *
 * ExoPlayer is pointed at [inputSurface], which is backed by a [SurfaceTexture] bound to an
 * external OES texture. Every decoded frame is drawn to the display so playback stays live,
 * and additionally to the encoder surface whenever [startEncoder] is active. This lets the
 * preview keep running while Start/Stop simply toggle whether frames are also recorded.
 *
 * All GL work happens on a single dedicated thread.
 */
class GlRecordingPipeline(
    private val displaySurface: Surface,
    private var videoWidth: Int,
    private var videoHeight: Int
) {
    companion object {
        private const val TAG = "GlRecordingPipeline"

        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        // Full-screen quad as a triangle strip: (x, y, u, v) per vertex.
        private val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }

    private val thread = HandlerThread("GlRecordingPipeline").apply { start() }
    private val handler = Handler(thread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    private var displayEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var textureId = 0
    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexMatrixLoc = 0

    private lateinit var surfaceTexture: SurfaceTexture
    lateinit var inputSurface: Surface
        private set

    @Volatile private var recording = false
    private val txMatrix = FloatArray(16)
    private val quadBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    /** Blocks until the GL context and [inputSurface] are ready. Call once before playback. */
    fun init() {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                initEgl()
                initGl()
                surfaceTexture = SurfaceTexture(textureId).apply {
                    setDefaultBufferSize(videoWidth, videoHeight)
                    setOnFrameAvailableListener({ handler.post { drawFrame() } }, handler)
                }
                inputSurface = Surface(surfaceTexture)
            } catch (e: Exception) {
                Log.e(TAG, "init failed: ${e.message}", e)
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    /** Update the source resolution once ExoPlayer reports it (improves texture sharpness). */
    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        handler.post {
            videoWidth = width
            videoHeight = height
            if (::surfaceTexture.isInitialized) surfaceTexture.setDefaultBufferSize(width, height)
        }
    }

    /** Begin also drawing frames to [encoderInputSurface]. */
    fun startEncoder(encoderInputSurface: Surface) {
        handler.post {
            encoderEglSurface = createWindowSurface(encoderInputSurface)
            recording = true
            Log.d(TAG, "Encoder output attached")
        }
    }

    /** Stop drawing to the encoder surface and release its EGL surface. */
    fun stopEncoder() {
        handler.post {
            recording = false
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
            }
            Log.d(TAG, "Encoder output detached")
        }
    }

    /** Blocks until all GL resources are torn down, so the caller may then release surfaces. */
    fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                recording = false
                if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, encoderEglSurface)
                    encoderEglSurface = EGL14.EGL_NO_SURFACE
                }
                if (::surfaceTexture.isInitialized) surfaceTexture.release()
                if (::inputSurface.isInitialized) inputSurface.release()
                if (program != 0) GLES20.glDeleteProgram(program)
                if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, displayEglSurface)
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (e: Exception) {
                Log.e(TAG, "release error: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        thread.quitSafely()
        latch.await()
    }

    private fun drawFrame() {
        try {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(txMatrix)
            val timestampNs = surfaceTexture.timestamp

            // Always render to the on-screen surface so the preview stays live.
            makeCurrent(displayEglSurface)
            renderTexture(querySurfaceWidth(displayEglSurface), querySurfaceHeight(displayEglSurface))
            EGL14.eglSwapBuffers(eglDisplay, displayEglSurface)

            // Additionally render to the encoder surface while recording.
            if (recording && encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                makeCurrent(encoderEglSurface)
                renderTexture(videoWidth, videoHeight)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, encoderEglSurface, timestampNs)
                EGL14.eglSwapBuffers(eglDisplay, encoderEglSurface)
            }
        } catch (e: Exception) {
            Log.e(TAG, "drawFrame error: ${e.message}")
        }
    }

    private fun renderTexture(viewportW: Int, viewportH: Int) {
        GLES20.glViewport(0, 0, viewportW, viewportH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, txMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    // ---- EGL / GL setup ----

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )

        displayEglSurface = createWindowSurface(displaySurface)
        makeCurrent(displayEglSurface)
    }

    private fun initGl() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
    }

    private fun makeCurrent(eglSurface: EGLSurface) {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun querySurfaceWidth(eglSurface: EGLSurface): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, value, 0)
        return value[0]
    }

    private fun querySurfaceHeight(eglSurface: EGLSurface): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, value, 0)
        return value[0]
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("Program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $log")
        }
        return shader
    }
}