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

package com.android.adservices.service.common.compat;

import static com.android.adservices.AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/**
 * Utility class that contains methods associated with {@link android.app.job.JobService} that need
 * to be handled in a backward-compatible manner.
 */
public final class ServiceCompatUtils {
    private ServiceCompatUtils() {
        // Prevent instantiation
    }

    /**
     * Checks if the specified context is running within the ExtServices apex on a device running
     * Android T or later.
     *
     * @param context the context to use to retrieve the package name.
     * @return true if the device is running on Android T+ and the package name matches the
     *     AdServices package within the ExtServices apex.
     */
    public static boolean shouldDisableExtServicesJobOnTPlus(@NonNull Context context) {
        Objects.requireNonNull(context);

        // On S or lower, do not disable the job because it's the only one running.
        if (!SdkLevel.isAtLeastT()) {
            return false;
        }

        // On T+, check if the job is in the ExtServices apk. This indicates that it's a holdover
        // from an OTA from R/S, and should therefore be disabled.
        String packageName = context.getPackageName();
        return packageName != null && packageName.endsWith(ADEXTSERVICES_PACKAGE_NAME_SUFFIX);
    }
}
