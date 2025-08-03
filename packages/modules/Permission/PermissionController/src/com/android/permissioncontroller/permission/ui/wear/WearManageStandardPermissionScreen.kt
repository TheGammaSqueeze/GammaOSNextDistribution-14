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

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo
import com.android.permissioncontroller.permission.ui.model.ManageStandardPermissionsViewModel
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupIcon
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.StringUtils
import java.text.Collator

@Composable
fun WearManageStandardPermissionScreen(
    viewModel: ManageStandardPermissionsViewModel,
    onPermGroupClick: (String) -> Unit,
    onCustomPermissionsClick: () -> Unit,
    onAutoRevokedClick: () -> Unit
) {
    val permissionGroups = viewModel.uiDataLiveData.observeAsState(emptyMap())
    val numCustomPermGroups = viewModel.numCustomPermGroups.observeAsState(0)
    val numAutoRevoked = viewModel.numAutoRevoked.observeAsState(0)
    var isLoading by remember { mutableStateOf(true) }

    WearManageStandardPermissionContent(
        isLoading,
        getPermGroupChipParams(permissionGroups.value),
        numCustomPermGroups.value,
        numAutoRevoked.value,
        onPermGroupClick,
        onCustomPermissionsClick,
        onAutoRevokedClick
    )

    if (isLoading && permissionGroups.value.isNotEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun getPermGroupChipParams(
    permissionGroups: Map<String, PermGroupPackagesUiInfo?>
): List<PermGroupChipParam> {
    val context = LocalContext.current
    val collator = Collator.getInstance(context.resources.getConfiguration().getLocales().get(0))
    val summary =
        if (context.resources.getBoolean(R.bool.config_useAlternativePermGroupSummary)) {
            R.string.app_permissions_group_summary2
        } else {
            R.string.app_permissions_group_summary
        }
    return permissionGroups
        .mapNotNull {
            val uiInfo = it.value ?: return@mapNotNull null
            PermGroupChipParam(
                permGroupName = it.key,
                label = getPermGroupLabel(context, it.key).toString(),
                icon = getPermGroupIcon(context, it.key),
                secondaryLabel =
                    stringResource(summary, uiInfo.nonSystemGranted, uiInfo.nonSystemTotal)
            )
        }
        .sortedWith { lhs, rhs -> collator.compare(lhs.label, rhs.label) }
        .toList()
}

@Composable
internal fun WearManageStandardPermissionContent(
    isLoading: Boolean,
    permGroupChipParams: List<PermGroupChipParam>,
    numCustomPermGroups: Int,
    numAutoRevoked: Int,
    onPermGroupClick: (String) -> Unit,
    onCustomPermissionsClick: () -> Unit,
    onAutoRevokedClick: () -> Unit
) {
    ScrollableScreen(
        title = stringResource(R.string.app_permission_manager),
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

        if (numCustomPermGroups > 0) {
            item {
                Chip(
                    label = stringResource(R.string.additional_permissions),
                    labelMaxLines = 3,
                    icon = R.drawable.ic_more_horizontal,
                    secondaryLabel =
                        StringUtils.getIcuPluralsString(
                            LocalContext.current,
                            R.string.additional_permissions_more,
                            numCustomPermGroups
                        ),
                    secondaryLabelMaxLines = 3,
                    onClick = onCustomPermissionsClick
                )
            }
        }

        if (numAutoRevoked > 0) {
            item {
                Chip(
                    label = stringResource(R.string.auto_revoke_permission_notification_title),
                    labelMaxLines = 3,
                    icon = R.drawable.ic_info,
                    secondaryLabel = stringResource(R.string.auto_revoke_setting_subtitle),
                    secondaryLabelMaxLines = 3,
                    onClick = onAutoRevokedClick
                )
            }
        }
    }
}

internal data class PermGroupChipParam(
    val permGroupName: String,
    val label: String,
    val icon: Drawable?,
    val secondaryLabel: String,
)
