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

package com.android.personalcontext.ace.visualizer.templates.call

import android.app.RemoteAction
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.util.Log

internal object CallInsightConverter {
  private val TAG = "CallInsightConverter"

  /**
   * Converts a top-level [ContextInsight] to a [CallVisualizerWidget].
   *
   * The top-level [ContextInsight] is expected to be an [InsightCollection] with 4 insights in the
   * following order:
   * 1. A detailed cards [InsightCollection]
   * 2. A general cards for emails [InsightCollection]
   * 3. A general cards for messages [InsightCollection]
   * 4. Rendering Metadata [InsightCollection]
   *
   * If any of the insights are not in the expected order, or are not the expected type, throw an
   * exception.
   */
  fun ContextInsight.toCallVisualizerWidget(): CallVisualizerWidget {
    val insightCollection =
      this as? InsightCollection
        ?: error(
          "[CallEmbedded] Expected a top-level InsightCollection, actual: ${this.javaClass.simpleName}."
        )
    val insights = insightCollection.insights
    if (insights.size != 4) {
      error("[CallEmbedded] Expected 4 inner insights, actual: ${insights.size}")
    }

    val detailedCardsInsight = insights.getOrNull(0) as? InsightCollection
    val generalCardsEmailsInsight = insights.getOrNull(1) as? InsightCollection
    val generalCardsMessagesInsight = insights.getOrNull(2) as? InsightCollection
    val widgetStaticRenderingInsight = insights.getOrNull(3) as? InsightCollection

    val detailedCards = detailedCardsInsight?.toCallVisualizerDetailedCards() ?: emptyList()
    val generalCardsEmails =
      generalCardsEmailsInsight?.toCallVisualizerGeneralCards() ?: emptyList()
    val generalCardsMessages =
      generalCardsMessagesInsight?.toCallVisualizerGeneralCards() ?: emptyList()
    val aiDisclaimer: String? = widgetStaticRenderingInsight?.toAiDisclaimer()
    val ctaDisplayMoreResults: DisplayInsight? =
      widgetStaticRenderingInsight?.toCtaDisplayMoreResults()

    if (detailedCards.isEmpty() && generalCardsEmails.isEmpty() && generalCardsMessages.isEmpty()) {
      error(
        "[CallEmbedded] [toCallVisualizerWidget] No actual cards were included in the InsightCollection."
      )
    }

    Log.i(
      TAG,
      "[CallEmbedded] Returning CallVisualizerWidget with ${detailedCards.size} detailed cards, ${generalCardsEmails.size} general cards emails, ${generalCardsMessages.size} general cards messages",
    )

    return CallVisualizerWidget(
      detailedCards = detailedCards,
      generalCardsEmails = generalCardsEmails,
      generalCardsMessages = generalCardsMessages,
      aiDisclaimer = aiDisclaimer,
      ctaDisplayMoreResults = ctaDisplayMoreResults,
    )
  }

  /** Converts an [InsightCollection] to a list of [CallVisualizerDetailedCard]. */
  private fun InsightCollection.toCallVisualizerDetailedCards(): List<CallVisualizerDetailedCard> {
    return this.insights.mapNotNull { detailedCardInsight: ContextInsight ->
      (detailedCardInsight as? InsightCollection)?.toSingleDetailedCard()
    }
  }

  /**
   * Converts an [InsightCollection] to a single [CallVisualizerDetailedCard].
   *
   * The [InsightCollection] is expected to have 3 insights in the following order:
   * 1. A header [DisplayInsight], representing the title of the detailed card
   * 2. A rows [InsightCollection], representing a single row of "details"
   * 3. An action [ActionableInsight] for deeplinks
   * 4. A feedback [InsightCollection] for thumbs up/down
   *
   * If any of the insights are not in the expected order, or are not the expected type, null will
   * be returned.
   */
  private fun InsightCollection.toSingleDetailedCard(): CallVisualizerDetailedCard? {
    val detailedCollection = this as? InsightCollection ?: return null
    val elements: List<ContextInsight> = detailedCollection.insights
    if (elements.size != 4) return null

    val headerInsight = elements.getOrNull(0) as? DisplayInsight ?: return null
    val rowsInsight = elements.getOrNull(1) as? InsightCollection ?: return null
    val actionInsight = elements.getOrNull(2) as? ActionableInsight
    val feedbackInsight = elements.getOrNull(3) as? InsightCollection

    val title = headerInsight.details.title?.toString() ?: ""
    val rows = rowsInsight.toCallVisualizerRows()
    val dataSource = actionInsight?.actionDetails?.toRemoteAction()
    val feedback = feedbackInsight?.toFeedback()

    return CallVisualizerDetailedCard(
      title = title,
      rows = rows,
      dataSource = dataSource,
      listUuid = headerInsight.insightId.toString(),
      feedback = feedback,
    )
  }

  /** Converts an [InsightCollection] to a list of [CallVisualizerRow]. */
  private fun InsightCollection.toCallVisualizerRows(): List<CallVisualizerRow> {
    return this.insights.mapNotNull { rowInsight -> rowInsight.toSingleRow() }
  }

