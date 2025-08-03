/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.v2.listcontroller

import android.app.AppGlobals
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.RemoteException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Class that stores and retrieves the most recently chosen resolutions. */
interface LastChosenManager {

    /** Returns the most recently chosen resolution. */
    suspend fun getLastChosen(): ResolveInfo

    /** Sets the most recently chosen resolution. */
    suspend fun setLastChosen(intent: Intent, filter: IntentFilter, match: Int)
}

/**
 * Stores and retrieves the most recently chosen resolutions using the [PackageManager] provided by
 * the [packageManagerProvider].
 */
class PackageManagerLastChosenManager(
    private val contentResolver: ContentResolver,
    private val bgDispatcher: CoroutineDispatcher,
    private val targetIntent: Intent,
    private val packageManagerProvider: () -> IPackageManager = AppGlobals::getPackageManager,
) : LastChosenManager {

    @Throws(RemoteException::class)
    override suspend fun getLastChosen(): ResolveInfo {
        return withContext(bgDispatcher) {
            packageManagerProvider()
                .getLastChosenActivity(
                    targetIntent,
                    targetIntent.resolveTypeIfNeeded(contentResolver),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
        }
    }

    @Throws(RemoteException::class)
    override suspend fun setLastChosen(intent: Intent, filter: IntentFilter, match: Int) {
        return withContext(bgDispatcher) {
            packageManagerProvider()
                .setLastChosenActivity(
                    intent,
                    intent.resolveType(contentResolver),
                    PackageManager.MATCH_DEFAULT_ONLY,
                    filter,
                    match,
                    intent.component,
                )
        }
    }
}
