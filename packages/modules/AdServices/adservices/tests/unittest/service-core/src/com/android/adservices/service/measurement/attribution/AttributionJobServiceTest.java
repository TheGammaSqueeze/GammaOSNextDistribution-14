/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.common.JobServiceTestHelper.createJobFinishedCallback;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.JobServiceCallback;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.reporting.DebugReportingJobService;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link AttributionJobService
 */
public class AttributionJobServiceTest {
    private static final long WAIT_IN_MILLIS = 1_000L;
    private static final long JOB_DELAY_MS = TimeUnit.MINUTES.toMillis(2);
    private static final int MEASUREMENT_ATTRIBUTION_JOB_ID =
            MEASUREMENT_ATTRIBUTION_JOB.getJobId();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private AttributionJobService mSpyService;

    private Flags mMockFlags;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AttributionJobService.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(DebugReportingJobService.class)
                    .spyStatic(FlagsFactory.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new AttributionJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);

        mMockFlags = mock(Flags.class);
        mSpyLogger = getSpiedAdServicesJobServiceLogger(CONTEXT, mMockFlags);
        when(mMockFlags.getMeasurementAttributionJobTriggeringDelayMs()).thenReturn(JOB_DELAY_MS);
    }

    @Test
    public void onStartJob_killSwitchOn_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOn();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOff();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
                    JobServiceLoggingCallback onStartJobCallback =
                            syncPersistJobExecutionData(mSpyLogger);
                    JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOff();

                    verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    JobServiceCallback callback = createJobFinishedCallback(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    callback.assertJobFinished();

                    assertTrue(result);

                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .jobFinished(any(), anyBoolean());

                    JobServiceCallback secondCallback = createJobFinishedCallback(mSpyService);
                    // Execute
                    result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    secondCallback.assertJobFinished();

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    verify(mMockDatastoreManager, times(2)).runInTransactionWithResult(any());
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                            never());
                    verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    adServicesExtendedMockitoRule.mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_shouldDisableJobTrue();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    adServicesExtendedMockitoRule.mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void testRescheduling_failureWhileProcessingRecords_dontRescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Failure while processing records
                    ExtendedMockito.doReturn(AttributionJobHandler.ProcessingResult.FAILURE)
                            .when(mSpyService)
                            .acquireLockAndProcessPendingAttributions();

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, reschedule job with jobFinished
                    assertTrue(result);
                    // recordJobFinished
                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ATTRIBUTION_JOB_ID),
                                    /* isSuccessful= */ eq(false),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .jobFinished(any(), eq(/* wantsReschedule= */ true));
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                            never());
                    verify(mSpyService, never()).scheduleImmediately(any());
                });
    }

    @Test
    public void testRescheduling_successNoMoreRecordsToProcess_rescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Successful processing
                    ExtendedMockito.doReturn(
                                    AttributionJobHandler.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .acquireLockAndProcessPendingAttributions();

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, do not reschedule with jobFinished, but reschedule manually
                    assertTrue(result);

                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ATTRIBUTION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(false));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), eq(true)),
                            timeout(WAIT_IN_MILLIS).times(1));
                    verify(mSpyService, never()).scheduleImmediately(any());
                });
    }

    @Test
    public void testRescheduling_hasMoreRecordsToProcess_rescheduleImmediately() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Pending records
                    ExtendedMockito.doReturn(
                                    AttributionJobHandler.ProcessingResult
                                            .SUCCESS_WITH_PENDING_RECORDS)
                            .when(mSpyService)
                            .acquireLockAndProcessPendingAttributions();

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, do not reschedule with jobFinished, but reschedule immediately
                    assertTrue(result);
                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ATTRIBUTION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                            never());
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .scheduleImmediately(any());
                });
    }

    @Test
    public void testScheduleImmediately_killSwitchOff_rescheduleImmediately() {
        // Setup
        disableKillSwitch();
        JobScheduler jobScheduler = mock(JobScheduler.class);
        Context context = mock(Context.class);
        doReturn(jobScheduler).when(context).getSystemService(eq(JobScheduler.class));

        // Execute
        mSpyService.scheduleImmediately(context);

        // Validate jobInfo params to run immediately
        ArgumentCaptor<JobInfo> captorJobInfo = ArgumentCaptor.forClass(JobInfo.class);
        verify(jobScheduler, times(1)).schedule(captorJobInfo.capture());
        JobInfo jobInfo = captorJobInfo.getValue();
        assertNotNull(jobInfo);
        assertNull(jobInfo.getTriggerContentUris());
        assertEquals(-1, jobInfo.getTriggerContentUpdateDelay());
    }

    @Test
    public void testScheduleImmediately_killSwitchOn_dontReschedule() {
        // Setup
        enableKillSwitch();
        JobScheduler jobScheduler = mock(JobScheduler.class);
        Context context = mock(Context.class);
        doReturn(jobScheduler).when(context).getSystemService(eq(JobScheduler.class));

        // Execute
        mSpyService.scheduleImmediately(context);

        // Validate, job did not schedule
        verify(jobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_sameJobInfoDontForceSchedule_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ATTRIBUTION_JOB_ID,
                                            new ComponentName(
                                                    mockContext, AttributionJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    TriggerContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    .setTriggerContentUpdateDelay(JOB_DELAY_MS)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfoDontForceSchedule_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ATTRIBUTION_JOB_ID,
                                            new ComponentName(
                                                    mockContext, AttributionJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    TriggerContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    // Difference
                                    .setTriggerContentUpdateDelay(JOB_DELAY_MS + 1)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(() -> AttributionJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    // Mock the JobScheduler to have no pending job.
                    doReturn(null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    Context context = spy(ApplicationProvider.getApplicationContext());
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
                    doReturn(jobScheduler).when(context).getSystemService(JobScheduler.class);
                    doReturn(null)
                            .when(jobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(() -> AttributionJobService.schedule(any(), any()));
                    AttributionJobService.scheduleIfNeeded(context, true);

                    // Validate
                    verify(jobScheduler, times(1)).schedule(captor.capture());
                    assertNotNull(captor.getValue());
                    assertFalse(captor.getValue().isPersisted());
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .acquireLockAndProcessPendingAttributions();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).onStartJob(any());
                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        JobServiceCallback callback = createJobFinishedCallback(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();

        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();

        ExtendedMockito.doNothing()
                .when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));

        JobServiceCallback callback = createJobFinishedCallback(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        callback.assertJobFinished();

        verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
        verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        JobServiceCallback callback = createJobFinishedCallback(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()), never());
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        ExtendedMockito.doNothing().when(() -> AttributionJobService.schedule(any(), any()));
        ExtendedMockito.doNothing()
                .when(() -> DebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        adServicesExtendedMockitoRule.mockGetFlags(mMockFlags);
        when(mMockFlags.getMeasurementJobAttributionKillSwitch()).thenReturn(value);
    }

    private CountDownLatch createCountDownLatch() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(i -> countDown(countDownLatch)).when(mSpyService).jobFinished(any(), anyBoolean());
        return countDownLatch;
    }

    private Object countDown(CountDownLatch countDownLatch) {
        countDownLatch.countDown();
        return null;
    }
}
