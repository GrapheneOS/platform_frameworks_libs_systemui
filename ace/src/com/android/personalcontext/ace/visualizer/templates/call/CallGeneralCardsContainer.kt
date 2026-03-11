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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.personalcontext.ace.visualizer.R
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeLarge
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.IconSizeMedium
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeExtraSmall
import com.android.personalcontext.ace.visualizer.templates.call.CallWidgetConstants.RoundedCornerSizeLarge
import com.android.personalcontext.ace.visualizer.templates.utils.RemoteActionUtils.execute

private const val TAG = "CallGeneralCardsContainer"

/** The container for a the simple cards in the Magic Cue Call widget. */
@Composable
internal fun CallGeneralCardsContainer(cards: List<CallVisualizerGeneralCard>) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
    for ((i, card) in cards.withIndex()) {
      val isFirstCard = i == 0
      val isLastCard = i == cards.size - 1

      key(i) { // TODO: Do not use index as the key
        CallGeneralCardContainer(card = card, isFirstCard = isFirstCard, isLastCard = isLastCard)
      }
    }
  }
}

/** Layout for a single general card. */
@VisibleForTesting
@Composable
internal fun CallGeneralCardContainer(
  card: CallVisualizerGeneralCard,
  isFirstCard: Boolean,
  isLastCard: Boolean,
) {
  var isExpanded by remember { mutableStateOf(false) }

  Card(
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    shape =
      RoundedCornerShape(
        topStart = if (isFirstCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
        topEnd = if (isFirstCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
        bottomStart = if (isLastCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
        bottomEnd = if (isLastCard) RoundedCornerSizeLarge else RoundedCornerSizeExtraSmall,
      ),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .semantics(mergeDescendants = true) { this.role = Role.Button }
          .clickable { isExpanded = !isExpanded }
          .padding(16.dp)
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
          // Calculate the top padding needed for the date Text to align its top edge with the
          // title Text.
          // Only add padding if the title's top offset is larger than the date's.
          val titleTextStyle = MaterialTheme.typography.titleMedium
          val dateTextStyle = MaterialTheme.typography.bodySmall
          val dateTopOffsetSp =
            ((titleTextStyle.lineHeight.value - dateTextStyle.lineHeight.value) / 2)
              .coerceAtLeast(0f)
              .toInt()
              .sp
          val dateTopPaddingDp = with(LocalDensity.current) { dateTopOffsetSp.toPx().toDp() }

          Row(verticalAlignment = Alignment.Top) {
            Text(
              modifier = Modifier.weight(1f, fill = false),
              text = card.title,
              style = titleTextStyle,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines =
                if (isExpanded) {
                  3
                } else {
                  1
                },
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              modifier = Modifier.padding(top = dateTopPaddingDp),
              text = card.date,
              style = dateTextStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.width(4.dp))
        ExpandIcon(isExpanded = isExpanded, contentDescription = "")
      }

      card.detailedText?.let { detailedText ->
        Spacer(modifier = Modifier.height(6.dp))
        EmailSummaryText(isExpanded = isExpanded, text = detailedText, displayIcon = true)
      }

      card.dataSource?.let { dataSource ->
        val buttonText = dataSource.title.toString()
        val buttonContentDescription = dataSource.contentDescription.toString()
        val context = LocalContext.current

        Button(
          onClick = { dataSource.execute(context) },
          modifier =
            Modifier.semantics(mergeDescendants = true) {
              contentDescription = buttonContentDescription
            },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              modifier = Modifier.size(IconSizeMedium),
              painter = painterResource(R.drawable.gs_open_in_new_vd_theme_24),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              contentDescription = null,
            )
            Text(text = buttonText)
          }
        }
      }
    }
  }
}

