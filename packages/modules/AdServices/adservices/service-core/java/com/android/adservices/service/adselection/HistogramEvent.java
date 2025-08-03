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
import android.adservices.common.FrequencyCapFilters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.time.Instant;
import java.util.Objects;

/**
 * A value class representing a single histogram event.
 *
 * <p>Ad events are registered with FLEDGE in order to update internal event histograms which are
 * used during ad selection to filter out ads with frequency cap filters.
 */
@AutoValue
public abstract class HistogramEvent {
    /**
     * Returns the arbitrary int representing a grouping that a buyer adtech has assigned to an ad
     * or histogram.
     */
    @NonNull
    public abstract int getAdCounterKey();

    /** Returns the histogram's buyer adtech's {@link AdTechIdentifier}. */
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** Returns the owner package name of the custom audience the histogram is associated with. */
    @Nullable
    public abstract String getCustomAudienceOwner();

    /** Returns the name of the custom audience the histogram is associated with. */
    @Nullable
    public abstract String getCustomAudienceName();

    /** Returns the enumerated type of the ad event. */
    @FrequencyCapFilters.AdEventType
    public abstract int getAdEventType();

    /** Returns the timestamp for the event. */
    @NonNull
    public abstract Instant getTimestamp();

    /** Returns the package name of the source app the histogram is being updated from. */
    @NonNull
    public abstract String getSourceApp();

    /** Returns an AutoValue builder for a {@link HistogramEvent} object. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_HistogramEvent.Builder();
    }

    /** Builder class for a {@link HistogramEvent} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Sets the arbitrary int representing a grouping that a buyer adtech has assigned to an ad
         * or histogram.
         */
        @NonNull
        public abstract Builder setAdCounterKey(int adCounterKey);

        /** Sets the histogram's buyer adtech's {@link AdTechIdentifier}. */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier buyer);

        /** Sets the owner package name of the custom audience the histogram is associated with. */
        @NonNull
        public abstract Builder setCustomAudienceOwner(@Nullable String customAudienceOwner);

        /** Sets the name of the custom audience the histogram is associated with. */
        @NonNull
        public abstract Builder setCustomAudienceName(@Nullable String customAudienceName);

        /** Sets the enumerated type of the ad event. */
        @NonNull
        public abstract Builder setAdEventType(@FrequencyCapFilters.AdEventType int adEventType);

        /** Sets the timestamp for the event. */
        @NonNull
        public abstract Builder setTimestamp(@NonNull Instant timestamp);

        /** Sets the package name of the source app the histogram is being updated from. */
        @NonNull
        public abstract Builder setSourceApp(@NonNull String sourceApp);

        /**
         * Builds and returns the {@link HistogramEvent} object.
         *
         * <p>Note that AutoValue doesn't by itself do any validation, so splitting the builder with
         * a manual verification is recommended.
         *
         * @throws IllegalStateException if any required field is unset when the object is built
         */
        @NonNull
        abstract HistogramEvent autoValueBuild();

        /**
         * Builds, validates, and returns the {@link HistogramEvent} object.
         *
         * @throws IllegalStateException if any required field is unset when the object is built
         * @throws NullPointerException if any custom audience field is unset when the object is
         *     built with a {@link FrequencyCapFilters#AD_EVENT_TYPE_WIN} event type
         */
        @NonNull
        public HistogramEvent build() {
            HistogramEvent event = autoValueBuild();

            // Win-typed events must be scoped to a custom audience
            if (event.getAdEventType() == FrequencyCapFilters.AD_EVENT_TYPE_WIN) {
                Objects.requireNonNull(
                        event.getCustomAudienceOwner(),
                        "Custom audience owner must not be null for WIN events");
                Objects.requireNonNull(
                        event.getCustomAudienceName(),
                        "Custom audience name must not be null for WIN events");
            }

            return event;
        }
    }
}
