/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.connectedapps

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.connectedapps.searchapps.SearchHealthPermissionApps
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.utils.postValueIfUpdated
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectedAppsViewModel
@Inject
constructor(
    private val loadHealthPermissionApps: ILoadHealthPermissionApps,
    private val searchHealthPermissionApps: SearchHealthPermissionApps,
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "ConnectedAppsViewModel"
    }

    private val _connectedApps = MutableLiveData<List<ConnectedAppMetadata>>()
    val connectedApps: LiveData<List<ConnectedAppMetadata>>
        get() = _connectedApps

    private val _disconnectAllState =
        MutableLiveData<DisconnectAllState>(DisconnectAllState.NotStarted)
    val disconnectAllState: LiveData<DisconnectAllState>
        get() = _disconnectAllState

    private val _alertDialogActive = MutableLiveData(false)
    val alertDialogActive: LiveData<Boolean>
        get() = _alertDialogActive

    init {
        loadConnectedApps()
    }

    fun setAlertDialogStatus(isActive: Boolean) {
        _alertDialogActive.postValue(isActive)
    }

    fun loadConnectedApps() {
        viewModelScope.launch {
            _connectedApps.postValueIfUpdated(loadHealthPermissionApps.invoke())
        }
    }

    fun searchConnectedApps(searchValue: String) {
        viewModelScope.launch {
            _connectedApps.postValueIfUpdated(
                searchHealthPermissionApps.search(loadHealthPermissionApps.invoke(), searchValue))
        }
    }

    fun disconnectAllApps(apps: List<ConnectedAppMetadata>): Boolean {
        try {
            viewModelScope.launch(ioDispatcher) {
                _disconnectAllState.postValue(DisconnectAllState.Loading)
                apps.forEach { app ->
                    revokeAllHealthPermissionsUseCase.invoke(app.appMetadata.packageName)
                }
                loadConnectedApps()
                _disconnectAllState.postValue(DisconnectAllState.Updated)
            }
            _alertDialogActive.postValue(false)
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update permissions!", ex)
        }
        _alertDialogActive.postValue(false)
        return false
    }

    sealed class DisconnectAllState {
        object NotStarted : DisconnectAllState()

        object Loading : DisconnectAllState()

        object Updated : DisconnectAllState()
    }
}
