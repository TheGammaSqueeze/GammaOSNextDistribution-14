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
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_CURRENT_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;

import android.annotation.NonNull;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/** Validator for the {@code expirationTime} field of a Custom Audience. */
public class CustomAudienceExpirationTimeValidator {
    @NonNull private final Clock mClock;
    @NonNull private final Duration mCustomAudienceMaxExpireIn;

    public CustomAudienceExpirationTimeValidator(
            @NonNull Clock clock, @NonNull Duration customAudienceMaxExpireIn) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(customAudienceMaxExpireIn);

        mClock = clock;
        mCustomAudienceMaxExpireIn = customAudienceMaxExpireIn;
    }

    /**
     * Validates the {@code expirationTime} field of a Custom Audience against multiple parameters.
     *
     * @param expirationTime the {@code expirationTime} field of a Custom Audience.
     * @param calculatedActivationTime the reference {@code activationTime} for comparison.
     * @throws IllegalArgumentException if any violation is found
     */
    public void validate(@NonNull Instant expirationTime, @NonNull Instant calculatedActivationTime)
            throws IllegalArgumentException {
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(calculatedActivationTime);

        Collection<String> violations =
                getValidationViolations(expirationTime, calculatedActivationTime);
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
     * @param expirationTime the {@code expirationTime} field of a Custom Audience.
     * @param calculatedActivationTime the reference {@code activationTime} for comparison.
     * @return the {@code Collection<String>} of violations found.
     */
    public Collection<String> getValidationViolations(
            @NonNull Instant expirationTime, @NonNull Instant calculatedActivationTime) {
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(calculatedActivationTime);

        ImmutableCollection.Builder<String> violations = new ImmutableList.Builder<>();
        addValidation(expirationTime, calculatedActivationTime, violations);
        return violations.build();
    }

    /**
     * Validates the {@code expirationTime} field of a Custom Audience as follows:
     *
     * <ul>
     *   <li>{@code expirationTime} is in the future
     *   <li>{@code expirationTime} is after the {@code calculatedActivationTime}
     *   <li>{@code expirationTime} is within {@code customAudienceMaxExpireIn} duration (set while
     *       instantiating a {@link CustomAudienceExpirationTimeValidator}) after the {@code
     *       calculatedActivationTime}
     * </ul>
     *
     * @param expirationTime the {@code expirationTime} field of a Custom Audience.
     * @param calculatedActivationTime the reference {@code activationTime} for comparison.
     * @param violations the collection of violations to add to.
     */
    public void addValidation(
            @NonNull Instant expirationTime,
            @NonNull Instant calculatedActivationTime,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(calculatedActivationTime);
        Objects.requireNonNull(violations);

        Instant currentTime = mClock.instant();

        if (!expirationTime.isAfter(currentTime)) {
            violations.add(
                    String.format(
                            Locale.ENGLISH, VIOLATION_EXPIRE_BEFORE_CURRENT_TIME, expirationTime));

        } else if (!expirationTime.isAfter(calculatedActivationTime)) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                            calculatedActivationTime,
                            expirationTime));
        } else if (expirationTime.isAfter(
                calculatedActivationTime.plus(mCustomAudienceMaxExpireIn))) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                            mCustomAudienceMaxExpireIn,
                            calculatedActivationTime,
                            currentTime,
                            expirationTime));
        }
    }
}
