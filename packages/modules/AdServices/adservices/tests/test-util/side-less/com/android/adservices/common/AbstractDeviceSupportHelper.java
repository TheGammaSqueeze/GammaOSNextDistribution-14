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
package com.android.adservices.common;

import com.android.adservices.common.Logger.RealLogger;

import java.util.Objects;

// TODO(b/295321663): add unit tests

/** Helper to check if AdServices is supported / enabled in a device. */
abstract class AbstractDeviceSupportHelper {

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    // TODO(b/295321663): 3 constants below should be static imported from AdServicesCommonConstants
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_DEVICE =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "supported";
    // TODO(b/297408848): rename to AdServicesLite something
    private static final String SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "low_ram_device";

    // Used only for Go device checks, which rely on checking GMS Core and Play Store.
    public static final String GMS_CORE_PACKAGE = "com.google.android.gms";
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";

    protected final Logger mLog;
    private final SystemPropertiesHelper.Interface mSystemProperties;

    protected AbstractDeviceSupportHelper(
            RealLogger logger, SystemPropertiesHelper.Interface systemProperties) {
        mLog = new Logger(Objects.requireNonNull(logger), getClass());
        mSystemProperties = Objects.requireNonNull(systemProperties);
    }

    /** Checks whether AdServices is supported by the device / form factor. */
    public final boolean isDeviceSupported() {
        if (isDebuggable()) {
            String overriddenValue =
                    mSystemProperties.get(SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_DEVICE);
            if (isNotEmpty(overriddenValue)) {
                boolean supported = Boolean.valueOf(overriddenValue);
                mLog.i(
                        "isDeviceSupported(): returning %b as defined by system property %s (%s)",
                        supported, SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_DEVICE, overriddenValue);
                return supported;
            }
        }

        boolean supported = isDeviceSupportedByDefault();
        mLog.v("isDeviceSupported(): returning hardcoded value (%b)", supported);
        return supported;
    }

    public final boolean isGoDevice() {
        return isLowRamDevice() && isPhone() && hasGmsCore() && hasPlayStore();
    }

    // TODO(b/297408848): rename to isAdservicesLiteDevice() or something like that
    /** Checks whether the device has low ram. */
    public final boolean isLowRamDevice() {
        if (isDebuggable()) {
            String overriddenValue =
                    mSystemProperties.get(SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW);
            if (isNotEmpty(overriddenValue)) {
                boolean isLowRamDevice = Boolean.valueOf(overriddenValue);
                mLog.i(
                        "isLowRamDevice(): returning %b as defined by system property %s (%s)",
                        isLowRamDevice,
                        SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW,
                        overriddenValue);
                return isLowRamDevice;
            }
        }

        boolean isLowRamDevice = isLowRamDeviceByDefault();
        boolean isPhone = isPhone();
        boolean isIt = isPhone && isLowRamDevice;
        mLog.v(
                "isLowRamDevice(): returning non-simulated value %b when isPhone=%b and"
                        + " isLowRamDevice=%b",
                isIt, isPhone, isLowRamDevice);
        return isIt;
    }

    protected abstract boolean hasGmsCore();

    protected abstract boolean hasPlayStore();

    protected abstract boolean hasPackageManagerFeature(String feature);

    protected abstract boolean isLowRamDeviceByDefault();

    protected abstract boolean isDebuggable();

    private boolean isDeviceSupportedByDefault() {
        return isPhone() && !isGoDevice();
    }

    private boolean isPhone() {
        // TODO(b/284744130): need to figure out how to filter out tablets
        boolean isIt =
                !hasPackageManagerFeature(FEATURE_WATCH)
                        && !hasPackageManagerFeature(FEATURE_AUTOMOTIVE)
                        && !hasPackageManagerFeature(FEATURE_LEANBACK);
        mLog.v("isPhone(): returning %b", isIt);
        return isIt;
    }

    private static boolean isNotEmpty(String string) {
        return string != null && !string.isEmpty();
    }
}
