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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.TimeUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Listeners that allows to check the execution time of a given test case and fail it if it goes
 * over a given timeout.
 *
 * <p>Note that this enforcer doesn't interrupt the tests, but will make them fail.
 */
public final class TestTimeoutEnforcer extends ResultForwarder {
    // The option name & description we want to share across class that uses the enforcer.
    public static final String TEST_CASE_TIMEOUT_OPTION = "test-case-timeout";
    public static final String TEST_CASE_TIMEOUT_DESCRIPTION =
            "The timeout that will be applied to each test case of the run.";

    // Timeout limit to enforce on test cases. 0L means nothing will be enforced.
    private long mPerTestCaseTimeoutMs = 0L;

    private Map<TestDescription, Long> mTrackingStartTime = new HashMap<>();

    /**
     * Create the {@link TestTimeoutEnforcer} with the given timeout to enforce.
     *
     * @param perTestCaseTimeout The value of the timeout.
     * @param unit The {@link TimeUnit} of the perTestCaseTimeout.
     * @param listeners The {@link ITestInvocationListener} to forward to.
     */
    public TestTimeoutEnforcer(
            long perTestCaseTimeout, TimeUnit unit, ITestInvocationListener... listeners) {
        super(listeners);
        mPerTestCaseTimeoutMs = unit.toMillis(perTestCaseTimeout);
    }

    /**
     * Create the {@link TestTimeoutEnforcer} with the given timeout to enforce.
     *
     * @param perTestCaseTimeout The value of the timeout.
     * @param unit The {@link TimeUnit} of the perTestCaseTimeout.
     * @param listeners The {@link ITestInvocationListener} to forward to.
     */
    public TestTimeoutEnforcer(
            long perTestCaseTimeout, TimeUnit unit, List<ITestInvocationListener> listeners) {
        super(listeners);
        mPerTestCaseTimeoutMs = unit.toMillis(perTestCaseTimeout);
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        super.testStarted(test, startTime);
        if (mPerTestCaseTimeoutMs != 0L) {
            mTrackingStartTime.put(test, startTime);
        }
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        super.testFailed(test, trace);
        // If the test already fails, don't consider for timeout.
        mTrackingStartTime.remove(test);
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        super.testFailed(test, failure);
        // If the test already fails, don't consider for timeout.
        mTrackingStartTime.remove(test);
    }

    @Override
    public void testIgnored(TestDescription test) {
        super.testIgnored(test);
        // If the test is ignored, don't consider for timeout.
        mTrackingStartTime.remove(test);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        super.testAssumptionFailure(test, failure);
        // If the test is assumption failure, don't consider for timeout.
        mTrackingStartTime.remove(test);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        super.testAssumptionFailure(test, trace);
        // If the test is assumption failure, don't consider for timeout.
        mTrackingStartTime.remove(test);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        try {
            if (mPerTestCaseTimeoutMs == 0L) {
                return;
            }
            Long startTime = mTrackingStartTime.get(test);
            if (startTime == null) {
                return;
            }
            long elapsedTime = endTime - startTime;
            if (elapsedTime > mPerTestCaseTimeoutMs) {
                FailureDescription failure =
                        FailureDescription.create(
                                String.format(
                                        "%s took %s while timeout is %s",
                                        test,
                                        TimeUtil.formatElapsedTime(elapsedTime),
                                        TimeUtil.formatElapsedTime(mPerTestCaseTimeoutMs)),
                                FailureStatus.TIMED_OUT);
                super.testFailed(test, failure);
            }
        } catch (RuntimeException ignore) {
            // Don't allow our extra logic to prevent testEnded to be called.
            CLog.e("Exception while attempting to apply timeout.");
            CLog.e(ignore);
        } finally {
            mTrackingStartTime.remove(test);
            super.testEnded(test, endTime, testMetrics);
        }
    }
}
