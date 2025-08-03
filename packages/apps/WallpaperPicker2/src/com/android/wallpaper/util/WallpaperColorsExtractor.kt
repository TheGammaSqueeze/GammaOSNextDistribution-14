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
package com.android.wallpaper.util

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Handler
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wallpaper color extractor. Instantiate it with a proper handler. We usually use the main thread
 * handler, so we can change UI accordingly when colors are extracted.
 */
class WallpaperColorsExtractor(
    private val mExecutor: Executor,
    private val mResultHandler: Handler
) {
    private val mCurrentTaskId = AtomicInteger(0)

    /**
     * Extracts wallpaper colors. Noticed that when there are consecutive calls, only the results
     * from the latest call will be posted. This is done by an incremental [mCurrentTaskId] to
     * identify if a task id is still the latest, right before posting the results.
     */
    fun extractWallpaperColors(
        wallpaperBitmap: Bitmap,
        onColorsExtractedListener: OnColorsExtractedListener
    ) {
        mExecutor.execute {
            val taskId = mCurrentTaskId.incrementAndGet()

            val tmpOut = ByteArrayOutputStream()
            var shouldRecycle = false
            var cropped = wallpaperBitmap
            if (cropped.compress(Bitmap.CompressFormat.PNG, 100, tmpOut)) {
                val outByteArray = tmpOut.toByteArray()
                val options = BitmapFactory.Options()
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                cropped = BitmapFactory.decodeByteArray(outByteArray, 0, outByteArray.size)
            }
            if (cropped.config == Bitmap.Config.HARDWARE) {
                cropped = cropped.copy(Bitmap.Config.ARGB_8888, false)
                shouldRecycle = true
            }
            val colors = WallpaperColors.fromBitmap(cropped)
            if (shouldRecycle) {
                cropped.recycle()
            }
            // This makes sure that the listener only listen to the latest results, when multiple
            // extractWallpaperColors tasks are executed.
            if (taskId == mCurrentTaskId.get()) {
                mResultHandler.post { onColorsExtractedListener.onColorsExtracted(colors) }
            }
        }
    }
}
