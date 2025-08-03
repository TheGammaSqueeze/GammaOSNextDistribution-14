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

package com.android.adservices.service;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdServicesJobInfo.MAINTENANCE_JOB;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.FledgeMaintenanceTasksWorker;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.signals.SignalsMaintenanceTasksWorker;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Objects;

/** Maintenance job to clean up. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public final class MaintenanceJobService extends JobService {
    private static final int MAINTENANCE_JOB_ID = MAINTENANCE_JOB.getJobId();

    private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorker;

    private SignalsMaintenanceTasksWorker mSignalsMaintenanceTasksWorker;

    /** Injects a {@link FledgeMaintenanceTasksWorker to be used during testing} */
    @VisibleForTesting
    public void injectFledgeMaintenanceTasksWorker(
            @NonNull FledgeMaintenanceTasksWorker fledgeMaintenanceTasksWorker) {
        mFledgeMaintenanceTasksWorker = fledgeMaintenanceTasksWorker;
    }

    /** Injects a {@link SignalsMaintenanceTasksWorker to be used during testing} */
    @VisibleForTesting
    public void injectSignalsMaintenanceTasksWorker(
            @NonNull SignalsMaintenanceTasksWorker signalsMaintenanceTasksWorker) {
        mSignalsMaintenanceTasksWorker = signalsMaintenanceTasksWorker;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        // Always ensure that the first thing this job does is check if it should be running, and
        // cancel itself if it's not supposed to be.
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling MaintenanceJobService job because it's running in ExtServices on"
                            + " T+");
            return skipAndCancelBackgroundJob(params, /* skipReason= */ 0, /* doRecord= */ false);
        }

        Flags flags = FlagsFactory.getFlags();

        LogUtil.d("MaintenanceJobService.onStartJob");
        AdServicesJobServiceLogger.getInstance(this).recordOnStartJob(MAINTENANCE_JOB_ID);

        if (flags.getTopicsKillSwitch()
                && flags.getFledgeSelectAdsKillSwitch()
                && (!flags.getProtectedSignalsCleanupEnabled() || flags.getGlobalKillSwitch())) {
            LogUtil.e(
                    "All maintenance jobs are disabled, skipping and cancelling"
                            + " MaintenanceJobService");
            return skipAndCancelBackgroundJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON,
                    /* doRecord= */ true);
        }

        ListenableFuture<Void> appReconciliationFuture;
        if (flags.getTopicsKillSwitch()) {
            LogUtil.d("Topics API is disabled, skipping Topics Job");
            appReconciliationFuture = Futures.immediateFuture(null);
        } else {
            appReconciliationFuture =
                    Futures.submit(
                            () -> TopicsWorker.getInstance(this).reconcileApplicationUpdate(this),
                            AdServicesExecutors.getBackgroundExecutor());
        }

        ListenableFuture<Void> fledgeMaintenanceTasksFuture;
        if (flags.getFledgeSelectAdsKillSwitch()) {
            LogUtil.d("Ad Selection API is disabled, skipping Ad Selection Maintenance Job");
            fledgeMaintenanceTasksFuture = Futures.immediateFuture(null);
        } else {
            fledgeMaintenanceTasksFuture =
                    Futures.submit(
                            this::doAdSelectionDataMaintenanceTasks,
                            AdServicesExecutors.getBackgroundExecutor());
        }

        ListenableFuture<Void> protectedSignalsMaintenanceTasksFuture;
        if (!flags.getProtectedSignalsCleanupEnabled()) {
            LogUtil.d("Signals cleanup is disabled, skipping maintenance job");
            protectedSignalsMaintenanceTasksFuture = Futures.immediateFuture(null);
        } else {
            protectedSignalsMaintenanceTasksFuture =
                    Futures.submit(
                            this::doProtectedSignalsDataMaintenanceTasks,
                            AdServicesExecutors.getBackgroundExecutor());
        }

        ListenableFuture<List<Void>> futuresList =
                Futures.allAsList(
                        fledgeMaintenanceTasksFuture,
                        protectedSignalsMaintenanceTasksFuture,
                        appReconciliationFuture);

        Futures.addCallback(
                futuresList,
                new FutureCallback<List<Void>>() {
                    @Override
                    public void onSuccess(List<Void> result) {
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(MaintenanceJobService.this)
                                .recordJobFinished(
                                        MAINTENANCE_JOB_ID, /* isSuccessful= */ true, shouldRetry);

                        LogUtil.d("PP API jobs are done!");
                        jobFinished(params, shouldRetry);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        boolean shouldRetry = false;
                        AdServicesJobServiceLogger.getInstance(MaintenanceJobService.this)
                                .recordJobFinished(
                                        MAINTENANCE_JOB_ID, /* isSuccessful= */ false, shouldRetry);

                        LogUtil.e(
                                t, "Failed to handle MaintenanceJobService: " + params.getJobId());
                        jobFinished(params, shouldRetry);
                    }
                },
                directExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("MaintenanceJobService.onStopJob");

        // Tell JobScheduler not to reschedule the job because it's unknown at this stage if the
        // execution is completed or not to avoid executing the task twice.
        boolean shouldRetry = false;

        AdServicesJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, MAINTENANCE_JOB_ID, shouldRetry);
        return shouldRetry;
    }

    @VisibleForTesting
    static void schedule(
            Context context,
            @NonNull JobScheduler jobScheduler,
            long maintenanceJobPeriodMs,
            long maintenanceJobFlexMs) {
        final JobInfo job =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(context, MaintenanceJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(maintenanceJobPeriodMs, maintenanceJobFlexMs)
                        .build();

        jobScheduler.schedule(job);
        LogUtil.d("Scheduling maintenance job ...");
    }

    /**
     * Schedule Maintenance Job Service if needed: there is no scheduled job with same job
     * parameters.
     *
     * @param context the context
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @return a {@code boolean} to indicate if the service job is actually scheduled.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        Flags flags = FlagsFactory.getFlags();

        if (flags.getTopicsKillSwitch() && flags.getFledgeSelectAdsKillSwitch()) {
            LogUtil.e(
                    "Both Topics and Select Ads are disabled, skipping scheduling"
                            + " MaintenanceJobService");
            return false;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("Cannot fetch Job Scheduler!");
            return false;
        }

        long flagsMaintenanceJobPeriodMs = flags.getMaintenanceJobPeriodMs();
        long flagsMaintenanceJobFlexMs = flags.getMaintenanceJobFlexMs();

        JobInfo job = jobScheduler.getPendingJob(MAINTENANCE_JOB_ID);
        // Skip to reschedule the job if there is same scheduled job with same parameters.
        if (job != null && !forceSchedule) {
            long maintenanceJobPeriodMs = job.getIntervalMillis();
            long maintenanceJobFlexMs = job.getFlexMillis();

            if (flagsMaintenanceJobPeriodMs == maintenanceJobPeriodMs
                    && flagsMaintenanceJobFlexMs == maintenanceJobFlexMs) {
                LogUtil.d(
                        "Maintenance Job Service has been scheduled with same parameters, skip"
                                + " rescheduling!");
                return false;
            }
        }

        schedule(context, jobScheduler, flagsMaintenanceJobPeriodMs, flagsMaintenanceJobFlexMs);
        return true;
    }

    private boolean skipAndCancelBackgroundJob(
            final JobParameters params, int skipReason, boolean doRecord) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MAINTENANCE_JOB_ID);
        }

        if (doRecord) {
            AdServicesJobServiceLogger.getInstance(this)
                    .recordJobSkipped(MAINTENANCE_JOB_ID, skipReason);
        }

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    private FledgeMaintenanceTasksWorker getFledgeMaintenanceTasksWorker() {
        if (!Objects.isNull(mFledgeMaintenanceTasksWorker)) {
            return mFledgeMaintenanceTasksWorker;
        }
        mFledgeMaintenanceTasksWorker = FledgeMaintenanceTasksWorker.create(this);
        return mFledgeMaintenanceTasksWorker;
    }

    private SignalsMaintenanceTasksWorker getSignalsMaintenanceTasksWorker() {
        if (!Objects.isNull(mSignalsMaintenanceTasksWorker)) {
            return mSignalsMaintenanceTasksWorker;
        }
        mSignalsMaintenanceTasksWorker = SignalsMaintenanceTasksWorker.create(this);
        return mSignalsMaintenanceTasksWorker;
    }

    private void doAdSelectionDataMaintenanceTasks() {
        LogUtil.v("Performing Ad Selection maintenance tasks");
        getFledgeMaintenanceTasksWorker().clearExpiredAdSelectionData();
        getFledgeMaintenanceTasksWorker()
                .clearInvalidFrequencyCapHistogramData(this.getPackageManager());
        getFledgeMaintenanceTasksWorker().clearExpiredKAnonMessageEntities();
    }

    private void doProtectedSignalsDataMaintenanceTasks() {
        LogUtil.v("Performing protected signals maintenance tasks");
        getSignalsMaintenanceTasksWorker().clearInvalidProtectedSignalsData();
    }
}
