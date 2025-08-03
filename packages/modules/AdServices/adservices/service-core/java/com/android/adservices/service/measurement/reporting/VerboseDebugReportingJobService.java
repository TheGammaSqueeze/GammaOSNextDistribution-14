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

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.VERBOSE_DEBUG_REPORTING;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB;

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
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Main service for scheduling verbose debug reporting jobs. The actual job execution logic is part
 * of {@link DebugReportingJobHandler }.
 */
public final class VerboseDebugReportingJobService extends JobService {
    private static final ListeningExecutorService sBlockingExecutor =
            AdServicesExecutors.getBlockingExecutor();
    static final int VERBOSE_DEBUG_REPORT_JOB_ID = MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB.getJobId();

    private Future mExecutorFuture;

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling VerboseDebugReportingJobService job because it's running in "
                            + "ExtServices on T+");
            return skipAndCancelBackgroundJob(params);
        }

        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(VERBOSE_DEBUG_REPORT_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobVerboseDebugReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("VerboseDebugReportingJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LoggerFactory.getMeasurementLogger().d("VerboseDebugReportingJobService.onStartJob");
        mExecutorFuture =
                sBlockingExecutor.submit(
                        () -> {
                            sendReports();
                            AdServicesJobServiceLogger.getInstance(
                                            VerboseDebugReportingJobService.this)
                                    .recordJobFinished(
                                            VERBOSE_DEBUG_REPORT_JOB_ID,
                                            /* isSuccessful */ true,
                                            /* shouldRetry*/ false);
                            jobFinished(params, /* wantsReschedule= */ false);
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("VerboseDebugReportingJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, VERBOSE_DEBUG_REPORT_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link VerboseDebugReportingJobService} */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    /**
     * Schedule Verbose Debug Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobVerboseDebugReportingKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .d("VerboseDebugReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(VERBOSE_DEBUG_REPORT_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled VerboseDebugReportingJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("VerboseDebugReportingJobService already scheduled, skipping reschedule");
        }
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        VERBOSE_DEBUG_REPORT_JOB_ID,
                        new ComponentName(context, VerboseDebugReportingJobService.class))
                .setRequiredNetworkType(
                        flags.getMeasurementVerboseDebugReportingJobRequiredNetworkType())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(VERBOSE_DEBUG_REPORT_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, /* wantsReschedule= */ false);

        // Returning false means that this job has completed its work.
        return false;
    }

    @VisibleForTesting
    void sendReports() {
        final JobLockHolder lock = JobLockHolder.getInstance(VERBOSE_DEBUG_REPORTING);
        if (lock.tryLock()) {
            try {
                DatastoreManager datastoreManager =
                        DatastoreManagerFactory.getDatastoreManager(getApplicationContext());
                new DebugReportingJobHandler(
                                datastoreManager,
                                FlagsFactory.getFlags(),
                                AdServicesLoggerImpl.getInstance(),
                                ReportingStatus.UploadMethod.REGULAR,
                                getApplicationContext())
                        .performScheduledPendingReports();
                return;
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger()
                .d("VerboseDebugReportingJobService did not acquire the lock");
    }

    @VisibleForTesting
    Future getFutureForTesting() {
        return mExecutorFuture;
    }
}
