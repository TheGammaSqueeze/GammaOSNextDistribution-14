/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class GetAdSelectionDataBuyerInputGeneratedStatsTest extends AdServicesUnitTestCase {
    private static final int NUM_CUSTOM_AUDIENCES = 4;
    private static final int NUM_CUSTOM_AUDIENCES_OMIT_ADS = 2;
    private static final float CUSTOM_AUDIENCE_SIZE_MEAN = 21.22F;
    private static final float CUSTOM_AUDIENCE_SIZE_VARIANCE = 27.4F;
    private static final float TRUSTED_BIDDING_SIGNALS_KEYS_SIZE_MEAN = 2.1F;
    private static final float TRUSTED_BIDDING_SIGNALS_SIZE_VARIANCE = 2.43F;
    private static final float USER_BIDDING_SIGNALS_SIZE_MEAN = 2.55F;
    private static final float USER_BIDDING_SIGNALS_SIZE_VARIANCE = 2.98F;

    @Test
    public void testBuildGGetAdSelectionDataBuyerInputGeneratedStats() {
        GetAdSelectionDataBuyerInputGeneratedStats stats =
                GetAdSelectionDataBuyerInputGeneratedStats.builder()
                        .setNumCustomAudiences(NUM_CUSTOM_AUDIENCES)
                        .setNumCustomAudiencesOmitAds(NUM_CUSTOM_AUDIENCES_OMIT_ADS)
                        .setCustomAudienceSizeMeanB(CUSTOM_AUDIENCE_SIZE_MEAN)
                        .setCustomAudienceSizeVarianceB(CUSTOM_AUDIENCE_SIZE_VARIANCE)
                        .setTrustedBiddingSignalsKeysSizeMeanB(
                                TRUSTED_BIDDING_SIGNALS_KEYS_SIZE_MEAN)
                        .setTrustedBiddingSignalsKeysSizeVarianceB(
                                TRUSTED_BIDDING_SIGNALS_SIZE_VARIANCE)
                        .setUserBiddingSignalsSizeMeanB(USER_BIDDING_SIGNALS_SIZE_MEAN)
                        .setUserBiddingSignalsSizeVarianceB(USER_BIDDING_SIGNALS_SIZE_VARIANCE)
                        .build();

        expect.that(stats.getNumCustomAudiences()).isEqualTo(NUM_CUSTOM_AUDIENCES);
        expect.that(stats.getNumCustomAudiencesOmitAds()).isEqualTo(NUM_CUSTOM_AUDIENCES_OMIT_ADS);
        expect.that(stats.getCustomAudienceSizeMeanB()).isEqualTo(CUSTOM_AUDIENCE_SIZE_MEAN);
        expect.that(stats.getCustomAudienceSizeVarianceB())
                .isEqualTo(CUSTOM_AUDIENCE_SIZE_VARIANCE);
        expect.that(stats.getTrustedBiddingSignalsKeysSizeMeanB())
                .isEqualTo(TRUSTED_BIDDING_SIGNALS_KEYS_SIZE_MEAN);
        expect.that(stats.getTrustedBiddingSignalsKeysSizeVarianceB())
                .isEqualTo(TRUSTED_BIDDING_SIGNALS_SIZE_VARIANCE);
        expect.that(stats.getUserBiddingSignalsSizeMeanB())
                .isEqualTo(USER_BIDDING_SIGNALS_SIZE_MEAN);
        expect.that(stats.getUserBiddingSignalsSizeVarianceB())
                .isEqualTo(USER_BIDDING_SIGNALS_SIZE_VARIANCE);
    }
}
