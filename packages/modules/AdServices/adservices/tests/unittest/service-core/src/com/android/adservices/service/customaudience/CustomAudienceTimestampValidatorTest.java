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

import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.ValidatorTestUtil;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;

public class CustomAudienceTimestampValidatorTest {
    private static final Flags FLAGS = FlagsFactory.getFlagsForTest();
    private final CustomAudienceTimestampValidator mValidator =
            new CustomAudienceTimestampValidator(
                    CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI, FLAGS);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAllValidTimes() {
        assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .isEmpty());
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
    public void testNullActivationTimeAndNullExpirationTime() {
        assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setActivationTime(null)
                                        .setExpirationTime(null)
                                        .build())
                        .isEmpty());
    }

    @Test
    public void testNullActivationTimeExpireBeforeMax() {
        assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setActivationTime(null)
                                        .setExpirationTime(
                                                CustomAudienceFixture.VALID_EXPIRATION_TIME)
                                        .build())
                        .isEmpty());
    }

    @Test
    public void testNullActivationTimeWithExpireAfterMax() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setActivationTime(null)
                                .setExpirationTime(
                                        CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                                .build()),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                        Duration.ofMillis(FLAGS.getFledgeCustomAudienceMaxExpireInMs()),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME));
    }

    @Test
    public void testNullExpirationTimeWithValidActivationTime() {
        assertTrue(
                mValidator
                        .getValidationViolations(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setExpirationTime(null)
                                        .setActivationTime(
                                                CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                                        .build())
                        .isEmpty());
    }

    @Test
    public void testNullExpirationTimeWithBeyondMaxActivationTime() {
        ValidatorTestUtil.assertViolationContainsOnly(
                mValidator.getValidationViolations(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setExpirationTime(null)
                                .setActivationTime(
                                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME)
                                .build()),
                String.format(
                        CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                        CustomAudienceFixture.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME));
    }
}
