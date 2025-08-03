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

package com.android.car.carlauncher.datasources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface LauncherActivitiesDataSource {

    /**
     * Gets all the Launchable activities for the user.
     */
    suspend fun getAllLauncherActivities(): List<LauncherActivityInfo>

    /**
     * Flow notifying changes if packages are changed.
     */
    fun getOnPackagesChanged(): Flow<String>

    companion object {
        val TAG: String = LauncherActivitiesDataSource::class.java.simpleName
    }
}

/**
 * Impl of [LauncherActivitiesDataSource] to surface all the launcher activities apis.
 * All the operations in this class are non blocking.
 *
 * @property [launcherApps] Used to fetch launcher activities.
 * @property [registerReceiverFunction] Function to register the broadcast receiver.
 * Should be provided by the Android Component owning the [Context]
 * @property [unregisterReceiverFunction] Function to unregister the broadcast receiver.
 * Should be provided by the Android Component owning the [Context]
 * @property [userHandle] Specified user's handle to fetch launcher activities.
 * @property [bgDispatcher] Executes all the operations on this background coroutine dispatcher.
 */
class LauncherActivitiesDataSourceImpl(
    private val launcherApps: LauncherApps,
    private val registerReceiverFunction: (BroadcastReceiver, IntentFilter) -> Unit,
    private val unregisterReceiverFunction: (BroadcastReceiver) -> Unit,
    private val userHandle: UserHandle,
    private val bgDispatcher: CoroutineDispatcher = Dispatchers.Default
) : LauncherActivitiesDataSource {

    /**
     * Gets all launcherActivities for a user with [userHandle]
     */
    override suspend fun getAllLauncherActivities(): List<LauncherActivityInfo> {
        return withContext(bgDispatcher) {
            launcherApps.getActivityList(
                /* packageName = */
                null,
                userHandle
            )
        }
    }

    /**
     * Gets a flow Producer which report changes in the packages with following actions:
     * [Intent.ACTION_PACKAGE_ADDED], [Intent.ACTION_PACKAGE_CHANGED],
     * [Intent.ACTION_PACKAGE_REPLACED] or [Intent.ACTION_PACKAGE_REMOVED].
     *
     * Note: The producer sends an `Empty String` initially. This immediately tells the collector
     * that there are no changes as of now with packages.
     *
     * When the scope in which this flow is collected is closed/canceled
     * [unregisterReceiverFunction] is triggered.
     */
    override fun getOnPackagesChanged(): Flow<String> {
        return callbackFlow {
            trySend("")
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_PACKAGE_ADDED)
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED)
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
            filter.addDataScheme("package")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val packageName = intent?.data?.schemeSpecificPart
                    if (packageName.isNullOrBlank()) {
                        return
                    }
                    trySend(packageName)
                }
            }
            registerReceiverFunction(receiver, filter)
            awaitClose {
                unregisterReceiverFunction(receiver)
            }
        }.flowOn(bgDispatcher).conflate()
    }
}
