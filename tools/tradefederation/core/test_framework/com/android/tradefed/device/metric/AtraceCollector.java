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

import com.android.ddmlib.NullOutputReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IMetricCollector} that runs atrace during a test and collects the result and log
 * them to the invocation.
 */
@OptionClass(alias = "atrace")
public class AtraceCollector extends BaseDeviceMetricCollector {

    @Option(name = "categories",
            description = "the tracing categories atrace will capture")
    private List<String> mCategories = new ArrayList<>();

    @Option(
        name = "log-path",
        description = "the temporary location the trace log will be saved to on device"
    )
    private String mLogPath = "/data/local/tmp/";

    @Option(
        name = "log-filename",
        description = "the temporary location the trace log will be saved to on device"
    )
    private String mLogFilename = "atrace";

    @Option(name = "preserve-ondevice-log",
            description = "delete the trace log on the target device after the host collects it")
    private boolean mPreserveOndeviceLog = false;

    @Option(name = "compress-dump",
            description = "produce a compressed trace dump")
    private boolean mCompressDump = true;

    @Option(name = "atrace-on-boot",
            description = "enable atrace collection for bootup")
    private boolean mTraceOnBoot = false;

    @Option(name = "skip-atrace-start",
            description = "Skip atrace start if the option is enabled. Needed when atrace is"
                + "enabled through fastboot option.")
    private boolean mSkipAtraceStart = false;

    /* These options will arrange a post processing executable binary to be ran on the collected
     * trace.
     * E.G.
     * <option name="post-process-binary" value="/path/to/analyzer.par"/>
     * <option name="post-process-input-file-key" value="TRACE_FILE"/>
     * <option name="post-process-args" value="--input_file TRACE_FILE --arg1 --arg2"/>
     * Will be executed as
     * /path/to/analyzer.par --input_file TRACE_FILE --arg1 --arg2
     */
    @Option(
        name = "post-process-input-file-key",
        description =
                "The string that will be replaced with the absolute path to the trace file "
                        + "in post-process-args"
    )
    private String mLogProcessingTraceInput = "TRACE_FILE";

    @Option(
        name = "post-process-binary",
        description = "a self-contained binary that will be executed on the trace file"
    )
    private File mLogProcessingBinary = null;

    @Option(name = "post-process-args", description = "args for the binary")
    private List<String> mLogProcessingArgs = new ArrayList<>();

    @Option(
        name = "post-process-timeout",
        isTimeVal = true,
        description =
                "The amount of time (eg, 1m2s) that Tradefed will wait for the "
                        + "postprocessing subprocess to finish"
    )
    private long mLogProcessingTimeoutMilliseconds = 0;

    /* If the tool is producing files to upload, this can be used to key in to which files are
     * produced and upload them.
     * The first matching group in the regex is treated as a file and uploaded.
     * Eg, if this output is produced by the postprocessing binary:
     *
     * my-metric-name /tmp/a.txt
     *
     * then setting this option to: "my-metric-name (.*txt)" will upload /tmp/a.txt
     *
     * If not set, or the file cannot be found, the tool will still upload its stdout and stderr.
     * Can be specified multiple times.
     */
    @Option(
            name = "post-process-output-file-regex",
            description =
                    "A regex that will be applied to the stdout of the post processing program."
                            + "the first matching group will be treated as a file and uploaded as "
                            + "a test log.")
    private List<String> mLogProcessingOutputRegex = new ArrayList<>();

    private IRunUtil mRunUtil = RunUtil.getDefault();

    private Thread mThread;

    private static final long DEVICE_OFFLINE_TIMEOUT_MS = 60 * 1000;
    private static final long DEVICE_ONLINE_TIMEOUT_MS = 60 * 1000;

    protected String fullLogPath() {
        return Paths.get(mLogPath, mLogFilename + "." + getLogType().getFileExt()).toString();
    }

    protected LogDataType getLogType() {
        if (mCompressDump) {
            return LogDataType.ATRACE;
        } else {
            return LogDataType.TEXT;
        }
    }

    protected void startTracing(ITestDevice device) throws DeviceNotAvailableException {
        //atrace --async_start will set a variety of sysfs entries, and then exit.
        String cmd = "atrace --async_start ";
        if (mCompressDump) {
            cmd += "-z ";
        }
        cmd += String.join(" ", mCategories);
        CollectingOutputReceiver c = new CollectingOutputReceiver();
        CLog.i("issuing command : %s to device: %s", cmd, device.getSerialNumber());
        device.executeShellCommand(cmd, c, 1, TimeUnit.SECONDS, 1);
        CLog.i("command output: %s", c.getOutput());
    }

