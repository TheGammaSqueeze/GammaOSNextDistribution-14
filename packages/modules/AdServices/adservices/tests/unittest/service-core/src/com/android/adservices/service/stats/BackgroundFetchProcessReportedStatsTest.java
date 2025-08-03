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

import org.junit.Test;

/** Unit tests for {@link BackgroundFetchProcessReportedStats}. */
public class BackgroundFetchProcessReportedStatsTest {
    static final int LATENCY_IN_MILLIS = 10;
    static final int NUM_OF_ELIGIBLE_TO_UPDATE_CAS = 5;
    static final int RESULT_CODE = AdServicesStatusUtils.STATUS_SUCCESS;

    @Test
    public void testBuilderCreateSuccess() {
        BackgroundFetchProcessReportedStats stats =
                BackgroundFetchProcessReportedStats.builder()
                        .setLatencyInMillis(LATENCY_IN_MILLIS)
                        .setNumOfEligibleToUpdateCas(NUM_OF_ELIGIBLE_TO_UPDATE_CAS)
                        .setResultCode(RESULT_CODE)
                        .build();
        assertEquals(LATENCY_IN_MILLIS, stats.getLatencyInMillis());
        assertEquals(NUM_OF_ELIGIBLE_TO_UPDATE_CAS, stats.getNumOfEligibleToUpdateCas());
        assertEquals(RESULT_CODE, stats.getResultCode());
    }
}
