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

/** inodeop benchmark runner. */
public class InodeopBenchmarkTest implements IRemoteTest, IDeviceTest {
    @Option(name = "inodeop-bin", description = "Path to inodeop binary.", mandatory = true)
    private String inodeopBinary = null;

    @Option(
            name = "name",
            description = "ASCII name of the benchmark.",
            mandatory = true,
            importance = Importance.ALWAYS)
    private String benchmarkName = "inodeop_benchmark";

    @Option(
            name = "iter",
            description = "Number of times the inodeop benchmark is executed.",
            mandatory = true,
            importance = Importance.ALWAYS)
    private int iterations = 1;

    @Option(name = "workload", description = "Type of workload to execute.", mandatory = true)
    private String workload = null;

    @Option(name = "dir", description = "Working directory.")
    private String directory = null;

    @Option(name = "from-dir", description = "Source directory.")
    private String fromDirectory = null;

    @Option(name = "to-dir", description = "Destination directory.")
    private String toDirectory = null;

    @Option(
            name = "n-files",
            description = "Number of files to create/delete etc.",
            mandatory = true)
    private int nFiles = 0;

    @Option(
            name = "maintain-state",
            description = "Do not drop state (caches) before running the workload.")
    private boolean maintainState = false;

    @Option(name = "reboot-between-runs", description = "Reboot the device before each run.")
    private boolean rebootBetweenRuns = false;

    private Map<String, String> metrics = new HashMap<>();
    private SimpleStats executionTimeStats = new SimpleStats();
    private boolean hasCollectedMetrics = false;
    private ITestDevice mDevice;

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        List<String> results = runInodeopBenchmark();
        for (String result : results)
            hasCollectedMetrics = parseInodeopOutput(result) || hasCollectedMetrics;
        reportInodeopMetrics(listener);
    }

    private List<String> runInodeopBenchmark() throws DeviceNotAvailableException {
        List<String> results = new ArrayList<String>();
        String inodeopCommand =
                buildInodeopCommand(
                        inodeopBinary,
                        directory,
                        fromDirectory,
                        toDirectory,
                        nFiles,
                        maintainState,
                        workload);

        for (int i = 0; i < iterations; i++) {
            dropState();
            results.add(getDevice().executeShellCommand(inodeopCommand));
        }

        return results;
    }

    private String getInodeopVersion() throws DeviceNotAvailableException {
        String inodeopVersionCommand = String.format("%s -v", inodeopBinary);
        return getDevice().executeShellCommand(inodeopVersionCommand).trim();
    }

    private void reportInodeopMetrics(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        listener.testRunStarted(benchmarkName, 0);
        if (!hasCollectedMetrics) {
            String errorMessage = "Failed to collect inodeop benchmark metrics";
            CLog.i(errorMessage);
            listener.testRunFailed(errorMessage);
            return;
        }
        String inodeopVersion = getInodeopVersion();
        String meanMetricName = String.format("%s-%s", inodeopVersion, "exec_time_avg_ms");
        String stdevMetricName = String.format("%s-%s", inodeopVersion, "exec_time_stdev_ms");
        metrics.put(meanMetricName, String.format("%.4f", executionTimeStats.mean()));
        metrics.put(stdevMetricName, String.format("%.4f", executionTimeStats.stdev()));
        listener.testRunEnded(0, metrics);
    }

    private void dropState() throws DeviceNotAvailableException {
        if (rebootBetweenRuns) getDevice().reboot();
        else getDevice().executeShellCommand("sync; echo 3 > /proc/sys/vm/drop_caches");
    }

    /** Parse inodeop output assuming the `0` version output format. */
    private boolean parseInodeopOutput(String output) {
        /* In case of failure the output is empty and the error message is written to stderr.
         * Instead, in case of success the output format is the following:
         * 0;WORKLOAD_NAME;EXEC_TIME;ms
         */
        if (output == null || output.equals("")) return false;
        String[] fields = output.split(";");
        if (fields.length != InodeopOutputV0.values().length) return false;
        int outputVersion =
                Integer.parseInt(getInodeopOutputField(fields, InodeopOutputV0.VERSION));
        if (outputVersion != 0) return false;

        String timeUnit = getInodeopOutputField(fields, InodeopOutputV0.TIME_UNIT);
        if (!timeUnit.equals("ms")) return false;
        double executionTime =
                Double.parseDouble(getInodeopOutputField(fields, InodeopOutputV0.EXEC_TIME));
        executionTimeStats.add(executionTime);
        return true;
    }

    public enum InodeopOutputV0 {
        VERSION,
        WORKLOAD,
        EXEC_TIME,
        TIME_UNIT;
    }

    public static String getInodeopOutputField(String[] inodeOutputFields, InodeopOutputV0 field) {
        return inodeOutputFields[field.ordinal()].trim();
    }

    public static String buildInodeopCommand(
            String inodeopBinary,
            String directory,
            String fromDirectory,
            String toDirectory,
            int nFiles,
            boolean maintainState,
            String workload) {
        if (inodeopBinary == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(inodeopBinary);
        if (directory != null) {
            sb.append(" -d ");
            sb.append(directory);
        }
        if (fromDirectory != null) {
            sb.append(" -f ");
            sb.append(fromDirectory);
        }
        if (toDirectory != null) {
            sb.append(" -t ");
            sb.append(toDirectory);
        }
        if (maintainState) sb.append(" -s");
        sb.append(" -n ");
        sb.append(nFiles);
        // inodeop requires the workload type to come last.
        if (workload != null) {
            sb.append(" -w ");
            sb.append(workload);
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
