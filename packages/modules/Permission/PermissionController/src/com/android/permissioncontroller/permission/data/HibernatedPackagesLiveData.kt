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

import android.apphibernation.AppHibernationManager
import android.content.Context.APP_HIBERNATION_SERVICE
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils.getUserContext
import kotlinx.coroutines.Job

/** Tracks which packages have been hibernated. */
object HibernatedPackagesLiveData : SmartAsyncMediatorLiveData<Set<Pair<String, UserHandle>>>() {
    private val LOG_TAG = HibernatedPackagesLiveData::class.java.simpleName

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
        val allPackages = AllPackageInfosLiveData.value!!
        val hibernatingPackages = mutableSetOf<Pair<String, UserHandle>>()
        for ((user, pkgs) in allPackages) {
            val userContext = getUserContext(PermissionControllerApplication.get(), user)
            val hibernationManager =
                userContext.getSystemService(APP_HIBERNATION_SERVICE) as AppHibernationManager
            for (pkg in pkgs) {
                try {
                    if (hibernationManager.isHibernatingForUser(pkg.packageName)) {
                        hibernatingPackages.add(pkg.packageName to user)
                    }
                } catch (e: Exception) {
                    DumpableLog.e(
                        LOG_TAG,
                        "Failed to get hibernation state of package: ${pkg.packageName}"
                    )
                }
            }
        }

        Log.i(LOG_TAG, "postValue: $hibernatingPackages")

        postValue(hibernatingPackages)
    }
}

private val hibernatedOrRevokedPackagesLiveData =
    object : SmartUpdateMediatorLiveData<Set<Pair<String, UserHandle>>>() {

        init {
            addSource(AutoRevokedPackagesLiveData) { update() }
            addSource(HibernatedPackagesLiveData) { update() }
        }

        override fun onUpdate() {
            if (
                !AutoRevokedPackagesLiveData.isInitialized ||
                    !HibernatedPackagesLiveData.isInitialized
            ) {
                return
            }
            value = AutoRevokedPackagesLiveData.value!!.keys + HibernatedPackagesLiveData.value!!
        }
    }
val unusedHibernatedOrRevokedPackagesLiveData =
    UnusedPackagesLiveData(hibernatedOrRevokedPackagesLiveData)
