/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.device.collectors;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.PerfettoHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A base {@link PerfettoTracingStrategy} that allows capturing traces during testing and save the
 * perfetto trace files under <root>/<test_name>/PerfettoTracingStrategy/
 */
public abstract class PerfettoTracingStrategy {
    // Option to pass the folder name which contains the perfetto trace config file.
    private static final String PERFETTO_CONFIG_ROOT_DIR_ARG = "perfetto_config_root_dir";
    // Default folder name to look for the perfetto config file.
    // Argument to indicate the perfetto output file prefix
    public static final String PERFETTO_CONFIG_OUTPUT_FILE_PREFIX =
            "perfetto_config_output_file_prefix";
    private static final String DEFAULT_PERFETTO_CONFIG_ROOT_DIR = "/data/misc/perfetto-traces/";
    // Collect per run if it is set to true otherwise collect per test.
    // Default perfetto config file name.
    private static final String DEFAULT_CONFIG_FILE = "trace_config.pb";
    // Default perfetto config file name when text proto config is used.
    private static final String DEFAULT_TEXT_CONFIG_FILE = "trace_config.textproto";
    // Argument to get custom config file name for collecting the trace.
    private static final String PERFETTO_CONFIG_FILE_ARG = "perfetto_config_file";
    // Skip failure metrics collection if this flag is set to true.
    public static final String SKIP_TEST_FAILURE_METRICS = "skip_test_failure_metrics";
    // Skip success metrics collection if this flag is set to true (i.e. collect only failures).
    public static final String SKIP_TEST_SUCCESS_METRICS = "skip_test_success_metrics";
    // Perfetto file path key.
    protected static final String PERFETTO_FILE_PATH = "perfetto_file_path";
    // Argument to get custom time in millisecs to wait before dumping the trace.
    // This has to be at least the dump interval time set in the trace config file
    // or greater than that. Otherwise, we will miss trace information from the test.
    private static final String PERFETTO_WAIT_TIME_ARG = "perfetto_wait_time_ms";
    // Argument to indicate the perfetto config file is text proto file.
    public static final String PERFETTO_CONFIG_TEXT_PROTO = "perfetto_config_text_proto";
    // Argument to indicate the perfetto config content in a textual format
    public static final String PERFETTO_CONFIG_TEXT_CONTENT = "perfetto_config_text_content";
    // Destination directory to save the trace results.
    private static final String TEST_OUTPUT_ROOT = "test_output_root";
    // Default output folder to store the perfetto output traces.
    private static final String DEFAULT_OUTPUT_ROOT = "/sdcard/test_results";
    // Default wait time before stopping the perfetto trace.
    private static final String DEFAULT_WAIT_TIME_MSECS = "0";
    // Argument to get custom time in millisecs to wait before starting the
    // perfetto trace.
    public static final String PERFETTO_START_WAIT_TIME_ARG = "perfetto_start_wait_time_ms";
    // Default prefix for the output file
    public static final String DEFAULT_PERFETTO_PREFIX = "perfetto_";
    // Default wait time before starting the perfetto trace.
    public static final String DEFAULT_START_WAIT_TIME_MSECS = "0";
    // Regular expression pattern to identify multiple spaces.
    public static final String SPACES_PATTERN = "\\s+";
    // Space replacement value
    public static final String REPLACEMENT_CHAR = "#";
    // For USB disconnected cases you may want this option to be true. This option makes sure
    // the device does not go to sleep while collecting.
    public static final String PERFETTO_START_BG_WAIT = "perfetto_start_bg_wait";

    @VisibleForTesting
    static final String HOLD_WAKELOCK_WHILE_COLLECTING = "hold_wakelock_while_collecting";

    private boolean mHoldWakelockWhileCollecting;

    private final WakeLockContext mWakeLockContext;
    private final Supplier<PowerManager.WakeLock> mWakelockSupplier;
    private final WakeLockAcquirer mWakeLockAcquirer;
    private final WakeLockReleaser mWakeLockReleaser;
    private final Instrumentation mInstr;

    private PerfettoHelper mPerfettoHelper = new PerfettoHelper();
    // Wait time can be customized based on the dump interval set in the trace config.
    private long mWaitTimeInMs;
    // Wait time can be customized based on how much time to wait before starting the
    // trace.
    private long mWaitStartTimeInMs;
    // Trace config file name to use while collecting the trace which is defaulted to
    // trace_config.pb. It can be changed via the perfetto_config_file arg.
    private String mConfigFileName;
    // Perfetto traces collected during the test will be saved under this root folder.
    private String mTestOutputRoot;
    private boolean mIsConfigTextProto = false;
    private String mConfigContent;
    protected boolean mSkipTestFailureMetrics;
    private boolean mSkipTestSuccessMetrics;
    private boolean mIsTestFailed = false;
    // Store the method name and invocation count to create unique file name for each trace.
    private boolean mPerfettoStartSuccess = false;
    private String mOutputFilePrefix;

