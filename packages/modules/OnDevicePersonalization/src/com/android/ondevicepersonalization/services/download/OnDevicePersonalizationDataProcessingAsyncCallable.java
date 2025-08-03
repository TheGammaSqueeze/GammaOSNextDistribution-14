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

package com.android.ondevicepersonalization.services.download;

import android.adservices.ondevicepersonalization.DownloadCompletedOutputParcel;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowOrchestrator;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowType;

import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * AsyncCallable to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDataProcessingAsyncCallable implements AsyncCallable {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final ServiceFlowOrchestrator sSfo = ServiceFlowOrchestrator.getInstance();

    private final String mPackageName;
    private final Context mContext;
    private final Injector mInjector;

    @VisibleForTesting
    public static class Injector {
        FutureCallback<DownloadCompletedOutputParcel> getFutureCallback(
                SettableFuture<Boolean> downloadFlowFuture) {
            return new FutureCallback<>() {
                @Override
                public void onSuccess(DownloadCompletedOutputParcel result) {
                    downloadFlowFuture.set(true);
                }

                @Override
                public void onFailure(Throwable t) {
                    downloadFlowFuture.setException(t);
                }
            };
        }
    }

    public OnDevicePersonalizationDataProcessingAsyncCallable(String packageName,
            Context context) {
        this(packageName, context, new Injector());
    }

    @VisibleForTesting
    public OnDevicePersonalizationDataProcessingAsyncCallable(String packageName,
            Context context, Injector injector) {
        mPackageName = packageName;
        mContext = context;
        mInjector = injector;
    }

    /**
     * Processes the downloaded files for the given package and stores the data into sqlite
     * vendor tables.
     */
    public ListenableFuture<Boolean> call() {
        SettableFuture<Boolean> downloadFlowFuture = SettableFuture.create();
        FutureCallback<DownloadCompletedOutputParcel> callback =
                mInjector.getFutureCallback(downloadFlowFuture);

        sSfo.schedule(ServiceFlowType.DOWNLOAD_FLOW, mPackageName, mContext, callback);

        return downloadFlowFuture;
    }
}
