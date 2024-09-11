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

package com.google.android.wallpaper.weathereffects.graphics.fog

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.SizeF
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect.Companion.DEFAULT_INTENSITY
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.google.android.wallpaper.weathereffects.graphics.utils.ImageCrop
import com.google.android.wallpaper.weathereffects.graphics.utils.TimeUtils
import kotlin.math.sin
import kotlin.random.Random

/** Defines and generates the fog weather effect animation. */
class FogEffect(
    private val fogConfig: FogEffectConfig,
    private var foreground: Bitmap,
    private var background: Bitmap,
    private var intensity: Float = DEFAULT_INTENSITY,
    /** The initial size of the surface where the effect will be shown. */
    private var surfaceSize: SizeF
) : WeatherEffect {

    private val fogPaint = Paint().also { it.shader = fogConfig.colorGradingShader }
    private var elapsedTime: Float = 0f

    init {
        updateTextureUniforms()
        adjustCropping(surfaceSize)
        prepareColorGrading()
        updateFogGridSize(surfaceSize)
        setIntensity(intensity)
    }

    override fun resize(newSurfaceSize: SizeF) {
        adjustCropping(newSurfaceSize)
        updateFogGridSize(newSurfaceSize)
        surfaceSize = newSurfaceSize
    }

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        val deltaTime = TimeUtils.millisToSeconds(deltaMillis)

        val time = TimeUtils.nanosToSeconds(frameTimeNanos)
        // Variation range [0.4, 1]. We don't want the variation to be 0.
        val variation = sin(0.06f * time + sin(0.18f * time)) * 0.3f + 0.7f
        elapsedTime += variation * deltaTime

        val scaledElapsedTime = elapsedTime * 0.248f

        val variationFgd0 = 0.256f * sin(scaledElapsedTime)
        val variationFgd1 = 0.156f * sin(scaledElapsedTime) * sin(scaledElapsedTime)
        val timeFgd0 = 0.4f * elapsedTime * 5f + variationFgd0
        val timeFgd1 = 0.1f * elapsedTime * 5f + variationFgd1

        val variationBgd0 = 0.156f * sin((scaledElapsedTime + Math.PI.toFloat() / 2.0f))
        val variationBgd1 =
            0.0156f * sin((scaledElapsedTime + Math.PI.toFloat() / 3.0f)) * sin(scaledElapsedTime)
        val timeBgd0 = 0.8f * elapsedTime * 5f + variationBgd0
        val timeBgd1 = 0.2f * elapsedTime * 5f + variationBgd1

        fogConfig.shader.setFloatUniform("time", timeFgd0, timeFgd1, timeBgd0, timeBgd1)

        fogConfig.colorGradingShader.setInputShader("texture", fogConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(fogPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        fogConfig.lut?.recycle()
    }

    override fun setIntensity(intensity: Float) {
        fogConfig.shader.setFloatUniform("intensity", intensity)
        fogConfig.colorGradingShader.setFloatUniform(
            "intensity",
            fogConfig.colorGradingIntensity * intensity
        )
    }

    override fun setBitmaps(foreground: Bitmap, background: Bitmap) {
        this.foreground = foreground
        this.background = background
        fogConfig.shader.setInputBuffer(
            "background",
            BitmapShader(background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
        fogConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
        adjustCropping(surfaceSize)
    }

    private fun adjustCropping(surfaceSize: SizeF) {
        val imageCropFgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                foreground.width.toFloat(),
                foreground.height.toFloat()
            )
        fogConfig.shader.setFloatUniform(
            "uvOffsetFgd",
            imageCropFgd.leftOffset,
            imageCropFgd.topOffset
        )
        fogConfig.shader.setFloatUniform(
            "uvScaleFgd",
            imageCropFgd.horizontalScale,
            imageCropFgd.verticalScale
        )
        val imageCropBgd =
            ImageCrop.centerCoverCrop(
                surfaceSize.width,
                surfaceSize.height,
                background.width.toFloat(),
                background.height.toFloat()
            )
        fogConfig.shader.setFloatUniform(
            "uvOffsetBgd",
            imageCropBgd.leftOffset,
            imageCropBgd.topOffset
        )
        fogConfig.shader.setFloatUniform(
            "uvScaleBgd",
            imageCropBgd.horizontalScale,
            imageCropBgd.verticalScale
        )
        fogConfig.shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        fogConfig.shader.setFloatUniform(
            "screenAspectRatio",
            GraphicsUtils.getAspectRatio(surfaceSize)
        )
    }

    private fun updateTextureUniforms() {
        fogConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        fogConfig.shader.setInputBuffer(
            "background",
            BitmapShader(background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        fogConfig.shader.setInputBuffer(
            "clouds",
            BitmapShader(fogConfig.cloudsTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        )

        fogConfig.shader.setInputBuffer(
            "fog",
            BitmapShader(fogConfig.fogTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        )

        fogConfig.shader.setFloatUniform("pixelDensity", fogConfig.pixelDensity)
    }

    private fun prepareColorGrading() {
        fogConfig.colorGradingShader.setInputShader("texture", fogConfig.shader)
        fogConfig.lut?.let {
            fogConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
        fogConfig.colorGradingShader.setFloatUniform("intensity", fogConfig.colorGradingIntensity)
    }

    private fun updateFogGridSize(surfaceSize: SizeF) {
        val widthScreenScale =
            GraphicsUtils.computeDefaultGridSize(surfaceSize, fogConfig.pixelDensity)
        fogConfig.shader.setFloatUniform(
            "cloudsSize",
            widthScreenScale * fogConfig.cloudsTexture.width.toFloat(),
            widthScreenScale * fogConfig.cloudsTexture.height.toFloat()
        )

        fogConfig.shader.setFloatUniform(
            "fogSize",
            widthScreenScale * fogConfig.fogTexture.width.toFloat(),
            widthScreenScale * fogConfig.fogTexture.height.toFloat()
        )
    }
}
