/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.skipped.SkipReason;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/** Listener that logs all the events it receives into a file */
public class EventsLoggerListener implements ILogSaverListener {

    private File mLog;
    private TestStatus mTestCaseStatus = null;

    public EventsLoggerListener(String name) {
        try {
            mLog = FileUtil.createTempFile(name, ".txt");
        } catch (IOException e) {
            CLog.e(e);
            mLog = null;
        }
    }

    public File getLoggedEvents() {
        return mLog;
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        writeToFile("[invocation started]\n");
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        writeToFile(
                String.format(
                        "[invocation failed: %s|%s|%s]\n",
                        failure.getFailureStatus(), failure.getErrorIdentifier(), failure));
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        writeToFile("[invocation ended]\n");
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        writeToFile(
                String.format(
                        "  [module %s started]\n",
                        moduleContext
                                .getAttributes()
                                .getUniqueMap()
                                .get(ModuleDefinition.MODULE_ID)));
    }

    @Override
    public void testModuleEnded() {
        writeToFile("  [module ended]\n");
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        testRunStarted(runName, testCount, 0, System.currentTimeMillis());
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber) {
        testRunStarted(runName, testCount, attemptNumber, System.currentTimeMillis());
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber, long startTime) {
        writeToFile(
                String.format(
                        "    [run %s (testCount: %s,attempt: %s) started]\n",
                        runName, testCount, attemptNumber));
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        writeToFile(
                String.format(
                        "        [run failed with %s|%s|%s]\n",
                        failure.getErrorMessage(),
                        failure.getFailureStatus(),
                        failure.getErrorIdentifier()));
    }

    @Override
    public void testRunFailed(String errorMessage) {
        writeToFile(String.format("        [run failed with %s]\n", errorMessage));
    }

    @Override
    public void testRunEnded(long elapsedTimeMillis, HashMap<String, Metric> runMetrics) {
        writeToFile("    [run ended]\n");
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        mTestCaseStatus = TestStatus.PASSED;
    }

    @Override
    public void testIgnored(TestDescription test) {
        mTestCaseStatus = TestStatus.IGNORED;
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        mTestCaseStatus = TestStatus.FAILURE;
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        mTestCaseStatus = TestStatus.FAILURE;
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        mTestCaseStatus = TestStatus.ASSUMPTION_FAILURE;
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        mTestCaseStatus = TestStatus.ASSUMPTION_FAILURE;
    }

    @Override
    public void testSkipped(TestDescription test, SkipReason reason) {
        mTestCaseStatus = TestStatus.SKIPPED;
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        writeToFile(String.format("      - test: %s (status=%s)\n", test, mTestCaseStatus));
        mTestCaseStatus = null;
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        String extra = "";
        if (mTestCaseStatus != null) {
            extra = "test";
        }
        writeToFile(
                String.format("[   %s log: %s | path: %s]\n", extra, dataName, logFile.getPath()));
    }

    private void writeToFile(String text) {
        if (mLog == null || !mLog.exists()) {
            return;
        }
        try {
            FileUtil.writeToFile(text, mLog, true);
        } catch (IOException e) {
            CLog.e(e);
        }
    }
}
