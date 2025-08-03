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

package com.android.permissioncontroller.permission.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.Text
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ListSubheader
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen

/** Compose the screen associated to a [WearPermissionAppsFragment]. */
@Composable
fun WearPermissionAppsScreen(helper: WearPermissionAppsHelper) {
    val categorizedApps = helper.categorizedAppsLiveData().observeAsState(emptyMap())
    val hasSystemApps = helper.hasSystemAppsLiveData().observeAsState(false)
    val showSystem = helper.shouldShowSystemLiveData().observeAsState(false)
    val appPermissionUsages = helper.wearViewModel.appPermissionUsages.observeAsState(emptyList())
    var isLoading by remember { mutableStateOf(true) }

    val title = helper.getTitle()
    val subTitle = helper.getSubTitle()
    val showAlways = helper.showAlways()
    val chipsByCategory =
        helper.getChipsByCategory(categorizedApps.value, appPermissionUsages.value)

    WearPermissionAppsContent(
        chipsByCategory,
        showSystem.value,
        hasSystemApps.value,
        title,
        subTitle,
        showAlways,
        isLoading,
        helper.onShowSystemClick
    )

    if (isLoading && categorizedApps.value.isNotEmpty()) {
        isLoading = false
    }
    helper.setCreationLogged(true)
}

@Composable
internal fun WearPermissionAppsContent(
    chipsByCategory: Map<String, List<ChipInfo>>,
    showSystem: Boolean,
    hasSystemApps: Boolean,
    title: String,
    subtitle: String,
    showAlways: Boolean,
    isLoading: Boolean,
    onShowSystemClick: (showSystem: Boolean) -> Unit
) {
    ScrollableScreen(title = title, subtitle = subtitle, isLoading = isLoading) {
        for (category in categoryOrder) {
            val chips = chipsByCategory[category]
            if (chips.isNullOrEmpty()) {
                continue
            }
            item {
                ListSubheader {
                    Text(text = stringResource(getCategoryString(category, showAlways)))
                }
            }
            chips.forEach {
                item {
                    Chip(
                        label = it.title,
                        labelMaxLines = Int.MAX_VALUE,
                        secondaryLabel = it.summary,
                        secondaryLabelMaxLines = Int.MAX_VALUE,
                        icon = it.icon,
                        enabled = it.enabled,
                        onClick = { it.onClick() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (hasSystemApps) {
            item {
                Chip(
                    label =
                        if (showSystem) {
                            stringResource(R.string.menu_hide_system)
                        } else {
                            stringResource(R.string.menu_show_system)
                        },
                    labelMaxLines = Int.MAX_VALUE,
                    onClick = { onShowSystemClick(!showSystem) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

internal fun getCategoryString(category: String, showAlways: Boolean) =
    when (category) {
        "allowed_storage_full" -> R.string.allowed_storage_full
        "allowed_storage_scoped" -> R.string.allowed_storage_scoped
        Category.ALLOWED.categoryName ->
            if (showAlways) {
                R.string.allowed_always_header
            } else {
                R.string.allowed_header
            }
        Category.ALLOWED_FOREGROUND.categoryName -> R.string.allowed_foreground_header
        Category.ASK.categoryName -> R.string.ask_header
        Category.DENIED.categoryName -> R.string.denied_header
        else -> throw IllegalArgumentException("Wrong category: $category")
    }

internal val categoryOrder =
    listOf(
        "allowed_storage_full",
        "allowed_storage_scoped",
        Category.ALLOWED.categoryName,
        Category.ALLOWED_FOREGROUND.categoryName,
        Category.ASK.categoryName,
        Category.DENIED.categoryName
    )
