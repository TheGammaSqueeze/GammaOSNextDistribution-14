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

package com.android.server.wifi.entitlement.http;

import com.android.libraries.entitlement.http.HttpConstants.ContentType;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * The response of the HTTP request.
 */
@AutoValue
public abstract class HttpResponse {
    /**
     * Content type of the response.
     */
    public abstract int contentType();

    /**
     * Returns the body of the HttpResponse.
     */
    public abstract String body();

    /**
     * Returns the response code of the HttpResponse.
     */
    public abstract int responseCode();

    /**
     * Returns the response message of the HttpResponse.
     */
    public abstract String responseMessage();

    /**
     * Content of the "Set-Cookie" response header.
     */
    public abstract ImmutableList<String> cookies();

    /**
     * Builder of {@link HttpResponse}.
     */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Builds a HttpResponse object. */
        public abstract HttpResponse build();

        /**
         * Set the content type of the response to the {@link HttpResponse#Builder}.
         */
        public abstract Builder setContentType(int contentType);

        /**
         * Set the response body to the {@link HttpResponse#Builder}.
         */
        public abstract Builder setBody(String body);

        /**
         * Set the response code to the {@link HttpResponse#Builder}.
         */
        public abstract Builder setResponseCode(int responseCode);

        /**
         * Set the response message to the {@link HttpResponse#Builder}.
         */
        public abstract Builder setResponseMessage(String responseMessage);

        /**
         * Sets the content of the "Set-Cookie" response headers.
         */
        public abstract Builder setCookies(List<String> cookies);
    }

    /**
     * Generates a {@link HttpResponse#Builder}.
     */
    public static Builder builder() {
        return new AutoValue_HttpResponse.Builder()
                .setContentType(ContentType.UNKNOWN)
                .setBody("")
                .setResponseCode(0)
                .setResponseMessage("")
                .setCookies(ImmutableList.of());
    }

    /**
     * Returns a short string representation for debugging purposes. Doesn't include the cookie or
     * full body to prevent leaking sensitive data.
     */
    public String toShortDebugString() {
        return new StringBuilder("HttpResponse{")
                .append("contentType=")
                .append(contentType())
                .append(" body=(")
                .append(body().length())
                .append(" characters)")
                .append(" responseCode=")
                .append(responseCode())
                .append(" responseMessage=")
                .append(responseMessage())
                .append(" cookies=[")
                .append(cookies().size())
                .append(" cookies]}")
                .toString();
    }
}
