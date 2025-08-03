/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.data.v34

import android.app.Application
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.DataRepositoryForPackage
import com.android.permissioncontroller.permission.data.PackageBroadcastReceiver
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.v34.LightInstallSourceInfo
import com.android.permissioncontroller.permission.model.livedatatypes.v34.LightInstallSourceInfo.Companion.INSTALL_SOURCE_UNAVAILABLE
import kotlinx.coroutines.Job

/**
 * [LightInstallSourceInfo] [LiveData] for the specified package
 *
 * @param app current Application
 * @param packageName name of the package to get InstallSourceInfo for
 * @param user The user of the package
 */
class LightInstallSourceInfoLiveData
private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) :
    SmartAsyncMediatorLiveData<LightInstallSourceInfo>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    override fun onActive() {
        super.onActive()
        PackageBroadcastReceiver.addChangeCallback(packageName, this)
    }

    override fun onInactive() {
        super.onInactive()
        PackageBroadcastReceiver.removeChangeCallback(packageName, this)
    }

    /**
     * Callback from the PackageBroadcastReceiver
     *
     * @param packageName the name of the package which was updated.
     */
    override fun onPackageUpdate(packageName: String) {
        update()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val lightInstallSourceInfo: LightInstallSourceInfo =
            try {
                val installSourceInfo = getInstallSourceInfo(packageName)
                LightInstallSourceInfo(
                    installSourceInfo.packageSource,
                    installSourceInfo.initiatingPackageName
                )
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "InstallSourceInfo for $packageName not found")
                invalidateSingle(packageName to user)
                INSTALL_SOURCE_UNAVAILABLE
            }
        postValue(lightInstallSourceInfo)
    }

    /** Returns the [InstallSourceInfo] for the given package. */
    @Throws(PackageManager.NameNotFoundException::class)
    private fun getInstallSourceInfo(packageName: String): InstallSourceInfo {
        val userContext =
            if (user == Process.myUserHandle()) {
                app
            } else {
                app.createContextAsUser(user, /* flags= */ 0)
            }
        return userContext.packageManager.getInstallSourceInfo(packageName)
    }

    companion object :
        DataRepositoryForPackage<Pair<String, UserHandle>, LightInstallSourceInfoLiveData>() {
        private val LOG_TAG = LightInstallSourceInfoLiveData::class.java.simpleName

        override fun newValue(key: Pair<String, UserHandle>): LightInstallSourceInfoLiveData {
            return LightInstallSourceInfoLiveData(
                PermissionControllerApplication.get(),
                key.first,
                key.second
            )
        }
    }
}
