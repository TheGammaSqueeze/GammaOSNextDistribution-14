/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.Observer
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.ContextCompat
import com.android.permissioncontroller.permission.utils.MultiDeviceUtils.isPermissionDeviceAware
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * LiveData for a LightPackageInfo.
 *
 * @param app The current Application
 * @param packageName The name of the package this LiveData will watch for mode changes for
 * @param user The user for whom the packageInfo will be defined
 */
class LightPackageInfoLiveData
private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle,
    private val deviceId: Int
) :
    SmartAsyncMediatorLiveData<LightPackageInfo?>(alwaysUpdateOnActive = false),
    PackageBroadcastReceiver.PackageBroadcastListener,
    PermissionListenerMultiplexer.PermissionChangeCallback {

    private val LOG_TAG = LightPackageInfoLiveData::class.java.simpleName
    private val userPackagesLiveData = UserPackageInfosLiveData[user]

    private var uid: Int? = null
    /** The currently registered UID on which this LiveData is listening for permission changes. */
    private var registeredUid: Int? = null
    /** Whether or not this package livedata is watching the UserPackageInfosLiveData */
    private var watchingUserPackagesLiveData: Boolean = false

    /**
     * Callback from the PackageBroadcastReceiver. Either deletes or generates package data.
     *
     * @param packageName the name of the package which was updated. Ignored in this method
     */
    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    override fun setValue(newValue: LightPackageInfo?) {
        newValue?.let { packageInfo ->
            if (packageInfo.uid != uid) {
                uid = packageInfo.uid

                if (hasActiveObservers()) {
                    PermissionListenerMultiplexer.addOrReplaceCallback(
                        registeredUid,
                        packageInfo.uid,
                        this
                    )
                    registeredUid = uid
                }
            }
        }
        super.setValue(newValue)
    }

    override fun updateAsync() {
        // If we were watching the userPackageInfosLiveData, stop, since we will override its value
        if (watchingUserPackagesLiveData) {
            removeSource(userPackagesLiveData)
            watchingUserPackagesLiveData = false
        }
        super.updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        postValue(
            try {
                var flags = PackageManager.GET_PERMISSIONS
                if (SdkLevel.isAtLeastS()) {
                    flags = flags or PackageManager.GET_ATTRIBUTIONS
                }

                val packageManager = Utils.getUserContext(app, user).packageManager
                val pI = packageManager.getPackageInfo(packageName, flags)

                // PackageInfo#requestedPermissionsFlags is not device aware. Hence for device aware
                // permissions if the deviceId is not the primary device we need to separately check
                // permission for that device and update requestedPermissionsFlags.
                if (SdkLevel.isAtLeastV() && deviceId != ContextCompat.DEVICE_ID_DEFAULT) {
                    val requestedPermissionsFlagsForDevice =
                        getPermissionsFlagsForDevice(
                            pI.requestedPermissions?.toList() ?: emptyList(),
                            pI.requestedPermissionsFlags?.toList() ?: emptyList(),
                            pI.applicationInfo!!.uid,
                            deviceId
                        )

                    LightPackageInfo(pI, deviceId, requestedPermissionsFlagsForDevice)
                } else {
                    LightPackageInfo(pI)
                }
            } catch (e: Exception) {
                if (e is PackageManager.NameNotFoundException) {
                    Log.w(LOG_TAG, "Package \"$packageName\" not found for user $user")
                } else {
                    val profiles = app.getSystemService(UserManager::class.java)!!.userProfiles
                    Log.e(
                        LOG_TAG,
                        "Failed to create context for user $user. " +
                            "User exists : ${user in profiles }",
                        e
                    )
                }
                invalidateSingle(Triple(packageName, user, deviceId))
                null
            }
        )
    }

    /** Callback from the PermissionListener. Either deletes or generates package data. */
    override fun onPermissionChange() {
        updateAsync()
    }

    override fun onActive() {
        super.onActive()

        PackageBroadcastReceiver.addChangeCallback(packageName, this)
        uid?.let {
            registeredUid = uid
            PermissionListenerMultiplexer.addCallback(it, this)
        }
        if (
            userPackagesLiveData.hasActiveObservers() &&
                !watchingUserPackagesLiveData &&
                !userPackagesLiveData.permChangeStale
        ) {
            watchingUserPackagesLiveData = true
            addSource(userPackagesLiveData, userPackageInfosObserver)
        } else {
            updateAsync()
        }
    }

    private val userPackageInfosObserver =
        Observer<List<LightPackageInfo>> { updateFromUserPackageInfosLiveData() }

    @MainThread
    private fun updateFromUserPackageInfosLiveData() {
        if (!userPackagesLiveData.isInitialized) {
            return
        }

        val packageInfo = userPackagesLiveData.value!!.find { it.packageName == packageName }
        if (packageInfo != null) {
            // Once we get one non-stale update, stop listening, as any further updates will likely
            // be individual package updates.
            if (!userPackagesLiveData.isStale) {
                removeSource(UserPackageInfosLiveData[user])
                watchingUserPackagesLiveData = false
            }

            if (SdkLevel.isAtLeastV() && deviceId != Context.DEVICE_ID_DEFAULT) {
                packageInfo.deviceId = deviceId
                packageInfo.requestedPermissionsFlags =
                    getPermissionsFlagsForDevice(
                        packageInfo.requestedPermissions,
                        packageInfo.requestedPermissionsFlags,
                        packageInfo.uid,
                        deviceId
                    )
            }
            value = packageInfo
        } else {
            // If the UserPackageInfosLiveData does not contain this package, check for removal, and
            // stop watching.
            updateAsync()
        }
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.removeChangeCallback(packageName, this)
        registeredUid?.let {
            PermissionListenerMultiplexer.removeCallback(it, this)
            registeredUid = null
        }
        if (watchingUserPackagesLiveData) {
            removeSource(userPackagesLiveData)
            watchingUserPackagesLiveData = false
        }
    }

    // Given permission flags of the default device and an external device Id, return a new list of
    // permission flags for that device by checking grant state of device aware permissions for the
    // device.
    private fun getPermissionsFlagsForDevice(
        requestedPermissions: List<String>,
        requestedPermissionsFlags: List<Int>,
        uid: Int,
        deviceId: Int
    ): List<Int> {
        val requestedPermissionsFlagsForDevice = requestedPermissionsFlags.toMutableList()
        val deviceContext = ContextCompat.createDeviceContext(app, deviceId)

        for ((idx, permName) in requestedPermissions.withIndex()) {
            if (isPermissionDeviceAware(permName)) {
                val result = deviceContext.checkPermission(permName, -1, uid)

                if (result == PackageManager.PERMISSION_GRANTED) {
                    requestedPermissionsFlagsForDevice[idx] =
                        requestedPermissionsFlagsForDevice[idx] or
                            PackageInfo.REQUESTED_PERMISSION_GRANTED
                }

                if (result == PackageManager.PERMISSION_DENIED) {
                    requestedPermissionsFlagsForDevice[idx] =
                        requestedPermissionsFlagsForDevice[idx] and
                            PackageInfo.REQUESTED_PERMISSION_GRANTED.inv()
                }
            }
        }

        return requestedPermissionsFlagsForDevice
    }

    /**
     * Repository for LightPackageInfoLiveDatas
     *
     * <p> Key value is a triple of package name, UserHandle and virtual deviceId, value is its
     * corresponding LiveData.
     */
    companion object :
        DataRepositoryForDevice<Triple<String, UserHandle, Int>, LightPackageInfoLiveData>() {
        override fun newValue(
            key: Triple<String, UserHandle, Int>,
            deviceId: Int
        ): LightPackageInfoLiveData {
            return LightPackageInfoLiveData(
                PermissionControllerApplication.get(),
                key.first,
                key.second,
                deviceId
            )
        }
    }
}
