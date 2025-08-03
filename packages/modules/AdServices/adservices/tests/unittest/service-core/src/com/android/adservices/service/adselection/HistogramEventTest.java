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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class HistogramEventTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidHistogramEvent_success() {
        HistogramEvent event =
                HistogramEvent.builder()
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        assertThat(event.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(event.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(event.getCustomAudienceOwner()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(event.getCustomAudienceName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(event.getAdEventType()).isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_WIN);
        assertThat(event.getTimestamp()).isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(event.getSourceApp()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testBuildUnsetAdCounterKey_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        HistogramEvent.builder()
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetBuyer_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceOwnerWithWinType_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceOwnerWithNonWinType_success() {
        HistogramEvent event =
                HistogramEvent.builder()
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        assertThat(event.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(event.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(event.getCustomAudienceOwner()).isNull();
        assertThat(event.getCustomAudienceName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(event.getAdEventType()).isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_VIEW);
        assertThat(event.getTimestamp()).isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(event.getSourceApp()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testBuildUnsetCustomAudienceNameWithWinType_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceNameWithNonWinType_success() {
        HistogramEvent event =
                HistogramEvent.builder()
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        assertThat(event.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(event.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(event.getCustomAudienceOwner()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(event.getCustomAudienceName()).isNull();
        assertThat(event.getAdEventType()).isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(event.getTimestamp()).isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(event.getSourceApp()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testBuildUnsetAdEventType_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetTimestamp_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                                .build());
    }

    @Test
    public void testBuildUnsetSourceApp_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        HistogramEvent.builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .build());
    }

    @Test
    public void testSetNullBuyer_throws() {
        assertThrows(NullPointerException.class, () -> HistogramEvent.builder().setBuyer(null));
    }

    @Test
    public void testSetNullCustomAudienceOwnerNonWinType_success() {
        HistogramEvent event =
                HistogramEvent.builder()
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(null)
                        .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        assertThat(event.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(event.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(event.getCustomAudienceOwner()).isNull();
        assertThat(event.getCustomAudienceName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(event.getAdEventType()).isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(event.getTimestamp()).isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(event.getSourceApp()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testSetNullCustomAudienceNameNonWinType_success() {
        HistogramEvent event =
                HistogramEvent.builder()
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(null)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        assertThat(event.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(event.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(event.getCustomAudienceOwner()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(event.getCustomAudienceName()).isNull();
        assertThat(event.getAdEventType()).isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(event.getTimestamp()).isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(event.getSourceApp()).isEqualTo(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testSetNullTimestamp_throws() {
        assertThrows(NullPointerException.class, () -> HistogramEvent.builder().setTimestamp(null));
    }

    @Test
    public void testSetNullSourceApp_throws() {
        assertThrows(NullPointerException.class, () -> HistogramEvent.builder().setSourceApp(null));
    }
}
