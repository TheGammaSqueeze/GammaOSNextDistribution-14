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

import android.os.Build
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
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.handheld.v31.PermissionUsageControlPreference
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModel
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import java.text.Collator

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WearPermissionUsageScreen(sessionId: Long, viewModel: PermissionUsageViewModel) {
    val context = LocalContext.current
    val permissionUsagesUiData = viewModel.permissionUsagesUiLiveData.observeAsState(null)
    val showSystem = viewModel.showSystemAppsLiveData.observeAsState(false)
    val show7Days = viewModel.show7DaysLiveData.observeAsState(false)
    var isLoading by remember { mutableStateOf(true) }

    val hasSystemApps: Boolean = permissionUsagesUiData.value?.containsSystemAppUsages ?: false
    val onShowSystemClick: (Boolean) -> Unit = { show -> run { viewModel.updateShowSystem(show) } }

    val permissionGroupWithUsageCounts: Map<String, Int> =
        permissionUsagesUiData.value?.permissionGroupsWithUsageCount ?: emptyMap()
    val permissionGroupWithUsageCountsEntries: List<Map.Entry<String, Int>> =
        ArrayList<Map.Entry<String, Int>>(permissionGroupWithUsageCounts.entries)

    val collator = Collator.getInstance(context.resources.configuration.locales.get(0))
    val permissionGroupPreferences =
        permissionGroupWithUsageCountsEntries
            .map {
                PermissionUsageControlPreference(
                    context,
                    it.key,
                    it.value,
                    showSystem.value,
                    sessionId,
                    show7Days.value
                )
            }
            .sortedWith { o1, o2 ->
                var result = collator.compare(o1.title.toString(), o2.title.toString())
                if (result == 0) {
                    result = o1.title.toString().compareTo(o2.title.toString())
                }
                result
            }
            .toList()

    WearPermissionUsageContent(
        isLoading,
        hasSystemApps,
        showSystem.value,
        onShowSystemClick,
        permissionGroupPreferences
    )

    if (isLoading && permissionUsagesUiData.value != null) {
        isLoading = false
    }
}

@Composable
internal fun WearPermissionUsageContent(
    isLoading: Boolean,
    hasSystemApps: Boolean,
    showSystem: Boolean,
    onShowSystemClick: (Boolean) -> Unit,
    permissionGroupPreferences: List<PermissionUsageControlPreference>
) {
    ScrollableScreen(
        title = stringResource(R.string.permission_usage_title),
        isLoading = isLoading
    ) {
        if (permissionGroupPreferences.isEmpty()) {
            item { Chip(label = stringResource(R.string.no_permissions), onClick = {}) }
        } else {
            for (preference in permissionGroupPreferences) {
                item {
                    Chip(
                        label = preference.title.toString(),
                        labelMaxLines = Int.MAX_VALUE,
                        secondaryLabel = preference.summary.toString(),
                        secondaryLabelMaxLines = Int.MAX_VALUE,
                        icon = preference.icon,
                        enabled = preference.isEnabled,
                        onClick = { preference.performClick() }
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
        }
    }
}
