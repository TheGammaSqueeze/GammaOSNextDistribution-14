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

package com.android.adservices.service.measurement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/** Class to hold measurement http responses */
public class MeasurementHttpResponse {

    private Integer mStatusCode;
    private Map<String, List<String>> mHeaders;
    private String mPayload;

    /** Empty constructor to initialize default values */
    private MeasurementHttpResponse() {
        mHeaders = Map.of();
    }

    /** Returns response status code */
    @NonNull
    public int getStatusCode() {
        return mStatusCode;
    }

    /** Returns response headers */
    @NonNull
    public Map<String, List<String>> getHeaders() {
        return mHeaders;
    }

    /** Returns response payload */
    @Nullable
    public String getPayload() {
        return mPayload;
    }

    /** Builder for {@link MeasurementHttpResponse}. */
    public static final class Builder {

        private final MeasurementHttpResponse mHttpResponse;

        /** Default constructor. */
        public Builder() {
            mHttpResponse = new MeasurementHttpResponse();
        }

        /** See {@link MeasurementHttpResponse#getStatusCode()}. */
        public Builder setStatusCode(int statusCode) {
            mHttpResponse.mStatusCode = statusCode;
            return this;
        }

        /** See {@link MeasurementHttpResponse#getHeaders()}. */
        public Builder setHeaders(Map<String, List<String>> headers) {
            mHttpResponse.mHeaders = headers;
            return this;
        }

        /** See {@link MeasurementHttpResponse#getPayload()}. */
        public Builder setPayload(String payload) {
            mHttpResponse.mPayload = payload;
            return this;
        }

        /** Build the {@link MeasurementHttpResponse}. */
        public MeasurementHttpResponse build() {
            if (mHttpResponse.mStatusCode == null) {
                throw new IllegalArgumentException("Invalid status code");
            }
            return mHttpResponse;
        }
    }
}
