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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
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
// the BackgroundKeyFetchJobService, and adding them is non-trivial.
@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
@MockStatic(ConsentManager.class)
@SpyStatic(BackgroundKeyFetchJobService.class)
@SpyStatic(BackgroundKeyFetchWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class BackgroundKeyFetchJobServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final int FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID =
            FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private final BackgroundKeyFetchJobService mBgFJobServiceSpy =
            new BackgroundKeyFetchJobService();

    private final Flags mFlagsWithEnabledBgFGaUxDisabled =
            new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxDisabled();
    private final Flags mFlagsWithDisabledBgF =
            new BackgroundKeyFetchJobServiceTest.FlagsWithDisabledBgF();
    private final Flags mFlagsWithAdSelectionDataKillSwitchOn =
            new BackgroundKeyFetchJobServiceTest.FlagsWithKillSwitchOn();
    private final Flags mFlagsWithAdSelectionDataKillSwitchOff =
            new BackgroundKeyFetchJobServiceTest.FlagsWithKillSwitchOff();
    @Mock private BackgroundKeyFetchWorker mBgFWorkerMock;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled() throws Exception {
        extendedMockito.mockGetFlags(mFlagsWithEnabledBgFGaUxDisabled);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobAdSelectionDataKillSwitchOn() throws Exception {
        extendedMockito.mockGetFlags(mFlagsWithAdSelectionDataKillSwitchOn);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobAdSelectionDataKillSwitchOff() throws Exception {
        extendedMockito.mockGetFlags(mFlagsWithAdSelectionDataKillSwitchOff);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
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

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccessdd() throws Exception {
        Flags flagsWithEnabledBgFGaUxDisabledWithoutLogging =
                new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxEnabledWithoutLogging();
        extendedMockito.mockGetFlags(flagsWithEnabledBgFGaUxDisabledWithoutLogging);
        AdServicesJobServiceLogger spyLogger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithEnabledBgFGaUxDisabledWithoutLogging);

        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));

        verifyLoggingNotHappened(spyLogger);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled() throws Exception {
        Flags flagsWithEnabledBgFGaUxDisabled =
                new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxDisabled();
        extendedMockito.mockGetFlags(flagsWithEnabledBgFGaUxDisabled);

        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);
        extendedMockito.mockGetFlags(mFlagsWithEnabledBgFGaUxDisabled);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStopJobCallsStopWork() {
        extendedMockito.mockGetFlags(mMockFlags);

        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doNothing().when(mBgFWorkerMock).stopWork();

        assertTrue(mBgFJobServiceSpy.onStopJob(mJobParametersMock));

        verify(mBgFWorkerMock).stopWork();
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundKeyFetchJobService.scheduleIfNeeded(sContext, mFlagsWithDisabledBgF, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> BackgroundKeyFetchJobService.schedule(any(), any()));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> BackgroundKeyFetchJobService.schedule(any(), any()));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                sContext, mFlagsWithEnabledBgFGaUxDisabled, true);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        BackgroundKeyFetchJobService.schedule(sContext, mFlagsWithDisabledBgF);

        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue() throws Exception {
        // Logging killswitch is on.
        extendedMockito.mockGetFlags(mMockFlags);

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
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(sContext, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    private static class FlagsWithEnabledBgFGaUxDisabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
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

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithEnabledBgFGaUxEnabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
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

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
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

    private static class FlagsWithDisabledBgF implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return false;
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOn implements Flags {

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return true;
        }

        // For testing the corner case where the BgF is enabled but overall Ad Selection Data
        // kill switch is on
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }
}
