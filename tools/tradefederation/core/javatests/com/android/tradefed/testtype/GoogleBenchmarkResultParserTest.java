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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link GoogleBenchmarkResultParser}. */
@RunWith(JUnit4.class)
public class GoogleBenchmarkResultParserTest {

    private static final String TEST_TYPE_DIR = "testtype";
    private static final String GBENCH_OUTPUT_FILE_1 = "gbench_output1.json";
    private static final String GBENCH_OUTPUT_FILE_2 = "gbench_output2.json";
    private static final String GBENCH_OUTPUT_FILE_3 = "gbench_output3.json";
    private static final String GBENCH_OUTPUT_FILE_4 = "gbench_output4.json";
    private static final String GBENCH_OUTPUT_FILE_5 = "gbench_output5.json";
    private static final String GBENCH_OUTPUT_FILE_6 = "gbench_output6.json";
    private static final String GBENCH_OUTPUT_FILE_7 = "gbench_output7.json";
    private static final String GBENCH_OUTPUT_FILE_8 = "gbench_output8.json";

    private static final String TEST_RUN = "test_run";

    /**
     * Helper to read a file from the res/testtype directory and return its contents as a String
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a String[] of the
     */
    private String readInFile(String filename) {
        String retString = "";
        try {
            InputStream gtestResultStream1 =
                    getClass()
                            .getResourceAsStream(
                                    File.separator + TEST_TYPE_DIR + File.separator + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gtestResultStream1));
            String line;
            while ((line = reader.readLine()) != null) {
                retString += line + "\n";
            }
        } catch (NullPointerException e) {
            CLog.e("GTest output file does not exist: " + filename);
        } catch (IOException e) {
            CLog.e("Unable to read contents of gtest output file: " + filename);
        }
        return retString;
    }

    /** Tests the parser for a simple test output for 2 tests. */
    @Test
    public void testParseSimpleFile() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_1));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        Map<String, String> results = resultParser.parse(cmd_result);
        Map<String, String> expectedRes = new HashMap<String, String>();
        expectedRes.put("date", "2016-03-07 10:59:01");
        expectedRes.put("num_cpus", "48");
        expectedRes.put("mhz_per_cpu", "2601");
        expectedRes.put("cpu_scaling_enabled", "true");
        expectedRes.put("library_build_type", "debug");

        expectedRes.put("Pass", "3");
        assertEquals(expectedRes, results);
        verify(mMockInvocationListener, times(3)).testStarted((TestDescription) Mockito.any());
        verify(mMockInvocationListener, times(3))
                .testEnded((TestDescription) Mockito.any(), capture.capture());

        // Test 1
        HashMap<String, Metric> resultTest1 = capture.getAllValues().get(0);
        assertEquals(4, resultTest1.size());
        assertEquals("5", resultTest1.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("5", resultTest1.get("real_time").getMeasurements().getSingleString());
        assertEquals("BM_one", resultTest1.get("name").getMeasurements().getSingleString());
        assertEquals(
                "109451958", resultTest1.get("iterations").getMeasurements().getSingleString());

        // Test 2
        HashMap<String, Metric> resultTest2 = capture.getAllValues().get(1);
        assertEquals(4, resultTest2.size());
        assertEquals("11", resultTest2.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("1", resultTest2.get("real_time").getMeasurements().getSingleString());
        assertEquals("BM_two", resultTest2.get("name").getMeasurements().getSingleString());
        assertEquals("50906784", resultTest2.get("iterations").getMeasurements().getSingleString());

        // Test 3
        HashMap<String, Metric> resultTest3 = capture.getAllValues().get(2);
        assertEquals(5, resultTest3.size());
        assertEquals("60", resultTest3.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("60", resultTest3.get("real_time").getMeasurements().getSingleString());
        assertEquals(
                "BM_string_strlen/64", resultTest3.get("name").getMeasurements().getSingleString());
        assertEquals("10499948", resultTest3.get("iterations").getMeasurements().getSingleString());
        assertEquals(
                "1061047935",
                resultTest3.get("bytes_per_second").getMeasurements().getSingleString());
    }

    /** Tests the parser for a two simple test with different keys on the second test. */
    @Test
    public void testParseSimpleFile_twoTests() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_2));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);
        verify(mMockInvocationListener, times(2)).testStarted((TestDescription) Mockito.any());
        verify(mMockInvocationListener, times(2))
                .testEnded((TestDescription) Mockito.any(), capture.capture());

        HashMap<String, Metric> results = capture.getAllValues().get(0);
        assertEquals(4, results.size());
        assertEquals("5", results.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("5", results.get("real_time").getMeasurements().getSingleString());
        assertEquals("BM_one", results.get("name").getMeasurements().getSingleString());
        assertEquals("109451958", results.get("iterations").getMeasurements().getSingleString());
    }

    /**
     * Tests the parser with an error in the context, should stop parsing because format is
     * unexpected.
     */
    @Test
    public void testParse_contextError() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_3));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testRunFailed((FailureDescription) Mockito.any());
    }

    /** Tests the parser with an error: context is fine but no benchmark results */
    @Test
    public void testParse_noBenchmarkResults() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_4));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testRunFailed((String) Mockito.any());
    }

    /** Tests that errors reported in JSON are parsed and appropriately reported */
    @Test
    public void testParse_benchmarkError() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<String> capture = ArgumentCaptor.forClass(String.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_7));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);
        verify(mMockInvocationListener, times(4)).testStarted((TestDescription) Mockito.any());
        verify(mMockInvocationListener, times(4))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockInvocationListener, times(1))
                .testFailed((TestDescription) Mockito.any(), capture.capture());
        assertEquals(
                "Benchmark reported an error: synchronizeKernelRCU() failed with errno=13",
                capture.getValue());
    }

    /** Test for {@link GoogleBenchmarkResultParser#parseJsonToMap(JSONObject)} */
    @Test
    public void testJsonParse() throws JSONException {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);
        String jsonTest = "{ \"key1\": \"value1\", \"key2\": 2, \"key3\": [\"one\", \"two\"]}";
        JSONObject test = new JSONObject(jsonTest);
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        Map<String, String> results = resultParser.parseJsonToMap(test);
        assertEquals(results.get("key1"), "value1");
        assertEquals(results.get("key2"), "2");
        assertEquals(results.get("key3"), "[\"one\",\"two\"]");
    }

    /**
     * Test when a warning is printed before the JSON output. As long as the JSON is fine, we should
     * still parse the output.
     */
    @Test
    public void testParseSimpleFile_withWarning() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_5));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testStarted((TestDescription) Mockito.any());
        verify(mMockInvocationListener)
                .testEnded((TestDescription) Mockito.any(), capture.capture());

        HashMap<String, Metric> results = capture.getValue();
        assertEquals(5, results.size());
        assertEquals("19361", results.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("44930", results.get("real_time").getMeasurements().getSingleString());
        assertEquals("BM_addInts", results.get("name").getMeasurements().getSingleString());
        assertEquals("36464", results.get("iterations").getMeasurements().getSingleString());
        assertEquals("ns", results.get("time_unit").getMeasurements().getSingleString());
    }

    /** When iterations is 0 it means the test was skipped. */
    @Test
    public void testParse_ignore() throws Exception {
        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_6));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testStarted((TestDescription) Mockito.any());
        verify(mMockInvocationListener).testIgnored(Mockito.any());
        verify(mMockInvocationListener)
                .testEnded((TestDescription) Mockito.any(), capture.capture());

        HashMap<String, Metric> results = capture.getValue();
        assertEquals(5, results.size());
        assertEquals("19361", results.get("cpu_time").getMeasurements().getSingleString());
        assertEquals("44930", results.get("real_time").getMeasurements().getSingleString());
        assertEquals("BM_addInts", results.get("name").getMeasurements().getSingleString());
        assertEquals("0", results.get("iterations").getMeasurements().getSingleString());
        assertEquals("ns", results.get("time_unit").getMeasurements().getSingleString());
    }

    /** Test proper failure is reported when test is aborted. */
    @Test
    public void testParse_aborted() throws Exception {
        // The error reported with GBENCH_OUTPUT_FILE_8.
        String errorMessage =
                "Test aborted with the error: 'FontFamily must contain at least one font.'";

        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);
        ArgumentCaptor<FailureDescription> capture =
                ArgumentCaptor.forClass(FailureDescription.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.SUCCESS);
        cmd_result.setStdout(readInFile(GBENCH_OUTPUT_FILE_8));
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testRunFailed(capture.capture());
        FailureDescription failure = capture.getValue();
        assertTrue(FailureStatus.TEST_FAILURE.equals(failure.getFailureStatus()));
        assertEquals(failure.getErrorMessage(), errorMessage);
    }

    /** Test proper failure is reported when test is timed out. */
    @Test
    public void testParse_timedout() throws Exception {
        String errorMessage = "Test timed out.";

        ITestInvocationListener mMockInvocationListener = mock(ITestInvocationListener.class);
        ArgumentCaptor<FailureDescription> capture =
                ArgumentCaptor.forClass(FailureDescription.class);

        CommandResult cmd_result = new CommandResult();
        cmd_result.setStatus(CommandStatus.TIMED_OUT);
        cmd_result.setStdout("Random text.");
        GoogleBenchmarkResultParser resultParser =
                new GoogleBenchmarkResultParser(TEST_RUN, mMockInvocationListener);
        resultParser.parse(cmd_result);

        verify(mMockInvocationListener).testRunFailed(capture.capture());
        FailureDescription failure = capture.getValue();
        assertEquals(FailureStatus.TIMED_OUT, failure.getFailureStatus());
        assertEquals(failure.getErrorMessage(), errorMessage);
    }
}
