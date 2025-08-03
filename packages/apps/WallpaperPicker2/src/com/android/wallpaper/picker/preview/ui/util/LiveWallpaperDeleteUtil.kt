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
package com.android.wallpaper.picker.preview.ui.util

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.text.TextUtils
import com.android.wallpaper.picker.LivePreviewFragment
import javax.inject.Inject
import javax.inject.Singleton

/** The utility class for live wallpaper that can be deleted */
@Singleton
class LiveWallpaperDeleteUtil @Inject constructor(private val wallpaperManager: WallpaperManager) {

    fun getDeleteActionIntent(wallpaperInfo: WallpaperInfo): Intent? {
        val deleteAction = getDeleteAction(wallpaperInfo)
        if (TextUtils.isEmpty(deleteAction)) {
            return null
        }
        val deleteActionIntent = Intent(deleteAction)
        deleteActionIntent.setPackage(wallpaperInfo.packageName)
        deleteActionIntent.putExtra(LivePreviewFragment.EXTRA_LIVE_WALLPAPER_INFO, wallpaperInfo)
        return deleteActionIntent
    }

    private fun getDeleteAction(wallpaperInfo: WallpaperInfo): String? {
        val currentInfo = wallpaperManager.getWallpaperInfo(FLAG_SYSTEM)
        val currentLockInfo = wallpaperManager.getWallpaperInfo(FLAG_LOCK)
        val serviceInfo = wallpaperInfo.serviceInfo
        val appInfo = serviceInfo.applicationInfo
        val isPackagePreInstalled =
            appInfo != null && appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        if (!isPackagePreInstalled) {
            // This wallpaper is not installed before
            return null
        }

        // A currently set Live wallpaper should not be deleted.
        val currentService = currentInfo?.serviceInfo
        if (currentService != null && TextUtils.equals(serviceInfo.name, currentService.name)) {
            return null
        }
        val currentLockService = currentLockInfo?.serviceInfo
        if (
            currentLockService != null &&
                TextUtils.equals(serviceInfo.name, currentLockService.name)
        ) {
            return null
        }
        val metaData = serviceInfo.metaData
        return metaData?.getString(LivePreviewFragment.KEY_ACTION_DELETE_LIVE_WALLPAPER)
    }
}
