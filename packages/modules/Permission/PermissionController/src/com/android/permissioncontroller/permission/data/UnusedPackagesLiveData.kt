/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.UserHandle
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PERMISSIONS
import android.util.ArraySet
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.hibernation.getUnusedThresholdMs
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.hibernation.lastTimePackageUsed
import com.android.permissioncontroller.permission.utils.Utils

/**
 * Gets all unused packages from an existing live data that have not been opened in a few months and
 * the permission groups that have been revoked for them, if any. This will let us removed used apps
 * from the Unused Apps screen.
 *
 * @param sourceLiveData the live data for packages to base this list of unused apps on
 *
 * ```(packageName, user) -> [groupName]```
 */
class UnusedPackagesLiveData(
    private val sourceLiveData: SmartUpdateMediatorLiveData<Set<Pair<String, UserHandle>>>
) : SmartUpdateMediatorLiveData<Map<Pair<String, UserHandle>, Set<String>>>() {

    private val LOG_TAG = UnusedPackagesLiveData::class.java.simpleName

    private var unusedThreshold = getUnusedThresholdMs()
    private var usageStatsLiveData = UsageStatsLiveData[unusedThreshold]

    init {
        addSource(usageStatsLiveData) { update() }
        addSource(AutoRevokedPackagesLiveData) { update() }
        addSource(sourceLiveData) { update() }
        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_PERMISSIONS,
            PermissionControllerApplication.get().mainExecutor,
            { properties ->
                for (key in properties.keyset) {
                    if (key == Utils.PROPERTY_HIBERNATION_UNUSED_THRESHOLD_MILLIS) {
                        removeSource(usageStatsLiveData)
                        unusedThreshold = getUnusedThresholdMs()
                        usageStatsLiveData = UsageStatsLiveData[unusedThreshold]
                        addSource(usageStatsLiveData) { update() }
                    }
                }
            }
        )
    }

    override fun onUpdate() {
        if (
            !usageStatsLiveData.isInitialized ||
                !AutoRevokedPackagesLiveData.isInitialized ||
                !sourceLiveData.isInitialized
        ) {
            return
        }

        val sourcePackages = sourceLiveData.value!!
        val autoRevokedPackages = AutoRevokedPackagesLiveData.value!!

        val unusedPackages = mutableMapOf<Pair<String, UserHandle>, Set<String>>()
        for (userPackage in sourcePackages) {
            val perms = autoRevokedPackages[userPackage] ?: ArraySet()
            unusedPackages[userPackage] = perms.toSet()
        }

        val now = System.currentTimeMillis()
        for ((user, stats) in usageStatsLiveData.value!!) {
            for (stat in stats) {
                val userPackage = stat.packageName to user
                if (
                    userPackage in autoRevokedPackages &&
                        (now - stat.lastTimePackageUsed()) < unusedThreshold
                ) {
                    unusedPackages.remove(userPackage)
                }
            }
        }

        Log.i(LOG_TAG, "onUpdate() -> $unusedPackages")

        value = unusedPackages
    }
}

fun getUnusedPackages(): UnusedPackagesLiveData {
    return if (isHibernationEnabled()) {
        unusedHibernatedOrRevokedPackagesLiveData
    } else {
        unusedAutoRevokePackagesLiveData
    }
}
