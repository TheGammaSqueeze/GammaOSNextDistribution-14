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

package com.android.wallpaper.picker.preview.data.util

import android.app.WallpaperInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(WallpaperInfo::class)
class ShadowWallpaperInfo {
    private lateinit var context: Context
    private lateinit var resolveInfo: ResolveInfo

    // CHECKSTYLE:OFF Generated code
    @Implementation
    fun __constructor__(context: Context, resolveInfo: ResolveInfo) {
        this.context = context
        this.resolveInfo = resolveInfo
    }

    @Implementation
    fun getPackageName(): String {
        return resolveInfo.serviceInfo.packageName
    }

    @Implementation
    fun getServiceInfo(): ServiceInfo {
        return resolveInfo.serviceInfo
    }

    @Implementation
    fun getComponent(): ComponentName {
        return ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name)
    }
}
