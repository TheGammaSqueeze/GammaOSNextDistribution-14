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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** A slot based composable for creating a list footer item. */
@Composable
fun ListFooter(description: String, iconRes: Int? = null, onClick: (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth()
    Row(
        modifier =
            if (onClick == null) {
                modifier
            } else {
                modifier.clickable(onClick = onClick)
            }
    ) {
        iconRes?.let {
            Spacer(modifier = Modifier.width(LeadingIconStartSpacing))
            Icon(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier =
                    Modifier.size(LeadingIconSize, LeadingIconSize)
                        .align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(LeadingIconEndSpacing))
        }
        Text(
            text = description,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colors.onSurfaceVariant,
            style = MaterialTheme.typography.caption2
        )
    }
}

/** The size of the spacing before the leading icon when they used inside a list footer. */
private val LeadingIconStartSpacing = 4.dp

/** The size of the spacing between the leading icon and a text inside a list footer. */
private val LeadingIconEndSpacing = 8.dp

/** The size of the leading icon. */
private val LeadingIconSize = 24.dp
