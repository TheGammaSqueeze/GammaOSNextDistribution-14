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

package com.android.federatedcompute.services.training;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeJobUtil;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.statsd.joblogging.FederatedComputeJobServiceLogger;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;

/** Main service for the scheduled federated computation jobs. */
public class FederatedJobService extends JobService {
    private static final String TAG = FederatedJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = params.getJobId();
        LogUtil.d(TAG, "FederatedJobService.onStartJob");
        FederatedComputeJobServiceLogger.getInstance(this)
                .recordOnStartJob(jobId);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch enabled, finishing job.");
            return FederatedComputeJobUtil.cancelAndFinishJob(this, params, jobId,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }
        FederatedComputeWorker worker = FederatedComputeWorker.getInstance(this);
        ListenableFuture<FLRunnerResult> runCompleteFuture =
                worker.startTrainingRun(params.getJobId(), new OnJobFinishedCallback(params, this));

        Futures.addCallback(
                runCompleteFuture,
                new FutureCallback<FLRunnerResult>() {
                    @Override
                    public void onSuccess(FLRunnerResult flRunnerResult) {
                        LogUtil.d(TAG, "Federated computation job %d is done!", params.getJobId());
                        if (flRunnerResult != null) {
                            worker.finish(flRunnerResult);
                        } else {
                            worker.cleanUpActiveRun();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(
                                TAG, t, "Failed to handle computation job: %d", params.getJobId());
                        worker.finish(null, ContributionResult.FAIL, false);
                    }
                },
                getBackgroundExecutor());
        return true;
    }

    public static class OnJobFinishedCallback {
        JobParameters mParams;
        FederatedJobService mJobService;

        public OnJobFinishedCallback(JobParameters mParams, FederatedJobService mJobService) {
            this.mParams = mParams;
            this.mJobService = mJobService;
        }

        /**
         * To be called each time we are about to reschedule the job.
         */
        public void callJobFinished(boolean isSuccessful) {
            LogUtil.d(
                    TAG,
                    "Job Finished called for Federated computation job %d!",
                    mParams.getJobId());
            boolean wantsReschedule = false;
            FederatedComputeJobServiceLogger.getInstance(mJobService)
                    .recordJobFinished(
                            mParams.getJobId(),
                            isSuccessful,
                            wantsReschedule);
            mJobService.jobFinished(mParams, wantsReschedule);
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        LogUtil.d(
                TAG,
                "FederatedJobService.onStopJob %d with reason %d",
                jobId,
                params.getStopReason());
        boolean wantsReschedule = false;
        FederatedComputeJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params,
                        jobId,
                        wantsReschedule);
        FederatedComputeWorker.getInstance(this).finish(null, ContributionResult.FAIL, true);
        return wantsReschedule;
    }
}
