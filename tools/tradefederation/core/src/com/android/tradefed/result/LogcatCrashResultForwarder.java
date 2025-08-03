/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.loganalysis.item.JavaCrashItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.item.MiscLogcatItem;
import com.android.loganalysis.item.NativeCrashItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Special listener: on failures (instrumentation process crashing) it will attempt to extract from
 * the logcat the crash and adds it to the failure message associated with the test.
 */
public class LogcatCrashResultForwarder extends ResultForwarder {

    /** Special error message from the instrumentation when something goes wrong on device side. */
    public static final String ERROR_MESSAGE = "Process crashed.";
    public static final String SYSTEM_CRASH_MESSAGE = "System has crashed.";
    public static final String INCOMPLETE_MESSAGE = "Test run failed to complete";
    public static final List<String> TIMEOUT_MESSAGES =
            ImmutableList.of(
                    "Failed to receive adb shell test output",
                    "TimeoutException when running tests",
                    "TestTimedOutException: test timed out after");

    public static final int MAX_NUMBER_CRASH = 3;

    private static final int MAX_CRASH_SIZE = 250000;
    private static final String MAX_CRASH_SIZE_MESSAGE = "\n<Truncated>";
    // Message from crash collector that reflect an issue
    private static final String FILTER_NOT_FOUND =
            "java.lang.IllegalArgumentException: testfile not found:";
    private static final String FILTER_NOT_READ =
            "java.lang.IllegalArgumentException: Could not read test file";

    private static final String LOW_MEMORY_KILLER_TAG = "lowmemorykiller";

    private Long mStartTime = null;
    private Long mLastStartTime = null;
    private ITestDevice mDevice;
    private LogcatItem mLogcatItem = null;
    private String mPackageName = null;

