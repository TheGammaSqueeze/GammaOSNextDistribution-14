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

import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PER_CAPROCESS_REPORTED__BUYER_DECISION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdServicesStatusUtils;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link RunAdBiddingPerCAProcessReportedStats}. */
public class RunAdBiddingPerCAProcessReportedStatsTest {
    static final int NUM_OF_ADS_FOR_BIDDING = 10;
    static final int RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS = 5;
    static final int RUN_AD_BIDDING_PER_CA_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS = 5;
    static final int GET_BUYER_DECISION_LOGIC_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int BUYER_DECISION_LOGIC_SCRIPT_TYPE =
            RUN_AD_BIDDING_PER_CAPROCESS_REPORTED__BUYER_DECISION_LOGIC_SCRIPT_TYPE__JAVASCRIPT;
    static final int FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES = 10;
    static final int NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS = 10;
    static final int FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES = 10;
    static final int GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS = 5;
    static final int GENERATE_BIDS_LATENCY_IN_MILLIS = 10;
    static final int RUN_BIDDING_LATENCY_IN_MILLIS = 5;
    static final int RUN_BIDDING_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuilderCreateSuccess() {
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
        assertEquals(NUM_OF_ADS_FOR_BIDDING, stats.getNumOfAdsForBidding());
        assertEquals(
                RUN_AD_BIDDING_PER_CA_LATENCY_IN_MILLIS,
                stats.getRunAdBiddingPerCaLatencyInMillis());
        assertEquals(RUN_AD_BIDDING_PER_CA_RESULT_CODE, stats.getRunAdBiddingPerCaResultCode());
        assertEquals(
                GET_BUYER_DECISION_LOGIC_LATENCY_IN_MILLIS,
                stats.getGetBuyerDecisionLogicLatencyInMillis());
        assertEquals(
                GET_BUYER_DECISION_LOGIC_RESULT_CODE, stats.getGetBuyerDecisionLogicResultCode());
        assertEquals(BUYER_DECISION_LOGIC_SCRIPT_TYPE, stats.getBuyerDecisionLogicScriptType());
        assertEquals(
                FETCHED_BUYER_DECISION_LOGIC_SCRIPT_SIZE_IN_BYTES,
                stats.getFetchedBuyerDecisionLogicScriptSizeInBytes());
        assertEquals(
                NUM_OF_KEYS_OF_TRUSTED_BIDDING_SIGNALS,
                stats.getNumOfKeysOfTrustedBiddingSignals());
        assertEquals(
                FETCHED_TRUSTED_BIDDING_SIGNALS_DATA_SIZE_IN_BYTES,
                stats.getFetchedTrustedBiddingSignalsDataSizeInBytes());
        assertEquals(
                GET_TRUSTED_BIDDING_SIGNALS_LATENCY_IN_MILLIS,
                stats.getGetTrustedBiddingSignalsLatencyInMillis());
        assertEquals(
                GET_TRUSTED_BIDDING_SIGNALS_RESULT_CODE,
                stats.getGetTrustedBiddingSignalsResultCode());
        assertEquals(GENERATE_BIDS_LATENCY_IN_MILLIS, stats.getGenerateBidsLatencyInMillis());
        assertEquals(RUN_BIDDING_LATENCY_IN_MILLIS, stats.getRunBiddingLatencyInMillis());
        assertEquals(RUN_BIDDING_RESULT_CODE, stats.getRunBiddingResultCode());
    }
}
