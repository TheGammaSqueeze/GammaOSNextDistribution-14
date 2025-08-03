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

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__UNSET;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.adselection.AdBiddingOutcome;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class for logging the run ad selection process. It provides the functions to collect and log the
 * corresponding ad selection process and its subcomponent processes and log the data into the
 * statsd logs. This class collect data for the telemetry atoms:
 *
 * <ul>
 *   <li>RunAdBiddingProcessReportedStats for bidding stage:
 *       <ul>
 *         <li>Subprocess:
 *             <ul>
 *               <li>getBuyerCustomAudience
 *               <li>RunAdBidding
 *             </ul>
 *       </ul>
 *   <li>RunAdScoringProcessReportedStats for scoring stage:
 *       <ul>
 *         <li>Subprocess:
 *             <ul>
 *               <li>RunAdScoring
 *               <li>GetAdScores
 *               <li>scoreAds
 *             </ul>
 *       </ul>
 *   <li>RunAdSelectionProcessReportedStats for overall ad selection:
 *       <ul>
 *         <li>Subprocess:
 *             <ul>
 *               <li>persistAdSelection
 *             </ul>
 *       </ul>
 * </ul>
 *
 * <p>Each complete parent process (bidding, scoring, and overall ad selection) should call its
 * corresponding start and end methods to record its states and log the generated atom proto into
 * the statsd logger.
 *
 * <p>Each subprocess should call its corresponding start method if it starts, and only call the end
 * method for successful completion. In failure cases, the exceptions thrown should propagate to the
 * end of the parent process, and unset ending timestamps will be clearly communicated in the logged
 * atom.
 */
