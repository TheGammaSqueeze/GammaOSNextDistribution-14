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

package com.android.server.wifi.entitlement.http;

import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.net.Network;

import androidx.test.filters.SmallTest;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.entitlement.http.FakeURLStreamHandler.FakeHttpsURLConnection;
import com.android.server.wifi.entitlement.http.FakeURLStreamHandler.FakeResponse;
import com.android.server.wifi.entitlement.http.HttpConstants.ContentType;
import com.android.server.wifi.entitlement.http.HttpConstants.RequestMethod;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.Map;

/**
 * Unit tests for {@link HttpClient}.
 */
@SmallTest
public class HttpClientTest extends WifiBaseTest {
    private static final String TEST_URL = "https://test.url";
    private static final String TEST_RESPONSE_BODY = "TEST_RESPONSE_BODY";
    private static final String CONTENT_TYPE_STRING_JSON = "application/json";
    private static final String RETRY_AFTER = "RETRY_AFTER";

    private static FakeURLStreamHandler sFakeURLStreamHandler;

    private JSONArray mJsonArray;
    private static URLStreamHandlerFactory sOldFactory;
    private static Field sFactoryField;

    @BeforeClass
    public static void setupURLStreamHandlerFactory() throws IllegalAccessException {
        sFakeURLStreamHandler = new FakeURLStreamHandler();
        for (Field field : URL.class.getDeclaredFields()) {
            if (URLStreamHandlerFactory.class.equals(field.getType())) {
                assertWithMessage(
                        "URL declares multiple URLStreamHandlerFactory fields")
                        .that(sOldFactory).isNull();
                sFactoryField = field;
                sFactoryField.setAccessible(true);
                sOldFactory = (URLStreamHandlerFactory) sFactoryField.get(null);
            }
        }
        URL.setURLStreamHandlerFactory(sFakeURLStreamHandler);
    }

    @AfterClass
    public static void restoreURLStreamHandlerFactory() throws IllegalAccessException {
        sFactoryField.set(null, null);
        URL.setURLStreamHandlerFactory(sOldFactory);
    }

    @Before
    public void setUp() throws JSONException {
        // Reset sFakeURLStreamHandler
        sFakeURLStreamHandler.stubResponse(ImmutableMap.of());

        JSONObject jsonObject1 = (new JSONObject()).put("key11", "value11")
                .put("key12", "value12");
        JSONObject jsonObject2 = (new JSONObject()).put("key21", "value21")
                .put("key22", "value22");
        mJsonArray = (new JSONArray()).put(jsonObject1).put(jsonObject2);
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

        HttpResponse httpResponse = HttpClient.request(request);

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
                assertThrows(ServiceEntitlementException.class, () -> HttpClient.request(request));

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

        HttpResponse httpResponse = HttpClient.request(request);

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

        HttpClient.request(request);

        FakeHttpsURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getBytesWrittenToOutputStream()).isEqualTo(postData.getBytes(UTF_8));
    }

    @Test
    public void request_postGzipJsonArray() throws Exception {
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.POST)
                        .setPostDataJsonArray(mJsonArray)
                        .addRequestProperty(CONTENT_ENCODING, HttpClient.GZIP)
                        .build();
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        HttpClient.request(request);

        assertThat(sFakeURLStreamHandler.getConnections()).hasSize(1);
        FakeHttpsURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getBytesWrittenToOutputStream()).isEqualTo(
                HttpClient.toGzipBytes(mJsonArray.toString()));
    }

    @Test
    public void request_postJsonArray() throws Exception {
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.POST)
                        .setPostDataJsonArray(mJsonArray)
                        .build();
        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseBody(TEST_RESPONSE_BODY.getBytes(UTF_8))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        HttpClient.request(request);

        assertThat(sFakeURLStreamHandler.getConnections()).hasSize(1);
        FakeHttpsURLConnection connection = sFakeURLStreamHandler.getConnections().get(0);
        assertThat(connection.getBytesWrittenToOutputStream()).isEqualTo(
                mJsonArray.toString().getBytes(UTF_8));
    }

    @Test
    public void request_postGzipJsonArray_response_gunzipJsonArray() throws Exception {
        HttpRequest request =
                HttpRequest.builder()
                        .setUrl(TEST_URL)
                        .setRequestMethod(RequestMethod.POST)
                        .setPostDataJsonArray(mJsonArray)
                        .addRequestProperty(CONTENT_ENCODING, HttpClient.GZIP)
                        .build();

        FakeResponse responseContent =
                FakeResponse.builder()
                        .setResponseCode(HttpURLConnection.HTTP_OK)
                        .setResponseLocation(null)
                        .setResponseBody(HttpClient.toGzipBytes(TEST_RESPONSE_BODY))
                        .setContentType(CONTENT_TYPE_STRING_JSON)
                        .setContentEncoding(HttpClient.GZIP)
                        .build();
        Map<String, FakeResponse> response = ImmutableMap.of(TEST_URL, responseContent);
        sFakeURLStreamHandler.stubResponse(response);

        HttpResponse httpResponse = HttpClient.request(request);

        // Verify the HttpResponse.
        assertThat(httpResponse.contentType()).isEqualTo(ContentType.JSON);
        assertThat(httpResponse.body()).isEqualTo(TEST_RESPONSE_BODY);
        assertThat(httpResponse.responseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
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

        ServiceEntitlementException exception = assertThrows(
                ServiceEntitlementException.class, () -> HttpClient.request(request));

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

        ServiceEntitlementException exception = assertThrows(
                ServiceEntitlementException.class, () -> HttpClient.request(request));

        assertThat(exception.getErrorCode()).isEqualTo(
                ServiceEntitlementException.ERROR_MALFORMED_HTTP_RESPONSE);
        assertThat(exception.getMessage()).isEqualTo("Read response body/message failed!");
        assertThat(exception.getHttpStatus()).isEqualTo(0);
        assertThat(exception.getRetryAfter()).isEmpty();
    }
}
