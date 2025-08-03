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

package com.android.adservices.cobalt;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Cobalt JobService. This will trigger cobalt generate observation and upload logging in background
 * tasks.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public final class CobaltJobService extends JobService {
    private static final int COBALT_LOGGING_JOB_ID = COBALT_LOGGING_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d("Disabling cobalt logging job because it's running in ExtServices on T+");
            // Do not log via the AdservicesJobServiceLogger because the it might cause
            // ClassNotFound exception on earlier beta versions.
            return skipAndCancelBackgroundJob(
                    params, COBALT_LOGGING_JOB_ID, /* skipReason= */ 0, /* doRecord= */ false);
        }

        Flags flags = FlagsFactory.getFlags();

        // Record the invocation of onStartJob() for logging purpose.
        LogUtil.d("CobaltJobService.onStartJob");
        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(COBALT_LOGGING_JOB_ID);

        if (!flags.getCobaltLoggingEnabled()) {
            LogUtil.d(
                    "Cobalt logging killswitch is enabled, skipping and cancelling"
                            + " CobaltJobService");
            return skipAndCancelBackgroundJob(
                    params,
                    COBALT_LOGGING_JOB_ID,
                    /* skipReason= */ AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord= */ true);
        }

        ListenableFuture<Void> cobaltLoggingFuture =
                PropagatedFutures.submitAsync(
                        () -> {
                            LogUtil.d("CobaltJobService.onStart Job.");
                            return CobaltFactory.getCobaltPeriodicJob(this, flags)
                                    .generateAggregatedObservations();
                        },
                        AdServicesExecutors.getBackgroundExecutor());

        // Background job logging in onSuccess and OnFailure have to happen before jobFinished() is
        // called. Due to JobScheduler infra, the JobService instance will end its lifecycle (call
        // onDestroy()) once jobFinished() is invoked.
        Futures.addCallback(
                cobaltLoggingFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.d("Cobalt logging job succeeded.");

                        // Tell the JobScheduler that the job has completed and does not
                        // need to be rescheduled.
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(CobaltJobService.this)
                                .recordJobFinished(
                                        COBALT_LOGGING_JOB_ID,
                                        /* isSuccessful= */ true,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Failed to handle cobalt logging job");

                        // When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(CobaltJobService.this)
                                .recordJobFinished(
                                        COBALT_LOGGING_JOB_ID,
                                        /* isSuccessful= */ false,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }
                },
                directExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("CobaltJobService.onStopJob");
        // Tell JobScheduler not to reschedule the job because it's unknown at this stage if the
        // execution is completed or not to avoid executing the task twice.
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, COBALT_LOGGING_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    static void schedule(Context context, JobScheduler jobScheduler, Flags flags) {
        JobInfo job =
                new JobInfo.Builder(
                                COBALT_LOGGING_JOB_ID,
                                new ComponentName(context, CobaltJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(flags.getCobaltLoggingJobPeriodMs())
                        .build();

        jobScheduler.schedule(job);
        LogUtil.d("Scheduling cobalt logging job ...");
    }

    /**
     * Schedules cobalt Job Service if needed: there is no scheduled job with name job parameters.
     *
     * @param context the context
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();

        if (!flags.getCobaltLoggingEnabled()) {
            LogUtil.e("Cobalt logging feature is disabled, skip scheduling the CobaltJobService.");
            return false;
        }

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch job scheduler.");
            return false;
        }

        long flagsCobaltJobPeriodMs = flags.getCobaltLoggingJobPeriodMs();
        JobInfo job = jobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID);
        if (job != null && !forceSchedule) {
            long cobaltJobPeriodMs = job.getIntervalMillis();
            if (flagsCobaltJobPeriodMs == cobaltJobPeriodMs) {
                LogUtil.i(
                        "Cobalt Job Service has been scheduled with same parameters, skip "
                                + "rescheduling.");
                return false;
            }
        }

        schedule(context, jobScheduler, flags);
        return true;
    }

    private boolean skipAndCancelBackgroundJob(
            JobParameters params, int jobId, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(COBALT_LOGGING_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this).recordJobSkipped(jobId, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }
}
