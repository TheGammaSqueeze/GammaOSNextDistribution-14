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

package com.android.permissioncontroller.permission.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.annotation.VisibleForTesting
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.PermissionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * [BroadcastReceiver] to clear user decision information when a package has its data cleared or is
 * fully removed.
 */
class PersistedStoragePackageUninstalledReceiver(
    @VisibleForTesting
    private val storages: List<PermissionEventStorage<out PermissionEvent>> =
        PermissionEventStorageImpls.getInstance(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BroadcastReceiver() {

    companion object {
        private const val LOG_TAG = "PersistedStoragePackageUninstalledReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (storages.isEmpty()) {
            return
        }
        val action = intent.action
        if (
            !(action == Intent.ACTION_PACKAGE_DATA_CLEARED ||
                action == Intent.ACTION_PACKAGE_FULLY_REMOVED)
        ) {
            return
        }
        intent.data?.let {
            val packageName = it.schemeSpecificPart
            val userId = Process.myUserHandle().identifier
            DumpableLog.d(LOG_TAG, "Received $action for $packageName for u$userId")

            GlobalScope.launch(dispatcher) {
                for (storage in storages) {
                    storage.removeEventsForPackage(packageName)
                }
            }
        }
    }
}
