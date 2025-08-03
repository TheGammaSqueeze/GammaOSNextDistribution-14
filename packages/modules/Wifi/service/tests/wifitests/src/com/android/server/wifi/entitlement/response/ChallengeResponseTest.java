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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.entitlement.RequestFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests for {@link ChallengeResponse}.
 */
@SmallTest
public class ChallengeResponseTest {
    private static final String CHALLENGE = "challenge";
    private static final int INVALID_MESSAGE_ID = -1;

    @Test
    public void responseBodyCorrect() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_AKA_CHALLENGE)
                .put(Response.JSON_KEY_EAP_AKA_CHALLENGE, CHALLENGE);
        JSONArray bodyArray = (new JSONArray()).put(body1);

        ChallengeResponse response = new ChallengeResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_AKA_CHALLENGE, response.getAuthResponseCode());
        assertEquals(CHALLENGE, response.getEapAkaChallenge());
    }

    @Test
    public void responseBodyNotJsonArray() {
        ChallengeResponse response = new ChallengeResponse("wrongbody");
        assertEquals(0, response.getAuthResponseCode());
        assertNull(response.getEapAkaChallenge());
    }

    @Test
    public void responseBodyInvalidRequest() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID,
                        RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_INVALID_REQUEST);
        JSONArray bodyArray = (new JSONArray()).put(body1);

        ChallengeResponse response = new ChallengeResponse(bodyArray.toString());
        assertEquals(Response.RESPONSE_CODE_INVALID_REQUEST, response.getAuthResponseCode());
        assertNull(response.getEapAkaChallenge());
    }

    @Test
    public void responseBodyWrongMessageId() throws JSONException {
        JSONObject body1 = (new JSONObject()).put(Response.JSON_KEY_MESSAGE_ID, INVALID_MESSAGE_ID)
                .put(Response.JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_AKA_CHALLENGE)
                .put(Response.JSON_KEY_EAP_AKA_CHALLENGE, CHALLENGE);
        JSONArray bodyArray = (new JSONArray()).put(body1);

        ChallengeResponse response = new ChallengeResponse(bodyArray.toString());
        assertEquals(0, response.getAuthResponseCode());
        assertNull(response.getEapAkaChallenge());
    }
}
