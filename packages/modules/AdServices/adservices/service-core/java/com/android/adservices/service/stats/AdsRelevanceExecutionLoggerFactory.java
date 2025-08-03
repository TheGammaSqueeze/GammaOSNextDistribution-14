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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;

import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

public class AdsRelevanceExecutionLoggerFactory {

    @VisibleForTesting
    static final String GET_AD_SELECTION_DATA_API_NAME = "GET_AD_SELECTION_DATA";

    @VisibleForTesting
    static final String PERSIST_AD_SELECTION_RESULT_API_NAME = "PERSIST_AD_SELECTION_RESULT";

    @VisibleForTesting
    static final String UPDATE_SIGNALS_API_NAME = "UPDATE_SIGNALS";

    @VisibleForTesting
    static final String UNKNOWN_API_NAME = "UNKNOWN_API_NAME";

    private final String mCallerAppPackageName;

    private final CallerMetadata mCallerMetadata;

    private final Clock mClock;

    private final AdServicesLogger mAdServicesLogger;

    private final int mApiNameCode;

    private final boolean mFledgeAuctionServerApiUsageMetricsEnabled;

    public AdsRelevanceExecutionLoggerFactory(
            @NonNull String callerAppPackageName,
            @NonNull CallerMetadata callerMetadata,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            int apiNameCode) {
        Objects.requireNonNull(callerAppPackageName);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        mCallerAppPackageName = callerAppPackageName;
        mFledgeAuctionServerApiUsageMetricsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAuctionServerApiUsageMetricsEnabled);
        mCallerMetadata = callerMetadata;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mApiNameCode = apiNameCode;
    }

    /**
     * Gets the {@link AdsRelevanceExecutionLogger} implementation to use,
     * dependent on whether the Ads Relevance Api metrics is enabled.
     *
     * @return an {@link AdsRelevanceExecutionLoggerImpl} instance if the Ads Relevance
     *      metrics is enabled, or {@link AdsRelevanceExecutionLoggerNoLoggingImpl}
     *      instance otherwise
     */
    public AdsRelevanceExecutionLogger getAdsRelevanceExecutionLogger() {
        return switch (mApiNameCode) {
            case AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT ->
                    getAuctionServerApiExecutionLogger(PERSIST_AD_SELECTION_RESULT_API_NAME);
            case AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA ->
                    getAuctionServerApiExecutionLogger(GET_AD_SELECTION_DATA_API_NAME);
            case AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS ->
                    getProtectedSignalsApiExecutionLogger(UPDATE_SIGNALS_API_NAME);
            default -> new AdsRelevanceExecutionLoggerNoLoggingImpl(UNKNOWN_API_NAME);
        };
    }

    private AdsRelevanceExecutionLogger getAuctionServerApiExecutionLogger(
            @NonNull String apiName) {
        if (mFledgeAuctionServerApiUsageMetricsEnabled) {
            return new AdsRelevanceExecutionLoggerImpl(
                    mCallerAppPackageName,
                    mCallerMetadata,
                    mClock,
                    mAdServicesLogger,
                    apiName,
                    mApiNameCode);
        } else {
            return new AdsRelevanceExecutionLoggerNoLoggingImpl(apiName);
        }
    }

    private AdsRelevanceExecutionLogger getProtectedSignalsApiExecutionLogger(
            @NonNull String apiName) {
        return new AdsRelevanceExecutionLoggerImpl(
                mCallerAppPackageName,
                mCallerMetadata,
                mClock,
                mAdServicesLogger,
                apiName,
                mApiNameCode);
    }
}
