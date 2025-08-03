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

package com.android.permissioncontroller.permission.ui.wear.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs

class AppPermissionConfirmDialogViewModel : ViewModel() {
    /** A livedata which stores whether confirmation dialog is visible. */
    val showConfirmDialogLiveData = MutableLiveData<Boolean>()

    /** Arguments for a confirmation dialog. */
    var confirmDialogArgs: ConfirmDialogArgs? = null

    /** A livedata which stores whether confirmation dialog is visible. */
    val showAdvancedConfirmDialogLiveData = MutableLiveData<Boolean>()

    /** Arguments for an advanced confirmation dialog. */
    var advancedConfirmDialogArgs: AdvancedConfirmDialogArgs? = null

    init {
        showConfirmDialogLiveData.value = false
        showAdvancedConfirmDialogLiveData.value = false
    }
}

/** Factory for an AppPermissionConfirmDialogViewModel */
class AppPermissionConfirmDialogViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return AppPermissionConfirmDialogViewModel() as T
    }
}

/**  */
data class ConfirmDialogArgs(
    val messageId: Int,
    val changeRequest: AppPermissionViewModel.ChangeRequest,
    val buttonPressed: Int,
    val oneTime: Boolean
)
