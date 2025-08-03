/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.catbox.result;

import com.android.annotations.VisibleForTesting;

import com.android.catbox.util.TestMetricsUtil;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.util.CollectorUtil;
import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.android.ddmlib.Log.LogLevel;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;

import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import com.android.tradefed.testtype.suite.ModuleDefinition;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JsonResultReporter aggregates and writes performance test metrics to a Json file. */
@OptionClass(alias = "json-result-reporter")
public class JsonResultReporter implements ITestInvocationListener {
    private CompatibilityBuildHelper mBuildHelper;
    private IInvocationContext mContext;
    private IInvocationContext mModuleContext;
    private IBuildInfo mBuildInfo;
    private TestMetricsUtil mTestMetricsUtil;

    @Option(
            name = "dest-dir",
            description =
                    "The directory under the result to store the files. "
                            + "Default to 'report-log-files'.")
    private String mDestDir = "report-log-files";

    private String mTempReportFolder = "temp-report-logs";

    @Option(name = "report-log-name", description = "Name of the JSON report file.")
    private String mReportLogName = null;

    @Option(
            name = "report-test-name-mapping",
            description = "Mapping for test name to use in report.")
    private Map<String, String> mReportTestNameMap = new HashMap<String, String>();

    @Option(
            name = "exclude-metric-from-test-name",
            description = "Exclude metric from test name in report.")
    private Map<String, String> mExcludeMetricTestNameMap = new HashMap<String, String>();


    @Option(
            name = "report-all-metrics",
            description = "Report all the generated metrics. Default to 'true'.")
    private boolean mReportAllMetrics = true;

    @Option(
            name = "report-metric-key-mapping",
            description =
                    "Mapping for Metric Keys to be reported. "
                            + "Only report the keys provided in the mapping.")
    private Map<String, String> mReportMetricKeyMap = new HashMap<String, String>();

    @Option(name = "test-iteration-separator", description = "Separator used in between the test"
            + " class name and the iteration number. Default separator is '$'")
    private String mTestIterationSeparator = "$";

    @Option(name = "aggregate-similar-tests", description = "To aggregate the metrics from test"
            + " cases which differ only by iteration number or having the same test name."
            + " Used only in context with the microbenchmark test runner. Set this flag to false"
            + " to disable aggregating the metrics.")
    private boolean mAggregateSimilarTests = false;

    public JsonResultReporter() {
        // Default Constructor
        // Nothing to do
    }

    /**
     * Return the primary build info that was reported via {@link
     * #invocationStarted(IInvocationContext)}. Primary build is the build returned by the first
     * build provider of the running configuration. Returns null if there is no context (no build to
     * test case).
     */
    private IBuildInfo getPrimaryBuildInfo() {
        if (mContext == null) {
            return null;
        } else {
            return mContext.getBuildInfos().get(0);
        }
    }

    /** Create Build Helper */
    @VisibleForTesting
    CompatibilityBuildHelper createBuildHelper() {
        return new CompatibilityBuildHelper(getPrimaryBuildInfo());
    }

    /** Get Device ABI Information */
    @VisibleForTesting
    String getAbiInfo() {
        CLog.logAndDisplay(LogLevel.INFO, "Getting ABI Information.");
        if (mModuleContext == null) {
            // Return Empty String
            return "";
        }
        List<String> abis = mModuleContext.getAttributes().get(ModuleDefinition.MODULE_ABI);
        if (abis == null || abis.isEmpty()) {
            // Return Empty String
            return "";
        }
        if (abis.size() > 1) {
            CLog.logAndDisplay(
                    LogLevel.WARN,
                    String.format(
                            "More than one ABI name specified (using first one): %s",
                            abis.toString()));
        }
        return abis.get(0);
    }

    /** Initialize Test Metrics Util */
    @VisibleForTesting
    TestMetricsUtil initializeTestMetricsUtil() {
        return new TestMetricsUtil();
    }

