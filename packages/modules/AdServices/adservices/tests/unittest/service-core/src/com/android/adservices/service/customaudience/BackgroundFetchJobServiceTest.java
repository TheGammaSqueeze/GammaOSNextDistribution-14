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

package com.android.adservices.service.customaudience;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStartJobLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// The actual scheduling of the job needs to be mocked out because the test application does
// not have the required permissions to schedule the job with the constraints requested by
// the BackgroundFetchJobService, and adding them is non-trivial.
@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
@MockStatic(ConsentManager.class)
@SpyStatic(BackgroundFetchJobService.class)
@SpyStatic(BackgroundFetchWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class BackgroundFetchJobServiceTest extends AdServicesExtendedMockitoTestCase {
    private static final int FLEDGE_BACKGROUND_FETCH_JOB_ID =
            FLEDGE_BACKGROUND_FETCH_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private final BackgroundFetchJobService mBgFJobServiceSpy = new BackgroundFetchJobService();

    private final Flags mFlagsWithEnabledBgFGaUxDisabled = new FlagsWithEnabledBgFGaUxDisabled();
    private final Flags mFlagsWithDisabledBgF = new FlagsWithDisabledBgF();
    private final Flags mFlagsWithCustomAudienceServiceKillSwitchOn = new FlagsWithKillSwitchOn();
    private final Flags mFlagsWithCustomAudienceServiceKillSwitchOff = new FlagsWithKillSwitchOff();

    @Mock private BackgroundFetchWorker mBgFWorkerMock;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private PackageManager mPackageManagerMock;

    @Before
    public void setup() {
        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobFlagDisabled_withoutLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags mFlagsWithDisabledBgFWithoutLogging =
                new FlagsWithDisabledBgF() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }
                };
        doReturn(mFlagsWithDisabledBgFWithoutLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, mFlagsWithDisabledBgFWithoutLogging);

        testOnStartJobFlagDisabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags mFlagsWithDisabledBgFWithLogging =
                new FlagsWithDisabledBgF() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }
                };
        doReturn(mFlagsWithDisabledBgFWithLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, mFlagsWithDisabledBgFWithLogging);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobFlagDisabled();

        verifyOnStartJobLogged(logger, onStartJobCallback);
        onJobDoneCallback.assertLoggingFinished();
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withoutLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        FlagsWithEnabledBgFGaUxEnabledWithoutLogging flags =
                new FlagsWithEnabledBgFGaUxEnabledWithoutLogging();
        doReturn(flags).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, flags);

        testOnStartJobConsentRevokedGaUxEnabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        FlagsWithEnabledBgFGaUxEnabledWithLogging flags =
                new FlagsWithEnabledBgFGaUxEnabledWithLogging();
        doReturn(flags).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, flags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobCustomAudienceKillSwitchOn()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithCustomAudienceServiceKillSwitchOn).when(FlagsFactory::getFlags);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobCustomAudienceKillSwitchOff()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithCustomAudienceServiceKillSwitchOff).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccess_withoutLogging()
            throws InterruptedException, ExecutionException, TimeoutException {
        Flags flagsWithEnabledBgFGaUxDisabledWithoutLogging =
                new FlagsWithEnabledBgFGaUxDisabledWithoutLogging();
        doReturn(flagsWithEnabledBgFGaUxDisabledWithoutLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithEnabledBgFGaUxDisabledWithoutLogging);

        testOnStartJobUpdateSuccess();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateSuccess_withLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags flagsWithEnabledBgFGaUxDisabledWithLogging =
                new FlagsWithEnabledBgFGaUxDisabledWithLogging();
        doReturn(flagsWithEnabledBgFGaUxDisabledWithLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithEnabledBgFGaUxDisabledWithLogging);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withoutLogging() throws InterruptedException {
        Flags flagsWithEnabledBgFGaUxDisabledWithoutLogging =
                new FlagsWithEnabledBgFGaUxDisabledWithoutLogging();
        doReturn(flagsWithEnabledBgFGaUxDisabledWithoutLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithEnabledBgFGaUxDisabledWithoutLogging);

        testOnStartJobUpdateTimeoutHandled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withLogging() throws InterruptedException {
        Flags flagsWithEnabledBgFGaUxDisabledWithLogging =
                new FlagsWithEnabledBgFGaUxDisabledWithLogging();
        doReturn(flagsWithEnabledBgFGaUxDisabledWithLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithEnabledBgFGaUxDisabledWithLogging);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mFlagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mFlagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStopJobCallsStopWork_withoutLogging() {
        Flags mockFlag = mock(Flags.class);
        mockGetFlags(mockFlag);
        mockBackgroundJobsLoggingKillSwitch(mockFlag, /* overrideValue= */ true);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);

        testOnStopJobCallsStopWork();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        Flags mockFlag = mock(Flags.class);
        mockGetFlags(mockFlag);
        mockBackgroundJobsLoggingKillSwitch(mockFlag, /* overrideValue= */ false);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStopJobCallsStopWork();

        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        doCallRealMethod()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundFetchJobService.scheduleIfNeeded(sContext, mFlagsWithDisabledBgF, false);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        doCallRealMethod()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> BackgroundFetchJobService.schedule(any(), any()));

        BackgroundFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, false);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, false);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> BackgroundFetchJobService.schedule(any(), any()));

        BackgroundFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, true);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        BackgroundFetchJobService.schedule(sContext, mFlagsWithDisabledBgF);

        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags mockFlag = mock(Flags.class);
        mockGetFlags(mockFlag);
        mockBackgroundJobsLoggingKillSwitch(mockFlag, /* overrideValue= */ true);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);

        testOnStartJobShouldDisableJobTrue();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags mockFlag = mock(Flags.class);
        mockGetFlags(mockFlag);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    private void testOnStartJobShouldDisableJobTrue()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobFlagDisabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobUpdateSuccess()
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobUpdateTimeoutHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStopJobCallsStopWork() {
        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doNothing().when(mBgFWorkerMock).stopWork();

        assertTrue(mBgFJobServiceSpy.onStopJob(mJobParametersMock));

        verify(mBgFWorkerMock).stopWork();
    }

    private static class FlagsWithEnabledBgFGaUxDisabled implements Flags {
        @Override
        public boolean getFledgeBackgroundFetchEnabled() {
            return true;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return false;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithEnabledBgFGaUxDisabledWithoutLogging
            extends FlagsWithEnabledBgFGaUxDisabled {
        @Override
        public boolean getBackgroundJobsLoggingKillSwitch() {
            return true;
        }
    }

    private static class FlagsWithEnabledBgFGaUxDisabledWithLogging
            extends FlagsWithEnabledBgFGaUxDisabled {
        @Override
        public boolean getBackgroundJobsLoggingKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithEnabledBgFGaUxEnabled implements Flags {
        @Override
        public boolean getFledgeBackgroundFetchEnabled() {
            return true;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithEnabledBgFGaUxEnabledWithoutLogging
            extends FlagsWithEnabledBgFGaUxEnabled {
        @Override
        public boolean getBackgroundJobsLoggingKillSwitch() {
            return true;
        }
    }

    private static class FlagsWithEnabledBgFGaUxEnabledWithLogging
            extends FlagsWithEnabledBgFGaUxEnabled {
        @Override
        public boolean getBackgroundJobsLoggingKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithDisabledBgF implements Flags {
        @Override
        public boolean getFledgeBackgroundFetchEnabled() {
            return false;
        }

        @Override
        public long getFledgeBackgroundFetchJobPeriodMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public long getFledgeBackgroundFetchJobFlexMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOn implements Flags {

        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return true;
        }

        // For testing the corner case where the BgF is enabled but overall Custom Audience Service
        // kill switch is on
        @Override
        public boolean getFledgeBackgroundFetchEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {

        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeBackgroundFetchEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }
}
