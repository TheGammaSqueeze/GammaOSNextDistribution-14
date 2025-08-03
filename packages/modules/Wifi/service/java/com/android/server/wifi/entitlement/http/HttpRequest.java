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

import static com.android.server.wifi.entitlement.http.HttpConstants.DEFAULT_TIMEOUT_IN_SEC;

import android.annotation.NonNull;
import android.net.Network;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** The parameters of an http request. */
@AutoValue
public abstract class HttpRequest {
    /** The URL. */
    public abstract String url();

    /** The HTTP request method, like "GET" or "POST". */
    public abstract String requestMethod();

    /**
     * For "POST" request method, the body of the request in JSONObject.
     * At most one of {@link postData} or {@link postDataJsonArray} is set.
     */
    public abstract JSONObject postData();

    /**
     * For "POST" request method, the body of the request in JSONArray.
     * At most one of {@link postData} or {@link postDataJsonArray} is set.
     */
    public abstract JSONArray postDataJsonArray();

    /** HTTP header fields. */
    public abstract ImmutableListMultimap<String, String> requestProperties();

    /** The client side timeout, in seconds. See {@link Builder#setTimeoutInSec}. */
    public abstract int timeoutInSec();

    /** The network used for this HTTP connection. See {@link Builder#setNetwork}. */
    @Nullable
    public abstract Network network();

    /** Builder of {@link HttpRequest}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Builds a HttpRequest object. */
        public abstract HttpRequest build();

        /** Sets the URL. */
        public abstract Builder setUrl(@NonNull String url);

        /**
         * Sets the HTTP request method, like "GET" or "POST".
         *
         * @see HttpConstants.RequestMethod
         */
        public abstract Builder setRequestMethod(@NonNull String requestMethod);

        /**
         * For "POST" request method, sets the body of the request to a JSONObject. If the body of
         * the request is a JSONArray, please use {@link #setPostDataJsonArray(JSONArray)}.
         */
        public abstract Builder setPostData(@NonNull JSONObject postData);

        /**
         * For "POST" request method, sets the body of the request to a JSONArray. If the body of
         * the request is a JSONObject, please use {@link #setPostData(JSONObject)}.
         */
        public abstract Builder setPostDataJsonArray(@NonNull JSONArray postDataJsonArray);

        abstract ImmutableListMultimap.Builder<String, String> requestPropertiesBuilder();

        /** Adds an HTTP header field. */
        public Builder addRequestProperty(@NonNull String key, @NonNull String value) {
            requestPropertiesBuilder().put(key, value);
            return this;
        }

        /**
          * Adds an HTTP header field with multiple values. Equivalent to calling
          * {@link #addRequestProperty(String, String)} multiple times with the same key and
          * one value at a time.
          */
        public Builder addRequestProperty(@NonNull String key, @NonNull List<String> value) {
            requestPropertiesBuilder().putAll(key, value);
            return this;
        }

        /**
         * Sets the client side timeout for HTTP connection. Default to
         * {@link HttpConstants#DEFAULT_TIMEOUT_IN_SEC}.
         *
         * <p>This timeout is used by both {@link java.net.URLConnection#setConnectTimeout} and
         * {@link java.net.URLConnection#setReadTimeout}.
         */
        public abstract Builder setTimeoutInSec(int timeoutInSec);

        /**
         * Sets the network used for this HTTP connection. If not set, the device default network
         * is used.
         */
        public abstract Builder setNetwork(@Nullable Network network);
    }

    /**
     * Generates an {@link HttpRequest#Builder}
     */
    public static Builder builder() {
        return new AutoValue_HttpRequest.Builder()
                .setUrl("")
                .setRequestMethod("")
                .setPostData(new JSONObject())
                .setPostDataJsonArray(new JSONArray())
                .setTimeoutInSec(DEFAULT_TIMEOUT_IN_SEC);
    }
}
