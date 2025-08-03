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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdDataValidator;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorImpl;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.JsonValidator;
import com.android.adservices.service.common.ValidatorTestUtil;
import com.android.adservices.service.common.ValidatorUtil;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class CustomAudienceValidatorTest {
    private static final AdTechIdentifier ANOTHER_BUYER = AdTechIdentifier.fromString("b.com");

    private final CustomAudienceValidator mValidator =
            new CustomAudienceValidator(
                    CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                    FlagsFactory.getFlagsForTest(),
                    new FrequencyCapAdDataValidatorNoOpImpl(),
                    AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testValidCustomAudience() {
        Assert.assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .isEmpty());
    }

    @Test
    public void testValidCustomAudienceWithNullFields() {
        Assert.assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setTrustedBiddingData(null)
                                        .setUserBiddingSignals(null)
                                        .build())
                        .isEmpty());
    }

    @Test
    public void testInvalidBuyer() {
        AdTechIdentifier buyerWithPath =
                AdTechIdentifier.fromString(CommonFixture.VALID_BUYER_1.toString() + "/path");
        List<AdData> adDataList = AdDataFixture.getValidAdsByBuyer(buyerWithPath);

        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(buyerWithPath).build()),
                String.format(
                        AdTechIdentifierValidator.IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME,
                        ValidatorUtil.AD_TECH_ROLE_BUYER),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CustomAudienceValidator.DAILY_UPDATE_URI_FIELD_NAME,
                        CommonFixture.VALID_BUYER_1),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CustomAudienceValidator.BIDDING_LOGIC_URI_FIELD_NAME,
                        CommonFixture.VALID_BUYER_1),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        TrustedBiddingDataValidator.TRUSTED_BIDDING_URI_FIELD_NAME,
                        CommonFixture.VALID_BUYER_1),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adDataList.get(0),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                buyerWithPath.toString(),
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                CommonFixture.VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adDataList.get(1),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                buyerWithPath.toString(),
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                CommonFixture.VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adDataList.get(2),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                buyerWithPath.toString(),
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                CommonFixture.VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        adDataList.get(3),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                CommonFixture.VALID_BUYER_1)));
    }

    @Test
    public void testInvalidDailyUpdateUriAndBiddingLogicUri() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setDailyUpdateUri(
                                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                                ANOTHER_BUYER))
                                .setBiddingLogicUri(
                                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                                ANOTHER_BUYER))
                                .build()),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CommonFixture.VALID_BUYER_1,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CustomAudienceValidator.DAILY_UPDATE_URI_FIELD_NAME,
                        ANOTHER_BUYER),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CommonFixture.VALID_BUYER_1,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CustomAudienceValidator.BIDDING_LOGIC_URI_FIELD_NAME,
                        ANOTHER_BUYER));
    }

    @Test
    public void testInvalidTrustedBiddingData() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setTrustedBiddingData(
                                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                                ANOTHER_BUYER))
                                .build()),
                String.format(
                        AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        CommonFixture.VALID_BUYER_1,
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        TrustedBiddingDataValidator.TRUSTED_BIDDING_URI_FIELD_NAME,
                        ANOTHER_BUYER));
    }

    @Test
    public void testInvalidUserBiddingSignals() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setUserBiddingSignals(
                                        AdSelectionSignals.fromString("Not[A]VALID[JSON]"))
                                .build()),
                String.format(
                        JsonValidator.SHOULD_BE_A_VALID_JSON,
                        CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME,
                        CustomAudienceValidator.USER_BIDDING_SIGNALS_FIELD_NAME));
    }

    @Test
    public void testActivationTimeBeyondMaxAndExpireBeforeActivation() {

        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setActivationTime(
                                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME)
                                .build()),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                        CustomAudienceFixture.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME));
    }

    @Test
    public void testExpirationTimeBeforeNow() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setExpirationTime(
                                        CustomAudienceFixture.INVALID_BEFORE_NOW_EXPIRATION_TIME)
                                .build()),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_CURRENT_TIME,
                        CustomAudienceFixture.INVALID_BEFORE_NOW_EXPIRATION_TIME));
    }

    @Test
    public void testExpirationTimeBeforeActivation() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setExpirationTime(
                                        CustomAudienceFixture
                                                .INVALID_BEFORE_DELAYED_EXPIRATION_TIME)
                                .setActivationTime(
                                        CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                                .build()),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                        CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_BEFORE_DELAYED_EXPIRATION_TIME));
    }

    @Test
    public void testInvalidAdData() {
        AdData invalidAdDataWithAnotherBuyer =
                new AdData.Builder()
                        .setRenderUri(AdDataFixture.getValidRenderUriByBuyer(ANOTHER_BUYER, 1))
                        .setMetadata("{\"a\":1}")
                        .build();
        AdData invalidAdDataWithInvalidMetadata =
                new AdData.Builder()
                        .setRenderUri(
                                AdDataFixture.getValidRenderUriByBuyer(
                                        CommonFixture.VALID_BUYER_1, 2))
                        .setMetadata("not[valid]json")
                        .build();
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(
                                AdDataFixture.getValidRenderUriByBuyer(
                                        CommonFixture.VALID_BUYER_1, 3))
                        .setMetadata("{\"a\":1}")
                        .build();
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(
                                        List.of(
                                                invalidAdDataWithAnotherBuyer,
                                                invalidAdDataWithInvalidMetadata,
                                                validAdData))
                                .build()),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        invalidAdDataWithAnotherBuyer,
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                CommonFixture.VALID_BUYER_1,
                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                AdDataValidator.RENDER_URI_FIELD_NAME,
                                ANOTHER_BUYER)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        invalidAdDataWithInvalidMetadata,
                        String.format(
                                JsonValidator.SHOULD_BE_A_VALID_JSON,
                                AdDataValidator.AD_DATA_CLASS_NAME,
                                AdDataValidator.METADATA_FIELD_NAME)));
    }

    @Test
    public void testNameTooLong() {
        String tooLongName =
                "This is a super long name.This is a super long name.This is a super long name.This"
                    + " is a super long name.This is a super long name.This is a super long"
                    + " name.This is a super long name.This is a super long name.This is a super"
                    + " long name.This is a super long name.";
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(tooLongName)
                                .build()),
                String.format(
                        Locale.getDefault(),
                        CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG,
                        CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxNameSizeB(),
                        tooLongName.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    public void testExceededFrequencyCapLimits() {
        AdData adDataWithExceededFrequencyCapLimits =
                AdDataFixture.getAdDataWithExceededFrequencyCapLimits(
                        CommonFixture.VALID_BUYER_1, 0);

        CustomAudienceValidator validator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        FlagsFactory.getFlagsForTest(),
                        new FrequencyCapAdDataValidatorImpl(),
                        AdRenderIdValidator.createEnabledInstance(100));

        List<String> expectedViolations =
                List.of(
                        String.format(
                                Locale.ENGLISH,
                                "For %s, AdData should have no more than 10 ad counter keys",
                                adDataWithExceededFrequencyCapLimits),
                        String.format(
                                Locale.ENGLISH,
                                "For %s, FrequencyCapFilters should have no more than 20 filters",
                                adDataWithExceededFrequencyCapLimits));

        assertThat(
                        validator.getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(List.of(adDataWithExceededFrequencyCapLimits))
                                        .build()))
                .containsExactlyElementsIn(expectedViolations);
    }

    @Test
    public void testInvalidRenderId() {
        AdData adDataWithInvalidRenderId =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdRenderId("0123456789")
                        .build();

        CustomAudienceValidator validator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        FlagsFactory.getFlagsForTest(),
                        new FrequencyCapAdDataValidatorImpl(),
                        AdRenderIdValidator.createEnabledInstance(5));

        assertThat(
                        validator.getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(List.of(adDataWithInvalidRenderId))
                                        .build()))
                .isNotEmpty();
    }
}
