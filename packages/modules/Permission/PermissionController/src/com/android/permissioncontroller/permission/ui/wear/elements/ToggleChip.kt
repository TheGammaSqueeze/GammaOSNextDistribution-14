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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipColors
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.contentColorFor
import com.android.permissioncontroller.R

/**
 * This component is an alternative to [ToggleChip], providing the following:
 * - a convenient way of providing a label and a secondary label;
 * - a convenient way of choosing the toggle control;
 * - a convenient way of providing an icon and setting the icon to be mirrored in RTL mode;
 */
@Composable
public fun ToggleChip(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    label: String,
    labelMaxLine: Int? = null,
    toggleControl: ToggleChipToggleControl,
    modifier: Modifier = Modifier,
    icon: Any? = null,
    iconColor: Color = Color.Unspecified,
    iconRtlMode: IconRtlMode = IconRtlMode.Default,
    secondaryLabel: String? = null,
    secondaryLabelMaxLine: Int? = null,
    colors: ToggleChipColors = ToggleChipDefaults.toggleChipColors(),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val hasSecondaryLabel = secondaryLabel != null

    val labelParam: (@Composable RowScope.() -> Unit) = {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            overflow = TextOverflow.Ellipsis,
            maxLines = labelMaxLine ?: if (hasSecondaryLabel) 1 else 2,
            style = MaterialTheme.typography.button
        )
    }

    val secondaryLabelParam: (@Composable RowScope.() -> Unit)? =
        secondaryLabel?.let {
            {
                Text(
                    text = secondaryLabel,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = secondaryLabelMaxLine ?: 1,
                    style = MaterialTheme.typography.caption2
                )
            }
        }

    val toggleControlParam: (@Composable () -> Unit) = {
        Icon(
            imageVector =
                when (toggleControl) {
                    ToggleChipToggleControl.Switch -> ToggleChipDefaults.switchIcon(checked)
                    ToggleChipToggleControl.Radio -> ToggleChipDefaults.radioIcon(checked)
                    ToggleChipToggleControl.Checkbox -> ToggleChipDefaults.checkboxIcon(checked)
                },
            contentDescription = null,
            // This potentially be removed once this issue is addressed:
            // https://issuetracker.google.com/issues/287087138
            rtlMode =
                if (toggleControl == ToggleChipToggleControl.Switch) {
                    IconRtlMode.Mirrored
                } else {
                    IconRtlMode.Default
                }
        )
    }

    val iconParam: (@Composable BoxScope.() -> Unit)? =
        icon?.let {
            {
                Row {
                    Icon(
                        icon = icon,
                        tint = iconColor,
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape),
                        rtlMode = iconRtlMode
                    )
                }
            }
        }

    val stateDescriptionSemantics =
        stringResource(
            if (checked) {
                R.string.on
            } else {
                R.string.off
            }
        )
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChanged,
        label = labelParam,
        toggleControl = toggleControlParam,
        modifier =
            modifier
                .adjustChipHeightToFontScale(LocalConfiguration.current.fontScale)
                .fillMaxWidth()
                .semantics { stateDescription = stateDescriptionSemantics },
        appIcon = iconParam,
        secondaryLabel = secondaryLabelParam,
        colors = colors,
        enabled = enabled,
        interactionSource = interactionSource
    )
}

/**
 * ToggleChipColors that disabled alpha is applied based on [ToggleChipDefaults.toggleChipColors()].
 * It is used for a ToggleChip which would like to respond to click events, meanwhile it seems
 * disabled.
 */
@Composable
fun toggleChipDisabledColors(): ToggleChipColors {
    val checkedStartBackgroundColor =
        MaterialTheme.colors.surface.copy(alpha = 0f).compositeOver(MaterialTheme.colors.surface)
    val checkedEndBackgroundColor =
        MaterialTheme.colors.primary.copy(alpha = 0.5f).compositeOver(MaterialTheme.colors.surface)
    val checkedContentColor = MaterialTheme.colors.onSurface
    val checkedSecondaryContentColor = MaterialTheme.colors.onSurfaceVariant
    val checkedToggleControlColor = MaterialTheme.colors.secondary
    val uncheckedStartBackgroundColor = MaterialTheme.colors.surface
    val uncheckedEndBackgroundColor = uncheckedStartBackgroundColor
    val uncheckedContentColor = contentColorFor(checkedEndBackgroundColor)
    val uncheckedSecondaryContentColor = uncheckedContentColor
    val uncheckedToggleControlColor = uncheckedContentColor

    return ToggleChipDefaults.toggleChipColors(
        checkedStartBackgroundColor =
            checkedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
        checkedEndBackgroundColor = checkedEndBackgroundColor.copy(alpha = ContentAlpha.disabled),
        checkedContentColor = checkedContentColor.copy(alpha = ContentAlpha.disabled),
        checkedSecondaryContentColor =
            checkedSecondaryContentColor.copy(alpha = ContentAlpha.disabled),
        checkedToggleControlColor = checkedToggleControlColor.copy(alpha = ContentAlpha.disabled),
        uncheckedStartBackgroundColor =
            uncheckedStartBackgroundColor.copy(alpha = ContentAlpha.disabled),
        uncheckedEndBackgroundColor =
            uncheckedEndBackgroundColor.copy(alpha = ContentAlpha.disabled),
        uncheckedContentColor = uncheckedContentColor.copy(alpha = ContentAlpha.disabled),
        uncheckedSecondaryContentColor =
            uncheckedSecondaryContentColor.copy(alpha = ContentAlpha.disabled),
        uncheckedToggleControlColor =
            uncheckedToggleControlColor.copy(alpha = ContentAlpha.disabled)
    )
}

/**
 * ToggleChipColors that theme background color is applied based on
 * [ToggleChipDefaults.toggleChipColors()]. It is used for a ToggleChip having the same background
 * color of the screen.
 */
@Composable
fun toggleChipBackgroundColors(): ToggleChipColors {
    val checkedStartBackgroundColor =
        MaterialTheme.colors.background
            .copy(alpha = 0f)
            .compositeOver(MaterialTheme.colors.background)
    val checkedEndBackgroundColor =
        MaterialTheme.colors.primary
            .copy(alpha = 0.5f)
            .compositeOver(MaterialTheme.colors.background)
    val checkedContentColor = MaterialTheme.colors.onBackground
    val checkedSecondaryContentColor = MaterialTheme.colors.onSurfaceVariant
    val checkedToggleControlColor = MaterialTheme.colors.secondary
    val uncheckedStartBackgroundColor = MaterialTheme.colors.background
    val uncheckedEndBackgroundColor = uncheckedStartBackgroundColor
    val uncheckedContentColor = contentColorFor(checkedEndBackgroundColor)
    val uncheckedSecondaryContentColor = uncheckedContentColor
    val uncheckedToggleControlColor = uncheckedContentColor

    return ToggleChipDefaults.toggleChipColors(
        checkedStartBackgroundColor = checkedStartBackgroundColor,
        checkedEndBackgroundColor = checkedEndBackgroundColor,
        checkedContentColor = checkedContentColor,
        checkedSecondaryContentColor = checkedSecondaryContentColor,
        checkedToggleControlColor = checkedToggleControlColor,
        uncheckedStartBackgroundColor = uncheckedStartBackgroundColor,
        uncheckedEndBackgroundColor = uncheckedEndBackgroundColor,
        uncheckedContentColor = uncheckedContentColor,
        uncheckedSecondaryContentColor = uncheckedSecondaryContentColor,
        uncheckedToggleControlColor = uncheckedToggleControlColor
    )
}
