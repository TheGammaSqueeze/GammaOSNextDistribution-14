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
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.DBAdSelectionDebugReport;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Sender strategy to persist the debug reports in database to be sent by a background job in a
 * batch.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class DebugReportSenderStrategyBatchImpl implements DebugReportSenderStrategy {
    private static final int MAX_QUEUE_DEPTH = 1000;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Context mContext;
    @NonNull private final Queue<DBAdSelectionDebugReport> mDebugReportQueue;
    @NonNull private final AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @NonNull private final DevContext mDevContext;

    DebugReportSenderStrategyBatchImpl(
            @NonNull Context context,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao,
            @NonNull DevContext devContext) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(adSelectionDebugReportDao);
        Objects.requireNonNull(devContext);

        mContext = context;
        mAdSelectionDebugReportDao = adSelectionDebugReportDao;
        mDevContext = devContext;
        mDebugReportQueue = new ArrayBlockingQueue<>(MAX_QUEUE_DEPTH);
    }

    @Override
    public void enqueue(@NonNull Uri uri) {
        Objects.requireNonNull(uri);
        DBAdSelectionDebugReport dbAdSelectionDebugReport =
                DBAdSelectionDebugReport.create(
                        null,
                        uri,
                        mDevContext.getDevOptionsEnabled(),
                        Instant.now().toEpochMilli());
        mDebugReportQueue.add(dbAdSelectionDebugReport);
    }

    @Override
    public void batchEnqueue(@NonNull List<Uri> uris) {
        Objects.requireNonNull(uris);
        uris.forEach(
                uri -> {
                    DBAdSelectionDebugReport dbAdSelectionDebugReport =
                            DBAdSelectionDebugReport.create(
                                    null,
                                    uri,
                                    mDevContext.getDevOptionsEnabled(),
                                    Instant.now().toEpochMilli());
                    mDebugReportQueue.add(dbAdSelectionDebugReport);
                });
    }

    @Override
    public ListenableFuture<Void> flush() {
        if (mDebugReportQueue.isEmpty()) {
            return Futures.immediateVoidFuture();
        }
        List<DBAdSelectionDebugReport> adSelectionDebugReports = new ArrayList<>(mDebugReportQueue);
        mDebugReportQueue.clear();
        mAdSelectionDebugReportDao.persistAdSelectionDebugReporting(adSelectionDebugReports);
        sLogger.v("successfully persisted %d debug reports in db", adSelectionDebugReports.size());
        DebugReportSenderJobService.scheduleIfNeeded(mContext, false);
        return Futures.immediateVoidFuture();
    }
}
