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
package com.android.tradefed.testtype.suite;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.testtype.IRemoteTest;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Listeners that allows to check the execution time of a given test config and fail it if it goes
 * over a given timeout.
 *
 * <p>Note that this enforcer doesn't interrupt the tests, but will make them fail.
 */
public class RemoteTestTimeOutEnforcer implements ITestInvocationListener {

    // The option name & description we want to share across class that uses the enforcer.
    public static final String REMOTE_TEST_TIMEOUT_OPTION = "remote-test-timeout";
    public static final String REMOTE_TEST_TIMEOUT_DESCRIPTION =
            "The timeout that will be applied to each remote test object of the run.";

    private IRemoteTest mIRemoteTest;
    private Duration mTimeOut;
    private ModuleDefinition mModuleDefinition;
    private ModuleListener mListener;

    /**
     * Create the {@link RemoteTestTimeOutEnforcer} with the given timeout to enforce.
     *
     * @param listener The {@link ModuleListener} for each test run.
     * @param moduleDefinition The {@link ModuleDefinition} of the test module to be executed.
     * @param test The {@link IRemoteTest} to be executed.
     * @param timeOut The {@link Duration} of the time out per test run.
     */
    public RemoteTestTimeOutEnforcer(
            ModuleListener listener,
            ModuleDefinition moduleDefinition,
            IRemoteTest test,
            Duration timeOut) {
        mListener = listener;
        mIRemoteTest = test;
        mModuleDefinition = moduleDefinition;
        mTimeOut = timeOut;
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        if (elapsedTime >= mTimeOut.toMillis()) {
            String failureString = String.format(
                    "%s defined in %s took %s seconds while timeout is %s seconds",
                    mModuleDefinition.getId(),
                    mModuleDefinition.getModuleInvocationContext().getConfigurationDescriptor().
                            getMetaData(Integer.toString(mIRemoteTest.hashCode())),
                    TimeUnit.MILLISECONDS.toSeconds(elapsedTime),
                    mTimeOut.toSeconds());
            if (!mListener.hasLastAttemptFailed()) {
                FailureDescription failure = FailureDescription.create(
                        failureString, FailureStatus.TIMED_OUT).setRetriable(false);
                mListener.testRunFailed(failure);
            }
        }
    }
}
