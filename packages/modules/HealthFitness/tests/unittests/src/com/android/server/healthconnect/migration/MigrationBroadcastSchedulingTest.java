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

package com.android.server.healthconnect.migration;

import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_ALLOWED;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS;

import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.MIGRATION_STATE_ALLOWED_COUNT_DEFAULT_FLAG_VALUE;
import static com.android.server.healthconnect.HealthConnectDeviceConfigManager.MIGRATION_STATE_IN_PROGRESS_COUNT_DEFAULT_FLAG_VALUE;
import static com.android.server.healthconnect.migration.MigrationBroadcastScheduler.MIGRATION_BROADCAST_NAMESPACE;
import static com.android.server.healthconnect.migration.MigrationConstants.MIGRATION_STATE_CHANGE_NAMESPACE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectThreadScheduler;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.time.Duration;
import java.util.Objects;

/** Unit tests for broadcast scheduling logic in {@link MigrationBroadcastScheduler} */
@RunWith(AndroidJUnit4.class)
public class MigrationBroadcastSchedulingTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(MigrationStateManager.class)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(HealthConnectThreadScheduler.class)
                    .mockStatic(HealthConnectDeviceConfigManager.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock private Context mContext;
    @Mock private JobScheduler mJobScheduler;
    @Mock private MigrationStateManager mMigrationStateManager;
    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;

    private MigrationBroadcastScheduler mMigrationBroadcastScheduler;

    private static final int MIGRATION_STATE_ALLOWED_COUNT_MOCK_VALUE =
            MIGRATION_STATE_ALLOWED_COUNT_DEFAULT_FLAG_VALUE;
    private static final int MIGRATION_STATE_IN_PROGRESS_COUNT_MOCK_VALUE =
            MIGRATION_STATE_IN_PROGRESS_COUNT_DEFAULT_FLAG_VALUE;

    private static final Duration NON_IDLE_STATE_TIMEOUT_MOCK_VALUE =
            Duration.ofDays(
                    HealthConnectDeviceConfigManager
                            .NON_IDLE_STATE_TIMEOUT_DAYS_DEFAULT_FLAG_VALUE);

    private final long mMinPeriodMillis = JobInfo.getMinPeriodMillis();
    private final long mIntervalGreaterThanMinPeriod = mMinPeriodMillis + 1000;
    private final long mIntervalLessThanMinPeriod = mMinPeriodMillis - 1000;

    @Before
    public void setUp() {
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mContext.getPackageName()).thenReturn("packageName");
        when(mJobScheduler.forNamespace(MIGRATION_BROADCAST_NAMESPACE)).thenReturn(mJobScheduler);
        when(HealthConnectDeviceConfigManager.getInitialisedInstance())
                .thenReturn(mHealthConnectDeviceConfigManager);
        when(mHealthConnectDeviceConfigManager.getMigrationStateAllowedCount())
                .thenReturn(MIGRATION_STATE_ALLOWED_COUNT_MOCK_VALUE);
        when(mHealthConnectDeviceConfigManager.getMigrationStateInProgressCount())
                .thenReturn(MIGRATION_STATE_IN_PROGRESS_COUNT_MOCK_VALUE);
        when(mHealthConnectDeviceConfigManager.getNonIdleStateTimeoutPeriod())
                .thenReturn(NON_IDLE_STATE_TIMEOUT_MOCK_VALUE);

        mMigrationBroadcastScheduler = Mockito.spy(new MigrationBroadcastScheduler(0));
    }

    @Test
    public void testPrescheduleNewJobs_updateMigrationState_newJobsScheduled() {
        when(MigrationStateManager.initializeInstance(anyInt())).thenCallRealMethod();
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        when(mJobScheduler.forNamespace(MIGRATION_STATE_CHANGE_NAMESPACE))
                .thenReturn(mJobScheduler);
        ExtendedMockito.doAnswer(
                        (Answer<Void>)
                                invocationOnMock -> {
                                    Runnable task = invocationOnMock.getArgument(0);
                                    task.run();
                                    return null;
                                })
                .when(() -> HealthConnectThreadScheduler.scheduleInternalTask(any()));

        MigrationStateManager migrationStateManager = MigrationStateManager.initializeInstance(0);
        migrationStateManager.setMigrationBroadcastScheduler(mMigrationBroadcastScheduler);
        migrationStateManager.updateMigrationState(mContext, MIGRATION_STATE_IN_PROGRESS);

        verify(mMigrationBroadcastScheduler, times(1)).scheduleNewJobs(any());
    }

    @Test
    public void
            testScheduling_migrationInProgressIntervalGreaterThanMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_IN_PROGRESS);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_IN_PROGRESS)))
                .thenReturn(mIntervalGreaterThanMinPeriod);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(1));
    }

    @Test
    public void testScheduling_migrationInProgressIntervalEqualToMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_IN_PROGRESS);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_IN_PROGRESS)))
                .thenReturn(mMinPeriodMillis);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
    }

    @Test
    public void testScheduling_migrationInProgressIntervalLessThanMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_IN_PROGRESS);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_IN_PROGRESS)))
                .thenReturn(mIntervalLessThanMinPeriod);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_SUCCESS);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
    }

    @Test
    public void testScheduling_migrationAllowedIntervalGreaterThanMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mIntervalGreaterThanMinPeriod);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(1));
    }

    @Test
    public void testScheduling_requiredCountEqualToZero_noJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredCount(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(0);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mIntervalGreaterThanMinPeriod);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(0));
    }

    @Test
    public void testScheduling_migrationAllowedIntervalEqualToMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mMinPeriodMillis);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
    }

    @Test
    public void testScheduling_migrationAllowedIntervalLessThanMinimum_periodicJobScheduled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mIntervalLessThanMinPeriod);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_SUCCESS);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
    }

    @Test
    public void
            testReinvocation_origAndNewIntervalsGreaterThanMin_previouslyScheduledJobsCancelled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mIntervalGreaterThanMinPeriod);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_SUCCESS);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(1)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(1));
        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(2)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(2));
    }

    @Test
    public void
            testReinvocation_origAndNewIntervalsEqualToMinimum_previouslyScheduledJobsCancelled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mMinPeriodMillis);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_SUCCESS);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(1)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(2)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(2));
    }

    @Test
    public void testReinvocation_previouslyScheduledJobsCancelled() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mIntervalGreaterThanMinPeriod);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_SUCCESS);
        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(1)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mIntervalGreaterThanMinPeriod, times(1));
        when(mMigrationBroadcastScheduler.getRequiredInterval(eq(MIGRATION_STATE_ALLOWED)))
                .thenReturn(mMinPeriodMillis);
        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
        verify(mJobScheduler, times(2)).cancelAll();
        verifyPeriodicJobSchedulerInvocation(mMinPeriodMillis, times(1));
    }

    @Test
    public void testScheduling_schedulingFails_noFurtherScheduling() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(mMigrationStateManager.getMigrationState()).thenReturn(MIGRATION_STATE_ALLOWED);
        when(mJobScheduler.schedule(any(JobInfo.class))).thenReturn(JobScheduler.RESULT_FAILURE);

        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);

        verify(mJobScheduler, atMost(1))
                .schedule(
                        argThat(
                                jobInfo ->
                                        (Objects.equals(
                                                jobInfo.getService().getClassName(),
                                                MigrationBroadcastJobService.class.getName()))));
    }

    private void verifyPeriodicJobSchedulerInvocation(
            long interval, VerificationMode verificationMode) {
        verify(mJobScheduler, verificationMode)
                .schedule(
                        argThat(
                                jobInfo ->
                                        (Objects.equals(
                                                        jobInfo.getService().getClassName(),
                                                        MigrationBroadcastJobService.class
                                                                .getName()))
                                                && jobInfo.isPeriodic()
                                                && jobInfo.getIntervalMillis() == interval));
    }
}
