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

package com.android.bedstead.nene.wallpaper;

import android.app.WallpaperManager
import android.graphics.Bitmap
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.permissions.CommonPermissions.SET_WALLPAPER
import com.android.compatibility.common.util.BitmapUtils
import java.io.InputStream

/** Test APIs related to wallpaper. */
@Experimental
object Wallpaper {

    private val sContext = TestApis.context().instrumentedContext()
    private val wallpaperManager = sContext.getSystemService(WallpaperManager::class.java)!!

    /**
     * Get the {@code Bitmap} value of the current wallpaper.
     */
    fun getBitmap(): Bitmap? {
        try {
            return BitmapUtils.getWallpaperBitmap(sContext)
        } catch (e: Exception) {
            throw NeneException("Unable to get current wallpaper", e);
        }
    }

    /**
     * See {@link WallpaperManager#setBitmap(Bitmap)}.
     */
    fun setBitmap(bitmap: Bitmap) {
        TestApis.permissions().withPermission(SET_WALLPAPER).use {
            try {
                wallpaperManager.setBitmap(bitmap)
            } catch (e: Exception) {
                throw NeneException("Unable to set wallpaper to the provided bitmap", e);
            }
        }
    }

    /**
     * See {@link WallpaperManager#setStream(InputStream)}.
     */
    fun setStream(inputStream: InputStream) {
        TestApis.permissions().withPermission(SET_WALLPAPER).use {
            try {
                wallpaperManager.setStream(inputStream);
            } catch (e: Exception) {
                throw NeneException("Unable to set wallpaper to the provided stream", e);
            }
        }
    }

}
