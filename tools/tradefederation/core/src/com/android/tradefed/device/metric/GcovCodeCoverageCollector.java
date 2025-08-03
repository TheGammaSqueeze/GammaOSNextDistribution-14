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

package com.android.tradefed.device.metric;

import static com.android.tradefed.testtype.coverage.CoverageOptions.Toolchain.GCOV;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.AdbRootElevator;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.NativeCodeCoverageFlusher;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * A {@link com.android.tradefed.device.metric.BaseDeviceMetricCollector} that will pull gcov
 * coverage measurements off of the device and log them as test artifacts.
 */
public final class GcovCodeCoverageCollector extends BaseDeviceMetricCollector
        implements IConfigurationReceiver {

    private static final String NATIVE_COVERAGE_DEVICE_PATH = "/data/misc/trace";
    private static final String COVERAGE_TAR_PATH =
            String.format("%s/coverage.tar", NATIVE_COVERAGE_DEVICE_PATH);

    // Finds .gcda files in /data/misc/trace and compresses those files only. Stores the full
    // path of the file on the device.
    private static final String ZIP_COVERAGE_FILES_COMMAND =
            String.format(
                    "find %s -name '*.gcda' | tar -cvf %s -T -",
                    NATIVE_COVERAGE_DEVICE_PATH, COVERAGE_TAR_PATH);

    // Deletes .gcda files in /data/misc/trace.
    private static final String DELETE_COVERAGE_FILES_COMMAND =
            String.format("find %s -name '*.gcda' -delete", NATIVE_COVERAGE_DEVICE_PATH);

    private NativeCodeCoverageFlusher mFlusher;
    private boolean mCollectCoverageOnTestEnd = true;
    private IConfiguration mConfiguration;
    private IRunUtil mRunUtil = RunUtil.getDefault();

    @Override
    public void extraInit(IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        super.extraInit(context, listener);

        if (isGcovCoverageEnabled()) {
            for (ITestDevice device : getRealDevices()) {
                // Clear coverage measurements on the device.
                try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                    getCoverageFlusher(device).resetCoverage();
                }
            }
        }
    }

    @VisibleForTesting
    void setRunUtil(IRunUtil runUtil) {
        mRunUtil = runUtil;
        if (mFlusher != null) {
            mFlusher.setRunUtil(runUtil);
        }
    }

    @Override
    public void setConfiguration(IConfiguration config) {
        mConfiguration = config;
    }

    private boolean isGcovCoverageEnabled() {
        return mConfiguration != null
                && mConfiguration.getCoverageOptions().isCoverageEnabled()
                && mConfiguration.getCoverageOptions().getCoverageToolchains().contains(GCOV);
    }

    private NativeCodeCoverageFlusher getCoverageFlusher(ITestDevice device) {
        if (mFlusher == null) {
            mFlusher =
                    new NativeCodeCoverageFlusher(
                            device, mConfiguration.getCoverageOptions().getCoverageProcesses());
            mFlusher.setRunUtil(mRunUtil);
        }
        return mFlusher;
    }

    /**
     * Sets whether to collect coverage on testRunEnded.
     *
     * <p>Set this to false during re-runs, otherwise each individual test re-run will collect
     * coverage rather than having a single merged coverage result.
     */
    public void setCollectOnTestEnd(boolean collect) {
        mCollectCoverageOnTestEnd = collect;
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> runMetrics)
            throws DeviceNotAvailableException {
        if (!isGcovCoverageEnabled()) {
            return;
        }

        if (mCollectCoverageOnTestEnd) {
            for (ITestDevice device : getRealDevices()) {
                logCoverageMeasurements(device, getRunName());
            }
        }
    }

    /** Pulls native coverage measurements from the device and logs them. */
    public void logCoverageMeasurements(ITestDevice device, String runName)
            throws DeviceNotAvailableException {
        File coverageTar = null;
        File coverageZip = null;

        // Enable abd root on the device, otherwise the following commands will fail.
        try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
            // Flush cross-process coverage.
            if (mConfiguration.getCoverageOptions().isCoverageFlushEnabled()) {
                getCoverageFlusher(device).forceCoverageFlush();
            }

            // Compress coverage measurements on the device before pulling.
            device.executeShellCommand(ZIP_COVERAGE_FILES_COMMAND);
            coverageTar = device.pullFile(COVERAGE_TAR_PATH);
            verifyNotNull(
                    coverageTar,
                    "Failed to pull the native code coverage file %s",
                    COVERAGE_TAR_PATH);
            device.deleteFile(COVERAGE_TAR_PATH);

            coverageZip = convertTarToZip(coverageTar);

            try (FileInputStreamSource source = new FileInputStreamSource(coverageZip, true)) {
                testLog(runName + "_native_runtime_coverage", LogDataType.NATIVE_COVERAGE, source);
            }

            // Delete coverage files on the device.
            device.executeShellCommand(DELETE_COVERAGE_FILES_COMMAND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            FileUtil.deleteFile(coverageTar);
            FileUtil.deleteFile(coverageZip);
        }
    }

    /**
     * Converts a .tar file to a .zip file.
     *
     * @param tar the .tar file to convert
     * @return a .zip file with the same contents
     * @throws IOException
     */
    private File convertTarToZip(File tar) throws IOException {
        File untarDir = null;
        try {
            untarDir = FileUtil.createTempDir("gcov_coverage");
            TarUtil.unTar(tar, untarDir);
            return ZipUtil.createZip(Arrays.asList(untarDir.listFiles()), "native_coverage");
        } finally {
            FileUtil.recursiveDelete(untarDir);
        }
    }
}
