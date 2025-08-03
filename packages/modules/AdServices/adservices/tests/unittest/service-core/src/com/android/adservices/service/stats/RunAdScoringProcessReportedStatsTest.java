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

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdServicesStatusUtils;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link RunAdScoringProcessReportedStats}. */
public class RunAdScoringProcessReportedStatsTest {
    static final int GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS = 10;
    static final int FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES = 20;
    static final int GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS = 10;
    static final int FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES = 10;
    static final int SCORE_ADS_LATENCY_IN_MILLIS = 5;
    static final int GET_AD_SCORES_LATENCY_IN_MILLIS = 5;
    static final int GET_AD_SELECTION_LOGIC_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int GET_AD_SCORES_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int NUM_OF_CAS_ENTERING_SCORING = 5;
    static final int NUM_OF_REMARKETING_ADS_ENTERING_SCORING = 6;
    static final int NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING = 0;
    static final int RUN_AD_SCORING_LATENCY_IN_MILLIS = 10;
    static final int RUN_AD_SCORING_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int GET_AD_SELECTION_LOGIC_SCRIPT_TYPE = AdServicesStatusUtils.STATUS_SUCCESS;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuilderCreateSuccess() {
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
        assertEquals(
                GET_AD_SELECTION_LOGIC_LATENCY_IN_MILLIS,
                stats.getGetAdSelectionLogicLatencyInMillis());
        assertEquals(GET_AD_SELECTION_LOGIC_RESULT_CODE, stats.getGetAdSelectionLogicResultCode());
        assertEquals(GET_AD_SELECTION_LOGIC_SCRIPT_TYPE, stats.getGetAdSelectionLogicScriptType());
        assertEquals(
                FETCHED_AD_SELECTION_LOGIC_SCRIPT_SIZE_IN_BYTES,
                stats.getFetchedAdSelectionLogicScriptSizeInBytes());
        assertEquals(
                GET_TRUSTED_SCORING_SIGNALS_LATENCY_IN_MILLIS,
                stats.getGetTrustedScoringSignalsLatencyInMillis());
        assertEquals(
                GET_TRUSTED_SCORING_SIGNALS_RESULT_CODE,
                stats.getGetTrustedScoringSignalsResultCode());
        assertEquals(
                FETCHED_TRUSTED_SCORING_SIGNALS_DATA_SIZE_IN_BYTES,
                stats.getFetchedTrustedScoringSignalsDataSizeInBytes());
        assertEquals(SCORE_ADS_LATENCY_IN_MILLIS, stats.getScoreAdsLatencyInMillis());
        assertEquals(GET_AD_SCORES_LATENCY_IN_MILLIS, stats.getGetAdScoresLatencyInMillis());
        assertEquals(GET_AD_SCORES_RESULT_CODE, stats.getGetAdScoresResultCode());
        assertEquals(NUM_OF_CAS_ENTERING_SCORING, stats.getNumOfCasEnteringScoring());
        assertEquals(
                NUM_OF_REMARKETING_ADS_ENTERING_SCORING,
                stats.getNumOfRemarketingAdsEnteringScoring());
        assertEquals(
                NUM_OF_CONTEXTUAL_ADS_ENTERING_SCORING,
                stats.getNumOfContextualAdsEnteringScoring());
        assertEquals(RUN_AD_SCORING_LATENCY_IN_MILLIS, stats.getRunAdScoringLatencyInMillis());
        assertEquals(RUN_AD_SCORING_RESULT_CODE, stats.getRunAdScoringResultCode());
    }
}
