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

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BaseEmulatorPreparer;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AaptParser;

import com.google.common.base.Preconditions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A base call for emulator performance tests that does repeated emulator launches, and runs
 * instrumentation tests
 */
public abstract class BaseEmulatorPerfTest implements IRemoteTest, IConfigurationReceiver {
    @Option(name = "iterations", description = "number of launch iterations to perform")
    private int mIterations = 1;

    @Option(name = "test-apk-name", description = "File name of test apk install", mandatory = true)
    private String mInstallApkName;

    @Option(name = "test-dir-path", description = "File system path to test apks")
    private File mTestDirPath;

    @Option(
            name = "emulator-metrics-key",
            description = "The test run name to use to report metrics",
            mandatory = true)
    private String mRunName;

    private IConfiguration mConfig;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        Preconditions.checkArgument(testInfo.getDevice().getIDevice().isEmulator());
        Preconditions.checkArgument(
                testInfo.getDevice().getDeviceState() == TestDeviceState.NOT_AVAILABLE);
        Preconditions.checkNotNull(mTestDirPath, "--test-dir-path was not set");
        Path apkPath = mTestDirPath.toPath().resolve(mInstallApkName);
        Preconditions.checkArgument(Files.exists(apkPath), apkPath.toString() + " does not exist");

        // Pull the objects to perform the emulator launch amd run tests
        // They are stored in config in order to support receiving Options.
        BaseEmulatorPreparer emulatorLauncher =
                (BaseEmulatorPreparer) mConfig.getConfigurationObject("emulator_launcher");

        AndroidJUnitTest delegateTest =
                (AndroidJUnitTest) mConfig.getConfigurationObject("delegate_test");
        delegateTest.setDevice(testInfo.getDevice());
        // auto find package name of test apk
        AaptParser parser = AaptParser.parse(apkPath.toFile());
        delegateTest.setPackageName(parser.getPackageName());

        DataRecorder dataRecorder = new DataRecorder(mRunName);

        try {
            for (int i = 1; i <= mIterations; i++) {
                LogUtil.CLog.i("Performing %d iteration of %d", i, mIterations);

                performIteration(
                        testInfo, emulatorLauncher, delegateTest, apkPath, dataRecorder, listener);

                // let tradefed kill emulator for last iteration
                if (i < mIterations) {
                    GlobalConfiguration.getDeviceManagerInstance()
                            .killEmulator(testInfo.getDevice());
                }
            }

            Map<String, String> buildMetrics = getBuildMetrics(testInfo);
            dataRecorder.reportMetrics(listener, buildMetrics);

        } catch (Exception e) {
            LogUtil.CLog.e(e);
            listener.invocationFailed(e);
        }
    }

    private Map<String, String> getBuildMetrics(TestInformation testInfo) {
        Map<String, String> metrics = new HashMap<>();
        // add 'emulator_build_id' to data to report if it exists
        String emuBuildId = testInfo.getBuildInfo().getBuildAttributes().get("emulator-build-id");
        if (emuBuildId != null) {
            metrics.put("emulator_build_id", emuBuildId);
        }
        return metrics;
    }

    protected abstract void performIteration(
            TestInformation device,
            BaseEmulatorPreparer emulatorLauncher,
            AndroidJUnitTest delegateTest,
            Path apkPath,
            DataRecorder dataRecorder,
            ITestInvocationListener listener)
            throws Exception;
}
