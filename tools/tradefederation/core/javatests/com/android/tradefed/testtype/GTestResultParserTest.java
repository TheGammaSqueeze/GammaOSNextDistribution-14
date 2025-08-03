/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;

/** Unit tests for {@link GTestResultParser}. */
@RunWith(JUnit4.class)
public class GTestResultParserTest extends GTestParserTestBase {

    /** Tests the parser for a simple test run output with 11 tests. */
    @Test
    public void testParseSimpleFile() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_1);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 11);
        verify(mockRunListener, times(11)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(11))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a simple test run output with 53 tests and no times. */
    @Test
    public void testParseSimpleFileNoTimes() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_2);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 53);
        verify(mockRunListener, times(53)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(53))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a simple test run output with 0 tests and no times. */
    @Test
    public void testParseNoTests() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_3);
        HashMap<String, Metric> expected = new HashMap<>();
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 0);
        verify(mockRunListener).testRunEnded(Mockito.anyLong(), Mockito.eq(expected));
    }

    /** Tests the parser for a run with 268 tests. */
    @Test
    public void testParseLargerFile() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_4);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 268);
        verify(mockRunListener, times(268)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(268))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a run with test failures. */
    @Test
    public void testParseWithFailures() throws Exception {
        String MESSAGE_OUTPUT = "This is some random text that should get captured by the parser.";
        String[] contents = readInFile(GTEST_OUTPUT_FILE_5);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);
        // 13 test cases in this run
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 13);
        verify(mockRunListener, times(13))
                .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(3))
                .testFailed((TestDescription) Mockito.any(), (String) Mockito.any());
        verify(mockRunListener)
                .testFailed((TestDescription) Mockito.any(), Mockito.matches(MESSAGE_OUTPUT));
        verify(mockRunListener, times(13))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        assertTrue("Test run contains Unexpected Test.", !resultParser.isUnexpectedTestFound());
    }

    /** Tests the parser for a run with test errors. */
    @Test
    public void testParseWithErrors() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_6);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 10);
        verify(mockRunListener, times(10)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(10))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener, times(2)).testFailed(Mockito.any(), (String) Mockito.any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a run with 11 tests. */
    @Test
    public void testParseNonAlignedTag() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_7);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 11);
        verify(mockRunListener, times(11)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(11))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Tests the parser for a simple test run output with 18 tests with Non GTest format Should not
     * crash.
     */
    @Test
    public void testParseSimpleFile_AltFormat() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_8);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 18);
        verify(mockRunListener, times(18)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(18))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener, times(3)).testFailed(Mockito.any(), (String) Mockito.any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a simple test run output with a link error. */
    @Test
    public void testParseSimpleFile_LinkError() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_9);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 0);
        verify(mockRunListener).testRunFailed(captured.capture());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "module did not report any run:\nCANNOT LINK EXECUTABLE "
                                        + "\"/data/installd_cache_test\": "
                                        + "library \"liblogwrap.so\" not found"));
    }

    /**
     * Test that if the binary simply doesn't output something obvious and doesn't report any run we
     * report all the logs we currently have and an error.
     */
    @Test
    public void testParseSimpleFile_earlyError() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_10);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 0);
        verify(mockRunListener).testRunFailed(captured.capture());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "module did not report any run:\n"
                                        + "failed to read section .testzipdata"));
    }

    /** Tests the parser for a simple test run output with 11 tests where some are skipped. */
    @Test
    public void testParseSimpleFileWithSkips() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_11);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 11);
        verify(mockRunListener, times(11)).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener, times(2)).testIgnored((TestDescription) Mockito.any());
        verify(mockRunListener, times(11))
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Tests the parser for a test runs but doesn't finish run completely. */
    @Test
    public void testParseSimpleFileWithoutRunComplete() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_12);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 11);
        verify(mockRunListener).testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
        verify(mockRunListener)
                .testFailed((TestDescription) Mockito.any(), (FailureDescription) Mockito.any());
        verify(mockRunListener)
                .testEnded((TestDescription) Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener).testRunFailed(captured.capture());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        FailureDescription cap = captured.getValue();
        assertEquals("Test run incomplete. Expected 11 tests, received 0", cap.getErrorMessage());
    }

    /** Tests the parser when all tests are executed, but doesn't get run end tag from device. */
    @Test
    public void testParseSimpleFile_TestsExecutedButNoRunEndTag() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_14);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);
        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 1);
        verify(mockRunListener).testStarted(Mockito.any(), Mockito.anyLong());
        verify(mockRunListener)
                .testEnded(
                        (TestDescription) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testParse_interrupted() throws Exception {
        String[] contents = readInFile(GTEST_OUTPUT_FILE_13);
        ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        GTestResultParser resultParser = new GTestResultParser(TEST_MODULE_NAME, mockRunListener);
        resultParser.processNewLines(contents);
        resultParser.flush();

        verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 11);
        verify(mockRunListener).testRunFailed(captured.capture());
        verify(mockRunListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        FailureDescription cap = captured.getValue();
        assertEquals("Test run incomplete. Expected 11 tests, received 0", cap.getErrorMessage());
        Mockito.verifyNoMoreInteractions(mockRunListener);
    }
}
