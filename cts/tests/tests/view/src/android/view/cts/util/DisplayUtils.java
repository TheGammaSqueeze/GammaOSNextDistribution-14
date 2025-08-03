/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.cts.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/** Utilities class for querying display info. */
public class DisplayUtils {

    /** Whether the device supports orientation request from apps. */
    public static boolean supportOrientationRequest(final Activity activity) {
        final PackageManager pm = activity.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)
                && !isCloseToSquareDisplay(activity);
    }

    /** Checks whether the display dimension is close to square. */
    public static boolean isCloseToSquareDisplay(final Activity activity) {
        final Resources resources = activity.getResources();
        final float closeToSquareMaxAspectRatio;
        try {
            closeToSquareMaxAspectRatio = resources.getFloat(resources.getIdentifier(
                    "config_closeToSquareDisplayMaxAspectRatio", "dimen", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume device is not close to square.
            return false;
        }
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getDisplay().getRealMetrics(displayMetrics);
        final int w = displayMetrics.widthPixels;
        final int h = displayMetrics.heightPixels;
        final float aspectRatio = Math.max(w, h) / (float) Math.min(w, h);
        return aspectRatio <= closeToSquareMaxAspectRatio;
    }

    /** Whether the device is in portrait. */
    public static boolean isDevicePortrait(final Activity activity) {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getDisplay().getRealMetrics(displayMetrics);
        return displayMetrics.widthPixels < displayMetrics.heightPixels;
    }

    private DisplayUtils() {}
}
