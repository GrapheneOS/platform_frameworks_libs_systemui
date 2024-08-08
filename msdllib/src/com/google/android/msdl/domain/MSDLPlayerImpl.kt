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

package com.google.android.msdl.domain

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.android.msdl.data.model.FeedbackLevel
import com.google.android.msdl.data.model.HapticComposition
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.data.repository.MSDLHapticData
import com.google.android.msdl.data.repository.MSDLRepository
import com.google.android.msdl.data.repository.MSDLSoundData
import java.util.concurrent.Executor

/**
 * Implementation of the MSDLPlayer.
 *
 * At the core, the player is in charge of delivering haptic and audio feedback closely in time.
 *
 * @param[repository] Repository to retrieve audio and haptic data.
 * @param[executor] An [Executor] used to schedule haptic playback.
 * @param[vibrator] Instance of the default [Vibrator] on the device.
 */
class MSDLPlayerImpl(
    private val repository: MSDLRepository,
    private val vibrator: Vibrator,
    private val executor: Executor,
) : MSDLPlayer {

    // TODO(b/355230334): This should be retrieved from the system Settings
    override fun getSystemFeedbackLevel(): FeedbackLevel = MSDLPlayer.SYSTEM_FEEDBACK_LEVEL

    override fun playToken(token: MSDLToken, properties: InteractionProperties?) {
        // Don't play the data for the token if the current feedback level is below the minimal
        // level of the token
        if (getSystemFeedbackLevel() < token.minimumFeedbackLevel) return

        // Gather the data from the repositories
        val hapticData = repository.getHapticData(token.hapticToken)
        val soundData = repository.getAudioData(token.soundToken)

        // Play the data for the token with the given properties
        playData(hapticData, soundData, properties)
    }

    private fun playData(
        hapticData: MSDLHapticData?,
        soundData: MSDLSoundData?,
        properties: InteractionProperties?,
    ) {
        // Nothing to play
        if (hapticData == null && soundData == null) return

        if (soundData == null) {
            // Play haptics only
            // 1. Create the effect
            val composition: HapticComposition? = hapticData?.get() as? HapticComposition
            val effect =
                if (properties == null) {
                    // Compose as-is
                    composition?.composeIntoVibrationEffect()
                } else {
                    when (properties) {
                        is InteractionProperties.DynamicVibrationScale -> {
                            composition?.composeIntoVibrationEffect(
                                scaleOverride = properties.scale
                            )
                        }
                        else -> null
                    }
                }

            // 2. Deliver the haptics with attributes
            if (effect == null || !vibrator.hasVibrator()) return
            val attributes =
                if (properties?.vibrationAttributes != null) {
                    properties.vibrationAttributes
                } else {
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
                }
            executor.execute { vibrator.vibrate(effect, attributes) }
        } else {
            // TODO(b/345248875): Play audio and haptics
        }
    }
}

fun HapticComposition.composeIntoVibrationEffect(
    scaleOverride: Float? = null,
    delayOverride: Int? = null,
): VibrationEffect? {
    if (primitives == null) return null

    val effectComposition = VibrationEffect.startComposition()
    primitives.forEach { primitive ->
        effectComposition.addPrimitive(
            primitive.primitiveId,
            scaleOverride ?: primitive.scale,
            delayOverride ?: primitive.delay,
        )
    }
    return effectComposition.compose()
}
