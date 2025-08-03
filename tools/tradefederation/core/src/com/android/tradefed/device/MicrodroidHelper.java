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

package com.android.tradefed.device;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;

import javax.annotation.Nonnull;

/** A helper class for running commands on Microdroid {@link TestDevice}. */
class MicrodroidHelper {

    // Run a shell command on Microdroid
    public String runOnMicrodroid(@Nonnull final String microdroidSerial, String... cmd) {
        final long timeout = 30000; // 30 sec. Microdroid is extremely slow on GCE-on-CF.
        IDeviceManager deviceManager = GlobalConfiguration.getDeviceManagerInstance();
        CommandResult result =
                getRunUtil()
                        .runTimedCmd(
                                timeout,
                                deviceManager.getAdbPath(),
                                "-s",
                                microdroidSerial,
                                "shell",
                                join(cmd));
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    join(cmd) + " has failed: " + result,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
        return result.getStdout().trim();
    }

    // Same as runOnMicrodroid, but returns null on error.
    public String tryRunOnMicrodroid(@Nonnull final String microdroidSerial, String... cmd) {
        final long timeout = 30000; // 30 sec. Microdroid is extremely slow on GCE-on-CF.
        IDeviceManager deviceManager = GlobalConfiguration.getDeviceManagerInstance();
        CommandResult result =
                getRunUtil()
                        .runTimedCmd(
                                timeout,
                                deviceManager.getAdbPath(),
                                "-s",
                                microdroidSerial,
                                "shell",
                                join(cmd));
        if (result.getStatus() == CommandStatus.SUCCESS) {
            return result.getStdout().trim();
        } else {
            CLog.d(join(cmd) + " has failed (but ok): " + result);
            return null;
        }
    }

    private IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    private static String join(String... strs) {
        return String.join(" ", Arrays.asList(strs));
    }
}
