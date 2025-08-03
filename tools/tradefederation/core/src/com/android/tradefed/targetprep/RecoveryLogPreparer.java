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
package com.android.tradefed.targetprep;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import java.io.File;

/**
 * A target preparer used to collect logs before recovery.
 */
public class RecoveryLogPreparer extends BaseTargetPreparer implements ITestLoggerReceiver {

    private static final String RECOVERY_LOG_NAME = "recovery.log";
    private static final String RECOVERY_LOG = "/tmp/recovery.log";
    private ITestLogger mLogger;

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();

        if (TestDeviceState.RECOVERY.equals(device.getDeviceState())) {
            // Track when we entered the recovery mode handling
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.AUTO_RECOVERY_MODE_COUNT, 1);
            // Recovery mode root is a bit different
            String outputRoot = device.executeAdbCommand("root");
            CLog.d("Root output: %s", outputRoot);
            File recoveryLog = device.pullFile(RECOVERY_LOG);
            if (recoveryLog == null) {
                CLog.w("Failed to pull recovery.log file.");
            } else {
                try (InputStreamSource source = new FileInputStreamSource(recoveryLog, true)) {
                    mLogger.testLog(RECOVERY_LOG_NAME, LogDataType.RECOVERY_MODE_LOG, source);
                }
            }
            rebootDevice(testInformation);
        }
    }

    protected void rebootDevice(TestInformation testInformation)
            throws DeviceNotAvailableException {
        // Turn device into bootloader for the rest of recovery
        testInformation.getDevice().rebootIntoBootloader();
    }
}