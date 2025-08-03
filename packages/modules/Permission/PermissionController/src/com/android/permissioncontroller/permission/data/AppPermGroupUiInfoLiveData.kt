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

package com.android.permissioncontroller.permission.data

import android.Manifest
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission_group.READ_MEDIA_VISUAL
import android.Manifest.permission_group.STORAGE
import android.app.AppOpsManager
import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermState
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping.isPlatformPermissionGroup
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData representing UI properties of an App Permission Group:
 * <ul>
 * <li>shouldShow</li>
 * <li>isSystem</li>
 * <li>isGranted</li>
 * </ul>
 *
 * @param app The current application
 * @param packageName The name of the package
 * @param permGroupName The name of the permission group whose permissions are observed
 * @param user The user of the package
 */
class AppPermGroupUiInfoLiveData
private constructor(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<AppPermGroupUiInfo>(), LocationUtils.LocationListener {

    private var isSpecialLocation = false
    private val packageInfoLiveData = LightPackageInfoLiveData[packageName, user]
    private val permGroupLiveData = PermGroupLiveData[permGroupName]
    private val permissionStateLiveData = PermStateLiveData[packageName, permGroupName, user]
    private val isStorage = permGroupName == STORAGE
    private val isHealth = Utils.isHealthPermissionGroup(permGroupName)

    init {
        isSpecialLocation = LightAppPermGroupLiveData
            .isSpecialLocationGranted(app, packageName, permGroupName, user) != null

        addSource(packageInfoLiveData) { update() }

        addSource(permGroupLiveData) { update() }

        addSource(permissionStateLiveData) { update() }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        val packageInfo = packageInfoLiveData.value
        val permissionGroup = permGroupLiveData.value
        val permissionState = permissionStateLiveData.value

        if (packageInfo == null || permissionGroup == null || permissionState == null) {
            if (
                packageInfoLiveData.isInitialized &&
                    permGroupLiveData.isInitialized &&
                    permissionStateLiveData.isInitialized
            ) {
                invalidateSingle(Triple(packageName, permGroupName, user))
                postValue(null)
            }
            return
        }

        postValue(
            getAppPermGroupUiInfo(
                packageInfo,
                permissionGroup.groupInfo,
                permissionGroup.permissionInfos,
                permissionState
            )
        )
    }

    /**
     * Determines if the UI should show a given package, if that package is a system app, and if it
     * has granted permissions in this LiveData's permission group.
     *
     * @param packageInfo The PackageInfo of the package we wish to examine
     * @param groupInfo The groupInfo of the permission group we wish to examine
     * @param allPermInfos All of the PermissionInfos in the permission group
     * @param permissionState The flags and grant state for all permissions in the permission group
     *   that this package requests
     */
    private fun getAppPermGroupUiInfo(
        packageInfo: LightPackageInfo,
        groupInfo: LightPermGroupInfo,
        allPermInfos: Map<String, LightPermInfo>,
        permissionState: Map<String, PermState>
    ): AppPermGroupUiInfo {
        /*
         * Filter out any permission infos in the permission group that this package
         * does not request.
         */
        val requestedPermissionInfos =
            allPermInfos.filter { permissionState.containsKey(it.key) }.values

        val shouldShow =
            packageInfo.enabled &&
                isGrantableAndNotLegacyPlatform(packageInfo, groupInfo, requestedPermissionInfos) &&
                (!isStorage || Utils.shouldShowStorage(packageInfo)) &&
                (!isHealth || Utils.shouldShowHealthPermission(packageInfo, groupInfo.name))

        val isSystemApp = !isUserSensitive(permissionState)

        val isUserSet = isUserSet(permissionState)

        val permGrantState =
            getGrantedIncludingBackground(permissionState, allPermInfos, packageInfo)

        return AppPermGroupUiInfo(shouldShow, permGrantState, isSystemApp, isUserSet)
    }

    /**
     * Determines if a package permission group is able to be granted, and whether or not it is a
     * legacy system permission group.
     *
     * @param packageInfo The PackageInfo of the package we are examining
     * @param groupInfo The Permission Group Info of the permission group we are examining
     * @param permissionInfos The LightPermInfos corresponding to the permissions in the permission
     *   group that this package requests
     * @return True if the app permission group is grantable, and is not a legacy system permission,
     *   false otherwise.
     */
    private fun isGrantableAndNotLegacyPlatform(
        packageInfo: LightPackageInfo,
        groupInfo: LightPermGroupInfo,
        permissionInfos: Collection<LightPermInfo>
    ): Boolean {
        if (groupInfo.packageName == Utils.OS_PKG && !isPlatformPermissionGroup(groupInfo.name)) {
            return false
        }

        var hasInstantPerm = false
        var hasPreRuntime = false

        for (permissionInfo in permissionInfos) {
            if (
                permissionInfo.protectionFlags and PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY == 0
            ) {
                hasPreRuntime = true
            }

            if (permissionInfo.protectionFlags and PermissionInfo.PROTECTION_FLAG_INSTANT != 0) {
                hasInstantPerm = true
            }
        }

        val isGrantingAllowed =
            (!packageInfo.isInstantApp || hasInstantPerm) &&
                (packageInfo.targetSdkVersion >= Build.VERSION_CODES.M || hasPreRuntime)
        if (!isGrantingAllowed) {
            return false
        }

        return true
    }

    /**
     * Determines if an app's permission group is user-sensitive. If an app is not user sensitive,
     * then it is considered a system app, and hidden in the UI by default.
     *
     * @param permissionState The permission flags and grant state corresponding to the permissions
     *   in this group requested by a given app
     * @return Whether or not this package requests a user sensitive permission in the given
     *   permission group
     */
    private fun isUserSensitive(permissionState: Map<String, PermState>): Boolean {
        if (!isPlatformPermissionGroup(permGroupName)) {
            return true
        }

        for (permissionName in permissionState.keys) {
            val flags = permissionState[permissionName]?.permFlags ?: return true
            val granted = permissionState[permissionName]?.granted ?: return true
            if (
                (granted &&
                    flags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED != 0) ||
                    (!granted &&
                        flags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED != 0)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Determines if the app permission group is user set
     *
     * @param permissionState The permission flags and grant state corresponding to the permissions
     *   in this group requested by a given app
     * @return Whether or not any of the permissions in this group have been set or fixed by the
     *   user
     */
    private fun isUserSet(permissionState: Map<String, PermState>): Boolean {
        val flagMask =
            PackageManager.FLAG_PERMISSION_USER_SET or PackageManager.FLAG_PERMISSION_USER_FIXED
        return permissionState.any { (it.value.permFlags and flagMask) != 0 }
    }

    /**
     * Determines if this app permission group is granted, granted in foreground only, or denied. It
     * is granted if it either requests no background permissions, and has at least one requested
     * permission that is granted, or has granted at least one requested background permission. It
     * is granted in foreground only if it has at least one non-background permission granted, and
     * has denied all requested background permissions. It is denied if all requested permissions
     * are denied.
     *
     * @param permissionState The permission flags and grant state corresponding to the permissions
     *   in this group requested by a given app
     * @param allPermInfos All of the permissionInfos in the permission group of this app permission
     *   group
     * @return The int code corresponding to the app permission group state, either allowed, allowed
     *   in foreground only, or denied.
     */
    private fun getGrantedIncludingBackground(
        permissionState: Map<String, PermState>,
        allPermInfos: Map<String, LightPermInfo>,
        pkg: LightPackageInfo
    ): PermGrantState {
        val specialLocationState = LightAppPermGroupLiveData
            .isSpecialLocationGranted(app, packageName, permGroupName, user)
        val specialFixedStorage = LightAppPermGroupLiveData
            .isSpecialFixedStorageGranted(app, packageName, permGroupName, pkg.uid)
        if (isStorage && isFullFilesAccessGranted(pkg)) {
            return PermGrantState.PERMS_ALLOWED
        } else if (permGroupName == READ_MEDIA_VISUAL && specialFixedStorage) {
            return PermGrantState.PERMS_ALLOWED
        }

        var hasPermWithBackground = false
        var isUserFixed = false

        for ((permName, permState) in permissionState) {
            val permInfo = allPermInfos[permName] ?: continue
            permInfo.backgroundPermission?.let { backgroundPerm ->
                hasPermWithBackground = true
                if (
                    permissionState[backgroundPerm]?.granted == true &&
                        (permissionState[backgroundPerm]!!.permFlags and
                            PackageManager.FLAG_PERMISSION_ONE_TIME == 0) &&
                        specialLocationState != false
                ) {
                    return PermGrantState.PERMS_ALLOWED_ALWAYS
                }
            }
            isUserFixed =
                isUserFixed ||
                    permState.permFlags and PackageManager.FLAG_PERMISSION_USER_FIXED != 0
        }

        // isOneTime indicates whether all granted permissions in permission states are one-time
        // permissions
        val isOneTime =
            permissionState.any {
                it.value.permFlags and PackageManager.FLAG_PERMISSION_ONE_TIME != 0
            } &&
                !permissionState.any {
                    it.value.permFlags and PackageManager.FLAG_PERMISSION_ONE_TIME == 0 &&
                        it.value.granted
                }

        val supportsRuntime = pkg.targetSdkVersion >= Build.VERSION_CODES.M
        val anyAllowed =
            specialLocationState
                ?: permissionState.any { (_, state) ->
                    state.granted ||
                        (supportsRuntime &&
                            (state.permFlags and PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED) !=
                                0)
                }
        val onlySelectedPhotosGranted =
            permissionState.containsKey(READ_MEDIA_VISUAL_USER_SELECTED) &&
                permissionState.all { (permName, state) ->
                    (permName == READ_MEDIA_VISUAL_USER_SELECTED && state.granted) ||
                        (permName != READ_MEDIA_VISUAL_USER_SELECTED && !state.granted)
                }
        if (anyAllowed && (hasPermWithBackground || shouldShowAsForegroundGroup())) {
            return if (isOneTime) {
                PermGrantState.PERMS_ASK
            } else {
                PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
            }
        } else if (anyAllowed) {
            return if (isOneTime || onlySelectedPhotosGranted) {
                PermGrantState.PERMS_ASK
            } else {
                PermGrantState.PERMS_ALLOWED
            }
        }
        if (isUserFixed) {
            return PermGrantState.PERMS_DENIED
        }
        if (isOneTime) {
            return PermGrantState.PERMS_ASK
        }
        return PermGrantState.PERMS_DENIED
    }

    private fun isFullFilesAccessGranted(pkg: LightPackageInfo): Boolean {
        val packageState =
            if (!FullStoragePermissionAppsLiveData.isStale) {
                val fullStoragePackages = FullStoragePermissionAppsLiveData.value ?: return false
                fullStoragePackages.find { it.packageName == packageName && it.user == user }
                    ?: return false
            } else {
                val appOpsManager =
                    Utils.getUserContext(app, UserHandle.getUserHandleForUid(pkg.uid))
                        .getSystemService(AppOpsManager::class.java)!!
                FullStoragePermissionAppsLiveData.getFullStorageStateForPackage(appOpsManager, pkg)
                    ?: return false
            }
        return !packageState.isLegacy && packageState.isGranted
    }

    // TODO moltmann-team: Actually change mic/camera to be a foreground only permission
    private fun shouldShowAsForegroundGroup(): Boolean {
        return permGroupName.equals(Manifest.permission_group.CAMERA) ||
            permGroupName.equals(Manifest.permission_group.MICROPHONE)
    }

    override fun onLocationStateChange(enabled: Boolean) {
        update()
    }

    override fun onActive() {
        super.onActive()
        if (isSpecialLocation) {
            LocationUtils.addLocationListener(this)
            update()
        }
    }

    override fun onInactive() {
        super.onInactive()

        if (isSpecialLocation) {
            LocationUtils.removeLocationListener(this)
        }
    }

    /**
     * Repository for AppPermGroupUiInfoLiveDatas.
     *
     * <p> Key value is a triple of string package name, string permission group name, and
     * UserHandle, value is its corresponding LiveData.
     */
    companion object :
        DataRepositoryForPackage<Triple<String, String, UserHandle>, AppPermGroupUiInfoLiveData>() {
        override fun newValue(key: Triple<String, String, UserHandle>): AppPermGroupUiInfoLiveData {
            return AppPermGroupUiInfoLiveData(
                PermissionControllerApplication.get(),
                key.first,
                key.second,
                key.third
            )
        }
    }
}
