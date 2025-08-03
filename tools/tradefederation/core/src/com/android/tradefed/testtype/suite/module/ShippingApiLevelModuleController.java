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
package com.android.tradefed.testtype.suite.module;

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Run tests if the device meets the following conditions:
 *
 * <ul>
 *   <li>If {@code min-api-level} is defined:
 *       <ul>
 *         <li>The device shipped with the {@code min-api-level} or later.
 *       </ul>
 *   <li>If {@code vsr-min-api-level} is defined:
 *       <ul>
 *         <li>The device shipped with the {@code vsr-min-api-level} or later.
 *         <li>The vendor image implemented the features for the {@code vsr-min-api-level} or later.
 *       </ul>
 * </ul>
 */
public class ShippingApiLevelModuleController extends BaseModuleController {

    private static final String SYSTEM_SHIPPING_API_LEVEL_PROP = "ro.product.first_api_level";
    private static final String SYSTEM_API_LEVEL_PROP = "ro.build.version.sdk";
    private static final String VENDOR_SHIPPING_API_LEVEL_PROP = "ro.board.first_api_level";
    private static final String VENDOR_API_LEVEL_PROP = "ro.board.api_level";
    private static final String VSR_VENDOR_API_LEVEL_PROP = "ro.vendor.api_level";
    private static final long VALUE_NOT_FOUND = -1;

    @Option(
            name = "min-api-level",
            description = "The minimum shipping api-level of the device on which tests will run.")
    private Integer mMinApiLevel = 0;

    @Option(
            name = "vsr-min-api-level",
            description = "The minimum vendor api-level of the device on which tests will run.")
    private Integer mMinVendorApiLevel = 0;

    /**
     * Compares the API level from the {@code minApiLevel} and decide if the test should run or not.
     *
     * @param device the {@link ITestDevice}.
     * @param apiLevelprops names of api level properties. This function compares the api level with
     *     the first available property.
     * @param minApiLevel the minimum api level on which the test will run.
     * @return {@code true} if the api level is equal to or greater than the {@code minApiLevel}.
     *     Otherwise, {@code false}.
     * @throws DeviceNotAvailableException
     */
    private boolean shouldRunTestWithApiLevels(
            ITestDevice device, String[] apiLevelprops, int minApiLevel)
            throws DeviceNotAvailableException {
        for (String prop : apiLevelprops) {
            long apiLevel = device.getIntProperty(prop, VALUE_NOT_FOUND);
            if (apiLevel == VALUE_NOT_FOUND) {
                continue;
            }
            if (apiLevel < minApiLevel) {
                CLog.d(
                        "Skipping module %s because API Level %d from %s is less than %d.",
                        getModuleName(), apiLevel, prop, minApiLevel);
                return false;
            }
            // Found the first available api level prop.
            // Return true as it is greater than or equal to the minApiLevel.
            return true;
        }
        return true;
    }

    /**
     * Method to decide if the module should run or not.
     *
     * @param context the {@link IInvocationContext} of the module
     * @return {@link RunStrategy#RUN} if the module should run, {@link
     *     RunStrategy#FULL_MODULE_BYPASS} otherwise.
     * @throws DeviceNotAvailableException if device is not available
     */
    @Override
    public RunStrategy shouldRun(IInvocationContext context) throws DeviceNotAvailableException {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            // Check system shipping api level against the min-api-level.
            // The base property to see the shipping api level of the device is the
            // "ro.product.first_api_level". If it is not defined, the current api level will be
            // read from the "ro.build.version.sdk"
            if (mMinApiLevel > 0) {
                if (!shouldRunTestWithApiLevels(
                        device,
                        new String[] {
                            SYSTEM_SHIPPING_API_LEVEL_PROP, SYSTEM_API_LEVEL_PROP,
                        },
                        mMinApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
            }

            if (mMinVendorApiLevel > 0) {
                if (mMinVendorApiLevel > 34 && mMinVendorApiLevel < 202404) {
                    throw new RuntimeException(
                            "vsr-min-api-level must have YYYYMM format if it has a value greater"
                                    + " than 34, but has "
                                    + mMinVendorApiLevel);
                }
                // All devices with Android T or newer defines "ro.vendor.api_level". Read this to
                // compare the API level with vsr-min-api-level.
                long vsrApiLevel =
                        device.getIntProperty(VSR_VENDOR_API_LEVEL_PROP, VALUE_NOT_FOUND);
                if (vsrApiLevel != VALUE_NOT_FOUND) {
                    if (vsrApiLevel < mMinVendorApiLevel) {
                        return RunStrategy.FULL_MODULE_BYPASS;
                    } else {
                        return RunStrategy.RUN;
                    }
                }
                // For older devices that do not have "ro.vendor.api_level", fallback to read each
                // api levels starting with product shipping api level.
                if (!shouldRunTestWithApiLevels(
                        device,
                        new String[] {
                            SYSTEM_SHIPPING_API_LEVEL_PROP, SYSTEM_API_LEVEL_PROP,
                        },
                        mMinVendorApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
                // And then, read "ro.board.api_level" and "ro.board.first_api_level".
                if (!shouldRunTestWithApiLevels(
                        device,
                        new String[] {
                            VENDOR_API_LEVEL_PROP, VENDOR_SHIPPING_API_LEVEL_PROP,
                        },
                        mMinVendorApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
            }
        }
        return RunStrategy.RUN;
    }
}
