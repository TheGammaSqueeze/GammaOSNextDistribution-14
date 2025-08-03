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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB;

import android.annotation.SuppressLint;
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
 * Debug report sender for FLEDGE Select Ads API, executing periodic pinging of debug reports and
 * cleanup..
 */
// TODO(b/269798827): Enable for R.
@SuppressLint("LineLength")
@RequiresApi(Build.VERSION_CODES.S)
public class DebugReportSenderJobService extends JobService {
    private static final int FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID =
            FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling DebugReportSenderJobService job because it's running in "
                            + " ExtServices on T+");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS,
                    false);
        }
        LoggerFactory.getFledgeLogger().d("DebugReportSenderJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID);

        if (FlagsFactory.getFlags().getFledgeSelectAdsKillSwitch()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE Ad Selection API is disabled ; skipping and cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    true);
        }

        if (!FlagsFactory.getFlags().getFledgeEventLevelDebugReportingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d(
                            "FLEDGE Ad Selection Debug Reporting  is disabled ; skipping and"
                                    + " cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    true);
        }

        // Skip the execution and cancel the job if user consent is revoked.
        // Use the per-API consent with GA UX.
        if (!ConsentManager.getInstance().getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            LoggerFactory.getFledgeLogger()
                    .d("User Consent is revoked ; skipping and cancelling job");
            return skipAndCancelKeyFetchJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED,
                    true);
        }

        // TODO(b/235841960): Consider using com.android.adservices.service.stats.Clock instead of
        //  Java Clock
        Instant jobStartTime = Clock.systemUTC().instant();
        LoggerFactory.getFledgeLogger()
                .d(
                        "Starting FLEDGE DebugReportSenderJobService job at %s",
                        jobStartTime.toString());

        DebugReportSenderWorker.getInstance(this)
                .runDebugReportSender()
                .addCallback(
                        new FutureCallback<Void>() {
                            // Never manually reschedule the background fetch job, since it is
                            // already scheduled periodically and should try again multiple times
                            // per day
                            @Override
                            public void onSuccess(Void result) {
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                DebugReportSenderJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID,
                                                /* isSuccessful= */ true,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                logExceptionMessage(t);
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                DebugReportSenderJobService.this)
                                        .recordJobFinished(
                                                FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID,
                                                /* isSuccessful= */ false,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }
                        },
                        AdServicesExecutors.getLightWeightExecutor());
        return true;
    }

    private void logExceptionMessage(Throwable t) {
        if (t instanceof InterruptedException) {
            LoggerFactory.getFledgeLogger()
                    .e(t, "FLEDGE DebugReport Sender JobService interrupted");
        } else if (t instanceof ExecutionException) {
            LoggerFactory.getFledgeLogger()
                    .e(t, "FLEDGE DebugReport Sender JobService failed due to internal error");
        } else if (t instanceof TimeoutException) {
            LoggerFactory.getFledgeLogger()
                    .e(t, "FLEDGE DebugReport Sender JobService timeout exceeded");
        } else {
            LoggerFactory.getFledgeLogger()
                    .e(t, "FLEDGE DebugReport Sender JobService failed due to unexpected error");
        }
    }

    private boolean skipAndCancelKeyFetchJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        this.getSystemService(JobScheduler.class)
                .cancel(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID);
        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID, skipReason);
        }
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getFledgeLogger().d("DebugReportSenderJobService.onStopJob");
        DebugReportSenderWorker.getInstance(this).stopWork();

        boolean shouldRetry = true;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params, FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /**
     * Attempts to schedule the FLEDGE Ad Selection debug report sender as a singleton periodic job
     * if it is not already scheduled.
     *
     * <p>The debug report sender job primarily sends debug reports generated for ad selections. It
     * also prunes the ad selection debug report database of any expired data.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getFledgeEventLevelDebugReportingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("FLEDGE Ad selection Debug reporting is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        // Scheduling a job can be expensive, and forcing a schedule could interrupt a job that is
        // already in progress
        // TODO(b/221837833): Intelligently decide when to overwrite a scheduled job
        if ((jobScheduler.getPendingJob(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID) == null)
                || forceSchedule) {
            schedule(context, flags);
            LoggerFactory.getFledgeLogger()
                    .d("Scheduled FLEDGE ad selection Debug report sender job");
        } else {
            LoggerFactory.getFledgeLogger()
                    .v(
                            "FLEDGE ad selection Debug report sender job already scheduled,"
                                    + " skipping reschedule");
        }
    }

    /**
     * Actually schedules the FLEDGE Ad Selection Debug Report sender job as a singleton periodic
     * job.
     *
     * <p>Split out from {@link #scheduleIfNeeded(Context, boolean)} for mockable testing without
     * pesky permissions.
     */
    @VisibleForTesting
    protected static void schedule(Context context, Flags flags) {
        if (!flags.getFledgeEventLevelDebugReportingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("FLEDGE Ad selection Debug reporting is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(context, DebugReportSenderJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                flags.getFledgeDebugReportSenderJobPeriodMs(),
                                flags.getFledgeDebugReportSenderJobFlexMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }
}
