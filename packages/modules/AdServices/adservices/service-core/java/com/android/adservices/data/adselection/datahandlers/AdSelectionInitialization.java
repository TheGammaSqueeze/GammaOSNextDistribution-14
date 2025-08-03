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

package com.android.adservices.data.adselection.datahandlers;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;


import com.google.auto.value.AutoValue;

import java.time.Instant;

/** Data class representing an ad selection run instantiation. */
@AutoValue
public abstract class AdSelectionInitialization {

    /** AdTech initiating this ad selection run. */
    @NonNull
    public abstract AdTechIdentifier getSeller();

    /** Caller package name initiating this ad selection run. */
    @NonNull
    public abstract String getCallerPackageName();

    /** The creation time for this ad selection run. */
    @NonNull
    public abstract Instant getCreationInstant();

    /**
     * @return generic builder
     */
    @NonNull
    public static Builder builder() {
        return new AutoValue_AdSelectionInitialization.Builder();
    }

    /**
     * Creates a {@link AdSelectionInitialization} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static AdSelectionInitialization create(
            @NonNull AdTechIdentifier seller,
            @NonNull String callerPackageName,
            @NonNull Instant creationInstant) {
        return builder()
                .setSeller(seller)
                .setCallerPackageName(callerPackageName)
                .setCreationInstant(creationInstant)
                .build();
    }

    /** Builder for AdSelectionInitialization. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the AdTech initiating this ad selection run. */
        public abstract Builder setSeller(@NonNull AdTechIdentifier seller);

        /** Sets the caller package name initiating this ad selection run. */
        public abstract Builder setCallerPackageName(@NonNull String callerPackageName);

        /** Sets the caller package name initiating this ad selection run. */
        public abstract Builder setCreationInstant(@NonNull Instant creationInstant);

        /** Builds a {@link AdSelectionInitialization} object. */
        @NonNull
        public abstract AdSelectionInitialization build();
    }
}
