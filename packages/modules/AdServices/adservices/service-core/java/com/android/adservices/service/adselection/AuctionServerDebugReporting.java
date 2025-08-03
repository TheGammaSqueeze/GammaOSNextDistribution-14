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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;

public class AuctionServerDebugReporting {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final boolean mIsEnabled;

    private AuctionServerDebugReporting(boolean isEnabled) {
        this.mIsEnabled = isEnabled;
    }

    /**
     * @return an instance of auction server debug reporting after checking for is limited ad
     *     tracking is enabled or not.
     */
    public static ListenableFuture<AuctionServerDebugReporting> createInstance(
            @NonNull Flags flags,
            @NonNull AdIdFetcher adIdFetcher,
            @NonNull String packageName,
            int callingUid,
            @NonNull ExecutorService lightweightExecutorService) {
        if (!getEnablementStatus(flags)) {
            return Futures.immediateFuture(createForDebugReportingDisabled());
        }
        long auctionServerAdIdFetchTimeoutMs = flags.getFledgeAuctionServerAdIdFetcherTimeoutMs();
        return FluentFuture.from(
                        adIdFetcher.isLimitedAdTrackingEnabled(
                                packageName, callingUid, auctionServerAdIdFetchTimeoutMs))
                .transform(
                        isLatEnabled -> new AuctionServerDebugReporting(!isLatEnabled),
                        lightweightExecutorService);
    }

    /**
     * @return an instance of auction server debug reporting as disabled
     */
    public static AuctionServerDebugReporting createForDebugReportingDisabled() {
        return new AuctionServerDebugReporting(false);
    }

    /**
     * @return returns status of auction server debug reporting
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    private static boolean getEnablementStatus(Flags flags) {
        if (flags.getAdIdKillSwitch()) {
            sLogger.v("AdIdService kill switch is enabled, disabling event level debug reporting");
            return false;
        }
        return flags.getFledgeAuctionServerEnableDebugReporting();
    }
}
