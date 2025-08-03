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
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;

/**
 * Event-level debug reporting for ad selection.
 *
 * <p>Protected Audience debug reporting allows ad tech developers to declare remote URLs to receive
 * a GET request from devices when an auction is won / lost. This allows the following use-cases:
 *
 * <ul>
 *   <li>See if auctions are being won / lost
 *   <li>Understand why auctions are being lost (e.g. understand if it’s an issue with a bidding /
 *       scoring script implementation or a core logic issue)
 *   <li>Monitor roll-outs of new JavaScript logic to clients
 * </ul>
 *
 * <p>Debug reporting consists of two JS APIs available for usage, both of which take a URL string:
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 *
 *     <p>For the classes that wrap JavaScript code, see {@link DebugReportingScriptStrategy}.
 *
 *     <p>For the classes that send events, see {@link DebugReportSenderStrategy}.
 *
 *     <p>For the business logic processing events, see {@link DebugReportProcessor}.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public abstract class DebugReporting {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    /**
     * @return an instance of debug reporting after checking for is limited ad tracking is enabled
     *     or not.
     */
    public static ListenableFuture<DebugReporting> createInstance(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdSelectionDebugReportDao adSelectionDebugReportDao,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull AdIdFetcher adIdFetcher,
            @NonNull String packageName,
            int callingUid) {
        if (!getEnablementStatus(flags)) {
            return Futures.immediateFuture(new DebugReportingDisabled());
        }
        long adIdFetchTimeoutMs = flags.getAdIdFetcherTimeoutMs();
        return FluentFuture.from(
                        adIdFetcher.isLimitedAdTrackingEnabled(
                                packageName, callingUid, adIdFetchTimeoutMs))
                .transform(
                        isLatEnabled -> {
                            if (isLatEnabled) {
                                return new DebugReportingDisabled();
                            } else {
                                return new DebugReportingEnabled(
                                        context,
                                        flags,
                                        adServicesHttpsClient,
                                        devContext,
                                        adSelectionDebugReportDao);
                            }
                        },
                        lightweightExecutorService);
    }

    /**
     * @return DebugReportingScriptStrategy to be used while running on device ad selection.
     */
    @NonNull
    public abstract DebugReportingScriptStrategy getScriptStrategy();

    /**
     * @return DebugReportSenderStrategy to be used while running on device ad selection.
     */
    @NonNull
    public abstract DebugReportSenderStrategy getSenderStrategy();

    /**
     * @return returns status of debug reporting
     */
    public abstract boolean isEnabled();

    private static boolean getEnablementStatus(Flags flags) {
        if (flags.getAdIdKillSwitch()) {
            sLogger.v("AdIdService kill switch is enabled, disabling event level debug reporting");
            return false;
        }
        return flags.getFledgeEventLevelDebugReportingEnabled();
    }
}
