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

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceOverrideRequestTest {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCreateAddCustomAudienceOverrideRequestSuccess() {
        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest(
                        BUYER, NAME, BIDDING_LOGIC_JS, TRUSTED_BIDDING_DATA);

        assertEquals(request.getBuyer(), BUYER);
        assertEquals(request.getName(), NAME);
        assertEquals(request.getBiddingLogicJs(), BIDDING_LOGIC_JS);
        assertEquals(request.getTrustedBiddingSignals(), TRUSTED_BIDDING_DATA);
    }

    @Test
    public void testBuildAddCustomAudienceOverrideRequest_biddingLogicVersionNotSet_Success() {
        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_DATA)
                        .build();

        assertEquals(request.getBuyer(), BUYER);
        assertEquals(request.getName(), NAME);
        assertEquals(request.getBiddingLogicJs(), BIDDING_LOGIC_JS);
        assertEquals(request.getTrustedBiddingSignals(), TRUSTED_BIDDING_DATA);
    }

    @Test
    public void testCreateRemoveCustomAudienceOverrideRequestSuccess() {
        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest(BUYER, NAME);

        assertEquals(request.getBuyer(), BUYER);
        assertEquals(request.getName(), NAME);
    }

    @Test
    public void testBuildRemoveCustomAudienceOverrideRequestSuccess() {
        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        assertEquals(request.getBuyer(), BUYER);
        assertEquals(request.getName(), NAME);
    }
}
