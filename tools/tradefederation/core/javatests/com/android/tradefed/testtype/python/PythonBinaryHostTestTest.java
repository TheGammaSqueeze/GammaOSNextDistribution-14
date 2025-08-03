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
package com.android.tradefed.testtype.python;

import static com.android.tradefed.testtype.python.PythonBinaryHostTest.TEST_OUTPUT_FILE_FLAG;
import static com.android.tradefed.testtype.python.PythonBinaryHostTest.USE_TEST_OUTPUT_FILE_OPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.StreamUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/** Unit tests for {@link PythonBinaryHostTest}. */
@RunWith(JUnit4.class)
public final class PythonBinaryHostTestTest {

    private static final String PYTHON_OUTPUT_FILE_1 = "python_output1.txt";

    private PythonBinaryHostTest mTest;
    @Mock IRunUtil mMockRunUtil;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo;
    @Mock ITestInvocationListener mMockListener;
    private File mFakeAdb;
    private File mPythonBinary;
    private File mOutputFile;
    private File mModuleDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeAdb = FileUtil.createTempFile("adb-python-tests", "");

        mTest =
                new PythonBinaryHostTest() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    String getAdbPath() {
                        return mFakeAdb.getAbsolutePath();
                    }
                };
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        mMockRunUtil.setEnvVariable(PythonBinaryHostTest.ANDROID_SERIAL_VAR, "SERIAL");
        mMockRunUtil.setWorkingDir(Mockito.any());
        mMockRunUtil.setEnvVariablePriority(EnvPriority.SET);
        mMockRunUtil.setEnvVariable(Mockito.eq("PATH"), Mockito.any());

        mModuleDir = FileUtil.createTempDir("python-module");
        mPythonBinary = FileUtil.createTempFile("python-dir", "", mModuleDir);
        mTestInfo.executionFiles().put(FilesKey.HOST_TESTS_DIRECTORY, new File("/path-not-exist"));
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mFakeAdb);
        FileUtil.deleteFile(mPythonBinary);
        FileUtil.deleteFile(mOutputFile);
        FileUtil.recursiveDelete(mModuleDir);
    }

    /** Test that when running a python binary the output is parsed to obtain results. */
    @Test
    public void testRun() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStdout("python binary stdout.");
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyFileToStream(mOutputFile, stream);
                                return res;
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(mPythonBinary.getName()),
                            Mockito.eq(11),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stdout"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /** Test that when running a non-unittest python binary with any filter, the test shall fail. */
    @Test
    public void testRun_failWithIncludeFilters() throws Exception {
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        mMockListener = mock(ITestInvocationListener.class);
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());
            mTest.addIncludeFilter("test1");

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                throw new RuntimeException("Parser error");
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener)
                    .testRunStarted(Mockito.eq(mPythonBinary.getName()), Mockito.eq(0));
            verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /**
     * Test that when running a python binary with include filters, the output is parsed to obtain
     * results.
     */
    @Test
    public void testRun_withIncludeFilters() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());
            mTest.addIncludeFilter("__main__.Class1#test_1");

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            String output =
                    "test_1 (__main__.Class1)\n"
                        + "run first test. ... ok\n"
                        + "test_2 (__main__.Class1)\n"
                        + "run second test. ... ok\n"
                        + "test_3 (__main__.Class1)\n"
                        + "run third test. ... ok\n"
                        + "----------------------------------------------------------------------\n"
                        + "Ran 3 tests in 1s\n";
            res.setStderr(output);
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyStreams(
                                        new ByteArrayInputStream(output.getBytes()), stream);
                                return res;
                            });
            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener, times(3)).testStarted(Mockito.any(), Mockito.anyLong());
            verify(mMockListener, times(3))
                    .testEnded(
                            Mockito.<TestDescription>any(),
                            Mockito.anyLong(),
                            Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener, times(2)).testIgnored(Mockito.<TestDescription>any());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(mPythonBinary.getName()),
                            Mockito.eq(3),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /**
     * Test that when running a python binary with exclude filters, the output is parsed to obtain
     * results.
     */
    @Test
    public void testRun_withExcludeFilters() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());
            mTest.addExcludeFilter("__main__.Class1#test_1");

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            String output =
                    "test_1 (__main__.Class1)\n"
                        + "run first test. ... ok\n"
                        + "test_2 (__main__.Class1)\n"
                        + "run second test. ... ok\n"
                        + "test_3 (__main__.Class1)\n"
                        + "run third test. ... ok\n"
                        + "----------------------------------------------------------------------\n"
                        + "Ran 3 tests in 1s\n";
            res.setStderr(output);
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyStreams(
                                        new ByteArrayInputStream(output.getBytes()), stream);
                                return res;
                            });
            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener, times(3)).testStarted(Mockito.any(), Mockito.anyLong());
            verify(mMockListener, times(3))
                    .testEnded(
                            Mockito.<TestDescription>any(),
                            Mockito.anyLong(),
                            Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener).testIgnored(Mockito.<TestDescription>any());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(mPythonBinary.getName()),
                            Mockito.eq(3),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /**
     * Test running the python tests when an adb path has been set. In that case we ensure the
     * python script will use the provided adb.
     */
    @Test
    public void testRun_withAdbPath() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        mTestInfo.executionFiles().put(FilesKey.ADB_BINARY, new File("/test/adb"));

        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyFileToStream(mOutputFile, stream);
                                return res;
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable(
                            "PATH", String.format("%s:bin/", new File("/test/adb").getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(mPythonBinary.getName()),
                            Mockito.eq(11),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /** Test running the python tests when shared lib is available in HOST_TESTS_DIRECTORY. */
    @Test
    public void testRun_withSharedLibInHostTestsDir() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        File hostTestsDir = FileUtil.createTempDir("host-test-cases");
        mTestInfo.executionFiles().put(FilesKey.HOST_TESTS_DIRECTORY, hostTestsDir);
        File binary = FileUtil.createTempFile("python-dir", "", hostTestsDir);
        File lib = new File(hostTestsDir, "lib");
        lib.mkdirs();
        File lib64 = new File(hostTestsDir, "lib64");
        lib64.mkdirs();

        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", binary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(binary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyFileToStream(mOutputFile, stream);
                                return res;
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil)
                    .setEnvVariable(
                            PythonBinaryHostTest.LD_LIBRARY_PATH,
                            lib.getAbsolutePath()
                                    + ":"
                                    + lib64.getAbsolutePath()
                                    + ":"
                                    + hostTestsDir.getAbsolutePath());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(11),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(binary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.recursiveDelete(hostTestsDir);
        }
    }

    /** Test running the python tests when shared lib is available in TESTS_DIRECTORY. */
    @Test
    public void testRun_withSharedLib() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        File testsDir = FileUtil.createTempDir("host-test-cases");
        mTestInfo.executionFiles().put(FilesKey.TESTS_DIRECTORY, testsDir);
        File binary = FileUtil.createTempFile("python-dir", "", testsDir);
        File lib = new File(testsDir, "lib");
        lib.mkdirs();
        File lib64 = new File(testsDir, "lib64");
        lib64.mkdirs();

        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", binary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(binary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyFileToStream(mOutputFile, stream);
                                return res;
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil)
                    .setEnvVariable(
                            PythonBinaryHostTest.LD_LIBRARY_PATH,
                            lib.getAbsolutePath()
                                    + ":"
                                    + lib64.getAbsolutePath()
                                    + ":"
                                    + testsDir.getAbsolutePath());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(binary.getName()),
                            Mockito.eq(11),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(binary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.recursiveDelete(testsDir);
        }
    }

    /**
     * If the binary returns an exception status, we should throw a runtime exception since
     * something went wrong with the binary setup.
     */
    @Test
    public void testRunFail_exception() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.EXCEPTION);
            res.setStderr("Could not execute.");
            String output = "Could not execute.";
            res.setStderr(output);
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyStreams(
                                        new ByteArrayInputStream(output.getBytes()), stream);
                                return res;
                            });
            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            // Report a failure if we cannot parse the logs

            FailureDescription failure =
                    FailureDescription.create(
                            "Failed to parse the python logs: Parser finished in unexpected "
                                    + "state TEST_CASE. Please ensure that verbosity of output "
                                    + "is high enough to be parsed. Stderr: Could not execute.");
            failure.setFailureStatus(FailureStatus.TEST_FAILURE);

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
            verify(mMockListener).testRunStarted(mPythonBinary.getName(), 0);
            verify(mMockListener).testRunFailed(failure);
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    /**
     * If the binary reports a FAILED status but the output actually have some tests, it most *
     * likely means that some tests failed. So we simply continue with parsing the results.
     */
    @Test
    public void testRunFail_failureOnly() throws Exception {
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());

            expectedAdbPath();

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.FAILED);
            res.setStderr(FileUtil.readStringFromFile(mOutputFile));
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(),
                            Mockito.isNull(),
                            (OutputStream) Mockito.any(),
                            Mockito.eq(mPythonBinary.getAbsolutePath())))
                    .thenAnswer(
                            invocation -> {
                                OutputStream stream = (OutputStream) invocation.getArguments()[2];
                                StreamUtil.copyFileToStream(mOutputFile, stream);
                                return res;
                            });

            when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

            mTest.run(mTestInfo, mMockListener);

            verify(mMockRunUtil)
                    .setEnvVariable("PATH", String.format("%s:bin/", mFakeAdb.getParent()));
            verify(mMockRunUtil).setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
            verify(mMockListener)
                    .testRunStarted(
                            Mockito.eq(mPythonBinary.getName()),
                            Mockito.eq(11),
                            Mockito.eq(0),
                            Mockito.anyLong());
            verify(mMockListener)
                    .testLog(
                            Mockito.eq(mPythonBinary.getName() + "-stderr"),
                            Mockito.eq(LogDataType.TEXT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(mPythonBinary);
        }
    }

    @Test
    public void testRun_useTestOutputFileOptionSet_parsesSubprocessOutputFile() throws Exception {
        mMockRunUtil.setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
        mMockListener = mock(ITestInvocationListener.class);
        mOutputFile = readInFile(PYTHON_OUTPUT_FILE_1);
        newDefaultOptionSetter(mTest).setOptionValue(USE_TEST_OUTPUT_FILE_OPTION, "true");
        expectRunThatWritesTestOutputFile(
                newCommandResult(CommandStatus.SUCCESS, "NOT TEST OUTPUT"),
                FileUtil.readStringFromFile(mOutputFile));
        mTest.run(mTestInfo, mMockListener);

        verify(mMockListener)
                .testRunStarted(Mockito.any(), Mockito.eq(11), Mockito.eq(0), anyLong());
    }

    @Test
    public void testRun_useTestOutputFileOptionSet_parsesUnitTestOutputFile() throws Exception {
        mMockRunUtil.setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
        mMockListener = mock(ITestInvocationListener.class);
        newDefaultOptionSetter(mTest).setOptionValue(USE_TEST_OUTPUT_FILE_OPTION, "true");
        expectRunThatWritesTestOutputFile(
                newCommandResult(CommandStatus.SUCCESS, "NOT TEST OUTPUT"),
                "test_1 (__main__.Class1)\n"
                        + "run first test. ... ok\n"
                        + "test_2 (__main__.Class1)\n"
                        + "run second test. ... ok\n"
                        + "----------------------------------------------------------------------\n"
                        + "Ran 2 tests in 1s");

        mTest.run(mTestInfo, mMockListener);

        verify(mMockListener)
                .testRunStarted(Mockito.any(), Mockito.eq(2), Mockito.eq(0), anyLong());
    }

    @Test
    public void testRun_useTestOutputFileOptionSet_logsErrorOutput() throws Exception {
        mMockRunUtil.setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
        mMockListener = mock(ITestInvocationListener.class);
        String errorOutput = "NOT TEST OUTPUT";
        newDefaultOptionSetter(mTest).setOptionValue(USE_TEST_OUTPUT_FILE_OPTION, "true");
        expectRunThatWritesTestOutputFile(
                newCommandResult(CommandStatus.SUCCESS, errorOutput),
                "TEST_RUN_STARTED {\"testCount\": 5, \"runName\": \"TestSuite\"}");

        mTest.run(mTestInfo, mMockListener);

        verify(mMockListener, times(2))
                .testLog(Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
    }

    @Test
    public void testRun_useTestOutputFileOptionSet_logsTestOutput() throws Exception {
        mMockRunUtil.setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
        mMockListener = mock(ITestInvocationListener.class);
        String testOutput = "TEST_RUN_STARTED {\"testCount\": 5, \"runName\": \"TestSuite\"}";
        newDefaultOptionSetter(mTest).setOptionValue(USE_TEST_OUTPUT_FILE_OPTION, "true");
        expectRunThatWritesTestOutputFile(
                newCommandResult(CommandStatus.SUCCESS, "NOT TEST OUTPUT"), testOutput);

        mTest.run(mTestInfo, mMockListener);

        verify(mMockListener)
                .testLog(
                        Mockito.eq(mPythonBinary.getName() + "-stderr"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any());
        verify(mMockListener, times(2))
                .testLog(Mockito.any(), Mockito.eq(LogDataType.TEXT), Mockito.any());
    }

    @Test
    public void testRun_useTestOutputFileOptionSet_failureMessageContainsHints() throws Exception {
        mMockRunUtil.setEnvVariable(Mockito.eq("LD_LIBRARY_PATH"), Mockito.any());
        mMockListener = mock(ITestInvocationListener.class);
        newDefaultOptionSetter(mTest).setOptionValue(USE_TEST_OUTPUT_FILE_OPTION, "true");
        expectRunThatWritesTestOutputFile(
                newCommandResult(CommandStatus.SUCCESS, "NOT TEST OUTPUT"), "BAD OUTPUT FORMAT");

        mTest.run(mTestInfo, mMockListener);

        ArgumentCaptor<FailureDescription> description =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(description.capture());
        String message = description.getValue().getErrorMessage();
        assertThat(message).contains("--" + TEST_OUTPUT_FILE_FLAG);
        assertThat(message).contains("verbosity");
    }

    private OptionSetter newDefaultOptionSetter(PythonBinaryHostTest test) throws Exception {
        OptionSetter setter = new OptionSetter(test);
        setter.setOptionValue("python-binaries", mPythonBinary.getAbsolutePath());
        return setter;
    }

    private static CommandResult newCommandResult(CommandStatus status, String stderr) {
        CommandResult res = new CommandResult();
        res.setStatus(status);
        res.setStderr(stderr);
        return res;
    }

    private void expectRunThatWritesTestOutputFile(
            CommandResult result, String testOutputFileContents) {
        ArgumentCaptor<String> testOutputFilePath = ArgumentCaptor.forClass(String.class);

        when(mMockRunUtil.runTimedCmd(
                        anyLong(),
                        Mockito.eq(mPythonBinary.getAbsolutePath()),
                        Mockito.eq("--test-output-file"),
                        testOutputFilePath.capture()))
                .thenAnswer(
                        invocation -> {
                            try {
                                FileUtil.writeToFile(
                                        testOutputFileContents,
                                        new File(testOutputFilePath.getValue()));
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            return result;
                        });
        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        expectedAdbPath();
    }

    private void expectedAdbPath() {
        CommandResult pathRes = new CommandResult();
        pathRes.setStatus(CommandStatus.SUCCESS);
        pathRes.setStdout("bin/");
        when(mMockRunUtil.runTimedCmd(60000L, "/bin/bash", "-c", "echo $PATH")).thenReturn(pathRes);

        CommandResult versionRes = new CommandResult();
        versionRes.setStatus(CommandStatus.SUCCESS);
        versionRes.setStdout("bin/");
        when(mMockRunUtil.runTimedCmd(60000L, "adb", "version")).thenReturn(versionRes);
    }

    private File readInFile(String filename) throws IOException {
        File output = FileUtil.createTempFile("python-host-test", ".txt");
        InputStream stream =
                getClass()
                        .getResourceAsStream(
                                File.separator + "testtype" + File.separator + filename);
        FileUtil.writeToFile(stream, output);
        return output;
    }
}
