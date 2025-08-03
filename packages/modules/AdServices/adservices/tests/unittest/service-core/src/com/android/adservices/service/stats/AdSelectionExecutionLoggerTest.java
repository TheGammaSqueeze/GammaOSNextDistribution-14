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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_END_GET_AD_SELECTION_LOGIC;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_END_GET_BUYERS_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_END_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_END_SCORE_ADS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_GET_BUYERS_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_BIDDING_STAGE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_GET_AD_SCORES;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_GET_AD_SELECTION_LOGIC;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_GET_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_RUN_AD_BIDDING;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_RUN_AD_SCORING;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.MISSING_START_SCORE_ADS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.RATIO_OF_CAS_UNSET;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_BIDDING_STAGE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_GET_AD_SCORES;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_GET_AD_SELECTION_LOGIC;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_GET_BUYERS_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_GET_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_RUN_AD_SCORING;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_END_SCORE_ADS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_BIDDING_STAGE;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_GET_AD_SCORES;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_GET_AD_SELECTION_LOGIC;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_GET_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_PERSIST_AD_SELECTION;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_RUN_AD_BIDDING;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_RUN_AD_SCORING;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.REPEATED_START_SCORE_ADS;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_JAVASCRIPT;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_UNSET;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.adselection.AdBiddingOutcome;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdSelectionExecutionLoggerTest {

    public static final int GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS = 1;
    public static final int RUN_AD_BIDDING_LATENCY_MS = 1;
    public static final int GET_AD_SELECTION_LOGIC_LATENCY_MS = 1;
    public static final int GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS = 1;
    public static final int SCORE_ADS_LATENCY_MS = 1;
    public static final int PERSIST_AD_SELECTION_LATENCY_MS = 1;
    public static final long DB_AD_SELECTION_FILE_SIZE = 10L;
    public static final boolean IS_RMKT_ADS_WON_UNSET = false;
    public static final int DB_AD_SELECTION_SIZE_IN_BYTES_UNSET = -1;
    public static final boolean IS_RMKT_ADS_WON = true;
    public static final int NUM_BUYERS_REQUESTED = 5;
    public static final int NUM_BUYERS_FETCHED = 3;
    private static final long BINDER_ELAPSED_TIMESTAMP = 90L;
    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    private static final int BINDER_LATENCY_MS = 2;
    public static final long START_ELAPSED_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;
    public static final long BIDDING_STAGE_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1L;
    public static final long GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP =
            BIDDING_STAGE_START_TIMESTAMP + GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS;
    public static final long RUN_AD_BIDDING_START_TIMESTAMP =
            GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP + 1L;
    public static final long RUN_AD_BIDDING_END_TIMESTAMP =
            RUN_AD_BIDDING_START_TIMESTAMP + RUN_AD_BIDDING_LATENCY_MS;
    public static final long BIDDING_STAGE_END_TIMESTAMP = RUN_AD_BIDDING_END_TIMESTAMP;
    public static final long TOTAL_BIDDING_STAGE_LATENCY_IN_MS =
            BIDDING_STAGE_END_TIMESTAMP - BIDDING_STAGE_START_TIMESTAMP;
    public static final long RUN_AD_SCORING_START_TIMESTAMP = RUN_AD_BIDDING_END_TIMESTAMP;
    public static final long GET_AD_SELECTION_LOGIC_START_TIMESTAMP =
            RUN_AD_SCORING_START_TIMESTAMP + 1L;
    public static final long GET_AD_SELECTION_LOGIC_END_TIMESTAMP =
            GET_AD_SELECTION_LOGIC_START_TIMESTAMP + GET_AD_SELECTION_LOGIC_LATENCY_MS;
    public static final long GET_AD_SCORES_START_TIMESTAMP = GET_AD_SELECTION_LOGIC_END_TIMESTAMP;
    public static final long GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP =
            GET_AD_SCORES_START_TIMESTAMP + 1;
    public static final long GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP =
            GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP + GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS;
    public static final long SCORE_ADS_START_TIMESTAMP =
            GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP + 1L;
    public static final long SCORE_ADS_END_TIMESTAMP =
            SCORE_ADS_START_TIMESTAMP + SCORE_ADS_LATENCY_MS;
    public static final long GET_AD_SCORES_END_TIMESTAMP = SCORE_ADS_END_TIMESTAMP;
    public static final int GET_AD_SCORES_LATENCY_MS =
            (int) (GET_AD_SCORES_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP);
    public static final long RUN_AD_SCORING_END_TIMESTAMP = GET_AD_SCORES_END_TIMESTAMP + 1L;
    public static final int RUN_AD_SCORING_LATENCY_MS =
            (int) (RUN_AD_SCORING_END_TIMESTAMP - RUN_AD_SCORING_START_TIMESTAMP);
    public static final long PERSIST_AD_SELECTION_START_TIMESTAMP =
            RUN_AD_SCORING_END_TIMESTAMP + 1;
    public static final long PERSIST_AD_SELECTION_END_TIMESTAMP =
            PERSIST_AD_SELECTION_START_TIMESTAMP + PERSIST_AD_SELECTION_LATENCY_MS;
    public static final long STOP_ELAPSED_TIMESTAMP = PERSIST_AD_SELECTION_END_TIMESTAMP + 1;
    public static final int RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - START_ELAPSED_TIMESTAMP);
    public static final int RUN_AD_SELECTION_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_uris");
    private static final List<AdTechIdentifier> BUYERS =
            Arrays.asList(
                    AdSelectionConfigFixture.BUYER_1,
                    AdSelectionConfigFixture.BUYER_2,
                    AdSelectionConfigFixture.BUYER_3);
    private static final List<DBCustomAudience> CUSTOM_AUDIENCES =
            DBCustomAudienceFixture.getListOfBuyersCustomAudiences(BUYERS);
    private static final int NUM_OF_ADS_ENTERING_BIDDING =
            CUSTOM_AUDIENCES.stream()
                    .map(DBCustomAudience::getAds)
                    .filter(Objects::nonNull)
                    .map(List::size)
                    .reduce(0, Integer::sum);
    private static final int NUM_OF_CAS_ENTERING_BIDDING = CUSTOM_AUDIENCES.size();
    private static final double BID1 = 1.0;
    private static final double BID2 = 2.0;
    private static final double BID3 = 3.0;
    private static final List<Pair<AdTechIdentifier, Double>> buyersAndBids =
            Arrays.asList(
                    Pair.create(AdSelectionConfigFixture.BUYER_1, BID1),
                    Pair.create(AdSelectionConfigFixture.BUYER_2, BID2),
                    Pair.create(AdSelectionConfigFixture.BUYER_3, BID3));
    public static final List<AdBiddingOutcome> AD_BIDDING_OUTCOMES =
            AdBiddingOutcomeFixture.getListOfAdBiddingOutcomes(buyersAndBids);
    private static final int NUM_OF_CAS_POST_BIDDING =
            AD_BIDDING_OUTCOMES.stream()
                    .map(
                            a ->
                                    a.getCustomAudienceBiddingInfo()
                                            .getCustomAudienceSignals()
                                            .hashCode())
                    .collect(Collectors.toSet())
                    .size();
    private static final float RATIO_OF_CAS_SELECTING_RMKT_ADS =
            ((float) NUM_OF_CAS_POST_BIDDING) / NUM_OF_CAS_ENTERING_BIDDING;

    public static final int NUM_OF_CAS_ENTERING_SCORING =
            AD_BIDDING_OUTCOMES.stream()
                    .filter(Objects::nonNull)
                    .map(AdBiddingOutcome::getCustomAudienceBiddingInfo)
                    .filter(Objects::nonNull)
                    .map(a -> a.getCustomAudienceSignals().hashCode())
                    .collect(Collectors.toSet())
                    .size();
    public static final int NUM_OF_ADS_ENTERING_SCORING =
            AD_BIDDING_OUTCOMES.stream()
                    .filter(Objects::nonNull)
                    .map(AdBiddingOutcome::getAdWithBid)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())
                    .size();
    private static final String SCRIPT_STRING = "The fetched script string.";
    public static final int FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES =
            SCRIPT_STRING.getBytes().length;
    public static final int FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES =
            SCRIPT_STRING.getBytes().length;
    @Captor
    ArgumentCaptor<RunAdSelectionProcessReportedStats>
            mRunAdSelectionProcessReportedStatsArgumentCaptor;
    @Captor
    ArgumentCaptor<RunAdBiddingProcessReportedStats>
            mRunAdBiddingProcessReportedStatsArgumentCaptor;
    @Captor
    ArgumentCaptor<RunAdScoringProcessReportedStats>
            mRunAdScoringProcessReportedStatsArgumentCaptor;

    private String mAdSelectionLogic = SCRIPT_STRING;
    private AdSelectionSignals mAdSelectionSignals = AdSelectionSignals.fromString(SCRIPT_STRING);
    @Mock private Context mContextMock;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private Clock mMockClock;
    @Mock private DBAdSelection mMockDBAdSelection;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
    }

    @Test
    public void testAdSelectionExecutionLogger_SuccessAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        when(mContextMock.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);

        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        // Set the start and end state of the subcomponent run-ad-bidding process.
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        adSelectionExecutionLogger.endBiddingProcess(AD_BIDDING_OUTCOMES, STATUS_SUCCESS);
        // Set the start and end states of the scoring stage.
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();
        adSelectionExecutionLogger.endScoreAds();
        adSelectionExecutionLogger.endGetAdScores();
        adSelectionExecutionLogger.endRunAdScoring(STATUS_SUCCESS);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection(mMockDBAdSelection);
        // Set end state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.endPersistAdSelection();
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        int resultCode = STATUS_SUCCESS;
        adSelectionExecutionLogger.close(resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(NUM_OF_CAS_POST_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);

        // Verify the logging of the RunAdScoringProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES);

        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(SCORE_ADS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(GET_AD_SCORES_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_ADS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);

        // Verify the logging of the RunAdSelectionProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo((int) (DB_AD_SELECTION_FILE_SIZE));
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_FailedAdSelectionDuringScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        // Set the start and end state of the subcomponent run-ad-bidding process.
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        adSelectionExecutionLogger.endBiddingProcess(AD_BIDDING_OUTCOMES, STATUS_SUCCESS);
        // Set the start and end states of the scoring stage.
        int resultCode = STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();
        adSelectionExecutionLogger.endRunAdScoring(resultCode);
        adSelectionExecutionLogger.close(resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(NUM_OF_CAS_POST_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);

        // Verify the logging of the RunAdScoringProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES);

        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - SCORE_ADS_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_ADS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_FailedAdSelectionDuringFetchTrustedScoringSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        // Set the start and end state of the subcomponent run-ad-bidding process.
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        adSelectionExecutionLogger.endBiddingProcess(AD_BIDDING_OUTCOMES, STATUS_SUCCESS);
        // Set the start and end states of the scoring stage.
        int resultCode = STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endRunAdScoring(resultCode);

        adSelectionExecutionLogger.close(resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(NUM_OF_CAS_POST_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);

        // Verify the logging of the RunAdScoringProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES);

        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(
                        (int)
                                (RUN_AD_SCORING_END_TIMESTAMP
                                        - GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(resultCode);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_ADS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_FailedAdSelectionDuringGetAdSelectionLogicScript() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        // Set the start and end state of the subcomponent run-ad-bidding process.
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        adSelectionExecutionLogger.endBiddingProcess(AD_BIDDING_OUTCOMES, STATUS_SUCCESS);
        // Set the start and end states of the scoring stage.
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        int resultCode = STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.endRunAdScoring(resultCode);
        adSelectionExecutionLogger.close(resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(NUM_OF_CAS_POST_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);

        // Verify the logging of the RunAdScoringProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(
                        (int)
                                (RUN_AD_SCORING_END_TIMESTAMP
                                        - GET_AD_SELECTION_LOGIC_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_UNSET);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_ADS_ENTERING_SCORING);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartOfGetBuyersCustomAudience() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, BIDDING_STAGE_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent get-buyers-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED));
        assertThat(throwable.getMessage()).contains(REPEATED_START_BIDDING_STAGE);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndOfGetBuyersCustomAudience() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endGetBuyersCustomAudience(
                                        NUM_BUYERS_FETCHED));
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_BUYERS_CUSTOM_AUDIENCE);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartBiddingStage() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endGetBuyersCustomAudience(
                                        NUM_BUYERS_FETCHED));
        assertThat(throwable.getMessage()).contains(MISSING_START_BIDDING_STAGE);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingGetBuyersCustomAudience() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_BIDDING_START_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES));
        assertThat(throwable.getMessage()).contains(MISSING_GET_BUYERS_CUSTOM_AUDIENCE);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartRunAdBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES));
        assertThat(throwable.getMessage()).contains(REPEATED_START_RUN_AD_BIDDING);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartOfBiddingWithEndBiddingStage() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endBiddingProcess(
                                        AD_BIDDING_OUTCOMES, STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_START_BIDDING_STAGE);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingEndOfGetBuyersCAsWithEndRunAdBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, BIDDING_STAGE_START_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endBiddingProcess(
                                        AD_BIDDING_OUTCOMES, STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_END_GET_BUYERS_CUSTOM_AUDIENCE);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartOfRunAdBiddingWithEndRunAdBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endBiddingProcess(
                                        AD_BIDDING_OUTCOMES, STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_START_RUN_AD_BIDDING);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndRunAdBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        adSelectionExecutionLogger.endBiddingProcess(AD_BIDDING_OUTCOMES, STATUS_SUCCESS);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endBiddingProcess(
                                        AD_BIDDING_OUTCOMES, STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(REPEATED_END_BIDDING_STAGE);
    }

    @Test
    public void testAdSelectionExecutionLogger_failedGetBuyersCustomAudience() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP);

        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.endBiddingProcess(null, resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched()).isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    @Test
    public void testAdSelectionExecutionLogger_emptyFetchedCustomAudiences() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP);

        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.endBiddingProcess(null, resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    @Test
    public void testAdSelectionExecutionLogger_FailedDuringRunAdBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP);

        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end state of the subcomponent get-BUYERS-custom-audience process.
        adSelectionExecutionLogger.startBiddingProcess(NUM_BUYERS_REQUESTED);
        adSelectionExecutionLogger.endGetBuyersCustomAudience(NUM_BUYERS_FETCHED);
        adSelectionExecutionLogger.startRunAdBidding(CUSTOM_AUDIENCES);
        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.endBiddingProcess(null, resultCode);

        // Verify the logging of the RunAdBiddingProcessReportedStats.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(NUM_BUYERS_REQUESTED);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(NUM_BUYERS_FETCHED);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(RATIO_OF_CAS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo((int) (BIDDING_STAGE_END_TIMESTAMP - RUN_AD_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartRunAdScoring() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_SCORING_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set the start of the run-ad-scoring process.
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES));
        assertThat(throwable.getMessage()).contains(REPEATED_START_RUN_AD_SCORING);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartRunAdScoring() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set the start of the get-ad-selection-logic process.
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetAdSelectionLogic());
        assertThat(throwable.getMessage()).contains(MISSING_START_RUN_AD_SCORING);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartGetAdSelectionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_SCORING_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set the start of the get-ad-selection-logic process.
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic));
        assertThat(throwable.getMessage()).contains(MISSING_START_GET_AD_SELECTION_LOGIC);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartGetAdSelectionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetAdSelectionLogic());
        assertThat(throwable.getMessage()).contains(REPEATED_START_GET_AD_SELECTION_LOGIC);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingEndGetAdSelectionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_SCORING_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetAdScores());
        assertThat(throwable.getMessage()).contains(MISSING_END_GET_AD_SELECTION_LOGIC);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndGetAdSelectionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic));
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_AD_SELECTION_LOGIC);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartGetAdScores() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetTrustedScoringSignals());
        assertThat(throwable.getMessage()).contains(MISSING_START_GET_AD_SCORES);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartGetAdScores() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetAdScores());
        assertThat(throwable.getMessage()).contains(REPEATED_START_GET_AD_SCORES);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartGetTrustedScoringSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endGetTrustedScoringSignals(
                                        mAdSelectionSignals));
        assertThat(throwable.getMessage()).contains(MISSING_START_GET_TRUSTED_SCORING_SIGNALS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartGetTrustedScoringSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startGetTrustedScoringSignals());
        assertThat(throwable.getMessage()).contains(REPEATED_START_GET_TRUSTED_SCORING_SIGNALS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndGetTrustedScoringSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.endGetTrustedScoringSignals(
                                        mAdSelectionSignals));
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_TRUSTED_SCORING_SIGNALS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.startScoreAds());
        assertThat(throwable.getMessage()).contains(REPEATED_START_SCORE_ADS);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endScoreAds());
        assertThat(throwable.getMessage()).contains(MISSING_START_SCORE_ADS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();
        adSelectionExecutionLogger.endScoreAds();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endScoreAds());
        assertThat(throwable.getMessage()).contains(REPEATED_END_SCORE_ADS);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingEndScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endGetAdScores());
        assertThat(throwable.getMessage()).contains(MISSING_END_SCORE_ADS);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndGetScoreAds() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.startGetAdSelectionLogic();
        adSelectionExecutionLogger.endGetAdSelectionLogic(mAdSelectionLogic);
        adSelectionExecutionLogger.startGetAdScores();
        adSelectionExecutionLogger.startGetTrustedScoringSignals();
        adSelectionExecutionLogger.endGetTrustedScoringSignals(mAdSelectionSignals);
        adSelectionExecutionLogger.startScoreAds();
        adSelectionExecutionLogger.endScoreAds();
        adSelectionExecutionLogger.endGetAdScores();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endGetAdScores());
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_AD_SCORES);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndRunAdScoring() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_SCORING_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);

        adSelectionExecutionLogger.startRunAdScoring(AD_BIDDING_OUTCOMES);
        adSelectionExecutionLogger.endRunAdScoring(STATUS_INTERNAL_ERROR);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.endRunAdScoring(STATUS_INTERNAL_ERROR));
        assertThat(throwable.getMessage()).contains(REPEATED_END_RUN_AD_SCORING);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantStartOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, PERSIST_AD_SELECTION_START_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection(mMockDBAdSelection);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adSelectionExecutionLogger.startPersistAdSelection(
                                        mMockDBAdSelection));
        assertThat(throwable.getMessage()).contains(REPEATED_START_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_redundantEndOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP);
        when(mContextMock.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start and end states of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection(mMockDBAdSelection);
        adSelectionExecutionLogger.endPersistAdSelection();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        adSelectionExecutionLogger::endPersistAdSelection);
        assertThat(throwable.getMessage()).contains(REPEATED_END_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingStartOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        adSelectionExecutionLogger::endPersistAdSelection);
        assertThat(throwable.getMessage()).contains(MISSING_START_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingEndOfPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Set start state of the subcomponent persist-ad-selection process.
        adSelectionExecutionLogger.startPersistAdSelection(mMockDBAdSelection);
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.close(STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_END_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_missingPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        when(mMockDBAdSelection.getBiddingLogicUri()).thenReturn(DECISION_LOGIC_URI);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        // Close the Ad selection execution logger and log the data into the AdServicesLogger.
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> adSelectionExecutionLogger.close(STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_PERSIST_AD_SELECTION);
    }

    @Test
    public void testAdSelectionExecutionLogger_RunAdSelectionFailedBeforePersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testAdSelectionExecutionLogger_RunAdSelectionFailedDuringPersistAdSelection() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        adSelectionExecutionLogger.startPersistAdSelection(mMockDBAdSelection);

        int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        adSelectionExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - PERSIST_AD_SELECTION_START_TIMESTAMP));
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testRunAdSelectionLatencyCalculator_getRunAdSelectionOverallLatency() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Start the Ad selection execution logger and set start state of the process.
        AdSelectionExecutionLogger adSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata, mMockClock, mContextMock, mAdServicesLoggerMock);
        assertThat(adSelectionExecutionLogger.getRunAdSelectionOverallLatencyInMs())
                .isEqualTo(RUN_AD_SELECTION_OVERALL_LATENCY_MS);
    }
}
