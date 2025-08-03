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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceStatsTest {
    private static final long TOTAL_CUSTOM_AUDIENCE_COUNT = 1L;
    private static final long PER_OWNER_CUSTOM_AUDIENCE_COUNT = 2L;
    private static final long TOTAL_OWNER_COUNT = 3L;
    private static final long PER_BUYER_CUSTOM_AUDIENCE_COUNT = 4L;
    private static final long TOTAL_BUYER_COUNT = 5L;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDefault() {
        CustomAudienceStats stats = CustomAudienceStats.builder().build();

        assertThat(stats).isNotNull();
        assertThat(stats.getOwner()).isNull();
        assertThat(stats.getBuyer()).isNull();
        assertThat(stats.getTotalCustomAudienceCount()).isEqualTo(CustomAudienceStats.UNSET_COUNT);
        assertThat(stats.getPerOwnerCustomAudienceCount())
                .isEqualTo(CustomAudienceStats.UNSET_COUNT);
        assertThat(stats.getTotalOwnerCount()).isEqualTo(CustomAudienceStats.UNSET_COUNT);
        assertThat(stats.getPerBuyerCustomAudienceCount())
                .isEqualTo(CustomAudienceStats.UNSET_COUNT);
        assertThat(stats.getTotalBuyerCount()).isEqualTo(CustomAudienceStats.UNSET_COUNT);
    }

    @Test
    public void testBuildWithValues() {
        CustomAudienceStats stats =
                CustomAudienceStats.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setTotalCustomAudienceCount(TOTAL_CUSTOM_AUDIENCE_COUNT)
                        .setPerOwnerCustomAudienceCount(PER_OWNER_CUSTOM_AUDIENCE_COUNT)
                        .setTotalOwnerCount(TOTAL_OWNER_COUNT)
                        .setPerBuyerCustomAudienceCount(PER_BUYER_CUSTOM_AUDIENCE_COUNT)
                        .setTotalBuyerCount(TOTAL_BUYER_COUNT)
                        .build();

        assertThat(stats).isNotNull();
        assertThat(stats.getOwner()).isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(stats.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(stats.getTotalCustomAudienceCount()).isEqualTo(TOTAL_CUSTOM_AUDIENCE_COUNT);
        assertThat(stats.getPerOwnerCustomAudienceCount())
                .isEqualTo(PER_OWNER_CUSTOM_AUDIENCE_COUNT);
        assertThat(stats.getTotalOwnerCount()).isEqualTo(TOTAL_OWNER_COUNT);
        assertThat(stats.getPerBuyerCustomAudienceCount())
                .isEqualTo(PER_BUYER_CUSTOM_AUDIENCE_COUNT);
        assertThat(stats.getTotalBuyerCount()).isEqualTo(TOTAL_BUYER_COUNT);
    }
}
