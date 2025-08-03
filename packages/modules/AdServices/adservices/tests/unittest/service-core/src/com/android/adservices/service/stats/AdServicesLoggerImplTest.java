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

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionStatus.INSERT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionType.WRITE_TRANSACTION_TYPE;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.MethodName.INSERT_KEY;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus.IO_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.NUM_OF_ELIGIBLE_TO_UPDATE_CAS;
import static com.android.adservices.service.stats.BackgroundFetchProcessReportedStatsTest.RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.BUYER_DECISION_LOGIC_SCRIPT_TYPE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GENERATE_BIDS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_BUYER_DECISION_LOGIC_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.NUM_OF_ADS_FOR_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_AD_BIDDING_PER_CA_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_BIDDING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStatsTest.RUN_BIDDING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_BUYERS_FETCHED;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_BUYERS_REQUESTED;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_ADS_ENTERING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_CAS_ENTERING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.NUM_OF_CAS_POSTING_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RATIO_OF_CAS_SELECTING_RMKT_ADS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RUN_AD_BIDDING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.RUN_AD_BIDDING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdBiddingProcessReportedStatsTest.TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SCORES_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SCORES_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_AD_SELECTION_LOGIC_SCRIPT_TYPE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_CAS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.NUM_OF_REMARKETING_ADS_ENTERING_SCORING;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.RUN_AD_SCORING_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.RUN_AD_SCORING_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdScoringProcessReportedStatsTest.SCORE_ADS_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.DB_AD_SELECTION_SIZE_IN_BYTES;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.IS_RMKT_ADS_WON;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.PERSIST_AD_SELECTION_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.PERSIST_AD_SELECTION_RESULT_CODE;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.RUN_AD_SELECTION_LATENCY_IN_MILLIS;
import static com.android.adservices.service.stats.RunAdSelectionProcessReportedStatsTest.RUN_AD_SELECTION_RESULT_CODE;
import static com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStatsTest.DATA_SIZE_OF_ADS_IN_BYTES;
import static com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStatsTest.NUM_OF_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.adservices.adselection.ReportEventRequest;

import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.measurement.attribution.AttributionStatus;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link AdServicesLoggerImpl}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(AppNameApiErrorLogger.class)
public final class AdServicesLoggerImplTest extends AdServicesExtendedMockitoTestCase {

    @Mock private StatsdAdServicesLogger mStatsdLoggerMock;
    @Mock private Flags mMockFlags;
    @Mock private AppNameApiErrorLogger mMockAppNameApiErrorLogger;

