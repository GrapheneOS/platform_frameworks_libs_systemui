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

import androidx.annotation.FloatRange
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import com.google.android.wallpaper.weathereffects.utils.GraphicsUtils

/** Configuration for a snow effect. */
data class SnowEffectConfig(
    /** The main shader of the effect. */
    val shader: RuntimeShader,
    /** The shader of accumulated snow effect. */
    val accumulatedSnowShader: RuntimeShader,
    /** The color grading shader. */
    val colorGradingShader: RuntimeShader,
    /** The main lut (color grading) for the effect. */
    val lut: Bitmap?,
    /** The intensity of the color grading. 0: no color grading, 1: color grading in full effect. */
    @FloatRange(from = 0.0, to = 1.0)
    val colorGradingIntensity: Float,
    /** A bitmap containing the foreground of the image. */
    val foreground: Bitmap,
    /** A bitmap containing the background of the image. */
    val background: Bitmap,
    /** A bitmap containing the blurred background. */
    val blurredBackground: Bitmap
) {

    companion object {

        /**
         * A convenient way for creating a [SnowEffectConfig]. If the client does not want to use
         * this constructor, a [SnowEffectConfig] object can still be created a directly.
         *
         * @param context the application context.
         * @param foreground a bitmap containing the foreground of the image.
         * @param background a bitmap containing the background of the image.
         *
         * @return the [SnowEffectConfig] object.
         */
        fun create(context: Context, foreground: Bitmap, background: Bitmap): SnowEffectConfig {
            return SnowEffectConfig(
                shader = GraphicsUtils.loadShader(context.assets, "shaders/snow_effect.agsl"),
                accumulatedSnowShader = GraphicsUtils.loadShader(
                    context.assets, "shaders/snow_accumulation.agsl"
                ),
                colorGradingShader = GraphicsUtils.loadShader(
                    context.assets,
                    "shaders/color_grading_lut.agsl"
                ),
                lut = GraphicsUtils.loadTexture(context.assets, "textures/lut_rain_and_fog.png"),
                colorGradingIntensity = 0.7f,
                foreground,
                background,
                GraphicsUtils.blurImage(context, background, 20f)
            )
        }
    }
}
