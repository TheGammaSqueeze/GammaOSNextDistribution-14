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
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_DAILY_UPDATE_URI_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.DAILY_UPDATE_URI_FIELD_NAME;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.ValidatorUtil;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/** Validator for the {@code dailyUpdateUri} field of a Custom Audience. */
public class CustomAudienceDailyUpdateUriValidator {
    private final int mCustomAudienceMaxDailyUpdateUriSizeB;

    public CustomAudienceDailyUpdateUriValidator(int customAudienceMaxDailyUpdateUriSizeB) {
        mCustomAudienceMaxDailyUpdateUriSizeB = customAudienceMaxDailyUpdateUriSizeB;
    }

    /**
     * Validates the {@code dailyUpdateUri} field of a Custom Audience against multiple parameters.
     *
     * @param dailyUpdateUri the {@code dailyUpdateUri} field of a Custom Audience.
     * @param buyer the buyer expected to be associated with the dailyUpdateUri.
     * @throws IllegalArgumentException if any violation is found
     */
    public void validate(@NonNull Uri dailyUpdateUri, @NonNull AdTechIdentifier buyer)
            throws IllegalArgumentException {
        Objects.requireNonNull(dailyUpdateUri);
        Objects.requireNonNull(buyer);

        Collection<String> violations = getValidationViolations(dailyUpdateUri, buyer);
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
     * @param dailyUpdateUri the {@code dailyUpdateUri} field of a Custom Audience.
     * @param buyer the buyer expected to be associated with the dailyUpdateUri.
     * @return the {@code Collection<String>} of violations found.
     */
    public Collection<String> getValidationViolations(
            @NonNull Uri dailyUpdateUri, @NonNull AdTechIdentifier buyer) {
        Objects.requireNonNull(dailyUpdateUri);
        Objects.requireNonNull(buyer);

        ImmutableCollection.Builder<String> violations = new ImmutableList.Builder<>();
        addValidation(dailyUpdateUri, buyer, violations);
        return violations.build();
    }

    /**
     * Validates the {@code dailyUpdateUri} field of a Custom Audience as follows:
     *
     * <ul>
     *   <li>URI is well-formed
     *   <li>Size is less than {@code customAudienceMaxDailyUpdateUriSizeB} (set while instantiating
     *       a {@link CustomAudienceDailyUpdateUriValidator}
     * </ul>
     *
     * @param dailyUpdateUri the {@code dailyUpdateUri} field of a Custom Audience.
     * @param violations the collection of violations to add to.
     */
    public void addValidation(
            @NonNull Uri dailyUpdateUri,
            @NonNull AdTechIdentifier buyer,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(dailyUpdateUri);
        Objects.requireNonNull(violations);

        // Validate uri format.
        AdTechUriValidator adTechUriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer.toString(),
                        CUSTOM_AUDIENCE_CLASS_NAME,
                        DAILY_UPDATE_URI_FIELD_NAME);
        adTechUriValidator.addValidation(dailyUpdateUri, violations);

        // Validate uri's size is within limits.
        int dailyUpdateUriSizeB = dailyUpdateUri.toString().getBytes(StandardCharsets.UTF_8).length;
        if (dailyUpdateUriSizeB > mCustomAudienceMaxDailyUpdateUriSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_DAILY_UPDATE_URI_TOO_LONG,
                            mCustomAudienceMaxDailyUpdateUriSizeB,
                            dailyUpdateUriSizeB));
        }
    }
}
