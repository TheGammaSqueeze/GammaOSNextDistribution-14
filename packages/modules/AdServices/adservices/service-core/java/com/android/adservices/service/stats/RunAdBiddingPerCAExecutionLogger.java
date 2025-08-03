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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__UNSET;

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class for logging the run ad bidding per CA process. It provides the functions to collect the
 * process and its subcomponent processes and log the data into the statsd logs. This class collects
 * data for the telemetry atoms:
 *
 * <ul>
 *   <li>RunAdBiddingPerCAProcessReportedStats for bidding per CA stage:
 *       <ul>
 *         <li>Subprocess:
 *             <ul>
 *               <li>getBuyerDecisionLogic
 *               <li>RunBidding
 *               <li>GetTrustedBiddingSignals
 *               <li>generateBids
 *             </ul>
 *       </ul>
 * </ul>
 *
 * <p>Only the runAdBiddingPerCA process should call the close method to record the end state
 * regardless success or failure and log the generated atom proto into the statsd logger.
 *
 * <p>Each subprocess should call its corresponding start method if it starts, and only call the end
 * method for successful completion. In failure cases, the exceptions thrown should propagate to the
 * end of the parent process, and unset ending timestamps will be clearly communicated in the logged
 * atom.
 */
public class RunAdBiddingPerCAExecutionLogger extends ApiServiceLatencyCalculator {
    @VisibleForTesting
    public static final int SCRIPT_JAVASCRIPT =
            RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;

    @VisibleForTesting
    public static final int SCRIPT_UNSET =
            RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__UNSET;

    @VisibleForTesting
    static final String MISSING_START_GET_BUYER_DECISION_LOGIC =
            "The logger should set the start of the get-buyer-decision-logic process. ";

    @VisibleForTesting
    static final String MISSING_END_GET_BUYER_DECISION_LOGIC =
            "The logger should set the end of the get-buyer-decision-logic process.";
    @VisibleForTesting
    static final String MISSING_START_GET_TRUSTED_BIDDING_SIGNALS =
            "The logger should set the start of the get-trusted-bidding-signals process.";
    @VisibleForTesting
    static final String MISSING_START_RUN_BIDDING =
            "The logger should set the start of the run-bidding process.";
    @VisibleForTesting
    static final String MISSING_END_GET_TRUSTED_BIDDING_SIGNALS =
            "The logger should set the end of the get-trusted-bidding-signals process.";
    @VisibleForTesting
    static final String MISSING_START_GENERATE_BIDS =
            "The logger should set the start of the generate-bids process.";
    @VisibleForTesting
    static final String MISSING_END_GENERATE_BIDS =
            "The logger should set the end of the generate-bids process.";
    @VisibleForTesting
    static final String MISSING_END_RUN_BIDDING =
            "The logger should set the end of the run-bidding process.";
    @VisibleForTesting
    static final String MISSING_START_RUN_AD_BIDDING_PER_CA =
            "The logger should set the start of the run-ad-bidding-per-ca process.";
    @VisibleForTesting
    static final String REPEATED_END_RUN_AD_BIDDING_PER_CA =
            "The logger has already set the end of the run-ad-bidding-per-ca process.";
    @VisibleForTesting
    static final String REPEATED_END_GET_BUYER_DECISION_LOGIC =
            "The logger has already set the end of the get-buyer-decision-logic process.";
    @VisibleForTesting
    static final String REPEATED_START_GET_BUYER_DECISION_LOGIC =
            "The logger has already set the start of the get-buyer-decision-logic process.";
    @VisibleForTesting
    static final String REPEATED_START_TRUSTED_BIDDING_SIGNALS =
            "The logger have already set the start of the get-trusted-bidding-signals process.";
    @VisibleForTesting
    static final String REPEATED_END_GET_TRUSTED_BIDDING_SIGNALS =
            "The logger have already set the end of the get-trusted-bidding-signals process.";
    @VisibleForTesting
    static final String REPEATED_START_GENERATE_BIDS =
            "The logger have already set the start of the generate-bids process.";
    @VisibleForTesting
    static final String REPEATED_END_GENERATE_BIDS =
            "The logger have already set the end of the generate-bids process.";
    @VisibleForTesting
    static final String REPEATED_START_RUN_AD_BIDDING_PER_CA =
            "The logger has already set the start of the run-ad-bidding-per-ca process.";
    @VisibleForTesting
    static final String REPEATED_END_RUN_BIDDING =
            "The logger have already set the end of the run-bidding process.";
    @VisibleForTesting
    static final String REPEATED_START_RUN_BIDDING =
            "The logger has set the start of the run-bidding process.";
    private final AdServicesLogger mAdServicesLogger;
    private int mNumOfAdsForBidding;
    private long mGetBuyerDecisionLogicStartTimestamp;
    private long mGetBuyerDecisionLogicEndTimestamp;
    private int mFetchedBuyerDecisionLogicScriptSizeInBytes;
    private long mRunBiddingStartTimestamp;

