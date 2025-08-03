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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.FledgeRoomConverters;

import org.junit.Rule;
import org.junit.Test;

public class DBAdSelectionHistogramInfoTest {
    private static final String SERIALIZED_AD_COUNTER_KEYS =
            FledgeRoomConverters.serializeIntegerSet(AdDataFixture.getAdCounterKeys());

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCreateValidHistogramInfo() {
        DBAdSelectionHistogramInfo histogramInfo =
                DBAdSelectionHistogramInfo.create(
                        CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS);

        assertThat(histogramInfo.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(histogramInfo.getSerializedAdCounterKeys())
                .isEqualTo(SERIALIZED_AD_COUNTER_KEYS);
    }

    @Test
    public void testGetAdCounterKeysDeserializesCorrectly() {
        DBAdSelectionHistogramInfo histogramInfo =
                DBAdSelectionHistogramInfo.create(
                        CommonFixture.VALID_BUYER_1, SERIALIZED_AD_COUNTER_KEYS);

        assertThat(histogramInfo.getSerializedAdCounterKeys())
                .isEqualTo(SERIALIZED_AD_COUNTER_KEYS);
        assertThat(histogramInfo.getAdCounterKeys()).isEqualTo(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testCreateNullBuyerThrows() {
        assertThrows(
                NullPointerException.class,
                () -> DBAdSelectionHistogramInfo.create(null, SERIALIZED_AD_COUNTER_KEYS));
    }

    @Test
    public void testCreateNullAdCounterKeysSuccess() {
        DBAdSelectionHistogramInfo histogramInfo =
                DBAdSelectionHistogramInfo.create(CommonFixture.VALID_BUYER_1, null);

        assertThat(histogramInfo.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(histogramInfo.getSerializedAdCounterKeys()).isNull();
    }

    @Test
    public void testGetNullAdCounterKeysDeserializesCorrectly() {
        DBAdSelectionHistogramInfo histogramInfo =
                DBAdSelectionHistogramInfo.create(CommonFixture.VALID_BUYER_1, null);

        assertThat(histogramInfo.getSerializedAdCounterKeys()).isNull();
        assertThat(histogramInfo.getAdCounterKeys()).isNull();
    }
}
