package com.vr.androidrecordexoplayer.opengl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20

class TextureRender {
    private val vertexShader = """
attribute vec4 aPosition;
attribute vec2 aTextureCoord;
varying vec2 vTextureCoord;
void main() {
    gl_Position = aPosition;
    vTextureCoord = aTextureCoord;
}
"""
    private val fragmentShader = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
    gl_FragColor = texture2D(sTexture, vTextureCoord);
}
"""

    private val triangleVerticesData = floatArrayOf(
        // X, Y,    U, V
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )

    private var program = 0
    private var textureID = 0
    private var aPosition = 0
    private var aTextureCoord = 0
    private var uTexture = 0
    private var vertexBuffer = GlUtil.createFloatBuffer(triangleVerticesData)

    fun surfaceCreated() {
        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexture = GLES20.glGetUniformLocation(program, "sTexture")
        textureID = GlUtil.createOESTexture()
    }

    fun getTextureId(): Int = textureID

    fun drawFrame(surfaceTexture: SurfaceTexture) {
        GlUtil.checkGlError("onDrawFrame start")
        surfaceTexture.getTransformMatrix(GlUtil.ST_MATRIX)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID)
        GLES20.glUniform1i(uTexture, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPosition)

        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aTextureCoord)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTextureCoord)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GlUtil.checkGlError("onDrawFrame end")
    }
}
