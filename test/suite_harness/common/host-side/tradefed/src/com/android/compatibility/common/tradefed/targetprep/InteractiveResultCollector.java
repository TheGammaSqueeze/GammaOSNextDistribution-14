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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ITargetPreparer} that attempts to pull any number of files from device path(s) to any
 * xTS report.
 *
 * <p>Should be performed *before* a new build is flashed, and *after* DeviceSetup is run (if
 * enabled).
 */
@OptionClass(alias = "interactive-result-collector")
public class InteractiveResultCollector extends BaseTargetPreparer {

    @Option(
            name = "host-path",
            description =
                    "The host-side relative path to the directory where results should be"
                        + " collected. If not specified, defaults to the 'screenshots' directory.")
    private String hostPath = "screenshots";

    @Option(
            name = "device-cleanup",
            description =
                    "Whether all files in the device folers should be cleaned up before test. "
                            + "Note that the preparer does not verify that files/directories have"
                            + "been deleted successfully.")
    private boolean mCleanup = true;

    @Option(
            name = "device-paths",
            description = "The list of paths to the files stored on the device.")
    private List<String> devicePaths = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice mDevice = testInfo.getDevice();
        if (!(testInfo.getBuildInfo() instanceof IDeviceBuildInfo)) {
            throw new TargetSetupError(
                    "Invalid buildInfo, expecting an IDeviceBuildInfo",
                    mDevice.getDeviceDescriptor(),
                    InfraErrorIdentifier.UNDETERMINED);
        }
        if (mCleanup && !devicePaths.isEmpty()) {
            for (String devicePath : devicePaths) {
                if (!devicePath.isEmpty()) {
                    CLog.d("Start clean up path: %s", devicePath);
                    mDevice.executeAdbCommand("shell", "rm", "-rf", devicePath);
                }
            }
        }
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (e != null && (e instanceof DeviceNotAvailableException)) {
            CLog.e("Invocation finished with DeviceNotAvailable, skip collecting results.");
            return;
        }

        File hostResultDir = null;
        if (!devicePaths.isEmpty()) {
            try {
                hostResultDir =
                        new File(
                                new CompatibilityBuildHelper(testInfo.getBuildInfo())
                                        .getResultDir(),
                                hostPath);
                if (!hostResultDir.exists()) {
                    hostResultDir.mkdir();
                }
            } catch (FileNotFoundException exception) {
                CLog.e(exception);
            }
        }
        if (hostResultDir == null) {
            // No host result directory, either no device paths, or fail to create it.
            return;
        }

        ITestDevice testDevice = testInfo.getDevice();
        for (String devicePath : devicePaths) {
            if (!devicePath.isEmpty()) {
                if (testDevice.pullDir(devicePath, hostResultDir)) {
                    CLog.d(
                            String.format(
                                    "Successfully pulled %s to %s.",
                                    devicePath, hostResultDir.getAbsolutePath()));
                }
            }
        }
    }
}
