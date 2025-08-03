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

package com.android.adservices.service.topics;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.JobServiceCallback;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

/** Unit tests for {@link com.android.adservices.service.topics.EpochJobService} */
@SuppressWarnings("ConstantConditions")
public class EpochJobServiceTest {
    private static final int TOPICS_EPOCH_JOB_ID = TOPICS_EPOCH_JOB.getJobId();
    private static final long EPOCH_JOB_PERIOD_MS = 10_000L;
    private static final long AWAIT_JOB_TIMEOUT_MS = 5_000L;
    private static final long EPOCH_JOB_FLEX_MS = 1_000L;
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    @Spy private EpochJobService mSpyEpochJobService;

    // Mock EpochManager and CacheManager as the methods called are tested in corresponding
    // unit test. In this test, only verify whether specific method is initiated.
    @Mock private EpochManager mMockEpochManager;
    @Mock private CacheManager mMockCacheManager;
    @Mock private BlockedTopicsManager mBlockedTopicsManager;
    @Mock private AppUpdateManager mMockAppUpdateManager;
    @Mock private JobParameters mMockJobParameters;
    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mMockJobScheduler;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(EpochJobService.class)
                    .spyStatic(TopicsWorker.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .spyStatic(ErrorLogUtil.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.WARN)
                    .build();

    @Before
    public void setup() {
        mockGetFlags(mMockFlags);

        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID));

        ExtendedMockito.doReturn(JOB_SCHEDULER)
                .when(mSpyEpochJobService)
                .getSystemService(JobScheduler.class);

        mSpyLogger = mockAdServicesJobServiceLogger(CONTEXT, mMockFlags);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJob_killSwitchOff_withoutLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_killSwitchOff();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJob_killSwitchOff_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        testOnStartJob_killSwitchOff();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJob_killSwitchOn_withoutLogging() {
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_killSwitchOn();

        verifyLoggingNotHappened(mSpyLogger);
        ExtendedMockito.verify(
                () -> {
                    ErrorLogUtil.e(
                            eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED),
                            eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS));
                });
    }

    @Test
    public void testOnStartJob_killSwitchOn_withLogging() throws InterruptedException {
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStartJob_killSwitchOn();

        verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
        ExtendedMockito.verify(
                () -> {
                    ErrorLogUtil.e(
                            eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED),
                            eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS));
                });
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_shouldDisableJobTrue();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    @FlakyTest(bugId = 298886083)
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled()
            throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

        testOnStartJob_shouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStopJob_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStopJob();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStopJob();

        verifyOnStopJobLogged(mSpyLogger, callback);
    }

    @Test
    public void testScheduleIfNeeded_Success() {
        ExtendedMockito.doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // Change the value of a parameter so that the second invocation of scheduleIfNeeded()
        // schedules the job.
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs() + 1)
                .when(mMockFlags)
                .getTopicsEpochJobFlexMs();
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
    }

    @Test
    public void testScheduleIfNeeded_forceRun() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ true)).isTrue();
    }

    @Test
    public void testScheduleIfNeeded_scheduledWithKillSwichOn() {
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNull();
        ExtendedMockito.verify(
                () -> {
                    ErrorLogUtil.e(
                            eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED),
                            eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() {
        final ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);

        EpochJobService.schedule(
                CONTEXT, mMockJobScheduler, EPOCH_JOB_PERIOD_MS, EPOCH_JOB_FLEX_MS);

        verify(mMockJobScheduler, times(1)).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    private void testOnStartJob_killSwitchOff() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        mMockFlags);

        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        // Mock static method TopicsWorker.getInstance, let it return the local topicsWorker
        // in order to get a test instance.
        ExtendedMockito.doReturn(topicsWorker)
                .when(() -> TopicsWorker.getInstance(any(Context.class)));

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                TOPICS_EPOCH_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        JobServiceCallback callback = createJobFinishedCallback(mSpyEpochJobService);

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyEpochJobService.onStartJob(mMockJobParameters)).isTrue();

        callback.assertJobFinished();
    }

    private void testOnStartJob_killSwitchOn() {
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                TOPICS_EPOCH_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(EPOCH_JOB_PERIOD_MS, EPOCH_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);

        // Even though the job shouldn't execute, allow some time for async job to execute
        // just in case before running any assertions.
        SystemClock.sleep(AWAIT_JOB_TIMEOUT_MS);

        assertNotNull(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyEpochJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID));

        verify(mSpyEpochJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));
    }

    private void testOnStartJob_shouldDisableJobTrue() {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                TOPICS_EPOCH_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(EPOCH_JOB_PERIOD_MS, EPOCH_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);

        // Even though the job shouldn't execute, allow some time for async job to execute
        // just in case before running any assertions.
        SystemClock.sleep(AWAIT_JOB_TIMEOUT_MS);

        assertNotNull(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyEpochJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID));

        verify(mSpyEpochJobService).jobFinished(mMockJobParameters, false);
        verify(mSpyLogger, never()).recordOnStartJob(anyInt());
    }

    private void testOnStopJob() {
        // Verify nothing throws
        mSpyEpochJobService.onStopJob(mMockJobParameters);
    }

    private JobServiceCallback createJobFinishedCallback(JobService jobService) {
        JobServiceCallback callback = new JobServiceCallback();

        doAnswer(
                        unusedInvocation -> {
                            callback.onJobFinished();
                            return null;
                        })
                .when(jobService)
                .jobFinished(any(), anyBoolean());

        return callback;
    }
}
