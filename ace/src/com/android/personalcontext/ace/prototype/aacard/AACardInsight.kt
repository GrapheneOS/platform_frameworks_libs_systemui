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
package com.android.personalcontext.ace.prototype.aacard

import android.os.Bundle
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import com.android.personalcontext.ace.prototype.PrototypeInsight
import com.android.personalcontext.ace.prototype.PrototypeInsightId.AACardInsightId

/** An insight for AA Card. */
data class AACardInsight(
    val title: String,
    // TODO(b/480789784): add more fields
    override val originHints: Set<PublishedContextHint>,
) : PrototypeInsight(AACardInsightId, this) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(KEY_TITLE, title)
    }

    companion object : Creator {
        private const val KEY_TITLE = "title"

        override fun create(
            bundle: Bundle,
            insights: List<ContextInsight?>,
            originHints: Set<PublishedContextHint>,
        ): PrototypeInsight =
            AACardInsight(title = bundle.getString(KEY_TITLE) ?: "", originHints = originHints)
    }
}
