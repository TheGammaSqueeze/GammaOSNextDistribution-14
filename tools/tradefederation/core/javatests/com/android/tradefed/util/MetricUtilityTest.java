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

import static org.junit.Assert.assertTrue;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.collect.ArrayListMultimap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Unit tests for {@link MetricUtility}. */
@RunWith(JUnit4.class)
public class MetricUtilityTest {

    private static final String OUTPUT_1 = "case_1";
    private static final String OUTPUT_2 = "case_2";
    private static final String OUTPUT_3 = "case_3";
    private static final String OUTPUT_4 = "case_4";
    private static final String OUTPUT_5 = "case_5";
    private static final String OUTPUT_6 = "case_6";
    private static final String OUTPUT_7 = "case_7";
    private static final String OUTPUT_8 = "case_8";
    private static final String OUTPUT_9 = "case_9";

    private MetricUtility mMetricUtil;
    private File mResultsFile;

    private static final TestDescription TEST_1_ITERATION_1 = new TestDescription("pkg$1", "test1");
    private static final TestDescription TEST_1_ITERATION_2 = new TestDescription("pkg$2", "test1");
    private static final TestDescription TEST_2_ITERATION_1 = new TestDescription("pkg$1", "test2");
    private static final TestDescription TEST_3_WITHOUT_ITERATION = new TestDescription("pkg",
            "test3");

