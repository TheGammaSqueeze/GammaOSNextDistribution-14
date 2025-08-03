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
package com.android.tradefed.testtype.rust;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link RustBinaryHostTest}. */
@RunWith(JUnit4.class)
public class RustBinaryHostTestTest {
    private RustBinaryHostTest mTest;
    private TestInformation mTestInfo;
    @Mock IRunUtil mMockRunUtil;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTest =
                new RustBinaryHostTest() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mTest.setBuild(mMockBuildInfo);
        InvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    private CommandResult newCommandResult(CommandStatus status, String stderr, String stdout) {
        CommandResult res = new CommandResult();
        res.setStatus(status);
        res.setStderr(stderr);
        res.setStdout(stdout);
        return res;
    }

    private String resultCount(int pass, int fail, int ignore) {
        return "running 2 tests\ntest result: ok. "
                + pass
                + " passed; "
                + fail
                + " failed; "
                + ignore
                + " ignored;";
    }

    private CommandResult successResult(String stderr, String stdout) throws Exception {
        return newCommandResult(CommandStatus.SUCCESS, stderr, stdout);
    }

    // shared with RustBinaryTestTest
    static String runListOutput(int numTests) {
        String listOutput = "";
        for (int i = 1; i <= numTests; i++) {
            listOutput += "test_case_" + i + ": test\n";
        }
        return listOutput + numTests + " tests, 0 benchmarks";
    }

    // shared with RustBinaryTestTest
    static String runListOutput(String[] tests) {
        String listOutput = "";
        for (String name : tests) {
            listOutput += name + ": test\n";
        }
        return listOutput + tests.length + " tests, 0 benchmarks";
    }

    // shared with RustBinaryTestTest
    static String runListBenchmarksOutput(int numTests) {
        String listOutput = "";
        for (int i = 1; i <= numTests; i++) {
            listOutput += "test_case_" + i + ": bench\n";
        }
        return listOutput;
    }

