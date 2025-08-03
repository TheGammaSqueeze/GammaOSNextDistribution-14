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

import static android.adservices.common.CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEFORE_DELAYED_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEFORE_NOW_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_CURRENT_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.Locale;

public class CustomAudienceExpirationTimeValidatorTest {
    public static final Duration CUSTOM_AUDIENCE_MAX_EXPIRE_IN =
            Duration.ofMillis(CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxExpireInMs());

    private final CustomAudienceExpirationTimeValidator mValidator =
            new CustomAudienceExpirationTimeValidator(
                    FIXED_CLOCK_TRUNCATED_TO_MILLI, CUSTOM_AUDIENCE_MAX_EXPIRE_IN);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConstructor_nullClock_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceExpirationTimeValidator(
                                null, CUSTOM_AUDIENCE_MAX_EXPIRE_IN));
    }

    @Test
    public void testConstructor_nullMaxExpireIn_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceExpirationTimeValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI, null));
    }

    @Test
    public void testGetValidationViolations_nullExpirationTime_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(null, VALID_ACTIVATION_TIME));
    }

    @Test
    public void testGetValidationViolations_nullCalculatedActivationTime_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(VALID_EXPIRATION_TIME, null));
    }

    @Test
    public void testAddValidation_nullExpirationTime_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                null, VALID_ACTIVATION_TIME, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullCalculatedActivationTime_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                VALID_EXPIRATION_TIME, null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(VALID_EXPIRATION_TIME, VALID_ACTIVATION_TIME, null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid expiration time does not throw.
        mValidator.validate(VALID_EXPIRATION_TIME, VALID_ACTIVATION_TIME);
    }

    @Test
    public void testValidator_beforeNow() {
        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        INVALID_BEFORE_NOW_EXPIRATION_TIME, VALID_ACTIVATION_TIME));

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
                                                VIOLATION_EXPIRE_BEFORE_CURRENT_TIME,
                                                INVALID_BEFORE_NOW_EXPIRATION_TIME))));
    }

    @Test
    public void testValidator_beforeActivationTime() {
        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        INVALID_BEFORE_DELAYED_EXPIRATION_TIME,
                                        VALID_DELAYED_ACTIVATION_TIME));

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
                                                VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                                                VALID_DELAYED_ACTIVATION_TIME,
                                                INVALID_BEFORE_DELAYED_EXPIRATION_TIME))));
    }

    @Test
    public void testValidator_exceedsExpiryLimit() {
        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        INVALID_BEYOND_MAX_EXPIRATION_TIME, VALID_ACTIVATION_TIME));

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
                                                VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                                                CUSTOM_AUDIENCE_MAX_EXPIRE_IN,
                                                FIXED_NOW_TRUNCATED_TO_MILLI,
                                                FIXED_NOW_TRUNCATED_TO_MILLI,
                                                INVALID_BEYOND_MAX_EXPIRATION_TIME))));
    }
}
