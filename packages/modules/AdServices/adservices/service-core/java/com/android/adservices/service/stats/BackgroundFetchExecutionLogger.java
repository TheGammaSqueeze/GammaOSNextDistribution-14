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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.customaudience.BackgroundFetchWorker;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class for logging the background fetch process when {@link
 * BackgroundFetchWorker#runBackgroundFetch} is called.
 */
public class BackgroundFetchExecutionLogger extends ApiServiceLatencyCalculator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String MISSING_START_TIMESTAMP =
            "The logger should set the start of the background fetch process.";

    @VisibleForTesting
    static final String REPEATED_END_TIMESTAMP =
            "The logger has already set the end of the background fetch process.";

    private final AdServicesLogger mAdServicesLogger;
    private long mRunBackgroundFetchStartTimestamp;
    private long mRunBackgroundFetchEndTimestamp;
    private int mNumOfEligibleToUpdateCAs;

    public BackgroundFetchExecutionLogger(
            @NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        super(clock);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        this.mAdServicesLogger = adServicesLogger;
        sLogger.v("BackgroundFetchExecutionLogger starts.");
    }

    /** Start the background fetch process. */
    public void start() {
        sLogger.v("Start logging the BackgroundFetch process.");
        this.mRunBackgroundFetchStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Close and log the background fetch process into AdServicesLogger. */
    public void close(int numOfEligibleToUpdateCAs, int resultCode) {
        if (mRunBackgroundFetchStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_TIMESTAMP);
        }
        if (mRunBackgroundFetchEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_TIMESTAMP);
        }
        sLogger.v("Close BackgroundFetchExecutionLogger.");
        getApiServiceInternalFinalLatencyInMs();
        this.mRunBackgroundFetchEndTimestamp = getServiceElapsedTimestamp();
        int runBackgroundFetchLatencyInMs =
                (int) (mRunBackgroundFetchEndTimestamp - mRunBackgroundFetchStartTimestamp);

        BackgroundFetchProcessReportedStats backgroundFetchProcessReportedStats =
                BackgroundFetchProcessReportedStats.builder()
                        .setLatencyInMillis(runBackgroundFetchLatencyInMs)
                        .setNumOfEligibleToUpdateCas(numOfEligibleToUpdateCAs)
                        .setResultCode(resultCode)
                        .build();
        sLogger.v("BackgroundFetch process has been logged into AdServicesLogger.");
        mAdServicesLogger.logBackgroundFetchProcessReportedStats(
                backgroundFetchProcessReportedStats);
    }

    /**
     * Getter for number of eligible to update CAs.
     *
     * @return the number of eligible to update CAs.
     */
    public int getNumOfEligibleToUpdateCAs() {
        return mNumOfEligibleToUpdateCAs;
    }

    /**
     * Setter for number of eligible to update CAs
     *
     * @param numOfEligibleToUpdateCAs the number of eligible to update CAs.
     */
    public void setNumOfEligibleToUpdateCAs(int numOfEligibleToUpdateCAs) {
        mNumOfEligibleToUpdateCAs = numOfEligibleToUpdateCAs;
    }
}
