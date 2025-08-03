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
package com.android.wallpaper.picker;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.R;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.picker.AppbarFragment.AppbarFragmentHost;
import com.android.wallpaper.picker.preview.ui.WallpaperPreviewActivity;
import com.android.wallpaper.util.ActivityUtils;

/**
 * Activity that displays a view-only preview of a specific wallpaper.
 *
 * <p>TODO(b/298037335): Maybe reuse PreviewActivity and remove ViewOnlyPreviewActivity.
 */
public class ViewOnlyPreviewActivity extends BasePreviewActivity implements AppbarFragmentHost {

    /**
     * Returns a new Intent with the provided WallpaperInfo instance put as an extra.
     */
    public static Intent newIntent(Context context, WallpaperInfo wallpaper) {
        return new Intent(context, ViewOnlyPreviewActivity.class)
                .putExtra(EXTRA_WALLPAPER_INFO, wallpaper);
    }

    /**
     * Returns a new Intent with extra to start this activity.
     *
     * @param isVewAsHome true to preview home screen, otherwise preview lock screen.
     */
    public static Intent newIntent(Context context, WallpaperInfo wallpaper,
            boolean isVewAsHome, boolean isAssetIdPresent) {
        return newIntent(context, wallpaper).putExtra(EXTRA_VIEW_AS_HOME, isVewAsHome)
                .putExtra(IS_ASSET_ID_PRESENT, isAssetIdPresent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        enableFullScreen();

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            Intent intent = getIntent();
            WallpaperInfo wallpaper = intent.getParcelableExtra(EXTRA_WALLPAPER_INFO);
            boolean viewAsHome = intent.getBooleanExtra(EXTRA_VIEW_AS_HOME, true);
            boolean isAssetIdPresent = intent.getBooleanExtra(IS_ASSET_ID_PRESENT, true);
            fragment = InjectorProvider.getInjector().getPreviewFragment(
                    /* context */ this,
                    wallpaper,
                    viewAsHome,
                    isAssetIdPresent,
                    /* isNewTask= */ false);
            fm.beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    public void onUpArrowPressed() {
        onBackPressed();
    }

    @Override
    public boolean isUpArrowSupported() {
        return !ActivityUtils.isSUWMode(getBaseContext());
    }

    /**
     * Implementation that provides an intent to start a PreviewActivity.
     *
     * <p>Get singleton instance from [Injector] instead of creating new instance directly.
     */
    public static class ViewOnlyPreviewActivityIntentFactory implements InlinePreviewIntentFactory {
        private boolean mIsViewAsHome = false;

        @Override
        public Intent newIntent(Context context, WallpaperInfo wallpaper,
                boolean isAssetIdPresent) {
            Context appContext = context.getApplicationContext();
            LargeScreenMultiPanesChecker multiPanesChecker = new LargeScreenMultiPanesChecker();
            final boolean isMultiPanel = multiPanesChecker.isMultiPanesEnabled(appContext);
            final BaseFlags flags = InjectorProvider.getInjector().getFlags();
            if (flags.isMultiCropPreviewUiEnabled() && flags.isMultiCropEnabled()) {
                return WallpaperPreviewActivity.Companion.newIntent(appContext, wallpaper,
                        isAssetIdPresent, mIsViewAsHome, /* isNewTask= */ isMultiPanel);
            }

            // Launch a full preview activity for devices supporting multipanel mode
            if (isMultiPanel) {
                return FullPreviewActivity.newIntent(appContext, wallpaper, mIsViewAsHome,
                        isAssetIdPresent);
            }

            return ViewOnlyPreviewActivity.newIntent(appContext, wallpaper, mIsViewAsHome,
                    isAssetIdPresent);
        }

        @Override
        public void setViewAsHome(boolean isViewAsHome) {
            mIsViewAsHome = isViewAsHome;
        }
    }
}
