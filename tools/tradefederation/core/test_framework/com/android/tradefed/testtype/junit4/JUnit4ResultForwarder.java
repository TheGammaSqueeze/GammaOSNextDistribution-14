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
package com.android.tradefed.testtype.junit4;

import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.LogAnnotation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.MetricAnnotation;
import com.android.tradefed.testtype.MetricTestCase.LogHolder;
import com.android.tradefed.util.StreamUtil;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.MultipleFailureException;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Result forwarder from JUnit4 Runner.
 */
public class JUnit4ResultForwarder extends RunListener {

    private ITestInvocationListener mListener;
    private List<Throwable> mTestCaseFailures;
    private Description mRunDescription;
    private boolean mBeforeClass = true;
    private CloseableTraceScope mMethodTrace = null;

    private LogUploaderThread mLogUploaderThread;

    public JUnit4ResultForwarder(ITestInvocationListener listener) {
        mListener = listener;
        mTestCaseFailures = new ArrayList<>();
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        if (mLogUploaderThread != null) {
            mLogUploaderThread.cancel();
        }

        Description description = failure.getDescription();
        if (description.getMethodName() == null) {
            Throwable error = failure.getException();
            String message = error.getMessage();
            if (message == null) {
                if (error instanceof CarryInterruptedException) {
                    message = "Test Phase Timeout Reached.";
                } else {
                    message = "Exception with no error message";
                }
            }
            FailureDescription failureDesc =
                    FailureDescription.create(message).setFailureStatus(FailureStatus.TEST_FAILURE);
            if (error instanceof CarryDnaeError) {
                error = ((CarryDnaeError) error).getDeviceNotAvailableException();
            }
            failureDesc.setCause(error);
            if (error instanceof IHarnessException) {
                ErrorIdentifier id = ((IHarnessException) error).getErrorId();
                if (id != null) {
                    failureDesc.setFailureStatus(id.status());
                }
                failureDesc.setErrorIdentifier(((IHarnessException) error).getErrorId());
                failureDesc.setOrigin(((IHarnessException) error).getOrigin());
            } else if (error instanceof CarryInterruptedException) {
                failureDesc.setErrorIdentifier(TestErrorIdentifier.TEST_PHASE_TIMED_OUT);
            }
            mListener.testRunFailed(failureDesc);
            // If the exception is ours thrown from before, rethrow it
            if (error instanceof CarryDnaeError) {
                throw ((CarryDnaeError) error).getDeviceNotAvailableException();
            }
            return;
        }
        mTestCaseFailures.add(failure.getException());
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        mTestCaseFailures.add(failure.getException());
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        mRunDescription = description;
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        if (!mTestCaseFailures.isEmpty()) {
            String stack = StreamUtil.getStackTrace(mTestCaseFailures.get(0));
            if (mBeforeClass) {
                for (Description test : mRunDescription.getChildren()) {
                    TestDescription testid =
                            new TestDescription(
                                    test.getClassName(),
                                    test.getMethodName(),
                                    test.getAnnotations());
                    mListener.testStarted(testid);
                    mListener.testAssumptionFailure(testid, stack);
                    mListener.testEnded(testid, new HashMap<String, Metric>());
                }
            } else {
                // This would be an error in AfterClass, we have no good place to put results today
                // so report it as a failure for now.
                mListener.testRunFailed(stack);
            }
        }
    }

    @Override
    public void testStarted(Description description) throws Exception {
        mMethodTrace = new CloseableTraceScope(description.getMethodName());
        mBeforeClass = false;
        mTestCaseFailures.clear();
        TestDescription testid =
                new TestDescription(
                        description.getClassName(),
                        description.getMethodName(),
                        description.getAnnotations());
        mListener.testStarted(testid);

        mLogUploaderThread = new LogUploaderThread(description);
        mLogUploaderThread.setDaemon(true);
        mLogUploaderThread.start();
    }

    @Override
    public void testFinished(Description description) throws Exception {
        mLogUploaderThread.cancel();

        TestDescription testid =
                new TestDescription(
                        description.getClassName(),
                        description.getMethodName(),
                        description.getAnnotations());
        try {
            handleFailures(testid);
        } finally {
            mLogUploaderThread.join();
            // run last time to make sure all logs uploaded
            pollLogsAndUpload(description);

            // Explore the Description to see if we find any Annotation metrics carrier
            HashMap<String, Metric> metrics = new HashMap<>();
            for (Description child : description.getChildren()) {
                for (Annotation a : child.getAnnotations()) {
                    if (a instanceof MetricAnnotation) {
                        metrics.putAll(((MetricAnnotation) a).mMetrics);
                    }
                }
            }
            mListener.testEnded(testid, metrics);
            mTestCaseFailures.clear();
            if (mMethodTrace != null) {
                mMethodTrace.close();
                mMethodTrace = null;
            }
        }
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        TestDescription testid =
                new TestDescription(
                        description.getClassName(),
                        description.getMethodName(),
                        description.getAnnotations());
        // We complete the event life cycle since JUnit4 fireIgnored is not within fireTestStarted
        // and fireTestEnded.
        mListener.testStarted(testid);
        mListener.testIgnored(testid);
        mListener.testEnded(testid, new HashMap<String, Metric>());
    }

    /**
     * Handle all the failure received from the JUnit4 tests, if a single
     * AssumptionViolatedException is received then treat the test as assumption failure. Otherwise
     * treat everything else as failure.
     */
    private void handleFailures(TestDescription testid) {
        if (mTestCaseFailures.isEmpty()) {
            return;
        }
        if (mTestCaseFailures.size() == 1) {
            Throwable t = mTestCaseFailures.get(0);
            if (t instanceof AssumptionViolatedException) {
                mListener.testAssumptionFailure(testid, StreamUtil.getStackTrace(t));
            } else {
                mListener.testFailed(testid, StreamUtil.getStackTrace(t));
            }
        } else {
            MultipleFailureException multiException =
                    new MultipleFailureException(mTestCaseFailures);
            mListener.testFailed(testid, getMultiFailureStack(multiException));
        }
    }

    /**
     * Thread used to upload logs in between of testStarted and testFinished, in parallel to the
     * test actual run
     */
    private class LogUploaderThread extends Thread {
        private Description mDescription;
        private AtomicBoolean mIsCancelled = new AtomicBoolean(false);

        public LogUploaderThread(Description description) {
            mDescription = description;
        }

        @Override
        public void run() {
            while (!mIsCancelled.get()) {
                pollLogsAndUpload(mDescription);
            }
        }

        public void cancel() {
            mIsCancelled.set(true);
        }
    }

    private void pollLogsAndUpload(Description description) {
        for (Description child : description.getChildren()) {
            for (Annotation a : child.getAnnotations()) {
                if (a instanceof LogAnnotation) {
                    LinkedBlockingQueue<LogHolder> list = ((LogAnnotation) a).mLogs;
                    while (!list.isEmpty()) {
                        LogHolder log = list.poll();
                        // upload log
                        mListener.testLog(log.mDataName, log.mDataType, log.mDataStream);
                        StreamUtil.cancel(log.mDataStream);
                    }
                }
            }
        }
    }

    private String getMultiFailureStack(MultipleFailureException multiException) {
        StringBuilder sb =
                new StringBuilder(
                        String.format(
                                "MultipleFailureException, There were %d errors:",
                                multiException.getFailures().size()));
        for (Throwable e : multiException.getFailures()) {
            sb.append(String.format("\n  %s", StreamUtil.getStackTrace(e)));
        }
        return sb.toString();
    }
}
