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

import android.adservices.common.AdSelectionSignals;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.Objects;

/** Represents buyer contextual signals that will be passed through buyer JS functions. */
@AutoValue
public abstract class BuyerContextualSignals {
    @Nullable
    abstract AdCost getAdCost();

    @Nullable
    abstract Integer getDataVersion();

    /** Creates a builder for a {@link BuyerContextualSignals} object. */
    public static BuyerContextualSignals.Builder builder() {
        return new AutoValue_BuyerContextualSignals.Builder();
    }

    /** Defines a builder for a {@link BuyerContextualSignals} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the adCost. */
        public abstract Builder setAdCost(@Nullable AdCost adCost);

        public abstract Builder setDataVersion(@Nullable Integer dataVersion);

        /** Builds a {@link BuyerContextualSignals} object. */
        public abstract BuyerContextualSignals build();
    }

    /** Returns {@link BuyerContextualSignals} in a JSON format */
    @Override
    public final String toString() {
        return "{"
                + (Objects.nonNull(getAdCost()) ? "\"adCost\":" + getAdCost() : "")
                + (Objects.nonNull(getDataVersion())
                        ? "\"dataVersion\":" + getDataVersion().toString()
                        : "")
                + "}";
    }

    public AdSelectionSignals toAdSelectionSignals() {
        return AdSelectionSignals.fromString(toString());
    }
}
