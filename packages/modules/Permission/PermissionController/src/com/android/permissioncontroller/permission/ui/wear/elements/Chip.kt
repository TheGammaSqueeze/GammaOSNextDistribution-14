/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear.elements

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.contentColorFor
import com.android.permissioncontroller.R

/**
 * This component is an alternative to [Chip], providing the following:
 * - a convenient way of providing a label and a secondary label;
 * - a convenient way of providing an icon, and choosing their size based on the sizes recommended
 *   by the Wear guidelines;
 */
@Composable
public fun Chip(
    label: String,
    labelMaxLines: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    secondaryLabelMaxLines: Int? = null,
    icon: Any? = null,
    iconContentDescription: String? = null,
    largeIcon: Boolean = false,
    textColor: Color = MaterialTheme.colors.onSurface,
    iconColor: Color = Color.Unspecified,
    colors: ChipColors = chipDefaultColors(),
    enabled: Boolean = true
) {
    val iconParam: (@Composable BoxScope.() -> Unit)? =
        icon?.let {
            {
                val iconSize =
                    if (largeIcon) {
                        ChipDefaults.LargeIconSize
                    } else {
                        ChipDefaults.IconSize
                    }

                Row {
                    val iconModifier = Modifier.size(iconSize).clip(CircleShape)
                    when (icon) {
                        is ImageVector ->
                            Icon(
                                imageVector = icon,
                                tint = iconColor,
                                contentDescription = iconContentDescription,
                                modifier = iconModifier
                            )
                        is Int ->
                            Icon(
                                painter = painterResource(id = icon),
                                tint = iconColor,
                                contentDescription = iconContentDescription,
                                modifier = iconModifier
                            )
                        is Drawable ->
                            Icon(
                                painter = rememberDrawablePainter(icon),
                                tint = iconColor,
                                contentDescription = iconContentDescription,
                                modifier = iconModifier
                            )
                        else -> {}
                    }
                }
            }
        }

    Chip(
        label = label,
        labelMaxLines = labelMaxLines,
        onClick = onClick,
        modifier = modifier,
        secondaryLabel = secondaryLabel,
        secondaryLabelMaxLines = secondaryLabelMaxLines,
        icon = iconParam,
        largeIcon = largeIcon,
        textColor = textColor,
        colors = colors,
        enabled = enabled
    )
}

/**
 * This component is an alternative to [Chip], providing the following:
 * - a convenient way of providing a label and a secondary label;
 * - a convenient way of providing an icon, and choosing their size based on the sizes recommended
 *   by the Wear guidelines;
 */
@Composable
public fun Chip(
    @StringRes labelId: Int,
    labelMaxLines: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes secondaryLabel: Int? = null,
    secondaryLabelMaxLines: Int? = null,
    icon: Any? = null,
    largeIcon: Boolean = false,
    textColor: Color = MaterialTheme.colors.onSurface,
    iconColor: Color = Color.Unspecified,
    colors: ChipColors = chipDefaultColors(),
    enabled: Boolean = true
) {
    Chip(
        label = stringResource(id = labelId),
        labelMaxLines = labelMaxLines,
        onClick = onClick,
        modifier = modifier,
        secondaryLabel = secondaryLabel?.let { stringResource(id = it) },
        secondaryLabelMaxLines = secondaryLabelMaxLines,
        icon = icon,
        largeIcon = largeIcon,
        textColor = textColor,
        iconColor = iconColor,
        colors = colors,
        enabled = enabled
    )
}

/**
 * This component is an alternative to [Chip], providing the following:
 * - a convenient way of providing a label and a secondary label;
 */
@Composable
public fun Chip(
    label: String,
    labelMaxLines: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    secondaryLabelMaxLines: Int? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    largeIcon: Boolean = false,
    textColor: Color = MaterialTheme.colors.onSurface,
    secondaryTextColor: Color = colorResource(R.color.wear_material_gray_600),
    colors: ChipColors = chipDefaultColors(),
    enabled: Boolean = true
) {
    val hasSecondaryLabel = secondaryLabel != null
    val hasIcon = icon != null

    val labelParam: (@Composable RowScope.() -> Unit) = {
        Text(
            text = label,
            color = textColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (hasSecondaryLabel || hasIcon) TextAlign.Start else TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = labelMaxLines ?: if (hasSecondaryLabel) 1 else 2,
            style = MaterialTheme.typography.button
        )
    }

    val secondaryLabelParam: (@Composable RowScope.() -> Unit)? =
        secondaryLabel?.let {
            {
                Text(
                    text = secondaryLabel,
                    color = secondaryTextColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = secondaryLabelMaxLines ?: 1,
                    style = MaterialTheme.typography.caption2
                )
            }
        }

    val contentPadding =
        if (largeIcon) {
            val verticalPadding = ChipDefaults.ChipVerticalPadding
            PaddingValues(
                start = 10.dp,
                top = verticalPadding,
                end = ChipDefaults.ChipHorizontalPadding,
                bottom = verticalPadding
            )
        } else {
            ChipDefaults.ContentPadding
        }

    Chip(
        label = labelParam,
        onClick = onClick,
        modifier =
            modifier
                .adjustChipHeightToFontScale(LocalConfiguration.current.fontScale)
                .fillMaxWidth(),
        secondaryLabel = secondaryLabelParam,
        icon = icon,
        colors = colors,
        enabled = enabled,
        contentPadding = contentPadding,
        shape = RoundedCornerShape(26.dp)
    )
}

/** Default colors of a Chip. */
@Composable fun chipDefaultColors(): ChipColors = ChipDefaults.secondaryChipColors()

/**
 * ChipColors that disabled alpha is applied based on [ChipDefaults.secondaryChipColors()]. It is
 * used for a Chip which would like to respond to click events, meanwhile it seems disabled.
 */
@Composable
fun chipDisabledColors(): ChipColors {
    val backgroundColor = MaterialTheme.colors.surface
    val contentColor = contentColorFor(backgroundColor)
    val secondaryContentColor = contentColor
    val iconColor = contentColor

    return ChipDefaults.chipColors(
        backgroundColor = backgroundColor.copy(alpha = ContentAlpha.disabled),
        contentColor = contentColor.copy(alpha = ContentAlpha.disabled),
        secondaryContentColor = secondaryContentColor.copy(alpha = ContentAlpha.disabled),
        iconColor = iconColor.copy(alpha = ContentAlpha.disabled)
    )
}
