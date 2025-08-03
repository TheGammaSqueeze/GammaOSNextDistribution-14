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

import android.content.pm.PackageInfo
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.os.Build
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tracks which packages have been auto-revoked, and which groups have been auto revoked for those
 * packages.
 *
 * ```(packageName, user) -> [groupName]```
 */
object AutoRevokedPackagesLiveData :
    SmartAsyncMediatorLiveData<Map<Pair<String, UserHandle>, Set<String>>>() {

    private val LOG_TAG = AutoRevokedPackagesLiveData::class.java.simpleName

    init {
        addSource(AllPackageInfosLiveData) { update() }
    }

    private val permStateLiveDatas =
        mutableMapOf<Triple<String, String, UserHandle>, PermStateLiveData>()
    private val packageAutoRevokedPermsList =
        mutableMapOf<Pair<String, UserHandle>, MutableSet<String>>()

    override suspend fun loadDataAndPostValue(job: Job) {
        if (!AllPackageInfosLiveData.isInitialized) {
            return
        }

        val allPackageGroups = mutableSetOf<Triple<String, String, UserHandle>>()
        for ((user, packageList) in AllPackageInfosLiveData.value ?: emptyMap()) {
            for (pkg in packageList) {
                if (job.isCancelled) {
                    return
                }

                val pkgGroups = mutableSetOf<Triple<String, String, UserHandle>>()
                for ((idx, requestedPerm) in pkg.requestedPermissions.withIndex()) {
                    val group =
                        PermissionMapping.getGroupOfPlatformPermission(requestedPerm) ?: continue
                    val granted =
                        (pkg.requestedPermissionsFlags[idx] and
                            PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (pkg.targetSdkVersion < Build.VERSION_CODES.M || !granted) {
                        pkgGroups.add(Triple(pkg.packageName, group, user))
                    }
                }
                allPackageGroups.addAll(pkgGroups)
            }
        }

        if (allPackageGroups.isEmpty()) {
            postCopyOfMap()
        } else {
            observePermStateLiveDatas(allPackageGroups)
        }
    }

    private fun observePermStateLiveDatas(packageGroups: Set<Triple<String, String, UserHandle>>) {
        GlobalScope.launch(Main.immediate) {
            val (toAdd, toRemove) =
                KotlinUtils.getMapAndListDifferences(packageGroups, permStateLiveDatas)

            for (packagePermGroup in toRemove) {
                removeSource(permStateLiveDatas.remove(packagePermGroup) ?: continue)
                val packageUser = packagePermGroup.first to packagePermGroup.third
                packageAutoRevokedPermsList[packageUser]?.remove(packagePermGroup.second)
                if (packageAutoRevokedPermsList[packageUser]?.isEmpty() == true) {
                    packageAutoRevokedPermsList.remove(packageUser)
                }
            }

            if (toRemove.isNotEmpty()) {
                postCopyOfMap()
            }

            for (packagePermGroup in toAdd) {
                permStateLiveDatas[packagePermGroup] = PermStateLiveData[packagePermGroup]
            }

            for (packagePermGroup in toAdd) {
                val permStateLiveData = permStateLiveDatas[packagePermGroup]!!
                val packageUser = packagePermGroup.first to packagePermGroup.third

                addSource(permStateLiveData) { permState ->
                    var added = false
                    if (permState == null && permStateLiveData.isInitialized) {
                        permStateLiveDatas.remove(packagePermGroup)
                        removeSource(permStateLiveData)
                    } else if (permState != null) {
                        for ((_, state) in permState) {
                            if (state.permFlags and FLAG_PERMISSION_AUTO_REVOKED != 0) {
                                packageAutoRevokedPermsList
                                    .getOrPut(packageUser) { mutableSetOf() }
                                    .add(packagePermGroup.second)
                                added = true
                                break
                            }
                        }
                    }

                    if (!added) {
                        packageAutoRevokedPermsList[packageUser]?.remove(packagePermGroup.second)
                        if (packageAutoRevokedPermsList[packageUser]?.isEmpty() == true) {
                            packageAutoRevokedPermsList.remove(packageUser)
                        }
                    }

                    if (permStateLiveDatas.all { it.value.isInitialized }) {
                        postCopyOfMap()
                    }
                }
            }
        }
    }

    private fun postCopyOfMap() {
        val autoRevokedCopy = mutableMapOf<Pair<String, UserHandle>, Set<String>>()
        for ((userPackage, permGroups) in packageAutoRevokedPermsList) {
            autoRevokedCopy[userPackage] = permGroups.toSet()
        }
        Log.i(LOG_TAG, "postValue: $autoRevokedCopy")
        postValue(autoRevokedCopy)
    }
}

private val autoRevokedPackagesSetLiveData =
    object : SmartUpdateMediatorLiveData<Set<Pair<String, UserHandle>>>() {
        init {
            addSource(AutoRevokedPackagesLiveData) { update() }
        }

        override fun onUpdate() {
            if (!AutoRevokedPackagesLiveData.isInitialized) {
                return
            }
            value = AutoRevokedPackagesLiveData.value!!.keys
        }
    }

val unusedAutoRevokePackagesLiveData = UnusedPackagesLiveData(autoRevokedPackagesSetLiveData)
