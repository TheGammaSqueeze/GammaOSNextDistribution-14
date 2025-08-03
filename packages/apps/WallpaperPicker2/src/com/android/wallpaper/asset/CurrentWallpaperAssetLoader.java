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

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;

import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

/**
 * Glide custom model loader for {@link CurrentWallpaperAsset}.
 */
public class CurrentWallpaperAssetLoader implements
        ModelLoader<CurrentWallpaperAsset, InputStream> {

    @Override
    public boolean handles(CurrentWallpaperAsset currentWallpaperAsset) {
        return true;
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(CurrentWallpaperAsset currentWallpaperAsset,
                                               int width, int height, Options options) {
        return new LoadData<>(currentWallpaperAsset.getKey(),
                new CurrentWallpaperAssetDataFetcher(currentWallpaperAsset));
    }

    /**
     * Factory that constructs {@link ResourceAssetLoader} instances.
     */
    public static class CurrentWallpaperAssetLoaderFactory
            implements ModelLoaderFactory<CurrentWallpaperAsset, InputStream> {
        public CurrentWallpaperAssetLoaderFactory() {
        }

        @Override
        public ModelLoader<CurrentWallpaperAsset, InputStream> build(
                MultiModelLoaderFactory multiFactory) {
            return new CurrentWallpaperAssetLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    private static class CurrentWallpaperAssetDataFetcher implements DataFetcher<InputStream> {

        private CurrentWallpaperAsset mAsset;

        CurrentWallpaperAssetDataFetcher(CurrentWallpaperAsset asset) {
            mAsset = asset;
        }

        @Override
        public void loadData(Priority priority, final DataCallback<? super InputStream> callback) {
            ParcelFileDescriptor pfd = mAsset.getWallpaperPfd();

            if (pfd == null) {
                callback.onLoadFailed(new Exception("ParcelFileDescriptor for wallpaper is null, "
                        + "unable to open InputStream."));
                return;
            }

            callback.onDataReady(new AutoCloseInputStream(pfd));
        }

        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }

        @Override
        public void cancel() {
            // no op
        }

        @Override
        public void cleanup() {
            // no op
        }

        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }
    }
}
