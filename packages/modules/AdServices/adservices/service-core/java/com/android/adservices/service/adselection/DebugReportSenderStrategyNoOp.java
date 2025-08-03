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

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

class DebugReportSenderStrategyNoOp implements DebugReportSenderStrategy {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final Queue<Uri> mDebugReportQueue;

    public DebugReportSenderStrategyNoOp() {
        mDebugReportQueue = new LinkedBlockingQueue<>();
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
        return Futures.submit(() -> {
            List<Uri> uris = mDebugReportQueue.stream().collect(Collectors.toList());
            mDebugReportQueue.clear();
            for (Uri uri : uris) {
                sLogger.v("[DebugReporting] Reporting disabled, but would ping: %s",
                        uri.toString());
            }
        }, AdServicesExecutors.getBlockingExecutor());
    }
}
