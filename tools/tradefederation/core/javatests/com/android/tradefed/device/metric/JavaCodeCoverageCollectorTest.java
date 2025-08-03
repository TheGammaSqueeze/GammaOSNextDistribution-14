/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tradefed.device.metric;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.coverage.CoverageOptions;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.JavaCodeCoverageFlusher;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.internal.data.CRC64;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link JavaCodeCoverageCollector}. */
@RunWith(JUnit4.class)
public class JavaCodeCoverageCollectorTest {

    private static final int PROBE_COUNT = 10;

    private static final String RUN_NAME = "SomeTest";
    private static final int TEST_COUNT = 5;
    private static final long ELAPSED_TIME = 1000;

    private static final String DEVICE_PATH = "/some/path/on/the/device.ec";
    private static final ByteString COVERAGE_MEASUREMENT =
            ByteString.copyFromUtf8("Mi estas kovrado mezurado");

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Mock IConfiguration mMockConfiguration;
    @Mock IInvocationContext mMockContext;
    @Mock ITestDevice mMockDevice;
    @Mock JavaCodeCoverageFlusher mMockFlusher;

    @Spy LogFileReader mFakeListener = new LogFileReader();

    /** Object under test. */
    JavaCodeCoverageCollector mCodeCoverageCollector;

    CoverageOptions mCoverageOptions = null;
    OptionSetter mCoverageOptionsSetter = null;
    List<File> mFilesToClean;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mCoverageOptions = new CoverageOptions();
        mCoverageOptionsSetter = new OptionSetter(mCoverageOptions);
        mFilesToClean = new ArrayList<>();

        when(mMockConfiguration.getCoverageOptions()).thenReturn(mCoverageOptions);

        when(mMockContext.getDevices()).thenReturn(ImmutableList.of(mMockDevice));
        when(mMockContext.getAttributes())
                .thenReturn(
                        new MultiMap(ImmutableMap.of(ModuleDefinition.MODULE_NAME, "myModule")));

        // Mock an unrooted device that has no issues enabling or disabling root.
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(true);
        when(mMockDevice.disableAdbRoot()).thenReturn(true);

