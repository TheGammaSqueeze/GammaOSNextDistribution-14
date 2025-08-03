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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.wear.elements.AlertDialog
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChip
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChipToggleControl
import com.android.permissioncontroller.permission.ui.wear.model.RevokeDialogArgs

@Composable
fun WearAppPermissionGroupsScreen(helper: WearAppPermissionGroupsHelper) {
    val packagePermGroups = helper.viewModel.packagePermGroupsLiveData.observeAsState(null)
    val autoRevoke = helper.viewModel.autoRevokeLiveData.observeAsState(null)
    val appPermissionUsages = helper.wearViewModel.appPermissionUsages.observeAsState(emptyList())
    val showRevokeDialog = helper.revokeDialogViewModel.showDialogLiveData.observeAsState(false)
    var isLoading by remember { mutableStateOf(true) }

    Box {
        WearAppPermissionGroupsContent(
            isLoading,
            helper.getPermissionGroupChipParams(appPermissionUsages.value),
            helper.getAutoRevokeChipParam(autoRevoke.value)
        )
        RevokeDialog(
            showDialog = showRevokeDialog.value,
            args = helper.revokeDialogViewModel.revokeDialogArgs
        )
    }

    if (isLoading && !packagePermGroups.value.isNullOrEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun WearAppPermissionGroupsContent(
    isLoading: Boolean,
    permissionGroupChipParams: List<PermissionGroupChipParam>,
    autoRevokeChipParam: AutoRevokeChipParam?
) {
    ScrollableScreen(title = stringResource(R.string.app_permissions), isLoading = isLoading) {
        if (permissionGroupChipParams.isEmpty()) {
            item { Chip(label = stringResource(R.string.no_permissions), onClick = {}) }
        } else {
            for (info in permissionGroupChipParams) {
                item {
                    if (info.checked != null) {
                        ToggleChip(
                            checked = info.checked,
                            label = info.label,
                            enabled = info.enabled,
                            toggleControl = ToggleChipToggleControl.Switch,
                            onCheckedChanged = info.onCheckedChanged
                        )
                    } else {
                        Chip(
                            label = info.label,
                            labelMaxLines = Integer.MAX_VALUE,
                            secondaryLabel = info.summary?.let { info.summary },
                            secondaryLabelMaxLines = Integer.MAX_VALUE,
                            enabled = info.enabled,
                            onClick = info.onClick
                        )
                    }
                }
            }
            autoRevokeChipParam?.let {
                if (it.visible) {
                    item {
                        ToggleChip(
                            checked = it.checked,
                            label = stringResource(it.labelRes),
                            labelMaxLine = 3,
                            toggleControl = ToggleChipToggleControl.Switch,
                            onCheckedChanged = it.onCheckedChanged
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RevokeDialog(showDialog: Boolean, args: RevokeDialogArgs?) {
    args?.let {
        AlertDialog(
            showDialog = showDialog,
            message = stringResource(it.messageId),
            onOKButtonClick = it.onOkButtonClick,
            onCancelButtonClick = it.onCancelButtonClick,
            scalingLazyListState = rememberScalingLazyListState()
        )
    }
}
