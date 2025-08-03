/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher.datasources.restricted

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_RESOLVED_FILTER
import android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.text.TextUtils
import android.util.ArraySet

/**
 * Helper class for Restricted category of launcher apps.
 */
internal object RestrictedAppsUtils {

    /**
     * @param contentResolver required to retrieve secure strings from Settings.
     * @param secureKey key used to store the list of packages.
     * @param separator separator used for packages in the stored string.
     *
     * @return Set of packages stored in [Settings.Secure] with key [secureKey]
     */
    fun getRestrictedPackages(
        contentResolver: ContentResolver,
        secureKey: String,
        separator: String
    ): Set<String> {
        val settingsValue = Settings.Secure.getString(
            contentResolver,
            secureKey
        )

        return if (TextUtils.isEmpty(settingsValue)) {
            ArraySet()
        } else {
            ArraySet(
            listOf(
                *settingsValue.split(
                    separator.toRegex()
                ).dropLastWhile { it.isEmpty() }.toTypedArray()
            )
        )
        }
    }

    /**
     * @param packageManager required to queryIntentActivities category [Intent.CATEGORY_LAUNCHER].
     * @param contentResolver required to retrieve secure string from [Settings.Secure].
     * @param secureKey key used to store the list of packages.
     * @param separator separator used for packages in the stored string.
     *
     * @return List of ResolveInfo for restricted launcher activities filtered by packages found at
     *  [Settings.Secure] with key [secureKey].
     */
    fun getLauncherActivitiesForRestrictedApps(
        packageManager: PackageManager,
        contentResolver: ContentResolver,
        secureKey: String,
        separator: String
    ): List<ResolveInfo> {
        return packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(
                (GET_RESOLVED_FILTER or MATCH_DISABLED_UNTIL_USED_COMPONENTS).toLong()
            )
        ).filter {
            getRestrictedPackages(
                contentResolver,
                secureKey,
                separator
            ).contains(it.activityInfo.packageName)
        }
    }
}
