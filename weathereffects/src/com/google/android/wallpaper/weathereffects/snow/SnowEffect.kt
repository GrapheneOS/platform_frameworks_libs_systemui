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

package com.google.android.wallpaper.weathereffects.snow

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.SizeF
import com.google.android.torus.utils.extensions.getAspectRatio
import com.google.android.wallpaper.weathereffects.WeatherEffect
import kotlin.random.Random

/** Defines and generates the rain weather effect animation. */
class SnowEffect(
    /** The config of the snow effect. */
    private val snowConfig: SnowEffectConfig,
    /** The initial size of the surface where the effect will be shown. */
    surfaceSize: SizeF
) : WeatherEffect {

    private val snowPaint = Paint().also { it.shader = snowConfig.colorGradingShader }
    private var elapsedTime: Float = 0f
    private var imageSize: SizeF = SizeF(
        snowConfig.background.width.toFloat(),
        snowConfig.background.height.toFloat()
    )

    init {
        updateTextureUniforms(snowConfig.blurredBackground)
        adjustCropping(surfaceSize)
        prepareColorGrading()
    }

    override fun resize(newSurfaceSize: SizeF) = adjustCropping(newSurfaceSize)

    override fun update(deltaMillis: Long, frameTimeNanos: Long) {
        elapsedTime += deltaMillis * MILLIS_TO_SECONDS
        snowConfig.shader.setFloatUniform("time", elapsedTime)
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPaint(snowPaint)
    }

    override fun reset() {
        elapsedTime = Random.nextFloat() * 90f
    }

    override fun release() {
        snowConfig.lut?.recycle()
        snowConfig.blurredBackground.recycle()
    }

    private fun adjustCropping(surfaceSize: SizeF) {
        adjustUVs(surfaceSize)
        snowConfig.shader.setFloatUniform("screenSize", surfaceSize.width, surfaceSize.height)
        snowConfig.shader.setFloatUniform("screenAspectRatio", surfaceSize.getAspectRatio())
    }

    private fun updateTextureUniforms(blurredBackground: Bitmap) {
        snowConfig.shader.setInputBuffer(
            "foreground",
            BitmapShader(snowConfig.foreground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        snowConfig.shader.setInputBuffer(
            "background",
            BitmapShader(snowConfig.background, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )

        snowConfig.shader.setInputBuffer(
            "blurredBackground",
            BitmapShader(blurredBackground, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        )
    }

    private fun prepareColorGrading() {
        snowConfig.colorGradingShader.setInputShader("texture", snowConfig.shader)
        snowConfig.lut?.let {
            snowConfig.colorGradingShader.setInputShader(
                "lut",
                BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            )
        }
        snowConfig.colorGradingShader.setFloatUniform(
            "intensity",
            snowConfig.colorGradingIntensity
        )
    }

    private fun adjustUVs(surfaceSize: SizeF) {
        val uvScaleHeight: Float = imageSize.height / surfaceSize.height
        val uvScaleWidth: Float = imageSize.width / surfaceSize.width

        val uvScale = if (imageSize.getAspectRatio() > surfaceSize.getAspectRatio()) {
            uvScaleHeight
        } else {
            uvScaleWidth
        }

        val horizontalOffset = (imageSize.width - surfaceSize.width * uvScale) / 2f
        val verticalOffset = (imageSize.height - surfaceSize.height * uvScale) / 2f

        snowConfig.shader.setFloatUniform("uvOffsets", horizontalOffset, verticalOffset)
        snowConfig.shader.setFloatUniform("uvScale", uvScale)
    }

    private companion object {

        private const val MILLIS_TO_SECONDS = 1 / 1000f
    }
}
