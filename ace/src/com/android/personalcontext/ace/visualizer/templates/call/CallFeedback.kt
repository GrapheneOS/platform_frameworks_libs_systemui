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
package com.android.personalcontext.ace.visualizer.templates.call

import android.graphics.Bitmap
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.personalcontext.ace.visualizer.templates.LocalInsightEventReporter
import com.android.personalcontext.ace.visualizer.templates.LocalPublishedContextInsight
import com.android.personalcontext.ace.visualizer.templates.LocalRenderToken
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeLarge

/** Contains the text label for the feedback buttons and the feedback buttons themselves. */
@Composable
fun CallVisualizerFeedback.CardFeedback() {
  val feedback = this@CardFeedback

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = feedback.label ?: "",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
    FeedbackButtons()
  }
}

/** The feedback buttons (thumbs up and thumbs down) for the card. */
@Composable
fun CallVisualizerFeedback.FeedbackButtons() {
  val feedback = this@FeedbackButtons

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FeedbackButton(
      feedbackInsight = feedback.positiveFeedback,
      fallbackIcon = Icons.Outlined.ThumbUp,
    )
    FeedbackButton(
      feedbackInsight = feedback.negativeFeedback,
      fallbackIcon = Icons.Outlined.ThumbDown,
    )
  }
}

/** A single feedback button (ie. thumbs up or thumbs down). */
@Composable
@Suppress("FlaggedApi", "NewApi")
private fun FeedbackButton(feedbackInsight: DisplayInsight, fallbackIcon: ImageVector) {
  val context = LocalContext.current
  val insightEventReporter = LocalInsightEventReporter.current
  val publishedInsight = LocalPublishedContextInsight.current
  val renderToken = LocalRenderToken.current

  val feedbackBitmap: Bitmap? =
    remember(feedbackInsight.details.icon) {
      feedbackInsight.details.icon?.loadDrawable(context)?.toBitmap()
    }

  val personalContextManager = remember {
    context.getSystemService(PersonalContextManager::class.java)
  }

  fun reportEvent(event: Int) {
    with(insightEventReporter) {
      personalContextManager?.reportChildInsightEvent(
        publishedInsight,
        feedbackInsight,
        event,
        renderToken,
      )
    }
  }

  LaunchedEffect(Unit) { reportEvent(InsightEvent.EVENT_SHOW) }

  IconButton(onClick = { reportEvent(InsightEvent.EVENT_USER_TAP) }) {
    if (feedbackBitmap != null) {
      Icon(
        bitmap = feedbackBitmap.asImageBitmap(),
        contentDescription = feedbackInsight.details.contentDescription.toString(),
        modifier = Modifier.size(IconSizeLarge),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    } else {
      Icon(
        imageVector = fallbackIcon,
        contentDescription = feedbackInsight.details.contentDescription.toString(),
        modifier = Modifier.size(IconSizeLarge),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
