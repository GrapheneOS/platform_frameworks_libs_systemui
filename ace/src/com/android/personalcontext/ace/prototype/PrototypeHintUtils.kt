/*
 * Copyright (C) 2026 The Android Open Source Project
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
@file:Suppress("FlaggedApi", "NewApi")

package com.android.personalcontext.ace.prototype

import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.hint.ContextHint
import com.android.personalcontext.ace.prototype.PrototypeHintId.AACardHintId
import com.android.personalcontext.ace.prototype.PrototypeHintId.CrossDeviceIntentHintId
import com.android.personalcontext.ace.prototype.PrototypeHintId.DialerClickEventHintId
import com.android.personalcontext.ace.prototype.PrototypeHintId.ExampleEmbeddedHintId
import com.android.personalcontext.ace.prototype.PrototypeHintId.WeatherHintId
import com.android.personalcontext.ace.prototype.aacard.AACardHint
import com.android.personalcontext.ace.prototype.call.DialerClickEventHint
import com.android.personalcontext.ace.prototype.crossdevice.CrossDeviceIntentHint
import com.android.personalcontext.ace.prototype.example.ExampleEmbeddedHint
import com.android.personalcontext.ace.prototype.weather.WeatherHint

private const val PROTOTYPE_HINT_ID_KEY = "prototype_hint_id_key"

object PrototypeHintUtils {

    /** Converts the [PrototypeHint] into a [ContextHint]. */
    fun <T : PrototypeHint> T.toContextHint(): ContextHint {
        val hint = this
        return BundleHint.Builder().build().apply {
            dataBundle.putInt(PROTOTYPE_HINT_ID_KEY, hint.id.uid)
            hint.exportDataToBundle(dataBundle)
        }
    }

    /** Converts a [ContextHint] back into a [PrototypeHint], if possible. */
    fun ContextHint.toPrototypeHint(): PrototypeHint? {
        if (this !is BundleHint) return null

        val uid = dataBundle.getInt(PROTOTYPE_HINT_ID_KEY)
        val id = PrototypeHintId.entries.find { it.uid == uid } ?: return null

        val creator =
            when (id) {
                ExampleEmbeddedHintId -> ExampleEmbeddedHint
                WeatherHintId -> WeatherHint
                AACardHintId -> AACardHint
                DialerClickEventHintId -> DialerClickEventHint
                CrossDeviceIntentHintId -> CrossDeviceIntentHint
            }

        return creator.create(dataBundle)
    }

    /** Converts a [ContextHint] back into a [PrototypeHint] as [T], if possible. */
    @JvmSynthetic
    @JvmName("toTypedPrototypeHint")
    inline fun <reified T : PrototypeHint> ContextHint.toPrototypeHint(): T? {
        return toPrototypeHint() as? T
    }

    /** Returns whether a [ContextHint] is a [PrototypeHint] of type [T]. */
    @JvmSynthetic
    inline fun <reified T : PrototypeHint> ContextHint.isPrototypeHint(): Boolean {
        return toPrototypeHint() is T
    }
}
