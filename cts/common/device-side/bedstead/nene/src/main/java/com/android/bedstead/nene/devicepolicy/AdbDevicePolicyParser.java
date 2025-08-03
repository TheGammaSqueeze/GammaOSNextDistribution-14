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

package com.android.bedstead.nene.devicepolicy;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.users.UserReference;

import java.util.Map;

/** Parser for `adb dumpsys device_policy`. */
@TargetApi(Build.VERSION_CODES.O_MR1)
public interface AdbDevicePolicyParser {

    /**
     * Get the {@link AdbDevicePolicyParser} for the given version.
     */
    static AdbDevicePolicyParser get(int sdkVersion) {
        return new AdbDevicePolicyParser27();
    }

    /**
     * The result of parsing.
     *
     * <p>Values which are not used on the current version of Android will be {@code null}.
     */
    class ParseResult {
        Map<UserReference, ProfileOwner> mProfileOwners;
        DeviceOwner mDeviceOwner;
    }

    /**
     * Parse the device policy output.
     */
    ParseResult parse(String dumpsysDevicePolicyOutput) throws AdbParseException;
}
