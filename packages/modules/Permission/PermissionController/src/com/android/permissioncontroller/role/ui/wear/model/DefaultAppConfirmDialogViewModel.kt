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

package com.android.permissioncontroller.role.ui.wear.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** ViewModel for a default app confirm dialog. */
class DefaultAppConfirmDialogViewModel : ViewModel() {
    /** A livedata which stores whether confirmation dialog is visible. */
    val showConfirmDialogLiveData = MutableLiveData<Boolean>()

    /** Arguments for a confirmation dialog. */
    var confirmDialogArgs: ConfirmDialogArgs? = null

    init {
        showConfirmDialogLiveData.value = false
    }
}

/** Factory for a DefaultAppConfirmDialogViewModel */
class DefaultAppConfirmDialogViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return DefaultAppConfirmDialogViewModel() as T
    }
}

/** Data class for arguments of a default app confirm dialog. */
data class ConfirmDialogArgs(
    val message: String,
    val onOkButtonClick: () -> Unit,
    val onCancelButtonClick: () -> Unit
)
