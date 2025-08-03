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

package com.android.server.wifi;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.inOrder;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.location.Location;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticInOrder;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.net.MalformedURLException;
import java.util.Random;

/**
 * Unit tests for {@link AfcClient}.
 */
@SmallTest
public class AfcClientTest {
    @Captor ArgumentCaptor<AfcServerResponse> mAfcServerResponseCaptor;
    @Captor ArgumentCaptor<HttpRequest> mHttpRequestCaptor;
    @Mock private Location mLocation;
    @Mock private Random mRandom;
    @Mock AfcClient.Callback mCallback;
    private AfcClient mAfcClient;
    private MockitoSession mSession;
    private AfcLocation mAfcLocation;
    private TestLooper mTestLooper;
    private Handler mWifiHandler;
    private static final double LONGITUDE = -122;
    private static final double LATITUDE = 37;
    private static final int HTTP_RESPONSE_CODE = 200;
    private static final String AVAILABILITY_EXPIRE_TIME = "2020-11-03T13:34:05Z";

    @Before
    public void setUp() throws MalformedURLException, ServiceEntitlementException, JSONException {
        MockitoAnnotations.initMocks(this);

        mAfcServerResponseCaptor = ArgumentCaptor.forClass(AfcServerResponse.class);
        mHttpRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        mTestLooper = new TestLooper();
        mWifiHandler = new Handler(mTestLooper.getLooper());

        when(mRandom.nextDouble()).thenReturn(0.5);
        when(mLocation.getLongitude()).thenReturn(LONGITUDE);
        when(mLocation.getLatitude()).thenReturn(LATITUDE);
        mAfcLocation = new AfcEllipseLocation(
                AfcEllipseLocation.DEFAULT_SEMI_MINOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_ORIENTATION,
                AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_DEGREES, mRandom, mLocation
        );

        HttpResponse httpResponse = HttpResponse.builder().setResponseCode(
                HTTP_RESPONSE_CODE).setBody(
                getHttpResponseJSONBody()).build();

        // Start a mockitoSession to mock HttpClient's static request method
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(HttpClient.class, withSettings().lenient())
                .startMocking();
        when(HttpClient.request(any(HttpRequest.class))).thenReturn(httpResponse);

        // Create new AfcClient with a testing server URL
        Handler backgroundHandler = new Handler(mTestLooper.getLooper());
        mAfcClient = new AfcClient(backgroundHandler);
        mAfcClient.setServerURL("https://testingURL");
    }

    /**
     * Create a test HttpResponse JSON body to be received by server queries.
     */
    private String getHttpResponseJSONBody() throws JSONException {
        JSONObject responseObject = new JSONObject();
        JSONArray inquiryResponses = new JSONArray();
        JSONObject inquiryResponse = new JSONObject();
        inquiryResponse.put("requestId", String.valueOf(0));
        inquiryResponse.put("availabilityExpireTime", AVAILABILITY_EXPIRE_TIME);
        JSONObject responseResultObject = new JSONObject();
        responseResultObject.put("responseCode", 0);
        responseResultObject.put("shortDescription", "Success");
        inquiryResponse.put("response", responseResultObject);
        inquiryResponses.put(0, inquiryResponse);
        responseObject.put("availableSpectrumInquiryResponses", inquiryResponses);
        return responseObject.toString();
    }

    /**
     * Verify that the Http request has expected fields set and that the request ID in the HTTP
     * request is incremented with every query to the AFC server.
     */
    @Test
    public void testHTTPRequestInitialization() throws JSONException {
        StaticInOrder inOrder = inOrder(staticMockMarker(HttpClient.class));

        mAfcClient.queryAfcServer(mAfcLocation, mWifiHandler, mCallback);
        mTestLooper.dispatchAll();

        inOrder.verify(() -> HttpClient.request(mHttpRequestCaptor.capture()));
        HttpRequest httpRequest1 = mHttpRequestCaptor.getValue();
        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).getJSONObject("location").getJSONObject(
                        "ellipse").getJSONObject("center").get("longitude"))
                .isEqualTo(LONGITUDE);
        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).getJSONObject("location").getJSONObject(
                        "ellipse").getJSONObject("center").get("latitude"))
                .isEqualTo(LATITUDE);

        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).get("requestId")).isEqualTo("0");

        mAfcClient.queryAfcServer(mAfcLocation, mWifiHandler, mCallback);
        mTestLooper.dispatchAll();

        inOrder.verify(() -> HttpClient.request(mHttpRequestCaptor.capture()));
        HttpRequest httpRequest2 = mHttpRequestCaptor.getValue();

        // Ensure that the request ID has been incremented
        assertThat(httpRequest2.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).get("requestId")).isEqualTo("1");
    }

    /**
     * Verify that the AfcClient retrieves the HTTP request object and that the background thread
     * successfully sends a request to the AFC server. Checks that the response is passed to the
     * Wi-Fi handler callback with expected fields.
     */
    @Test
    public void testSendRequestAndReceiveResponse() {
        mAfcClient.queryAfcServer(mAfcLocation, mWifiHandler, mCallback);
        mTestLooper.dispatchAll();
        assertThat(mAfcClient.getAfcHttpRequestObject(mAfcLocation).url()).isEqualTo(
                "https://testingURL");
        verify(mCallback).onResult(mAfcServerResponseCaptor.capture(), any(AfcLocation.class));
        AfcServerResponse serverResponse = mAfcServerResponseCaptor.getValue();
        assertThat(serverResponse.getAfcChannelAllowance().availabilityExpireTimeMs)
                .isEqualTo(AfcServerResponse.convertExpireTimeStringToTimestamp(
                        AVAILABILITY_EXPIRE_TIME));
        assertThat(serverResponse.getHttpResponseCode()).isEqualTo(HTTP_RESPONSE_CODE);
        assertThat(serverResponse.getAfcResponseCode()).isEqualTo(0);
        assertThat(serverResponse.getAfcResponseDescription()).isEqualTo("Success");
    }

    /**
     * Verify that sending the callback onFailure method is called with the correct reason code and
     * message when the request sent to the server throws a ServiceEntitlementException.
     */
    @Test
    public void testSendHTTPRequestFail() throws ServiceEntitlementException {
        when(HttpClient.request(any(HttpRequest.class))).thenThrow(
                new ServiceEntitlementException(-1, "Test message"));
        mAfcClient.queryAfcServer(mAfcLocation, mWifiHandler, mCallback);
        mTestLooper.dispatchAll();
        verify(mCallback).onFailure(AfcClient.REASON_SERVICE_ENTITLEMENT_FAILURE,
                "Encountered Service Entitlement Exception when sending request to server. "
                        + "com.android.wifi.x.com.android.libraries.entitlement."
                        + "ServiceEntitlementException: Test message");
    }

     /**
     * Called after each test.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }
}
