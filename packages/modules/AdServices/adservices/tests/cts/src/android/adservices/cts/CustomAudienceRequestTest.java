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

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.JoinCustomAudienceRequest;
import android.adservices.customaudience.LeaveCustomAudienceRequest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceRequestTest {
    private static final CustomAudience CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();
    private static final CustomAudience CUSTOM_AUDIENCE_OMIT_ADS =
            CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                            VALID_BUYER_1, FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                    .build();
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String NAME = CustomAudienceFixture.VALID_NAME;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildJoinCustomAudienceRequestSuccess() {
        JoinCustomAudienceRequest request =
                new JoinCustomAudienceRequest.Builder().setCustomAudience(CUSTOM_AUDIENCE).build();

        assertEquals(request.getCustomAudience(), CUSTOM_AUDIENCE);
    }

    @Test
    public void testBuildJoinCustomAudienceRequestSuccessWithAuctionServerRequestFlags() {
        JoinCustomAudienceRequest request =
                new JoinCustomAudienceRequest.Builder()
                        .setCustomAudience(CUSTOM_AUDIENCE_OMIT_ADS)
                        .build();

        assertEquals(request.getCustomAudience(), CUSTOM_AUDIENCE_OMIT_ADS);
    }

    @Test
    public void testBuildLeaveCustomAudienceRequestSuccess() {
        LeaveCustomAudienceRequest request =
                new LeaveCustomAudienceRequest.Builder().setBuyer(BUYER).setName(NAME).build();

        assertEquals(request.getBuyer(), BUYER);
        assertEquals(request.getName(), NAME);
    }
}
