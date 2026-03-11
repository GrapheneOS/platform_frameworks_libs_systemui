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

package com.android.personalcontext.ace.visualizer.templates.message

import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightCollection
import android.util.Log
import com.android.personalcontext.ace.visualizer.templates.message.ClientActionChip.Companion.toClientActionChip
import com.android.personalcontext.ace.visualizer.templates.message.RemoteActionChip.Companion.toRemoteActionChip
import com.android.personalcontext.ace.visualizer.templates.message.SuggestionChip.Companion.toSuggestionChip

/**
 * A data class that contains the data for the message template.
 *
 * @property messageChipList The list of chips to display in the message template. This list must be
 *   non-empty.
 */
data class MessageTemplateData(val messageChipList: List<MessageChip>) {
  companion object {
    private const val TAG = "MessageTemplateData"

    fun ContextInsight.toMessageTemplateData(): MessageTemplateData {
      Log.d(TAG, "[MessagesEmbedded] toMessageTemplateData")

      if (this !is InsightCollection) {
        error(
          "[MessagesEmbedded] Expected a top-level InsightCollection, actual: ${this.javaClass.simpleName}"
        )
      }

      val messageChipList =
        insights.mapNotNull { insight ->
          when (insight) {
            is DisplayInsight -> {
              Log.d(TAG, "[MessagesEmbedded] Find display insight")
              insight.toSuggestionChip()
            }
            is ActionableInsight -> {
              Log.d(TAG, "[MessagesEmbedded] Find actionable insight")
              insight.toRemoteActionChip()
            }
            is InsightCollection -> {
              Log.d(TAG, "[MessagesEmbedded] Find insight collection")
              insight.toClientActionChip()
            }
            else -> {
              Log.w(
                TAG,
                "[MessagesEmbedded] Find unexpected insight: ${insight.javaClass.simpleName}",
              )
              null
            }
          }
        }
      if (messageChipList.isEmpty()) {
        error("[MessagesEmbedded] messageChipList is empty")
      }

      return MessageTemplateData(messageChipList)
    }
  }
}

/** A sealed interface for chips that can be displayed in the message template. */
sealed interface MessageChip {
  val title: String
  val contentDescription: String
  val icon: Icon?
}

data class SuggestionChip(
  override val title: String,
  val subtitle: String,
  override val contentDescription: String,
  override val icon: Icon?,
  val egressInsight: DisplayInsight,
) : MessageChip {
  companion object {
    private const val TAG = "SuggestionChip"

    fun DisplayInsight.toSuggestionChip(): SuggestionChip {
      Log.d(TAG, "[MessagesEmbedded] title: ${details.title}")
      return SuggestionChip(
        title = details.title.toString(),
        subtitle = details.subtitle.toString(),
        contentDescription = details.contentDescription.toString(),
        icon = details.icon,
        egressInsight = this,
      )
    }
  }
}

sealed interface ActionChip : MessageChip {
  override val icon: Icon
  val attributionInsight: ContextInsight
}

data class RemoteActionChip(
  override val title: String,
  override val contentDescription: String,
  override val icon: Icon,
  override val attributionInsight: ActionableInsight,
  val remoteAction: RemoteAction,
) : ActionChip {
  companion object {
    private const val TAG = "RemoteActionChip"

    fun ActionableInsight.toRemoteActionChip(): RemoteActionChip? {
      Log.d(
        TAG,
        "[MessagesEmbedded] actionDetails.remoteAction is null: ${actionDetails.remoteAction == null}",
      )
      return actionDetails.remoteAction?.let { remoteAction ->
        RemoteActionChip(
          title = displayDetails.title.toString(),
          contentDescription = displayDetails.contentDescription.toString(),
          icon = remoteAction.icon,
          attributionInsight = this,
          remoteAction = remoteAction,
        )
      }
    }
  }
}

data class ClientActionChip(
  override val title: String,
  override val contentDescription: String,
  override val icon: Icon,
  override val attributionInsight: ActionableInsight,
  val egressInsight: BundleInsight,
) : ActionChip {
  companion object {
    private const val TAG = "ClientActionChip"

    fun InsightCollection.toClientActionChip(): ClientActionChip? {
      val actionableInsight = insights.findActionableInsight()
      val bundleInsight = insights.findBundleInsight()
      if (actionableInsight != null && bundleInsight != null) {
        Log.d(TAG, "[MessagesEmbedded] actionableInsight and bundleInsight are not null")
        return actionableInsight.actionDetails.remoteAction?.let { remoteAction ->
          ClientActionChip(
            title = actionableInsight.displayDetails.title.toString(),
            contentDescription = actionableInsight.displayDetails.contentDescription.toString(),
            icon = remoteAction.icon,
            attributionInsight = actionableInsight,
            egressInsight = bundleInsight,
          )
        }
      }
      Log.d(TAG, "[MessagesEmbedded] actionableInsight or bundleInsight is null")
      return null
    }

    private fun List<ContextInsight>.findBundleInsight(): BundleInsight? = firstNotNullOfOrNull {
      it as? BundleInsight
    }

    private fun List<ContextInsight>.findActionableInsight(): ActionableInsight? =
      firstNotNullOfOrNull {
        it as? ActionableInsight
      }
  }
}
