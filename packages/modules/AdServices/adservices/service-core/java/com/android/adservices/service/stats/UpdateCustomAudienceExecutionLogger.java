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

package com.android.adservices.service.stats;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.LoggerFactory.Logger;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

/** Class for logging the update a signal custom audience process during background fetch. */
public class UpdateCustomAudienceExecutionLogger extends ApiServiceLatencyCalculator {
    private static final Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String MISSING_START_UPDATE_CUSTOM_AUDIENCE =
            "The logger should set the start of the update custom audience process.";

    @VisibleForTesting
    static final String REPEATED_END_UPDATE_CUSTOM_AUDIENCE =
            "The logger has already set the end of the update custom audience process.";

    private final AdServicesLogger mAdServicesLogger;
    private long mUpdateCustomAudienceStartTimestamp;
    private long mUpdateCustomAudienceEndTimestamp;

    public UpdateCustomAudienceExecutionLogger(
            @NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        super(clock);
        this.mAdServicesLogger = adServicesLogger;
        sLogger.v("UpdateCustomAudienceExecutionLogger starts.");
    }

    /** starts the update-custom-audience process. */
    public void start() {
        this.mUpdateCustomAudienceStartTimestamp = getServiceElapsedTimestamp();
        sLogger.v("The update custom audience process starts.");
    }

    /** close the update-custom-audience process. */
    public void close(int adsDataSizeInBytes, int numOfAds, int resultCode) {
        if (mUpdateCustomAudienceStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_UPDATE_CUSTOM_AUDIENCE);
        }
        if (mUpdateCustomAudienceEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_UPDATE_CUSTOM_AUDIENCE);
        }
        getApiServiceInternalFinalLatencyInMs();
        this.mUpdateCustomAudienceEndTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Log the updateCustomAudience process into AdServicesLogger.");
        mAdServicesLogger.logUpdateCustomAudienceProcessReportedStats(
                UpdateCustomAudienceProcessReportedStats.builder()
                        .setLatencyInMills(
                                (int)
                                        (mUpdateCustomAudienceEndTimestamp
                                                - mUpdateCustomAudienceStartTimestamp))
                        .setResultCode(resultCode)
                        .setDataSizeOfAdsInBytes(adsDataSizeInBytes)
                        .setNumOfAds(numOfAds)
                        .build());
    }
}
