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

package com.android.compatibility.common.tradefed.result.suite;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

import java.util.Map;
import java.util.HashMap;

/** Unit tests for {@link TestMetricsJsonResultReporter}. */
@RunWith(JUnit4.class)
public class TestMetricsJsonResultReporterTest {
    private TestMetricsJsonResultReporter mResultReporter;

    private OptionSetter mOptionSetter;

    private CompatibilityBuildHelper mBuildHelper;

    private IInvocationContext mContext;

    private File mResultDir;
    private File mFakeDir;

    private static final String DESTINATION_DIR = "report-log-files";
    private static final String REPORT_FILE_SUFFIX = "reportlog.json";
    private static final String TEST_TAG = "testTag";

    @Before
    public void setUp() throws Exception {
        mFakeDir = FileUtil.createTempDir("result-dir");
        mResultDir = new File(mFakeDir, "android-cts/results");
        mResultDir.mkdirs();

        IBuildInfo info = new BuildInfo();
        info.addBuildAttribute(CompatibilityBuildHelper.ROOT_DIR, mFakeDir.getAbsolutePath());
        info.addBuildAttribute(
                CompatibilityBuildHelper.START_TIME_MS, Long.toString(System.currentTimeMillis()));

        mBuildHelper =
                new CompatibilityBuildHelper(info) {
                    @Override
                    public String getSuiteName() {
                        return "CTS";
                    }

                    @Override
                    public String getSuiteVersion() {
                        return "version";
                    }

                    @Override
                    public String getSuitePlan() {
                        return "cts";
                    }

                    @Override
                    public String getSuiteBuild() {
                        return "R1";
                    }
                };

        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, info);
        mContext.setTestTag(TEST_TAG);

        mResultReporter =
                new TestMetricsJsonResultReporter() {
                    @Override
                    CompatibilityBuildHelper createBuildHelper() {
                        return mBuildHelper;
                    }

                    @Override
                    String getAbiInfo() {
                        return "testABI";
                    }
                };

