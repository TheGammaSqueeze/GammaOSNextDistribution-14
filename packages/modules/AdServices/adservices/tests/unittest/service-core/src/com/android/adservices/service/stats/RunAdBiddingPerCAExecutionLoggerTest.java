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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.BUYER_DECISION_LOGIC_JS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_JAVASCRIPT;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_END_GENERATE_BIDS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_END_GET_BUYER_DECISION_LOGIC;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_END_GET_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_END_RUN_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_START_GENERATE_BIDS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_START_GET_BUYER_DECISION_LOGIC;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_START_GET_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_START_RUN_AD_BIDDING_PER_CA;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.MISSING_START_RUN_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_END_GENERATE_BIDS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_END_GET_BUYER_DECISION_LOGIC;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_END_GET_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_END_RUN_AD_BIDDING_PER_CA;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_END_RUN_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_START_GENERATE_BIDS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_START_GET_BUYER_DECISION_LOGIC;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_START_RUN_AD_BIDDING_PER_CA;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_START_RUN_BIDDING;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.REPEATED_START_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.SCRIPT_UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdSelectionSignals;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RunAdBiddingPerCAExecutionLoggerTest {

    public static final long RUN_AD_BIDDING_PER_CA_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1;
    public static final long GET_BUYER_DECISION_LOGIC_START_TIMESTAMP =
            RUN_AD_BIDDING_PER_CA_START_TIMESTAMP + 1;
    public static final int GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS = 1;
    public static final long GET_BUYER_DECISION_LOGIC_END_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_START_TIMESTAMP + GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS;
    public static final long RUN_BIDDING_START_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_END_TIMESTAMP + 1;
    public static final long GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP =
            RUN_BIDDING_START_TIMESTAMP + 1;
    public static final int GET_TRUSTED_BIDDING_SIGNALS_IN_MS = 1;
    public static final long GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP =
            GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP + GET_TRUSTED_BIDDING_SIGNALS_IN_MS;
    public static final long GENERATE_BIDS_START_TIMESTAMP =
            GET_BUYER_DECISION_LOGIC_END_TIMESTAMP + 1;
    public static final int GENERATE_BIDS_LATENCY_IN_MS = 1;
    public static final long GENERATE_BIDS_END_TIMESTAMP =
            GENERATE_BIDS_START_TIMESTAMP + GENERATE_BIDS_LATENCY_IN_MS;
    public static final long RUN_BIDDING_END_TIMESTAMP = GENERATE_BIDS_END_TIMESTAMP + 1;
    public static final int RUN_BIDDING_LATENCY_IN_MS =
            (int) (RUN_BIDDING_END_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP);
    public static final int RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - RUN_AD_BIDDING_PER_CA_START_TIMESTAMP);
    public static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final int NUM_OF_ADS_FOR_BIDDING = 4;
    private static final int NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS = 3;
    private static final int FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES =
            BUYER_DECISION_LOGIC_JS.getBytes().length;
    private static final int FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES =
            TRUSTED_BIDDING_SIGNALS.getSizeInBytes();
    @Captor
    ArgumentCaptor<RunAdBiddingPerCAProcessReportedStats>
            mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor;

    @Mock private Clock mMockClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_SuccessRunAdBiddingPerCA() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        runAdBiddingPerCAExecutionLogger.endGenerateBids();
        runAdBiddingPerCAExecutionLogger.endRunBidding();
        runAdBiddingPerCAExecutionLogger.close(STATUS_SUCCESS);

        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(GENERATE_BIDS_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(RUN_BIDDING_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_FailedBeforeGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);

        int resultCode = STATUS_INTERNAL_ERROR;
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_FailedByGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        int resultCode = STATUS_INTERNAL_ERROR;
        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        runAdBiddingPerCAExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - GENERATE_BIDS_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_FailedByGetTrustedBiddingSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        int resultCode = STATUS_INTERNAL_ERROR;
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(
                        (int)
                                (STOP_ELAPSED_TIMESTAMP
                                        - GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(resultCode);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_FailedByGetBuyerDecisionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        int resultCode = STATUS_INTERNAL_ERROR;
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.close(resultCode);

        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(NUM_OF_ADS_FOR_BIDDING);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(
                        (int) (STOP_ELAPSED_TIMESTAMP - GET_BUYER_DECISION_LOGIC_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_UNSET);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(FIELD_UNSET);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(FIELD_UNSET);
        assertThat(
                        runAdBiddingPerCAProcessReportedStats
                                .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(FIELD_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedStartRunAdBiddingPerCA() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_BIDDING_PER_CA_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(
                                        NUM_OF_ADS_FOR_BIDDING));
        assertThat(throwable.getMessage()).contains(REPEATED_START_RUN_AD_BIDDING_PER_CA);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingStartRunAdBiddingPerCA() {
        when(mMockClock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic());
        assertThat(throwable.getMessage()).contains(MISSING_START_RUN_AD_BIDDING_PER_CA);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedStartGetBuyerDecisionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::startGetBuyerDecisionLogic);
        assertThat(throwable.getMessage()).contains(REPEATED_START_GET_BUYER_DECISION_LOGIC);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingStartGetBuyerDecisionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, RUN_AD_BIDDING_PER_CA_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(
                                        BUYER_DECISION_LOGIC_JS));
        assertThat(throwable.getMessage()).contains(MISSING_START_GET_BUYER_DECISION_LOGIC);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedEndGetBuyerDecisionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(
                                        BUYER_DECISION_LOGIC_JS));
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_BUYER_DECISION_LOGIC);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingEndGetBuyerDecisionLogic() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::startRunBidding);
        assertThat(throwable.getMessage()).contains(MISSING_END_GET_BUYER_DECISION_LOGIC);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedStartRunBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::startRunBidding);
        assertThat(throwable.getMessage()).contains(REPEATED_START_RUN_BIDDING);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingStartRunBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                                        NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS));
        assertThat(throwable.getMessage()).contains(MISSING_START_RUN_BIDDING);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedStartGetTrustedBiddingSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                                        NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS));
        assertThat(throwable.getMessage()).contains(REPEATED_START_TRUSTED_BIDDING_SIGNALS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingStartGetTrustedBiddingSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(
                                        TRUSTED_BIDDING_SIGNALS));
        assertThat(throwable.getMessage()).contains(MISSING_START_GET_TRUSTED_BIDDING_SIGNALS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedEndGetTrustedBiddingSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(
                                        TRUSTED_BIDDING_SIGNALS));
        assertThat(throwable.getMessage()).contains(REPEATED_END_GET_TRUSTED_BIDDING_SIGNALS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingEndGetTrustedBiddingSignals() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::startGenerateBids);
        assertThat(throwable.getMessage()).contains(MISSING_END_GET_TRUSTED_BIDDING_SIGNALS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedStartGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::startGenerateBids);
        assertThat(throwable.getMessage()).contains(REPEATED_START_GENERATE_BIDS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingStartGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::endGenerateBids);
        assertThat(throwable.getMessage()).contains(MISSING_START_GENERATE_BIDS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedEndGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        runAdBiddingPerCAExecutionLogger.endGenerateBids();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::endGenerateBids);
        assertThat(throwable.getMessage()).contains(REPEATED_END_GENERATE_BIDS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingEndGenerateBids() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::endRunBidding);
        assertThat(throwable.getMessage()).contains(MISSING_END_GENERATE_BIDS);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedEndRunBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        runAdBiddingPerCAExecutionLogger.endGenerateBids();
        runAdBiddingPerCAExecutionLogger.endRunBidding();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        runAdBiddingPerCAExecutionLogger::endRunBidding);
        assertThat(throwable.getMessage()).contains(REPEATED_END_RUN_BIDDING);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_missingEndRunBidding() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.startGetBuyerDecisionLogic();
        runAdBiddingPerCAExecutionLogger.endGetBuyerDecisionLogic(BUYER_DECISION_LOGIC_JS);
        runAdBiddingPerCAExecutionLogger.startRunBidding();
        runAdBiddingPerCAExecutionLogger.startGetTrustedBiddingSignals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.endGetTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS);
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        runAdBiddingPerCAExecutionLogger.endGenerateBids();

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> runAdBiddingPerCAExecutionLogger.close(STATUS_SUCCESS));
        assertThat(throwable.getMessage()).contains(MISSING_END_RUN_BIDDING);
    }

    @Test
    public void testRunAdBiddingPerCAExecutionLogger_repeatedEndRunAdBiddingPerCA() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(mMockClock, mAdServicesLoggerMock);
        runAdBiddingPerCAExecutionLogger.startRunAdBiddingPerCA(NUM_OF_ADS_FOR_BIDDING);
        runAdBiddingPerCAExecutionLogger.close(STATUS_INTERNAL_ERROR);

        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () -> runAdBiddingPerCAExecutionLogger.close(STATUS_INTERNAL_ERROR));
        assertThat(throwable.getMessage()).contains(REPEATED_END_RUN_AD_BIDDING_PER_CA);
    }
}
