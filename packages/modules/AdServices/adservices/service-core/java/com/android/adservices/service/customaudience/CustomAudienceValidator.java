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

import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdDataValidator;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.JsonValidator;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.time.Clock;
import java.util.Objects;

/** Validator for Custom Audience. */
public class CustomAudienceValidator implements Validator<CustomAudience> {
    @VisibleForTesting
    static final String CUSTOM_AUDIENCE_CLASS_NAME = CustomAudience.class.getName();

    @VisibleForTesting static final String DAILY_UPDATE_URI_FIELD_NAME = "daily update uri";
    @VisibleForTesting static final String BIDDING_LOGIC_URI_FIELD_NAME = "bidding logic uri";
    @VisibleForTesting static final String USER_BIDDING_SIGNALS_FIELD_NAME = "user bidding signals";

    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceValidator sSingleton;

    @NonNull private final CustomAudienceTimestampValidator mCustomAudienceTimestampValidator;
    @NonNull private final AdTechIdentifierValidator mBuyerValidator;
    @NonNull private final JsonValidator mUserBiddingSignalsValidator;
    @NonNull private final CustomAudienceFieldSizeValidator mCustomAudienceFieldSizeValidator;
    @NonNull private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator;
    @NonNull private final AdRenderIdValidator mAdRenderIdValidator;

    // Process-stable flag values
    private final int mCustomAudienceMaxTrustedBiddingDataSizeB;

    @VisibleForTesting
    public CustomAudienceValidator(
            @NonNull CustomAudienceTimestampValidator customAudienceTimestampValidator,
            @NonNull AdTechIdentifierValidator buyerValidator,
            @NonNull JsonValidator userBiddingSignalsValidator,
            @NonNull CustomAudienceFieldSizeValidator customAudienceFieldSizeValidator,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator,
            int customAudienceMaxTrustedBiddingDataSizeB) {
        Objects.requireNonNull(customAudienceTimestampValidator);
        Objects.requireNonNull(buyerValidator);
        Objects.requireNonNull(userBiddingSignalsValidator);
        Objects.requireNonNull(customAudienceFieldSizeValidator);
        Objects.requireNonNull(frequencyCapAdDataValidator);

        mCustomAudienceTimestampValidator = customAudienceTimestampValidator;
        mBuyerValidator = buyerValidator;
        mUserBiddingSignalsValidator = userBiddingSignalsValidator;
        mCustomAudienceFieldSizeValidator = customAudienceFieldSizeValidator;
        mFrequencyCapAdDataValidator = frequencyCapAdDataValidator;
        mAdRenderIdValidator = adRenderIdValidator;
        mCustomAudienceMaxTrustedBiddingDataSizeB = customAudienceMaxTrustedBiddingDataSizeB;
    }

    public CustomAudienceValidator(
            @NonNull Clock clock,
            @NonNull Flags flags,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator) {
        this(
                new CustomAudienceTimestampValidator(clock, flags),
                new AdTechIdentifierValidator(
                        CUSTOM_AUDIENCE_CLASS_NAME, ValidatorUtil.AD_TECH_ROLE_BUYER),
                new JsonValidator(CUSTOM_AUDIENCE_CLASS_NAME, USER_BIDDING_SIGNALS_FIELD_NAME),
                new CustomAudienceFieldSizeValidator(flags),
                frequencyCapAdDataValidator,
                adRenderIdValidator,
                flags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB());
    }

    /**
     * Gets an instance of {@link CustomAudienceValidator} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static CustomAudienceValidator getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();
                sSingleton =
                        new CustomAudienceValidator(
                                Clock.systemUTC(),
                                flags,
                                new AdFilteringFeatureFactory(
                                                SharedStorageDatabase.getInstance(context)
                                                        .appInstallDao(),
                                                SharedStorageDatabase.getInstance(context)
                                                        .frequencyCapDao(),
                                                flags)
                                        .getFrequencyCapAdDataValidator(),
                                AdRenderIdValidator.createInstance(flags));
            }
            return sSingleton;
        }
    }

    /**
     * Validates the custom audience.
     *
     * @param customAudience the instance to be validated.
     */
    @Override
    public void addValidation(
            @NonNull CustomAudience customAudience,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(violations);

        validateFieldFormat(customAudience, violations);
        mCustomAudienceTimestampValidator.addValidation(customAudience, violations);
        mCustomAudienceFieldSizeValidator.addValidation(customAudience, violations);
    }

    private void validateFieldFormat(
            CustomAudience customAudience, ImmutableCollection.Builder<String> violations) {
        String buyer = customAudience.getBuyer().toString();
        mBuyerValidator.addValidation(buyer, violations);
        new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer,
                        CUSTOM_AUDIENCE_CLASS_NAME,
                        DAILY_UPDATE_URI_FIELD_NAME)
                .addValidation(customAudience.getDailyUpdateUri(), violations);
        new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer,
                        CUSTOM_AUDIENCE_CLASS_NAME,
                        BIDDING_LOGIC_URI_FIELD_NAME)
                .addValidation(customAudience.getBiddingLogicUri(), violations);
        if (customAudience.getUserBiddingSignals() != null) {
            mUserBiddingSignalsValidator.addValidation(
                    customAudience.getUserBiddingSignals().toString(), violations);
        }
        if (customAudience.getTrustedBiddingData() != null) {
            new TrustedBiddingDataValidator(mCustomAudienceMaxTrustedBiddingDataSizeB)
                    .addValidation(
                            customAudience.getTrustedBiddingData(),
                            AdTechIdentifier.fromString(buyer),
                            violations);
        }
        AdDataValidator adDataValidator =
                new AdDataValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer,
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator);
        for (AdData adData : customAudience.getAds()) {
            adDataValidator.addValidation(adData, violations);
        }
    }
}
