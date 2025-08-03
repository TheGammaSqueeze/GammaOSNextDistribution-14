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
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestSummary;

import java.util.HashMap;
import java.util.Map;

/** Null test listener. */
abstract class NullTestListener implements ILogSaverListener {

    protected NullTestListener() {}

    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        // Does nothing.
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        // Does nothing.
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber) {
        // Does nothing.
    }

    @Override
    public void testRunStarted(String runName, int testCount, int attemptNumber, long startTime) {
        // Does nothing.
    }

    @Override
    public void testRunFailed(String errorMessage) {
        // Does nothing.
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        // Does nothing.
    }

    @Override
    public void testRunEnded(long elapsedTimeMillis, Map<String, String> runMetrics) {
        // Does nothing.
    }

    @Override
    public void testRunEnded(long elapsedTimeMillis, HashMap<String, Metric> runMetrics) {
        // Does nothing.
    }

    @Override
    public void testRunStopped(long elapsedTime) {
        // Does nothing.
    }

    @Override
    public void testStarted(TestDescription test) {
        // Does nothing.
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        // Does nothing.
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        // Does nothing.
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        // Does nothing.
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        // Does nothing.
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        // Does nothing.
    }

    @Override
    public void testIgnored(TestDescription test) {
        // Does nothing.
    }

    @Override
    public void testEnded(TestDescription test, Map<String, String> testMetrics) {
        // Does nothing.
    }

    @Override
    public void testEnded(TestDescription test, HashMap<String, Metric> testMetrics) {
        // Does nothing.
    }

    @Override
    public void testEnded(TestDescription test, long endTime, Map<String, String> testMetrics) {
        // Does nothing.
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        // Does nothing.
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        // Does nothing.
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        // Does nothing.
    }

    @Override
    public void invocationFailed(Throwable cause) {
        // Does nothing.
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        // Does nothing.
    }

    @Override
    public TestSummary getSummary() {
        return null;
    }

    @Override
    public void invocationInterrupted() {
        // Does nothing.
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        // Does nothing.
    }

    @Override
    public void testModuleEnded() {
        // Does nothing.
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        // Does nothing.
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        // Does nothing.
    }

    @Override
    public void setLogSaver(ILogSaver logSaver) {
        // Does nothing.
    }
}
