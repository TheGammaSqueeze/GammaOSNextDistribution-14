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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.HashSet;
import java.util.Set;

/** Module controller to not run tests when the device has not got the given packages installed. */
public class PackageInstalledModuleController extends BaseModuleController {

    @Option(
            name = "required-package",
            description = "The packages that are required to run this module.")
    private Set<String> mPackages = new HashSet<>();

    @Override
    public RunStrategy shouldRun(IInvocationContext context) throws DeviceNotAvailableException {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            Set<String> installedPackageNames = device.getInstalledPackageNames();
            for (String packageName : mPackages) {
                if (!installedPackageNames.contains(packageName)) {
                    CLog.d(
                            "Skipping module %s because the device does not have package %s.",
                            getModuleName(), packageName);
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
            }
        }
        return RunStrategy.RUN;
    }
}
