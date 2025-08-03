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

import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG;

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;

import com.android.adservices.service.common.JsonValidator;
import com.android.adservices.service.common.Validator;

import com.google.common.collect.ImmutableCollection;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

/**
 * Validates the {@code userBiddingSignals} field (represented as {@link AdSelectionSignals}) of a
 * Custom Audience.
 */
public class CustomAudienceUserBiddingSignalsValidator implements Validator<AdSelectionSignals> {
    private final int mCustomAudienceMaxUserBiddingSignalsSizeB;
    @NonNull private final JsonValidator mJsonValidator;

    public CustomAudienceUserBiddingSignalsValidator(
            @NonNull JsonValidator jsonValidator, int customAudienceMaxUserBiddingSignalsSizeB) {
        Objects.requireNonNull(jsonValidator);

        mJsonValidator = jsonValidator;
        mCustomAudienceMaxUserBiddingSignalsSizeB = customAudienceMaxUserBiddingSignalsSizeB;
    }

    /**
     * Validates the {@code userBiddingSignals} field (represented as {@link AdSelectionSignals}) of
     * a Custom Audience as follows:
     *
     * <ul>
     *   <li>{@link JSONObject} is well-formed
     *   <li>Size is less than {@code customAudienceMaxUserBiddingSignalsSizeB} (set while
     *       instantiating a {@link CustomAudienceUserBiddingSignalsValidator})
     * </ul>
     *
     * @param userBiddingSignals the {@code userBiddingSignals} field (represented as {@link
     *     AdSelectionSignals} of a Custom Audience.
     * @param violations the collection of violations to add to.
     */
    @Override
    public void addValidation(
            @NonNull AdSelectionSignals userBiddingSignals,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(userBiddingSignals);
        Objects.requireNonNull(violations);

        // Validate json is well-formed.
        mJsonValidator.addValidation(userBiddingSignals.toString(), violations);

        // Validate json's size is within limits.
        int userBiddingSignalSizeB = userBiddingSignals.getSizeInBytes();
        if (userBiddingSignalSizeB > mCustomAudienceMaxUserBiddingSignalsSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                            mCustomAudienceMaxUserBiddingSignalsSizeB,
                            userBiddingSignalSizeB));
        }
    }
}
