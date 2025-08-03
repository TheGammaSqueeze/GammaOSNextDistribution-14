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

package com.android.adservices.service.adselection.signature;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class BinarySerializerSignedContextualAdsTest {
    public static final byte[] TEST_SIGNATURE = new byte[] {0, 1, 2};

    private SignedContextualAdsHashUtil mSerializer;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mSerializer = new SignedContextualAdsHashUtil();
    }

    @Test
    public void testWriteString() {
        String testString = "Hello";
        mSerializer.writeString(testString);
        assertThat(bytesToString(mSerializer.getBytes())).isEqualTo(testString);
    }

    @Test
    public void testWriteLong() {
        long testLong = 123456789L;
        mSerializer.writeLong(testLong);
        assertThat(bytesToString(mSerializer.getBytes())).isEqualTo("" + testLong);
    }

    @Test
    public void testWriteDouble() {
        double testDouble = 123.45;
        mSerializer.writeDouble(testDouble);
        assertThat(bytesToString(mSerializer.getBytes())).isEqualTo("" + testDouble);
    }

    @Test
    public void testWriteInt() {
        int testInt = 123;
        mSerializer.writeInt(testInt);
        assertThat(bytesToString(mSerializer.getBytes())).isEqualTo("" + testInt);
    }

    @Test
    public void testContextualAdSerialization_fullObject_success() {
        String buyer = "https://example.com";
        String decisionLogicUri = "example.com/decisionLogic";
        String metadata = "metadata";
        String adCounterKeys = "1,2,3";
        String adRenderUri = "example.com/render";
        String bid = "5.10";
        int adCounterKey = 42;
        int maxCount = 43;
        Duration oneDay = Duration.ofDays(1);
        KeyedFrequencyCap keyedFrequencyCap =
                new KeyedFrequencyCap.Builder(adCounterKey, maxCount, oneDay).build();
        FrequencyCapFilters frequencyCapFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForClickEvents(
                                Collections.singletonList(keyedFrequencyCap))
                        .setKeyedFrequencyCapsForWinEvents(
                                Collections.singletonList(keyedFrequencyCap))
                        .setKeyedFrequencyCapsForImpressionEvents(
                                Collections.singletonList(keyedFrequencyCap))
                        .setKeyedFrequencyCapsForViewEvents(
                                Collections.singletonList(keyedFrequencyCap))
                        .build();
        Set<String> packageNames = Set.of("com.awesome.app1", "com.awesome.app2");
        AdFilters adFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(frequencyCapFilters)
                        .setAppInstallFilters(
                                new AppInstallFilters.Builder()
                                        .setPackageNames(packageNames)
                                        .build())
                        .build();
        String adRenderId = "987";

        Set<Integer> adCounterKeysSet =
                Arrays.stream(adCounterKeys.split(","))
                        .map(Integer::valueOf)
                        .collect(Collectors.toSet());
        SignedContextualAds contextualAds =
                new SignedContextualAds.Builder()
                        .setBuyer(AdTechIdentifier.fromString(buyer))
                        .setDecisionLogicUri(Uri.parse(decisionLogicUri))
                        .setAdsWithBid(
                                ImmutableList.of(
                                        new AdWithBid(
                                                new AdData.Builder()
                                                        .setMetadata(metadata)
                                                        .setAdFilters(adFilters)
                                                        .setAdRenderId(adRenderId)
                                                        .setAdCounterKeys(adCounterKeysSet)
                                                        .setRenderUri(Uri.parse(adRenderUri))
                                                        .build(),
                                                Double.parseDouble(bid))))
                        .setSignature(TEST_SIGNATURE)
                        .build();

        byte[] serialized = new SignedContextualAdsHashUtil().serialize(contextualAds);

        /*
         * This serialization is created manually following the rules explained in
         * SignedContextualAds. Our serialization has to exactly match this to have consistency
         * between ad tech and PPAPI.
         */
        String expected =
                String.format(
                        "buyer=%s|decision_logic_uri=%s|ads_with_bid=ad_data=ad_counter_keys=%s|"
                                + "ad_filters=app_install_filters=package_names=%s||"
                                + "frequency_cap_filters=keyed_frequency_caps_for_click_events="
                                + "ad_counter_key=%s|interval=%s|max_count=%s||"
                                + "keyed_frequency_caps_for_impression_events=ad_counter_key=%s|"
                                + "interval=%s|max_count=%s||keyed_frequency_caps_for_view_events="
                                + "ad_counter_key=%s|interval=%s|max_count=%s||"
                                + "keyed_frequency_caps_for_win_events=ad_counter_key=%s|"
                                + "interval=%s|max_count=%s||||ad_render_id=%s|metadata=%s|"
                                + "render_uri=%s||bid=%s||",
                        buyer,
                        decisionLogicUri,
                        adCounterKeys,
                        packageNames.stream().sorted().collect(Collectors.joining(",")),
                        adCounterKey,
                        oneDay.toMillis(),
                        maxCount,
                        adCounterKey,
                        oneDay.toMillis(),
                        maxCount,
                        adCounterKey,
                        oneDay.toMillis(),
                        maxCount,
                        adCounterKey,
                        oneDay.toMillis(),
                        maxCount,
                        adRenderId,
                        metadata,
                        adRenderUri,
                        bid);
        assertThat(bytesToString(serialized)).isEqualTo(expected);
    }

    @Test
    public void testContextualAdSerialization_minimalistObject_success() {
        String buyer = "https://example.com";
        String decisionLogicUri = "example.com/decisionLogic";
        String metadata = "metadata";
        String adCounterKeys = "1,2,3";
        String adRenderUri = "example.com/render";
        String bid = "5.10";
        SignedContextualAds contextualAds =
                new SignedContextualAds.Builder()
                        .setBuyer(AdTechIdentifier.fromString(buyer))
                        .setDecisionLogicUri(Uri.parse(decisionLogicUri))
                        .setAdsWithBid(
                                ImmutableList.of(
                                        new AdWithBid(
                                                new AdData.Builder()
                                                        .setMetadata(metadata)
                                                        .setAdCounterKeys(
                                                                Arrays.stream(
                                                                                adCounterKeys.split(
                                                                                        ","))
                                                                        .map(Integer::valueOf)
                                                                        .collect(
                                                                                Collectors.toSet()))
                                                        .setRenderUri(Uri.parse(adRenderUri))
                                                        .build(),
                                                Double.parseDouble(bid))))
                        .setSignature(TEST_SIGNATURE)
                        .build();

        byte[] serialized = new SignedContextualAdsHashUtil().serialize(contextualAds);

        String expected =
                String.format(
                        "buyer=%s|decision_logic_uri=%s|ads_with_bid=ad_data=ad_counter_keys=%s|"
                                + "metadata=%s|render_uri=%s||bid=%s||",
                        buyer, decisionLogicUri, adCounterKeys, metadata, adRenderUri, bid);
        assertThat(bytesToString(serialized)).isEqualTo(expected);
    }

    private String bytesToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
