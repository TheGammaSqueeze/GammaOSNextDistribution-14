/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.transition.Slide;
import android.view.Window;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment.AppbarFragmentHost;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.DisplayUtils;

/**
 * Activity that displays a full preview of a specific wallpaper and provides the ability to set the
 * wallpaper as the user's current wallpaper.
 */
public class FullPreviewActivity extends BasePreviewActivity implements AppbarFragmentHost {

    /**
     * Returns a new Intent with the provided WallpaperInfo instance put as an extra.
     */
    public static Intent newIntent(Context packageContext, WallpaperInfo wallpaperInfo) {
        Intent intent = new Intent(packageContext, FullPreviewActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(EXTRA_WALLPAPER_INFO, wallpaperInfo);
        return intent;
    }

    /**
     * Returns a new Intent with the provided WallpaperInfo instance and view as home variable
     * put as an extra.
     */
    public static Intent newIntent(Context packageContext, WallpaperInfo wallpaperInfo,
            boolean viewAsHome, boolean isAssetIdPresent) {
        return newIntent(packageContext, wallpaperInfo).putExtra(EXTRA_VIEW_AS_HOME, viewAsHome)
                .putExtra(IS_ASSET_ID_PRESENT, isAssetIdPresent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setAllowEnterTransitionOverlap(true);
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        getWindow().setExitTransition(new Slide());
        getWindow().setEnterTransition(new Slide());
        setContentView(R.layout.activity_fullscreen_preview);

        enableFullScreen();

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            Intent intent = getIntent();
            WallpaperInfo wallpaper = intent.getParcelableExtra(EXTRA_WALLPAPER_INFO);
            boolean viewAsHome = intent.getBooleanExtra(EXTRA_VIEW_AS_HOME, false);
            boolean isAssetIDPresent = intent.getBooleanExtra(IS_ASSET_ID_PRESENT, false);
            fragment = InjectorProvider.getInjector().getPreviewFragment(
                    /* context= */ this,
                    wallpaper,
                    viewAsHome,
                    isAssetIDPresent,
                    /* isNewTask= */ true);
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
    protected void onResume() {
        super.onResume();
        DisplayUtils displayUtils = InjectorProvider.getInjector().getDisplayUtils(this);
        int orientation = displayUtils.isOnWallpaperDisplay(this)
                ? ActivityInfo.SCREEN_ORIENTATION_USER : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setRequestedOrientation(orientation);
        if (isInMultiWindowMode()) {
            Toast.makeText(
                            this,
                            R.string.wallpaper_exit_split_screen,
                            Toast.LENGTH_SHORT
                    )
                    .show();
            onBackPressed();
        }
    }
}