    @Before
    public void setUp() {
        mMetricUtil = new MetricUtility();
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mResultsFile);
    }

    @Test
    public void testStoreMultipleTestSingleIterationMetrics() {
        mMetricUtil.setIterationSeparator("$");

        // Build first test metric.
        Map<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);

        // Build second test metric.
        Map<String, Metric> secondTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder2 = Metric.newBuilder();
        metricBuilder2.getMeasurementsBuilder().setSingleString("1.5");
        Metric currentTestMetric2 = metricBuilder2.build();
        secondTestMetric.put("second_test_metric", currentTestMetric2);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstTestMetric);
        mMetricUtil.storeTestMetrics(TEST_2_ITERATION_1, secondTestMetric);

        Map<String, ArrayListMultimap<String, Metric>> storedTestmetrics = mMetricUtil
                .getStoredTestMetric();

        Assert.assertTrue(storedTestmetrics.size() == 2);
        Assert.assertTrue(storedTestmetrics.get("pkg#test1").get("first_test_metric").size() == 1);
        Assert.assertTrue(storedTestmetrics.get("pkg#test2").get("second_test_metric").size() == 1);
        Assert.assertTrue(storedTestmetrics.get("pkg#test1").get("first_test_metric").get(0)
                .getMeasurements().getSingleString().equals("2.9"));
        Assert.assertTrue(storedTestmetrics.get("pkg#test2").get("second_test_metric").get(0)
                .getMeasurements().getSingleString().equals("1.5"));
    }

    @Test
    public void testStoreSingleTestMultipleIterationMetrics() {
        mMetricUtil.setIterationSeparator("$");

        // Build first test metric.
        Map<String, Metric> firstIterationMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstIterationMetric.put("first_test_metric", currentTestMetric);

        // Build second test metric.
        Map<String, Metric> secondIterationMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder2 = Metric.newBuilder();
        metricBuilder2.getMeasurementsBuilder().setSingleString("1.5");
        Metric currentTestMetric2 = metricBuilder2.build();
        secondIterationMetric.put("first_test_metric", currentTestMetric2);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstIterationMetric);
        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_2, secondIterationMetric);

        Map<String, ArrayListMultimap<String, Metric>> storedTestmetrics = mMetricUtil
                .getStoredTestMetric();

        Assert.assertTrue(storedTestmetrics.size() == 1);

        Assert.assertTrue(storedTestmetrics.get("pkg#test1").get("first_test_metric").size() == 2);
        Assert.assertTrue(storedTestmetrics.get("pkg#test1").get("first_test_metric").get(0)
                .getMeasurements().getSingleString().equals("2.9"));
        Assert.assertTrue(storedTestmetrics.get("pkg#test1").get("first_test_metric").get(1)
                .getMeasurements().getSingleString().equals("1.5"));

    }

    @Test
    public void testStoreWithoutIteration() {
        // Build first test metric.
        HashMap<String, Metric> thirdTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        thirdTestMetric.put("third_test_metric", currentTestMetric);

        mMetricUtil.storeTestMetrics(TEST_3_WITHOUT_ITERATION, thirdTestMetric);

        Map<String, ArrayListMultimap<String, Metric>> storedTestmetrics = mMetricUtil
                .getStoredTestMetric();

        Assert.assertTrue(storedTestmetrics.size() == 1);
        Assert.assertTrue(storedTestmetrics.get("pkg#test3").get("third_test_metric").size() == 1);
    }

    @Test
    public void testStoreEmptyTestMetrics() {
        // Make sure we have the entry for the test name with empty metrics.
        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, new HashMap<String, Metric>());
        Map<String, ArrayListMultimap<String, Metric>> storedTestmetrics = mMetricUtil
                .getStoredTestMetric();

        // Entry for test name with zero metrics.
        Assert.assertTrue(
                storedTestmetrics.size() == 1 && storedTestmetrics.get("pkg#test1").size() == 0);
    }

    @Test
    public void testResultFileWithSingleTest() throws IOException {
        mMetricUtil.setIterationSeparator("$");
        // Build first test metric.
        Map<String, String> firstTestMetric = new LinkedHashMap<String, String>();
        firstTestMetric.put("first_test_metric_1", "2.9");
        firstTestMetric.put("first_test_metric_2", "3");

        mResultsFile = mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name",
                firstTestMetric, null);

        assertTrue(mResultsFile.getName().contains("file_suffix"));
        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_1)));
    }

    @Test
    public void testResultFileWithMultipleTest() throws IOException {

        mMetricUtil.setIterationSeparator("$");

        // Build first test metric.
        Map<String, String> firstTestMetric = new LinkedHashMap<String, String>();
        firstTestMetric.put("first_test_metric_1", "2.9");
        firstTestMetric.put("first_test_metric_2", "3");

        // Build second test metric.
        Map<String, String> secondTestMetric = new LinkedHashMap<String, String>();
        secondTestMetric.put("second_test_metric_1", "5.9");
        secondTestMetric.put("second_test_metric_2", "0");

        mResultsFile = mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name_1",
                firstTestMetric, null);

        // Write second test metrics
        mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name_2",
                secondTestMetric, mResultsFile);

        assertTrue(mResultsFile.getName().contains("file_suffix"));
        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_2)));

    }

    @Test
    public void testResultFileWithEmptyTestMetrics() throws IOException {

        mMetricUtil.setIterationSeparator("$");
        Map<String, String> firstTestMetric = new LinkedHashMap<String, String>();

        // Create a file with empty test metrics
        mResultsFile = mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name",
                firstTestMetric, null);
        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_3)));
    }

    @Test
    public void testAggregateCommaSeparatedMetrics() throws IOException {

        mMetricUtil.setPercentiles(new HashSet<>());

        // Build first test metric.
        Map<String, Metric> thirdTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("1,2,3");
        Metric currentTestMetric = metricBuilder1.build();
        thirdTestMetric.put("third_test_metric", currentTestMetric);

        Map<String, Metric> aggregatedMetric = mMetricUtil.aggregateMetrics(thirdTestMetric);

        Map<String, String> compatibleTestMetrics = TfMetricProtoUtil
                .compatibleConvert(aggregatedMetric);

        mResultsFile = mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name",
                compatibleTestMetrics, null);

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_4)));
    }

    @Test
    public void testCustomPercentiles() throws IOException {

        mMetricUtil.setPercentiles(new LinkedHashSet<> (Arrays.asList(50, 90, 99)));

        // Build first test metric.
        Map<String, Metric> thirdTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("1,2,3");
        Metric currentTestMetric = metricBuilder1.build();
        thirdTestMetric.put("third_test_metric", currentTestMetric);

        Map<String, Metric> aggregatedMetric = mMetricUtil.aggregateMetrics(thirdTestMetric);

        Map<String, String> compatibleTestMetrics = TfMetricProtoUtil
                .compatibleConvert(aggregatedMetric);

        mResultsFile = mMetricUtil.writeResultsToFile("file_suffix", "pkg_name#test_name",
                compatibleTestMetrics, null);

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_5)));
    }

    @Test
    public void testAggregateStoredTestMetricsForMultipleIterations() throws IOException {

        mMetricUtil.setIterationSeparator("$");
        mMetricUtil.setPercentiles(new HashSet<>());

        // Build first test metric.
        Map<String, Metric> firstIterationMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstIterationMetric.put("first_test_metric", currentTestMetric);

        // Build second test metric.
        HashMap<String, Metric> secondIterationMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder2 = Metric.newBuilder();
        metricBuilder2.getMeasurementsBuilder().setSingleString("1.5");
        Metric currentTestMetric2 = metricBuilder2.build();
        secondIterationMetric.put("first_test_metric", currentTestMetric2);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstIterationMetric);
        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_2, secondIterationMetric);

        mResultsFile = mMetricUtil.aggregateStoredTestMetricsAndWriteToFile("run_name");

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_6)));

    }

    @Test
    public void testAggregateStoredTestMetricsForSingleIterationMultipleTest()
            throws IOException {
        mMetricUtil.setIterationSeparator("$");
        mMetricUtil.setPercentiles(new HashSet<>());

        // Build first test metric.
        Map<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);

        // Build second test metric.
        HashMap<String, Metric> secondTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder2 = Metric.newBuilder();
        metricBuilder2.getMeasurementsBuilder().setSingleString("1.5");
        Metric currentTestMetric2 = metricBuilder2.build();
        secondTestMetric.put("second_test_metric", currentTestMetric2);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstTestMetric);
        mMetricUtil.storeTestMetrics(TEST_2_ITERATION_1, secondTestMetric);

        mResultsFile = mMetricUtil.aggregateStoredTestMetricsAndWriteToFile("run_name");

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_7)));
    }

    @Test
    public void testAggregateEmptyStoredTestMetricsForMultipleIterations() throws IOException {

        mMetricUtil.setIterationSeparator("$");
        mMetricUtil.setPercentiles(new HashSet<>());

        // Build first empty test metric.
        Map<String, Metric> firstIterationMetric = new HashMap<String, Metric>();

        // Build second empty test metric.
        Map<String, Metric> secondIterationMetric = new HashMap<String, Metric>();

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstIterationMetric);
        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_2, secondIterationMetric);

        mResultsFile = mMetricUtil.aggregateStoredTestMetricsAndWriteToFile("run_name");

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_8)));
    }

    @Test
    public void testAggregateStoredTestMetricsWithStrictFilters() throws IOException {
        mMetricUtil.setIterationSeparator("$");
        mMetricUtil.setPercentiles(new HashSet<>());
        mMetricUtil.buildMetricFilterPatterns(
                new HashSet<>(Arrays.asList("first_test_metric-min", "second_test_metric-min")));

        // Build first test metric.
        Map<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);

        // Build second test metric.
        HashMap<String, Metric> secondTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder2 = Metric.newBuilder();
        metricBuilder2.getMeasurementsBuilder().setSingleString("1.5");
        Metric currentTestMetric2 = metricBuilder2.build();
        secondTestMetric.put("second_test_metric", currentTestMetric2);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstTestMetric);
        mMetricUtil.storeTestMetrics(TEST_2_ITERATION_1, secondTestMetric);

        mResultsFile = mMetricUtil.aggregateStoredTestMetricsAndWriteToFile("run_name");

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_9)));
    }

    @Test
    public void testAggregateStoredTestMetricsWithStrictFiltersNotMatching() throws IOException {
        mMetricUtil.setIterationSeparator("$");
        mMetricUtil.setPercentiles(new HashSet<>());
        mMetricUtil.buildMetricFilterPatterns(new HashSet<>(Arrays.asList("no_metric")));

        // Build first test metric.
        Map<String, Metric> firstTestMetric = new HashMap<String, Metric>();
        Metric.Builder metricBuilder1 = Metric.newBuilder();
        metricBuilder1.getMeasurementsBuilder().setSingleString("2.9");
        Metric currentTestMetric = metricBuilder1.build();
        firstTestMetric.put("first_test_metric", currentTestMetric);

        mMetricUtil.storeTestMetrics(TEST_1_ITERATION_1, firstTestMetric);

        mResultsFile = mMetricUtil.aggregateStoredTestMetricsAndWriteToFile("run_name");

        assertTrue(FileUtil.readStringFromFile(mResultsFile).equals(getSampleOutput(OUTPUT_8)));
    }

    /**
     * Method to get the sample output based on the give test cases.
     * @param outputType
     * @return sample output in string format.
     */
    private String getSampleOutput(String outputType) {
        switch (outputType) {
            case OUTPUT_1:
                return "pkg_name#test_name\n" +
                        "\n" +
                        "first_test_metric_1:2.9\n" +
                        "first_test_metric_2:3\n" +
                        "\n" +
                        "\n";
            case OUTPUT_2:
                return "pkg_name#test_name_1\n" +
                        "\n" +
                        "first_test_metric_1:2.9\n" +
                        "first_test_metric_2:3\n" +
                        "\n" +
                        "\n" +
                        "pkg_name#test_name_2\n" +
                        "\n" +
                        "second_test_metric_1:5.9\n" +
                        "second_test_metric_2:0\n" +
                        "\n" +
                        "\n";
            case OUTPUT_3:
                return "pkg_name#test_name\n" +
                        "\n" +
                        "\n" +
                        "\n";
            case OUTPUT_4:
                return "pkg_name#test_name\n" +
                        "\n" +
                        "third_test_metric-min:1.00\n" +
                        "third_test_metric-max:3.00\n" +
                        "third_test_metric-mean:2.00\n" +
                        "third_test_metric-var:0.67\n" +
                        "third_test_metric-stdev:0.82\n" +
                        "third_test_metric-median:2.00\n" +
                        "third_test_metric-total:6.00\n" +
                        "third_test_metric-metric-count:3.00\n" +
                        "third_test_metric-last-first-diff:2.00\n" +
                        "\n" +
                        "\n";
            case OUTPUT_5:
                return "pkg_name#test_name\n" +
                        "\n" +
                        "third_test_metric-min:1.00\n" +
                        "third_test_metric-max:3.00\n" +
                        "third_test_metric-mean:2.00\n" +
                        "third_test_metric-var:0.67\n" +
                        "third_test_metric-stdev:0.82\n" +
                        "third_test_metric-median:2.00\n" +
                        "third_test_metric-total:6.00\n" +
                        "third_test_metric-metric-count:3.00\n" +
                        "third_test_metric-last-first-diff:2.00\n" +
                        "third_test_metric-p50:2.00\n" +
                        "third_test_metric-p90:2.80\n" +
                        "third_test_metric-p99:2.98\n" +
                        "\n" +
                        "\n";
            case OUTPUT_6:
                return "pkg#test1\n" +
                        "\n" +
                        "first_test_metric-min:1.50\n" +
                        "first_test_metric-max:2.90\n" +
                        "first_test_metric-mean:2.20\n" +
                        "first_test_metric-var:0.49\n" +
                        "first_test_metric-stdev:0.70\n" +
                        "first_test_metric-median:2.20\n" +
                        "first_test_metric-total:4.40\n" +
                        "first_test_metric-metric-count:2.00\n" +
                        "first_test_metric-last-first-diff:-1.40\n" +
                        "\n" +
                        "\n";
            case OUTPUT_7:
                return "pkg#test1\n" +
                        "\n" +
                        "first_test_metric-min:2.90\n" +
                        "first_test_metric-max:2.90\n" +
                        "first_test_metric-mean:2.90\n" +
                        "first_test_metric-var:0.00\n" +
                        "first_test_metric-stdev:0.00\n" +
                        "first_test_metric-median:2.90\n" +
                        "first_test_metric-total:2.90\n" +
                        "first_test_metric-metric-count:1.00\n" +
                        "first_test_metric-last-first-diff:2.90\n" +
                        "\n" +
                        "\n" +
                        "pkg#test2\n" +
                        "\n" +
                        "second_test_metric-min:1.50\n" +
                        "second_test_metric-max:1.50\n" +
                        "second_test_metric-mean:1.50\n" +
                        "second_test_metric-var:0.00\n" +
                        "second_test_metric-stdev:0.00\n" +
                        "second_test_metric-median:1.50\n" +
                        "second_test_metric-total:1.50\n" +
                        "second_test_metric-metric-count:1.00\n" +
                        "second_test_metric-last-first-diff:1.50\n" +
                        "\n" +
                        "\n";
            case OUTPUT_8:
                return "pkg#test1\n" +
                        "\n" +
                        "\n" +
                        "\n";
            case OUTPUT_9:
                return "pkg#test1\n"
                        + "\n"
                        + "first_test_metric-min:2.90\n"
                        + "\n"
                        + "\n"
                        + "pkg#test2\n"
                        + "\n"
                        + "second_test_metric-min:1.50\n"
                        + "\n"
                        + "\n";
            default:
                return null;
        }
    }
}
