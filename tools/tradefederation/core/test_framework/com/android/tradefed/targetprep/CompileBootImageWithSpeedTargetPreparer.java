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
package com.android.tradefed.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.TestInformation;

import com.google.common.annotations.VisibleForTesting;

/** Recompiles the boot classpath and system server with the compiler filter 'speed'. */
@OptionClass(alias = "boot-image-speed-preparer")
public final class CompileBootImageWithSpeedTargetPreparer extends BaseTargetPreparer {

    @Option(
            name = "compile-boot-image-speed",
            description = "Compile the boot image in speed, not speed-profile")
    private boolean mEnableBootImageSpeed = true;

    @VisibleForTesting
    static final String COMPILE_BOOT_IMAGE_SPEED_CMD =
            "odrefresh --system-server-compiler-filter=speed "
                    + "--boot-image-compiler-filter=speed --force-compile";

    // Since speed-profile is the default, simply re-compile the boot image.
    @VisibleForTesting
    static final String COMPILE_BOOT_IMAGE_SPEED_PROFILE_CMD = "odrefresh --force-compile";

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        // Ignore setUp if it's a stub device, since there is no real device to set up.
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }
        device.executeShellCommand("stop");
        device.executeShellCommand("setprop dev.bootcomplete 0");
        if (mEnableBootImageSpeed) {
            device.executeShellCommand(COMPILE_BOOT_IMAGE_SPEED_CMD);
        } else {
            device.executeShellCommand(COMPILE_BOOT_IMAGE_SPEED_PROFILE_CMD);
        }
        device.executeShellCommand("start");
        device.waitForDeviceAvailable();
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        // Ignore tearDown if it's a stub device, since there is no real device to clean.
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }
    }
}
