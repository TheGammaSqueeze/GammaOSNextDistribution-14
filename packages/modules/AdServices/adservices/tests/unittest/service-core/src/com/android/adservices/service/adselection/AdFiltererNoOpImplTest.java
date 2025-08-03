/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertEquals;

import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class AdFiltererNoOpImplTest {
    private static final AdData.Builder AD_DATA_BUILDER =
            AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0);

    private static final SignedContextualAds.Builder CONTEXTUAL_ADS_BUILDER =
            SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                    .setAdsWithBid(ImmutableList.of(new AdWithBid(AD_DATA_BUILDER.build(), 1.0)))
                    .setBuyer(CommonFixture.VALID_BUYER_1)
                    .setDecisionLogicUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/decisionPath/"));

    private AdFilterer mAdFilterer;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAdFilterer = new AdFiltererNoOpImpl();
    }

    @Test
    public void testFilterNullAdFilters() {
        final AdData adData = AD_DATA_BUILDER.setAdFilters(null).build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testFilterNullComponentFilters() {
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setAppInstallFilters(null)
                                        .setFrequencyCapFilters(null)
                                        .build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();
        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testAppInstallFilter() {
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();
        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testMultipleApps() {
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(
                                        Arrays.asList(
                                                CommonFixture.TEST_PACKAGE_NAME_1,
                                                CommonFixture.TEST_PACKAGE_NAME_2)))
                        .build();
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();
        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testMultipleAds() {
        AppInstallFilters appFilters1 =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1)))
                        .build();
        AppInstallFilters appFilters2 =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_2)))
                        .build();
        final AdData adData1 =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters1).build())
                        .build();
        final AdData adData2 =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters2).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(
                                ImmutableList.of(
                                        new AdWithBid(adData1, 1.0), new AdWithBid(adData2, 2.0)))
                        .build();
        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testFilterOnCustomAudience() {
        List<DBCustomAudience> caList =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        assertEquals(caList, mAdFilterer.filterCustomAudiences(caList));
    }
}
