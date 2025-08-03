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

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;

import androidx.annotation.Nullable;

import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.model.CurrentWallpaperInfo;
import com.android.wallpaper.model.LiveWallpaperMetadata;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;
import com.android.wallpaper.picker.customization.data.content.WallpaperClient;
import com.android.wallpaper.util.DisplayUtils;

import java.util.HashMap;
import java.util.List;

/**
 * Default implementation of {@link CurrentWallpaperInfoFactory} which actually constructs
 * {@link WallpaperInfo} instances representing the wallpapers currently set to the device.
 */
public class DefaultCurrentWallpaperInfoFactory implements CurrentWallpaperInfoFactory {

    private final WallpaperRefresher mWallpaperRefresher;
    private final LiveWallpaperInfoFactory mLiveWallpaperInfoFactory;

    // Cached copies of the currently-set WallpaperInfo(s) and presentation mode.
    private WallpaperInfo mHomeWallpaper;
    @Nullable
    private WallpaperInfo mLockWallpaper;
    @PresentationMode
    private int mPresentationMode;

    public DefaultCurrentWallpaperInfoFactory(WallpaperRefresher wallpaperRefresher,
            LiveWallpaperInfoFactory liveWallpaperInfoFactory) {
        mWallpaperRefresher = wallpaperRefresher;
        mLiveWallpaperInfoFactory = liveWallpaperInfoFactory;
    }

    @Override
    public synchronized void createCurrentWallpaperInfos(Context context, boolean forceRefresh,
            WallpaperInfoCallback callback) {

        BaseFlags flags = InjectorProvider.getInjector().getFlags();
        final boolean isMultiCropEnabled =
                flags.isMultiCropEnabled() && flags.isMultiCropPreviewUiEnabled();

        boolean isHomeWallpaperSynced  = homeWallpaperSynced(context);
        boolean isLockWallpaperSynced  = lockWallpaperSynced(context);
        if (!forceRefresh && isHomeWallpaperSynced && isLockWallpaperSynced
                && mPresentationMode != WallpaperPreferences.PRESENTATION_MODE_ROTATING) {
            // Update wallpaper crop hints for static wallpaper even if home & lock wallpaper are
            // considered synced because wallpaper info are considered synced as long as both are
            // static
            if (isMultiCropEnabled) {
                DisplayUtils displayUtils = InjectorProvider.getInjector().getDisplayUtils(context);
                WallpaperClient wallpaperClient = InjectorProvider.getInjector().getWallpaperClient(
                        context);
                List<Point> displaySizes = displayUtils.getInternalDisplaySizes();
                if (mHomeWallpaper != null) {
                    boolean isHomeWallpaperStatic = mHomeWallpaper.getWallpaperComponent() == null
                            || mHomeWallpaper.getWallpaperComponent().getComponent() == null;
                    if (isHomeWallpaperStatic) {
                        mHomeWallpaper.setWallpaperCropHints(
                                wallpaperClient.getCurrentCropHints(displaySizes,
                                        WallpaperManager.FLAG_SYSTEM));
                    } else {
                        mHomeWallpaper.setWallpaperCropHints(new HashMap<>());
                    }
                }
                if (mLockWallpaper != null) {
                    boolean isLockWallpaperStatic = mLockWallpaper.getWallpaperComponent() == null
                            || mLockWallpaper.getWallpaperComponent().getComponent() == null;
                    if (isLockWallpaperStatic) {
                        mLockWallpaper.setWallpaperCropHints(
                                wallpaperClient.getCurrentCropHints(displaySizes,
                                        WallpaperManager.FLAG_LOCK));
                    } else {
                        mLockWallpaper.setWallpaperCropHints(new HashMap<>());
                    }
                }
            } else {
                if (mHomeWallpaper != null) mHomeWallpaper.setWallpaperCropHints(null);
                if (mLockWallpaper != null) mLockWallpaper.setWallpaperCropHints(null);
            }
            callback.onWallpaperInfoCreated(mHomeWallpaper, mLockWallpaper, mPresentationMode);
            return;
        }

        // Clear cached copies if we are refreshing the currently-set WallpaperInfo(s) from the
        // Refresher so that multiple calls to this method after a call with forceRefresh=true don't
        // provide old cached copies.
        if (forceRefresh) {
            clearCurrentWallpaperInfos();
        }

        mWallpaperRefresher.refresh(
                (homeWallpaperMetadata, lockWallpaperMetadata, presentationMode) -> {
                    WallpaperInfo homeWallpaper;
                    if (homeWallpaperMetadata instanceof LiveWallpaperMetadata) {
                        homeWallpaper = mLiveWallpaperInfoFactory.getLiveWallpaperInfo(
                                homeWallpaperMetadata.getWallpaperComponent());
                    } else {
                        homeWallpaper = new CurrentWallpaperInfo(
                                homeWallpaperMetadata.getAttributions(),
                                homeWallpaperMetadata.getActionUrl(),
                                homeWallpaperMetadata.getCollectionId(),
                                WallpaperManager.FLAG_SYSTEM);
                        if (isMultiCropEnabled) {
                            homeWallpaper.setWallpaperCropHints(
                                    homeWallpaperMetadata.getWallpaperCropHints());
                        }
                    }

                    WallpaperInfo lockWallpaper = null;

                    if (lockWallpaperMetadata != null) {

                        if (lockWallpaperMetadata instanceof LiveWallpaperMetadata) {
                            lockWallpaper = mLiveWallpaperInfoFactory.getLiveWallpaperInfo(
                                    lockWallpaperMetadata.getWallpaperComponent());
                        } else {
                            lockWallpaper = new CurrentWallpaperInfo(
                                    lockWallpaperMetadata.getAttributions(),
                                    lockWallpaperMetadata.getActionUrl(),
                                    lockWallpaperMetadata.getCollectionId(),
                                    WallpaperManager.FLAG_LOCK);

                            if (isMultiCropEnabled) {
                                lockWallpaper.setWallpaperCropHints(
                                        lockWallpaperMetadata.getWallpaperCropHints());
                            }
                        }
                    }

                    mHomeWallpaper = homeWallpaper;
                    mLockWallpaper = lockWallpaper;
                    mPresentationMode = presentationMode;

                    callback.onWallpaperInfoCreated(homeWallpaper, lockWallpaper, presentationMode);
                });
    }

