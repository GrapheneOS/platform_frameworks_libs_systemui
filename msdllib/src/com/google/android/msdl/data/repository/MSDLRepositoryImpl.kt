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

package com.google.android.msdl.data.repository

import android.os.VibrationEffect
import com.google.android.msdl.data.model.HapticComposition
import com.google.android.msdl.data.model.HapticCompositionPrimitive
import com.google.android.msdl.data.model.HapticToken
import com.google.android.msdl.data.model.SoundToken

/** A [MSDLRepository] that holds haptic compositions as haptic data. */
class MSDLRepositoryImpl : MSDLRepository {

    override fun getAudioData(soundToken: SoundToken): MSDLSoundData? {
        // TODO(b/345248875) Implement a caching strategy in accordance to the audio file strategy
        return null
    }

    override fun getHapticData(hapticToken: HapticToken): MSDLHapticData? = HAPTIC_DATA[hapticToken]

    companion object {
        private val HAPTIC_DATA: Map<HapticToken, MSDLHapticData> =
            mapOf(
                HapticToken.NEGATIVE_CONFIRMATION_HIGH_EMPHASIS to
                    MSDLHapticData { HapticComposition(null) },
                HapticToken.NEGATIVE_CONFIRMATION_MEDIUM_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    0
                                ),
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    114
                                ),
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    114
                                )
                            )
                        )
                    },
                HapticToken.POSITIVE_CONFIRMATION_HIGH_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    0
                                ),
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    114
                                )
                            )
                        )
                    },
                HapticToken.POSITIVE_CONFIRMATION_MEDIUM_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    0
                                ),
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    52
                                )
                            )
                        )
                    },
                HapticToken.POSITIVE_CONFIRMATION_LOW_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_TICK,
                                    1f,
                                    0
                                ),
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    52
                                )
                            )
                        )
                    },
                HapticToken.NEUTRAL_CONFIRMATION_HIGH_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_THUD,
                                    1f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.LONG_PRESS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    1f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.SWIPE_THRESHOLD_INDICATOR to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.7f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.TAP_HIGH_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.7f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.TAP_MEDIUM_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.5f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.DRAG_THRESHOLD_INDICATOR to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_TICK,
                                    1f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.DRAG_INDICATOR to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_TICK,
                                    0.5f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.TAP_LOW_EMPHASIS to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.3f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.KEYPRESS_STANDARD to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_TICK,
                                    0.7f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.KEYPRESS_SPACEBAR to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.7f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.KEYPRESS_RETURN to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.7f,
                                    0
                                )
                            )
                        )
                    },
                HapticToken.KEYPRESS_DELETE to
                    MSDLHapticData {
                        HapticComposition(
                            listOf(
                                HapticCompositionPrimitive(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    0.1f,
                                    0
                                )
                            )
                        )
                    }
            )
    }
}
