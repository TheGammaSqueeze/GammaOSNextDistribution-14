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
package com.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogcatCrashResultForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.ProcessInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal listener to Trade Federation for {@link InstrumentationTest}. It allows to collect extra
 * information needed for easier debugging.
 */
final class InstrumentationListener extends LogcatCrashResultForwarder {

    // Message from ddmlib InstrumentationResultParser for interrupted instrumentation.
    private static final String DDMLIB_INSTRU_FAILURE_MSG = "Test run failed to complete";
    // Message from ddmlib for ShellCommandUnresponsiveException
    private static final String DDMLIB_SHELL_UNRESPONSIVE =
            "Failed to receive adb shell test output within";
    private static final String JUNIT4_TIMEOUT =
            "org.junit.runners.model.TestTimedOutException: test timed out";
    // Message from ddmlib when there is a mismatch of test cases count
    private static final String DDMLIB_UNEXPECTED_COUNT = "Instrumentation reported numtests=";

    private Set<TestDescription> mTests = new HashSet<>();
    private Set<TestDescription> mDuplicateTests = new HashSet<>();
    private final Collection<TestDescription> mExpectedTests;
    private boolean mDisableDuplicateCheck = false;
    private boolean mReportUnexecutedTests = false;
    private ProcessInfo mSystemServerProcess = null;
    private String runLevelError = null;
    private TestDescription mLastTest = null;
    private TestDescription mLastStartedTest = null;

    private CloseableTraceScope mMethodScope = null;

    /**
     * @param device
     * @param listeners
     */
    public InstrumentationListener(
            ITestDevice device,
            Collection<TestDescription> expectedTests,
            ITestInvocationListener... listeners) {
        super(device, listeners);
        mExpectedTests = expectedTests;
    }

    public void addListener(ITestInvocationListener listener) {
        List<ITestInvocationListener> listeners = new ArrayList<>();
        listeners.addAll(getListeners());
        listeners.add(listener);
        setListeners(listeners);
    }

    /** Whether or not to disable the duplicate test method check. */
    public void setDisableDuplicateCheck(boolean disable) {
        mDisableDuplicateCheck = disable;
    }

    public void setOriginalSystemServer(ProcessInfo info) {
        mSystemServerProcess = info;
    }

    public void setReportUnexecutedTests(boolean enable) {
        mReportUnexecutedTests = enable;
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        runLevelError = null;
        // In case of crash, run will attempt to report with 0
        if (testCount == 0 && mExpectedTests != null && !mExpectedTests.isEmpty()) {
            CLog.e("Run reported 0 tests while we collected %s", mExpectedTests.size());
            super.testRunStarted(runName, mExpectedTests.size());
        } else {
            super.testRunStarted(runName, testCount);
        }
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        mMethodScope = new CloseableTraceScope(test.toString());
        super.testStarted(test, startTime);
        if (!mTests.add(test)) {
            mDuplicateTests.add(test);
        }
        mLastStartedTest = test;
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        String message = failure.getErrorMessage();
        if (message.startsWith(JUNIT4_TIMEOUT) || message.contains(DDMLIB_SHELL_UNRESPONSIVE)) {
            failure.setErrorIdentifier(TestErrorIdentifier.TEST_TIMEOUT).setRetriable(false);
        }
        super.testFailed(test, failure);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        mLastTest = test;
        mLastStartedTest = null;
        super.testEnded(test, endTime, testMetrics);
        if (mMethodScope != null) {
            mMethodScope.close();
            mMethodScope = null;
        }
    }

