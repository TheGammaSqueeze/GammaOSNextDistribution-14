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

package com.android.sts.common.util;

import com.android.sts.common.CommandUtil;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;

/** Tools for comparing kernel versions */
public final class KernelVersionHost {

    private KernelVersionHost() {}

    /**
     * Get the device kernel version
     *
     * @param device The device to collect the kernel version from
     */
    public static KernelVersion getKernelVersion(ITestDevice device)
            throws DeviceNotAvailableException {
        // https://android.googlesource.com/platform/system/core/+/master/shell_and_utilities/README.md
        // uname is part of Android since 6.0 Marshmallow
        CommandResult res = CommandUtil.runAndCheck(device, "uname -r");
        return KernelVersion.parse(res.getStdout());
    }

    /**
     * Helper for BusinessLogic
     *
     * @param device The device to test against
     * @param testVersion The kernel version string for comparison. "version.patchlevel.sublevel"
     */
    public static boolean isKernelVersionEqualTo(ITestDevice device, String testVersion)
            throws DeviceNotAvailableException {
        KernelVersion deviceKernelVersion = getKernelVersion(device);
        KernelVersion testKernelVersion = KernelVersion.parse(testVersion);
        return deviceKernelVersion.compareTo(testKernelVersion) == 0;
    }

    /**
     * Helper for BusinessLogic
     *
     * @param device The device to test against
     * @param testVersion The kernel version string for comparison. "version.patchlevel.sublevel"
     */
    public static boolean isKernelVersionLessThan(ITestDevice device, String testVersion)
            throws DeviceNotAvailableException {
        KernelVersion deviceKernelVersion = getKernelVersion(device);
        KernelVersion testKernelVersion = KernelVersion.parse(testVersion);
        return deviceKernelVersion.compareTo(testKernelVersion) < 0;
    }

    /**
     * Helper for BusinessLogic
     *
     * @param device The device to test against
     * @param testVersion The kernel version string for comparison. "version.patchlevel.sublevel"
     */
    public static boolean isKernelVersionLessThanEqualTo(ITestDevice device, String testVersion)
            throws DeviceNotAvailableException {
        KernelVersion deviceKernelVersion = getKernelVersion(device);
        KernelVersion testKernelVersion = KernelVersion.parse(testVersion);
        return deviceKernelVersion.compareTo(testKernelVersion) <= 0;
    }

    /**
     * Helper for BusinessLogic
     *
     * @param device The device to test against
     * @param testVersion The kernel version string for comparison. "version.patchlevel.sublevel"
     */
    public static boolean isKernelVersionGreaterThan(ITestDevice device, String testVersion)
            throws DeviceNotAvailableException {
        KernelVersion deviceKernelVersion = getKernelVersion(device);
        KernelVersion testKernelVersion = KernelVersion.parse(testVersion);
        return deviceKernelVersion.compareTo(testKernelVersion) > 0;
    }

    /**
     * Helper for BusinessLogic
     *
     * @param device The device to test against
     * @param testVersion The kernel version string for comparison. "version.patchlevel.sublevel"
     */
    public static boolean isKernelVersionGreaterThanEqualTo(ITestDevice device, String testVersion)
            throws DeviceNotAvailableException {
        KernelVersion deviceKernelVersion = getKernelVersion(device);
        KernelVersion testKernelVersion = KernelVersion.parse(testVersion);
        return deviceKernelVersion.compareTo(testKernelVersion) >= 0;
    }
}
