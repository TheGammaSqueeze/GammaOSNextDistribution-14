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
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;

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
import com.android.adservices.service.measurement.attribution.AttributionJobHandler.ProcessingResult;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportingJobService;
import com.android.adservices.service.measurement.util.JobLockHolder;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Future;

/**
 * Service for scheduling attribution jobs. The actual job execution logic is part of {@link
 * AttributionJobHandler}.
 */
public class AttributionJobService extends JobService {
    private static final int MEASUREMENT_ATTRIBUTION_JOB_ID =
            MEASUREMENT_ATTRIBUTION_JOB.getJobId();
    private static final ListeningExecutorService sBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();

    private Future mExecutorFuture;

    @Override
    public void onCreate() {
        LogUtil.d("AttributionJobService.onCreate");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AttributionJobService job because it's running in ExtServices on"
                            + " T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_ATTRIBUTION_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("AttributionJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("AttributionJobService.onStartJob");
        mExecutorFuture =
                sBackgroundExecutor.submit(
                        () -> {
                            ProcessingResult result = acquireLockAndProcessPendingAttributions();
                            LoggerFactory.getMeasurementLogger()
                                    .d("AttributionJobService finished processing [%s]", result);

                            final boolean shouldRetry =
                                    !ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED.equals(result);
                            final boolean isSuccessful = !ProcessingResult.FAILURE.equals(result);
                            AdServicesJobServiceLogger.getInstance(AttributionJobService.this)
                                    .recordJobFinished(
                                            MEASUREMENT_ATTRIBUTION_JOB_ID,
                                            isSuccessful,
                                            shouldRetry);

                            switch (result) {
                                case SUCCESS_ALL_RECORDS_PROCESSED:
                                    // Force scheduling to avoid concurrency issue
                                    scheduleIfNeeded(this, /* forceSchedule */ true);
                                    break;
                                case SUCCESS_WITH_PENDING_RECORDS:
                                    scheduleImmediately(AttributionJobService.this);
                                    break;
                                case FAILURE:
                                default:
                                    // Reschedule with back-off criteria specified when it was
                                    // scheduled
                                    jobFinished(params, /* wantsReschedule= */ true);
                            }

                            DebugReportingJobService.scheduleIfNeeded(
                                    getApplicationContext(), /* forceSchedule */ false);
                        });
        return true;
    }

    @VisibleForTesting
    ProcessingResult acquireLockAndProcessPendingAttributions() {
        final JobLockHolder lock = JobLockHolder.getInstance(ATTRIBUTION_PROCESSING);
        if (lock.tryLock()) {
            try {
                return processPendingAttributions();
            } finally {
                lock.unlock();
            }
        }
        LoggerFactory.getMeasurementLogger().d("AttributionJobService did not acquire the lock");
        // Another thread is already processing attribution. Returning success to not reschedule.
        return ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED;
    }

    @VisibleForTesting
    ProcessingResult processPendingAttributions() {
        return new AttributionJobHandler(
                        DatastoreManagerFactory.getDatastoreManager(getApplicationContext()),
                        new DebugReportApi(getApplicationContext(), FlagsFactory.getFlags()))
                .performPendingAttributions();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("AttributionJobService.onStopJob");
        boolean shouldRetry = true;
        if (mExecutorFuture != null) {
            shouldRetry = mExecutorFuture.cancel(/* mayInterruptIfRunning */ true);
        }
        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, MEASUREMENT_ATTRIBUTION_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedules {@link AttributionJobService} to observer {@link Trigger} content URI change. */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_ATTRIBUTION_JOB_ID,
                        new ComponentName(context, AttributionJobService.class))
                .addTriggerContentUri(
                        new JobInfo.TriggerContentUri(
                                TriggerContentProvider.TRIGGER_URI,
                                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(flags.getMeasurementAttributionJobTriggeringDelayMs())
                // Can't call addTriggerContentUri() on a persisted job
                .setPersisted(flags.getMeasurementAttributionJobPersisted())
                .build();
    }

    /**
     * Schedule Attribution Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobAttributionKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AttributionJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(MEASUREMENT_ATTRIBUTION_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        final JobInfo job = buildJobInfo(context, flags);
        if (forceSchedule || !job.equals(scheduledJob)) {
            schedule(jobScheduler, job);
            LoggerFactory.getMeasurementLogger().d("Scheduled AttributionJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("AttributionJobService already scheduled, skipping reschedule");
        }
    }

    @VisibleForTesting
    void scheduleImmediately(Context context) {
        if (FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("AttributionJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo job =
                new JobInfo.Builder(
                                MEASUREMENT_ATTRIBUTION_JOB_ID,
                                new ComponentName(context, AttributionJobService.class))
                        .build();

        schedule(jobScheduler, job);
        LoggerFactory.getMeasurementLogger()
                .d("AttributionJobService scheduled to run immediately");
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MEASUREMENT_ATTRIBUTION_JOB_ID, skipReason);
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
