/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.encryption;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.FederatedComputeJobUtil;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.statsd.joblogging.FederatedComputeJobServiceLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class BackgroundKeyFetchJobService extends JobService {
    private static final String TAG = BackgroundKeyFetchJobService.class.getSimpleName();

    private static final int ENCRYPTION_KEY_FETCH_JOB_ID =
            FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID;

    static class Injector {
        ListeningExecutorService getExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }

        ListeningExecutorService getLightWeightExecutor() {
            return FederatedComputeExecutors.getLightweightExecutor();
        }

        FederatedComputeEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return FederatedComputeEncryptionKeyManager.getInstance(context);
        }
    }

    private final Injector mInjector;

    public BackgroundKeyFetchJobService() {
        mInjector = new Injector();
    }

    @VisibleForTesting
    BackgroundKeyFetchJobService(Injector injector) {
        mInjector = injector;
    }

    /** Runs the background key fetch and persist keys job. The method is for testing only. */
    @VisibleForTesting
    public void run(JobParameters params) {
        var unused = Futures.submit(() -> onStartJob(params), mInjector.getExecutor());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d(TAG, "BackgroundKeyFetchJobService.onStartJob %d", params.getJobId());
        FederatedComputeJobServiceLogger.getInstance(this)
                .recordOnStartJob(ENCRYPTION_KEY_FETCH_JOB_ID);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch enabled, finishing job.");
            return FederatedComputeJobUtil.cancelAndFinishJob(this, params,
                    ENCRYPTION_KEY_FETCH_JOB_ID,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }
        mInjector
                .getEncryptionKeyManager(this)
                .fetchAndPersistActiveKeys(FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION,
                        /* isScheduledJob= */ true)
                .addCallback(
                        new FutureCallback<List<FederatedComputeEncryptionKey>>() {
                            @Override
                            public void onSuccess(
                                    List<FederatedComputeEncryptionKey>
                                            federatedComputeEncryptionKeys) {
                                LogUtil.d(
                                        TAG,
                                        "BackgroundKeyFetchJobService %d is done, fetched %d keys",
                                        params.getJobId(),
                                        federatedComputeEncryptionKeys.size());
                                boolean wantsReschedule = false;
                                FederatedComputeJobServiceLogger.getInstance(
                                        BackgroundKeyFetchJobService.this)
                                        .recordJobFinished(
                                                ENCRYPTION_KEY_FETCH_JOB_ID,
                                                /* isSuccessful= */ true,
                                                wantsReschedule);
                                jobFinished(params, wantsReschedule);
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                LogUtil.e(
                                        TAG,
                                        "Failed to run job %d to fetch key and delete expired keys",
                                        params.getJobId());
                                if (throwable instanceof ExecutionException) {
                                    LogUtil.e(
                                            TAG,
                                            "Background key fetch failed due to internal error");
                                } else if (throwable instanceof TimeoutException) {
                                    LogUtil.e(
                                            TAG,
                                            "Background key fetch failed due to timeout error");
                                } else if (throwable instanceof InterruptedException) {
                                    LogUtil.e(
                                            TAG,
                                            "Background key fetch failed due to interruption "
                                            + "error");
                                } else if (throwable instanceof IllegalArgumentException) {
                                    LogUtil.e(
                                            TAG,
                                            "Background key fetch failed due to illegal argument "
                                            + "error");
                                } else {
                                    LogUtil.e(
                                            TAG,
                                            "Background key fetch failed due to unexpected error");
                                }
                                boolean wantsReschedule = false;
                                FederatedComputeJobServiceLogger.getInstance(
                                        BackgroundKeyFetchJobService.this)
                                        .recordJobFinished(
                                                ENCRYPTION_KEY_FETCH_JOB_ID,
                                                /* isSuccessful= */ false,
                                                wantsReschedule);
                                jobFinished(params, wantsReschedule);
                            }
                        },
                        mInjector.getLightWeightExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d(TAG, "BackgroundKeyFetchJobService.onStopJob %d", params.getJobId());
        boolean wantsReschedule = false;
        FederatedComputeJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params,
                        ENCRYPTION_KEY_FETCH_JOB_ID,
                        wantsReschedule);
        return wantsReschedule;
    }

    /** Schedule the periodic background key fetch and delete job if it is not scheduled. */
    public static boolean scheduleJobIfNeeded(Context context, Flags flags) {
        if (!flags.getEnableBackgroundEncryptionKeyFetch()) {
            LogUtil.d(
                    TAG,
                    "Schedule encryption key job fetch is not enable in flags.");
            return false;
        }
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e(TAG, "Failed to get job scheduler from system service.");
            return false;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(ENCRYPTION_KEY_FETCH_JOB_ID);
        final JobInfo jobInfo =
                new JobInfo.Builder(
                                ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(context, BackgroundKeyFetchJobService.class))
                        .setPeriodic(
                                flags.getEncryptionKeyFetchPeriodSeconds()
                                        * 1000) // convert to milliseconds
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build();

        if (!jobInfo.equals(scheduledJob)) {
            jobScheduler.schedule(jobInfo);
            LogUtil.d(
                    TAG,
                    "Scheduled job BackgroundKeyFetchJobService id %d",
                    ENCRYPTION_KEY_FETCH_JOB_ID);
            return true;
        } else {
            LogUtil.d(
                    TAG,
                    "Already scheduled job BackgroundKeyFetchJobService id %d",
                    ENCRYPTION_KEY_FETCH_JOB_ID);
            return false;
        }
    }
}
