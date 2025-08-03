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

package android.appsecurity.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Checks that there are no unexpected reboots during a test. Before each expected reboot call
 * {@link #increaseExpectedBootCountDifference(int)}
 */
public class BootCountTrackerRule implements TestRule {

    private final ITestInformationReceiver mTestInformationReceiver;
    private int mExpectedBootCountDifference;

    public BootCountTrackerRule(
            ITestInformationReceiver testInformationReceiver, int expectedBootCountDifference) {
        mTestInformationReceiver = testInformationReceiver;
        mExpectedBootCountDifference = expectedBootCountDifference;

    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                int preTestBootCount = getBootCount();
                try {
                    base.evaluate();
                } catch (Exception error) {
                    int postTestBootCount = getBootCount();
                    int bootDifference = postTestBootCount - preTestBootCount;
                    if (preTestBootCount >= 0 && postTestBootCount >= 0
                            && bootDifference > mExpectedBootCountDifference) {
                        throw new AssertionError(
                                "Boot-count increased more than expected at time of failure. "
                                        + "Expected: "
                                        + mExpectedBootCountDifference + " Actual: "
                                        + bootDifference
                                        + " An unexpected reboot may be the cause of failure:",
                                error);
                    }
                    throw error;
                }
            }
        };
    }

    public void increaseExpectedBootCountDifference(int increment) {
        mExpectedBootCountDifference += increment;
    }

    private ITestDevice getDevice() {
        return mTestInformationReceiver.getTestInformation().getDevice();
    }

    /** Parses the boot count global setting. Returns -1 if it could not be parsed. */
    private int getBootCount() throws DeviceNotAvailableException {
        CommandResult result = getDevice().executeShellV2Command("settings get global boot_count");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.w("Failed to get boot count. Status: %s, Exit code: %d, Error: %s",
                    result.getStatus(), result.getExitCode(), result.getStderr());
            return -1;
        }
        try {
            return Integer.parseInt(result.getStdout().trim());
        } catch (NumberFormatException e) {
            CLog.w("Couldn't parse boot count.", e);
            return -1;
        }

    }
}
