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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class WearConfirmationActivityViewModel : ViewModel() {
    /** A livedata which stores whether the incident/bug report dialog is visible. */
    val showDialogLiveData = MutableLiveData<Boolean>()

    /** A livedata which stores to whether to show the screen for deny report */
    val showDenyReportLiveData = MutableLiveData<Boolean>()

    /** A livedata which stores arguments for a confirmation section. */
    var contentArgsLiveData = MutableLiveData<ContentArgs>()

    data class ContentArgs(
        // stores the incident/bug report title
        val title: String,
        // stores the incident/bug report message body
        val message: String,
        // this is a button shows only in a denied incident/bug report dialog
        val onDenyClick: () -> Unit,
        val onOkClick: () -> Unit,
        val onCancelClick: () -> Unit,
    )

    init {
        showDialogLiveData.value = false
        showDenyReportLiveData.value = false
        contentArgsLiveData.value = null
    }
}

/** Factory for a WearConfirmationActivityViewModel */
class WearConfirmationActivityViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WearConfirmationActivityViewModel() as T
    }
}
