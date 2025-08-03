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

package com.android.ondevicepersonalization.services.download.mdd;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_TASK_TAG_KEY;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJobService;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * MDD JobService. This will download MDD files in background tasks.
 */
public class MddJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "MddJobService";

    private String mMddTaskTag;

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = getMddTaskJobId(params);
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this).recordOnStartJob(jobId);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            return cancelAndFinishJob(params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }

        if (!UserPrivacyStatus.getInstance().isPersonalizationStatusEnabled()) {
            sLogger.d(TAG + ": Personalization is not allowed, finishing job.");
            OdpJobServiceLogger.getInstance(this).recordJobSkipped(jobId,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED);
            jobFinished(params, false);
            return true;
        }

        mMddTaskTag = getMddTaskTag(params);

        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> MobileDataDownloadFactory.getMdd(this).handleTask(mMddTaskTag),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Context context = this;
        Futures.addCallback(
                handleTaskFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": MddJobService.MddHandleTask succeeded!");
                        // Attempt to process any data downloaded
                        if (WIFI_CHARGING_PERIODIC_TASK.equals(mMddTaskTag)) {
                            OnDevicePersonalizationDownloadProcessingJobService.schedule(context);
                        }
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(MddJobService.this)
                                .recordJobFinished(jobId,
                                        /* isSuccessful= */ true,
                                        wantsReschedule);
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + jobId, t);
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(MddJobService.this)
                                .recordJobFinished(jobId,
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
        // Attempt to process any data downloaded before the worker was stopped.
        if (WIFI_CHARGING_PERIODIC_TASK.equals(mMddTaskTag)) {
            OnDevicePersonalizationDownloadProcessingJobService.schedule(this);
        }
        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, getMddTaskJobId(params), wantsReschedule);
        return wantsReschedule;
    }

    private boolean cancelAndFinishJob(final JobParameters params, int skipReason) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        int jobId = getMddTaskJobId(params);
        if (jobScheduler != null) {
            jobScheduler.cancel(jobId);
        }
        OdpJobServiceLogger.getInstance(this).recordJobSkipped(jobId, skipReason);
        jobFinished(params, /* wantsReschedule = */ false);
        return true;
    }

    private int getMddTaskJobId(final JobParameters params) {
        mMddTaskTag = getMddTaskTag(params);
        return MddTaskScheduler.getMddTaskJobId(mMddTaskTag);
    }

    private String getMddTaskTag(final JobParameters params) {
        // Get the MddTaskTag from input.
        PersistableBundle extras = params.getExtras();
        if (null == extras) {
            sLogger.e(TAG + ": can't find MDD task tag");
            throw new IllegalArgumentException("Can't find MDD Tasks Tag!");
        }
        return extras.getString(MDD_TASK_TAG_KEY);
    }
}
