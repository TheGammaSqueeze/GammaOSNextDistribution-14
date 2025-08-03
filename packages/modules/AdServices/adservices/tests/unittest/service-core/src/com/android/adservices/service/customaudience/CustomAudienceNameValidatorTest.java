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

import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CustomAudienceNameValidatorTest {
    public static final int CUSTOM_AUDIENCE_MAX_NAME_SIZE_B =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxNameSizeB();

    public final CustomAudienceNameValidator mValidator =
            new CustomAudienceNameValidator(CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAddValidation_nullName_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(NullPointerException.class, () -> mValidator.addValidation(VALID_NAME, null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid name does not throw.
        mValidator.validate(VALID_NAME);
    }

    @Test
    public void testValidator_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceNameValidator mValidatorWithSmallLimits = new CustomAudienceNameValidator(1);

        // Constructor a valid name which will now be too big for the validator.
        String tooLongName = "tooLongName";

        // Assert invalid activation time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidatorWithSmallLimits.validate(tooLongName));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                tooLongName.getClass().getName(),
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_NAME_TOO_LONG,
                                                1,
                                                tooLongName.getBytes(StandardCharsets.UTF_8)
                                                        .length))));
    }
}
