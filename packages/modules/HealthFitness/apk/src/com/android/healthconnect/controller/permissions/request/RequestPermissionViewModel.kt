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
 *
 *
 */

package com.android.healthconnect.controller.permissions.request

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for {@link PermissionsFragment} . */
@HiltViewModel
class RequestPermissionViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase,
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "RequestPermissionViewMo"
    }

    private val _appMetaData = MutableLiveData<AppMetadata>()
    val appMetadata: LiveData<AppMetadata>
        get() = _appMetaData

    private val _permissionsList = MutableLiveData<List<HealthPermission>>()
    val permissionsList: LiveData<List<HealthPermission>>
        get() = _permissionsList

    private val _grantedPermissions = MutableLiveData<Set<HealthPermission>>(emptySet())
    val grantedPermissions: LiveData<Set<HealthPermission>>
        get() = _grantedPermissions

    private val _allPermissionsGranted =
        MediatorLiveData(false).apply {
            addSource(_permissionsList) {
                postValue(isAllPermissionsGranted(permissionsList, grantedPermissions))
            }
            addSource(_grantedPermissions) {
                postValue(isAllPermissionsGranted(permissionsList, grantedPermissions))
            }
        }
    val allPermissionsGranted: LiveData<Boolean>
        get() = _allPermissionsGranted

    private fun isAllPermissionsGranted(
        permissionsListLiveData: LiveData<List<HealthPermission>>,
        grantedPermissionsLiveData: LiveData<Set<HealthPermission>>
    ): Boolean {
        val permissionsList = permissionsListLiveData.value.orEmpty()
        val grantedPermissions = grantedPermissionsLiveData.value.orEmpty()
        return if (permissionsList.isEmpty() || grantedPermissions.isEmpty()) {
            false
        } else {
            permissionsList.size == grantedPermissions.size
        }
    }

    fun init(packageName: String, permissions: Array<out String>) {
        loadAppInfo(packageName)
        loadPermissions(packageName, permissions)
    }

    fun isPermissionGranted(permission: HealthPermission): Boolean {
        return _grantedPermissions.value.orEmpty().contains(permission)
    }

    private fun loadPermissions(packageName: String, permissions: Array<out String>) {
        val grantedPermissions = getGrantedHealthPermissionsUseCase.invoke(packageName)
        val filteredPermissions =
            permissions
                .filter { permissionString -> !grantedPermissions.contains(permissionString) }
                .mapNotNull { permissionString ->
                    try {
                        HealthPermission.fromPermissionString(permissionString)
                    } catch (exception: IllegalArgumentException) {
                        Log.e(TAG, "Unrecognized health exception!", exception)
                        null
                    }
                }

        _permissionsList.postValue(filteredPermissions)
    }

    fun updatePermission(permission: HealthPermission, grant: Boolean) {
        val updatedGrantedPermissions = _grantedPermissions.value.orEmpty().toMutableSet()
        if (grant) {
            updatedGrantedPermissions.add(permission)
        } else {
            updatedGrantedPermissions.remove(permission)
        }
        _grantedPermissions.postValue(updatedGrantedPermissions)
    }

    fun updatePermissions(grant: Boolean) {
        if (grant) {
            _grantedPermissions.setValue(_permissionsList.value.orEmpty().toSet())
        } else {
            _grantedPermissions.setValue(emptySet())
        }
    }

    private fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appMetaData.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    fun request(packageName: String): MutableMap<HealthPermission, PermissionState> {
        val grants: MutableMap<HealthPermission, PermissionState> = mutableMapOf()
        _permissionsList.value.orEmpty().forEach { permission ->
            val granted = isPermissionGranted(permission)
            try {
                if (granted) {
                    grantHealthPermissionUseCase.invoke(packageName, permission.toString())
                    grants[permission] = PermissionState.GRANTED
                } else {
                    revokeHealthPermissionUseCase.invoke(packageName, permission.toString())
                    grants[permission] = PermissionState.NOT_GRANTED
                }
            } catch (e: SecurityException) {
                grants[permission] = PermissionState.NOT_GRANTED
            } catch (e: Exception) {
                grants[permission] = PermissionState.ERROR
            }
        }
        return grants
    }
}
