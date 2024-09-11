/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.wallpaper.weathereffects.graphics.sun

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.SizeF
import com.google.android.wallpaper.weathereffects.graphics.WeatherEffect
import com.google.android.wallpaper.weathereffects.graphics.utils.GraphicsUtils
import com.google.android.wallpaper.weathereffects.graphics.utils.ImageCrop
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Defines and generates the sunny weather animation. */
class SunEffect(
    /** The config of the sunny effect. */
    private val sunConfig: SunEffectConfig,
    private var foreground: Bitmap,
    private var background: Bitmap,
    private var intensity: Float = WeatherEffect.DEFAULT_INTENSITY,
    /** The initial size of the surface where the effect will be shown. */
    var surfaceSize: SizeF
) : WeatherEffect {

    private val sunnyPaint = Paint().also { it.shader = sunConfig.colorGradingShader }
    private var elapsedTime: Float = 0f

    init {
        updateTextureUniforms()
        adjustCropping(surfaceSize)
        prepareColorGrading()
        setIntensity(intensity)
    }

    override fun resize(newSurfaceSize: SizeF) {
        adjustCropping(newSurfaceSize)
        surfaceSize = newSurfaceSize
    }

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += TimeUnit.MILLISECONDS.toSeconds(deltaMillis)
        sunConfig.shader.setFloatUniform("time", elapsedTime)
        sunConfig.colorGradingShader.setInputShader("texture", sunConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(sunnyPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        sunConfig.lut?.recycle()
    }

    override fun setIntensity(intensity: Float) {
        sunConfig.shader.setFloatUniform("intensity", intensity)
        sunConfig.colorGradingShader.setFloatUniform(
            "intensity",
            sunConfig.colorGradingIntensity * intensity
        )
    }

    override fun setBitmaps(foreground: Bitmap, background: Bitmap) {
        this.foreground = foreground
        this.background = background
        sunConfig.shader.setInputBuffer(
            "background",
            BitmapShader(background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
        sunConfig.shader.setInputBuffer(
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
        sunConfig.shader.setFloatUniform(
            "uvOffsetFgd",
            imageCropFgd.leftOffset,
            imageCropFgd.topOffset
        )
        sunConfig.shader.setFloatUniform(
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
        sunConfig.shader.setFloatUniform(
            "uvOffsetBgd",
            imageCropBgd.leftOffset,
            imageCropBgd.topOffset
        )
        sunConfig.shader.setFloatUniform(
            "uvScaleBgd",
            imageCropBgd.horizontalScale,
            imageCropBgd.verticalScale
        )
        sunConfig.shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        sunConfig.shader.setFloatUniform(
            "screenAspectRatio",
            GraphicsUtils.getAspectRatio(surfaceSize)
        )
    }

    private fun updateTextureUniforms() {
        sunConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        sunConfig.shader.setInputBuffer(
            "background",
            BitmapShader(background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
    }

    private fun prepareColorGrading() {
        sunConfig.colorGradingShader.setInputShader("texture", sunConfig.shader)
        sunConfig.lut?.let {
            sunConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
        sunConfig.colorGradingShader.setFloatUniform("intensity", sunConfig.colorGradingIntensity)
    }
}
