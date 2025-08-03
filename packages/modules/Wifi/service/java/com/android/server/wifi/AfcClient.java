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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.net.wifi.ScanResult;
import android.os.Handler;
import android.util.Log;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpConstants.RequestMethod;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that queries the AFC server with HTTP post requests and returns the HTTP response result.
 */
public class AfcClient {
    private static final String TAG = "WifiAfcClient";
    public static final int REASON_NO_URL_SPECIFIED = 0;
    public static final int REASON_AFC_RESPONSE_CODE_ERROR = 1;
    public static final int REASON_SERVICE_ENTITLEMENT_FAILURE = 2;
    public static final int REASON_JSON_FAILURE = 3;
    public static final int REASON_UNDEFINED_FAILURE = 4;
    @IntDef(prefix = { "REASON_" }, value = {
            REASON_NO_URL_SPECIFIED,
            REASON_AFC_RESPONSE_CODE_ERROR,
            REASON_SERVICE_ENTITLEMENT_FAILURE,
            REASON_JSON_FAILURE,
            REASON_UNDEFINED_FAILURE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FailureReasonCode {}
    static final int CONNECT_TIMEOUT_SECS = 30;
    static final int LOW_FREQUENCY = ScanResult.BAND_6_GHZ_START_FREQ_MHZ;

    // TODO(b/291774201): Update max frequency since server is currently returning errors with
    //  higher values than 6425.
    static final int HIGH_FREQUENCY = 6425;
    static final String SERIAL_NUMBER = "ABCDEFG";
    static final String NRA = "FCC";
    static final String ID = "EFGHIJK";
    static final String RULE_SET_ID = "US_47_CFR_PART_15_SUBPART_E";
    static final String VERSION = "1.2";
    private static String sServerUrl;
    private static HashMap<String, String> sRequestProperties;
    private final Handler mBackgroundHandler;
    private int mRequestId;

    AfcClient(Handler backgroundHandler) {
        mRequestId = 0;
        sRequestProperties = new HashMap<>();
        mBackgroundHandler = backgroundHandler;
    }

    /**
     * Query the AFC server using the background thread and post the response to the callback on
     * the Wi-Fi handler thread.
     */
    public void queryAfcServer(@NonNull AfcLocation afcLocation, @NonNull Handler wifiHandler,
            @NonNull Callback callback) {
        // If no URL is provided, then fail to proceed with sending request to AFC server
        if (sServerUrl == null) {
            wifiHandler.post(() -> callback.onFailure(REASON_NO_URL_SPECIFIED,
                    "No Server URL was provided through command line argument. Must "
                            + "provide URL through configure-afc-server wifi shell command."
            ));
            return;
        }

        HttpRequest httpRequest = getAfcHttpRequestObject(afcLocation);
        mBackgroundHandler.post(() -> {
            try {
                HttpResponse httpResponse = HttpClient.request(httpRequest);
                JSONObject httpResponseBodyJSON = new JSONObject(httpResponse.body());

                AfcServerResponse serverResponse = AfcServerResponse
                        .fromSpectrumInquiryResponse(getHttpResponseCode(httpResponse),
                                getAvailableSpectrumInquiryResponse(httpResponseBodyJSON,
                                        mRequestId));
                if (serverResponse == null) {
                    wifiHandler.post(() -> callback.onFailure(REASON_JSON_FAILURE,
                            "Encountered JSON error when parsing AFC server's "
                                    + "response."
                    ));
                } else if (serverResponse.getAfcChannelAllowance() == null) {
                    wifiHandler.post(() -> callback.onFailure(
                            REASON_AFC_RESPONSE_CODE_ERROR, "Response code server error. "
                                    + "HttpResponseCode=" + serverResponse.getHttpResponseCode()
                                    + ", AfcServerResponseCode=" + serverResponse
                                    .getAfcResponseCode() + ", Short Description: "
                                    + serverResponse.getAfcResponseDescription()));
                } else {
                    // Post the server response to the callback
                    wifiHandler.post(() -> callback.onResult(serverResponse, afcLocation));
                }
            } catch (ServiceEntitlementException e) {
                wifiHandler.post(() -> callback.onFailure(REASON_SERVICE_ENTITLEMENT_FAILURE,
                        "Encountered Service Entitlement Exception when sending "
                                + "request to server. " + e));
            } catch (JSONException e) {
                wifiHandler.post(() -> callback.onFailure(REASON_JSON_FAILURE,
                        "Encountered JSON error when parsing HTTP response." + e
                ));
            } catch (Exception e) {
                wifiHandler.post(() -> callback.onFailure(REASON_UNDEFINED_FAILURE,
                        "Encountered unexpected error when parsing AFC server's response."
                                + e));
            } finally {
                // Increment the request ID by 1 for the next request
                mRequestId++;
            }
        });
    }

    /**
     * Create and return the HttpRequest object that queries the AFC server for the afcLocation
     * object.
     */
    public HttpRequest getAfcHttpRequestObject(AfcLocation afcLocation) {
        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .setUrl(sServerUrl)
                .setRequestMethod(RequestMethod.POST)
                .setTimeoutInSec(CONNECT_TIMEOUT_SECS);
        for (Map.Entry<String, String> requestProperty : sRequestProperties.entrySet()) {
            httpRequestBuilder.addRequestProperty(requestProperty.getKey(),
                    requestProperty.getValue());
        }
        JSONObject jsonRequestObject = getAfcRequestJSONObject(afcLocation);
        httpRequestBuilder.setPostData(jsonRequestObject);
        return httpRequestBuilder.build();
    }

    /**
     * Get the AFC request JSON object used to query the AFC server.
     */
    private JSONObject getAfcRequestJSONObject(AfcLocation afcLocation) {
        try {
            JSONObject requestObject = new JSONObject();
            JSONArray inquiryRequests = new JSONArray();
            JSONObject inquiryRequest = new JSONObject();

            inquiryRequest.put("requestId", String.valueOf(mRequestId));

            JSONObject deviceDescriptor = new JSONObject();
            deviceDescriptor.put("serialNumber", AfcClient.SERIAL_NUMBER);
            JSONObject certificationId = new JSONObject();
            certificationId.put("nra", AfcClient.NRA);
            certificationId.put("id", AfcClient.ID);
            deviceDescriptor.put("certificationId", certificationId);
            deviceDescriptor.put("rulesetIds", AfcClient.RULE_SET_ID);
            inquiryRequest.put("deviceDescriptor", deviceDescriptor);

            JSONObject location = afcLocation.toJson();
            inquiryRequest.put("location", location);

            JSONArray inquiredFrequencyRange = new JSONArray();
            JSONObject range = new JSONObject();
            range.put("lowFrequency", AfcClient.LOW_FREQUENCY);
            range.put("highFrequency", AfcClient.HIGH_FREQUENCY);
            inquiredFrequencyRange.put(range);
            inquiryRequest.put("inquiredFrequencyRange", inquiredFrequencyRange);

            inquiryRequests.put(0, inquiryRequest);

            requestObject.put("version", AfcClient.VERSION);
            requestObject.put("availableSpectrumInquiryRequests", inquiryRequests);

            return requestObject;
        } catch (JSONException e) {
            Log.e(TAG, "Encountered error when building JSON object: " + e);
            return null;
        }
    }

    /**
     * Parses the AFC server's HTTP response and finds an Available Spectrum Inquiry Response with
     * a matching request ID.
     *
     * @param httpResponseJSON the response of the AFC server
     * @return the Available Spectrum Inquiry Response as a JSON Object with a request ID matching
     * the ID used in the request, or null if no object with a matching ID is found.
     */
    private JSONObject getAvailableSpectrumInquiryResponse(JSONObject httpResponseJSON,
            int requestId) {
        if (httpResponseJSON == null) {
            return null;
        }
        String requestIdString = Integer.toString(requestId);

        try {
            JSONArray spectrumInquiryResponses = httpResponseJSON.getJSONArray(
                    "availableSpectrumInquiryResponses");

            // iterate through responses to find one with a matching request ID
            for (int i = 0, numResponses = spectrumInquiryResponses.length();
                    i < numResponses; i++) {
                JSONObject spectrumInquiryResponse = spectrumInquiryResponses.getJSONObject(i);
                if (requestIdString.equals(spectrumInquiryResponse.getString("requestId"))) {
                    return spectrumInquiryResponse;
                }
            }

            Log.e(TAG, "Did not find an available spectrum inquiry response with request ID: "
                    + requestIdString);
        } catch (JSONException e) {
            Log.e(TAG, "Error occurred while parsing available spectrum inquiry response: "
                    + e);
        }
        return null;
    }

    /**
     * @return the http response code, or 500 if it does not exist
     */
    private int getHttpResponseCode(HttpResponse httpResponse) {
        return httpResponse == null ? 500 : httpResponse.responseCode();
    }

    /**
     * Set the server URL used to query the AFC server.
     */
    public void setServerURL(String url) {
        sServerUrl = url;
    }

    /**
     * Sets the request properties Map through copying the Map created from the configure-afc-server
     * wifi-shell command.
     * @param requestProperties A map with key and value Strings for the HTTP header's request
     *                          property fields. Each key has a corresponding value.
     */
    public void setRequestPropertyPairs(Map<String, String> requestProperties) {
        sRequestProperties = new HashMap<>();
        sRequestProperties.putAll(requestProperties);
    }

    /**
     * Get the server URL for this AfcClient.
     */
    public String getServerURL() {
        return sServerUrl;
    }

    /**
     * Get the Map with <key, value> header fields for the HTTP request properties.
     */
    public Map<String, String> getRequestProperties() {
        return sRequestProperties;
    }

    /**
     * Callback which will be called after AfcResponse retrieval.
     */
    public interface Callback {
        /**
         * Indicates that an AfcServerResponse was received successfully.
         */
        void onResult(AfcServerResponse serverResponse, AfcLocation afcLocation);
        /**
         * Indicate a failure happens when receiving the AfcServerResponse.
         * @param reasonCode The failure reason code.
         * @param description The description of the failure.
         */
        void onFailure(@FailureReasonCode int reasonCode, String description);
    }
}
