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

package com.android.ondevicepersonalization.services.data.user;

import static android.app.job.JobScheduler.RESULT_FAILURE;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * JobService to collect user data in the background thread.
 */
public class UserDataCollectionJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "UserDataCollectionJobService";
    // 4-hour interval.
    private static final long PERIOD_SECONDS = 14400;
    private ListenableFuture<Void> mFuture;
    private UserDataCollector mUserDataCollector;
    private RawUserData mUserData;

    /**
     * Schedules a unique instance of UserDataCollectionJobService to be run.
     */
    public static int schedule(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(
                USER_DATA_COLLECTION_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }
        ComponentName serviceComponent = new ComponentName(context,
                UserDataCollectionJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(
                USER_DATA_COLLECTION_ID, serviceComponent);

        // Constraints
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiresStorageNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setPeriodic(1000 * PERIOD_SECONDS); // JobScheduler uses Milliseconds.
        // persist this job across boots
        builder.setPersisted(true);

        return jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this)
                .recordOnStartJob(USER_DATA_COLLECTION_ID);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            return cancelAndFinishJob(params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }
        if (!UserPrivacyStatus.getInstance().isPersonalizationStatusEnabled()) {
            sLogger.d(TAG + ": Personalization is not allowed, finishing job.");
            OdpJobServiceLogger.getInstance(this).recordJobSkipped(
                    USER_DATA_COLLECTION_ID,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED);
            jobFinished(params, /* wantsReschedule = */ false);
            return true;
        }
        mUserDataCollector = UserDataCollector.getInstance(this);
        mUserData = RawUserData.getInstance();
        mFuture = Futures.submit(new Runnable() {
            @Override
            public void run() {
                sLogger.d(TAG + ": Running user data collection job");
                try {
                    // TODO(b/262749958): add multi-threading support if necessary.
                    mUserDataCollector.updateUserData(mUserData);
                } catch (Exception e) {
                    sLogger.e(TAG + ": Failed to collect user data", e);
                }
            }
        }, OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": User data collection job completed.");
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(UserDataCollectionJobService.this)
                                .recordJobFinished(
                                        USER_DATA_COLLECTION_ID,
                                        /* isSuccessful= */ true,
                                        wantsReschedule);
                        jobFinished(params, wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), t);
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(UserDataCollectionJobService.this)
                                .recordJobFinished(
                                        USER_DATA_COLLECTION_ID,
                                        /* isSuccessful= */ false,
                                        wantsReschedule);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, wantsReschedule);
                    }
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mFuture != null) {
            mFuture.cancel(true);
        }
        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params,
                        USER_DATA_COLLECTION_ID,
                        wantsReschedule);
        return wantsReschedule;
    }

    private boolean cancelAndFinishJob(final JobParameters params, int skipReason) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(USER_DATA_COLLECTION_ID);
        }
        OdpJobServiceLogger.getInstance(this).recordJobSkipped(
                USER_DATA_COLLECTION_ID,
                skipReason);
        jobFinished(params, /* wantsReschedule = */ false);
        return true;
    }
}
