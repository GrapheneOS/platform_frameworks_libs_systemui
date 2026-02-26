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

import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightCollection
import com.android.personalcontext.ace.prototype.PrototypeHintUtils.toPrototypeHint
import com.android.personalcontext.ace.prototype.PrototypeInsightUtils.toPrototypeInsight
import com.android.personalcontext.ace.visualizer.compat.PrototypeTransformCompat
import javax.inject.Inject

/**
 * [PrototypeTransformCompat] implementation that transforms [ContextHint] and [ContextInsight] to
 * [PrototypeHint] and [PrototypeInsight], respectively.
 */
class PrototypeTransformCompatImpl @Inject constructor() : PrototypeTransformCompat {
    override fun transform(hint: ContextHint): Any? {
        return hint.toPrototypeHint()
    }

    override fun transform(insight: ContextInsight): Any? {
        return insight.toPrototypeInsight()
    }

    override fun transformChildren(insight: InsightCollection): List<ContextInsight>? {
        return insight.toPrototypeInsight()?.exportInsightsToList()?.filterNotNull()
    }
}
