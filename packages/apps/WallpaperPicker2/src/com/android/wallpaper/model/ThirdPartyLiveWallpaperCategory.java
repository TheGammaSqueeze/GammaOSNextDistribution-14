/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.wallpaper.model;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Category listing third party live wallpapers the user might have installed.
 */
public class ThirdPartyLiveWallpaperCategory extends WallpaperCategory {

    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();

    private final Set<String> mExcludedPackages;

    public ThirdPartyLiveWallpaperCategory(String title, String collectionId,
            List<WallpaperInfo> wallpapers,
            int priority, @Nullable Set<String> excludedLiveWallpaperPackageNames) {
        super(title, collectionId, wallpapers, priority);
        mExcludedPackages = excludedLiveWallpaperPackageNames;
    }

    @Override
    public void fetchWallpapers(Context context, WallpaperReceiver receiver, boolean forceReload) {
        if (forceReload) {
            sExecutorService.execute(() -> {
                List<WallpaperInfo> mCategoryWallpapers = getMutableWallpapers();
                List<WallpaperInfo> liveWallpapers = LiveWallpaperInfo.getAll(context,
                        mExcludedPackages);
                synchronized (mWallpapersLock) {
                    mCategoryWallpapers.clear();
                    mCategoryWallpapers.addAll(liveWallpapers);
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        // Perform a shallow clone so as not to pass the reference to the list
                        // along to clients.
                        receiver.onWallpapersReceived(new ArrayList<>(mCategoryWallpapers)));
            });
        } else {
            super.fetchWallpapers(context, receiver, forceReload);
        }
    }

    @Override
    public boolean supportsThirdParty() {
        return true;
    }

    @Override
    public boolean containsThirdParty(String packageName) {
        if (!supportsThirdParty()) return false;
        synchronized (mWallpapersLock) {
            for (WallpaperInfo wallpaper : getMutableWallpapers()) {
                android.app.WallpaperInfo wallpaperComponent = wallpaper.getWallpaperComponent();
                if (wallpaperComponent != null
                        && wallpaperComponent.getPackageName().equals(packageName)) {
                    return true;
                }
            }
        }
        return super.containsThirdParty(packageName);
    }
}
