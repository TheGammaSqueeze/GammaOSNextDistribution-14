/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.AppCrashOccurred;
import com.android.os.AtomsProto.AppStartOccurred;
import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.StatsLog.StatsLogReport;
import com.android.os.sdksandbox.SandboxApiCalled;
import com.android.os.sdksandbox.SdksandboxExtensionAtoms;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;

import com.google.common.io.CharStreams;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link StatsdGenericPostProcessor}. */
@RunWith(JUnit4.class)
public class StatsdGenericPostProcessorTest {
    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @Mock private ITestInvocationListener mListener;
    @Mock private ILogSaver mLogSaver;
    private StatsdGenericPostProcessor mProcessor;
    private OptionSetter mOptionSetter;

    // Mirrors the option in the post processor.
    private static final String PREFIX_OPTION = "statsd-report-data-prefix";
    private static final String OUTPUT_PROTO_OPTION = "output-statsd-report-proto";

    private static final String APP_STARTUP_FILENAME = "app-startup.pb";
    private static final String APP_CRASH_FILENAME = "app-crash.pb";
    private static final String BAD_REPORT_FILENAME = "bad-report.pb";

    private static final String EXTENSION_ATOM_REPORT_FILENAME = "extension-atom-report.pb";
    private static final String REPORT_PREFIX_APP_START = "app-start";
    private static final String REPORT_PREFIX_APP_CRASH = "app-crash";
    private static final String REPORT_PREFIX_BAD = "bad";

    private static final String REPORT_PREFIX_EXTENSION_ATOM = "extension-atom";
    private static final ConfigMetricsReportList APP_STARTUP_REPORT =
            generateReportListProto(generateAppStartupData());
    private static final ConfigMetricsReportList APP_CRASH_REPORT =
            generateReportListProto(generateAppCrashData());

    private static final ConfigMetricsReportList EXTENSION_ATOM_REPORT =
            generateReportListProto(generateSandboxApiCalledData());

    // A few constants that stand in for metric values, to increase readability of the assertions.
    private static final long APP_START_NANOS = 7;
    private static final String APP_START_PACKAGE = "startup.package";
    private static final int APP_START_DURATION = 500;
    private static final long APP_CRASH_NANOS = 11;
    private static final String APP_CRASH_PACKAGE = "crash.package";
    private static final int EXTENSION_ATOM_NANOS = 10;
    private static final int EXTENSION_ATOM_UID = 10101;
    private static final int EXTENSION_ATOM_LATENCY_MILLIS = 15;
    private static final boolean EXTENSION_ATOM_SUCCESS = true;

    private File mAppStartupReportFile;
    private File mAppCrashReportFile;
    private File mBadReportFile;
    private File mExtensionAtomReportFile;

    @Before
    public void setUp() throws IOException, ConfigurationException {
        initMocks(this);
        mProcessor = new StatsdGenericPostProcessor();
        mProcessor.init(mListener);
        mOptionSetter = new OptionSetter(mProcessor);

        mAppStartupReportFile = testDir.newFile(APP_STARTUP_FILENAME);
        Files.write(mAppStartupReportFile.toPath(), APP_STARTUP_REPORT.toByteArray());

        mAppCrashReportFile = testDir.newFile(APP_CRASH_FILENAME);
        Files.write(mAppCrashReportFile.toPath(), APP_CRASH_REPORT.toByteArray());

        mBadReportFile = testDir.newFile(BAD_REPORT_FILENAME);
        Files.write(mBadReportFile.toPath(), "not a report".getBytes());

        mExtensionAtomReportFile = testDir.newFile(EXTENSION_ATOM_REPORT_FILENAME);
        Files.write(mExtensionAtomReportFile.toPath(), EXTENSION_ATOM_REPORT.toByteArray());
    }

