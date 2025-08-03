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
package com.android.wallpaper.util;

import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SETTINGS_SEARCH;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS;
import static com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS_HOMEPAGE;
import static com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.wallpaper.R;

/**
 * Various utilities pertaining to activities.
 */
public final class ActivityUtils {
    private static final int SUW_NOT_YET = 0;
    private static final int SUW_COMPLETE = 1;

    /**
     * Starts an activity with the given intent "safely" - i.e., catches exceptions that may occur
     * and displays a toast to the user in response to such issues.
     *
     * @param activity
     * @param intent
     * @param requestCode
     */
    public static void startActivityForResultSafely(
            Activity activity, Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.app_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, R.string.app_not_found, Toast.LENGTH_SHORT).show();
            Log.e("Wallpaper", "Wallpaper does not have the permission to launch " + intent
                    + ". Make sure to create a MAIN intent-filter for the corresponding activity "
                    + "or use the exported attribute for this activity.", e);
        }
    }

    /**
     * Returns true if wallpaper launch source is from Settings related.
     *
     * @param intent activity intent.
     */
    public static boolean isLaunchedFromSettingsRelated(Intent intent) {
        return isLaunchedFromSettings(intent) || isLaunchedFromSettingsSearch(intent);
    }

    /**
     * Checks if the Activity's launch source is from Settings' trampoline.
     *
     * @param intent intent to start the Activity
     * @return {@code true} if the Activity's launch source is from Settings' trampoline.
     */
    public static boolean isLaunchedFromSettingsTrampoline(Intent intent) {
        return isLaunchedFromSettingsHome(intent);
    }

    /**
     * Returns true if wallpaper launch source is from Settings.
     *
     * @param intent activity intent.
     */
    private static boolean isLaunchedFromSettings(Intent intent) {
        return (intent != null && TextUtils.equals(LAUNCH_SOURCE_SETTINGS,
                intent.getStringExtra(WALLPAPER_LAUNCH_SOURCE)));
    }

    private static boolean isLaunchedFromSettingsHome(Intent intent) {
        return (intent != null && intent.getBooleanExtra(LAUNCH_SOURCE_SETTINGS_HOMEPAGE, false));
    }

    /**
     * Returns true if wallpaper launch source is from Settings Search.
     *
     * @param intent activity intent.
     */
    public static boolean isLaunchedFromSettingsSearch(Intent intent) {
        return (intent != null && intent.hasExtra(LAUNCH_SETTINGS_SEARCH));
    }

    /**
     * Returns true if wallpaper is in SUW mode.
     *
     * @param context activity's context.
     */
    public static boolean isSUWMode(Context context) {
        return (Settings.Secure.getInt(
                context.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, SUW_COMPLETE)
                == SUW_NOT_YET);
    }

    /**
     * Returns true if it's wallpaper only mode.
     *
     * @param intent activity intent.
     */
    public static boolean isWallpaperOnlyMode(Intent intent) {
        return "wallpaper_only".equals(
                intent.getStringExtra("com.android.launcher3.WALLPAPER_FLAVOR"));
    }

    /**
     * Returns {@code true} if the activity was launched from the home screen (launcher);
     * {@code false} otherwise.
     */
    public static boolean isLaunchedFromLauncher(Intent intent) {
        return LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER.equals(
                intent.getStringExtra(WALLPAPER_LAUNCH_SOURCE));
    }
}