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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

class DebugReportSenderStrategyHttpImpl implements DebugReportSenderStrategy {

    private static final int MAX_QUEUE_DEPTH = 1000;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Queue<Uri> mDebugReportQueue;
    @NonNull private final AdServicesHttpsClient mHttpsClient;
    @NonNull private final DevContext mDevContext;

    DebugReportSenderStrategyHttpImpl(
            @NonNull AdServicesHttpsClient adServicesHttpsClient, @NonNull DevContext devContext) {
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContext);

        mHttpsClient = adServicesHttpsClient;
        mDebugReportQueue = new ArrayBlockingQueue<>(MAX_QUEUE_DEPTH);
        mDevContext = devContext;
    }

    @Override
    public void enqueue(Uri uri) {
        mDebugReportQueue.add(uri);
    }

    @Override
    public void batchEnqueue(List<Uri> uris) {
        mDebugReportQueue.addAll(uris);
    }

    @Override
    public ListenableFuture<Void> flush() {
        List<Uri> uris = mDebugReportQueue.stream().collect(Collectors.toList());
        mDebugReportQueue.clear();
        if (uris.isEmpty()) {
            return Futures.immediateVoidFuture();
        }
        List<ListenableFuture<Void>> futures =
                uris.stream()
                        .map(
                                uri -> {
                                    sLogger.v("[DebugReporting] Pinging %s", uri.toString());
                                    return mHttpsClient.getAndReadNothing(uri, mDevContext);
                                })
                        .collect(Collectors.toList());
        return Futures.whenAllComplete(futures).call(() -> null,
                AdServicesExecutors.getBlockingExecutor());
    }
}
