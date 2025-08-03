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

package com.android.performance.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BaseEmulatorPreparer;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.util.RunUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** A performance test that does repeated emulator launches and measures timings. */
public class EmulatorStartupPerfTest extends BaseEmulatorPerfTest {

    public static class EmulatorLauncher extends BaseEmulatorPreparer {

        /**
         * Launches emulator with args provided via Configuration.
         *
         * <p>We want to launch emulator directly rather than going through
         * DeviceManager#launchEmulator in order to measure timing accurately: DeviceManager
         * performs several time consuming steps before returning
         */
        public void launchEmulator(ITestDevice device) throws IOException {
            List<String> args = buildEmulatorLaunchArgs();

            args.add("-read-only");
            String port = device.getSerialNumber().replace("emulator-", "");
            args.add("-port");
            args.add(port);

            RunUtil runUtil = buildRunUtilForEmulatorLaunch();

            Process p = runUtil.runCmdInBackground(args);
            ((IManagedTestDevice) device).setEmulatorProcess(p);
        }
    }

    @Override
    protected void performIteration(
            TestInformation testInfo,
            BaseEmulatorPreparer baseEmulatorPreparer,
            AndroidJUnitTest delegateTest,
            Path apkPath,
            DataRecorder timingsRecorder,
            ITestInvocationListener listener)
            throws Exception {

        EmulatorLauncher emulatorLauncher = (EmulatorLauncher) baseEmulatorPreparer;

        ITestDevice device = testInfo.getDevice();
        long startTimeMs = System.currentTimeMillis();
        timingsRecorder.captureTime(
                "online_time",
                () -> {
                    emulatorLauncher.launchEmulator(device);
                    device.waitForDeviceOnline(1 * 60 * 1000);
                    return null;
                });

        timingsRecorder.captureTime(
                "boot_time",
                () -> {
                    waitForBootComplete(device, startTimeMs + 3 * 60 * 1000);
                    return null;
                });

        timingsRecorder.captureTime(
                "install_time",
                () -> {
                    // direcly install package instead of using
                    // ITestDevice.installPackage
                    // to avoid overhead of the expensive aapt parsing checks it
                    // does every time
                    String result = device.executeAdbCommand("install", apkPath.toString());
                    LogUtil.CLog.i("Install returned " + result);
                    return null;
                });

        timingsRecorder.captureTime(
                "test_time",
                () -> {
                    delegateTest.run(testInfo, listener);
                    return null;
                });

        timingsRecorder.recordMetric("total_time", System.currentTimeMillis() - startTimeMs);

        LogUtil.CLog.i("Metrics: %s", timingsRecorder.toString());
    }

    private void waitForBootComplete(ITestDevice device, long quitAfterTime)
            throws DeviceNotAvailableException {
        // we don't want to use  waitForDeviceAvailable, as that has a 3 second sleep
        // so directly query for boot complete
        while (System.currentTimeMillis() < quitAfterTime) {
            String result = device.executeShellCommand("getprop dev.bootcomplete");
            if (result.trim().equals("1")) {
                return;
            }
            RunUtil.getDefault().sleep(50);
        }
    }
}
