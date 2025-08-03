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

import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Validates custom audience activation and expiration time */
public class CustomAudienceTimestampValidator implements Validator<CustomAudience> {
    @VisibleForTesting
    static final String VIOLATION_EXPIRE_BEFORE_CURRENT_TIME =
            "Custom audience must expire in the future, provided time is %s.";

    @VisibleForTesting
    static final String VIOLATION_EXPIRE_BEFORE_ACTIVATION =
            "Custom audience activation time %s should not be after expiration time %s.";

    @VisibleForTesting
    static final String VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME =
            "Custom audience expiration time should be within %s of activation time %s or"
                    + " current time %s, which ever is later, provided time is %s.";

    @VisibleForTesting
    static final String VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE =
            "Custom audience activation time should be within %s from current time %s, "
                    + "provided time is %s.";

    @NonNull private final Clock mClock;
    @NonNull private final Duration mCustomAudienceMaxActivateIn;
    @NonNull private final Duration mCustomAudienceMaxExpireIn;

    public CustomAudienceTimestampValidator(@NonNull Clock clock, @NonNull Flags flags) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(flags);

        mClock = clock;
        mCustomAudienceMaxActivateIn =
                Duration.ofMillis(flags.getFledgeCustomAudienceMaxActivationDelayInMs());
        mCustomAudienceMaxExpireIn =
                Duration.ofMillis(flags.getFledgeCustomAudienceMaxExpireInMs());
    }

    /**
     * Validates custom audience activation time and expiration time.
     *
     * <ol>
     *   <li>The activation delay should not beyond limit.
     *   <li>The expiration time must be later then custom audience activation time.
     *   <li>The expiration time must be in the future.
     *   <li>The expiration time should not beyond certain time from activation.
     * </ol>
     *
     * @param customAudience The custom audience need to be validated.
     */
    @Override
    public void addValidation(
            @NonNull CustomAudience customAudience,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(violations);

        Instant currentTime = mClock.instant();
        Instant activationTime = customAudience.getActivationTime();
        Instant calculatedActivationTime;
        if (activationTime == null || activationTime.isBefore(currentTime)) {
            calculatedActivationTime = currentTime;
        } else {
            calculatedActivationTime = activationTime;
        }
        Instant maxActivationTime = currentTime.plus(mCustomAudienceMaxActivateIn);
        if (calculatedActivationTime.isAfter(maxActivationTime)) {
            violations.add(
                    String.format(
                            VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                            mCustomAudienceMaxActivateIn,
                            currentTime,
                            calculatedActivationTime));
        }
        Instant expirationTime = customAudience.getExpirationTime();
        if (expirationTime != null) {
            if (!expirationTime.isAfter(currentTime)) {
                violations.add(String.format(VIOLATION_EXPIRE_BEFORE_CURRENT_TIME, expirationTime));
            } else if (!expirationTime.isAfter(calculatedActivationTime)) {
                violations.add(
                        String.format(
                                VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                                calculatedActivationTime,
                                expirationTime));
            } else if (expirationTime.isAfter(
                    calculatedActivationTime.plus(mCustomAudienceMaxExpireIn))) {
                violations.add(
                        String.format(
                                VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                                mCustomAudienceMaxExpireIn,
                                calculatedActivationTime,
                                currentTime,
                                expirationTime));
            }
        }
    }
}
