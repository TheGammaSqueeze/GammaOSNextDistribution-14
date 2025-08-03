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

package com.google.android.wallpaper.weathereffects.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperFileUtils {
    /**
     * Exports the [bitmap] to an image file in local storage.
     * This method may take several seconds to complete, so it should be called from
     * a background [dispatcher].
     *
     * @param context the [Context] of the caller
     * @param bitmap the source to be exported
     * @param dispatcher the dispatcher to run within.
     * @return `true` when exported successfully
     */
    suspend fun export(
        context: Context,
        fileName: String,
        bitmap: Bitmap,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean {
        val protectedContext = asProtectedContext(context)
        return try {
            withContext(dispatcher) {
                var success: Boolean
                protectedContext
                    .openFileOutput(fileName, Context.MODE_PRIVATE)
                    .use {
                        success = bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            /* quality = */ 100,
                            it,
                        )
                        if (!success) {
                            Log.e(TAG, "Failed to write the bitmap to local storage")
                        } else {
                            Log.i(TAG, "Wrote bitmap to local storage. filename: $fileName")
                        }
                    }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export", e)
            false
        }
    }

    /**
     * Imports the bitmap from an absolute path. This method may take several seconds to complete,
     * so it should be called from a background [dispatcher].
     *
     * @param absolutePath the absolute file path of the bitmap to be imported.
     * @param dispatcher the dispatcher to run within.
     * @return the imported wallpaper bitmap, or `null` if importing failed.
     */
    suspend fun importBitmapFromAbsolutePath(
        absolutePath: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Bitmap? {
        return try {
            withContext(dispatcher) {
                val bitmap = BitmapFactory.decodeFile(absolutePath)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode the bitmap")
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import the image", e)
            null
        }
    }

    /**
     * Imports the bitmap from local storage. This method may take several seconds to complete,
     * so it should be called from a background [dispatcher].
     *
     * @param fileName name of the bitmap file in local storage.
     * @param dispatcher the dispatcher to run within.
     * @return the imported wallpaper bitmap, or `null` if importing failed.
     */
    suspend fun importBitmapFromLocalStorage(
        fileName: String,
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Bitmap? {
        return try {
            withContext(dispatcher) {
                val protectedContext = asProtectedContext(context)
                val inputStream = protectedContext.openFileInput(fileName)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode the bitmap")
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import the image", e)
            null
        }
    }

    private fun asProtectedContext(context: Context): Context {
        return if (context.isDeviceProtectedStorage) {
            context
        } else {
            context.createDeviceProtectedStorageContext()
        }
    }

    private const val TAG = "WallpaperFileUtils"
    const val FG_FILE_NAME = "fg_image"
    const val BG_FILE_NAME = "bg_image"
}
