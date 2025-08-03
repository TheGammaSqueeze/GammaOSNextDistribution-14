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

package com.android.permissioncontroller.hibernation.v31

import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.DataRepositoryForPackage
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import kotlinx.coroutines.Job

/** Packages that are the installer of record for some package on the device. */
@RequiresApi(Build.VERSION_CODES.S)
class InstallerPackagesLiveData(private val user: UserHandle) :
    SmartAsyncMediatorLiveData<Set<String>>() {

    init {
        addSource(AllPackageInfosLiveData) { update() }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        if (!AllPackageInfosLiveData.isInitialized) {
            return
        }
        val userPackageInfos = AllPackageInfosLiveData.value!![user]
        val installerPackages = mutableSetOf<String>()
        val packageManager = PermissionControllerApplication.get().packageManager

        userPackageInfos!!.forEach { pkgInfo ->
            try {
                val installerPkg =
                    packageManager.getInstallSourceInfo(pkgInfo.packageName).installingPackageName
                if (installerPkg != null) {
                    installerPackages.add(installerPkg)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                DumpableLog.w(LOG_TAG, "Unable to find installer source info", e)
            }
        }

        postValue(installerPackages)
    }

    /**
     * Repository for installer packages
     *
     * <p> Key value is user
     */
    companion object : DataRepositoryForPackage<UserHandle, InstallerPackagesLiveData>() {
        override fun newValue(key: UserHandle): InstallerPackagesLiveData {
            return InstallerPackagesLiveData(key)
        }
    }
}