    public LogcatCrashResultForwarder(ITestDevice device, ITestInvocationListener... listeners) {
        super(listeners);
        mDevice = device;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        mStartTime = startTime;
        super.testStarted(test, startTime);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        testFailed(test, FailureDescription.create(trace));
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        if (FailureStatus.NOT_EXECUTED.equals(failure.getFailureStatus())) {
            super.testFailed(test, failure);
            return;
        }
        // If the test case was detected as crashing the instrumentation, we add the crash to it.
        String trace = extractCrashAndAddToMessage(failure.getErrorMessage(), mStartTime);
        if (trace.contains(LOW_MEMORY_KILLER_TAG)) {
            failure.setErrorIdentifier(DeviceErrorIdentifier.INSTRUMENTATION_LOWMEMORYKILLER);
        } else if (isCrash(failure.getErrorMessage())) {
            failure.setErrorIdentifier(DeviceErrorIdentifier.INSTRUMENTATION_CRASH);
        } else if (isTimeout(failure.getErrorMessage())) {
            failure.setErrorIdentifier(TestErrorIdentifier.INSTRUMENTATION_TIMED_OUT);
        }
        failure.setErrorMessage(trace);
        // Add metrics for assessing uncaught IntrumentationTest crash failures (test level).
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.TEST_CRASH_FAILURES, 1);
        if (failure.getFailureStatus() == null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.UNCAUGHT_TEST_CRASH_FAILURES, 1);
        }
        super.testFailed(test, failure);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        super.testEnded(test, endTime, testMetrics);
        mLastStartTime = mStartTime;
        mStartTime = null;
    }

    @Override
    public void testRunFailed(String errorMessage) {
        testRunFailed(FailureDescription.create(errorMessage, FailureStatus.TEST_FAILURE));
    }

    @Override
    public void testRunFailed(FailureDescription error) {
        // Also add the failure to the run failure if the testFailed generated it.
        // A Process crash would end the instrumentation, so a testRunFailed is probably going to
        // be raised for the same reason.
        String errorMessage = error.getErrorMessage();
        if (mLogcatItem != null) {
            errorMessage = addCrashesToString(mLogcatItem, errorMessage);
            mLogcatItem = null;
        } else {
            errorMessage = extractCrashAndAddToMessage(errorMessage, mLastStartTime);
        }

        if (isCrash(errorMessage)) {
            error.setErrorIdentifier(DeviceErrorIdentifier.INSTRUMENTATION_CRASH);
            // Special failure due to permission issue.
            if (errorMessage.contains(FILTER_NOT_FOUND) || errorMessage.contains(FILTER_NOT_READ)) {
                CLog.d("Detected a permission error with filters.");
                // First stop retrying, it won't work
                error.setRetriable(false);
                error.setErrorIdentifier(TestErrorIdentifier.TEST_FILTER_NEEDS_UPDATE);
                errorMessage = "See go/iae-testfile-not-found \n" + errorMessage;
            }
        }
        error.setErrorMessage(errorMessage.trim());
        // Add metrics for assessing uncaught IntrumentationTest crash failures.
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.CRASH_FAILURES, 1);
        if (error.getFailureStatus() == null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.UNCAUGHT_CRASH_FAILURES, 1);
        }
        super.testRunFailed(error);
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        mLastStartTime = null;
    }

    /** Attempt to extract the crash from the logcat if the test was seen as started. */
    private String extractCrashAndAddToMessage(String errorMessage, Long startTime) {
        if (startTime == null) {
            // If no tests in progress, look for a fixed window
            startTime = System.currentTimeMillis() - 60000;
        }
        if (isCrash(errorMessage) && startTime != null) {
            mLogcatItem = extractLogcat(mDevice, startTime);
            errorMessage = addCrashesToString(mLogcatItem, errorMessage);
        }
        return errorMessage;
    }

    private boolean isCrash(String errorMessage) {
        return errorMessage.contains(ERROR_MESSAGE)
                || errorMessage.contains(SYSTEM_CRASH_MESSAGE)
                || errorMessage.contains(INCOMPLETE_MESSAGE);
    }

    private boolean isTimeout(String errorMessage) {
        for (String timeoutMessage : TIMEOUT_MESSAGES) {
            if (errorMessage.contains(timeoutMessage)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Extract a formatted object from the logcat snippet.
     *
     * @param device The device from which to pull the logcat.
     * @param startTime The beginning time of the last tests.
     * @return A {@link LogcatItem} that contains the information inside the logcat.
     */
    private LogcatItem extractLogcat(ITestDevice device, long startTime) {
        if (!TestDeviceState.ONLINE.equals(device.getDeviceState())) {
            CLog.w(
                    "Device is in state '%s' skip attempt to extract crash.",
                    device.getDeviceState());
            return null;
        }
        try (InputStreamSource logSource = device.getLogcatSince(startTime)) {
            if (logSource == null) {
                return null;
            }
            if (logSource.size() == 0L) {
                return null;
            }
            LogcatParser parser = new LogcatParser();
            if (mPackageName != null) {
                parser.addPattern(
                        Pattern.compile(String.format("Kill '%s'.*", mPackageName)),
                        null,
                        LOW_MEMORY_KILLER_TAG,
                        LOW_MEMORY_KILLER_TAG);
            }
            LogcatItem result = null;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(logSource.createInputStream()))) {
                result = parser.parse(reader);
            }
            return result;
        } catch (IOException e) {
            CLog.e(e);
        }
        return null;
    }

    /** Append the crashes information to the failure message. */
    private String addCrashesToString(LogcatItem item, String errorMsg) {
        if (item == null) {
            return errorMsg;
        }
        List<String> javaCrashes = dedupJavaCrash(item.getJavaCrashes());
        // Invert to report the most recent one first.
        Collections.reverse(javaCrashes);
        int displayed = Math.min(javaCrashes.size(), MAX_NUMBER_CRASH);
        if (!javaCrashes.isEmpty()) {
            errorMsg =
                    String.format("%s\nJava Crash Messages sorted from most recent:\n", errorMsg);
            for (int i = 0; i < displayed; i++) {
                errorMsg =
                        String.format("%s%s\n", errorMsg, truncateLargeCrash(javaCrashes.get(i)));
            }
        }

        List<String> nativeCrashes = dedupNativeCrash(item.getNativeCrashes());
        // Invert to report the most recent one first.
        Collections.reverse(nativeCrashes);
        displayed = Math.min(nativeCrashes.size(), MAX_NUMBER_CRASH);
        if (!nativeCrashes.isEmpty()) {
            errorMsg =
                    String.format("%s\nNative Crash Messages sorted from most recent:\n", errorMsg);
            for (int i = 0; i < displayed; i++) {
                errorMsg = String.format("%s%s\n", errorMsg, nativeCrashes.get(i));
            }
        }

        List<MiscLogcatItem> lowMemKiller = item.getMiscEvents(LOW_MEMORY_KILLER_TAG);
        if (!lowMemKiller.isEmpty()) {
            errorMsg =
                    String.format(
                            "%s\nInstrumentation was killed by lowmemorykiller: %s",
                            errorMsg, lowMemKiller.get(0).getStack());
        }

        return errorMsg;
    }

    private String truncateLargeCrash(String stack) {
        if (stack.length() > MAX_CRASH_SIZE) {
            return new StringBuilder(stack.substring(0, MAX_CRASH_SIZE))
                    .append(MAX_CRASH_SIZE_MESSAGE)
                    .toString();
        }
        return stack;
    }

    /** Remove identical crash from the list of errors. */
    private List<String> dedupJavaCrash(List<JavaCrashItem> origList) {
        LinkedHashSet<String> dedupList = new LinkedHashSet<>();
        for (JavaCrashItem item : origList) {
            dedupList.add(String.format("%s\n%s", item.getMessage(), item.getStack()));
        }
        return new ArrayList<>(dedupList);
    }

    /** Remove identical crash from the list of errors. */
    private List<String> dedupNativeCrash(List<NativeCrashItem> origList) {
        LinkedHashSet<String> dedupList = new LinkedHashSet<>();
        for (NativeCrashItem item : origList) {
            dedupList.add(
                    String.format(
                            "fingerprint: %s\napp: %s\n%s",
                            item.getFingerprint(), item.getApp(), item.getStack()));
        }
        return new ArrayList<>(dedupList);
    }
}
