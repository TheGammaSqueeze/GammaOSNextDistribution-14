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
package com.android.tradefed.util;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.math.Quantiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains common utility methods for storing the test metrics, aggregating the metrics in similar
 * tests and writing the metrics to a file.
 */
public class MetricUtility {

    private static final String TEST_HEADER_SEPARATOR = "\n\n";
    private static final String METRIC_SEPARATOR = "\n";
    private static final String METRIC_KEY_VALUE_SEPARATOR = ":";
    private static final String STATS_KEY_MIN = "min";
    private static final String STATS_KEY_MAX = "max";
    private static final String STATS_KEY_MEAN = "mean";
    private static final String STATS_KEY_VAR = "var";
    private static final String STATS_KEY_STDEV = "stdev";
    private static final String STATS_KEY_MEDIAN = "median";
    private static final String STATS_KEY_TOTAL = "total";
    private static final String STATS_KEY_COUNT = "metric-count";
    private static final String STATS_KEY_DIFF = "last-first-diff";
    private static final String STATS_KEY_PERCENTILE_PREFIX = "p";
    private static final String STATS_KEY_SEPARATOR = "-";
    private static final Joiner CLASS_METHOD_JOINER = Joiner.on("#").skipNulls();

    // Used to separate the package name from the iteration number. Default is set to "$".
    private String mTestIterationSeparator = "$";

    // Percentiles to include when calculating the aggregates.
    private Set<Integer> mActualPercentiles = new LinkedHashSet<>();

    // Store the test metrics for aggregation at the end of test run.
    // Outer map key is the test id and inner map key is the metric key name.
    private Map<String, ArrayListMultimap<String, Metric>> mStoredTestMetrics =
            new LinkedHashMap<String, ArrayListMultimap<String, Metric>>();

    private List<Pattern> mMetricPatterns = new ArrayList<>();