    /** Initialize configurations for Result Reporter */
    private void initializeReporterConfig() {
        CLog.logAndDisplay(LogLevel.INFO, "Initializing Test Metrics Result Reporter Config.");
        // Initialize Build Info
        mBuildInfo = getPrimaryBuildInfo();

        // Initialize Build Helper
        if (mBuildHelper == null) {
            mBuildHelper = createBuildHelper();
        }

        // Initialize Report Log Name
        // Use test tag as the report name if not provided
        if (mReportLogName == null) {
            mReportLogName = mContext.getTestTag();
        }

        // Initialize Test Metrics Util
        if (mTestMetricsUtil == null) {
            mTestMetricsUtil = initializeTestMetricsUtil();
        }
        mTestMetricsUtil.setIterationSeparator(mTestIterationSeparator);
    }

    /** Re-initialize object to erase all existing test metrics */
    private void reInitializeTestMetricsUtil() {
        mTestMetricsUtil = initializeTestMetricsUtil();
        mTestMetricsUtil.setIterationSeparator(mTestIterationSeparator);
    }

    /** Write Test Metrics to JSON */
    private void writeTestMetrics(
            String classMethodName, Map<String, String> metrics) {

        // Use class method name as stream name if mapping is not provided
        String streamName = classMethodName;
        if (mReportTestNameMap != null && mReportTestNameMap.containsKey(classMethodName)) {
            streamName = mReportTestNameMap.get(classMethodName);
        }

        // Get ABI Info
        String abiName = getAbiInfo();

        // Initialize Metrics Report Log
        // TODO: b/194103027 [Remove MetricsReportLog dependency as it is being deprecated].
        MetricsReportLog reportLog =
                new MetricsReportLog(
                        mBuildInfo, abiName, classMethodName, mReportLogName, streamName);

        // Write Test Metrics in the Log
        if (mReportAllMetrics) {
            // Write all the metrics to the report
            writeAllMetrics(reportLog, metrics);
        } else {
            // Write metrics for given keys to the report
            writeMetricsForGivenKeys(reportLog, metrics, streamName);
        }

        // Submit Report Log
        reportLog.submit();
    }

    /** Write all the metrics to JSON Report */
    private void writeAllMetrics(MetricsReportLog reportLog, Map<String, String> metrics) {
        CLog.logAndDisplay(LogLevel.INFO, "Writing all the metrics to JSON report.");
        for (String key : metrics.keySet()) {
            try {
                double value = Double.parseDouble(metrics.get(key));
                reportLog.addValue(key, value, ResultType.NEUTRAL, ResultUnit.NONE);
            } catch (NumberFormatException exception) {
                CLog.logAndDisplay(
                        LogLevel.ERROR,
                        String.format(
                                "Unable to parse value '%s' for '%s' metric key.",
                                metrics.get(key), key));
            }
        }
        CLog.logAndDisplay(
                LogLevel.INFO, "Successfully completed writing the metrics to JSON report.");
    }

    /** Write given set of metrics to JSON Report */
    private void writeMetricsForGivenKeys(
            MetricsReportLog reportLog, Map<String, String> metrics, String streamName) {
        CLog.logAndDisplay(LogLevel.INFO, "Writing given set of metrics to JSON report.");
        if (mReportMetricKeyMap == null || mReportMetricKeyMap.isEmpty()) {
            CLog.logAndDisplay(
                    LogLevel.WARN, "Skip reporting metrics. Metric keys are not provided.");
            return;
        }
        for (String key : mReportMetricKeyMap.keySet()) {
            if (!metrics.containsKey(key) || metrics.get(key) == null) {
                CLog.logAndDisplay(LogLevel.WARN, String.format("%s metric key is missing.", key));
                continue;
            }
            if(mExcludeMetricTestNameMap != null && mExcludeMetricTestNameMap.get(key) !=null && mExcludeMetricTestNameMap.get(key).equals(streamName)){
                CLog.logAndDisplay(LogLevel.WARN, String.format("Excluding %s metric key from %s test.", key, streamName));
                continue;
            }
            try {
                double value = Double.parseDouble(metrics.get(key));
                reportLog.addValue(
                        mReportMetricKeyMap.get(key), value, ResultType.NEUTRAL, ResultUnit.NONE);
            } catch (NumberFormatException exception) {
                CLog.logAndDisplay(
                        LogLevel.ERROR,
                        String.format(
                                "Unable to parse value '%s' for '%s' metric key.",
                                metrics.get(key), key));
            }
        }
        CLog.logAndDisplay(
                LogLevel.INFO, "Successfully completed writing the metrics to JSON report.");
    }

