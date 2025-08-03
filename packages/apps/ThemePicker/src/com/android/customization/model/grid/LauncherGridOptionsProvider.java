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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.customization.model.ResourceConstants;
import com.android.themepicker.R;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.util.PreviewUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstracts the logic to retrieve available grid options from the current Launcher.
 */
public class LauncherGridOptionsProvider {

    private static final String LIST_OPTIONS = "list_options";
    private static final String PREVIEW = "preview";
    private static final String DEFAULT_GRID = "default_grid";

    private static final String COL_NAME = "name";
    private static final String COL_ROWS = "rows";
    private static final String COL_COLS = "cols";
    private static final String COL_PREVIEW_COUNT = "preview_count";
    private static final String COL_IS_DEFAULT = "is_default";

    private static final String METADATA_KEY_PREVIEW_VERSION = "preview_version";

    private final Context mContext;
    private final PreviewUtils mPreviewUtils;
    private final boolean mIsGridApplyButtonEnabled;
    private List<GridOption> mOptions;
    private OptionChangeLiveData mLiveData;

    public LauncherGridOptionsProvider(Context context, String authorityMetadataKey) {
        mPreviewUtils = new PreviewUtils(context, authorityMetadataKey);
        mContext = context;
        mIsGridApplyButtonEnabled = BaseFlags.get().isGridApplyButtonEnabled(context);
    }

    boolean areGridsAvailable() {
        return mPreviewUtils.supportsPreview();
    }

    /**
     * Retrieve the available grids.
     * @param reload whether to reload grid options if they're cached.
     */
    @WorkerThread
    @Nullable
    List<GridOption> fetch(boolean reload) {
        if (!areGridsAvailable()) {
            return null;
        }
        if (mOptions != null && !reload) {
            return mOptions;
        }
        ContentResolver resolver = mContext.getContentResolver();
        String iconPath = mContext.getResources().getString(Resources.getSystem().getIdentifier(
                ResourceConstants.CONFIG_ICON_MASK, "string", ResourceConstants.ANDROID_PACKAGE));
        try (Cursor c = resolver.query(mPreviewUtils.getUri(LIST_OPTIONS), null, null, null,
                null)) {
            mOptions = new ArrayList<>();
            while(c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(COL_NAME));
                int rows = c.getInt(c.getColumnIndex(COL_ROWS));
                int cols = c.getInt(c.getColumnIndex(COL_COLS));
                int previewCount = c.getInt(c.getColumnIndex(COL_PREVIEW_COUNT));
                boolean isSet = Boolean.parseBoolean(c.getString(c.getColumnIndex(COL_IS_DEFAULT)));
                String title = mContext.getString(R.string.grid_title_pattern, cols, rows);
                mOptions.add(new GridOption(title, name, isSet, rows, cols,
                        mPreviewUtils.getUri(PREVIEW), previewCount, iconPath));
            }
        } catch (Exception e) {
            mOptions = null;
        }
        return mOptions;
    }

    /**
     * Request rendering of home screen preview via Launcher to Wallpaper using SurfaceView
     * @param name      the grid option name
     * @param bundle    surface view request bundle generated from
     *    {@link com.android.wallpaper.util.SurfaceViewUtils#createSurfaceViewRequest(SurfaceView)}.
     * @param callback To receive the result (will be called on the main thread)
     */
    void renderPreview(String name, Bundle bundle,
            PreviewUtils.WorkspacePreviewCallback callback) {
        bundle.putString("name", name);
        mPreviewUtils.renderPreview(bundle, callback);
    }

    void updateView() {
        mLiveData.postValue(new Object());
    }

    int applyGrid(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("enable_apply_button", mIsGridApplyButtonEnabled);
        return mContext.getContentResolver().update(mPreviewUtils.getUri(DEFAULT_GRID), values,
                null, null);
    }

    /**
     * Returns an observable that receives a new value each time that the grid options are changed.
     * Do not call if {@link #areGridsAvailable()} returns false
     */
    public LiveData<Object> getOptionChangeObservable(
            @Nullable Handler handler) {
        if (mLiveData == null) {
            mLiveData = new OptionChangeLiveData(
                    mContext, mPreviewUtils.getUri(DEFAULT_GRID), handler);
        }

        return mLiveData;
    }

    private static class OptionChangeLiveData extends MutableLiveData<Object> {

        private final ContentResolver mContentResolver;
        private final Uri mUri;
        private final ContentObserver mContentObserver;

        OptionChangeLiveData(
                Context context,
                Uri uri,
                @Nullable Handler handler) {
            mContentResolver = context.getContentResolver();
            mUri = uri;
            mContentObserver = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    // If grid apply button is enabled, user has previewed the grid before applying
                    // the grid change. Thus there is no need to preview again (which will cause a
                    // blank preview as launcher's is loader thread is busy reloading workspace)
                    // after applying grid change. Thus we should ignore ContentObserver#onChange
                    // from launcher
                    if (BaseFlags.get().isGridApplyButtonEnabled(context.getApplicationContext())) {
                        return;
                    }
                    postValue(new Object());
                }
            };
        }

        @Override
        protected void onActive() {
            mContentResolver.registerContentObserver(
                    mUri,
                    /* notifyForDescendants= */ true,
                    mContentObserver);
        }

        @Override
        protected void onInactive() {
            mContentResolver.unregisterContentObserver(mContentObserver);
        }
    }
}
