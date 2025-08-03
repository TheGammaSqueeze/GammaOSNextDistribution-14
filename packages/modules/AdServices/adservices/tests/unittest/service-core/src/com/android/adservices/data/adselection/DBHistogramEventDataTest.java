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

package com.android.adservices.data.adselection;

import static com.android.adservices.data.adselection.DBHistogramIdentifierFixture.VALID_FOREIGN_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.adselection.HistogramEventFixture;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class DBHistogramEventDataTest {
    private static final Long NON_NULL_ROW_ID = 50L;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidEventData_success() {
        DBHistogramEventData originalEventData =
                DBHistogramEventData.builder()
                        .setRowId(NON_NULL_ROW_ID)
                        .setHistogramIdentifierForeignKey(VALID_FOREIGN_KEY)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setAdCounterIntKey(KeyedFrequencyCapFixture.KEY1)
                        .build();

        assertThat(originalEventData.getRowId()).isEqualTo(NON_NULL_ROW_ID);
        assertThat(originalEventData.getHistogramIdentifierForeignKey())
                .isEqualTo(VALID_FOREIGN_KEY);
        assertThat(originalEventData.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_WIN);
        assertThat(originalEventData.getTimestamp())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(originalEventData.getAdCounterIntKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
    }

    @Test
    public void testBuildUnsetRowId_success() {
        DBHistogramEventData originalEventData =
                DBHistogramEventData.builder()
                        .setHistogramIdentifierForeignKey(VALID_FOREIGN_KEY)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setAdCounterIntKey(KeyedFrequencyCapFixture.KEY1)
                        .build();

        assertThat(originalEventData.getRowId()).isNull();
        assertThat(originalEventData.getHistogramIdentifierForeignKey())
                .isEqualTo(VALID_FOREIGN_KEY);
        assertThat(originalEventData.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_WIN);
        assertThat(originalEventData.getTimestamp())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(originalEventData.getAdCounterIntKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
    }

    @Test
    public void testBuildUnsetForeignKey_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBHistogramEventData.builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setAdCounterIntKey(KeyedFrequencyCapFixture.KEY1)
                                .build());
    }

    @Test
    public void testBuildUnsetAdEventType_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBHistogramEventData.builder()
                                .setHistogramIdentifierForeignKey(VALID_FOREIGN_KEY)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .setAdCounterIntKey(KeyedFrequencyCapFixture.KEY1)
                                .build());
    }

    @Test
    public void testBuildUnsetTimestamp_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBHistogramEventData.builder()
                                .setHistogramIdentifierForeignKey(VALID_FOREIGN_KEY)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setAdCounterIntKey(KeyedFrequencyCapFixture.KEY1)
                                .build());
    }

    @Test
    public void testBuildUnsetAdCounterKey_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBHistogramEventData.builder()
                                .setHistogramIdentifierForeignKey(VALID_FOREIGN_KEY)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                                .build());
    }

    @Test
    public void testSetNullTimestamp_throws() {
        assertThrows(
                NullPointerException.class,
                () -> DBHistogramEventData.builder().setTimestamp(null));
    }

    @Test
    public void testCreateValidEventData_success() {
        DBHistogramEventData originalEventData =
                DBHistogramEventData.create(
                        NON_NULL_ROW_ID,
                        VALID_FOREIGN_KEY,
                        FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        KeyedFrequencyCapFixture.KEY1);

        assertThat(originalEventData.getRowId()).isEqualTo(NON_NULL_ROW_ID);
        assertThat(originalEventData.getHistogramIdentifierForeignKey())
                .isEqualTo(VALID_FOREIGN_KEY);
        assertThat(originalEventData.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        assertThat(originalEventData.getTimestamp())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(originalEventData.getAdCounterIntKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
    }

    @Test
    public void testCreateNullRowId_success() {
        DBHistogramEventData originalEventData =
                DBHistogramEventData.create(
                        null,
                        VALID_FOREIGN_KEY,
                        FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        KeyedFrequencyCapFixture.KEY1);

        assertThat(originalEventData.getRowId()).isNull();
        assertThat(originalEventData.getHistogramIdentifierForeignKey())
                .isEqualTo(VALID_FOREIGN_KEY);
        assertThat(originalEventData.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        assertThat(originalEventData.getTimestamp())
                .isEqualTo(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertThat(originalEventData.getAdCounterIntKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
    }

    @Test
    public void testCreateNullTimestamp_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBHistogramEventData.create(
                                NON_NULL_ROW_ID,
                                VALID_FOREIGN_KEY,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                null,
                                KeyedFrequencyCapFixture.KEY1));
    }

    @Test
    public void testFromHistogramEvent_success() {
        DBHistogramEventData originalEventData =
                DBHistogramEventData.fromHistogramEvent(
                        VALID_FOREIGN_KEY, HistogramEventFixture.VALID_HISTOGRAM_EVENT);

        assertThat(originalEventData.getHistogramIdentifierForeignKey())
                .isEqualTo(VALID_FOREIGN_KEY);
        assertThat(originalEventData.getAdEventType())
                .isEqualTo(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getAdEventType());
        assertThat(originalEventData.getTimestamp())
                .isEqualTo(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getTimestamp());
        assertThat(originalEventData.getAdCounterIntKey())
                .isEqualTo(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getAdCounterKey());
    }

    @Test
    public void testFromNullHistogramEvent_throws() {
        assertThrows(
                NullPointerException.class,
                () -> DBHistogramEventData.fromHistogramEvent(VALID_FOREIGN_KEY, null));
    }
}
