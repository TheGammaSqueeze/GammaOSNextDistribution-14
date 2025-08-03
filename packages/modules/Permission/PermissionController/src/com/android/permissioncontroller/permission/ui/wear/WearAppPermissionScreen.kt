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
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonState
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs
import com.android.permissioncontroller.permission.ui.wear.elements.AlertDialog
import com.android.permissioncontroller.permission.ui.wear.elements.ListFooter
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChip
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChipToggleControl
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionConfirmDialogViewModel
import com.android.permissioncontroller.permission.ui.wear.model.ConfirmDialogArgs
import com.android.settingslib.RestrictedLockUtils

@Composable
fun WearAppPermissionScreen(
    title: String,
    viewModel: AppPermissionViewModel,
    confirmDialogViewModel: AppPermissionConfirmDialogViewModel,
    onLocationSwitchChanged: (Boolean) -> Unit,
    onGrantedStateChanged: (ButtonType, Boolean) -> Unit,
    onFooterClicked: (RestrictedLockUtils.EnforcedAdmin) -> Unit,
    onConfirmDialogOkButtonClick: (ConfirmDialogArgs) -> Unit,
    onConfirmDialogCancelButtonClick: () -> Unit,
    onAdvancedConfirmDialogOkButtonClick: (AdvancedConfirmDialogArgs) -> Unit,
    onAdvancedConfirmDialogCancelButtonClick: () -> Unit
) {
    val buttonState = viewModel.buttonStateLiveData.observeAsState(null)
    val detailResIds = viewModel.detailResIdLiveData.observeAsState(null)
    val admin = viewModel.showAdminSupportLiveData.observeAsState(null)
    var isLoading by remember { mutableStateOf(true) }
    val showConfirmDialog = confirmDialogViewModel.showConfirmDialogLiveData.observeAsState(false)
    val showAdvancedConfirmDialog =
        confirmDialogViewModel.showAdvancedConfirmDialogLiveData.observeAsState(false)

    Box {
        WearAppPermissionContent(
            title,
            buttonState.value,
            detailResIds.value,
            admin.value,
            isLoading,
            onLocationSwitchChanged,
            onGrantedStateChanged,
            onFooterClicked,
        )
        ConfirmDialog(
            showDialog = showConfirmDialog.value,
            args = confirmDialogViewModel.confirmDialogArgs,
            onOkButtonClick = onConfirmDialogOkButtonClick,
            onCancelButtonClick = onConfirmDialogCancelButtonClick
        )
        AdvancedConfirmDialog(
            showDialog = showAdvancedConfirmDialog.value,
            args = confirmDialogViewModel.advancedConfirmDialogArgs,
            onOkButtonClick = onAdvancedConfirmDialogOkButtonClick,
            onCancelButtonClick = onAdvancedConfirmDialogCancelButtonClick
        )
    }
    if (isLoading && !buttonState.value.isNullOrEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun WearAppPermissionContent(
    title: String,
    buttonState: Map<ButtonType, ButtonState>?,
    detailResIds: Pair<Int, Int?>?,
    admin: RestrictedLockUtils.EnforcedAdmin?,
    isLoading: Boolean,
    onLocationSwitchChanged: (Boolean) -> Unit,
    onGrantedStateChanged: (ButtonType, Boolean) -> Unit,
    onFooterClicked: (RestrictedLockUtils.EnforcedAdmin) -> Unit
) {
    ScrollableScreen(title = title, isLoading = isLoading) {
        buttonState?.get(ButtonType.LOCATION_ACCURACY)?.let {
            if (it.isShown) {
                item {
                    ToggleChip(
                        checked = it.isChecked,
                        enabled = it.isEnabled,
                        label = stringResource(R.string.app_permission_location_accuracy),
                        toggleControl = ToggleChipToggleControl.Switch,
                        onCheckedChanged = onLocationSwitchChanged,
                        labelMaxLine = Integer.MAX_VALUE
                    )
                }
            }
        }
        for (buttonType in buttonTypeOrder) {
            buttonState?.get(buttonType)?.let {
                if (it.isShown) {
                    item {
                        ToggleChip(
                            checked = it.isChecked,
                            enabled = it.isEnabled,
                            label = labelsByButton(buttonType),
                            toggleControl = ToggleChipToggleControl.Radio,
                            onCheckedChanged = { checked ->
                                onGrantedStateChanged(buttonType, checked)
                            },
                            labelMaxLine = Integer.MAX_VALUE
                        )
                    }
                }
            }
        }
        detailResIds?.let {
            item {
                ListFooter(
                    description = stringResource(detailResIds.first),
                    iconRes = R.drawable.ic_info,
                    onClick =
                        if (admin != null) {
                            { onFooterClicked(admin) }
                        } else {
                            null
                        }
                )
            }
        }
    }
}

internal val buttonTypeOrder =
    listOf(
        ButtonType.ALLOW,
        ButtonType.ALLOW_ALWAYS,
        ButtonType.ALLOW_FOREGROUND,
        ButtonType.ASK_ONCE,
        ButtonType.ASK,
        ButtonType.DENY,
        ButtonType.DENY_FOREGROUND
    )

@Composable
internal fun labelsByButton(buttonType: ButtonType) =
    when (buttonType) {
        ButtonType.ALLOW -> stringResource(R.string.app_permission_button_allow)
        ButtonType.ALLOW_ALWAYS -> stringResource(R.string.app_permission_button_allow_always)
        ButtonType.ALLOW_FOREGROUND ->
            stringResource(R.string.app_permission_button_allow_foreground)
        ButtonType.ASK_ONCE -> stringResource(R.string.app_permission_button_ask)
        ButtonType.ASK -> stringResource(R.string.app_permission_button_ask)
        ButtonType.DENY -> stringResource(R.string.app_permission_button_deny)
        ButtonType.DENY_FOREGROUND -> stringResource(R.string.app_permission_button_deny)
        else -> ""
    }

@Composable
internal fun ConfirmDialog(
    showDialog: Boolean,
    args: ConfirmDialogArgs?,
    onOkButtonClick: (ConfirmDialogArgs) -> Unit,
    onCancelButtonClick: () -> Unit
) {
    args?.let {
        AlertDialog(
            showDialog = showDialog,
            message = stringResource(it.messageId),
            onOKButtonClick = { onOkButtonClick(it) },
            onCancelButtonClick = onCancelButtonClick,
            scalingLazyListState = rememberScalingLazyListState()
        )
    }
}

@Composable
internal fun AdvancedConfirmDialog(
    showDialog: Boolean,
    args: AdvancedConfirmDialogArgs?,
    onOkButtonClick: (AdvancedConfirmDialogArgs) -> Unit,
    onCancelButtonClick: () -> Unit
) {
    args?.let {
        AlertDialog(
            showDialog = showDialog,
            title =
                if (it.titleId != 0) {
                    stringResource(it.titleId)
                } else {
                    ""
                },
            iconRes = it.iconId,
            message = stringResource(it.messageId),
            okButtonContentDescription = stringResource(it.positiveButtonTextId),
            cancelButtonContentDescription = stringResource(it.negativeButtonTextId),
            onOKButtonClick = { onOkButtonClick(it) },
            onCancelButtonClick = onCancelButtonClick,
            scalingLazyListState = rememberScalingLazyListState()
        )
    }
}
