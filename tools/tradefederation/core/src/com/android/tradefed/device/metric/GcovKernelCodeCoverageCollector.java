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

package com.android.tradefed.device.metric;

import static com.android.tradefed.testtype.coverage.CoverageOptions.Toolchain.GCOV_KERNEL;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.util.AdbRootElevator;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import com.google.common.base.Strings;

import java.io.File;
import java.util.Map;

/**
 * A {@link com.android.tradefed.device.metric.BaseDeviceMetricCollector} that will pull gcov kernel
 * coverage measurements out of debugfs and off of the device and then finally logs them as test
 * artifacts.
 */
public final class GcovKernelCodeCoverageCollector extends BaseDeviceMetricCollector
        implements IConfigurationReceiver {

    public static final String RESET_GCOV_COUNTS_COMMAND =
            String.format("echo 1 > %s/gcov/reset", NativeDevice.DEBUGFS_PATH);
    public static final String MAKE_TEMP_DIR_COMMAND = "mktemp -d -p /data/local/tmp/";
    public static final String MAKE_GCDA_TEMP_DIR_COMMAND_FMT = "mkdir -p %s";
    public static final String COPY_GCOV_DATA_COMMAND_FMT = "cp -rf %s/* %s";
    public static final String TAR_GCOV_DATA_COMMAND_FMT = "tar -czf %s -C %s %s";

    private IConfiguration mConfiguration;
    private boolean mTestRunStartFail;
    private int mTestCount;

    public GcovKernelCodeCoverageCollector() {
        setDisableReceiver(false);
    }

    @Override
    public void setConfiguration(IConfiguration config) {
        mConfiguration = config;
    }

    private boolean isGcovKernelCoverageEnabled() {
        return mConfiguration != null
                && mConfiguration.getCoverageOptions().isCoverageEnabled()
                && mConfiguration
                        .getCoverageOptions()
                        .getCoverageToolchains()
                        .contains(GCOV_KERNEL);
    }

    @Override
    public void onTestRunStart(DeviceMetricData runData, int testCount)
            throws DeviceNotAvailableException {
        mTestCount = testCount;

        if (!isGcovKernelCoverageEnabled()) {
            return;
        }

        if (mTestCount == 0) {
            CLog.i("No tests in test run, not collecting coverage for %s.", getTarBasename());
            return;
        }

        try {
            for (ITestDevice device : getRealDevices()) {
                try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                    device.mountDebugfs();
                    resetGcovCounts(device);
                }
            }
        } catch (Throwable t) {
            mTestRunStartFail = true;
            throw t;
        }
        mTestRunStartFail = false;
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        if (!isGcovKernelCoverageEnabled() || mTestCount == 0) {
            return;
        }

        if (mTestRunStartFail) {
            CLog.e("onTestRunStart failed, not collecting coverage for %s.", getTarBasename());
            return;
        }

        for (ITestDevice device : getRealDevices()) {
            try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                collectGcovDebugfsCoverage(device, getTarBasename());
                device.unmountDebugfs();
            }
        }
    }

    @Override
    public void rebootStarted(ITestDevice device) throws DeviceNotAvailableException {
        super.rebootStarted(device);
        try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
            collectGcovDebugfsCoverage(device, getTarBasename());
        }
    }

    @Override
    public void rebootEnded(ITestDevice device) throws DeviceNotAvailableException {
        super.rebootEnded(device);
        try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
            device.mountDebugfs();
        }
    }

    /* Gets the name to be used for the collected coverage tar file.
     * Prefer the module name if available otherwise use the run name.
     */
    private String getTarBasename() {
        String collectionFilename = getModuleName();
        return Strings.isNullOrEmpty(collectionFilename) ? getRunName() : collectionFilename;
    }

    /** Reset gcov counts by writing to the gcov debugfs reset node. */
    private void resetGcovCounts(ITestDevice device) throws DeviceNotAvailableException {
        CommandResult result = device.executeShellV2Command(RESET_GCOV_COUNTS_COMMAND);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("Failed to reset gcov counts for %s. %s", getTarBasename(), result);
            throw new DeviceRuntimeException(
                    "'" + RESET_GCOV_COUNTS_COMMAND + "' has failed: " + result,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
    }

    /**
     * Gather overage data files off of the device. This logic is was originally taken directly from
     * the `gather_on_test.sh` script detailed here:
     * https://www.kernel.org/doc/html/v4.15/dev-tools/gcov.html#appendix-b-gather-on-test-sh
     * However, in practice the `find` + `cat` approach ended up taking a lot of time. The reasoning
     * given for this approach was because of issues with the `seq_file` interface. It turns out
     * this issue no longer applies to the `cp` command (it still applies to the `tar`). Discussion
     * on this can b e found here:
     * https://github.com/linux-test-project/lcov/discussions/199#discussion-4895422
     *
     * <p>TODO: Revert this summary back to the original text, once upstream patch lands that
     * updates `gather_on_test.sh` `cp` instead of `find` + `cat`.
     */
    private void collectGcovDebugfsCoverage(INativeDevice device, String name)
            throws DeviceNotAvailableException {
        if (!device.isDebugfsMounted()) {
                String errorMessage =
                        String.format("debugfs not mounted, unable to collect for %s.", name);
                CLog.e(errorMessage);
                throw new DeviceRuntimeException(
                        errorMessage, DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }

            CommandResult result = device.executeShellV2Command(MAKE_TEMP_DIR_COMMAND);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Failed to create temp dir for %s. %s", name, result);
                throw new DeviceRuntimeException(
                        "'" + MAKE_TEMP_DIR_COMMAND + "' has failed: " + result,
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }
            String tempDir = result.getStdout().strip();

            String gcda = "/d/gcov";
            String gcdaTempDir = tempDir + gcda;
            String makeGcdaTempDirCommand =
                    String.format(MAKE_GCDA_TEMP_DIR_COMMAND_FMT, gcdaTempDir);
            result = device.executeShellV2Command(makeGcdaTempDirCommand);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Failed to create gcda temp directory %s. %s", gcdaTempDir, result);
                throw new DeviceRuntimeException(
                        "'" + makeGcdaTempDirCommand + "' has failed: " + result,
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }

            String tarName = String.format("%s.tar.gz", name);
            String tarFullPath = String.format("%s/%s", tempDir, tarName);

            String copyGcovDataCommand =
                    String.format(COPY_GCOV_DATA_COMMAND_FMT, gcda, gcdaTempDir);
            result = device.executeShellV2Command(copyGcovDataCommand);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Failed to collect coverage files for %s. %s", name, result);
                throw new DeviceRuntimeException(
                        "'" + copyGcovDataCommand + "' has failed: " + result,
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }

            String tarCommand =
                    String.format(
                            TAR_GCOV_DATA_COMMAND_FMT, tarFullPath, tempDir, gcda.substring(1));
            result = device.executeShellV2Command(tarCommand);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Failed to tar collected files for %s. %s", name, result);
                throw new DeviceRuntimeException(
                        "'" + tarCommand + "' has failed: " + result,
                        DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
            }

            try {
                // We specify the root's user id here, 0, because framework services may be stopped
                // which would cause the non-user id version of this method to fail when it attempts
                // to get the current user id which isn't needed.
                File coverageTar = device.pullFile(tarFullPath, 0);
                verifyNotNull(
                        coverageTar,
                        "Failed to pull the native kernel coverage file %s for %s",
                        tarFullPath,
                        name);

                try (FileInputStreamSource source = new FileInputStreamSource(coverageTar, true)) {
                    String fileName =
                            String.format(
                                    "%s_%d_kernel_coverage", name, System.currentTimeMillis());
                    testLog(fileName, LogDataType.GCOV_KERNEL_COVERAGE, source);
                } finally {
                    FileUtil.deleteFile(coverageTar);
                }
            } finally {
                device.deleteFile(tempDir);
            }
    }
}
