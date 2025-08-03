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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.R;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.model.ImageWallpaperInfo;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.picker.AppbarFragment.AppbarFragmentHost;
import com.android.wallpaper.picker.preview.ui.WallpaperPreviewActivity;
import com.android.wallpaper.util.ActivityUtils;

/**
 * Activity that displays a preview of a specific wallpaper and provides the ability to set the
 * wallpaper as the user's current wallpaper.
 */
public class PreviewActivity extends BasePreviewActivity implements AppbarFragmentHost {
    public static final int RESULT_MY_PHOTOS = 0;

    /**
     * Returns a new Intent with the provided WallpaperInfo instance put as an extra.
     */
    public static Intent newIntent(Context packageContext, WallpaperInfo wallpaperInfo,
            boolean viewAsHome, boolean isAssetIdPresent) {
        Intent intent = new Intent(packageContext, PreviewActivity.class);
        intent.putExtra(EXTRA_WALLPAPER_INFO, wallpaperInfo);
        intent.putExtra(IS_ASSET_ID_PRESENT, isAssetIdPresent);
        intent.putExtra(EXTRA_VIEW_AS_HOME, viewAsHome);
        return intent;
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
            boolean viewAsHome = intent.getBooleanExtra(EXTRA_VIEW_AS_HOME, false);
            boolean isAssetIdPresent = intent.getBooleanExtra(IS_ASSET_ID_PRESENT,
                    false);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_MY_PHOTOS && resultCode == Activity.RESULT_OK) {
            Uri imageUri = (data == null) ? null : data.getData();
            if (imageUri != null) {
                ImageWallpaperInfo imageWallpaper = new ImageWallpaperInfo(imageUri);
                FragmentManager fm = getSupportFragmentManager();
                Fragment fragment = InjectorProvider.getInjector().getPreviewFragment(
                        /* context= */ this,
                        imageWallpaper,
                        /* viewAsHome= */ true,
                        /* isAssetIdPresent= */ true,
                        /* isNewTask= */ false);
                fm.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
            }
        }
    }

    /**
     * Implementation that provides an intent to start a PreviewActivity.
     *
     * <p>Get singleton instance from [Injector] instead of creating new instance directly.
     */
    public static class PreviewActivityIntentFactory implements InlinePreviewIntentFactory {
        private boolean mIsViewAsHome = false;

        @Override
        public Intent newIntent(Context context, WallpaperInfo wallpaper,
                boolean isAssetIdPresent) {
            Context appContext = context.getApplicationContext();
            final BaseFlags flags = InjectorProvider.getInjector().getFlags();
            LargeScreenMultiPanesChecker multiPanesChecker = new LargeScreenMultiPanesChecker();
            final boolean isMultiPanel = multiPanesChecker.isMultiPanesEnabled(appContext);

            if (flags.isMultiCropPreviewUiEnabled() && flags.isMultiCropEnabled()) {
                return WallpaperPreviewActivity.Companion.newIntent(appContext,
                        wallpaper, isAssetIdPresent, mIsViewAsHome, /* isNewTask= */ isMultiPanel);
            }

            // Launch a full preview activity for devices supporting multipanel mode
            if (isMultiPanel) {
                return FullPreviewActivity.newIntent(appContext, wallpaper, mIsViewAsHome,
                        isAssetIdPresent);
            }
            return PreviewActivity.newIntent(appContext, wallpaper, mIsViewAsHome,
                    isAssetIdPresent);
        }

        @Override
        public void setViewAsHome(boolean isViewAsHome) {
            mIsViewAsHome = isViewAsHome;
        }
    }
}
