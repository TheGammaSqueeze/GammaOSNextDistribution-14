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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_FETCH_JOB_SCHEDULER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_HANDLE_JOB_SERVICE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Epoch computation job. This will be run approximately once per epoch to compute Topics. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public final class EpochJobService extends JobService {
    private static final int TOPICS_EPOCH_JOB_ID = TOPICS_EPOCH_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d("Disabling EpochJobService job because it's running in ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        LoggerFactory.getTopicsLogger().d("EpochJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(TOPICS_EPOCH_JOB_ID);

        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger()
                    .e("Topics API is disabled, skipping and cancelling EpochJobService");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        // This service executes each incoming job on a Handler running on the application's
        // main thread. This means that we must offload the execution logic to background executor.
        // TODO(b/225382268): Handle cancellation.
        ListenableFuture<Void> epochComputationFuture =
                Futures.submit(
                        () -> {
                            TopicsWorker.getInstance(this).computeEpoch();
                        },
                        AdServicesExecutors.getBackgroundExecutor());

        Futures.addCallback(
                epochComputationFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LoggerFactory.getTopicsLogger().d("Epoch Computation succeeded!");

                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(EpochJobService.this)
                                .recordJobFinished(
                                        TOPICS_EPOCH_JOB_ID, /* isSuccessful= */ true, shouldRetry);

                        // Tell the JobScheduler that the job has completed and does not need to be
                        // rescheduled.
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        ErrorLogUtil.e(
                                t,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_HANDLE_JOB_SERVICE_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                        LoggerFactory.getTopicsLogger()
                                .e(t, "Failed to handle JobService: " + params.getJobId());

                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(EpochJobService.this)
                                .recordJobFinished(
                                        TOPICS_EPOCH_JOB_ID,
                                        /* isSuccessful= */ false,
                                        shouldRetry);

                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        // TODO(b/225909845): Revisit this. We need a retry policy.
                        jobFinished(params, shouldRetry);
                    }
                },
                directExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getTopicsLogger().d("EpochJobService.onStopJob");

        // Tell JobScheduler not to reschedule the job because it's unknown at this stage if the
        // execution is completed or not to avoid executing the task twice.
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, TOPICS_EPOCH_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    static void schedule(
            Context context,
            @NonNull JobScheduler jobScheduler,
            long epochJobPeriodMs,
            long epochJobFlexMs) {
        final JobInfo job =
                new JobInfo.Builder(
                                TOPICS_EPOCH_JOB_ID,
                                new ComponentName(context, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(epochJobPeriodMs, epochJobFlexMs)
                        .build();

        jobScheduler.schedule(job);
        LoggerFactory.getTopicsLogger().d("Scheduling Epoch job ...");
    }

    /**
     * Schedule Epoch Job Service if needed: there is no scheduled job with same job parameters.
     *
     * @param context the context
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger()
                    .e("Topics API is disabled, skip scheduling the EpochJobService");
            return false;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_FETCH_JOB_SCHEDULER_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            LoggerFactory.getTopicsLogger().e("Cannot fetch Job Scheduler!");
            return false;
        }

        long flagsEpochJobPeriodMs = FlagsFactory.getFlags().getTopicsEpochJobPeriodMs();
        long flagsEpochJobFlexMs = FlagsFactory.getFlags().getTopicsEpochJobFlexMs();

        JobInfo job = jobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        // Skip to reschedule the job if there is same scheduled job with same parameters.
        if (job != null && !forceSchedule) {
            long epochJobPeriodMs = job.getIntervalMillis();
            long epochJobFlexMs = job.getFlexMillis();

            if (flagsEpochJobPeriodMs == epochJobPeriodMs
                    && flagsEpochJobFlexMs == epochJobFlexMs) {
                LoggerFactory.getTopicsLogger()
                        .i(
                                "Epoch Job Service has been scheduled with same parameters, skip"
                                        + " rescheduling!");
                return false;
            }
        }

        schedule(context, jobScheduler, flagsEpochJobPeriodMs, flagsEpochJobFlexMs);
        return true;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(TOPICS_EPOCH_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(TOPICS_EPOCH_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }
}
