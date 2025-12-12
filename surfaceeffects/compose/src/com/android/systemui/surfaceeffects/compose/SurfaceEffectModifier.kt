/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.surfaceeffects.compose

import androidx.compose.ui.Modifier
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.DwellEffectConfig
import com.android.systemui.surfaceeffects.core.ripple.RippleAnimationConfig
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseShader

/**
 * Applies the base Simplex Noise effect to the modified Composable.
 *
 * @param shaderConfig The configuration defining the noise movement, color, and grid size.
 * @param isEnabled A boolean to enable (fade-in) or disable (fade-out) the effect.
 */
fun Modifier.simplexNoiseEffect(
    shaderConfig: TurbulenceNoiseAnimationConfig,
    isEnabled: Boolean = true,
): Modifier {
    return this.turbulenceNoiseImpl(
        TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE,
        shaderConfig = shaderConfig,
        isEnabled = isEnabled,
    )
}

/**
 * Applies the Simple Simplex Noise effect, typically rendered as an overlay.
 *
 * @param shaderConfig The configuration defining the noise movement, color, and grid size.
 * @param isEnabled A boolean to enable (fade-in) or disable (fade-out) the effect.
 */
fun Modifier.simpleSimplexNoiseEffect(
    shaderConfig: TurbulenceNoiseAnimationConfig,
    isEnabled: Boolean = true,
): Modifier {
    return this.turbulenceNoiseImpl(
        TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_SIMPLE,
        shaderConfig = shaderConfig,
        isEnabled = isEnabled,
    )
}

/**
 * Applies the Simplex Noise effect with a sparkle pattern.
 *
 * @param shaderConfig The configuration defining the noise movement, color, and grid size.
 * @param isEnabled A boolean to enable (fade-in) or disable (fade-out) the effect.
 */
fun Modifier.sparkleNoiseEffect(
    shaderConfig: TurbulenceNoiseAnimationConfig,
    isEnabled: Boolean = true,
): Modifier {
    return this.turbulenceNoiseImpl(
        TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_SPARKLE,
        shaderConfig = shaderConfig,
        isEnabled = isEnabled,
    )
}

/**
 * Applies the Fractal Simplex Noise effect, often used for complex, turbulent textures.
 *
 * @param shaderConfig The configuration defining the noise movement, color, and grid size. Defaults
 *   to a standard configuration.
 * @param isEnabled A boolean to enable (fade-in) or disable (fade-out) the effect.
 */
fun Modifier.fractalNoiseEffect(
    shaderConfig: TurbulenceNoiseAnimationConfig = TurbulenceNoiseAnimationConfig(),
    isEnabled: Boolean = true,
): Modifier {
    return this.turbulenceNoiseImpl(
        TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_FRACTAL,
        shaderConfig = shaderConfig,
        isEnabled = isEnabled,
    )
}

/**
 * Applies the Ripple Circle shader effect to the modified Composable.
 *
 * @param shaderConfig The configuration ([RippleAnimationConfig]) defining the size, fade, and
 *   color of the ripple.
 * @param triggerKey An integer key that, when changed, restarts the ripple animation from the
 *   beginning. Defaults to 0.
 */
fun Modifier.rippleCircleEffect(
    shaderConfig: RippleAnimationConfig,
    triggerKey: Int = 0,
): Modifier {
    return rippleEffectImpl(shaderConfig = shaderConfig, triggerKey = triggerKey)
}

/**
 * Applies the Ripple Rounded Box shader effect to the modified Composable.
 *
 * @param shaderConfig The configuration ([RippleAnimationConfig]) defining the size, fade, and
 *   color of the ripple.
 * @param triggerKey An integer key that, when changed, restarts the ripple animation from the
 *   beginning. Defaults to 0.
 */
fun Modifier.rippleRoundedBoxEffect(
    shaderConfig: RippleAnimationConfig,
    triggerKey: Int = 0,
): Modifier {
    return rippleEffectImpl(shaderConfig = shaderConfig, triggerKey = triggerKey)
}

/**
 * Applies the Ripple Ellipse shader effect to the modified Composable.
 *
 * @param shaderConfig The configuration ([RippleAnimationConfig]) defining the size, fade, and
 *   color of the ripple.
 * @param triggerKey An integer key that, when changed, restarts the ripple animation from the
 *   beginning. Defaults to 0.
 */
fun Modifier.rippleEllipseEffect(
    shaderConfig: RippleAnimationConfig,
    triggerKey: Int = 0,
): Modifier {
    return rippleEffectImpl(shaderConfig = shaderConfig, triggerKey = triggerKey)
}

/**
 * Add dwell ripple effect
 *
 * @param isExpanding True means expanding, false means retracting.
 * @param dwellEffectConfig The configuration ([DwellEffectConfig]) defining the radius, color of
 *   the dwell ripple.
 */
fun Modifier.dwellRippleEffect(
    isExpanding: Boolean,
    dwellEffectConfig: DwellEffectConfig,
): Modifier {
    return dwellEffectImpl(dwellEffectConfig, isExpanding)
}
