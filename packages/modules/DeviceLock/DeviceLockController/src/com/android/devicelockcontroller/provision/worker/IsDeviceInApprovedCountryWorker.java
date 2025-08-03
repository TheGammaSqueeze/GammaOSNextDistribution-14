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
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/**
 * A worker class dedicated to check whether device is in approved country.
 * Note that this worker always returns {@link androidx.work.ListenableWorker.Result.Success}
 * regardless of the success of the underlying rpc.
 * This worker only returns a successful result if it gets the country eligibility information from
 * the server.
 */
public final class IsDeviceInApprovedCountryWorker extends AbstractCheckInWorker {

    public static final String KEY_IS_IN_APPROVED_COUNTRY = "is-in-approved-country";

    public IsDeviceInApprovedCountryWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        super(context, workerParams, null, executorService);
    }

    @VisibleForTesting
    IsDeviceInApprovedCountryWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters,
            DeviceCheckInClient client, ListeningExecutorService executorService) {
        super(context, workerParameters, client, executorService);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transform(mClient, client -> {
            String carrierInfo = Objects.requireNonNull(
                    mContext.getSystemService(TelephonyManager.class)).getSimOperator();
            IsDeviceInApprovedCountryGrpcResponse response =
                    client.isDeviceInApprovedCountry(carrierInfo);
            ((StatsLoggerProvider) mContext.getApplicationContext()).getStatsLogger()
                    .logIsDeviceInApprovedCountry();
            if (response.hasRecoverableError()) {
                LogUtil.w(TAG, "Is in approve country failed w/ recoverable error" + response
                        + "\nRetrying...");
                return Result.retry();
            }
            Data.Builder builder = new Data.Builder();
            if (response.isSuccessful()) {
                return Result.success(builder.putBoolean(KEY_IS_IN_APPROVED_COUNTRY,
                        response.isDeviceInApprovedCountry()).build());
            }
            return Result.failure();
        }, mExecutorService);
    }
}
