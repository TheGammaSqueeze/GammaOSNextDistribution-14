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

class WearGrantPermissionsViewModel : ViewModel() {
    /** A livedata which stores the permission group name. */
    val groupNameLiveData = MutableLiveData<String>()

    /** A livedata which stores the permission group icon. */
    val iconLiveData = MutableLiveData<Drawable?>()

    /** A livedata which stores the permission group message. */
    val groupMessageLiveData = MutableLiveData<String>()

    /** A livedata which stores the permission group detail message. */
    val detailMessageLiveData = MutableLiveData<CharSequence?>()

    /** A livedata which stores the permission group location-granularity visibilities. */
    val locationVisibilitiesLiveData = MutableLiveData<List<Boolean>>()

    /** A livedata which stores weather the precise location toggle chip is checked. */
    val preciseLocationCheckedLiveData = MutableLiveData<Boolean>()

    /** A livedata which stores the permission group button visibilities. */
    val buttonVisibilitiesLiveData = MutableLiveData<List<Boolean>>()

    init {
        groupNameLiveData.value = ""
        iconLiveData.value = null
        groupMessageLiveData.value = ""
        detailMessageLiveData.value = null
        locationVisibilitiesLiveData.value = emptyList()
        preciseLocationCheckedLiveData.value = false
        buttonVisibilitiesLiveData.value = emptyList()
    }
}

/** Factory for a WearGrantPermissionsViewModel */
class WearGrantPermissionsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WearGrantPermissionsViewModel() as T
    }
}