    PerfettoTracingStrategy(Instrumentation instr) {
        super();
        mInstr = instr;
        mWakeLockContext = this::runWithWakeLock;
        mWakelockSupplier = this::getWakeLock;
        mWakeLockAcquirer = this::acquireWakelock;
        mWakeLockReleaser = this::releaseWakelock;
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    PerfettoTracingStrategy(PerfettoHelper helper, Instrumentation instr) {
        super();
        mPerfettoHelper = helper;
        mInstr = instr;
        mWakeLockContext = this::runWithWakeLock;
        mWakelockSupplier = this::getWakeLock;
        mWakeLockAcquirer = this::acquireWakelock;
        mWakeLockReleaser = this::releaseWakelock;
    }
    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    PerfettoTracingStrategy(
            PerfettoHelper helper,
            Instrumentation instr,
            WakeLockContext wakeLockContext,
            Supplier<PowerManager.WakeLock> wakelockSupplier,
            WakeLockAcquirer wakeLockAcquirer,
            WakeLockReleaser wakeLockReleaser) {
        super();
        mPerfettoHelper = helper;
        mInstr = instr;
        mWakeLockContext = wakeLockContext;
        mWakeLockAcquirer = wakeLockAcquirer;
        mWakeLockReleaser = wakeLockReleaser;
        mWakelockSupplier = wakelockSupplier;
    }

    PerfettoHelper getPerfettoHelper() {
        return mPerfettoHelper;
    }

    void testFail(DataRecord testData, Description description, Failure failure) {
        mIsTestFailed = true;
    }

    void testRunStart(DataRecord runData, Description description) {
        // Clean up any perfetto process from previous test runs.
        if (!mPerfettoHelper.getPerfettoPids().isEmpty()) {
            try {
                if (mPerfettoHelper.stopPerfettoProcesses(mPerfettoHelper.getPerfettoPids())) {
                    Log.i(
                            getTag(),
                            "Stopped the already running perfetto tracing before the new test run"
                                    + " start.");
                }
            } catch (IOException e) {
                Log.e(getTag(), "Failed to stop the perfetto.", e);
            }
        } else {
            Log.i(getTag(), "No perfetto process running before the test run starts.");
        }
    }

    void testStart(DataRecord testData, Description description) {
        mIsTestFailed = false;
    }

    void testEnd(DataRecord testData, Description description) {
        // No-op
    }

    void testRunEnd(DataRecord runData, Result result) {
        // No-op
    }

    /** Start perfetto tracing using the given config file. */
    protected void startPerfettoTracing() {
        boolean perfettoStartSuccess;
        SystemClock.sleep(mWaitStartTimeInMs);

        perfettoStartSuccess =
                mPerfettoHelper
                        .setTextProtoConfig(mConfigContent)
                        .setConfigFileName(mConfigFileName)
                        .setIsTextProtoConfig(mIsConfigTextProto)
                        .startCollecting();
        if (!perfettoStartSuccess) {
            Log.e(getTag(), "Perfetto did not start successfully.");
        }

        setPerfettoStartSuccess(perfettoStartSuccess);
    }

    /**
     * Stop perfetto tracing and dumping the collected trace file in given path and updating the
     * record with the path to the trace file.
     */
    protected boolean stopPerfettoTracing(Path path) {
        boolean success = mPerfettoHelper.stopCollecting(mWaitTimeInMs, path.toString());
        if (!success) {
            Log.e(getTag(), "Failed to collect the perfetto output.");
        }
        setPerfettoStartSuccess(false);
        return success;
    }

    /**
     * Stop perfetto tracing and dumping the collected trace file in given path and updating the
     * record with the path to the trace file.
     */
    protected void stopPerfettoTracingAndReportMetric(Path path, DataRecord record) {
        if (stopPerfettoTracing(path)) {
            record.addStringMetric(PERFETTO_FILE_PATH, path.toString());
        }
    }

    protected void stopPerfettoTracingWithoutMetric() {
        // Stop the existing perfetto trace collection.
        try {
            if (!mPerfettoHelper.stopPerfetto(mPerfettoHelper.getPerfettoPid())) {
                Log.e(getTag(), "Failed to stop the perfetto process.");
            }
            setPerfettoStartSuccess(false);
        } catch (IOException e) {
            Log.e(getTag(), "Failed to stop the perfetto.", e);
        }
    }

    protected void setPerfettoStartSuccess(boolean success) {
        mPerfettoStartSuccess = success;
    }

    protected boolean isPerfettoStartSuccess() {
        return mPerfettoStartSuccess;
    }

    protected String getOutputFilePrefix() {
        return mOutputFilePrefix;
    }

    protected String getTestOutputRoot() {
        return mTestOutputRoot;
    }

    protected boolean skipMetric() {
        return (mSkipTestFailureMetrics && mIsTestFailed)
                || (mSkipTestSuccessMetrics && !mIsTestFailed);
    }

    protected void runTask(Runnable task, String message) {
        if (mHoldWakelockWhileCollecting) {
            if (!message.isEmpty()) {
                Log.d(getTag(), message);
            }
            mWakeLockContext.run(task);
        } else {
            task.run();
        }
    }

    @VisibleForTesting
    void runWithWakeLock(Runnable runnable) {
        PowerManager.WakeLock wakelock = null;
        try {
            wakelock = mWakelockSupplier.get();
            mWakeLockAcquirer.acquire(wakelock);
            runnable.run();
        } finally {
            mWakeLockReleaser.release(wakelock);
        }
    }

    @VisibleForTesting
    public void acquireWakelock(PowerManager.WakeLock wakelock) {
        if (wakelock != null) {
            Log.d(getTag(), "wakelock.isHeld: " + wakelock.isHeld());
            Log.d(getTag(), "acquiring wakelock.");
            Log.d(getTag(), "wakelock acquired.");
            Log.d(getTag(), "wakelock.isHeld: " + wakelock.isHeld());
        }
    }

    @VisibleForTesting
    public void releaseWakelock(PowerManager.WakeLock wakelock) {
        if (wakelock != null) {
            Log.d(getTag(), "wakelock.isHeld: " + wakelock.isHeld());
            Log.d(getTag(), "releasing wakelock.");
            wakelock.release();
            Log.d(getTag(), "wakelock released.");
            Log.d(getTag(), "wakelock.isHeld: " + wakelock.isHeld());
        }
    }

    private PowerManager.WakeLock getWakeLock() {
        PowerManager pm =
                (PowerManager) mInstr.getContext().getSystemService(Context.POWER_SERVICE);

        return pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, PerfettoTracingStrategy.class.getName());
    }

