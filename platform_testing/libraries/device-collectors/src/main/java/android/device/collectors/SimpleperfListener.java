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
package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.helpers.SimpleperfHelper;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link SimpleperfListener} that captures simpleperf samples for a test run or per test method
 * run and saves the results under
 * <root_folder>/<test_display_name>/SimpleperfListener/<test_display_name>-<invocation_count>.perf
 */
@OptionClass(alias = "simpleperf-collector")
public class SimpleperfListener extends BaseMetricListener {

    // Default output folder to store the simpleperf sample files.
    private static final String DEFAULT_OUTPUT_ROOT = "/sdcard/test_results";
    // Default subcommand passed to simpleperf
    private static final String DEFAULT_SUBCOMMAND = "record";
    // Default arguments passed to simpleperf command
    private static final String DEFAULT_ARGUMENTS = "-g --post-unwind=yes -f 500 -a --exclude-perf";
    // Destination directory to save the trace results.
    private static final String TEST_OUTPUT_ROOT = "test_output_root";
    // Simpleperf file path key.
    private static final String SIMPLEPERF_FILE_PATH = "simpleperf_file_path";
    // Argument determining whether we collect for the entire run, or per test.
    public static final String COLLECT_PER_RUN = "per_run";
    public static final String SIMPLEPERF_PREFIX = "simpleperf_";
    // Skip failure metrics collection if set to true.
    public static final String SKIP_TEST_FAILURE_METRICS = "skip_test_failure_metrics";
    // Subcommand to pass to simpleperf on start.
    public static final String SUBCOMMAND = "subcommand";
    // Arguments to pass to simpleperf on start.
    public static final String ARGUMENTS = "arguments";
    // Arguments to record specific processes separated by commas and spaces are ignored.
    // Ex. "surfaceflinger,system_server"
    public static final String PROCESSES = "processes_to_record";
    // Arguments to record specific events separated by commas and spaces are ignored.
    // Ex. "instructions,cpu-cycles"
    public static final String EVENTS = "events_to_record";
    // Argument to determine if report is generated after recording.
    public static final String REPORT = "report";
    // Report events per symbol. Symbols are separated by the key to identify the symbol and the
    // substring used to search for the symbol.
    // Ex. "writeInt32;android::Parcel::writeInt32(;commit;android::SurfaceFlinger::commit("
    // symbols matching the substring "android::Parcel::writeInt32(" will be reported as
    // "writeInt32" and
    // symbols matching "android::SurfaceFlinger::commit(" will be reported as "commit"
    public static final String REPORT_SYMBOLS = "symbols_to_report";
    // Test iterations used to divide any reported event counts.
    public static final String TEST_ITERATIONS = "test_iterations";

    // Skips recording simpleperf. If this is set, then the test is responsible for starting and
    // stopping simpleperf. The listener can still be used for reporting metrics.
    public static final String RECORD = "record";

    // Simpleperf samples collected during the test will be saved under this root folder.
    private String mTestOutputRoot;
    // Store the method name and invocation count to create a unique filename for each trace.
    private Map<String, Integer> mTestIdInvocationCount = new HashMap<>();
    private boolean mSimpleperfStartSuccess = false;
    private boolean mIsCollectPerRun;
    private boolean mIsTestFailed = false;
    private boolean mSkipTestFailureMetrics;
    private String mSubcommand;
    private String mArguments;
    private Map<String, String> mProcessToPid = new HashMap<>();
    private boolean mReport;
    private Map<String, String> mSymbolToMetricKey = new HashMap<>();
    private int mTestIterations;
    private boolean mRecord;

    private SimpleperfHelper mSimpleperfHelper = new SimpleperfHelper();

