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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.model.ManageCustomPermissionsViewModel
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen

@Composable
fun WearManageCustomPermissionScreen(
    viewModel: ManageCustomPermissionsViewModel,
    onPermGroupClick: (String) -> Unit
) {
    val permissionGroups = viewModel.uiDataLiveData.observeAsState(emptyMap())
    var isLoading by remember { mutableStateOf(true) }

    WearManageCustomPermissionContent(
        isLoading,
        getPermGroupChipParams(permissionGroups.value),
        onPermGroupClick
    )

    if (isLoading && permissionGroups.value.isNotEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun WearManageCustomPermissionContent(
    isLoading: Boolean,
    permGroupChipParams: List<PermGroupChipParam>,
    onPermGroupClick: (String) -> Unit
) {
    ScrollableScreen(
        title = stringResource(R.string.additional_permissions),
        isLoading = isLoading
    ) {
        for (params in permGroupChipParams) {
            item {
                Chip(
                    label = params.label,
                    labelMaxLines = 3,
                    icon = params.icon,
                    secondaryLabel = params.secondaryLabel,
                    secondaryLabelMaxLines = 3,
                    onClick = { onPermGroupClick(params.permGroupName) }
                )
            }
        }
    }
}
