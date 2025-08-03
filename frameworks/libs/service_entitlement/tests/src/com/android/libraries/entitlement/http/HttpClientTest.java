/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.libraries.entitlement.http;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.net.Network;

import androidx.test.runner.AndroidJUnit4;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.http.HttpConstants.ContentType;
import com.android.libraries.entitlement.http.HttpConstants.RequestMethod;
import com.android.libraries.entitlement.testing.FakeURLStreamHandler;
import com.android.libraries.entitlement.testing.FakeURLStreamHandler.FakeHttpsURLConnection;
import com.android.libraries.entitlement.testing.FakeURLStreamHandler.FakeResponse;

import com.google.common.collect.ImmutableMap;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class HttpClientTest {
    private static final String TEST_URL = "https://test.url";
    private static final String TEST_RESPONSE_BODY = "TEST_RESPONSE_BODY";
    private static final String CONTENT_TYPE_STRING_JSON = "application/json";
    private static final String RETRY_AFTER = "RETRY_AFTER";

    private static FakeURLStreamHandler sFakeURLStreamHandler;

    private HttpClient mHttpClient;

    @BeforeClass
    public static void setupURLStreamHandlerFactory() {
        sFakeURLStreamHandler = new FakeURLStreamHandler();
        URL.setURLStreamHandlerFactory(sFakeURLStreamHandler);
    }

    @Before
    public void setUp() {
        // Reset sFakeURLStreamHandler
        sFakeURLStreamHandler.stubResponse(ImmutableMap.of());

        mHttpClient = new HttpClient(true);
    }

    @Test
    public void request_contentTypeXml_returnsXmlBody() throws Exception {
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseLocation(null)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.GET)
                        .setTimeoutInSec(70)
                        .build();

        HttpResponse httpResponse = mHttpClient.request(request);

        // Verify that one HttpURLConnection was opened and its timeout is 70 seconds.
        assertThat(sFakeURLStreamHandler.getConnections()).hasSize(1);
        HttpURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getConnectTimeout()).isEqualTo(70 * 1000);
        assertThat(connection.getReadTimeout()).isEqualTo(70 * 1000);
        // Verify the HttpResponse.
        assertThat(httpResponse.contentType()).isEqualTo(ContentType.JSON);
        assertThat(httpResponse.body()).isEqualTo(TEST_RESPONSE_BODY);
        assertThat(httpResponse.responseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    }

    @Test
    public void request_httpGetResponseBadRequest_throwsException() {
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
                        .setResponseLocation(null)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .setRetryAfter(RETRY_AFTER)
                        .build();
        HttpRequest request =
                HttpRequest.builder().setUrl(TEST_URL).setRequestMethod(RequestMethod.GET).build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        ServiceEntitlementException exception =
                expectThrows(ServiceEntitlementException.class, () -> mHttpClient.request(request));

         // Verify the ServiceEntitlementException.
        assertThat(exception.getErrorCode()).isEqualTo(
                ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
        assertThat(exception).hasMessageThat().contains("Invalid connection response");
        assertThat(exception.getRetryAfter()).isEqualTo(RETRY_AFTER);
        // Verify that one HttpURLConnection was opened and its timeout is 30 seconds.
        assertThat(sFakeURLStreamHandler.getConnections()).hasSize(1);
        HttpURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getConnectTimeout()).isEqualTo(30 * 1000);
        assertThat(connection.getReadTimeout()).isEqualTo(30 * 1000);
    }

    @Test
    public void request_contentTypeXml_returnsXmlBody_useSpecificNetwork() throws Exception {
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseLocation(null)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Network network = mock(Network.class);
        URL url = new URL(TEST_URL);
        FakeHttpsURLConnection connection = new FakeHttpsURLConnection(url, responseContent);
        when(network.openConnection(url)).thenReturn(connection);
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.GET)
                        .setNetwork(network)
                        .setTimeoutInSec(70)
                        .build();

        HttpResponse httpResponse = mHttpClient.request(request);

        // Verify that the HttpURLConnection associsted with Netwotk was opened
        // and its timeout is 70 seconds.
        verify(network).openConnection(url);
        assertThat(connection.getConnectTimeout()).isEqualTo(70 * 1000);
        assertThat(connection.getReadTimeout()).isEqualTo(70 * 1000);
        // Verify the HttpResponse.
        assertThat(httpResponse.contentType()).isEqualTo(ContentType.JSON);
        assertThat(httpResponse.body()).isEqualTo(TEST_RESPONSE_BODY);
        assertThat(httpResponse.responseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    }

    @Test
    public void request_postJson_doNotEscapeForwardSlash() throws Exception {
        String postData = "{\"key\":\"base64/base64+b\"}";
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.POST)
                        .setPostData(new JSONObject(postData))
                        .build();
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        HttpResponse httpResponse = mHttpClient.request(request);

        FakeHttpsURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getBytesWrittenToOutputStream()).isEqualTo(postData.getBytes(UTF_8));
    }

    @Test
    public void request_getResponseCodeFailed_expectThrowsException() {
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.GET)
                        .build();
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .setHasException(true)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        ServiceEntitlementException exception = expectThrows(
                ServiceEntitlementException.class, () -> mHttpClient.request(request));

        assertThat(exception.getErrorCode()).isEqualTo(
                ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS);
        assertThat(exception.getMessage()).isEqualTo("Read response code failed!");
        assertThat(exception.getHttpStatus()).isEqualTo(0);
        assertThat(exception.getRetryAfter()).isEmpty();
    }

    @Test
    public void request_getResponseBodyFailed_expectThrowsException() {
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.GET)
                        .build();
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .setHasException(true)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        ServiceEntitlementException exception = expectThrows(
                ServiceEntitlementException.class, () -> mHttpClient.request(request));

        assertThat(exception.getErrorCode()).isEqualTo(
                ServiceEntitlementException.ERROR_MALFORMED_HTTP_RESPONSE);
        assertThat(exception.getMessage()).isEqualTo("Read response body/message failed!");
        assertThat(exception.getHttpStatus()).isEqualTo(0);
        assertThat(exception.getRetryAfter()).isEmpty();
    }

    @Test
    public void history() throws Exception {
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseLocation(null)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.GET)
                        .setTimeoutInSec(70)
                        .build();

        HttpResponse httpResponse0 = mHttpClient.request(request);
        HttpResponse httpResponse1 = mHttpClient.request(request);
        List<String> history = mHttpClient.getHistory();

        assertThat(history)
                .containsExactly(
                        request.toString(),
                        httpResponse0.toString(),
                        request.toString(),
                        httpResponse1.toString())
                .inOrder();

        mHttpClient.clearHistory();
        history = mHttpClient.getHistory();

        assertThat(history).isEmpty();
    }
}
