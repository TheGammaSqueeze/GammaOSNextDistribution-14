/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.healthconnect.controller.recentaccess

import android.health.connect.HealthConnectManager
import android.health.connect.accesslog.AccessLog
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.utils.TimeSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadRecentAccessUseCase
@Inject
constructor(
    private val manager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource
) : ILoadRecentAccessUseCase {

    companion object {
        private const val TAG = "LoadRecentAccessUseCase"
    }

    /** Returns a list of apps that have recently accessed Health Connect */
    override suspend fun invoke(): List<AccessLog> =
        withContext(dispatcher) {
            val accessLogs =
                try {
                    suspendCancellableCoroutine<List<AccessLog>> { continuation ->
                        manager.queryAccessLogs(Runnable::run, continuation.asOutcomeReceiver())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Load error ", e)
                    listOf()
                }

            val instant24Hours =
                Instant.ofEpochMilli(timeSource.currentTimeMillis()).minus(Duration.ofDays(1))

            // only need the last 24 hours of access logs
            accessLogs
                .filter { accessLog -> accessLog.accessTime.isAfter(instant24Hours) }
                .sortedByDescending { it.accessTime }
        }
}

interface ILoadRecentAccessUseCase {
    suspend fun invoke(): List<AccessLog>
}
