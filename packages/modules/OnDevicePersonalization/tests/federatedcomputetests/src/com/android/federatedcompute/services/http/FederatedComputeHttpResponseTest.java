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

import static com.android.federatedcompute.services.http.HttpClientUtil.OCTET_STREAM;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public final class FederatedComputeHttpResponseTest {
    @Test
    public void testBuildWithAllValues() {
        final int responseCode = 200;
        final byte[] payload = "payload".getBytes(UTF_8);
        final Map<String, List<String>> headers =
                ImmutableMap.of(
                        "x-content",
                        ImmutableList.of("1", "2"),
                        "api-key",
                        ImmutableList.of("xyz"));

        FederatedComputeHttpResponse response =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(responseCode)
                        .setPayload(payload)
                        .setHeaders(headers)
                        .build();

        assertThat(response.getStatusCode()).isEqualTo(responseCode);
        assertThat(response.getPayload()).isEqualTo(payload);
        assertThat(response.getHeaders()).isEqualTo(headers);
    }

    @Test
    public void testBuildWithMinimalRequiredValues() {
        final int responseCode = 200;
        FederatedComputeHttpResponse response =
                new FederatedComputeHttpResponse.Builder().setStatusCode(responseCode).build();

        assertThat(response.getStatusCode()).isEqualTo(responseCode);
    }

    @Test
    public void testBuildStatusCodeNull_invalid() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FederatedComputeHttpResponse.Builder()
                                .setPayload("payload".getBytes(UTF_8))
                                .build());
    }

    @Test
    public void testGetBody_success() {
        final byte[] uncompressedBody = "payload".getBytes(UTF_8);
        Map<String, List<String>> expectedHeaders = new HashMap<>();
        expectedHeaders.put(HttpClientUtil.CONTENT_TYPE_HDR, ImmutableList.of(OCTET_STREAM));

        FederatedComputeHttpResponse response =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(uncompressedBody)
                        .setHeaders(expectedHeaders)
                        .build();

        assertThat(response.getPayload()).isEqualTo(uncompressedBody);
    }
}
