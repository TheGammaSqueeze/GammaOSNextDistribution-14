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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import android.app.usage.UsageStats
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKED_APP_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__NEWER_BUCKET
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__OLDER_BUCKET
import com.android.permissioncontroller.hibernation.lastTimePackageUsed
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.getUnusedPackages
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.Utils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * UnusedAppsViewModel for the AutoRevokeFragment. Has a livedata which provides all unused apps,
 * organized by how long they have been unused.
 */
class UnusedAppsViewModel(private val app: Application, private val sessionId: Long) : ViewModel() {

    companion object {
        private val MAX_UNUSED_PERIOD_MILLIS =
            UnusedPeriod.allPeriods.maxBy(UnusedPeriod::duration).duration.inWholeMilliseconds
        private val LOG_TAG = AppPermissionViewModel::class.java.simpleName
    }

    enum class UnusedPeriod(val duration: Duration) {
        ONE_MONTH(30.days),
        THREE_MONTHS(90.days),
        SIX_MONTHS(180.days);

        val months: Int = (duration.inWholeDays / 30).toInt()

        fun isNewlyUnused(): Boolean {
            return (this == ONE_MONTH) || (this == THREE_MONTHS)
        }

        companion object {

            val allPeriods: List<UnusedPeriod> = values().toList()

            // Find the longest period shorter than unused time
            fun findLongestValidPeriod(durationInMs: Long): UnusedPeriod {
                val duration = durationInMs.milliseconds
                return UnusedPeriod.allPeriods.findLast { duration > it.duration }
                    ?: UnusedPeriod.allPeriods.first()
            }
        }
    }

    data class UnusedPackageInfo(
        val packageName: String,
        val user: UserHandle,
        val isSystemApp: Boolean,
        val revokedGroups: Set<String>,
    )

    private data class PackageLastUsageTime(val packageName: String, val usageTime: Long)

    val unusedPackageCategoriesLiveData =
        object :
            SmartAsyncMediatorLiveData<Map<UnusedPeriod, List<UnusedPackageInfo>>>(
                alwaysUpdateOnActive = false
            ) {
            // Get apps usage stats from the longest interesting period (MAX_UNUSED_PERIOD_MILLIS)
            private val usageStatsLiveData = UsageStatsLiveData[MAX_UNUSED_PERIOD_MILLIS]

            init {
                addSource(getUnusedPackages()) { onUpdate() }

                addSource(AllPackageInfosLiveData) { onUpdate() }

                addSource(usageStatsLiveData) { onUpdate() }
            }

            override suspend fun loadDataAndPostValue(job: Job) {
                if (
                    !getUnusedPackages().isInitialized ||
                        !usageStatsLiveData.isInitialized ||
                        !AllPackageInfosLiveData.isInitialized
                ) {
                    return
                }

                val unusedApps = getUnusedPackages().value!!
                Log.i(LOG_TAG, "Unused apps: $unusedApps")
                val categorizedApps = mutableMapOf<UnusedPeriod, MutableList<UnusedPackageInfo>>()
                for (period in UnusedPeriod.allPeriods) {
                    categorizedApps[period] = mutableListOf()
                }

                // Get all packages which cannot be uninstalled.
                val systemApps = getUnusedSystemApps(AllPackageInfosLiveData.value!!, unusedApps)
                val lastUsedDataUnusedApps =
                    extractUnusedAppsUsageData(usageStatsLiveData.value!!, unusedApps) {
                        it: UsageStats ->
                        PackageLastUsageTime(it.packageName, it.lastTimePackageUsed())
                    }
                val firstInstallDataUnusedApps =
                    extractUnusedAppsUsageData(AllPackageInfosLiveData.value!!, unusedApps) {
                        it: LightPackageInfo ->
                        PackageLastUsageTime(it.packageName, it.firstInstallTime)
                    }

                val now = System.currentTimeMillis()
                unusedApps.keys.forEach { (packageName, user) ->
                    val userPackage = packageName to user

                    // If we didn't find the stat for a package in our usageStats search, it is more
                    // than
                    // 6 months old, or the app has never been opened. Then use first install date
                    // instead.
                    var lastUsageTime =
                        lastUsedDataUnusedApps[userPackage]
                            ?: firstInstallDataUnusedApps[userPackage] ?: 0L

                    val period = UnusedPeriod.findLongestValidPeriod(now - lastUsageTime)
                    categorizedApps[period]!!.add(
                        UnusedPackageInfo(
                            packageName,
                            user,
                            systemApps.contains(userPackage),
                            unusedApps[userPackage]!!
                        )
                    )
                }

                postValue(categorizedApps)
            }
        }

