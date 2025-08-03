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

import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.Set;

/** Data class representing the custom audience which won an ad selection run . */
@AutoValue
public abstract class WinningCustomAudience {

    /** Winner custom audience owner name, i.e. the caller package which created the CA. */
    @Nullable
    public abstract String getOwner();

    /** Winner custom audience name. */
    @NonNull
    public abstract String getName();

    /** Ad counter keys associated with the winning custom audience ad. */
    @Nullable
    public abstract Set<Integer> getAdCounterKeys();

    /**
     * Creates a {@link WinningCustomAudience} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static WinningCustomAudience create(
            @Nullable String owner, String name, @Nullable Set<Integer> adCounterKeys) {
        return builder().setOwner(owner).setName(name).setAdCounterKeys(adCounterKeys).build();
    }

    /**
     * @return generic builder
     */
    @NonNull
    public static Builder builder() {
        return new AutoValue_WinningCustomAudience.Builder();
    }

    /** Builder for WinningCustomAudience. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Winner custom audience owner name, i.e. the caller package which created the CA. */
        public abstract Builder setOwner(@Nullable String owner);

        /** Sets the winner custom audience name. */
        public abstract Builder setName(@NonNull String name);

        /** Sets the ad counter keys associated with the winning custom audience ad. */
        public abstract Builder setAdCounterKeys(@Nullable Set<Integer> adCounterKeys);

        /** Builds a {@link WinningCustomAudience} object. */
        @NonNull
        public abstract WinningCustomAudience build();
    }
}
