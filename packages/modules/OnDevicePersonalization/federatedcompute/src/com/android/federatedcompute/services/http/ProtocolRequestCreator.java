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

package com.android.federatedcompute.services.http;

import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_TYPE_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.PROTOBUF_CONTENT_TYPE;

import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;

import com.google.internal.federatedcompute.v1.ForwardingInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * A helper class to create FederatedComputeHttpRequest with base uri, request headers and
 * compression setting.
 */
public final class ProtocolRequestCreator {
    private final String mRequestBaseUri;
    private final HashMap<String, String> mHeaderList;

    public ProtocolRequestCreator(String requestBaseUri, HashMap<String, String> headerList) {
        this.mRequestBaseUri = requestBaseUri;
        this.mHeaderList = headerList;
    }

    /**
     * Creates a {@link ProtocolRequestCreator} based on forwarding info. Validates and extracts the
     * base URI for the subsequent requests.
     */
    public static ProtocolRequestCreator create(ForwardingInfo forwardingInfo) {
        if (forwardingInfo.getTargetUriPrefix().isEmpty()) {
            throw new IllegalArgumentException("Missing `ForwardingInfo.target_uri_prefix`");
        }
        HashMap<String, String> extraHeaders = new HashMap<>();
        extraHeaders.putAll(forwardingInfo.getExtraRequestHeadersMap());
        return new ProtocolRequestCreator(forwardingInfo.getTargetUriPrefix(), extraHeaders);
    }

    /** Creates a {@link FederatedComputeHttpRequest} with base uri and compression setting. */
    public FederatedComputeHttpRequest createProtoRequest(
            String uri, HttpMethod httpMethod, byte[] requestBody, boolean isProtobufEncoded) {
        HashMap<String, String> extraHeaders = new HashMap<>();
        return createProtoRequest(uri, httpMethod, extraHeaders, requestBody, isProtobufEncoded);
    }

    /**
     * Creates a {@link FederatedComputeHttpRequest} with base uri, request headers and compression
     * setting.
     */
    public FederatedComputeHttpRequest createProtoRequest(
            String uri,
            HttpMethod httpMethod,
            Map<String, String> extraHeaders,
            byte[] requestBody,
            boolean isProtobufEncoded) {
        HashMap<String, String> requestHeader = new HashMap<>();
        requestHeader.putAll(mHeaderList);
        requestHeader.putAll(extraHeaders);

        if (isProtobufEncoded && requestBody.length > 0) {
            requestHeader.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        }
        return FederatedComputeHttpRequest.create(
                joinBaseUriWithSuffix(mRequestBaseUri, uri),
                httpMethod,
                requestHeader,
                requestBody);
    }

    private String joinBaseUriWithSuffix(String baseUri, String suffix) {
        if (suffix.isEmpty() || !suffix.startsWith("/")) {
            throw new IllegalArgumentException("uri_suffix be empty or must have a leading '/'");
        }

        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }
        suffix = suffix.substring(1);
        return String.join("/", baseUri, suffix);
    }
}
