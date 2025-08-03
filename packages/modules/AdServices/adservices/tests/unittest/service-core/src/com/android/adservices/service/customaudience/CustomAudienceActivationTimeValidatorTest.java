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
import static android.adservices.customaudience.CustomAudienceFixture.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME;

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class CustomAudienceActivationTimeValidatorTest {
    public static final Duration CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY =
            Duration.ofMillis(
                    CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxActivationDelayInMs());

    private final CustomAudienceActivationTimeValidator mValidator =
            new CustomAudienceActivationTimeValidator(
                    FIXED_CLOCK_TRUNCATED_TO_MILLI, CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConstructor_nullClock_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceActivationTimeValidator(
                                null, CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY));
    }

    @Test
    public void testConstructor_nullMaxActivationDelay_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceActivationTimeValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI, null));
    }

    @Test
    public void testAddValidation_nullActivationTime_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(VALID_ACTIVATION_TIME, null));
    }

    @Test
    public void testValidator_afterNow_valid() {
        // Assert valid activation time does not throw.
        mValidator.validate(VALID_DELAYED_ACTIVATION_TIME);
    }

    @Test
    public void testValidator_beforeNow_valid() {
        // Assert valid activation time does not throw.
        mValidator.validate(Instant.EPOCH);
    }

    @Test
    public void testValidator_exceedsDelayLimit() {
        // Assert invalid activation time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidator.validate(INVALID_DELAYED_ACTIVATION_TIME));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                Instant.class.getName(),
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                                                CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN,
                                                FIXED_NOW_TRUNCATED_TO_MILLI,
                                                INVALID_DELAYED_ACTIVATION_TIME))));
    }
}
