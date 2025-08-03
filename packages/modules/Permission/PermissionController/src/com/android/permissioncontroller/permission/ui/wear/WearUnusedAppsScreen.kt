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

import android.icu.text.MessageFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.Text
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod.Companion.allPeriods
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.Icon
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.model.WearUnusedAppsViewModel

@Composable
fun WearUnusedAppsScreen(viewModel: WearUnusedAppsViewModel) {
    val loading = viewModel.loadingLiveData.observeAsState(true)
    val unusedPeriodCategoryVisibilities =
        viewModel.unusedPeriodCategoryVisibilitiesLiveData.observeAsState(emptyList())
    val infoMsgCategoryVisibility =
        viewModel.infoMsgCategoryVisibilityLiveData.observeAsState(false)
    val unusedAppChips = viewModel.unusedAppChipsLiveData.observeAsState(mapOf())

    ScrollableScreen(
        showTimeText = true,
        title = getScreenTitle(),
        isLoading = loading.value,
        subtitle = getSubTitle(!infoMsgCategoryVisibility.value)
    ) {
        for (period in allPeriods) {
            if (!unusedAppChips.value.containsKey(period)) {
                continue
            }
            item {
                val pos = posByPeriod(period)
                if (unusedPeriodCategoryVisibilities.value.getOrElse(pos) { false }) {
                    Text(text = categoryTitleByPeriod(period))
                }
            }
            for (unusedAppChip in unusedAppChips.value[period]!!.values) {
                item {
                    Chip(
                        label = unusedAppChip.label,
                        secondaryLabel = unusedAppChip.summary,
                        icon = unusedAppChip.icon,
                        iconContentDescription = unusedAppChip.contentDescription,
                        onClick = unusedAppChip.onClick
                    )
                }
            }
        }
        // For info_msg_category
        if (infoMsgCategoryVisibility.value) {
            item { Icon(icon = R.drawable.ic_info_outline, contentDescription = null) }
            if (isHibernationEnabled()) {
                item { Text(text = stringResource(R.string.unused_apps_page_summary)) }
            } else {
                item { Text(text = stringResource(R.string.auto_revoked_apps_page_summary)) }
                item { Text(text = stringResource(R.string.auto_revoke_open_app_message)) }
            }
        }
    }
}

@Composable
private fun getScreenTitle() =
    if (isHibernationEnabled()) {
        stringResource(R.string.unused_apps_page_title)
    } else {
        stringResource(R.string.permission_removed_page_title)
    }

@Composable
private fun getSubTitle(shouldShow: Boolean) =
    if (shouldShow) {
        stringResource(R.string.no_unused_apps)
    } else {
        null
    }

@Composable
private fun posByPeriod(period: UnusedPeriod) =
    when (period) {
        UnusedPeriod.ONE_MONTH -> 0
        UnusedPeriod.THREE_MONTHS -> 1
        UnusedPeriod.SIX_MONTHS -> 2
    }

@Composable
private fun categoryTitleByPeriod(period: UnusedPeriod) =
    MessageFormat.format(
        stringResource(R.string.last_opened_category_title),
        mapOf("count" to period.months)
    )
