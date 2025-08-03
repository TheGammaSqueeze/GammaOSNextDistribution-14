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

package com.android.adservices.service.customaudience;

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;

import static com.android.adservices.service.common.AdDataValidator.AD_DATA_CLASS_NAME;
import static com.android.adservices.service.common.AdDataValidator.METADATA_FIELD_NAME;
import static com.android.adservices.service.common.AdDataValidator.RENDER_URI_FIELD_NAME;
import static com.android.adservices.service.common.AdDataValidator.VIOLATION_FORMAT;
import static com.android.adservices.service.common.AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT;
import static com.android.adservices.service.common.JsonValidator.SHOULD_BE_A_VALID_JSON;
import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_COUNT_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_SIZE_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorImpl;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class CustomAudienceAdsValidatorTest {
    public static final int CUSTOM_AUDIENCE_MAX_ADS_SIZE_B =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxAdsSizeB();

    public static final int CUSTOM_AUDIENCE_MAX_NUM_ADS =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxNumAds();

    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator =
            new FrequencyCapAdDataValidatorImpl();

    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    private final CustomAudienceAdsValidator mValidator =
            new CustomAudienceAdsValidator(
                    mFrequencyCapAdDataValidator,
                    mAdRenderIdValidator,
                    AD_DATA_CONVERSION_STRATEGY,
                    CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                    CUSTOM_AUDIENCE_MAX_NUM_ADS);

    private final List<AdData> mValidAds = AdDataFixture.getValidFilterAdsByBuyer(VALID_BUYER_1);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConstructor_nullFrequencyCapAdDataValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceAdsValidator(
                                null,
                                mAdRenderIdValidator,
                                AD_DATA_CONVERSION_STRATEGY,
                                CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                                CUSTOM_AUDIENCE_MAX_NUM_ADS));
    }

    @Test
    public void testConstructor_nullAdRenderIdValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceAdsValidator(
                                mFrequencyCapAdDataValidator,
                                null,
                                AD_DATA_CONVERSION_STRATEGY,
                                CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                                CUSTOM_AUDIENCE_MAX_NUM_ADS));
    }

    @Test
    public void testValidate_nullAds_throws() {
        assertThrows(NullPointerException.class, () -> mValidator.validate(null, VALID_BUYER_1));
    }

    @Test
    public void testValidate_nullBuyer_throws() {
        assertThrows(NullPointerException.class, () -> mValidator.validate(mValidAds, null));
    }

    @Test
    public void testGetValidationViolations_nullAds_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(null, VALID_BUYER_1));
    }

    @Test
    public void testGetValidationViolations_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(mValidAds, null));
    }

    @Test
    public void testAddValidation_nullAds_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, VALID_BUYER_1, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(mValidAds, null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(mValidAds, VALID_BUYER_1, null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid ads do not throw.
        mValidator.validate(mValidAds, VALID_BUYER_1);
    }

    @Test
    public void testValidator_malformedAds() {
        AdData invalidAdDataWithAnotherBuyer =
                new AdData.Builder()
                        .setRenderUri(AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_2, 1))
                        .setMetadata("{\"a\":1}")
                        .build();
        AdData invalidAdDataWithInvalidMetadata =
                new AdData.Builder()
                        .setRenderUri(AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_1, 2))
                        .setMetadata("not[valid]json")
                        .build();
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_1, 3))
                        .setMetadata("{\"a\":1}")
                        .build();

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        List.of(
                                                invalidAdDataWithAnotherBuyer,
                                                invalidAdDataWithInvalidMetadata,
                                                validAdData),
                                        VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CUSTOM_AUDIENCE_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_FORMAT,
                                                invalidAdDataWithAnotherBuyer,
                                                String.format(
                                                        Locale.ENGLISH,
                                                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                                        AD_TECH_ROLE_BUYER,
                                                        VALID_BUYER_1,
                                                        AD_TECH_ROLE_BUYER,
                                                        RENDER_URI_FIELD_NAME,
                                                        VALID_BUYER_2)),
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_FORMAT,
                                                invalidAdDataWithInvalidMetadata,
                                                String.format(
                                                        Locale.ENGLISH,
                                                        SHOULD_BE_A_VALID_JSON,
                                                        AD_DATA_CLASS_NAME,
                                                        METADATA_FIELD_NAME)))));
    }

    @Test
    public void testValidator_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceAdsValidator mValidatorWithSmallLimit =
                new CustomAudienceAdsValidator(
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        1,
                        CUSTOM_AUDIENCE_MAX_NUM_ADS);

        // Constructor valid ads which will now be too big for the validator.
        List<AdData> tooBigAds = mValidAds;

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidatorWithSmallLimit.validate(tooBigAds, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CUSTOM_AUDIENCE_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_TOTAL_ADS_SIZE_TOO_BIG,
                                                1,
                                                tooBigAds.stream()
                                                        .map(
                                                                obj ->
                                                                        AD_DATA_CONVERSION_STRATEGY
                                                                                .fromServiceObject(
                                                                                        obj)
                                                                                .build())
                                                        .mapToInt(DBAdData::size)
                                                        .sum()))));
    }

    @Test
    public void testValidator_exceedsCountLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceAdsValidator mValidatorWithSmallLimit =
                new CustomAudienceAdsValidator(
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                        1);

        // Constructor valid ads which will now be too big for the validator.
        List<AdData> tooManyAds = mValidAds;

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidatorWithSmallLimit.validate(tooManyAds, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CUSTOM_AUDIENCE_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_TOTAL_ADS_COUNT_TOO_BIG,
                                                1,
                                                tooManyAds.size()))));
    }
}
