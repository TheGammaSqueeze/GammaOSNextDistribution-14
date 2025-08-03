/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;

import android.annotation.RequiresApi;
import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/** Background job for making sign/join calls. */
@SuppressLint("LineLength")
@RequiresApi(Build.VERSION_CODES.S)
public class KAnonSignJoinBackgroundJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        Flags flags = FlagsFactory.getFlags();

        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    true);
        }
        if (!flags.getFledgeKAnonBackgroundProcessEnabled()) {
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    true);
        }
        // Skip the execution and cancel the job if user consent is revoked.
        // Use the per-API consent with GA UX.
        if (!ConsentManager.getInstance().getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            LoggerFactory.getFledgeLogger()
                    .d("User Consent is revoked ; skipping and cancelling job");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED,
                    /* doRecord= */ true);
        }

        Futures.addCallback(
                doSignJoinBackgroundJob(),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(
                                        KAnonSignJoinBackgroundJobService.this)
                                .recordJobFinished(
                                        FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId(),
                                        /* isSuccessful= */ true,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(
                                        KAnonSignJoinBackgroundJobService.this)
                                .recordJobFinished(
                                        FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId(),
                                        /* isSuccessful= */ false,
                                        shouldRetry);
                        jobFinished(params, shouldRetry);
                    }
                },
                AdServicesExecutors.getBackgroundExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId(), false);
        KAnonSignJoinBackgroundJobWorker.getInstance(this).stopWork();
        return false;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId());
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId(), skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    /**
     * Attempts to schedule the KAnon Sign join background job as a periodic job if it is not
     * already scheduled.
     */
    public static void scheduleIfNeeded(@NonNull Context context, boolean forceSchedule) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        Flags flags = FlagsFactory.getFlags();
        if (jobScheduler.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId()) == null
                || forceSchedule) {
            schedule(context, flags);
        }
    }

    /** Schedules the kanon sign join background job as a periodic job. */
    public static void schedule(Context context, Flags flags) {
        if (!flags.getFledgeKAnonBackgroundProcessEnabled()) {
            return;
        }
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId(),
                                new ComponentName(context, KAnonSignJoinBackgroundJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(flags.getFledgeKAnonBackgroundProcessTimePeriodInMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }

    private FluentFuture<Void> doSignJoinBackgroundJob() {
        return KAnonSignJoinBackgroundJobWorker.getInstance(this).runSignJoinBackgroundProcess();
    }
}
