/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.serviceflow;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;

import android.os.Bundle;

import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.PluginProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Task object representing a service flow task.
 */
public class ServiceFlowTask {
    private final ServiceFlowType mServiceFlowType;
    private final ServiceFlow mServiceFlow;
    private final ProcessRunner mProcessRunner;
    private volatile boolean mIsCompleted;
    private Exception mExecutionException;

    private final ListeningExecutorService mExecutor =
            OnDevicePersonalizationExecutors.getBackgroundExecutor();

    public ServiceFlowTask(ServiceFlowType serviceFlowType, ServiceFlow serviceFlow) {
        mIsCompleted = false;
        mServiceFlowType = serviceFlowType;
        mServiceFlow = serviceFlow;
        mProcessRunner =
                (boolean) FlagsFactory.getFlags()
                        .getStableFlag(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED)
                        ? SharedIsolatedProcessRunner.getInstance()
                        : PluginProcessRunner.getInstance();
    }

    public ServiceFlowType getServiceFlowType() {
        return mServiceFlowType;
    }

    public ServiceFlow getServiceFlow() {
        return mServiceFlow;
    }

    public boolean isCompleted() {
        return mIsCompleted;
    }

    public Exception getExecutionException() {
        return mExecutionException;
    }

    /** Executes the given service flow. */
    public void run() {
        try {
            if (mIsCompleted || !mServiceFlow.isServiceFlowReady()) return;

            ListenableFuture<IsolatedServiceInfo> loadServiceFuture =
                    mProcessRunner.loadIsolatedService(
                            mServiceFlowType.getTaskName(), mServiceFlow.getService());

            ListenableFuture<Bundle> runServiceFuture = FluentFuture.from(loadServiceFuture)
                    .transformAsync(
                            isolatedServiceInfo -> mProcessRunner
                                    .runIsolatedService(
                                            isolatedServiceInfo,
                                            mServiceFlowType.getOperationCode(),
                                            mServiceFlow.getServiceParams()),
                            mExecutor);

            mServiceFlow.uploadServiceFlowMetrics(runServiceFuture);

            ListenableFuture<?> serviceFlowResultFuture =
                    mServiceFlow.getServiceFlowResultFuture(runServiceFuture);

            mServiceFlow.returnResultThroughCallback(serviceFlowResultFuture);

            var unused =
                    Futures.whenAllComplete(loadServiceFuture, serviceFlowResultFuture)
                            .callAsync(
                                    () -> {
                                        mServiceFlow.cleanUpServiceParams();
                                        ListenableFuture<Void> unloadServiceFuture =
                                                mProcessRunner.unloadIsolatedService(
                                                        loadServiceFuture.get());
                                        mIsCompleted = true;
                                        return unloadServiceFuture;
                                    }, mExecutor);
        } catch (Exception e) {
            mExecutionException = e;
        }
    }
}
