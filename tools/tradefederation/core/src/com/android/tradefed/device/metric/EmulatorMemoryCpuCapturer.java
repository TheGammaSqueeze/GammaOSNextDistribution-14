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

package com.android.tradefed.device.metric;

import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmulatorMemoryCpuCapturer {

    private static final Pattern PSS_PATTERN = Pattern.compile("Pss:\\s+(\\d+)");
    private final long mPid;

    public EmulatorMemoryCpuCapturer(ITestDevice device) {
        IManagedTestDevice managedTestDevice = (IManagedTestDevice) device;
        Preconditions.checkArgument(
                managedTestDevice.getIDevice().isEmulator(),
                String.format(
                        "device %s is not an emulator",
                        managedTestDevice.getIDevice().getSerialNumber()));
        mPid = managedTestDevice.getEmulatorProcess().pid();
    }

    @VisibleForTesting
    EmulatorMemoryCpuCapturer(long pid) {
        mPid = pid;
    }

    public long getPssMemory() {
        try {
            String s = FileUtil.readStringFromFile(new File(String.format("/proc/%d/smaps", mPid)));
            return parsePssMemory(s);
        } catch (IOException e) {
            LogUtil.CLog.e("Failed to read /proc/x/smaps", e);
        }
        return 0;
    }

    static long parsePssMemory(String procSmapsContent) {
        // expect several entries of 'Pss:  XX KB'. Parse them out and add them up.
        Matcher m = PSS_PATTERN.matcher(procSmapsContent);
        List<Integer> allPss = new ArrayList<>();
        while (m.find()) {
            allPss.add(Integer.parseInt(m.group(1)));
        }

        return allPss.stream().mapToInt(Integer::intValue).sum();
    }

    public float getCpuUsage() {
        CommandResult result =
                getRunUtil().runTimedCmd(20000L, "ps", "-o", "%cpu", "-p", Long.toString(mPid));
        if (result.getStatus() == CommandStatus.SUCCESS) {
            return parseCpuUsage(result.getStdout());
        } else {
            LogUtil.CLog.e("Failed to run ps %s", result.toString());
            return 0;
        }
    }

    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Parse the cpu usage string.
     *
     * <p>Expected input in format: %CPU 1.4
     */
    static float parseCpuUsage(String psContents) {
        return Float.parseFloat(psContents.replace("%CPU\n", "").trim());
    }
}
