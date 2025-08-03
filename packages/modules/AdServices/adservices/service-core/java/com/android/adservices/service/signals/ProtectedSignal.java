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

package com.android.adservices.service.signals;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/** Internal representation of a custom protected signal's value and metadata. */
@AutoValue
public abstract class ProtectedSignal {

    // 60 days
    public static final int EXPIRATION_SECONDS = 60 * 24 * 60 * 60;

    /**
     * @return The value of this signal in the form of base 64 encoded string
     */
    public abstract String getBase64EncodedValue();

    /**
     * @return The time the signal was creation
     */
    public abstract Instant getCreationTime();

    /**
     * @return The package name that created the signal.
     */
    public abstract String getPackageName();

    static Builder builder() {
        return new AutoValue_ProtectedSignal.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setBase64EncodedValue(String value);

        abstract Builder setCreationTime(Instant creationTime);

        abstract Builder setPackageName(String packageName);

        abstract ProtectedSignal build();
    }
}
