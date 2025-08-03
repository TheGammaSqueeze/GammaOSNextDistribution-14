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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.AGGREGATE_REPORTING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Main service for scheduling aggregate reporting jobs. The actual job execution logic is part of
 * {@link AggregateReportingJobHandler}
 */
public final class AggregateFallbackReportingJobService extends JobService {
    private static final int MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID =
            MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB.getJobId();

    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AggregateFallbackReportingJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobAggregateFallbackReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AggregateFallbackReportingJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("AggregateFallbackReportingJobService.onStartJob");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            processPendingReports();

                            AdServicesJobServiceLogger.getInstance(
                                            AggregateFallbackReportingJobService.this)
                                    .recordJobFinished(
                                            MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID,
                                            /* isSuccessful= */ true,
                                            /* shouldRetry= */ false);

                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @VisibleForTesting
    void processPendingReports() {
        final JobLockHolder lock = JobLockHolder.getInstance(AGGREGATE_REPORTING);
        if (lock.tryLock()) {
            try {
                final long windowStartTime =
                        System.currentTimeMillis()
                                - FlagsFactory.getFlags()
                                        .getMeasurementMaxAggregateReportUploadRetryWindowMs();
                final long windowEndTime =
                        System.currentTimeMillis()
                                - AdServicesConfig
                                        .getMeasurementAggregateMainReportingJobPeriodMs();
                DatastoreManager datastoreManager =
                        DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
                new AggregateReportingJobHandler(
                                datastoreManager,
                                new AggregateEncryptionKeyManager(
                                        datastoreManager, getApplicationContext()),
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.AGGREGATE,
                                ReportingStatus.UploadMethod.FALLBACK,
                                getApplicationContext())
                        .performScheduledPendingReportsInWindow(windowStartTime, windowEndTime);
                return;
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger()
                .d("AggregateFallbackReportingJobService did not acquire the lock");
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("AggregateFallbackReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params, MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link AggregateFallbackReportingJobService} */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID,
                        new ComponentName(context, AggregateFallbackReportingJobService.class))
                .setRequiredNetworkType(
                        flags.getMeasurementAggregateFallbackReportingJobRequiredNetworkType())
                .setRequiresBatteryNotLow(
                        flags.getMeasurementAggregateFallbackReportingJobRequiredBatteryNotLow())
                .setPeriodic(flags.getMeasurementAggregateFallbackReportingJobPeriodMs())
                .setPersisted(flags.getMeasurementAggregateFallbackReportingJobPersisted())
                .build();
    }

    /**
     * Schedule Aggregate Fallback Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobAggregateFallbackReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .d("AggregateFallbackReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger()
                    .d("Scheduled AggregateFallbackReportingJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AggregateFallbackReportingJobService already scheduled, skipping"
                                    + " reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID, skipReason);
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
