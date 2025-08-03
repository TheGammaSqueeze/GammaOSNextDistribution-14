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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Creates the request to do authentication and query the entitlement status. */
public class RequestFactory {
    private static final int IMEI_LENGTH = 14;

    @VisibleForTesting
    static final String METHOD_3GPP_AUTHENTICATION = "3gppAuthentication";
    @VisibleForTesting
    static final String METHOD_GET_IMSI_PSEUDONYM = "getImsiPseudonym";
    @VisibleForTesting
    static final String JSON_KEY_MESSAGE_ID = "message-id";
    @VisibleForTesting
    static final String JSON_KEY_METHOD = "method";
    @VisibleForTesting
    static final String JSON_KEY_DEVICE_ID = "device-id";
    @VisibleForTesting
    static final String JSON_KEY_DEVICE_TYPE = "device-type";
    @VisibleForTesting
    static final String JSON_KEY_OS_TYPE = "os-type";
    @VisibleForTesting
    static final String JSON_KEY_DEVICE_NAME = "device-name";
    @VisibleForTesting
    static final String JSON_KEY_IMSI_EAP = "imsi-eap";
    @VisibleForTesting
    static final String JSON_KEY_AKA_TOKEN = "aka-token";
    @VisibleForTesting
    static final String JSON_KEY_AKA_CHALLENGE_RSP = "aka-challenge-rsp";

    @VisibleForTesting
    static final int DEVICE_TYPE_SIM = 0;
    @VisibleForTesting
    static final int OS_TYPE_ANDROID = 0;

    public static final int MESSAGE_ID_3GPP_AUTHENTICATION = 1;
    public static final int MESSAGE_ID_GET_IMSI_PSEUDONYM = 2;

    private final TelephonyManager mTelephonyManager;

    public RequestFactory(TelephonyManager telephonyManager) {
        mTelephonyManager = telephonyManager;
    }

    /**
     * Creates {@link JSONArray} object with method {@code METHOD_3GPP_AUTHENTICATION} to get
     * challenge response data.
     */
    public JSONArray createAuthRequest() throws TransientException {
        JSONArray requests = new JSONArray();
        try {
            requests.put(makeAuthenticationRequest(null /*akaToken*/, null /*challengeResponse*/));
        } catch (JSONException e) {
            throw new TransientException("createAuthRequest failed!" + e);
        }
        return requests;
    }

    /**
     * Creates {link JSONArray} object with method {@code METHOD_SERVICE_ENTITLEMENT_STATUS} to
     * query entitlement status.
     */
    public JSONArray createGetImsiPseudonymRequest(String akaToken, String challengeResponse)
            throws TransientException {
        JSONArray requests = new JSONArray();
        try {
            requests.put(makeAuthenticationRequest(akaToken, challengeResponse));
            requests.put(makeGetImsiPseudonymRequest());
        } catch (JSONException e) {
            throw new TransientException("createGetImsiPseudonymRequest failed!" + e);
        }
        return requests;
    }

    private JSONObject makeBaseRequest(int messageId, String method) throws JSONException {
        JSONObject request = new JSONObject();
        request.put(JSON_KEY_MESSAGE_ID, messageId);
        request.put(JSON_KEY_METHOD, method);
        return request;
    }

    @Nullable
    private String getImeiSv() {
        String imeiValue = mTelephonyManager.getImei();
        String svnValue = mTelephonyManager.getDeviceSoftwareVersion();
        if (TextUtils.isEmpty(imeiValue) || TextUtils.isEmpty(svnValue)) {
            return null;
        }
        if (imeiValue.length() > IMEI_LENGTH) {
            imeiValue = imeiValue.substring(0, IMEI_LENGTH);
        }
        String value = imeiValue + svnValue; // 14 digits + 2 digits
        return Base64.encodeToString(value.getBytes(UTF_8), Base64.NO_WRAP).trim();
    }

    private JSONObject makeAuthenticationRequest(String akaToken, String challengeResponse)
            throws JSONException, TransientException {
        JSONObject request =
                makeBaseRequest(MESSAGE_ID_3GPP_AUTHENTICATION, METHOD_3GPP_AUTHENTICATION);
        String imeiSv = getImeiSv();
        if (imeiSv == null) {
            // device-id(base64 encodede IMEISV) is mandatory.
            throw new TransientException("IMEISV is null.");
        }
        request.put(JSON_KEY_DEVICE_ID, imeiSv);
        request.put(JSON_KEY_DEVICE_TYPE, DEVICE_TYPE_SIM);
        request.put(JSON_KEY_OS_TYPE, OS_TYPE_ANDROID);
        request.put(JSON_KEY_DEVICE_NAME, Build.MODEL);
        request.put(JSON_KEY_IMSI_EAP, getImsiEap());
        if (!TextUtils.isEmpty(akaToken)) {
            request.put(JSON_KEY_AKA_TOKEN, akaToken);
        }
        if (!TextUtils.isEmpty(challengeResponse)) {
            request.put(JSON_KEY_AKA_CHALLENGE_RSP, challengeResponse);
        }
        return request;
    }

    @Nullable
    private String getImsiEap() {
        String imsi = mTelephonyManager.getSubscriberId();
        String mccmnc = mTelephonyManager.getSimOperator(); // MCCMNC is 5 or 6 decimal digits
        if ((imsi == null) || (mccmnc == null) || (mccmnc.length() < 5)) {
            return null;
        }
        String mcc = mccmnc.substring(0, 3);
        String mnc = mccmnc.substring(3);
        return String.format("0%s@nai.epc.mnc%s.mcc%s.3gppnetwork.org", imsi, mnc, mcc);
    }

    private JSONObject makeGetImsiPseudonymRequest() throws JSONException {
        return makeBaseRequest(MESSAGE_ID_GET_IMSI_PSEUDONYM, METHOD_GET_IMSI_PSEUDONYM);
    }
}

