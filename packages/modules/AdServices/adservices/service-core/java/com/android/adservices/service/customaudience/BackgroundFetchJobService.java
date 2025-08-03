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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;

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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Background fetch for FLEDGE Custom Audience API, executing periodic garbage collection and custom
 * audience updates.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BackgroundFetchJobService extends JobService {
    private static final int FLEDGE_BACKGROUND_FETCH_JOB_ID =
            FLEDGE_BACKGROUND_FETCH_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling BackgroundFetchJobService job because it's running in ExtServices"
                            + " on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        LoggerFactory.getFledgeLogger().d("BackgroundFetchJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(FLEDGE_BACKGROUND_FETCH_JOB_ID);

        if (!FlagsFactory.getFlags().getFledgeBackgroundFetchEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE background fetch is disabled; skipping and cancelling job");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        if (FlagsFactory.getFlags().getFledgeCustomAudienceServiceKillSwitch()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE Custom Audience API is disabled ; skipping and cancelling job");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        // Skip the execution and cancel the job if user consent is revoked.
        // Use the per-API consent with GA UX.
        if (!ConsentManager.getInstance().getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            LoggerFactory.getFledgeLogger()
                    .d("User Consent is revoked ; skipping and cancelling job");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED,
                    /* doRecord=*/ true);
        }

        // TODO(b/235841960): Consider using com.android.adservices.service.stats.Clock instead of
        //  Java Clock
        Instant jobStartTime = Clock.systemUTC().instant();
        LoggerFactory.getFledgeLogger()
                .d("Starting FLEDGE background fetch job at %s", jobStartTime.toString());

        BackgroundFetchWorker.getInstance(this)
                .runBackgroundFetch()
                .addCallback(
                        new FutureCallback<Void>() {
                            // Never manually reschedule the background fetch job, since it is
                            // already scheduled periodically and should try again multiple times
                            // per day
                            @Override
                            public void onSuccess(Void result) {
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                BackgroundFetchJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                                /* isSuccessful= */ true,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                if (t instanceof InterruptedException) {
                                    LoggerFactory.getFledgeLogger()
                                            .e(
                                                    t,
                                                    "FLEDGE background fetch interrupted while"
                                                        + " waiting for custom audience updates");
                                } else if (t instanceof ExecutionException) {
                                    LoggerFactory.getFledgeLogger()
                                            .e(
                                                    t,
                                                    "FLEDGE background fetch failed due to"
                                                            + " internal error");
                                } else if (t instanceof TimeoutException) {
                                    LoggerFactory.getFledgeLogger()
                                            .e(t, "FLEDGE background fetch timeout exceeded");
                                } else {
                                    LoggerFactory.getFledgeLogger()
                                            .e(
                                                    t,
                                                    "FLEDGE background fetch failed due to"
                                                            + " unexpected error");
                                }

                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                BackgroundFetchJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                                /* isSuccessful= */ false,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }
                        },
                        AdServicesExecutors.getLightWeightExecutor());

        return true;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(FLEDGE_BACKGROUND_FETCH_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(FLEDGE_BACKGROUND_FETCH_JOB_ID, skipReason);
        }

        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getFledgeLogger().d("BackgroundFetchJobService.onStopJob");
        BackgroundFetchWorker.getInstance(this).stopWork();

        boolean shouldRetry = true;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, FLEDGE_BACKGROUND_FETCH_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /**
     * Attempts to schedule the FLEDGE Background Fetch as a singleton periodic job if it is not
     * already scheduled.
     *
     * <p>The background fetch primarily updates custom audiences' ads and bidding data. It also
     * prunes the custom audience database of any expired data.
     */
    public static void scheduleIfNeeded(Context context, Flags flags, boolean forceSchedule) {
        if (!flags.getFledgeBackgroundFetchEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("FLEDGE background fetch is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        // Scheduling a job can be expensive, and forcing a schedule could interrupt a job that is
        // already in progress
        // TODO(b/221837833): Intelligently decide when to overwrite a scheduled job
        if ((jobScheduler.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID) == null) || forceSchedule) {
            schedule(context, flags);
            LoggerFactory.getFledgeLogger().d("Scheduled FLEDGE Background Fetch job");
        } else {
            LoggerFactory.getFledgeLogger()
                    .v("FLEDGE Background Fetch job already scheduled, skipping reschedule");
        }
    }

    /**
     * Actually schedules the FLEDGE Background Fetch as a singleton periodic job.
     *
     * <p>Split out from {@link #scheduleIfNeeded(Context, Flags, boolean)} for mockable testing
     * without pesky permissions.
     */
    @VisibleForTesting
    protected static void schedule(Context context, Flags flags) {
        if (!flags.getFledgeBackgroundFetchEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("FLEDGE background fetch is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(context, BackgroundFetchJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                flags.getFledgeBackgroundFetchJobPeriodMs(),
                                flags.getFledgeBackgroundFetchJobFlexMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }
}
