/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.RunUtil;

import java.time.Duration;
import java.util.List;

/**
 * A TargetPreparer that launches an emulator locally from an android build environment.
 *
 * <p>Its highly recommended to prepare an emulator snapshot (eg tradefed run
 * emulator/generate-snapshot) before running any configs that includes this.
 */
public class LocalEmulatorLaunch extends BaseEmulatorPreparer {

    @Option(name = "boot-timeout", description = "maximum duration to wait for emulator to boot")
    private Duration mBootTimeout = Duration.ofSeconds(30);

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice allocatedDevice = testInformation.getDevice();
        IDeviceManager manager = GlobalConfiguration.getDeviceManagerInstance();
        List<String> args = buildEmulatorLaunchArgs();
        args.add("-read-only");
        RunUtil runUtil = buildRunUtilForEmulatorLaunch();

        manager.launchEmulator(allocatedDevice, mBootTimeout.toMillis(), runUtil, args);
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        // device manager should automatically free/kill the launched emulator
    }
}