        mCodeCoverageCollector = new JavaCodeCoverageCollector();
        mCodeCoverageCollector.setConfiguration(mMockConfiguration);
    }

    @After
    public void cleanUp() throws IOException {
        for (File file : mFilesToClean) {
            file.delete();
        }
    }

    @Test
    public void testRunEnded_noCoverageEnabled_noop() throws Exception {
        // Setup mocks.
        HashMap<String, Metric> runMetrics = new HashMap<>();

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        // Verify testLog(..) was not called.
        verify(mFakeListener, never())
                .testLog(anyString(), eq(LogDataType.COVERAGE), eq(COVERAGE_MEASUREMENT));
    }

    @Test
    public void testRunEnded_rootEnabled_logsCoverageMeasurement() throws Exception {
        enableJavaCoverage();
        mCoverageOptionsSetter.setOptionValue("pull-timeout", "314159");

        // Setup mocks.
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        mockCoverageFileOnDevice(DEVICE_PATH);
        when(mMockDevice.isAdbRoot()).thenReturn(true);
        doReturn("").when(mMockDevice).executeShellCommand(anyString());
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        // Verify timeout is set.
        verify(mMockDevice, times(1))
                .executeShellV2Command(
                        eq("find /data/misc/trace -name '*.ec' | tar -czf - -T - 2>/dev/null"),
                        any(),
                        any(),
                        eq(314159L),
                        eq(TimeUnit.MILLISECONDS),
                        eq(1));

        // Verify testLog(..) was called with the coverage file.
        verify(mFakeListener)
                .testLog(anyString(), eq(LogDataType.COVERAGE), eq(COVERAGE_MEASUREMENT));
    }

    @Test
    public void testRunEnded_rootEnabled_noModuleName_logsCoverageMeasurement() throws Exception {
        enableJavaCoverage();

        // Setup mocks.
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        mockCoverageFileOnDevice(DEVICE_PATH);
        when(mMockDevice.isAdbRoot()).thenReturn(true);
        when(mMockContext.getAttributes()).thenReturn(new MultiMap(ImmutableMap.of()));
        doReturn("").when(mMockDevice).executeShellCommand(anyString());
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        // Verify testLog(..) was called with the coverage file.
        verify(mFakeListener)
                .testLog(anyString(), eq(LogDataType.COVERAGE), eq(COVERAGE_MEASUREMENT));
    }

    @Test
    public void testFailure_unableToPullFile() throws Exception {
        enableJavaCoverage();
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        doReturn("").when(mMockDevice).executeShellCommand(anyString());
        doReturn(null).when(mMockDevice).pullFile(DEVICE_PATH);
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        verify(mFakeListener, never())
                .testLog(anyString(), eq(LogDataType.COVERAGE), any(InputStreamSource.class));
    }

    @Test
    public void testRunEnded_rootDisabled_enablesRootBeforePullingFiles() throws Exception {
        enableJavaCoverage();
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        mockCoverageFileOnDevice(DEVICE_PATH);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        doReturn("").when(mMockDevice).executeShellCommand(anyString());
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        InOrder inOrder = inOrder(mMockDevice);
        inOrder.verify(mMockDevice).enableAdbRoot();
        inOrder.verify(mMockDevice).pullFile(anyString());
    }

    @Test
    public void testRunEnded_rootDisabled_noLogIfCannotEnableRoot() throws Exception {
        enableJavaCoverage();
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        mockCoverageFileOnDevice(DEVICE_PATH);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        when(mMockDevice.enableAdbRoot()).thenReturn(false);

        // Simulate a test run.
        try {
            mCodeCoverageCollector.init(mMockContext, mFakeListener);
            fail("An exception should have been thrown.");
        } catch (RuntimeException e) {
            // Expected.
        }

        verify(mFakeListener, never())
                .testLog(anyString(), eq(LogDataType.COVERAGE), any(InputStreamSource.class));
    }

    @Test
    public void testRunEnded_rootDisabled_disablesRootAfterPullingFiles() throws Exception {
        enableJavaCoverage();
        HashMap<String, Metric> runMetrics = createMetricsWithCoverageMeasurement(DEVICE_PATH);
        mockCoverageFileOnDevice(DEVICE_PATH);
        when(mMockDevice.isAdbRoot()).thenReturn(false);
        doReturn("").when(mMockDevice).executeShellCommand(anyString());
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, runMetrics);

        InOrder inOrder = inOrder(mMockDevice);
        inOrder.verify(mMockDevice).pullFile(anyString());
        inOrder.verify(mMockDevice).disableAdbRoot();
    }

    @Test
    public void testCoverageFlush_producesMultipleMeasurements() throws Exception {
        enableJavaCoverage();

        Map<String, ByteString> coverageData =
                ImmutableMap.of(
                        "/data/misc/trace/com.android.test1.ec",
                        ByteString.copyFromUtf8("com.android.test1.ec"),
                        "/data/misc/trace/com.android.test2.ec",
                        ByteString.copyFromUtf8("com.android.test2.ec"),
                        "/data/misc/trace/com.google.test3.ec",
                        ByteString.copyFromUtf8("com.google.test3.ec"));

        mCoverageOptionsSetter.setOptionValue("coverage-flush", "true");

        // Setup mocks.
        mockCoverageFileOnDevice(DEVICE_PATH);

        doReturn("").when(mMockDevice).executeShellCommand("ps -e");
        doReturn("")
                .when(mMockDevice)
                .executeShellCommand(JavaCodeCoverageCollector.FIND_COVERAGE_FILES);
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(coverageData));

        mCodeCoverageCollector.setCoverageFlusher(mMockFlusher);

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        Map<String, String> metric = new HashMap<>();
        metric.put("coverageFilePath", DEVICE_PATH);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, TfMetricProtoUtil.upgradeConvert(metric));

        // Verify the coverage data was logged.
        for (ByteString contents : coverageData.values()) {
            verify(mFakeListener).testLog(anyString(), eq(LogDataType.COVERAGE), eq(contents));
        }
    }

    @Test
    public void testRunningProcess_coverageFileNotDeleted() throws Exception {
        enableJavaCoverage();

        List<String> coverageFileList =
                ImmutableList.of(
                        "/data/misc/trace/coverage1.ec",
                        "/data/misc/trace/coverage2.ec",
                        "/data/misc/trace/jacoco-123.mm.ec",
                        "/data/misc/trace/jacoco-456.mm.ec");
        String psOutput =
                "USER       PID   PPID  VSZ   RSS   WCHAN       PC  S NAME\n"
                    + "bluetooth   123  1366  123    456   SyS_epoll+   0  S"
                    + " com.android.bluetooth\n"
                    + "radio       890     1 7890   123   binder_io+   0  S com.android.phone\n"
                    + "root         11  1234  567   890   binder_io+   0  S not.a.java.package\n";

        // Setup mocks.
        mockCoverageFileOnDevice(DEVICE_PATH);

        for (String additionalFile : coverageFileList) {
            mockCoverageFileOnDevice(additionalFile);
        }

        doReturn("").when(mMockDevice).executeShellCommand("pm list packages -a");
        doReturn(psOutput).when(mMockDevice).executeShellCommand("ps -e");
        doReturn(String.join("\n", coverageFileList))
                .when(mMockDevice)
                .executeShellCommand("find /data/misc/trace -name '*.ec'");
        returnFileContentsOnShellCommand(mMockDevice, createTarGz(ImmutableMap.of()));

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        Map<String, String> metric = new HashMap<>();
        metric.put("coverageFilePath", DEVICE_PATH);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, TfMetricProtoUtil.upgradeConvert(metric));

        // Verify the correct files were deleted and some files were not deleted.
        verify(mMockDevice).deleteFile(coverageFileList.get(0));
        verify(mMockDevice).deleteFile(coverageFileList.get(1));
        verify(mMockDevice, never()).deleteFile(coverageFileList.get(2));
        verify(mMockDevice).deleteFile(coverageFileList.get(3));
    }

    @Test
    public void testStreamingCoverage_logsReceived() throws Exception {
        enableJavaCoverage();

        String path1 = "path/to/coverage1.ec";
        ByteString contents1 = ByteString.copyFromUtf8("File contents 1");
        String path2 = "path/to/coverage2.ec";
        ByteString contents2 = ByteString.copyFromUtf8("File contents 2");
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                path1, contents1,
                                path2, contents2));

        // Return the tar.gz file when running the stream-compress command.
        returnFileContentsOnShellCommand(mMockDevice, tarGz);

        // Return no data for the `ps -e` command.
        doReturn("").when(mMockDevice).executeShellCommand(anyString());

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, new HashMap<String, Metric>());

        // Verify that the coverage data was logged.
        verify(mFakeListener).testLog(anyString(), eq(LogDataType.COVERAGE), eq(contents1));
        verify(mFakeListener).testLog(anyString(), eq(LogDataType.COVERAGE), eq(contents2));
    }

    @Test
    public void testInitNoResetCoverage_noop() throws Exception {
        enableJavaCoverage();
        mCoverageOptionsSetter.setOptionValue("reset-coverage-before-test", "false");

        // Run init(...).
        mCodeCoverageCollector.init(mMockContext, mFakeListener);

        // Verify that nothing was run on the device.
        verifyNoMoreInteractions(mMockDevice);
    }

    @Test
    public void testMergeSingleMeasurement_logReceived() throws Exception {
        enableJavaCoverage();
        mCoverageOptionsSetter.setOptionValue("merge-coverage", "true");

        doReturn("").when(mMockDevice).executeShellCommand(anyString());

        ByteString measurement = measurement(firstHalfCovered(JavaCodeCoverageCollector.class));
        File tarGz = createTarGz(ImmutableMap.of("path/to/coverage.ec", measurement));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, new HashMap<String, Metric>());

        // Validate the logged coverage data.
        ArgumentCaptor<ByteString> stream = ArgumentCaptor.forClass(ByteString.class);
        verify(mFakeListener).testLog(anyString(), eq(LogDataType.COVERAGE), stream.capture());

        ExecFileLoader execFileLoader = new ExecFileLoader();
        execFileLoader.load(stream.getValue().newInput());

        ExecutionDataStore execData = execFileLoader.getExecutionDataStore();
        boolean[] firstHalf = new boolean[PROBE_COUNT];
        for (int i = 0; i < PROBE_COUNT / 2; i++) {
            firstHalf[i] = true;
        }

        assertThat(execData.contains(vmName(JavaCodeCoverageCollector.class))).isTrue();
        assertThat(getProbes(JavaCodeCoverageCollector.class, execData)).isEqualTo(firstHalf);
    }

    @Test
    public void testMergeMultipleMeasurements_logContainsAllData() throws Exception {
        enableJavaCoverage();
        mCoverageOptionsSetter.setOptionValue("merge-coverage", "true");

        doReturn("").when(mMockDevice).executeShellCommand(anyString());

        ByteString firstHalfCollector =
                measurement(firstHalfCovered(JavaCodeCoverageCollector.class));
        ByteString secondHalfCollector =
                measurement(secondHalfCovered(JavaCodeCoverageCollector.class));
        ByteString partialCollectorTest =
                measurement(partiallyCovered(JavaCodeCoverageCollectorTest.class));
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "JavaCodeCoverageColletor1.ec", firstHalfCollector,
                                "JavaCodeCoverageCollector2.ec", secondHalfCollector,
                                "JavaCodeCoverageCollectorTest.ec", partialCollectorTest));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);

        // Simulate a test run.
        mCodeCoverageCollector.init(mMockContext, mFakeListener);
        mCodeCoverageCollector.testRunStarted(RUN_NAME, TEST_COUNT);
        mCodeCoverageCollector.testRunEnded(ELAPSED_TIME, new HashMap<String, Metric>());

        // Validate the logged coverage data.
        ArgumentCaptor<ByteString> stream = ArgumentCaptor.forClass(ByteString.class);
        verify(mFakeListener).testLog(anyString(), eq(LogDataType.COVERAGE), stream.capture());

        ExecFileLoader execFileLoader = new ExecFileLoader();
        execFileLoader.load(stream.getValue().newInput());

        ExecutionDataStore execData = execFileLoader.getExecutionDataStore();

        // Check coverage data for JavaCodeCoverageCollector. All probes should be true if the data
        // merged successfully.
        boolean[] fullyCovered = new boolean[PROBE_COUNT];
        Arrays.fill(fullyCovered, Boolean.TRUE);

        assertThat(execData.contains(vmName(JavaCodeCoverageCollector.class))).isTrue();
        assertThat(getProbes(JavaCodeCoverageCollector.class, execData)).isEqualTo(fullyCovered);

        // Check coverage data for JavaCodeCoverageCollectorTest. Only the first probe should be
        // true.
        boolean[] partiallyCovered = new boolean[PROBE_COUNT];
        partiallyCovered[0] = true;

        assertThat(execData.contains(vmName(JavaCodeCoverageCollectorTest.class))).isTrue();
        assertThat(getProbes(JavaCodeCoverageCollectorTest.class, execData))
                .isEqualTo(partiallyCovered);
    }

    private void mockCoverageFileOnDevice(String devicePath)
            throws IOException, DeviceNotAvailableException {
        File coverageFile = folder.newFile(new File(devicePath).getName());

        try (OutputStream out = new FileOutputStream(coverageFile)) {
            COVERAGE_MEASUREMENT.writeTo(out);
        }

        doReturn(coverageFile).when(mMockDevice).pullFile(devicePath);
    }

    private static <T> String vmName(Class<T> clazz) {
        return clazz.getName().replace('.', '/');
    }

    private static <T> ExecutionData fullyCovered(Class<T> clazz) throws IOException {
        boolean[] probes = new boolean[PROBE_COUNT];
        Arrays.fill(probes, Boolean.TRUE);
        return new ExecutionData(classId(clazz), vmName(clazz), probes);
    }

    private static <T> ExecutionData partiallyCovered(Class<T> clazz) throws IOException {
        boolean[] probes = new boolean[PROBE_COUNT];
        probes[0] = true;
        return new ExecutionData(classId(clazz), vmName(clazz), probes);
    }

    private static <T> ExecutionData firstHalfCovered(Class<T> clazz) throws IOException {
        boolean[] probes = new boolean[PROBE_COUNT];
        for (int i = 0; i < PROBE_COUNT / 2; i++) {
            probes[i] = true;
        }
        return new ExecutionData(classId(clazz), vmName(clazz), probes);
    }

    private static <T> ExecutionData secondHalfCovered(Class<T> clazz) throws IOException {
        boolean[] probes = new boolean[PROBE_COUNT];
        for (int i = PROBE_COUNT / 2; i < PROBE_COUNT; i++) {
            probes[i] = true;
        }
        return new ExecutionData(classId(clazz), vmName(clazz), probes);
    }

    private static <T> long classId(Class<T> clazz) throws IOException {
        return Long.valueOf(CRC64.classId(classBytes(clazz).toByteArray()));
    }

    private static <T> ByteString classBytes(Class<T> clazz) throws IOException {
        return ByteString.readFrom(
                clazz.getClassLoader().getResourceAsStream(vmName(clazz) + ".class"));
    }

    private static ByteString measurement(ExecutionData... data) throws IOException {
        ExecutionDataStore dataStore = new ExecutionDataStore();
        Arrays.stream(data).forEach(dataStore::put);

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            dataStore.accept(new ExecutionDataWriter(bytes));
            return ByteString.copyFrom(bytes.toByteArray());
        }
    }

    private static <T> boolean[] getProbes(Class<T> clazz, ExecutionDataStore execData)
            throws IOException {
        return execData.get(classId(clazz), vmName(clazz), PROBE_COUNT).getProbesCopy();
    }

    private static HashMap<String, Metric> createMetricsWithCoverageMeasurement(String devicePath) {
        return TfMetricProtoUtil.upgradeConvert(ImmutableMap.of("coverageFilePath", devicePath));
    }

    private static void returnFileContentsOnShellCommand(ITestDevice device, File file)
            throws DeviceNotAvailableException, IOException {
        doAnswer(
                        invocation -> {
                            OutputStream out = (OutputStream) invocation.getArgument(2);
                            try (InputStream in = new FileInputStream(file)) {
                                in.transferTo(out);
                            }
                            return new CommandResult(CommandStatus.SUCCESS);
                        })
                .when(device)
                .executeShellV2Command(
                        eq(JavaCodeCoverageCollector.COMPRESS_COVERAGE_FILES),
                        any(),
                        any(OutputStream.class),
                        anyLong(),
                        any(TimeUnit.class),
                        anyInt());
    }

    private File createTarGz(Map<String, ByteString> fileContents) throws IOException {
        File tarFile = folder.newFile();
        try (TarArchiveOutputStream out =
                new TarArchiveOutputStream(new FileOutputStream(tarFile))) {
            for (Map.Entry<String, ByteString> file : fileContents.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(file.getValue().size());

                out.putArchiveEntry(entry);
                file.getValue().writeTo(out);
                out.closeArchiveEntry();
            }
            File tarGz = TarUtil.gzip(tarFile);
            mFilesToClean.add(tarGz);
            return tarGz;
        } finally {
            tarFile.delete();
        }
    }

    private void enableJavaCoverage() throws ConfigurationException {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "JACOCO");
    }

    /** An {@link ITestInvocationListener} which reads test log data streams for verification. */
    private static class LogFileReader implements ITestInvocationListener {
        /**
         * Reads the contents of the {@code dataStream} and forwards it to the {@link
         * #testLog(String, LogDataType, ByteString)} method.
         */
        @Override
        public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
            try (InputStream input = dataStream.createInputStream()) {
                testLog(dataName, dataType, ByteString.readFrom(input));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** No-op method for {@link Spy} verification. */
        public void testLog(String dataName, LogDataType dataType, ByteString data) {}
    }
}

