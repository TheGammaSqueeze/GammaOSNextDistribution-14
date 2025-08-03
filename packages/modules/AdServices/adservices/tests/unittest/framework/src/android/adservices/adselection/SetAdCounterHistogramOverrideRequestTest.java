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

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

// TODO(b/265204820): Move to CTS tests once public APIs are unhidden
@SmallTest
public class SetAdCounterHistogramOverrideRequestTest {
    private static final ImmutableList<Instant> HISTOGRAM_TIMESTAMPS =
            ImmutableList.of(
                    CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                    CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusMillis(500));
    private static final String NAME = "test_ca_name";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidRequest_success() {
        final SetAdCounterHistogramOverrideRequest originalRequest =
                new SetAdCounterHistogramOverrideRequest.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        assertThat(originalRequest.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        assertThat(originalRequest.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(originalRequest.getHistogramTimestamps()).isEqualTo(HISTOGRAM_TIMESTAMPS);
        assertThat(originalRequest.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(originalRequest.getCustomAudienceOwner())
                .isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(originalRequest.getCustomAudienceName()).isEqualTo(NAME);
    }

    @Test
    public void testToString() {
        final SetAdCounterHistogramOverrideRequest originalRequest =
                new SetAdCounterHistogramOverrideRequest.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        final String expected =
                String.format(
                        "SetAdCounterHistogramOverrideRequest{mAdEventType=%d, mAdCounterKey=%d,"
                                + " mHistogramTimestamps=%s, mBuyer=%s, mCustomAudienceOwner='%s',"
                                + " mCustomAudienceName='%s'}",
                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                        KeyedFrequencyCapFixture.KEY1,
                        HISTOGRAM_TIMESTAMPS,
                        CommonFixture.VALID_BUYER_1,
                        CommonFixture.TEST_PACKAGE_NAME,
                        NAME);

        assertThat(originalRequest.toString()).isEqualTo(expected);
    }

    @Test
    public void testSetNullHistogramTimestamps_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setHistogramTimestamps(null));
    }

    @Test
    public void testSetNullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new SetAdCounterHistogramOverrideRequest.Builder().setBuyer(null));
    }

    @Test
    public void testSetNullCustomAudienceOwner_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setCustomAudienceOwner(null));
    }

    @Test
    public void testSetNullCustomAudienceName_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setCustomAudienceName(null));
    }

    @Test
    public void testBuildUnsetAdEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetAdCounterKey_success() {
        final SetAdCounterHistogramOverrideRequest originalRequest =
                new SetAdCounterHistogramOverrideRequest.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        assertThat(originalRequest.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        assertThat(originalRequest.getAdCounterKey()).isEqualTo(0);
        assertThat(originalRequest.getHistogramTimestamps()).isEqualTo(HISTOGRAM_TIMESTAMPS);
        assertThat(originalRequest.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(originalRequest.getCustomAudienceOwner())
                .isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        assertThat(originalRequest.getCustomAudienceName()).isEqualTo(NAME);
    }

    @Test
    public void testBuildUnsetHistogramTimestamps_success() {
        SetAdCounterHistogramOverrideRequest originalRequest =
                new SetAdCounterHistogramOverrideRequest.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        assertThat(originalRequest.getHistogramTimestamps()).isEmpty();
    }

    @Test
    public void testBuildUnsetBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceOwner_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceName_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideRequest.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .build());
    }
}
