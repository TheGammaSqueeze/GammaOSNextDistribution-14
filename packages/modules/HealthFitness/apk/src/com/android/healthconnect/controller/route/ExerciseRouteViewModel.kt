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
package com.android.healthconnect.controller.route

import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import android.health.connect.HealthPermissions.WRITE_EXERCISE_ROUTE
import android.health.connect.datatypes.ExerciseSessionRecord
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Objects
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for reading an exercise route for the given session. */
@HiltViewModel
class ExerciseRouteViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val loadExerciseRouteUseCase: LoadExerciseRouteUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase,
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase,
    private val loadAccessDateUseCase: LoadAccessDateUseCase,
    private val appInfoReader: AppInfoReader,
) : ViewModel() {

    companion object {
        private const val TAG = "ExerciseRouteViewModel"
    }

    private val _exerciseSession = MutableLiveData<SessionWithAttribution?>()
    val exerciseSession: LiveData<SessionWithAttribution?>
        get() = _exerciseSession

    fun getExerciseWithRoute(sessionId: String) {
        viewModelScope.launch {
            when (val result = loadExerciseRouteUseCase.invoke(sessionId)) {
                is UseCaseResults.Success -> {
                    if (!Objects.equals(result.data, null)) {
                        val record: ExerciseSessionRecord = result.data!!
                        _exerciseSession.postValue(
                            SessionWithAttribution(
                                record,
                                appInfoReader.getAppMetadata(
                                    record.metadata.dataOrigin.packageName)))
                    } else {
                        _exerciseSession.postValue(null)
                    }
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, result.exception.message!!)
                    _exerciseSession.postValue(null)
                }
            }
        }
    }

    fun isRouteReadOrWritePermissionGranted(packageName: String): Boolean {
        val grantedPermissions = getGrantedHealthPermissionsUseCase(packageName)
        val routePermissions = listOf(READ_EXERCISE_ROUTES, WRITE_EXERCISE_ROUTE)
        return grantedPermissions.any { it in routePermissions }
    }

    fun isReadRoutesPermissionGranted(packageName: String): Boolean {
        val grantedPermissions = getGrantedHealthPermissionsUseCase(packageName)
        return grantedPermissions.contains(READ_EXERCISE_ROUTES)
    }

    fun isReadRoutesPermissionUserFixed(packageName: String): Boolean {
        val permission = READ_EXERCISE_ROUTES
        val flags = getHealthPermissionsFlagsUseCase(packageName, listOf(READ_EXERCISE_ROUTES))

        return flags[permission]!!.and(PackageManager.FLAG_PERMISSION_USER_FIXED) != 0
    }

    fun isReadRoutesPermissionDeclared(packageName: String): Boolean {
        return try {
            val appInfo =
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))

            if (appInfo.requestedPermissions == null) {
                Log.e(TAG, "isPermissionDeclared error: no permissions")
                return false
            }

            appInfo.requestedPermissions.contains(READ_EXERCISE_ROUTES)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "isPermissionDeclared error", e)
            false
        }
    }

    fun grantReadRoutesPermission(packageName: String) {
        grantHealthPermissionUseCase.invoke(packageName, READ_EXERCISE_ROUTES)
    }

    fun isSessionInaccessible(packageName: String, session: ExerciseSessionRecord): Boolean {
        if (packageName == session.metadata.dataOrigin.packageName) {
            return false
        }

        val accessLimit = loadAccessDateUseCase(packageName)

        return session.startTime.isBefore(accessLimit)
    }

    data class SessionWithAttribution(val session: ExerciseSessionRecord, val appInfo: AppMetadata)
}
