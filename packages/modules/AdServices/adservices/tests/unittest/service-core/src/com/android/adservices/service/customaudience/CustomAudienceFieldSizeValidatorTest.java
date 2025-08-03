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

import static com.android.adservices.service.common.ValidatorUtil.HTTPS_SCHEME;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.ValidatorTestUtil;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomAudienceFieldSizeValidatorTest {
    private static final Flags FLAGS = CommonFixture.FLAGS_FOR_TEST;

    private CustomAudienceFieldSizeValidator mValidator =
            new CustomAudienceFieldSizeValidator(FLAGS);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testNameTooLong() {
        String tooLongName = getStringWithLength(FLAGS.getFledgeCustomAudienceMaxNameSizeB() * 2);
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(tooLongName)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG,
                        FLAGS.getFledgeCustomAudienceMaxNameSizeB(),
                        tooLongName.getBytes().length));
    }

    @Test
    public void testBiddingLogicUriTooLong() {
        Uri tooLongBiddingLogicUri =
                getUriWithPathLength(
                        CommonFixture.VALID_BUYER_1,
                        FLAGS.getFledgeCustomAudienceMaxBiddingLogicUriSizeB());
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setBiddingLogicUri(tooLongBiddingLogicUri)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_BIDDING_LOGIC_URI_TOO_LONG,
                        FLAGS.getFledgeCustomAudienceMaxBiddingLogicUriSizeB(),
                        tooLongBiddingLogicUri.toString().getBytes().length));
    }

    @Test
    public void testDailyUpdateUriTooLong() {
        Uri tooLongDailyUpdateUri =
                getUriWithPathLength(
                        CommonFixture.VALID_BUYER_1,
                        FLAGS.getFledgeCustomAudienceMaxDailyUpdateUriSizeB());
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setDailyUpdateUri(tooLongDailyUpdateUri)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_DAILY_UPDATE_URI_TOO_LONG,
                        FLAGS.getFledgeCustomAudienceMaxDailyUpdateUriSizeB(),
                        tooLongDailyUpdateUri.toString().getBytes().length));
    }

    @Test
    public void testUserBiddingSignalsTooBig() {
        AdSelectionSignals tooBigUserBiddingSignals =
                getAdSelectionSignalsWithLength(
                        FLAGS.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() * 2);
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setUserBiddingSignals(tooBigUserBiddingSignals)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                        FLAGS.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        tooBigUserBiddingSignals.toString().getBytes().length));
    }

    @Test
    public void testTrustedBiddingDataTooBig() {
        TrustedBiddingData tooBigTrustedBiddingData =
                new TrustedBiddingData.Builder()
                        .setTrustedBiddingKeys(List.of())
                        .setTrustedBiddingUri(
                                getUriWithPathLength(
                                        CommonFixture.VALID_BUYER_1,
                                        FLAGS.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB()))
                        .build();
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setTrustedBiddingData(tooBigTrustedBiddingData)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                        FLAGS.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        DBTrustedBiddingData.fromServiceObject(tooBigTrustedBiddingData).size()));
    }

    @Test
    public void testAdsTooBig() {
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
        List<AdData> tooBigAds =
                List.of(
                        new AdData.Builder()
                                .setRenderUri(getUriWithPathLength(CommonFixture.VALID_BUYER_1, 20))
                                .setMetadata(
                                        getAdSelectionSignalsWithLength(
                                                        FLAGS.getFledgeCustomAudienceMaxAdsSizeB())
                                                .toString())
                                .build());
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(tooBigAds)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_SIZE_TOO_BIG,
                        FLAGS.getFledgeCustomAudienceMaxAdsSizeB(),
                        tooBigAds.stream()
                                .map(obj -> adDataConversionStrategy.fromServiceObject(obj).build())
                                .mapToInt(DBAdData::size)
                                .sum()));
    }

    @Test
    public void testAdsCountTooBig() {
        List<AdData> tooManyAds = new ArrayList<>();
        for (int i = 0; i < FLAGS.getFledgeCustomAudienceMaxNumAds() + 2; i++) {
            tooManyAds.add(
                    new AdData.Builder()
                            .setMetadata(AdSelectionSignals.EMPTY.toString())
                            .setRenderUri(getUriWithPathLength(CommonFixture.VALID_BUYER_1, 1))
                            .build());
        }
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(tooManyAds)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_COUNT_TOO_BIG,
                        FLAGS.getFledgeCustomAudienceMaxNumAds(),
                        tooManyAds.size()));
    }

    private String getStringWithLength(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    private Uri getUriWithPathLength(AdTechIdentifier buyer, int pathLength) {
        return Uri.parse(HTTPS_SCHEME + "://" + buyer + "/" + getStringWithLength(pathLength));
    }

    private AdSelectionSignals getAdSelectionSignalsWithLength(int length) {
        return AdSelectionSignals.fromString(
                String.format("{\"a\":\"%s\"}", getStringWithLength(length - 8)));
    }
}