    /** Add mocked call "binary --list" to count the number of tests. */
    private void mockCountTests(File binary, int numOfTest) throws Exception {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(binary.getAbsolutePath()),
                        Mockito.eq("-Zunstable-options"),
                        Mockito.eq("--report-time"),
                        Mockito.eq("--exact"),
                        Mockito.eq("--list")))
                .thenReturn(successResult("", runListOutput(numOfTest)));
    }

    private void mockCountBenchmarks(File binary, int numOfTest) throws Exception {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(binary.getAbsolutePath()),
                        Mockito.eq("--bench"),
                        Mockito.eq("--color"),
                        Mockito.eq("never"),
                        Mockito.eq("--list")))
                .thenReturn(successResult("", runListBenchmarksOutput(numOfTest)));
    }

    /** Add mocked testRunStarted call to the listener. */
    private void mockListenerStarted(File binary, int count) throws Exception {
        mMockListener.testRunStarted(
                Mockito.eq(binary.getName()),
                Mockito.eq(count),
                Mockito.anyInt(),
                Mockito.anyLong());
    }

    /** Add mocked call to check listener log file. */
    private void verifyListenerLog(File binary, boolean error) {
        if (error) {
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(binary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        }
        verify(mMockListener)
                .testLog(
                        Mockito.eq(binary.getName() + "-stdout"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any());
    }

    private void mockTestRunExpect(File binary, CommandResult res) throws Exception {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(binary.getAbsolutePath()),
                        Mockito.eq("-Zunstable-options"),
                        Mockito.eq("--report-time"),
                        Mockito.eq("--exact")))
                .thenReturn(res);
    }

    private void mockBenchmarkRunExpect(File binary, String output) throws Exception {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(binary.getAbsolutePath()),
                        Mockito.eq("--bench"),
                        Mockito.eq("--color"),
                        Mockito.eq("never")))
                .thenReturn(successResult("", output));
    }

    /** Test that when running a rust binary the output is parsed to obtain results. */
    @Test
    public void testRun() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            mockCountTests(binary, 9);
            mockListenerStarted(binary, 9);
            CommandResult res = successResult("", resultCount(6, 1, 2));
            mockTestRunExpect(binary, res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(9),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /**
     * Test running the rust tests when an adb path has been set. In that case we ensure the rust
     * test will use the provided adb.
     */
    @Test
    public void testRun_withAdbPath() throws Exception {
        mMockBuildInfo = mock(IBuildInfo.class);
        mTest.setBuild(mMockBuildInfo);

        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            mockCountTests(binary, 9);
            CommandResult res = successResult("", resultCount(6, 1, 2));
            mockTestRunExpect(binary, res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(9),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** If the binary returns an exception status, it is treated as a failed test. */
    @Test
    public void testRunFail_exception() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            mockCountTests(binary, 2);
            CommandResult res =
                    newCommandResult(
                            CommandStatus.EXCEPTION, "Err.", "running 2 tests\nException.");
            mockTestRunExpect(binary, res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(2),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, true);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /**
     * If the binary reports a FAILED status when trying to count tests, it is treated as a failed
     * test.
     */
    @Test
    public void testRunFail_list() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            CommandResult listRes = newCommandResult(CommandStatus.FAILED, "", "");
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--list")))
                    .thenReturn(listRes);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener).testRunStarted(Mockito.eq(binary.getName()), Mockito.eq(0));
            verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** If the binary reports a FAILED status, it is treated as a failed test. */
    @Test
    public void testRunFail_failureOnly() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            mockCountTests(binary, 9);
            CommandResult res = newCommandResult(CommandStatus.FAILED, "", resultCount(6, 1, 2));
            mockTestRunExpect(binary, res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(9),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** Test the exclude filtering of test methods. */
    @Test
    public void testExcludeFilter() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("exclude-filter", "NotMe");
            setter.setOptionValue("exclude-filter", "Long");
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Long"),
                            Mockito.eq("--list")))
                    .thenReturn(successResult("", runListOutput(9)));

            CommandResult res = successResult("", resultCount(6, 1, 2));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Long")))
                    .thenReturn(res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(9),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** Test both include and exclude filters. */
    @Test
    public void testIncludeExcludeFilter() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("exclude-filter", "MyTest#NotMe");
            setter.setOptionValue("include-filter", "MyTest#OnlyMe");
            setter.setOptionValue("exclude-filter", "Other");
            // We always pass the include-filter before exclude-filter strings.
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("OnlyMe"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other"),
                            Mockito.eq("--list")))
                    .thenReturn(successResult("", runListOutput(3)));

            CommandResult res = successResult("", resultCount(3, 0, 0));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("OnlyMe"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other")))
                    .thenReturn(res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(3),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** Test multiple include and exclude filters. */
    @Test
    public void testMultipleIncludeExcludeFilter() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("exclude-filter", "NotMe");
            setter.setOptionValue("include-filter", "MyTest#OnlyMe");
            setter.setOptionValue("exclude-filter", "MyTest#Other");
            setter.setOptionValue("include-filter", "Me2");
            // Multiple include filters are run one by one with --list.
            String[] selection1 = new String[] {"test1", "test2"};
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("OnlyMe"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other"),
                            Mockito.eq("--list")))
                    .thenReturn(successResult("", runListOutput(selection1)));
            String[] selection2 = new String[] {"test2", "test3", "test4"};
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("Me2"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other"),
                            Mockito.eq("--list")))
                    .thenReturn(successResult("", runListOutput(selection2)));

            CommandResult res = successResult("", resultCount(2, 0, 0));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("OnlyMe"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other")))
                    .thenReturn(res);

            res = successResult("", resultCount(3, 0, 0));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.eq(binary.getAbsolutePath()),
                            Mockito.eq("-Zunstable-options"),
                            Mockito.eq("--report-time"),
                            Mockito.eq("Me2"),
                            Mockito.eq("--exact"),
                            Mockito.eq("--skip"),
                            Mockito.eq("NotMe"),
                            Mockito.eq("--skip"),
                            Mockito.eq("Other")))
                    .thenReturn(res);

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(4),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verify(mMockListener, times(2))
                    .testLog(
                            Mockito.eq(binary.getName() + "-stdout"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
            verify(mMockListener, times(2))
                    .testRunFailed("Test run incomplete. Started 2 tests, finished 0");
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /** Test benchmark run */
    @Test
    public void testRun_benchmark() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("is-benchmark", "true");
            mockCountBenchmarks(binary, 2);
            mockBenchmarkRunExpect(
                    binary,
                    "Benchmarking test1\n"
                            + "test                   time:   [0.1 ms 0.1 ms 0.1 ms]\n"
                            + "Benchmarking test2\n"
                            + "test                   time:   [0.1 ms 0.1 ms 0.1 ms]\n");

            TestDescription desc1 = new TestDescription(binary.getName(), "test1");
            TestDescription desc2 = new TestDescription(binary.getName(), "test2");

            mTest.run(mTestInfo, mMockListener);

            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(2),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testStarted(desc1);
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc1), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener).testStarted(desc2);
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc2), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    @Test
    public void testRun_benchmarkDoubleStart() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("is-benchmark", "true");
            mockCountBenchmarks(binary, 2);
            mockBenchmarkRunExpect(
                    binary,
                    "Benchmarking test1\n"
                            + "Benchmarking test2\n"
                            + "test                   time:   [0.1 ms 0.1 ms 0.1 ms]\n");

            TestDescription desc1 = new TestDescription(binary.getName(), "test1");
            TestDescription desc2 = new TestDescription(binary.getName(), "test2");

            mTest.run(mTestInfo, mMockListener);
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(2),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testStarted(desc1);
            verify(mMockListener).testFailed(Mockito.eq(desc1), Mockito.<String>any());
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc1), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener).testStarted(desc2);
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc2), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    @Test
    public void testRun_benchmarkNotFinished() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());
            setter.setOptionValue("is-benchmark", "true");
            mockCountBenchmarks(binary, 2);
            mockBenchmarkRunExpect(
                    binary,
                    "Benchmarking test1\n"
                            + "test                   time:   [0.1 ms 0.1 ms 0.1 ms]\n"
                            + "Benchmarking test2\n");

            TestDescription desc1 = new TestDescription(binary.getName(), "test1");
            TestDescription desc2 = new TestDescription(binary.getName(), "test2");

            mTest.run(mTestInfo, mMockListener);

            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(2),
                            Mockito.anyInt(),
                            Mockito.anyLong());
            verifyListenerLog(binary, false);
            verify(mMockListener).testStarted(desc1);
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc1), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener).testStarted(desc2);
            verify(mMockListener).testFailed(Mockito.eq(desc2), Mockito.<String>any());
            verify(mMockListener)
                    .testEnded(Mockito.eq(desc2), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener).testRunFailed(Mockito.<String>any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    @Test
    public void testFindBinaryWithBitness() throws Exception {
        String moduleName = "testModuleName";
        File testsDir = FileUtil.createTempDir("rust-test");
        try {
            File moduleDir = new File(testsDir, moduleName);
            moduleDir.mkdirs();
            File subDir = new File(moduleDir, "x86_64");
            subDir.mkdirs();
            File testModuleBinary64 = new File(subDir, moduleName + "64");
            testModuleBinary64.createNewFile();
            when(mMockBuildInfo.getFile(BuildInfoFileKey.HOST_LINKED_DIR)).thenReturn(testsDir);
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", moduleName);
            IAbi abi = new Abi("arm64-v8a", "64");
            mTest.setAbi(abi);

            List<File> files = mTest.findFiles();
            Truth.assertThat(files.size()).isEqualTo(1);
            for (File f : files) {
                assertFalse(f.isDirectory());
            }
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }
}
