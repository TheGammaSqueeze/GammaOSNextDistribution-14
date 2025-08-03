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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.retry.ISupportGranularResults;
import com.android.tradefed.util.FileUtil;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A result reporter that saves test results needed by ResultDB and LUCI
 * into JSON format (go/result-sink) and logs the file location in the console.
 * https://pkg.go.dev/go.chromium.org/luci/resultdb/proto/v1#TestResult
 * It stores the test result for each test case in the test run in an array.
 */
@OptionClass(alias = "luci-result-reporter")
public class LUCIResultReporter extends CollectingTestListener
        implements ILogSaverListener, ISupportGranularResults {

    /** Separator for class name and method name when encoding test identifier */
    private final static String SEPARATOR = "#";
    private final static String RESULT_SEPARATOR = "##";

    /** Constants used as keys in JSON results */
    private final static String KEY_TEST_RESULTS = "tr";
    private final static String KEY_RESULT_NAME = "result_name";
    private final static String KEY_TEST_ID = "testId";
    private final static String KEY_TEST_NAME = "test_name";
    private final static String KEY_STATUS = "status";
    private final static String KEY_EXPECTED = "expected";
    private final static String KEY_NAME = "name";
    private final static String KEY_TEST_METADATA = "testMetadata";
    private final static String KEY_RAW_STATUS = "raw_status";
    private final static String KEY_TAGS = "tags";
    private final static String KEY_DURATION = "duration";
    private final static String KEY_FAILURE_REASON = "failureReason";

    /** Prefix of directory under default temp folder to save result file. */
    private final static String RESULT_DIR = "luci_formatted_results";

    @Option(
            name = "additional-key-value-pairs",
            description = "Map of additional key/value pairs to be added to the results.")
    private Map<String, String> mAdditionalKeyValuePairs = new LinkedHashMap<>();

    @Option(
        name = "enable-granular-attempts",
        description =
                "Whether or not to allow this reporter receiving granular attempts. Feature flag."
    )
    private boolean mReportGranularResults = true;

    private boolean mHasInvocationFailures = false;
    private LinkedHashMap<String, LogFile> mLoggedFiles = new LinkedHashMap<>();
    private File mRootDir = null;

    // Map from Tradefed TestStatus to LUCI TestStatus in test_result.pb.go.
    // https://pkg.go.dev/go.chromium.org/luci/resultdb/proto/v1#TestStatus
    private static Map<TestStatus, String> resultMap =
            ImmutableMap.of(
                    TestStatus.FAILURE, "FAIL",
                    TestStatus.PASSED, "PASS",
                    TestStatus.INCOMPLETE, "CRASH",
                    TestStatus.ASSUMPTION_FAILURE, "SKIP",
                    TestStatus.IGNORED, "SKIP",
                    TestStatus.SKIPPED, "SKIP");

    @Override
    public boolean supportGranularResults() {
        return mReportGranularResults;
    }

    public void setGranularResults(boolean granularResults) {
        mReportGranularResults = granularResults;
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        try {
            mRootDir = FileUtil.createTempDir(RESULT_DIR);
        } catch(IOException e) {
            CLog.e("Failed to create tmpdir");
            CLog.e(e);
        }
    }

    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        mHasInvocationFailures = true;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        if (mHasInvocationFailures) {
            CLog.d("Skipping reporting because there are invocation failures.");
        } else {
          try {
              // Merge all runs across different attempts with the latest test result
              // overwriting test results of previous runs.
              Collection<TestRunResult> runResults = getMergedTestRunResults();
              if (mReportGranularResults) {
                  // Replace runResults with separate test result for all runs across
                  // attempts if there are retries.
                  runResults.clear();
                  Collection<String> testRunNames = getTestRunNames();
                  testRunNames.forEach(testRunName -> {
                      List<TestRunResult> attempts = getTestRunAttempts(testRunName);
                      attempts.forEach(attempt -> runResults.add(attempt));
                  });
              }
              JSONObject jsonResults = convertMetricsToJson(runResults);
              saveJsonFile(jsonResults);
          } catch (JSONException e) {
              CLog.e("JSONException while converting test metrics.");
              CLog.e(e);
          }
        }

        // Log the result file locations to console.
        for (Entry<String, LogFile> entry : mLoggedFiles.entrySet()) {
            String dataName = entry.getKey();
            LogFile logFile = entry.getValue();
            printLog(dataName, logFile);
        }
    }

    /**
     * A util method that converts test metrics to the JSON format needed by ResultDB and LUCI.
     *
     * @return a JSONObject containing the test result
     */
    JSONObject convertMetricsToJson(Collection<TestRunResult> runResults) throws JSONException {
        JSONArray testResultArray = new JSONArray();

        // Loops over all test runs.
        for (TestRunResult runResult : runResults) {
            // Populate run name.
            StringBuilder runNameAndTimestamp = new StringBuilder();
            if (!runResult.getRunMetrics().isEmpty()) {
                String reportingUnit = runResult.getName();
                runNameAndTimestamp.append(String.format("%s%s", reportingUnit, RESULT_SEPARATOR));
            }
            // Get test result of test case(s) in the test run.
            Map<TestDescription, TestResult> testResultMap = runResult.getTestResults();
            // Loop over all test cases and populate test result for LUCI.
            for (Entry<TestDescription, TestResult> entry : testResultMap.entrySet()) {
                JSONObject testResultContainer = new JSONObject();
                TestDescription testDescription = entry.getKey();
                TestResult testResult = entry.getValue();

                // A name that identifies a result by run name and timestamp, not needed by LUCI
                OffsetDateTime offsetDT = OffsetDateTime.now();
                testResultContainer.put(KEY_RESULT_NAME, runNameAndTimestamp.toString() + offsetDT.toString());

                String testId = String.join(SEPARATOR, testDescription.getClassName(),
                                            testDescription.getTestName());
                testResultContainer.put(KEY_TEST_ID, testId);

                TestStatus rawStatus = testResult.getResultStatus();
                String status = resultMap.get(rawStatus);
                testResultContainer.put(KEY_STATUS, status);
                boolean expected = ("PASS".equals(status) || "SKIP".equals(status)) ? true : false;
                testResultContainer.put(KEY_EXPECTED, expected);
                // If failed test, add non-null failure reason.
                if (rawStatus == TestStatus.FAILURE || rawStatus == TestStatus.INCOMPLETE
                    || rawStatus == TestStatus.ASSUMPTION_FAILURE) {
                    FailureDescription failure = testResult.getFailure();
                    String errorMessage = ((failure.getCause() == null)
                        ? failure.getErrorMessage() : failure.getCause().toString());
                    String failureReason = testResult.getFailure().getOrigin() + ": "
                        + errorMessage;
                    testResultContainer.put(KEY_FAILURE_REASON, failureReason);
                }

                JSONObject testMetadataObj = new JSONObject();
                testMetadataObj.put(KEY_NAME, testId);
                testResultContainer.put(KEY_TEST_METADATA, testMetadataObj);

                JSONArray tags = new JSONArray();
                JSONObject testIdObj = new JSONObject();
                testIdObj.put("key", KEY_TEST_NAME);
                testIdObj.put("value", testId);
                tags.put(testIdObj);
                JSONObject rawStatusObj = new JSONObject();
                rawStatusObj.put("key", KEY_RAW_STATUS);
                rawStatusObj.put("value", rawStatus.toString());
                tags.put(rawStatusObj);
                testResultContainer.put(KEY_TAGS, tags);
                // Test duration (Long type) in seconds.
                Long duration = testResult.getEndTime() - testResult.getStartTime();
                testResultContainer.put(KEY_DURATION, duration / 1000.0);

                testResultArray.put(testResultContainer);
            }
        }

        JSONObject result = new JSONObject();
        result.put(KEY_TEST_RESULTS, testResultArray);

        if (!mAdditionalKeyValuePairs.isEmpty()) {
            for (Map.Entry<String, String> pair : mAdditionalKeyValuePairs.entrySet()) {
                result.put(pair.getKey(), pair.getValue());
            }
        }

        boolean isValidResult = isValidObject("result", result);
        if (isValidResult) {
            CLog.logAndDisplay(LogLevel.DEBUG, "Result JSON object is valid.");
        } else {
            CLog.logAndDisplay(LogLevel.DEBUG, "Result JSON object is invalid.");
        }

        return result;
    }

    /** Saves the JSON result file. */
    public void saveJsonFile(JSONObject jsonResults) {
        ByteArrayInputStream resultStream = new ByteArrayInputStream(
            jsonResults.toString().getBytes());
        LogFileSaver saver = new LogFileSaver(mRootDir);
        File generatedDir = saver.getFileDir();
        try {
          File logFile = saver.saveLogData("LUCIResult", LogDataType.JSON, resultStream);
          logResultFileLocation(logFile);
        } catch(IOException e) {
          CLog.e("Failed to save JSON results to " + generatedDir.toString());
          CLog.e(e);
        }
    }

    /** Log the JSON result file location to console. */
    void logResultFileLocation(File logFile) {
        CLog.logAndDisplay(LogLevel.DEBUG, "JSON result for LUCI: %s", logFile.getPath());
    }

    /** A helper method to validate the values in a JSONObject. */
    boolean isValidJSONObject(String key, JSONObject jsonObject) throws JSONException {
        boolean isValid = true;
        Iterator<String> keys = (Iterator<String>) jsonObject.keys();
        while (keys.hasNext()) {
            String subKey = keys.next();
            isValid = isValid && isValidObject(subKey, jsonObject.get(subKey));
        }
        return isValid;
    }

    /** A helper method to validate the values in a JSONArray. */
    boolean isValidJSONArray(String key, JSONArray jsonArray) throws JSONException {
        boolean isValid = true;
        for (int i = 0; i < jsonArray.length(); i++) {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          isValid = isValid && isValidJSONObject("", jsonObject);
        }
        return isValid;
    }

    /**
     * Traverses JSON object that may contain nested JSONObject or JSONArray to check if values
     * are either String, Double or Boolean.
    */
    boolean isValidObject(String key, Object value) throws JSONException {
        boolean isValid = true;
        if (value instanceof JSONObject) {
            isValid = isValid && isValidJSONObject(key, (JSONObject) value);
        } else if (value instanceof JSONArray) {
            isValid = isValid && isValidJSONArray(key, (JSONArray) value);
        } else {
            isValid = isValid && ((value instanceof String) || (value instanceof Double)
                || (value instanceof Boolean));
        }
        if (!isValid) {
            CLog.logAndDisplay(LogLevel.DEBUG, "Invalid result (key=%s, value=%s) of type: %s",
                                key, value.toString(), value.getClass().getName());
        }
        return isValid;
    }

    /** Collects result files. */
    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        mLoggedFiles.put(dataName, logFile);
    }

    /** A helper method to format and print result file's name and location to console. */
    private void printLog(String dataName, LogFile logFile) {
        String logDesc = logFile.getUrl() == null ? logFile.getPath() : logFile.getUrl();
        CLog.logAndDisplay(LogLevel.DEBUG, "%s: %s\r\n", dataName, logDesc);
    }
}