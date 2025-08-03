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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;

import java.time.Instant;
import java.util.Objects;

/** This POJO represents the schema for the background fetch data for custom audiences. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = DBCustomAudienceBackgroundFetchData.TABLE_NAME,
        primaryKeys = {"owner", "buyer", "name"},
        inheritSuperIndices = true)
public abstract class DBCustomAudienceBackgroundFetchData {
    public static final String TABLE_NAME = "custom_audience_background_fetch_data";

    /** @return the owner package name of the custom audience */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "owner", index = true)
    @NonNull
    public abstract String getOwner();

    /** @return the buyer for the custom audience */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** @return the name of the custom audience */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "name", index = true)
    @NonNull
    public abstract String getName();

    /** @return the daily update URI for the custom audience */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "daily_update_uri")
    @NonNull
    public abstract Uri getDailyUpdateUri();

    /** @return the time after which the specified custom audience is eligible to be updated */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "eligible_update_time", index = true)
    @NonNull
    public abstract Instant getEligibleUpdateTime();

    /**
     * @return the number of failures since the last successful update caused by response validation
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "num_validation_failures")
    public abstract long getNumValidationFailures();

    /** @return the number of failures since the last successful update caused by fetch timeouts */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "num_timeout_failures")
    public abstract long getNumTimeoutFailures();

    /**
     * @return if the custom audience is considered debuggable (i.e. was created by an app where
     *     android:debuggable="true" while developer options are enabled.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "is_debuggable", defaultValue = "false")
    public abstract boolean getIsDebuggable();

    /** @return an AutoValue builder for a {@link DBCustomAudienceBackgroundFetchData} object */
    @NonNull
    public static DBCustomAudienceBackgroundFetchData.Builder builder() {
        return new AutoValue_DBCustomAudienceBackgroundFetchData.Builder()
                .setNumValidationFailures(0)
                .setNumTimeoutFailures(0)
                .setIsDebuggable(false);
    }

    /**
     * Creates a {@link DBCustomAudienceBackgroundFetchData} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBCustomAudienceBackgroundFetchData create(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull Uri dailyUpdateUri,
            @NonNull Instant eligibleUpdateTime,
            long numValidationFailures,
            long numTimeoutFailures,
            boolean debuggable) {
        return builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setName(name)
                .setDailyUpdateUri(dailyUpdateUri)
                .setEligibleUpdateTime(eligibleUpdateTime)
                .setNumValidationFailures(numValidationFailures)
                .setNumTimeoutFailures(numTimeoutFailures)
                .setIsDebuggable(debuggable)
                .build();
    }

    /**
     * Computes the next eligible update time, given the most recent successful update time and
     * flags.
     *
     * <p>This method is split out for testing because testing with P/H flags in a static method
     * requires non-trivial permissions in test applications.
     */
    @VisibleForTesting
    @NonNull
    public static Instant computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
            @NonNull Instant successfulUpdateTime, @NonNull Flags flags) {
        Objects.requireNonNull(successfulUpdateTime);
        Objects.requireNonNull(flags);

        // Successful updates are next eligible in base interval (one day) + jitter
        // TODO(b/221861706): Implement jitter
        return successfulUpdateTime.plusSeconds(
                flags.getFledgeBackgroundFetchEligibleUpdateBaseIntervalS());
    }

    /** Computes the next eligible update time, given the most recent successful update time. */
    @NonNull
    public static Instant computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
            @NonNull Instant successfulUpdateTime) {
        return computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                successfulUpdateTime, FlagsFactory.getFlags());
    }

    /**
     * Creates a copy of the current object with an updated failure count based on the input failure
     * type.
     */
    @NonNull
    public final DBCustomAudienceBackgroundFetchData copyWithUpdatableData(
            @NonNull CustomAudienceUpdatableData updatableData) {
        // Create a builder with a full copy of the current object
        DBCustomAudienceBackgroundFetchData.Builder fetchDataBuilder =
                builder()
                        .setOwner(getOwner())
                        .setBuyer(getBuyer())
                        .setName(getName())
                        .setDailyUpdateUri(getDailyUpdateUri())
                        .setEligibleUpdateTime(getEligibleUpdateTime())
                        .setNumValidationFailures(getNumValidationFailures())
                        .setNumTimeoutFailures(getNumTimeoutFailures())
                        .setIsDebuggable(getIsDebuggable());

        if (updatableData.getContainsSuccessfulUpdate()) {
            fetchDataBuilder.setEligibleUpdateTime(
                    computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                            updatableData.getAttemptedUpdateTime()));

            // Reset all failure counts on any successful update
            fetchDataBuilder.setNumValidationFailures(0).setNumTimeoutFailures(0);
        } else {
            switch (updatableData.getInitialUpdateResult()) {
                case SUCCESS:
                    // This success result is only the result set prior to syntax validation of the
                    // response, so an error must have occurred during data validation
                    // INTENTIONAL FALLTHROUGH
                case RESPONSE_VALIDATION_FAILURE:
                    fetchDataBuilder.setNumValidationFailures(getNumValidationFailures() + 1);
                    break;
                case NETWORK_FAILURE:
                    // TODO(b/221861706): Consider differentiating timeout failures for fairness
                    // TODO(b/237342352): Consolidate timeout failures if they don't need to be
                    //  distinguished
                    // INTENTIONAL FALLTHROUGH
                case NETWORK_READ_TIMEOUT_FAILURE:
                    fetchDataBuilder.setNumTimeoutFailures(getNumTimeoutFailures() + 1);
                    break;
                case K_ANON_FAILURE:
                    // TODO(b/234884352): Implement k-anon check
                    // INTENTIONAL FALLTHROUGH
                case UNKNOWN:
                    // Treat this as a benign failure, so we can just try again
                    break;
            }

            // TODO(b/221861706): Decide whether this custom audience is delinquent and set its next
            //  eligible update time
            // TODO(b/221861706): Implement jitter for delinquent updates

            // Non-delinquent failed updates are immediately eligible to be updated in the next job
            fetchDataBuilder.setEligibleUpdateTime(getEligibleUpdateTime());
        }

        return fetchDataBuilder.build();
    }

    /** Builder class for a {@link DBCustomAudienceBackgroundFetchData} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the owner package name for the custom audience. */
        @NonNull
        public abstract Builder setOwner(@NonNull String value);

        /** Sets the buyer for the custom audience. */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /** Sets the name for the custom audience. */
        @NonNull
        public abstract Builder setName(@NonNull String value);

        /** Sets the daily update URI for the custom audience. */
        @NonNull
        public abstract Builder setDailyUpdateUri(@NonNull Uri value);

        /** Sets the time after which the custom audience will be eligible for update. */
        @NonNull
        public abstract Builder setEligibleUpdateTime(@NonNull Instant value);

        /**
         * Sets the number of failures due to response validation for the custom audience since the
         * last successful update.
         */
        @NonNull
        public abstract Builder setNumValidationFailures(long value);

        /**
         * Sets the number of failures due to fetch timeout for the custom audience since the last
         * successful update.
         */
        @NonNull
        public abstract Builder setNumTimeoutFailures(long value);

        /** Sets if the custom audience was created in a debuggable context. */
        @NonNull
        public abstract Builder setIsDebuggable(boolean value);

        /**
         * Builds the {@link DBCustomAudienceBackgroundFetchData} object and returns it.
         *
         * <p>Note that AutoValue doesn't by itself do any validation, so splitting the builder with
         * a manual verification is recommended. See go/autovalue/builders-howto#validate for more
         * information.
         */
        @NonNull
        protected abstract DBCustomAudienceBackgroundFetchData autoValueBuild();

        /**
         * Builds, validates, and returns the {@link DBCustomAudienceBackgroundFetchData} object.
         */
        @NonNull
        public final DBCustomAudienceBackgroundFetchData build() {
            DBCustomAudienceBackgroundFetchData fetchData = autoValueBuild();

            // Fields marked @NonNull are already validated by AutoValue
            Preconditions.checkArgument(
                    fetchData.getNumValidationFailures() >= 0
                            && fetchData.getNumTimeoutFailures() >= 0,
                    "Update failure count must be non-negative");

            return fetchData;
        }
    }
}
