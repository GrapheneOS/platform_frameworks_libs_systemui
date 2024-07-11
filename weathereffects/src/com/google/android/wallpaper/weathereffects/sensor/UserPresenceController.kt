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

package com.google.android.wallpaper.weathereffects.sensor

import android.app.KeyguardManager
import android.content.Context
import java.util.concurrent.Executor

/** Controls user presence based on Keyguard and Live Wallpaper wake/sleep events. */
class UserPresenceController(
    context: Context,
    private var onUserPresenceChanged:
        (newPresence: UserPresence, oldPresence: UserPresence) -> Unit
) {

    /** The current user presence. It is [UserPresence.UNDEFINED] when it hasn't been set yet. */
    private var userPresence: UserPresence = UserPresence.UNDEFINED
        set(value) {
            val oldValue = field
            field = value
            if (field != oldValue) onUserPresenceChanged(field, oldValue)
        }

    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
    private var keyguardListener: (Boolean) -> Unit = { locked: Boolean ->
        updateUserPresence(isDeviceLocked = locked)
    }

    private var deviceLocked: Boolean = keyguardManager?.isKeyguardLocked ?: false
    private var deviceAwake: Boolean = true

    /** Start listening to the different sensors. */
    fun start(executor: Executor) {
        updateUserPresence(isDeviceLocked = keyguardManager?.isKeyguardLocked ?: false)
        /*
         * From KeyguardManager.java `keyguardListener` is saved into a map and thus won't add
         * multiple of the same listener.
         */
        keyguardManager?.addKeyguardLockedStateListener(executor, keyguardListener)
    }

    /** Stop listening to the different sensors. */
    fun stop() {
        keyguardManager?.removeKeyguardLockedStateListener(keyguardListener)
    }

    /**
     * Set the device wake state.
     *
     * @param isAwake if the device is awake. That means that the screen is not turned off or that
     * the device is not in Ambient on Display mode.
     */
    fun setWakeState(isAwake: Boolean) {
        updateUserPresence(isDeviceAwake = isAwake)
    }

    /**
     * Call when the keyguard is going away. This will happen before lock state is false (but it
     * happens at the same time that unlock animation starts).
     */
    fun onKeyguardGoingAway() = updateUserPresence(isDeviceLocked = false)

    private fun updateUserPresence(
        isDeviceAwake: Boolean = deviceAwake,
        isDeviceLocked: Boolean = deviceLocked
    ) {
        this.deviceAwake = isDeviceAwake
        this.deviceLocked = isDeviceLocked

        userPresence = when {
            !deviceAwake -> UserPresence.AWAY
            deviceLocked -> UserPresence.LOCKED
            else -> UserPresence.ACTIVE // == awake && !locked.
        }
    }

    /** Define the different user presence available. */
    enum class UserPresence {

        /**
         * We don't know the status of the User presence (usually at the beginning of the session).
         */
        UNDEFINED,

        /** User is in AoD or with the screen off. */
        AWAY,

        /** User is in lock screen. */
        LOCKED,

        /** User is in the home screen or in an app. */
        ACTIVE
    }
}
