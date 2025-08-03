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

import static android.app.WallpaperManager.SetWallpaperFlags;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.WorkerThread;

import com.android.wallpaper.util.WallpaperCropUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutionException;

/**
 * Asset representing the currently-set image wallpaper, including when daily rotation
 * is set with a static wallpaper (but not when daily rotation uses a live wallpaper).
 */
public class CurrentWallpaperAsset extends StreamableAsset {

    private static final String TAG = "CurrentWallpaperAsset";
    int mWallpaperId;
    private final WallpaperManager mWallpaperManager;
    @SetWallpaperFlags
    private final int mWallpaperManagerFlag;

    private final boolean mCropped;

    public CurrentWallpaperAsset(Context context, @SetWallpaperFlags int wallpaperManagerFlag,
            boolean getCropped) {
        mWallpaperManager = WallpaperManager.getInstance(context.getApplicationContext());
        mWallpaperManagerFlag = wallpaperManagerFlag;
        mWallpaperId = mWallpaperManager.getWallpaperId(mWallpaperManagerFlag);
        mCropped = getCropped;
    }

    @Override
    protected InputStream openInputStream() {
        ParcelFileDescriptor pfd = getWallpaperPfd();

        if (pfd == null) {
            Log.e(TAG, "ParcelFileDescriptor for wallpaper " + mWallpaperManagerFlag
                    + " is null, unable to open InputStream.");
            return null;
        }

        return new AutoCloseInputStream(pfd);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = result * 31 + mWallpaperManagerFlag;
        result = result * 31 + mWallpaperId;
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CurrentWallpaperAsset) {
            CurrentWallpaperAsset otherAsset = (CurrentWallpaperAsset) object;
            return otherAsset.mWallpaperManagerFlag == mWallpaperManagerFlag
                    && otherAsset.mWallpaperId == mWallpaperId;

        }
        return false;
    }


    @Override
    public void loadLowResDrawable(Activity activity, ImageView imageView, int placeholderColor,
            BitmapTransformation transformation) {
        MultiTransformation<Bitmap> multiTransformation =
                new MultiTransformation<>(new FitCenter(), transformation);
        Glide.with(activity)
                .asDrawable()
                .load(this)
                .apply(RequestOptions.bitmapTransform(multiTransformation)
                        .placeholder(new ColorDrawable(placeholderColor)))
                .into(imageView);
    }

    @Override
    @WorkerThread
    public Bitmap getLowResBitmap(Context context) {
        try {
            return Glide.with(context)
                    .asBitmap()
                    .load(this)
                    .submit()
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, "Couldn't obtain low res bitmap", e);
        }
        return null;
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int unusedPlaceholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(CurrentWallpaperAsset.this)
                .apply(RequestOptions.centerCropTransform())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    @Override
    protected void adjustCropRect(Context context, Point assetDimensions, Rect cropRect,
            boolean offsetToStart) {
        if (offsetToStart) {
            cropRect.offsetTo(0, 0);
        }
        WallpaperCropUtils.adjustCurrentWallpaperCropRect(context, assetDimensions, cropRect);
    }

    public Key getKey() {
        return new CurrentWallpaperKey(mWallpaperManager, mWallpaperManagerFlag);
    }

    ParcelFileDescriptor getWallpaperPfd() {
        return mWallpaperManager.getWallpaperFile(mWallpaperManagerFlag, mCropped);
    }

    /**
     * Glide caching key for currently-set wallpapers using wallpaper IDs provided by
     * WallpaperManager.
     */
    private static final class CurrentWallpaperKey implements Key {
        private final WallpaperManager mWallpaperManager;
        @SetWallpaperFlags
        private final int mWallpaperFlag;

        CurrentWallpaperKey(WallpaperManager wallpaperManager,
                @SetWallpaperFlags int wallpaperFlag) {
            mWallpaperManager = wallpaperManager;
            mWallpaperFlag = wallpaperFlag;
        }

        @Override
        public String toString() {
            return getCacheKey();
        }

        @Override
        public int hashCode() {
            return getCacheKey().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof CurrentWallpaperKey) {
                CurrentWallpaperKey otherKey = (CurrentWallpaperKey) object;
                return getCacheKey().equals(otherKey.getCacheKey());

            }
            return false;
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        /**
         * Returns an inexpensively calculated {@link String} suitable for use as a disk cache key.
         */
        private String getCacheKey() {
            return "CurrentWallpaperKey{"
                    + "flag=" + mWallpaperFlag
                    + ",id=" + mWallpaperManager.getWallpaperId(mWallpaperFlag)
                    + '}';
        }
    }
}
