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

package com.google.android.msdl.data.model

/** A haptic composition as a list of [HapticCompositionPrimitive] */
data class HapticComposition(val primitives: List<HapticCompositionPrimitive>? = null)

/**
 * An abstraction of a haptic primitive in a composition that includes:
 *
 * @param[primitiveId] The id of the primitive.
 * @param[scale] The scale of the primitive.
 * @param[delay] The delay of the primitive relative to the end of a previous primitive.
 */
data class HapticCompositionPrimitive(
    val primitiveId: Int,
    var scale: Float = 1f,
    var delay: Int = 0,
)
