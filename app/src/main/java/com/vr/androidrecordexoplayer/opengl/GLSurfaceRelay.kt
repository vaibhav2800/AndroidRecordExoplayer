// GLSurfaceRelay.kt
package com.vr.androidrecordexoplayer

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import com.vr.androidrecordexoplayer.opengl.TextureRender
import java.util.concurrent.atomic.AtomicBoolean

class GLSurfaceRelay(
    private val encoderSurface: Surface,
    private val previewSurface: Surface
) : SurfaceTexture.OnFrameAvailableListener {

    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var relaySurface: Surface
    private val frameAvailable = AtomicBoolean(false)
    private var running = false
    private var glThread: Thread? = null
    private lateinit var textureRender: TextureRender

    fun getSurface(): Surface = relaySurface

    fun start() {
        running = true
        glThread = Thread { glLoop() }
        glThread?.start()
    }

    fun stop() {
        running = false
        surfaceTexture.release()
        relaySurface.release()
    }

    private fun glLoop() {
        // EGL setup
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(eglDisplay, null, 0, null, 0)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0]

        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, attrib_list, 0)

        // Create EGL surfaces for encoder and preview
        val eglEncoderSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
        val eglPreviewSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, previewSurface, intArrayOf(EGL14.EGL_NONE), 0)

        // Make EGL context current on one of the surfaces (e.g. preview)
        EGL14.eglMakeCurrent(eglDisplay, eglPreviewSurface, eglPreviewSurface, eglContext)

        // Now it is safe to call GLES20 functions (including shader compilation)
        textureRender = TextureRender()
        textureRender.surfaceCreated()
        surfaceTexture = SurfaceTexture(textureRender.getTextureId())
        relaySurface = Surface(surfaceTexture)
        surfaceTexture.setOnFrameAvailableListener(this)

        while (running) {
            if (frameAvailable.getAndSet(false)) {
                surfaceTexture.updateTexImage()

                // Draw to preview
                EGL14.eglMakeCurrent(eglDisplay, eglPreviewSurface, eglPreviewSurface, eglContext)
                textureRender.drawFrame(surfaceTexture)
                EGL14.eglSwapBuffers(eglDisplay, eglPreviewSurface)

                // Draw to encoder
                EGL14.eglMakeCurrent(eglDisplay, eglEncoderSurface, eglEncoderSurface, eglContext)
                textureRender.drawFrame(surfaceTexture)
                EGL14.eglSwapBuffers(eglDisplay, eglEncoderSurface)
            }
            Thread.sleep(10)
        }

        EGL14.eglDestroySurface(eglDisplay, eglEncoderSurface)
        EGL14.eglDestroySurface(eglDisplay, eglPreviewSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable.set(true)
    }
}
