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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.coverage.CoverageOptions;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/** Unit tests for {@link ClangCodeCoverageCollector}. */
@RunWith(JUnit4.class)
public class ClangCodeCoverageCollectorTest {

    private static final String RUN_NAME = "SomeTest";
    private static final int TEST_COUNT = 5;
    private static final long ELAPSED_TIME = 1000;

    private static final String PS_OUTPUT =
            "USER       PID   PPID  VSZ   RSS   WCHAN       PC  S NAME\n"
                    + "shell       123  1366  123    456   SyS_epoll+   0  S adbd\n";

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private HashMap<String, MetricMeasurement.Metric> mMetrics;

    /** Fakes, Mocks and Spies. */
    @Mock IBuildInfo mMockBuildInfo;

    @Mock IConfiguration mMockConfiguration;
    @Mock IBuildProvider mMockBuildProvider;
    @Mock ITestDevice mMockDevice;
    @Mock IInvocationContext mMockContext;
    @Spy CommandArgumentCaptor mCommandArgumentCaptor;
    LogFileReader mFakeListener = new LogFileReader();

    /** Options for coverage. */
    CoverageOptions mCoverageOptions;

    OptionSetter mCoverageOptionsSetter = null;

    /** Object under test. */
    ClangCodeCoverageCollector mListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Map<String, String> metric = new HashMap<>();
        mMetrics = TfMetricProtoUtil.upgradeConvert(metric);

        mCoverageOptions = new CoverageOptions();
        mCoverageOptionsSetter = new OptionSetter(mCoverageOptions);

        doReturn(mCoverageOptions).when(mMockConfiguration).getCoverageOptions();
        doReturn(mMockBuildProvider).when(mMockConfiguration).getBuildProvider();
        doReturn(mMockBuildInfo).when(mMockBuildProvider).getBuild();

        doReturn(ImmutableList.of(mMockDevice)).when(mMockContext).getDevices();
        when(mMockContext.getAttributes())
                .thenReturn(
                        new MultiMap(ImmutableMap.of(ModuleDefinition.MODULE_NAME, "myModule")));

        doReturn(PS_OUTPUT).when(mMockDevice).executeShellCommand("ps -e");

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("ffffffffff\n");
        result.setExitCode(0);
        when(mMockDevice.executeShellV2Command(anyString())).thenReturn(result);

