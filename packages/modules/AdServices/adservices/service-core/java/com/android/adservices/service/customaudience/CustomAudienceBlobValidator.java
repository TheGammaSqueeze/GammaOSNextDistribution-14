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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Validator for a {@link CustomAudienceBlob}. */
public class CustomAudienceBlobValidator implements Validator<CustomAudienceBlob> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting public static final String CLASS_NAME = CustomAudienceBlob.class.getName();

    @VisibleForTesting
    public static final String ADTECH_IDENTIFIER_SHOULD_BE_VALID =
            "The %s ad tech's identifier should be present and valid.";

    private final Clock mClock;
    private final AdTechIdentifierValidator mBuyerValidator;
    private final CustomAudienceNameValidator mNameValidator;
    private final CustomAudienceActivationTimeValidator mActivationTimeValidator;
    private final CustomAudienceExpirationTimeValidator mExpirationTimeValidator;
    private final CustomAudienceDailyUpdateUriValidator mDailyUpdateUriValidator;
    private final CustomAudienceBiddingLogicUriValidator mBiddingLogicUriValidator;
    private final CustomAudienceUserBiddingSignalsValidator mUserBiddingSignalsValidator;
    private final TrustedBiddingDataValidator mTrustedBiddingDataValidator;
    private final CustomAudienceAdsValidator mAdsValidator;

    public CustomAudienceBlobValidator(
            @NonNull Clock clock,
            @NonNull CustomAudienceNameValidator nameValidator,
            @NonNull CustomAudienceUserBiddingSignalsValidator userBiddingSignalsValidator,
            @NonNull CustomAudienceActivationTimeValidator activationTimeValidator,
            @NonNull CustomAudienceExpirationTimeValidator expirationTimeValidator,
            @NonNull AdTechIdentifierValidator buyerValidator,
            @NonNull CustomAudienceBiddingLogicUriValidator biddingLogicUriValidator,
            @NonNull CustomAudienceDailyUpdateUriValidator dailyUpdateUriValidator,
            @NonNull TrustedBiddingDataValidator trustedBiddingDataValidator,
            @NonNull CustomAudienceAdsValidator adsValidator) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(nameValidator);
        Objects.requireNonNull(userBiddingSignalsValidator);
        Objects.requireNonNull(activationTimeValidator);
        Objects.requireNonNull(expirationTimeValidator);
        Objects.requireNonNull(buyerValidator);
        Objects.requireNonNull(biddingLogicUriValidator);
        Objects.requireNonNull(dailyUpdateUriValidator);
        Objects.requireNonNull(trustedBiddingDataValidator);
        Objects.requireNonNull(adsValidator);

        mClock = clock;
        mNameValidator = nameValidator;
        mUserBiddingSignalsValidator = userBiddingSignalsValidator;
        mActivationTimeValidator = activationTimeValidator;
        mExpirationTimeValidator = expirationTimeValidator;
        mBuyerValidator = buyerValidator;
        mBiddingLogicUriValidator = biddingLogicUriValidator;
        mDailyUpdateUriValidator = dailyUpdateUriValidator;
        mTrustedBiddingDataValidator = trustedBiddingDataValidator;
        mAdsValidator = adsValidator;
    }

    /**
     * Validates a {@link CustomAudienceBlob} as follows:
     *
     * <ul>
     *   <li>Validates the {@code name}, if present.
     *   <li>Validates the {@code userBiddingSignals}, if present.
     *   <li>Validates the {@code activationTime}, if present.
     *   <li>Validates the {@code expirationTime}, if present. It will use {@code activationTime},
     *       if present, or the current time as the {@code calculatedActivationTime} for validation.
     *   <li>Validates the {@code dailyUpdateUri}, if present with a valid buyer.
     *   <li>Validates the {@code biddingLogicUri}, if present with a valid buyer.
     *   <li>Validates the {@code trustedBiddingData}, if present with a valid buyer.
     *   <li>Validates the {@code ads}, if present with a valid buyer.
     * </ul>
     *
     * @param customAudience the {@code CustomAudienceBlob}.
     * @param violations the collection of violations to add to.
     */
    @Override
    public void addValidation(
            @NonNull CustomAudienceBlob customAudience,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(violations);

        // Validating the name, if present.
        if (customAudience.hasName()) {
            sLogger.d("Validating the CustomAudienceBlob's name.");
            mNameValidator.addValidation(customAudience.getName(), violations);
        }

        // Validating the user bidding signals, if present.
        if (customAudience.hasUserBiddingSignals()) {
            sLogger.d("Validating the CustomAudienceBlob's user bidding signals.");
            mUserBiddingSignalsValidator.addValidation(
                    customAudience.getUserBiddingSignals(), violations);
        }

        // Validating the activation and expiration times, if present.
        Instant calculatedActivationTime = mClock.instant();
        if (customAudience.hasActivationTime()) {
            sLogger.d("Validating the CustomAudienceBlob's activation time.");
            calculatedActivationTime = customAudience.getActivationTime();
            mActivationTimeValidator.addValidation(calculatedActivationTime, violations);
        }
        if (customAudience.hasExpirationTime()) {
            sLogger.d(
                    "Validating the CustomAudienceBlob's expiration time with calculated activation"
                            + " time as %s.",
                    calculatedActivationTime);
            mExpirationTimeValidator.addValidation(
                    customAudience.getExpirationTime(), calculatedActivationTime, violations);
        }

        // Validating the buyer, if present.
        if (customAudience.hasBuyer()) {
            sLogger.d("The CustomAudienceBlob has a valid buyer: %s.", customAudience.getBuyer());
            mBuyerValidator.addValidation(customAudience.getBuyer().toString(), violations);
        }

        // Defaulting to an empty ad tech identifier if buyer is absent.
        AdTechIdentifier buyer =
                customAudience.hasBuyer()
                        ? customAudience.getBuyer()
                        : AdTechIdentifier.fromString("");

        // Validating the bidding logic uri, if present.
        if (customAudience.hasBiddingLogicUri()) {
            sLogger.d(
                    "Validating the CustomAudienceBlob's bidding logic uri with buyer: %s.",
                    customAudience.getBuyer());
            mBiddingLogicUriValidator.addValidation(
                    customAudience.getBiddingLogicUri(), buyer, violations);
        }

        // Validating the daily update uri, if present.
        if (customAudience.hasDailyUpdateUri()) {
            sLogger.d(
                    "Validating the CustomAudienceBlob's daily update uri with buyer: %s.",
                    customAudience.getBuyer());
            mDailyUpdateUriValidator.addValidation(
                    customAudience.getDailyUpdateUri(), buyer, violations);
        }

        // Validating the trusted bidding data, if present.
        if (customAudience.hasTrustedBiddingData()) {
            sLogger.d(
                    "Validating the CustomAudienceBlob's trusted bidding data with buyer: %s.",
                    customAudience.getBuyer());
            mTrustedBiddingDataValidator.addValidation(
                    customAudience.getTrustedBiddingData(), buyer, violations);
        }

        // Validating the ads, if present.
        if (customAudience.hasAds()) {
            sLogger.d(
                    "Validating the CustomAudienceBlob's ads with buyer: %s.",
                    customAudience.getBuyer());
            mAdsValidator.addValidation(customAudience.getAds(), buyer, violations);
        }
    }
}