    @Override
    public void onTestStart(DeviceMetricData testData) throws DeviceNotAvailableException {
        if(mSkipAtraceStart) {
            CLog.d("Skip atrace start because tracing is enabled through fastboot option");
            return;
        }
        if (mCategories.isEmpty()) {
            CLog.d("no categories specified to trace, not running AtraceMetricCollector");
            return;
        }

        if (mTraceOnBoot) {
            mThread = new Thread(() -> {
                try {
                    for (ITestDevice device : getDevices()) {
                        // wait for device reboot
                        device.waitForDeviceNotAvailable(DEVICE_OFFLINE_TIMEOUT_MS);
                        device.waitForDeviceOnline(DEVICE_ONLINE_TIMEOUT_MS);
                        // wait for device to be in root
                        device.waitForDeviceNotAvailable(DEVICE_OFFLINE_TIMEOUT_MS);
                        device.waitForDeviceOnline();
                        startTracing(device);
                    }
                } catch (DeviceNotAvailableException e) {
                    CLog.e("Error starting atrace");
                    CLog.e(e);
                }
            });
            mThread.setDaemon(true);
            mThread.setName("AtraceCollector-on-boot");
            mThread.start();
        } else {
            for (ITestDevice device : getDevices()) {
                startTracing(device);
            }
        }
    }

    protected void stopTracing(ITestDevice device) throws DeviceNotAvailableException {
        CLog.i("collecting atrace log from device: %s", device.getSerialNumber());
        device.executeShellCommand(
                "atrace --async_stop -z -c -o " + fullLogPath(),
                new NullOutputReceiver(),
                300,
                TimeUnit.SECONDS,
                1);
        CLog.d("Trace collected successfully.");
    }

    private void postProcess(File trace) {
        if (mLogProcessingBinary == null
                || !mLogProcessingBinary.exists()
                || !mLogProcessingBinary.canExecute()) {
            CLog.w("No trace postprocessor specified. Skipping trace postprocessing.");
            return;
        }

        List<String> commandLine = new ArrayList<String>();
        commandLine.add(mLogProcessingBinary.getAbsolutePath());
        for (String entry : mLogProcessingArgs) {
            commandLine.add(entry.replaceAll(mLogProcessingTraceInput, trace.getAbsolutePath()));
        }

        String[] commandLineArr = new String[commandLine.size()];
        commandLine.toArray(commandLineArr);
        CommandResult result =
                mRunUtil.runTimedCmd(mLogProcessingTimeoutMilliseconds, commandLineArr);

        CLog.v(
                "Trace postprocessing status: %s\nstdout: %s\nstderr: ",
                result.getStatus(), result.getStdout(), result.getStderr());
        if (result.getStdout() == null) {
            return;
        }

        for (String regex : mLogProcessingOutputRegex) {
            Pattern pattern = Pattern.compile(regex);
            for (String line : result.getStdout().split("\n")) {
                Matcher m = pattern.matcher(line);
                if (m.find() && m.groupCount() == 1) {
                    File f = new File(m.group(1));
                    if (f.exists() && !f.isDirectory()) {
                        LogDataType type;
                        switch (FileUtil.getExtension(f.getName())) {
                            case ".png":
                                type = LogDataType.PNG;
                                break;
                            case ".txt":
                                type = LogDataType.TEXT;
                                break;
                            default:
                                type = LogDataType.UNKNOWN;
                                break;
                        }
                        try (FileInputStreamSource stream = new FileInputStreamSource(f)) {
                            testLog(FileUtil.getBaseName(f.getName()), type, stream);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData,
            final Map<String, Metric> currentTestCaseMetrics,
            TestDescription test)
            throws DeviceNotAvailableException {


        if (!mSkipAtraceStart && mCategories.isEmpty()) {
            return;
        }

        // Stop and collect the atrace only if the atrace start is skipped which
        // then uses the default categories or if the categories are explicitly
        // passed.
        for (ITestDevice device : getDevices()) {
            try {
                stopTracing(device);

                File trace = device.pullFile(fullLogPath());
                if (trace != null) {
                    CLog.i("Log size: %s bytes", String.valueOf(trace.length()));

                    try (FileInputStreamSource streamSource = new FileInputStreamSource(trace)) {
                        testLog(
                                mLogFilename + "_" + test + device.getSerialNumber() + "_",
                                getLogType(),
                                streamSource);
                    }

                    postProcess(trace);
                    trace.delete();
                } else {
                    throw new DeviceRuntimeException(
                            String.format("failed to pull log: %s", fullLogPath()),
                            DeviceErrorIdentifier.FAIL_PULL_FILE);
                }

                if (!mPreserveOndeviceLog) {
                    device.deleteFile(fullLogPath());
                }
                else {
                    CLog.w("preserving ondevice atrace log: %s", fullLogPath());
                }
            } catch (DeviceRuntimeException e) {
                CLog.e("Error retrieving atrace log! device not available:");
                CLog.e(e);
            }
        }
    }

    @VisibleForTesting
    void setRunUtil(IRunUtil util) {
        mRunUtil = util;
    }
}
