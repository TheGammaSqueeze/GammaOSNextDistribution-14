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

package com.android.server.wifi.entitlement.response;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION;
import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_GET_IMSI_PSEUDONYM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.entitlement.PseudonymInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.time.Duration;

/**
 * Unit tests for {@link ChallengeResponse}.
 */
@SmallTest
public class GetImsiPseudonymResponseTest {
    private static final String AKA_TOKEN = "aka_token";
    private static final String IMSI_PSEUDONYM = "imsi_pseudonym";
    private static final int REFRESH_INTERVAL = 48;
    private static final long DEFAULT_PSEUDONYM_TTL_IN_MILLIS = Duration.ofDays(2).toMillis();
    private static final int INVALID_MESSAGE_ID = -1;
    private static final String IMSI = "imsi";

    @Test
    public void responseBodyCorrectWithRefreshInterval() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_AKA_TOKEN, AKA_TOKEN);
        JSONObject body2 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_IMSI_PSEUDONYM, IMSI_PSEUDONYM)
                .put(GetImsiPseudonymResponse.JSON_KEY_REFRESH_INTERVAL, REFRESH_INTERVAL);
        JSONArray bodyArray = (new JSONArray()).put(body1).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL, response.getAuthResponseCode());
        assertEquals(AKA_TOKEN, response.getAkaToken());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL,
                response.getGetImsiPseudonymResponseCode());
        PseudonymInfo pseudonymInfo = response.toPseudonymInfo(IMSI).get();
        assertEquals(IMSI_PSEUDONYM, pseudonymInfo.getPseudonym());
        assertEquals(REFRESH_INTERVAL * HOUR_IN_MILLIS, pseudonymInfo.getTtlInMillis());
        assertEquals(IMSI, pseudonymInfo.getImsi());
    }

    @Test
    public void responseBodyCorrectWithoutRefreshInterval() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_AKA_TOKEN, AKA_TOKEN);
        JSONObject body2 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_IMSI_PSEUDONYM, IMSI_PSEUDONYM);
        JSONArray bodyArray = (new JSONArray()).put(body1).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL, response.getAuthResponseCode());
        assertEquals(AKA_TOKEN, response.getAkaToken());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL,
                response.getGetImsiPseudonymResponseCode());
        PseudonymInfo pseudonymInfo = response.toPseudonymInfo(IMSI).get();
        assertEquals(IMSI_PSEUDONYM, pseudonymInfo.getPseudonym());
        assertEquals(DEFAULT_PSEUDONYM_TTL_IN_MILLIS, pseudonymInfo.getTtlInMillis());
        assertEquals(IMSI, pseudonymInfo.getImsi());
    }

    @Test
    public void responseBodyNotJsonArray() {
        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse("wrongbody");
        assertEquals(0, response.getAuthResponseCode());
        assertEquals(0, response.getGetImsiPseudonymResponseCode());
        assertTrue(response.toPseudonymInfo(IMSI).isEmpty());
    }

    @Test
    public void responseBodyInvalidRequest() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_AKA_TOKEN, AKA_TOKEN);
        JSONObject body2 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_INVALID_REQUEST)
                .put(GetImsiPseudonymResponse.JSON_KEY_IMSI_PSEUDONYM, IMSI_PSEUDONYM)
                .put(GetImsiPseudonymResponse.JSON_KEY_REFRESH_INTERVAL, REFRESH_INTERVAL);
        JSONArray bodyArray = (new JSONArray()).put(body1).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL, response.getAuthResponseCode());
        assertEquals(AKA_TOKEN, response.getAkaToken());
        assertEquals(Response.RESPONSE_CODE_INVALID_REQUEST,
                response.getGetImsiPseudonymResponseCode());
        assertTrue(response.toPseudonymInfo(IMSI).isEmpty());
    }

    @Test
    public void responseBodyAuthFailed() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_AKA_AUTH_FAILED)
                .put(GetImsiPseudonymResponse.JSON_KEY_AKA_TOKEN, AKA_TOKEN);
        JSONObject body2 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(GetImsiPseudonymResponse.JSON_KEY_IMSI_PSEUDONYM, IMSI_PSEUDONYM)
                .put(GetImsiPseudonymResponse.JSON_KEY_REFRESH_INTERVAL, REFRESH_INTERVAL);
        JSONArray bodyArray = (new JSONArray()).put(body1).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_AKA_AUTH_FAILED, response.getAuthResponseCode());
        assertEquals(AKA_TOKEN, response.getAkaToken());
        assertEquals(Response.RESPONSE_CODE_REQUEST_SUCCESSFUL,
                response.getGetImsiPseudonymResponseCode());
        assertTrue(response.toPseudonymInfo(IMSI).isEmpty());
    }

    @Test
    public void responseBodyWrongMessageId() throws JSONException {
        JSONObject body2 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID, INVALID_MESSAGE_ID)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL);
        JSONArray bodyArray = (new JSONArray()).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(0, response.getAuthResponseCode());
        assertEquals(0, response.getGetImsiPseudonymResponseCode());
        assertNull(response.getAkaToken());
        assertTrue(response.toPseudonymInfo(IMSI).isEmpty());
    }

    @Test
    public void responseBodyServerError() throws JSONException {
        JSONObject body2 = (new JSONObject())
                .put(Response.JSON_KEY_MESSAGE_ID, MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_SERVER_ERROR);
        JSONArray bodyArray = (new JSONArray()).put(body2);

        GetImsiPseudonymResponse response = new GetImsiPseudonymResponse(bodyArray.toString());
        assertEquals(0, response.getAuthResponseCode());
        assertEquals(Response.RESPONSE_CODE_SERVER_ERROR,
                response.getGetImsiPseudonymResponseCode());
        assertNull(response.getAkaToken());
        assertTrue(response.toPseudonymInfo(IMSI).isEmpty());
    }
}
