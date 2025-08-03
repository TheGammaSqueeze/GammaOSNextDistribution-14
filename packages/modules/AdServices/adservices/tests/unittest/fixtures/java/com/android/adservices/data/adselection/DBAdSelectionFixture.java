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

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.customaudience.DBCustomAudienceFixture;

public final class DBAdSelectionFixture {
    public static final long VALID_AD_SELECTION_ID = 99;
    public static final String EMPTY_SIGNALS = "{}";
    public static final double VALID_BID = 1.5;

    public static DBAdSelection.Builder getValidDbAdSelectionBuilder() {
        return new DBAdSelection.Builder()
                .setAdSelectionId(VALID_AD_SELECTION_ID)
                .setCustomAudienceSignals(
                        CustomAudienceSignals.buildFromCustomAudience(
                                DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS))
                .setBuyerContextualSignals(EMPTY_SIGNALS)
                .setSellerContextualSignals(EMPTY_SIGNALS)
                .setBiddingLogicUri(
                        CommonFixture.getUri(
                                DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS
                                        .getBuyer(),
                                /* path= */ "/bidding"))
                .setWinningAdRenderUri(
                        CommonFixture.getUri(
                                DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS
                                        .getBuyer(),
                                /* path= */ "/bidding"))
                .setWinningAdBid(VALID_BID)
                .setCreationTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys());
    }
}
