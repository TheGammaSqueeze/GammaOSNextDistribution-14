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
package com.android.adservices.common;

import static android.provider.DeviceConfig.NAMESPACE_ADSERVICES;

import android.provider.DeviceConfig;
import android.util.Log;

/** Provides helper methods to deal with DeviceConfig. */
public final class DeviceConfigUtil {
    public static final String TAG = "DeviceConfigUtil";

    /** Sets the value of a "generic" device config flag. */
    public static boolean setDeviceConfigFlag(String namespace, String name, String value) {
        String logPrefix =
                "setDeviceConfigFlag(namespage="
                        + namespace
                        + ", name="
                        + name
                        + ", value="
                        + value
                        + ")";
        Log.d(TAG, logPrefix);

        boolean set = DeviceConfig.setProperty(namespace, name, value, /* makeDefault= */ false);
        if (!set) {
            Log.e(TAG, logPrefix + ": DeviceConfig call returned false");
        }
        return set;
    }

    /** Sets the value of {@code boolean} flag in the AdServices namespace. */
    public static boolean setAdservicesFlag(String name, boolean value) {
        return setDeviceConfigFlag(NAMESPACE_ADSERVICES, name, Boolean.toString(value));
    }

    /** Sets the value of {@code long} flag in the AdServices namespace. */
    public static boolean setAdservicesFlag(String name, long value) {
        return setDeviceConfigFlag(NAMESPACE_ADSERVICES, name, Long.toString(value));
    }

    private DeviceConfigUtil() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
