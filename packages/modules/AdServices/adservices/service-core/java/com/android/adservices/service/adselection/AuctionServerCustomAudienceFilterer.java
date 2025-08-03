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

package com.android.adservices.service.adselection;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.base.Strings;

import java.util.Objects;

/** Class to filter CustomAudience for server-side auctions. */
public class AuctionServerCustomAudienceFilterer {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    /** Returns true if the given customAudience is valid for server side auction. */
    public static boolean isValidCustomAudienceForServerSideAuction(
            DBCustomAudience dbCustomAudience) {
        sLogger.v(
                "Checking Auction Server eligibility for Custom Audience %s",
                dbCustomAudience.getName());

        if (dbCustomAudience.getTrustedBiddingData() == null) {
            sLogger.v(
                    "Trusted bidding data is required for server auction but it's 'null' for '%s'",
                    dbCustomAudience.getName());
            return false;
        }

        if (dbCustomAudience.getTrustedBiddingData().getKeys().stream()
                .allMatch(Strings::isNullOrEmpty)) {
            sLogger.v(
                    "At least one trusted bidding keys should be non-empty and non-null strings but"
                            + " no keys are for '%s'",
                    dbCustomAudience.getName());
            return false;
        }

        if (Objects.isNull(dbCustomAudience.getAds()) || dbCustomAudience.getAds().isEmpty()) {
            sLogger.v(
                    "At least one ad should be present but no ads are found for %s",
                    dbCustomAudience.getName());
            return false;
        }

        if (dbCustomAudience.getAds().stream()
                .allMatch(ad -> Strings.isNullOrEmpty(ad.getAdRenderId()))) {
            sLogger.v(
                    "At least one ad should have should have a non-null and non-empty ad render id"
                            + " but none present for '%s'",
                    dbCustomAudience.getName());
            return false;
        }

        sLogger.v("Custom Audience %s is eligible for Server Auction", dbCustomAudience.getName());
        return true;
    }
}
