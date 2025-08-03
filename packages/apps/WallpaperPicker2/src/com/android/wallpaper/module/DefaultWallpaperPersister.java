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

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.annotation.SuppressLint;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.BitmapReceiver;
import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.asset.StreamableAsset;
import com.android.wallpaper.asset.StreamableAsset.StreamReceiver;
import com.android.wallpaper.model.StaticWallpaperPrefMetadata;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.BitmapCropper.Callback;
import com.android.wallpaper.util.BitmapTransformer;
import com.android.wallpaper.util.DisplayUtils;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.WallpaperCropUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of WallpaperPersister which actually sets wallpapers to the system via
 * the WallpaperManager.
 */
public class DefaultWallpaperPersister implements WallpaperPersister {

    private static final int DEFAULT_COMPRESS_QUALITY = 100;
    private static final String TAG = "WallpaperPersister";

    private final Context mAppContext;
    private final WallpaperManager mWallpaperManager;
    private final WallpaperPreferences mWallpaperPreferences;
    private final WallpaperChangedNotifier mWallpaperChangedNotifier;
    private final DisplayUtils mDisplayUtils;
    private final BitmapCropper mBitmapCropper;
    private final WallpaperStatusChecker mWallpaperStatusChecker;
    private final CurrentWallpaperInfoFactory mCurrentWallpaperInfoFactory;
    private final boolean mIsRefactorSettingWallpaper;

    private WallpaperInfo mWallpaperInfoInPreview;

    @SuppressLint("ServiceCast")
    public DefaultWallpaperPersister(
            Context context,
            WallpaperManager wallpaperManager,
            WallpaperPreferences wallpaperPreferences,
            WallpaperChangedNotifier wallpaperChangedNotifier,
            DisplayUtils displayUtils,
            BitmapCropper bitmapCropper,
            WallpaperStatusChecker wallpaperStatusChecker,
            CurrentWallpaperInfoFactory wallpaperInfoFactory,
            boolean isRefactorSettingWallpaper
    ) {
        mAppContext = context.getApplicationContext();
        mWallpaperManager = wallpaperManager;
        mWallpaperPreferences = wallpaperPreferences;
        mWallpaperChangedNotifier = wallpaperChangedNotifier;
        mDisplayUtils = displayUtils;
        mBitmapCropper = bitmapCropper;
        mWallpaperStatusChecker = wallpaperStatusChecker;
        mCurrentWallpaperInfoFactory = wallpaperInfoFactory;
        mIsRefactorSettingWallpaper = isRefactorSettingWallpaper;
    }

