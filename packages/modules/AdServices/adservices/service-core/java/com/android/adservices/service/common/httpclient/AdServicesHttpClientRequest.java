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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.service.common.httpclient.AdServicesHttpUtil.HttpMethodType;
import com.android.adservices.service.devapi.DevContext;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Input to the {@link AdServicesHttpsClient}, which makes request to download content */
@AutoValue
public abstract class AdServicesHttpClientRequest {

    /** @return uri that is used to make the request. */
    public abstract Uri getUri();

    /** @return request properties that need to be piggybacked to the url connection */
    public abstract ImmutableMap<String, String> getRequestProperties();

    /** @return set of keys that we want in the {@link AdServicesHttpClientResponse} */
    public abstract ImmutableSet<String> getResponseHeaderKeys();

    /** @return boolean is the results should be cached or not */
    public abstract boolean getUseCache();

    /** @return The http method type {@link HttpMethodType} */
    public abstract HttpMethodType getHttpMethodType();

    /** @return The byte array that will be used as a request body for this request.  */
    @SuppressWarnings("mutable")
    public abstract byte[] getBodyInBytes();

    /** @return DevContext associated with this call. */
    @NonNull
    public abstract DevContext getDevContext();

    /**
     * @param uri see {@link #getUri()}
     * @param requestProperties see {@link #getRequestProperties()}
     * @param responseHeaderKeys see {@link #getResponseHeaderKeys()}
     * @param useCache see {@link #getUseCache()}
     * @param httpMethodType see {@link #getHttpMethodType()}
     * @param body see {@link #getBodyInBytes()}. This must be an empty array of bytes for GET
     *     request.
     * @return an instance of {@link AdServicesHttpClientRequest}
     */
    public static AdServicesHttpClientRequest create(
            Uri uri,
            ImmutableMap<String, String> requestProperties,
            ImmutableSet<String> responseHeaderKeys,
            boolean useCache,
            @NonNull DevContext devContext,
            HttpMethodType httpMethodType,
            byte[] body) {
        if (body.length > 0) {
            if (httpMethodType != HttpMethodType.POST) {
                throw new IllegalArgumentException(
                        "Request method does not allow request mBody: " + httpMethodType);
            }
        }
        return builder()
                .setUri(uri)
                .setRequestProperties(requestProperties)
                .setResponseHeaderKeys(responseHeaderKeys)
                .setUseCache(useCache)
                .setDevContext(devContext)
                .setBodyInBytes(body)
                .setHttpMethodType(httpMethodType)
                .build();
    }

    /** @return a builder that can be used to build an {@link AdServicesHttpClientRequest} */
    public static AdServicesHttpClientRequest.Builder builder() {
        return new AutoValue_AdServicesHttpClientRequest.Builder()
                .setRequestProperties(ImmutableMap.of())
                .setResponseHeaderKeys(ImmutableSet.of())
                .setUseCache(false)
                .setHttpMethodType(HttpMethodType.GET)
                .setBodyInBytes(new byte[0]);
    }

    /** Builder that cane be used to build an {@link AdServicesHttpClientRequest} */
    @AutoValue.Builder
    public abstract static class Builder {

        /**
         * @param uri that is used to make the request
         */
        public abstract AdServicesHttpClientRequest.Builder setUri(Uri uri);

        /**
         * @param queryParams that need to be piggybacked to the url connection
         */
        public abstract AdServicesHttpClientRequest.Builder setRequestProperties(
                ImmutableMap<String, String> queryParams);

        /**
         * @param responseHeaderKeys set of keys that we want in the {@link
         *     AdServicesHttpClientResponse}
         */
        public abstract AdServicesHttpClientRequest.Builder setResponseHeaderKeys(
                ImmutableSet<String> responseHeaderKeys);

        /**
         * @param useCache flag to cache the response of this request
         */
        public abstract AdServicesHttpClientRequest.Builder setUseCache(boolean useCache);

        /**
         * @param methodType type of HTTP request
         */
        public abstract Builder setHttpMethodType(HttpMethodType methodType);

        /**
         * @param bodyInBytes byte array that is part of the request
         */
        public abstract Builder setBodyInBytes(byte[] bodyInBytes);

        /**
         * @param devContext indicates the dev context associated with this request.
         */
        public abstract AdServicesHttpClientRequest.Builder setDevContext(DevContext devContext);

        /**
         * @return an {@link AdServicesHttpClientRequest}
         */
        public abstract AdServicesHttpClientRequest build();
    }
}
