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

package com.google.android.wallpaper.weathereffects.fog

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.annotation.FloatRange
import com.google.android.wallpaper.weathereffects.utils.GraphicsUtils

/** Configuration for a fog effect. */
data class FogEffectConfig(
    /** The main shader of the effect. */
    val shader: RuntimeShader,
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
    /** Pixel density of the display. Used for dithering. */
    val pixelDensity: Float
) {

    companion object {

        /**
         * A convenient way for creating a [FogEffectConfig]. If the client does not want to use
         * this constructor, a [FogEffectConfig] object can still be created a directly.
         *
         * @param assets the application [AssetManager].
         * @param foreground a bitmap containing the foreground of the image.
         * @param background a bitmap containing the background of the image.
         * @param pixelDensity pixel density of the display.
         *
         * @return the [FogEffectConfig] object.
         */
        fun create(
            assets: AssetManager,
            foreground: Bitmap,
            background: Bitmap,
            pixelDensity: Float,
        ): FogEffectConfig {
            return FogEffectConfig(
                shader = GraphicsUtils.loadShader(assets, "shaders/fog_effect.agsl"),
                colorGradingShader = GraphicsUtils.loadShader(
                    assets,
                    "shaders/color_grading_lut.agsl"
                ),
                lut = GraphicsUtils.loadTexture(assets, "textures/lut_rain_and_fog.png"),
                colorGradingIntensity = 0.7f,
                foreground,
                background,
                pixelDensity
            )
        }
    }
}
