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

import android.app.Application
import android.app.role.RoleManager
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.RoleListenerMultiplexer.RoleHoldersChangeCallback
import kotlinx.coroutines.Job

/**
 * LiveData for a list of packages which hold a given role for a user.
 *
 * @param app The current application
 * @param roleName The name of the role
 */
class RoleHoldersLiveData
private constructor(
    private val app: Application,
    private val roleName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<List<String>>(), RoleHoldersChangeCallback {

    private val roleManager = app.getSystemService(RoleManager::class.java)!!

    override fun onActive() {
        super.onActive()
        RoleListenerMultiplexer.addCallback(roleName, user, this)
    }

    override fun onInactive() {
        super.onInactive()
        RoleListenerMultiplexer.removeCallback(roleName, user, this)
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        postValue(roleManager.getRoleHoldersAsUser(roleName, user))
    }

    override fun onRoleHoldersChanged() {
        updateAsync()
    }

    /**
     * Repository for RoleHoldersLiveData.
     *
     * <p> Key value is the name of the role.
     */
    companion object : DataRepository<Pair<String, UserHandle>, RoleHoldersLiveData>() {
        override fun newValue(key: Pair<String, UserHandle>): RoleHoldersLiveData {
            return RoleHoldersLiveData(PermissionControllerApplication.get(), key.first, key.second)
        }
    }
}