    /**
     * Used for storing the individual test metrics and use it for aggregation.
     *
     * @param testDescription contains the test details like class name and test name.
     * @param testMetrics metrics collected for the test.
     */
    public void storeTestMetrics(TestDescription testDescription,
            Map<String, Metric> testMetrics) {

        if (testMetrics == null) {
            return;
        }

        // Group test cases which differs only by the iteration separator or test the same name.
        String className = testDescription.getClassName();
        int iterationSeparatorIndex = testDescription.getClassName()
                .indexOf(mTestIterationSeparator);
        if (iterationSeparatorIndex != -1) {
            className = testDescription.getClassName().substring(0, iterationSeparatorIndex);
        }
        String newTestId = CLASS_METHOD_JOINER.join(className, testDescription.getTestName());

        if (!mStoredTestMetrics.containsKey(newTestId)) {
            mStoredTestMetrics.put(newTestId, ArrayListMultimap.create());
        }
        ArrayListMultimap<String, Metric> storedMetricsForThisTest = mStoredTestMetrics
                .get(newTestId);
        for (Map.Entry<String, Metric> entry : testMetrics.entrySet()) {
            storedMetricsForThisTest.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     *
     * Write metrics to a file.
     *
     * @param testFileSuffix is used as suffix in the test metric file name.
     * @param testHeaderName metrics will be written under the test header name.
     * @param metrics to write in the file.
     * @param resultsFile if null create a new file and write the metrics otherwise append the
     *        test header name and metric to the file.
     * @return file with the metric.
     */
    public File writeResultsToFile(String testFileSuffix, String testHeaderName,
            Map<String, String> metrics, File resultsFile) {

        if (resultsFile == null) {
            try {
                resultsFile = FileUtil.createTempFile(String.format("test_results_%s_",
                        testFileSuffix), "");
            } catch (IOException e) {
                CLog.e(e);
                return resultsFile;
            }
        }

        try (FileOutputStream outputStream = new FileOutputStream(resultsFile, true)) {
            // Write the header description name.
            outputStream.write(String.format("%s%s", testHeaderName, TEST_HEADER_SEPARATOR)
                    .getBytes());
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                String test_metric = String.format("%s%s%s", entry.getKey(),
                        METRIC_KEY_VALUE_SEPARATOR, entry.getValue());
                outputStream.write(String.format("%s%s", test_metric, METRIC_SEPARATOR).getBytes());
            }
            outputStream.write(TEST_HEADER_SEPARATOR.getBytes());
        } catch (IOException ioe) {
            CLog.e(ioe);
        }
        return resultsFile;
    }

    /**
     * Aggregate comma separated metrics.
     *
     * @param rawMetrics metrics collected during the test run.
     * @return aggregated metrics.
     */
    public Map<String, Metric> aggregateMetrics(Map<String, Metric> rawMetrics) {
        Map<String, Metric> aggregateMetrics = new LinkedHashMap<String, Metric>();
        for (Map.Entry<String, Metric> entry : rawMetrics.entrySet()) {
            String values = entry.getValue().getMeasurements().getSingleString();
            List<String> splitVals = Arrays.asList(values.split(",", 0));
            // Build stats for keys with any values, even only one.
            if (isAllDoubleValues(splitVals)) {
                buildStats(entry.getKey(), splitVals, aggregateMetrics);
            }
        }
        return aggregateMetrics;
    }

    /**
     * Aggregate the metrics collected from multiple iterations of the test and
     * write the aggregated metrics to a test result file.
     *
     * @param fileName file name to use while writing the metrics.
     */
    public File aggregateStoredTestMetricsAndWriteToFile(String fileName) {
        File resultsFile = null;
        for (String testName : mStoredTestMetrics.keySet()) {
            ArrayListMultimap<String, Metric> currentTest = mStoredTestMetrics.get(testName);

            Map<String, Metric> aggregateMetrics = new LinkedHashMap<String, Metric>();
            for (String metricKey : currentTest.keySet()) {
                List<Metric> metrics = currentTest.get(metricKey);
                List<Measurements> measures = metrics.stream().map(Metric::getMeasurements)
                        .collect(Collectors.toList());
                // Parse metrics into a list of SingleString values, concating lists in the process
                List<String> rawValues = measures.stream()
                        .map(Measurements::getSingleString)
                        .map(
                                m -> {
                                    // Split results; also deals with the case of empty results
                                    // in a certain run
                                    List<String> splitVals = Arrays.asList(m.split(",", 0));
                                    if (splitVals.size() == 1 && splitVals.get(0).isEmpty()) {
                                        return Collections.<String> emptyList();
                                    }
                                    return splitVals;
                                })
                        .flatMap(Collection::stream)
                        .map(String::trim)
                        .collect(Collectors.toList());
                // Do not report empty metrics
                if (rawValues.isEmpty()) {
                    continue;
                }
                if (isAllDoubleValues(rawValues)) {
                    buildStats(metricKey, rawValues, aggregateMetrics);
                }
            }

            aggregateMetrics =
                    mMetricPatterns.size() > 0 ? filterMetrics(aggregateMetrics) : aggregateMetrics;
            Map<String, String> compatibleTestMetrics = TfMetricProtoUtil
                    .compatibleConvert(aggregateMetrics);

            resultsFile = writeResultsToFile(fileName, testName,
                    compatibleTestMetrics, resultsFile);
        }
        return resultsFile;
    }

    public void setPercentiles(Set<Integer> percentiles) {
        mActualPercentiles = percentiles;
    }

    public void setIterationSeparator(String separator) {
        mTestIterationSeparator = separator;
    }

    @VisibleForTesting
    public Map<String, ArrayListMultimap<String, Metric>> getStoredTestMetric() {
        return mStoredTestMetrics;
    }

    /**
     * Return true is all the values can be parsed to double value.
     * Otherwise return false.
     *
     * @param rawValues list whose values are validated.
     */
    public static boolean isAllDoubleValues(List<String> rawValues) {
        return rawValues
                .stream()
                .allMatch(
                        val -> {
                            try {
                                Double.parseDouble(val);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        });
    }

    /**
     * Compute the stats from the give list of values.
     *
     * @param values raw values to compute the aggregation.
     * @param percentiles stats to include in the final metrics.
     * @return aggregated values.
     */
    public static Map<String, Double> getStats(List<Double> values,
            Set<Integer> percentiles) {
        Map<String, Double> stats = new LinkedHashMap<>();
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double count = values.size();
        // The orElse situation should never happen.
        double mean = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow(IllegalStateException::new);
        double variance = values.stream().reduce(0.0, (a, b) -> a + Math.pow(b - mean, 2) / count);
        // Calculate percentiles. 50 th percentile will be used as median.
        Set<Integer> updatedPercentile = new LinkedHashSet<>(percentiles);
        updatedPercentile.add(50);
        Map<Integer, Double> percentileStat = Quantiles.percentiles().indexes(updatedPercentile)
                .compute(values);
        double median = percentileStat.get(50);
        // Diff of last and first value from the list. Return the first value if only one value is
        // present.
        double diff = (values.size() > 1) ? (values.get(values.size() - 1) - values.get(0))
                : values.get(0);

        stats.put(STATS_KEY_MIN, Collections.min(values));
        stats.put(STATS_KEY_MAX, Collections.max(values));
        stats.put(STATS_KEY_MEAN, mean);
        stats.put(STATS_KEY_VAR, variance);
        stats.put(STATS_KEY_STDEV, Math.sqrt(variance));
        stats.put(STATS_KEY_MEDIAN, median);
        stats.put(STATS_KEY_TOTAL, sum);
        stats.put(STATS_KEY_COUNT, count);
        stats.put(STATS_KEY_DIFF, diff);
        percentileStat
                .entrySet()
                .stream()
                .forEach(
                        e -> {
                            // If the percentile is 50, only include it if the user asks for it
                            // explicitly.
                            if (e.getKey() != 50 || percentiles.contains(50)) {
                                stats.put(
                                        STATS_KEY_PERCENTILE_PREFIX + e.getKey().toString(),
                                        e.getValue());
                            }
                        });
        return stats;
    }

    /**
     * Build stats for the given set of values and build the metrics using the metric key
     * and stats name and update the results in aggregated metrics.
     *
     * @param metricKey key to which the values correspond to.
     * @param values list of raw values.
     * @param aggregateMetrics where final metrics will be stored.
     */
    private void buildStats(String metricKey, List<String> values,
            Map<String, Metric> aggregateMetrics) {
        List<Double> doubleValues = values.stream().map(Double::parseDouble)
                .collect(Collectors.toList());
        Map<String, Double> stats = getStats(doubleValues, mActualPercentiles);
        for (String statKey : stats.keySet()) {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder
                    .getMeasurementsBuilder()
                    .setSingleString(String.format("%2.2f", stats.get(statKey)));
            aggregateMetrics.put(
                    String.join(STATS_KEY_SEPARATOR, metricKey, statKey),
                    metricBuilder.build());
        }
    }

    /** Build regular expression patterns to filter the metrics. */
    public void buildMetricFilterPatterns(Set<String> strictIncludeRegEx) {
        if (!strictIncludeRegEx.isEmpty() && mMetricPatterns.isEmpty()) {
            for (String regEx : strictIncludeRegEx) {
                mMetricPatterns.add(Pattern.compile(regEx));
            }
        }
    }

    /** Filter the metrics that matches the pattern. */
    public Map<String, Metric> filterMetrics(Map<String, Metric> parsedMetrics) {
        Map<String, Metric> filteredMetrics = new HashMap<>();
        for (Entry<String, Metric> metricEntry : parsedMetrics.entrySet()) {
            for (Pattern pattern : mMetricPatterns) {
                if (pattern.matcher(metricEntry.getKey()).matches()) {
                    filteredMetrics.put(metricEntry.getKey(), metricEntry.getValue());
                    break;
                }
            }
        }
        return filteredMetrics;
    }
}
