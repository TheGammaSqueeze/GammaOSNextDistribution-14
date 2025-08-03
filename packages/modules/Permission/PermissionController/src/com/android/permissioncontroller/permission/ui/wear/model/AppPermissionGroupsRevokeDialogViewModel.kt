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

class AppPermissionGroupsRevokeDialogViewModel : ViewModel() {
    /** A livedata which stores whether the dialog is visible. */
    val showDialogLiveData = MutableLiveData<Boolean>()
    var hasConfirmedRevoke: Boolean = false
    var revokeDialogArgs: RevokeDialogArgs? = null

    init {
        showDialogLiveData.value = false
    }

    fun dismissDialog() {
        showDialogLiveData.value = false
        revokeDialogArgs = null
    }
}

/** Factory for an AppPermissionGroupsRevokeDialogViewModel */
class AppPermissionGroupsRevokeDialogViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return AppPermissionGroupsRevokeDialogViewModel() as T
    }
}

data class RevokeDialogArgs(
    val messageId: Int,
    val onOkButtonClick: () -> Unit,
    val onCancelButtonClick: () -> Unit
)
