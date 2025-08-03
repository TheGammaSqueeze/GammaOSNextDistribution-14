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

/** Unit tests for {@link RunAdSelectionProcessReportedStats}. */
public class RunAdSelectionProcessReportedStatsTest {
    static final boolean IS_RMKT_ADS_WON = true;
    static final int DB_AD_SELECTION_SIZE_IN_BYTES = 100;
    static final int PERSIST_AD_SELECTION_LATENCY_IN_MILLIS = 10;
    static final int RUN_AD_SELECTION_LATENCY_IN_MILLIS = 10;
    static final int PERSIST_AD_SELECTION_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;
    static final int RUN_AD_SELECTION_RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuilderCreateSuccess() {
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
        assertEquals(IS_RMKT_ADS_WON, stats.getIsRemarketingAdsWon());
        assertEquals(DB_AD_SELECTION_SIZE_IN_BYTES, stats.getDBAdSelectionSizeInBytes());
        assertEquals(
                PERSIST_AD_SELECTION_LATENCY_IN_MILLIS,
                stats.getPersistAdSelectionLatencyInMillis());
        assertEquals(PERSIST_AD_SELECTION_RESULT_CODE, stats.getPersistAdSelectionResultCode());
        assertEquals(RUN_AD_SELECTION_LATENCY_IN_MILLIS, stats.getRunAdSelectionLatencyInMillis());
        assertEquals(RUN_AD_SELECTION_RESULT_CODE, stats.getRunAdSelectionResultCode());
    }
}
