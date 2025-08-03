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

package com.android.permissioncontroller.role.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LiveData
import androidx.wear.compose.material.Text
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.chipDefaultColors
import com.android.permissioncontroller.permission.ui.wear.elements.chipDisabledColors
import com.android.permissioncontroller.role.ui.RoleItem

@Composable
fun WearDefaultAppListScreen(
    helper: WearDefaultAppListHelper,
    defaultAppsLiveData: LiveData<List<RoleItem>>,
) {
    val defaultAppsState = defaultAppsLiveData.observeAsState(emptyList())
    val defaultApps: List<RoleItem> by remember { derivedStateOf { defaultAppsState.value } }
    val preferences = helper.getPreferences(defaultApps)
    var isLoading by remember { mutableStateOf(true) }

    ScrollableScreen(title = stringResource(R.string.default_apps), isLoading = isLoading) {
        if (preferences.isEmpty()) {
            item { Text(stringResource(R.string.no_default_apps)) }
            return@ScrollableScreen
        }
        preferences.forEach { pref ->
            item {
                Chip(
                    label = pref.label,
                    icon = pref.icon,
                    colors =
                        if (pref.isEnabled()) {
                            chipDefaultColors()
                        } else {
                            chipDisabledColors()
                        },
                    secondaryLabel = pref.summary?.toString(),
                    onClick = pref.getOnClicked(),
                    modifier = Modifier.fillMaxWidth(),
                    labelMaxLines = Int.MAX_VALUE,
                    secondaryLabelMaxLines = Integer.MAX_VALUE
                )
            }
        }
    }
    if (isLoading && defaultApps.isNotEmpty()) {
        isLoading = false
    }
}
