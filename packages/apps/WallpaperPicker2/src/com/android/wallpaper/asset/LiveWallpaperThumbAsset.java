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

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.WorkerThread;

import com.android.wallpaper.module.DrawableLayerResolver;
import com.android.wallpaper.module.InjectorProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Asset wrapping a drawable for a live wallpaper thumbnail.
 */
public class LiveWallpaperThumbAsset extends Asset {
    private static final String TAG = "LiveWallpaperThumbAsset";
    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();
    private static final int LOW_RES_THUMB_TIMEOUT_SECONDS = 2;

    protected final Context mContext;
    protected final android.app.WallpaperInfo mInfo;
    protected final DrawableLayerResolver mLayerResolver;
    // The content Uri of thumbnail
    protected Uri mUri;
    protected boolean mShouldCacheThumbnail;
    private Drawable mCachedThumbnail;

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info) {
        this(context, info, /* uri= */ null);
    }

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info, Uri uri) {
        this(context, info, uri, /* shouldCacheThumbnail= */ true);
    }

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info, Uri uri,
            boolean shouldCacheThumbnail) {
        mContext = context.getApplicationContext();
        mInfo = info;
        mUri = uri;
        mShouldCacheThumbnail = shouldCacheThumbnail;
        mLayerResolver = InjectorProvider.getInjector().getDrawableLayerResolver();
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight, boolean useHardwareBitmapIfPossible,
                             BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            Drawable thumb = getThumbnailDrawable();

            // Live wallpaper components may or may not specify a thumbnail drawable.
            if (thumb instanceof BitmapDrawable) {
                BitmapDrawable drawableThumb = (BitmapDrawable) thumb;
                int thumbHeight = drawableThumb.getIntrinsicHeight();
                int thumbWidth = drawableThumb.getIntrinsicWidth();
                int height, width;
                if (thumbHeight > 0 && thumbWidth > 0) {
                    double ratio = thumbHeight > thumbWidth ? (double) targetHeight / thumbHeight
                            : (double) targetWidth / thumbWidth;
                    height = (int) (thumbHeight * ratio);
                    width =  (int) (thumbWidth * ratio);
                } else {
                    height = targetHeight;
                    width = targetWidth;
                }
                decodeBitmapCompleted(receiver,
                        Bitmap.createScaledBitmap(drawableThumb.getBitmap(), width, height, true));
                return;
            } else if (thumb != null) {
                Bitmap bitmap;
                if (thumb.getIntrinsicWidth() > 0 && thumb.getIntrinsicHeight() > 0) {
                    bitmap = Bitmap.createBitmap(thumb.getIntrinsicWidth(),
                            thumb.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                } else {
                    decodeBitmapCompleted(receiver, null);
                    return;
                }

                Canvas canvas = new Canvas(bitmap);
                thumb.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                thumb.draw(canvas);
                decodeBitmapCompleted(receiver,
                        Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true));
                return;
            }
            decodeBitmapCompleted(receiver, null);
        });
    }

    @Override
    public void decodeBitmap(BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            Drawable thumb = getThumbnailDrawable();
            Bitmap bitmap = null;
            // Live wallpaper components may or may not specify a thumbnail drawable.
            if (thumb instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) thumb).getBitmap();
            } else if (thumb != null) {
                if (thumb.getIntrinsicWidth() > 0 && thumb.getIntrinsicHeight() > 0) {
                    bitmap = Bitmap.createBitmap(thumb.getIntrinsicWidth(),
                            thumb.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                }
            }
            decodeBitmapCompleted(receiver, bitmap);
        });
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
            boolean shouldAdjustForRtl, BitmapReceiver receiver) {
        receiver.onBitmapDecoded(null);
    }

    @Override
    public void decodeRawDimensions(Activity unused, DimensionsReceiver receiver) {
        // TODO(b/277166654): Reuse the logic for all thumb asset decoding
        sExecutorService.execute(() -> {
            Bitmap result = null;
            Drawable thumb = mInfo.loadThumbnail(mContext.getPackageManager());
            if (thumb instanceof BitmapDrawable) {
                result = ((BitmapDrawable) thumb).getBitmap();
            } else if (thumb instanceof LayerDrawable) {
                Drawable layer = mLayerResolver.resolveLayer((LayerDrawable) thumb);
                if (layer instanceof BitmapDrawable) {
                    result = ((BitmapDrawable) layer).getBitmap();
                }
            }
            final Bitmap lr = result;
            new Handler(Looper.getMainLooper()).post(
                    () ->
                            receiver.onDimensionsDecoded(
                                    lr == null ? null : new Point(lr.getWidth(), lr.getHeight()))
            );
        });
    }

    @Override
    public boolean supportsTiling() {
        return false;
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int placeholderColor) {
        RequestOptions reqOptions;
        if (mUri != null) {
            reqOptions = RequestOptions.centerCropTransform().apply(RequestOptions
                    .diskCacheStrategyOf(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true))
                    .placeholder(new ColorDrawable(placeholderColor));
        } else {
            reqOptions = RequestOptions.centerCropTransform()
                    .placeholder(new ColorDrawable(placeholderColor));
        }
        imageView.setBackgroundColor(placeholderColor);
        Glide.with(context)
                .asDrawable()
                .load(LiveWallpaperThumbAsset.this)
                .apply(reqOptions)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    @Override
    public void loadLowResDrawable(Activity activity, ImageView imageView, int placeholderColor,
            BitmapTransformation transformation) {
        Transformation<Bitmap> finalTransformation = (transformation == null)
                ? new FitCenter()
                : new MultiTransformation<>(new FitCenter(), transformation);
        Glide.with(activity)
                .asDrawable()
                .load(LiveWallpaperThumbAsset.this)
                .apply(RequestOptions.bitmapTransform(finalTransformation)
                        .placeholder(new ColorDrawable(placeholderColor)))
                .into(imageView);
    }

    @Override
    @WorkerThread
    public Bitmap getLowResBitmap(Context context) {
        try {
            Drawable drawable = Glide.with(context)
                    .asDrawable()
                    .load(this)
                    .submit()
                    .get(LOW_RES_THUMB_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null) {
                    return bitmap;
                }
            }
            Bitmap bitmap;
            // If not a bitmap, draw the drawable into a bitmap
            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                return null;
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(), Bitmap.Config.RGB_565);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.w(TAG, "Couldn't obtain low res bitmap", e);
        }
        return null;
    }

    /**
     * Returns a Glide cache key.
     */
    Key getKey() {
        return new LiveWallpaperThumbKey(mInfo);
    }

    /**
     * Returns the thumbnail drawable for the live wallpaper synchronously. Should not be called on
     * the main UI thread.
     *
     * <p>Cache the thumbnail if {@code mShouldCacheThumbnail} is true.
     */
    @WorkerThread
    protected Drawable getThumbnailDrawable() {
        if (!mShouldCacheThumbnail) {
            return loadThumbnailFromUri();
        }

        if (mCachedThumbnail != null) {
            return mCachedThumbnail;
        }

        mCachedThumbnail = loadThumbnailFromUri();
        if (mCachedThumbnail == null) {
            mCachedThumbnail = loadThumbnailFromInfo();
        }

        return mCachedThumbnail;
    }

    private Drawable loadThumbnailFromUri() {
        if (mUri != null) {
            try (AssetFileDescriptor assetFileDescriptor =
                         mContext.getContentResolver().openAssetFileDescriptor(mUri, "r")) {
                if (assetFileDescriptor != null) {
                    return new BitmapDrawable(mContext.getResources(),
                            BitmapFactory.decodeStream(assetFileDescriptor.createInputStream()));
                }
            } catch (IOException e) {
                Log.w(TAG, "Not found thumbnail from URI.");
            }
        }
        return null;
    }

    private Drawable loadThumbnailFromInfo() {
        return mInfo.loadThumbnail(mContext.getPackageManager());
    }

    /**
     * Glide caching key for resources from any arbitrary package.
     */
    private static final class LiveWallpaperThumbKey implements Key {
        private android.app.WallpaperInfo mInfo;

        public LiveWallpaperThumbKey(android.app.WallpaperInfo info) {
            mInfo = info;
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
            if (!(object instanceof LiveWallpaperThumbKey)) {
                return false;
            }

            LiveWallpaperThumbKey otherKey = (LiveWallpaperThumbKey) object;
            return getCacheKey().equals(otherKey.getCacheKey());
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        /**
         * Returns an inexpensively calculated {@link String} suitable for use as a disk cache key,
         * based on the live wallpaper's package name and service name, which is enough to uniquely
         * identify a live wallpaper.
         */
        private String getCacheKey() {
            return "LiveWallpaperThumbKey{"
                    + "packageName=" + mInfo.getPackageName() + ","
                    + "serviceName=" + mInfo.getServiceName()
                    + '}';
        }
    }
}
