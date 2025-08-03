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

import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE;

import android.annotation.NonNull;

import com.android.adservices.service.common.Validator;

import com.google.common.collect.ImmutableCollection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Validator for the {@code activationTime} field of a Custom Audience. */
public class CustomAudienceActivationTimeValidator implements Validator<Instant> {
    @NonNull private final Clock mClock;
    @NonNull private final Duration mCustomAudienceMaxActivationDelay;

    public CustomAudienceActivationTimeValidator(
            @NonNull Clock clock, @NonNull Duration customAudienceMaxActivationDelay) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(customAudienceMaxActivationDelay);

        mClock = clock;
        mCustomAudienceMaxActivationDelay = customAudienceMaxActivationDelay;
    }

    /**
     * Validates the {@code activationTime} field of a Custom Audience as follows:
     *
     * <ul>
     *   <li>{@code activationTime} is within {@code customAudienceMaxActivationDelay} duration (set
     *       while instantiating a {@link CustomAudienceActivationTimeValidator}) from {@link
     *       Clock#instant()}
     * </ul>
     *
     * @param activationTime the {@code activationTime} field of a Custom Audience.
     * @param violations the collection of violations to add to.
     */
    @Override
    public void addValidation(
            @NonNull Instant activationTime,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(activationTime);
        Objects.requireNonNull(violations);

        // Calculate a valid activation time.
        Instant calculatedActivationTime;
        Instant currentTime = mClock.instant();
        if (activationTime.isBefore(currentTime)) {
            calculatedActivationTime = currentTime;
        } else {
            calculatedActivationTime = activationTime;
        }

        // Validate activation time is within limits.
        Instant maxActivationTime = currentTime.plus(mCustomAudienceMaxActivationDelay);
        if (calculatedActivationTime.isAfter(maxActivationTime)) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                            mCustomAudienceMaxActivationDelay,
                            currentTime,
                            calculatedActivationTime));
        }
    }
}
