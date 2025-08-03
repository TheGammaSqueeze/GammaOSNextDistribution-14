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
package com.android.tradefed.postprocessor;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import static java.util.stream.Collectors.toMap;

import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.OnDevicePowerMeasurement;
import com.android.os.AtomsProto.RemainingBatteryCapacity;
import com.android.os.StatsLog.AggregatedAtomInfo;
import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.GaugeBucketInfo;
import com.android.os.StatsLog.GaugeMetricData;
import com.android.os.StatsLog.StatsLogReport;
import com.android.os.StatsLog.StatsLogReport.GaugeMetricDataWrapper;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.FieldDescriptor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Unit tests for {@link StatsdBeforeAfterGaugeMetricPostProcessor}. */
@RunWith(Parameterized.class)
public class StatsdBeforeAfterGaugeMetricPostProcessorTest {
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    @Parameter public boolean mUseAggregatedAtomInfo;

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @Mock private ITestInvocationListener mListener;
    private StatsdBeforeAfterGaugeMetricPostProcessor mProcessor;
    private OptionSetter mOptionSetter;

    // Test data related to statsd's OnDevicePowerMeasurement(ODPM) metric.
    private static final Atom TEST_ATOM_ODPM_RAIL_1_BEFORE =
            createTestOdpmAtom("subsystem", "rail1", 1);
    private static final Atom TEST_ATOM_ODPM_RAIL_2_BEFORE =
            createTestOdpmAtom("subsystem", "rail2", 2);
    private static final Atom TEST_ATOM_ODPM_RAIL_3_BEFORE =
            createTestOdpmAtom("subsystem", "rail3", 3);
    private static final Atom TEST_ATOM_ODPM_RAIL_1_AFTER =
            createTestOdpmAtom("subsystem", "rail1", 4);
    private static final Atom TEST_ATOM_ODPM_RAIL_2_AFTER =
            createTestOdpmAtom("subsystem", "rail2", 8);
    private static final Atom TEST_ATOM_ODPM_RAIL_3_AFTER =
            createTestOdpmAtom("subsystem", "rail3", 12);
    private static final String ATOM_NAME_ODPM = "on_device_power_measurement";
    private static final String STATSD_REPORT_PREFIX_ODPM = "statsd-metric-odpm";
    private static final String METRIC_PREFIX_ODPM =
            STATSD_REPORT_PREFIX_ODPM + "-gauge-" + ATOM_NAME_ODPM;
    private static final String METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL =
            "[subsystem_name]-[rail_name]=[energy_microwatt_secs]";
    private static final String METRIC_FORMATTER_ODPM_SUBSYSTEM =
            "[subsystem_name]=[energy_microwatt_secs]";

    // Test data related to statsd's RemainingBatteryCapacity metric.
    private static final Atom TEST_ATOM_BATTERY_BEFORE = createTestBatteryAtom(999);
    private static final Atom TEST_ATOM_BATTERY_AFTER = createTestBatteryAtom(222);
    private static final String ATOM_NAME_BATTERY = "remaining_battery_capacity";
    private static final String STATSD_REPORT_PREFIX_BATTERY = "statsd-metric-battery";
    private static final String METRIC_PREFIX_BATTERY =
            STATSD_REPORT_PREFIX_BATTERY + "-gauge-" + ATOM_NAME_BATTERY;
    private static final String METRIC_FORMATTER_BATTERY = "=[charge_micro_ampere_hour]";
    private static final String METRIC_FORMATTER_BATTERY_ALT = "=[charge_micro_amp_hours]";

    // Test data related to testing multiple metrics within one report.
    private static final String STATSD_REPORT_PREFIX_MULTI = "statsd-metric-multi";

    @Before
    public void setUp() throws ConfigurationException {
        initMocks(this);
        mProcessor = spy(new StatsdBeforeAfterGaugeMetricPostProcessor());
        mProcessor.init(mListener);

        mOptionSetter = new OptionSetter(mProcessor);
        // We can always set the processor to look for both; if only one is supplied in the test,
        // the other will be ignored.
        mOptionSetter.setOptionValue("statsd-report-data-prefix", STATSD_REPORT_PREFIX_ODPM);
        mOptionSetter.setOptionValue("statsd-report-data-prefix", STATSD_REPORT_PREFIX_BATTERY);
        mOptionSetter.setOptionValue("statsd-report-data-prefix", STATSD_REPORT_PREFIX_MULTI);
    }

