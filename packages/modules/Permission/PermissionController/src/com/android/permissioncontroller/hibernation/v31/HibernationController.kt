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

package com.android.permissioncontroller.hibernation.v31

import android.app.usage.UsageStatsManager
import android.apphibernation.AppHibernationManager
import android.content.Context
import android.content.Context.APP_HIBERNATION_SERVICE
import android.content.Context.USAGE_STATS_SERVICE
import android.os.Build
import android.os.UserHandle
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.HibernatedPackagesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo

/** Hibernation controller that handles modifying hibernation state. */
@RequiresApi(Build.VERSION_CODES.S)
class HibernationController(
    private val context: Context,
    private val unusedThreshold: Long,
    private val targetsPreS: Boolean
) {

    companion object {
        private const val LOG_TAG = "HibernationController"
        private const val DEBUG_HIBERNATION = true
    }

    /**
     * Hibernates the apps provided for each user.
     *
     * @param apps map of each user to a list of packages that should be hibernated for the user
     * @return list of apps that were successfully hibernated
     */
    fun hibernateApps(
        apps: Map<UserHandle, List<LightPackageInfo>>
    ): Set<Pair<String, UserHandle>> {
        val hibernatedApps = mutableSetOf<Pair<String, UserHandle>>()
        for ((user, userApps) in apps) {
            val userContext = context.createContextAsUser(user, 0 /* flags */)
            val hibernationManager =
                userContext.getSystemService(APP_HIBERNATION_SERVICE) as AppHibernationManager
            for (pkg in userApps) {
                try {
                    if (hibernationManager.isHibernatingForUser(pkg.packageName)) {
                        continue
                    }
                    if (!targetsPreS && pkg.targetSdkVersion < Build.VERSION_CODES.S) {
                        // Only apps targeting S or above can be truly hibernated.
                        continue
                    }
                    hibernationManager.setHibernatingForUser(pkg.packageName, true)
                    hibernatedApps.add(pkg.packageName to user)
                } catch (e: Exception) {
                    DumpableLog.e(LOG_TAG, "Failed to hibernate package: ${pkg.packageName}", e)
                }
            }
        }

        // Globally hibernate any of the hibernated apps that are unused by any user
        val usageStatsManager = context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val hibernationManager =
            context.getSystemService(APP_HIBERNATION_SERVICE) as AppHibernationManager
        val globallyHibernatedApps = mutableSetOf<String>()
        for ((pkgName, _) in hibernatedApps) {
            if (
                globallyHibernatedApps.contains(pkgName) ||
                    hibernationManager.isHibernatingGlobally(pkgName)
            ) {
                continue
            }

            val now = System.currentTimeMillis()
            val lastUsedGlobally = usageStatsManager.getLastTimeAnyComponentUsed(pkgName)
            if (now - lastUsedGlobally < unusedThreshold) {
                continue
            }

            hibernationManager.setHibernatingGlobally(pkgName, true)
            globallyHibernatedApps.add(pkgName)
        }
        if (hibernatedApps.isNotEmpty()) {
            HibernatedPackagesLiveData.update()
        }
        if (DEBUG_HIBERNATION) {
            DumpableLog.i(
                LOG_TAG,
                "Done hibernating apps $hibernatedApps \n " +
                    "Globally hibernating apps $globallyHibernatedApps"
            )
        }

        return hibernatedApps
    }
}