    /**
     * We check 2 things in this function:
     * 1. If mHomeWallpaper is null, the wallpaper is not initialized. Return false.
     * 2. In the case when mHomeWallpaper is not null, we check if mHomeWallpaper is synced with the
     *    one from the wallpaper manager.
     */
    private boolean homeWallpaperSynced(Context context) {
        if (mHomeWallpaper == null) {
            return false;
        }
        return wallpaperSynced(context, mHomeWallpaper, WallpaperManager.FLAG_SYSTEM);
    }

    /**
     * mLockWallpaper can be null even after initialization. We only check the case if the
     * lockscreen wallpaper is synced.
     */
    private boolean lockWallpaperSynced(Context context) {
        return wallpaperSynced(context, mLockWallpaper, WallpaperManager.FLAG_LOCK);
    }

    /**
     * Check if the given wallpaper info is synced with the one from the wallpaper manager. We only
     * try to get the underlying ComponentName from both sides.
     * If both are null, it means both are static image wallpapers, or both are not set,
     * which we consider synced and return true.
     * If only of the them is null, it means one is static image wallpaper and another is live
     * wallpaper. We should return false.
     * If both are not null, we check if the two ComponentName(s) are equal.
     */
    private boolean wallpaperSynced(Context context, @Nullable WallpaperInfo wallpaperInfo,
            int which) {
        android.app.WallpaperInfo currentWallpaperInfo = WallpaperManager.getInstance(context)
                .getWallpaperInfo(which);
        ComponentName currentComponentName = currentWallpaperInfo != null
                ? currentWallpaperInfo.getComponent() : null;
        android.app.WallpaperInfo info = wallpaperInfo != null
                ? wallpaperInfo.getWallpaperComponent() : null;
        ComponentName homeComponentName = info != null
                ? info.getComponent() : null;
        if (currentComponentName == null) {
            // If both are null, it's synced.
            return homeComponentName == null;
        } else if (homeComponentName == null) {
            // currentComponentName not null and homeComponentName null. It's not synced.
            return false;
        } else {
            return currentComponentName.equals(homeComponentName);
        }
    }

    @Override
    public void clearCurrentWallpaperInfos() {
        mHomeWallpaper = null;
        mLockWallpaper = null;
    }
}
