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

import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONObject;

/** The response of any entitlement request. */
public abstract class Response {
    static final String JSON_KEY_MESSAGE_ID = "message-id";
    static final String JSON_KEY_RESPONSE_CODE = "response-code";
    @VisibleForTesting
    static final String JSON_KEY_EAP_AKA_CHALLENGE = "aka-challenge";
    @VisibleForTesting
    static final String JSON_KEY_AKA_TOKEN = "aka-token";
    public static final int RESPONSE_CODE_REQUEST_SUCCESSFUL = 1000;
    public static final int RESPONSE_CODE_AKA_CHALLENGE = 1003;
    public static final int RESPONSE_CODE_INVALID_REQUEST = 1004;
    public static final int RESPONSE_CODE_AKA_AUTH_FAILED = 1006;

    // The request is not applicable to this subscriber or the imsi-pseudonym cannot be retrieved.
    public static final int RESPONSE_CODE_FORBIDDEN_REQUEST = 1007;
    public static final int RESPONSE_CODE_SERVER_ERROR = 1111;
    public static final int RESPONSE_CODE_3GPP_AUTH_ONGOING = 1112;

    // The method is not supported by the installed version of SES
    public static final int RESPONSE_CODE_UNSUPPORTED_OPERATION = 9999;
    protected int mAuthResponseCode;
    protected String mEapAkaChallenge;
    protected String mAkaToken;

    protected void parse3gppAuthentication(JSONObject object) {
        mAuthResponseCode = object.optInt(JSON_KEY_RESPONSE_CODE, -1);
        mEapAkaChallenge = object.optString(JSON_KEY_EAP_AKA_CHALLENGE, null);
        mAkaToken = object.optString(JSON_KEY_AKA_TOKEN, null);
    }

    public int getAuthResponseCode() {
        return mAuthResponseCode;
    }
}

