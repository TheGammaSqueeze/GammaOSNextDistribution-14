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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

public class BuyerInputGeneratorIntermediateStatsTest {

    private BuyerInputGeneratorIntermediateStats mBuyerInputGeneratorIntermediateStats;

    @Before
    public void setup() {
        mBuyerInputGeneratorIntermediateStats = new BuyerInputGeneratorIntermediateStats();
    }

    @Test
    public void testIncrementNumCustomAudiences() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiences()).isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.incrementNumCustomAudiences();
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiences()).isEqualTo(1);
        mBuyerInputGeneratorIntermediateStats.incrementNumCustomAudiences();
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiences()).isEqualTo(2);
    }

    @Test
    public void testIncrementNumCustomAudiencesOmitAds() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiencesOmitAds())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.incrementNumCustomAudiencesOmitAds();
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiencesOmitAds())
                .isEqualTo(1);
        mBuyerInputGeneratorIntermediateStats.incrementNumCustomAudiencesOmitAds();
        assertThat(mBuyerInputGeneratorIntermediateStats.getNumCustomAudiencesOmitAds())
                .isEqualTo(2);
    }

    @Test
    public void testAddCustomAudienceSize() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizes()).isEmpty();
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizes())
                .isEqualTo(ImmutableList.of(1));
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizes())
                .isEqualTo(ImmutableList.of(1, 2));
    }

    @Test
    public void testAddTrustedBiddingSignalsKeysSize() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizes())
                .isEmpty();
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizes())
                .isEqualTo(ImmutableList.of(1));
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizes())
                .isEqualTo(ImmutableList.of(1, 2));
    }

    @Test
    public void testAddUserBiddingSignalsSize() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizes()).isEmpty();
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizes())
                .isEqualTo(ImmutableList.of(1));
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizes())
                .isEqualTo(ImmutableList.of(1, 2));
    }

    @Test
    public void testGetCustomAudienceSizeMeanB() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeMeanB()).isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeMeanB())
                .isEqualTo(1F);
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeMeanB())
                .isEqualTo(1.5F);
    }

    @Test
    public void testGetTrustedBiddingSignalsKeysSizeMeanB() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizeMeanB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizeMeanB())
                .isEqualTo(1F);
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getTrustedBiddingSignalsKeysSizeMeanB())
                .isEqualTo(1.5F);
    }

    @Test
    public void testGetUserBiddingSignalsSizeMeanB() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeMeanB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeMeanB())
                .isEqualTo(1F);
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(2);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeMeanB())
                .isEqualTo(1.5F);
    }

    @Test
    public void testGetCustomAudienceSizeVarianceB() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addCustomAudienceSize(5);
        assertThat(mBuyerInputGeneratorIntermediateStats.getCustomAudienceSizeVarianceB())
                .isEqualTo(4);
    }

    @Test
    public void testGetTrustedBiddingSignalsKeysSizeVarianceB() {
        assertThat(
                        mBuyerInputGeneratorIntermediateStats
                                .getTrustedBiddingSignalskeysSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(1);
        assertThat(
                        mBuyerInputGeneratorIntermediateStats
                                .getTrustedBiddingSignalskeysSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addTrustedBiddingSignalsKeysSize(5);
        assertThat(
                        mBuyerInputGeneratorIntermediateStats
                                .getTrustedBiddingSignalskeysSizeVarianceB())
                .isEqualTo(4);
    }

    @Test
    public void testGetUserBiddingSignalsSizeVarianceB() {
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(1);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeVarianceB())
                .isEqualTo(0);
        mBuyerInputGeneratorIntermediateStats.addUserBiddingSignalsSize(5);
        assertThat(mBuyerInputGeneratorIntermediateStats.getUserBiddingSignalsSizeVarianceB())
                .isEqualTo(4);
    }
}
