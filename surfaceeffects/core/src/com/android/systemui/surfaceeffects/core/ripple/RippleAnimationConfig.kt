/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.surfaceeffects.core.ripple

import android.graphics.Color

/**
 * A struct that holds the ripple animation configurations.
 *
 * <p>This configuration is designed to play a SINGLE animation. Do not reuse or modify the
 * configuration parameters to play different animations, unless the value has to change within the
 * single animation (e.g. Change color or opacity during the animation). Note that this data class
 * is pulled out to make the [RippleAnimation] constructor succinct.
 */
data class RippleAnimationConfig(
    val rippleShape: RippleShader.RippleShape = RippleShader.RippleShape.CIRCLE,
    val duration: Long = 0L,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val maxWidth: Float = 0f,
    val maxHeight: Float = 0f,
    val pixelDensity: Float = 1f,
    var color: Int = Color.WHITE,
    val opacity: Int = RippleShader.RIPPLE_DEFAULT_ALPHA,
    val sparkleStrength: Float = RippleShader.RIPPLE_SPARKLE_STRENGTH,
    // Null means it uses default fade parameter values.
    val baseRingFadeParams: RippleShader.FadeParams? = null,
    val sparkleRingFadeParams: RippleShader.FadeParams? = null,
    val centerFillFadeParams: RippleShader.FadeParams? = null,
    val shouldDistort: Boolean = true,
)
