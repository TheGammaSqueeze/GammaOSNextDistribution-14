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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Random;

@SmallTest
public class UpdateAdCounterHistogramRequestTest {
    private static final Random RANDOM = new Random();
    private static final long VALID_AD_SELECTION_ID = 10;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidRequest_success() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1)
                        .build();

        assertThat(originalRequest.getAdSelectionId()).isEqualTo(VALID_AD_SELECTION_ID);
        assertThat(originalRequest.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(originalRequest.getCallerAdTech()).isEqualTo(CommonFixture.VALID_BUYER_1);
    }

    @Test
    public void testEqualsIdentical() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        final UpdateAdCounterHistogramRequest identicalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();

        assertThat(originalRequest.equals(identicalRequest)).isTrue();
    }

    @Test
    public void testEqualsDifferent() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        final UpdateAdCounterHistogramRequest differentRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID + 99,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_2)
                        .build();

        assertThat(originalRequest.equals(differentRequest)).isFalse();
    }

    @Test
    public void testEqualsNull() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        final UpdateAdCounterHistogramRequest nullRequest = null;

        assertThat(originalRequest.equals(nullRequest)).isFalse();
    }

    @Test
    public void testHashCodeIdentical() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        final UpdateAdCounterHistogramRequest identicalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();

        assertThat(originalRequest.hashCode()).isEqualTo(identicalRequest.hashCode());
    }

    @Test
    public void testHashCodeDifferent() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        final UpdateAdCounterHistogramRequest differentRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID + 99,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_2)
                        .build();

        assertThat(originalRequest.hashCode()).isNotEqualTo(differentRequest.hashCode());
    }

    @Test
    public void testToString() {
        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                CommonFixture.VALID_BUYER_1)
                        .build();

        final String expected =
                String.format(
                        "UpdateAdCounterHistogramRequest{mAdSelectionId=%s, mAdEventType=%s,"
                                + " mCallerAdTech=%s}",
                        VALID_AD_SELECTION_ID,
                        FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                        CommonFixture.VALID_BUYER_1);

        assertThat(originalRequest.toString()).isEqualTo(expected);
    }

    @Test
    public void testAllSettersOverwrite_success() {
        final long otherAdSelectionId = VALID_AD_SELECTION_ID + 1;

        final UpdateAdCounterHistogramRequest originalRequest =
                new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1)
                        .setAdSelectionId(otherAdSelectionId)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                        .setCallerAdTech(CommonFixture.VALID_BUYER_2)
                        .build();

        assertThat(originalRequest.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        assertThat(originalRequest.getAdSelectionId()).isEqualTo(otherAdSelectionId);
        assertThat(originalRequest.getCallerAdTech()).isEqualTo(CommonFixture.VALID_BUYER_2);
    }

    @Test
    public void testBuildZeroAdSelectionId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                /* adSelectionId= */ 0,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testSetZeroAdSelectionId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setAdSelectionId(0));
    }

    @Test
    public void testBuildWinType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                                CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testSetWinType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN));
    }

    @Test
    public void testBuildInvalidType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_INVALID,
                                CommonFixture.VALID_BUYER_1));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_MIN - 1 - RANDOM.nextInt(10),
                                CommonFixture.VALID_BUYER_1));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_MAX + 1 + RANDOM.nextInt(10),
                                CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testSetInvalidType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_INVALID));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setAdEventType(
                                        FrequencyCapFilters.AD_EVENT_TYPE_MIN
                                                - 1
                                                - RANDOM.nextInt(10)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setAdEventType(
                                        FrequencyCapFilters.AD_EVENT_TYPE_MAX
                                                + 1
                                                + RANDOM.nextInt(10)));
    }

    @Test
    public void testBuildNullCaller_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                /* callerAdTech= */ null));
    }

    @Test
    public void testSetNullCaller_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1)
                                .setCallerAdTech(null));
    }
}