    @Override
    public void testRunFailed(FailureDescription error) {
        if (error.getErrorMessage().startsWith(DDMLIB_INSTRU_FAILURE_MSG)) {
            if (mExpectedTests != null) {
                Set<TestDescription> expected = new LinkedHashSet<>(mExpectedTests);
                expected.removeAll(mTests);
                String helpMessage = String.format("The following tests didn't run: %s", expected);
                error.setDebugHelpMessage(helpMessage);
            }
            error.setFailureStatus(FailureStatus.TEST_FAILURE);
            String wrapMessage = error.getErrorMessage();
            boolean restarted = false;
            if (mSystemServerProcess != null) {
                try {
                    restarted = getDevice().deviceSoftRestarted(mSystemServerProcess);
                } catch (DeviceNotAvailableException e) {
                    // Ignore
                }
                if (restarted) {
                    error.setFailureStatus(FailureStatus.SYSTEM_UNDER_TEST_CRASHED);
                    error.setErrorIdentifier(DeviceErrorIdentifier.DEVICE_CRASHED);
                    wrapMessage =
                            String.format(
                                    "Detected system_server restart causing instrumentation error:"
                                            + " %s",
                                    error.getErrorMessage());
                }
            }
            if (!restarted && !TestDeviceState.ONLINE.equals(getDevice().getDeviceState())) {
                error.setErrorIdentifier(DeviceErrorIdentifier.ADB_DISCONNECT);
                wrapMessage =
                        String.format(
                                "Detected device offline causing instrumentation error: %s",
                                error.getErrorMessage());
            }
            error.setErrorMessage(wrapMessage);
        } else if (error.getErrorMessage().startsWith(DDMLIB_SHELL_UNRESPONSIVE)) {
            String wrapMessage = "Instrumentation ran for longer than the configured timeout.";
            if (mLastStartedTest != null) {
                wrapMessage += String.format(" The last started but unfinished test was: %s.",
                    mLastStartedTest.toString());
            }
            CLog.w("ddmlib reported error: %s.", error.getErrorMessage());
            error.setErrorMessage(wrapMessage);
            error.setFailureStatus(FailureStatus.TIMED_OUT);
            error.setErrorIdentifier(TestErrorIdentifier.INSTRUMENTATION_TIMED_OUT);
        } else if (error.getErrorMessage().startsWith(DDMLIB_UNEXPECTED_COUNT)) {
            error.setFailureStatus(FailureStatus.TEST_FAILURE);
            error.setErrorIdentifier(InfraErrorIdentifier.EXPECTED_TESTS_MISMATCH);
        }
        // Use error before injecting the crashes
        runLevelError = error.getErrorMessage();
        super.testRunFailed(error);
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        if (!mDuplicateTests.isEmpty() && !mDisableDuplicateCheck) {
            FailureDescription error =
                    FailureDescription.create(
                            String.format(
                                    "The following tests ran more than once: %s. Check "
                                            + "your run configuration, you might be "
                                            + "including the same test class several "
                                            + "times.",
                                    mDuplicateTests));
            error.setFailureStatus(FailureStatus.TEST_FAILURE)
                    .setRetriable(false); // Don't retry duplicate tests.
            super.testRunFailed(error);
        } else if (mReportUnexecutedTests
                && mExpectedTests != null
                && mExpectedTests.size() > mTests.size()) {
            Set<TestDescription> missingTests = new LinkedHashSet<>(mExpectedTests);
            missingTests.removeAll(mTests);

            TestDescription lastTest = mLastTest;
            String lastExecutedLog = "";
            if (lastTest != null) {
                lastExecutedLog = "Last executed test was " + lastTest.toString() + ".";
            }
            if (runLevelError == null) {
                runLevelError = "Method was expected to run but didn't.";
            } else {
                runLevelError =
                        String.format("Run level error reported reason: '%s", runLevelError);
            }
            for (TestDescription miss : missingTests) {
                super.testStarted(miss);
                FailureDescription failure =
                        FailureDescription.create(
                                String.format(
                                        "Test did not run due to instrumentation issue. %s %s",
                                        lastExecutedLog, runLevelError),
                                FailureStatus.NOT_EXECUTED);
                super.testFailed(miss, failure);
                super.testEnded(miss, new HashMap<String, Metric>());
            }
        }
        runLevelError = null;
        super.testRunEnded(elapsedTime, runMetrics);
    }
}
