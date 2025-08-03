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

import android.app.WallpaperColors;
import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.color.ColorUtils;
import com.android.wallpaper.module.InjectorProvider;

import com.google.android.material.color.MaterialColors;

/** Interface to help Fragment to preview wallpaper color theme. */
public interface WallpaperColorThemePreview {

    String TAG = "WallpaperColorThemePreview";

    /** */
    FragmentActivity getActivity();

    /**
     * Updates the color of status bar and navigation bar with the background color attribute
     * extracted from {@code context}.
     */
    default void updateSystemBarColor(Context context) {
        int colorPrimary = MaterialColors.getColor(context, android.R.attr.colorPrimary,
                "android.R.attr.colorPrimary is not set in the current theme");
        Window window = getActivity().getWindow();
        window.setStatusBarColor(colorPrimary);
        window.setNavigationBarColor(colorPrimary);
    }

    /** Updates the background color for the given {@code fragmentView}. */
    default void updateBackgroundColor(Context context, View fragmentView) {
        int colorPrimary = MaterialColors.getColor(context, android.R.attr.colorPrimary,
                "android.R.attr.colorPrimary is not set in the current theme");
        fragmentView.setBackgroundColor(colorPrimary);
    }

    /** Returns {@code true} if the fragment theme should apply the wallpaper colors. */
    default boolean shouldApplyWallpaperColors() {
        FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity == null || fragmentActivity.isFinishing()) {
            Log.w(TAG, "shouldApplyWallpaperColors: activity is null or finishing");
            return false;
        }

        if (!ColorUtils.isMonetEnabled(fragmentActivity)) {
            Log.w(TAG, "Monet is not enabled");
            return false;
        }
        // If user selects a preset color, we should never apply the color extracted from the
        // wallpaper preview.
        Context appContext = fragmentActivity.getApplicationContext();
        return !InjectorProvider.getInjector().isCurrentSelectedColorPreset(appContext);
    }

    /**
     * If needed, re-request a workspace preview from Launcher based on the updated WallpaperColors
     * (which can be {@code null} to use the default color scheme.
     */
    default void updateWorkspacePreview(SurfaceView workspaceSurface,
            WorkspaceSurfaceHolderCallback callback, @Nullable WallpaperColors colors) {
        updateWorkspacePreview(workspaceSurface, callback, colors, false);
    }

    /**
     * If needed, re-request a workspace preview from Launcher based on the updated WallpaperColors
     * (which can be {@code null} to use the default color scheme and the flag of whether to hide
     * workspace QSB.
     */
    default void updateWorkspacePreview(
            SurfaceView workspaceSurface,
            WorkspaceSurfaceHolderCallback callback,
            @Nullable WallpaperColors colors,
            boolean hideBottomRow) {
        if (!shouldApplyWallpaperColors()) {
            return;
        }
        // Adjust surface visibility to trigger a surfaceCreated event in the callback
        int previousVisibility = workspaceSurface.getVisibility();
        workspaceSurface.setVisibility(View.GONE);
        if (callback != null) {
            callback.cleanUp();
            callback.setHideBottomRow(hideBottomRow);
            callback.setWallpaperColors(colors);
            callback.maybeRenderPreview();
            workspaceSurface.setUseAlpha();
            workspaceSurface.setAlpha(0);
            callback.setListener(() -> {
                // Move one pixel to trigger a surface update, otherwise it won't pick up the
                // proper scale
                workspaceSurface.setTop(-1);
                workspaceSurface.animate().alpha(1f).setDuration(300).start();
            });
        }
        workspaceSurface.setVisibility(previousVisibility);
    }
}