    private long mRunAdBiddingPerCAEndTimestamp;
    private long mGetTrustedBiddingSignalsStartTimestamp;
    private long mGetTrustedBiddingSignalsEndTimestamp;
    private int mFetchedTrustedBiddingSignalsDataSizeInBytes;
    private int mNumOfKeysOfTrustedBiddingSignals;
    private long mGenerateBidsStartTimestamp;
    private long mGenerateBidsEndTimestamp;
    private long mRunBiddingEndTimestamp;
    private long mRunAdBiddingPerCAStartTimestamp;

    public RunAdBiddingPerCAExecutionLogger(
            @NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        super(clock);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        this.mAdServicesLogger = adServicesLogger;
    }

    /** Start the run-ad-bidding-per-ca process. */
    public void startRunAdBiddingPerCA(int numOfAdsForBidding) {
        if (mRunAdBiddingPerCAStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_RUN_AD_BIDDING_PER_CA);
        }
        LogUtil.v("Start the run-ad-bidding-per-ca process.");
        this.mRunAdBiddingPerCAStartTimestamp = getServiceElapsedTimestamp();
        this.mNumOfAdsForBidding = numOfAdsForBidding;
    }

    /** Start the get-buyer-decision-logic process. */
    public void startGetBuyerDecisionLogic() {
        if (mRunAdBiddingPerCAStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_RUN_AD_BIDDING_PER_CA);
        }
        if (mGetBuyerDecisionLogicStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_GET_BUYER_DECISION_LOGIC);
        }
        LogUtil.v("Start the get-buyer-decision-logic process.");
        this.mGetBuyerDecisionLogicStartTimestamp = getServiceElapsedTimestamp();
    }

    /** End the get-buyer-decision-logic process. */
    public void endGetBuyerDecisionLogic(String buyerDecisionLogicJs) {
        if (mGetBuyerDecisionLogicStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GET_BUYER_DECISION_LOGIC);
        }
        if (mGetBuyerDecisionLogicEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_BUYER_DECISION_LOGIC);
        }
        LogUtil.v("End a successful get-buyer-decision-logic process.");
        this.mGetBuyerDecisionLogicEndTimestamp = getServiceElapsedTimestamp();
        this.mFetchedBuyerDecisionLogicScriptSizeInBytes =
                Objects.nonNull(buyerDecisionLogicJs)
                        ? buyerDecisionLogicJs.getBytes().length
                        : FIELD_UNSET;
    }

    /** Start the run-bidding process. */
    public void startRunBidding() {
        if (mGetBuyerDecisionLogicEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_END_GET_BUYER_DECISION_LOGIC);
        }
        if (mRunBiddingStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_RUN_BIDDING);
        }
        LogUtil.v("Start the run-bidding process.");
        this.mRunBiddingStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Start the get-trusted-bidding-signals process. */
    public void startGetTrustedBiddingSignals(int numOfKeysOfTrustedBiddingSignals) {
        if (mRunBiddingStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_RUN_BIDDING);
        }
        if (mGetTrustedBiddingSignalsStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_TRUSTED_BIDDING_SIGNALS);
        }
        LogUtil.v("Start the get-trusted-bidding-signals process.");
        this.mGetTrustedBiddingSignalsStartTimestamp = getServiceElapsedTimestamp();
        this.mNumOfKeysOfTrustedBiddingSignals = numOfKeysOfTrustedBiddingSignals;
    }

    /** Recode the end of get-trusted-bidding-signals. */
    public void endGetTrustedBiddingSignals(AdSelectionSignals trustedBiddingSignals) {
        if (mGetTrustedBiddingSignalsStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GET_TRUSTED_BIDDING_SIGNALS);
        }
        if (mGetTrustedBiddingSignalsEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_TRUSTED_BIDDING_SIGNALS);
        }
        LogUtil.v("End a successful get-trusted-bidding-signals process.");
        this.mGetTrustedBiddingSignalsEndTimestamp = getServiceElapsedTimestamp();

        this.mFetchedTrustedBiddingSignalsDataSizeInBytes =
                Objects.nonNull(trustedBiddingSignals)
                        ? trustedBiddingSignals.getSizeInBytes()
                        : FIELD_UNSET;
    }

    /** Record the start of the generate-bids process. */
    public void startGenerateBids() {
        if (mGetTrustedBiddingSignalsEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_END_GET_TRUSTED_BIDDING_SIGNALS);
        }
        if (mGenerateBidsStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_GENERATE_BIDS);
        }
        LogUtil.v("Start the generate-bids process.");
        this.mGenerateBidsStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Record the end of the generate-bids process. */
    public void endGenerateBids() {
        if (mGenerateBidsStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GENERATE_BIDS);
        }
        if (mGenerateBidsEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GENERATE_BIDS);
        }
        LogUtil.v("End a successful generate-bids process.");
        this.mGenerateBidsEndTimestamp = getServiceElapsedTimestamp();
    }

    /** End a successful run-bidding process. */
    public void endRunBidding() {
        if (mGenerateBidsEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_END_GENERATE_BIDS);
        }
        if (mRunBiddingEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_RUN_BIDDING);
        }
        LogUtil.v("End a successful run-bidding process.");
        this.mRunBiddingEndTimestamp = getServiceElapsedTimestamp();
    }

    /** End the run-ad-bidding-per-ca process and log the generated atom into logger. */
    public void close(int resultCode) {
        if (resultCode == STATUS_SUCCESS) {
            if (mRunBiddingEndTimestamp == 0L) {
                throw new IllegalStateException(MISSING_END_RUN_BIDDING);
            }
        }
        if (mRunAdBiddingPerCAStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_RUN_AD_BIDDING_PER_CA);
        }
        if (mRunAdBiddingPerCAEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_RUN_AD_BIDDING_PER_CA);
        }
        // Stop the logger's ApiServiceLatencyCalculator.
        getApiServiceInternalFinalLatencyInMs();
        this.mRunAdBiddingPerCAEndTimestamp = getServiceElapsedTimestamp();
        int runAdBiddingPerCALatencyInMs =
                (int) (mRunAdBiddingPerCAEndTimestamp - mRunAdBiddingPerCAStartTimestamp);
        LogUtil.v("End the run-ad-bidding-per-ca process.");
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                getRunAdBiddingPerCAProcessReportedStats(resultCode, runAdBiddingPerCALatencyInMs);
        mAdServicesLogger.logRunAdBiddingPerCAProcessReportedStats(
                runAdBiddingPerCAProcessReportedStats);
    }

    private RunAdBiddingPerCAProcessReportedStats getRunAdBiddingPerCAProcessReportedStats(
            int resultCode, int runAdBiddingPerCALatencyMs) {
        return RunAdBiddingPerCAProcessReportedStats.builder()
                .setNumOfAdsForBidding(mNumOfAdsForBidding)
                .setRunAdBiddingPerCaLatencyInMillis(runAdBiddingPerCALatencyMs)
                .setRunAdBiddingPerCaResultCode(resultCode)
                .setGetBuyerDecisionLogicLatencyInMillis(getBuyerDecisionLogicLatencyInMs())
                .setGetBuyerDecisionLogicResultCode(getBuyerDecisionLogicResultCode(resultCode))
                .setBuyerDecisionLogicScriptType(getBuyerDecisionLogicScriptType())
                .setFetchedBuyerDecisionLogicScriptSizeInBytes(
                        getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .setNumOfKeysOfTrustedBiddingSignals(getNumOfKeysOfTrustedBiddingSignals())
                .setFetchedTrustedBiddingSignalsDataSizeInBytes(
                        getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .setGetTrustedBiddingSignalsLatencyInMillis(getTrustedBiddingSignalsLatencyInMs())
                .setGetTrustedBiddingSignalsResultCode(
                        getTrustedBiddingSignalsResultCode(resultCode))
                .setGenerateBidsLatencyInMillis(getGenerateBidsLatencyInMs())
                .setRunBiddingLatencyInMillis(getRunBiddingLatencyInMs())
                .setRunBiddingResultCode(getRunBiddingResultCode(resultCode))
                .build();
    }

    private int getBuyerDecisionLogicScriptType() {
        if (mGetBuyerDecisionLogicEndTimestamp == 0L) {
            return SCRIPT_UNSET;
        }
        return SCRIPT_JAVASCRIPT;
    }

    private int getRunBiddingResultCode(int resultCode) {
        if (mRunBiddingStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        if (mRunBiddingEndTimestamp == 0L) {
            return resultCode;
        }
        return STATUS_SUCCESS;
    }

    private int getRunBiddingLatencyInMs() {
        if (mRunBiddingStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        if (mRunBiddingEndTimestamp == 0L) {
            return (int) (mRunAdBiddingPerCAEndTimestamp - mRunBiddingStartTimestamp);
        }
        return (int) (mRunBiddingEndTimestamp - mRunBiddingStartTimestamp);
    }

    private int getGenerateBidsLatencyInMs() {
        if (mGenerateBidsStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        if (mGenerateBidsEndTimestamp == 0L) {
            return (int) (mRunAdBiddingPerCAEndTimestamp - mGenerateBidsStartTimestamp);
        }
        return (int) (mGenerateBidsEndTimestamp - mGenerateBidsStartTimestamp);
    }

    private int getTrustedBiddingSignalsResultCode(int resultCode) {
        if (mGetTrustedBiddingSignalsStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetTrustedBiddingSignalsEndTimestamp == 0L) {
            return resultCode;
        }
        return STATUS_SUCCESS;
    }

    private int getTrustedBiddingSignalsLatencyInMs() {
        if (mGetTrustedBiddingSignalsStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetTrustedBiddingSignalsEndTimestamp == 0L) {
            return (int) (mRunAdBiddingPerCAEndTimestamp - mGetTrustedBiddingSignalsStartTimestamp);
        }
        return (int)
                (mGetTrustedBiddingSignalsEndTimestamp - mGetTrustedBiddingSignalsStartTimestamp);
    }

    private int getFetchedTrustedBiddingSignalsDataSizeInBytes() {
        if (mGetTrustedBiddingSignalsEndTimestamp > 0L) {
            return mFetchedTrustedBiddingSignalsDataSizeInBytes;
        }
        return FIELD_UNSET;
    }

    private int getNumOfKeysOfTrustedBiddingSignals() {
        if (mGetTrustedBiddingSignalsStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return mNumOfKeysOfTrustedBiddingSignals;
    }

    private int getFetchedBuyerDecisionLogicScriptSizeInBytes() {
        if (mGetBuyerDecisionLogicEndTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return mFetchedBuyerDecisionLogicScriptSizeInBytes;
    }

    private int getBuyerDecisionLogicResultCode(int resultCode) {
        if (mGetBuyerDecisionLogicStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetBuyerDecisionLogicEndTimestamp == 0L) {
            return resultCode;
        }
        return STATUS_SUCCESS;
    }

    private int getBuyerDecisionLogicLatencyInMs() {
        if (mGetBuyerDecisionLogicStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetBuyerDecisionLogicEndTimestamp == 0L) {
            return (int) (mRunAdBiddingPerCAEndTimestamp - mGetBuyerDecisionLogicStartTimestamp);
        }
        return (int) (mGetBuyerDecisionLogicEndTimestamp - mGetBuyerDecisionLogicStartTimestamp);
    }
}
