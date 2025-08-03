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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;

import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_SCORING_KEY;
import static com.android.adservices.service.adselection.DataVersionFetcher.MAX_UNSIGNED_8_BIT;
import static com.android.adservices.service.adselection.DataVersionFetcher.getBuyerDataVersion;
import static com.android.adservices.service.adselection.DataVersionFetcher.getSellerDataVersion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class DataVersionFetcherTest {
    public static final Uri VALID_URI = Uri.parse("test.com");
    public static final DBTrustedBiddingData VALID_TB_DATA =
            new DBTrustedBiddingData(VALID_URI, ImmutableList.of());
    public static final Map<Uri, TrustedBiddingResponse> VALID_TRUSTED_BIDDING_RESPONSE_MAP =
            ImmutableMap.of(
                    VALID_URI,
                    TrustedBiddingResponse.builder()
                            .setBody(new JSONObject())
                            .setHeaders(
                                    new JSONObject(
                                            ImmutableMap.of(
                                                    DATA_VERSION_HEADER_BIDDING_KEY,
                                                    List.of(DATA_VERSION_1))))
                            .build());

    public static final Map<String, List<String>> VALID_HEADERS_SELLER =
            ImmutableMap.of(
                    DATA_VERSION_HEADER_SCORING_KEY, List.of(Integer.toString(DATA_VERSION_1)));

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetBuyerDataVersionHeaderSuccess() {
        int dataVersion = getBuyerDataVersion(VALID_TB_DATA, VALID_TRUSTED_BIDDING_RESPONSE_MAP);
        assertEquals(DATA_VERSION_1, dataVersion);
    }

    @Test
    public void testGetSellerDataVersionHeaderSuccess() {
        int dataVersion = getSellerDataVersion(VALID_HEADERS_SELLER);
        assertEquals(DATA_VERSION_1, dataVersion);
    }

    @Test
    public void testGetBuyerDataVersionHeaderSuccessTakesFirstOfMultipleValues() {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapMultipleValues =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1, DATA_VERSION_2))))
                                .build());

        int dataVersion =
                getBuyerDataVersion(VALID_TB_DATA, trustedBiddingResponseMapMultipleValues);
        assertEquals(DATA_VERSION_1, dataVersion);
    }

    @Test
    public void testGetSellerDataVersionHeaderTakesFirstOfMultipleValues() {
        Map<String, List<String>> headersMultipleValues =
                ImmutableMap.of(
                        DATA_VERSION_HEADER_SCORING_KEY,
                        List.of(
                                Integer.toString(DATA_VERSION_1),
                                Integer.toString(DATA_VERSION_2)));
        int dataVersion = getSellerDataVersion(headersMultipleValues);
        assertEquals(DATA_VERSION_1, dataVersion);
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenTrustedBidingDataIsNull() {
        assertThrows(
                IllegalStateException.class,
                () -> getBuyerDataVersion(null, VALID_TRUSTED_BIDDING_RESPONSE_MAP));
    }

    @Test
    public void testGetSellerDataVersionHeaderThrowsExceptionWhenHeadersAreNull() {
        assertThrows(IllegalStateException.class, () -> getSellerDataVersion(null));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenMapDoesNotHaveUriKey() {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidKey =
                ImmutableMap.of(
                        Uri.parse("invalidKey"),
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1))))
                                .build());

        assertThrows(
                IllegalStateException.class,
                () -> getBuyerDataVersion(VALID_TB_DATA, trustedBiddingResponseMapWithoutValidKey));
    }

    @Test
    public void testGetSellerDataVersionHeaderThrowsExceptionWhenHeadersDoNotHaveDataVersionKey() {
        Map<String, List<String>> headersWithWrongKey =
                ImmutableMap.of("incorrectKey", List.of(Integer.toString(DATA_VERSION_1)));

        assertThrows(IllegalStateException.class, () -> getSellerDataVersion(headersWithWrongKey));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenHeadersDoNotHaveDataVersionKey() {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidKey =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        "invalidKey", List.of(DATA_VERSION_1))))
                                .build());
        assertThrows(
                IllegalStateException.class,
                () -> getBuyerDataVersion(VALID_TB_DATA, trustedBiddingResponseMapWithoutValidKey));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenHeadersDoNotHaveListValue()
            throws Exception {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidValue =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject()
                                                .put(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        DATA_VERSION_1))
                                .build());
        assertThrows(
                IllegalStateException.class,
                () ->
                        getBuyerDataVersion(
                                VALID_TB_DATA, trustedBiddingResponseMapWithoutValidValue));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenHeadersDoNotHaveIntegerValue()
            throws Exception {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidValue =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of("invalid"))))
                                .build());
        assertThrows(
                IllegalStateException.class,
                () ->
                        getBuyerDataVersion(
                                VALID_TB_DATA, trustedBiddingResponseMapWithoutValidValue));
    }

    @Test
    public void testGetSellerDataVersionHeaderThrowsExceptionWhenHeadersDoesNotParseIntoInt()
            throws Exception {
        Map<String, List<String>> headersWithNonIntValue =
                ImmutableMap.of(DATA_VERSION_HEADER_SCORING_KEY, List.of("incorrectValue"));

        assertThrows(
                IllegalStateException.class, () -> getSellerDataVersion(headersWithNonIntValue));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenDataVersionExceedsMax()
            throws Exception {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidValue =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(MAX_UNSIGNED_8_BIT + 1))))
                                .build());
        assertThrows(
                IllegalStateException.class,
                () ->
                        getBuyerDataVersion(
                                VALID_TB_DATA, trustedBiddingResponseMapWithoutValidValue));
    }

    @Test
    public void testGetSellerDataVersionHeaderThrowsExceptionWhenDataVersionExceedsMax()
            throws Exception {
        Map<String, List<String>> headersWithExceedingIntValue =
                ImmutableMap.of(
                        DATA_VERSION_HEADER_SCORING_KEY,
                        List.of(Integer.toString(MAX_UNSIGNED_8_BIT + 1)));

        assertThrows(
                IllegalStateException.class,
                () -> getSellerDataVersion(headersWithExceedingIntValue));
    }

    @Test
    public void testGetBuyerDataVersionHeaderThrowsExceptionWhenDataVersionIsNegative()
            throws Exception {
        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseMapWithoutValidValue =
                ImmutableMap.of(
                        VALID_URI,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject())
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(-1))))
                                .build());
        assertThrows(
                IllegalStateException.class,
                () ->
                        getBuyerDataVersion(
                                VALID_TB_DATA, trustedBiddingResponseMapWithoutValidValue));
    }

    @Test
    public void testGetSellerDataVersionHeaderThrowsExceptionWhenDataVersionIsNegative()
            throws Exception {
        Map<String, List<String>> headersWithNegativeIntValue =
                ImmutableMap.of(DATA_VERSION_HEADER_SCORING_KEY, List.of(Integer.toString(-1)));

        assertThrows(
                IllegalStateException.class,
                () -> getSellerDataVersion(headersWithNegativeIntValue));
    }
}
