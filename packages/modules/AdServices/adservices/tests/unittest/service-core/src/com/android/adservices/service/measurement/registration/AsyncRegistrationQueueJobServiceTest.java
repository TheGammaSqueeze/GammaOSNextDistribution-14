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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.common.JobServiceTestHelper.createJobFinishedCallback;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
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
import java.util.concurrent.TimeUnit;

public class AsyncRegistrationQueueJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_ASYNC_REGISTRATION_JOB_ID =
            MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 5_000L;
    private static final long JOB_TRIGGER_MIN_DELAY_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long JOB_TRIGGER_MAX_DELAY_MS = TimeUnit.MINUTES.toMillis(5);
    private JobScheduler mMockJobScheduler;
    private AsyncRegistrationQueueJobService mSpyService;
    private DatastoreManager mMockDatastoreManager;
    private Flags mMockFlags;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AsyncRegistrationQueueJobService.class)
                    .spyStatic(AdServicesLoggerImpl.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new AsyncRegistrationQueueJobService());
        mMockJobScheduler = mock(JobScheduler.class);

        mMockFlags = mock(Flags.class);
        mSpyLogger = getSpiedAdServicesJobServiceLogger(CONTEXT, mMockFlags);
        when(mMockFlags.getMeasurementAsyncRegistrationQueueJobPersisted()).thenReturn(false);
        when(mMockFlags.getMeasurementAsyncRegistrationQueueJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
        when(mMockFlags.getMeasurementAsyncRegistrationJobTriggerMinDelayMs())
                .thenReturn(JOB_TRIGGER_MIN_DELAY_MS);
        when(mMockFlags.getMeasurementAsyncRegistrationJobTriggerMaxDelayMs())
                .thenReturn(JOB_TRIGGER_MAX_DELAY_MS);
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
                    doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    onStartJob_killSwitchOff();

                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS)).recordOnStartJob(anyInt());
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    mSpyService.onStartJob(mock(JobParameters.class));

                    // Verify before executing again to make sure the lock has been unlocked
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)),
                            timeout(WAIT_IN_MILLIS).atLeast(1));

                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate the job ran successfully twice
                    assertTrue(result);
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)),
                            timeout(WAIT_IN_MILLIS).atLeast(2));
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_shouldDisableJobTrue();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    mockGetFlags(mMockFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void testRescheduling_threadInterruptedWhileProcessingRecords_dontRescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Failure while processing records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .THREAD_INTERRUPTED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, reschedule job with jobFinished
                    assertTrue(result);
                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(false),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .jobFinished(any(), eq(/* wantsReschedule= */ true));
                    verify(mSpyService, never()).scheduleImmediately(any());
                    verify(mMockJobScheduler, never()).schedule(any());
                });
    }

    @Test
    public void testRescheduling_successNoMoreRecordsToProcess_rescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Successful processing
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, do not reschedule with jobFinished, but reschedule manually
                    assertTrue(result);
                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(false));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)),
                            timeout(WAIT_IN_MILLIS).atLeast(1));
                    verify(mSpyService, never()).scheduleImmediately(any());
                });
    }

    @Test
    public void testRescheduling_hasMoreRecordsToProcess_rescheduleImmediately() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Pending records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_WITH_PENDING_RECORDS)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate, do not reschedule with jobFinished, but reschedule immediately
                    assertTrue(result);
                    verify(mSpyLogger, timeout(WAIT_IN_MILLIS))
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
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
        verify(jobScheduler, timeout(WAIT_IN_MILLIS).times(1)).schedule(captorJobInfo.capture());
        JobInfo jobInfo = captorJobInfo.getValue();
        assertNotNull(jobInfo);
        assertNull(jobInfo.getTriggerContentUris());
        assertEquals(-1, jobInfo.getTriggerContentUpdateDelay());
        assertEquals(-1, jobInfo.getTriggerContentMaxDelay());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, jobInfo.getNetworkType());
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
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                    verify(mMockJobScheduler, never()).schedule(any());
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    .setTriggerContentUpdateDelay(JOB_TRIGGER_MIN_DELAY_MS)
                                    .setTriggerContentMaxDelay(JOB_TRIGGER_MAX_DELAY_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfo_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    // different
                                    .setTriggerContentUpdateDelay(JOB_TRIGGER_MIN_DELAY_MS - 1)
                                    .setTriggerContentMaxDelay(JOB_TRIGGER_MAX_DELAY_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            timeout(WAIT_IN_MILLIS));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            atLeast(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            timeout(WAIT_IN_MILLIS).atLeast(1));
                    verify(mMockJobScheduler, timeout(WAIT_IN_MILLIS).times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_minAndMaxDelayModified_scheduleAccordingly() throws Exception {
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
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            atLeast(1));
                    ArgumentCaptor<JobInfo> jobInfoArgumentCaptor =
                            ArgumentCaptor.forClass(JobInfo.class);
                    verify(mMockJobScheduler, timeout(WAIT_IN_MILLIS).times(1))
                            .schedule(jobInfoArgumentCaptor.capture());
                    JobInfo job = jobInfoArgumentCaptor.getValue();
                    assertEquals(JOB_TRIGGER_MIN_DELAY_MS, job.getTriggerContentUpdateDelay());
                    assertEquals(JOB_TRIGGER_MAX_DELAY_MS, job.getTriggerContentMaxDelay());
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .processAsyncRecords();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    Thread.sleep(WAIT_IN_MILLIS);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(0))
                            .jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
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
        verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, timeout(WAIT_IN_MILLIS).times(1))
                .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);
        verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, timeout(WAIT_IN_MILLIS).times(1))
                .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        ExtendedMockito.doNothing().when(() -> mSpyService.scheduleIfNeeded(any(), anyBoolean()));

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);
        ExtendedMockito.verify(
                () -> mSpyService.scheduleIfNeeded(any(), eq(true)),
                timeout(WAIT_IN_MILLIS).atLeast(1));
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        Context context = Mockito.mock(Context.class);
        doReturn(CONTEXT.getPackageName()).when(context).getPackageName();
        doReturn(CONTEXT.getPackageManager()).when(context).getPackageManager();
        // Setup mock everything in job
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(context).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                .when(() -> EnrollmentDao.getInstance(any()));
        ExtendedMockito.doReturn(mock(AdServicesLoggerImpl.class))
                .when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
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
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value).when(mMockFlags).getAsyncRegistrationJobQueueKillSwitch();
    }
}