    @Override
    public void setIndividualWallpaper(final WallpaperInfo wallpaper, Asset asset,
            @Nullable Rect cropRect, float scale, @Destination final int destination,
            final SetWallpaperCallback callback) {
        // Set wallpaper without downscaling directly from an input stream if there's no crop rect
        // specified by the caller and the asset is streamable.

        if (mWallpaperManager.isMultiCropEnabled() && (!(asset instanceof StreamableAsset))) {
            asset.decodeBitmap(bitmap -> {
                if (bitmap == null) {
                    callback.onError(null /* throwable */);
                    return;
                }
                setIndividualWallpaper(wallpaper, bitmap, cropRect, destination, callback);
            });
            return;
        }

        if ((cropRect == null || mWallpaperManager.isMultiCropEnabled())
                && asset instanceof StreamableAsset) {
            ((StreamableAsset) asset).fetchInputStream(new StreamReceiver() {
                @Override
                public void onInputStreamOpened(@Nullable InputStream inputStream) {
                    if (inputStream == null) {
                        callback.onError(null /* throwable */);
                        return;
                    }
                    setIndividualWallpaper(wallpaper, inputStream, cropRect, destination, callback);
                }
            });
            return;
        }

        // If no crop rect is specified but the wallpaper asset is not streamable, then fall back to
        // using the device's display size.
        if (cropRect == null) {
            Display display = ((WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display);
            asset.decodeBitmap(screenSize.x, screenSize.y, new BitmapReceiver() {
                @Override
                public void onBitmapDecoded(@Nullable Bitmap bitmap) {
                    if (bitmap == null) {
                        callback.onError(null /* throwable */);
                        return;
                    }
                    setIndividualWallpaper(wallpaper, bitmap, null, destination, callback);
                }
            });
            return;
        }

        mBitmapCropper.cropAndScaleBitmap(asset, scale, cropRect, false, new Callback() {
            @Override
            public void onBitmapCropped(Bitmap croppedBitmap) {
                setIndividualWallpaper(wallpaper, croppedBitmap, destination, callback);
            }

            @Override
            public void onError(@Nullable Throwable e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager.
     *
     * @param wallpaper     Wallpaper model object.
     * @param croppedBitmap Bitmap representing the individual wallpaper image.
     * @param destination   The destination - where to set the wallpaper to.
     * @param callback      Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaper(WallpaperInfo wallpaper, Bitmap croppedBitmap,
            @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, croppedBitmap, null, destination, callback);
        setWallpaperTask.execute();
    }

    private void setIndividualWallpaper(WallpaperInfo wallpaper, Bitmap fullBitmap, Rect cropHint,
            @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, fullBitmap, cropHint, destination, callback);
        setWallpaperTask.execute();
    }

    /**
     * Sets a static individual wallpaper stream to the system via the WallpaperManager.
     *
     * @param wallpaper   Wallpaper model object.
     * @param inputStream JPEG or PNG stream of wallpaper image's bytes.
     * @param destination The destination - where to set the wallpaper to.
     * @param callback    Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaper(WallpaperInfo wallpaper, InputStream inputStream,
            Rect cropHint, @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, inputStream, cropHint, destination, callback);
        setWallpaperTask.execute();
    }

    @Override
    public boolean setWallpaperInRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId, String remoteId) {
        final int wallpaperId = cropAndSetWallpaperBitmapInRotationStatic(wallpaperBitmap,
                attributions, actionUrl, collectionId, getDefaultWhichWallpaper());

        if (wallpaperId == 0) {
            return false;
        }

        return saveStaticWallpaperMetadata(attributions, actionUrl, collectionId, wallpaperId,
                remoteId, DEST_HOME_SCREEN);
    }

    @Override
    public int setWallpaperBitmapInNextRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId) {
        // The very first time setting the rotation wallpaper, make sure we set for both so that:
        // 1. The lock and home screen wallpaper become the same
        // 2. Lock screen wallpaper becomes "unset" until the next time user set wallpaper solely
        //    for the lock screen
        int whichWallpaper = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
        return cropAndSetWallpaperBitmapInRotationStatic(wallpaperBitmap, attributions, actionUrl,
                collectionId, whichWallpaper);
    }

    @Override
    public boolean finalizeWallpaperForNextRotation(List<String> attributions, String actionUrl,
            String collectionId, int wallpaperId, String remoteId) {
        return saveStaticWallpaperMetadata(attributions, actionUrl, collectionId, wallpaperId,
                remoteId, DEST_HOME_SCREEN);
    }

    @Override
    public boolean saveStaticWallpaperMetadata(List<String> attributions,
            String actionUrl,
            String collectionId,
            int wallpaperId,
            String remoteId,
            @Destination int destination) {
        if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
            mWallpaperPreferences.clearHomeWallpaperMetadata();

            // Persist wallpaper IDs if the rotating wallpaper component
            mWallpaperPreferences.setHomeWallpaperManagerId(wallpaperId);

            // Only copy over wallpaper ID to lock wallpaper if no explicit lock wallpaper is set
            // (so metadata isn't lost if a user explicitly sets a home-only wallpaper).

            mWallpaperPreferences.setHomeWallpaperAttributions(attributions);
            mWallpaperPreferences.setHomeWallpaperActionUrl(actionUrl);
            mWallpaperPreferences.setHomeWallpaperCollectionId(collectionId);
            mWallpaperPreferences.setHomeWallpaperRemoteId(remoteId);
        }

        // Set metadata to lock screen also when the rotating wallpaper so if user sets a home
        // screen-only wallpaper later, these attributions will still be available.
        if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH
                || !isSeparateLockScreenWallpaperSet()) {
            mWallpaperPreferences.clearLockWallpaperMetadata();
            mWallpaperPreferences.setLockWallpaperManagerId(wallpaperId);
            mWallpaperPreferences.setLockWallpaperAttributions(attributions);
            mWallpaperPreferences.setLockWallpaperActionUrl(actionUrl);
            mWallpaperPreferences.setLockWallpaperCollectionId(collectionId);
            mWallpaperPreferences.setLockWallpaperRemoteId(remoteId);
        }

        return true;
    }

    @Override
    public boolean saveStaticWallpaperToPreferences(@Destination int destination,
            @NonNull StaticWallpaperPrefMetadata metadata) {
        if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
            mWallpaperPreferences.clearHomeWallpaperMetadata();
            mWallpaperPreferences.setHomeStaticImageWallpaperMetadata(metadata);
        }

        if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
            mWallpaperPreferences.clearLockWallpaperMetadata();
            mWallpaperPreferences.setLockStaticImageWallpaperMetadata(metadata);
        }
        return true;
    }

    /**
     * Sets a wallpaper in rotation as a static wallpaper to the {@link WallpaperManager} with the
     * option allowBackup=false to save user data.
     *
     * @return wallpaper ID for the wallpaper bitmap.
     */
    private int cropAndSetWallpaperBitmapInRotationStatic(Bitmap wallpaperBitmap,
            List<String> attributions, String actionUrl, String collectionId,
            int whichWallpaper) {
        // Calculate crop and scale of the wallpaper to match the default one used in preview
        Point wallpaperSize = new Point(wallpaperBitmap.getWidth(), wallpaperBitmap.getHeight());
        Resources resources = mAppContext.getResources();
        Display croppingDisplay = mDisplayUtils.getWallpaperDisplay();
        Point defaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                resources, croppingDisplay);
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(croppingDisplay);

        // Determine minimum zoom to fit maximum visible area of wallpaper on crop surface.
        float minWallpaperZoom =
                WallpaperCropUtils.calculateMinZoom(wallpaperSize, screenSize);

        PointF centerPosition = WallpaperCropUtils.calculateDefaultCenter(mAppContext,
                wallpaperSize, WallpaperCropUtils.calculateVisibleRect(wallpaperSize, screenSize));

        Point scaledCenter = new Point((int) (minWallpaperZoom * centerPosition.x),
                (int) (minWallpaperZoom * centerPosition.y));

        int offsetX = Math.max(0, -(screenSize.x / 2 - scaledCenter.x));
        int offsetY = Math.max(0, -(screenSize.y / 2 - scaledCenter.y));

        Rect cropRect = WallpaperCropUtils.calculateCropRect(mAppContext, minWallpaperZoom,
                wallpaperSize, defaultCropSurfaceSize, screenSize, offsetX,
                offsetY, /* cropExtraWidth= */ true);

        Rect scaledCropRect = new Rect(
                (int) Math.floor((float) cropRect.left / minWallpaperZoom),
                (int) Math.floor((float) cropRect.top / minWallpaperZoom),
                (int) Math.floor((float) cropRect.right / minWallpaperZoom),
                (int) Math.floor((float) cropRect.bottom / minWallpaperZoom));

        // Scale and crop the bitmap
        if (!mWallpaperManager.isMultiCropEnabled()) {
            wallpaperBitmap = Bitmap.createBitmap(wallpaperBitmap,
                    scaledCropRect.left,
                    scaledCropRect.top,
                    scaledCropRect.width(),
                    scaledCropRect.height());
        }
        scaledCropRect = mWallpaperManager.isMultiCropEnabled() ? scaledCropRect : null;

        int wallpaperId = setBitmapToWallpaperManager(wallpaperBitmap, scaledCropRect,
                /* allowBackup */ false, whichWallpaper);
        if (wallpaperId > 0) {
            mWallpaperPreferences.storeLatestWallpaper(whichWallpaper,
                    String.valueOf(wallpaperId), attributions, actionUrl, collectionId,
                    wallpaperBitmap, WallpaperColors.fromBitmap(wallpaperBitmap));
        }
        mCurrentWallpaperInfoFactory.clearCurrentWallpaperInfos();
        return wallpaperId;
    }

    /*
     * Note: this method will return use home-only (FLAG_SYSTEM) instead of both home and lock
     * if there's a distinct lock-only static wallpaper set so we don't override the lock wallpaper.
     */
    @Override
    public int getDefaultWhichWallpaper() {
        return isSeparateLockScreenWallpaperSet()
                ? WallpaperManager.FLAG_SYSTEM
                : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
    }

    @Override
    public int setBitmapToWallpaperManager(Bitmap wallpaperBitmap, Rect cropHint,
            boolean allowBackup, int whichWallpaper) {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        if (wallpaperBitmap.compress(CompressFormat.PNG, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
            try {
                byte[] outByteArray = tmpOut.toByteArray();
                return mWallpaperManager.setStream(
                        new ByteArrayInputStream(outByteArray),
                        cropHint /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Log.e(TAG, "unable to write stream to wallpaper manager");
                return 0;
            }
        } else {
            Log.e(TAG, "unable to compress wallpaper");
            try {
                return mWallpaperManager.setBitmap(
                        wallpaperBitmap,
                        cropHint /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Log.e(TAG, "unable to set wallpaper");
                return 0;
            }
        }
    }

    @Override
    public int setStreamToWallpaperManager(InputStream inputStream, @Nullable Rect cropHint,
            boolean allowBackup, int whichWallpaper) {
        try {
            return mWallpaperManager.setStream(inputStream, cropHint, allowBackup,
                    whichWallpaper);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public int setStreamWithCropsToWallpaperManager(InputStream inputStream,
            @NonNull Map<Point, Rect> cropHints, boolean allowBackup, int whichWallpaper) {
        try {
            return mWallpaperManager.setStreamWithCrops(inputStream, cropHints, allowBackup,
                whichWallpaper);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public void setWallpaperInfoInPreview(WallpaperInfo wallpaper) {
        mWallpaperInfoInPreview = wallpaper;
    }

    @Override
    public void onLiveWallpaperSet(@Destination int destination) {
        android.app.WallpaperInfo currentWallpaperComponent = mWallpaperManager.getWallpaperInfo();
        android.app.WallpaperInfo previewedWallpaperComponent = mWallpaperInfoInPreview != null
                ? mWallpaperInfoInPreview.getWallpaperComponent() : null;

        // If there is no live wallpaper set on the WallpaperManager or it doesn't match the
        // WallpaperInfo which was last previewed, then do nothing and nullify last previewed
        // wallpaper.
        if (currentWallpaperComponent == null || previewedWallpaperComponent == null
                || !currentWallpaperComponent.getServiceName()
                .equals(previewedWallpaperComponent.getServiceName())) {
            mWallpaperInfoInPreview = null;
            return;
        }

        setLiveWallpaperMetadata(mWallpaperInfoInPreview, mWallpaperInfoInPreview.getEffectNames(),
                destination);
    }

    /**
     * Returns whether a separate lock-screen wallpaper is set to the WallpaperManager.
     */
    private boolean isSeparateLockScreenWallpaperSet() {
        return mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0;
    }

    @Override
    public void setLiveWallpaperMetadata(WallpaperInfo wallpaperInfo, String effects,
            @Destination int destination) {
        android.app.WallpaperInfo component = wallpaperInfo.getWallpaperComponent();

        if (destination == WallpaperPersister.DEST_HOME_SCREEN
                || destination == WallpaperPersister.DEST_BOTH) {
            mWallpaperPreferences.clearHomeWallpaperMetadata();
            mWallpaperPreferences.setHomeWallpaperServiceName(component.getServiceName());
            mWallpaperPreferences.setHomeWallpaperEffects(effects);
            mWallpaperPreferences.setHomeWallpaperCollectionId(
                    wallpaperInfo.getCollectionId(mAppContext));

            // Disable rotation wallpaper when setting live wallpaper to home screen
            // Daily rotation rotates both home and lock screen wallpaper when lock screen is not
            // set; otherwise daily rotation only rotates home screen while lock screen wallpaper
            // stays as what it's set to.
            mWallpaperPreferences.setWallpaperPresentationMode(
                    WallpaperPreferences.PRESENTATION_MODE_STATIC);
            mWallpaperPreferences.clearDailyRotations();
        }

        if (destination == WallpaperPersister.DEST_LOCK_SCREEN
                || destination == WallpaperPersister.DEST_BOTH) {
            mWallpaperPreferences.clearLockWallpaperMetadata();
            mWallpaperPreferences.setLockWallpaperServiceName(component.getServiceName());
            mWallpaperPreferences.setLockWallpaperEffects(effects);
            mWallpaperPreferences.setLockWallpaperCollectionId(
                    wallpaperInfo.getCollectionId(mAppContext));
        }
    }

    private class SetWallpaperTask extends AsyncTask<Void, Void, Boolean> {

        private final WallpaperInfo mWallpaper;
        @Destination
        private final int mDestination;
        private final WallpaperPersister.SetWallpaperCallback mCallback;

        private Bitmap mBitmap;
        private InputStream mInputStream;
        @Nullable
        private Rect mCropHint;

        /**
         * Optional parameters for applying a post-decoding fill or stretch transformation.
         */
        @Nullable
        private Point mFillSize;
        @Nullable
        private Point mStretchSize;

        SetWallpaperTask(WallpaperInfo wallpaper, Bitmap bitmap, Rect cropHint,
                @Destination int destination, WallpaperPersister.SetWallpaperCallback callback) {
            super();
            mWallpaper = wallpaper;
            mBitmap = bitmap;
            mCropHint = cropHint;
            mDestination = destination;
            mCallback = callback;
        }

        /**
         * Constructor for SetWallpaperTask which takes an InputStream instead of a bitmap. The task
         * will close the InputStream once it is done with it.
         */
        SetWallpaperTask(WallpaperInfo wallpaper, InputStream stream, Rect cropHint,
                @Destination int destination, WallpaperPersister.SetWallpaperCallback callback) {
            mWallpaper = wallpaper;
            mInputStream = stream;
            mCropHint = cropHint;
            mDestination = destination;
            mCallback = callback;
        }

        void setFillSize(Point fillSize) {
            if (mStretchSize != null) {
                throw new IllegalArgumentException(
                        "Can't pass a fill size option if a stretch size is "
                                + "already set.");
            }
            mFillSize = fillSize;
        }

        void setStretchSize(Point stretchSize) {
            if (mFillSize != null) {
                throw new IllegalArgumentException(
                        "Can't pass a stretch size option if a fill size is "
                                + "already set.");
            }
            mStretchSize = stretchSize;
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            int whichWallpaper;
            if (mDestination == DEST_HOME_SCREEN) {
                whichWallpaper = WallpaperManager.FLAG_SYSTEM;
            } else if (mDestination == DEST_LOCK_SCREEN) {
                whichWallpaper = WallpaperManager.FLAG_LOCK;
            } else { // DEST_BOTH
                whichWallpaper = WallpaperManager.FLAG_SYSTEM
                        | WallpaperManager.FLAG_LOCK;
            }

            boolean wasLockWallpaperSet = mWallpaperStatusChecker.isLockWallpaperSet();

            boolean allowBackup = mWallpaper.getBackupPermission() == WallpaperInfo.BACKUP_ALLOWED;
            final int wallpaperId;
            if (mBitmap != null) {
                // Apply fill or stretch transformations on mBitmap if necessary.
                if (mFillSize != null) {
                    mBitmap = BitmapTransformer.applyFillTransformation(mBitmap, mFillSize);
                }
                if (mStretchSize != null) {
                    mBitmap = Bitmap.createScaledBitmap(mBitmap, mStretchSize.x, mStretchSize.y,
                            true);
                }

                wallpaperId = setBitmapToWallpaperManager(mBitmap, mCropHint, allowBackup,
                        whichWallpaper);
            } else if (mInputStream != null) {
                wallpaperId = setStreamToWallpaperManager(mInputStream, mCropHint,
                        allowBackup, whichWallpaper);
            } else {
                Log.e(TAG,
                        "Both the wallpaper bitmap and input stream are null so we're unable "
                                + "to set any kind of wallpaper here.");
                wallpaperId = 0;
            }

            if (wallpaperId > 0) {
                if (mDestination == DEST_HOME_SCREEN
                        && mWallpaperPreferences.getWallpaperPresentationMode()
                        == WallpaperPreferences.PRESENTATION_MODE_ROTATING
                        && !wasLockWallpaperSet) {
                    copyRotatingWallpaperToLock();
                }

                if (mIsRefactorSettingWallpaper) {
                    if (mBitmap == null) {
                        mWallpaperManager.forgetLoadedWallpaper();
                        mBitmap = ((BitmapDrawable) mWallpaperManager
                                .getDrawable(WallpaperPersister.destinationToFlags(mDestination)))
                                .getBitmap();
                    }
                    setStaticWallpaperMetadataToPreferences(
                            mDestination,
                            wallpaperId,
                            BitmapUtils.generateHashCode(mBitmap),
                            WallpaperColors.fromBitmap(mBitmap));
                } else {
                    setImageWallpaperMetadata(mDestination, wallpaperId);
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close input stream " + e);
                    mCallback.onError(e /* throwable */);
                    return;
                }
            }

            if (isSuccess) {
                mCallback.onSuccess(mWallpaper, mDestination);
                mWallpaperChangedNotifier.notifyWallpaperChanged();
            } else {
                mCallback.onError(null /* throwable */);
            }
        }

        /**
         * Copies home wallpaper metadata to lock, and if rotation was enabled with a live wallpaper
         * previously, then copies over the rotating wallpaper image to the WallpaperManager also.
         * <p>
         * Used to accommodate the case where a user had gone from a home+lock daily rotation to
         * selecting a static wallpaper on home-only. The image and metadata that was previously
         * rotating is now copied to the lock screen.
         */
        private void copyRotatingWallpaperToLock() {
            mWallpaperPreferences.setLockWallpaperAttributions(
                    mWallpaperPreferences.getHomeWallpaperAttributions());
            mWallpaperPreferences.setLockWallpaperActionUrl(
                    mWallpaperPreferences.getHomeWallpaperActionUrl());
            mWallpaperPreferences.setLockWallpaperCollectionId(
                    mWallpaperPreferences.getHomeWallpaperCollectionId());

            // Set the lock wallpaper ID to what Android set it to, following its having
            // copied the system wallpaper over to the lock screen when we changed from
            // "both" to distinct system and lock screen wallpapers.
            mWallpaperPreferences.setLockWallpaperManagerId(
                    mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK));
        }

        /**
         * Sets the image wallpaper's metadata on SharedPreferences. This method is called after the
         * set wallpaper operation is successful.
         *
         * @param destination Which destination of wallpaper the metadata corresponds to (home
         *                    screen, lock screen, or both).
         * @param wallpaperId The ID of the static wallpaper returned by WallpaperManager, which
         *                    on N and later versions of Android uniquely identifies a wallpaper
         *                    image.
         */
        private void setImageWallpaperMetadata(@Destination int destination, int wallpaperId) {
            if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.clearHomeWallpaperMetadata();
                setImageWallpaperHomeMetadata(wallpaperId);

                // Disable rotation wallpaper when setting static image wallpaper to home screen
                // Daily rotation rotates both home and lock screen wallpaper when lock screen is
                // not set; otherwise daily rotation only rotates home screen while lock screen
                // wallpaper stays as what it's set to.
                mWallpaperPreferences.setWallpaperPresentationMode(
                        WallpaperPreferences.PRESENTATION_MODE_STATIC);
                mWallpaperPreferences.clearDailyRotations();
            }

            if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.clearLockWallpaperMetadata();
                setImageWallpaperLockMetadata(wallpaperId);
            }
        }

        private void setImageWallpaperHomeMetadata(int homeWallpaperId) {
            mWallpaperPreferences.setHomeWallpaperManagerId(homeWallpaperId);

            // Compute bitmap hash code after setting the wallpaper because JPEG compression has
            // likely changed many pixels' color values. Forget the previously loaded wallpaper
            // bitmap so that WallpaperManager doesn't return the old wallpaper drawable. Do this
            // on N+ devices in addition to saving the wallpaper ID for the purpose of backup &
            // restore.
            mWallpaperManager.forgetLoadedWallpaper();
            mBitmap = ((BitmapDrawable) mWallpaperManager.getDrawable()).getBitmap();
            long bitmapHash = BitmapUtils.generateHashCode(mBitmap);
            WallpaperColors colors = WallpaperColors.fromBitmap(mBitmap);

            mWallpaperPreferences.setHomeWallpaperHashCode(bitmapHash);

            mWallpaperPreferences.setHomeWallpaperAttributions(
                    mWallpaper.getAttributions(mAppContext));
            mWallpaperPreferences.setHomeWallpaperActionUrl(mWallpaper.getActionUrl(mAppContext));
            mWallpaperPreferences.setHomeWallpaperCollectionId(
                    mWallpaper.getCollectionId(mAppContext));
            mWallpaperPreferences.setHomeWallpaperRemoteId(mWallpaper.getWallpaperId());
            // Wallpaper ID can not be null or empty to save to the recent wallpaper as preferences
            String recentWallpaperId = TextUtils.isEmpty(mWallpaper.getWallpaperId())
                    ? String.valueOf(bitmapHash) : mWallpaper.getWallpaperId();
            mWallpaperPreferences.storeLatestWallpaper(FLAG_SYSTEM, recentWallpaperId,
                    mWallpaper, mBitmap, colors);
        }

        private void setImageWallpaperLockMetadata(int lockWallpaperId) {
            mWallpaperPreferences.setLockWallpaperManagerId(lockWallpaperId);
            mWallpaperPreferences.setLockWallpaperAttributions(
                    mWallpaper.getAttributions(mAppContext));
            mWallpaperPreferences.setLockWallpaperActionUrl(mWallpaper.getActionUrl(mAppContext));
            mWallpaperPreferences.setLockWallpaperCollectionId(
                    mWallpaper.getCollectionId(mAppContext));
            mWallpaperPreferences.setLockWallpaperRemoteId(mWallpaper.getWallpaperId());

            // Save the lock wallpaper image's hash code as well for the sake of backup & restore
            // because WallpaperManager-generated IDs are specific to a physical device and
            // cannot be  used to identify a wallpaper image on another device after restore is
            // complete.
            Bitmap lockBitmap = getLockWallpaperBitmap();
            long bitmapHashCode = 0;
            if (lockBitmap != null) {
                saveLockWallpaperHashCode(lockBitmap);
                bitmapHashCode = mWallpaperPreferences.getLockWallpaperHashCode();
            }

            // If the destination is both, use the home screen bitmap to populate the lock screen
            // recents list.
            if (lockBitmap == null
                    && lockWallpaperId == mWallpaperPreferences.getHomeWallpaperManagerId()) {
                lockBitmap = mBitmap;
                bitmapHashCode = mWallpaperPreferences.getHomeWallpaperHashCode();
            }

            if (lockBitmap != null) {
                mWallpaperPreferences.storeLatestWallpaper(FLAG_LOCK,
                        TextUtils.isEmpty(mWallpaper.getWallpaperId()) ? String.valueOf(
                                bitmapHashCode) : mWallpaper.getWallpaperId(), mWallpaper,
                        lockBitmap, WallpaperColors.fromBitmap(lockBitmap));
            }
        }

        private void setStaticWallpaperMetadataToPreferences(@Destination int destination,
                int wallpaperId, long bitmapHash, WallpaperColors colors) {
            saveStaticWallpaperToPreferences(
                    destination,
                    new StaticWallpaperPrefMetadata(
                            mWallpaper.getAttributions(mAppContext),
                            mWallpaper.getActionUrl(mAppContext),
                            mWallpaper.getCollectionId(mAppContext),
                            bitmapHash,
                            wallpaperId,
                            mWallpaper.getWallpaperId()));

            if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.storeLatestWallpaper(
                        FLAG_SYSTEM,
                        mWallpaper.getWallpaperId(),
                        mWallpaper,
                        mBitmap,
                        colors);
                // Stop wallpaper rotation if a static wallpaper is set to home.
                mWallpaperPreferences.setWallpaperPresentationMode(
                        WallpaperPreferences.PRESENTATION_MODE_STATIC);
                mWallpaperPreferences.clearDailyRotations();
            }

            if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.storeLatestWallpaper(
                        FLAG_LOCK,
                        mWallpaper.getWallpaperId(),
                        mWallpaper,
                        mBitmap,
                        colors);
            }
        }

        private Bitmap getLockWallpaperBitmap() {
            ParcelFileDescriptor parcelFd = mWallpaperManager.getWallpaperFile(
                    WallpaperManager.FLAG_LOCK);

            if (parcelFd == null) {
                return null;
            }

            try (InputStream fileStream = new FileInputStream(parcelFd.getFileDescriptor())) {
                return BitmapFactory.decodeStream(fileStream);
            } catch (IOException e) {
                Log.e(TAG, "IO exception when closing the file stream.", e);
                return null;
            } finally {
                try {
                    parcelFd.close();
                } catch (IOException e) {
                    Log.e(TAG, "IO exception when closing the file descriptor.", e);
                }
            }
        }

        private long saveLockWallpaperHashCode(Bitmap lockBitmap) {
            if (lockBitmap != null) {
                long bitmapHash = BitmapUtils.generateHashCode(lockBitmap);
                mWallpaperPreferences.setLockWallpaperHashCode(bitmapHash);
                return bitmapHash;
            }
            return 0;
        }
    }
}
