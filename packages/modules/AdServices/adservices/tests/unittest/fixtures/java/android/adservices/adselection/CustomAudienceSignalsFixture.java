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

package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.data.adselection.CustomAudienceSignals;

public class CustomAudienceSignalsFixture {
    public static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("test.com");

    public static CustomAudienceSignals aCustomAudienceSignals() {
        return aCustomAudienceSignalsBuilder().build();
    }

    public static CustomAudienceSignals.Builder aCustomAudienceSignalsBuilder() {
        return new CustomAudienceSignals.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }
}
