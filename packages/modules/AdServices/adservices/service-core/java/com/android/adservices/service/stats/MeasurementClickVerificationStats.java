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

package com.android.adservices.service.stats;

import android.hardware.input.InputManager;
import android.view.InputEvent;

import com.android.adservices.service.measurement.util.Validation;

import com.google.auto.value.AutoValue;

/** Class for AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION_STATS atom. */
@AutoValue
public abstract class MeasurementClickVerificationStats {
    /**
     * @return Final source type of source registered.
     */
    public abstract int getSourceType();

    /**
     * @return true, if an InputEvent object was present at source registration (meaning the source
     *     was intended to be a navigation).
     */
    public abstract boolean isInputEventPresent();

    /**
     * @return true, if the system could verify the InputEvent with {@link
     *     InputManager#verifyInputEvent(InputEvent)}
     */
    public abstract boolean isSystemClickVerificationSuccessful();

    /**
     * @return true, if the system click verification is enabled.
     */
    public abstract boolean isSystemClickVerificationEnabled();

    /**
     * @return the delay (in millis) from when the input event was created to when the
     *     registerSource API was called.
     */
    public abstract long getInputEventDelayMillis();

    /**
     * @return the max difference (in millis) between input event creation and the API cal for the
     *     source to not get downgraded.
     */
    public abstract long getValidDelayWindowMillis();

    /**
     * @return source registrant.
     */
    public abstract String getSourceRegistrant();

    /**
     * @return true, if click deduplication is enabled (the result is not recorded).
     */
    public abstract boolean isClickDeduplicationEnabled();

    /**
     * @return true, if click deduplication is enforced (the result is recorded but the click is not
     *     demoted if it fails).
     */
    public abstract boolean isClickDeduplicationEnforced();

    /**
     * @return the current limit for the number of sources per click.
     */
    public abstract long getMaxSourcesPerClick();

    /**
     * @return whether the current registration attempt is under the limit.
     */
    public abstract boolean isCurrentRegistrationUnderClickDeduplicationLimit();

    /**
     * @return generic builder.
     */
    public static MeasurementClickVerificationStats.Builder builder() {
        return new AutoValue_MeasurementClickVerificationStats.Builder();
    }

    /** Builder class for {@link MeasurementClickVerificationStats} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set sourceType. */
        public abstract Builder setSourceType(int value);

        /** Set to true, if an InputEvent is present at source registration. */
        public abstract Builder setInputEventPresent(boolean value);

        /** Set to true, if click verification by the system is successful. */
        public abstract Builder setSystemClickVerificationSuccessful(boolean value);

        /** Set to true, if click verification by the system is enabled. */
        public abstract Builder setSystemClickVerificationEnabled(boolean value);

        /**
         * Set the delay from when the input event was created to when the registration occurred.
         */
        public abstract Builder setInputEventDelayMillis(long value);

        /**
         * Set the valid delay window between input event creation and the registration call time.
         */
        public abstract Builder setValidDelayWindowMillis(long value);

        /** Set source registrant. */
        public abstract Builder setSourceRegistrant(String value);

        /** Set to true, if click deduplication is enabled. */
        public abstract Builder setClickDeduplicationEnabled(boolean value);

        /** Set to true, if click deduplication is enforced. */
        public abstract Builder setClickDeduplicationEnforced(boolean value);

        /** Set the current limit for number of sources per click. */
        public abstract Builder setMaxSourcesPerClick(long value);

        /** Set to true, if the current click is under the deduplication limit. */
        public abstract Builder setCurrentRegistrationUnderClickDeduplicationLimit(boolean value);

        /** Auto build for {@link MeasurementClickVerificationStats} */
        abstract MeasurementClickVerificationStats autoBuild();

        /** build for {@link MeasurementClickVerificationStats} */
        public final MeasurementClickVerificationStats build() {
            MeasurementClickVerificationStats stats = autoBuild();
            Validation.validateNonNull(stats.getSourceRegistrant());
            return stats;
        }
    }
}