    @Test
    public void testParseSingleMetricFormatter() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE, TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_2_AFTER, TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics: (the values are doubles because deltas are calculated after
        // metric values are cast to doubles)
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail1=3.0 (4 - 1)
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail2=6.0 (8 - 2)
        assertThat(metrics).hasSize(2);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail1", String.valueOf(3.0));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail2", String.valueOf(6.0));
    }

    @Test
    public void testParseMultipleMetricFormatters() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        // Provide two metric formatters.
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics: (the values are doubles because deltas are calculated after
        // metric values are cast to doubles)
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail1=3.0 (4 - 1)
        // <METRIC_PREFIX_ODPM>-delta-subsystem=3.0 (4 - 1)
        assertThat(metrics).hasSize(2);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail1", String.valueOf(3.0));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem", String.valueOf(3.0));
    }

    @Test
    public void testReportsBeforeAndAfterIfSet() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE, TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_2_AFTER, TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be six metrics, with four being before and after: (the values are ints
        // because before/after metrics are reported as-is)
        // <METRIC_PREFIX_ODPM>-before-subsystem-rail1=1
        // <METRIC_PREFIX_ODPM>-before-subsystem-rail2=2
        // <METRIC_PREFIX_ODPM>-after-subsystem-rail1=4
        // <METRIC_PREFIX_ODPM>-after-subsystem-rail2=8
        assertThat(metrics).hasSize(6);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem-rail1", String.valueOf(1));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem-rail2", String.valueOf(2));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem-rail1", String.valueOf(4));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem-rail2", String.valueOf(8));
    }

    @Test
    public void testSkipsMetricsWhenSnapshotIsMissing() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE, TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        null,
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        assertThat(metrics).isEmpty();
    }

    @Test
    public void testLogsRawMetricReportWhenSnapshotIsMissing()
            throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE, TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        null,
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);

        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        report.getReports(0)
                                                .getMetrics(0)
                                                .getGaugeMetrics()
                                                .getData(0)
                                                .toString())));
    }

    @Test
    public void testSkipsDeltaForMetricMissingInOneSnapshot()
            throws IOException, ConfigurationException {
        // The report is missing metrics for rail1 in the "before" snapshot and missing rail2 in the
        // "after" snapshot.
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_2_BEFORE, TEST_ATOM_ODPM_RAIL_3_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER, TEST_ATOM_ODPM_RAIL_3_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should only be one metric for rail1 since rail2 is missing in one snapshot.
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail3=9.0 (12 - 3)
        assertThat(metrics).hasSize(1);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail3", String.valueOf(9.0));
    }

    @Test
    public void testWarnsMetricMissingInOneSnapshot() throws IOException, ConfigurationException {
        // The report is missing metrics for rail1 in the "before" snapshot and missing rail2 in the
        // "after" snapshot.
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_2_BEFORE, TEST_ATOM_ODPM_RAIL_3_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER, TEST_ATOM_ODPM_RAIL_3_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);

        // The warning should include the atom names and any metrics (key & value) without a match,
        // and which snapshot the metric was seen in.
        // verify(mProcessor,
        // times(1)).logFormattedWarning(argThat(stringContainsInOrder(Arrays.asList(ATOM_NAME_ODPM,
        // "before", "subsystem-rail2", "2"))));
        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        ATOM_NAME_ODPM, "before", "subsystem-rail2", "2")));
        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        ATOM_NAME_ODPM, "after", "subsystem-rail1", "4")));
    }

    @Test
    public void testStillReportsBeforeAfterForMetricsMissingInOneSnapshot()
            throws IOException, ConfigurationException {
        // The report is missing metrics for rail1 in the "before" snapshot and missing rail2 in the
        // "after" snapshot.
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));

        // There should be two metrics: (the values are ints because before/after metrics are
        // reported as-is)
        // <METRIC_PREFIX_ODPM>-before-subsystem-rail2=2
        // <METRIC_PREFIX_ODPM>-after-subsystem-rail1=4
        assertThat(metrics).hasSize(2);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem-rail2", String.valueOf(2));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem-rail1", String.valueOf(4));
    }

    @Test
    public void testMetricWithMultipleValuesAreSkippedForDeltaCalculation()
            throws IOException, ConfigurationException {
        // The report will include the same atoms more than one time to create "multiple values".
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // Metrics for both rail1 and rail2 should be ignored for delta calculation.
        assertThat(metrics).isEmpty();
    }

    @Test
    public void testMetricWithMultipleValuesAreReportedInBeforeAfterIfSet()
            throws IOException, ConfigurationException {
        // The report will include the same atoms more than one time to create "multiple values".
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be four metrics:
        // <METRIC_PREFIX_ODPM>-before-subsystem-rail1=1,1
        // <METRIC_PREFIX_ODPM>-before-subsystem-rail2=2
        // <METRIC_PREFIX_ODPM>-after-subsystem-rail1=4
        // <METRIC_PREFIX_ODPM>-after-subsystem-rail2=8,8
        assertThat(metrics).hasSize(4);
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem-rail1", "1,1");
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem-rail2", "2");
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem-rail1", "4");
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem-rail2", "8,8");
    }

    @Test
    public void testMetricsWithMultipleValuesAreLoggedInWarning()
            throws IOException, ConfigurationException {
        // The report will include the same atoms more than one time to create "multiple values".
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_1_BEFORE,
                                TEST_ATOM_ODPM_RAIL_2_BEFORE),
                        Arrays.asList(
                                TEST_ATOM_ODPM_RAIL_1_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER,
                                TEST_ATOM_ODPM_RAIL_2_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // These metrics should be in the warning (these do not have prefixes like the eventually
        // reported metrics):
        // Before without after:
        //   subsystem-rail1=1,1 ("after" value: 4)
        // After without before:
        //   subsystem-rail2=8,8 ("before" value: 2)
        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        "subsystem-rail1",
                                        METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL,
                                        ATOM_NAME_ODPM,
                                        Arrays.asList("1", "1").toString(),
                                        "before",
                                        "4")));
        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        "subsystem-rail2",
                                        METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL,
                                        ATOM_NAME_ODPM,
                                        Arrays.asList("8", "8").toString(),
                                        "after",
                                        "2")));
    }

    @Test
    public void testIgnoresInvalidFormatters() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        // Provide one invalid and one valid formatter. Only the valid one should be reported.
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, "[subsystem_name]-[rail_name]=[not_a_field]");
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should only be one metric:
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail1=3.0 (4 - 1)
        assertThat(metrics).hasSize(1);
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail1", String.valueOf(3.0));
    }

    @Test
    public void testNonNumericValuesAreSkippedForDeltaCalculation()
            throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        // Provide one invalid and one valid formatter. Only the valid one should be reported.
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, "[subsystem_name]=[rail_name]");

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics from before and after, but not delta:
        // <METRIC_PREFIX_ODPM>-before-subsystem=rail1
        // <METRIC_PREFIX_ODPM>-after-subsystem=rail1
        assertThat(metrics).hasSize(2);
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-before-subsystem", "rail1");
        assertThat(metrics).containsEntry(METRIC_PREFIX_ODPM + "-after-subsystem", "rail1");
    }

    @Test
    public void testNonNumericValuesAreWarned() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_ODPM, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, "[subsystem_name]=[rail_name]");

        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        verify(mProcessor, times(1))
                .logFormattedWarning(
                        argThat(
                                SubstringMatcher.matchesAll(
                                        "subsystem",
                                        ATOM_NAME_ODPM,
                                        "[subsystem_name]=[rail_name]",
                                        "rail1",
                                        "rail1",
                                        "skipping delta calculation")));
    }

    @Test
    public void testSupportsEmptyMetricKeys() throws IOException, ConfigurationException {
        ConfigMetricsReportList report =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_BATTERY_BEFORE),
                        Arrays.asList(TEST_ATOM_BATTERY_AFTER),
                        mUseAggregatedAtomInfo);
        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_BATTERY, report));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(true));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY_ALT);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be three metrics:
        // <METRIC_PREFIX_BATTERY>-delta=-777.0 (222 - 999)
        // <METRIC_PREFIX_BATTERY>-before=999
        // <METRIC_PREFIX_BATTERY>-after=222
        assertThat(metrics).hasSize(3);
        assertThat(metrics).containsEntry(METRIC_PREFIX_BATTERY + "-delta", String.valueOf(-777.0));
        assertThat(metrics).containsEntry(METRIC_PREFIX_BATTERY + "-before", String.valueOf(999));
        assertThat(metrics).containsEntry(METRIC_PREFIX_BATTERY + "-after", String.valueOf(222));
    }

    @Test
    public void testSupportsMultipleReportFiles() throws IOException, ConfigurationException {
        ConfigMetricsReportList odpmReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        ConfigMetricsReportList batteryReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_BATTERY_BEFORE),
                        Arrays.asList(TEST_ATOM_BATTERY_AFTER),
                        mUseAggregatedAtomInfo);

        Map<String, LogFile> runLogs =
                setUpTestData(
                        ImmutableMap.of(
                                STATSD_REPORT_PREFIX_BATTERY,
                                batteryReport,
                                STATSD_REPORT_PREFIX_ODPM,
                                odpmReport));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY_ALT);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics:
        // <METRIC_PREFIX_BATTERY>-delta=-777.0 (222 - 999)
        // <METRIC_PREFIX_ODPM>-delta-subsystem-rail1=3.0 (4 - 1)
        assertThat(metrics).hasSize(2);
        assertThat(metrics).containsEntry(METRIC_PREFIX_BATTERY + "-delta", String.valueOf(-777.0));
        assertThat(metrics)
                .containsEntry(METRIC_PREFIX_ODPM + "-delta-subsystem-rail1", String.valueOf(3.0));
    }

    @Test
    public void testSupportsMultipleReportWithinOneFile()
            throws IOException, ConfigurationException {
        // Create two reports and combine them into one ConfigMetricsReportList with multiple
        // reports.
        ConfigMetricsReportList odpmReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        ConfigMetricsReportList batteryReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_BATTERY_BEFORE),
                        Arrays.asList(TEST_ATOM_BATTERY_AFTER),
                        mUseAggregatedAtomInfo);
        ConfigMetricsReportList multiReport =
                odpmReport.toBuilder().addReports(batteryReport.getReports(0)).build();

        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_MULTI, multiReport));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY_ALT);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics:
        // <STATSD_REPORT_PREFIX_MULTI>-gauge-remaining_battery_capacity-delta=-777.0 (222 - 999)
        // <STATSD_REPORT_PREFIX_ODPM>-gauge_on-device-power-measurement-delta-subsystem-rail1=3.0
        // (4 - 1)
        assertThat(metrics).hasSize(2);
        assertThat(metrics)
                .containsEntry(
                        STATSD_REPORT_PREFIX_MULTI + "-gauge-remaining_battery_capacity-delta",
                        String.valueOf(-777.0));
        assertThat(metrics)
                .containsEntry(
                        STATSD_REPORT_PREFIX_MULTI
                                + "-gauge-on_device_power_measurement-delta-subsystem-rail1",
                        String.valueOf(3.0));
    }

    @Test
    public void testSupportsMultipleMetricsWithinOneReport()
            throws IOException, ConfigurationException {
        // Create two reports and combine them into one ConfigMetricsReportList with multiple
        // metrics under one report.
        ConfigMetricsReportList odpmReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_BEFORE),
                        Arrays.asList(TEST_ATOM_ODPM_RAIL_1_AFTER),
                        mUseAggregatedAtomInfo);
        ConfigMetricsReportList batteryReport =
                createTestReportList(
                        Arrays.asList(TEST_ATOM_BATTERY_BEFORE),
                        Arrays.asList(TEST_ATOM_BATTERY_AFTER),
                        mUseAggregatedAtomInfo);
        ConfigMetricsReportList multiReport =
                ConfigMetricsReportList.newBuilder()
                        .addReports(
                                ConfigMetricsReport.newBuilder()
                                        .addMetrics(odpmReport.getReports(0).getMetrics(0))
                                        .addMetrics(batteryReport.getReports(0).getMetrics(0)))
                        .build();

        Map<String, LogFile> runLogs =
                setUpTestData(ImmutableMap.of(STATSD_REPORT_PREFIX_MULTI, multiReport));

        mOptionSetter.setOptionValue("also-report-before-after", String.valueOf(false));

        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_ODPM, METRIC_FORMATTER_ODPM_SUBSYSTEM_RAIL);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY_ALT);
        mOptionSetter.setOptionValue(
                "metric-formatter", ATOM_NAME_BATTERY, METRIC_FORMATTER_BATTERY);

        Map<String, String> metrics =
                getSingleStringMetrics(
                        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs));
        // There should be two metrics:
        // <STATSD_REPORT_PREFIX_MULTI>-gauge-<ATOM_NAME_BATTERY>-delta=-777.0 (222 - 999)
        // <STATSD_REPORT_PREFIX_ODPM>-gauge-<ATOM_NAME_ODPM>-delta-subsystem-rail1=3.0
        // (4 - 1)
        assertThat(metrics).hasSize(2);
        assertThat(metrics)
                .containsEntry(
                        STATSD_REPORT_PREFIX_MULTI + "-gauge-" + ATOM_NAME_BATTERY + "-delta",
                        String.valueOf(-777.0));
        assertThat(metrics)
                .containsEntry(
                        STATSD_REPORT_PREFIX_MULTI
                                + "-gauge-"
                                + ATOM_NAME_ODPM
                                + "-delta-subsystem-rail1",
                        String.valueOf(3.0));
    }

    private static Atom createTestOdpmAtom(
            String subsystemName, String railName, int energyMicrowattSecs) {
        return Atom.newBuilder()
                .setOnDevicePowerMeasurement(
                        OnDevicePowerMeasurement.newBuilder()
                                .setSubsystemName(subsystemName)
                                .setRailName(railName)
                                .setEnergyMicrowattSecs(energyMicrowattSecs))
                .build();
    }

    private static Atom createTestBatteryAtom(int chargeMicroAmpereHour) {
        // Use the field number due to naming difference in proto libs
        FieldDescriptor chargeMicroAmpHour =
                RemainingBatteryCapacity.getDescriptor().findFieldByNumber(1);
        return Atom.newBuilder()
                .setRemainingBatteryCapacity(
                        RemainingBatteryCapacity.newBuilder()
                                .setField(
                                        chargeMicroAmpHour, Integer.valueOf(chargeMicroAmpereHour))
                                .build())
                .build();
    }

    /**
     * Create a {@code ConfigMetricsReportList} for test from the supplied atoms. If either {@code
     * befores} or {@code afters} is {@code null}, the corresponding bucket will be omitted.
     */
    private static ConfigMetricsReportList createTestReportList(
            Collection<Atom> befores, Collection<Atom> afters, boolean useAggregatedAtomInfo) {
        ConfigMetricsReportList.Builder reportListBuilder = ConfigMetricsReportList.newBuilder();
        GaugeMetricData.Builder gaugeMetricBuilder = GaugeMetricData.newBuilder();
        if (befores != null) {
            gaugeMetricBuilder.addBucketInfo(buildBucket(befores, useAggregatedAtomInfo));
        }
        if (afters != null) {
            gaugeMetricBuilder.addBucketInfo(buildBucket(afters, useAggregatedAtomInfo));
        }
        return reportListBuilder
                .addReports(
                        ConfigMetricsReport.newBuilder()
                                .addMetrics(
                                        StatsLogReport.newBuilder()
                                                .setGaugeMetrics(
                                                        GaugeMetricDataWrapper.newBuilder()
                                                                .addData(gaugeMetricBuilder.build())
                                                                .build())
                                                .build())
                                .build())
                .build();
    }

    /** Build {@code GaugeBucketInfo} from a supplied list of {@code Atom}s. */
    private static GaugeBucketInfo buildBucket(
            Collection<Atom> atoms, boolean useAggregatedAtomInfo) {
        GaugeBucketInfo.Builder builder = GaugeBucketInfo.newBuilder();
        if (useAggregatedAtomInfo) {
            atoms.stream()
                    .forEach(
                            atom ->
                                    builder.addAggregatedAtomInfo(
                                            AggregatedAtomInfo.newBuilder().setAtom(atom).build()));
        } else {
            atoms.stream().forEach(atom -> builder.addAtom(atom));
        }
        return builder.build();
    }

    /** Convert the metrics reported as Metric.Builder to String for simpler assertions. */
    private Map<String, String> getSingleStringMetrics(Map<String, Metric.Builder> metrics) {
        return metrics.entrySet()
                .stream()
                .collect(
                        toMap(
                                e -> e.getKey(),
                                e -> e.getValue().getMeasurements().getSingleString()));
    }

    /** Convert the supplied protos and report names to the log file formats TF uses. */
    private Map<String, LogFile> setUpTestData(Map<String, ConfigMetricsReportList> reports)
            throws IOException {
        Map<String, LogFile> runLogs = new HashMap<String, LogFile>();
        for (String reportName : reports.keySet()) {
            String reportFileName = reportName + ".pb";
            File reportFile = testDir.newFile(reportFileName);
            Files.write(reportFile.toPath(), reports.get(reportName).toByteArray());
            runLogs.put(
                    reportName + "-run-level",
                    new LogFile(reportFile.getAbsolutePath(), reportName + ".url", LogDataType.PB));
        }
        return runLogs;
    }

    /** An {@link ArgumentMatcher} matching a string with a list of substrings ignoring order. */
    private static class SubstringMatcher implements ArgumentMatcher<String> {
        private String[] substrings;

        private SubstringMatcher(String... substrings) {
            this.substrings = substrings;
        }

        @Override
        public boolean matches(String string) {
            return Stream.of(substrings).allMatch(s -> string.contains(s));
        }

        @Override
        public String toString() {
            return String.format(
                    "<String that contains all of the following: %s>",
                    String.join(", ", substrings));
        }

        /** Static factory for more fluent assertions. */
        public static SubstringMatcher matchesAll(String... substrings) {
            return new SubstringMatcher(substrings);
        }
    }
}
