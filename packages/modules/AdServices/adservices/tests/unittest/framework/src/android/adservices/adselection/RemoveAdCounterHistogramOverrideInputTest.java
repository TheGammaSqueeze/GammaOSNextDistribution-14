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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class RemoveAdCounterHistogramOverrideInputTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidInput_success() {
        final RemoveAdCounterHistogramOverrideInput originalInput =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        assertThat(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(originalInput.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(originalInput.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
    }

    @Test
    public void testParcelValidInput_success() {
        final RemoveAdCounterHistogramOverrideInput originalInput =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalInput.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final RemoveAdCounterHistogramOverrideInput inputFromParcel =
                RemoveAdCounterHistogramOverrideInput.CREATOR.createFromParcel(targetParcel);

        assertThat(inputFromParcel.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(inputFromParcel.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(inputFromParcel.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
    }

    @Test
    public void testCreatorNewArray_success() {
        final int arraySize = 10;

        assertArrayEquals(
                new RemoveAdCounterHistogramOverrideInput[arraySize],
                RemoveAdCounterHistogramOverrideInput.CREATOR.newArray(arraySize));
    }

    @Test
    public void testToString() {
        final RemoveAdCounterHistogramOverrideInput originalInput =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        final String expected =
                String.format(
                        "RemoveAdCounterHistogramOverrideInput{mAdEventType=%d,"
                                + " mAdCounterKey=%d, mBuyer=%s}",
                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                        KeyedFrequencyCapFixture.KEY1,
                        CommonFixture.VALID_BUYER_1);

        assertThat(originalInput.toString()).isEqualTo(expected);
    }

    @Test
    public void testSetNullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new RemoveAdCounterHistogramOverrideInput.Builder().setBuyer(null));
    }

    @Test
    public void testBuildUnsetAdEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RemoveAdCounterHistogramOverrideInput.Builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .build());
    }

    @Test
    public void testBuildUnsetAdCounterKey_success() {
        final RemoveAdCounterHistogramOverrideInput originalInput =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        assertThat(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        assertThat(originalInput.getAdCounterKey()).isEqualTo(0);
        assertThat(originalInput.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
    }

    @Test
    public void testBuildUnsetBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RemoveAdCounterHistogramOverrideInput.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .build());
    }
}
