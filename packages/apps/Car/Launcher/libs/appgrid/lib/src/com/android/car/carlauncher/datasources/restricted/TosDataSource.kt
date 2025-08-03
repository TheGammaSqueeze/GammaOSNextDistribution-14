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
import android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS
import android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * DataSource exposes a flow which tracks list of tos(Terms of Service) disabled apps.
 */
interface TosDataSource {
    fun getTosState(): Flow<TosState>
}

data class TosState(
    val shouldBlockTosApps: Boolean,
    val restrictedApps: List<ResolveInfo> = emptyList()
)

/**
 * Impl of [TosDataSource], to surface all DisabledApps apis.
 *
 * All the operations in this class are non blocking.
 */
class TosDataSourceImpl(
    private val contentResolver: ContentResolver,
    private val packageManager: PackageManager,
    private val bgDispatcher: CoroutineDispatcher,
) : TosDataSource {

    /**
     * Gets a Flow producer which gets if TOS is accepted by the user and
     * the current list of tos disabled apps installed for this user and found at
     * [Settings.Secure.getString] for Key [CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS]
     * __only if__ [TosState.hasAccepted] is false which is reflected by
     * [CarSettings.Secure.KEY_USER_TOS_ACCEPTED]
     *
     * The Flow also pushes new list if there is any updates in the list of disabled apps.
     * @return Flow of [TosState]
     */
    override fun getTosState(): Flow<TosState> {
        if (isTosAccepted()) {
            // Tos is accepted, so we do not need to block the apps.
            // We can assume the list of blocked apps as empty in this case.
            return flowOf(
                TosState(
                    false,
                    emptyList()
                )
            )
        }

        return callbackFlow {
            trySend(
                TosState(
                    shouldBlockTosApps(),
                    getLauncherActivitiesForRestrictedApps(
                        packageManager,
                        contentResolver,
                        KEY_UNACCEPTED_TOS_DISABLED_APPS,
                        TOS_DISABLED_APPS_SEPARATOR
                    )
                )
            )
            if (Looper.myLooper() == null) {
                Looper.prepare()
            }

            val looper: Looper =
                Looper.myLooper().takeIf { it != null } ?: Looper.getMainLooper().also {
                    Log.d(TAG, "Current thread looper is null, fallback to MainLooper")
                }
            // Tos Accepted Observer
            val tosStateObserver = object : ContentObserver(Handler(looper)) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val isAccepted = isTosAccepted()
                    val restrictedApps: List<ResolveInfo> = if (isAccepted) {
                        // We don't need to observe the changes once TOS is accepted
                        contentResolver.unregisterContentObserver(this)
                        // if TOS is accepted, we can assume that TOS disabled apps will be empty
                        emptyList()
                    } else {
                        getLauncherActivitiesForRestrictedApps(
                            packageManager,
                            contentResolver,
                            KEY_UNACCEPTED_TOS_DISABLED_APPS,
                            TOS_DISABLED_APPS_SEPARATOR
                        )
                    }
                    trySend(TosState(shouldBlockTosApps(), restrictedApps))
                }
            }

            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(KEY_UNACCEPTED_TOS_DISABLED_APPS),
                false,
                tosStateObserver
            )

            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(KEY_USER_TOS_ACCEPTED),
                false,
                tosStateObserver
            )

            awaitClose {
                // If MainLooper do not quit it. MainLooper always stays alive.
                if (looper != Looper.getMainLooper()) {
                    looper.quitSafely()
                }
                contentResolver.unregisterContentObserver(tosStateObserver)
            }
        }.flowOn(bgDispatcher).conflate()
    }

    /**
     * Check if a user has accepted TOS
     * @return true if the user has accepted Tos, false otherwise
     */
    private fun isTosAccepted(): Boolean {
        val settingsValue = Settings.Secure.getString(
            contentResolver,
            KEY_USER_TOS_ACCEPTED
        )
        return settingsValue == TOS_ACCEPTED
    }

    /**
     * Only block the apps to the user when the TOS state is [TOS_NOT_ACCEPTED]
     *
     * Note: Even when TOS state is [TOS_UNINITIALIZED] we do not want to block tos apps.
     */
    private fun shouldBlockTosApps(): Boolean {
        val settingsValue = Settings.Secure.getString(
            contentResolver,
            KEY_USER_TOS_ACCEPTED
        )
        return settingsValue == TOS_NOT_ACCEPTED
    }

    companion object {
        // This value indicates if TOS is in uninitialized state
        const val TOS_UNINITIALIZED = "0"

        // This value indicates if TOS has not been accepted by the user
        const val TOS_NOT_ACCEPTED = "1"

        // This value indicates if TOS has been accepted by the user
        const val TOS_ACCEPTED = "2"
        const val TOS_DISABLED_APPS_SEPARATOR = ","

        private val TAG = TosDataSourceImpl::class.java.simpleName
    }
}
