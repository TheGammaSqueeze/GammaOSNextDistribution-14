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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.ArrayUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Vector;

/** Unit tests for {@link PythonUnitTestResultParser}. */
@RunWith(JUnit4.class)
public class PythonUnitTestResultParserTest {

    public static final String PYTHON_OUTPUT_FILE_1 = "python_output1.txt";
    public static final String PYTHON_OUTPUT_FILE_2 = "python_output2.txt";
    public static final String PYTHON_OUTPUT_FILE_3 = "python_output3.txt";
    public static final String PYTHON_OUTPUT_FILE_4 = "python_output4.txt";

    private PythonUnitTestResultParser mParser;
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mParser = new PythonUnitTestResultParser(ArrayUtil.list(mMockListener), "test");
    }

    @Test
    public void testRegexTestCase() {
        String s = "a (b) ... ok";
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        assertFalse(PythonUnitTestResultParser.PATTERN_TWO_LINE_RESULT_FIRST.matcher(s).matches());
        s = "a (b) ... FAIL";
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        s = "a (b) ... ERROR";
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        s = "a (b) ... expected failure";
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        s = "a (b) ... skipped 'reason foo'";
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        s = "a (b)";
        assertFalse(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
        assertTrue(PythonUnitTestResultParser.PATTERN_TWO_LINE_RESULT_FIRST.matcher(s).matches());
        s = "doc string foo bar ... ok";
        assertTrue(PythonUnitTestResultParser.PATTERN_TWO_LINE_RESULT_SECOND.matcher(s).matches());
        s = "docstringfoobar ... ok";
        assertTrue(PythonUnitTestResultParser.PATTERN_TWO_LINE_RESULT_SECOND.matcher(s).matches());

        s = "a (b) ... "; // Tests with failed subtest assertions could be missing the status.
        assertTrue(PythonUnitTestResultParser.PATTERN_ONE_LINE_RESULT.matcher(s).matches());
    }

    @Test
    public void testRegexFailMessage() {
        String s = "FAIL: a (b)";
        assertTrue(PythonUnitTestResultParser.PATTERN_FAIL_MESSAGE.matcher(s).matches());
        s = "ERROR: a (b)";
        assertTrue(PythonUnitTestResultParser.PATTERN_FAIL_MESSAGE.matcher(s).matches());
        s = "FAIL: a (b) (<subtest>)";
        assertTrue(PythonUnitTestResultParser.PATTERN_FAIL_MESSAGE.matcher(s).matches());
        s = "FAIL: a (b) (i=3)";
        assertTrue(PythonUnitTestResultParser.PATTERN_FAIL_MESSAGE.matcher(s).matches());
    }

    @Test
    public void testRegexRunSummary() {
        String s = "Ran 1 test in 1s";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_SUMMARY.matcher(s).matches());
        s = "Ran 42 tests in 1s";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_SUMMARY.matcher(s).matches());
        s = "Ran 1 tests in 0.000s";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_SUMMARY.matcher(s).matches());
        s = "Ran 1 test in 0.001s";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_SUMMARY.matcher(s).matches());
        s = "Ran 1 test in 12345s";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_SUMMARY.matcher(s).matches());
    }

    @Test
    public void testRegexRunResult() {
        String s = "OK";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_RESULT.matcher(s).matches());
        s = "OK (expected failures=2) ";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_RESULT.matcher(s).matches());
        s = "FAILED (errors=1)";
        assertTrue(PythonUnitTestResultParser.PATTERN_RUN_RESULT.matcher(s).matches());
    }

    @Test
    public void testParseNoTests() throws Exception {
        String[] output = {
            "", PythonUnitTestResultParser.DASH_LINE, "Ran 0 tests in 0.000s", "", "OK"
        };

        mParser.processNewLines(output);

        verify(mMockListener, times(1)).testRunStarted("test", 0);
        verify(mMockListener, times(1)).testRunEnded(0L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestPass() throws Exception {
        String[] output = {
            "b (a) ... ok", "", PythonUnitTestResultParser.DASH_LINE, "Ran 1 test in 1s", "", "OK"
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParsePartialSingleLineMatchSkipped() throws Exception {
        String[] output = {
            "b (a) ... ok bad-token",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK"
        };

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestPassWithExpectedFailure() throws Exception {
        String[] output = {
            "b (a) ... expected failure",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK (expected failures=1)"
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseMultiTestPass() throws Exception {
        String[] output = {
            "b (a) ... ok",
            "d (c) ... ok",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 2 tests in 1s",
            "",
            "OK"
        };
        TestDescription id = new TestDescription("a", "b");
        TestDescription id2 = new TestDescription("c", "d");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 2);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id2));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id2), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseMultiTestPassWithOneExpectedFailure() throws Exception {
        String[] output = {
            "b (a) ... expected failure",
            "d (c) ... ok",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 2 tests in 1s",
            "",
            "OK (expected failures=1)"
        };
        TestDescription id = new TestDescription("a", "b");
        TestDescription id2 = new TestDescription("c", "d");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 2);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id2));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id2), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseMultiTestPassWithAllExpectedFailure() throws Exception {
        String[] output = {
            "b (a) ... expected failure",
            "d (c) ... expected failure",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 2 tests in 1s",
            "",
            "OK (expected failures=2)"
        };
        TestDescription id = new TestDescription("a", "b");
        TestDescription id2 = new TestDescription("c", "d");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 2);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id2));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id2), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestFail() throws Exception {
        String[] output = {
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
            "Ran 1 test in 1s",
            "",
            "FAILED (errors=1)"
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1)).testFailed(Mockito.any(), (String) Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSubtestFailure() throws Exception {
        String[] output = {
            "b (a) ... d (c) ... ok", // Tests with failed subtests don't output a status.
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "FAIL: b (a) (i=3)",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "  File \"example_test.py\", line 129, in test_with_failing_subtests",
            "    self.assertTrue(False)",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 2 test in 1s",
            "",
            "FAILED (failures=1)"
        };
        TestDescription id = new TestDescription("a", "b");
        TestDescription id2 = new TestDescription("c", "d");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 2);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id2));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id2), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1)).testFailed(Mockito.any(), (String) Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseMultiTestFailWithExpectedFailure() throws Exception {
        String[] output = {
            "b (a) ... expected failure",
            "d (c) ... ERROR",
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "ERROR: d (c)",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "  File \"test_rangelib.py\", line 129, in test_reallyfail",
            "    raise ValueError()",
            "ValueError",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "FAILED (errors=1)"
        };
        TestDescription id = new TestDescription("a", "b");
        TestDescription id2 = new TestDescription("c", "d");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id2));
        inOrder.verify(mMockListener, times(1)).testFailed(Mockito.any(), (String) Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id2), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestUnexpectedSuccess() throws Exception {
        String[] output = {
            "b (a) ... unexpected success",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK (unexpected success=1)",
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1)).testFailed(Mockito.any(), (String) Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestSkipped() throws Exception {
        String[] output = {
            "b (a) ... skipped 'reason foo'",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK (skipped=1)",
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1)).testIgnored(Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestPassWithDocString() throws Exception {
        String[] output = {
            "b (a)",
            "doc string foo bar ... ok",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK",
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestFailWithDocString() throws Exception {
        String[] output = {
            "b (a)",
            "doc string foo bar ... ERROR",
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "ERROR: b (a)",
            "doc string foo bar",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "  File \"test_rangelib.py\", line 129, in test_reallyfail",
            "    raise ValueError()",
            "ValueError",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "FAILED (errors=1)"
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1)).testFailed(Mockito.any(), (String) Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSingleTestPassWithCorruptedOutput() throws Exception {
        String[] output = {
            "b (a) ... text(not-status)",
            "text(not-status)",
            "ok",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 1 test in 1s",
            "",
            "OK"
        };
        TestDescription id = new TestDescription("a", "b");

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.eq(id));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.eq(id), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseOneWithEverything() throws Exception {
        String[] output = {
            "testError (foo.testFoo) ... ERROR",
            "testExpectedFailure (foo.testFoo) ... expected failure",
            "testFail (foo.testFoo) ... FAIL",
            "testFailWithDocString (foo.testFoo)",
            "foo bar ... FAIL",
            "testOk (foo.testFoo) ... ok",
            "testOkWithDocString (foo.testFoo)",
            "foo bar ... ok",
            "testSkipped (foo.testFoo) ... skipped 'reason foo'",
            "testUnexpectedSuccess (foo.testFoo) ... unexpected success",
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "ERROR: testError (foo.testFoo)",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "File \"foo.py\", line 11, in testError",
            "self.assertEqual(2+2, 5/0)",
            "ZeroDivisionError: integer division or modulo by zero",
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "FAIL: testFail (foo.testFoo)",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "File \"foo.py\", line 8, in testFail",
            "self.assertEqual(2+2, 5)",
            "AssertionError: 4 != 5",
            "",
            PythonUnitTestResultParser.EQUAL_LINE,
            "FAIL: testFailWithDocString (foo.testFoo)",
            "foo bar",
            PythonUnitTestResultParser.DASH_LINE,
            "Traceback (most recent call last):",
            "File \"foo.py\", line 8, in testFail",
            "self.assertEqual(2+2, 5)",
            "AssertionError: 4 != 5",
            "",
            PythonUnitTestResultParser.DASH_LINE,
            "Ran 8 tests in 1s",
            "",
            "FAILED (failures=2, errors=1, skipped=1, expected failures=1, unexpected successes=1)",
        };
        TestDescription[] ids = {
            new TestDescription("foo.testFoo", "testError"),
            new TestDescription("foo.testFoo", "testExpectedFailure"),
            new TestDescription("foo.testFoo", "testFail"),
            new TestDescription("foo.testFoo", "testFailWithDocString"),
            new TestDescription("foo.testFoo", "testOk"),
            new TestDescription("foo.testFoo", "testOkWithDocString"),
            new TestDescription("foo.testFoo", "testSkipped"),
            new TestDescription("foo.testFoo", "testUnexpectedSuccess")
        };

        mParser.processNewLines(output);

        verify(mMockListener, times(1)).testRunStarted("test", 8);
        verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testCaptureMultilineTraceback() {
        String[] output = {
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
            "Ran 1 test in 1s",
            "",
            "FAILED (errors=1)"
        };
        String[] tracebackLines = Arrays.copyOfRange(output, 5, 10);
        String expectedTrackback = String.join(System.lineSeparator(), tracebackLines);

        mParser.processNewLines(output);

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener, times(1)).testRunStarted("test", 1);
        inOrder.verify(mMockListener, times(1)).testStarted(Mockito.any());
        inOrder.verify(mMockListener, times(1))
                .testFailed(Mockito.any(), Mockito.eq(expectedTrackback));
        inOrder.verify(mMockListener, times(1))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener, times(1)).testRunEnded(1000L, new HashMap<String, Metric>());
    }

    @Test
    public void testParseRealOutput() {
        String[] contents = readInFile(PYTHON_OUTPUT_FILE_1);

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 11);
        verify(mMockListener, times(11)).testStarted(Mockito.any());
        verify(mMockListener, times(11))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.DisconnectionTest", "test_disconnect")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.EmulatorTest", "test_emulator_connect")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.PowerTest", "test_resume_usb_kick")));
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ServerTest", "test_handle_inheritance")),
                        (String) Mockito.any());
        verify(mMockListener).testRunEnded(10314, new HashMap<String, Metric>());
    }

    /** Test another output starting by a warning */
    @Test
    public void testParseRealOutput2() {
        String[] contents = readInFile(PYTHON_OUTPUT_FILE_2);

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 107);
        verify(mMockListener, times(107)).testStarted(Mockito.any());
        verify(mMockListener, times(107))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(295, new HashMap<String, Metric>());
    }

    @Test
    public void testParseRealOutput3() {
        String[] contents = readInFile(PYTHON_OUTPUT_FILE_3);

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 11);
        verify(mMockListener, times(11)).testStarted(Mockito.any());
        verify(mMockListener, times(11))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription("__main__.ConnectionTest", "test_reconnect")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.PowerTest", "test_resume_usb_kick")));
        verify(mMockListener).testRunEnded(27353, new HashMap<String, Metric>());
    }

    @Test
    public void testParseSubtestOutput() {
        String[] contents = readInFile("python_subtest_output.txt");

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 4);
        verify(mMockListener, times(4)).testStarted(Mockito.any());
        verify(mMockListener, times(4))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ExampleTest", "test_with_some_failing_subtests")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ExampleTest", "test_with_more_failing_subtests")),
                        (String) Mockito.any());
        verify(mMockListener).testRunEnded(1, new HashMap<String, Metric>());
    }

    @Test
    public void testParseTestResults_withIncludeFilters() {
        Set<String> includeFilters = new LinkedHashSet<>();
        Set<String> excludeFilters = new LinkedHashSet<>();
        includeFilters.add("__main__.ConnectionTest#test_connect_ipv4_ipv6");
        includeFilters.add("__main__.EmulatorTest");
        mParser =
                new PythonUnitTestResultParser(
                        ArrayUtil.list(mMockListener), "test", includeFilters, excludeFilters);

        String[] contents = readInFile(PYTHON_OUTPUT_FILE_1);

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 11);
        verify(mMockListener, times(11)).testStarted(Mockito.any());
        verify(mMockListener, times(11))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.EmulatorTest", "test_emulator_connect")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(new TestDescription("__main__.CommandlineTest", "test_help")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.CommandlineTest", "test_tcpip_error_messages")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.CommandlineTest", "test_version")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ConnectionTest", "test_already_connected")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.ConnectionTest", "test_reconnect")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.DisconnectionTest", "test_disconnect")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.PowerTest", "test_resume_usb_kick")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ServerTest", "test_handle_inheritance")));
        verify(mMockListener).testRunEnded(10314, new HashMap<String, Metric>());
    }

    @Test
    public void testParseTestResults_withExcludeFilters() {
        Set<String> includeFilters = new LinkedHashSet<>();
        Set<String> excludeFilters = new LinkedHashSet<>();
        excludeFilters.add("__main__.ConnectionTest#test_connect_ipv4_ipv6");
        excludeFilters.add("__main__.EmulatorTest");
        mParser =
                new PythonUnitTestResultParser(
                        ArrayUtil.list(mMockListener), "test", includeFilters, excludeFilters);

        String[] contents = readInFile(PYTHON_OUTPUT_FILE_1);

        mParser.processNewLines(contents);

        verify(mMockListener).testRunStarted("test", 11);
        verify(mMockListener, times(11)).testStarted(Mockito.any());
        verify(mMockListener, times(11))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ConnectionTest", "test_connect_ipv4_ipv6")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(new TestDescription("__main__.EmulatorTest", "test_emu_kill")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.EmulatorTest", "test_emulator_connect")));
        verify(mMockListener)
                .testIgnored(
                        Mockito.eq(
                                new TestDescription("__main__.PowerTest", "test_resume_usb_kick")));
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.DisconnectionTest", "test_disconnect")),
                        (String) Mockito.any());
        verify(mMockListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        "__main__.ServerTest", "test_handle_inheritance")),
                        (String) Mockito.any());
        verify(mMockListener).testRunEnded(10314, new HashMap<String, Metric>());
    }

    /**
     * Ensure that we end up in a COMPLETE state and do not throw an exception when summary has some
     * end-characters.
     */
    @Test
    public void testParseSummary() {
        Set<String> includeFilters = new LinkedHashSet<>();
        Set<String> excludeFilters = new LinkedHashSet<>();
        mParser =
                new PythonUnitTestResultParser(
                        ArrayUtil.list(mMockListener), "test", includeFilters, excludeFilters);

        String[] contents = readInFile(PYTHON_OUTPUT_FILE_4);
        // Shouldn't throw
        mParser.processNewLines(contents);
    }

    /**
     * Helper to read a file from the res/testtype directory and return its contents as a String[]
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a String[] of the
     */
    private String[] readInFile(String filename) {
        Vector<String> fileContents = new Vector<String>();
        try {
            InputStream gtestResultStream1 =
                    getClass()
                            .getResourceAsStream(
                                    File.separator + "testtype" + File.separator + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gtestResultStream1));
            String line = null;
            while ((line = reader.readLine()) != null) {
                fileContents.add(line);
            }
        } catch (NullPointerException e) {
            CLog.e("Gest output file does not exist: " + filename);
        } catch (IOException e) {
            CLog.e("Unable to read contents of gtest output file: " + filename);
        }
        return fileContents.toArray(new String[fileContents.size()]);
    }
}