// TODO(b/259332713): Refactor the logger for individual bidding, scoring process etc.
public class AdSelectionExecutionLogger extends ApiServiceLatencyCalculator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    public static final int SCRIPT_JAVASCRIPT =
            RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;

    @VisibleForTesting
    static final int SCRIPT_UNSET =
            RUN_AD_SCORING_PROCESS_REPORTED__GET_AD_SELECTION_LOGIC_SCRIPT_TYPE__UNSET;

    @VisibleForTesting
    static final String REPEATED_START_PERSIST_AD_SELECTION =
            "The logger has already set the start of the persist-ad-selection process.";

    @VisibleForTesting
    static final String REPEATED_END_PERSIST_AD_SELECTION =
            "The logger has already set the end of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_START_PERSIST_AD_SELECTION =
            "The logger should set the start of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_END_PERSIST_AD_SELECTION =
            "The logger should set the end of the persist-ad-selection process.";

    @VisibleForTesting
    static final String MISSING_PERSIST_AD_SELECTION =
            "The ad selection execution logger should log the persist-ad-selection process.";

    @VisibleForTesting
    static final String REPEATED_END_BIDDING_STAGE =
            "The logger has already set the end state of the bidding stage.";

    @VisibleForTesting
    static final String MISSING_START_RUN_AD_BIDDING =
            "The logger should set the start of the run-ad-selection process.";

    @VisibleForTesting
    static final String REPEATED_START_BIDDING_STAGE =
            "The logger has already set the start of the bidding stage.";

    @VisibleForTesting
    static final String REPEATED_END_GET_BUYERS_CUSTOM_AUDIENCE =
            "The logger has already set the end of the get-buyers-custom-audience process.";

    @VisibleForTesting
    static final String MISSING_START_BIDDING_STAGE =
            "The logger should set the start state of the bidding stage.";

    @VisibleForTesting
    static final String MISSING_END_GET_BUYERS_CUSTOM_AUDIENCE =
            "The logger should set the end of the get-buyers-custom-audience process.";

    @VisibleForTesting
    static final String REPEATED_START_RUN_AD_BIDDING =
            "The logger has already set the start of the run-ad-bidding process.";

    @VisibleForTesting
    static final String MISSING_GET_BUYERS_CUSTOM_AUDIENCE =
            "The logger should set the start of the bidding stage and the "
                    + "get-buyers-custom-audience process.";

    @VisibleForTesting static final float RATIO_OF_CAS_UNSET = -1.0f;

    @VisibleForTesting
    static final String REPEATED_START_RUN_AD_SCORING =
            "The logger has already set the start of the run-ad-scoring process.";

    @VisibleForTesting
    static final String MISSING_START_RUN_AD_SCORING =
            "The logger should set the start of the run-ad-scoring process.";

    @VisibleForTesting
    static final String REPEATED_END_RUN_AD_SCORING =
            "The logger has already set the end of the run-ad-scoring process.";

    @VisibleForTesting
    static final String MISSING_START_GET_AD_SELECTION_LOGIC =
            "The logger should set the start of the get-ad-selection-logic process.";

    @VisibleForTesting
    static final String REPEATED_END_GET_AD_SELECTION_LOGIC =
            "The logger has already set the end of the get-ad-selection-logic process.";

    @VisibleForTesting
    static final String REPEATED_START_GET_TRUSTED_SCORING_SIGNALS =
            "The logger has already set the start of the get-trusted-scoring-signals process.";

    @VisibleForTesting
    static final String MISSING_START_GET_TRUSTED_SCORING_SIGNALS =
            "The logger should set the start of the get-trusted-scoring-signals process.";

    @VisibleForTesting
    static final String REPEATED_END_GET_TRUSTED_SCORING_SIGNALS =
            "The logger has already set the end of the get-trusted-scoring-signals process.";

    @VisibleForTesting
    static final String REPEATED_START_SCORE_ADS =
            "The logger has already set the start of the score-ads process.";

    @VisibleForTesting
    static final String MISSING_START_SCORE_ADS =
            "The logger should set the start of the score-ads process.";

    @VisibleForTesting
    static final String REPEATED_END_SCORE_ADS =
            "The logger has already set the end of the score-ads process.";

    @VisibleForTesting
    static final String REPEATED_START_GET_AD_SELECTION_LOGIC =
            "The logger has already set the start of the get-ad-selection-logic process.";

    @VisibleForTesting
    static final String MISSING_GET_TRUSTED_SCORING_SIGNALS_PROCESS =
            "The logger should set the get-trusted-scoring-signals process.";

    @VisibleForTesting
    static final String MISSING_END_SCORE_ADS =
            "The logger should set the end of the score-ads process.";

    @VisibleForTesting
    static final String REPEATED_START_GET_AD_SCORES =
            "The logger has already set the get-ad-scores progress.";

    @VisibleForTesting
    static final String MISSING_START_GET_AD_SCORES =
            "The logger should set the start of the get-ad-scores process.";

    @VisibleForTesting
    static final String REPEATED_END_GET_AD_SCORES =
            "The logger has already set the ned of the get-ad-scores process.";

    @VisibleForTesting
    static final String MISSING_END_GET_AD_SELECTION_LOGIC =
            "The logger should set the end of the get-ad-selection-logic process.";

    private final Context mContext;
    private final long mBinderElapsedTimestamp;

    // Bidding stage.
    private long mBiddingStageStartTimestamp;
    private long mGetBuyersCustomAudienceEndTimestamp;
    private long mRunAdBiddingStartTimestamp;
    private long mRunAdBiddingEndTimestamp;
    private long mBiddingStageEndTimestamp;
    private int mNumBuyersRequested;
    private int mNumBuyersFetched;
    private int mNumOfAdsEnteringBidding;
    private int mNumOfCAsEnteringBidding;
    private int mNumOfCAsPostBidding;

    // Scoring stage.
    private long mRunAdScoringStartTimestamp;
    private int mNumOfCAsEnteringScoring;
    private int mNumOfRmktAdsEnteringScoring;
    private long mGetAdSelectionLogicStartTimestamp;
    private long mGetAdSelectionLogicEndTimestamp;
    private int mGetAdSelectionLogicScriptSizeInBytes;
    private long mGetAdScoresStartTimestamp;
    private long mGetTrustedScoringSignalsStartTimestamp;
    private long mGetTrustedScoringSignalsEndTimestamp;
    private int mFetchedTrustedScoringSignalsDataSizeInBytes;
    private long mGetAdScoresEndTimestamp;
    private long mScoreAdsStartTimestamp;
    private long mScoreAdsEndTimestamp;
    private long mRunAdScoringEndTimestamp;

    // Persist ad selection.
    private boolean mIsRemarketingAdsWon;
    private long mPersistAdSelectionStartTimestamp;
    private long mPersistAdSelectionEndTimestamp;
    private long mDBAdSelectionSizeInBytes;

    private AdServicesLogger mAdServicesLogger;


    public AdSelectionExecutionLogger(
            @NonNull CallerMetadata callerMetadata,
            @NonNull Clock clock,
            @NonNull Context context,
            @NonNull AdServicesLogger adServicesLogger) {
        super(clock);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(context);
        Objects.requireNonNull(adServicesLogger);
        this.mBinderElapsedTimestamp = callerMetadata.getBinderElapsedTimestamp();
        this.mContext = context;
        this.mAdServicesLogger = adServicesLogger;
        sLogger.v("AdSelectionExecutionLogger starts.");
    }

    /** records the start state of a bidding process. */
    public void startBiddingProcess(int numBuyersRequested) throws IllegalStateException {
        if (mBiddingStageStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_BIDDING_STAGE);
        }
        sLogger.v("Start the bidding process.");
        this.mBiddingStageStartTimestamp = getServiceElapsedTimestamp();
        this.mNumBuyersRequested = numBuyersRequested;
        sLogger.v("The bidding process start timestamp is %d", mBiddingStageStartTimestamp);
        sLogger.v("With number of buyers requested: %d", mNumBuyersRequested);
    }

    /** records the end state of a successful get-buyers-custom-audience process. */
    public void endGetBuyersCustomAudience(int numBuyersFetched) throws IllegalStateException {
        if (mBiddingStageStartTimestamp == 0L) {
            throw new IllegalStateException((MISSING_START_BIDDING_STAGE));
        }
        if (mGetBuyersCustomAudienceEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_BUYERS_CUSTOM_AUDIENCE);
        }
        sLogger.v("End the get-buyers-custom-audience process.");
        this.mGetBuyersCustomAudienceEndTimestamp = getServiceElapsedTimestamp();
        this.mNumBuyersFetched = numBuyersFetched;
        sLogger.v(
                "The get-buyers-custom-audience process ends at %d with"
                        + " num of buyers fetched %d:",
                mGetBuyersCustomAudienceEndTimestamp, mNumBuyersFetched);
    }

    /** records the start state of the run-ad-bidding process. */
    public void startRunAdBidding(@NonNull List<DBCustomAudience> customAudiences)
            throws IllegalStateException {
        Objects.requireNonNull(customAudiences);
        if (mBiddingStageStartTimestamp == 0L || mGetBuyersCustomAudienceEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_GET_BUYERS_CUSTOM_AUDIENCE);
        }
        if (mRunAdBiddingStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_RUN_AD_BIDDING);
        }
        sLogger.v("Starts the run-ad-bidding process.");
        this.mRunAdBiddingStartTimestamp = getServiceElapsedTimestamp();
        if (customAudiences.isEmpty()) return;
        this.mNumOfAdsEnteringBidding =
                customAudiences.stream()
                        .filter(a -> Objects.nonNull(a))
                        .map(a -> a.getAds().size())
                        .reduce(0, (a, b) -> a + b);
        this.mNumOfCAsEnteringBidding = customAudiences.size();
        sLogger.v(
                "Entering bidding NO of Ads: %d:, NO of CAs : %d",
                mNumOfAdsEnteringBidding, mNumOfCAsEnteringBidding);
    }

    /**
     * records the end state of the bidding process and log the generated {@link
     * RunAdBiddingProcessReportedStats} into the {@link AdServicesLogger}.
     */
    public void endBiddingProcess(@Nullable List<AdBiddingOutcome> result, int resultCode)
            throws IllegalStateException {
        sLogger.v("End the BiddingProcess with resultCode %d.", resultCode);
        // The start of the get-buyers-custom-audience must be set as the start of the bidding
        // process.
        if (mBiddingStageStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_BIDDING_STAGE);
        }
        if (mBiddingStageEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_BIDDING_STAGE);
        }
        if (resultCode == STATUS_SUCCESS) {
            // Throws IllegalStateException if the buyers-custom-audience process has not been
            // set correctly.
            if (mGetBuyersCustomAudienceEndTimestamp == 0L) {
                throw new IllegalStateException(MISSING_END_GET_BUYERS_CUSTOM_AUDIENCE);
            } else if (mRunAdBiddingStartTimestamp == 0L) {
                throw new IllegalStateException(MISSING_START_RUN_AD_BIDDING);
            } else if (mRunAdBiddingEndTimestamp > 0L || mBiddingStageEndTimestamp > 0L) {
                throw new IllegalStateException(REPEATED_END_BIDDING_STAGE);
            }
            this.mRunAdBiddingEndTimestamp = getServiceElapsedTimestamp();
            this.mBiddingStageEndTimestamp = mRunAdBiddingEndTimestamp;
            this.mNumOfCAsPostBidding =
                    result.stream()
                            .filter(Objects::nonNull)
                            .map(a -> a.getCustomAudienceBiddingInfo())
                            .filter(Objects::nonNull)
                            .map(a -> a.getCustomAudienceSignals().hashCode())
                            .collect(Collectors.toSet())
                            .size();
            sLogger.v(
                    "Ends of a successful run-ad-bidding process with num of CAs post "
                            + "bidding %d. with end timestamp: %d ",
                    mNumOfCAsPostBidding, mRunAdBiddingEndTimestamp);
        } else {
            sLogger.v("Ends of a failed run-ad-bidding process.");
            this.mBiddingStageEndTimestamp = getServiceElapsedTimestamp();
        }
        sLogger.v("Log the AdRunBiddingProcessReportedStats to the AdServicesLog.");
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                getRunAdBiddingProcessReportedStats(resultCode);
        mAdServicesLogger.logRunAdBiddingProcessReportedStats(runAdBiddingProcessReportedStats);
    }

    /** start the run-ad-scoring process. */
    public void startRunAdScoring(List<AdBiddingOutcome> adBiddingOutcomes)
            throws IllegalStateException {
        if (mRunAdScoringStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_RUN_AD_SCORING);
        }
        sLogger.v("Start the run-ad-scoring process.");
        this.mRunAdScoringStartTimestamp = getServiceElapsedTimestamp();
        this.mNumOfRmktAdsEnteringScoring =
                adBiddingOutcomes.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        this.mNumOfCAsEnteringScoring =
                adBiddingOutcomes.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
    }

    /** start the get-ad-selection-logic process. */
    public void startGetAdSelectionLogic() throws IllegalStateException {
        if (mRunAdScoringStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_RUN_AD_SCORING);
        }
        if (mGetAdSelectionLogicStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_GET_AD_SELECTION_LOGIC);
        }
        sLogger.v("Start the get-ad-selection-logic process.");
        this.mGetAdSelectionLogicStartTimestamp = getServiceElapsedTimestamp();
    }

    /** end a successful get-ad-selection-logic process. */
    public void endGetAdSelectionLogic(@NonNull String adSelectionLogic)
            throws IllegalStateException {
        if (mGetAdSelectionLogicStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GET_AD_SELECTION_LOGIC);
        }
        if (mGetAdSelectionLogicEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_AD_SELECTION_LOGIC);
        }
        sLogger.v("End a successful get-ad-selection-logic process.");
        this.mGetAdSelectionLogicScriptSizeInBytes = adSelectionLogic.getBytes().length;
        this.mGetAdSelectionLogicEndTimestamp = getServiceElapsedTimestamp();
    }

    /** start the get-ad-scores process. */
    public void startGetAdScores() throws IllegalStateException {
        if (mGetAdSelectionLogicEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_END_GET_AD_SELECTION_LOGIC);
        }
        if (mGetAdScoresStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_GET_AD_SCORES);
        }
        sLogger.v("Start the get-ad-scores process.");
        this.mGetAdScoresStartTimestamp = getServiceElapsedTimestamp();
    }

    /** start the get-trusted-scoring-signals process. */
    public void startGetTrustedScoringSignals() throws IllegalStateException {
        if (mGetAdScoresStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GET_AD_SCORES);
        }
        if (mGetTrustedScoringSignalsStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_GET_TRUSTED_SCORING_SIGNALS);
        }
        sLogger.v("Starts the get-trusted-scoring-signals.");
        this.mGetTrustedScoringSignalsStartTimestamp = getServiceElapsedTimestamp();
    }

    /** end a successful get-trusted-scoring-signals process. */
    public void endGetTrustedScoringSignals(@NonNull AdSelectionSignals adSelectionSignals)
            throws IllegalStateException {
        if (mGetTrustedScoringSignalsStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_GET_TRUSTED_SCORING_SIGNALS);
        }
        if (mGetTrustedScoringSignalsEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_TRUSTED_SCORING_SIGNALS);
        }
        sLogger.v("End a successful get-trusted-signals process.");
        this.mFetchedTrustedScoringSignalsDataSizeInBytes =
                adSelectionSignals.toString().getBytes().length;
        this.mGetTrustedScoringSignalsEndTimestamp = getServiceElapsedTimestamp();
    }

    /** start scoreAds script execution process. */
    public void startScoreAds() throws IllegalStateException {
        if (mGetTrustedScoringSignalsEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_GET_TRUSTED_SCORING_SIGNALS_PROCESS);
        }
        if (mScoreAdsStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_SCORE_ADS);
        }
        sLogger.v("Start the execution of the scoreAds script.");
        this.mScoreAdsStartTimestamp = getServiceElapsedTimestamp();
    }

    /** end a complete execution of the scoreAds script. */
    public void endScoreAds() throws IllegalStateException {
        if (mScoreAdsStartTimestamp == 0L) throw new IllegalStateException(MISSING_START_SCORE_ADS);
        if (mScoreAdsEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_SCORE_ADS);
        }
        sLogger.v("End the execution of the scoreAds script successfully.");
        this.mScoreAdsEndTimestamp = getServiceElapsedTimestamp();
    }

    /** end a successful get-ad-scores process. */
    public void endGetAdScores() throws IllegalStateException {
        if (mScoreAdsEndTimestamp == 0L) {
            throw new IllegalStateException(MISSING_END_SCORE_ADS);
        }
        if (mGetAdScoresEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_GET_AD_SCORES);
        }
        sLogger.v("End the get-ad-scores process.");
        this.mGetAdScoresEndTimestamp = getServiceElapsedTimestamp();
    }

    /**
     * end a run-ad-scoring process and log the {@link RunAdScoringProcessReportedStats} atom into
     * the {@link AdServicesLogger}.
     */
    public void endRunAdScoring(int resultCode) throws IllegalStateException {
        if (mRunAdScoringStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_RUN_AD_SCORING);
        }
        if (mRunAdScoringEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_RUN_AD_SCORING);
        }
        sLogger.v("End Running Ad Scoring.");
        this.mRunAdScoringEndTimestamp = getServiceElapsedTimestamp();
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                getRunAdScoringProcessReportedStats(resultCode);
        sLogger.v("Log the RunAdScoringProcessReportedStats into the AdServicesLogger.");
        mAdServicesLogger.logRunAdScoringProcessReportedStats(runAdScoringProcessReportedStats);
    }

    /** records the start state of the persist-ad-selection process. */
    public void startPersistAdSelection(DBAdSelection dbAdSelection) throws IllegalStateException {
        if (mPersistAdSelectionStartTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_START_PERSIST_AD_SELECTION);
        }
        sLogger.v("Starts the persisting ad selection.");
        this.mIsRemarketingAdsWon = Objects.nonNull(dbAdSelection.getBiddingLogicUri());
        this.mPersistAdSelectionStartTimestamp = getServiceElapsedTimestamp();
    }

    /** records the end state of a finished persist-ad-selection process. */
    public void endPersistAdSelection() throws IllegalStateException {
        if (mPersistAdSelectionStartTimestamp == 0L) {
            throw new IllegalStateException(MISSING_START_PERSIST_AD_SELECTION);
        }
        if (mPersistAdSelectionEndTimestamp > 0L) {
            throw new IllegalStateException(REPEATED_END_PERSIST_AD_SELECTION);
        }
        sLogger.v("Ends the persisting ad selection.");
        this.mPersistAdSelectionEndTimestamp = getServiceElapsedTimestamp();
        this.mDBAdSelectionSizeInBytes =
                FileCompatUtils.getDatabasePathHelper(mContext, DATABASE_NAME).length();
        sLogger.v("The persistAdSelection end timestamp is %d:", mPersistAdSelectionEndTimestamp);
        sLogger.v("The database file size is %d", mDBAdSelectionSizeInBytes);
    }

    /**
     * This method should be called at the end of an ad selection process to generate and log the
     * {@link RunAdSelectionProcessReportedStats} into the {@link AdServicesLogger}.
     */
    public void close(int resultCode) throws IllegalStateException {
        sLogger.v("Log the RunAdSelectionProcessReportedStats to the AdServicesLog.");
        if (resultCode == STATUS_SUCCESS) {
            if (mPersistAdSelectionStartTimestamp == 0L
                    && mPersistAdSelectionEndTimestamp == 0L
                    && mDBAdSelectionSizeInBytes == 0L) {
                throw new IllegalStateException(MISSING_PERSIST_AD_SELECTION);
            } else if (mPersistAdSelectionEndTimestamp == 0L || mDBAdSelectionSizeInBytes == 0L) {
                throw new IllegalStateException(MISSING_END_PERSIST_AD_SELECTION);
            }
            sLogger.v(
                    "Log RunAdSelectionProcessReportedStats for a successful ad selection "
                            + "run.");
        } else {
            sLogger.v("Log RunAdSelectionProcessReportedStats for a failed ad selection run.");
        }
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                getRunAdSelectionProcessReportedStats(
                        resultCode, getApiServiceInternalFinalLatencyInMs());
        mAdServicesLogger.logRunAdSelectionProcessReportedStats(runAdSelectionProcessReportedStats);
    }

    /** @return the overall latency in milliseconds of selectAds called by the client interface. */
    public int getRunAdSelectionOverallLatencyInMs() {
        return getBinderLatencyInMs(mBinderElapsedTimestamp)
                + getApiServiceInternalFinalLatencyInMs();
    }

    private RunAdSelectionProcessReportedStats getRunAdSelectionProcessReportedStats(
            int runAdSelectionResultCode, int runAdSelectionLatencyInMs) {
        return RunAdSelectionProcessReportedStats.builder()
                .setIsRemarketingAdsWon(mIsRemarketingAdsWon)
                .setDBAdSelectionSizeInBytes(getDbAdSelectionSizeInBytes())
                .setPersistAdSelectionLatencyInMillis(getPersistAdSelectionLatencyInMs())
                .setPersistAdSelectionResultCode(
                        getPersistAdSelectionResultCode(runAdSelectionResultCode))
                .setRunAdSelectionLatencyInMillis(runAdSelectionLatencyInMs)
                .setRunAdSelectionResultCode(runAdSelectionResultCode)
                .build();
    }

    private RunAdBiddingProcessReportedStats getRunAdBiddingProcessReportedStats(int resultCode) {
        return RunAdBiddingProcessReportedStats.builder()
                .setGetBuyersCustomAudienceLatencyInMills(getGetBuyersCustomAudienceLatencyInMs())
                .setGetBuyersCustomAudienceResultCode(
                        getGetBuyersCustomAudienceResultCode(resultCode))
                .setNumBuyersRequested(getNumBuyersRequested())
                .setNumBuyersFetched(getNumBuyersFetched())
                .setNumOfAdsEnteringBidding(getNumOfAdsEnteringBidding())
                .setNumOfCasEnteringBidding(getNumOfCAsEnteringBidding())
                .setNumOfCasPostBidding(getNumOfCAsPostBidding())
                .setRatioOfCasSelectingRmktAds(getRatioOfCasSelectingRmktAds())
                .setRunAdBiddingLatencyInMillis(getRunAdBiddingLatencyInMs())
                .setRunAdBiddingResultCode(getRunAdBiddingResultCode(resultCode))
                .setTotalAdBiddingStageLatencyInMillis(getTotalAdBiddingStageLatencyInMs())
                .build();
    }

    private RunAdScoringProcessReportedStats getRunAdScoringProcessReportedStats(int resultCode) {
        return RunAdScoringProcessReportedStats.builder()
                .setGetAdSelectionLogicLatencyInMillis(getAdSelectionLogicLatencyInMs())
                .setGetAdSelectionLogicResultCode(getGetAdSelectionLogicResultCode(resultCode))
                .setGetAdSelectionLogicScriptType(getGetAdSelectionLogicScriptType())
                .setFetchedAdSelectionLogicScriptSizeInBytes(
                        getFetchedAdSelectionLogicScriptSizeInBytes())
                .setGetTrustedScoringSignalsLatencyInMillis(
                        getGetTrustedScoringSignalsLatencyInMs())
                .setGetTrustedScoringSignalsResultCode(
                        getGetTrustedScoringSignalsResultCode(resultCode))
                .setFetchedTrustedScoringSignalsDataSizeInBytes(
                        getFetchedTrustedScoringSignalsDataSizeInBytes())
                .setScoreAdsLatencyInMillis(getScoreAdsLatencyInMs())
                .setGetAdScoresLatencyInMillis(getAdScoresLatencyInMs())
                .setGetAdScoresResultCode(getAdScoresResultCode(resultCode))
                .setNumOfCasEnteringScoring(getNumOfCAsEnteringScoring())
                .setNumOfRemarketingAdsEnteringScoring(getNumOfRemarketingAdsEnteringScoring())
                .setNumOfContextualAdsEnteringScoring(FIELD_UNSET)
                .setRunAdScoringLatencyInMillis(getRunScoringLatencyInMs())
                .setRunAdScoringResultCode(resultCode)
                .build();
    }

    private int getGetAdSelectionLogicScriptType() {
        if (mGetAdSelectionLogicEndTimestamp > 0L) return SCRIPT_JAVASCRIPT;
        return SCRIPT_UNSET;
    }

    private int getRunScoringLatencyInMs() {
        if (mRunAdScoringStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return (int) (mRunAdScoringEndTimestamp - mRunAdScoringStartTimestamp);
    }

    private int getNumOfCAsEnteringScoring() {
        if (mRunAdScoringStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return mNumOfCAsEnteringScoring;
    }

    private int getNumOfRemarketingAdsEnteringScoring() {
        if (mRunAdScoringStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return mNumOfRmktAdsEnteringScoring;
    }

    private int getAdScoresResultCode(int resultCode) {
        if (mGetAdScoresStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        if (mGetAdScoresEndTimestamp == 0L) {
            return resultCode;
        }
        return STATUS_SUCCESS;
    }

    private int getAdScoresLatencyInMs() {
        if (mGetAdScoresStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        if (mGetAdScoresEndTimestamp == 0L) {
            return (int) (mRunAdScoringEndTimestamp - mGetAdScoresStartTimestamp);
        }
        return (int) (mGetAdScoresEndTimestamp - mGetAdScoresStartTimestamp);
    }

    private int getScoreAdsLatencyInMs() {
        if (mScoreAdsStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mScoreAdsEndTimestamp == 0L) {
            return (int) (mRunAdScoringEndTimestamp - mScoreAdsStartTimestamp);
        }
        return (int) (mScoreAdsEndTimestamp - mScoreAdsStartTimestamp);
    }

    private int getFetchedTrustedScoringSignalsDataSizeInBytes() {
        if (mGetTrustedScoringSignalsEndTimestamp > 0L) {
            return mFetchedTrustedScoringSignalsDataSizeInBytes;
        }
        return FIELD_UNSET;
    }

    private int getGetTrustedScoringSignalsResultCode(int resultCode) {
        if (mGetTrustedScoringSignalsStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetTrustedScoringSignalsEndTimestamp > 0L) {
            return STATUS_SUCCESS;
        }
        return resultCode;
    }

    private int getGetTrustedScoringSignalsLatencyInMs() {
        if (mGetTrustedScoringSignalsStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetTrustedScoringSignalsEndTimestamp == 0L) {
            return (int) (mRunAdScoringEndTimestamp - mGetTrustedScoringSignalsStartTimestamp);
        }
        return (int)
                (mGetTrustedScoringSignalsEndTimestamp - mGetTrustedScoringSignalsStartTimestamp);
    }

    private int getFetchedAdSelectionLogicScriptSizeInBytes() {
        if (mGetAdSelectionLogicEndTimestamp > 0) {
            return mGetAdSelectionLogicScriptSizeInBytes;
        }
        return FIELD_UNSET;
    }

    private int getGetAdSelectionLogicResultCode(int resultCode) {
        if (mGetAdSelectionLogicStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetAdSelectionLogicEndTimestamp > 0L) {
            return STATUS_SUCCESS;
        }
        return resultCode;
    }

    private int getAdSelectionLogicLatencyInMs() {
        if (mGetAdSelectionLogicStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetAdSelectionLogicEndTimestamp == 0L) {
            return (int) (mRunAdScoringEndTimestamp - mGetAdSelectionLogicStartTimestamp);
        }
        return (int) (mGetAdSelectionLogicEndTimestamp - mGetAdSelectionLogicStartTimestamp);
    }

    private int getNumBuyersFetched() {
        if (mGetBuyersCustomAudienceEndTimestamp > 0L) {
            return mNumBuyersFetched;
        }
        return FIELD_UNSET;
    }

    private int getNumBuyersRequested() {
        if (mBiddingStageStartTimestamp > 0L) {
            return mNumBuyersRequested;
        }
        return FIELD_UNSET;
    }

    private int getNumOfAdsEnteringBidding() {
        if (mRunAdBiddingStartTimestamp > 0L) {
            return mNumOfAdsEnteringBidding;
        }
        return FIELD_UNSET;
    }

    private int getNumOfCAsEnteringBidding() {
        if (mRunAdBiddingStartTimestamp > 0L) {
            return mNumOfCAsEnteringBidding;
        }
        return FIELD_UNSET;
    }

    private int getNumOfCAsPostBidding() {
        if (mRunAdBiddingEndTimestamp > 0L) {
            return mNumOfCAsPostBidding;
        }
        return FIELD_UNSET;
    }

    private float getRatioOfCasSelectingRmktAds() {
        if (mRunAdBiddingEndTimestamp > 0L) {
            return ((float) mNumOfCAsPostBidding) / mNumOfCAsEnteringBidding;
        }
        return RATIO_OF_CAS_UNSET;
    }

    private int getDbAdSelectionSizeInBytes() {
        if (mPersistAdSelectionEndTimestamp > 0L) {
            return (int) mDBAdSelectionSizeInBytes;
        }
        return FIELD_UNSET;
    }

    private int getGetBuyersCustomAudienceResultCode(int resultCode) {
        if (mBiddingStageStartTimestamp == 0) {
            return FIELD_UNSET;
        }
        if (mGetBuyersCustomAudienceEndTimestamp > 0L) {
            return STATUS_SUCCESS;
        }
        return resultCode;
    }

    private int getRunAdBiddingResultCode(int resultCode) {
        if (mRunAdBiddingStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mRunAdBiddingEndTimestamp > 0L) {
            return STATUS_SUCCESS;
        }
        return resultCode;
    }

    private int getPersistAdSelectionResultCode(int resultCode) {
        if (mPersistAdSelectionStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mPersistAdSelectionEndTimestamp > 0L) {
            return STATUS_SUCCESS;
        }
        return resultCode;
    }

    /**
     * @return the latency in milliseconds of the get-buyers-custom-audience process if started,
     *     otherwise the {@link AdServicesLoggerUtil#FIELD_UNSET}.
     */
    private int getGetBuyersCustomAudienceLatencyInMs() {
        if (mBiddingStageStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mGetBuyersCustomAudienceEndTimestamp == 0L) {
            return (int) (mBiddingStageEndTimestamp - mBiddingStageStartTimestamp);
        }
        return (int) (mGetBuyersCustomAudienceEndTimestamp - mBiddingStageStartTimestamp);
    }

    /**
     * @return the latency in milliseconds of the run-ad-bidding process if started, otherwise the
     *     {@link AdServicesLoggerUtil#FIELD_UNSET}.
     */
    private int getRunAdBiddingLatencyInMs() {
        if (mRunAdBiddingStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mRunAdBiddingEndTimestamp == 0L) {
            return (int) (mBiddingStageEndTimestamp - mRunAdBiddingStartTimestamp);
        }
        return (int) (mRunAdBiddingEndTimestamp - mRunAdBiddingStartTimestamp);
    }

    /** @return the total latency of ad bidding stage. */
    private int getTotalAdBiddingStageLatencyInMs() {
        if (mBiddingStageStartTimestamp == 0L) {
            return FIELD_UNSET;
        }
        return (int) (mBiddingStageEndTimestamp - mBiddingStageStartTimestamp);
    }

    /**
     * @return the latency in milliseconds of the persist-ad-selection process if started, otherwise
     *     the {@link AdServicesLoggerUtil#FIELD_UNSET}.
     */
    private int getPersistAdSelectionLatencyInMs() {
        if (mPersistAdSelectionStartTimestamp == 0L) {
            return FIELD_UNSET;
        } else if (mPersistAdSelectionEndTimestamp == 0L) {
            return (int) (getServiceElapsedTimestamp() - mPersistAdSelectionStartTimestamp);
        }
        return (int) (mPersistAdSelectionEndTimestamp - mPersistAdSelectionStartTimestamp);
    }

    private int getBinderLatencyInMs(long binderElapsedTimestamp) {
        return (int) (getStartElapsedTimestamp() - binderElapsedTimestamp) * 2;
    }
}
