/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.settings.location;

import android.app.ActivityManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.UserHandle;

/**
 * Utility functions related to all location controllers.
 */
public class LocationUtil {
    private LocationUtil() {}

    /**
     * @return {@code true} if the current user is the driver and there are ADAS allow-listed
     * applications installed.
     */
    public static boolean isDriverWithAdasApps(Context context) {
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        boolean isDriver = ActivityManager.getCurrentUser() == UserHandle.myUserId();
        return isDriver && !locationManager.getAdasAllowlist().isEmpty();
    }
}