    // Extract UserPackage information for unused system apps from source map.
    private fun getUnusedSystemApps(
        userPackages: Map<UserHandle, List<LightPackageInfo>>,
        unusedApps: Map<UserPackage, Set<String>>,
    ): List<UserPackage> {
        return userPackages
            .flatMap { (userHandle, packageList) ->
                packageList
                    .filter { (it.appFlags and ApplicationInfo.FLAG_SYSTEM) != 0 }
                    .map { it.packageName to userHandle }
            }
            .filter { unusedApps.contains(it) }
    }

    /**
     * Extract PackageLastUsageTime for unused apps from userPackages map. This method may be used
     * for extracting different usage time (such as installation time or last opened time) from
     * different Package structures
     */
    private fun <PackageData> extractUnusedAppsUsageData(
        userPackages: Map<UserHandle, List<PackageData>>,
        unusedApps: Map<UserPackage, Set<String>>,
        extractUsageData: (fullData: PackageData) -> PackageLastUsageTime,
    ): Map<UserPackage, Long> {
        return userPackages
            .flatMap { (userHandle, fullData) ->
                fullData.map { userHandle to extractUsageData(it) }
            }
            .associate { (handle, appData) -> (appData.packageName to handle) to appData.usageTime }
            .filterKeys { unusedApps.contains(it) }
    }

    fun navigateToAppInfo(packageName: String, user: UserHandle, sessionId: Long) {
        val userContext = Utils.getUserContext(app, user)
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        intent.putExtra(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, sessionId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        userContext.startActivityAsUser(intent, user)
    }

    fun requestUninstallApp(fragment: Fragment, packageName: String, user: UserHandle) {
        Log.i(LOG_TAG, "sessionId: $sessionId, Requesting uninstall of $packageName, $user")
        logAppInteraction(packageName, user, AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE)
        val packageUri = Uri.parse("package:$packageName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        uninstallIntent.putExtra(Intent.EXTRA_USER, user)
        fragment.startActivity(uninstallIntent)
    }

    fun disableApp(packageName: String, user: UserHandle) {
        Log.i(LOG_TAG, "sessionId: $sessionId, Disabling $packageName, $user")
        logAppInteraction(packageName, user, AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE)
        val userContext = Utils.getUserContext(app, user)
        userContext.packageManager.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            0
        )
    }

    private fun logAppInteraction(packageName: String, user: UserHandle, action: Int) {
        GlobalScope.launch(IPC) {
            // If we are logging an app interaction, then the AllPackageInfosLiveData is not stale.
            val uid =
                AllPackageInfosLiveData.value
                    ?.get(user)
                    ?.find { info -> info.packageName == packageName }
                    ?.uid

            if (uid != null) {
                PermissionControllerStatsLog.write(
                    AUTO_REVOKED_APP_INTERACTION,
                    sessionId,
                    uid,
                    packageName,
                    action
                )
            }
        }
    }

    fun logAppView(packageName: String, user: UserHandle, groupName: String, isNew: Boolean) {
        GlobalScope.launch(IPC) {
            val uid =
                AllPackageInfosLiveData.value!![user]!!.find { info ->
                        info.packageName == packageName
                    }
                    ?.uid

            if (uid != null) {
                val bucket =
                    if (isNew) {
                        AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__NEWER_BUCKET
                    } else {
                        AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__OLDER_BUCKET
                    }
                PermissionControllerStatsLog.write(
                    AUTO_REVOKE_FRAGMENT_APP_VIEWED,
                    sessionId,
                    uid,
                    packageName,
                    groupName,
                    bucket
                )
            }
        }
    }
}

typealias UserPackage = Pair<String, UserHandle>

class UnusedAppsViewModelFactory(
    private val app: Application,
    private val sessionId: Long,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return UnusedAppsViewModel(app, sessionId) as T
    }
}
