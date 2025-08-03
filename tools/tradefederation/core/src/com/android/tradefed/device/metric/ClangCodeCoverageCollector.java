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

package com.android.tradefed.device.metric;

import static com.android.tradefed.testtype.coverage.CoverageOptions.Toolchain.CLANG;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.testtype.coverage.CoverageOptions;
import com.android.tradefed.util.AdbRootElevator;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.NativeCodeCoverageFlusher;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A {@link com.android.tradefed.device.metric.BaseDeviceMetricCollector} that will pull Clang
 * coverage measurements off of the device and log them as test artifacts.
 */
public final class ClangCodeCoverageCollector extends BaseDeviceMetricCollector
        implements IConfigurationReceiver {

    private static final String NATIVE_COVERAGE_DEVICE_PATH = "/data/misc/trace";

    // Maximum number of profile files before writing the list to a file. Beyond this value,
    // llvm-profdata will use the -f option to read the list from a file to prevent exceeding
    // the command line length limit.
    private static final int MAX_PROFILE_FILES = 100;

    // Finds .profraw files in /data/misc/trace and compresses those files only. Stores the full
    // path of the file on the device.
    private static final String ZIP_CLANG_FILES_COMMAND =
            String.format(
                    "find %s -name '*.profraw' | tar -czf - -T - 2>/dev/null",
                    NATIVE_COVERAGE_DEVICE_PATH);

    // Deletes .profraw files in /data/misc/trace.
    private static final String DELETE_COVERAGE_FILES_COMMAND =
            String.format("find %s -name '*.profraw' -delete", NATIVE_COVERAGE_DEVICE_PATH);

    private IConfiguration mConfiguration;
    private IRunUtil mRunUtil = RunUtil.getDefault();
    // Timeout for pulling coverage measurements from the device, in milliseconds.
    private long mTimeoutMilli = 20 * 60 * 1000;
    private File mLlvmProfileTool;

    private NativeCodeCoverageFlusher mFlusher;

    @Override
    public void extraInit(IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        super.extraInit(context, listener);
        setDisableReceiver(false);

        verifyNotNull(mConfiguration);
        setCoverageOptions(mConfiguration.getCoverageOptions());

        if (isClangCoverageEnabled()
                && mConfiguration.getCoverageOptions().shouldResetCoverageBeforeTest()) {
            for (ITestDevice device : getRealDevices()) {
                // Clear coverage measurements on the device.
                try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                    getCoverageFlusher(device).deleteCoverageMeasurements();
                }
            }
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    @Override
    public void rebootEnded(ITestDevice device) throws DeviceNotAvailableException {
        if (isClangCoverageEnabled()
                && mConfiguration.getCoverageOptions().shouldResetCoverageBeforeTest()) {
            getCoverageFlusher(device).deleteCoverageMeasurements();
        }
    }

    @VisibleForTesting
    public void setRunUtil(IRunUtil runUtil) {
        mRunUtil = runUtil;
        if (mFlusher != null) {
            mFlusher.setRunUtil(runUtil);
        }
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        if (!isClangCoverageEnabled()) {
            return;
        }

        for (ITestDevice device : getRealDevices()) {
            try (AdbRootElevator adbRoot = new AdbRootElevator(device)) {
                if (mConfiguration.getCoverageOptions().isCoverageFlushEnabled()) {
                    getCoverageFlusher(device).forceCoverageFlush();
                }
                logCoverageMeasurement(device, generateMeasurementFileName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Generate the .ec file prefix in format "$moduleName_MODULE_$runName". */
    private String generateMeasurementFileName() {
        String moduleName = Strings.nullToEmpty(getModuleName());
        if (moduleName.length() > 0) {
            moduleName += "_MODULE_";
        }
        return moduleName + getRunName().replace(' ', '_');
    }

    /**
     * Logs Clang coverage measurements from the device.
     *
     * @param runName name used in the log file
     * @throws DeviceNotAvailableException
     * @throws IOException
     */
    private void logCoverageMeasurement(ITestDevice device, String runName)
            throws DeviceNotAvailableException, IOException {
        File coverageTarGz = null;
        File untarDir = null;
        File fileList = null;
        File profileTool = null;
        File indexedProfileFile = null;
        try {
            coverageTarGz = FileUtil.createTempFile("clang_coverage", ".tar.gz");

            // Compress coverage measurements on the device before streaming to the host.
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(coverageTarGz))) {
                device.executeShellV2Command(
                        ZIP_CLANG_FILES_COMMAND, // Command
                        null, // File pipe as input
                        out, // OutputStream to write to
                        mTimeoutMilli, // Timeout in milliseconds
                        TimeUnit.MILLISECONDS, // Timeout units
                        1); // Retry count
            }

            untarDir = TarUtil.extractTarGzipToTemp(coverageTarGz, "clang_coverage");
            Set<String> rawProfileFiles =
                    FileUtil.findFiles(
                            untarDir, mConfiguration.getCoverageOptions().getProfrawFilter());

            if (rawProfileFiles.isEmpty()) {
                CLog.i("No Clang code coverage measurements found.");
                return;
            }

            CLog.i("Received %d Clang code coverage measurements.", rawProfileFiles.size());

            // Get the llvm-profdata tool from the build. This tool must match the same one used to
            // compile the build, otherwise this action will fail.
            profileTool = getProfileTool();
            Path profileBin = profileTool.toPath().resolve("bin/llvm-profdata");
            profileBin.toFile().setExecutable(true);

            List<String> command = new ArrayList<>();
            command.add(profileBin.toString());
            command.add("merge");
            command.add("-sparse");

            if (rawProfileFiles.size() > MAX_PROFILE_FILES) {
                // Write the measurement file list to a temporary file. This allows large numbers
                // of measurements to not exceed the command line length limit.
                fileList = FileUtil.createTempFile("clang_measurements", ".txt");
                Files.write(fileList.toPath(), rawProfileFiles, Charset.defaultCharset());

                // Add the file containing the list of .profraw files.
                command.add("-f");
                command.add(fileList.getAbsolutePath());
            } else {
                command.addAll(rawProfileFiles);
            }

            // Create the output file.
            indexedProfileFile =
                    FileUtil.createTempFile(runName + "_clang_runtime_coverage", ".profdata");
            command.add("-o");
            command.add(indexedProfileFile.getAbsolutePath());

            CommandResult result = mRunUtil.runTimedCmd(0, command.toArray(new String[0]));
            if (result.getStatus() != CommandStatus.SUCCESS) {
                // Retry with -failure-mode=all to still be able to report some coverage.
                command.add("-failure-mode=all");
                result = mRunUtil.runTimedCmd(0, command.toArray(new String[0]));

                if (result.getStatus() != CommandStatus.SUCCESS) {
                    throw new HarnessRuntimeException(
                            "Failed to merge Clang profile data:\n" + result.toString(),
                            InfraErrorIdentifier.CODE_COVERAGE_ERROR);
                }
            }

            try (FileInputStreamSource source =
                    new FileInputStreamSource(indexedProfileFile, true)) {
                testLog(runName + "_clang_runtime_coverage", LogDataType.CLANG_COVERAGE, source);
            }
        } finally {
            // Delete coverage files on the device.
            device.executeShellCommand(DELETE_COVERAGE_FILES_COMMAND);
            FileUtil.deleteFile(coverageTarGz);
            FileUtil.recursiveDelete(untarDir);
            FileUtil.deleteFile(fileList);
            FileUtil.recursiveDelete(mLlvmProfileTool);
            FileUtil.deleteFile(indexedProfileFile);
        }
    }

    /**
     * Creates a {@link NativeCodeCoverageFlusher} if one does not already exist.
     *
     * @return a NativeCodeCoverageFlusher
     */
    private NativeCodeCoverageFlusher getCoverageFlusher(ITestDevice device) {
        if (mFlusher == null) {
            verifyNotNull(mConfiguration);
            mFlusher =
                    new NativeCodeCoverageFlusher(
                            device, mConfiguration.getCoverageOptions().getCoverageProcesses());
            mFlusher.setRunUtil(mRunUtil);
        }
        return mFlusher;
    }

    private boolean isClangCoverageEnabled() {
        return mConfiguration != null
                && mConfiguration.getCoverageOptions().isCoverageEnabled()
                && mConfiguration.getCoverageOptions().getCoverageToolchains().contains(CLANG);
    }

    /**
     * Retrieves the profile tool and dependencies from the build, and extracts them.
     *
     * @return the directory containing the profile tool and dependencies
     */
    private File getProfileTool() throws IOException {
        // If llvm-profdata-path was set in the Configuration, pass it through. Don't save the path
        // locally since the parent process is responsible for cleaning it up.
        File configurationTool = mConfiguration.getCoverageOptions().getLlvmProfdataPath();
        if (configurationTool != null) {
            return configurationTool;
        }
        if (mLlvmProfileTool != null && mLlvmProfileTool.exists()) {
            return mLlvmProfileTool;
        }

        // Otherwise, try to download llvm-profdata.zip from the build and cache it.
        File profileToolZip = null;
        for (IBuildInfo info : getBuildInfos()) {
            if (info.getFile("llvm-profdata.zip") != null) {
                profileToolZip = info.getFile("llvm-profdata.zip");
                mLlvmProfileTool = ZipUtil.extractZipToTemp(profileToolZip, "llvm-profdata");
                return mLlvmProfileTool;
            }
        }
        try {
            // TODO: Delete this, we shouldn't have re-entry in the build
            // provider this can cause quite a lot of overhead.
            IBuildInfo buildInfo = mConfiguration.getBuildProvider().getBuild();
            profileToolZip =
                    verifyNotNull(
                            buildInfo.getFile("llvm-profdata.zip"),
                            "Could not get llvm-profdata.zip from the build.");
            mLlvmProfileTool = ZipUtil.extractZipToTemp(profileToolZip, "llvm-profdata");
            return mLlvmProfileTool;
        } catch (BuildRetrievalError e) {
            throw new RuntimeException(e);
        } finally {
            FileUtil.deleteFile(profileToolZip);
        }
    }

    private void setCoverageOptions(CoverageOptions coverageOptions) {
        mTimeoutMilli = coverageOptions.getPullTimeout();
    }
}