        mListener = new ClangCodeCoverageCollector();
        mListener.setConfiguration(mMockConfiguration);
        mListener.setRunUtil(mCommandArgumentCaptor);
    }

    @Test
    public void coverageDisabled_noCoverageLog() throws Exception {
        mListener.init(mMockContext, mFakeListener);

        // Simulate a test run.
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify testLog(...) was never called.
        assertThat(mFakeListener.getLogs()).isEmpty();
    }

    @Test
    public void clangCoverageDisabled_noCoverageLog() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");

        mListener.init(mMockContext, mFakeListener);

        // Simulate a test run.
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify testLog(...) was never called.
        assertThat(mFakeListener.getLogs()).isEmpty();
    }

    @Test
    public void coverageFlushEnabled_flushCalled() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");
        mCoverageOptionsSetter.setOptionValue("coverage-flush", "true");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify flush-coverage command was called at the end of the test run.
        verify(mMockDevice).executeShellCommand("kill -37 123");
    }

    @Test
    public void testRun_logsCoverageFile() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");
        mCoverageOptionsSetter.setOptionValue("pull-timeout", "314159");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/.hidden/coverage2.profraw",
                                ByteString.copyFromUtf8("coverage2.profraw")));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);
        doReturn(createProfileToolZip()).when(mMockBuildInfo).getFile(anyString());

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify the timeout is set.
        verify(mMockDevice, times(1))
                .executeShellV2Command(
                        eq("find /data/misc/trace -name '*.profraw' | tar -czf - -T - 2>/dev/null"),
                        any(),
                        any(),
                        eq(314159L),
                        eq(TimeUnit.MILLISECONDS),
                        eq(1));
        // Verify that the command line contains the files above.
        List<String> command = mCommandArgumentCaptor.getCommand();
        checkListContainsSuffixes(
                command,
                ImmutableList.of(
                        "llvm-profdata",
                        "path/to/coverage.profraw",
                        "path/to/.hidden/coverage2.profraw"));

        // Verify testLog(..) was called with a single indexed profile data.
        List<ByteString> logs = mFakeListener.getLogs();
        assertThat(logs).hasSize(1);

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testRun_noModuleName_logsCoverageFile() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/.hidden/coverage2.profraw",
                                ByteString.copyFromUtf8("coverage2.profraw")));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);
        doReturn(createProfileToolZip()).when(mMockBuildInfo).getFile(anyString());
        when(mMockContext.getAttributes()).thenReturn(new MultiMap(ImmutableMap.of()));

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify that the command line contains the files above.
        List<String> command = mCommandArgumentCaptor.getCommand();
        checkListContainsSuffixes(
                command,
                ImmutableList.of(
                        "llvm-profdata",
                        "path/to/coverage.profraw",
                        "path/to/.hidden/coverage2.profraw"));

        // Verify testLog(..) was called with a single indexed profile data.
        List<ByteString> logs = mFakeListener.getLogs();
        assertThat(logs).hasSize(1);

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testRun_profraw_filter_option() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");
        mCoverageOptionsSetter.setOptionValue("profraw-filter", "file.*\\.profraw");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/file.profraw",
                                ByteString.copyFromUtf8("file.profraw"),
                                "path/to/file1.profraw",
                                ByteString.copyFromUtf8("file1.profraw")));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);
        doReturn(createProfileToolZip()).when(mMockBuildInfo).getFile(anyString());

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify that the command line contains the files above.
        List<String> command = mCommandArgumentCaptor.getCommand();
        checkListContainsSuffixes(
                command,
                ImmutableList.of("llvm-profdata", "path/to/file.profraw", "path/to/file1.profraw"));
        checkListDoesNotContainSuffix(command, "path/to/coverage.profraw");

        // Verify testLog(..) was called with a single indexed profile data.
        List<ByteString> logs = mFakeListener.getLogs();
        assertThat(logs).hasSize(1);

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testOtherFileTypes_ignored() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/coverage.gcda",
                                ByteString.copyFromUtf8("coverage.gcda")));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);

        doReturn(createProfileToolZip()).when(mMockBuildInfo).getFile(anyString());

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify that the command line contains the files above, not including the .gcda file.
        List<String> command = mCommandArgumentCaptor.getCommand();
        checkListContainsSuffixes(
                command, ImmutableList.of("llvm-profdata", "path/to/coverage.profraw"));
        checkListDoesNotContainSuffix(command, "path/to/coverage.gcda");

        // Verify testLog(..) was called with a single indexed profile data.
        List<ByteString> logs = mFakeListener.getLogs();
        assertThat(logs).hasSize(1);

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testNoClangMeasurements_noLogFile() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify testLog(..) was never called.
        assertThat(mFakeListener.getLogs()).isEmpty();
    }

    @Test
    public void testProfileToolInConfiguration_notFromBuild() throws Exception {
        File profileToolFolder = folder.newFolder();
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");
        mCoverageOptionsSetter.setOptionValue("llvm-profdata-path", profileToolFolder.getPath());

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/.hidden/coverage2.profraw",
                                ByteString.copyFromUtf8("coverage2.profraw")));
        returnFileContentsOnShellCommand(mMockDevice, tarGz);

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify that the command line contains the llvm-profile-path set above.
        List<String> command = mCommandArgumentCaptor.getCommand();
        assertThat(command.get(0)).isEqualTo(profileToolFolder.getPath() + "/bin/llvm-profdata");

        // Verify that the profile tool was not deleted.
        assertThat(profileToolFolder.exists()).isTrue();

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testProfileToolNotFound_noLog() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/.hidden/coverage2.profraw",
                                ByteString.copyFromUtf8("coverage2.profraw")));
        doReturn(tarGz).when(mMockDevice).pullFile(anyString());

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify testLog(..) was never called.
        assertThat(mFakeListener.getLogs()).isEmpty();

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testProfileToolFailed_noLog() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        doReturn(true).when(mMockDevice).isAdbRoot();
        File tarGz =
                createTarGz(
                        ImmutableMap.of(
                                "path/to/coverage.profraw",
                                ByteString.copyFromUtf8("coverage.profraw"),
                                "path/to/.hidden/coverage2.profraw",
                                ByteString.copyFromUtf8("coverage2.profraw")));
        doReturn(tarGz).when(mMockDevice).pullFile(anyString());
        doReturn(createProfileToolZip()).when(mMockBuildInfo).getFile(anyString());

        mCommandArgumentCaptor.setResult(CommandStatus.FAILED);

        // Simulate a test run.
        mListener.init(mMockContext, mFakeListener);
        mListener.testRunStarted(RUN_NAME, TEST_COUNT);
        mListener.testRunEnded(ELAPSED_TIME, mMetrics);
        mListener.invocationEnded(ELAPSED_TIME);

        // Verify testLog(..) was never called.
        assertThat(mFakeListener.getLogs()).isEmpty();

        FileUtil.deleteFile(tarGz);
    }

    @Test
    public void testInit_adbRootAndCoverageFlush() throws Exception {
        mCoverageOptionsSetter.setOptionValue("coverage", "true");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Setup mocks.
        when(mMockDevice.isAdbRoot()).thenReturn(false).thenReturn(true);
        doReturn(true).when(mMockDevice).enableAdbRoot();

        // Call init(...).
        mListener.init(mMockContext, mFakeListener);

        // Verify.
        InOrder inOrder = Mockito.inOrder(mMockDevice);
        inOrder.verify(mMockDevice).isAdbRoot();
        inOrder.verify(mMockDevice).enableAdbRoot();
        inOrder.verify(mMockDevice, times(2)).executeShellCommand(anyString());
        inOrder.verify(mMockDevice).disableAdbRoot();
    }

    @Test
    public void testInitNoReset_noop() throws Exception {
        mCoverageOptionsSetter.setOptionValue("reset-coverage-before-test", "false");
        mCoverageOptionsSetter.setOptionValue("coverage-toolchain", "CLANG");

        // Call init(...).
        mListener.init(mMockContext, mFakeListener);

        // Verify nothing was executed on the device.
        verifyNoMoreInteractions(mMockDevice);
    }

    abstract static class CommandArgumentCaptor implements IRunUtil {
        private List<String> mCommand = new ArrayList<>();
        private CommandResult mResult = new CommandResult(CommandStatus.SUCCESS);

        /** Stores the command for retrieval later. */
        @Override
        public CommandResult runTimedCmd(long timeout, String... cmd) {
            mCommand = Arrays.asList(cmd);
            return mResult;
        }

        void setResult(CommandStatus status) {
            mResult = new CommandResult(status);
        }

        List<String> getCommand() {
            return mCommand;
        }

        /** Ignores sleep calls. */
        @Override
        public void sleep(long ms) {}
    }

    /** An {@link ITestInvocationListener} which reads test log data streams for verification. */
    private static class LogFileReader implements ITestInvocationListener {
        private List<ByteString> mLogs = new ArrayList<>();

        /** Reads the contents of the {@code dataStream} and saves it in the logs. */
        @Override
        public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
            try (InputStream input = dataStream.createInputStream()) {
                mLogs.add(ByteString.readFrom(input));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<ByteString> getLogs() {
            return new ArrayList<>(mLogs);
        }
    }

    private void returnFileContentsOnShellCommand(ITestDevice device, File file)
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
                        anyString(),
                        (File) any(),
                        (OutputStream) any(),
                        anyLong(),
                        any(),
                        anyInt());
    }

    /** Utility method to create .tar.gz files. */
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
            return TarUtil.gzip(tarFile);
        } finally {
            FileUtil.deleteFile(tarFile);
        }
    }

    private File createProfileToolZip() throws IOException {
        File profileToolZip = folder.newFile("llvm-profdata.zip");
        try (FileOutputStream stream = new FileOutputStream(profileToolZip);
                ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(stream))) {
            // Add bin/llvm-profdata.
            ZipEntry entry = new ZipEntry("bin/llvm-profdata");
            out.putNextEntry(entry);
            out.closeEntry();
        }
        return profileToolZip;
    }

    /** Utility function to verify that certain suffixes are contained in the List. */
    void checkListContainsSuffixes(List<String> list, List<String> suffixes) {
        for (String suffix : suffixes) {
            boolean found = false;
            for (String item : list) {
                if (item.endsWith(suffix)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                fail("List " + list.toString() + " does not contain suffix '" + suffix + "'");
            }
        }
    }

    void checkListDoesNotContainSuffix(List<String> list, String suffix) {
        for (String item : list) {
            if (item.endsWith(suffix)) {
                fail("List " + list.toString() + " should not contain suffix '" + suffix + "'");
            }
        }
    }
}
