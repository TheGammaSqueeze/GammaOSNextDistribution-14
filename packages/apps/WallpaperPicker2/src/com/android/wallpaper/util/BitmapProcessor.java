/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.wallpaper.util;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

/**
 * Class with different bitmap processors to apply to bitmaps
 */
public final class BitmapProcessor {

    private static final String TAG = "BitmapProcessor";
    private static final int DOWNSAMPLE = 5;

    private BitmapProcessor() {
    }

    /**
     * Function that transforms a bitmap into a lower resolution.
     *
     * @param bitmap    the bitmap we want to blur.
     * @param outWidth  the end width of the blurred bitmap.
     * @param outHeight the end height of the blurred bitmap.
     * @return the blurred bitmap.
     */
    public static Bitmap createLowResBitmap(Bitmap bitmap, int outWidth, int outHeight) {
        try {
            Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            WallpaperCropUtils.fitToSize(rect, outWidth / DOWNSAMPLE, outHeight / DOWNSAMPLE);

            return Bitmap.createScaledBitmap(bitmap, rect.width(), rect.height(),
                    true /* filter */);
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "error while blurring bitmap", ex);
        }

        return null;
    }
}
