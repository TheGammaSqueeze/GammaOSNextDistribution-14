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

package com.android.adservices.service.common.httpclient;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.List;

/** Response sent by {@link AdServicesHttpsClient} when downloading payload */
@AutoValue
public abstract class AdServicesHttpClientResponse {

    /**
     * @return the body of response when a payload is downloaded. This string can be encoded in
     *     UTF-8 or Base64 encoding depending on the {@link AdServicesHttpsClient} method used to
     *     make the HTTP call.
     */
    // TODO(b/323851937): We should return the response body in a byte
    //  array. It will make {@link AdServicesHttpsClient} uniform and will be easy to handle all
    //  different types of responses.
    public abstract String getResponseBody();

    /** @return the response headers associated with a payload download */
    public abstract ImmutableMap<String, List<String>> getResponseHeaders();

    /**
     * Creates an {@link AdServicesHttpClientResponse}
     *
     * @param responseBody see {{@link #getResponseBody()}}
     * @param responseHeaders see {{@link #getResponseHeaders()}}
     * @return a response from downloading a payload
     */
    public static AdServicesHttpClientResponse create(
            String responseBody, ImmutableMap<String, List<String>> responseHeaders) {
        return builder().setResponseBody(responseBody).setResponseHeaders(responseHeaders).build();
    }

    /** @return a builder to create an instance of {@link AdServicesHttpClientResponse} */
    public static AdServicesHttpClientResponse.Builder builder() {
        return new AutoValue_AdServicesHttpClientResponse.Builder()
                .setResponseHeaders(ImmutableMap.of());
    }

    /** A builder to create an instance of {@link AdServicesHttpClientResponse} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** @param responseBody the body for the downloaded payload */
        public abstract AdServicesHttpClientResponse.Builder setResponseBody(String responseBody);

        /** @param responseHeaders response headers associated with a downloaded */
        public abstract AdServicesHttpClientResponse.Builder setResponseHeaders(
                ImmutableMap<String, List<String>> responseHeaders);

        /** @return an instance of {@link AdServicesHttpsClient} */
        public abstract AdServicesHttpClientResponse build();
    }
}
