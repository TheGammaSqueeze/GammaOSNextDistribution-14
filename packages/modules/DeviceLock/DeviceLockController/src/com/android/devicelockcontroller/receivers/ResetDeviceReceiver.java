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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A broadcast receiver that will factory reset the device when it receives a broadcast.
 */
public final class ResetDeviceReceiver extends BroadcastReceiver {
    private static final String TAG = "ResetDeviceReceiver";
    private final Executor mExecutor;

    public ResetDeviceReceiver() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @VisibleForTesting
    ResetDeviceReceiver(Executor executor) {
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ResetDeviceReceiver.class.getName().equals(intent.getComponent().getClassName())) {
            throw new IllegalArgumentException("Can not handle implicit intent!");
        }
        Futures.addCallback(SetupParametersClient.getInstance().isProvisionMandatory(),
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isProvisionMandatory) {
                        StatsLogger logger = ((StatsLoggerProvider) context.getApplicationContext())
                                .getStatsLogger();
                        logger.logDeviceReset(isProvisionMandatory);
                        ((PolicyObjectsProvider) context.getApplicationContext())
                                .getPolicyController().wipeDevice();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // We don't want the statistics event to cancel the device reset, so
                        // we just log the error here and proceed.
                        LogUtil.e(TAG, "Error querying isProvisionMandatory", t);
                        ((PolicyObjectsProvider) context.getApplicationContext())
                                .getPolicyController().wipeDevice();
                    }
                }, mExecutor);
    }
}
