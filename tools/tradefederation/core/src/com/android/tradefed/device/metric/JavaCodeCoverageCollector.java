/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.io.Files.getNameWithoutExtension;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.coverage.CoverageOptions;
import com.android.tradefed.util.AdbRootElevator;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.JavaCodeCoverageFlusher;
import com.android.tradefed.util.ProcessInfo;
import com.android.tradefed.util.PsParser;
import com.android.tradefed.util.TarUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import org.jacoco.core.tools.ExecFileLoader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A {@link com.android.tradefed.device.metric.BaseDeviceMetricCollector} that will pull Java
 * coverage measurements off of the device and log them as test artifacts.
 */
public final class JavaCodeCoverageCollector extends BaseDeviceMetricCollector
        implements IConfigurationReceiver {

    public static final String MERGE_COVERAGE_MEASUREMENTS_TEST_NAME = "mergeCoverageMeasurements";
    public static final String COVERAGE_MEASUREMENT_KEY = "coverageFilePath";
    public static final String COVERAGE_DIRECTORY = "/data/misc/trace";
    public static final String FIND_COVERAGE_FILES =
            String.format("find %s -name '*.ec'", COVERAGE_DIRECTORY);
    public static final String COMPRESS_COVERAGE_FILES =
            String.format("%s | tar -czf - -T - 2>/dev/null", FIND_COVERAGE_FILES);

    private ExecFileLoader mExecFileLoader;

    private JavaCodeCoverageFlusher mFlusher;
    private IConfiguration mConfiguration;
    // Timeout for pulling coverage files from the device, in milliseconds.
    private long mTimeoutMilli = 20 * 60 * 1000;

    @Override
    public void extraInit(IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        super.extraInit(context, listener);

        verifyNotNull(mConfiguration);
        setCoverageOptions(mConfiguration.getCoverageOptions());

        if (isJavaCoverageEnabled()
                && mConfiguration.getCoverageOptions().shouldResetCoverageBeforeTest()) {
            for (ITestDevice device : getRealDevices()) {
                try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                    getCoverageFlusher(device).resetCoverage();
                }
            }
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    private JavaCodeCoverageFlusher getCoverageFlusher(ITestDevice device) {
        if (mFlusher == null) {
            mFlusher =
                    new JavaCodeCoverageFlusher(
                            device, mConfiguration.getCoverageOptions().getCoverageProcesses());
        }
        return mFlusher;
    }

    @VisibleForTesting
    void setCoverageFlusher(JavaCodeCoverageFlusher flusher) {
        mFlusher = flusher;
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> runMetrics)
            throws DeviceNotAvailableException {
        if (!isJavaCoverageEnabled()) {
            return;
        }

        String testCoveragePath = null;

        // Get the path of the coverage measurement on the device.
        Metric devicePathMetric = runMetrics.get(COVERAGE_MEASUREMENT_KEY);
        if (devicePathMetric == null) {
            CLog.d("No Java code coverage measurement.");
        } else {
            testCoveragePath = devicePathMetric.getMeasurements().getSingleString();
            if (testCoveragePath == null) {
                CLog.d("No Java code coverage measurement.");
            }
        }

        for (ITestDevice device : getRealDevices()) {
            File testCoverage = null;
            File coverageTarGz = null;
            File untarDir = null;

            try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                if (mConfiguration.getCoverageOptions().isCoverageFlushEnabled()) {
                    getCoverageFlusher(device).forceCoverageFlush();
                }

                // Pull and log the test coverage file.
                if (testCoveragePath != null) {
                    if (!new File(testCoveragePath).isAbsolute()) {
                        testCoveragePath =
                                "/sdcard/googletest/internal_use/" + testCoveragePath;
                    }
                    testCoverage = device.pullFile(testCoveragePath);
                    if (testCoverage == null) {
                        // Log a warning only, since multi-device tests will not have this file on
                        // all devices.
                        CLog.w(
                                "Failed to pull test coverage file %s from the device.",
                                testCoveragePath);
                    } else {
                        saveCoverageMeasurement(testCoverage);
                    }
                }

                // Stream compressed coverage measurements from /data/misc/trace to the host.
                coverageTarGz = FileUtil.createTempFile("java_coverage", ".tar.gz");
                try (OutputStream out =
                        new BufferedOutputStream(new FileOutputStream(coverageTarGz))) {
                    CommandResult result =
                            device.executeShellV2Command(
                                    COMPRESS_COVERAGE_FILES,
                                    null,
                                    out,
                                    mTimeoutMilli,
                                    TimeUnit.MILLISECONDS,
                                    1);
                    if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                        CLog.e(
                                "Failed to stream coverage data from the device: %s",
                                result.toString());
                    }
                }

                // Decompress the files and log the measurements.
                untarDir = TarUtil.extractTarGzipToTemp(coverageTarGz, "java_coverage");
                for (String coveragePath : FileUtil.findFiles(untarDir, ".*\\.ec")) {
                    saveCoverageMeasurement(new File(coveragePath));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // Clean up local coverage files.
                FileUtil.deleteFile(testCoverage);
                FileUtil.deleteFile(coverageTarGz);
                FileUtil.recursiveDelete(untarDir);

                // Clean up device coverage files.
                cleanUpDeviceCoverageFiles(device);
            }
        }

        // Log the merged coverage data file if the flag is set.
        if (shouldMergeCoverage() && (mExecFileLoader != null)) {
            File mergedCoverage = null;
            try {
                mergedCoverage = FileUtil.createTempFile("merged_java_coverage", ".ec");
                mExecFileLoader.save(mergedCoverage, false);
                logCoverageMeasurement(mergedCoverage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                mExecFileLoader = null;
                FileUtil.deleteFile(mergedCoverage);
            }
        }
    }

    /** Saves Java coverage file data. */
    private void saveCoverageMeasurement(File coverageFile) throws IOException {
        if (shouldMergeCoverage()) {
            if (mExecFileLoader == null) {
                mExecFileLoader = new ExecFileLoader();
            }
            mExecFileLoader.load(coverageFile);
        } else {
            logCoverageMeasurement(coverageFile);
        }
    }

    /** Logs files as Java coverage measurements. */
    private void logCoverageMeasurement(File coverageFile) {
        try (FileInputStreamSource source = new FileInputStreamSource(coverageFile, true)) {
            testLog(generateMeasurementFileName(coverageFile), LogDataType.COVERAGE, source);
        }
    }

    /** Generate the .ec file prefix in format "$moduleName_MODULE_$runName". */
    private String generateMeasurementFileName(File coverageFile) {
        String moduleName = Strings.nullToEmpty(getModuleName());
        if (moduleName.length() > 0) {
            moduleName += "_MODULE_";
        }
        return moduleName
                + getRunName()
                + "_"
                + getNameWithoutExtension(coverageFile.getName())
                + "_runtime_coverage";
    }

    /** Cleans up .ec files in /data/misc/trace. */
    private void cleanUpDeviceCoverageFiles(ITestDevice device) throws DeviceNotAvailableException {
        try (AdbRootElevator root = new AdbRootElevator(device)) {
            List<Integer> activePids = getRunningProcessIds(device);

            String fileList = device.executeShellCommand(FIND_COVERAGE_FILES);
            for (String devicePath : Splitter.on('\n').omitEmptyStrings().split(fileList)) {
                if (devicePath.endsWith(".mm.ec")) {
                    // Check if the process was still running. The file will have the format
                    // /data/misc/trace/jacoco-XXXXX.mm.ec where XXXXX is the process id.
                    int start = devicePath.indexOf('-') + 1;
                    int end = devicePath.indexOf('.');
                    int pid = Integer.parseInt(devicePath.substring(start, end));
                    if (!activePids.contains(pid)) {
                        device.deleteFile(devicePath);
                    }
                } else {
                    device.deleteFile(devicePath);
                }
            }
        }
    }

    /** Parses the output of `ps -e` to get a list of running process ids. */
    private List<Integer> getRunningProcessIds(ITestDevice device)
            throws DeviceNotAvailableException {
        List<ProcessInfo> processes = PsParser.getProcesses(device.executeShellCommand("ps -e"));
        List<Integer> pids = new ArrayList<>();

        for (ProcessInfo process : processes) {
            pids.add(process.getPid());
        }
        return pids;
    }

    private boolean isJavaCoverageEnabled() {
        return mConfiguration != null
                && mConfiguration.getCoverageOptions().isCoverageEnabled()
                && mConfiguration
                        .getCoverageOptions()
                        .getCoverageToolchains()
                        .contains(CoverageOptions.Toolchain.JACOCO);
    }

    private boolean shouldMergeCoverage() {
        return mConfiguration != null && mConfiguration.getCoverageOptions().shouldMergeCoverage();
    }

    private void setCoverageOptions(CoverageOptions coverageOptions) {
        mTimeoutMilli = coverageOptions.getPullTimeout();
    }
}
