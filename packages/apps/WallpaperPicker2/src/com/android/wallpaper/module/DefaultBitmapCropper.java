/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.BitmapReceiver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default implementation of BitmapCropper, which actually crops and scales bitmaps.
 */
public class DefaultBitmapCropper implements BitmapCropper {
    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();
    private static final String TAG = "DefaultBitmapCropper";
    private static final boolean FILTER_SCALED_BITMAP = true;

    @Override
    public void cropAndScaleBitmap(Asset asset, float scale, Rect cropRect,
            boolean isRtl, Callback callback) {
        // Crop rect in pixels of source image.
        Rect scaledCropRect = new Rect(
                (int) Math.floor((float) cropRect.left / scale),
                (int) Math.floor((float) cropRect.top / scale),
                (int) Math.floor((float) cropRect.right / scale),
                (int) Math.floor((float) cropRect.bottom / scale));

        asset.decodeBitmapRegion(scaledCropRect, cropRect.width(), cropRect.height(), isRtl,
                new BitmapReceiver() {
                    @Override
                    public void onBitmapDecoded(Bitmap bitmap) {
                        if (bitmap == null) {
                            callback.onError(null);
                            return;
                        }
                        // Asset provides a bitmap which is appropriate for the target width &
                        // height, but since it does not guarantee an exact size we need to fit
                        // the bitmap to the cropRect.
                        sExecutorService.execute(() -> {
                            try {
                                // Fit bitmap to exact dimensions of crop rect.
                                Bitmap result = Bitmap.createScaledBitmap(
                                        bitmap,
                                        cropRect.width(),
                                        cropRect.height(),
                                        FILTER_SCALED_BITMAP);
                                new Handler(Looper.getMainLooper()).post(
                                        () -> callback.onBitmapCropped(result));
                            } catch (OutOfMemoryError e) {
                                Log.w(TAG,
                                        "Not enough memory to fit the final cropped and "
                                                + "scaled bitmap to size", e);
                                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
                            }
                        });
                    }
                });
    }
}