    public SimpleperfListener() {
        super();
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    SimpleperfListener(Bundle args, SimpleperfHelper helper, Map invocationMap) {
        super(args);
        mSimpleperfHelper = helper;
        mTestIdInvocationCount = invocationMap;
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        Bundle args = getArgsBundle();

        // Whether to collect for the entire run, or per test.
        mIsCollectPerRun = Boolean.parseBoolean(args.getString(COLLECT_PER_RUN));

        // Destination folder in the device to save all simpleperf sample files.
        // Defaulted to /sdcard/test_results if test_output_root is not passed.
        mTestOutputRoot = args.getString(TEST_OUTPUT_ROOT, DEFAULT_OUTPUT_ROOT);

        // By default this flag is set to false to collect metrics on test failure.
        mSkipTestFailureMetrics = "true".equals(args.getString(SKIP_TEST_FAILURE_METRICS));

        // Subcommand passed to simpleperf. Defaults to record.
        mSubcommand = args.getString(SUBCOMMAND, DEFAULT_SUBCOMMAND);

        // Command arguments passed to simpleperf.
        mArguments = args.getString(ARGUMENTS, DEFAULT_ARGUMENTS);

        // Processes passed into recording arguments for simpleperf.
        String processes = args.getString(PROCESSES, "");
        String[] individualProcesses = processes.trim().split("\\s*,\\s*");

        // Events passed into recording arguments for simpleperf.
        String events = args.getString(EVENTS, "");
        String[] individualEvents = events.trim().split("\\s*,\\s*");

        // Whether to generate report after recording or not, by default set to false.
        mReport = "true".equals(args.getString(REPORT));

        // Symbols to look for when reporting events for processes.
        String[] symbolAndMetricKey = args.getString(REPORT_SYMBOLS, "").trim().split("\\s*;\\s*");
        for (int i = 0; i < symbolAndMetricKey.length - 1; i += 2) {
            mSymbolToMetricKey.put(symbolAndMetricKey[i + 1], symbolAndMetricKey[i]);
        }

        // Appending recording argument for recording specified events if given.
        if (!events.isEmpty()) {
            mArguments += " -e ";
            mArguments += String.join(",", individualEvents);
        }
        // Appending recording argument for recording specified processes if given.
        if (!processes.isEmpty()) {
            for (String process : individualProcesses) {
                mProcessToPid.put(process, mSimpleperfHelper.getPID(process));
            }
            mArguments += " -p " + String.join(",", mProcessToPid.values());
        }

        mTestIterations = Integer.parseInt(args.getString(TEST_ITERATIONS, "1"));
        Log.i(getTag(), "onTestRunStart arguments mTestIterations=" + mTestIterations);
        mRecord = Boolean.parseBoolean(args.getString(RECORD, "true"));

        if (!mIsCollectPerRun) {
            return;
        }

        Log.i(getTag(), "Starting simpleperf before test run started.");
        startSimpleperf(mSubcommand, mArguments);
    }

    @Override
    public void onTestStart(DataRecord testData, Description description) {
        mIsTestFailed = false;
        if (mIsCollectPerRun) {
            return;
        }

        mTestIdInvocationCount.compute(
                getTestFileName(description), (key, value) -> (value == null) ? 1 : value + 1);
        Log.i(getTag(), "Starting simpleperf before test started.");
        startSimpleperf(mSubcommand, mArguments);
    }

    @Override
    public void onTestFail(DataRecord testData, Description description, Failure failure) {
        mIsTestFailed = true;
    }

    @Override
    public void onTestEnd(DataRecord testData, Description description) {
        if (mIsCollectPerRun) {
            return;
        }

        if (!mSimpleperfStartSuccess) {
            Log.i(
                    getTag(),
                    "Skipping simpleperf stop attempt onTestEnd because simpleperf did not start"
                            + " successfully");
            return;
        }

        if (mSkipTestFailureMetrics && mIsTestFailed) {
            Log.i(getTag(), "Skipping metric collection due to test failure");
            // Stop the existing simpleperf session.
            try {
                if (!mSimpleperfHelper.stopSimpleperf()) {
                    Log.e(getTag(), "Failed to stop the simpleperf process.");
                }
            } catch (IOException e) {
                Log.e(getTag(), "Failed to stop simpleperf", e);
            }
        } else {
            Log.i(getTag(), "Stopping simpleperf after test ended.");
            // Construct test output directory in the below format
            // <root_folder>/<test_name>/SimpleperfListener/<test_name>-<count>.data
            Path path =
                    Paths.get(
                            mTestOutputRoot,
                            getTestFileName(description),
                            this.getClass().getSimpleName(),
                            String.format(
                                    "%s%s-%d.data",
                                    SIMPLEPERF_PREFIX,
                                    getTestFileName(description),
                                    mTestIdInvocationCount.get(getTestFileName(description))));
            stopSimpleperf(path, testData);
            if (mReport) {
                getSimpleperfReport(path, testData);
            }
        }
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        if (!mIsCollectPerRun) {
            return;
        }

        if (!mSimpleperfStartSuccess) {
            Log.i(getTag(), "Skipping simpleperf stop attempt as simpleperf failed to start.");
            return;
        }

        Log.i(getTag(), "Stopping simpleperf after test run ended");
        String uniqueId = Integer.toString(UUID.randomUUID().hashCode());
        Path path =
                Paths.get(
                        mTestOutputRoot,
                        this.getClass().getSimpleName(),
                        String.format("%s%s.data", SIMPLEPERF_PREFIX, uniqueId));
        stopSimpleperf(path, runData);
        if (mReport) {
            getSimpleperfReport(path, runData);
        }
    }

    /** Start simpleperf sampling. */
    public void startSimpleperf(String subcommand, String arguments) {
        mSimpleperfStartSuccess =
                !mRecord || mSimpleperfHelper.startCollecting(subcommand, arguments);
        if (!mSimpleperfStartSuccess) {
            Log.e(getTag(), "Simpleperf did not start successfully.");
        }
    }

    /** Stop simpleperf sampling and dump the collected file into the given path. */
    private void stopSimpleperf(Path path, DataRecord record) {
        boolean status = false;
        if (mRecord) {
            status = mSimpleperfHelper.stopCollecting(path.toString());
        } else {
            status = mSimpleperfHelper.copyFileOutput(path.toString());
        }
        if (!status) {
            Log.e(getTag(), "Failed to collect the simpleperf output.");
        } else {
            record.addStringMetric(SIMPLEPERF_FILE_PATH, path.toString());
        }
    }

    /**
     * Generate simpleperf report from an existing record file then add parsed metrics to
     * DataRecord.
     *
     * @param path Path to read binary record from.
     * @param data DataRecord to store metrics parsed from report
     */
    private void getSimpleperfReport(Path path, DataRecord data) {
        for (Map.Entry<String, String> entry : mProcessToPid.entrySet()) {
            Map<String, String> metricPerProcess =
                    mSimpleperfHelper.getSimpleperfReport(
                            path.toString(), entry, mSymbolToMetricKey, mTestIterations);
            Log.i(getTag(), "Simpleperf Metrics report collected. " + metricPerProcess);
            for (Map.Entry<String /*event-process-symbol*/, String /*eventCount*/> metric :
                    metricPerProcess.entrySet()) {
                data.addStringMetric(metric.getKey(), metric.getValue());
            }
        }
    }

    /**
     * Returns the packagename.classname_methodname which has no special characters and is used to
     * create file names.
     */
    public static String getTestFileName(Description description) {
        return String.format("%s_%s", description.getClassName(), description.getMethodName());
    }
}
