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
package com.android.performance;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.SimpleStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** dd benchmark runner. */
public class DDBenchmarkTest implements IRemoteTest, IDeviceTest {
    @Option(name = "dd-bin", description = "Path to dd binary.", mandatory = true)
    private String ddBinary = "dd";

    @Option(
            name = "name",
            description = "ASCII name of the benchmark.",
            mandatory = true,
            importance = Importance.ALWAYS)
    private String benchmarkName = "dd_benchmark";

    @Option(
            name = "iter",
            description = "Number of times the dd benchmark is executed.",
            mandatory = true,
            importance = Importance.ALWAYS)
    private int iterations = 1;

    @Option(name = "if", description = "Read from this file instead of stdin.")
    private String inputFile = null;

    @Option(name = "of", description = "Write to this file instead of stdout.")
    private String outputFile = null;

    @Option(name = "ibs", description = "Input block size.")
    private String inputBlockSize = null;

    @Option(name = "obs", description = "Ouput block size.")
    private String outputBlockSize = null;

    @Option(name = "bs", description = "Read and write N bytes at a time.")
    private String ddBlockSize = null;

    @Option(name = "count", description = "Copy only N input blocks.")
    private String count = null;

    @Option(name = "iflag", description = "Set input flags")
    private String inputFlags = null;

    @Option(name = "oflag", description = "Set output flags")
    private String outputFlags = null;

    @Option(name = "conv", description = "Convert the file as per the comma separated symbol list")
    private String conv = null;

    @Option(name = "create-if", description = "Fill if with count input blocks before running dd.")
    private boolean createInputFile = false;

    @Option(name = "clean-if", description = "Delete if after the benchmark ends.")
    private boolean deleteInputFile = false;

    @Option(name = "clean-of", description = "Delete of after the benchmark ends.")
    private boolean deleteOutputFile = false;

    @Option(name = "reboot-between-runs", description = "Reboot the device before each run.")
    private boolean rebootBetweenRuns = false;

