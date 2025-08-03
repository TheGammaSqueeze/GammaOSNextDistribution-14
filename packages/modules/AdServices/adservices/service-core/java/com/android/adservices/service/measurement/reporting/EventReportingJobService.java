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

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.EVENT_REPORTING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;

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
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Main service for scheduling event reporting jobs. The actual job execution logic is part of
 * {@link EventReportingJobHandler}
 */
public final class EventReportingJobService extends JobService {
    private static final int MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID =
            MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId();

    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling EventReportingJobService job because it's running in ExtServices on"
                            + " T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobEventReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("EventReportingJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("EventReportingJobService.onStartJob: ");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            processPendingReports();

                            AdServicesJobServiceLogger.getInstance(EventReportingJobService.this)
                                    .recordJobFinished(
                                            MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID,
                                            /* isSuccessful= */ true,
                                            /* shouldRetry= */ false);

                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @VisibleForTesting
    void processPendingReports() {
        final JobLockHolder lock = JobLockHolder.getInstance(EVENT_REPORTING);
        if (lock.tryLock()) {
            try {
                long maxEventReportUploadRetryWindowMs =
                        FlagsFactory.getFlags().getMeasurementMaxEventReportUploadRetryWindowMs();
                new EventReportingJobHandler(
                                DatastoreManagerFactory.getDatastoreManager(
                                        getApplicationContext()),
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.EVENT,
                                ReportingStatus.UploadMethod.REGULAR,
                                getApplicationContext())
                        .performScheduledPendingReportsInWindow(
                                System.currentTimeMillis() - maxEventReportUploadRetryWindowMs,
                                System.currentTimeMillis());
                return;
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger().d("EventReportingJobService did not acquire the lock");
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("EventReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link EventReportingJobService} */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Event Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobEventReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .d("EventReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled EventReportingJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("EventReportingJobService already scheduled, skipping reschedule");
        }
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID,
                        new ComponentName(context, EventReportingJobService.class))
                .setRequiresBatteryNotLow(
                        flags.getMeasurementEventReportingJobRequiredBatteryNotLow())
                .setRequiredNetworkType(flags.getMeasurementEventReportingJobRequiredNetworkType())
                .setPeriodic(flags.getMeasurementEventMainReportingJobPeriodMs())
                .setPersisted(flags.getMeasurementEventReportingJobPersisted())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID, skipReason);
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
