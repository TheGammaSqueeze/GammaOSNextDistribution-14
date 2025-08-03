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
package com.android.wallpaper.testing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.StaticWallpaperPrefMetadata;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperChangedNotifier;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPreferences;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Test double for {@link WallpaperPersister}.
 */
public class TestWallpaperPersister implements WallpaperPersister {

    private Context mAppContext;
    private WallpaperPreferences mPrefs;
    private WallpaperChangedNotifier mWallpaperChangedNotifier;
    private Bitmap mCurrentHomeWallpaper;
    private Bitmap mCurrentLockWallpaper;
    private Bitmap mPendingHomeWallpaper;

    private Bitmap mPendingLockWallpaper;
    private List<String> mHomeAttributions;
    private String mHomeActionUrl;
    @Destination
    private int mDestination;
    private WallpaperPersister.SetWallpaperCallback mCallback;
    private boolean mFailNextCall;
    private Rect mCropRect;
    private float mScale;
    private WallpaperInfo mWallpaperInfo;
    private StaticWallpaperPrefMetadata mHomeStaticWallpaperPrefMetadata;
    private StaticWallpaperPrefMetadata mLockStaticWallpaperPrefMetadata;

    public TestWallpaperPersister(Context appContext) {
        mAppContext = appContext;
        mPrefs = InjectorProvider.getInjector().getPreferences(appContext);
        mWallpaperChangedNotifier = WallpaperChangedNotifier.getInstance();

        mCurrentHomeWallpaper = null;
        mCurrentLockWallpaper = null;
        mPendingHomeWallpaper = null;
        mPendingLockWallpaper = null;
        mWallpaperInfo = null;
        mFailNextCall = false;
        mScale = -1.0f;
    }

    @Override
    public void setIndividualWallpaper(final WallpaperInfo wallpaperInfo, Asset asset,
            @Nullable final Rect cropRect, final float scale, final @Destination int destination,
            final WallpaperPersister.SetWallpaperCallback callback) {
        asset.decodeBitmap(50, 50, bitmap -> {
            if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
                mPendingHomeWallpaper = bitmap;
                mPrefs.setHomeWallpaperAttributions(wallpaperInfo.getAttributions(mAppContext));
                mPrefs.setWallpaperPresentationMode(
                        WallpaperPreferences.PRESENTATION_MODE_STATIC);
                mPrefs.setHomeWallpaperRemoteId(wallpaperInfo.getWallpaperId());
            }
            if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
                mPendingLockWallpaper = bitmap;
                mPrefs.setLockWallpaperAttributions(wallpaperInfo.getAttributions(mAppContext));
                mPrefs.setLockWallpaperRemoteId(wallpaperInfo.getWallpaperId());
            }
            mDestination = destination;
            mCallback = callback;
            mCropRect = cropRect;
            mScale = scale;
            mWallpaperInfo = wallpaperInfo;
        });
    }

    @Override
    public boolean setWallpaperInRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId, String remoteId) {
        if (mFailNextCall) {
            return false;
        }

        mCurrentHomeWallpaper = wallpaperBitmap;
        mCurrentLockWallpaper = wallpaperBitmap;
        mHomeAttributions = attributions;
        mHomeActionUrl = actionUrl;
        return true;
    }

    @Override
    public int setWallpaperBitmapInNextRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId) {
        mCurrentHomeWallpaper = wallpaperBitmap;
        mCurrentLockWallpaper = wallpaperBitmap;
        mHomeAttributions = attributions;
        mHomeActionUrl = actionUrl;
        return 1;
    }

    @Override
    public boolean finalizeWallpaperForNextRotation(List<String> attributions, String actionUrl,
            String collectionId, int wallpaperId, String remoteId) {
        mHomeAttributions = attributions;
        mHomeActionUrl = actionUrl;
        return true;
    }

    /** Returns mock system wallpaper bitmap. */
    public Bitmap getCurrentHomeWallpaper() {
        return mCurrentHomeWallpaper;
    }

    /** Returns mock lock screen wallpaper bitmap. */
    public Bitmap getCurrentLockWallpaper() {
        return mCurrentLockWallpaper;
    }

    /** Returns mock home attributions. */
    public List<String> getHomeAttributions() {
        return mHomeAttributions;
    }

    /** Returns the home wallpaper action URL. */
    public String getHomeActionUrl() {
        return mHomeActionUrl;
    }

    /** Returns the Destination a wallpaper was most recently set on. */
    @Destination
    public int getLastDestination() {
        return mDestination;
    }

    /**
     * Sets whether the next "set wallpaper" operation should fail or succeed.
     */
    public void setFailNextCall(Boolean failNextCall) {
        mFailNextCall = failNextCall;
    }

    /**
     * Implemented so synchronous test methods can control the completion of what would otherwise be
     * an asynchronous operation.
     */
    public void finishSettingWallpaper() {
        if (mFailNextCall) {
            mCallback.onError(null /* throwable */);
        } else {
            if (mDestination == DEST_HOME_SCREEN || mDestination == DEST_BOTH) {
                mCurrentHomeWallpaper = mPendingHomeWallpaper;
                mPendingHomeWallpaper = null;
            }
            if (mDestination == DEST_LOCK_SCREEN || mDestination == DEST_BOTH) {
                mCurrentLockWallpaper = mPendingLockWallpaper;
                mPendingLockWallpaper = null;
            }
            mCallback.onSuccess(mWallpaperInfo, mDestination);
            mWallpaperChangedNotifier.notifyWallpaperChanged();
        }
    }

    @Override
    public void setWallpaperInfoInPreview(WallpaperInfo wallpaperInfo) {
    }

    @Override
    public void onLiveWallpaperSet(@Destination int destination) {
    }

    @Override
    public void setLiveWallpaperMetadata(WallpaperInfo wallpaperInfo, String effects,
            @Destination int destination) {
    }

    /** Returns the last requested wallpaper bitmap scale. */
    public float getScale() {
        return mScale;
    }

    /** Returns the last requested wallpaper crop. */
    public Rect getCropRect() {
        return mCropRect;
    }

    @Override
    public boolean saveStaticWallpaperMetadata(List<String> attributions, String actionUrl,
            String collectionId, int wallpaperId, String remoteId, @Destination int destination) {
        return false;
    }

    @Override
    public boolean saveStaticWallpaperToPreferences(int destination,
            StaticWallpaperPrefMetadata metadata) {
        if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
            mHomeStaticWallpaperPrefMetadata = metadata;
        }

        if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
            mLockStaticWallpaperPrefMetadata = metadata;
        }
        return true;
    }

    @Override
    public int getDefaultWhichWallpaper() {
        return 0;
    }

    @Override
    public int setBitmapToWallpaperManager(Bitmap wallpaperBitmap, Rect cropHint,
            boolean allowBackup, int whichWallpaper) {
        return 1;
    }

    @Override
    public int setStreamToWallpaperManager(InputStream inputStream, Rect cropHint,
            boolean allowBackup, int whichWallpaper) {
        return 1;
    }

    @Override
    public int setStreamWithCropsToWallpaperManager(InputStream inputStream,
            @NonNull Map<Point, Rect> cropHints, boolean allowBackup, int whichWallpaper) {
        return 1;
    }
}
