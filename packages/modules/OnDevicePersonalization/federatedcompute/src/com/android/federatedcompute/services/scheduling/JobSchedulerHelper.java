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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.data.FederatedTrainingTask;

/** The helper class of JobScheduler. */
public class JobSchedulerHelper {
    private static final String TAG = JobSchedulerHelper.class.getSimpleName();
    private static final String TRAINING_JOB_SERVICE =
            "com.android.federatedcompute.services.training.FederatedJobService";
    private Clock mClock;

    public JobSchedulerHelper(Clock clock) {
        this.mClock = clock;
    }

    /** Schedules a task using JobScheduler. */
    public boolean scheduleTask(Context context, FederatedTrainingTask newTask) {
        JobInfo jobInfo = convertToJobInfo(context, newTask);
        LogUtil.i(
                TAG,
                "Scheduling job %s (with minimum latency until next run %d milliseconds)",
                jobInfo.getId(),
                jobInfo.getMinLatencyMillis());
        return tryScheduleJob(context, jobInfo);
    }

    /** Cancels a task using JobScheduler. */
    public void cancelTask(Context context, FederatedTrainingTask taskToCancel) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        jobScheduler.cancel(taskToCancel.jobId());
    }

    /**
     * Tries to schedule the given job, but checks first if it collides with a non-FederatedCompute
     * job.
     */
    private boolean tryScheduleJob(Context context, JobInfo jobInfo) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (checkCollidesWithNonFederatedComputationJob(jobScheduler, jobInfo)) {
            LogUtil.w(
                    TAG,
                    "Collision with non-FederatedComputation job with same job ID (%s)"
                            + " detected, not scheduling!",
                    jobInfo.getId());
            return false;
        }

        return jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS;
    }

    private boolean checkCollidesWithNonFederatedComputationJob(
            JobScheduler jobScheduler, JobInfo jobInfo) {
        JobInfo existingJobInfo = jobScheduler.getPendingJob(jobInfo.getId());
        if (existingJobInfo == null) {
            return false;
        }
        return !existingJobInfo.getService().equals(jobInfo.getService());
    }

    private JobInfo convertToJobInfo(Context context, FederatedTrainingTask task) {
        ComponentName jobComponent = new ComponentName(context, TRAINING_JOB_SERVICE);

        // Get the "now" time once, so we use a single consistent value throughout this method.
        long nowMillis = mClock.currentTimeMillis();

        JobInfo.Builder jobInfo = new JobInfo.Builder(task.jobId(), jobComponent);
        jobInfo.setRequiresDeviceIdle(task.getTrainingConstraints().requiresSchedulerIdle())
                .setRequiresBatteryNotLow(
                        task.getTrainingConstraints().requiresSchedulerBatteryNotLow())
                .setMinimumLatency(task.earliestNextRunTime() - nowMillis)
                .setPersisted(true);

        jobInfo.setRequiredNetworkType(
                task.getTrainingConstraints().requiresSchedulerUnmeteredNetwork()
                        ? JobInfo.NETWORK_TYPE_UNMETERED
                        : JobInfo.NETWORK_TYPE_ANY);
        return jobInfo.build();
    }

    /** Checks if a task is already scheduled by JobScheduler. */
    public boolean isTaskScheduled(Context context, FederatedTrainingTask task) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        return jobScheduler.getPendingJob(task.jobId()) != null;
    }
}