    @Test
    public void testLogFledgeApiCallStats() {
        int latencyMs = 10;
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logFledgeApiCallStats(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, STATUS_SUCCESS, latencyMs);
        verify(mStatsdLoggerMock)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, STATUS_SUCCESS, latencyMs);
    }

    @Test
    public void testLogFledgeApiCallStatsWithAppPackageNameLogging() {
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        String appPackageName = TEST_PACKAGE_NAME;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;

        adServicesLogger.logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        // Verify method logging app package name is called.
        verify(mStatsdLoggerMock)
                .logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);
    }

    @Test
    public void testLogRunAdSelectionProcessReportedStats() {
        RunAdSelectionProcessReportedStats stats =
                RunAdSelectionProcessReportedStats.builder()
                        .setIsRemarketingAdsWon(IS_RMKT_ADS_WON)
                        .setDBAdSelectionSizeInBytes(DB_AD_SELECTION_SIZE_IN_BYTES)
                        .setPersistAdSelectionLatencyInMillis(
                                PERSIST_AD_SELECTION_LATENCY_IN_MILLIS)
                        .setPersistAdSelectionResultCode(PERSIST_AD_SELECTION_RESULT_CODE)
                        .setRunAdSelectionLatencyInMillis(RUN_AD_SELECTION_LATENCY_IN_MILLIS)
                        .setRunAdSelectionResultCode(RUN_AD_SELECTION_RESULT_CODE)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logRunAdSelectionProcessReportedStats(stats);
        ArgumentCaptor<RunAdSelectionProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdSelectionProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdSelectionProcessReportedStats(argumentCaptor.capture());
        RunAdSelectionProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getIsRemarketingAdsWon()).isEqualTo(IS_RMKT_ADS_WON);
        expect.that(loggedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(DB_AD_SELECTION_SIZE_IN_BYTES);
        expect.that(loggedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdSelectionResultCode())
                .isEqualTo(PERSIST_AD_SELECTION_RESULT_CODE);
        expect.that(loggedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdSelectionResultCode())
                .isEqualTo(RUN_AD_SELECTION_RESULT_CODE);
    }

    @Test
    public void testLogRunAdScoringProcessReportedStats() {
        RunAdScoringProcessReportedStats stats =
                RunAdScoringProcessReportedStats.builder()
                        .setGetAdSelectionLogicLatencyInMillis(
                                GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS)
                        .setGetAdSelectionLogicResultCode(GET_AD_SELECTION_LOGIC_RESULT_CODE)
                        .setGetAdSelectionLogicScriptType(GET_AD_SELECTION_LOGIC_SCRIPT_TYPE)
                        .setFetchedAdSelectionLogicScriptSizeInBytes(
                                FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES)
                        .setGetTrustedScoringSignalsLatencyInMillis(
                                GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS)
                        .setGetTrustedScoringSignalsResultCode(
                                GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE)
                        .setFetchedTrustedScoringSignalsDataSizeInBytes(
                                FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES)
                        .setScoreAdsLatencyInMillis(SCORE_ADS_LATENCY_IN_MILLIS)
                        .setGetAdScoresLatencyInMillis(GET_AD_SCORES_LATENCY_IN_MILLIS)
                        .setGetAdScoresResultCode(GET_AD_SCORES_RESULT_CODE)
                        .setNumOfCasEnteringScoring(NUM_OF_CAS_ENTERING_SCORING)
                        .setNumOfRemarketingAdsEnteringScoring(
                                NUM_OF_REMARKETING_ADS_ENTERING_SCORING)
                        .setNumOfContextualAdsEnteringScoring(
                                NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING)
                        .setRunAdScoringLatencyInMillis(RUN_AD_SCORING_LATENCY_IN_MILLIS)
                        .setRunAdScoringResultCode(RUN_AD_SCORING_RESULT_CODE)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logRunAdScoringProcessReportedStats(stats);
        ArgumentCaptor<RunAdScoringProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdScoringProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdScoringProcessReportedStats(argumentCaptor.capture());
        RunAdScoringProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(GET_AD_SELECTION_LOGIC_RESULT_CODE);
        expect.that(loggedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(GET_AD_SELECTION_LOGIC_SCRIPT_TYPE);
        expect.that(loggedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        expect.that(loggedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE);
        expect.that(loggedStats.getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES);
        expect.that(loggedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(SCORE_ADS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(GET_AD_SCORES_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetAdScoresResultCode()).isEqualTo(GET_AD_SCORES_RESULT_CODE);
        expect.that(loggedStats.getNumOfCasEnteringScoring())
                .isEqualTo(NUM_OF_CAS_ENTERING_SCORING);
        expect.that(loggedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(NUM_OF_REMARKETING_ADS_ENTERING_SCORING);
        expect.that(loggedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING);
        expect.that(loggedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdScoringResultCode()).isEqualTo(RUN_AD_SCORING_RESULT_CODE);
    }

    @Test
    public void testLogRunAdBiddingProcessReportedStats() {
        RunAdBiddingProcessReportedStats stats =
                RunAdBiddingProcessReportedStats.builder()
                        .setGetBuyersCustomAudienceLatencyInMills(
                                GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS)
                        .setGetBuyersCustomAudienceResultCode(
                                GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE)
                        .setNumBuyersRequested(NUM_BUYERS_REQUESTED)
                        .setNumBuyersFetched(NUM_BUYERS_FETCHED)
                        .setNumOfAdsEnteringBidding(NUM_OF_ADS_ENTERING_BIDDING)
                        .setNumOfCasEnteringBidding(NUM_OF_CAS_ENTERING_BIDDING)
                        .setNumOfCasPostBidding(NUM_OF_CAS_POSTING_BIDDING)
                        .setRatioOfCasSelectingRmktAds(RATIO_OF_CAS_SELECTING_RMKT_ADS)
                        .setRunAdBiddingLatencyInMillis(RUN_AD_BIDDING_LATENCY_IN_MILLIS)
                        .setRunAdBiddingResultCode(RUN_AD_BIDDING_RESULT_CODE)
                        .setTotalAdBiddingStageLatencyInMillis(
                                TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logRunAdBiddingProcessReportedStats(stats);
        ArgumentCaptor<RunAdBiddingProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdBiddingProcessReportedStats.class);
        verify(mStatsdLoggerMock).logRunAdBiddingProcessReportedStats(argumentCaptor.capture());
        RunAdBiddingProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_RESULT_CODE);
        expect.that(loggedStats.getNumBuyersRequested()).isEqualTo(NUM_BUYERS_REQUESTED);
        expect.that(loggedStats.getNumBuyersFetched()).isEqualTo(NUM_BUYERS_FETCHED);
        expect.that(loggedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(NUM_OF_ADS_ENTERING_BIDDING);
        expect.that(loggedStats.getNumOfCasEnteringBidding())
                .isEqualTo(NUM_OF_CAS_ENTERING_BIDDING);
        expect.that(loggedStats.getNumOfCasPostBidding()).isEqualTo(NUM_OF_CAS_POSTING_BIDDING);
        expect.that(loggedStats.getRatioOfCasSelectingRmktAds())
                .isWithin(0.0f)
                .of(RATIO_OF_CAS_SELECTING_RMKT_ADS);
        expect.that(loggedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdBiddingResultCode()).isEqualTo(RUN_AD_BIDDING_RESULT_CODE);
        expect.that(loggedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_AD_BIDDING_STAGE_LATENCY_IN_MILLIS);
    }

    @Test
    public void testLogRunAdBiddingPerCAProcessReportedStats() {
        RunAdBiddingPerCAProcessReportedStats stats =
                RunAdBiddingPerCAProcessReportedStats.builder()
                        .setNumOfAdsForBidding(NUM_OF_ADS_FOR_BIDDING)
                        .setRunAdBiddingPerCaLatencyInMillis(
                                RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS)
                        .setRunAdBiddingPerCaResultCode(RUN_AD_BIDDING_PER_CA_RESULT_CODE)
                        .setGetBuyerDecisionLogicLatencyInMillis(
                                GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS)
                        .setGetBuyerDecisionLogicResultCode(GET_BUYER_DECISION_LOGIC_RESULT_CODE)
                        .setBuyerDecisionLogicScriptType(BUYER_DECISION_LOGIC_SCRIPT_TYPE)
                        .setFetchedBuyerDecisionLogicScriptSizeInBytes(
                                FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES)
                        .setNumOfKeysOfTrustedBiddingSignals(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS)
                        .setFetchedTrustedBiddingSignalsDataSizeInBytes(
                                FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES)
                        .setGetTrustedBiddingSignalsLatencyInMillis(
                                GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS)
                        .setGetTrustedBiddingSignalsResultCode(
                                GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE)
                        .setGenerateBidsLatencyInMillis(GENERATE_BIDS_LATENCY_IN_MILLIS)
                        .setRunBiddingLatencyInMillis(RUN_BIDDING_LATENCY_IN_MILLIS)
                        .setRunBiddingResultCode(RUN_BIDDING_RESULT_CODE)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logRunAdBiddingPerCAProcessReportedStats(stats);
        ArgumentCaptor<RunAdBiddingPerCAProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(RunAdBiddingPerCAProcessReportedStats.class);
        verify(mStatsdLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(argumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getNumOfAdsForBidding()).isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        expect.that(loggedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_RESULT_CODE);
        expect.that(loggedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_RESULT_CODE);
        expect.that(loggedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(BUYER_DECISION_LOGIC_SCRIPT_TYPE);
        expect.that(loggedStats.getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        expect.that(loggedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        expect.that(loggedStats.getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES);
        expect.that(loggedStats.getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE);
        expect.that(loggedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(GENERATE_BIDS_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(RUN_BIDDING_LATENCY_IN_MILLIS);
        expect.that(loggedStats.getRunBiddingResultCode()).isEqualTo(RUN_BIDDING_RESULT_CODE);
    }

    @Test
    public void testLogBackgroundFetchProcessReportedStats() {
        BackgroundFetchProcessReportedStats stats =
                BackgroundFetchProcessReportedStats.builder()
                        .setLatencyInMillis(LATENCY_IN_MILLIS)
                        .setNumOfEligibleToUpdateCas(NUM_OF_ELIGIBLE_TO_UPDATE_CAS)
                        .setResultCode(RESULT_CODE)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logBackgroundFetchProcessReportedStats(stats);
        ArgumentCaptor<BackgroundFetchProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(BackgroundFetchProcessReportedStats.class);
        verify(mStatsdLoggerMock).logBackgroundFetchProcessReportedStats(argumentCaptor.capture());
        BackgroundFetchProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getLatencyInMillis()).isEqualTo(LATENCY_IN_MILLIS);
        expect.that(loggedStats.getNumOfEligibleToUpdateCas())
                .isEqualTo(NUM_OF_ELIGIBLE_TO_UPDATE_CAS);
        expect.that(loggedStats.getResultCode()).isEqualTo(RESULT_CODE);
    }

    @Test
    public void testLogUpdateCustomAudienceProcessReportedStats() {
        UpdateCustomAudienceProcessReportedStats stats =
                UpdateCustomAudienceProcessReportedStats.builder()
                        .setLatencyInMills(LATENCY_IN_MILLIS)
                        .setResultCode(RESULT_CODE)
                        .setDataSizeOfAdsInBytes(DATA_SIZE_OF_ADS_IN_BYTES)
                        .setNumOfAds(NUM_OF_ADS)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logUpdateCustomAudienceProcessReportedStats(stats);
        ArgumentCaptor<UpdateCustomAudienceProcessReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(UpdateCustomAudienceProcessReportedStats.class);
        verify(mStatsdLoggerMock)
                .logUpdateCustomAudienceProcessReportedStats(argumentCaptor.capture());
        UpdateCustomAudienceProcessReportedStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getLatencyInMills()).isEqualTo(LATENCY_IN_MILLIS);
        expect.that(loggedStats.getResultCode()).isEqualTo(RESULT_CODE);
        expect.that(loggedStats.getDataSizeOfAdsInBytes()).isEqualTo(DATA_SIZE_OF_ADS_IN_BYTES);
        expect.that(loggedStats.getNumOfAds()).isEqualTo(NUM_OF_ADS);
    }

    @Test
    public void testLogMeasurementReportReports() {
        MeasurementReportsStats stats =
                new MeasurementReportsStats.Builder()
                        .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                        .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT)
                        .setResultCode(STATUS_SUCCESS)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementReports(stats);
        ArgumentCaptor<MeasurementReportsStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mStatsdLoggerMock).logMeasurementReports(argumentCaptor.capture());
        MeasurementReportsStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED);
        expect.that(loggedStats.getType())
                .isEqualTo(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT);
        expect.that(loggedStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
    }

    @Test
    public void testLogApiCallStats() {
        String packageName = "com.android.test";
        String sdkName = "com.android.container";
        int latency = 100;

        extendedMockito.mockGetFlags(mMockFlags);
        mockAppNameApiErrorLogger();

        ApiCallStats stats =
                new ApiCallStats.Builder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS)
                        .setAppPackageName(packageName)
                        .setSdkPackageName(sdkName)
                        .setLatencyMillisecond(latency)
                        .setResult(
                                ApiCallStats.failureResult(
                                        STATUS_SUCCESS,
                                        FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND))
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logApiCallStats(stats);
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mStatsdLoggerMock).logApiCallStats(argumentCaptor.capture());
        ApiCallStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_API_CALLED);
        expect.that(loggedStats.getApiClass())
                .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__TARGETING);
        expect.that(loggedStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
        expect.that(loggedStats.getAppPackageName()).isEqualTo(packageName);
        expect.that(loggedStats.getSdkPackageName()).isEqualTo(sdkName);
        expect.that(loggedStats.getLatencyMillisecond()).isEqualTo(latency);
        expect.that(loggedStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        expect.that(loggedStats.getFailureReason())
                .isEqualTo(FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND);

        verify(() -> AppNameApiErrorLogger.getInstance(any(), any()));
        verify(mMockAppNameApiErrorLogger)
                .logErrorOccurrence(
                        packageName, AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS, STATUS_SUCCESS);
    }

    @Test
    public void testLogUIStats() {
        UIStats stats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logUIStats(stats);
        ArgumentCaptor<UIStats> argumentCaptor = ArgumentCaptor.forClass(UIStats.class);
        verify(mStatsdLoggerMock).logUIStats(argumentCaptor.capture());
        UIStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED);
        expect.that(loggedStats.getRegion())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW);
        expect.that(loggedStats.getAction())
                .isEqualTo(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED);
    }

    @Test
    public void testLogMsmtDebugKeyMatchStats() {
        String sourceRegistrant = "android-app://com.registrant";
        String enrollmentId = "EnrollmentId";
        long hashedValue = 5000L;
        long hashLimit = 10000L;
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setDebugJoinKeyHashedValue(hashedValue)
                        .setDebugJoinKeyHashLimit(hashLimit)
                        .setSourceRegistrant(sourceRegistrant)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementDebugKeysMatch(stats);
        ArgumentCaptor<MsmtDebugKeysMatchStats> argumentCaptor =
                ArgumentCaptor.forClass(MsmtDebugKeysMatchStats.class);
        verify(mStatsdLoggerMock).logMeasurementDebugKeysMatch(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogMsmtAdIdMatchForDebugKeysStats() {
        String sourceRegistrant = "android-app://com.registrant";
        String enrollmentId = "enrollmentId";
        long uniqueAdIds = 2L;
        long uniqueAdIdLimit = 5L;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setNumUniqueAdIds(uniqueAdIds)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(sourceRegistrant)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);
        ArgumentCaptor<MsmtAdIdMatchForDebugKeysStats> argumentCaptor =
                ArgumentCaptor.forClass(MsmtAdIdMatchForDebugKeysStats.class);
        verify(mStatsdLoggerMock)
                .logMeasurementAdIdMatchForDebugKeysStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogMeasurementAttributionStats() {
        MeasurementAttributionStats stats =
                new MeasurementAttributionStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
                        .setSourceType(AttributionStatus.SourceType.VIEW.getValue())
                        .setSurfaceType(AttributionStatus.AttributionSurface.APP_WEB.getValue())
                        .setResult(AttributionStatus.AttributionResult.SUCCESS.getValue())
                        .setFailureType(AttributionStatus.FailureType.UNKNOWN.getValue())
                        .setSourceDerived(false)
                        .setInstallAttribution(true)
                        .setAttributionDelay(100L)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementAttributionStats(stats);
        ArgumentCaptor<MeasurementAttributionStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementAttributionStats.class);
        verify(mStatsdLoggerMock).logMeasurementAttributionStats(argumentCaptor.capture());
        MeasurementAttributionStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MEASUREMENT_ATTRIBUTION);
        expect.that(loggedStats.getSourceType())
                .isEqualTo(AttributionStatus.SourceType.VIEW.getValue());
        expect.that(loggedStats.getSurfaceType())
                .isEqualTo(AttributionStatus.AttributionSurface.APP_WEB.getValue());
        expect.that(loggedStats.getResult())
                .isEqualTo(AttributionStatus.AttributionResult.SUCCESS.getValue());
        expect.that(loggedStats.getFailureType())
                .isEqualTo(AttributionStatus.FailureType.UNKNOWN.getValue());
        expect.that(loggedStats.isSourceDerived()).isFalse();
        expect.that(loggedStats.isInstallAttribution()).isTrue();
        expect.that(loggedStats.getAttributionDelay()).isEqualTo(100L);
    }

    @Test
    public void testLogMeasurementWipeoutStats() {
        MeasurementWipeoutStats stats =
                new MeasurementWipeoutStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                        .setWipeoutType(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal())
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementWipeoutStats(stats);
        ArgumentCaptor<MeasurementWipeoutStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementWipeoutStats.class);
        verify(mStatsdLoggerMock).logMeasurementWipeoutStats(argumentCaptor.capture());
        MeasurementWipeoutStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode()).isEqualTo(AD_SERVICES_MEASUREMENT_WIPEOUT);
        expect.that(loggedStats.getWipeoutType())
                .isEqualTo(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal());
    }

    @Test
    public void testLogMeasurementDelayedSourceRegistrationStats() {
        int UnknownEnumValue = 0;
        long registrationDelay = 500L;
        MeasurementDelayedSourceRegistrationStats stats =
                new MeasurementDelayedSourceRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
                        .setRegistrationStatus(UnknownEnumValue)
                        .setRegistrationDelay(registrationDelay)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementDelayedSourceRegistrationStats(stats);
        ArgumentCaptor<MeasurementDelayedSourceRegistrationStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementDelayedSourceRegistrationStats.class);
        verify(mStatsdLoggerMock)
                .logMeasurementDelayedSourceRegistrationStats(argumentCaptor.capture());
        MeasurementDelayedSourceRegistrationStats loggedStats = argumentCaptor.getValue();
        expect.that(loggedStats.getCode())
                .isEqualTo(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION);
        expect.that(loggedStats.getRegistrationStatus()).isEqualTo(UnknownEnumValue);
        expect.that(loggedStats.getRegistrationDelay()).isEqualTo(registrationDelay);
    }

    @Test
    public void testLogEnrollmentDataStats() {
        int transactionTypeEnumValue =
                EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.ordinal();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEnrollmentDataStats(transactionTypeEnumValue, true, 100);
        verify(mStatsdLoggerMock).logEnrollmentDataStats(transactionTypeEnumValue, true, 100);
    }

    @Test
    public void testLogEnrollmentMatchStats() {
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEnrollmentMatchStats(true, 100);
        verify(mStatsdLoggerMock).logEnrollmentMatchStats(true, 100);
    }

    @Test
    public void testLogEnrollmentFileDownloadStats() {
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEnrollmentFileDownloadStats(true, 100);
        verify(mStatsdLoggerMock).logEnrollmentFileDownloadStats(true, 100);
    }

    @Test
    public void testLogEnrollmentFailedStats() {
        int dataFileGroupStatusEnumValue =
                EnrollmentStatus.DataFileGroupStatus.PENDING_CUSTOM_VALIDATION.ordinal();
        int errorCauseEnumValue =
                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.ordinal();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEnrollmentFailedStats(
                100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);
        verify(mStatsdLoggerMock)
                .logEnrollmentFailedStats(
                        100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);
    }

    @Test
    public void testLogMsmtClickVerificationStats() {
        int sourceType = Source.SourceType.NAVIGATION.getIntValue();
        boolean inputEventPresent = true;
        boolean systemClickVerificationSuccessful = true;
        boolean systemClickVerificationEnabled = true;
        long inputEventDelayMs = 200L;
        long validDelayWindowMs = 1000L;
        String sourceRegistrant = "test_source_registrant";
        boolean clickDeduplicationEnabled = true;
        boolean clickDeduplicationEnforced = true;
        long maxSourcesPerClick = 1;
        boolean clickUnderLimit = true;

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(sourceType)
                        .setInputEventPresent(inputEventPresent)
                        .setSystemClickVerificationSuccessful(systemClickVerificationSuccessful)
                        .setSystemClickVerificationEnabled(systemClickVerificationEnabled)
                        .setInputEventDelayMillis(inputEventDelayMs)
                        .setValidDelayWindowMillis(validDelayWindowMs)
                        .setSourceRegistrant(sourceRegistrant)
                        .setClickDeduplicationEnabled(clickDeduplicationEnabled)
                        .setClickDeduplicationEnforced(clickDeduplicationEnforced)
                        .setMaxSourcesPerClick(maxSourcesPerClick)
                        .setCurrentRegistrationUnderClickDeduplicationLimit(clickUnderLimit)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logMeasurementClickVerificationStats(stats);
        ArgumentCaptor<MeasurementClickVerificationStats> argumentCaptor =
                ArgumentCaptor.forClass(MeasurementClickVerificationStats.class);
        verify(mStatsdLoggerMock).logMeasurementClickVerificationStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogEncryptionKeyFetchedStats() {
        String enrollmentId = "enrollmentId";
        String encryptionKeyUrl = "https://www.adtech1.com/.well-known/encryption-keys";

        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(IO_EXCEPTION)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(enrollmentId)
                        .setEncryptionKeyUrl(encryptionKeyUrl)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEncryptionKeyFetchedStats(stats);

        ArgumentCaptor<AdServicesEncryptionKeyFetchedStats> argumentCaptor =
                ArgumentCaptor.forClass(AdServicesEncryptionKeyFetchedStats.class);
        verify(mStatsdLoggerMock).logEncryptionKeyFetchedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogEncryptionKeyDbTransactionEndedStats() {
        AdServicesEncryptionKeyDbTransactionEndedStats stats =
                AdServicesEncryptionKeyDbTransactionEndedStats.builder()
                        .setDbTransactionType(WRITE_TRANSACTION_TYPE)
                        .setDbTransactionStatus(INSERT_EXCEPTION)
                        .setMethodName(INSERT_KEY)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logEncryptionKeyDbTransactionEndedStats(stats);

        ArgumentCaptor<AdServicesEncryptionKeyDbTransactionEndedStats> argumentCaptor =
                ArgumentCaptor.forClass(AdServicesEncryptionKeyDbTransactionEndedStats.class);
        verify(mStatsdLoggerMock).logEncryptionKeyDbTransactionEndedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogDestinationRegisteredBeaconsReportedStats() {
        List<DestinationRegisteredBeaconsReportedStats.InteractionKeySizeRangeType>
                keySizeRangeTypeList =
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.LARGER_THAN_MAXIMUM_KEY_SIZE,
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.SMALLER_THAN_MAXIMUM_KEY_SIZE,
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.EQUAL_TO_MAXIMUM_KEY_SIZE);

        DestinationRegisteredBeaconsReportedStats stats =
                DestinationRegisteredBeaconsReportedStats.builder()
                        .setBeaconReportingDestinationType(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .setAttemptedRegisteredBeacons(5)
                        .setAttemptedKeySizesRangeType(keySizeRangeTypeList)
                        .setTableNumRows(25)
                        .setAdServicesStatusCode(0)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logDestinationRegisteredBeaconsReportedStats(stats);

        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);
        verify(mStatsdLoggerMock)
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogReportInteractionApiCalledStats() {
        ReportInteractionApiCalledStats stats =
                ReportInteractionApiCalledStats.builder()
                        .setBeaconReportingDestinationType(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .setNumMatchingUris(5)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logReportInteractionApiCalledStats(stats);

        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);
        verify(mStatsdLoggerMock).logReportInteractionApiCalledStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogInteractionReportingTableClearedStats() {
        InteractionReportingTableClearedStats stats =
                InteractionReportingTableClearedStats.builder()
                        .setNumUrisCleared(100)
                        .setNumUnreportedUris(50)
                        .build();

        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logInteractionReportingTableClearedStats(stats);

        ArgumentCaptor<InteractionReportingTableClearedStats> argumentCaptor =
                ArgumentCaptor.forClass(InteractionReportingTableClearedStats.class);
        verify(mStatsdLoggerMock)
                .logInteractionReportingTableClearedStats(argumentCaptor.capture());
        expect.that(stats).isEqualTo(argumentCaptor.getValue());
    }

    @Test
    public void testLogAppManifestConfigCall() {
        String pkgName = "pkg.I.am";
        @ApiType int apiType = AppManifestConfigCall.API_TOPICS;
        @Result int result = AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
        AppManifestConfigCall call = new AppManifestConfigCall(pkgName, apiType);
        call.result = result;
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);

        adServicesLogger.logAppManifestConfigCall(call);

        ArgumentCaptor<AppManifestConfigCall> argumentCaptor =
                ArgumentCaptor.forClass(AppManifestConfigCall.class);
        verify(mStatsdLoggerMock).logAppManifestConfigCall(argumentCaptor.capture());
        expect.that(argumentCaptor.getValue()).isEqualTo(call);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStats() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(64)
                        .setNumBuyers(3)
                        .setStatusCode(STATUS_SUCCESS)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logGetAdSelectionDataApiCalledStats(stats);

        verify(mStatsdLoggerMock).logGetAdSelectionDataApiCalledStats(eq(stats));
    }

    @Test
    public void testlogGetAdSelectionDataBuyerInputGeneratedStats() {
        GetAdSelectionDataBuyerInputGeneratedStats stats =
                GetAdSelectionDataBuyerInputGeneratedStats.builder()
                        .setNumCustomAudiences(2)
                        .setNumCustomAudiencesOmitAds(1)
                        .setCustomAudienceSizeMeanB(23F)
                        .setCustomAudienceSizeVarianceB(24F)
                        .setTrustedBiddingSignalsKeysSizeMeanB(25F)
                        .setTrustedBiddingSignalsKeysSizeVarianceB(26F)
                        .setUserBiddingSignalsSizeMeanB(27F)
                        .setUserBiddingSignalsSizeVarianceB(28F)
                        .build();
        AdServicesLoggerImpl adServicesLogger = new AdServicesLoggerImpl(mStatsdLoggerMock);
        adServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);

        verify(mStatsdLoggerMock).logGetAdSelectionDataBuyerInputGeneratedStats(eq(stats));
    }

    private void mockAppNameApiErrorLogger() {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getAppNameApiErrorCobaltLoggingEnabled()).thenReturn(true);
        doReturn(mMockAppNameApiErrorLogger)
                .when(() -> AppNameApiErrorLogger.getInstance(any(), any()));
    }
}