    String getTag() {
        return this.getClass().getName();
    }

    interface WakeLockContext {
        void run(Runnable runnable);
    }

    interface WakeLockAcquirer {
        void acquire(PowerManager.WakeLock wakelock);
    }

    interface WakeLockReleaser {
        void release(PowerManager.WakeLock wakelock);
    }

    void setup(Bundle args) {

        boolean perfettoStartBgWait =
                Boolean.parseBoolean(args.getString(PERFETTO_START_BG_WAIT, String.valueOf(true)));
        mPerfettoHelper.setPerfettoStartBgWait(perfettoStartBgWait);

        // Root directory path containing the perfetto config file.
        String configRootDir =
                args.getString(PERFETTO_CONFIG_ROOT_DIR_ARG, DEFAULT_PERFETTO_CONFIG_ROOT_DIR);
        if (!configRootDir.endsWith("/")) {
            configRootDir = configRootDir.concat("/");
        }
        mPerfettoHelper.setPerfettoConfigRootDir(configRootDir);

        // Whether the config is text proto or not. By default set to false.
        mIsConfigTextProto = Boolean.parseBoolean(args.getString(PERFETTO_CONFIG_TEXT_PROTO));

        // Perfetto config file has to be under /data/misc/perfetto-traces/
        // defaulted to DEFAULT_TEXT_CONFIG_FILE or DEFAULT_CONFIG_FILE if perfetto_config_file is
        // not passed.
        mConfigFileName =
                args.getString(
                        PERFETTO_CONFIG_FILE_ARG,
                        mIsConfigTextProto ? DEFAULT_TEXT_CONFIG_FILE : DEFAULT_CONFIG_FILE);

        mConfigContent = args.getString(PERFETTO_CONFIG_TEXT_CONTENT, "");

        mOutputFilePrefix =
                args.getString(PERFETTO_CONFIG_OUTPUT_FILE_PREFIX, DEFAULT_PERFETTO_PREFIX);

        // Whether to hold wakelocks on all Prefetto tracing functions. You may want to enable
        // this if your device is not USB connected. This option prevents the device from
        // going into suspend mode while this listener is running intensive tasks.
        mHoldWakelockWhileCollecting =
                Boolean.parseBoolean(args.getString(HOLD_WAKELOCK_WHILE_COLLECTING));

        // Wait time before stopping the perfetto trace collection after the test
        // is completed. Defaulted to 0 msecs.
        mWaitTimeInMs =
                Long.parseLong(args.getString(PERFETTO_WAIT_TIME_ARG, DEFAULT_WAIT_TIME_MSECS));

        // Wait time before the perfetto trace is started.
        mWaitStartTimeInMs =
                Long.parseLong(
                        args.getString(
                                PERFETTO_START_WAIT_TIME_ARG, DEFAULT_START_WAIT_TIME_MSECS));

        // Destination folder in the device to save all the trace files.
        // Defaulted to /sdcard/test_results if test_output_root is not passed.
        mTestOutputRoot = args.getString(TEST_OUTPUT_ROOT, DEFAULT_OUTPUT_ROOT);

        // By default, this flag is set to false to collect the metrics on test failure.
        mSkipTestFailureMetrics = "true".equals(args.getString(SKIP_TEST_FAILURE_METRICS));
        mSkipTestSuccessMetrics = "true".equals(args.getString(SKIP_TEST_SUCCESS_METRICS));
    }

    /**
     * Returns the packagename.classname_methodname which has no spaces and used to create file
     * names.
     */
    public static String getTestFileName(Description description) {
        return String.format(
                "%s_%s",
                sanitizeString(description.getClassName()),
                sanitizeString(description.getMethodName()));
    }

    protected static String sanitizeString(String value) {
        return value.replaceAll(SPACES_PATTERN, REPLACEMENT_CHAR).trim();
    }
}
