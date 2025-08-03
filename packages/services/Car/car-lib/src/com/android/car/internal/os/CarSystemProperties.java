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

package com.android.car.internal.os;

import android.annotation.Nullable;
import android.os.SystemProperties;

import java.util.Optional;

/**
 * Replacement for {@code android.sysprop.CarProperties}. This should be manually updated.
 *
 * @hide
 */
public final class CarSystemProperties {
    private static final String PROP_BOOT_USER_OVERRIDE_ID =
            "android.car.systemuser.bootuseroverrideid";
    private static final String PROP_USER_HAL_ENABLED = "android.car.user_hal_enabled";
    private static final String PROP_USER_HAL_TIMEOUT = "android.car.user_hal_timeout";
    private static final String PROP_DEVICE_POLICY_MANAGER_TIMEOUT =
            "android.car.device_policy_manager_timeout";

    private CarSystemProperties() {
        throw new UnsupportedOperationException();
    }

    /** Check {@code system/libsysprop/srcs/android/sysprop/CarProperties.sysprop} */
    public static Optional<Integer> getBootUserOverrideId() {
        return Optional.ofNullable(tryParseInteger(SystemProperties.get(
                PROP_BOOT_USER_OVERRIDE_ID)));
    }

    /** Check {@code system/libsysprop/srcs/android/sysprop/CarProperties.sysprop} */
    public static Optional<Boolean> getUserHalEnabled() {
        return Optional.ofNullable(Boolean.valueOf(SystemProperties.get(PROP_USER_HAL_ENABLED)));
    }

    /** Check {@code system/libsysprop/srcs/android/sysprop/CarProperties.sysprop} */
    public static Optional<Integer> getUserHalTimeout() {
        return Optional.ofNullable(tryParseInteger(SystemProperties.get(PROP_USER_HAL_TIMEOUT)));
    }

    /** Check {@code system/libsysprop/srcs/android/sysprop/CarProperties.sysprop} */
    public static Optional<Integer> getDevicePolicyManagerTimeout() {
        return Optional.ofNullable(tryParseInteger(SystemProperties.get(
                PROP_DEVICE_POLICY_MANAGER_TIMEOUT)));
    }

    @Nullable
    private static Integer tryParseInteger(String str) {
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