    /** Test that the post processor can parse reports from test metrics. */
    @Test
    public void testParsingTestMetrics() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        Map<String, LogFile> testLogs = new HashMap<>();
        testLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processTestMetricsAndLogs(
                        new TestDescription("class", "test"), new HashMap<>(), testLogs);
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList("app_start_occurred", "windows_drawn_delay_millis"));
        assertMetricsContain(
                parsedMetrics, APP_START_PACKAGE, Arrays.asList("app_start_occurred", "pkg_name"));
        assertMetricsContain(parsedMetrics, "COLD", Arrays.asList("app_start_occurred", "type"));
    }

    /** Test that the post processor can parse reports from run metrics. */
    @Test
    public void testParsingRunMetrics() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList("app_start_occurred", "windows_drawn_delay_millis"));
        assertMetricsContain(
                parsedMetrics, APP_START_PACKAGE, Arrays.asList("app_start_occurred", "pkg_name"));
        assertMetricsContain(parsedMetrics, "COLD", Arrays.asList("app_start_occurred", "type"));
    }

    /** Test that the post processor can parse reports for a exntension atoms. */
    @Test
    public void testParsingExtensionAtom() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_EXTENSION_ATOM);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_EXTENSION_ATOM + "-report",
                new LogFile(
                        mExtensionAtomReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);

        assertMetricsContain(
                parsedMetrics,
                SandboxApiCalled.Method.LOAD_SDK,
                Arrays.asList("sandbox_api_called", "method"));

        assertMetricsContain(
                parsedMetrics,
                EXTENSION_ATOM_LATENCY_MILLIS,
                Arrays.asList("sandbox_api_called", "latency_millis"));

        assertMetricsContain(
                parsedMetrics,
                EXTENSION_ATOM_SUCCESS,
                Arrays.asList("sandbox_api_called", "success"));

        assertMetricsContain(
                parsedMetrics,
                SandboxApiCalled.Stage.SANDBOX,
                Arrays.asList("sandbox_api_called", "stage"));

        assertMetricsContain(
                parsedMetrics, EXTENSION_ATOM_UID, Arrays.asList("sandbox_api_called", "uid"));
    }

    @Test
    public void testLogsProtosFromTestsWithTestDescriptionIfConfigured()
            throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        mOptionSetter.setOptionValue(OUTPUT_PROTO_OPTION, LogDataType.TEXTPB.toString());
        Map<String, LogFile> testLogs = new HashMap<>();
        TestDescription description = new TestDescription("class", "test");
        testLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        mProcessor.processTestMetricsAndLogs(description, new HashMap<>(), testLogs);
        String expectedTextPb = TextFormat.printer().printToString(APP_STARTUP_REPORT);

        verify(mListener, times(1))
                .testLog(
                        argThat(
                                dataName ->
                                        dataName.contains(REPORT_PREFIX_APP_START)
                                                && dataName.contains(description.toString())),
                        eq(LogDataType.TEXTPB),
                        argThat(contentMatches(expectedTextPb)));
    }

    // Tests from this point on will test on run metrics only, as the above tests have verified that
    // the post processor is capable of processing test metrics and the processing logic is shared
    // between run metrics and test metrics processing.

    /**
     * Test that the post processor can parse multiple metrics reports and differentiate them in the
     * metric key.
     */
    @Test
    public void testMultipleReports() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_CRASH);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        runLogs.put(
                REPORT_PREFIX_APP_CRASH + "-report",
                new LogFile(
                        mAppCrashReportFile.getAbsolutePath(), "some.other.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // Assertions on contents of metric report 1.
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList(
                        REPORT_PREFIX_APP_START,
                        "app_start_occurred",
                        "windows_drawn_delay_millis"));
        assertMetricsContain(
                parsedMetrics,
                APP_START_PACKAGE,
                Arrays.asList(REPORT_PREFIX_APP_START, "app_start_occurred", "pkg_name"));
        assertMetricsContain(
                parsedMetrics,
                "COLD",
                Arrays.asList(REPORT_PREFIX_APP_START, "app_start_occurred", "type"));
        // Assertions on contents of metric report 2.
        assertMetricsContain(
                parsedMetrics,
                APP_CRASH_PACKAGE,
                Arrays.asList(REPORT_PREFIX_APP_CRASH, "app_crash_occurred", "package_name"));
        assertMetricsContain(
                parsedMetrics,
                "UNKNOWN",
                Arrays.asList(REPORT_PREFIX_APP_CRASH, "app_crash_occurred", "foreground_state"));
    }

    /** Test that the post processor only parses reports specified in the prefix option. */
    @Test
    public void testExtraReportsIgnored() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        runLogs.put(
                REPORT_PREFIX_APP_CRASH + "-report",
                new LogFile(
                        mAppCrashReportFile.getAbsolutePath(), "some.other.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // Assertions on contents of metric report 1.
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList(
                        REPORT_PREFIX_APP_START,
                        "app_start_occurred",
                        "windows_drawn_delay_millis"));
        assertMetricsContain(
                parsedMetrics,
                APP_START_PACKAGE,
                Arrays.asList(REPORT_PREFIX_APP_START, "app_start_occurred", "pkg_name"));
        assertMetricsContain(
                parsedMetrics,
                "COLD",
                Arrays.asList(REPORT_PREFIX_APP_START, "app_start_occurred", "type"));
        // Assertions that metrics from report 2 are ignored.
        assertMetricsDoNotContain(
                parsedMetrics,
                Arrays.asList(REPORT_PREFIX_APP_CRASH, "app_crash_occurred", "package_name"));
        assertMetricsDoNotContain(
                parsedMetrics,
                Arrays.asList(REPORT_PREFIX_APP_CRASH, "app_crash_occurred", "foreground_state"));
    }

    /** Test that an invalid report is ignored and does not affect parsing other reports. */
    @Test
    public void testInvalidReportIgnored() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_BAD);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_BAD + "-report",
                new LogFile(mBadReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(
                        mAppStartupReportFile.getAbsolutePath(), "some.other.url", LogDataType.PB));
        // This should not throw.
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // Info from metric report 1 should end up in the metrics.
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList("report", "app_start_occurred", "windows_drawn_delay_millis"));
    }

    /**
     * Test the parsing logic of the metric report messages.
     *
     * <p>Tests the full expansion of the report tree structure: the proto message is parsed such
     * that each non-proto value nested within is treated as a leaf node, which is a metric value,
     * while the full path of field names from the root to the value is concatenated and turned into
     * the metric key. Repeated fields are treated as children of its parent node with its index
     * appended to the field name.
     */
    @Test
    public void testParsingLogic() throws IOException, ConfigurationException {
        // Create a report list structure that looks like:
        // - report 1: metric 1 -> app startup data, metric 2 -> app crash data
        // - report 2: metric 1 -> app crash data
        ConfigMetricsReportList testProto = generateReportListProto(generateAppStartupData());
        testProto =
                testProto
                        .toBuilder()
                        .setReports(
                                0,
                                testProto
                                        .getReports(0)
                                        .toBuilder()
                                        .addMetrics(generateStatsLogReport(generateAppCrashData())))
                        .build();
        testProto =
                testProto
                        .toBuilder()
                        .addReports(
                                ConfigMetricsReport.newBuilder()
                                        .addMetrics(generateStatsLogReport(generateAppCrashData())))
                        .build();

        // Set up the test proto report file.
        String testFilename = "test_report.pb";
        File testMetricFile = testDir.newFile(testFilename);
        Files.write(testMetricFile.toPath(), testProto.toByteArray());

        // Parse the metrics and verify.
        String reportPrefix = "test";
        mOptionSetter.setOptionValue(PREFIX_OPTION, reportPrefix);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                reportPrefix + "-report",
                new LogFile(testMetricFile.getAbsolutePath(), "some.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // report 1 + metric 1
        assertMetricsContain(
                parsedMetrics,
                APP_START_NANOS,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                "metrics",
                                "event_metrics",
                                "data",
                                "elapsed_timestamp_nanos")));
        assertMetricsContain(
                parsedMetrics,
                APP_START_DURATION,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                "metrics",
                                "event_metrics",
                                "data",
                                "atom",
                                "app_start_occurred",
                                "windows_drawn_delay_millis")));
        assertMetricsContain(
                parsedMetrics,
                APP_START_PACKAGE,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                "metrics",
                                "event_metrics",
                                "data",
                                "atom",
                                "app_start_occurred",
                                "pkg_name")));
        assertMetricsContain(
                parsedMetrics,
                "COLD",
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                "metrics",
                                "event_metrics",
                                "data",
                                "atom",
                                "app_start_occurred",
                                "type")));
        // report 1 + metric 2
        assertMetricsContain(
                parsedMetrics,
                APP_CRASH_NANOS,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "metrics", "2"),
                                "event_metrics",
                                "data",
                                "elapsed_timestamp_nanos")));
        assertMetricsContain(
                parsedMetrics,
                APP_CRASH_PACKAGE,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "metrics", "2"),
                                "event_metrics",
                                "data",
                                "atom",
                                "app_crash_occurred",
                                "package_name")));
        assertMetricsContain(
                parsedMetrics,
                "UNKNOWN",
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                "reports",
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "metrics", "2"),
                                "event_metrics",
                                "data",
                                "atom",
                                "app_crash_occurred",
                                "foreground_state")));
        // report 2 + metric 1
        assertMetricsContain(
                parsedMetrics,
                APP_CRASH_NANOS,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "reports", "2"),
                                "metrics",
                                "event_metrics",
                                "data",
                                "elapsed_timestamp_nanos")));
        assertMetricsContain(
                parsedMetrics,
                APP_CRASH_PACKAGE,
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "reports", "2"),
                                "metrics",
                                "event_metrics",
                                "data",
                                "atom",
                                "app_crash_occurred",
                                "package_name")));
        assertMetricsContain(
                parsedMetrics,
                "UNKNOWN",
                Arrays.asList(
                        String.join(
                                StatsdGenericPostProcessor.METRIC_SEP,
                                reportPrefix,
                                String.join(StatsdGenericPostProcessor.INDEX_SEP, "reports", "2"),
                                "metrics",
                                "event_metrics",
                                "data",
                                "atom",
                                "app_crash_occurred",
                                "foreground_state")));
    }

    /**
     * Test that the parsing skips unwanted fields in {@link ConfigMetricsReport}, in this case the
     * "strings" field.
     */
    @Test
    public void testUnwantedFieldsSkipped() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        Map<String, Metric.Builder> parsedMetrics =
                mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // There should not be any metrics containing the "strings" field.
        assertMetricsDoNotContain(
                parsedMetrics, Arrays.asList(StatsdGenericPostProcessor.METRIC_SEP + "strings"));
    }

    @Test
    public void testNoOutputtingProtosIfNotSpecified() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        // There should not be logging of the statsd proto in any form.
        verify(mListener, never()).testLog(any(String.class), eq(LogDataType.TEXTPB), any());
        verify(mListener, never()).testLog(any(String.class), eq(LogDataType.JSON), any());
    }

    @Test
    public void testReportsReadableProtoFormatsIfSpecified()
            throws ConfigurationException, IOException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        mOptionSetter.setOptionValue(OUTPUT_PROTO_OPTION, LogDataType.TEXTPB.toString());
        mOptionSetter.setOptionValue(OUTPUT_PROTO_OPTION, LogDataType.JSON.toString());
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);
        String expectedTextPb = TextFormat.printer().printToString(APP_STARTUP_REPORT);
        String expectedJson = JsonFormat.printer().print(APP_STARTUP_REPORT);

        verify(mListener, times(1))
                .testLog(
                        argThat(
                                dataName ->
                                        dataName.contains(REPORT_PREFIX_APP_START)
                                                && dataName.contains("TestRun")),
                        eq(LogDataType.TEXTPB),
                        argThat(contentMatches(expectedTextPb)));
        verify(mListener, times(1))
                .testLog(
                        argThat(
                                dataName ->
                                        dataName.contains(REPORT_PREFIX_APP_START)
                                                && dataName.contains("TestRun")),
                        eq(LogDataType.JSON),
                        argThat(contentMatches(expectedJson)));
    }

    @Test
    public void testReportingProtoIgnoresUnsupportedFormats() throws ConfigurationException {
        mOptionSetter.setOptionValue(PREFIX_OPTION, REPORT_PREFIX_APP_START);
        mOptionSetter.setOptionValue(OUTPUT_PROTO_OPTION, LogDataType.PNG.toString());
        mOptionSetter.setOptionValue(OUTPUT_PROTO_OPTION, LogDataType.JSON.toString());
        Map<String, LogFile> runLogs = new HashMap<>();
        runLogs.put(
                REPORT_PREFIX_APP_START + "-report",
                new LogFile(mAppStartupReportFile.getAbsolutePath(), "some.url", LogDataType.PB));
        mProcessor.processRunMetricsAndLogs(new HashMap<>(), runLogs);

        // JSON should be logged, but PNG should be ignored.
        verify(mListener, times(1))
                .testLog(
                        argThat(
                                dataName ->
                                        dataName.contains(REPORT_PREFIX_APP_START)
                                                && dataName.contains("TestRun")),
                        any(LogDataType.class),
                        any(ByteArrayInputStreamSource.class));
        verify(mListener, never())
                .testLog(
                        argThat(
                                dataName ->
                                        dataName.contains(REPORT_PREFIX_APP_START)
                                                && dataName.contains("TestRun")),
                        eq(LogDataType.PNG),
                        any(ByteArrayInputStreamSource.class));
    }

    private ArgumentMatcher<ByteArrayInputStreamSource> contentMatches(String expectedContent) {
        return byteStreamSource -> {
            String content = null;
            try (InputStreamReader reader =
                    new InputStreamReader(byteStreamSource.createInputStream())) {
                content = CharStreams.toString(reader);
            } catch (IOException e) {
                // Do nothing; fileContent won't be set, and the test will fail.
            }
            return expectedContent.equals(content);
        };
    }

    /**
     * Test the default metric type is set to RAW.
     */
    @Test
    public void testMetricTypeIsRaw() {
        assertTrue(mProcessor.getMetricType().equals(DataType.RAW));
    }

    /** Assert that metrics contain a key with a set of components and a corresponding value. */
    private void assertMetricsContain(
            Map<String, Metric.Builder> metrics, Object value, List<String> keyComponents) {
        assertTrue(
                String.format(
                        "Metric with key containing %s and value %s was expected but not found.",
                        Arrays.asList(keyComponents), value),
                metrics.entrySet()
                        .stream()
                        .anyMatch(
                                e ->
                                        keyComponents.stream().allMatch(c -> e.getKey().contains(c))
                                                && String.valueOf(value)
                                                        .equals(
                                                                e.getValue()
                                                                        .build()
                                                                        .getMeasurements()
                                                                        .getSingleString())));
    }

    /** Assert that metrics do not contain a key with a series of components. */
    private void assertMetricsDoNotContain(
            Map<String, Metric.Builder> metrics, List<String> keyComponents) {
        assertTrue(
                String.format(
                        "Metric with key containing %s was found but not expected.",
                        Arrays.asList(keyComponents)),
                metrics.keySet()
                        .stream()
                        .noneMatch(k -> keyComponents.stream().allMatch(c -> k.contains(c))));
    }

    /** Generates an app startup event for testing purposes. */
    private static EventMetricData generateAppStartupData() {
        return EventMetricData.newBuilder()
                .setElapsedTimestampNanos(APP_START_NANOS)
                .setAtom(
                        Atom.newBuilder()
                                .setAppStartOccurred(
                                        AppStartOccurred.newBuilder()
                                                .setPkgName(APP_START_PACKAGE)
                                                .setType(AppStartOccurred.TransitionType.COLD)
                                                .setWindowsDrawnDelayMillis(APP_START_DURATION)))
                .build();
    }

    /** Generates a Sandbox Api Called (extension atom proto) event for testing purposes. */
    private static EventMetricData generateSandboxApiCalledData() {
        return EventMetricData.newBuilder()
                .setElapsedTimestampNanos(EXTENSION_ATOM_NANOS)
                .setAtom(
                        Atom.newBuilder()
                                .setExtension(
                                        SdksandboxExtensionAtoms.sandboxApiCalled,
                                        SandboxApiCalled.newBuilder()
                                                .setMethod(SandboxApiCalled.Method.LOAD_SDK)
                                                .setLatencyMillis(EXTENSION_ATOM_LATENCY_MILLIS)
                                                .setSuccess(EXTENSION_ATOM_SUCCESS)
                                                .setStage(SandboxApiCalled.Stage.SANDBOX)
                                                .setUid(EXTENSION_ATOM_UID)
                                                .build()))
                .build();
    }

    /** Generates an app crash event for testing purposes. */
    private static EventMetricData generateAppCrashData() {
        return EventMetricData.newBuilder()
                .setElapsedTimestampNanos(APP_CRASH_NANOS)
                .setAtom(
                        Atom.newBuilder()
                                .setAppCrashOccurred(
                                        AppCrashOccurred.newBuilder()
                                                .setPackageName(APP_CRASH_PACKAGE)
                                                .setForegroundState(
                                                        AppCrashOccurred.ForegroundState.UNKNOWN)))
                .build();
    }

    /** Wrap an {@link EventMetricData} message in a ConfigMetricsReportList. */
    private static ConfigMetricsReportList generateReportListProto(EventMetricData metrics) {
        return ConfigMetricsReportList.newBuilder()
                .addReports(
                        ConfigMetricsReport.newBuilder()
                                .addMetrics(generateStatsLogReport(metrics))
                                .addStrings("some string"))
                .build();
    }

    /** Generates a {@link StatsLogReport} from an {@link EventMetricData} instance. */
    private static StatsLogReport generateStatsLogReport(EventMetricData data) {
        return StatsLogReport.newBuilder()
                .setEventMetrics(StatsLogReport.EventMetricDataWrapper.newBuilder().addData(data))
                .build();
    }
}
