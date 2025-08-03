/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.art;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;
import android.os.SystemProperties;
import android.system.Os;

/**
 * A mockable wrapper class for device-specific constants.
 *
 * @hide
 */
public class Constants {
    private Constants() {}

    /** Returns the ABI that the device prefers. */
    @NonNull
    public static String getPreferredAbi() {
        return Build.SUPPORTED_ABIS[0];
    }

    /** Returns the 64 bit ABI that is native to the device. */
    @Nullable
    public static String getNative64BitAbi() {
        // The value comes from "ro.product.cpu.abilist64" and we assume that the first element is
        // the native one.
        return Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS[0] : null;
    }

    /** Returns the 32 bit ABI that is native to the device. */
    @Nullable
    public static String getNative32BitAbi() {
        // The value comes from "ro.product.cpu.abilist32" and we assume that the first element is
        // the native one.
        return Build.SUPPORTED_32_BIT_ABIS.length > 0 ? Build.SUPPORTED_32_BIT_ABIS[0] : null;
    }

    @Nullable
    public static String getenv(@NonNull String name) {
        return Os.getenv(name);
    }

    public static boolean isBootImageProfilingEnabled() {
        boolean profileBootClassPath = SystemProperties.getBoolean(
                "persist.device_config.runtime_native_boot.profilebootclasspath",
                SystemProperties.getBoolean("dalvik.vm.profilebootclasspath", false /* def */));
        return Build.isDebuggable() && profileBootClassPath;
    }
}
