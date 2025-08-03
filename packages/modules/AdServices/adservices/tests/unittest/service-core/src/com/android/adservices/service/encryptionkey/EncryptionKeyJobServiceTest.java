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

package com.android.adservices.service.encryptionkey;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.service.Flags.ENCRYPTION_KEY_JOB_PERIOD_MS;
import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

/** Unit test for {@link EncryptionKeyJobService}. */
@SpyStatic(AdServicesConfig.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(EncryptionKeyDao.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class EncryptionKeyJobServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final int ENCRYPTION_KEY_JOB_ID = ENCRYPTION_KEY_PERIODIC_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1_000L;

    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mMockJobScheduler;
    @Mock private JobParameters mMockJobParameters;
    @Mock private JobInfo mMockJobInfo;

    private AdServicesJobServiceLogger mSpyLogger;
    private EncryptionKeyJobService mSpyService;

    @Before
    public void setUp() {
        extendedMockito.mockGetFlags(mMockFlags);
        mSpyService = spy(new EncryptionKeyJobService());

        mSpyLogger = getSpiedAdServicesJobServiceLogger(sContext, mMockFlags);

        setDefaultExpectations();
    }

    @Test
    public void testOnStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);

        // Validate
        assertThat(result).isFalse();
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testOnStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        doReturn(true).when(() -> EncryptionKeyJobService.scheduleIfNeeded(any(), anyBoolean()));

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);

        // Validate
        assertThat(result).isTrue();
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);

        // Validate
        assertThat(result).isFalse();
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(ENCRYPTION_KEY_JOB_ID);
        ExtendedMockito.verifyZeroInteractions(
                ExtendedMockito.staticMockMarker(FlagsFactory.class));
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        enableKillSwitch();

        doReturn(mMockJobScheduler).when(mMockContext).getSystemService(JobScheduler.class);
        doReturn(mMockJobInfo).when(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);

        // Execute
        assertThat(
                        EncryptionKeyJobService.scheduleIfNeeded(
                                mMockContext, /* forceSchedule= */ false))
                .isFalse();

        // Validate
        verify(() -> EncryptionKeyJobService.schedule(any(), any()), never());
        verify(mMockJobScheduler, never()).getPendingJob(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_sameJobInfoDontForceSchedule_dontSchedule()
            throws Exception {
        // Setup
        disableKillSwitch();
        doReturn(mMockJobScheduler).when(mSpyContext).getSystemService(JobScheduler.class);

        when(mMockFlags.getEncryptionKeyJobPeriodMs()).thenReturn(24 * 60 * 60 * 1000L);
        when(mMockFlags.getEncryptionKeyJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);

        JobInfo jobInfo =
                new JobInfo.Builder(
                                ENCRYPTION_KEY_JOB_ID,
                                new ComponentName(mSpyContext, EncryptionKeyJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPeriodic(24 * 60 * 60 * 1000L)
                        .build();
        doReturn(jobInfo).when(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);

        // Execute
        assertThat(
                        EncryptionKeyJobService.scheduleIfNeeded(
                                mSpyContext, /* forceSchedule= */ false))
                .isFalse();

        // Validate
        verify(() -> EncryptionKeyJobService.schedule(any(), any()), never());
        verify(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_diffJobInfoDontForceSchedule_schedule()
            throws Exception {
        // Setup
        disableKillSwitch();
        doReturn(mMockJobScheduler).when(mSpyContext).getSystemService(JobScheduler.class);
        JobInfo jobInfo =
                new JobInfo.Builder(
                                ENCRYPTION_KEY_JOB_ID,
                                new ComponentName(mSpyContext, EncryptionKeyJobService.class))
                        // Difference
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                        .build();
        doReturn(jobInfo).when(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);

        // Execute
        assertThat(
                        EncryptionKeyJobService.scheduleIfNeeded(
                                mSpyContext, /* forceSchedule= */ false))
                .isTrue();

        // Validate
        verify(() -> EncryptionKeyJobService.schedule(any(), any()));
        verify(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testScheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        // Setup
        disableKillSwitch();

        doReturn(mMockJobScheduler).when(mMockContext).getSystemService(JobScheduler.class);
        doReturn(mMockJobInfo).when(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);

        // Execute
        assertThat(
                        EncryptionKeyJobService.scheduleIfNeeded(
                                mMockContext, /* forceSchedule= */ true))
                .isTrue();

        // Validate
        verify(() -> EncryptionKeyJobService.schedule(any(), any()));
        verify(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void
            testScheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
                    throws Exception {
        // Setup
        disableKillSwitch();

        doReturn(mMockJobScheduler).when(mMockContext).getSystemService(JobScheduler.class);
        doReturn(/* noJobInfo= */ null)
                .when(mMockJobScheduler)
                .getPendingJob(ENCRYPTION_KEY_JOB_ID);

        // Execute
        assertThat(
                        EncryptionKeyJobService.scheduleIfNeeded(
                                mMockContext, /* forceSchedule= */ false))
                .isTrue();

        // Validate
        verify(() -> EncryptionKeyJobService.schedule(any(), any()));
        verify(mMockJobScheduler).getPendingJob(ENCRYPTION_KEY_JOB_ID);
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        disableKillSwitch();

        doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                .when(mSpyService)
                .fetchAndUpdateEncryptionKeys();
        mSpyService.onStartJob(mMockJobParameters);
        Thread.sleep(WAIT_IN_MILLIS);

        assertThat(mSpyService.getFutureForTesting()).isNotNull();

        boolean onStopJobResult = mSpyService.onStopJob(mMockJobParameters);
        verify(mSpyService, never()).jobFinished(any(), anyBoolean());
        assertThat(onStopJobResult).isTrue();
        assertThat(mSpyService.getFutureForTesting().isCancelled()).isTrue();
    }

    private void setDefaultExpectations() {
        // Setup mock everything in job
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(mMockContext).when(mSpyService).getApplicationContext();
        doReturn(mock(EnrollmentDao.class)).when(() -> EnrollmentDao.getInstance(any()));
        doReturn(mock(EncryptionKeyDao.class)).when(() -> EncryptionKeyDao.getInstance(any()));
        doNothing().when(() -> EncryptionKeyJobService.schedule(any(), any()));
        mockGetAdServicesJobServiceLogger(mSpyLogger);
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(value);
        when(mMockFlags.getEncryptionKeyJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);
        when(mMockFlags.getEncryptionKeyJobPeriodMs()).thenReturn(ENCRYPTION_KEY_JOB_PERIOD_MS);
    }
}
