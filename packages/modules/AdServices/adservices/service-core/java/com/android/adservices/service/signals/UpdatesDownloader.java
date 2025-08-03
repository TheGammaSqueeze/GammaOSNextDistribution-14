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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Downloads signal updates for the updateSignals API. */
public class UpdatesDownloader {

    public static final String PACKAGE_NAME_HEADER = "X-PROTECTED-SIGNALS-PACKAGE";
    public static final String CONVERSION_ERROR_MSG = "Error converting response body to JSON";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Executor mLightweightExecutor;
    @NonNull private final AdServicesHttpsClient mHttpClient;

    public UpdatesDownloader(
            @NonNull Executor lightweightExecutor, @NonNull AdServicesHttpsClient httpClient) {
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(httpClient);
        mLightweightExecutor = lightweightExecutor;
        mHttpClient = httpClient;
    }

    /**
     * Gets the JSON from the remote server.
     *
     * @param validatedUri Validated Uri to fetch JSON from.
     * @param packageName The package name of the calling app.
     * @param devContext Development context for testing the network call
     * @return A future containing the fetched JSON.
     */
    @NonNull
    public FluentFuture<JSONObject> getUpdateJson(
            Uri validatedUri, String packageName, DevContext devContext) {
        sLogger.v("Fetching signals from " + validatedUri);

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(PACKAGE_NAME_HEADER, packageName);
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(validatedUri)
                        .setDevContext(devContext)
                        .build();
        FluentFuture<AdServicesHttpClientResponse> response =
                FluentFuture.from(mHttpClient.fetchPayload(request));
        return response.transform(this::responseToJson, mLightweightExecutor);
    }

    private JSONObject responseToJson(AdServicesHttpClientResponse response) {
        try {
            return new JSONObject(response.getResponseBody());
        } catch (JSONException e) {
            sLogger.e(e, "Error converting updateSignals response body to JSON");
            throw new IllegalArgumentException(CONVERSION_ERROR_MSG, e);
        }
    }
}
