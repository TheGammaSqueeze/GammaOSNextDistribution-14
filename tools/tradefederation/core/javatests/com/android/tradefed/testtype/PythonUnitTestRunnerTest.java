/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link PythonUnitTestRunner}. */
@RunWith(JUnit4.class)
public class PythonUnitTestRunnerTest {

    private static final String[] TEST_PASS_STDERR = {
        "b (a) ... ok", "", PythonUnitTestResultParser.DASH_LINE, "Ran 1 tests in 1s", "", "OK",
    };

    private static final String[] TEST_FAIL_STDERR = {
        "b (a) ... ERROR",
        "",
        PythonUnitTestResultParser.EQUAL_LINE,
        "ERROR: b (a)",
        PythonUnitTestResultParser.DASH_LINE,
        "Traceback (most recent call last):",
        "  File \"test_rangelib.py\", line 129, in test_reallyfail",
        "    raise ValueError()",
        "ValueError",
        "",
        PythonUnitTestResultParser.DASH_LINE,
        "Ran 1 tests in 1s",
        "",
        "FAILED (errors=1)",
    };

    private static final String[] TEST_EXECUTION_FAIL_STDERR = {
        "Traceback (most recent call last):",
        "  File \"/usr/lib/python2.7/runpy.py\", line 162, in _run_module_as_main",
        "    \"__main__\", fname, loader, pkg_name)",
        "  File \"/usr/lib/python2.7/runpy.py\", line 72, in _run_code",
        "    exec code in run_globals",
        "  File \"/usr/lib/python2.7/unittest/__main__.py\", line 12, in <module>",
        "    main(module=None)",
        "  File \"/usr/lib/python2.7/unittest/main.py\", line 94, in __init__",
        "    self.parseArgs(argv)",
        "  File \"/usr/lib/python2.7/unittest/main.py\", line 149, in parseArgs",
        "    self.createTests()",
        "  File \"/usr/lib/python2.7/unittest/main.py\", line 158, in createTests",
        "    self.module)",
        "  File \"/usr/lib/python2.7/unittest/loader.py\", line 130, in loadTestsFromNames",
        "    suites = [self.loadTestsFromName(name, module) for name in names]",
        "  File \"/usr/lib/python2.7/unittest/loader.py\", line 91, in loadTestsFromName",
        "    module = __import__('.'.join(parts_copy))",
        "ImportError: No module named stub",
    };

    private enum UnitTestResult {
        PASS,
        FAIL,
        EXECUTION_FAIL,
        TIMEOUT;

        private String getStderr() {
            switch (this) {
                case PASS:
                    return String.join("\n", TEST_PASS_STDERR);
                case FAIL:
                    return String.join("\n", TEST_FAIL_STDERR);
                case EXECUTION_FAIL:
                    return String.join("\n", TEST_EXECUTION_FAIL_STDERR);
                case TIMEOUT:
                    return null;
            }
            return null;
        }

        private CommandStatus getStatus() {
            switch (this) {
                case PASS:
                    return CommandStatus.SUCCESS;
                case FAIL:
                    return CommandStatus.FAILED;
                case EXECUTION_FAIL:
                    // UnitTest runner returns with exit code 1 if execution failed.
                    return CommandStatus.FAILED;
                case TIMEOUT:
                    return CommandStatus.TIMED_OUT;
            }
            return null;
        }

        public CommandResult getCommandResult() {
            CommandResult cr = new CommandResult();
            cr.setStderr(this.getStderr());
            cr.setStatus(this.getStatus());
            return cr;
        }
    }

    private PythonUnitTestRunner mRunner;
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mRunner = new PythonUnitTestRunner();
    }

    @Test
    public void testCheckPythonVersion_276given270min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 2.7.6");
        mRunner.checkPythonVersion(c);
    }

    @Test
    public void testCheckPythonVersion_276given331min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 2.7.6");
        mRunner.setMinPythonVersion("3.3.1");
        try {
            mRunner.checkPythonVersion(c);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    @Test
    public void testCheckPythonVersion_300given276min() {
        CommandResult c = new CommandResult();
        c.setStderr("Python 3.0.0");
        mRunner.setMinPythonVersion("2.7.6");
        mRunner.checkPythonVersion(c);
    }

    private IRunUtil getMockRunUtil(UnitTestResult testResult) {
        CommandResult expectedResult = testResult.getCommandResult();
        IRunUtil mockRunUtil = mock(IRunUtil.class);
        // EasyMock checks the number of arguments when verifying method call.
        // The actual runTimedCmd() expected here looks like:
        // runTimedCmd(300000, null, "-m", "unittest", "-v", "")
        when(mockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(expectedResult);
        return mockRunUtil;
    }

    private void verifyMockRunUtil(IRunUtil mockRunUtil) {
        verify(mockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any());
    }

    private void verifyMockListenerExpectTestPass(boolean testPass) {
        verify(mMockListener, times(1)).testRunStarted((String) Mockito.any(), Mockito.anyInt());
        verify(mMockListener, times(1)).testStarted((TestDescription) Mockito.any());

        if (!testPass) {
            verify(mMockListener, times(1))
                    .testFailed((TestDescription) Mockito.any(), (String) Mockito.any());
        }

        verify(mMockListener, times(1))
                .testEnded(
                        (TestDescription) Mockito.any(), (HashMap<String, Metric>) Mockito.any());
        verify(mMockListener, times(1))
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /** Test execution succeeds and all test cases pass. */
    @Test
    public void testRunPass() {
        IRunUtil mockRunUtil = getMockRunUtil(UnitTestResult.PASS);

        mRunner.doRunTest(mMockListener, mockRunUtil, "");
        verifyMockListenerExpectTestPass(true);
        verifyMockRunUtil(mockRunUtil);
    }

    /** Test execution succeeds and some test cases fail. */
    @Test
    public void testRunFail() {
        IRunUtil mockRunUtil = getMockRunUtil(UnitTestResult.FAIL);

        mRunner.doRunTest(mMockListener, mockRunUtil, "");

        verifyMockListenerExpectTestPass(false);
        verifyMockRunUtil(mockRunUtil);
    }

    /** Test execution fails. */
    @Test
    public void testRunExecutionFail() {
        IRunUtil mockRunUtil = getMockRunUtil(UnitTestResult.EXECUTION_FAIL);

        try {
            mRunner.doRunTest(mMockListener, mockRunUtil, "");
            fail("Should not reach here.");
        } catch (RuntimeException e) {
            verifyMockRunUtil(mockRunUtil);
            assertEquals("Test execution failed", e.getMessage());
        }
    }

    /** Test execution times out. */
    @Test
    public void testRunTimeout() {
        IRunUtil mockRunUtil = getMockRunUtil(UnitTestResult.TIMEOUT);

        try {
            mRunner.doRunTest(mMockListener, mockRunUtil, "");
            fail("Should not reach here.");
        } catch (RuntimeException e) {
            verifyMockRunUtil(mockRunUtil);
            assertTrue(e.getMessage().startsWith("Python unit test timed out after"));
        }
    }
}
