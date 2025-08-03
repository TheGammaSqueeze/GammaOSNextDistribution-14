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

package com.android.adservices.data.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class CustomAudienceConvertersTest {

    DBCustomAudience.Converters mConverters = new DBCustomAudience.Converters(true, true);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testDeserialize_invalidString() {
        RuntimeException runtimeException =
                assertThrows(RuntimeException.class, () -> mConverters.fromJson("   "));
        assertTrue(runtimeException.getCause() instanceof JSONException);
    }

    @Test
    public void testSerializeAndDeserialize_runNormally() {
        List<DBAdData> input =
                AdDataFixture.getValidFilterAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        String serializedString = mConverters.toJson(input);
        List<DBAdData> output = mConverters.fromJson(serializedString);
        assertEquals(input, output);
    }

    @Test
    public void testSerializeAndDeserialize_runNormallyNoFilters() {
        List<DBAdData> input =
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        String serializedString = mConverters.toJson(input);
        List<DBAdData> output = mConverters.fromJson(serializedString);
        assertEquals(input, output);
    }

    @Test
    public void testSerializeAndDeserialize_filtersDisabled() {
        DBCustomAudience.Converters noFilterConverter =
                new DBCustomAudience.Converters(false, true);
        List<DBAdData> input =
                AdDataFixture.getValidFilterAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        List<DBAdData> expected =
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        String serializedString = noFilterConverter.toJson(input);
        List<DBAdData> output = noFilterConverter.fromJson(serializedString);
        assertEquals(expected, output);
    }

    @Test
    public void testSerializeAndDeserialize_toJsonFiltersDisabled() {
        DBCustomAudience.Converters noFilterConverter =
                new DBCustomAudience.Converters(false, true);
        List<DBAdData> input =
                AdDataFixture.getValidFilterAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        List<DBAdData> expected =
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        String serializedString = noFilterConverter.toJson(input);
        List<DBAdData> output = mConverters.fromJson(serializedString);
        assertEquals(expected, output);
    }

    @Test
    public void testSerializeAndDeserialize_fromJsonFiltersDisabled() {
        DBCustomAudience.Converters noFilterConverter =
                new DBCustomAudience.Converters(false, true);
        List<DBAdData> input =
                AdDataFixture.getValidFilterAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        List<DBAdData> expected =
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdDataFixture::convertAdDataToDBAdData)
                        .collect(Collectors.toList());
        String serializedString = mConverters.toJson(input);
        List<DBAdData> output = noFilterConverter.fromJson(serializedString);
        assertEquals(expected, output);
    }

    @Test
    public void testSerializeAndDeserialize_toJsonRenderIdDisabled() {
        DBCustomAudience.Converters noRenderIdConverter =
                new DBCustomAudience.Converters(true, false);

        DBAdData input =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId("ad-render-id")
                                .build());
        DBAdData expected =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId(null)
                                .build());

        String serializedString = noRenderIdConverter.toJson(ImmutableList.of(input));
        List<DBAdData> output = mConverters.fromJson(serializedString);
        assertEquals(List.of(expected), output);
    }

    @Test
    public void testSerializeAndDeserialize_fromJsonRenderIdDisabled() {
        DBCustomAudience.Converters noRenderIdConverter =
                new DBCustomAudience.Converters(true, false);

        DBAdData input =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId("ad-render-id")
                                .build());
        DBAdData expected =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId(null)
                                .build());

        String serializedString = mConverters.toJson(ImmutableList.of(input));
        List<DBAdData> output = noRenderIdConverter.fromJson(serializedString);
        assertEquals(List.of(expected), output);
    }

    @Test
    public void testSerializeAndDeserialize_toJsonRenderIdEnabled() {
        DBCustomAudience.Converters noRenderIdConverter =
                new DBCustomAudience.Converters(true, true);

        DBAdData expected =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId("ad-render-id")
                                .build());

        String serializedString = noRenderIdConverter.toJson(ImmutableList.of(expected));
        List<DBAdData> output = mConverters.fromJson(serializedString);
        assertEquals(List.of(expected), output);
    }

    @Test
    public void testSerializeAndDeserialize_fromJsonRenderIdEnabled() {
        DBCustomAudience.Converters noRenderIdConverter =
                new DBCustomAudience.Converters(true, true);

        DBAdData expected =
                DBAdDataFixture.convertAdDataToDBAdData(
                        AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                                .setAdRenderId("ad-render-id")
                                .build());

        String serializedString = mConverters.toJson(ImmutableList.of(expected));
        List<DBAdData> output = noRenderIdConverter.fromJson(serializedString);
        assertEquals(List.of(expected), output);
    }

    @Test
    public void testSerialize_nullInput() throws JSONException {
        assertNull(mConverters.toJson(null));
    }

    @Test
    public void testDeserialize_nullInput() throws JSONException {
        assertNull(mConverters.fromJson(null));
    }
}
