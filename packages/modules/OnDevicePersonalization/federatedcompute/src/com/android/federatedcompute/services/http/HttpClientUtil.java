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

import com.android.federatedcompute.internal.util.LogUtil;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Utility class containing http related variable e.g. headers, method. */
public final class HttpClientUtil {
    private static final String TAG = HttpClientUtil.class.getSimpleName();
    public static final String CONTENT_ENCODING_HDR = "Content-Encoding";

    public static final String ACCEPT_ENCODING_HDR = "Accept-Encoding";
    public static final String CONTENT_LENGTH_HDR = "Content-Length";
    public static final String GZIP_ENCODING_HDR = "gzip";
    public static final String CONTENT_TYPE_HDR = "Content-Type";
    public static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
    public static final String OCTET_STREAM = "application/octet-stream";
    public static final ImmutableSet<Integer> HTTP_OK_STATUS = ImmutableSet.of(200, 201);

    public static final Integer HTTP_UNAUTHENTICATED_STATUS = 401;

    public static final Integer HTTP_UNAUTHORIZED_STATUS = 403;

    public static final ImmutableSet<Integer> HTTP_OK_OR_UNAUTHENTICATED_STATUS =
            ImmutableSet.of(200, 201, 401);

    // This key indicates the key attestation record used for authentication.
    public static final String ODP_AUTHENTICATION_KEY = "odp-authentication-key";

    // This key indicates a UUID as a verified token for the device.
    public static final String ODP_AUTHORIZATION_KEY = "odp-authorization-key";

    public static final String ODP_IDEMPOTENCY_KEY = "odp-idempotency-key";

    public static final String FCP_OWNER_ID_DIGEST = "fcp-owner-id-digest";

    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final byte[] EMPTY_BODY = new byte[0];

    /** The supported http methods. */
    public enum HttpMethod {
        GET,
        POST,
        PUT,
    }

    public static final class FederatedComputePayloadDataContract {
        public static final String KEY_ID = "keyId";

        public static final String ENCRYPTED_PAYLOAD = "encryptedPayload";

        public static final String ASSOCIATED_DATA_KEY = "associatedData";

        public static final byte[] ASSOCIATED_DATA = new JSONObject().toString().getBytes();
    }

    /** Compresses the input data using Gzip. */
    public static byte[] compressWithGzip(byte[] uncompressedData) {
        try (ByteString.Output outputStream = ByteString.newOutput(uncompressedData.length);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(uncompressedData);
            gzipOutputStream.finish();
            return outputStream.toByteString().toByteArray();
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to compress using Gzip");
            throw new IllegalStateException("Failed to compress using Gzip", e);
        }
    }

    /** Uncompresses the input data using Gzip. */
    public static byte[] uncompressWithGzip(byte[] data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                GZIPInputStream gzip = new GZIPInputStream(inputStream);
                ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            int length;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            while ((length = gzip.read(buffer, 0, DEFAULT_BUFFER_SIZE)) > 0) {
                result.write(buffer, 0, length);
            }
            return result.toByteArray();
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to decompress the data.", e);
            throw new IllegalStateException("Failed to unscompress using Gzip", e);
        }
    }

    /** Calculates total bytes are sent via network based on provided http request. */
    public static long getTotalSentBytes(FederatedComputeHttpRequest request) {
        long totalBytes = 0;
        totalBytes +=
                request.getHttpMethod().name().length()
                        + " ".length()
                        + request.getUri().length()
                        + " HTTP/1.1\r\n".length();
        for (String key : request.getExtraHeaders().keySet()) {
            totalBytes +=
                    key.length()
                            + ": ".length()
                            + request.getExtraHeaders().get(key).length()
                            + "\r\n".length();
        }
        if (request.getExtraHeaders().containsKey(CONTENT_LENGTH_HDR)) {
            totalBytes += Long.parseLong(request.getExtraHeaders().get(CONTENT_LENGTH_HDR));
        }
        return totalBytes;
    }

    /** Calculates total bytes are received via network based on provided http response. */
    public static long getTotalReceivedBytes(FederatedComputeHttpResponse response) {
        long totalBytes = 0;
        boolean foundContentLengthHdr = false;
        for (Map.Entry<String, List<String>> header : response.getHeaders().entrySet()) {
            if (header.getKey() == null) {
                continue;
            }
            for (String headerValue : header.getValue()) {
                totalBytes += header.getKey().length() + ": ".length();
                totalBytes += headerValue == null ? 0 : headerValue.length();
            }
            // Uses Content-Length header to estimate total received bytes which is the most
            // accurate.
            if (header.getKey().equals(CONTENT_LENGTH_HDR)) {
                totalBytes += Long.parseLong(header.getValue().get(0));
                foundContentLengthHdr = true;
            }
        }
        if (!foundContentLengthHdr && response.getPayload() != null) {
            totalBytes += response.getPayload().length;
        }
        return totalBytes;
    }

    private HttpClientUtil() {}
}
