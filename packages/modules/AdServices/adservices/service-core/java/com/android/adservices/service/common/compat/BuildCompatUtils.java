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

import android.os.Build;
import android.os.SystemProperties;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

/** Utility class for compatibility of Build APIs with Android S and earlier. */
public final class BuildCompatUtils {
    private BuildCompatUtils() {
        // Prevent instantiation
    }

    /**
     * Invoke the {@code Build.isDebuggable} API on Android S or higher, and re-implement the same
     * functionality on Android R
     *
     * @return {@code true} if the device is running a debuggable build such as "userdebug" or
     *     "eng".
     */
    public static boolean isDebuggable() {
        return SdkLevel.isAtLeastS() ? Build.isDebuggable() : computeIsDebuggable_R();
    }

    @VisibleForTesting
    static boolean computeIsDebuggable_R() {
        // Build.isDebuggable was added in S; duplicate that functionality for R.
        return SystemProperties.getInt("ro.debuggable", 0) == 1;
    }
}
