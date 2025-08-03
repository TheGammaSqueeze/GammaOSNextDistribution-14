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

package com.android.server.wifi.entitlement;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_AKA_CHALLENGE_RSP;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_AKA_TOKEN;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_DEVICE_ID;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_IMSI_EAP;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_MESSAGE_ID;
import static com.android.server.wifi.entitlement.RequestFactory.JSON_KEY_METHOD;
import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION;
import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_GET_IMSI_PSEUDONYM;
import static com.android.server.wifi.entitlement.RequestFactory.METHOD_3GPP_AUTHENTICATION;
import static com.android.server.wifi.entitlement.RequestFactory.METHOD_GET_IMSI_PSEUDONYM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.telephony.TelephonyManager;
import android.util.Base64;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link RequestFactory}.
 */
@SmallTest
public class RequestFactoryTest extends WifiBaseTest {
    @Mock
    TelephonyManager mTelephonyManager;
    private static final String IMEI = "1234";
    private static final String DEVICE_SV = "01";
    private static final String BASE64_IMEI =
            Base64.encodeToString((IMEI + DEVICE_SV).getBytes(UTF_8), Base64.NO_WRAP).trim();
    private static final String IMSI = "6789";
    private static final String MCC = "123";
    private static final String MNC = "45";

    private static final String CHALLENGE_RESPONSE = "CHALLENGE_RESPONSE";
    private static final String AKA_TOKEN = "AKA_TOKEN";
    private static final String SIM_OPERATOR_INVALID = "1234";

    private RequestFactory mRequestFactory;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRequestFactory = new RequestFactory(mTelephonyManager);
    }

    @Test
    public void createAuthRequestSuccess() throws JSONException, TransientException {
        when(mTelephonyManager.getImei()).thenReturn(IMEI);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(IMSI);
        when(mTelephonyManager.getSimOperator()).thenReturn(MCC + MNC);

        JSONArray authRequest = mRequestFactory.createAuthRequest();
        assertEquals(1, authRequest.length());

        JSONObject jsonObject = authRequest.getJSONObject(0);
        assertEquals(MESSAGE_ID_3GPP_AUTHENTICATION, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_3GPP_AUTHENTICATION, jsonObject.get(JSON_KEY_METHOD));
        assertEquals(BASE64_IMEI, jsonObject.get(JSON_KEY_DEVICE_ID));
        assertEquals("0" + IMSI + "@nai.epc.mnc" + MNC + ".mcc" + MCC + ".3gppnetwork.org",
                jsonObject.get(JSON_KEY_IMSI_EAP));

    }
    @Test
    public void createAuthRequestFailureWrongSimOperator() throws JSONException,
            TransientException {
        when(mTelephonyManager.getImei()).thenReturn(IMEI);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(IMSI);
        when(mTelephonyManager.getSimOperator()).thenReturn(SIM_OPERATOR_INVALID);

        JSONArray authRequest = mRequestFactory.createAuthRequest();
        assertEquals(1, authRequest.length());

        JSONObject jsonObject = authRequest.getJSONObject(0);
        assertEquals(MESSAGE_ID_3GPP_AUTHENTICATION, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_3GPP_AUTHENTICATION, jsonObject.get(JSON_KEY_METHOD));
        assertEquals(BASE64_IMEI, jsonObject.get(JSON_KEY_DEVICE_ID));
        assertFalse(jsonObject.has(JSON_KEY_IMSI_EAP));
    }

    @Test
    public void createAuthRequestFailureNullImsi() throws JSONException, TransientException {
        when(mTelephonyManager.getImei()).thenReturn(IMEI);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(null);
        when(mTelephonyManager.getSimOperator()).thenReturn(MCC + MNC);

        JSONArray authRequest = mRequestFactory.createAuthRequest();
        assertEquals(1, authRequest.length());

        JSONObject jsonObject = authRequest.getJSONObject(0);
        assertEquals(MESSAGE_ID_3GPP_AUTHENTICATION, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_3GPP_AUTHENTICATION, jsonObject.get(JSON_KEY_METHOD));
        assertEquals(BASE64_IMEI, jsonObject.get(JSON_KEY_DEVICE_ID));
        assertFalse(jsonObject.has(JSON_KEY_IMSI_EAP));
    }

    @Test
    public void createAuthRequestFailureNullImei() {
        when(mTelephonyManager.getImei()).thenReturn(null);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(null);
        when(mTelephonyManager.getSimOperator()).thenReturn(MCC + MNC);

        assertThrows(TransientException.class, () -> mRequestFactory.createAuthRequest());
    }

    @Test
    public void createGetImsiPseudonymRequestSuccess() throws JSONException, TransientException {
        when(mTelephonyManager.getImei()).thenReturn(IMEI);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(IMSI);
        when(mTelephonyManager.getSimOperator()).thenReturn(MCC + MNC);

        JSONArray getImsiPseudonymRequest =
                mRequestFactory.createGetImsiPseudonymRequest(AKA_TOKEN, CHALLENGE_RESPONSE);
        assertEquals(2, getImsiPseudonymRequest.length());

        JSONObject jsonObject = getImsiPseudonymRequest.getJSONObject(0);
        assertEquals(MESSAGE_ID_3GPP_AUTHENTICATION, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_3GPP_AUTHENTICATION, jsonObject.get(JSON_KEY_METHOD));
        assertEquals(AKA_TOKEN, jsonObject.get(JSON_KEY_AKA_TOKEN));
        assertEquals(CHALLENGE_RESPONSE, jsonObject.get(JSON_KEY_AKA_CHALLENGE_RSP));

        jsonObject = getImsiPseudonymRequest.getJSONObject(1);
        assertEquals(MESSAGE_ID_GET_IMSI_PSEUDONYM, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_GET_IMSI_PSEUDONYM, jsonObject.get(JSON_KEY_METHOD));
    }

    @Test
    public void createGetImsiPseudonymRequestWithEmptyData() throws JSONException,
            TransientException {
        when(mTelephonyManager.getImei()).thenReturn(IMEI);
        when(mTelephonyManager.getDeviceSoftwareVersion()).thenReturn(DEVICE_SV);
        when(mTelephonyManager.getSubscriberId()).thenReturn(IMSI);
        when(mTelephonyManager.getSimOperator()).thenReturn(MCC + MNC);

        JSONArray getImsiPseudonymRequest =
                mRequestFactory.createGetImsiPseudonymRequest(null, "");
        assertEquals(2, getImsiPseudonymRequest.length());

        JSONObject jsonObject = getImsiPseudonymRequest.getJSONObject(0);
        assertEquals(MESSAGE_ID_3GPP_AUTHENTICATION, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_3GPP_AUTHENTICATION, jsonObject.get(JSON_KEY_METHOD));
        assertFalse(jsonObject.has(JSON_KEY_AKA_TOKEN));
        assertFalse(jsonObject.has(JSON_KEY_AKA_CHALLENGE_RSP));

        jsonObject = getImsiPseudonymRequest.getJSONObject(1);
        assertEquals(MESSAGE_ID_GET_IMSI_PSEUDONYM, jsonObject.getInt(JSON_KEY_MESSAGE_ID));
        assertEquals(METHOD_GET_IMSI_PSEUDONYM, jsonObject.get(JSON_KEY_METHOD));
    }

}
