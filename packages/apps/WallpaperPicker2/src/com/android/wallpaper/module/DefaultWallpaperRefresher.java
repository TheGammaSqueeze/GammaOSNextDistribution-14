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
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.model.LiveWallpaperMetadata;
import com.android.wallpaper.model.WallpaperMetadata;
import com.android.wallpaper.picker.customization.data.content.WallpaperClient;
import com.android.wallpaper.util.DisplayUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link WallpaperRefresher} which refreshes wallpaper metadata
 * asynchronously.
 */
@SuppressLint("ServiceCast")
public class DefaultWallpaperRefresher implements WallpaperRefresher {

    private static final String TAG = "DefaultWPRefresher";

    private final Context mAppContext;
    private final WallpaperPreferences mWallpaperPreferences;
    private final WallpaperManager mWallpaperManager;
    private final WallpaperStatusChecker mWallpaperStatusChecker;

    private final DisplayUtils mDisplayUtils;

    private final WallpaperClient mWallpaperClient;


    /**
     * @param context The application's context.
     */
    public DefaultWallpaperRefresher(Context context) {
        mAppContext = context.getApplicationContext();

        Injector injector = InjectorProvider.getInjector();
        mWallpaperPreferences = injector.getPreferences(mAppContext);
        mWallpaperStatusChecker = injector.getWallpaperStatusChecker(context);
        mDisplayUtils = injector.getDisplayUtils(mAppContext);
        mWallpaperClient = injector.getWallpaperClient(mAppContext);

        // Retrieve WallpaperManager using Context#getSystemService instead of
        // WallpaperManager#getInstance so it can be mocked out in test.
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
    }

    @Override
    public void refresh(RefreshListener listener) {
        GetWallpaperMetadataAsyncTask task = new GetWallpaperMetadataAsyncTask(listener);
        task.execute();
    }

