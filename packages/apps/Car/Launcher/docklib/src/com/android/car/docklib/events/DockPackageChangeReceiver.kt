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

package com.android.car.docklib.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.os.Build
import android.util.Log
import com.android.car.docklib.DockInterface

class DockPackageChangeReceiver(
    private val dockController: DockInterface
) : BroadcastReceiver() {
    companion object {
        private val DEBUG = Build.isDebuggable()
        private const val TAG = "DockPackageChangeReceiver"

        /**
         * Helper method to register [DockPackageChangeReceiver] through context and listen to
         * changes to packages in the system.
         *
         * @param context the context through which the [DockPackageChangeReceiver] is registered
         * @return successfully registered [DockPackageChangeReceiver].
         */
        fun registerReceiver(
            context: Context,
            dockController: DockInterface
        ): DockPackageChangeReceiver {
            val receiver = DockPackageChangeReceiver(dockController)
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_PACKAGE_ADDED)
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
            filter.addDataScheme("package")
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            if (DEBUG) {
                Log.d(
                    TAG,
                    "DockPackageChangeReceiver registered from package: " +
                            "${context.packageName}, for user ${context.userId}"
                )
            }
            return receiver
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.data?.schemeSpecificPart?.let { packageName ->
            if (DEBUG) Log.d(TAG, "package name: $packageName")
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (intent.getBooleanExtra(
                            Intent.EXTRA_REPLACING,
                            false // defaultValue
                        )
                    ) {
                        return
                    }
                    if (DEBUG) Log.d(TAG, "ACTION_PACKAGE_ADDED")
                    dockController.packageAdded(packageName)
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (intent.getBooleanExtra(
                            Intent.EXTRA_REPLACING,
                            false // defaultValue
                        )
                    ) {
                        return
                    }
                    if (DEBUG) Log.d(TAG, "ACTION_PACKAGE_REMOVED")
                    dockController.packageRemoved(packageName)
                }

                Intent.ACTION_PACKAGE_CHANGED -> {
                    if (DEBUG) Log.d(TAG, "ACTION_PACKAGE_CHANGED")
                    when (context?.packageManager?.getApplicationEnabledSetting(packageName)) {
                        COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DISABLED_USER -> {
                            if (DEBUG) Log.d(TAG, "package disabled")
                            dockController.packageRemoved(packageName)
                        }
                    }
                }
            }
        }
    }
}