    /** Copy the report generated at temporary path to the given destination path in Results */
    private void copyGeneratedReportToResultsDirectory() {
        CLog.logAndDisplay(LogLevel.INFO, "Copying the report log to results directory.");
        // Copy report log files to results dir.
        try {
            // Get Result Directory
            File resultDir = mBuildHelper.getResultDir();
            // Create a directory ( if it does not exist ) in results for report logs
            if (mDestDir != null) {
                resultDir = new File(resultDir, mDestDir);
            }
            if (!resultDir.exists()) {
                resultDir.mkdirs();
            }
            if (!resultDir.isDirectory()) {
                CLog.logAndDisplay(
                        LogLevel.ERROR,
                        String.format("%s is not a directory", resultDir.getAbsolutePath()));
                return;
            }
            // Temp directory for report logs
            final File hostReportDir = FileUtil.createNamedTempDir(mTempReportFolder);
            if (!hostReportDir.isDirectory()) {
                CLog.logAndDisplay(
                        LogLevel.ERROR,
                        String.format("%s is not a directory", hostReportDir.getAbsolutePath()));
                return;
            }
            // Copy/Merge the report logs from temp directory and to the results directory
            CollectorUtil.reformatRepeatedStreams(hostReportDir);
            CollectorUtil.pullFromHost(hostReportDir, resultDir);
            CLog.logAndDisplay(LogLevel.INFO, "Copying the report log completed successfully.");
        } catch (IOException exception) {
            CLog.logAndDisplay(LogLevel.ERROR, exception.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        mContext = context;
        initializeReporterConfig();
    }

    /** {@inheritDoc} */
    @Override
    public void invocationEnded(long elapsedTime) {
        // Copy the generated report to Results Directory
        copyGeneratedReportToResultsDirectory();
    }

    /** Overrides parent to explicitly to store test metrics */
    @Override
    public void testEnded(TestDescription testDescription, HashMap<String, Metric> metrics) {
        // If metrics are available and aggregate-similar-metrics is set to true, store the metrics
        if (metrics != null && !metrics.isEmpty() && mAggregateSimilarTests) {
            // Store the metrics
            mTestMetricsUtil.storeTestMetrics(testDescription, metrics);
        }
    }

    /** Overrides parent to explicitly to process and write metrics  */
    @Override
    public final void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        // If aggregate-similar-metrics is set to true, aggregate the metrics
        if (mAggregateSimilarTests) {
            // Aggregate Metrics for Similar Tests and write to the file
            Map<String, Map<String, String>> aggregatedMetrics =
                    mTestMetricsUtil.getAggregatedStoredTestMetrics();
            for (String testName: aggregatedMetrics.keySet()) {
                writeTestMetrics(testName, aggregatedMetrics.get(testName));
            }
        }

        // Avoid reporting duplicate metrics by erasing metrics from previous runs
        reInitializeTestMetricsUtil();
    }

    /** {@inheritDoc} */
    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        mModuleContext = moduleContext;
    }

    /** {@inheritDoc} */
    @Override
    public void testModuleEnded() {
        mModuleContext = null;
    }
}
