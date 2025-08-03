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

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.android.federatedcompute.services.scheduling.SchedulingUtil.convertSchedulingMode;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.PackageUtils;
import com.android.federatedcompute.services.common.PhFlags;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.internal.util.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Handles scheduling training tasks e.g. calling into JobScheduler, maintaining datastore. */
public class FederatedComputeJobManager {
    private static final String TAG = FederatedComputeJobManager.class.getSimpleName();
    private static volatile FederatedComputeJobManager sSingletonInstance;
    @NonNull private final Context mContext;
    private final FederatedTrainingTaskDao mFederatedTrainingTaskDao;
    private final JobSchedulerHelper mJobSchedulerHelper;
    private final FederatedJobIdGenerator mJobIdGenerator;
    private final Clock mClock;
    private final Flags mFlags;

    @VisibleForTesting
    FederatedComputeJobManager(
            @NonNull Context context,
            FederatedTrainingTaskDao federatedTrainingTaskDao,
            FederatedJobIdGenerator jobIdGenerator,
            JobSchedulerHelper jobSchedulerHelper,
            @NonNull Clock clock,
            Flags flag) {
        this.mContext = context.getApplicationContext();
        this.mFederatedTrainingTaskDao = federatedTrainingTaskDao;
        this.mJobIdGenerator = jobIdGenerator;
        this.mJobSchedulerHelper = jobSchedulerHelper;
        this.mClock = clock;
        this.mFlags = flag;
    }

    /** Returns an instance of FederatedComputeJobManager given a context. */
    @NonNull
    public static FederatedComputeJobManager getInstance(@NonNull Context mContext) {
        if (sSingletonInstance == null) {
            synchronized (FederatedComputeJobManager.class) {
                if (sSingletonInstance == null) {
                    Clock clock = MonotonicClock.getInstance();
                    sSingletonInstance =
                            new FederatedComputeJobManager(
                                    mContext.getApplicationContext(),
                                    FederatedTrainingTaskDao.getInstance(mContext),
                                    FederatedJobIdGenerator.getInstance(),
                                    new JobSchedulerHelper(clock),
                                    clock,
                                    PhFlags.getInstance());
                }
            }
        }
        return sSingletonInstance;
    }

