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

package com.android.car.docklib.media

import android.app.ActivityManager
import android.car.media.CarMediaIntents
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.stream.Collectors

class MediaUtils {
    companion object {
        private const val TAG = "MediaUtils"
        private val DEBUG = Build.isDebuggable()

        @VisibleForTesting
        val CAR_MEDIA_ACTIVITY = ComponentName(
            "com.android.car.media",
            "com.android.car.media.MediaActivity"
        )

        @VisibleForTesting
        const val CAR_MEDIA_DATA_SCHEME = "custom"

        fun getMediaComponentName(taskInfo: ActivityManager.RunningTaskInfo): ComponentName? {
            val data = taskInfo.baseIntent.data
            if (data == null) {
                if (DEBUG) Log.d(TAG, "No data attached to the base intent")
                return null
            }
            if (CAR_MEDIA_DATA_SCHEME != data.scheme) {
                if (DEBUG) Log.d(TAG, "Data scheme doesn't match")
                return null
            }
            // should drop the first backslash that is part of the schemeSpecificPart
            val ssp = data.schemeSpecificPart
            val mediaComponentString = if (ssp.startsWith("/")) ssp.drop(1) else ssp
            val mediaComponent = ComponentName.unflattenFromString(mediaComponentString)
            if (DEBUG) Log.d(TAG, "Media component found: $mediaComponent")
            return mediaComponent
        }

        fun isMediaComponent(component: ComponentName?) = component == CAR_MEDIA_ACTIVITY

        fun createLaunchIntent(componentName: ComponentName) =
            Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, componentName.flattenToString())

        fun fetchMediaServiceComponents(
            packageManager: PackageManager,
            packageName: String? = null
        ): MutableSet<ComponentName> {
            val intent = Intent(MediaBrowserService.SERVICE_INTERFACE)
            if (packageName != null) intent.setPackage(packageName)
            return packageManager.queryIntentServices(
                intent,
                PackageManager.GET_RESOLVED_FILTER
            ).stream()
                .map { resolveInfo: ResolveInfo -> resolveInfo.serviceInfo.componentName }
                .collect(Collectors.toSet())
        }
    }
}
