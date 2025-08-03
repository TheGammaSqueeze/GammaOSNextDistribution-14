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
package com.android.tradefed.testtype.bazel;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.result.retry.ISupportGranularResults;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract Forwarding Listener class which will forward all listener calls to a given delegate
 * listener.
 */
abstract class ForwardingTestListener implements ILogSaverListener, ISupportGranularResults {

    protected ForwardingTestListener() {}

    protected abstract ITestInvocationListener delegate();

    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        delegate().testLog(dataName, dataType, dataStream);
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        delegate().testRunStarted(runName, testCount);
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber) {
        delegate().testRunStarted(runName, testCount, attemptNumber);
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber, long startTime) {
        delegate().testRunStarted(runName, testCount, attemptNumber, startTime);
    }

    @Override
    public void testRunFailed(String errorMessage) {
        delegate().testRunFailed(errorMessage);
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        delegate().testRunFailed(failure);
    }

    @Override
    public void testRunEnded(long elapsedTimeMillis, Map<String, String> runMetrics) {
        delegate().testRunEnded(elapsedTimeMillis, runMetrics);
    }

    @Override
    public void testRunEnded(long elapsedTimeMillis, HashMap<String, Metric> runMetrics) {
        delegate().testRunEnded(elapsedTimeMillis, runMetrics);
    }

    @Override
    public void testRunStopped(long elapsedTime) {
        delegate().testRunStopped(elapsedTime);
    }

    @Override
    public void testStarted(TestDescription test) {
        delegate().testStarted(test);
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        delegate().testStarted(test, startTime);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        delegate().testFailed(test, trace);
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        delegate().testFailed(test, failure);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        delegate().testAssumptionFailure(test, trace);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        delegate().testAssumptionFailure(test, failure);
    }

    @Override
    public void testIgnored(TestDescription test) {
        delegate().testIgnored(test);
    }

    @Override
    public void testEnded(TestDescription test, Map<String, String> testMetrics) {
        delegate().testEnded(test, testMetrics);
    }

    @Override
    public void testEnded(TestDescription test, HashMap<String, Metric> testMetrics) {
        delegate().testEnded(test, testMetrics);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, Map<String, String> testMetrics) {
        delegate().testEnded(test, endTime, testMetrics);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        delegate().testEnded(test, endTime, testMetrics);
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        delegate().invocationStarted(context);
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        delegate().invocationEnded(elapsedTime);
    }

    @Override
    public void invocationFailed(Throwable cause) {
        delegate().invocationFailed(cause);
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        delegate().invocationFailed(failure);
    }

    @Override
    public TestSummary getSummary() {
        return delegate().getSummary();
    }

    @Override
    public void invocationInterrupted() {
        delegate().invocationInterrupted();
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        delegate().testModuleStarted(moduleContext);
    }

    @Override
    public void testModuleEnded() {
        delegate().testModuleEnded();
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {

        TestListeners.testLogSaved(delegate(), dataName, dataType, dataStream, logFile);
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        TestListeners.logAssociation(delegate(), dataName, logFile);
    }

    @Override
    public void setLogSaver(ILogSaver logSaver) {
        TestListeners.setLogSaver(delegate(), logSaver);
    }

    @Override
    public boolean supportGranularResults() {
        return TestListeners.supportGranularResults(delegate());
    }
}
