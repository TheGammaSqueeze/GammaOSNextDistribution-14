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
package com.android.wallpaper.asset;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asset representing the system's built-in wallpaper.
 * NOTE: This is only used for KitKat and newer devices. On older versions of Android, the
 * built-in wallpaper is accessed via the system Resources object, and is thus be represented
 * by a {@code ResourceAsset} instead.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public final class BuiltInWallpaperAsset extends Asset {
    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();
    private static final boolean SCALE_TO_FIT = true;
    private static final boolean CROP_TO_FIT = false;
    private static final float HORIZONTAL_CENTER_ALIGNED = 0.5f;
    private static final float VERTICAL_CENTER_ALIGNED = 0.5f;

    private final Context mContext;

    private Point mDimensions;
    private WallpaperModel mBuiltInWallpaperModel;

    /**
     * @param context The application's context.
     */
    public BuiltInWallpaperAsset(Context context) {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) {
            throw new AssertionError("BuiltInWallpaperAsset should not be instantiated on a pre-KitKat"
                    + " build");
        }

        mContext = context.getApplicationContext();
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
            boolean shouldAdjustForRtl, BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            Point dimensions = calculateRawDimensions();

            float horizontalCenter = BitmapUtils.calculateHorizontalAlignment(dimensions, rect);
            float verticalCenter = BitmapUtils.calculateVerticalAlignment(dimensions, rect);

            Drawable drawable = WallpaperManager.getInstance(mContext).getBuiltInDrawable(
                    rect.width(),
                    rect.height(),
                    CROP_TO_FIT,
                    horizontalCenter,
                    verticalCenter);
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            decodeBitmapCompleted(receiver, bitmap);
        });
    }

    @Override
    public void decodeRawDimensions(Activity unused, DimensionsReceiver receiver) {
        sExecutorService.execute(() -> {
            Point dimensions = calculateRawDimensions();
            new Handler(Looper.getMainLooper()).post(
                    () -> receiver.onDimensionsDecoded(dimensions));
        });
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight, boolean useHardwareBitmapIfPossible,
                             BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            final WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);

            Drawable drawable = (targetWidth <= 0 || targetHeight <= 0)
                    ? wallpaperManager.getBuiltInDrawable()
                    : wallpaperManager.getBuiltInDrawable(
                        targetWidth,
                        targetHeight,
                        SCALE_TO_FIT,
                        HORIZONTAL_CENTER_ALIGNED,
                        VERTICAL_CENTER_ALIGNED);

            // Manually request that WallpaperManager loses its reference to the built-in wallpaper
            // bitmap, which can occupy a large memory allocation for the lifetime of the app.
            wallpaperManager.forgetLoadedWallpaper();

            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            decodeBitmapCompleted(receiver, bitmap);
        });
    }

    @Override
    public void decodeBitmap(BitmapReceiver receiver) {
        decodeBitmap(0, 0, receiver);
    }

    @Override
    public boolean supportsTiling() {
        return false;
    }

    /**
     * Calculates the raw dimensions of the built-in drawable. This method should not be called from
     * the main UI thread.
     *
     * @return Raw dimensions of the built-in wallpaper drawable.
     */
    private Point calculateRawDimensions() {
        if (mDimensions != null) {
            return mDimensions;
        }

        Drawable builtInDrawable = WallpaperManager.getInstance(mContext).getBuiltInDrawable();
        Bitmap builtInBitmap = ((BitmapDrawable) builtInDrawable).getBitmap();
        mDimensions = new Point(builtInBitmap.getWidth(), builtInBitmap.getHeight());
        return mDimensions;
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView, int placeholderColor) {
        if (mBuiltInWallpaperModel == null) {
            mBuiltInWallpaperModel =
                    new WallpaperModel(context.getApplicationContext(), WallpaperModel.SOURCE_BUILT_IN);
        }

        Glide.with(context)
                .asDrawable()
                .load(mBuiltInWallpaperModel)
                .apply(RequestOptions.centerCropTransform()
                        .placeholder(new ColorDrawable(placeholderColor)))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }
}
