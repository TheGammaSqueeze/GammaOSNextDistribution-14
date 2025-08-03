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

import android.annotation.NonNull;

import java.util.Objects;

/** class for measurement delayed source registration stats. */
public class MeasurementDelayedSourceRegistrationStats {
    private int mCode;
    private int mRegistrationStatus;
    private long mRegistrationDelay;
    private String mRegistrant;

    public MeasurementDelayedSourceRegistrationStats() {}

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MeasurementDelayedSourceRegistrationStats)) {
            return false;
        }
        MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats =
                (MeasurementDelayedSourceRegistrationStats) obj;
        return mCode == measurementDelayedSourceRegistrationStats.getCode()
                && mRegistrationStatus
                        == measurementDelayedSourceRegistrationStats.getRegistrationStatus()
                && mRegistrationDelay
                        == measurementDelayedSourceRegistrationStats.getRegistrationDelay()
                && Objects.equals(
                        mRegistrant, measurementDelayedSourceRegistrationStats.mRegistrant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCode, mRegistrationStatus, mRegistrationDelay, mRegistrant);
    }

    public int getCode() {
        return mCode;
    }

    public int getRegistrationStatus() {
        return mRegistrationStatus;
    }

    public long getRegistrationDelay() {
        return mRegistrationDelay;
    }

    public String getRegistrant() {
        return mRegistrant;
    }

    /** Builder for {@link MeasurementDelayedSourceRegistrationStats}. */
    public static final class Builder {
        private final MeasurementDelayedSourceRegistrationStats mBuilding;

        public Builder() {
            mBuilding = new MeasurementDelayedSourceRegistrationStats();
        }

        /** See {@link MeasurementDelayedSourceRegistrationStats#getCode()} . */
        public @NonNull MeasurementDelayedSourceRegistrationStats.Builder setCode(int code) {
            mBuilding.mCode = code;
            return this;
        }

        /** See {@link MeasurementDelayedSourceRegistrationStats#getRegistrationStatus()} . */
        public @NonNull MeasurementDelayedSourceRegistrationStats.Builder setRegistrationStatus(
                int registrationStatus) {
            mBuilding.mRegistrationStatus = registrationStatus;
            return this;
        }

        /** See {@link MeasurementDelayedSourceRegistrationStats#getRegistrationDelay()} . */
        public @NonNull MeasurementDelayedSourceRegistrationStats.Builder setRegistrationDelay(
                long registrationDelay) {
            mBuilding.mRegistrationDelay = registrationDelay;
            return this;
        }

        /** See {@link MeasurementDelayedSourceRegistrationStats#getRegistrant()}. */
        public @NonNull MeasurementDelayedSourceRegistrationStats.Builder setRegistrant(
                String registrant) {
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /** Build the {@link MeasurementDelayedSourceRegistrationStats}. */
        public @NonNull MeasurementDelayedSourceRegistrationStats build() {
            return mBuilding;
        }
    }
}
