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
package com.android.tradefed.postprocessor;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric.Builder;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.MetricUtility;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Used for uploading the metrics log file collected during the test and run level.
 *
 * Use "aggregate-similar-tests" option to aggregate similar tests metrics at the test run level
 * and write it to a log file. Tests differ only by the iteration number or with the same name
 * are considered similar tests.
 *
 * This will have access to only raw metrics.
 */
@OptionClass(alias = "metric-file-post-processor")
public class MetricFilePostProcessor extends BasePostProcessor {

    private static final String AGGREGATE_TEST_SUFFIX = "_aggregate_test_metrics";
    private static final String AGGREGATE_RUN_SUFFIX = "_aggregate_run_metrics";

    @Option(name = "enable-per-test-log", description = "Set this flag to false to disable"
            + " writing the per test metrics to a file.")
    private boolean mIsPerTestLogEnabled= true;

    @Option(name = "enable-run-log", description = "Set this flag to false to disable"
            + " writing the run metrics to a file.")
    private boolean mIsRunLogEnabled= true;

    @Option(name = "aggregate-similar-tests", description = "To aggregate the metrics from test"
            + " cases which differ only by iteration number or having the same test name."
            + " Used only in context with the microbenchmark test runner. Set this flag to false"
            + " to disable aggregating the metrics.")
    private boolean mAggregateSimilarTests= false;

    @Option(name = "aggregate-run-metrics", description = "Aggregate run metrics which has more"
            + " than one value.")
    private boolean mAggregateRunMetrics= false;

    @Option(name = "test-iteration-separator", description = "Separator used in between the test"
            + " class name and the iteration number.")
    private String mTestIterationSeparator = "$";

    @Option(name = "report-percentiles", description = "Additional percentiles of each metric to"
            + " report in integers in the 0 - 100 range. Can be repeated.")
    private Set<Integer> mPercentiles = new HashSet<>();

    @Option(
            name = "strict-include-metric-filter",
            description =
                    "Regular expression that will be used for filtering the metrics from individual"
                            + " test metrics and aggregated metrics.")
    private Set<String> mStrictIncludeRegEx = new HashSet<>();

    private MetricUtility mMetricUtil = new MetricUtility();

    public MetricFilePostProcessor() {
    }

    @VisibleForTesting
    public MetricFilePostProcessor(MetricUtility metricUtil) {
        mMetricUtil = metricUtil;
    }

    @Override
    public Map<String, Metric.Builder> processTestMetricsAndLogs(
            TestDescription testDescription,
            HashMap<String, Metric> testMetrics,
            Map<String, LogFile> testLogs) {

        // Store the test metric and use it for aggregation later at the end of
        // test run.
        if (mAggregateSimilarTests) {
            mMetricUtil.storeTestMetrics(testDescription, testMetrics);
        }

        // Write test metric to a file and log it.
        if (mIsPerTestLogEnabled) {
            Map<String, Metric> filteredTestMetrics =
                    mStrictIncludeRegEx.size() > 0
                            ? mMetricUtil.filterMetrics(testMetrics)
                            : testMetrics;
            writeMetricFile(filteredTestMetrics, testDescription.toString());
        }

        return new HashMap<String, Builder>();
    }

    @Override
    public Map<String, Builder> processRunMetricsAndLogs(
            HashMap<String, Metric> rawMetrics, Map<String, LogFile> runLogs) {
        if (mIsRunLogEnabled) {
            // Log the raw run metrics.
            Map<String, Metric> filteredRawRunMetrics =
                    mStrictIncludeRegEx.size() > 0
                            ? mMetricUtil.filterMetrics(rawMetrics)
                            : rawMetrics;
            writeMetricFile(filteredRawRunMetrics, getRunName());

            // Log the aggregate run metrics.
            if (mAggregateRunMetrics) {
                Map<String, Metric> aggregatedRunMetrics = mMetricUtil.aggregateMetrics(rawMetrics);
                Map<String, Metric> filteredAggregateRunMetrics =
                        mStrictIncludeRegEx.size() > 0
                                ? mMetricUtil.filterMetrics(aggregatedRunMetrics)
                                : aggregatedRunMetrics;
                writeMetricFile(filteredAggregateRunMetrics, getRunName() + AGGREGATE_RUN_SUFFIX);
            }
        }

        // Aggregate similar tests metric at the run level, write it to results file and upload it.
        if (mAggregateSimilarTests) {
            File aggregateTestResultsFile = mMetricUtil
                    .aggregateStoredTestMetricsAndWriteToFile(getRunName() + AGGREGATE_TEST_SUFFIX);
            if (aggregateTestResultsFile != null) {
                try (InputStreamSource source = new FileInputStreamSource(aggregateTestResultsFile,
                        true)) {
                    testLog(aggregateTestResultsFile.getName(), LogDataType.CB_METRICS_FILE,
                            source);
                }
            }
        }
        return new HashMap<String, Builder>();
    }

    /**
     * Write the metrics to the results file and upload it.
     *
     * @param metrics
     * @param testId
     */
    public void writeMetricFile(Map<String, Metric> metrics, String testId) {
        Map<String, String> compatibleMetrics = TfMetricProtoUtil
                .compatibleConvert(metrics);
        File metricFile = mMetricUtil.writeResultsToFile(testId, testId,
                compatibleMetrics,
                null);
        if (metricFile != null) {
            try (InputStreamSource source = new FileInputStreamSource(metricFile,
                    true)) {
                testLog(metricFile.getName(), LogDataType.CB_METRICS_FILE, source);
            }
        }
    }

    @Override
    public void setUp() {
        mMetricUtil.setPercentiles(mPercentiles);
        mMetricUtil.setIterationSeparator(mTestIterationSeparator);
        mMetricUtil.buildMetricFilterPatterns(mStrictIncludeRegEx);
    }
}
