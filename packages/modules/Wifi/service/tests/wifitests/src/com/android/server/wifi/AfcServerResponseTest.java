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

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.AfcServerResponse}.
 */
public class AfcServerResponseTest {
    static final int SUCCESS_RESPONSE_CODE = 0;
    static final int GENERAL_FAILURE_RESPONSE_CODE = -1;
    static final int PROTOCOL_ERROR_RESPONSE_CODE = 100;
    static final int MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE = 300;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Returns a mock response from the AFC sever with valid available AFC frequency and channel
     * information.
     */
    private JSONObject buildSuccessfulSpectrumInquiryResponse() {
        JSONObject availableSpectrumInquiryResponse = new JSONObject();
        try {
            availableSpectrumInquiryResponse.put("availableFrequencyInfo",
                    buildAvailableFrequencyInfoArray());
            availableSpectrumInquiryResponse.put("availableChannelInfo",
                    buildAvailableChannelInfoArray());
            availableSpectrumInquiryResponse.put("availabilityExpireTime",
                    "2022-06-18T19:17:52.593206170Z");
            availableSpectrumInquiryResponse.put("response", new JSONObject());
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "shortDescription", "Example description");
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "responseCode", SUCCESS_RESPONSE_CODE);
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return availableSpectrumInquiryResponse;
    }

    /**
     * Creates a JSON array of allowed AFC frequencies.
     */
    public JSONArray buildAvailableFrequencyInfoArray() {
        try {
            JSONArray availableFrequencyInfo = new JSONArray();
            JSONObject frequencyJSON1 = new JSONObject();
            JSONObject frequencyJSON2 = new JSONObject();
            JSONObject frequencyJSON3 = new JSONObject();
            JSONObject frequencyRangeJSON1 = new JSONObject();
            JSONObject frequencyRangeJSON2 = new JSONObject();
            JSONObject frequencyRangeJSON3 = new JSONObject();
            availableFrequencyInfo.put(frequencyJSON1);
            availableFrequencyInfo.put(frequencyJSON2);
            availableFrequencyInfo.put(frequencyJSON3);

            frequencyJSON1.put("frequencyRange", frequencyRangeJSON1);
            frequencyJSON2.put("frequencyRange", frequencyRangeJSON2);
            frequencyJSON3.put("frequencyRange", frequencyRangeJSON3);
            frequencyRangeJSON1.put("lowFrequency", 6109);
            frequencyRangeJSON1.put("highFrequency", 6111);
            frequencyJSON1.put("maxPsd", 17);
            frequencyRangeJSON2.put("lowFrequency", 5925);
            frequencyRangeJSON2.put("highFrequency", 5930);
            frequencyJSON2.put("maxPsd", 23);
            frequencyRangeJSON3.put("lowFrequency", 6177);
            frequencyRangeJSON3.put("highFrequency", 6182);
            frequencyJSON3.put("maxPsd", 23);

            return availableFrequencyInfo;
        } catch (JSONException ignore) { /* should never occur */ }

        return null;
    }

    /**
     * Creates a JSON array of allowed AFC channels.
     */
    private JSONArray buildAvailableChannelInfoArray() {
        int[] channelCfis1 = {7, 23, 39, 55, 71, 87};
        int[] channelCfis2 = {15, 47, 79};
        int[] maxEirps1 = {31, 31, 34, 34, 31, 34};
        int[] maxEirps2 = {34, 36, 34};
        int globalOperatingClass1 = 133;
        int globalOperatingClass2 = 134;

        try {
            JSONArray availableChannelInfo = new JSONArray();
            JSONObject availableChannel1 = new JSONObject();
            JSONObject availableChannel2 = new JSONObject();
            availableChannelInfo.put(availableChannel1);
            availableChannelInfo.put(availableChannel2);

            JSONArray channelCfisJSON1 = new JSONArray();
            JSONArray maxEirpsJSON1 = new JSONArray();
            JSONArray channelCfisJSON2 = new JSONArray();
            JSONArray maxEirpsJSON2 = new JSONArray();
            availableChannel1.put("globalOperatingClass", globalOperatingClass1);
            availableChannel2.put("globalOperatingClass", globalOperatingClass2);
            availableChannel1.put("channelCfi", channelCfisJSON1);
            availableChannel2.put("channelCfi", channelCfisJSON2);
            availableChannel1.put("maxEirp", maxEirpsJSON1);
            availableChannel2.put("maxEirp", maxEirpsJSON2);

            for (int i = 0; i < channelCfis1.length; ++i) {
                channelCfisJSON1.put(channelCfis1[i]);
                maxEirpsJSON1.put(maxEirps1[i]);
            }

            for (int i = 0; i < channelCfis2.length; ++i) {
                channelCfisJSON2.put(channelCfis2[i]);
                maxEirpsJSON2.put(maxEirps2[i]);
            }

            return availableChannelInfo;
        } catch (JSONException ignore) { /* should never occur */ }

        return null;
    }

    /**
     * Builds a successful inquiry response JSON object, but the available frequency info is an
     * empty array.
     */
    JSONObject buildSuccessfulSpectrumInquiryResponseWithEmptyFrequencyInfo() {
        JSONObject availableSpectrumInquiryResponse = new JSONObject();
        try {
            availableSpectrumInquiryResponse.put("availableFrequencyInfo", new JSONArray());
            availableSpectrumInquiryResponse.put("availableChannelInfo",
                    buildAvailableChannelInfoArray());
            availableSpectrumInquiryResponse.put("availabilityExpireTime",
                    "2022-06-18T19:17:52.593206170Z");
            availableSpectrumInquiryResponse.put("response", new JSONObject());
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "shortDescription", "Example description");
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "responseCode", SUCCESS_RESPONSE_CODE);
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return availableSpectrumInquiryResponse;
    }

    /**
     * Builds a successful inquiry response JSON object, but the available channel info is an
     * empty array.
     */
    JSONObject buildSuccessfulSpectrumInquiryResponseWithEmptyChannelInfo() {
        JSONObject availableSpectrumInquiryResponse = new JSONObject();
        try {
            availableSpectrumInquiryResponse.put("availableFrequencyInfo",
                    buildAvailableFrequencyInfoArray());
            availableSpectrumInquiryResponse.put("availableChannelInfo", new JSONArray());
            availableSpectrumInquiryResponse.put("availabilityExpireTime",
                    "2022-06-18T19:17:52.593206170Z");
            availableSpectrumInquiryResponse.put("response", new JSONObject());
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "shortDescription", "Example description");
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "responseCode", SUCCESS_RESPONSE_CODE);
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return availableSpectrumInquiryResponse;
    }

    /**
     * Create a response JSON object with a specified AFC response code. This allows the response
     * to indicate if it was successful, or for what reason it failed.
     *
     * @param afcResponseCode the code indicating the status of the response from the AFC server
     * @return a spectrum inquiry response with the desired response code
     */
    JSONObject buildSpectrumInquiryResponseWithSpecifiedCode(int afcResponseCode) {
        JSONObject availableSpectrumInquiryResponse = new JSONObject();

        try {
            availableSpectrumInquiryResponse.put("availableFrequencyInfo",
                    new JSONArray());
            availableSpectrumInquiryResponse.put("availableChannelInfo",
                    buildAvailableChannelInfoArray());
            availableSpectrumInquiryResponse.put("availabilityExpireTime",
                    "2022-06-18T19:17:52.593206170Z");
            availableSpectrumInquiryResponse.put("response", new JSONObject());
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "shortDescription", "Example description");
            availableSpectrumInquiryResponse.getJSONObject("response").put(
                    "responseCode", afcResponseCode);
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return availableSpectrumInquiryResponse;
    }

    /**
     * Verify that the correct values are set when the response is successful.
     */
    @Test
    public void testSuccessfulResponse() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSuccessfulSpectrumInquiryResponse());

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(SUCCESS_RESPONSE_CODE, serverResponse.getAfcResponseCode());
        assertEquals(3, serverResponse.getAfcChannelAllowance().availableAfcFrequencyInfos.size());
        assertEquals(9, serverResponse.getAfcChannelAllowance().availableAfcChannelInfos.size());
    }

    /**
     * Verify that the availability expiration time is parsed and converted to a timestamp
     * correctly.
     */
    @Test
    public void testExpireTimeSetCorrectly() {
        int httpResponseCode = 200;
        String expireTimeString = "2023-06-22T19:47:20Z";
        long expireTimestampMs = 1687463240000L;

        try {
            JSONObject availableSpectrumInquiryResponse = buildSuccessfulSpectrumInquiryResponse();
            availableSpectrumInquiryResponse.put("availabilityExpireTime", expireTimeString);
            AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                    httpResponseCode, availableSpectrumInquiryResponse);

            assertEquals(expireTimestampMs,
                    serverResponse.getAfcChannelAllowance().availabilityExpireTimeMs);
        } catch (JSONException ignore) {
            // should never occur
        }
    }

    /**
     * Verify that when the response is successful and contains an empty frequency info array,
     * the AfcChannelAllowance allowed frequency list is empty.
     */
    @Test
    public void testResponseWithEmptyFrequencyArray() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSuccessfulSpectrumInquiryResponseWithEmptyFrequencyInfo());

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(SUCCESS_RESPONSE_CODE, serverResponse.getAfcResponseCode());

        // the allowed frequency array should be empty
        assertEquals(0, serverResponse.getAfcChannelAllowance().availableAfcFrequencyInfos.size());
    }

    /**
     * Verify that when the response is successful and contains an empty channel info array,
     * the AfcChannelAllowance allowed channel list is empty.
     */
    @Test
    public void testResponseWithEmptyChannelArray() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSuccessfulSpectrumInquiryResponseWithEmptyChannelInfo());

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(SUCCESS_RESPONSE_CODE, serverResponse.getAfcResponseCode());

        // the allowed frequency array should be empty
        assertEquals(0, serverResponse.getAfcChannelAllowance().availableAfcChannelInfos.size());
    }

    /**
     * Verify that the case of a response of type GENERAL_FAILURE is handled correctly.
     */
    @Test
    public void testGeneralFailureResponse() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSpectrumInquiryResponseWithSpecifiedCode(
                        GENERAL_FAILURE_RESPONSE_CODE));

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(GENERAL_FAILURE_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that the case of a response of type PROTOCOL_ERROR is handled correctly.
     */
    @Test
    public void testProtocolErrorResponse() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSpectrumInquiryResponseWithSpecifiedCode(
                        PROTOCOL_ERROR_RESPONSE_CODE));

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(PROTOCOL_ERROR_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that the case of a response of type MESSAGE_EXCHANGE_ERROR is handled correctly.
     */
    @Test
    public void testMessageExchangeErrorResponse() {
        int httpResponseCode = 200;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, buildSpectrumInquiryResponseWithSpecifiedCode(
                        MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE));

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that no crash occurs if a null spectrum inquiry response object is passed to the AFC
     * server response.
     */
    @Test
    public void testNoCrashOnNullInquiryResponse() {
        int httpResponseCode = 500;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, null);
        assertEquals(null, serverResponse);
    }
}
