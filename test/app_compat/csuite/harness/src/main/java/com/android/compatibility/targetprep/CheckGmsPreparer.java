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

package com.android.compatibility.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;

import com.google.common.annotations.VisibleForTesting;

/**
 * Checks and recover GMS on a device.
 *
 * <p>This preparer checks whether the GMS process is running during setUp and tearDown. If GMS is
 * not running before the test, a reboot will be attempted to recover.
 */
public final class CheckGmsPreparer implements ITargetPreparer {

    private static final long WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS = 1000 * 60;
    @VisibleForTesting static final String CHECK_GMS_COMMAND = "pidof com.google.android.gms";
    @VisibleForTesting static final String OPTION_ENABLE = "enable";

    @Option(name = OPTION_ENABLE, description = "Enable GMS checks.")
    protected boolean mEnable = false;

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (!mEnable || isGmsRunning(testInfo)) {
            return;
        }

        CLog.e("Did not detect a running GMS process, rebooting device to recover");
        testInfo.getDevice().reboot();
        testInfo.getDevice().waitForBootComplete(WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS);

        if (!isGmsRunning(testInfo)) {
            CLog.e("GMS process still not running, throwing error");
            mEnable = false;
            throw new TargetSetupError(
                    "GMS required but did not detect a running GMS process after device reboot",
                    testInfo.getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    private static boolean isGmsRunning(TestInformation testInfo)
            throws DeviceNotAvailableException {
        CommandResult res = testInfo.getDevice().executeShellV2Command(CHECK_GMS_COMMAND);
        if (res.getExitCode() == 0) {
            CLog.d("Detected a running GMS process with PID: %s", res.getStdout());
            return true;
        }

        CLog.e(
                "Check GMS command returned non zero exit code. Command: %s, Result: %s",
                CHECK_GMS_COMMAND, res.toString());
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (!mEnable || isGmsRunning(testInfo)) {
            return;
        }

        CLog.e("Did not detect a running GMS process on tearDown");
    }
}