    /** We enforce device idle, battery not low and unmetered network training constraints. */
    private static byte[] buildTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingConstraints.createTrainingConstraints(
                        builder,
                        /** requiresSchedulerIdle= */
                        true,
                        /** requiresSchedulerBatteryNotLow= */
                        true,
                        /** requiresSchedulerUnmeteredNetwork= */
                        true));
        return builder.sizedByteArray();
    }

    private static byte[] buildDefaultTrainingInterval() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }

    private static byte[] buildTrainingIntervalOptions(
            @Nullable TrainingInterval trainingInterval) {
        if (trainingInterval == null) {
            return buildDefaultTrainingInterval();
        }

        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder,
                        convertSchedulingMode(trainingInterval.getSchedulingMode()),
                        trainingInterval.getMinimumIntervalMillis()));

        return builder.sizedByteArray();
    }

    private static boolean trainingIntervalChanged(
            TrainingOptions newTaskOptions, FederatedTrainingTask existingTask) {
        byte[] incomingTrainingIntervalOptions =
                buildTrainingIntervalOptions(newTaskOptions.getTrainingInterval());
        return !Arrays.equals(incomingTrainingIntervalOptions, existingTask.intervalOptions());
    }

    /**
     * Called when a client indicates via the client API that a task with the given parameters
     * should be scheduled.
     */
    public synchronized int onTrainerStartCalled(
            String callingPackageName, TrainingOptions trainingOptions) {
        String packageName = trainingOptions.getOwnerComponentName().getPackageName();
        String ownerCertDigest;
        try {
            ownerCertDigest = PackageUtils.getCertDigest(mContext, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, e, ": Error while scheduling  federatedCompute task.");
            return STATUS_INTERNAL_ERROR;
        }

        FederatedTrainingTask existingTask =
                mFederatedTrainingTaskDao.findAndRemoveTaskByPopulationNameAndOwnerId(
                        trainingOptions.getPopulationName(),
                        trainingOptions.getOwnerComponentName().flattenToString(),
                        ownerCertDigest);
        Set<FederatedTrainingTask> trainingTasksToCancel = new HashSet<>();
        String populationName = trainingOptions.getPopulationName();

        String ownerIdentifier =
                packageName
                        + "-"
                        + trainingOptions.getOwnerComponentName().getClassName()
                        + "-"
                        + ownerCertDigest;
        long nowMs = mClock.currentTimeMillis();
        boolean shouldSchedule;
        FederatedTrainingTask newTask;
        byte[] newTrainingConstraint = buildTrainingConstraints();
        // Federated server address is required to schedule the job.
        Preconditions.checkStringNotEmpty(trainingOptions.getServerAddress());

        if (existingTask == null) {
            int jobId =
                    mJobIdGenerator.generateJobId(this.mContext, populationName, ownerIdentifier);
            FederatedTrainingTask.Builder newTaskBuilder =
                    FederatedTrainingTask.builder()
                            .appPackageName(callingPackageName)
                            .jobId(jobId)
                            .ownerId(trainingOptions.getOwnerComponentName().flattenToString())
                            .ownerIdCertDigest(ownerCertDigest)
                            .creationTime(nowMs)
                            .lastScheduledTime(nowMs)
                            .schedulingReason(SchedulingReason.SCHEDULING_REASON_NEW_TASK)
                            .constraints(newTrainingConstraint)
                            .intervalOptions(
                                    buildTrainingIntervalOptions(
                                            trainingOptions.getTrainingInterval()))
                            .populationName(trainingOptions.getPopulationName())
                            .contextData(trainingOptions.getContextData())
                            .serverAddress(trainingOptions.getServerAddress())
                            .earliestNextRunTime(
                                    SchedulingUtil.getEarliestRuntimeForInitialSchedule(
                                            nowMs, 0, trainingOptions, mFlags));
            newTask = newTaskBuilder.build();
            shouldSchedule = true;
        } else {
            // If another task with same jobId exists, we only need to delete it and don't need
            // cancel the task because we will overwrite it anyway.
            mFederatedTrainingTaskDao.findAndRemoveTaskByJobId(existingTask.jobId());
            // If a task does exist already then update only those fields that should be
            // updated: population name, constraints, last scheduled time, BUT maintain
            // other important fields like job id, the earliest next run time. This ensures that
            // repeated calls to onTrainerStart do not keep postponing the job's next runtime.
            FederatedTrainingTask.Builder newTaskBuilder =
                    existingTask.toBuilder()
                            .constraints(buildTrainingConstraints())
                            .serverAddress(trainingOptions.getServerAddress())
                            .contextData(trainingOptions.getContextData())
                            .lastScheduledTime(nowMs);
            if (detectKeyParametersChanged(trainingOptions, existingTask)) {
                newTaskBuilder.intervalOptions(null).lastRunStartTime(null).lastRunEndTime(null);
                newTaskBuilder
                        .populationName(trainingOptions.getPopulationName())
                        .intervalOptions(
                                buildTrainingIntervalOptions(trainingOptions.getTrainingInterval()))
                        .earliestNextRunTime(
                                SchedulingUtil.getEarliestRuntimeForInitialSchedule(
                                        nowMs, nowMs, trainingOptions, mFlags));
                shouldSchedule = true;
            } else {
                long earliestNextRunTime =
                        SchedulingUtil.getEarliestRuntimeForExistingTask(
                                existingTask, trainingOptions, mFlags, nowMs);
                long maxExpectedRuntimeSecs =
                        mFlags.getTrainingServiceResultCallbackTimeoutSecs() + /*buffer*/ 30;
                boolean currentlyRunningHeuristic =
                        existingTask.getLastRunStartTime() < nowMs
                                && nowMs - existingTask.getLastRunStartTime()
                                        < 1000 * maxExpectedRuntimeSecs
                                && existingTask.getLastRunStartTime()
                                        > existingTask.getLastRunEndTime();
                shouldSchedule =
                        !currentlyRunningHeuristic
                                && (!mJobSchedulerHelper.isTaskScheduled(mContext, existingTask)
                                        || !Arrays.equals(
                                                existingTask.constraints(), newTrainingConstraint)
                                        || !existingTask
                                                .earliestNextRunTime()
                                                .equals(earliestNextRunTime));

                // If we have to reschedule, update the earliest next run time. Otherwise,
                // retain the original earliest next run time.
                newTaskBuilder.earliestNextRunTime(
                        shouldSchedule ? earliestNextRunTime : existingTask.earliestNextRunTime());
            }
            // If we have to reschedule, mark this task as "new"; otherwise, retain the original
            // reason for scheduling it.
            newTaskBuilder.schedulingReason(
                    shouldSchedule
                            ? SchedulingReason.SCHEDULING_REASON_NEW_TASK
                            : existingTask.schedulingReason());
            newTask = newTaskBuilder.build();
        }

        // Now reconcile the new task store and JobScheduler.
        //
        // First, if necessary, try to (re)schedule the task.
        if (shouldSchedule) {
            boolean scheduleResult = mJobSchedulerHelper.scheduleTask(mContext, newTask);
            if (!scheduleResult) {
                LogUtil.w(
                        TAG,
                        "JobScheduler returned failure when starting training job %d",
                        newTask.jobId());
                // If scheduling failed then leave the task store as-is, and bail.
                return STATUS_INTERNAL_ERROR;
            }
        }

        // Add the new task into federated training task store. if failed, return the error.
        boolean storeResult =
                mFederatedTrainingTaskDao.updateOrInsertFederatedTrainingTask(newTask);
        if (!storeResult) {
            LogUtil.w(
                    TAG,
                    "JobScheduler returned failure when storing training job with id %d!",
                    newTask.jobId());
            return STATUS_INTERNAL_ERROR;
        }
        // Second, if the task previously had a different job ID or a if there was another
        // task with the same population name, then cancel the corresponding old tasks.
        for (FederatedTrainingTask trainingTaskToCancel : trainingTasksToCancel) {
            LogUtil.i(TAG, " JobScheduler cancel the task %d", newTask.jobId());
            mJobSchedulerHelper.cancelTask(mContext, trainingTaskToCancel);
        }
        return STATUS_SUCCESS;
    }

    /**
     * Called when a client indicates via the client API that a task with the given parameters
     * should be canceled.
     */
    public synchronized int onTrainerStopCalled(
            ComponentName ownerComponent, String populationName) {
        String ownerCertDigest;
        try {
            ownerCertDigest = PackageUtils.getCertDigest(mContext, ownerComponent.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, e, ": Error while cancelling federatedCompute task.");
            return STATUS_INTERNAL_ERROR;
        }
        FederatedTrainingTask taskToCancel =
                mFederatedTrainingTaskDao.findAndRemoveTaskByPopulationNameAndOwnerId(
                        populationName, ownerComponent.flattenToString(), ownerCertDigest);
        // If no matching task exists then there's nothing for us to do. This is not an error
        // case though.
        if (taskToCancel == null) {
            LogUtil.i(
                    TAG,
                    "No matching task exists when cancel the job (population: %s,"
                            + " ATP: %s, cert: %s)",
                    populationName,
                    ownerComponent.flattenToString(),
                    ownerCertDigest);
            return STATUS_SUCCESS;
        }

        LogUtil.i(TAG, " onTrainerStopCalled cancel the task %d", taskToCancel.jobId());
        mJobSchedulerHelper.cancelTask(mContext, taskToCancel);
        return STATUS_SUCCESS;
    }

    /** Called when a training task identified by {@code jobId} starts running. */
    @Nullable
    public synchronized FederatedTrainingTask onTrainingStarted(int jobId) {
        FederatedTrainingTask existingTask =
                mFederatedTrainingTaskDao.findAndRemoveTaskByJobId(jobId);
        if (existingTask == null) {
            return null;
        }
        long ttlMs = SECONDS.toMillis(mFlags.getTrainingTimeForLiveSeconds());
        long nowMs = mClock.currentTimeMillis();
        if (ttlMs > 0 && nowMs - existingTask.lastScheduledTime() > ttlMs) {
            // If the TTL is expired, then delete the task.
            LogUtil.i(TAG, "Training task %d TTLd", jobId);
            return null;
        }
        FederatedTrainingTask newTask = existingTask.toBuilder().lastRunStartTime(nowMs).build();
        mFederatedTrainingTaskDao.updateOrInsertFederatedTrainingTask(newTask);
        return newTask;
    }

    /**
     * Add or update the training task history record if this round of training submit result to
     * server successfully. The record will be used to run eligibility task.
     */
    public synchronized void recordSuccessContribution(
            int jobId, String populationName, TaskAssignment taskAssignment) {
        TaskHistory existingTaskHistory =
                mFederatedTrainingTaskDao.getLatestTaskHistory(
                        jobId, populationName, taskAssignment.getTaskId());
        long roundNumber = 0;
        for (EligibilityPolicyEvalSpec evalSpec :
                taskAssignment.getEligibilityTaskInfo().getEligibilityPoliciesList()) {
            if (evalSpec.getPolicyTypeCase()
                    == EligibilityPolicyEvalSpec.PolicyTypeCase.MIN_SEP_POLICY) {
                roundNumber = evalSpec.getMinSepPolicy().getCurrentIndex();
                break;
            }
        }
        boolean result =
                mFederatedTrainingTaskDao.updateOrInsertTaskHistory(
                        new TaskHistory.Builder()
                                .setJobId(jobId)
                                .setPopulationName(populationName)
                                .setTaskId(taskAssignment.getTaskId())
                                .setContributionRound(roundNumber)
                                .setContributionTime(mClock.currentTimeMillis())
                                .setTotalParticipation(
                                        existingTaskHistory == null
                                                ? 1
                                                : existingTaskHistory.getTotalParticipation() + 1)
                                .build());
        if (!result) {
            LogUtil.e(
                    TAG,
                    "Failed to store success contribution in TaskHistory %s %s",
                    populationName,
                    taskAssignment.getTaskId());
        }
    }

    /** Called when a training task completed. */
    public synchronized void onTrainingCompleted(
            int jobId,
            String populationName,
            TrainingIntervalOptions trainingIntervalOptions,
            TaskRetry taskRetry,
            ContributionResult trainingResult) {
        boolean result =
                rescheduleFederatedTaskAfterTraining(
                        jobId, populationName, trainingIntervalOptions, taskRetry, trainingResult);
        if (!result) {
            LogUtil.e(TAG, "JobScheduler returned failure after successful run!");
        }
    }

    /** Tries to reschedule a federated task after a failed or successful training run. */
    private synchronized boolean rescheduleFederatedTaskAfterTraining(
            int jobId,
            String populationName,
            TrainingIntervalOptions intervalOptions,
            TaskRetry taskRetry,
            ContributionResult trainingResult) {
        FederatedTrainingTask existingTask =
                mFederatedTrainingTaskDao.findAndRemoveTaskByPopulationAndJobId(
                        populationName, jobId);
        // If task was deleted already, then return early, but still consider it a success
        // since this is not really an error case (e.g. Trainer.stop may have simply been
        // called while training was running).
        if (existingTask == null) {
            return true;
        }
        boolean hasContributed = trainingResult == ContributionResult.SUCCESS;
        if (intervalOptions != null
                && intervalOptions.schedulingMode() == SchedulingMode.ONE_TIME
                && hasContributed) {
            mJobSchedulerHelper.cancelTask(mContext, existingTask);
            LogUtil.i(TAG, "federated task remove because oneoff task succeeded: %d", jobId);
            return true;
        }
        // Update the task and add it back to the training task store.
        long nowMillis = mClock.currentTimeMillis();
        long earliestNextRunTime =
                SchedulingUtil.getEarliestRuntimeForFCReschedule(
                        nowMillis, intervalOptions, taskRetry, hasContributed, mFlags);
        FederatedTrainingTask.Builder newTaskBuilder =
                existingTask.toBuilder()
                        .lastRunEndTime(nowMillis)
                        .earliestNextRunTime(earliestNextRunTime);
        newTaskBuilder.schedulingReason(
                taskRetry != null
                        ? SchedulingReason.SCHEDULING_REASON_FEDERATED_COMPUTATION_RETRY
                        : SchedulingReason.SCHEDULING_REASON_FAILURE);
        if (trainingResult == ContributionResult.FAIL) {
            int rescheduleCount = existingTask.rescheduleCount() + 1;
            if (rescheduleCount > mFlags.getFcpRescheduleLimit()) {
                LogUtil.i(
                        TAG,
                        "federated task (id: %d) was not rescheduled due to reschedule limit "
                                + "reached!",
                        jobId);
                mJobSchedulerHelper.cancelTask(mContext, newTaskBuilder.build());
                return false;
            }
            newTaskBuilder.rescheduleCount(rescheduleCount);
        } else {
            // drop reschedule count to 0 in case it was not a faulty run.
            newTaskBuilder.rescheduleCount(0);
        }
        FederatedTrainingTask newTask = newTaskBuilder.build();
        mFederatedTrainingTaskDao.updateOrInsertFederatedTrainingTask(newTask);
        return mJobSchedulerHelper.scheduleTask(mContext, newTask);
    }

    private boolean detectKeyParametersChanged(
            TrainingOptions newTaskOptions, FederatedTrainingTask existingTask) {
        // Check if the task previously had a different population name.
        boolean populationChanged =
                !existingTask.populationName().equals(newTaskOptions.getPopulationName());
        if (populationChanged) {
            LogUtil.i(
                    TAG,
                    "JobScheduler population name changed from %s to %s",
                    existingTask.populationName(),
                    newTaskOptions.getPopulationName());
        }

        boolean trainingIntervalChanged = trainingIntervalChanged(newTaskOptions, existingTask);
        if (trainingIntervalChanged) {
            LogUtil.i(
                    TAG,
                    "JobScheduler training interval changed from %s to %s",
                    existingTask.getTrainingIntervalOptions(),
                    newTaskOptions.getTrainingInterval());
        }
        return populationChanged || trainingIntervalChanged;
    }
}
