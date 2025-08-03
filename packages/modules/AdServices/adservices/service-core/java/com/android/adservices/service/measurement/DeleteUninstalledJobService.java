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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;

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
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Service for deleting data for uninstalled packages that the package change receiver has missed.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public final class DeleteUninstalledJobService extends JobService {
    private static final int MEASUREMENT_DELETE_UNINSTALLED_JOB_ID =
            MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId();
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling DeleteUninstalledJobService job because it's running in ExtServices"
                            + " on T+");
            return skipAndCancelBackgroundJob(params, /* skipReason=*/ 0, /* doRecord=*/ false);
        }

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStartJob(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID);

        if (FlagsFactory.getFlags().getMeasurementJobDeleteUninstalledKillSwitch()) {
            LoggerFactory.getMeasurementLogger().e("DeleteUninstalledJobService is disabled");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord=*/ true);
        }

        LoggerFactory.getMeasurementLogger().d("DeleteUninstalledJobService.onStartJob");
        sBackgroundExecutor.execute(
                () -> {
                    MeasurementImpl.getInstance(this).deleteAllUninstalledMeasurementData();

                    boolean shouldRetry = false;
                    AdServicesJobServiceLogger.getInstance(DeleteUninstalledJobService.this)
                            .recordJobFinished(
                                    MEASUREMENT_DELETE_UNINSTALLED_JOB_ID,
                                    /* isSuccessful */ true,
                                    shouldRetry);
                    jobFinished(params, shouldRetry);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LoggerFactory.getMeasurementLogger().d("DeleteUninstalledJobService.onStopJob");
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, MEASUREMENT_DELETE_UNINSTALLED_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    /** Schedule the job. */
    @VisibleForTesting
    static void schedule(JobScheduler jobScheduler, JobInfo jobInfo) {
        jobScheduler.schedule(jobInfo);
    }

    /**
     * Schedule Delete Uninstalled Job Service if it is not already scheduled.
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementJobDeleteUninstalledKillSwitch()) {
            LoggerFactory.getMeasurementLogger()
                    .e("DeleteUninstalledJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LoggerFactory.getMeasurementLogger().e("JobScheduler not found");
            return;
        }

        final JobInfo scheduledJob =
                jobScheduler.getPendingJob(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling.
        JobInfo jobInfo = buildJobInfo(context, flags);
        if (forceSchedule || !jobInfo.equals(scheduledJob)) {
            schedule(jobScheduler, jobInfo);
            LoggerFactory.getMeasurementLogger().d("Scheduled DeleteUninstalledJobService");
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("DeleteUninstalledJobService already scheduled, skipping reschedule");
        }
    }

    private static JobInfo buildJobInfo(Context context, Flags flags) {
        return new JobInfo.Builder(
                        MEASUREMENT_DELETE_UNINSTALLED_JOB_ID,
                        new ComponentName(context, DeleteUninstalledJobService.class))
                .setPeriodic(flags.getMeasurementDeleteUninstalledJobPeriodMs())
                .setPersisted(flags.getMeasurementDeleteUninstalledJobPersisted())
                .build();
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, /* wantsReschedule */ false);

        // Returning false meas that this job has completed its work.
        return false;
    }
}
