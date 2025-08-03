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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ATTRIBUTION_PROCESSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportingJobService;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Fallback attribution job. The actual job execution logic is part of {@link
 * AttributionJobHandler}.
 */
public class AttributionFallbackJobService extends JobService {
    private static final int MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID =
            MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId();
    private static final ListeningExecutorService sBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private Future mExecutorFuture;

    @Override
    public void onCreate() {
        LogUtil.d("AttributionFallbackJobService.onCreate");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AttributionFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);

        if (!FlagsFactory.getFlags().getMeasurementAttributionFallbackJobEnabled()) {
            LoggerFactory.getMeasurementLogger().e("AttributionFallbackJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("AttributionFallbackJobService.onStartJob");
        mExecutorFuture =
                sBackgroundExecutor.submit(
                        () -> {
                            processPendingAttributions();

                            DebugReportingJobService.scheduleIfNeeded(
                                    getApplicationContext(), /* forceSchedule */ false);

                            AdServicesJobServiceLogger.getInstance(
                                            AttributionFallbackJobService.this)
                                    .recordJobFinished(
                                            MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID,
                                            /* isSuccessful */ true,
                                            /* shouldRetry */ false);

                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @VisibleForTesting
    void processPendingAttributions() {
        final JobLockHolder lock = JobLockHolder.getInstance(ATTRIBUTION_PROCESSING);
        if (lock.tryLock()) {
            try {
                new AttributionJobHandler(
                                DatastoreManagerFactory.getDatastoreManager(
                                        getApplicationContext()),
                                new DebugReportApi(
                                        getApplicationContext(), FlagsFactory.getFlags()))
                        .performPendingAttributions();
                return;
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger()
                .d("AttributionFallbackJobService did not acquire the lock");
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("AttributionFallbackJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /**
     * Schedules {@link AttributionFallbackJobService} to observer {@link Trigger} content URI
     * change.
     */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID,
                        new ComponentName(context, AttributionFallbackJobService.class))
                .setPeriodic(flags.getMeasurementAttributionFallbackJobPeriodMs())
                .setPersisted(flags.getMeasurementAttributionFallbackJobPersisted())
                .build();
    }

    /**
     * Schedule Attribution Fallback Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getMeasurementAttributionFallbackJobEnabled()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AttributionFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled AttributionFallbackJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("AttributionFallbackJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
