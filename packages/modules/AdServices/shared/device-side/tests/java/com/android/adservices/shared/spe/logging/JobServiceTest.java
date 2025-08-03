/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.shared.spe.logging;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITHOUT_RETRY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITH_RETRY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__ONSTOP_CALLED_WITHOUT_RETRY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__ONSTOP_CALLED_WITH_RETRY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__PUBLIC_STOP_REASON__STOP_REASON_UNDEFINED;
import static com.android.adservices.shared.spe.JobServiceConstants.SHARED_PREFS_BACKGROUND_JOBS;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_EXECUTION_PERIOD;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_LATENCY;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_STOP_REASON;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.util.Clock;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unit test to test each flow for a job service using {@link JobServiceLogger} to log the metrics.
 * This test creates an example {@link JobService} to use logging methods in {@link
 * JobServiceLogger} and runs tests against this class.
 */
public final class JobServiceTest extends AdServicesExtendedMockitoTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    // Use an arbitrary job ID for testing. It won't have side effect to use production id as
    // the test doesn't actually schedule a job. This avoids complicated mocking logic.
    private static final int JOB_ID = 1;
    // Below are constant timestamps passed to mocked Clock as returned value of
    // clock.currentTimeMillis(). They are timestamps of consecutive events in sequence.
    // Setting consecutive two values have a different difference in order to avoid interfering the
    // result.
    private static final long START_TIMESTAMP_EXECUTION_1 = 100L;
    private static final long END_TIMESTAMP_EXECUTION_1 = 300L;
    private static final long START_TIMESTAMP_EXECUTION_2 = 600L;
    private static final long END_TIMESTAMP_EXECUTION_2 = 1000L;
    private static final long Latency_EXECUTION_1 =
            END_TIMESTAMP_EXECUTION_1 - START_TIMESTAMP_EXECUTION_1;
    private static final long Latency_EXECUTION_2 =
            END_TIMESTAMP_EXECUTION_2 - START_TIMESTAMP_EXECUTION_2;
    // First run, so executionPeriod is the default value
    private static final long PERIOD_EXECUTION_1 = UNAVAILABLE_JOB_EXECUTION_PERIOD;
    private static final long PERIOD_EXECUTION_2 =
            START_TIMESTAMP_EXECUTION_2 - START_TIMESTAMP_EXECUTION_1;
    private static final long BACKGROUND_EXECUTION_TIMEOUT = 500L;
    // The customized StopReason if onStopJob() is invoked.
    private static final int STOP_REASON = JobParameters.STOP_REASON_CANCELLED_BY_APP;
    private static final ImmutableMap<Integer, String> sJobIdToNameMap =
            new ImmutableMap.Builder<Integer, String>().put(JOB_ID, "job").build();
    private static final StatsdJobServiceLogger sMockStatsdLogger =
            mock(StatsdJobServiceLogger.class);
    private static final ModuleSharedFlags sMockFlags = mock(ModuleSharedFlags.class);
    private JobServiceLogger mLogger;

    @Mock private JobParameters mMockJobParameters;
    @Mock private Clock mMockClock;
    @Mock private AdServicesErrorLogger mMockErrorLogger;

    @Before
    public void setup() {
        mLogger =
                Mockito.spy(
                        new JobServiceLogger(
                                CONTEXT,
                                mMockClock,
                                sMockStatsdLogger,
                                mMockErrorLogger,
                                Executors.newCachedThreadPool(),
                                sJobIdToNameMap,
                                sMockFlags));

        // Clear shared preference
        CONTEXT.deleteSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS);

        when(sMockFlags.getBackgroundJobsLoggingEnabled()).thenReturn(true);
    }

    @After
    public void teardown() {
        // Clear shared preference
        CONTEXT.deleteSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS);
    }

    /** To test 1) success as first execution 2) success result code */
    @Test
    public void testJobExecutionLifeCycle_succeedThenSucceed() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock clock to return mocked currentTimeStamp in sequence.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        END_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // onStopJob() is not called, so stop reason is the unavailable value.
        int stopReason = UNAVAILABLE_STOP_REASON;
        // First Execution -- Succeed to execute
        jobService.setOnSuccessCallback(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();

        jobService.onStartJob(mMockJobParameters);

        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_1,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL,
                        stopReason);

        // Second Execution -- Succeed to execute
        jobService.setOnSuccessCallback(true);
        CountDownLatch logOperationCalledLatch2 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);

        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_2,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL,
                        stopReason);
    }

    /** To test 1) Failure as first execution 2) failure w/o retry. */
    @Test
    public void testJobExecutionLifeCycle_FailWithRetryThenFailWithoutRetry()
            throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock clock to return mocked currentTimeStamp in sequence.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        END_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // onStopJob() is not called, so stop reason is the unavailable value.
        int stopReason = UNAVAILABLE_STOP_REASON;
        // First Execution -- Fail to execute with retry
        jobService.setOnSuccessCallback(false);
        jobService.setShouldRetryOnJobFinished(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_1,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITH_RETRY,
                        stopReason);
        // Second Execution -- Fail to execute without retry
        jobService.setOnSuccessCallback(false);
        jobService.setShouldRetryOnJobFinished(false);
        CountDownLatch logOperationCalledLatch2 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_2,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITHOUT_RETRY,
                        stopReason);
    }

    /** To test 1) onStopJob() is called as first execution 2) onStopJob w/o retry. */
    @Test
    public void testJobExecutionLifeCycle_onStopWithRetryThenOnStopWithoutRetry()
            throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock the stop reason on test purpose. It's assigned by JobScheduler in production.
        when(mMockJobParameters.getStopReason()).thenReturn(STOP_REASON);
        // Mock clock to return mocked currentTimeStamp in sequence.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        END_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // First Execution -- onStopJob() is called with retry
        jobService.setShouldOnStopJobHappen(true);
        jobService.setShouldRetryOnStopJob(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        jobService.onStopJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();

        // StopReason was only introduced in Android S; prior to that it'll only log as Unknown.
        int expectedStopReason =
                SdkLevel.isAtLeastS()
                        ? STOP_REASON
                        : AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__PUBLIC_STOP_REASON__STOP_REASON_UNDEFINED;
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_1,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__ONSTOP_CALLED_WITH_RETRY,
                        expectedStopReason);
        // Second Execution -- onStopJob() is called without retry
        jobService.setShouldOnStopJobHappen(true);
        jobService.setShouldRetryOnStopJob(false);
        CountDownLatch logOperationCalledLatch2 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        jobService.onStopJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_2,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__ONSTOP_CALLED_WITHOUT_RETRY,
                        expectedStopReason);
    }

    /** To test the flow that execution is halted without calling onStopJob(). */
    @Test
    public void testJobExecutionLifeCycle_successThenHaltedByDevice() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock the stop reason on test purpose. It's assigned by JobScheduler in production.
        when(mMockJobParameters.getStopReason()).thenReturn(STOP_REASON);
        // Mock clock to return mocked currentTimeStamp in sequence.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        END_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // onStopJob() is not called, so stop reason is the unavailable value.
        int stopReason = UNAVAILABLE_STOP_REASON;
        // First Execution -- successful
        jobService.setOnSuccessCallback(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_1,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL,
                        stopReason);
        // Second Execution -- halted due to system/device issue.
        // Set the flag shouldOnStopJobHappen to true to stop executing onStartJob(), but do not
        // actually invoke onStopJob() to mimic the scenario.
        jobService.setShouldOnStopJobHappen(true);
        CountDownLatch logOperationCalledLatch2 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        // Logging doesn't happen because the execution is open-ended.
        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        // The logging happens on the next execution for execution halted due to device issues.
        //
        // Third Execution -- halted due to system/device issue.
        // This execution doesn't log itself but will log previous execution halted by device issue.
        jobService.setShouldOnStopJobHappen(true);
        CountDownLatch logOperationCalledLatch3 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch3.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        UNAVAILABLE_JOB_LATENCY,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        stopReason);
    }

    /**
     * To test execution halted by device issues as the first execution. And following execution can
     * log successfully
     */
    @Test
    public void testJobExecutionLifeCycle_haltedThenSuccess() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock the stop reason on test purpose. It's assigned by JobScheduler in production.
        when(mMockJobParameters.getStopReason()).thenReturn(STOP_REASON);
        // Mock clock to return mocked currentTimeStamp in sequence.
        // Note the first execution is open-ended, so it doesn't have an ending timestamp
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // onStopJob() is not called, so stop reason is the unavailable value.
        int stopReason = UNAVAILABLE_STOP_REASON;
        // First Execution -- halted due to system/device issue.
        // Set the flag shouldOnStopJobHappen to true to stop executing onStartJob(), but do not
        // actually invoke onStopJob() to mimic the scenario.
        jobService.setShouldOnStopJobHappen(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        // Logging doesn't happen because the execution is open-ended.
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        // The logging happens on the next execution.
        //
        // Second Execution -- Successful
        // This execution logs itself and also logs previous execution halted by device issue.
        jobService.setOnSuccessCallback(true);
        jobService.setShouldOnStopJobHappen(false);
        // It will log twice so create a CountDownLatch with event count = 2.
        CountDownLatch logOperationCalledLatch2 = new CountDownLatch(2);
        doAnswer(
                        (Answer<Object>)
                                invocation -> {
                                    // The method logAdServicesBackgroundJobsStats is called.
                                    invocation.callRealMethod();
                                    logOperationCalledLatch2.countDown();
                                    return null;
                                })
                .when(mLogger)
                .logJobStatsHelper(anyInt(), anyLong(), anyLong(), anyInt(), anyInt());
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT * 2, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        UNAVAILABLE_JOB_LATENCY,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        stopReason);
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_2,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL,
                        stopReason);
    }

    /** To test 1) skipping as first execution 2) skipping result code */
    @Test
    public void testJobExecutionLifeCycle_skipThenSkip() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Mock clock to return mocked currentTimeStamp in sequence.
        when(mMockClock.currentTimeMillis())
                .thenReturn(
                        START_TIMESTAMP_EXECUTION_1,
                        END_TIMESTAMP_EXECUTION_1,
                        START_TIMESTAMP_EXECUTION_2,
                        END_TIMESTAMP_EXECUTION_2);
        // onStopJob() is not called, so stop reason is the unavailable value.
        int stopReason = UNAVAILABLE_STOP_REASON;
        // First Execution -- skip to execute
        jobService.setShouldSkip(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_1,
                        PERIOD_EXECUTION_1,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                        stopReason);
        // Second Execution -- Succeed to execute
        jobService.setShouldSkip(true);
        CountDownLatch logOperationCalledLatch2 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch2.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID,
                        Latency_EXECUTION_2,
                        PERIOD_EXECUTION_2,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                        stopReason);
    }

    @Test
    public void testLoggingNotEnabled_successfulExecution() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Disable logging feature
        when(sMockFlags.getBackgroundJobsLoggingEnabled()).thenReturn(false);
        // First Execution -- Succeed to execute
        jobService.setOnSuccessCallback(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    @Test
    public void testLoggingNotEnabled_failedExecution() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Disable logging feature
        when(sMockFlags.getBackgroundJobsLoggingEnabled()).thenReturn(false);
        // First Execution -- Fail to execute with retry
        jobService.setOnSuccessCallback(false);
        jobService.setShouldRetryOnJobFinished(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    @Test
    public void testLoggingNotEnabled_executionCallingOnStop() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Disable logging feature
        when(sMockFlags.getBackgroundJobsLoggingEnabled()).thenReturn(false);
        // First Execution -- onStopJob() is called with retry
        jobService.setShouldOnStopJobHappen(true);
        jobService.setShouldRetryOnStopJob(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        jobService.onStopJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    @Test
    public void testLoggingNotEnabled_skipExecution() throws InterruptedException {
        TestJobService jobService = new TestJobService(mLogger);
        // Disable logging feature
        when(sMockFlags.getBackgroundJobsLoggingEnabled()).thenReturn(false);
        // First Execution -- onStopJob() is called with retry
        jobService.setShouldSkip(true);
        CountDownLatch logOperationCalledLatch1 = createCountDownLatchWithMockedOperation();
        jobService.onStartJob(mMockJobParameters);
        expect.that(
                        logOperationCalledLatch1.await(
                                BACKGROUND_EXECUTION_TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    // Since executions happen in background thread, create and use a countdownLatch to verify
    // the logging event has happened.
    private CountDownLatch createCountDownLatchWithMockedOperation() {
        CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        doAnswer(
                        (Answer<Object>)
                                invocation -> {
                                    // The method logAdServicesBackgroundJobsStats is called.
                                    invocation.callRealMethod();
                                    logOperationCalledLatch.countDown();
                                    return null;
                                })
                .when(mLogger)
                .logJobStatsHelper(anyInt(), anyLong(), anyLong(), anyInt(), anyInt());
        return logOperationCalledLatch;
    }

    // Helper class implemented as the pattern of Adservices background jobs. It's used to mimic
    // different scenarios of a JobService's lifecycle, in order to test the logging logic.
    //
    // Note this class needs to use app context since the test doesn't actually schedule this
    // JobService.
    static final class TestJobService extends JobService {
        private boolean mOnSuccessCallback;
        private boolean mShouldRetryOnStopJob;
        private boolean mShouldRetryOnJobFinished;
        private boolean mShouldOnStopJobHappen;
        private boolean mShouldSkip;
        private final JobServiceLogger mLogger;

        TestJobService(JobServiceLogger logger) {
            mLogger = logger;
        }

        @Override
        public boolean onStartJob(@NonNull JobParameters params) {
            mLogger.recordOnStartJob(JOB_ID);

            if (mShouldOnStopJobHappen) {
                return false;
            }

            if (mShouldSkip) {
                mLogger.recordJobSkipped(
                        JOB_ID,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
                return false;
            }

            FutureCallback<Void> callback =
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            mLogger.recordJobFinished(
                                    JOB_ID, /* isSuccessful */ true, mShouldRetryOnJobFinished);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            mLogger.recordJobFinished(
                                    JOB_ID, /* isSuccessful */ false, mShouldRetryOnJobFinished);
                        }
                    };
            ListenableFuture<Void> listenableFuture =
                    Futures.submit(
                            () -> {
                                // Throw an exception if it needs the callback to invoke
                                // onFailure().
                                if (!mOnSuccessCallback) {
                                    throw new RuntimeException();
                                }
                            },
                            CALLBACK_EXECUTOR);
            Futures.addCallback(listenableFuture, callback, CALLBACK_EXECUTOR);
            return true;
        }

        @Override
        public boolean onStopJob(@NonNull JobParameters params) {
            mLogger.recordOnStopJob(params, JOB_ID, mShouldRetryOnStopJob);
            // Log the execution stats if feature is enabled.
            return mShouldRetryOnStopJob;
        }

        void setOnSuccessCallback(boolean isSuccess) {
            mOnSuccessCallback = isSuccess;
        }

        void setShouldRetryOnStopJob(boolean shouldRetry) {
            mShouldRetryOnStopJob = shouldRetry;
        }

        void setShouldRetryOnJobFinished(boolean shouldRetry) {
            mShouldRetryOnJobFinished = shouldRetry;
        }

        void setShouldOnStopJobHappen(boolean shouldHappen) {
            mShouldOnStopJobHappen = shouldHappen;
        }

        void setShouldSkip(@SuppressWarnings("SameParameterValue") boolean shouldSkip) {
            mShouldSkip = shouldSkip;
        }
    }
}