/** Displays the summary text for the email insight */
@Composable
private fun EmailSummaryText(isExpanded: Boolean, text: String, displayIcon: Boolean) {
  val detailedTextStyle = MaterialTheme.typography.bodyMedium
  val fontSizeSp = detailedTextStyle.fontSize
  val fontSizeDp = with(LocalDensity.current) { fontSizeSp.toPx().toDp() }

  // The size of the Icon. It's dynamically based on iconSizeDp, but is clamped between 16.dp and
  // 32.dp.
  val dynamicIconSize = max(min(fontSizeDp, 32.dp), 14.dp)
  val dynamicIconWrapperSizeDp = dynamicIconSize

  // Calculate how much padding to apply to the icon to vertically center it with the text.
  val fontHeightSp = detailedTextStyle.lineHeight
  val dynamicIconWrapperSizeSp =
    with(LocalDensity.current) { dynamicIconWrapperSizeDp.toPx().toSp() }
  val iconPaddingTopSp =
    ((fontHeightSp.value - dynamicIconWrapperSizeSp.value) / 2).coerceAtLeast(0f).toInt().sp
  val iconPaddingTopDp = with(LocalDensity.current) { iconPaddingTopSp.toPx().toDp() }

  // Calculate the left padding for the icon. This padding ensures the icon is center-aligned
  // with a reference icon of IconSizeMedium (24.dp). If dynamicIconSize is 24.dp or larger,
  // no left padding is needed. Otherwise, the padding is half the difference.
  val iconPaddingLeftDp = ((IconSizeLarge - dynamicIconSize) / 2).coerceAtLeast(0.dp)

  // Calculate the width of the Spacer, ensuring the combined width of the icon wrapper, left
  // padding, and spacer is 40.dp, with a minimum spacer width of 8.dp.
  val spacerWidth = (40.dp - dynamicIconWrapperSizeDp - iconPaddingLeftDp).coerceAtLeast(8.dp)

  Row(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier =
        Modifier.padding(top = iconPaddingTopDp, start = iconPaddingLeftDp)
          .size(dynamicIconWrapperSizeDp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        modifier = Modifier.size(dynamicIconSize),
        painter = painterResource(R.drawable.gs_call_text_analysis_2_vd_theme_24),
        tint = MaterialTheme.colorScheme.onSurface,
        contentDescription = null,
      )
    }
    Spacer(modifier = Modifier.width(spacerWidth))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines =
        if (isExpanded) {
          4
        } else {
          1
        },
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Displays the source app's icon. If the icon is not available, displays a generic icon. */
@Composable
private fun AppIcon(
  modifier: Modifier = Modifier,
  contentDescription: String,
  sourcePackageName: String?,
) {
  val packageManager = LocalContext.current.packageManager
  val icon: ImageBitmap? =
    remember(sourcePackageName) { getAppIcon(packageManager, sourcePackageName) }

  if (icon != null) {
    Image(
      modifier = Modifier.size(IconSizeLarge),
      bitmap = icon,
      contentDescription = contentDescription,
    )
  } else {
    Icon(
      modifier = Modifier.size(IconSizeLarge),
      painter = painterResource(R.drawable.gs_widgets_vd_theme_24),
      tint = MaterialTheme.colorScheme.secondary,
      contentDescription = null,
    )
  }
}

/**
 * Displays an icon indicating the expanded state.
 *
 * @param isExpanded Whether the card is currently expanded.
 * @param contentDescription The content description for the icon.
 */
@Composable
private fun ExpandIcon(
  modifier: Modifier = Modifier,
  isExpanded: Boolean,
  contentDescription: String? = null,
) {
  Icon(
    modifier = modifier.size(IconSizeLarge),
    painter =
      painterResource(
        if (isExpanded) {
          R.drawable.gs_keyboard_arrow_up_vd_theme_24
        } else {
          R.drawable.gs_keyboard_arrow_down_vd_theme_24
        }
      ),
    contentDescription = contentDescription,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Gets the app icon [ImageBitmap] for the given package name. */
private fun getAppIcon(packageManager: PackageManager, packageName: String?): ImageBitmap? =
  packageName
    .takeIf { !it.isNullOrBlank() }
    ?.let { getApplicationInfo(it, packageManager) }
    ?.loadUnbadgedIcon(packageManager)
    ?.toBitmap()
    ?.asImageBitmap()

private fun getApplicationInfo(
  packageName: String,
  packageManager: PackageManager,
): ApplicationInfo? =
  try {
    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
  } catch (e: Throwable) {
    null
  }
