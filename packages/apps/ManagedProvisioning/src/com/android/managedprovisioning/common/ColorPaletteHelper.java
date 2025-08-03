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

package com.android.managedprovisioning.common;

import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_ACCENT;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_BACKGROUND_SURFACE;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_IS_NIGHT_MODE_ACTIVE;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_NAVIGATION_BAR_COLOR;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_NAVIGATION_BAR_DIVIDER_COLOR;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_NOTIFICATION_BACKGROUND;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_PRIMARY_TEXT;
import static com.android.managedprovisioning.provisioning.Constants.COLOR_TYPE_SECONDARY_TEXT;

import android.content.Context;

import com.android.managedprovisioning.provisioning.Constants.ColorType;

import java.util.HashMap;

/**
 * A helper that generates a color palette map.
 */
public class ColorPaletteHelper {

    /**
     * Returns a {@link HashMap} that maps {@link ColorType} to {@link Integer} colors corresponding
     * to the app theme.
     */
    public HashMap<Integer, Integer> createColorPaletteMap(
            Context context,
            ManagedProvisioningSharedPreferences sharedPreferences) {
        HashMap<Integer, Integer> result = new HashMap<>();
        result.put(COLOR_TYPE_ACCENT, sharedPreferences.getAccentColor());
        result.put(COLOR_TYPE_PRIMARY_TEXT, sharedPreferences.getTextPrimaryColor());
        result.put(COLOR_TYPE_SECONDARY_TEXT, sharedPreferences.getTextSecondaryColor());
        result.put(COLOR_TYPE_BACKGROUND_SURFACE, sharedPreferences.getBackgroundColor());
        result.put(COLOR_TYPE_NOTIFICATION_BACKGROUND,
              sharedPreferences.getNotificationBackgroundColor());
        result.put(COLOR_TYPE_NAVIGATION_BAR_COLOR, sharedPreferences.getNavigationBarColor());
        result.put(COLOR_TYPE_NAVIGATION_BAR_DIVIDER_COLOR,
                sharedPreferences.getNavigationBarDividerColor());
        result.put(COLOR_TYPE_IS_NIGHT_MODE_ACTIVE,
                context.getResources().getConfiguration().isNightModeActive() ? 1 : 0);
        return result;
    }
}
