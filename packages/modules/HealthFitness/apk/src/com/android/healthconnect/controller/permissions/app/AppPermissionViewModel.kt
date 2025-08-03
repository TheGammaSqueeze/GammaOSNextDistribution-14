/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.app

import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import android.health.connect.TimeInstantRangeFilter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.api.DeleteAppDataUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.LoadExerciseRoutePermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.IGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.FeatureUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** View model for {@link ConnectedAppFragment} and {SettingsManageAppPermissionsFragment} . */
@HiltViewModel
class AppPermissionViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadAppPermissionsStatusUseCase: LoadAppPermissionsStatusUseCase,
    private val grantPermissionsStatusUseCase: GrantHealthPermissionUseCase,
    private val revokePermissionsStatusUseCase: RevokeHealthPermissionUseCase,
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase,
    private val deleteAppDataUseCase: DeleteAppDataUseCase,
    private val loadAccessDateUseCase: LoadAccessDateUseCase,
    private val loadGrantedHealthPermissionsUseCase: IGetGrantedHealthPermissionsUseCase,
    private val loadExerciseRoutePermissionUseCase: LoadExerciseRoutePermissionUseCase,
    private val healthPermissionReader: HealthPermissionReader,
    private val featureUtils: FeatureUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "AppPermissionViewModel"
    }

    private val _appPermissions = MutableLiveData<List<HealthPermission>>(emptyList())
    val appPermissions: LiveData<List<HealthPermission>>
        get() = _appPermissions

    private val _grantedPermissions = MutableLiveData<Set<HealthPermission>>(emptySet())
    val grantedPermissions: LiveData<Set<HealthPermission>>
        get() = _grantedPermissions

    val allAppPermissionsGranted =
        MediatorLiveData(false).apply {
            addSource(_appPermissions) {
                postValue(isAllPermissionsGranted(appPermissions, grantedPermissions))
            }
            addSource(_grantedPermissions) {
                postValue(isAllPermissionsGranted(appPermissions, grantedPermissions))
            }
        }

    val atLeastOnePermissionGranted =
        MediatorLiveData(false).apply {
            addSource(_grantedPermissions) { grantedPermissions ->
                postValue(grantedPermissions.isNotEmpty())
            }
        }

    private val _appInfo = MutableLiveData<AppMetadata>()
    val appInfo: LiveData<AppMetadata>
        get() = _appInfo

    private val _revokeAllPermissionsState =
        MutableLiveData<RevokeAllState>(RevokeAllState.NotStarted)
    val revokeAllPermissionsState: LiveData<RevokeAllState>
        get() = _revokeAllPermissionsState

    private var permissionsList: List<HealthPermissionStatus> = listOf()

    /**
     * Flag to prevent {@link SettingManageAppPermissionsFragment} from reloading the granted
     * permissions on orientation change
     */
    private var shouldLoadGrantedPermissions = true

    private val _showDisableExerciseRouteEvent = MutableLiveData(false)
    val showDisableExerciseRouteEvent =
        MediatorLiveData(DisableExerciseRouteDialogEvent()).apply {
            addSource(_showDisableExerciseRouteEvent) {
                postValue(
                    DisableExerciseRouteDialogEvent(
                        shouldShowDialog = _showDisableExerciseRouteEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
            addSource(_appInfo) {
                postValue(
                    DisableExerciseRouteDialogEvent(
                        shouldShowDialog = _showDisableExerciseRouteEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
        }

    fun loadPermissionsForPackage(packageName: String) {
        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
        if (isPackageSupported(packageName)) {
            loadAllPermissions(packageName)
        } else {
            // we only load granted permissions for not supported apps to allow users to revoke
            // these permissions.
            loadGrantedPermissionsForPackage(packageName)
        }
    }

    private fun loadAllPermissions(packageName: String) {
        viewModelScope.launch {
            permissionsList = loadAppPermissionsStatusUseCase.invoke(packageName)
            _appPermissions.postValue(permissionsList.map { it.healthPermission })
            _grantedPermissions.postValue(
                permissionsList.filter { it.isGranted }.map { it.healthPermission }.toSet())
        }
    }

    private fun loadGrantedPermissionsForPackage(packageName: String) {
        // Only reload the status the first time this method is called
        if (shouldLoadGrantedPermissions) {
            viewModelScope.launch {
                val grantedPermissions =
                    loadAppPermissionsStatusUseCase.invoke(packageName).filter { it.isGranted }
                permissionsList = grantedPermissions

                // Only show app permissions that are granted
                _appPermissions.postValue(grantedPermissions.map { it.healthPermission })
                _grantedPermissions.postValue(
                    grantedPermissions.map { it.healthPermission }.toSet())
            }
            shouldLoadGrantedPermissions = false
        }
    }

    fun loadAccessDate(packageName: String): Instant? {
        return loadAccessDateUseCase.invoke(packageName)
    }

    fun updatePermission(
        packageName: String,
        healthPermission: HealthPermission,
        grant: Boolean
    ): Boolean {
        try {
            if (grant) {
                grantPermission(packageName, healthPermission)
            } else {
                revokePermission(healthPermission, packageName)
                if (shouldDisplayExerciseRouteDialog(packageName, healthPermission)) {
                    _showDisableExerciseRouteEvent.postValue(true)
                }
            }

            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update permissions!", ex)
        }
        return false
    }

    private fun grantPermission(packageName: String, healthPermission: HealthPermission) {
        val grantedPermissions = _grantedPermissions.value.orEmpty().toMutableSet()
        grantPermissionsStatusUseCase.invoke(packageName, healthPermission.toString())
        grantedPermissions.add(healthPermission)
        _grantedPermissions.postValue(grantedPermissions)
    }

    private fun revokePermission(healthPermission: HealthPermission, packageName: String) {
        val grantedPermissions = _grantedPermissions.value.orEmpty().toMutableSet()
        grantedPermissions.remove(healthPermission)
        _grantedPermissions.postValue(grantedPermissions)
        revokePermissionsStatusUseCase.invoke(packageName, healthPermission.toString())
    }

    private fun shouldDisplayExerciseRouteDialog(
        packageName: String,
        healthPermission: HealthPermission
    ): Boolean {
        if (!featureUtils.isExerciseRouteReadAllEnabled() ||
            healthPermission.toString() != READ_EXERCISE) {
            return false
        }

        return runBlocking {
            when (val exerciseRouteState = loadExerciseRoutePermissionUseCase(packageName)) {
                is UseCaseResults.Success -> {
                    exerciseRouteState.data.exerciseRoutePermissionState == ALWAYS_ALLOW
                }
                else -> false
            }
        }
    }

    fun grantAllPermissions(packageName: String): Boolean {
        try {
            _appPermissions.value?.forEach {
                grantPermissionsStatusUseCase.invoke(packageName, it.toString())
            }
            val grantedPermissions = _grantedPermissions.value.orEmpty().toMutableSet()
            grantedPermissions.addAll(_appPermissions.value.orEmpty())
            _grantedPermissions.postValue(grantedPermissions)
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update permissions!", ex)
        }
        return false
    }

    fun disableExerciseRoutePermission(packageName: String) {
        revokePermission(fromPermissionString(READ_EXERCISE), packageName)
        revokePermissionsStatusUseCase(packageName, READ_EXERCISE_ROUTES)
    }

    fun revokeAllPermissions(packageName: String): Boolean {
        // TODO (b/325729045) if there is an error within the coroutine scope
        // it will not be caught by this statement in tests. Consider using LiveData instead
        try {
            viewModelScope.launch(ioDispatcher) {
                _revokeAllPermissionsState.postValue(RevokeAllState.Loading)
                revokeAllHealthPermissionsUseCase.invoke(packageName)
                if (isPackageSupported(packageName)) {
                    loadPermissionsForPackage(packageName)
                }
                _revokeAllPermissionsState.postValue(RevokeAllState.Updated)
                _grantedPermissions.postValue(emptySet())
            }
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update permissions!", ex)
        }
        return false
    }

    fun deleteAppData(packageName: String, appName: String) {
        viewModelScope.launch {
            val appData = DeletionType.DeletionTypeAppData(packageName, appName)
            val timeRangeFilter =
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.ofEpochMilli(Long.MAX_VALUE))
                    .build()
            deleteAppDataUseCase.invoke(appData, timeRangeFilter)
        }
    }

    fun shouldNavigateToAppPermissionsFragment(packageName: String): Boolean {
        return isPackageSupported(packageName) || hasGrantedPermissions(packageName)
    }

    private fun hasGrantedPermissions(packageName: String): Boolean {
        return loadGrantedHealthPermissionsUseCase(packageName)
            .map { permission -> fromPermissionString(permission) }
            .isNotEmpty()
    }

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

    /** Returns True if the packageName declares the Rationale intent, False otherwise */
    fun isPackageSupported(packageName: String): Boolean {
        return healthPermissionReader.isRationalIntentDeclared(packageName)
    }

    fun hideExerciseRoutePermissionDialog() {
        _showDisableExerciseRouteEvent.postValue(false)
    }

    sealed class RevokeAllState {
        object NotStarted : RevokeAllState()

        object Loading : RevokeAllState()

        object Updated : RevokeAllState()
    }

    data class DisableExerciseRouteDialogEvent(
        val shouldShowDialog: Boolean = false,
        val appName: String = ""
    )
}
