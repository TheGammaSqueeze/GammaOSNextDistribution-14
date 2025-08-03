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

import android.content.pm.ApplicationInfo
import android.util.Pair
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ListFooter
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChip
import com.android.permissioncontroller.permission.ui.wear.elements.ToggleChipToggleControl
import com.android.permissioncontroller.permission.ui.wear.elements.toggleChipBackgroundColors
import com.android.permissioncontroller.role.ui.ManageRoleHolderStateLiveData

@Composable
fun WearRequestRoleScreen(
    helper: WearRequestRoleHelper,
    onSetAsDefault: (Boolean, String?) -> Unit,
    onCanceled: () -> Unit
) {
    val roleLiveData = helper.viewModel.roleLiveData.observeAsState(emptyList())
    val manageRoleHolderState =
        helper.viewModel.manageRoleHolderStateLiveData.observeAsState(
            ManageRoleHolderStateLiveData.STATE_WORKING
        )
    val dontAskAgain = helper.wearViewModel.dontAskAgain.observeAsState(false)
    val selectedPackageName = helper.wearViewModel.selectedPackageName.observeAsState(null)
    var isLoading by remember { mutableStateOf(true) }

    if (isLoading && roleLiveData.value.isNotEmpty()) {
        helper.initializeHolderPackageName(roleLiveData.value)
        helper.initializeSelectedPackageName()
    }

    val onCheckedChanged: (Boolean, String?, Boolean) -> Unit = { checked, packageName, isHolder ->
        if (checked) {
            helper.wearViewModel.selectedPackageName.value = packageName
            helper.wearViewModel.isHolderChecked = isHolder
        }
    }

    val onDontAskAgainCheckedChanged: (Boolean) -> Unit = { checked ->
        helper.wearViewModel.dontAskAgain.value = checked
        if (checked) {
            helper.initializeSelectedPackageName()
        }
    }

    WearRequestRoleContent(
        isLoading,
        helper,
        roleLiveData.value,
        manageRoleHolderState.value == ManageRoleHolderStateLiveData.STATE_IDLE,
        dontAskAgain.value,
        selectedPackageName.value,
        onCheckedChanged,
        onDontAskAgainCheckedChanged,
        onSetAsDefault,
        onCanceled
    )

    if (isLoading && roleLiveData.value.isNotEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun WearRequestRoleContent(
    isLoading: Boolean,
    helper: WearRequestRoleHelper,
    qualifyingApplications: List<Pair<ApplicationInfo, Boolean>>,
    enabled: Boolean,
    dontAskAgain: Boolean,
    selectedPackageName: String?,
    onCheckedChanged: (Boolean, String?, Boolean) -> Unit,
    onDontAskAgainCheckedChanged: (Boolean) -> Unit,
    onSetAsDefault: (Boolean, String?) -> Unit,
    onCanceled: () -> Unit
) {
    ScrollableScreen(
        image = helper.getIcon(),
        title = helper.getTitle(),
        showTimeText = false,
        isLoading = isLoading
    ) {
        helper.getNonePreference(qualifyingApplications, selectedPackageName)?.let {
            item {
                ToggleChip(
                    label = it.label,
                    icon = it.icon,
                    enabled = enabled && it.enabled,
                    checked = it.checked,
                    onCheckedChanged = { checked ->
                        run { onCheckedChanged(checked, it.packageName, it.isHolder) }
                    },
                    toggleControl = ToggleChipToggleControl.Radio,
                    labelMaxLine = Integer.MAX_VALUE
                )
            }
            it.subTitle?.let { subTitle -> item { ListFooter(description = subTitle) } }
        }

        for (pref in helper.getPreferences(qualifyingApplications, selectedPackageName)) {
            item {
                ToggleChip(
                    label = pref.label,
                    icon = pref.icon,
                    enabled = enabled && pref.enabled,
                    checked = pref.checked,
                    onCheckedChanged = { checked ->
                        run { onCheckedChanged(checked, pref.packageName, pref.isHolder) }
                    },
                    toggleControl = ToggleChipToggleControl.Radio,
                )
            }
            pref.subTitle?.let { subTitle -> item { ListFooter(description = subTitle) } }
        }

        if (helper.showDontAskButton()) {
            item {
                ToggleChip(
                    checked = dontAskAgain,
                    enabled = enabled,
                    onCheckedChanged = { checked -> run { onDontAskAgainCheckedChanged(checked) } },
                    label = stringResource(R.string.request_role_dont_ask_again),
                    toggleControl = ToggleChipToggleControl.Checkbox,
                    colors = toggleChipBackgroundColors(),
                    modifier =
                        Modifier.testTag("com.android.permissioncontroller:id/dont_ask_again"),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(14.dp)) }

        item {
            Chip(
                label = stringResource(R.string.request_role_set_as_default),
                textColor = MaterialTheme.colors.background,
                colors = ChipDefaults.primaryChipColors(),
                enabled = helper.shouldSetAsDefaultEnabled(enabled),
                onClick = { onSetAsDefault(dontAskAgain, selectedPackageName) },
                modifier = Modifier.testTag("android:id/button1"),
            )
        }
        item {
            Chip(
                label = stringResource(R.string.cancel),
                enabled = enabled,
                onClick = { onCanceled() },
                modifier = Modifier.testTag("android:id/button2"),
            )
        }
    }
}
