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
package com.android.catbox.util;

import com.android.annotations.VisibleForTesting;

import com.android.ddmlib.Log.LogLevel;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.math.Quantiles;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains common utility methods for storing the test metrics and aggregating the metrics in
 * similar tests.
 */
public class TestMetricsUtil {

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
    private static final String STATS_KEY_PERCENTILE_PREFIX = "p";
    private static final String STATS_KEY_SEPARATOR = "-";
    private static final Joiner CLASS_METHOD_JOINER = Joiner.on("#").skipNulls();

    // Used to separate the package name from the iteration number. Default is set to "$".
    private String mTestIterationSeparator = "$";

    // Percentiles to include when calculating the aggregates.
    private Set<Integer> mActualPercentiles = new HashSet<>();

    // Store the test metrics for aggregation at the end of test run.
    // Outer map key is the test id and inner map key is the metric key name.
    private Map<String, ArrayListMultimap<String, Metric>> mStoredTestMetrics =
            new HashMap<String, ArrayListMultimap<String, Metric>>();

    /**
     * Used for storing the individual test metrics and use it for aggregation.
     */
    public void storeTestMetrics(TestDescription testDescription, Map<String, Metric> testMetrics) {
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

        // Store only raw metrics
        HashMap<String, Metric> rawMetrics = getRawMetricsOnly(testMetrics);

        for (Map.Entry<String, Metric> entry : rawMetrics.entrySet()) {
            String key = entry.getKey();
            // In case of Multi User test, the metric conatins className with iteration separator
            if (key.indexOf(mTestIterationSeparator) != -1 &&
                        key.contains(testDescription.getClassName())) {
                key = key.substring(0, key.indexOf(mTestIterationSeparator));
                key = CLASS_METHOD_JOINER.join(key, testDescription.getTestName());
            }
            storedMetricsForThisTest.put(key, entry.getValue());
        }
    }

    /**
     * Aggregate the metrics collected from multiple iterations of the test and
     * return aggregated metrics.
     */
    public Map<String, Map<String, String>> getAggregatedStoredTestMetrics() {
        Map<String, Map<String, String>> aggregatedStoredMetrics =
                new HashMap<String, Map<String, String>>();
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
            Map<String, String> compatibleTestMetrics = TfMetricProtoUtil
                    .compatibleConvert(aggregateMetrics);
            aggregatedStoredMetrics.put(testName, compatibleTestMetrics);
        }
        return aggregatedStoredMetrics;
    }

    /** Set percentiles */
    public void setPercentiles(Set<Integer> percentiles) {
        mActualPercentiles = percentiles;
    }

    /** Set iteration separator */
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
     */
    public static Map<String, Double> getStats(Collection<Double> values,
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
        // Calculate percentiles. 50 th percentile will be used as medain.
        Set<Integer> updatedPercentile = new HashSet<>(percentiles);
        updatedPercentile.add(50);
        Map<Integer, Double> percentileStat = Quantiles.percentiles().indexes(updatedPercentile)
                .compute(values);
        double median = percentileStat.get(50);

        stats.put(STATS_KEY_MIN, Collections.min(values));
        stats.put(STATS_KEY_MAX, Collections.max(values));
        stats.put(STATS_KEY_MEAN, mean);
        stats.put(STATS_KEY_VAR, variance);
        stats.put(STATS_KEY_STDEV, Math.sqrt(variance));
        stats.put(STATS_KEY_MEDIAN, median);
        stats.put(STATS_KEY_TOTAL, sum);
        stats.put(STATS_KEY_COUNT, count);
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

    /**
     * Get only raw values for processing.
     */
    private HashMap<String, Metric> getRawMetricsOnly(Map<String, Metric> metrics) {
        HashMap<String, Metric> rawMetrics = new HashMap<>();
        for (Entry<String, Metric> entry : metrics.entrySet()) {
            if (DataType.RAW.equals(entry.getValue().getType())) {
                rawMetrics.put(entry.getKey(), entry.getValue());
            }
        }
        return rawMetrics;
    }
}