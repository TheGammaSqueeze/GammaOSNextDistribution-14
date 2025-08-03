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

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer;

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.TrustedBiddingDataValidator.TRUSTED_BIDDING_DATA_CLASS_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class TrustedBiddingDataValidatorTest {
    public static final int CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB();
    private TrustedBiddingDataValidator mValidator =
            new TrustedBiddingDataValidator(CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetValidationViolation_nullTrustedBiddingData_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(null, VALID_BUYER_1));
    }

    @Test
    public void testGetValidationViolation_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.getValidationViolations(
                                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1), null));
    }

    @Test
    public void testAddValidation_nullTrustedBiddingData_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, VALID_BUYER_1, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1),
                                null,
                                new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1),
                                VALID_BUYER_1,
                                null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid trustedBiddingDatadoes not throw.
        mValidator.validate(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1), VALID_BUYER_1);
    }

    @Test
    public void testValidator_malformedUri() {
        // Construct trustedBiddingData with mismatched buyer.
        TrustedBiddingData invalidTrustedBiddingData =
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_2);

        // Assert buyer mismatch causes failure.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidator.validate(invalidTrustedBiddingData, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                TRUSTED_BIDDING_DATA_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                AdTechUriValidator
                                                        .IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                                VALID_BUYER_1,
                                                ValidatorUtil.AD_TECH_ROLE_BUYER,
                                                TrustedBiddingDataValidator
                                                        .TRUSTED_BIDDING_URI_FIELD_NAME,
                                                VALID_BUYER_2))));
    }

    @Test
    public void testTrustedBiddingDataTooBig() {
        // Use a validator with a clearly small size limit.
        TrustedBiddingDataValidator mValidatorWithSmallLimit = new TrustedBiddingDataValidator(1);

        // Constructor a valid instance of TrustedBiddingData which will now be too big for the
        // validator.
        TrustedBiddingData tooBigTrustedBiddingData =
                new TrustedBiddingData.Builder()
                        .setTrustedBiddingKeys(List.of())
                        .setTrustedBiddingUri(
                                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(VALID_BUYER_1))
                        .build();

        // Assert buyer mismatch causes failure.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidatorWithSmallLimit.validate(
                                        tooBigTrustedBiddingData, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                TRUSTED_BIDDING_DATA_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                CustomAudienceFieldSizeValidator
                                                        .VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                                                1,
                                                DBTrustedBiddingData.fromServiceObject(
                                                                tooBigTrustedBiddingData)
                                                        .size()))));
    }
}