    private static final int I_BANDWIDTH = 2;
    private Map<String, String> metrics = new HashMap<>();
    private SimpleStats bandwidthStats = new SimpleStats();
    private boolean hasCollectedMetrics = false;
    private ITestDevice mDevice;

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (createInputFile) setupInputFile();
        List<String> results = runDDBenchmark();
        for (String result : results)
            hasCollectedMetrics = parseDDOutput(result) || hasCollectedMetrics;
        reportDDMetrics(listener);
        cleanup();
    }

    private void setupInputFile() throws DeviceNotAvailableException {
        // We use the specified inputBlockSize for ibs, obs, bs to make sure we are creating a file
        // of the correct size.
        String fillCommand =
                buildDDCommand(
                        ddBinary,
                        "/dev/zero" /*inputFile*/,
                        inputFile /*outputFile*/,
                        inputBlockSize /*ibs*/,
                        inputBlockSize /*obs*/,
                        inputBlockSize /*bs*/,
                        count,
                        inputFlags,
                        null /*oflag*/,
                        "fsync" /*conv*/);
        getDevice().executeShellCommand(fillCommand);
    }

    private List<String> runDDBenchmark() throws DeviceNotAvailableException {
        List<String> results = new ArrayList<String>();
        String ddCommand =
                buildDDCommand(
                        ddBinary,
                        inputFile,
                        outputFile,
                        inputBlockSize,
                        outputBlockSize,
                        ddBlockSize,
                        count,
                        inputFlags,
                        outputFlags,
                        conv);

        for (int i = 0; i < iterations; i++) {
            dropState();
            results.add(getDevice().executeShellCommand(ddCommand));
        }

        return results;
    }

    private String getDDVersion() throws DeviceNotAvailableException {
        String ddVersionCommand = String.format("%s --version", ddBinary);
        return getDevice().executeShellCommand(ddVersionCommand).trim();
    }

    private void reportDDMetrics(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        listener.testRunStarted(benchmarkName, 0);
        if (!hasCollectedMetrics) {
            String errorMessage = "Failed to collect dd benchmark metrics";
            CLog.i(errorMessage);
            listener.testRunFailed(errorMessage);
            return;
        }
        String ddVersion = getDDVersion();
        String meanMetricName = String.format("%s-%s", ddVersion, "bandwidth_avg_MiB_s");
        String stdevMetricName = String.format("%s-%s", ddVersion, "bandwidth_stdev_MiB_s");
        metrics.put(meanMetricName, String.format("%.4f", bandwidthStats.mean()));
        metrics.put(stdevMetricName, String.format("%.4f", bandwidthStats.stdev()));
        listener.testRunEnded(0, metrics);
    }

    private void cleanup() throws DeviceNotAvailableException {
        if (deleteInputFile) {
            String rmCommand = String.format("rm %s", inputFile);
            getDevice().executeShellCommand(rmCommand);
        }
        if (deleteOutputFile) {
            String rmCommand = String.format("rm %s", outputFile);
            getDevice().executeShellCommand(rmCommand);
        }
    }

    private void dropState() throws DeviceNotAvailableException {
        if (rebootBetweenRuns) getDevice().reboot();
        else getDevice().executeShellCommand("sync; echo 3 > /proc/sys/vm/drop_caches");
    }

    /** Parse dd output assuming `toybox 0.8.3-android` version. */
    private boolean parseDDOutput(String output) {
        /* Output format in case of success:
         * Line | Content                                 | Notes
         * -----+-----------------------------------------+-------------------
         * 1:   | "count=" COUNT_FLAGS                    | Missing if count=0
         * 2:   | x+y "records in"                        |
         * 3:   | x+y "records out"                       |
         * 4:   | x "bytes" (y METRIC_PREFIX) "copied", \ |
         * 4:   | t TIME_UNIT, y BANDWIDTH_UNIT           |
         *
         * Output format in case of failure:
         * Line | Content
         * -----+--------------------
         * 1:   | "dd:" ERROR_MESSAGE
         */
        String[] lines = output.split("\n");
        if (lines.length < 3) return false;

        String bandwidthLine = lines[lines.length - 1];
        String[] bandwidthWithUnit = bandwidthLine.split(",")[I_BANDWIDTH].trim().split(" ");
        String bandwidthS = bandwidthWithUnit[0];
        String unit = bandwidthWithUnit[1];
        try {
            double bandwidth = bandwidthInMiB(bandwidthS, unit);
            bandwidthStats.add(bandwidth);
        } catch (IllegalArgumentException e) {
            CLog.i(String.format("Unknown unit %s while parsing dd output", unit));
            return false;
        }

        return true;
    }

    /**
     * Convert dd output bandwidth to MiB/s.
     *
     * <p>dd output bandwidth can have any of the suffixes reported by `dd --help`. This function
     * uses the values documented for the `toybox 0.8.3-android` version to return a consistent
     * bandwidth unit (MiB/s).
     */
    public static double bandwidthInMiB(String bandwidth, String unit)
            throws IllegalArgumentException {
        double multiplier = 1;

        switch (unit) {
            case "c/s":
                multiplier = 1;
                break;
            case "w/s":
                multiplier = 2;
                break;
            case "b/s":
                multiplier = 512;
                break;
            case "kD/s":
                multiplier = 1000;
                break;
            case "k/s":
                multiplier = 1024;
                break;
            case "MD/s":
                multiplier = 1000 * 1000;
                break;
            case "M/s":
                multiplier = 1024 * 1024;
                break;
            case "GD/s":
                multiplier = 1000 * 1000 * 1000;
                break;
            case "G/s":
                multiplier = 1024 * 1024 * 1024;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unknown unit %s", unit));
        }

        double bandwidthInB = Double.parseDouble(bandwidth) * multiplier;
        return bandwidthInB / (1024 * 1024);
    }

    public static String buildDDCommand(
            String ddBinary,
            String inputFile,
            String outputFile,
            String inputBlockSize,
            String outputBlockSize,
            String ddBlockSize,
            String count,
            String inputFlags,
            String outputFlags,
            String conv) {
        if (ddBinary == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(ddBinary);
        if (inputFile != null) {
            sb.append(" if=");
            sb.append(inputFile);
        }
        if (outputFile != null) {
            sb.append(" of=");
            sb.append(outputFile);
        }
        if (inputBlockSize != null) {
            sb.append(" ibs=");
            sb.append(inputBlockSize);
        }
        if (outputBlockSize != null) {
            sb.append(" obs=");
            sb.append(outputBlockSize);
        }
        if (ddBlockSize != null) {
            sb.append(" bs=");
            sb.append(ddBlockSize);
        }
        if (count != null) {
            sb.append(" count=");
            sb.append(count);
        }
        if (inputFlags != null) {
            sb.append(" iflag=");
            sb.append(inputFlags);
        }
        if (outputFlags != null) {
            sb.append(" oflag=");
            sb.append(outputFlags);
        }
        if (conv != null) {
            sb.append(" conv=");
            sb.append(conv);
        }

        return sb.toString();
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
}
