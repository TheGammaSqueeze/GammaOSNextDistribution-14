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

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** ViewModel for WearRequestRoleScreen. */
class WearRequestRoleViewModel : ViewModel() {
    val dontAskAgain = MutableLiveData<Boolean>(false)
    val selectedPackageName = MutableLiveData<String?>(null)
    var isHolderChecked: Boolean = false
    var holderPackageName: String? = null

    fun dontAskAgain(): Boolean {
        return dontAskAgain.value ?: false
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DONT_ASK_AGAIN, dontAskAgain())
        outState.putString(STATE_SELECTED_PACKAGE_NAME, selectedPackageName.value)
        outState.putBoolean(STATE_IS_HOLDER_CHECKED, isHolderChecked)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        dontAskAgain.value = savedInstanceState.getBoolean(STATE_DONT_ASK_AGAIN)
        selectedPackageName.value = savedInstanceState.getString(STATE_SELECTED_PACKAGE_NAME)
        isHolderChecked = savedInstanceState.getBoolean(STATE_IS_HOLDER_CHECKED)
    }

    companion object {
        const val STATE_DONT_ASK_AGAIN = "WearRequestRoleViewModel.state.DONT_ASK_AGAIN"
        const val STATE_SELECTED_PACKAGE_NAME =
            "WearRequestRoleViewModel.state.SELECTED_PACKAGE_NAME"
        const val STATE_IS_HOLDER_CHECKED = "WearRequestRoleViewModel.state.IS_HOLDER_CHECKED"
    }
}

/** Factory for a WearRequestRoleViewModel */
class WearRequestRoleViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WearRequestRoleViewModel() as T
    }
}
