/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.wallpaper.weathereffects.graphics.rain

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.SizeF
import com.google.android.wallpaper.weathereffects.graphics.FrameBuffer
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.google.android.wallpaper.weathereffects.graphics.utils.ImageCrop
import com.google.android.wallpaper.weathereffects.graphics.utils.SolidColorShader
import java.util.concurrent.Executor
import kotlin.random.Random

/** Defines and generates the rain weather effect animation. */
class RainEffect(
    /** The config of the rain effect. */
    private val rainConfig: RainEffectConfig,
    /** The initial size of the surface where the effect will be shown. */
    surfaceSize: SizeF,
    private val mainExecutor: Executor
) : WeatherEffect {

    private val rainPaint = Paint().also { it.shader = rainConfig.colorGradingShader }
    // Set blur effect to reduce the outline noise. No need to set blur effect every time we
    // re-generate the outline buffer.
    private val outlineBuffer =
        FrameBuffer(rainConfig.background.width, rainConfig.background.height).apply {
            setRenderEffect(RenderEffect.createBlurEffect(2f, 2f, Shader.TileMode.CLAMP))
        }
    private val outlineBufferPaint = Paint().also { it.shader = rainConfig.outlineShader }

    private var elapsedTime: Float = 0f

    init {
        updateTextureUniforms()
        adjustCropping(surfaceSize)
        prepareColorGrading()
        setIntensity(rainConfig.intensity)
    }

    override fun resize(newSurfaceSize: SizeF) = adjustCropping(newSurfaceSize)

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += deltaMillis * MILLIS_TO_SECONDS

        rainConfig.rainShowerShader.setFloatUniform("time", elapsedTime)
        rainConfig.glassRainShader.setFloatUniform("time", elapsedTime * 0.7f)

        rainConfig.glassRainShader.setInputShader("texture", rainConfig.rainShowerShader)
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.glassRainShader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(rainPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        rainConfig.lut?.recycle()
        outlineBuffer.close()
    }

    override fun setIntensity(intensity: Float) {
        rainConfig.rainShowerShader.setFloatUniform("intensity", intensity)
        rainConfig.glassRainShader.setFloatUniform("intensity", intensity * 0.6f)
        rainConfig.colorGradingShader.setFloatUniform(
            "intensity",
            rainConfig.colorGradingIntensity * intensity
        )
        val thickness = 1f + intensity * 10f
        rainConfig.outlineShader.setFloatUniform("thickness", thickness)

        // Need to recreate the outline buffer as the uniform has changed.
        createOutlineBuffer()
    }

    private fun adjustCropping(surfaceSize: SizeF) {
        val imageCropFgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                rainConfig.foreground.width.toFloat(),
                rainConfig.foreground.height.toFloat()
            )
        rainConfig.rainShowerShader.setFloatUniform(
            "uvOffsetFgd",
            imageCropFgd.leftOffset,
            imageCropFgd.topOffset
        )
        rainConfig.rainShowerShader.setFloatUniform(
            "uvScaleFgd",
            imageCropFgd.horizontalScale,
            imageCropFgd.verticalScale
        )

        val imageCropBgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                rainConfig.background.width.toFloat(),
                rainConfig.background.height.toFloat()
            )
        rainConfig.rainShowerShader.setFloatUniform(
            "uvOffsetBgd",
            imageCropBgd.leftOffset,
            imageCropBgd.topOffset
        )
        rainConfig.rainShowerShader.setFloatUniform(
            "uvScaleBgd",
            imageCropBgd.horizontalScale,
            imageCropBgd.verticalScale
        )

        rainConfig.rainShowerShader.setFloatUniform(
            "screenSize",
            surfaceSize.width,
            surfaceSize.height
        )
        rainConfig.glassRainShader.setFloatUniform(
            "screenSize",
            surfaceSize.width,
            surfaceSize.height
        )

        val screenAspectRatio = GraphicsUtils.getAspectRatio(surfaceSize)
        rainConfig.rainShowerShader.setFloatUniform("screenAspectRatio", screenAspectRatio)
        rainConfig.glassRainShader.setFloatUniform("screenAspectRatio", screenAspectRatio)
    }

    private fun updateTextureUniforms() {
        val foregroundBuffer =
            BitmapShader(rainConfig.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        rainConfig.rainShowerShader.setInputBuffer("foreground", foregroundBuffer)
        rainConfig.outlineShader.setInputBuffer("texture", foregroundBuffer)

        rainConfig.rainShowerShader.setInputBuffer(
            "background",
            BitmapShader(rainConfig.background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
    }

    private fun createOutlineBuffer() {
        val canvas = outlineBuffer.beginDrawing()
        canvas.drawPaint(outlineBufferPaint)
        outlineBuffer.endDrawing()

        outlineBuffer.tryObtainingImage(
            { buffer ->
                rainConfig.rainShowerShader.setInputBuffer(
                    "outlineBuffer",
                    BitmapShader(buffer, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                )
            },
            mainExecutor
        )
    }

    private fun prepareColorGrading() {
        // Initialize the buffer with black, so that we don't ever draw garbage buffer.
        rainConfig.glassRainShader.setInputShader("texture", SolidColorShader(Color.BLACK))
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.glassRainShader)
        rainConfig.lut?.let {
            rainConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
    }

    private companion object {
        private const val MILLIS_TO_SECONDS = 1 / 1000f
    }
}
