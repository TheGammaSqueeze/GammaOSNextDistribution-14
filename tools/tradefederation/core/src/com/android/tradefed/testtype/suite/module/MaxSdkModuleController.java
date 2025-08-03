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
package com.android.tradefed.testtype.suite.module;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Base class for a module controller to not run tests on versions above a specified SDK version
 * number.
 */
public class MaxSdkModuleController extends BaseModuleController {

    private static final String SDK_VERSION_PROP = "ro.build.version.sdk";

    @Option(name = "max-sdk-level", description = "The maximum sdk-level on which tests will run.")
    private int mMaxSdkVersion;

    @Override
    public RunStrategy shouldRun(IInvocationContext context) throws DeviceNotAvailableException {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            String sdkVersionString = device.getProperty(SDK_VERSION_PROP);
            int sdkVersion = Integer.MAX_VALUE;
            try {
                if (sdkVersionString != null) {
                    sdkVersion = Integer.parseInt(sdkVersionString);
                } else {
                    CLog.d("SDK version is null");
                }
            } catch (NumberFormatException e) {
                CLog.d("Error parsing system property " + SDK_VERSION_PROP + ": " + e.getMessage());
            }
            if (sdkVersion <= mMaxSdkVersion) {
                continue;
            }
            CLog.d(
                    "Skipping module %s because SDK version %d is > " + mMaxSdkVersion + ".",
                    getModuleName(),
                    sdkVersion);
            return RunStrategy.FULL_MODULE_BYPASS;
        }
        return RunStrategy.RUN;
    }
}
