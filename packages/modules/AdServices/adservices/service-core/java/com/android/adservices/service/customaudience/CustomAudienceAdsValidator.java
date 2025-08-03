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

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_COUNT_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TOTAL_ADS_SIZE_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;

import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.service.common.AdDataValidator;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.ValidatorUtil;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Validates the {@code ads} field (represented as {@code List<AdData>}) of a Custom Audience. */
public class CustomAudienceAdsValidator {
    @NonNull private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator;
    @NonNull private final AdDataConversionStrategy mAdDataConversionStrategy;
    @NonNull private final AdRenderIdValidator mAdRenderIdValidator;
    private final int mCustomAudienceMaxAdsSizeB;
    private final int mCustomAudienceMaxNumAds;

    public CustomAudienceAdsValidator(
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator,
            @NonNull AdDataConversionStrategy adDataConversionStrategy,
            int customAudienceMaxAdsSizeB,
            int customAudienceMaxNumAds) {
        Objects.requireNonNull(frequencyCapAdDataValidator);
        Objects.requireNonNull(adRenderIdValidator);
        Objects.requireNonNull(adDataConversionStrategy);

        mFrequencyCapAdDataValidator = frequencyCapAdDataValidator;
        mAdRenderIdValidator = adRenderIdValidator;
        mAdDataConversionStrategy = adDataConversionStrategy;
        mCustomAudienceMaxAdsSizeB = customAudienceMaxAdsSizeB;
        mCustomAudienceMaxNumAds = customAudienceMaxNumAds;
    }

    /**
     * Validates the {@code ads} field of a Custom Audience against multiple parameters.
     *
     * @param ads the {@code ads} field (represented as {@code List<AdData>}) of a Custom Audience.
     * @param buyer the buyer expected to be associated with the ads.
     * @throws IllegalArgumentException if any violation is found
     */
    public void validate(@NonNull List<AdData> ads, @NonNull AdTechIdentifier buyer)
            throws IllegalArgumentException {
        Objects.requireNonNull(ads);
        Objects.requireNonNull(buyer);

        Collection<String> violations = getValidationViolations(ads, buyer);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            EXCEPTION_MESSAGE_FORMAT,
                            CUSTOM_AUDIENCE_CLASS_NAME,
                            violations));
        }
    }

    /**
     * Populates violations.
     *
     * @param ads the {@code ads} field (represented as {@code List<AdData>}) of a Custom Audience.
     * @param buyer the buyer expected to be associated with the ads.
     * @return the {@code Collection<String>} of violations found.
     */
    public Collection<String> getValidationViolations(
            @NonNull List<AdData> ads, @NonNull AdTechIdentifier buyer) {
        Objects.requireNonNull(ads);
        Objects.requireNonNull(buyer);

        ImmutableCollection.Builder<String> violations = new ImmutableList.Builder<>();
        addValidation(ads, buyer, violations);
        return violations.build();
    }

    /**
     * Validates the {@code ads} field of a Custom Audience as follows:
     *
     * <ul>
     *   <li>Each ad (that is, {@link AdData}) is well-formed
     *   <li>Total size of {@code ads} is less than {@code customAudienceMaxAdsSizeB} (set while
     *       instantiating a {@link CustomAudienceAdsValidator})
     *   <li>Total count of {@code ads} is less than {@code customAudienceMaxNumAds} (set while
     *       instantiating a {@link CustomAudienceAdsValidator})
     * </ul>
     *
     * @param ads the {@code ads} field (represented as {@link List<AdData>}) of a Custom Audience.
     * @param buyer the buyer expected to be associated with the ads.
     * @param violations the collection of violations to add to.
     */
    public void addValidation(
            @NonNull List<AdData> ads,
            @NonNull AdTechIdentifier buyer,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(ads);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(violations);

        // Validate each ad is well-formed.
        AdDataValidator adDataValidator =
                new AdDataValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer.toString(),
                        mFrequencyCapAdDataValidator,
                        mAdRenderIdValidator);
        for (AdData ad : ads) {
            adDataValidator.addValidation(ad, violations);
        }

        // Validate total size of ads are within limits.
        int adsSizeB =
                ads.stream()
                        .map(obj -> mAdDataConversionStrategy.fromServiceObject(obj).build())
                        .mapToInt(DBAdData::size)
                        .sum();
        if (adsSizeB > mCustomAudienceMaxAdsSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_TOTAL_ADS_SIZE_TOO_BIG,
                            mCustomAudienceMaxAdsSizeB,
                            adsSizeB));
        }

        // Validate number of ads are within limits.
        if (ads.size() > mCustomAudienceMaxNumAds) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_TOTAL_ADS_COUNT_TOO_BIG,
                            mCustomAudienceMaxNumAds,
                            ads.size()));
        }
    }
}
