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

import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.TrustedBiddingData;
import android.annotation.NonNull;

import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/** Validator for the {@code trustedBiddingData} field of a Custom Audience. */
public class TrustedBiddingDataValidator {
    @VisibleForTesting
    static final String TRUSTED_BIDDING_DATA_CLASS_NAME = TrustedBiddingData.class.getName();
    public static final String TRUSTED_BIDDING_URI_FIELD_NAME = "trusted bidding URI";
    private final int mCustomAudienceMaxTrustedBiddingDataSizeB;

    public TrustedBiddingDataValidator(int customAudienceMaxTrustedBiddingDataSizeB) {
        mCustomAudienceMaxTrustedBiddingDataSizeB = customAudienceMaxTrustedBiddingDataSizeB;
    }

    /**
     * Validates the {@code trustedBiddingData} field of a Custom Audience against multiple
     * parameters.
     *
     * @param trustedBiddingData the {@code trustedBiddingData} field of a Custom Audience.
     * @param buyer the buyer expected to be associated with the trustedBiddingData.
     * @throws IllegalArgumentException if any violation is found
     */
    public void validate(
            @NonNull TrustedBiddingData trustedBiddingData, @NonNull AdTechIdentifier buyer)
            throws IllegalArgumentException {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(buyer);

        Collection<String> violations = getValidationViolations(trustedBiddingData, buyer);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ENGLISH,
                            EXCEPTION_MESSAGE_FORMAT,
                            TRUSTED_BIDDING_DATA_CLASS_NAME,
                            violations));
        }
    }

    /**
     * Populates violations.
     *
     * @param trustedBiddingData the {@code trustedBiddingData} field of a Custom Audience.
     * @param buyer the buyer expected to be associated with the trustedBiddingData.
     * @return the {@code Collection<String>} of violations found.
     */
    public Collection<String> getValidationViolations(
            @NonNull TrustedBiddingData trustedBiddingData, @NonNull AdTechIdentifier buyer) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(buyer);

        ImmutableCollection.Builder<String> violations = new ImmutableList.Builder<>();
        addValidation(trustedBiddingData, buyer, violations);
        return violations.build();
    }

    /**
     * Validates the {@link TrustedBiddingData} as follows:
     *
     * <ul>
     *   <li>{@link TrustedBiddingData#getTrustedBiddingUri()} is well-formed.
     *   <li>Size is less than {@code customAudienceMaxTrustedBiddingDataSizeB} (set while
     *       instantiating a {@link TrustedBiddingDataValidator}).
     * </ul>
     *
     * @param trustedBiddingData the {@link TrustedBiddingData} instance to be validated.
     * @param violations the collection of violations to add to.
     */
    public void addValidation(
            @NonNull TrustedBiddingData trustedBiddingData,
            @NonNull AdTechIdentifier buyer,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(violations);

        // Validate uri format.
        AdTechUriValidator adTechUriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer.toString(),
                        TRUSTED_BIDDING_DATA_CLASS_NAME,
                        TRUSTED_BIDDING_URI_FIELD_NAME);

        adTechUriValidator.addValidation(trustedBiddingData.getTrustedBiddingUri(), violations);

        // Validate the size is within limits.
        int trustedBiddingDataSizeB =
                DBTrustedBiddingData.fromServiceObject(trustedBiddingData).size();
        if (trustedBiddingDataSizeB > mCustomAudienceMaxTrustedBiddingDataSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                            mCustomAudienceMaxTrustedBiddingDataSizeB,
                            trustedBiddingDataSizeB));
        }
    }
}
