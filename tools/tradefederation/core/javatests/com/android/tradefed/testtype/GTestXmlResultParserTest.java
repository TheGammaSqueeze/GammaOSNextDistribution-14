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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.FileUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/** Unit tests for {@link GTestXmlResultParser} */
@RunWith(JUnit4.class)
public class GTestXmlResultParserTest {
    private static final String TEST_TYPE_DIR = "testtype";
    private static final String TEST_MODULE_NAME = "module";
    private static final String GTEST_OUTPUT_FILE_1 = "gtest_output1.xml";
    private static final String GTEST_OUTPUT_FILE_2 = "gtest_output2.xml";
    private static final String GTEST_OUTPUT_FILE_3 = "gtest_output3.xml";
    private static final String GTEST_OUTPUT_FILE_4 = "gtest_output4.xml";
    private static final String GTEST_OUTPUT_FILE_5 = "gtest_output5.xml";
    private static final String GTEST_OUTPUT_FILE_6 = "gtest_output6.xml";

    /**
     * Helper to read a file from the res/testtype directory and return the associated {@link File}
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a File to the output
     */
    private File readInFile(String filename) {
        InputStream gtest_output = null;
        File res = null;
        FileOutputStream out = null;
        try {
            gtest_output =
                    getClass()
                            .getResourceAsStream(
                                    File.separator + TEST_TYPE_DIR + File.separator + filename);
            res = FileUtil.createTempFile("unit_gtest_", ".xml");
            out = new FileOutputStream(res);
            byte[] buffer = new byte[1024];
            int byteRead;
            while ((byteRead = gtest_output.read(buffer)) != -1) {
                out.write(buffer, 0, byteRead);
            }
            out.close();
        } catch (NullPointerException | IOException e) {
            CLog.e("Gest output file does not exist: " + filename);
        }
        return res;
    }

    /** Tests the parser for a simple test run output with 6 tests. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseSimpleFile() throws Exception {
        File contents = readInFile(GTEST_OUTPUT_FILE_1);
        TestDescription firstInFile = new TestDescription("InteropTest", "test_lookup_hit");
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 6);
            verify(mockRunListener).testStarted(Mockito.eq(firstInFile), Mockito.anyLong());
            // +5 more passing test cases in this run
            verify(mockRunListener, times(6))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener, times(6))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Tests the parser for a run with 84 tests. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseLargerFile() throws Exception {
        File contents = readInFile(GTEST_OUTPUT_FILE_2);
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 84);
            // 84 passing test cases in this run
            verify(mockRunListener, times(84))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener, times(84))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Tests the parser for a run with test failures. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseWithFailures() throws Exception {
        String expectedMessage = "Message\nFailed";

        File contents = readInFile(GTEST_OUTPUT_FILE_3);
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 7);
            // 6 passing tests and 1 failed test.
            verify(mockRunListener, times(7))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener, times(7))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener)
                    .testFailed((TestDescription) Mockito.any(), Mockito.eq(expectedMessage));
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Tests the parser for a run with a bad file, as if the test hadn't outputed. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseWithEmptyFile() throws Exception {
        String expected = "Failed to get an xml output from tests, it probably crashed";

        File contents = FileUtil.createTempFile("test", ".xml");
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 0);
            verify(mockRunListener).testRunFailed(expected);
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Tests the parser for a simple test run output with 6 tests but report expected 7. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseUnexpectedNumberTest() throws Exception {
        String expected = "Test run incomplete. Expected 7 tests, received 6";
        File contents = readInFile(GTEST_OUTPUT_FILE_4);
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 7);
            // 6 passing test cases in this run
            verify(mockRunListener, times(6))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener, times(6))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener).testRunFailed(expected);
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /**
     * Tests the parser for a simple test run output with 6 tests but a bad xml tag so some tests
     * won't be parsed.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseSimpleFile_badXmltag() throws Exception {
        String expected = "Test run incomplete. Expected 6 tests, received 3";
        File contents = readInFile(GTEST_OUTPUT_FILE_5);
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 6);
            verify(mockRunListener, times(3))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener, times(3))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener).testRunFailed(expected);
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Tests the parser for a run with a bad file, with Collector output to get some logs. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseWithEmptyFile_AdditionalOutput() throws Exception {
        final String exec_log = "EXECUTION LOG";
        CollectingOutputReceiver fake =
                new CollectingOutputReceiver() {
                    @Override
                    public String getOutput() {
                        return exec_log;
                    }
                };
        String expected =
                "Failed to get an xml output from tests, it probably crashed\nlogs:\n" + exec_log;

        File contents = FileUtil.createTempFile("test", ".xml");
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, fake);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 0);
            verify(mockRunListener).testRunFailed(expected);
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }

    /** Ensure that skipped status is properly carried. */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseSimpleFile_skipped() throws Exception {
        File contents = readInFile(GTEST_OUTPUT_FILE_6);
        TestDescription firstInFile = new TestDescription("InteropTest", "test_lookup_hit");
        try {
            ITestInvocationListener mockRunListener = mock(ITestInvocationListener.class);

            GTestXmlResultParser resultParser =
                    new GTestXmlResultParser(TEST_MODULE_NAME, mockRunListener);
            resultParser.parseResult(contents, null);

            verify(mockRunListener).testRunStarted(TEST_MODULE_NAME, 6);
            verify(mockRunListener).testStarted(Mockito.eq(firstInFile), Mockito.anyLong());
            verify(mockRunListener)
                    .testEnded(
                            Mockito.eq(firstInFile),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener, times(6))
                    .testStarted((TestDescription) Mockito.any(), Mockito.anyLong());
            verify(mockRunListener).testIgnored((TestDescription) Mockito.any());
            verify(mockRunListener, times(6))
                    .testEnded(
                            (TestDescription) Mockito.any(),
                            Mockito.anyLong(),
                            (HashMap<String, Metric>) Mockito.any());
            verify(mockRunListener)
                    .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
        } finally {
            FileUtil.deleteFile(contents);
        }
    }
}
