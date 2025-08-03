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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.spe.AdServicesJobInfo.SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB;

import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
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
 * A periodic job that serves as scheduler for handling {@link ScheduleCustomAudienceUpdateRequest}.
 * When the job is run, it's {@link ScheduleCustomAudienceUpdateWorker} is triggered which initiates
 * update for pending delayed events.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class ScheduleCustomAudienceUpdateJobService extends JobService {

    private static final int SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID =
            SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB.getJobId();

    @Override
    public boolean onStartJob(JobParameters params) {

        // If job is not supposed to be running, cancel itself.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling ScheduleCustomAudienceUpdate job because it's running in ExtServices"
                            + " on T+");
            return skipAndCancelBackgroundJob(
                    params,
                    /* skipReason=*/ AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS,
                    /* doRecord=*/ false);
        }

        LoggerFactory.getFledgeLogger().d("ScheduleCustomAudienceUpdateJobService.onStartJob");

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID);

        if (!FlagsFactory.getFlags().getFledgeScheduleCustomAudienceUpdateEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d(
                            "FLEDGE Schedule Custom Audience Update API is disabled ; skipping and"
                                    + " cancelling job");
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

        ScheduleCustomAudienceUpdateWorker updateWorker =
                ScheduleCustomAudienceUpdateWorker.getInstance(this);
        updateWorker
                .updateCustomAudience()
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LoggerFactory.getFledgeLogger()
                                        .d("Schedule Custom Audience Update job completed");

                                boolean shouldRetry = false;
                                AdServicesJobServiceLogger.getInstance(
                                                ScheduleCustomAudienceUpdateJobService.this)
                                        .recordJobFinished(
                                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                                /* isSuccessful= */ true,
                                                shouldRetry);

                                jobFinished(params, shouldRetry);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                boolean shouldRetry = false;
                                LoggerFactory.getFledgeLogger()
                                        .e(t, "Schedule Custom Audience Update job worker failed");
                                AdServicesJobServiceLogger.getInstance(
                                                ScheduleCustomAudienceUpdateJobService.this)
                                        .recordJobFinished(
                                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
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
        LoggerFactory.getFledgeLogger().d("ScheduleCustomAudienceUpdateJobService.onStopJob");
        ScheduleCustomAudienceUpdateWorker.getInstance(this).stopWork();

        boolean shouldRetry = true;
        AdServicesJobServiceLogger.getInstance(ScheduleCustomAudienceUpdateJobService.this)
                .recordOnStopJob(
                        params, SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID, shouldRetry);

        return shouldRetry;
    }

    /**
     * Attempts to schedule the update Custom Audience job as a singleton job if it is not already
     * scheduled.
     */
    public static void scheduleIfNeeded(Context context, Flags flags, boolean forceSchedule) {
        LoggerFactory.getFledgeLogger()
                .v(
                        "Attempting to schedule job:%s if needed",
                        SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID);

        if (!flags.getFledgeScheduleCustomAudienceUpdateEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .d(
                            "FLEDGE Schedule Custom Audience Update API is disabled ; skipping and"
                                    + " cancelling job");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if ((jobScheduler.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID) == null)
                || forceSchedule) {
            schedule(context, flags);
        } else {
            LoggerFactory.getFledgeLogger()
                    .v(
                            "FLEDGE Schedule Custom Audience Update job already scheduled, skipping"
                                    + " reschedule");
        }
        // TODO(b/267651517) Jobs should be rescheduled if the job-params get updated
    }

    /**
     * Actually schedules the Update Custom Audience job as a singleton job.
     *
     * <p>Split out from {@link #scheduleIfNeeded(Context, Flags, boolean)} for mockable testing
     */
    @VisibleForTesting
    protected static void schedule(Context context, Flags flags) {
        if (!flags.getFledgeScheduleCustomAudienceUpdateEnabled()) {
            LoggerFactory.getFledgeLogger()
                    .v(
                            "FLEDGE Schedule Custom Audience Update API is disabled;"
                                    + " skipping schedule");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        context, ScheduleCustomAudienceUpdateJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPeriodic(
                                flags.getFledgeScheduleCustomAudienceUpdateJobPeriodMs(),
                                flags.getFledgeScheduleCustomAudienceUpdateJobFlexMs())
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(
                            SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID, skipReason);
        }

        jobFinished(params, false);
        return false;
    }
}
