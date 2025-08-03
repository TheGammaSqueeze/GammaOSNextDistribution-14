/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.healthconnect.controller.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.connectedapps.LoadHealthPermissionApps
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.utils.postValueIfUpdated
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HomeFragmentViewModel
@Inject
constructor(private val loadHealthPermissionApps: LoadHealthPermissionApps) : ViewModel() {

    private val _connectedApps = MutableLiveData<List<ConnectedAppMetadata>>()
    val connectedApps: LiveData<List<ConnectedAppMetadata>>
        get() = _connectedApps

    init {
        loadConnectedApps()
    }

    fun loadConnectedApps() {
        viewModelScope.launch {
            _connectedApps.postValueIfUpdated(loadHealthPermissionApps.invoke())
        }
    }
}
