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

import static android.adservices.common.AdDataFixture.getValidFilterAdsWithAdRenderIdByBuyer;
import static android.adservices.common.CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.FLAGS_FOR_TEST;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.CustomAudienceFixture.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEFORE_DELAYED_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEFORE_NOW_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;
import static android.adservices.customaudience.CustomAudienceFixture.getValidBiddingLogicUriByBuyer;
import static android.adservices.customaudience.CustomAudienceFixture.getValidDailyUpdateUriByBuyer;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer;

import static com.android.adservices.service.common.AdDataValidator.AD_DATA_CLASS_NAME;
import static com.android.adservices.service.common.AdDataValidator.METADATA_FIELD_NAME;
import static com.android.adservices.service.common.AdDataValidator.RENDER_URI_FIELD_NAME;
import static com.android.adservices.service.common.AdDataValidator.VIOLATION_FORMAT;
import static com.android.adservices.service.common.AdTechIdentifierValidator.IDENTIFIER_HAS_MISSING_DOMAIN_NAME;
import static com.android.adservices.service.common.AdTechIdentifierValidator.IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME;
import static com.android.adservices.service.common.AdTechIdentifierValidator.IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY;
import static com.android.adservices.service.common.AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT;
import static com.android.adservices.service.common.JsonValidator.SHOULD_BE_A_VALID_JSON;
import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceActivationTimeValidatorTest.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY;
import static com.android.adservices.service.customaudience.CustomAudienceAdsValidatorTest.CUSTOM_AUDIENCE_MAX_ADS_SIZE_B;
import static com.android.adservices.service.customaudience.CustomAudienceAdsValidatorTest.CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.customaudience.CustomAudienceBiddingLogicUriValidatorTest.CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B;
import static com.android.adservices.service.customaudience.CustomAudienceBlobValidator.CLASS_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceDailyUpdateUriValidatorTest.CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B;
import static com.android.adservices.service.customaudience.CustomAudienceExpirationTimeValidatorTest.CUSTOM_AUDIENCE_MAX_EXPIRE_IN;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_BIDDING_LOGIC_URI_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_DAILY_UPDATE_URI_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_COUNT_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_SIZE_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceNameValidatorTest.CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_CURRENT_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceUserBiddingSignalsValidatorTest.CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.BIDDING_LOGIC_URI_FIELD_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.DAILY_UPDATE_URI_FIELD_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.USER_BIDDING_SIGNALS_FIELD_NAME;
import static com.android.adservices.service.customaudience.TrustedBiddingDataValidator.TRUSTED_BIDDING_URI_FIELD_NAME;
import static com.android.adservices.service.customaudience.TrustedBiddingDataValidatorTest.CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.AdDataValidator;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorImpl;
import com.android.adservices.service.common.JsonValidator;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class CustomAudienceBlobValidatorTest {
    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private final AdTechIdentifierValidator mValidBuyerValidator =
            new AdTechIdentifierValidator(CLASS_NAME, AD_TECH_ROLE_BUYER);
    private final CustomAudienceNameValidator mValidNameValidator =
            new CustomAudienceNameValidator(CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);
    private final CustomAudienceActivationTimeValidator mValidActivationTimeValidator =
            new CustomAudienceActivationTimeValidator(
                    FIXED_CLOCK_TRUNCATED_TO_MILLI, CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY);
    private final CustomAudienceExpirationTimeValidator mValidExpirationTimeValidator =
            new CustomAudienceExpirationTimeValidator(
                    FIXED_CLOCK_TRUNCATED_TO_MILLI, CUSTOM_AUDIENCE_MAX_EXPIRE_IN);
    private final CustomAudienceDailyUpdateUriValidator mValidDailyUpdateUriValidator =
            new CustomAudienceDailyUpdateUriValidator(CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);
    private final CustomAudienceBiddingLogicUriValidator mValidBiddingLogicUriValidator =
            new CustomAudienceBiddingLogicUriValidator(
                    CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);
    private final JsonValidator mJsonValidator =
            new JsonValidator(CUSTOM_AUDIENCE_CLASS_NAME, USER_BIDDING_SIGNALS_FIELD_NAME);
    private final CustomAudienceUserBiddingSignalsValidator mValidUserBiddingSignalsValidator =
            new CustomAudienceUserBiddingSignalsValidator(
                    mJsonValidator, CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    private final TrustedBiddingDataValidator mValidTrustedBiddingDataValidator =
            new TrustedBiddingDataValidator(CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator =
            new FrequencyCapAdDataValidatorImpl();
    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(
                    FLAGS_FOR_TEST.getFledgeAuctionServerAdRenderIdMaxLength());
    private final CustomAudienceAdsValidator mValidAdsValidator =
            new CustomAudienceAdsValidator(
                    mFrequencyCapAdDataValidator,
                    mAdRenderIdValidator,
                    AD_DATA_CONVERSION_STRATEGY,
                    CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                    CUSTOM_AUDIENCE_MAX_NUM_ADS);

    private final CustomAudienceBlobValidator mValidator =
            new CustomAudienceBlobValidator(
                    FIXED_CLOCK_TRUNCATED_TO_MILLI,
                    mValidNameValidator,
                    mValidUserBiddingSignalsValidator,
                    mValidActivationTimeValidator,
                    mValidExpirationTimeValidator,
                    mValidBuyerValidator,
                    mValidBiddingLogicUriValidator,
                    mValidDailyUpdateUriValidator,
                    mValidTrustedBiddingDataValidator,
                    mValidAdsValidator);

    private CustomAudienceBlob mCustomAudience;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mCustomAudience = new CustomAudienceBlob();
    }

    @Test
    public void testConstructor_nullClock_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                null,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullNameValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                null,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullActivationTimeValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                null,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullUserBiddingSignalsValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                null,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullExpirationTimeValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                null,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullBuyerValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                null,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullBiddingLogicUriValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                null,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullDailyUpdateUriValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                null,
                                mValidTrustedBiddingDataValidator,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullTrustedBiddingDataValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                null,
                                mValidAdsValidator));
    }

    @Test
    public void testConstructor_nullAdsValidator_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CustomAudienceBlobValidator(
                                FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                mValidNameValidator,
                                mValidUserBiddingSignalsValidator,
                                mValidActivationTimeValidator,
                                mValidExpirationTimeValidator,
                                mValidBuyerValidator,
                                mValidBiddingLogicUriValidator,
                                mValidDailyUpdateUriValidator,
                                mValidTrustedBiddingDataValidator,
                                null));
    }

    @Test
    public void testAddValidation_nullCustomAudienceBlob_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(new CustomAudienceBlob(), null));
    }

    @Test
    public void testValidator_validName() {
        mCustomAudience.setName(VALID_NAME);

        // Assert valid name does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_invalidName_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceNameValidator nameValidatorWithSmallLimits =
                new CustomAudienceNameValidator(1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        nameValidatorWithSmallLimits,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        mValidDailyUpdateUriValidator,
                        mValidTrustedBiddingDataValidator,
                        mValidAdsValidator);

        // Constructor a valid name which will now be too big for the validator.
        String tooLongName = "tooLongName";
        mCustomAudience.setName(tooLongName);

        // Assert invalid name throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_NAME_TOO_LONG,
                        1,
                        tooLongName.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    public void testValidator_validUserBiddingSignals() {
        mCustomAudience.setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS);

        // Assert valid user bidding signals does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_invalidUserBiddingSignals_malformedJson() {
        // Construct invalid user bidding signals.
        AdSelectionSignals invalidUserBiddingSignals =
                AdSelectionSignals.fromString("Not[A]VALID[JSON]");
        mCustomAudience.setUserBiddingSignals(invalidUserBiddingSignals);

        // Assert invalid user bidding signals throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        JsonValidator.SHOULD_BE_A_VALID_JSON,
                        CUSTOM_AUDIENCE_CLASS_NAME,
                        USER_BIDDING_SIGNALS_FIELD_NAME));
    }

    @Test
    public void testValidator_invalidUserBiddingSignals_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceUserBiddingSignalsValidator userBiddingSignalsValidatorWithSmallLimits =
                new CustomAudienceUserBiddingSignalsValidator(mJsonValidator, 1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        userBiddingSignalsValidatorWithSmallLimits,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        mValidDailyUpdateUriValidator,
                        mValidTrustedBiddingDataValidator,
                        mValidAdsValidator);

        // Constructor a valid user bidding signals which will now be too big for the validator.
        AdSelectionSignals tooBigSignals = VALID_USER_BIDDING_SIGNALS;
        mCustomAudience.setUserBiddingSignals(tooBigSignals);

        // Assert invalid user bidding signals throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                        1,
                        tooBigSignals.getSizeInBytes()));
    }

    @Test
    public void testValidator_validActivationTime_afterNow() {
        mCustomAudience.setActivationTime(VALID_DELAYED_ACTIVATION_TIME);

        // Assert valid activation time does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_validActivationTime_beforeNow() {
        mCustomAudience.setActivationTime(Instant.EPOCH);

        // Assert valid activation time does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_invalidActivationTime_exceedsDelayLimit() {
        mCustomAudience.setActivationTime(INVALID_DELAYED_ACTIVATION_TIME);

        // Assert invalid activation time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                        CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN,
                        FIXED_NOW_TRUNCATED_TO_MILLI,
                        INVALID_DELAYED_ACTIVATION_TIME));
    }

    @Test
    public void testValidator_validExpirationTime_unsetActivationTime() {
        // Note that we do not set the activation time. The current time is used instead for
        // validation.
        mCustomAudience.setExpirationTime(VALID_EXPIRATION_TIME);

        // Assert valid expiration time does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_validExpirationTime_validActivationTime() {
        mCustomAudience.setActivationTime(VALID_ACTIVATION_TIME);
        mCustomAudience.setExpirationTime(VALID_EXPIRATION_TIME);

        // Assert valid expiration time does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_invalidExpirationTime_beforeNow() {
        mCustomAudience.setExpirationTime(INVALID_BEFORE_NOW_EXPIRATION_TIME);

        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_EXPIRE_BEFORE_CURRENT_TIME,
                        INVALID_BEFORE_NOW_EXPIRATION_TIME));
    }

    @Test
    public void testValidator_invalidExpirationTime_beforeActivationTime() {
        mCustomAudience.setActivationTime(VALID_DELAYED_ACTIVATION_TIME);
        mCustomAudience.setExpirationTime(INVALID_BEFORE_DELAYED_EXPIRATION_TIME);

        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                        VALID_DELAYED_ACTIVATION_TIME,
                        INVALID_BEFORE_DELAYED_EXPIRATION_TIME));
    }

    @Test
    public void testValidator_invalidExpirationTime_exceedsExpiryLimit() {
        mCustomAudience.setActivationTime(VALID_ACTIVATION_TIME);
        mCustomAudience.setExpirationTime(INVALID_BEYOND_MAX_EXPIRATION_TIME);

        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                        CUSTOM_AUDIENCE_MAX_EXPIRE_IN,
                        FIXED_NOW_TRUNCATED_TO_MILLI,
                        FIXED_NOW_TRUNCATED_TO_MILLI,
                        INVALID_BEYOND_MAX_EXPIRATION_TIME));
    }

    @Test
    public void testValidator_validBuyer() {
        mCustomAudience.setBuyer(VALID_BUYER_1);

        // Assert valid buyer does not throw.
        mValidator.validate(mCustomAudience);
    }

    @Test
    public void testValidator_invalidBuyer_emptyIdentifier() {
        AdTechIdentifier emptyBuyer = AdTechIdentifier.fromString("");
        mCustomAudience.setBuyer(emptyBuyer);

        // Assert invalid buyer throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        String.format(
                                Locale.ENGLISH,
                                IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                                CLASS_NAME,
                                AD_TECH_ROLE_BUYER)));
    }

    @Test
    public void testValidator_invalidBuyer_missingHost() {
        AdTechIdentifier buyerWithoutHost = AdTechIdentifier.fromString("test@");
        mCustomAudience.setBuyer(buyerWithoutHost);

        // Assert invalid buyer throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidBuyer_domainHasPath() {
        AdTechIdentifier buyerWithPath = AdTechIdentifier.fromString(VALID_BUYER_1 + "/path");
        mCustomAudience.setBuyer(buyerWithPath);

        // Assert invalid buyer throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidBuyer_domainHasPort() {
        AdTechIdentifier buyerWithPort = AdTechIdentifier.fromString(VALID_BUYER_1 + ":80");
        mCustomAudience.setBuyer(buyerWithPort);

        // Assert invalid buyer throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_validBiddingLogicUri() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert valid bidding logic uri does not throw.
        mValidator.validate(mCustomAudience);
    }

    // TODO(b/288972063): Remove ignore when AdTechUriValidator throws on empty identifier
    @Ignore("b/288972063")
    @Test
    public void testValidator_invalidBiddingLogicUri_invalidBuyer_unset() {
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_invalidBuyer_emptyIdentifier() {
        AdTechIdentifier emptyBuyer = AdTechIdentifier.fromString("");
        mCustomAudience.setBuyer(emptyBuyer);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_invalidBuyer_missingHost() {
        AdTechIdentifier buyerWithoutHost = AdTechIdentifier.fromString("test@");
        mCustomAudience.setBuyer(buyerWithoutHost);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithoutHost,
                        AD_TECH_ROLE_BUYER,
                        BIDDING_LOGIC_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_invalidBuyer_domainHasPath() {
        AdTechIdentifier buyerWithPath = AdTechIdentifier.fromString(VALID_BUYER_1 + "/path");
        mCustomAudience.setBuyer(buyerWithPath);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        AD_TECH_ROLE_BUYER,
                        BIDDING_LOGIC_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_invalidBuyer_domainHasPort() {
        AdTechIdentifier buyerWithPort = AdTechIdentifier.fromString(VALID_BUYER_1 + ":80");
        mCustomAudience.setBuyer(buyerWithPort);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPort,
                        AD_TECH_ROLE_BUYER,
                        BIDDING_LOGIC_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_malformedUri() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setBiddingLogicUri(getValidBiddingLogicUriByBuyer(VALID_BUYER_2));

        // Assert buyer mismatch causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        VALID_BUYER_1,
                        AD_TECH_ROLE_BUYER,
                        BIDDING_LOGIC_URI_FIELD_NAME,
                        VALID_BUYER_2));
    }

    @Test
    public void testValidator_invalidBiddingLogicUri_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceBiddingLogicUriValidator biddingLogicUriWithSmallLimit =
                new CustomAudienceBiddingLogicUriValidator(1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        biddingLogicUriWithSmallLimit,
                        mValidDailyUpdateUriValidator,
                        mValidTrustedBiddingDataValidator,
                        mValidAdsValidator);

        // Constructor a valid uri which will now be too big for the validator.
        Uri tooLongUri = getValidBiddingLogicUriByBuyer(VALID_BUYER_1);
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setBiddingLogicUri(tooLongUri);

        // Assert invalid bidding logic uri throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_BIDDING_LOGIC_URI_TOO_LONG,
                        1,
                        tooLongUri.toString().getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    public void testValidator_validDailyUpdateUri() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert valid update uri does not throw.
        mValidator.validate(mCustomAudience);
    }

    // TODO(b/288972063): Remove ignore when AdTechUriValidator throws on empty identifier
    @Ignore("b/288972063")
    @Test
    public void testValidator_invalidDailyUpdateUri_invalidBuyer_unset() {
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_invalidBuyer_emptyIdentifier() {
        AdTechIdentifier emptyBuyer = AdTechIdentifier.fromString("");
        mCustomAudience.setBuyer(emptyBuyer);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_invalidBuyer_missingHost() {
        AdTechIdentifier buyerWithoutHost = AdTechIdentifier.fromString("test@");
        mCustomAudience.setBuyer(buyerWithoutHost);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithoutHost,
                        AD_TECH_ROLE_BUYER,
                        DAILY_UPDATE_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_invalidBuyer_domainHasPath() {
        AdTechIdentifier buyerWithPath = AdTechIdentifier.fromString(VALID_BUYER_1 + "/path");
        mCustomAudience.setBuyer(buyerWithPath);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        AD_TECH_ROLE_BUYER,
                        DAILY_UPDATE_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_invalidBuyer_domainHasPort() {
        AdTechIdentifier buyerWithPort = AdTechIdentifier.fromString(VALID_BUYER_1 + ":80");
        mCustomAudience.setBuyer(buyerWithPort);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPort,
                        AD_TECH_ROLE_BUYER,
                        DAILY_UPDATE_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_malformedUri() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setDailyUpdateUri(getValidDailyUpdateUriByBuyer(VALID_BUYER_2));

        // Assert buyer mismatch causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        VALID_BUYER_1,
                        AD_TECH_ROLE_BUYER,
                        DAILY_UPDATE_URI_FIELD_NAME,
                        VALID_BUYER_2));
    }

    @Test
    public void testValidator_invalidDailyUpdateUri_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceDailyUpdateUriValidator dailyUpdateUriWithSmallLimit =
                new CustomAudienceDailyUpdateUriValidator(1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        dailyUpdateUriWithSmallLimit,
                        mValidTrustedBiddingDataValidator,
                        mValidAdsValidator);

        // Constructor a valid uri which will now be too big for the validator.
        Uri tooLongUri = getValidDailyUpdateUriByBuyer(VALID_BUYER_1);
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setDailyUpdateUri(tooLongUri);

        // Assert invalid daily update uri throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_DAILY_UPDATE_URI_TOO_LONG,
                        1,
                        tooLongUri.toString().getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    public void testValidator_validTrustedBiddingData() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert valid trusted bidding data does not throw.
        mValidator.validate(mCustomAudience);
    }

    // TODO(b/288972063): Remove ignore when AdTechUriValidator throws on empty identifier
    @Ignore("b/288972063")
    @Test
    public void testValidator_invalidTrustedBiddingData_invalidBuyer_unset() {
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_invalidBuyer_emptyIdentifier() {
        AdTechIdentifier emptyBuyer = AdTechIdentifier.fromString("");
        mCustomAudience.setBuyer(emptyBuyer);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_invalidBuyer_missingHost() {
        AdTechIdentifier buyerWithoutHost = AdTechIdentifier.fromString("test@");
        mCustomAudience.setBuyer(buyerWithoutHost);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithoutHost,
                        AD_TECH_ROLE_BUYER,
                        TRUSTED_BIDDING_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_invalidBuyer_domainHasPath() {
        AdTechIdentifier buyerWithPath = AdTechIdentifier.fromString(VALID_BUYER_1 + "/path");
        mCustomAudience.setBuyer(buyerWithPath);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPath,
                        AD_TECH_ROLE_BUYER,
                        TRUSTED_BIDDING_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_invalidBuyer_domainHasPort() {
        AdTechIdentifier buyerWithPort = AdTechIdentifier.fromString(VALID_BUYER_1 + ":80");
        mCustomAudience.setBuyer(buyerWithPort);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_1));

        // Assert invalid expiration time throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        buyerWithPort,
                        AD_TECH_ROLE_BUYER,
                        TRUSTED_BIDDING_URI_FIELD_NAME,
                        VALID_BUYER_1));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_malformedUri() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(VALID_BUYER_2));

        // Assert buyer mismatch causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                        AD_TECH_ROLE_BUYER,
                        VALID_BUYER_1,
                        AD_TECH_ROLE_BUYER,
                        TRUSTED_BIDDING_URI_FIELD_NAME,
                        VALID_BUYER_2));
    }

    @Test
    public void testValidator_invalidTrustedBiddingData_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        TrustedBiddingDataValidator trustedBiddingDataValidatorWithSmallLimit =
                new TrustedBiddingDataValidator(1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        mValidDailyUpdateUriValidator,
                        trustedBiddingDataValidatorWithSmallLimit,
                        mValidAdsValidator);

        // Constructor a valid instance of TrustedBiddingData which will now be too big for the
        // validator.
        TrustedBiddingData tooBigTrustedBiddingData =
                new TrustedBiddingData.Builder()
                        .setTrustedBiddingKeys(List.of())
                        .setTrustedBiddingUri(
                                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(VALID_BUYER_1))
                        .build();
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setTrustedBiddingData(tooBigTrustedBiddingData);

        // Assert invalid trusted bidding data throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                        1,
                        DBTrustedBiddingData.fromServiceObject(tooBigTrustedBiddingData).size()));
    }

    @Test
    public void testValidator_validAds() {
        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1));

        // Assert valid ads does not throw.
        mValidator.validate(mCustomAudience);
    }

    // TODO(b/288972063): Remove ignore when AdTechUriValidator throws on empty identifier
    @Ignore("b/288972063")
    @Test
    public void testValidator_invalidAds_invalidBuyer_unset() {
        mCustomAudience.setAds(getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidAds_invalidBuyer_emptyIdentifier() {
        AdTechIdentifier emptyBuyer = AdTechIdentifier.fromString("");
        mCustomAudience.setBuyer(emptyBuyer);
        mCustomAudience.setAds(getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1));

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_SHOULD_NOT_BE_NULL_OR_EMPTY,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER));
    }

    @Test
    public void testValidator_invalidAds_invalidBuyer_missingHost() {
        AdTechIdentifier buyerWithoutHost = AdTechIdentifier.fromString("test@");
        mCustomAudience.setBuyer(buyerWithoutHost);
        List<AdData> validAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(validAds);

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_HAS_MISSING_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(0),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(1),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(2),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(3),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(4),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(5),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithoutHost,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)));
    }

    @Test
    public void testValidator_invalidAds_invalidBuyer_domainHasPath() {
        AdTechIdentifier buyerWithPath = AdTechIdentifier.fromString(VALID_BUYER_1 + "/path");
        mCustomAudience.setBuyer(buyerWithPath);
        List<AdData> validAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(validAds);

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(0),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(1),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(2),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(3),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(4),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(5),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPath,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)));
    }

    @Test
    public void testValidator_invalidAds_invalidBuyer_domainHasPort() {
        AdTechIdentifier buyerWithPort = AdTechIdentifier.fromString(VALID_BUYER_1 + ":80");
        mCustomAudience.setBuyer(buyerWithPort);
        List<AdData> validAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(validAds);

        // Assert invalid buyer causes the validator to throw.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        IDENTIFIER_IS_AN_INVALID_DOMAIN_NAME,
                        CLASS_NAME,
                        AD_TECH_ROLE_BUYER),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(0),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(1),
                        String.format(
                                AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(2),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(3),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(4),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)),
                String.format(
                        AdDataValidator.VIOLATION_FORMAT,
                        validAds.get(5),
                        String.format(
                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                AD_TECH_ROLE_BUYER,
                                buyerWithPort,
                                AD_TECH_ROLE_BUYER,
                                RENDER_URI_FIELD_NAME,
                                VALID_BUYER_1)));
    }

    @Test
    public void testValidator_invalidAds_malformedAds() {
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
        String tooLongAdRenderId = "VeryLongLongLongId";
        AdData tooLongRenderIdAd =
                AdDataFixture.getValidAdDataBuilderByBuyer(VALID_BUYER_1, 1)
                        .setAdRenderId(tooLongAdRenderId)
                        .build();

        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(
                List.of(
                        invalidAdDataWithAnotherBuyer,
                        invalidAdDataWithInvalidMetadata,
                        tooLongRenderIdAd,
                        validAdData));

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class, () -> mValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
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
                                METADATA_FIELD_NAME)),
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_FORMAT,
                        tooLongRenderIdAd,
                        String.format(
                                Locale.ENGLISH,
                                AdRenderIdValidator.AD_RENDER_ID_TOO_LONG,
                                FLAGS_FOR_TEST.getFledgeAuctionServerAdRenderIdMaxLength(),
                                tooLongAdRenderId.getBytes().length)));
    }

    @Test
    public void testValidator_invalidAds_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceAdsValidator adsValidatorWithSmallLimit =
                new CustomAudienceAdsValidator(
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        1,
                        CUSTOM_AUDIENCE_MAX_NUM_ADS);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        mValidDailyUpdateUriValidator,
                        mValidTrustedBiddingDataValidator,
                        adsValidatorWithSmallLimit);

        // Constructor valid ads which will now be too big for the validator.
        List<AdData> tooBigAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);

        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(tooBigAds);

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH,
                        VIOLATION_TOTAL_ADS_SIZE_TOO_BIG,
                        1,
                        tooBigAds.stream()
                                .map(
                                        obj ->
                                                AD_DATA_CONVERSION_STRATEGY
                                                        .fromServiceObject(obj)
                                                        .build())
                                .mapToInt(DBAdData::size)
                                .sum()));
    }

    @Test
    public void testValidator_invalidAds_exceedsCountLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceAdsValidator adsValidatorWithSmallLimit =
                new CustomAudienceAdsValidator(
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                        1);
        CustomAudienceBlobValidator customAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mValidNameValidator,
                        mValidUserBiddingSignalsValidator,
                        mValidActivationTimeValidator,
                        mValidExpirationTimeValidator,
                        mValidBuyerValidator,
                        mValidBiddingLogicUriValidator,
                        mValidDailyUpdateUriValidator,
                        mValidTrustedBiddingDataValidator,
                        adsValidatorWithSmallLimit);

        // Constructor valid ads which will now be too big for the validator.
        List<AdData> tooManyAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);

        mCustomAudience.setBuyer(VALID_BUYER_1);
        mCustomAudience.setAds(tooManyAds);

        // Assert invalid ads throws.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> customAudienceBlobValidator.validate(mCustomAudience));

        // Assert error message is as expected.
        assertExceptionMessageHasViolations(
                exception,
                String.format(
                        Locale.ENGLISH, VIOLATION_TOTAL_ADS_COUNT_TOO_BIG, 1, tooManyAds.size()));
    }

    private void assertExceptionMessageHasViolations(Exception exception, String... violations) {
        ImmutableList<String> violationsList = ImmutableList.copyOf(violations);

        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CLASS_NAME,
                                violationsList));
    }
}
