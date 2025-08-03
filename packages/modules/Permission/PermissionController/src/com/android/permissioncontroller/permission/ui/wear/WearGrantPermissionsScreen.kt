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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_BOTH_LOCATIONS
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_FINE_LOCATION_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.LOCATION_ACCURACY_LAYOUT
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON
import com.android.permissioncontroller.permission.ui.wear.GrantPermissionsWearViewHandler.BUTTON_RES_ID_TO_NUM
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChip
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChipToggleControl
import com.android.permissioncontroller.permission.ui.wear.model.WearGrantPermissionsViewModel

@Composable
fun WearGrantPermissionsScreen(
    viewModel: WearGrantPermissionsViewModel,
    onButtonClicked: (Int) -> Unit,
    onLocationSwitchChanged: (Boolean) -> Unit
) {
    val groupMessage = viewModel.groupMessageLiveData.observeAsState("")
    val icon = viewModel.iconLiveData.observeAsState(null)
    val detailMessage = viewModel.detailMessageLiveData.observeAsState(null)
    val locationVisibilities = viewModel.locationVisibilitiesLiveData.observeAsState(emptyList())
    val preciseLocationChecked = viewModel.preciseLocationCheckedLiveData.observeAsState(false)
    val buttonVisibilities = viewModel.buttonVisibilitiesLiveData.observeAsState(emptyList())

    ScrollableScreen(
        showTimeText = false,
        image = icon.value,
        title = groupMessage.value,
        subtitle = detailMessage.value,
        titleTestTag = "com.android.permissioncontroller:id/permission_message",
        subtitleTestTag = "com.android.permissioncontroller:id/detail_message",
    ) {
        if (
            locationVisibilities.value.getOrElse(LOCATION_ACCURACY_LAYOUT) { false } &&
                locationVisibilities.value.getOrElse(DIALOG_WITH_BOTH_LOCATIONS) { false }
        ) {
            item {
                ToggleChip(
                    checked = preciseLocationChecked.value,
                    onCheckedChanged = { onLocationSwitchChanged(it) },
                    label = stringResource(R.string.app_permission_location_accuracy),
                    toggleControl = ToggleChipToggleControl.Switch,
                    modifier = Modifier.fillMaxWidth(),
                    labelMaxLine = Integer.MAX_VALUE
                )
            }
        }
        for (i in 0 until BUTTON_RES_ID_TO_NUM.size()) {
            val pos: Int = BUTTON_RES_ID_TO_NUM.valueAt(i)
            if (buttonVisibilities.value.size <= pos) {
                // initial value of buttonVisibilities is empty
                break
            }
            if (buttonVisibilities.value[pos]) {
                item {
                    Chip(
                        label =
                            getPrimaryText(
                                pos,
                                locationVisibilities.value,
                                labelsByButton(BUTTON_RES_ID_TO_NUM.valueAt(i))
                            ),
                        onClick = { onButtonClicked(BUTTON_RES_ID_TO_NUM.keyAt(i)) },
                        modifier = Modifier.fillMaxWidth(),
                        labelMaxLines = Integer.MAX_VALUE
                    )
                }
            }
        }
    }
}

fun setContent(
    composeView: ComposeView,
    viewModel: WearGrantPermissionsViewModel,
    onButtonClicked: (Int) -> Unit,
    onLocationSwitchChanged: (Boolean) -> Unit
) {
    composeView.setContent {
        WearGrantPermissionsScreen(viewModel, onButtonClicked, onLocationSwitchChanged)
    }
}

@Composable
internal fun labelsByButton(grantPermissionsButtonType: Int) =
    when (grantPermissionsButtonType) {
        ALLOW_BUTTON -> stringResource(R.string.grant_dialog_button_allow)
        ALLOW_ALWAYS_BUTTON -> stringResource(R.string.grant_dialog_button_allow_always)
        ALLOW_FOREGROUND_BUTTON -> stringResource(R.string.grant_dialog_button_allow_foreground)
        DENY_BUTTON -> stringResource(R.string.grant_dialog_button_deny)
        DENY_AND_DONT_ASK_AGAIN_BUTTON -> stringResource(R.string.grant_dialog_button_deny)
        ALLOW_ONE_TIME_BUTTON -> stringResource(R.string.grant_dialog_button_allow_one_time)
        NO_UPGRADE_BUTTON -> stringResource(R.string.grant_dialog_button_no_upgrade)
        NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON ->
            stringResource(R.string.grant_dialog_button_no_upgrade)
        NO_UPGRADE_OT_BUTTON -> stringResource(R.string.grant_dialog_button_no_upgrade_one_time)
        NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON ->
            stringResource(R.string.grant_dialog_button_no_upgrade_one_time)
        else -> ""
    }

@Composable
private fun getPrimaryText(pos: Int, locationVisibilities: List<Boolean>, default: String): String {
    val isPreciseLocation: Boolean =
        locationVisibilities.getOrElse(LOCATION_ACCURACY_LAYOUT) { false } &&
            locationVisibilities.getOrElse(DIALOG_WITH_FINE_LOCATION_ONLY) { false }
    var res: String = default
    if (pos == ALLOW_FOREGROUND_BUTTON && isPreciseLocation) {
        res = stringResource(R.string.grant_dialog_button_change_to_precise_location)
    }
    if ((pos == DENY_BUTTON || pos == DENY_AND_DONT_ASK_AGAIN_BUTTON) && isPreciseLocation) {
        res = stringResource(R.string.grant_dialog_button_keey_approximate_location)
    }
    return res
}
