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

package com.android.devicelockcontroller.provision.worker;

import static com.android.devicelockcontroller.common.DeviceLockConstants.REASON_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.USER_DEFERRED_DEVICE_PROVISIONING;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Despite the naming, this worker class is only to report provision has been paused by user to
 * backend server.
 */
public final class PauseProvisioningWorker extends AbstractCheckInWorker {
    private static final String KEY_PAUSE_DEVICE_PROVISIONING_REASON =
            "PAUSE_DEVICE_PROVISIONING_REASON";
    public static final String REPORT_PROVISION_PAUSED_BY_USER_WORK =
            "report-provision-paused-by-user";

    private final StatsLogger mStatsLogger;

    /**
     * Report provision has been paused by user to backend server by running a work item.
     */
    public static void reportProvisionPausedByUser(WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putInt(KEY_PAUSE_DEVICE_PROVISIONING_REASON, USER_DEFERRED_DEVICE_PROVISIONING)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(PauseProvisioningWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY)
                        .setInputData(inputData)
                        .build();
        ListenableFuture<Operation.State.SUCCESS> result =
                workManager.enqueueUniqueWork(REPORT_PROVISION_PAUSED_BY_USER_WORK,
                        ExistingWorkPolicy.KEEP, work).getResult();
        Futures.addCallback(result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Operation.State.SUCCESS result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Log an error but don't reset the device (non critical failure).
                        LogUtil.e(TAG, "Failed to enqueue 'Report provision paused' work", t);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    public PauseProvisioningWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        this(context, workerParams, null, executorService);
    }

    @VisibleForTesting
    PauseProvisioningWorker(@NonNull Context context, @NonNull WorkerParameters workerParams,
            DeviceCheckInClient client, ListeningExecutorService executorService) {
        super(context, workerParams, client, executorService);
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) context.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transform(mClient, client -> {
            int reason = getInputData().getInt(KEY_PAUSE_DEVICE_PROVISIONING_REASON,
                    REASON_UNSPECIFIED);
            PauseDeviceProvisioningGrpcResponse response = client.pauseDeviceProvisioning(reason);
            if (response.hasRecoverableError()) {
                LogUtil.w(TAG, "Report paused provisioning failed w/ recoverable error" + response
                        + "\nRetrying...");
                return Result.retry();
            }
            if (response.isSuccessful()) {
                mStatsLogger.logPauseDeviceProvisioning();
                return Result.success();
            }
            LogUtil.e(TAG, "Pause provisioning request failed: " + response);
            return Result.failure();
        }, mExecutorService);
    }
}
