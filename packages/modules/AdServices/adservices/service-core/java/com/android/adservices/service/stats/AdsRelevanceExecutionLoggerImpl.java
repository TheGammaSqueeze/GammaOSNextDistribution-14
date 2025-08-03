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

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;

import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class for logging the Ads Relevance API metrics. It provides the functions to collect and
 * log the corresponding Ads Relevance API process and log the data into the statsd logs.
 * This class collect data for the telemetry atoms:
 *
 * <ul>
 *   <li>ApiCallStats for getAdSelectionData API
 *   <li>ApiCallStats for persistAdSelectionResult API
 * </ul>
 *
 * <p>Each complete process should start the stopwatch immediately on construction this Logger
 * object, and call its corresponding end method to record its states and log the generated atom
 * proto into the statsd logger.
 */
public class AdsRelevanceExecutionLoggerImpl extends ApiServiceLatencyCalculator
        implements AdsRelevanceExecutionLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final int UNAVAILABLE_LATENCY = -1;
    @VisibleForTesting
    static final String MISSING_ADS_RELEVANCE_API_PROCESS =
            "The logger should set the start of Ads Relevance API process: ";
    @VisibleForTesting
    static final String REPEATED_ADS_RELEVANCE_API_PROCESS =
            "The logger has already set the end of Ads Relevance API process: ";

    private final long mBinderElapsedTimestamp;

    private long mAdsRelevanceApiStartTimestamp;
    private long mAdsRelevanceApiEndTimestamp;

    private AdServicesLogger mAdServicesLogger;
    private String mCallerAppPackageName;
    private int mApiNameCode;
    private String mApiName;

    private boolean isLatencyAvailable;

    public AdsRelevanceExecutionLoggerImpl(
            @NonNull String callerAppPackageName,
            @NonNull CallerMetadata callerMetadata,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull String apiName,
            int apiNameCode) {
        super(clock);
        Objects.requireNonNull(callerAppPackageName);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(apiName);
        this.mCallerAppPackageName = callerAppPackageName;
        this.mBinderElapsedTimestamp = callerMetadata.getBinderElapsedTimestamp();
        this.mAdServicesLogger = adServicesLogger;
        this.mApiName = apiName;
        this.mApiNameCode = apiNameCode;
        isLatencyAvailable = true;
        sLogger.v("AdsRelevanceExecutionLogger starts.");
        sLogger.v("Start the execution of " + mApiName);
        this.mAdsRelevanceApiStartTimestamp = getServiceElapsedTimestamp();
    }

    /** end a complete Ad Relevance Api process. */
    @Override
    public void endAdsRelevanceApi(int resultCode) {
        if (mAdsRelevanceApiStartTimestamp == 0L) {
            sLogger.e(MISSING_ADS_RELEVANCE_API_PROCESS + mApiName);
            isLatencyAvailable = false;
        }
        if (mAdsRelevanceApiEndTimestamp > 0L) {
            sLogger.e(REPEATED_ADS_RELEVANCE_API_PROCESS + mApiName);
            isLatencyAvailable = false;
        }
        sLogger.v("End the execution of " + mApiName);
        this.mAdsRelevanceApiEndTimestamp = getServiceElapsedTimestamp();
        int overallAdsRelevanceApiLatency =
                isLatencyAvailable ? getAdsRelevanceApiOverallLatencyInMs() : UNAVAILABLE_LATENCY;
        int adsRelevanceApiResultCode = isLatencyAvailable ? resultCode : STATUS_UNSET;
        mAdServicesLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__FLEDGE)
                        .setApiName(mApiNameCode)
                        .setLatencyMillisecond(overallAdsRelevanceApiLatency)
                        .setResult(adsRelevanceApiResultCode, FAILURE_REASON_UNSET)
                        .setAppPackageName(mCallerAppPackageName)
                        .setSdkPackageName("")
                        .build());
    }

    private int getAdsRelevanceApiOverallLatencyInMs() {
        return getBinderLatencyInMs(mBinderElapsedTimestamp)
                + getAdsRelevanceApiInternalFinalLatencyInMs();
    }

    /**
     * @return the latency in milliseconds of the Ads Relevance Api process if started,
     *      otherwise the {@link AdServicesLoggerUtil#FIELD_UNSET}.
     */
    private int getAdsRelevanceApiInternalFinalLatencyInMs() {
        if (mAdsRelevanceApiEndTimestamp == 0L) {
            return (int) (getServiceElapsedTimestamp() - mAdsRelevanceApiStartTimestamp);
        }
        return (int) (mAdsRelevanceApiEndTimestamp - mAdsRelevanceApiStartTimestamp);
    }

    private int getBinderLatencyInMs(long binderElapsedTimestamp) {
        return (int) (mAdsRelevanceApiStartTimestamp - binderElapsedTimestamp) * 2;
    }

    @VisibleForTesting
    void setAdsRelevanceApiStartTimestamp(long timestamp) {
        mAdsRelevanceApiStartTimestamp = timestamp;
    }

    @VisibleForTesting
    void setAdsRelevanceApiEndTimestamp(long timestamp) {
        mAdsRelevanceApiEndTimestamp = timestamp;
    }
}
