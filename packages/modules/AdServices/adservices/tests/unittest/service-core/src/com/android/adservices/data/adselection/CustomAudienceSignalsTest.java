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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;

import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceSignalsTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildCustomAudienceSignals() {
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        assertEquals(CustomAudienceFixture.VALID_OWNER, customAudienceSignals.getOwner());
        assertEquals(CommonFixture.VALID_BUYER_1, customAudienceSignals.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, customAudienceSignals.getName());
        assertEquals(
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                customAudienceSignals.getActivationTime());
        assertEquals(
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                customAudienceSignals.getExpirationTime());
        assertEquals(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                customAudienceSignals.getUserBiddingSignals());
    }

    @Test
    public void testBuildFromCustomAudience() {
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(
                        DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                                .build());

        assertEquals(CustomAudienceFixture.VALID_OWNER, customAudienceSignals.getOwner());
        assertEquals(CommonFixture.VALID_BUYER_1, customAudienceSignals.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, customAudienceSignals.getName());
        assertEquals(
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                customAudienceSignals.getActivationTime());
        assertEquals(
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                customAudienceSignals.getExpirationTime());
        assertEquals(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                customAudienceSignals.getUserBiddingSignals());
    }

    @Test
    public void testEqualCustomAudienceSignalsHaveSameHashCode() {
        CustomAudienceSignals obj1 =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("the-same-name")
                        .build();
        CustomAudienceSignals obj2 =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("the-same-name")
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualCustomAudienceSignalsHaveDifferentHashCodes() {
        CustomAudienceSignals obj1 =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("a-name")
                        .build();
        CustomAudienceSignals obj2 =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("a-different-name")
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2);
    }
}
