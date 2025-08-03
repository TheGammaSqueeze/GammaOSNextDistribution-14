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

package com.android.adservices.service.customaudience;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.annotation.NonNull;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Validator for Custom Audience fields size. */
public class CustomAudienceFieldSizeValidator implements Validator<CustomAudience> {
    @VisibleForTesting
    public static final String VIOLATION_NAME_TOO_LONG =
            "Custom audience name should not exceed %d byte(s), the provided data size is %d"
                    + " byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_DAILY_UPDATE_URI_TOO_LONG =
            "Custom audience daily update uri should not exceed %d byte(s), the provided data size"
                    + " is %d byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_BIDDING_LOGIC_URI_TOO_LONG =
            "Custom audience bidding logic uri should not exceed %d byte(s), the provided data"
                    + " size is %d byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG =
            "Custom audience user bidding signal size should not exceed %d byte(s), the provided"
                    + " data size is %d byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG =
            "Custom audience trusted bidding data size should not exceed %d byte(s), the provided"
                    + " data size is %d byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_TOTAL_ADS_SIZE_TOO_BIG =
            "Single custom audience total ads size should not exceed %d byte(s), the provided data"
                    + " size is %d byte(s).";

    @VisibleForTesting
    public static final String VIOLATION_TOTAL_ADS_COUNT_TOO_BIG =
            "Single custom audience ads count should not exceed %d, the provided data size is %d";

    @NonNull private final Flags mFlags;
    // Process stable flag value.
    private final boolean mIsFilteringEnabled;
    private final boolean mIsAdRenderIdEnabled;

    public CustomAudienceFieldSizeValidator(@NonNull Flags flags) {
        Objects.requireNonNull(flags);

        mFlags = flags;
        mIsFilteringEnabled = mFlags.getFledgeAdSelectionFilteringEnabled();
        mIsAdRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
    }

    /**
     * Validates the custom audience fields size.
     *
     * @param customAudience the instance to be validated.
     */
    @Override
    public void addValidation(
            @NonNull CustomAudience customAudience,
            @NonNull ImmutableCollection.Builder<String> violations) {

        int nameSize = customAudience.getName().getBytes().length;
        if (nameSize > mFlags.getFledgeCustomAudienceMaxNameSizeB()) {
            violations.add(
                    String.format(
                            Locale.getDefault(),
                            VIOLATION_NAME_TOO_LONG,
                            mFlags.getFledgeCustomAudienceMaxNameSizeB(),
                            nameSize));
        }

        int dailyUpdateUriSize = customAudience.getDailyUpdateUri().toString().getBytes().length;
        if (dailyUpdateUriSize > mFlags.getFledgeCustomAudienceMaxDailyUpdateUriSizeB()) {
            violations.add(
                    String.format(
                            Locale.getDefault(),
                            VIOLATION_DAILY_UPDATE_URI_TOO_LONG,
                            mFlags.getFledgeCustomAudienceMaxDailyUpdateUriSizeB(),
                            dailyUpdateUriSize));
        }

        int biddingLogicUriSize = customAudience.getBiddingLogicUri().toString().getBytes().length;
        if (biddingLogicUriSize > mFlags.getFledgeCustomAudienceMaxBiddingLogicUriSizeB()) {
            violations.add(
                    String.format(
                            Locale.getDefault(),
                            VIOLATION_BIDDING_LOGIC_URI_TOO_LONG,
                            mFlags.getFledgeCustomAudienceMaxBiddingLogicUriSizeB(),
                            biddingLogicUriSize));
        }

        int userBiddingSignalSize =
                getUserBiddingSignalsSize(customAudience.getUserBiddingSignals());
        if (userBiddingSignalSize > mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB()) {
            violations.add(
                    String.format(
                            Locale.getDefault(),
                            VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                            mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                            userBiddingSignalSize));
        }

        int trustedBiddingDataSize =
                getTrustedBiddingDataSize(customAudience.getTrustedBiddingData());
        if (trustedBiddingDataSize > mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB()) {
            violations.add(
                    String.format(
                            Locale.getDefault(),
                            VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                            mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                            trustedBiddingDataSize));
        }

        final List<AdData> ads = customAudience.getAds();
        if (ads != null) {
            int adsSize = getAdsSize(ads);
            if (adsSize > mFlags.getFledgeCustomAudienceMaxAdsSizeB()) {
                violations.add(
                        String.format(
                                Locale.getDefault(),
                                VIOLATION_TOTAL_ADS_SIZE_TOO_BIG,
                                mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                                adsSize));
            }
            if (ads.size() > mFlags.getFledgeCustomAudienceMaxNumAds()) {
                violations.add(
                        String.format(
                                Locale.getDefault(),
                                VIOLATION_TOTAL_ADS_COUNT_TOO_BIG,
                                mFlags.getFledgeCustomAudienceMaxNumAds(),
                                ads.size()));
            }
        }
    }

    private int getUserBiddingSignalsSize(AdSelectionSignals userBiddingSignals) {
        if (userBiddingSignals == null) {
            return 0;
        }
        return userBiddingSignals.toString().getBytes().length;
    }

    private int getTrustedBiddingDataSize(TrustedBiddingData trustedBiddingData) {
        if (trustedBiddingData == null) {
            return 0;
        }

        return DBTrustedBiddingData.fromServiceObject(trustedBiddingData).size();
    }

    private int getAdsSize(List<AdData> ads) {
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                        mIsFilteringEnabled, mIsAdRenderIdEnabled);
        return ads.stream()
                .map(ad -> adDataConversionStrategy.fromServiceObject(ad).build())
                .mapToInt(DBAdData::size)
                .sum();
    }
}
