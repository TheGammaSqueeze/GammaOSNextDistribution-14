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

package com.android.permissioncontroller.incident.wear

import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import com.android.permissioncontroller.permission.ui.wear.elements.AlertDialog
import com.android.permissioncontroller.permission.ui.wear.elements.SingleButtonAlertDialog
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme

@Composable
fun WearConfirmationScreen(viewModel: WearConfirmationActivityViewModel) {
    // Wear screen doesn't show incident/bug report's optional reasons and images.
    val showDialog = viewModel.showDialogLiveData.observeAsState(false)
    val showDenyReport = viewModel.showDenyReportLiveData.observeAsState(false)
    val contentArgs = viewModel.contentArgsLiveData.observeAsState(null)
    var isLoading by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            if (showDenyReport.value) {
                contentArgs.value?.let {
                    SingleButtonAlertDialog(
                        showDialog = showDialog.value,
                        title = it.title,
                        message = it.message,
                        onButtonClick = it.onDenyClick,
                        scalingLazyListState = ScalingLazyListState(0)
                    )
                }
                return
            }
            contentArgs.value?.let {
                AlertDialog(
                    showDialog = showDialog.value,
                    title = it.title,
                    message = it.message,
                    onOKButtonClick = it.onOkClick,
                    onCancelButtonClick = it.onCancelClick,
                    scalingLazyListState = ScalingLazyListState(0)
                )
            }
        }
    }

    if (isLoading && showDialog.value) {
        isLoading = false
    }
}

fun createView(activity: Activity, viewModel: WearConfirmationActivityViewModel): View {
    return ComposeView(activity).apply {
        setContent { WearPermissionTheme { WearConfirmationScreen(viewModel) } }
    }
}
