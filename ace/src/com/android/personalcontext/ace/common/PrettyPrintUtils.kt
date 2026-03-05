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

package com.android.personalcontext.ace.common

import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.InsightCollection
import com.android.personalcontext.ace.common.RenderTokenUtils.hasRendererToken

/** Pretty print utilities for ACE classes. */
object PrettyPrintUtils {

    /**
     * Pretty prints the type(s) of the given hints in a human-readable string.
     *
     * Prefix with "!" if the hint contains
     * [android.service.personalcontext.hint.PublishedContextHint.getRenderTokens].
     *
     * Postfix with "*" if the hint is [transform]ed.
     */
    fun Collection<PublishedContextHint>.toPrettyPrint(
        transform: (ContextHint) -> Any? = { null }
    ): String {
        return joinToString(", ") {
            val hint = it.contextHint
            val transformed = transform(hint)

            val name = (transformed ?: hint).javaClass.simpleName
            val prefix = if (it.hasRendererToken()) "!" else ""
            val postfix = if (transformed != null) "*" else ""

            "$prefix$name$postfix"
        }
    }

    /**
     * Pretty prints the type(s) of the given insight in a human-readable string.
     *
     * Postfix with "*" if the insight is [transform]ed.
     *
     * @param maxDepth The number of nested collection levels to expand before truncating.
     */
    fun ContextInsight.toPrettyPrint(
        maxDepth: Int = 1,
        transform: (ContextInsight) -> Any? = { null },
        children: (InsightCollection) -> List<ContextInsight>? = { it.insights },
    ): String {
        val insight = this
        val transformed = transform(insight)

        val name = (transformed ?: insight).javaClass.simpleName
        val prefix = ""
        val postfix = if (transformed != null) "*" else ""

        val type = "$prefix$name$postfix"

        if (insight is InsightCollection && insights.isNotEmpty()) {
            if (maxDepth <= 0) {
                return "$type[...]"
            } else {
                val childrenString =
                    (children(this) ?: insights).joinToString(", ") {
                        it.toPrettyPrint(maxDepth - 1, transform, children)
                    }
                return "$type[$childrenString]"
            }
        }

        return type
    }
}
