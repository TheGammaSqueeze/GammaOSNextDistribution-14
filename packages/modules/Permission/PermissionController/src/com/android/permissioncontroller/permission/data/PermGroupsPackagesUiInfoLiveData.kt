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

import android.app.Application
import android.app.role.RoleManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo
import com.android.permissioncontroller.permission.utils.Utils

/**
 * A LiveData which tracks all app permission groups for a set of permission groups, either platform
 * or custom, as well as the UI information related to each app permission group, and the permission
 * group as a whole.
 *
 * @param app The current application
 */
class PermGroupsPackagesUiInfoLiveData(
    private val app: Application,
    private val groupNamesLiveData: LiveData<List<String>>
) :
    SmartUpdateMediatorLiveData<
        @kotlin.jvm.JvmSuppressWildcards Map<String, PermGroupPackagesUiInfo?>
    >() {
    private val SYSTEM_SHELL = "android.app.role.SYSTEM_SHELL"

    private val STAGGER_LOAD_TIME_MS = 50L

    // Optimization: This LiveData relies on a large number of other ones. Enough that they can
    // cause loading issues when they all become active at once. If this value is true, then it will
    // slowly load data from all source LiveDatas, spacing loads them STAGGER_LOAD_TIME_MS apart
    var loadStaggered: Boolean = false

    // If we are returning from a particular permission group page, then that particular group is
    // the one most likely to change. If so, then it will be prioritized in the load order.
    var firstLoadGroup: String? = null

    private val handler: Handler = Handler(Looper.getMainLooper())

    /** Map<permission group name, PermGroupUiLiveDatas> */
    private val permGroupPackagesLiveDatas =
        mutableMapOf<String, SinglePermGroupPackagesUiInfoLiveData>()
    private val allPackageData = mutableMapOf<String, PermGroupPackagesUiInfo?>()

    private lateinit var groupNames: List<String>
    private val userManager =
        Utils.getSystemServiceSafe(app.applicationContext, UserManager::class.java)

    init {
        addSource(groupNamesLiveData) {
            groupNames = it
            update()
            getPermGroupPackageLiveDatas()
        }
    }

    private fun getPermGroupPackageLiveDatas() {
        val getLiveData = { groupName: String -> SinglePermGroupPackagesUiInfoLiveData[groupName] }
        setSourcesToDifference(groupNames, permGroupPackagesLiveDatas, getLiveData)
    }

    private fun isGranted(grantState: AppPermGroupUiInfo.PermGrantState): Boolean {
        return grantState != AppPermGroupUiInfo.PermGrantState.PERMS_DENIED &&
            grantState != AppPermGroupUiInfo.PermGrantState.PERMS_ASK
    }

    private fun createPermGroupPackageUiInfo(
        groupName: String,
        appPermGroups: Map<Pair<String, UserHandle>, AppPermGroupUiInfo>
    ): PermGroupPackagesUiInfo {
        var nonSystem = 0
        var grantedNonSystem = 0
        var userInteractedNonSystem = 0
        var grantedSystem = 0
        var userInteractedSystem = 0
        var firstGrantedSystemPackageName: String? = null
        val showInSettingsByUsers = HashMap<UserHandle, Boolean>()

        for ((packageUserPair, appPermGroup) in appPermGroups) {
            if (!appPermGroup.shouldShow) {
                continue
            }

            if (!showInSettingsByUsers.containsKey(packageUserPair.second)) {
                showInSettingsByUsers[packageUserPair.second] =
                    Utils.shouldShowInSettings(packageUserPair.second, userManager)
            }

            if (showInSettingsByUsers[packageUserPair.second] == false) {
                continue
            }

            if (appPermGroup.isSystem) {
                if (isGranted(appPermGroup.permGrantState)) {
                    if (grantedSystem == 0) {
                        firstGrantedSystemPackageName = packageUserPair.first
                    }
                    grantedSystem++
                    userInteractedSystem++
                } else if (appPermGroup.isUserSet) {
                    userInteractedSystem++
                }
            } else {
                nonSystem++

                if (isGranted(appPermGroup.permGrantState)) {
                    grantedNonSystem++
                    userInteractedNonSystem++
                } else if (appPermGroup.isUserSet) {
                    userInteractedNonSystem++
                }
            }
        }
        val onlyShellGranted =
            grantedNonSystem == 0 &&
                grantedSystem == 1 &&
                isPackageShell(firstGrantedSystemPackageName)
        return PermGroupPackagesUiInfo(
            groupName,
            nonSystem,
            grantedNonSystem,
            userInteractedNonSystem,
            grantedSystem,
            userInteractedSystem,
            onlyShellGranted
        )
    }

    private fun isPackageShell(packageName: String?): Boolean {
        if (packageName == null) {
            return false
        }

        // This method is only called at most once per permission group, so no need to cache value
        val roleManager =
            Utils.getSystemServiceSafe(
                PermissionControllerApplication.get(),
                RoleManager::class.java
            )
        return roleManager.getRoleHolders(SYSTEM_SHELL).contains(packageName)
    }

    override fun onUpdate() {
        /**
         * Only update when either- We have a list of groups, and none have loaded their data, or
         * All packages have loaded their data
         */
        val haveAllLiveDatas = groupNames.all { permGroupPackagesLiveDatas.contains(it) }
        val allInitialized = permGroupPackagesLiveDatas.all { it.value.isInitialized }
        for (groupName in groupNames) {
            allPackageData[groupName] =
                if (haveAllLiveDatas && allInitialized) {
                    permGroupPackagesLiveDatas[groupName]?.value?.let { uiInfo ->
                        createPermGroupPackageUiInfo(groupName, uiInfo)
                    }
                } else {
                    null
                }
        }
        value = allPackageData.toMap()
    }

    // Schedule a staggered loading of individual permission group livedatas
    private fun scheduleStaggeredGroupLoad() {
        if (groupNamesLiveData.value != null) {
            if (firstLoadGroup in groupNames) {
                addLiveDataDelayed(firstLoadGroup!!, 0)
            }
            for ((idx, groupName) in groupNames.withIndex()) {
                if (groupName != firstLoadGroup) {
                    addLiveDataDelayed(groupName, idx * STAGGER_LOAD_TIME_MS)
                }
            }
        }
    }

    private fun addLiveDataDelayed(groupName: String, delayTimeMs: Long) {
        val liveData = SinglePermGroupPackagesUiInfoLiveData[groupName]
        permGroupPackagesLiveDatas[groupName] = liveData
        handler.postDelayed({ addSource(liveData) { update() } }, delayTimeMs)
    }

    override fun onActive() {
        super.onActive()
        if (loadStaggered && permGroupPackagesLiveDatas.isEmpty()) {
            scheduleStaggeredGroupLoad()
        }
    }

    override fun onInactive() {
        super.onInactive()
        if (loadStaggered) {
            permGroupPackagesLiveDatas.forEach { (_, liveData) -> removeSource(liveData) }
            permGroupPackagesLiveDatas.clear()
        }
    }
}
