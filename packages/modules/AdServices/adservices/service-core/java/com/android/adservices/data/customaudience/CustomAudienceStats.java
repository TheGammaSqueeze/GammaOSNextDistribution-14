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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

/** This class represents the query result for custom audience stats. */
@AutoValue
public abstract class CustomAudienceStats {
    public static final long UNSET_COUNT = -1;

    /** @return the queried owner app's package name, or {@code null} if not set */
    @Nullable
    public abstract String getOwner();

    /** @return the queried buyer ad tech's {@link AdTechIdentifier}, or {@code null} if not set */
    @Nullable
    public abstract AdTechIdentifier getBuyer();

    /**
     * @return the total number of custom audiences in the database, or {@link #UNSET_COUNT} if not
     *     set
     */
    public abstract long getTotalCustomAudienceCount();

    /**
     * @return the number of custom audiences in the database owned by the queried owner app, or
     *     {@link #UNSET_COUNT} if not set
     */
    public abstract long getPerOwnerCustomAudienceCount();

    /**
     * @return the total number of distinct owner apps in the database, or {@link #UNSET_COUNT} if
     *     not set
     */
    public abstract long getTotalOwnerCount();

    /**
     * @return the number of custom audiences in the database associated with the queried buyer ad
     *     tech, or {@link #UNSET_COUNT} if not set
     */
    public abstract long getPerBuyerCustomAudienceCount();

    /**
     * @return the total number of distinct buyer ad techs in the database, or {@link #UNSET_COUNT}
     *     if not set
     */
    public abstract long getTotalBuyerCount();

    /** @return a {@link Builder} for a {@link CustomAudienceStats} object */
    @NonNull
    public static Builder builder() {
        return new AutoValue_CustomAudienceStats.Builder()
                .setTotalCustomAudienceCount(UNSET_COUNT)
                .setPerOwnerCustomAudienceCount(UNSET_COUNT)
                .setTotalOwnerCount(UNSET_COUNT)
                .setPerBuyerCustomAudienceCount(UNSET_COUNT)
                .setTotalBuyerCount(UNSET_COUNT);
    }

    /** Builder class for a {@link CustomAudienceStats} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Sets the owner app's package name which was queried on to create the {@link
         * CustomAudienceStats} object.
         */
        @NonNull
        public abstract Builder setOwner(@Nullable String value);

        /**
         * Sets the buyer ad tech's {@link AdTechIdentifier} which was queried to create the {@link
         * CustomAudienceStats} object.
         */
        @NonNull
        public abstract Builder setBuyer(@Nullable AdTechIdentifier value);

        /** Sets the total number of custom audiences in the database. */
        @NonNull
        public abstract Builder setTotalCustomAudienceCount(long value);

        /** Sets the number of custom audiences in the database owned by the queried owner. */
        @NonNull
        public abstract Builder setPerOwnerCustomAudienceCount(long value);

        /** Sets the total number of distinct owner apps in the database. */
        @NonNull
        public abstract Builder setTotalOwnerCount(long value);

        /**
         * Sets the number of custom audiences in the database associated with the queried buyer ad
         * tech.
         */
        @NonNull
        public abstract Builder setPerBuyerCustomAudienceCount(long value);

        /** Sets the total number of distinct buyer ad techs in the database. */
        @NonNull
        public abstract Builder setTotalBuyerCount(long value);

        /** Builds the {@link CustomAudienceStats} object and returns it. */
        @NonNull
        public abstract CustomAudienceStats build();
    }
}
