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

package com.android.sts.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

/** Collection of utilities to help run commands on device via adb */
public final class CommandUtil {

    private CommandUtil() {}

    /**
     * Execute shell command on device, throws AssumptionError if command does not return 0.
     *
     * @param device the device to use
     * @param cmd the command to run
     * @return the result of device.executeShellV2Command
     */
    public static CommandResult runAndCheck(ITestDevice device, String cmd)
            throws DeviceNotAvailableException {
        return runAndCheck(device, cmd, 0);
    }

    /**
     * Execute shell command on device, throws AssumptionError if command does not return 0.
     *
     * @param device the device to use
     * @param cmd the command to run
     * @param retries the number of retries to attempt
     * @return the result of device.executeShellV2Command
     */
    public static CommandResult runAndCheck(ITestDevice device, String cmd, int retries)
            throws DeviceNotAvailableException {
        int attempt = 0;
        CommandResult res;

        do {
            attempt += 1;
            res = device.executeShellV2Command(cmd);
        } while (res.getStatus() != CommandStatus.SUCCESS && attempt <= retries);

        String failMsg =
                String.format(
                        "cmd failed: %s\ncode: %s\nstdout:\n%s\nstderr:\n%s",
                        cmd, res.getExitCode(), res.getStdout(), res.getStderr());
        assumeThat(failMsg, res.getStatus(), equalTo(CommandStatus.SUCCESS));
        return res;
    }
}