  /** Converts a [ContextInsight] to a single [CallVisualizerRow]. */
  private fun ContextInsight.toSingleRow(): CallVisualizerRow? {
    return when (this) {
      is DisplayInsight -> CallVisualizerRow.FullLength(this.toCallVisualizerFullLengthRow())
      is InsightCollection -> { // Split Row
        val items = this.insights
        if (items.size == 2) {
          val itemOne = items.getOrNull(0) as? DisplayInsight ?: return@toSingleRow null
          val itemTwo = items.getOrNull(1) as? DisplayInsight ?: return@toSingleRow null

          CallVisualizerRow.HalfHalfSplit(
            CallVisualizerTwoItemRow(
              itemOne = itemOne.toCallVisualizerRowItem(),
              itemTwo = itemTwo.toCallVisualizerRowItem(),
            )
          )
        } else {
          null
        }
      }
      else -> null
    }
  }

  /** Converts a [DisplayInsight] to a [CallVisualizerFullLengthRow]. */
  private fun DisplayInsight.toCallVisualizerFullLengthRow(): CallVisualizerFullLengthRow =
    CallVisualizerFullLengthRow(item = this.toCallVisualizerRowItem())

  /** Converts a [DisplayInsight] to a [CallVisualizerRowItem]. */
  private fun DisplayInsight.toCallVisualizerRowItem(): CallVisualizerRowItem {
    val displayDetails = this.details
    return CallVisualizerRowItem(
      label =
        CallVisualizerRowItemText(
          text = displayDetails.title?.toString() ?: "",
          textSize = CallVisualizerRowItemText.TextSize.MEDIUM,
        ),
      content =
        CallVisualizerRowItemText(
          text = displayDetails.contentDescription?.toString() ?: "",
          textSize = CallVisualizerRowItemText.TextSize.MEDIUM,
        ),
      contentDescription = displayDetails.contentDescription?.toString() ?: "",
    )
  }

  /** Converts an [InsightCollection] to a list of [CallVisualizerGeneralCard]. */
  private fun InsightCollection.toCallVisualizerGeneralCards(): List<CallVisualizerGeneralCard> {
    return this.insights.mapNotNull { generalCardInsight: ContextInsight ->
      (generalCardInsight as? ActionableInsight)?.toCallVisualizerGeneralCard()
        ?: (generalCardInsight as? DisplayInsight)?.toCallVisualizerGeneralCard()
    }
  }

  /** Converts an [ActionableInsight] to a [CallVisualizerGeneralCard]. */
  private fun ActionableInsight.toCallVisualizerGeneralCard(): CallVisualizerGeneralCard {
    val displayDetails = this.displayDetails
    val actionDetails = this.actionDetails

    return CallVisualizerGeneralCard(
      title = displayDetails.title?.toString() ?: "",
      date = displayDetails.subtitle?.toString() ?: "",
      detailedText = displayDetails.contentDescription?.toString() ?: "",
      dataSource = actionDetails.toRemoteAction(),
      cardExpandButtonAccessibilityContentDescription = "",
      listUuid = this.insightId.toString(),
    )
  }

  /**
   * Converts a [DisplayInsight] to a [CallVisualizerGeneralCard]. Only used if the
   * [ActionableInsight] is not available (ie. a deeplink is missing).
   */
  private fun DisplayInsight.toCallVisualizerGeneralCard(): CallVisualizerGeneralCard {
    val displayDetails = this.details

    return CallVisualizerGeneralCard(
      title = displayDetails.title?.toString() ?: "",
      date = displayDetails.subtitle?.toString() ?: "",
      detailedText = displayDetails.contentDescription?.toString() ?: "",
      dataSource = null,
      cardExpandButtonAccessibilityContentDescription = "",
      listUuid = this.insightId.toString(),
    )
  }

  /** Converts [InsightActionDetails] to [CallVisualizerResponseSource]. */
  private fun InsightActionDetails.toRemoteAction(): RemoteAction? =
    if (this.remoteAction?.actionIntent == null) {
      null
    } else {
      this.remoteAction
    }

  private fun InsightCollection.toFeedback(): CallVisualizerFeedback? {
    val labelInsight = this.insights.getOrNull(0) as? DisplayInsight

    val positiveFeedback = (this.insights.getOrNull(1) as? DisplayInsight) ?: return null
    val negativeFeedback = (this.insights.getOrNull(2) as? DisplayInsight) ?: return null

    return CallVisualizerFeedback(
      label = labelInsight?.details?.title?.toString(),
      positiveFeedback = positiveFeedback,
      negativeFeedback = negativeFeedback,
    )
  }

  /**
   * The [InsightCollection] is expected to have 2 insights in the following order:
   * 1. A "Show more" button [DisplayInsight] - Only this one is extracted
   * 2. An AI disclaimer [DisplayInsight]
   *
   * If the understander provides a [BundleInsight] instead of a [DisplayInsight], then the display
   * more results button will not be displayed to the user.
   */
  private fun InsightCollection.toCtaDisplayMoreResults(): DisplayInsight? {
    return this.insights.getOrNull(0) as? DisplayInsight
  }

  /**
   * The [InsightCollection] is expected to have 2 insights in the following order:
   * 1. A "Show more" button [DisplayInsight]
   * 2. An AI disclaimer [DisplayInsight] - Only this one is extracted
   */
  private fun InsightCollection.toAiDisclaimer(): String? {
    val insight = this.insights.getOrNull(1) as? DisplayInsight
    return insight?.details?.title?.toString()
  }
}
