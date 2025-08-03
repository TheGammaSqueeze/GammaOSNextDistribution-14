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

import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.USER_BIDDING_SIGNALS_FIELD_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.common.JsonValidator;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.util.Locale;

public class CustomAudienceUserBiddingSignalsValidatorTest {
    public static final int CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB();

    private final JsonValidator mJsonValidator =
            new JsonValidator(CUSTOM_AUDIENCE_CLASS_NAME, USER_BIDDING_SIGNALS_FIELD_NAME);
    private final CustomAudienceUserBiddingSignalsValidator mValidator =
            new CustomAudienceUserBiddingSignalsValidator(
                    mJsonValidator, CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConstructor_nullJsonValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceUserBiddingSignalsValidator(
                                null, CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B));
    }

    @Test
    public void testAddValidation_nullUserBiddingSignals_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(VALID_USER_BIDDING_SIGNALS, null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid user bidding signals does not throw.
        mValidator.validate(VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testValidator_malformedJson() {
        // Construct invalid user bidding signals.
        AdSelectionSignals invalidUserBiddingSignals =
                AdSelectionSignals.fromString("Not[A]VALID[JSON]");

        // Assert invalid user bidding signals throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidator.validate(invalidUserBiddingSignals));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                invalidUserBiddingSignals.getClass().getName(),
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                JsonValidator.SHOULD_BE_A_VALID_JSON,
                                                CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME,
                                                CustomAudienceValidator
                                                        .USER_BIDDING_SIGNALS_FIELD_NAME))));
    }

    @Test
    public void testValidator_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceUserBiddingSignalsValidator mValidatorWithSmallLimits =
                new CustomAudienceUserBiddingSignalsValidator(mJsonValidator, 1);

        // Constructor a valid uri which will now be too big for the validator.
        AdSelectionSignals tooBigSignals = VALID_USER_BIDDING_SIGNALS;

        // Assert invalid user bidding signals throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidatorWithSmallLimits.validate(tooBigSignals));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                tooBigSignals.getClass().getName(),
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                                                1,
                                                tooBigSignals.getSizeInBytes()))));
    }
}
