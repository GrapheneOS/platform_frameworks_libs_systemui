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

package com.google.android.wallpaper.weathereffects.rain

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.SizeF
import com.google.android.torus.utils.extensions.getAspectRatio
import com.google.android.wallpaper.weathereffects.WeatherEffect
import com.google.android.wallpaper.weathereffects.utils.ImageCrop
import kotlin.random.Random

/** Defines and generates the rain weather effect animation. */
class RainEffect(
    /** The config of the rain effect. */
    private val rainConfig: RainEffectConfig,
    /** The initial size of the surface where the effect will be shown. */
    surfaceSize: SizeF
) : WeatherEffect {

    private val rainPaint = Paint().also { it.shader = rainConfig.colorGradingShader }
    private var elapsedTime: Float = 0f

    init {
        updateTextureUniforms()
        adjustCropping(surfaceSize)
        prepareColorGrading()
    }

    override fun resize(newSurfaceSize: SizeF) = adjustCropping(newSurfaceSize)

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += deltaMillis * MILLIS_TO_SECONDS
        rainConfig.shader.setFloatUniform("time", elapsedTime)
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(rainPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        rainConfig.lut?.recycle()
        rainConfig.blurredBackground.recycle()
    }

    private fun adjustCropping(surfaceSize: SizeF) {
        val imageCropFgd = ImageCrop.centerCoverCrop(
            surfaceSize.width,
            surfaceSize.height,
            rainConfig.foreground.width.toFloat(),
            rainConfig.foreground.height.toFloat()
        )
        rainConfig.shader.setFloatUniform(
            "uvOffsetFgd",
            imageCropFgd.leftOffset,
            imageCropFgd.topOffset
        )
        rainConfig.shader.setFloatUniform(
            "uvScaleFgd",
            imageCropFgd.horizontalScale,
            imageCropFgd.verticalScale
        )
        val imageCropBgd = ImageCrop.centerCoverCrop(
            surfaceSize.width,
            surfaceSize.height,
            rainConfig.background.width.toFloat(),
            rainConfig.background.height.toFloat()
        )
        rainConfig.shader.setFloatUniform(
            "uvOffsetBgd",
            imageCropBgd.leftOffset,
            imageCropBgd.topOffset
        )
        rainConfig.shader.setFloatUniform(
            "uvScaleBgd",
            imageCropBgd.horizontalScale,
            imageCropBgd.verticalScale
        )
        rainConfig.shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        rainConfig.shader.setFloatUniform("screenAspectRatio", surfaceSize.getAspectRatio())
    }

    private fun updateTextureUniforms() {
        rainConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(rainConfig.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        rainConfig.shader.setInputBuffer(
            "background",
            BitmapShader(rainConfig.background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        rainConfig.shader.setInputBuffer(
            "blurredBackground",
            BitmapShader(
                rainConfig.blurredBackground,
                Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR
            )
        )
    }

    private fun prepareColorGrading() {
        rainConfig.colorGradingShader.setInputShader("texture", rainConfig.shader)
        rainConfig.lut?.let {
            rainConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
        rainConfig.colorGradingShader.setFloatUniform(
            "intensity",
            rainConfig.colorGradingIntensity
        )
    }

    private companion object {

        private const val MILLIS_TO_SECONDS = 1 / 1000f
    }
}
