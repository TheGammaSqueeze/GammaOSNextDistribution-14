/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.Throwable;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link LUCIResultReporter}. */
@RunWith(JUnit4.class)
public class LUCIResultReporterTest {

    // Constants used as keys in JSON results in {@link LUCIResultReporter}.
    private static final String KEY_TEST_RESULTS = "tr";
    private final static String KEY_TEST_METADATA = "testMetadata";
    private final static String KEY_FILENAME = "file_name";
    private final static String KEY_REPO = "repo";
    private final static String KEY_NAME = "name";
    private final static String KEY_EXPECTED = "expected";
    private final static String KEY_TEST_ID = "testId";
    private final static String KEY_STATUS = "status";
    private final static String KEY_DURATION = "duration";
    private final static String KEY_TAGS = "tags";
    private final static String KEY_FAILURE_REASON = "failureReason";
    private final static String VALUE_FAIL = "FAIL";
    private final static String VALUE_PASS = "PASS";

    private LUCIResultReporter mReporter;
    private IInvocationContext mContext;

    @Before
    public void setUp() throws Exception {
        mReporter = spy(new LUCIResultReporter());
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("fakeDevice", new BuildInfo());
    }

    @After
    public void tearDown() throws Exception {
        // Delete the temp folder containing the JSON result file.
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(mReporter).logResultFileLocation(fileCaptor.capture());
        File logFile = fileCaptor.getValue();
        File mRootDir = logFile.getParentFile().getParentFile();
        FileUtil.recursiveDelete(mRootDir);
    }

    /** Test required keys exist in JSONObject if test passed. */
    @Test
    public void shouldSetRequiredJsonKeys_passedTest() throws JSONException {
        mReporter.invocationStarted(mContext);
        // Inject a random valid metric "5.99".
        injectTestRun(mReporter, "run1", "testClass1", "test1", "5.99", 0, false);
        mReporter.invocationEnded(0);
        ArgumentCaptor<JSONObject> jsonCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(mReporter).saveJsonFile(jsonCaptor.capture());
        JSONArray tr = jsonCaptor.getValue().getJSONArray(KEY_TEST_RESULTS);
        // Check all the test result JSONObjects have the required keys.
        assertJsonKeysExist(tr);
        // Passed test must not have failure reason key.
        for (int i = 0; i < tr.length(); i++) {
            Assert.assertFalse(tr.getJSONObject(i).has(KEY_FAILURE_REASON));
        }
    }

    /** Test required keys exist in JSONObject if test failed. */
    @Test
    public void shouldSetRequiredJsonKeys_failedTest() throws JSONException {
        mReporter.invocationStarted(mContext);
        // Inject a random valid metric "5.99".
        injectTestRun(mReporter, "run2", "testClass1", "test1", "5.99", 0, true);
        mReporter.invocationEnded(0);
        ArgumentCaptor<JSONObject> jsonCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(mReporter).saveJsonFile(jsonCaptor.capture());
        JSONArray tr = jsonCaptor.getValue().getJSONArray(KEY_TEST_RESULTS);
        // Check all the test result JSONObjects have the required keys.
        assertJsonKeysExist(tr);
        // Failed test must have failure reason key.
        for (int i = 0; i < tr.length(); i++) {
            Assert.assertTrue(tr.getJSONObject(i).has(KEY_FAILURE_REASON));
        }
    }

    /** Test separate test results for each attempt exist in JSONObject
     *  if test failed and retried. */
    @Test
    public void shouldReportSeparateTestResults_failedThenPassedTest() throws JSONException {
        mReporter.invocationStarted(mContext);
        // Inject a random valid metric and run 3 attempts (2 fail and 1 pass)
        injectTestRun(mReporter, "run2", "testClass1", "test1", "1.99", 1, true);
        injectTestRun(mReporter, "run2", "testClass1", "test1", "2.99", 2, true);
        injectTestRun(mReporter, "run2", "testClass1", "test1", "3.99", 3, false);
        mReporter.invocationEnded(0);
        ArgumentCaptor<JSONObject> jsonCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(mReporter).saveJsonFile(jsonCaptor.capture());
        JSONArray tr = jsonCaptor.getValue().getJSONArray(KEY_TEST_RESULTS);
        // 3 test results should exist corresponding to the 3 attempts.
        Assert.assertEquals(3, tr.length(), 0);
        Assert.assertEquals("Status is wrong.", VALUE_FAIL, tr.getJSONObject(0).get(KEY_STATUS));
        Assert.assertEquals("Status is wrong.", VALUE_FAIL, tr.getJSONObject(1).get(KEY_STATUS));
        Assert.assertEquals("Status is wrong.", VALUE_PASS, tr.getJSONObject(2).get(KEY_STATUS));
    }

    /** Test JSONObject values are valid. */
    @Test
    public void shouldSetValidJsonValues() throws JSONException {
        mReporter.invocationStarted(mContext);
        // Inject a random valid metric "5.99".
        injectTestRun(mReporter, "run1", "testClass1", "test1", "5.99", 0, false);
        mReporter.invocationEnded(0);
        ArgumentCaptor<JSONObject> jsonCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(mReporter).saveJsonFile(jsonCaptor.capture());
        JSONObject jsonResults = jsonCaptor.getValue();
        // The JSONObject must pass type validation.
        Assert.assertTrue(mReporter.isValidObject("results", jsonResults));
    }

    /**
     * Injects a single test run with 1 test into the {@link LUCIResultReporter} under test.
     *
     * @return the {@link TestDescription} of added test
     */
    private TestDescription injectTestRun(
            CollectingTestListener listener,
            String runName,
            String className,
            String testName,
            String metricValue,
            int attempt,
            boolean failTest) {
        Map<String, String> runMetrics = new HashMap<String, String>(1);
        runMetrics.put("run_metric", metricValue);
        Map<String, String> testMetrics = new HashMap<String, String>(1);
        testMetrics.put("test_metric", metricValue);

        listener.testRunStarted(runName, 1, attempt);
        final TestDescription test = new TestDescription(className, testName);
        listener.testStarted(test);
        if (failTest) {
            FailureDescription failure = FailureDescription.create("fake stacktrace");
            Throwable fakeThrowable = new Throwable("fake detailed message of throwable");
            failure.setOrigin(className + ".java");
            failure.setCause(fakeThrowable);
            listener.testFailed(test, failure);
        }
        listener.testEnded(test, TfMetricProtoUtil.upgradeConvert(testMetrics, true));
        listener.testRunEnded(0, TfMetricProtoUtil.upgradeConvert(runMetrics, true));
        return test;
    }

    /** Helper method to assert the common keys exist in a JSONArray of JSONObjects. */
    private void assertJsonKeysExist(JSONArray jsonArray) throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Assert.assertTrue(jsonObject.has(KEY_TEST_METADATA));
            Assert.assertTrue(jsonObject.getJSONObject(KEY_TEST_METADATA).has(KEY_NAME));
            Assert.assertTrue(jsonObject.has(KEY_DURATION));
            Assert.assertTrue(jsonObject.has(KEY_EXPECTED));
            Assert.assertTrue(jsonObject.has(KEY_STATUS));
            Assert.assertTrue(jsonObject.has(KEY_TAGS));
        }
    }
}