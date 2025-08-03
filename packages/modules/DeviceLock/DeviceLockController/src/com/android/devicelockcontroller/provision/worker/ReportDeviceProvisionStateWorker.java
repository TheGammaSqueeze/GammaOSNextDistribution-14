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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_SUCCESS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.DEADLINE_PASSED;

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

import com.android.devicelockcontroller.activities.DeviceLockNotificationManager;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A worker class dedicated to report state of provision for the device lock program.
 */
public final class ReportDeviceProvisionStateWorker extends AbstractCheckInWorker {
    public static final String KEY_IS_PROVISION_SUCCESSFUL = "is-provision-successful";
    public static final String KEY_PROVISION_FAILURE_REASON = "provision-failure-reason";
    public static final String REPORT_PROVISION_STATE_WORK_NAME = "report-provision-state";
    @VisibleForTesting
    static final String UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE = "Unexpected provision state!";

    private final StatsLogger mStatsLogger;

    /**
     * Report provision failure and get next failed step
     */
    public static void reportSetupFailed(WorkManager workManager,
            @ProvisionFailureReason int reason) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)
                .putInt(KEY_PROVISION_FAILURE_REASON, reason)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    /**
     * Report provision success
     */
    public static void reportSetupCompleted(WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, true)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    /**
     * Schedule a work to report the current provision failed step to server.
     */
    public static void reportCurrentFailedStep(WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)
                .putInt(KEY_PROVISION_FAILURE_REASON, DEADLINE_PASSED)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    private static void enqueueReportWork(Data inputData, WorkManager workManager) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(ReportDeviceProvisionStateWorker.class)
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY)
                        .build();
        ListenableFuture<Operation.State.SUCCESS> result =
                workManager.enqueueUniqueWork(REPORT_PROVISION_STATE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE, work).getResult();
        Futures.addCallback(result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Operation.State.SUCCESS result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Log an error but don't reset the device (non critical failure).
                        LogUtil.e(TAG, "Failed to enqueue 'report provision state' work", t);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    public ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        this(context, workerParams, /* client= */ null,
                executorService);
    }

    @VisibleForTesting
    ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, DeviceCheckInClient client,
            ListeningExecutorService executorService) {
        super(context, workerParams, client, executorService);
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) context.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        ListenableFuture<Integer> lastState =
                globalParametersClient.getLastReceivedProvisionState();
        DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) mContext;
        DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();
        return Futures.whenAllSucceed(mClient, lastState).call(() -> {
            boolean isSuccessful = getInputData().getBoolean(
                    KEY_IS_PROVISION_SUCCESSFUL, /* defaultValue= */ false);
            int failureReason = getInputData().getInt(KEY_PROVISION_FAILURE_REASON,
                    ProvisionFailureReason.UNKNOWN_REASON);
            if (!isSuccessful && failureReason == ProvisionFailureReason.UNKNOWN_REASON) {
                LogUtil.e(TAG, "Reporting failure with an unknown reason is not allowed");
            }
            ReportDeviceProvisionStateGrpcResponse response =
                    Futures.getDone(mClient).reportDeviceProvisionState(
                            Futures.getDone(lastState),
                            isSuccessful,
                            failureReason);
            if (response.hasRecoverableError()) {
                LogUtil.w(TAG, "Report provision state failed w/ recoverable error" + response
                        + "\nRetrying...");
                return Result.retry();
            }
            if (response.hasFatalError()) {
                LogUtil.e(TAG,
                        "Report provision state failed: " + response + "\nRetry current step");
                scheduler.scheduleNextProvisionFailedStepAlarm();
                return Result.failure();
            }
            int daysLeftUntilReset = response.getDaysLeftUntilReset();
            if (daysLeftUntilReset > 0) {
                UserParameters.setDaysLeftUntilReset(mContext, daysLeftUntilReset);
            }
            int nextState = response.getNextClientProvisionState();
            Futures.getUnchecked(globalParametersClient.setLastReceivedProvisionState(nextState));
            onNextProvisionStateReceived(nextState, daysLeftUntilReset);
            if (nextState == PROVISION_STATE_FACTORY_RESET) {
                scheduler.scheduleResetDeviceAlarm();
            } else if (nextState != PROVISION_STATE_SUCCESS) {
                scheduler.scheduleNextProvisionFailedStepAlarm();
            }
            mStatsLogger.logReportDeviceProvisionState();
            return Result.success();
        }, mExecutorService);
    }

    private void onNextProvisionStateReceived(@DeviceProvisionState int provisionState,
            int daysLeftUntilReset) {
        switch (provisionState) {
            case PROVISION_STATE_RETRY:
            case PROVISION_STATE_SUCCESS:
            case PROVISION_STATE_UNSPECIFIED:
            case PROVISION_STATE_FACTORY_RESET:
                // no-op
                break;
            case PROVISION_STATE_DISMISSIBLE_UI:
                DeviceLockNotificationManager.sendDeviceResetNotification(mContext,
                        daysLeftUntilReset);
                break;
            case PROVISION_STATE_PERSISTENT_UI:
                DeviceLockNotificationManager
                        .sendDeviceResetInOneDayOngoingNotification(mContext);
                break;
            default:
                throw new IllegalStateException(UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE);
        }
    }
}
