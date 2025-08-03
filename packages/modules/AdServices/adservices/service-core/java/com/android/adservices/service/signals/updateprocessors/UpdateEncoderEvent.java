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

package com.android.adservices.service.signals.updateprocessors;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

/** Event corresponding to buyer updating their encoder endpoint during signals fetch */
@AutoValue
public abstract class UpdateEncoderEvent {

    /**
     * @return an {@link UpdateType} which is part of finite enums of recognized events
     */
    @NonNull
    public abstract UpdateType getUpdateType();

    /**
     * @return the endpoint updated in the event
     */
    @Nullable
    public abstract Uri getEncoderEndpointUri();

    /**
     * @return a builder to build {@link UpdateEncoderEvent}
     */
    public static Builder builder() {
        return new AutoValue_UpdateEncoderEvent.Builder();
    }

    /** A builder to build {@link UpdateEncoderEvent} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** see {@link #getUpdateType()} for more details */
        public abstract Builder setUpdateType(@NonNull UpdateType action);

        /** see {@link #getEncoderEndpointUri()} for more details */
        public abstract Builder setEncoderEndpointUri(@Nullable Uri endpointUri);

        /**
         * @return an instance of {@link UpdateEncoderEvent}
         *     <p>Throws NPE in case the object is attempted to be built with null fields where not
         *     expected
         */
        public abstract UpdateEncoderEvent build();
    }

    public enum UpdateType {
        REGISTER
    }
}
