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

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A worker class dedicated to report completion of the device lock program.
 */
public final class ReportDeviceLockProgramCompleteWorker extends ListenableWorker {

    private static final String TAG = ReportDeviceLockProgramCompleteWorker.class.getSimpleName();

    public static final String REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME =
            "report-device-lock-program-complete";
    private final ListenableFuture<DeviceFinalizeClient> mClient;
    private final PolicyObjectsProvider mPolicyObjectsProvider;

    public ReportDeviceLockProgramCompleteWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        this(context,
                workerParams,
                null,
                ((PolicyObjectsProvider) context.getApplicationContext()),
                executorService);
    }

    @VisibleForTesting
    ReportDeviceLockProgramCompleteWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams,
            DeviceFinalizeClient client,
            PolicyObjectsProvider policyObjectsProvider,
            ListeningExecutorService executorService) {
        super(context, workerParams);
        if (client == null) {
            final String hostName = context.getResources().getString(
                    R.string.finalize_server_host_name);
            final int portNumber = context.getResources().getInteger(
                    R.integer.finalize_server_port_number);
            final String className = context.getResources().getString(
                    R.string.device_finalize_client_class_name);
            final Pair<String, String> apikey = new Pair<>(
                    context.getResources().getString(R.string.finalize_service_api_key_name),
                    context.getResources().getString(R.string.finalize_service_api_key_value));
            ListenableFuture<String> registeredDeviceId =
                    GlobalParametersClient.getInstance().getRegisteredDeviceId();
            mClient = Futures.transform(registeredDeviceId,
                    id -> DeviceFinalizeClient.getInstance(context, className, hostName,
                            portNumber, apikey, id), executorService);
        } else {
            mClient = Futures.immediateFuture(client);
        }
        mPolicyObjectsProvider = policyObjectsProvider;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        FinalizationController controller = mPolicyObjectsProvider.getFinalizationController();
        return Futures.transformAsync(mClient, client -> {
            DeviceFinalizeClient.ReportDeviceProgramCompleteResponse response =
                    client.reportDeviceProgramComplete();
            if (response.hasRecoverableError()) {
                LogUtil.w(TAG, "Report finalization failed w/ recoverable error" + response
                        + "\nRetrying...");
                return Futures.immediateFuture(Result.retry());
            }
            ListenableFuture<Void> notifyFuture =
                    controller.notifyFinalizationReportResult(response);
            return Futures.transform(notifyFuture,
                    unused -> response.isSuccessful() ? Result.success() : Result.failure(),
                    MoreExecutors.directExecutor());
        }, getBackgroundExecutor());
    }
}
