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

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.DEBUG_REPORTING;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORT_JOB;

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
 * Main service for scheduling debug reporting jobs. The actual job execution logic is part of
 * {@link EventReportingJobHandler } and {@link AggregateReportingJobHandler}
 */
public final class DebugReportingJobService extends JobService {
    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    static final int DEBUG_REPORT_JOB_ID = MEASUREMENT_DEBUG_REPORT_JOB.getJobId();
    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling DebugReportingJobService job because it's running in ExtServices on"
                            + " T+");
            return skipAndCancelBackgroundJob(params);
        }

        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(DEBUG_REPORT_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobDebugReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("DebugReportingJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LoggerFactory.getMeasurementLogger().d("DebugReportingJobService.onStartJob");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            sendReports();
                            AdServicesJobServiceLogger.getInstance(DebugReportingJobService.this)
                                    .recordJobFinished(
                                            DEBUG_REPORT_JOB_ID,
                                            /* isSuccessful */ true,
                                            /* shouldRetry*/ false);
                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("DebugReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, DEBUG_REPORT_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link DebugReportingJobService} */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo job) {
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Debug Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobDebugReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .d("DebugReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJobInfo = jobScheduler.getPendingJob(DEBUG_REPORT_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo job = buildJobInfo(context, flags);
        if (forceSchedule || !job.equals(scheduledJobInfo)) {
            schedule(jobScheduler, job);
            LoggerFactory.getMeasurementLogger().d("Scheduled DebugReportingJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("DebugReportingJobService already scheduled, skipping reschedule");
        }
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        DEBUG_REPORT_JOB_ID,
                        new ComponentName(context, DebugReportingJobService.class))
                .setRequiredNetworkType(flags.getMeasurementDebugReportingJobRequiredNetworkType())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(DEBUG_REPORT_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, /* wantsReschedule= */ false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    void sendReports() {
        final JobLockHolder lock = JobLockHolder.getInstance(DEBUG_REPORTING);
        if (lock.tryLock()) {
            try {
                DatastoreManager datastoreManager =
                        DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
                new EventReportingJobHandler(
                                datastoreManager,
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.DEBUG_EVENT,
                                ReportingStatus.UploadMethod.REGULAR,
                                getApplicationContext())
                        .setIsDebugInstance(true)
                        .performScheduledPendingReportsInWindow(0, 0);
                new AggregateReportingJobHandler(
                                datastoreManager,
                                new AggregateEncryptionKeyManager(
                                        datastoreManager, getApplicationContext()),
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.ReportType.DEBUG_AGGREGATE,
                                ReportingStatus.UploadMethod.REGULAR,
                                getApplicationContext())
                        .setIsDebugInstance(true)
                        .performScheduledPendingReportsInWindow(0, 0);
                return;
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger().d("DebugReportingJobService did not acquire the lock");
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
