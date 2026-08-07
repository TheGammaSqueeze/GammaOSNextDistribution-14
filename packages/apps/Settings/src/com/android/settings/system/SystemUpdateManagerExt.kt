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

package com.android.settings.system

import android.content.Context
import android.os.Bundle
import android.os.SystemUpdateManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SystemUpdateManagerExt"

/**
 * Gets the system update status.
 *
 * Note: [SystemUpdateManager.retrieveSystemUpdateInfo] must be called on worker thread to avoid
 * StrictMode violation.
 */
suspend fun Context.getSystemUpdateInfo(): Bundle? = withContext(Dispatchers.Default) {
    // GammaOS Nano: the system_update service is not published in minimal_boot, so
    // getSystemService returns null. Return null gracefully (callers already handle a null
    // Bundle) instead of the "!!" not-null assertion, which would throw an NPE inside this
    // coroutine and crash the settings process (off the main-thread controller-loop guard).
    val updateManager = getSystemService(SystemUpdateManager::class.java)
            ?: return@withContext null
    try {
        updateManager.retrieveSystemUpdateInfo()
    } catch (e: Exception) {
        Log.w(TAG, "Error getting system update info.")
        null
    }
}
