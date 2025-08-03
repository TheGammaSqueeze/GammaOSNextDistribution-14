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

package com.android.tv.settings.library.overlay;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.tv.settings.library.util.ResourcesUtil;


/** An Util class that manages logic related to build flavor and feature. */
public final class FlavorUtils {

    private static final String TAG = "OverlayUtils";

    // Build flavors of TvSettings that determines
    public static final int FLAVOR_UNDEFINED = 0b00000000; // Error/undefined flavor
    public static final int FLAVOR_CLASSIC = 0b00000001;   // Ordinary classic one panel settings
    public static final int FLAVOR_TWO_PANEL = 0b00000010; // Two panel settings
    public static final int FLAVOR_X = 0b00000100;         // Two panel settings with the X overlay
    public static final int FLAVOR_VENDOR = 0b00001000;    // Two panel settings with Vendor overlay

    public static final int ALL_FLAVORS_MASK =
            FLAVOR_CLASSIC | FLAVOR_TWO_PANEL | FLAVOR_X | FLAVOR_VENDOR;
    public static final int TWO_PANEL_FLAVORS_MASK = FLAVOR_TWO_PANEL | FLAVOR_X | FLAVOR_VENDOR;
    public static final int X_EXPERIENCE_FLAVORS_MASK = FLAVOR_X | FLAVOR_VENDOR;
    public static final int NON_X_EXPERIENCE_FLAVORS_MASK =
            FLAVOR_UNDEFINED | FLAVOR_CLASSIC | FLAVOR_TWO_PANEL;

    private static FeatureFactory sFeatureFactory;

    /** Returns the flavor of current TvSettings. */
    public static int getFlavor(@Nullable Context context) {
        if (context == null) {
            Log.w(TAG, "Trying to get flavor from null context. Returning undefined flavor.");
            return FLAVOR_UNDEFINED;
        }
        String flavor = ResourcesUtil.getString(context, "config_tvSettingsFlavor");
        if (TextUtils.isEmpty(flavor)) {
            return FLAVOR_CLASSIC;
        }
        switch (flavor) {
            case "Classic":
                return FLAVOR_CLASSIC;
            case "TwoPanel":
                return FLAVOR_TWO_PANEL;
            case "X":
                return FLAVOR_X;
            case "Vendor":
                return FLAVOR_VENDOR;
            default:
                Log.w(TAG, "Flavor is unspecified. Default to Classic flavor.");
                return FLAVOR_CLASSIC;
        }
    }

    /** Returns whether the UI is two panel style. */
    public static boolean isTwoPanel(@Nullable Context context) {
        return (getFlavor(context) & TWO_PANEL_FLAVORS_MASK) != 0;
    }

    /** Returns the correct FeatureFactory. */
    public static FeatureFactory getFeatureFactory(@Nullable Context context) {
        if (sFeatureFactory != null) {
            return sFeatureFactory;
        }
        switch (getFlavor(context)) {
            case FLAVOR_TWO_PANEL:
                sFeatureFactory = new FeatureFactoryImplTwoPanel();
                break;
            case FLAVOR_X:
            case FLAVOR_VENDOR:
                sFeatureFactory = new FeatureFactoryImplX();
                break;
            case FLAVOR_CLASSIC:
            case FLAVOR_UNDEFINED:
            default:
                sFeatureFactory = new FeatureFactoryImpl();
        }
        return sFeatureFactory;
    }
}
