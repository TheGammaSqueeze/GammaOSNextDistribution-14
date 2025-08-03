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

package com.android.federatedcompute.services.scheduling;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.FederatedComputeJobUtil;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.statsd.joblogging.FederatedComputeJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;

public class DeleteExpiredJobService extends JobService {

    private static final String TAG = DeleteExpiredJobService.class.getSimpleName();

    private static final int DELETE_EXPIRED_JOB_ID = FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID;

    private final Injector mInjector;

    public DeleteExpiredJobService() {
        mInjector = new Injector();
    }

    @VisibleForTesting
    public DeleteExpiredJobService(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        ListeningExecutorService getExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }

        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return ODPAuthorizationTokenDao.getInstance(context);
        }

        FederatedTrainingTaskDao getTrainingTaskDao(Context context) {
            return FederatedTrainingTaskDao.getInstance(context);
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d(TAG, "DeleteExpiredJobService.onStartJob %d", params.getJobId());
        FederatedComputeJobServiceLogger.getInstance(this).recordOnStartJob(DELETE_EXPIRED_JOB_ID);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch is enabled, finishing job.");
            return FederatedComputeJobUtil.cancelAndFinishJob(
                    this,
                    params,
                    DELETE_EXPIRED_JOB_ID,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }
        ListenableFuture<Integer> deleteExpiredAuthTokenFuture =
                Futures.submit(
                        () ->
                                mInjector
                                        .getODPAuthorizationTokenDao(this)
                                        .deleteExpiredAuthorizationTokens(),
                        mInjector.getExecutor());
        ListenableFuture<Integer> deleteExpiredTaskHistoryFuture =
                Futures.submit(
                        () -> {
                            long deleteTime =
                                    mInjector.getClock().currentTimeMillis()
                                            - mInjector.getFlags().getTaskHistoryTtl();
                            return mInjector
                                    .getTrainingTaskDao(this)
                                    .deleteExpiredTaskHistory(deleteTime);
                        },
                        mInjector.getExecutor());
        ListenableFuture<List<Integer>> futuresList =
                Futures.allAsList(deleteExpiredAuthTokenFuture, deleteExpiredTaskHistoryFuture);
        Futures.addCallback(
                futuresList,
                new FutureCallback<List<Integer>>() {
                    @Override
                    public void onSuccess(List<Integer> result) {
                        LogUtil.d(TAG, "Deleted expired records %s", result.toString());
                        boolean wantsReschedule = false;
                        FederatedComputeJobServiceLogger.getInstance(DeleteExpiredJobService.this)
                                .recordJobFinished(
                                        DELETE_EXPIRED_JOB_ID,
                                        /* isSuccessful= */ true,
                                        wantsReschedule);
                        jobFinished(params, /* wantsReschedule= */ wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, t, "Exception encountered when deleting expired records");
                        boolean wantsReschedule = false;
                        FederatedComputeJobServiceLogger.getInstance(DeleteExpiredJobService.this)
                                .recordJobFinished(
                                        DELETE_EXPIRED_JOB_ID,
                                        /* isSuccessful= */ false,
                                        wantsReschedule);
                        jobFinished(params, /* wantsReschedule= */ wantsReschedule);
                    }
                },
                FederatedComputeExecutors.getLightweightExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d(TAG, "DeleteExpiredJobService.onStopJob %d", params.getJobId());
        boolean wantsReschedule = false;
        FederatedComputeJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, DELETE_EXPIRED_JOB_ID, wantsReschedule);
        return wantsReschedule;
    }

    /** Schedule the periodic deletion job if it is not scheduled. */
    public static boolean scheduleJobIfNeeded(Context context, Flags flags) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e(TAG, "Failed to get job scheduler from system service.");
            return false;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(DELETE_EXPIRED_JOB_ID);
        final JobInfo jobInfo =
                new JobInfo.Builder(
                                DELETE_EXPIRED_JOB_ID,
                                new ComponentName(context, DeleteExpiredJobService.class))
                        .setPeriodic(
                                flags.getAuthorizationTokenDeletionPeriodSeconds()
                                        * 1000) // convert to milliseconds
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build();

        if (!jobInfo.equals(scheduledJob)) {
            jobScheduler.schedule(jobInfo);
            LogUtil.d(TAG, "Scheduled job DeleteExpiredJobService id %d", DELETE_EXPIRED_JOB_ID);
            return true;
        } else {
            LogUtil.d(
                    TAG,
                    "Already scheduled job DeleteExpiredJobService id %d",
                    DELETE_EXPIRED_JOB_ID);
            return false;
        }
    }
}
