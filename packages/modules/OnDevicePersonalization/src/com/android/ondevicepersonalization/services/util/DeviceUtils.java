/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.util;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

/** Device properties. */
public class DeviceUtils {
    /** Returns true if the device supports ODP. */
    public static boolean isOdpSupported(@NonNull Context context) {
        final PackageManager pm = context.getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                // Android TV
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                // Android Go
                && !pm.hasSystemFeature(PackageManager.FEATURE_RAM_LOW);
    }

    private DeviceUtils() {}
}
