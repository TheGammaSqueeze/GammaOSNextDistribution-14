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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.Frc9000VariableLengthIntegerUtil.toFrc9000Int;

import android.annotation.NonNull;

import com.google.auto.value.AutoValue;

import java.nio.charset.StandardCharsets;

/**
 * The control data for a request message contains the method and request target. That information
 * is encoded as an ordered sequence of fields: Method, Scheme, Authority, Path. Each of these
 * fields is prefixed with a length.
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-request-control-data">Binary
 *     HTTP Request Control Data</a>
 */
@AutoValue
public abstract class RequestControlData extends ControlData {
    private static final int SECTIONS_COUNT = 8;

    /** Returns the method of the request. */
    @NonNull
    public abstract String getMethod();

    /** Returns the scheme of the request. */
    @NonNull
    public abstract String getScheme();

    /** Returns the authority of the request. */
    @NonNull
    public abstract String getAuthority();

    /** Returns the path of the request. */
    @NonNull
    public abstract String getPath();

    @Override
    int getKnownLengthSerializedSectionsCount() {
        return SECTIONS_COUNT;
    }

    /**
     * {@inheritDoc}
     *
     * @return [method][scheme][authority][path]
     */
    @Override
    @NonNull
    byte[][] knownLengthSerialize() {
        byte[] method = getMethod().getBytes(StandardCharsets.UTF_8);
        byte[] scheme = getScheme().getBytes(StandardCharsets.UTF_8);
        byte[] authority = getAuthority().getBytes(StandardCharsets.UTF_8);
        byte[] path = getPath().getBytes(StandardCharsets.UTF_8);

        return new byte[][] {
            toFrc9000Int(method.length),
            method,
            toFrc9000Int(scheme.length),
            scheme,
            toFrc9000Int(authority.length),
            authority,
            toFrc9000Int(path.length),
            path
        };
    }

    /** Get a builder for request control data. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_RequestControlData.Builder();
    }

    /** Builder for {@link RequestControlData}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the method of the request. */
        @NonNull
        public abstract Builder setMethod(@NonNull String value);

        /** Sets the scheme of the request. */
        @NonNull
        public abstract Builder setScheme(@NonNull String value);

        /** Sets the authority of the request. */
        @NonNull
        public abstract Builder setAuthority(@NonNull String value);

        /** Sets the path of the request. */
        @NonNull
        public abstract Builder setPath(@NonNull String value);

        /** Returns the request control data built. */
        @NonNull
        public abstract RequestControlData build();
    }
}
