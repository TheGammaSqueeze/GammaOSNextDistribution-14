/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.app.Application
import android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT
import android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE
import android.os.Handler
import android.os.UserHandle
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_ELIGIBLE
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_EXEMPT_BY_SYSTEM
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_EXEMPT_BY_USER
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.hibernation.ExemptServicesLiveData
import com.android.permissioncontroller.hibernation.HibernationEnabledLiveData
import com.android.permissioncontroller.hibernation.isPackageHibernationExemptBySystem
import com.android.permissioncontroller.hibernation.isPackageHibernationExemptByUser
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.model.livedatatypes.HibernationSettingState
import com.android.permissioncontroller.permission.service.AUTO_REVOKE_EXEMPT_PERMISSIONS
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks the hibernation/auto-revoke state for one user package.
 *
 * @param app The current application
 * @param packageName The package name whose state we want
 * @param user The user for whom we want the package
 */
class HibernationSettingStateLiveData
private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<HibernationSettingState>(), AppOpsManager.OnOpChangedListener {

    private val packagePermsLiveData = PackagePermissionsLiveData[packageName, user]
    private val packageLiveData = LightPackageInfoLiveData[packageName, user]
    private val permStateLiveDatas = mutableMapOf<String, PermStateLiveData>()
    private val exemptServicesLiveData = ExemptServicesLiveData[user]
    private val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    // TODO 206455664: remove these once issue is identified
    private val LOG_TAG = "HibernationSettingStateLiveData"
    private val DELAY_MS = 3000L
    private var gotPermLiveDatas: Boolean = false
    private var gotPastIsUserExempt: Boolean = false
    private var gotPastIsSystemExempt: Boolean = false

    init {
        addSource(packagePermsLiveData) { update() }
        addSource(packageLiveData) { update() }
        addSource(exemptServicesLiveData) { update() }
        addSource(HibernationEnabledLiveData) { update() }
        Handler(app.mainLooper).postDelayed({ logState() }, DELAY_MS)
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (
            !packageLiveData.isInitialized ||
                !packagePermsLiveData.isInitialized ||
                !exemptServicesLiveData.isInitialized
        ) {
            return
        }

        val groups = packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }
        val packageInfo = packageLiveData.value
        if (packageInfo == null || groups == null) {
            postValue(null)
            return
        }
        val getLiveData = { groupName: String -> PermStateLiveData[packageName, groupName, user] }
        setSourcesToDifference(groups, permStateLiveDatas, getLiveData)
        gotPermLiveDatas = true

        if (!permStateLiveDatas.all { it.value.isInitialized }) {
            return
        }

        val exemptBySystem = isPackageHibernationExemptBySystem(packageInfo, user)
        val exemptByUser = isPackageHibernationExemptByUser(app, packageInfo)
        val eligibility =
            when {
                !exemptBySystem && !exemptByUser -> HIBERNATION_ELIGIBILITY_ELIGIBLE
                exemptBySystem -> HIBERNATION_ELIGIBILITY_EXEMPT_BY_SYSTEM
                else -> HIBERNATION_ELIGIBILITY_EXEMPT_BY_USER
            }
        gotPastIsUserExempt = true
        val revocableGroups = mutableListOf<String>()
        if (!isPackageHibernationExemptBySystem(packageInfo, user)) {
            gotPastIsSystemExempt = true
            permStateLiveDatas.forEach { (groupName, liveData) ->
                val default =
                    liveData.value?.any { (_, permState) ->
                        permState.permFlags and
                            (FLAG_PERMISSION_GRANTED_BY_DEFAULT or
                                FLAG_PERMISSION_GRANTED_BY_ROLE) != 0
                    }
                        ?: false
                val allExempt =
                    liveData.value?.all { (permName, _) ->
                        permName in AUTO_REVOKE_EXEMPT_PERMISSIONS
                    }
                        ?: false
                if (!default && !allExempt) {
                    revocableGroups.add(groupName)
                }
            }
        }
        gotPastIsSystemExempt = true

        postValue(HibernationSettingState(eligibility, revocableGroups))
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        if (op == OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED && packageName == packageName) {
            update()
        }
    }

    override fun onActive() {
        super.onActive()
        appOpsManager.startWatchingMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageName, this)
    }

    override fun onInactive() {
        super.onInactive()
        appOpsManager.stopWatchingMode(this)
    }

    // TODO 206455664: remove these once issue is identified
    private fun logState() {
        if (!isStale) {
            return
        }
        Log.i(
            LOG_TAG,
            "overall state: isStale:$isStale, isInitialized:$isInitialized, " +
                "value:$value, got perm LiveDatas:$gotPermLiveDatas, " +
                "got isUserExempt$gotPastIsUserExempt, got isSystemExempt$gotPastIsSystemExempt"
        )
        Log.i(
            LOG_TAG,
            "packagePermsLivedata isStale:${packagePermsLiveData.isStale}, " +
                "isInitialized:${packagePermsLiveData.isInitialized}"
        )
        Log.i(
            LOG_TAG,
            "ExemptServicesLiveData isStale:${exemptServicesLiveData.isStale}, " +
                "isInitialized:${exemptServicesLiveData.isInitialized}"
        )
        Log.i(LOG_TAG, "HibernationEnabledLivedata value:${HibernationEnabledLiveData.value}")
        for ((group, liveData) in permStateLiveDatas) {
            Log.i(
                LOG_TAG,
                "permStateLivedata $group isStale:${liveData.isStale}, " +
                    "isInitialized:${liveData.isInitialized}"
            )
        }
    }
    /**
     * Repository for HibernationSettingStateLiveDatas.
     *
     * <p> Key value is a pair of string package name and UserHandle, value is its corresponding
     * LiveData.
     */
    companion object :
        DataRepositoryForPackage<Pair<String, UserHandle>, HibernationSettingStateLiveData>() {
        override fun newValue(key: Pair<String, UserHandle>): HibernationSettingStateLiveData {
            return HibernationSettingStateLiveData(
                PermissionControllerApplication.get(),
                key.first,
                key.second
            )
        }
    }
}