        mOptionSetter = new OptionSetter(mResultReporter);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mFakeDir);
    }

    /** Check that results file is not generated if test metrics are not available. */
    @Test
    public void testReportLogFileNotGenerated() throws Exception {
        mResultReporter.invocationStarted(mContext);
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        assertFalse(reportLogFile.exists());
    }

    /**
     * Check that results file is generated if test metrics are available. Case: If user does not
     * provide the report file name, test tag is used as default file name.
     */
    @Test
    public void testReportLogFileIsGenerated_Default_File_Name() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("metric-1", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(map));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        assertTrue(reportLogFile.exists());
    }

    /**
     * Check that results file is generated if test metrics are available. Case: User Provided Name
     * is used as Report File Name
     */
    @Test
    public void testReportLogFileIsGenerated_Custom_File_Name() throws Exception {
        String reportFileName = "customName";
        mOptionSetter.setOptionValue("report-log-name", reportFileName);
        Map<String, String> map = new HashMap<>();
        map.put("metric-1", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(map));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check report file with default name does not exist
        assertFalse(reportLogFile.exists());
        reportLogFile =
                new File(reportLogDir, String.format("%s.%s", reportFileName, REPORT_FILE_SUFFIX));
        // Check that report file with user provided name is generated
        assertTrue(reportLogFile.exists());
    }

    /**
     * Check that results file is generated and have expected content. Case: If Test Name mapping is
     * not provided, ClassMethodName is used as default
     */
    @Test
    public void testReportLogValidateContent_No_Test_Name_Mapping() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("metric-1", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(map));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertTrue(content.contains("FooTest#testFoo"));
    }

    /**
     * Check that results file is generated and have expected content. Case: Test Name Mapping
     * Present
     */
    @Test
    public void testReportLogValidateContent_Test_Name_Mapping_Provided() throws Exception {
        mOptionSetter.setOptionValue(
                "report-test-name-mapping", "FooTest#testFoo", "foo_test_metric");
        Map<String, String> map = new HashMap<>();
        map.put("metric-1", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(map));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertFalse(content.contains("FooTest#testFoo"));
        assertTrue(content.contains("foo_test_metric"));
    }

    /** Check that results file is generated and have expected content. Case: Report All Metrics */
    @Test
    public void testReportLogValidateContent_Report_All_Metrics() throws Exception {
        Map<String, String> testMetricsMap = new HashMap<>();
        testMetricsMap.put("testMetric-1", "1.0");
        testMetricsMap.put("testMetric-2", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(testMetricsMap));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertTrue(content.contains("testMetric-1"));
        assertTrue(content.contains("testMetric-2"));
    }

    /**
     * Check that results file is generated and have expected content. Case: Report Metrics for
     * Given Keys
     */
    @Test
    public void testReportLogValidateContent_Report_Metrics_For_Given_Keys() throws Exception {
        mOptionSetter.setOptionValue("report-all-metrics", "false");
        mOptionSetter.setOptionValue(
                "report-metric-key-mapping", "testMetric-1", "custom-metric-name-1");
        Map<String, String> testMetricsMap = new HashMap<>();
        testMetricsMap.put("testMetric-1", "1.0");
        testMetricsMap.put("testMetric-2", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(testMetricsMap));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertFalse(content.contains("testMetric-1"));
        assertFalse(content.contains("testMetric-2"));
        assertTrue(content.contains("custom-metric-name-1"));
    }

    /**
     * Check that results file is generated and have expected content. Case: Report Metrics for
     * Given Keys [ Some Keys are Missing from Generated Metrics ]
     */
    @Test
    public void testReportLogValidateContent_Missing_Keys() throws Exception {
        mOptionSetter.setOptionValue("report-all-metrics", "false");
        mOptionSetter.setOptionValue(
                "report-metric-key-mapping", "testMetric-1", "custom-metric-name-1");
        mOptionSetter.setOptionValue(
                "report-metric-key-mapping", "testMetric-3", "custom-metric-name-3");
        Map<String, String> testMetricsMap = new HashMap<>();
        testMetricsMap.put("testMetric-1", "1.0");
        testMetricsMap.put("testMetric-2", "1.0");
        final TestDescription testId = new TestDescription("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, TfMetricProtoUtil.upgradeConvert(testMetricsMap));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertFalse(content.contains("testMetric-1"));
        assertFalse(content.contains("testMetric-2"));
        assertTrue(content.contains("custom-metric-name-1"));
        assertFalse(content.contains("custom-metric-name-3"));
    }

    /** Check that results file is generated and have expected content. Case: Multiple Tests Runs */
    @Test
    public void testReportLogValidateContent_Multiple_Tests() throws Exception {
        mOptionSetter.setOptionValue(
                "report-test-name-mapping", "FooTest#testFoo", "foo_test_metric");
        mOptionSetter.setOptionValue(
                "report-test-name-mapping", "BarTest#testBar", "bar_test_metric");
        mOptionSetter.setOptionValue("report-all-metrics", "false");
        mOptionSetter.setOptionValue(
                "report-metric-key-mapping", "testMetric-foo-1", "foo-custom-metric-name-1");
        mOptionSetter.setOptionValue(
                "report-metric-key-mapping", "testMetric-bar-1", "bar-custom-metric-name-1");
        Map<String, String> fooTestMetricsMap = new HashMap<>();
        fooTestMetricsMap.put("testMetric-foo-1", "1.0");
        fooTestMetricsMap.put("testMetric-foo-2", "1.0");
        Map<String, String> barTestMetricsMap = new HashMap<>();
        barTestMetricsMap.put("testMetric-bar-1", "1.0");
        barTestMetricsMap.put("testMetric-bar-2", "1.0");
        final TestDescription fooTestId = new TestDescription("FooTest", "testFoo");
        final TestDescription barTestId = new TestDescription("BarTest", "testBar");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(fooTestId);
        mResultReporter.testEnded(fooTestId, TfMetricProtoUtil.upgradeConvert(fooTestMetricsMap));
        mResultReporter.testStarted(barTestId);
        mResultReporter.testEnded(barTestId, TfMetricProtoUtil.upgradeConvert(barTestMetricsMap));
        mResultReporter.testRunEnded(3, new HashMap<String, Metric>());
        mResultReporter.invocationEnded(500L);
        File reportLogDir = new File(mBuildHelper.getResultDir(), DESTINATION_DIR);
        File reportLogFile =
                new File(reportLogDir, String.format("%s.%s", TEST_TAG, REPORT_FILE_SUFFIX));
        // Check that report file is generated
        assertTrue(reportLogFile.exists());
        // Validate Report Content
        String content = FileUtil.readStringFromFile(reportLogFile);
        assertFalse(content.contains("FooTest#testFoo"));
        assertTrue(content.contains("foo_test_metric"));
        assertFalse(content.contains("testMetric-foo-1"));
        assertFalse(content.contains("testMetric-foo-2"));
        assertTrue(content.contains("foo-custom-metric-name-1"));
        assertFalse(content.contains("BarTest#testBar"));
        assertTrue(content.contains("bar_test_metric"));
        assertFalse(content.contains("testMetric-bar-1"));
        assertFalse(content.contains("testMetric-bar-2"));
        assertTrue(content.contains("bar-custom-metric-name-1"));
    }
}
