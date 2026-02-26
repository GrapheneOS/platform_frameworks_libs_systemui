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
package com.android.personalcontext.ace.prototype.card

import android.os.Bundle
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import com.android.personalcontext.ace.prototype.PrototypeInsight
import com.android.personalcontext.ace.prototype.PrototypeInsightId.CardInsightId

data class CardInsight(
    val title: ContextInsight?,
    val header: ContextInsight?,
    val body: ContextInsight,
    val footer: ContextInsight?,
    val actions: ContextInsight?,
    override val originHints: Set<PublishedContextHint>,
) : PrototypeInsight(CardInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        /* No-op. */
    }

    override fun exportInsightsToList(): List<ContextInsight?> =
        listOf(title, header, body, footer, actions)

    companion object : Creator {

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            CardInsight(
                title = insights[0],
                header = insights[1],
                body = insights[2]!!,
                footer = insights[3],
                actions = insights[4],
                originHints = originHints,
            )
    }
}
