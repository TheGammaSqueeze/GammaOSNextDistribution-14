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
package com.android.wallpaper.util

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.text.TextUtils
import android.util.Log
import com.android.wallpaper.picker.LivePreviewFragment

/** The utility class for live wallpaper the can be deleted */
object DeletableUtils {
    private const val TAG = "DeletableUtils"

    /** If a wallpaper can be deleted. */
    @JvmStatic
    fun canBeDeleted(context: Context, wallpaperInfo: WallpaperInfo): Boolean {
        return !TextUtils.isEmpty(getDeleteAction(context, wallpaperInfo))
    }

    /** Delete a wallpaper. */
    @JvmStatic
    fun deleteLiveWallpaper(
        context: Context,
        wallpaperInfo: com.android.wallpaper.model.WallpaperInfo
    ) {
        val deleteIntent = getDeleteActionIntent(context, wallpaperInfo.wallpaperComponent)
        if (deleteIntent != null) {
            context.startService(deleteIntent)
        }
    }

    private fun getDeleteActionIntent(context: Context, wallpaperInfo: WallpaperInfo): Intent? {
        val deleteAction = getDeleteAction(context, wallpaperInfo)
        if (TextUtils.isEmpty(deleteAction)) {
            return null
        }
        val deleteActionIntent = Intent(deleteAction)
        deleteActionIntent.setPackage(wallpaperInfo.packageName)
        deleteActionIntent.putExtra(LivePreviewFragment.EXTRA_LIVE_WALLPAPER_INFO, wallpaperInfo)
        return deleteActionIntent
    }

    private fun getDeleteAction(context: Context, wallpaperInfo: WallpaperInfo): String? {
        val currentInfo = WallpaperManager.getInstance(context).getWallpaperInfo(FLAG_SYSTEM)
        val currentLockInfo = WallpaperManager.getInstance(context).getWallpaperInfo(FLAG_LOCK)
        val serviceInfo = wallpaperInfo.serviceInfo
        val appInfo = serviceInfo.applicationInfo
        val isPackagePreInstalled =
            appInfo != null && appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        // This wallpaper is not installed before
        if (!isPackagePreInstalled) {
            Log.d(TAG, "This wallpaper is not pre-installed: " + serviceInfo.name)
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
