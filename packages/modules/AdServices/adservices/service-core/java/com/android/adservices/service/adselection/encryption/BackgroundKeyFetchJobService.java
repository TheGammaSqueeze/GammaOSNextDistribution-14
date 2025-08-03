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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB;

import android.annotation.RequiresApi;
import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

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
 * Background fetch for Fledge encryption key fetch from the Key Management Servers and periodic
 * deletion of expired keys.
 */
// TODO(b/269798827): Enable for R.

@SuppressLint("LineLength")
@RequiresApi(Build.VERSION_CODES.S)
public class BackgroundKeyFetchJobService extends JobService {
    private static final int FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID =
            FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling BackgroundKeyFetchJobService job because it's running in "
                            + " ExtServices on T+");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS);
        }

        LoggerFactory.getFledgeLogger().d("BackgroundKeyFetchJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID);

        if (FlagsFactory.getFlags().getFledgeAuctionServerKillSwitch()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE Ad Selection Data API is disabled ; skipping and cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }

        if (!FlagsFactory.getFlags().getFledgeAuctionServerBackgroundKeyFetchJobEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE background key fetch is disabled; skipping and cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }

        // Skip the execution and cancel the job if user consent is revoked.
        // Use the per-API consent with GA UX.
        if (!ConsentManager.getInstance().getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            LoggerFactory.getFledgeLogger()
                    .d("User Consent is revoked ; skipping and cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED);
        }

        // TODO(b/235841960): Consider using com.android.adservices.service.stats.Clock instead of
        //  Java Clock
        Instant jobStartTime = Clock.systemUTC().instant();
        LoggerFactory.getFledgeLogger()
                .d("Starting FLEDGE key fetch job at %s", jobStartTime.toString());

        BackgroundKeyFetchWorker.getInstance(this)
                .runBackgroundKeyFetch()
                .addCallback(
                        new FutureCallback<Void>() {
                            // Never manually reschedule the background key fetch job, since it is
                            // already scheduled periodically and should try again as per its
                            // schedule.
                            @Override
                            public void onSuccess(Void result) {
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                BackgroundKeyFetchJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
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
                                                    "FLEDGE key background fetch interrupted while"
                                                            + " waiting for key fetch payload");
                                } else if (t instanceof ExecutionException) {
                                    LoggerFactory.getFledgeLogger()
                                            .e(
                                                    t,
                                                    "FLEDGE key background fetch failed due to"
                                                            + " internal error");
                                } else if (t instanceof TimeoutException) {
                                    LoggerFactory.getFledgeLogger()
                                            .e(t, "FLEDGE background key fetch timeout exceeded");
                                } else {
                                    LoggerFactory.getFledgeLogger()
                                            .e(
                                                    t,
                                                    "FLEDGE background key fetch failed due to"
                                                            + " unexpected error");
                                }

                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                BackgroundKeyFetchJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                                /* isSuccessful= */ false,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }
                        },
                        AdServicesExecutors.getLightWeightExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getFledgeLogger().d("BackgroundKeyFetchJobService.onStopJob");
        BackgroundKeyFetchWorker.getInstance(this).stopWork();

        boolean shouldRetry = true;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params, FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    private boolean skipAndCancelKeyFetchJob(final JobParameters params, int skipReason) {
        this.getSystemService(JobScheduler.class)
                .cancel(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID);

        AdServicesJobServiceLogger.getInstance(this)
                .recordJobSkipped(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID, skipReason);

        jobFinished(params, false);
        return false;
    }

    /**
     * Attempts to schedule the Key Background Fetch as a singleton periodic job if it is not
     * already scheduled.
     *
     * <p>The key fetch background job fetches fresh encryption key, persists them to
     * EncryptionKeyDb and deletes expired keys.
     */
    public static void scheduleIfNeeded(Context context, Flags flags, boolean forceSchedule) {
        if (!flags.getFledgeAuctionServerBackgroundKeyFetchJobEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("Background key fetch is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        // Scheduling a job can be expensive, and forcing a schedule could interrupt a job that is
        // already in progress
        // TODO(b/221837833): Intelligently decide when to overwrite a scheduled job
        if ((jobScheduler.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID) == null)
                || forceSchedule) {
            schedule(context, flags);
            LoggerFactory.getFledgeLogger().d("Scheduled Background Key Fetch job");
        } else {
            LoggerFactory.getFledgeLogger()
                    .v("Background Key Fetch job already scheduled, skipping reschedule");
        }
    }

    /**
     * Actually schedules the Background Key Fetch as a singleton periodic job.
     *
     * <p>Split out from {@link #scheduleIfNeeded(Context, Flags, boolean)} for mockable testing
     * without pesky permissions.
     */
    @VisibleForTesting
    protected static void schedule(Context context, Flags flags) {
        if (!flags.getFledgeAuctionServerBackgroundKeyFetchJobEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("Background key fetch is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(context, BackgroundKeyFetchJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                flags.getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs(),
                                flags.getFledgeAuctionServerBackgroundKeyFetchJobFlexMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }
}
