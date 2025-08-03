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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_SUCCESS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RETRY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A broadcast receiver to perform the next step in the provision failure flow.
 */
public final class NextProvisionFailedStepReceiver extends BroadcastReceiver {
    public static final String TAG = "NextProvisionFailedStepReceiver";
    private final Executor mExecutor;

    public NextProvisionFailedStepReceiver() {
        this(Executors.newSingleThreadExecutor());
    }

    @VisibleForTesting
    NextProvisionFailedStepReceiver(Executor executor) {
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NextProvisionFailedStepReceiver.class.getName().equals(
                intent.getComponent().getClassName())) {
            throw new IllegalArgumentException("Can not handle implicit intent!");
        }
        Context applicationContext = context.getApplicationContext();
        ProvisionStateController provisionStateController =
                ((PolicyObjectsProvider) applicationContext)
                        .getProvisionStateController();

        GlobalParametersClient globalParameters = GlobalParametersClient.getInstance();
        ListenableFuture<Boolean> needToReportFuture = Futures.transform(
                globalParameters.getLastReceivedProvisionState(),
                provisionState -> {
                    if (provisionState == PROVISION_STATE_RETRY) {
                        // We cannot report the state here because we do not know the
                        // result of the retry. It will be reported after the retry finishes no
                        // matter whether it succeeds or fails.
                        provisionStateController.postSetNextStateForEventRequest(
                                PROVISION_RETRY);
                        return false;
                    }
                    return !(provisionState == PROVISION_STATE_SUCCESS
                            || provisionState == PROVISION_STATE_UNSPECIFIED);
                }, mExecutor);
        Futures.addCallback(needToReportFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Boolean needToReport) {
                if (needToReport) {
                    ReportDeviceProvisionStateWorker.reportCurrentFailedStep(
                            WorkManager.getInstance(context));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to perform next provision failed step", t);
            }
        }, MoreExecutors.directExecutor());
    }

}
