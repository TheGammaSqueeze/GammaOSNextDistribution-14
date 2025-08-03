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

import android.car.settings.CarSettings
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.android.car.carlauncher.datasources.restricted.RestrictedAppsUtils.getLauncherActivitiesForRestrictedApps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * DataSource exposes a flow which tracks list of disabled apps.
 */
interface DisabledAppsDataSource {
    fun getDisabledApps(): Flow<List<ResolveInfo>>
}

/**
 * Impl of [DisabledAppsDataSource], to surface all DisabledApps apis.
 *
 * All the operations in this class are non blocking.
 */
class DisabledAppsDataSourceImpl(
    private val contentResolver: ContentResolver,
    private val packageManager: PackageManager,
    private val bgDispatcher: CoroutineDispatcher
) : DisabledAppsDataSource {

    /**
     * Gets a Flow producer which gets the current list of disabled apps installed for this user
     * and found at [Settings.Secure.getString]
     * for Key [CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE].
     *
     * The Flow also pushes new list if there is any updates in the list of disabled apps.
     */
    override fun getDisabledApps(): Flow<List<ResolveInfo>> {
        return callbackFlow {
            trySend(
                getLauncherActivitiesForRestrictedApps(
                    packageManager,
                    contentResolver,
                    CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                    PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR
                )
            )
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }

            val looper: Looper =
                Looper.myLooper().takeIf { it != null } ?: Looper.getMainLooper().also {
                    Log.d(TAG, "Current thread looper is null, fallback to MainLooper")
                }

            val disabledAppsObserver = object : ContentObserver(Handler(looper)) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    trySend(
                        getLauncherActivitiesForRestrictedApps(
                            packageManager,
                            contentResolver,
                            CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                            PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR
                        )
                    )
                }
            }

            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(
                    CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE
                ),
                false,
                disabledAppsObserver
            )

            awaitClose {
                // If MainLooper do not quit it. MainLooper always stays alive.
                if (looper != Looper.getMainLooper()) {
                    looper.quitSafely()
                }
                contentResolver.unregisterContentObserver(disabledAppsObserver)
            }
        }.flowOn(bgDispatcher).conflate()
    }

    companion object {
        val TAG: String = DisabledAppsDataSourceImpl::class.java.simpleName
        const val PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";"
    }
}
