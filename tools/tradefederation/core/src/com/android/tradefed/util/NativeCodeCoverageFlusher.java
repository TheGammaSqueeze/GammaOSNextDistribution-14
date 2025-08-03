/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tradefed.util;

import static com.google.common.base.Preconditions.checkState;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.StringJoiner;

/**
 * A utility class that clears native coverage measurements and forces a flush of native coverage
 * data from processes on the device.
 */
public final class NativeCodeCoverageFlusher {

    private static final String EXTRACT_SIGCGT_FORMAT =
            "cat /proc/%d/status | grep SigCgt | awk '{ print $2 }'";
    private static final long SIGNAL_37_BIT = 1L << (37 - 1);
    private static final String COVERAGE_FLUSH_COMMAND_FORMAT = "kill -37 %s";
    private static final String CLEAR_CLANG_COVERAGE_FILES =
            "find /data/misc/trace -name '*.profraw' -delete";
    private static final String CLEAR_GCOV_COVERAGE_FILES =
            "find /data/misc/trace -name '*.gcda' -delete";
    private static final long FLUSH_DELAY_MS = 2 * 60 * 1000; // 2 minutes

    private final ITestDevice mDevice;
    private final List<String> mProcessNames;
    private IRunUtil mRunUtil = RunUtil.getDefault();

    public NativeCodeCoverageFlusher(ITestDevice device, List<String> processNames) {
        mDevice = device;
        mProcessNames = processNames;
    }

    public void setRunUtil(IRunUtil runUtil) {
        mRunUtil = runUtil;
    }

    /**
     * Resets native coverage counters for processes running on the device and clears any existing
     * coverage measurements from disk. Device must be in adb root.
     *
     * @throws DeviceNotAvailableException
     */
    public void resetCoverage() throws DeviceNotAvailableException {
        forceCoverageFlush();
        deleteCoverageMeasurements();
    }

    /**
     * Deletes coverage measurements from the device. Device must be in adb root.
     *
     * @throws DeviceNotAvailableException
     */
    public void deleteCoverageMeasurements() throws DeviceNotAvailableException {
        mDevice.executeShellCommand(CLEAR_CLANG_COVERAGE_FILES);
        mDevice.executeShellCommand(CLEAR_GCOV_COVERAGE_FILES);
    }

    /**
     * Forces a flush of native coverage data from processes running on the device. Device must be
     * in adb root.
     *
     * @throws DeviceNotAvailableException
     */
    public void forceCoverageFlush() throws DeviceNotAvailableException {
        checkState(mDevice.isAdbRoot(), "adb root is required to flush native coverage data.");

        List<Integer> signalHandlingPids = findSignalHandlingPids(mProcessNames);
        StringJoiner pidString = new StringJoiner(" ");

        CLog.d("Signal handling pids: %s", signalHandlingPids.toString());

        for (Integer pid : signalHandlingPids) {
            pidString.add(pid.toString());
        }

        if (pidString.length() > 0) {
            mDevice.executeShellCommand(
                    String.format(COVERAGE_FLUSH_COMMAND_FORMAT, pidString.toString()));
        }

        // Wait 2 minutes for the device to complete flushing coverage data.
        mRunUtil.sleep(FLUSH_DELAY_MS);
        mDevice.waitForDeviceAvailable();
    }

    /** Finds processes that handle the native coverage flush signal (37). */
    private List<Integer> findSignalHandlingPids(List<String> processNames)
            throws DeviceNotAvailableException {
        // Get a list of all running pids.
        List<ProcessInfo> allProcessInfo =
                PsParser.getProcesses(mDevice.executeShellCommand("ps -e"));
        ImmutableList.Builder<Integer> signalHandlingPids = ImmutableList.builder();

        // Check SigCgt from /proc/<pid>/status to see if the bit for signal 37 is set.
        for (ProcessInfo processInfo : allProcessInfo) {
            CommandResult result =
                    mDevice.executeShellV2Command(
                            String.format(EXTRACT_SIGCGT_FORMAT, processInfo.getPid()));

            if (!result.getStatus().equals(CommandStatus.SUCCESS) || (result.getExitCode() != 0)) {
                CLog.w(
                        "Failed to read /proc/%d/status for %s",
                        processInfo.getPid(), processInfo.getName());
            } else if (result.getStdout().trim().isEmpty()) {
                CLog.w(
                        "Empty string when retrieving SigCgt for %s (pid %d)",
                        processInfo.getName(), processInfo.getPid());
            } else {
                long sigCgt = Long.parseUnsignedLong(result.getStdout().trim(), 16);

                // Check the signal bit is set and either no processes are set, or this specific
                // process is in the process list.
                if ((sigCgt & SIGNAL_37_BIT) == SIGNAL_37_BIT
                        && (processNames.isEmpty()
                                || processNames.contains(processInfo.getName()))) {
                    signalHandlingPids.add(processInfo.getPid());
                }
            }
        }

        return signalHandlingPids.build();
    }
}
