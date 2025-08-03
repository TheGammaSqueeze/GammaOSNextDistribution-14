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

package com.android.adservices.service.adselection;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validation utility class for {@link
 * android.adservices.adselection.AdSelectionManager#setAppInstallAdvertisers}'s AdTechIdentifier
 * set
 */
public class AdvertiserSetValidator implements Validator<Set<AdTechIdentifier>> {

    @VisibleForTesting
    public static final String SET_SHOULD_NOT_EXCEED_MAX_SIZE =
            "The sum of the size of the AdTechIdentifier Strings in the AdTechIdentifier set should"
                    + " not exceed %d bytes";

    // TODO(b/266976242) Move to PHFlag
    @VisibleForTesting public static final int MAX_TOTAL_SIZE_BYTES = 5000;

    private final AdTechIdentifierValidator mAdTechIdentifierValidator;

    public AdvertiserSetValidator(AdTechIdentifierValidator adTechIdentifierValidator) {
        mAdTechIdentifierValidator = adTechIdentifierValidator;
    }

    /**
     * Validate a set of ad tech identifiers:
     *
     * <ul>
     *   <li>The sum of the sizes of the AdTechIdentifier Strings in UTF+8 should be less than
     *       MAX_TOTAL_SIZE_BYTES bytes.
     *   <li>Each ad tech identifier should be valid.
     * </ul>
     *
     * @param advertisers the set of AdTechIdentifiers to be validated.
     */
    @Override
    public void addValidation(
            @NonNull Set<AdTechIdentifier> advertisers,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(violations);
        Objects.requireNonNull(advertisers);
        int totalSize = 0;
        for (AdTechIdentifier advertiser : advertisers) {
            String advertiserString = advertiser.toString();
            totalSize += advertiserString.getBytes().length;
            if (totalSize > MAX_TOTAL_SIZE_BYTES) {
                violations.add(
                        String.format(
                                Locale.US, SET_SHOULD_NOT_EXCEED_MAX_SIZE, MAX_TOTAL_SIZE_BYTES));
                // End validation early so we don't do extra work on oversized sets
                break;
            }
            mAdTechIdentifierValidator.addValidation(advertiserString, violations);
        }
    }
}
