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
package com.android.personalcontext.ace.prototype

import android.os.Bundle
import android.service.personalcontext.Token
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import androidx.annotation.IntRange

/**
 * [PrototypeInsight] is an abstract class that allows defining custom [ContextInsight] types for
 * prototyping purposes, without needing to modify the Android framework.
 *
 * These custom insight types are wrapped into a [ContextInsight], preserving both its parcelable
 * data and its children insights.
 *
 * This allows new insight types to be created and used before they are formally integrated into the
 * Android platform as new [ContextInsight] subclasses.
 */
abstract class PrototypeInsight(val id: PrototypeInsightId, val creator: Creator) {

    /** @see [android.service.personalcontext.insight.ContextInsight.getOriginHints] */
    open val originHints: Set<PublishedContextHint> = emptySet()

    /** @see [android.service.personalcontext.insight.ContextInsight.getTokens] */
    open val tokens: List<Token> = emptyList()

    /**
     * Exports the primitive data for this insight into the given [Bundle].
     *
     * [originHints] and [tokens] will be persisted for you automatically.
     */
    abstract fun exportDataToBundle(bundle: Bundle)

    /**
     * Exports the child [android.service.personalcontext.insight.ContextInsight]s for this insight
     * into a list.
     */
    open fun exportInsightsToList(): List<ContextInsight?> = emptyList()

    /**
     * Interface for creating instances of [PrototypeInsight] from their serialized state.
     *
     * Implementations of this interface act as factories that reconstruct concrete
     * [PrototypeInsight] subclasses using data previously exported via [exportDataToBundle] and
     * [exportInsightsToList].
     *
     * This mechanism allows the framework to instantiate specific prototype insights without having
     * compile-time knowledge of their concrete classes.
     */
    interface Creator {

        /**
         * Creates a new instance of a concrete [PrototypeInsight] subclass.
         *
         * @param bundle The [Bundle] containing the primitive data for this insight, as populated
         *   by [PrototypeInsight.exportDataToBundle].
         * @param insights The list of child [ContextInsight]s, as populated by
         *   [PrototypeInsight.exportInsightsToList].
         * @param originHints The set of origin hints associated with this insight. These are
         *   automatically preserved by the base class and passed back here for reconstruction.
         * @return A fully initialized instance of the specific [PrototypeInsight] subclass.
         */
        fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight
    }
}

/**
 * A unique identifier for each type of [PrototypeInsight].
 *
 * @property uid A unique positive value for each entry that should not change after creation.
 */
// Next ID: 10
enum class PrototypeInsightId(@field:IntRange(from = 1) val uid: Int) {
    ExampleEmbeddedInsightId(1),
    EmbeddedScrollInsightId(2),
    ClientActionInsightId(3),
    WeatherInsightId(4),
    EmptyRenderInsightId(5),
    AACardInsightId(6),
    CardInsightId(7),
    InsightGridId(8),
    ServerSideCloseInsightId(9),
}
