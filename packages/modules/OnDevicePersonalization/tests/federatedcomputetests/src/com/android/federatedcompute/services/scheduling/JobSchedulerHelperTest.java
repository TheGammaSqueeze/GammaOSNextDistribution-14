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

package com.android.federatedcompute.services.scheduling;

import static android.app.job.JobInfo.NETWORK_TYPE_ANY;
import static android.app.job.JobInfo.NETWORK_TYPE_UNMETERED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.common.collect.Iterables;
import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JobSchedulerHelperTest {
    private static final String PACKAGE_NAME = "foo.federatedcompute";
    private static final String TRAINING_JOB_SERVICE =
            "com.android.federatedcompute.services.training.FederatedJobService";
    private static final String OWNER_ID =
            "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "123SOME45DIGEST78";
    private static final String POPULATION_NAME = "population";
    private static final String SERVER_ADDRESS = "https://server.uri/";
    private static final int JOB_ID = 10281993;
    private static final long CURRENT_TIME_MILLIS = 80000;
    private static final long NEXT_RUNTIME_MILLSECONDS = 100000;
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private static final byte[] TRAINING_CONSTRAINTS =
            createTrainingConstraints(
                    /* requiresSchedulerIdle= */ true,
                    /* requiresBatteryNotLow= */ true,
                    /* requiresSchedulerUnmeteredNetwork= */ true);
    private static final int SCHEDULING_REASON = SchedulingReason.SCHEDULING_REASON_NEW_TASK;
    private static final FederatedTrainingTask TRAINING_TASK =
            FederatedTrainingTask.builder()
                    .appPackageName(PACKAGE_NAME)
                    .populationName(POPULATION_NAME)
                    .intervalOptions(INTERVAL_OPTIONS)
                    .creationTime(CURRENT_TIME_MILLIS)
                    .lastScheduledTime(CURRENT_TIME_MILLIS)
                    .schedulingReason(SCHEDULING_REASON)
                    .jobId(JOB_ID)
                    .ownerId(OWNER_ID)
                    .ownerIdCertDigest(OWNER_ID_CERT_DIGEST)
                    .serverAddress(SERVER_ADDRESS)
                    .earliestNextRunTime(NEXT_RUNTIME_MILLSECONDS)
                    .constraints(TRAINING_CONSTRAINTS)
                    .build();

    private JobSchedulerHelper mJobSchedulerHelper;
    private JobScheduler mJobScheduler;
    private Context mContext;

    @Mock private Clock mClock;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mJobScheduler = mContext.getSystemService(JobScheduler.class);
        mJobScheduler.cancelAll();
        mJobSchedulerHelper = new JobSchedulerHelper(mClock);
        when(mClock.currentTimeMillis()).thenReturn(1000L);
    }

    @Test
    public void scheduleTask() {
        assertThat(mJobSchedulerHelper.scheduleTask(mContext, TRAINING_TASK)).isTrue();

        JobInfo jobInfo = Iterables.getOnlyElement(mJobScheduler.getAllPendingJobs());

        verifyJobInfo(jobInfo);
    }

    @Test
    public void schedule_collides_sameService_success() {
        mJobSchedulerHelper.scheduleTask(mContext, TRAINING_TASK);
        // Schedule a job with same job id.
        assertThat(mJobSchedulerHelper.scheduleTask(mContext, TRAINING_TASK)).isTrue();
    }

    @Test
    public void scheduleTask_overrideIdleConstraint_waivesIdle() {
        FederatedTrainingTask trainingTask =
                TRAINING_TASK.toBuilder()
                        .constraints(
                                createTrainingConstraints(
                                        /* requiresSchedulerIdle= */ false,
                                        /* requiresBatteryNotLow= */ true,
                                        /* requiresSchedulerUnmeteredNetwork= */ true))
                        .build();

        assertThat(mJobSchedulerHelper.scheduleTask(mContext, trainingTask)).isTrue();

        JobInfo jobInfo = Iterables.getOnlyElement(mJobScheduler.getAllPendingJobs());

        assertThat(jobInfo.isRequireDeviceIdle()).isFalse();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.getNetworkType()).isEqualTo(NETWORK_TYPE_UNMETERED);
    }

    @Test
    public void scheduleTask_overrideUnmeteredNetworkConstraint_waivesNetwork() {
        FederatedTrainingTask trainingTask =
                TRAINING_TASK.toBuilder()
                        .constraints(
                                createTrainingConstraints(
                                        /* requiresSchedulerIdle= */ true,
                                        /* requiresBatteryNotLow= */ true,
                                        /* requiresSchedulerUnmeteredNetwork= */ false))
                        .build();

        assertThat(mJobSchedulerHelper.scheduleTask(mContext, trainingTask)).isTrue();

        JobInfo jobInfo = Iterables.getOnlyElement(mJobScheduler.getAllPendingJobs());

        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.getNetworkType()).isEqualTo(NETWORK_TYPE_ANY);
    }

    @Test
    public void scheduleTask_overrideChargingConstraint_waivesCharging() {
        FederatedTrainingTask trainingTask =
                TRAINING_TASK.toBuilder()
                        .constraints(
                                createTrainingConstraints(
                                        /* requiresSchedulerIdle= */ true,
                                        /* requiresBatteryNotLow= */ false,
                                        /* requiresSchedulerUnmeteredNetwork= */ true))
                        .build();

        assertThat(mJobSchedulerHelper.scheduleTask(mContext, trainingTask)).isTrue();

        JobInfo jobInfo = Iterables.getOnlyElement(mJobScheduler.getAllPendingJobs());

        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isRequireBatteryNotLow()).isFalse();
        assertThat(jobInfo.getNetworkType()).isEqualTo(NETWORK_TYPE_UNMETERED);
    }

    @Test
    public void cancelTask() {
        mJobSchedulerHelper.scheduleTask(mContext, TRAINING_TASK);
        assertThat(mJobScheduler.getAllPendingJobs()).hasSize(1);
        mJobSchedulerHelper.cancelTask(mContext, TRAINING_TASK);
        assertThat(mJobScheduler.getAllPendingJobs()).isEmpty();
    }

    @Test
    public void isTaskScheduled() {
        assertThat(mJobSchedulerHelper.isTaskScheduled(mContext, TRAINING_TASK)).isFalse();
    }

    private void verifyJobInfo(JobInfo jobInfo) {
        assertThat(jobInfo.getId()).isEqualTo(JOB_ID);
        assertThat(jobInfo.getService())
                .isEqualTo(new ComponentName(mContext, TRAINING_JOB_SERVICE));
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.getNetworkType()).isEqualTo(NETWORK_TYPE_UNMETERED);
    }

    private static byte[] createTrainingConstraints(
            boolean requiresSchedulerIdle,
            boolean requiresBatteryNotLow,
            boolean requiresSchedulerUnmeteredNetwork) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingConstraints.createTrainingConstraints(
                        builder,
                        requiresSchedulerIdle,
                        requiresBatteryNotLow,
                        requiresSchedulerUnmeteredNetwork));
        return builder.sizedByteArray();
    }

    private static byte[] createDefaultTrainingIntervalOptions() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }
}
