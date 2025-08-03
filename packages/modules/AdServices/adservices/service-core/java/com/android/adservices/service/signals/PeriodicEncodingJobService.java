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

package com.android.adservices.service.signals;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;

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

/**
 * Periodic encoding background job, periodically encodes raw protected signals into encoded
 * payloads. Also cleans up stale encoding logic and encoded results.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class PeriodicEncodingJobService extends JobService {

    private static final int PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID =
            PERIODIC_SIGNALS_ENCODING_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {

        // If job is not supposed to be running, cancel itself.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling PeriodicEncodingJobService job because it's running in ExtServices"
                            + " on T+");
            return skipAndCancelBackgroundJob(
                    params,
                    /* skipReason=*/ AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS,
                    /* doRecord=*/ false);
        }

        LoggerFactory.getFledgeLogger().d("PeriodicEncodingJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);

        if (!FlagsFactory.getFlags().getProtectedSignalsPeriodicEncodingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE periodic encoding is disabled; skipping and cancelling job");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        if (!FlagsFactory.getFlags().getProtectedSignalsEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE Protected Signals API is disabled ; skipping and cancelling job");
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

        PeriodicEncodingJobWorker encodingWorker = PeriodicEncodingJobWorker.getInstance(this);
        encodingWorker
                .encodeProtectedSignals()
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LoggerFactory.getFledgeLogger()
                                        .d("PeriodicEncodingJobService encoding completed");

                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                PeriodicEncodingJobService.this)
                                        .recordJobFinished(
                                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                                /* isSuccessful= */ true,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                PeriodicEncodingJobService.this)
                                        .recordJobFinished(
                                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
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
        LoggerFactory.getFledgeLogger().d("PeriodicEncodingJobService.onStopJob");
        PeriodicEncodingJobWorker.getInstance(this).stopWork();

        boolean shouldRetry = true;
        AdServicesJobServiceLogger.getInstance(PeriodicEncodingJobService.this)
                .recordOnStopJob(params, PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID, shouldRetry);

        return shouldRetry;
    }

    /**
     * Attempts to schedule the Periodic encoding as a singleton job if it is not already scheduled.
     */
    public static void scheduleIfNeeded(Context context, Flags flags, boolean forceSchedule) {
        LoggerFactory.getFledgeLogger()
                .v(
                        "Attempting to schedule job:%s if needed",
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);
        if (!flags.getProtectedSignalsPeriodicEncodingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("Protected Signals periodic encoding is disabled; skipping schedule");
            return;
        }

        if (!flags.getProtectedSignalsEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d("FLEDGE Protected Signals API is disabled ; skipping and cancelling job");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if ((jobScheduler.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID) == null)
                || forceSchedule) {
            schedule(context, flags);
        } else {
            LoggerFactory.getFledgeLogger()
                    .v(
                            "Protected Signals periodic encoding job already scheduled, skipping "
                                    + "reschedule");
        }
        // TODO(b/267651517) Jobs should be rescheduled if the job-params get updated
    }

    /**
     * Actually schedules the Periodic Encoding as a singleton periodic job.
     *
     * <p>Split out from {@link #scheduleIfNeeded(Context, Flags, boolean)} for mockable testing
     */
    @VisibleForTesting
    protected static void schedule(Context context, Flags flags) {
        if (!flags.getProtectedSignalsPeriodicEncodingEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v("Protected Signals periodic encoding is disabled; skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(context, PeriodicEncodingJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                flags.getProtectedSignalPeriodicEncodingJobPeriodMs(),
                                flags.getProtectedSignalsPeriodicEncodingJobFlexMs())
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID, skipReason);
        }

        jobFinished(params, false);
        return false;
    }
}