    /**
     * Retrieves the current wallpaper's thumbnail and metadata off the UI thread.
     */
    private class GetWallpaperMetadataAsyncTask extends
            AsyncTask<Void, Void, List<WallpaperMetadata>> {

        private final RefreshListener mListener;
        private final WallpaperManager mWallpaperManager;

        private long mCurrentHomeWallpaperHashCode;
        private long mCurrentLockWallpaperHashCode;
        private String mSystemWallpaperServiceName;

        @SuppressLint("ServiceCast")
        public GetWallpaperMetadataAsyncTask(RefreshListener listener) {
            mListener = listener;
            mWallpaperManager = WallpaperManager.getInstance(mAppContext);
        }

        @Override
        protected List<WallpaperMetadata> doInBackground(Void... unused) {
            List<WallpaperMetadata> wallpaperMetadatas = new ArrayList<>();

            boolean isHomeScreenStatic = mWallpaperManager.getWallpaperInfo(FLAG_SYSTEM) == null;
            if (!isHomeScreenMetadataCurrent() || (isHomeScreenStatic
                    && isHomeScreenAttributionsEmpty())) {
                mWallpaperPreferences.clearHomeWallpaperMetadata();
                setFallbackHomeScreenWallpaperMetadata();
            }

            boolean isLockScreenWallpaperCurrentlySet =
                    mWallpaperStatusChecker.isLockWallpaperSet();

            if (mWallpaperManager.getWallpaperInfo() == null) {
                wallpaperMetadatas.add(new WallpaperMetadata(
                        mWallpaperPreferences.getHomeWallpaperAttributions(),
                        mWallpaperPreferences.getHomeWallpaperActionUrl(),
                        mWallpaperPreferences.getHomeWallpaperCollectionId(),
                        /* wallpaperComponent= */ null,
                        getCurrentWallpaperCropHints(FLAG_SYSTEM)));
            } else {
                wallpaperMetadatas.add(
                        new LiveWallpaperMetadata(mWallpaperManager.getWallpaperInfo()));
            }

            // Return only home metadata if pre-N device or lock screen wallpaper is not explicitly
            // set.
            if (!isLockScreenWallpaperCurrentlySet) {
                return wallpaperMetadatas;
            }

            boolean isLockScreenStatic = mWallpaperManager.getWallpaperInfo(FLAG_LOCK) == null;
            if (!isLockScreenMetadataCurrent() || (isLockScreenStatic
                    && isLockScreenAttributionsEmpty())) {
                mWallpaperPreferences.clearLockWallpaperMetadata();
                setFallbackLockScreenWallpaperMetadata();
            }

            if (mWallpaperManager.getWallpaperInfo(FLAG_LOCK) == null) {
                wallpaperMetadatas.add(new WallpaperMetadata(
                        mWallpaperPreferences.getLockWallpaperAttributions(),
                        mWallpaperPreferences.getLockWallpaperActionUrl(),
                        mWallpaperPreferences.getLockWallpaperCollectionId(),
                        /* wallpaperComponent= */ null,
                        getCurrentWallpaperCropHints(FLAG_LOCK)));
            } else {
                wallpaperMetadatas.add(new LiveWallpaperMetadata(
                        mWallpaperManager.getWallpaperInfo(FLAG_LOCK)));
            }

            return wallpaperMetadatas;
        }

        @Override
        protected void onPostExecute(List<WallpaperMetadata> metadatas) {
            if (metadatas.size() > 2) {
                Log.e(TAG,
                        "Got more than 2 WallpaperMetadata objects - only home and (optionally) "
                                + "lock are permitted.");
                return;
            }

            mListener.onRefreshed(metadatas.get(0), metadatas.size() > 1 ? metadatas.get(1) : null,
                    mWallpaperPreferences.getWallpaperPresentationMode());
        }

        /**
         * Sets fallback wallpaper attributions to WallpaperPreferences when the saved metadata did
         * not match the system wallpaper. For live wallpapers, loads the label (title) but for
         * image wallpapers loads a generic title string.
         */
        private void setFallbackHomeScreenWallpaperMetadata() {
            android.app.WallpaperInfo wallpaperComponent = mWallpaperManager.getWallpaperInfo();
            if (wallpaperComponent == null) { // Image wallpaper
                mWallpaperPreferences.setHomeWallpaperAttributions(
                        Arrays.asList(mAppContext.getResources()
                                .getString(R.string.fallback_wallpaper_title)));

                mWallpaperPreferences.setHomeWallpaperManagerId(
                        mWallpaperManager.getWallpaperId(FLAG_SYSTEM));
            } else { // Live wallpaper
                mWallpaperPreferences.setHomeWallpaperAttributions(Arrays.asList(
                        wallpaperComponent.loadLabel(mAppContext.getPackageManager()).toString()));
                mWallpaperPreferences.setHomeWallpaperServiceName(mSystemWallpaperServiceName);
            }

            // Disable rotation wallpaper when setting fallback home screen wallpaper
            // Daily rotation wallpaper only rotates the home screen wallpaper
            mWallpaperPreferences.setWallpaperPresentationMode(
                    WallpaperPreferences.PRESENTATION_MODE_STATIC);
            mWallpaperPreferences.clearDailyRotations();
        }

        /**
         * Sets fallback lock screen wallpaper attributions to WallpaperPreferences. This should be
         * called when the saved lock screen wallpaper metadata does not match the currently set
         * lock screen wallpaper.
         */
        private void setFallbackLockScreenWallpaperMetadata() {
            mWallpaperPreferences.setLockWallpaperAttributions(
                    Arrays.asList(mAppContext.getResources()
                            .getString(R.string.fallback_wallpaper_title)));
            mWallpaperPreferences.setLockWallpaperManagerId(mWallpaperManager.getWallpaperId(
                    FLAG_LOCK));
        }

        /**
         * Returns whether the home screen metadata saved in WallpaperPreferences corresponds to the
         * current system wallpaper.
         */
        private boolean isHomeScreenMetadataCurrent() {
            return (mWallpaperManager.getWallpaperInfo() == null)
                    ? isHomeScreenImageWallpaperCurrent()
                    : isHomeScreenLiveWallpaperCurrent();
        }

        /**
         * Returns whether the home screen attributions saved in WallpaperPreferences is empty.
         */
        private boolean isHomeScreenAttributionsEmpty() {
            List<String> homeScreenAttributions =
                    mWallpaperPreferences.getHomeWallpaperAttributions();
            return homeScreenAttributions.get(0) == null
                    && homeScreenAttributions.get(1) == null
                    && homeScreenAttributions.get(2) == null;
        }

        private long getCurrentHomeWallpaperHashCode() {
            if (mCurrentHomeWallpaperHashCode == 0) {
                BitmapDrawable wallpaperDrawable = (BitmapDrawable) mWallpaperManager.getDrawable();
                // wallpaperDrawable should always be non-null, unless if there's a error in
                // WallpaperManager's state, in which case we'll consider the hashcode as unset.
                Bitmap wallpaperBitmap = wallpaperDrawable != null ? wallpaperDrawable.getBitmap()
                        : null;
                mCurrentHomeWallpaperHashCode =
                        wallpaperBitmap != null ? BitmapUtils.generateHashCode(wallpaperBitmap) : 0;

                // Manually request that WallpaperManager loses its reference to the current
                // wallpaper bitmap, which can occupy a large memory allocation for the lifetime of
                // the app.
                mWallpaperManager.forgetLoadedWallpaper();
            }
            return mCurrentHomeWallpaperHashCode;
        }

        private long getCurrentLockWallpaperHashCode() {
            if (mCurrentLockWallpaperHashCode == 0
                    && mWallpaperStatusChecker.isLockWallpaperSet()) {
                Bitmap wallpaperBitmap = getLockWallpaperBitmap();
                // If isLockWallpaperSet() returned true, wallpaperBitmap should always be
                // non-null, unless if there's a error in WallpaperManager, in which case we'll
                // consider the hashcode as unset.
                mCurrentLockWallpaperHashCode =
                        wallpaperBitmap != null ? BitmapUtils.generateHashCode(wallpaperBitmap) : 0;
            }
            return mCurrentLockWallpaperHashCode;
        }

        /**
         * Returns the lock screen wallpaper currently set on the device as a Bitmap, or null if no
         * lock screen wallpaper is set.
         */
        private Bitmap getLockWallpaperBitmap() {
            Bitmap lockBitmap = null;

            ParcelFileDescriptor pfd = mWallpaperManager.getWallpaperFile(FLAG_LOCK);
            // getWallpaperFile returns null if the lock screen isn't explicitly set, so need this
            // check.
            if (pfd != null) {
                InputStream fileStream = null;
                try {
                    fileStream = new FileInputStream(pfd.getFileDescriptor());
                    lockBitmap = BitmapFactory.decodeStream(fileStream);
                    pfd.close();
                    return lockBitmap;
                } catch (IOException e) {
                    Log.e(TAG, "IO exception when closing the file descriptor.");
                } finally {
                    if (fileStream != null) {
                        try {
                            fileStream.close();
                        } catch (IOException e) {
                            Log.e(TAG,
                                    "IO exception when closing input stream for lock screen WP.");
                        }
                    }
                }
            }

            return lockBitmap;
        }

        /**
         * Returns whether the image wallpaper set to the system matches the metadata in
         * WallpaperPreferences.
         */
        private boolean isHomeScreenImageWallpaperCurrent() {
            return mWallpaperPreferences.getHomeWallpaperManagerId()
                    == mWallpaperManager.getWallpaperId(FLAG_SYSTEM);
        }

        /**
         * Returns whether the live wallpaper set to the system's home screen matches the metadata
         * in WallpaperPreferences.
         */
        private boolean isHomeScreenLiveWallpaperCurrent() {
            mSystemWallpaperServiceName = mWallpaperManager.getWallpaperInfo().getServiceName();
            String homeWallpaperServiceName = mWallpaperPreferences.getHomeWallpaperServiceName();
            return mSystemWallpaperServiceName.equals(homeWallpaperServiceName);
        }

        /**
         * Returns whether the lock screen metadata saved in WallpaperPreferences corresponds to the
         * current lock screen wallpaper.
         */
        private boolean isLockScreenMetadataCurrent() {
            return (mWallpaperManager.getWallpaperInfo(FLAG_LOCK) == null)
                    ? isLockScreenImageWallpaperCurrent()
                    : isLockScreenLiveWallpaperCurrent();
        }

        /**
         * Returns whether the image wallpaper set for the lock screen matches the metadata in
         * WallpaperPreferences.
         */
        private boolean isLockScreenImageWallpaperCurrent() {
            // Check for lock wallpaper image same-ness only when there is no stored lock wallpaper
            // hash code. Otherwise if there is a lock wallpaper hash code stored in
            // {@link WallpaperPreferences}, then check hash codes.
            long savedLockWallpaperHash = mWallpaperPreferences.getLockWallpaperHashCode();

            if (savedLockWallpaperHash == 0) {
                return mWallpaperPreferences.getLockWallpaperManagerId()
                        == mWallpaperManager.getWallpaperId(FLAG_LOCK);
            } else {
                return savedLockWallpaperHash == getCurrentLockWallpaperHashCode();
            }
        }

        /**
         * Returns whether the live wallpaper for the home screen matches the metadata in
         * WallpaperPreferences.
         */
        private boolean isLockScreenLiveWallpaperCurrent() {
            String currentServiceName = mWallpaperManager.getWallpaperInfo(FLAG_LOCK)
                    .getServiceName();
            String storedServiceName = mWallpaperPreferences.getLockWallpaperServiceName();
            return currentServiceName.equals(storedServiceName);
        }


        /**
         * Returns whether the lock screen attributions saved in WallpaperPreferences are empty.
         */
        private boolean isLockScreenAttributionsEmpty() {
            List<String> attributions = mWallpaperPreferences.getLockWallpaperAttributions();
            return attributions.get(0) == null
                    && attributions.get(1) == null
                    && attributions.get(2) == null;
        }

        private Map<Point, Rect> getCurrentWallpaperCropHints(
                @WallpaperManager.SetWallpaperFlags int which) {
            List<Point> displaySizes = mDisplayUtils.getInternalDisplaySizes();
            return mWallpaperClient.getCurrentCropHints(displaySizes, which);
        }
    }
}
