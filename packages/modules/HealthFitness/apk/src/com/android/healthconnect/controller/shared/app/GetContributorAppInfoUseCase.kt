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
 *
 *
 */

/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.shared.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.health.connect.ApplicationInfoResponse
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.AppInfo
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class GetContributorAppInfoUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : IGetContributorAppInfoUseCase {
    companion object {
        private const val TAG = "GetContributorAppInfo"
    }

    override suspend fun invoke(): Map<String, AppMetadata> =
        withContext(dispatcher) {
            try {
                val appInfoList =
                    suspendCancellableCoroutine<ApplicationInfoResponse> { continuation ->
                            healthConnectManager.getContributorApplicationsInfo(
                                Runnable::run, continuation.asOutcomeReceiver())
                        }
                        .applicationInfoList
                appInfoList.associate { it.packageName to toAppMetadata(it) }
            } catch (e: Exception) {
                Log.e(TAG, "GetContributorApplicationsInfoUseCase", e)
                emptyMap()
            }
        }

    private fun toAppMetadata(appInfo: AppInfo): AppMetadata {
        return AppMetadata(
            packageName = appInfo.packageName,
            appName =
                appInfo.name
                    ?: appInfo.packageName, // default to package name if appInfo name is null
            icon = getIcon(appInfo.icon))
    }

    private fun getIcon(bitmap: Bitmap?): Drawable? {
        return bitmap?.let { BitmapDrawable(context.resources, it) }
    }
}

interface IGetContributorAppInfoUseCase {
    suspend operator fun invoke(): Map<String, AppMetadata>
}
