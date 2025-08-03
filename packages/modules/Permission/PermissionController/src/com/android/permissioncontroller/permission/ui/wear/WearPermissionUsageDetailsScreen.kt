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

import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.AppPermissionAccessUiInfo
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.utils.KotlinUtils

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WearPermissionUsageDetailsScreen(
    permissionGroup: String,
    viewModel: PermissionUsageDetailsViewModel
) {
    val context = LocalContext.current
    val uiData = viewModel.permissionUsagesDetailsInfoUiLiveData.observeAsState(null)
    val showSystem = viewModel.showSystemLiveData.observeAsState(false)
    var isLoading by remember { mutableStateOf(true) }

    val title = stringResource(R.string.permission_history_title)
    val subtitle =
        stringResource(
            R.string.permission_group_usage_title,
            KotlinUtils.getPermGroupLabel(context, permissionGroup)
        )
    val hasSystemApps: Boolean = uiData.value?.containsSystemAppAccesses ?: false
    val onShowSystemClick: (Boolean) -> Unit = { show ->
        run { viewModel.updateShowSystemAppsToggle(show) }
    }
    val onChipClick: (AppPermissionAccessUiInfo) -> Unit = { uiInfo ->
        run {
            val intent =
                PermissionUsageDetailsViewModel.createHistoryPreferenceClickIntent(
                    context,
                    uiInfo.userHandle,
                    uiInfo.packageName,
                    uiInfo.permissionGroup,
                    uiInfo.accessStartTime,
                    uiInfo.accessEndTime,
                    uiInfo.showingAttribution,
                    uiInfo.attributionTags
                )
            context.startActivityAsUser(intent, uiInfo.userHandle)
        }
    }
    val onManagePermissionClick: () -> Unit = {
        val intent: Intent =
            Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_NAME, permissionGroup)
        context.startActivity(intent)
    }

    val appPermissionAccessUiInfoList: List<AppPermissionAccessUiInfo> =
        uiData.value?.appPermissionAccessUiInfoList ?: emptyList()

    WearPermissionUsageDetailsContent(
        title,
        subtitle,
        isLoading,
        hasSystemApps,
        showSystem.value,
        onShowSystemClick,
        appPermissionAccessUiInfoList,
        onChipClick,
        onManagePermissionClick
    )

    if (isLoading && uiData.value != null) {
        isLoading = false
    }
}

@Composable
internal fun WearPermissionUsageDetailsContent(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    hasSystemApps: Boolean,
    showSystem: Boolean,
    onShowSystemClick: (Boolean) -> Unit,
    appPermissionAccessUiInfoList: List<AppPermissionAccessUiInfo>,
    onChipClick: (AppPermissionAccessUiInfo) -> Unit,
    onManagePermissionClick: () -> Unit
) {
    ScrollableScreen(title = title, subtitle = subtitle, isLoading = isLoading) {
        if (appPermissionAccessUiInfoList.isEmpty()) {
            item { Chip(label = stringResource(R.string.no_apps), onClick = {}) }
        } else {
            for (uiInfo in appPermissionAccessUiInfoList) {
                item {
                    Chip(
                        label = uiInfo.packageLabel,
                        labelMaxLines = Int.MAX_VALUE,
                        secondaryLabel =
                            DateFormat.getTimeFormat(LocalContext.current)
                                .format(uiInfo.accessEndTime),
                        secondaryLabelMaxLines = Int.MAX_VALUE,
                        icon = uiInfo.badgedPackageIcon,
                        onClick = { onChipClick(uiInfo) }
                    )
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
            item {
                Chip(
                    label = stringResource(R.string.manage_permission),
                    textColor = MaterialTheme.colors.background,
                    colors = ChipDefaults.primaryChipColors(),
                    onClick = { onManagePermissionClick() },
                )
            }
        }
    }
}
