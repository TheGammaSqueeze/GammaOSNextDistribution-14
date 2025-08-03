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

package com.android.permissioncontroller.privacysources

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.android.safetycenter.resources.SafetyCenterResourcesApk

// (TODO:b/242573074) Remove for Android U.
class NotificationListenerPregrants(private val context: Context) {
    @VisibleForTesting
    val pregrantedPackagesDelegate = lazy {
        hashSetOf(
                "android",
                "com.android.cellbroadcastreceiver",
                "com.android.server.telecom",
                "com.android.settings",
                "com.android.systemui",
                "com.android.launcher3",
                "com.android.dynsystem",
                "com.android.providers.settings",
                "com.android.inputdevices",
                "com.android.keychain",
                "com.android.localtransport",
                "com.android.wallpaperbackup",
                "com.android.location.fused"
            )
            .also {
                it.addAll(
                    SafetyCenterResourcesApk(context)
                        .getStringByName("config_NotificationListenerServicePregrants")
                        .split(",")
                )
            }
    }
    val pregrantedPackages: Set<String> by pregrantedPackagesDelegate
}
