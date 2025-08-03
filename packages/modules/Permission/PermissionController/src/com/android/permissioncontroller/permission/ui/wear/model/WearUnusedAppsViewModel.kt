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

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.ui.model.UnusedAppsViewModel.UnusedPeriod

class WearUnusedAppsViewModel : ViewModel() {
    /** A livedata which indicates if the loading animation should be showed. */
    val loadingLiveData = MutableLiveData<Boolean>()

    /** A livedata which stores unused period category visibilities. */
    val unusedPeriodCategoryVisibilitiesLiveData = MutableLiveData<List<Boolean>>()

    /** A livedata which indicates if the info massage category is visible or not. */
    val infoMsgCategoryVisibilityLiveData = MutableLiveData<Boolean>()

    /** A livedata which stores a map of unused apps group by UnusedPeriod. */
    val unusedAppChipsLiveData =
        MutableLiveData<Map<UnusedPeriod, Map<String, UnusedAppChip>>>()

    data class UnusedAppChip(
        val label: String,
        val summary: String?,
        val icon: Drawable?,
        val contentDescription: String?,
        val onClick: () -> Unit,
    )

    init {
        loadingLiveData.value = true
        unusedPeriodCategoryVisibilitiesLiveData.value = emptyList()
        infoMsgCategoryVisibilityLiveData.value = false
        unusedAppChipsLiveData.value = mutableMapOf()
    }
}

/** Factory for a WearUnusedAppsViewModel */
class WearUnusedAppsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WearUnusedAppsViewModel() as T
    }
}
