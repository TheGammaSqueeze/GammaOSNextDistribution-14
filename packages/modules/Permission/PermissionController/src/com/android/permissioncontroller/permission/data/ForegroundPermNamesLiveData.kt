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

import android.content.pm.PackageManager
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * LiveData for a map of background permission name -> list of foreground permission names for every
 * installed, runtime permission in every platform permission group. This LiveData's value is
 * static, since the background/foreground permission relationships are defined by the system.
 */
object ForegroundPermNamesLiveData : SmartAsyncMediatorLiveData<Map<String, List<String>>>(true) {
    private val app = PermissionControllerApplication.get()

    // Since the value will be static, initialize the value upon creating the LiveData.
    init {
        onUpdate()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val systemGroups = PermissionMapping.getPlatformPermissionGroups()
        val permMap = mutableMapOf<String, MutableList<String>>()
        for (groupName in systemGroups) {
            val permInfos =
                try {
                    Utils.getInstalledRuntimePermissionInfosForGroup(app.packageManager, groupName)
                } catch (e: PackageManager.NameNotFoundException) {
                    continue
                }
            for (permInfo in permInfos) {
                val backgroundPerm: String? = permInfo.backgroundPermission
                if (backgroundPerm != null) {
                    val foregroundPerms = permMap.getOrPut(backgroundPerm) { mutableListOf() }
                    foregroundPerms.add(permInfo.name)
                }
            }
        }
        postValue(permMap)
    }
}
