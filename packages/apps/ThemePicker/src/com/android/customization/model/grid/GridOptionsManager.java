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
package com.android.customization.model.grid;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;

import com.android.customization.model.CustomizationManager;
import com.android.customization.module.CustomizationInjector;
import com.android.customization.module.logging.ThemesUserEventLogger;
import com.android.wallpaper.R;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.util.PreviewUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link CustomizationManager} for interfacing with the launcher to handle {@link GridOption}s.
 */
public class GridOptionsManager implements CustomizationManager<GridOption> {

    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();
    private static final String TAG = "GridOptionsManager";

    private static GridOptionsManager sGridOptionsManager;

    private final LauncherGridOptionsProvider mProvider;
    private final ThemesUserEventLogger mEventLogger;
    private int mGridOptionSize = -1;

    /** Returns the {@link GridOptionsManager} instance. */
    public static GridOptionsManager getInstance(Context context) {
        if (sGridOptionsManager == null) {
            Context appContext = context.getApplicationContext();
            CustomizationInjector injector = (CustomizationInjector) InjectorProvider.getInjector();
            ThemesUserEventLogger eventLogger =
                    (ThemesUserEventLogger) injector.getUserEventLogger();
            sGridOptionsManager = new GridOptionsManager(
                    new LauncherGridOptionsProvider(appContext,
                            appContext.getString(R.string.grid_control_metadata_name)),
                    eventLogger);
        }
        return sGridOptionsManager;
    }

    @VisibleForTesting
    GridOptionsManager(LauncherGridOptionsProvider provider, ThemesUserEventLogger logger) {
        mProvider = provider;
        mEventLogger = logger;
    }

    @Override
    public boolean isAvailable() {
        if (mGridOptionSize < 0) {
            try {
                mGridOptionSize = sExecutorService.submit(() -> {
                    List<GridOption> gridOptions = mProvider.fetch(/* reload= */true);
                    return gridOptions == null ? 0 : gridOptions.size();
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.w(TAG, "could not get gridOptionSize", e);
            }
        }
        return mGridOptionSize > 1 && mProvider.areGridsAvailable();
    }

    @Override
    public void apply(GridOption option, Callback callback) {
        int updated = mProvider.applyGrid(option.name);
        if (updated == 1) {
            mEventLogger.logGridApplied(option);
            callback.onSuccess();
        } else {
            callback.onError(null);
        }
    }

    @Override
    public void preview(GridOption option) {
        mProvider.updateView();
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<GridOption> callback, boolean reload) {
        sExecutorService.submit(() -> {
            List<GridOption> gridOptions = mProvider.fetch(reload);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) {
                    if (gridOptions != null && !gridOptions.isEmpty()) {
                        callback.onOptionsLoaded(gridOptions);
                    } else {
                        callback.onError(null);
                    }
                }
            });
        });
    }

    /**
     * Returns an observable that receives a new value each time that the grid options are changed.
     */
    public LiveData<Object> getOptionChangeObservable(@Nullable Handler handler) {
        return mProvider.getOptionChangeObservable(handler);
    }

    /** Call through content provider API to render preview */
    public void renderPreview(Bundle bundle, String gridName,
            PreviewUtils.WorkspacePreviewCallback callback) {
        mProvider.renderPreview(gridName, bundle, callback);
    }
}
