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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public final class HttpClientTest {
    public static final FederatedComputeHttpRequest DEFAULT_GET_REQUEST =
            FederatedComputeHttpRequest.create(
                    "https://google.com",
                    HttpMethod.GET,
                    new HashMap<>(),
                    HttpClientUtil.EMPTY_BODY);
    @Spy private HttpClient mHttpClient = new HttpClient();
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Mock private HttpURLConnection mMockHttpURLConnection;

    @Test
    public void testUnableToOpenconnection_returnFailure() throws Exception {
        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        "https://google.com",
                        HttpMethod.POST,
                        new HashMap<>(),
                        HttpClientUtil.EMPTY_BODY);
        doThrow(new IOException()).when(mHttpClient).setup(ArgumentMatchers.any());

        assertThrows(IOException.class, () -> mHttpClient.performRequest(request));
    }

    @Test
    public void testPerformGetRequestSuccess() throws Exception {
        String successMessage = "Success!";
        InputStream mockStream = new ByteArrayInputStream(successMessage.getBytes(UTF_8));
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        when(mMockHttpURLConnection.getInputStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(200);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(mockHeaders);
        doReturn(mMockHttpURLConnection).when(mHttpClient).setup(ArgumentMatchers.any());
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) successMessage.length());

        FederatedComputeHttpResponse response = mHttpClient.performRequest(DEFAULT_GET_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
        assertThat(response.getPayload()).isEqualTo(successMessage.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestFails() throws Exception {
        String failureMessage = "FAIL!";
        InputStream mockStream = new ByteArrayInputStream(failureMessage.getBytes(UTF_8));
        when(mMockHttpURLConnection.getErrorStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(503);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(new HashMap<>());
        doReturn(mMockHttpURLConnection).when(mHttpClient).setup(ArgumentMatchers.any());
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) failureMessage.length());

        FederatedComputeHttpResponse response = mHttpClient.performRequest(DEFAULT_GET_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(503);
        assertTrue(response.getHeaders().isEmpty());
        assertThat(response.getPayload()).isEqualTo(failureMessage.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestFailsWithRetry() throws Exception {
        String failureMessage = "FAIL!";
        when(mMockHttpURLConnection.getErrorStream())
                .then(invocation -> new ByteArrayInputStream(failureMessage.getBytes(UTF_8)));
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(503);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(new HashMap<>());
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) failureMessage.length());
        doReturn(mMockHttpURLConnection).when(mHttpClient).setup(ArgumentMatchers.any());

        FederatedComputeHttpResponse response =
                mHttpClient.performRequestWithRetry(DEFAULT_GET_REQUEST);

        verify(mHttpClient, times(3)).performRequest(DEFAULT_GET_REQUEST);
        assertThat(response.getStatusCode()).isEqualTo(503);
        assertTrue(response.getHeaders().isEmpty());
        assertThat(response.getPayload()).isEqualTo(failureMessage.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestSuccessWithRetry() throws Exception {
        String failureMessage = "FAIL!";
        InputStream mockStream = new ByteArrayInputStream(failureMessage.getBytes(UTF_8));
        when(mMockHttpURLConnection.getErrorStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(503);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(new HashMap<>());
        HttpURLConnection mockSuccessfulHttpURLConnection = Mockito.mock(HttpURLConnection.class);
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        when(mockSuccessfulHttpURLConnection.getOutputStream())
                .thenReturn(new ByteArrayOutputStream());
        when(mockSuccessfulHttpURLConnection.getResponseCode()).thenReturn(200);
        when(mockSuccessfulHttpURLConnection.getHeaderFields()).thenReturn(mockHeaders);
        final AtomicInteger countCall = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            int count = countCall.incrementAndGet();
                            if (count < 3) {
                                return mMockHttpURLConnection;
                            } else {
                                return mockSuccessfulHttpURLConnection;
                            }
                        })
                .when(mHttpClient)
                .setup(ArgumentMatchers.any());
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) failureMessage.length());

        FederatedComputeHttpResponse response =
                mHttpClient.performRequestWithRetry(DEFAULT_GET_REQUEST);

        verify(mHttpClient, times(3)).performRequest(DEFAULT_GET_REQUEST);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
    }

    @Test
    public void testPerformPostRequestSuccess() throws Exception {
        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        "https://google.com",
                        HttpMethod.POST,
                        new HashMap<>(),
                        "payload".getBytes(UTF_8));
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        when(mMockHttpURLConnection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(200);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(mockHeaders);
        doReturn(mMockHttpURLConnection).when(mHttpClient).setup(ArgumentMatchers.any());

        FederatedComputeHttpResponse response = mHttpClient.performRequest(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
    }
}
