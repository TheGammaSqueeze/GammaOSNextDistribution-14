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

package com.android.tradefed.result;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import com.google.common.jimfs.Jimfs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@RunWith(JUnit4.class)
public final class BazelExitCodeResultReporterTest {

    private static final IInvocationContext DEFAULT_CONTEXT = createContext();
    private static final TestDescription TEST_ID = new TestDescription("FooTest", "testFoo");
    private static final String STACK_TRACE = "this is a trace";

    private final FileSystem mFileSystem = Jimfs.newFileSystem();
    private final HashMap<String, Metric> mEmptyMap = new HashMap<>();

    @Test
    public void writeNoTestsFoundExitCode_noTestsRun() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.invocationEnded(1);

        assertFileContentsEquals("0", exitCodeFile);
    }

    @Test
    public void writeRunFailureExitCode_runFailed() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID);
        reporter.testRunFailed("Error Message");
        reporter.invocationEnded(1);

        assertFileContentsEquals("6", exitCodeFile);
    }

    @Test
    public void writeSuccessExitCode_allTestsPassed() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID);
        reporter.testEnded(TEST_ID, mEmptyMap);
        reporter.testRunEnded(3, mEmptyMap);
        reporter.invocationEnded(1);

        assertFileContentsEquals("0", exitCodeFile);
    }

    @Test
    public void writeTestsFailedExitCode_oneTestFailed() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 1);
        reporter.testStarted(TEST_ID);
        reporter.testFailed(TEST_ID, "this is a trace");
        reporter.testEnded(TEST_ID, mEmptyMap);
        reporter.testRunEnded(3, mEmptyMap);
        reporter.invocationEnded(1);

        assertFileContentsEquals("3", exitCodeFile);
    }

    @Test
    public void writeRunFailureExitCode_bothRunFailedAndTestFailed() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 2);
        // First test failed.
        reporter.testStarted(TEST_ID);
        reporter.testFailed(TEST_ID, STACK_TRACE);
        reporter.testEnded(TEST_ID, mEmptyMap);
        // Second test has run failure.
        reporter.testStarted(TEST_ID);
        reporter.testRunFailed("Error Message");
        reporter.testEnded(TEST_ID, mEmptyMap);
        reporter.testRunEnded(3, mEmptyMap);
        reporter.invocationEnded(1);

        // Test Exit Code is RunFailure even when test failure happens before run failure.
        assertFileContentsEquals("6", exitCodeFile);
    }

    @Test
    public void writeRunFailureExitCode_noTestsAndRunFailed() throws Exception {
        Path exitCodeFile = createExitCodeFilePath();
        BazelExitCodeResultReporter reporter = createReporter(exitCodeFile);

        reporter.invocationStarted(DEFAULT_CONTEXT);
        reporter.testRunStarted("run", 0);
        reporter.testRunFailed("Error Message");
        reporter.invocationEnded(1);

        assertFileContentsEquals("6", exitCodeFile);
    }

    private static IInvocationContext createContext() {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", new BuildInfo("1", "test"));
        context.setTestTag("test");
        return context;
    }

    private static void assertFileContentsEquals(String expected, Path filePath)
            throws IOException {
        assertEquals(expected, Files.readAllLines(filePath).get(0));
    }

    private Path createExitCodeFilePath() {
        return mFileSystem.getPath("/tmp/test_exit_code.txt");
    }

    private BazelExitCodeResultReporter createReporter(Path path) throws Exception {
        BazelExitCodeResultReporter reporter = new BazelExitCodeResultReporter(mFileSystem);
        OptionSetter setter = new OptionSetter(reporter);
        setter.setOptionValue("file", path.toString());
        return reporter;
    }
}
